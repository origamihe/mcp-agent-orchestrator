package com.mcp.tools.model;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

/**
 * 文件版本信息，用于"读→改→版本校验→写"的编辑流程。
 * agent 读取文件时拿到 fileHash，修改时带上 expectedHash，
 * 如果文件已被其他进程/agent 修改，则拒绝写入，避免覆盖。
 */
public record FileVersion(
        String fileHash,       // SHA-256 hex (64 chars)
        long lastModified,     // epoch millis
        int lineCount,
        long fileSize,
        String version         // fileHash 前8位，轻量版本号
) {

    public static FileVersion of(String content, long lastModified) {
        String hash = sha256(content);
        int lineCount = content.isEmpty() ? 0 : content.split("\n", -1).length;
        return new FileVersion(
                hash,
                lastModified,
                lineCount,
                content.getBytes(StandardCharsets.UTF_8).length,
                hash.substring(0, Math.min(8, hash.length()))
        );
    }

    public static FileVersion empty() {
        return new FileVersion("", 0L, 0, 0L, "");
    }

    private static String sha256(String content) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(content.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}
