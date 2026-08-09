package com.mcp.core.context.provider;

import com.mcp.common.identity.GroupContext;
import com.mcp.common.identity.UserProfile;
import com.mcp.common.identity.UserRelation;
import com.mcp.core.context.BuildContext;
import com.mcp.core.context.ContextProvider;
import com.mcp.core.context.PromptContext;
import org.springframework.stereotype.Component;

/**
 * 关系上下文提供者 — 填充用户层（userProfile）和群组层（groupContext）。
 */
@Component
public class RelationshipContextProvider implements ContextProvider {

    @Override
    public void collect(PromptContext.PromptContextBuilder builder, BuildContext ctx) {
        // GROUP 层
        GroupContext groupContext = ctx.groupContext();
        if (groupContext != null) {
            builder.groupContext("【当前群信息】\n" + groupContext.toPromptText());
        }

        // USER 层
        UserProfile userProfile = ctx.userProfile();
        if (userProfile != null) {
            builder.userProfile(buildUserProfilePrompt(userProfile));
        }
    }

    private String buildUserProfilePrompt(UserProfile userProfile) {
        StringBuilder sb = new StringBuilder();
        sb.append("【当前用户信息】\n");
        sb.append("用户ID: ").append(userProfile.getUserId()).append("\n");
        sb.append("昵称: ").append(userProfile.getDisplayName()).append("\n");
        sb.append("角色: ").append(userProfile.getRole()).append("\n");
        sb.append("关系: ").append(userProfile.getRelation()).append("\n");
        sb.append("\n");

        sb.append("【权限规则】\n");
        sb.append(userProfile.getUserId()).append(" -> ").append(userProfile.getRole()).append("\n");
        if (userProfile.isOwner()) {
            sb.append("OWNER 拥有最高权限，允许：修改人格配置、管理记忆、管理Agent。\n");
        } else {
            sb.append("MEMBER 仅允许：聊天、提供偏好。\n");
        }
        sb.append("\n");

        sb.append("【关系规则】\n");
        UserRelation relation = userProfile.getRelation();
        if (relation != null) {
            switch (relation) {
                case OWNER -> sb.append("这是你的 Master，态度可以亲近但保持克制。\n");
                case FRIEND -> sb.append("这是你的朋友，态度自然友好。\n");
                case MEMBER -> sb.append("这是群成员，保持礼貌但不过度热情。\n");
                case STRANGER -> sb.append("这是陌生人，保持基本礼貌。\n");
            }
        } else {
            sb.append("这是陌生人，保持基本礼貌。\n");
        }
        return sb.toString();
    }
}