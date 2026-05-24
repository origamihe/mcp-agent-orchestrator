package com.mcp.core.mcp.exception;

public class McpException extends RuntimeException {

    private final int errorCode;

    public McpException(int errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public int getErrorCode() {
        return errorCode;
    }
}