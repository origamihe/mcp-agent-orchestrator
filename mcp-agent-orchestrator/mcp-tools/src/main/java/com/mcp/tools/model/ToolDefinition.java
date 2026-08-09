package com.mcp.tools.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolDefinition {

    private String name;
    private String description;
    private String inputSchema;           // JSON Schema
    private List<String> tags;
    private String version;

    // P7: enhanced fields
    private ToolCategory category;        // tool category
    private Set<ToolCapability> capabilities;  // capability semantics (what Planner requests)
    private ToolOwner owner;              // architectural ownership (Capability Domain)
    private boolean enabled;              // whether the tool is enabled
    private long timeoutMs;               // execution timeout in milliseconds
    private List<String> examples;        // usage examples
    @Builder.Default
    private int priority = 0;             // execution priority, higher = more important
    private Instant createdAt;
    private Instant updatedAt;
}