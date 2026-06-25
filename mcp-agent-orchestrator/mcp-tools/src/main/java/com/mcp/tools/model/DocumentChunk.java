package com.mcp.tools.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record DocumentChunk(
        String chunkId,
        int pageNo,
        String section,
        String heading,
        String text,
        List<DocumentTable> tables,
        List<String> images,
        int offset,
        String sourcePath,
        String fileType
) {
    public static DocumentChunk of(int pageNo, String text, String sourcePath, String fileType) {
        String chunkId = sourcePath + "#p" + pageNo;
        return new DocumentChunk(chunkId, pageNo, null, null, text, null, null, 0, sourcePath, fileType);
    }

    public DocumentChunk withSection(String section) {
        return new DocumentChunk(chunkId, pageNo, section, heading, text, tables, images, offset, sourcePath, fileType);
    }

    public DocumentChunk withHeading(String heading) {
        return new DocumentChunk(chunkId, pageNo, section, heading, text, tables, images, offset, sourcePath, fileType);
    }

    public DocumentChunk withTables(List<DocumentTable> tables) {
        return new DocumentChunk(chunkId, pageNo, section, heading, text, tables, images, offset, sourcePath, fileType);
    }

    public DocumentChunk withOffset(int offset) {
        return new DocumentChunk(chunkId, pageNo, section, heading, text, tables, images, offset, sourcePath, fileType);
    }

    public String toMarkdownRef() {
        return "[page " + pageNo + "](" + sourcePath + "#page=" + pageNo + ")";
    }
}
