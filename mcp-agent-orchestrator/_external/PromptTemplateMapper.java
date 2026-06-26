package com.mcp.core.mapper;

import com.mcp.core.domain.prompt.PromptTemplate;
import com.mcp.core.entity.PromptTemplateEntity;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface PromptTemplateMapper {

    PromptTemplate toDomain(PromptTemplateEntity entity);

    PromptTemplateEntity toEntity(PromptTemplate domain);

    void updateEntity(@MappingTarget PromptTemplateEntity entity, PromptTemplate domain);
}