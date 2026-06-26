package com.mcp.tools.document.normalizer;

import com.mcp.tools.model.DocumentChunk;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class DocumentNormalizer {

    public String toMarkdown(List<DocumentChunk> chunks) {
        StringBuilder md = new StringBuilder();

        String lastSection = null;
        for (DocumentChunk chunk : chunks) {
            if (chunk.heading() != null) {
                int level = Math.min(6, chunk.heading().length() > 0 ? 2 : 1);
                md.append("#".repeat(level)).append(" ").append(chunk.heading()).append("\n\n");
                lastSection = chunk.heading();
            } else if (chunk.tables() != null && !chunk.tables().isEmpty()) {
                md.append(chunk.text()).append("\n\n");
            } else {
                md.append(chunk.text()).append("\n\n");
            }
        }

        return md.toString().trim();
    }

    public String toStructuredJson(List<DocumentChunk> chunks, String sourcePath, String fileType) {
        StringBuilder json = new StringBuilder();
        json.append("{\"source\":\"").append(escapeJson(sourcePath)).append("\"");
        json.append(",\"type\":\"").append(fileType).append("\"");
        json.append(",\"chunks\":[");
        for (int i = 0; i < chunks.size(); i++) {
            if (i > 0) json.append(",");
            DocumentChunk c = chunks.get(i);
            json.append("{\"page\":").append(c.pageNo());
            if (c.section() != null) {
                json.append(",\"section\":\"").append(escapeJson(c.section())).append("\"");
            }
            if (c.heading() != null) {
                json.append(",\"heading\":\"").append(escapeJson(c.heading())).append("\"");
            }
            json.append(",\"text\":\"").append(escapeJson(c.text())).append("\"");
            if (c.tables() != null && !c.tables().isEmpty()) {
                json.append(",\"hasTable\":true");
                json.append(",\"tableCount\":").append(c.tables().size());
            }
            json.append(",\"offset\":").append(c.offset());
            json.append(",\"sourcePath\":\"").append(escapeJson(c.sourcePath())).append("\"");
            json.append("}");
        }
        json.append("]}");
        return json.toString();
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}