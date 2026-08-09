package com.mcp.tools.registry;

import com.mcp.tools.model.ToolDefinition;
import com.mcp.tools.model.ToolQuery;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * CapabilityResolver 的默认实现。
 * 当前直接委托给 ToolRegistry，未来可在此层加入 Priority、Stats、Skill 等决策逻辑。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultCapabilityResolver implements CapabilityResolver {

    private final ToolRegistry registry;

    @Override
    public List<ToolDefinition> resolve(ToolQuery query) {
        List<ToolDefinition> result = registry.query(query);
        log.debug("[CapabilityResolver] Query: owner={}, capability={}, category={}, enabled={} → {} results",
                query.getOwner(), query.getCapability(), query.getCategory(),
                query.getEnabled(), result.size());
        return result;
    }
}