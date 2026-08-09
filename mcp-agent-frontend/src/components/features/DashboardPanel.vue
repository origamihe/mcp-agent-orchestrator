<template>
    <div class="dashboard-panel">
        <div class="panel-header">
            <h2>系统概览</h2>
            <span class="uptime" v-if="overview.uptime">运行时间: {{ overview.uptime }}</span>
        </div>

        <div class="stats-grid">
            <div class="stat-card">
                <div class="stat-icon" style="background: rgba(102, 126, 234, 0.1); color: #667eea;">
                    <FolderOpenIcon class="icon" />
                </div>
                <div class="stat-info">
                    <span class="stat-value">{{ overview.workspaces }}</span>
                    <span class="stat-label">活跃工作空间</span>
                </div>
            </div>
            <div class="stat-card">
                <div class="stat-icon" style="background: rgba(118, 75, 162, 0.1); color: #764ba2;">
                    <LinkIcon class="icon" />
                </div>
                <div class="stat-info">
                    <span class="stat-value">{{ activeChannels }}</span>
                    <span class="stat-label">已连接宿主</span>
                </div>
            </div>
            <div class="stat-card">
                <div class="stat-icon" style="background: rgba(39, 174, 96, 0.1); color: #27ae60;">
                    <CheckCircleIcon class="icon" />
                </div>
                <div class="stat-info">
                    <span class="stat-value">{{ healthyChannels }}</span>
                    <span class="stat-label">健康通道</span>
                </div>
            </div>
            <div class="stat-card">
                <div class="stat-icon" style="background: rgba(243, 156, 18, 0.1); color: #f39c12;">
                    <ExclamationTriangleIcon class="icon" />
                </div>
                <div class="stat-info">
                    <span class="stat-value">{{ warnChannels }}</span>
                    <span class="stat-label">需要关注</span>
                </div>
            </div>
        </div>

        <div class="section">
            <h3>宿主状态</h3>
            <div class="channel-list">
                <div
                    v-for="ch in overview.channels"
                    :key="ch.channel"
                    class="channel-row"
                >
                    <span :class="['status-dot', ch.enabled ? 'online' : 'offline']"></span>
                    <span class="channel-name">{{ ch.channel.toUpperCase() }}</span>
                    <span class="channel-type">{{ channelTypeLabel(ch.channel) }}</span>
                    <span :class="['channel-badge', ch.enabled ? 'enabled' : 'disabled']">
                        {{ ch.enabled ? '已启用' : '已禁用' }}
                    </span>
                </div>
            </div>
        </div>

        <div class="section">
            <h3>快速操作</h3>
            <div class="quick-actions">
                <button class="action-btn" @click="$emit('navigate', 'workspaces')">
                    <FolderOpenIcon class="btn-icon" />
                    管理工作空间
                </button>
                <button class="action-btn" @click="$emit('navigate', 'hosts')">
                    <ComputerDesktopIcon class="btn-icon" />
                    查看宿主
                </button>
                <button class="action-btn" @click="$emit('navigate', 'skills')">
                    <CpuChipIcon class="btn-icon" />
                    技能配置
                </button>
                <button class="action-btn" @click="$emit('navigate', 'settings')">
                    <Cog6ToothIcon class="btn-icon" />
                    系统设置
                </button>
            </div>
        </div>
    </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import {
    FolderOpenIcon,
    LinkIcon,
    CheckCircleIcon,
    ExclamationTriangleIcon,
    ComputerDesktopIcon,
    CpuChipIcon,
    Cog6ToothIcon,
} from '@heroicons/vue/24/outline'
import type { ChannelStatus } from '@/types/agent'

const props = defineProps<{
    channels: ChannelStatus[]
    workspaces: number
    uptime: string
}>()

defineEmits<{
    (e: 'navigate', feature: string): void
}>()

const overview = computed(() => ({
    channels: props.channels,
    workspaces: props.workspaces,
    activeSessions: props.channels.filter(c => c.enabled).length,
    uptime: props.uptime,
}))

const activeChannels = computed(() =>
    props.channels.filter(c => c.enabled).length
)

const healthyChannels = computed(() =>
    props.channels.filter(c => c.enabled).length
)

