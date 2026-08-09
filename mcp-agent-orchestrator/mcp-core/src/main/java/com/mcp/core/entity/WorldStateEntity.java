package com.mcp.core.entity;

import com.mcp.common.channel.WorldState;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 世界状态持久化实体。
 * 每个会话维护一个独立的世界状态，支持跑团、NPC、长期任务等场景。
 */
@Entity
@Table(name = "world_states", schema = "mcp_agent")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class WorldStateEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", length = 64, nullable = false, unique = true)
    private String sessionId;

    @Column(name = "game_time", length = 200)
    private String currentTime;

    @Column(name = "current_location", length = 500)
    private String currentLocation;

    @Column(name = "weather", length = 200)
    private String weather;

    @Column(name = "atmosphere", length = 500)
    private String atmosphere;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "npcs", columnDefinition = "jsonb")
    private String npcs;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "active_events", columnDefinition = "jsonb")
    private String activeEvents;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "world_rules", columnDefinition = "jsonb")
    private String worldRules;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "recent_happenings", columnDefinition = "jsonb")
    private String recentHappenings;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}