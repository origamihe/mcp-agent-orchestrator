package com.mcp.core.mapper;

import com.mcp.core.domain.chat.CoreChatMessage;
import com.mcp.core.entity.ChatMessageEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ChatMessageMapper {

    @Mapping(target = "messageId", source = "id")
    CoreChatMessage toDomain(ChatMessageEntity entity);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "chatSession", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    ChatMessageEntity toEntity(CoreChatMessage domain);
}