package com.mcp.tools.document.ocr;

import com.mcp.tools.model.DocumentChunk;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class OcrFallback {

    private volatile boolean tesseractAvailable = false;
    private volatile boolean paddleAvailable = false;

    public OcrFallback() {
        checkAvailability();
    }

    private void checkAvailability() {
        try {
            ProcessBuilder pb = new ProcessBuilder("tesseract", "--version");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            int exitCode = p.waitFor();
            tesseractAvailable = (exitCode == 0);
            if (tesseractAvailable) {
                log.info("Tesseract OCR is available");
            }
        } catch (Exception e) {
            log.debug("Tesseract OCR not available: {}", e.getMessage());
        }

        try {
            Class.forName("com.github.paddleocr.PaddleOCR");
            paddleAvailable = true;
            log.info("PaddleOCR is available on classpath");
        } catch (ClassNotFoundException e) {
            log.debug("PaddleOCR not on classpath");
        }
    }

    public boolean isAvailable() {
        return tesseractAvailable || paddleAvailable;
    }

    public OcrResult ocr(Path filePath) throws Exception {
        if (tesseractAvailable) {
            return ocrWithTesseract(filePath);
        }
        if (paddleAvailable) {
            return ocrWithPaddle(filePath);
        }
        throw new Exception("No OCR engine available. Install Tesseract or add PaddleOCR dependency.");
    }

    private OcrResult ocrWithTesseract(Path filePath) throws Exception {
        List<DocumentChunk> chunks = new ArrayList<>();
        String sourcePath = filePath.toString();

        ProcessBuilder pb = new ProcessBuilder(
                "tesseract", filePath.toString(), "stdout",
                "-l", "chi_sim+eng",
                "--psm", "1"
        );
        pb.redirectErrorStream(true);
        Process p = pb.start();

        StringBuilder allText = new StringBuilder();
        try (var reader = new java.io.BufferedReader(
                new java.io.InputStreamReader(p.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                allText.append(line).append("\n");
            }
        }
        p.waitFor();

        String text = allText.toString().trim();
        if (text.isEmpty()) {
            throw new Exception("Tesseract OCR produced no text");
        }

        String[] paragraphs = text.split("\n\n");
        for (int i = 0; i < paragraphs.length; i++) {
            String para = paragraphs[i].trim();
            if (para.isEmpty()) continue;
            chunks.add(new DocumentChunk(
                    sourcePath + "#ocr" + i, 1, null, null, para,
                    null, null, i, sourcePath, "pdf"
            ));
        }

        return new OcrResult(chunks, 1, true);
    }

    private OcrResult ocrWithPaddle(Path filePath) throws Exception {
        List<DocumentChunk> chunks = new ArrayList<>();
        String sourcePath = filePath.toString();

        try {
            Class<?> paddleClass = Class.forName("com.github.paddleocr.PaddleOCR");
            Object paddle = paddleClass.getDeclaredConstructor().newInstance();
            var runMethod = paddleClass.getMethod("runOcr", String.class);
            @SuppressWarnings("unchecked")
            var results = (List<?>) runMethod.invoke(paddle, filePath.toString());

            for (int i = 0; i < results.size(); i++) {
                var result = results.get(i);
                String text = result.toString();
                if (!text.isEmpty()) {
                    chunks.add(new DocumentChunk(
                            sourcePath + "#paddle" + i, i + 1, null, null, text,
                            null, null, i, sourcePath, "pdf"
                    ));
                }
            }
        } catch (Exception e) {
            throw new Exception("PaddleOCR failed: " + e.getMessage(), e);
        }

        return new OcrResult(chunks, chunks.isEmpty() ? 1 : chunks.size(), true);
    }

    public record OcrResult(
            List<DocumentChunk> chunks,
            int totalPages,
            boolean isOcr
    ) {}
}