const warnChannels = computed(() =>
    props.channels.filter(c => !c.enabled).length
)

function channelTypeLabel(channel: string): string {
    const labels: Record<string, string> = {
        qq: 'QQ 群聊/私聊',
        desktop: '桌面助手',
        ide: 'IDE 开发',
        telegram: 'Telegram',
        discord: 'Discord',
    }
    return labels[channel] || channel
}
</script>

<style scoped>
.dashboard-panel {
    padding: 32px;
    max-width: 900px;
}

.panel-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: 28px;
}

.panel-header h2 {
    font-size: 24px;
    font-weight: 700;
    color: var(--color-text);
}

.uptime {
    font-size: 13px;
    color: var(--color-text-secondary);
    background: rgba(0,0,0,0.04);
    padding: 6px 14px;
    border-radius: 20px;
}

.stats-grid {
    display: grid;
    grid-template-columns: repeat(4, 1fr);
    gap: 16px;
    margin-bottom: 32px;
}

.stat-card {
    background: rgba(255,255,255,0.7);
    backdrop-filter: blur(20px);
    border-radius: 16px;
    padding: 20px;
    display: flex;
    align-items: center;
    gap: 14px;
    border: 1px solid rgba(255,255,255,0.8);
    box-shadow: 0 4px 16px rgba(0,0,0,0.04);
}

.stat-icon {
    width: 44px;
    height: 44px;
    border-radius: 12px;
    display: flex;
    align-items: center;
    justify-content: center;
}

.stat-icon .icon {
    width: 22px;
    height: 22px;
}

.stat-info {
    display: flex;
    flex-direction: column;
}

.stat-value {
    font-size: 28px;
    font-weight: 700;
    color: var(--color-text);
}

.stat-label {
    font-size: 12px;
    color: var(--color-text-secondary);
}

.section {
    background: rgba(255,255,255,0.7);
    backdrop-filter: blur(20px);
    border-radius: 16px;
    padding: 24px;
    margin-bottom: 20px;
    border: 1px solid rgba(255,255,255,0.8);
    box-shadow: 0 4px 16px rgba(0,0,0,0.04);
}

.section h3 {
    font-size: 16px;
    font-weight: 600;
    margin-bottom: 16px;
    color: var(--color-text);
}

.channel-list {
    display: flex;
    flex-direction: column;
    gap: 10px;
}

.channel-row {
    display: flex;
    align-items: center;
    gap: 12px;
    padding: 10px 14px;
    background: rgba(0,0,0,0.02);
    border-radius: 10px;
}

.status-dot {
    width: 10px;
    height: 10px;
    border-radius: 50%;
    flex-shrink: 0;
}

.status-dot.online {
    background: #27ae60;
    box-shadow: 0 0 8px rgba(39, 174, 96, 0.4);
}

.status-dot.offline {
    background: #ccc;
}

.channel-name {
    font-weight: 600;
    font-size: 14px;
    min-width: 80px;
}

.channel-type {
    font-size: 13px;
    color: var(--color-text-secondary);
    flex: 1;
}

.channel-badge {
    font-size: 11px;
    padding: 3px 10px;
    border-radius: 10px;
    font-weight: 500;
}

.channel-badge.enabled {
    background: rgba(39, 174, 96, 0.1);
    color: #27ae60;
}

.channel-badge.disabled {
    background: rgba(0,0,0,0.06);
    color: #999;
}

.quick-actions {
    display: grid;
    grid-template-columns: repeat(2, 1fr);
    gap: 12px;
}

.action-btn {
    display: flex;
    align-items: center;
    gap: 10px;
    padding: 14px 20px;
    background: rgba(102, 126, 234, 0.06);
    border: 1px solid rgba(102, 126, 234, 0.15);
    border-radius: 12px;
    font-size: 14px;
    font-weight: 500;
    color: #667eea;
    cursor: pointer;
    transition: all 0.2s;
}

.action-btn:hover {
    background: rgba(102, 126, 234, 0.12);
    border-color: rgba(102, 126, 234, 0.3);
    transform: translateY(-1px);
}

.btn-icon {
    width: 20px;
    height: 20px;
}
</style>