package com.mcp.tools.document.parser;

import com.mcp.tools.document.ocr.OcrFallback;
import com.mcp.tools.model.DocumentChunk;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class PdfParser implements DocumentParser {

    private final OcrFallback ocrFallback;

    @Override
    public String supportedType() {
        return "pdf";
    }

    @Override
    public DocumentParseResult parse(Path filePath) throws Exception {
        Map<String, String> metadata = new LinkedHashMap<>();
        String sourcePath = filePath.toString();

        BasicFileAttributes attrs = Files.readAttributes(filePath, BasicFileAttributes.class);
        metadata.put("createdAt", DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                .withZone(ZoneId.systemDefault())
                .format(Instant.ofEpochMilli(attrs.creationTime().toMillis())));
        metadata.put("fileSize", String.valueOf(Files.size(filePath)));

        try (var document = org.apache.pdfbox.Loader.loadPDF(filePath.toFile())) {
            metadata.put("totalPages", String.valueOf(document.getNumberOfPages()));

            List<DocumentChunk> chunks = new ArrayList<>();
            boolean hasText = false;

            for (int i = 0; i < document.getNumberOfPages(); i++) {
                var page = document.getPage(i);
                int pageNo = i + 1;

                var stripper = new org.apache.pdfbox.text.PDFTextStripper();
                stripper.setStartPage(pageNo);
                stripper.setEndPage(pageNo);
                stripper.setSortByPosition(true);
                stripper.setAddMoreFormatting(true);

                String pageText = stripper.getText(document).trim();

                if (!pageText.isEmpty()) {
                    hasText = true;
                    pageText = cleanPdfText(pageText);

                    List<DocumentChunk> pageChunks = splitPageIntoChunks(
                            pageText, pageNo, sourcePath);
                    chunks.addAll(pageChunks);
                }
            }

            if (!hasText && ocrFallback.isAvailable()) {
                log.info("No extractable text, falling back to OCR for: {}", sourcePath);
                var ocrResult = ocrFallback.ocr(filePath);
                chunks.addAll(ocrResult.chunks());
                metadata.put("isOcr", "true");
                return new DocumentParseResult(chunks,
                        document.getNumberOfPages(), true, metadata);
            }

            return new DocumentParseResult(chunks,
                    document.getNumberOfPages(), !hasText, metadata);

        } catch (NoClassDefFoundError e) {
            log.warn("PDFBox not available on classpath, cannot parse PDF: {}", e.getMessage());
            throw new Exception("PDFBox library not available. Please add pdfbox dependency.", e);
        }
    }

    private String cleanPdfText(String raw) {
        return raw
                .replaceAll("(?<!\n)\n(?!\n)", " ")
                .replaceAll("\n{3,}", "\n\n")
                .replaceAll("\\s{2,}", " ")
                .replaceAll("^\\s*\\d+\\s*$", "")
                .trim();
    }

    private List<DocumentChunk> splitPageIntoChunks(String pageText, int pageNo, String sourcePath) {
        List<DocumentChunk> chunks = new ArrayList<>();
        String[] paragraphs = pageText.split("\n\n");

        for (int i = 0; i < paragraphs.length; i++) {
            String text = paragraphs[i].trim();
            if (text.isEmpty()) continue;

            String chunkId = sourcePath + "#p" + pageNo + "s" + i;
            chunks.add(new DocumentChunk(
                    chunkId, pageNo, null, null, text,
                    null, null, i, sourcePath, "pdf"
            ));
        }
        return chunks;
    }
}