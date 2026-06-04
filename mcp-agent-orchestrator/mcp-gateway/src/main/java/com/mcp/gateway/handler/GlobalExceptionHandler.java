package com.mcp.gateway.handler;

import com.mcp.core.mcp.model.McpMessage;
import com.mcp.core.mcp.exception.McpException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import reactor.core.publisher.Mono;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(McpException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Mono<McpMessage> handleMcpException(McpException ex) {
        return Mono.just(McpMessage.builder()
                .error(new com.mcp.core.mcp.model.McpError(ex.getErrorCode(), ex.getMessage(), null))
                .build());
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Mono<McpMessage> handleGeneralException(Exception ex) {
        return Mono.just(McpMessage.builder()
                .error(new com.mcp.core.mcp.model.McpError(-32000, "Internal error: " + ex.getMessage(), null))
                .build());
    }
}