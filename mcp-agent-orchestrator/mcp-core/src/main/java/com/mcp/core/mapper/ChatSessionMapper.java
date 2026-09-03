package com.mcp.core.mapper;

import com.mcp.core.domain.chat.ChatSession;
import com.mcp.core.entity.ChatSessionEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", uses = ChatMessageMapper.class)
public interface ChatSessionMapper {

    @Mapping(target = "messages", source = "messages")
    ChatSession toDomain(ChatSessionEntity entity);

    @Mapping(target = "messages", ignore = true)
    ChatSessionEntity toEntity(ChatSession domain);
}