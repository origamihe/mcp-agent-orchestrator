package com.mcp.tools.index;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ReferenceGraph {

    private final Map<String, Set<String>> referrers = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> references = new ConcurrentHashMap<>();

    public void addReference(String fromSymbol, String toSymbol) {
        referrers.computeIfAbsent(toSymbol, k -> ConcurrentHashMap.newKeySet()).add(fromSymbol);
        references.computeIfAbsent(fromSymbol, k -> ConcurrentHashMap.newKeySet()).add(toSymbol);
    }

    public Set<String> getReferrers(String symbol) {
        return referrers.getOrDefault(symbol, Collections.emptySet());
    }

    public Set<String> getReferences(String symbol) {
        return references.getOrDefault(symbol, Collections.emptySet());
    }

    public int getReferrerCount(String symbol) {
        return getReferrers(symbol).size();
    }

    public int getReferenceCount(String symbol) {
        return getReferences(symbol).size();
    }

    public void clear() {
        referrers.clear();
        references.clear();
    }

    public int size() {
        return referrers.size();
    }
}