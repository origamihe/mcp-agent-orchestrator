package com.mcp.core.mapper;

import com.mcp.core.domain.llm.LlmModelConfig;
import com.mcp.core.entity.LlmConfigEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.Named;

import java.util.Map;

@Mapper(componentModel = "spring")
public interface LlmConfigMapper {

    @Mapping(target = "parameters", source = "parameters", qualifiedByName = "stringToMap")
    LlmModelConfig toDomain(LlmConfigEntity entity);

    @Mapping(target = "parameters", source = "parameters", qualifiedByName = "mapToString")
    LlmConfigEntity toEntity(LlmModelConfig domain);

    @Mapping(target = "parameters", source = "parameters", qualifiedByName = "mapToString")
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    void updateEntity(@MappingTarget LlmConfigEntity entity, LlmModelConfig domain);

    @Named("stringToMap")
    default Map<String, Object> stringToMap(String value) {
        if (value == null || value.trim().isEmpty()) {
            return Map.of();
        }
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper =
                    new com.fasterxml.jackson.databind.ObjectMapper();
            return mapper.readValue(value, Map.class);
        } catch (Exception e) {
            return Map.of();
        }
    }

    @Named("mapToString")
    default String mapToString(Map<String, Object> map) {
        if (map == null || map.isEmpty()) {
            return null;
        }
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper =
                    new com.fasterxml.jackson.databind.ObjectMapper();
            return mapper.writeValueAsString(map);
        } catch (Exception e) {
            return null;
        }
    }
}