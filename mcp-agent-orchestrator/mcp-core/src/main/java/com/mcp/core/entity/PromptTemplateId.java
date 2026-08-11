package com.mcp.core.entity;

import java.io.Serializable;
import java.util.Objects;

/**
 * PromptTemplate 复合主键 — (name, variant, version)
 */
public class PromptTemplateId implements Serializable {

    private String name;
    private String variant;
    private Integer version;

    public PromptTemplateId() {}

    public PromptTemplateId(String name, String variant, Integer version) {
        this.name = name;
        this.variant = variant;
        this.version = version;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PromptTemplateId that)) return false;
        return Objects.equals(name, that.name)
                && Objects.equals(variant, that.variant)
                && Objects.equals(version, that.version);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, variant, version);
    }
}