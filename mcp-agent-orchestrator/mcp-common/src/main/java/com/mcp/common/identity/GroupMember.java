package com.mcp.common.identity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 群成员信息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GroupMember {
    private String userId;
    private String displayName;
    private String role;
}