package com.mcp.common.identity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户身份档案 - 包含角色、关系、昵称
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserProfile {
    private String userId;
    private String nickname;
    private UserRole role;
    private UserRelation relation;
    @Builder.Default
    private int affinity = 50;
    private String preferredName;

    public boolean isOwner() {
        return role == UserRole.OWNER;
    }

    public boolean isAtLeast(UserRole required) {
        return role.isAtLeast(required);
    }

    public String getDisplayName() {
        return preferredName != null && !preferredName.isEmpty() ? preferredName : nickname;
    }

    public String toPromptSnippet() {
        return String.format("当前用户：%s | 权限：%s | 亲密度：%d",
                getDisplayName(), role.name(), affinity);
    }
}