package com.mcp.engine.context;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class FileContext {

    private String filePath;
    private String content;
    private int startLine;
    private int endLine;
    private int estimatedTokens;
    private boolean isFullFile;

    public String toPromptBlock() {
        StringBuilder sb = new StringBuilder();
        sb.append("【文件：").append(filePath).append("】");
        if (!isFullFile) {
            sb.append("（第").append(startLine).append("-").append(endLine).append("行）");
        }
        sb.append("\n").append(content).append("\n");
        return sb.toString();
    }
}