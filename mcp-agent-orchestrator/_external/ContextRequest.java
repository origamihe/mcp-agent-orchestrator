package com.mcp.engine.context;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class ContextRequest {

    private String sessionId;
    private String userId;
    private List<String> filePaths;
    private String userRequest;
    private int maxFileTokens;
    private int maxMemoryTokens;
    private int maxHistoryTokens;
}