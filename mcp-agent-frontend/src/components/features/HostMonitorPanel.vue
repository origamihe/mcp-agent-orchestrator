<template>
    <div class="host-monitor-panel">
        <div class="panel-header">
            <h2>宿主管理</h2>
            <button class="refresh-btn" @click="refresh()">
                <ArrowPathIcon class="refresh-icon" />
                刷新
            </button>
        </div>

        <div class="host-grid">
            <div
                v-for="host in hosts"
                :key="host.channel"
                :class="['host-card', { offline: !host.enabled }]"
            >
                <div class="host-card-header">
                    <div class="host-icon" :style="{ background: hostColor(host.channel) }">
                        <component :is="hostIconComponent(host.channel)" class="icon" />
                    </div>
                    <div class="host-meta">
                        <span class="host-name">{{ hostName(host.channel) }}</span>
                        <span class="host-type">{{ hostType(host.channel) }}</span>
                    </div>
                    <span :class="['status-badge', host.enabled ? 'active' : 'inactive']">
                        {{ host.enabled ? '运行中' : '已停止' }}
                    </span>
                </div>

                <div class="host-details" v-if="host.enabled">
                    <div class="detail-row" v-for="(val, key) in hostDetails(host)" :key="key">
                        <span class="detail-key">{{ key }}</span>
                        <span class="detail-val">{{ val }}</span>
                    </div>
                </div>

                <div class="host-card-footer">
                    <button
                        :class="['host-btn', host.enabled ? 'stop' : 'start']"
                        @click="toggleHost(host.channel)"
                    >
                        {{ host.enabled ? '停止' : '启动' }}
                    </button>
                    <button class="host-btn config" @click="$emit('navigate', 'settings')">
                        配置
                    </button>
                </div>
            </div>
        </div>
    </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import {
    ArrowPathIcon,
    ChatBubbleLeftRightIcon,
    ComputerDesktopIcon,
    CodeBracketIcon,
    PaperAirplaneIcon,
    GlobeAltIcon,
} from '@heroicons/vue/24/outline'
import type { ChannelStatus } from '@/types/agent'

const props = defineProps<{
    channels: ChannelStatus[]
}>()

const emit = defineEmits<{
    (e: 'navigate', feature: string): void
    (e: 'refresh'): void
}>()

const hosts = computed(() => props.channels)

function hostName(channel: string): string {
    const names: Record<string, string> = {
        qq: 'QQ Bot',
        desktop: 'Desktop Host',
        ide: 'IDE Host',
        telegram: 'Telegram Bot',
        discord: 'Discord Bot',
    }
    return names[channel] || channel
}

function hostType(channel: string): string {
    const types: Record<string, string> = {
        qq: 'OneBot v11 · NapCat',
        desktop: 'WebSocket 长连接',
        ide: 'WebSocket 长连接',
        telegram: 'Webhook',
        discord: 'Webhook',
    }
    return types[channel] || channel
}

function hostColor(channel: string): string {
    const colors: Record<string, string> = {
        qq: 'rgba(18, 183, 245, 0.1)',
        desktop: 'rgba(102, 126, 234, 0.1)',
        ide: 'rgba(118, 75, 162, 0.1)',
        telegram: 'rgba(39, 174, 96, 0.1)',
        discord: 'rgba(114, 137, 218, 0.1)',
    }
    return colors[channel] || 'rgba(0,0,0,0.05)'
}

function hostIconComponent(channel: string) {
    const icons: Record<string, any> = {
        qq: ChatBubbleLeftRightIcon,
        desktop: ComputerDesktopIcon,
        ide: CodeBracketIcon,
        telegram: PaperAirplaneIcon,
        discord: GlobeAltIcon,
    }
    return icons[channel] || ComputerDesktopIcon
}

function hostDetails(host: ChannelStatus): Record<string, string> {
    const details: Record<string, string> = {}
    for (const [key, val] of Object.entries(host)) {
        if (['channel', 'enabled'].includes(key)) continue
        if (val !== null && val !== undefined) {
            details[key] = String(val)
        }
    }
    return details
}

function toggleHost(channel: string) {
    console.log('[HostMonitor] Toggle:', channel)
}

function refresh() {
    emit('refresh')
}
</script>

<style scoped>
.host-monitor-panel {
    padding: 32px;
    max-width: 1000px;
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

.refresh-btn {
    display: flex;
    align-items: center;
    gap: 6px;
    padding: 8px 18px;
    background: rgba(102, 126, 234, 0.08);
    border: 1px solid rgba(102, 126, 234, 0.2);
    border-radius: 10px;
    color: #667eea;
    font-size: 13px;
    font-weight: 500;
    cursor: pointer;
    transition: all 0.2s;
}

.refresh-btn:hover {
    background: rgba(102, 126, 234, 0.15);
}

.refresh-icon {
    width: 16px;
    height: 16px;
}

.host-grid {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
    gap: 20px;
}

.host-card {
    background: rgba(255,255,255,0.7);
    backdrop-filter: blur(20px);
    border-radius: 16px;
    padding: 24px;
    border: 1px solid rgba(255,255,255,0.8);
    box-shadow: 0 4px 16px rgba(0,0,0,0.04);
    transition: all 0.3s;
}

.host-card.offline {
    opacity: 0.6;
}

.host-card-header {
    display: flex;
    align-items: center;
    gap: 14px;
    margin-bottom: 16px;
}

.host-icon {
    width: 48px;
    height: 48px;
    border-radius: 14px;
    display: flex;
    align-items: center;
    justify-content: center;
}

.host-icon .icon {
    width: 24px;
    height: 24px;
    color: #667eea;
}

.host-meta {
    flex: 1;
    display: flex;
    flex-direction: column;
}

.host-name {
    font-size: 16px;
    font-weight: 600;
    color: var(--color-text);
}

.host-type {
    font-size: 12px;
    color: var(--color-text-secondary);
}

.status-badge {
    font-size: 11px;
    padding: 4px 12px;
    border-radius: 10px;
    font-weight: 500;
}

.status-badge.active {
    background: rgba(39, 174, 96, 0.1);
    color: #27ae60;
}

.status-badge.inactive {
    background: rgba(0,0,0,0.06);
    color: #999;
}

.host-details {
    background: rgba(0,0,0,0.02);
    border-radius: 10px;
    padding: 12px 16px;
    margin-bottom: 16px;
}

.detail-row {
    display: flex;
    justify-content: space-between;
    padding: 6px 0;
    font-size: 13px;
}

.detail-key {
    color: var(--color-text-secondary);
}

.detail-val {
    color: var(--color-text);
    font-weight: 500;
    max-width: 180px;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
}

.host-card-footer {
    display: flex;
    gap: 10px;
}

.host-btn {
    flex: 1;
    padding: 10px;
    border-radius: 10px;
    font-size: 13px;
    font-weight: 500;
    cursor: pointer;
    border: none;
    transition: all 0.2s;
}

.host-btn.start {
    background: rgba(39, 174, 96, 0.1);
    color: #27ae60;
}

.host-btn.start:hover {
    background: rgba(39, 174, 96, 0.2);
}

.host-btn.stop {
    background: rgba(231, 76, 60, 0.08);
    color: #e74c3c;
}

.host-btn.stop:hover {
    background: rgba(231, 76, 60, 0.15);
}

.host-btn.config {
    background: rgba(0,0,0,0.04);
    color: #666;
}

.host-btn.config:hover {
    background: rgba(0,0,0,0.08);
}
</style>