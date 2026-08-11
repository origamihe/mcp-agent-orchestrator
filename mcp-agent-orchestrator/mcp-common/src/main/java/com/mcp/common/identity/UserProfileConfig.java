package com.mcp.common.identity;

import lombok.Data;

/**
 * 用户身份配置属性 — 对应 YAML 中 mcp.identity.users 列表项。
 */
@Data
public class UserProfileConfig {

    private String userId;
    private String nickname;
    private UserRole role;
    private UserRelation relation;
    private Integer affinity;
    private String preferredName;
}