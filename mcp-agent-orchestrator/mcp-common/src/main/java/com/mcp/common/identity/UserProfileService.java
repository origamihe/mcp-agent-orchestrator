package com.mcp.common.identity;

import jakarta.annotation.PostConstruct;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 用户身份服务 - 配置驱动的用户身份映射。
 * 支持通过 application.yml 的 mcp.identity.users 配置用户列表。
 *
 * 配置示例：
 * <pre>
 * mcp:
 *   identity:
 *     users:
 *       - userId: "2495444762"
 *         nickname: "Master"
 *         role: OWNER
 *         relation: OWNER
 *         affinity: 100
 *         preferredName: "Master"
 * </pre>
 */
@Slf4j
@Service
@ConfigurationProperties(prefix = "mcp.identity")
public class UserProfileService {

    private final Map<String, UserProfile> userProfiles = new ConcurrentHashMap<>();
    private final Map<String, GroupContext> groupContexts = new ConcurrentHashMap<>();

    @Setter
    private List<UserProfileConfig> users = new ArrayList<>();

    @PostConstruct
    public void init() {
        if (users != null) {
            for (UserProfileConfig cfg : users) {
                if (cfg.getUserId() == null || cfg.getUserId().isBlank()) {
                    log.warn("[UserProfile] Skipping user config with empty userId");
                    continue;
                }
                UserProfile profile = UserProfile.builder()
                        .userId(cfg.getUserId())
                        .nickname(cfg.getNickname() != null ? cfg.getNickname() : cfg.getUserId())
                        .role(cfg.getRole() != null ? cfg.getRole() : UserRole.MEMBER)
                        .relation(cfg.getRelation() != null ? cfg.getRelation() : UserRelation.STRANGER)
                        .affinity(cfg.getAffinity() != null ? cfg.getAffinity() : 50)
                        .preferredName(cfg.getPreferredName())
                        .build();
                userProfiles.put(cfg.getUserId(), profile);
                log.info("[UserProfile] Loaded from config: {} -> role={}, relation={}, affinity={}",
                        cfg.getUserId(), profile.getRole(), profile.getRelation(), profile.getAffinity());
            }
        }

        if (userProfiles.isEmpty()) {
            log.warn("[UserProfile] No user profiles configured via mcp.identity.users — all users will be treated as MEMBER/STRANGER");
        }

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