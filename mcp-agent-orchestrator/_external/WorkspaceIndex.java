package com.mcp.tools.index;

import com.mcp.tools.service.WorkspaceFileService;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Component
public class WorkspaceIndex {

    private final WorkspaceFileService fileService;
    private final JavaSourceIndexer indexer;

    private final Map<String, List<SymbolEntry>> byName = new ConcurrentHashMap<>();
    private final Map<String, SymbolEntry> byQualifiedName = new ConcurrentHashMap<>();
    private final Map<String, List<SymbolEntry>> byFile = new ConcurrentHashMap<>();
    private final ReferenceGraph referenceGraph = new ReferenceGraph();
    private final Map<String, String> fileContentCache = new ConcurrentHashMap<>();

    private volatile boolean initialized = false;

    public WorkspaceIndex(WorkspaceFileService fileService, JavaSourceIndexer indexer) {
        this.fileService = fileService;
        this.indexer = indexer;
    }

    @PostConstruct
    public void init() {
        log.info("[WorkspaceIndex] Starting full workspace scan...");
        long start = System.currentTimeMillis();
        try {
            Path root = fileService.resolve(".");
            scanDirectory(root);
            initialized = true;
            long elapsed = System.currentTimeMillis() - start;
            log.info("[WorkspaceIndex] Scan complete. {} symbols indexed, {} references, {}ms",
                    byName.values().stream().mapToInt(List::size).sum(),
                    referenceGraph.size(), elapsed);
        } catch (Exception e) {
            log.error("[WorkspaceIndex] Failed to initialize", e);
        }
    }

    private void scanDirectory(Path root) throws IOException {
        Files.walkFileTree(root, EnumSet.noneOf(FileVisitOption.class), Integer.MAX_VALUE,
                new SimpleFileVisitor<>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                        String name = file.getFileName().toString();
                        if (name.endsWith(".java") && !isExcluded(file)) {
                            indexFile(file);
                        }
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
    }

    private boolean isExcluded(Path file) {
        String path = file.toString().replace('\\', '/');
        return path.contains("/test/") || path.contains("/target/")
                || path.contains("/build/") || path.contains("/_external/");
    }

    public void indexFile(Path file) {
        try {
            String relativePath = fileService.resolve(file.toString()).toString();
            String content = Files.readString(file);
            fileContentCache.put(relativePath, content);

            JavaSourceIndexer.ParseResult result = indexer.parse(file);
            if (result.symbols().isEmpty()) return;

            String filePath = relativePath;

            synchronized (this) {
                byFile.remove(filePath);
                for (SymbolEntry entry : result.symbols()) {
                    byName.computeIfAbsent(entry.getName(), k -> new ArrayList<>()).add(entry);
                    byQualifiedName.put(entry.getQualifiedName(), entry);
                    byFile.computeIfAbsent(filePath, k -> new ArrayList<>()).add(entry);
                }
                for (String ref : result.references()) {
                    referenceGraph.addReference(filePath, ref);
                }
            }
        } catch (IOException e) {
            log.warn("[WorkspaceIndex] Cannot index file: {} - {}", file, e.getMessage());
        }
    }

    public void reindexFile(Path file) {
        indexFile(file);
    }

    public List<SymbolEntry> searchSymbol(String name) {
        if (!initialized) return Collections.emptyList();
        List<SymbolEntry> result = new ArrayList<>();

        List<SymbolEntry> exact = byName.getOrDefault(name, Collections.emptyList());
        result.addAll(exact);

        String lower = name.toLowerCase();
        for (var entry : byName.entrySet()) {
            if (entry.getKey().toLowerCase().contains(lower) && !entry.getKey().equals(name)) {
                result.addAll(entry.getValue());
            }
        }
        return result;
    }

    public Optional<SymbolEntry> findByQualifiedName(String qualifiedName) {
        return Optional.ofNullable(byQualifiedName.get(qualifiedName));
    }

    public List<SymbolEntry> getSymbolsInFile(String filePath) {
        return byFile.getOrDefault(filePath, Collections.emptyList());
    }

    public Set<String> findReferences(String symbol) {
        return referenceGraph.getReferrers(symbol);
    }

    public Set<String> findReferencesByFile(String filePath) {
        return referenceGraph.getReferences(filePath);
    }

    public int getReferrerCount(String symbol) {
        return referenceGraph.getReferrerCount(symbol);
    }

    public String getFileContent(String filePath) {
        return fileContentCache.getOrDefault(filePath, "");
    }

    public int getSymbolCount() {
        return byName.values().stream().mapToInt(List::size).sum();
    }

    public int getReferenceCount() {
        return referenceGraph.size();
    }

    public boolean isInitialized() {
        return initialized;
    }

    public Map<String, Integer> getStatistics() {
        Map<String, Integer> stats = new LinkedHashMap<>();
        stats.put("totalSymbols", getSymbolCount());
        stats.put("totalReferences", getReferenceCount());
        stats.put("indexedFiles", byFile.size());
        stats.put("cachedFiles", fileContentCache.size());

        Map<SymbolKind, Long> byKind = byName.values().stream()
                .flatMap(List::stream)
                .collect(Collectors.groupingBy(SymbolEntry::getKind, Collectors.counting()));
        byKind.forEach((k, v) -> stats.put(k.name().toLowerCase() + "s", v.intValue()));

        return stats;
    }
}