package com.mcp.tools.tool.read;

import com.mcp.tools.annotation.McpTool;
import com.mcp.tools.index.SymbolEntry;
import com.mcp.tools.index.WorkspaceIndex;
import com.mcp.tools.service.WorkspaceFileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class SymbolSearchToolSet {

    private final WorkspaceIndex workspaceIndex;
    private final WorkspaceFileService fileService;

    @McpTool(
            name = "search_symbol",
            description = "Search for a symbol (class, method, field) by name. Returns matching symbols with file path, line number, and kind. For finding where a class/method is defined.",
            tags = {"code", "search", "symbol", "read"}
    )
    public String searchSymbol(String name) {
        if (name == null || name.isBlank()) {
            return fail("Symbol name must not be empty", "search_symbol");
        }

        List<SymbolEntry> results = workspaceIndex.searchSymbol(name);
        if (results.isEmpty()) {
            return success("search_symbol", "No symbols found for: " + name, "[]");
        }

        StringBuilder sb = new StringBuilder();
        sb.append("{\"ok\":true,\"tool\":\"search_symbol\",\"count\":").append(results.size());
        sb.append(",\"results\":[");
        for (int i = 0; i < results.size(); i++) {
            if (i > 0) sb.append(",");
            SymbolEntry e = results.get(i);
            sb.append("{");
            sb.append("\"name\":\"").append(escapeJson(e.getName())).append("\"");
            sb.append(",\"qualifiedName\":\"").append(escapeJson(e.getQualifiedName())).append("\"");
            sb.append(",\"kind\":\"").append(e.getKind()).append("\"");
            sb.append(",\"file\":\"").append(escapeJson(e.getFilePath())).append("\"");
            sb.append(",\"line\":").append(e.getStartLine());
            sb.append(",\"parentClass\":\"").append(escapeJson(
                    e.getParentClass() != null ? e.getParentClass() : "")).append("\"");
            sb.append(",\"returnType\":\"").append(escapeJson(
                    e.getReturnType() != null ? e.getReturnType() : "")).append("\"");
            sb.append(",\"annotations\":[");
            if (e.getAnnotations() != null) {
                sb.append(e.getAnnotations().stream()
                        .map(a -> "\"" + escapeJson(a) + "\"")
                        .collect(Collectors.joining(",")));
            }
            sb.append("]");
            sb.append(",\"referrerCount\":").append(
                    workspaceIndex.getReferrerCount(e.getQualifiedName()));
            sb.append("}");
        }
        sb.append("]}");

        log.info("search_symbol: '{}' → {} results", name, results.size());
        return sb.toString();
    }

    @McpTool(
            name = "find_references",
            description = "Find all files that reference a given symbol. Returns file paths and line numbers. For understanding call chains and dependencies.",
            tags = {"code", "search", "reference", "read"}
    )
    public String findReferences(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            return fail("Symbol must not be empty", "find_references");
        }

        Set<String> refs = workspaceIndex.findReferences(symbol);
        if (refs.isEmpty()) {
            return success("find_references", "No references found for: " + symbol, "[]");
        }

        StringBuilder sb = new StringBuilder();
        sb.append("{\"ok\":true,\"tool\":\"find_references\",\"symbol\":\"")
                .append(escapeJson(symbol)).append("\"");
        sb.append(",\"count\":").append(refs.size());
        sb.append(",\"files\":[");
        boolean first = true;
        for (String ref : refs) {
            if (!first) sb.append(",");
            first = false;
            sb.append("\"").append(escapeJson(ref)).append("\"");
        }
        sb.append("]}");

        log.info("find_references: '{}' → {} files", symbol, refs.size());
        return sb.toString();
    }

    @McpTool(
            name = "workspace_tree",
            description = "Display the workspace project directory tree structure. Shows the overall project layout. Optional parameter: depth (1-5, default 3).",
            tags = {"workspace", "tree", "read"}
    )
    public String workspaceTree(int depth) {
        if (depth < 1) depth = 1;
        if (depth > 5) depth = 5;

        try {
            Path root = fileService.resolve(".");
            StringBuilder sb = new StringBuilder();
            sb.append(root.getFileName()).append("\n");
            buildTree(root, "", 1, depth, sb);

            String tree = sb.toString();
            log.info("workspace_tree: depth={}, {} lines", depth, tree.lines().count());
            return success("workspace_tree", "Workspace tree generated",
                    "\"" + escapeJson(tree) + "\"");
        } catch (Exception e) {
            log.error("workspace_tree failed", e);
            return fail("Failed to generate tree: " + e.getMessage(), "workspace_tree");
        }
    }

    @McpTool(
            name = "grep",
            description = "Search for a regex pattern in workspace files. Returns matching files with line numbers and content. Most efficient way to find text across the project. Parameters: pattern (regex), path (optional subdirectory filter), maxResults (default 50).",
            tags = {"code", "search", "grep", "read"}
    )
    public String grep(String pattern, String path, int maxResults) {
        if (pattern == null || pattern.isBlank()) {
            return fail("Pattern must not be empty", "grep");
        }
        final int max = maxResults <= 0 ? 50 : maxResults;

        try {
            Pattern regex = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE);
            Path searchRoot = (path != null && !path.isBlank())
                    ? fileService.resolve(path)
                    : fileService.resolve(".");
            List<GrepMatch> matches = new ArrayList<>();

            Files.walkFileTree(searchRoot, EnumSet.noneOf(FileVisitOption.class), 5,
                    new SimpleFileVisitor<>() {
                        @Override
                        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                            if (matches.size() >= max) return FileVisitResult.TERMINATE;
                            String name = file.getFileName().toString();
                            if (!name.endsWith(".java") && !name.endsWith(".xml")
                                    && !name.endsWith(".properties") && !name.endsWith(".yml")
                                    && !name.endsWith(".yaml") && !name.endsWith(".json")) {
                                return FileVisitResult.CONTINUE;
                            }
                            try {
                                List<String> lines = Files.readAllLines(file);
                                for (int i = 0; i < lines.size() && matches.size() < max; i++) {
                                    if (regex.matcher(lines.get(i)).find()) {
                                        matches.add(new GrepMatch(
                                                fileService.resolve(file.toString()).toString(),
                                                i + 1,
                                                lines.get(i).trim()
                                        ));
                                    }
                                }
                            } catch (IOException ignored) {}
                            return FileVisitResult.CONTINUE;
                        }

                        @Override
                        public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                            String dirName = dir.getFileName().toString();
                            if (dirName.startsWith(".") || dirName.equals("target")
                                    || dirName.equals("build") || dirName.equals("node_modules")
                                    || dirName.equals("_external")) {
                                return FileVisitResult.SKIP_SUBTREE;
                            }
                            return FileVisitResult.CONTINUE;
                        }
                    });

            StringBuilder sb = new StringBuilder();
            sb.append("{\"ok\":true,\"tool\":\"grep\",\"pattern\":\"")
                    .append(escapeJson(pattern)).append("\"");
            sb.append(",\"count\":").append(matches.size());
            sb.append(",\"matches\":[");
            for (int i = 0; i < matches.size(); i++) {
                if (i > 0) sb.append(",");
                GrepMatch m = matches.get(i);
                sb.append("{\"file\":\"").append(escapeJson(m.file)).append("\"");
                sb.append(",\"line\":").append(m.line);
                sb.append(",\"content\":\"").append(escapeJson(m.content)).append("\"}");
            }
            sb.append("]}");

            log.info("grep: '{}' → {} matches", pattern, matches.size());
            return sb.toString();
        } catch (PatternSyntaxException e) {
            return fail("Invalid regex pattern: " + e.getMessage(), "grep");
        } catch (Exception e) {
            log.error("grep failed", e);
            return fail("Grep failed: " + e.getMessage(), "grep");
        }
    }

    @McpTool(
            name = "glob",
            description = "Find files matching a glob pattern. Like Unix 'find' with wildcards. Parameters: pattern (glob pattern like '**/*.java' or 'src/**/Tool*.java'), maxResults (default 100).",
            tags = {"file", "search", "glob", "read"}
    )
    public String glob(String pattern, int maxResults) {
        if (pattern == null || pattern.isBlank()) {
            return fail("Pattern must not be empty", "glob");
        }
        final int max = maxResults <= 0 ? 100 : maxResults;

        try {
            Path root = fileService.resolve(".");
            PathMatcher matcher = FileSystems.getDefault()
                    .getPathMatcher("glob:" + pattern.replace('\\', '/'));
            List<String> matched = new ArrayList<>();

            Files.walkFileTree(root, EnumSet.noneOf(FileVisitOption.class), 10,
                    new SimpleFileVisitor<>() {
                        @Override
                        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                            if (matched.size() >= max) return FileVisitResult.TERMINATE;
                            Path relative = root.relativize(file);
                            if (matcher.matches(relative) || matcher.matches(file.getFileName())) {
                                matched.add(relative.toString().replace('\\', '/'));
                            }
                            return FileVisitResult.CONTINUE;
                        }

                        @Override
                        public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                            String dirName = dir.getFileName().toString();
                            if (dirName.startsWith(".") || dirName.equals("target")
                                    || dirName.equals("build") || dirName.equals("node_modules")) {
                                return FileVisitResult.SKIP_SUBTREE;
                            }
                            return FileVisitResult.CONTINUE;
                        }
                    });

            StringBuilder sb = new StringBuilder();
            sb.append("{\"ok\":true,\"tool\":\"glob\",\"pattern\":\"")
                    .append(escapeJson(pattern)).append("\"");
            sb.append(",\"count\":").append(matched.size());
            sb.append(",\"files\":[");
            sb.append(matched.stream()
                    .map(f -> "\"" + escapeJson(f) + "\"")
                    .collect(Collectors.joining(",")));
            sb.append("]}");

            log.info("glob: '{}' → {} files", pattern, matched.size());
            return sb.toString();
        } catch (Exception e) {
            log.error("glob failed", e);
            return fail("Glob failed: " + e.getMessage(), "glob");
        }
    }

    private record GrepMatch(String file, int line, String content) {}

    private String success(String tool, String message, String data) {
        return "{\"ok\":true,\"tool\":\"" + tool + "\",\"message\":\""
                + escapeJson(message) + "\",\"data\":" + data + "}";
    }

    private String fail(String message, String tool) {
        return "{\"ok\":false,\"tool\":\"" + tool + "\",\"error\":\""
                + escapeJson(message) + "\"}";
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private void buildTree(Path dir, String prefix, int currentDepth, int maxDepth,
                           StringBuilder sb) {
        if (currentDepth > maxDepth) return;
        try {
            File[] files = dir.toFile().listFiles();
            if (files == null) return;
            Arrays.sort(files, (a, b) -> {
                if (a.isDirectory() && !b.isDirectory()) return -1;
                if (!a.isDirectory() && b.isDirectory()) return 1;
                return a.getName().compareToIgnoreCase(b.getName());
            });

            for (int i = 0; i < files.length; i++) {
                File f = files[i];
                String name = f.getName();
                if (name.startsWith(".") || name.equals("target") || name.equals("build")
                        || name.equals("node_modules") || name.equals("_external")) {
                    continue;
                }
                boolean isLast = (i == files.length - 1);
                sb.append(prefix).append(isLast ? "└── " : "├── ").append(name);
                if (f.isDirectory()) {
                    sb.append("/");
                }
                sb.append("\n");
                if (f.isDirectory()) {
                    buildTree(f.toPath(), prefix + (isLast ? "    " : "│   "),
                            currentDepth + 1, maxDepth, sb);
                }
            }
        } catch (Exception ignored) {}
    }
}