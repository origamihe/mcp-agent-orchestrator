package com.mcp.common.identity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 群上下文 - 群的基本信息和主题
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GroupContext {
    private String groupId;
    private String groupName;
    @Builder.Default
    private List<String> topics = new ArrayList<>();
    private String description;

    public String toPromptText() {
        StringBuilder sb = new StringBuilder();
        sb.append("群名称：").append(groupName != null ? groupName : "未知群").append("\n");
        if (topics != null && !topics.isEmpty()) {
            sb.append("群主题：\n");
            for (String topic : topics) {
                sb.append("- ").append(topic).append("\n");
            }
        }
        if (description != null && !description.isEmpty()) {
            sb.append("群描述：").append(description).append("\n");
        }
        return sb.toString();
    }
}