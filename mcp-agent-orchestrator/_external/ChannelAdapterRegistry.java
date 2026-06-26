package com.mcp.gateway.channel;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import java.util.*;

@Slf4j
@Component
public class ChannelAdapterRegistry {
    private final Map<String, ChannelAdapter> adapters = new LinkedHashMap<>();

    public ChannelAdapterRegistry(List<ChannelAdapter> adapterList) {
        for (ChannelAdapter adapter : adapterList) {
            adapters.put(adapter.getChannelType(), adapter);
            log.info("[ChannelRegistry] Registered adapter: {}", adapter.getChannelType());
        }
    }

    public Optional<ChannelAdapter> get(String channelType) {
        return Optional.ofNullable(adapters.get(channelType));
    }

    public Collection<ChannelAdapter> getAll() {
        return adapters.values();
    }
}