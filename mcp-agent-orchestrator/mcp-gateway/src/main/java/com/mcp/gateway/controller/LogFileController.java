package com.mcp.gateway.controller;

import com.mcp.gateway.service.LogFileReaderService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/logs/files")
@RequiredArgsConstructor
public class LogFileController {

    private final LogFileReaderService logFileReaderService;

    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    @GetMapping("/modules")
    public ResponseEntity<List<String>> listModules() {
        return ResponseEntity.ok(logFileReaderService.listLogModules());
    }

    @GetMapping("/{module}")
    public ResponseEntity<Map<String, Object>> readLogFile(
            @PathVariable String module,
            @RequestParam(required = false) String level,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "100") int limit,
            @RequestParam(defaultValue = "0") int offset) {

        LocalDateTime start = parseTime(startTime);
        LocalDateTime end = parseTime(endTime);

        Map<String, Object> result = logFileReaderService.readLogFile(
                module, level, start, end, search, limit, offset);
        return ResponseEntity.ok(result);
    }

    private LocalDateTime parseTime(String timeStr) {
        if (timeStr == null || timeStr.isEmpty()) return null;
        try {
            return LocalDateTime.parse(timeStr, ISO_FORMATTER);
        } catch (Exception e) {
            return null;
        }
    }
}