package com.mcp.gateway.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Service
public class LogFileReaderService {

    private static final String DEFAULT_LOG_DIR = "log";

    private static final Pattern LOG_LINE_PATTERN = Pattern.compile(
            "^(\\d{4}-\\d{2}-\\d{2}\\s+\\d{2}:\\d{2}:\\d{2}\\.\\d{3})\\s+(\\S+)\\s+\\[(\\S+)\\]\\s+(\\S+)\\s+-\\s+(.*)$");

    private static final DateTimeFormatter LOG_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    private static final Map<String, String> MODULE_FILE_MAP = Map.of(
            "mcp-agent-orchestrator", "mcp-agent-orchestrator.log",
            "orchestrator", "orchestrator.log",
            "agent", "agent.log",
            "llm", "llm.log",
            "memory", "memory.log",
            "prompt", "prompt.log",
            "performance", "performance.log"
    );

    @Value("${app.log.dir:#{null}}")
    private String configuredLogDir;

    private Path getLogDir() {
        if (configuredLogDir != null && !configuredLogDir.isEmpty()) {
            return Paths.get(configuredLogDir);
        }
        return Paths.get(DEFAULT_LOG_DIR);
    }

    public List<String> listLogModules() {
        List<String> modules = new ArrayList<>();
        Path logDir = getLogDir();
        File dir = logDir.toFile();
        if (!dir.exists() || !dir.isDirectory()) {
            return modules;
        }
        File[] files = dir.listFiles((d, name) -> name.endsWith(".log"));
        if (files != null) {
            for (File f : files) {
                modules.add(f.getName().replace(".log", ""));
            }
        }
        modules.sort(String::compareTo);
        return modules;
    }

    public Map<String, Object> readLogFile(String module, String level,
                                            LocalDateTime startTime, LocalDateTime endTime,
                                            String search, int limit, int offset) {
        String fileName = MODULE_FILE_MAP.getOrDefault(module, module + ".log");
        Path logFile = getLogDir().resolve(fileName);

        Map<String, Object> result = new LinkedHashMap<>();
        List<Map<String, Object>> items = new ArrayList<>();

        if (!Files.exists(logFile)) {
            result.put("items", items);
            result.put("totalCount", 0);
            result.put("error", "Log file not found: " + logFile);
            return result;
        }

        try {
            List<String> allLines = Files.readAllLines(logFile);

            List<Map<String, Object>> parsed = new ArrayList<>();
            for (String line : allLines) {
                Map<String, Object> entry = parseLogLine(line);
                if (entry == null) continue;

                if (level != null && !level.isEmpty() &&
                        !level.equalsIgnoreCase((String) entry.get("level"))) {
                    continue;
                }
                if (startTime != null) {
                    String ts = (String) entry.get("timestamp");
                    if (ts != null && parseTimestamp(ts).isBefore(startTime)) continue;
                }
                if (endTime != null) {
                    String ts = (String) entry.get("timestamp");
                    if (ts != null && parseTimestamp(ts).isAfter(endTime)) continue;
                }
                if (search != null && !search.isEmpty()) {
                    String msg = (String) entry.get("message");
                    if (msg == null || !msg.toLowerCase().contains(search.toLowerCase())) continue;
                }
                parsed.add(entry);
            }

            int totalCount = parsed.size();

            int fromIndex = Math.min(offset, parsed.size());
            int toIndex = Math.min(offset + limit, parsed.size());
            if (fromIndex < toIndex) {
                items = parsed.subList(fromIndex, toIndex);
            }

            result.put("items", items);
            result.put("totalCount", totalCount);
            result.put("module", module);
            result.put("fileName", fileName);
        } catch (IOException e) {
            log.error("Failed to read log file: {}", logFile, e);
            result.put("items", items);
            result.put("totalCount", 0);
            result.put("error", "Failed to read log file: " + e.getMessage());
        }

        return result;
    }

    private Map<String, Object> parseLogLine(String line) {
        Matcher matcher = LOG_LINE_PATTERN.matcher(line);
        if (!matcher.matches()) {
            return null;
        }

        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("timestamp", matcher.group(1));
        entry.put("level", matcher.group(2));
        entry.put("thread", matcher.group(3));
        entry.put("logger", matcher.group(4));
        entry.put("message", matcher.group(5));
        return entry;
    }

    private LocalDateTime parseTimestamp(String ts) {
        try {
            return LocalDateTime.parse(ts, LOG_TIME_FORMATTER);
        } catch (DateTimeParseException e) {
            return LocalDateTime.MIN;
        }
    }
}