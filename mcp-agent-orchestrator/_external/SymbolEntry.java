package com.mcp.tools.index;

import lombok.Builder;
import lombok.Data;

import java.util.Collections;
import java.util.List;
import java.util.Set;

@Data
@Builder
public class SymbolEntry {

    private String name;
    private String qualifiedName;
    private SymbolKind kind;
    private String filePath;
    private int startLine;
    private int endLine;
    private String parentClass;
    private String returnType;

    @Builder.Default
    private List<String> paramTypes = Collections.emptyList();

    @Builder.Default
    private List<String> annotations = Collections.emptyList();

    @Builder.Default
    private Set<String> imports = Collections.emptySet();

    public String displayName() {
        if (kind == SymbolKind.METHOD || kind == SymbolKind.CONSTRUCTOR) {
            String params = paramTypes.isEmpty() ? "" : String.join(", ", paramTypes);
            return name + "(" + params + ")";
        }
        return name;
    }

    public String location() {
        return filePath + ":" + startLine;
    }
}