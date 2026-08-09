package com.mcp.common.identity;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 用户身份服务 - 配置驱动的用户身份映射
 * 可以通过配置文件或数据库动态加载
 */
@Slf4j
@Service
public class UserProfileService {

    private final Map<String, UserProfile> userProfiles = new ConcurrentHashMap<>();
    private final Map<String, GroupContext> groupContexts = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        // TODO: 从配置文件或数据库加载
        // 示例：开发者账号
        userProfiles.put("2495444762", UserProfile.builder()
                .userId("2495444762")
                .nickname("Master")
                .role(UserRole.OWNER)
                .relation(UserRelation.OWNER)
                .affinity(100)
                .preferredName("Master")
                .build());

        userProfiles.put("987654321", UserProfile.builder()
                .userId("987654321")
                .nickname("Alice")
                .role(UserRole.ADMIN)
                .relation(UserRelation.FRIEND)
                .affinity(70)
                .build());

        log.info("[UserProfile] Initialized {} user profiles, {} group contexts",
                userProfiles.size(), groupContexts.size());
    }

    public UserProfile getUserProfile(String senderId) {
        return userProfiles.getOrDefault(senderId,
                UserProfile.builder()
                        .userId(senderId)
                        .nickname(senderId)
                        .role(UserRole.MEMBER)
                        .relation(UserRelation.STRANGER)
                        .affinity(50)
                        .build());
    }

    public GroupContext getGroupContext(String groupId) {
        return groupContexts.getOrDefault(groupId,
                GroupContext.builder()
                        .groupId(groupId)
                        .groupName("未知群")
                        .build());
    }

    public void registerUser(UserProfile profile) {
        userProfiles.put(profile.getUserId(), profile);
        log.info("[UserProfile] Registered: {} -> {}", profile.getUserId(), profile.getRole());
    }

    public void registerGroup(GroupContext context) {
        groupContexts.put(context.getGroupId(), context);
        log.info("[UserProfile] Registered group: {} -> {}", context.getGroupId(), context.getGroupName());
    }
}