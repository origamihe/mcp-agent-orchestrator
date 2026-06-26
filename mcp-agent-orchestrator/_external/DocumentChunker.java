package com.mcp.tools.document.chunker;

import com.mcp.tools.model.DocumentChunk;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class DocumentChunker {

    private static final int DEFAULT_CHUNK_SIZE = 2000;
    private static final int DEFAULT_OVERLAP = 200;

    public List<DocumentChunk> chunkByHeading(List<DocumentChunk> chunks) {
        List<DocumentChunk> result = new ArrayList<>();
        String currentSection = null;
        StringBuilder currentText = new StringBuilder();
        int currentPage = 1;
        int offset = 0;

        for (DocumentChunk chunk : chunks) {
            if (chunk.heading() != null) {
                if (!currentText.isEmpty()) {
                    result.add(createChunk(currentText.toString(), currentPage,
                            currentSection, offset++, chunk.sourcePath(), chunk.fileType()));
                    currentText = new StringBuilder();
                }
                currentSection = chunk.heading();
                currentText.append(chunk.text()).append("\n\n");
            } else {
                if (currentText.length() + chunk.text().length() > DEFAULT_CHUNK_SIZE) {
                    if (!currentText.isEmpty()) {
                        result.add(createChunk(currentText.toString(), currentPage,
                                currentSection, offset++, chunk.sourcePath(), chunk.fileType()));
                    }
                    currentText = new StringBuilder(chunk.text()).append("\n\n");
                } else {
                    currentText.append(chunk.text()).append("\n\n");
                }
            }
            currentPage = chunk.pageNo();
        }

        if (!currentText.isEmpty()) {
            result.add(createChunk(currentText.toString(), currentPage,
                    currentSection, offset, chunks.get(0).sourcePath(), chunks.get(0).fileType()));
        }

        return result;
    }

    public List<DocumentChunk> chunkByPage(List<DocumentChunk> chunks) {
        List<DocumentChunk> result = new ArrayList<>();
        int currentPage = 0;
        StringBuilder pageText = new StringBuilder();

        for (DocumentChunk chunk : chunks) {
            if (chunk.pageNo() != currentPage) {
                if (!pageText.isEmpty()) {
                    result.add(createChunk(pageText.toString(), currentPage, null, 0,
                            chunk.sourcePath(), chunk.fileType()));
                }
                currentPage = chunk.pageNo();
                pageText = new StringBuilder(chunk.text()).append("\n\n");
            } else {
                pageText.append(chunk.text()).append("\n\n");
            }
        }

        if (!pageText.isEmpty()) {
            result.add(createChunk(pageText.toString(), currentPage, null, 0,
                    chunks.get(0).sourcePath(), chunks.get(0).fileType()));
        }

        return result;
    }

    public List<DocumentChunk> chunkBySize(List<DocumentChunk> chunks, int chunkSize, int overlap) {
        List<DocumentChunk> result = new ArrayList<>();
        StringBuilder buffer = new StringBuilder();
        int currentPage = 1;
        int offset = 0;

        for (DocumentChunk chunk : chunks) {
            String text = chunk.text();
            if (buffer.length() + text.length() > chunkSize && !buffer.isEmpty()) {
                result.add(createChunk(buffer.toString(), currentPage, null, offset++,
                        chunk.sourcePath(), chunk.fileType()));

                if (overlap > 0 && buffer.length() > overlap) {
                    String overlapText = buffer.substring(buffer.length() - overlap);
                    buffer = new StringBuilder(overlapText);
                } else {
                    buffer = new StringBuilder();
                }
            }
            buffer.append(text).append("\n\n");
            currentPage = chunk.pageNo();
        }

        if (!buffer.isEmpty()) {
            result.add(createChunk(buffer.toString(), currentPage, null, offset,
                    chunks.get(0).sourcePath(), chunks.get(0).fileType()));
        }

        return result;
    }

    private DocumentChunk createChunk(String text, int pageNo, String section,
                                      int offset, String sourcePath, String fileType) {
        String chunkId = sourcePath + "#c" + offset;
        return new DocumentChunk(chunkId, pageNo, section, null, text.trim(),
                null, null, offset, sourcePath, fileType);
    }
}