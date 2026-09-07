<template>
    <div class="page">
        <div class="page-header">
            <h2>宿主管理</h2>
            <span class="subtitle">MCP 宿主连接状态、能力与项目</span>
            <button class="btn-refresh" @click="hostStore.fetchHosts()" :disabled="hostStore.isLoading">
                {{ hostStore.isLoading ? '刷新中...' : '刷新' }}
            </button>
        </div>

        <div v-if="hostStore.isLoading" class="loading">加载中...</div>
        <div v-else-if="hostStore.hosts.length === 0" class="empty-state">
            <ComputerDesktopIcon class="empty-icon" />
            <p>暂无宿主连接</p>
        </div>
        <div class="host-grid" v-else>
            <div
                v-for="host in hostStore.hosts"
                :key="host.id"
                :class="['host-card', { expanded: selectedHost === host.id }]"
                @click="selectHost(host.id)"
            >
                <div class="host-header">
                    <div class="host-identity">
                        <span class="host-name">{{ host.name }}</span>
                        <span :class="['channel-badge', host.channelType]">{{ channelLabel(host.channelType) }}</span>
                    </div>
                    <div class="host-status-group">
                        <span :class="['status-dot', host.connected ? 'online' : 'offline']"></span>
                        <span class="status-text">{{ host.connected ? '已连接' : '已断开' }}</span>
                    </div>
                </div>
                <div class="host-meta">
                    <span v-if="host.lastActiveAt">上次活跃: {{ formatDate(host.lastActiveAt) }}</span>
                    <span>{{ host.capabilities.length }} 能力</span>
                    <span>{{ host.projects.length }} 项目</span>
                </div>
                <div v-if="selectedHost === host.id && hostStore.currentHost" class="host-detail">
                    <div class="detail-section" v-if="hostStore.currentHost.capabilities.length">
                        <h5>Capabilities</h5>
                        <div class="capability-list">
                            <div v-for="cap in hostStore.currentHost.capabilities" :key="cap.name" class="capability-item">
                                <span class="cap-name">{{ cap.name }}</span>
                                <span :class="['risk-badge', `risk-${cap.riskLevel.toLowerCase()}`]">{{ cap.riskLevel }}</span>
                                <span class="cap-desc">{{ cap.description }}</span>
                            </div>
                        </div>
                    </div>
                    <div class="detail-section" v-if="hostStore.currentHost.projects.length">
                        <h5>Projects</h5>
                        <div class="project-list">
                            <div v-for="proj in hostStore.currentHost.projects" :key="proj.projectId" class="project-item">
                                <span class="project-name">{{ proj.name }}</span>
                                <span class="project-path">{{ proj.path }}</span>
                                <span v-if="proj.activeWorkspace" class="active-badge">Active</span>
                            </div>
                        </div>
                    </div>
                </div>
            </div>
        </div>
    </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ComputerDesktopIcon } from '@heroicons/vue/24/outline'
import { useHostStore } from '@/stores/hostStore'

const hostStore = useHostStore()
const selectedHost = ref<string | null>(null)

function channelLabel(type: string): string {
    const labels: Record<string, string> = {
        qq: 'QQ', desktop: 'Desktop', ide: 'IDE', telegram: 'Telegram', discord: 'Discord',
    }
    return labels[type] || type
}

function selectHost(id: string) {
    selectedHost.value = selectedHost.value === id ? null : id
    if (selectedHost.value) {
        hostStore.fetchHostById(id)
    }
}

function formatDate(dateStr: string): string {
    if (!dateStr) return ''
    return new Date(dateStr).toLocaleString('zh-CN')
}

onMounted(() => {
    hostStore.fetchHosts()
})
</script>

<style scoped>
.page-header {
    display: flex;
    align-items: center;
    flex-wrap: wrap;
    gap: 12px;
    margin-bottom: 24px;
}

.page-header h2 {
    font-size: 28px;
    font-weight: 650;
    letter-spacing: -0.3px;
}

.subtitle {
    font-size: 13px;
    color: var(--color-text-secondary);
}

.btn-refresh {
    margin-left: auto;
    padding: 8px 18px;
    border-radius: var(--radius-sm);
    border: 1px solid var(--color-border);
    background: var(--color-surface);
    color: var(--color-text);
    cursor: pointer;
    font-size: 13px;
    font-weight: 500;
}

.btn-refresh:hover {
    background: var(--accent-bg);
    border-color: var(--color-accent);
    color: var(--color-accent);
    box-shadow: none;
}

.btn-refresh:disabled { opacity: 0.5; }

.loading, .empty-state {
    padding: 60px 0;
    text-align: center;
    color: var(--color-text-secondary);
}

.empty-icon {
    width: 40px;
    height: 40px;
    margin-bottom: 12px;
    opacity: 0.25;
}

.host-grid {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(380px, 1fr));
    gap: 14px;
}

.host-card {
    background: var(--color-surface);
    border-radius: var(--radius-md);
    padding: 20px;
    border: 1px solid var(--color-border);
    cursor: pointer;
    transition: background 0.15s ease, border-color 0.15s ease;
}

.host-card:hover {
    background: var(--accent-bg);
    border-color: var(--color-accent);
}

.host-card.expanded {
    border-color: var(--color-accent);
}

.host-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 10px;
}

.host-identity {
    display: flex;
    align-items: center;
    gap: 10px;
}

.host-name {
    font-weight: 600;
    font-size: 15px;
}

.channel-badge {
    padding: 3px 10px;
    border-radius: 20px;
    font-size: 12px;
    font-weight: 600;
}

.channel-badge.qq { background: rgba(18, 183, 245, 0.1); color: #12b7f5; }
.channel-badge.desktop { background: rgba(102, 126, 234, 0.1); color: #667eea; }
.channel-badge.ide { background: rgba(39, 174, 96, 0.1); color: #27ae60; }
.channel-badge.telegram { background: rgba(0, 136, 204, 0.1); color: #0088cc; }
.channel-badge.discord { background: rgba(88, 101, 242, 0.1); color: #5865f2; }

.host-status-group {
    display: flex;
    align-items: center;
    gap: 6px;
}

.status-dot {
    width: 8px;
    height: 8px;
    border-radius: 50%;
}

.status-dot.online { background: var(--color-success); }
.status-dot.offline { background: #bdc3c7; }

.status-text {
    font-size: 12px;
    color: var(--color-text-secondary);
}

.host-meta {
    display: flex;
    gap: 14px;
    font-size: 12px;
    color: var(--color-text-secondary);
}

.host-detail {
    margin-top: 14px;
    padding-top: 14px;
    border-top: 1px solid var(--color-border);
}

.detail-section {
    margin-bottom: 14px;
}

.detail-section:last-child {
    margin-bottom: 0;
}

.detail-section h5 {
    font-size: 12px;
    font-weight: 600;
    color: var(--color-text-secondary);
    text-transform: uppercase;
    letter-spacing: 0.5px;
    margin-bottom: 8px;
}

.capability-list {
    display: flex;
    flex-direction: column;
    gap: 8px;
}

.capability-item {
    display: flex;
    align-items: center;
    gap: 10px;
    padding: 6px 10px;
    background: rgba(0,0,0,0.02);
    border-radius: var(--radius-sm);
    font-size: 13px;
}

.cap-name {
    font-weight: 600;
    font-family: monospace;
}

.cap-desc {
    color: var(--color-text-secondary);
    margin-left: auto;
    font-size: 12px;
}

.risk-badge {
    padding: 2px 8px;
    border-radius: 20px;
    font-size: 11px;
    font-weight: 600;
}

.risk-l0 { background: #e8f5e9; color: #2e7d32; }
.risk-l1 { background: #c8e6c9; color: #388e3c; }
.risk-l2 { background: #fff9c4; color: #f9a825; }
.risk-l3 { background: #ffe0b2; color: #ef6c00; }
.risk-l4 { background: #ffccbc; color: #d84315; }
.risk-l5 { background: #ffcdd2; color: #c62828; }

.project-list {
    display: flex;
    flex-direction: column;
    gap: 8px;
}

.project-item {
    display: flex;
    align-items: center;
    gap: 10px;
    padding: 6px 10px;
    background: rgba(0,0,0,0.02);
    border-radius: var(--radius-sm);
    font-size: 13px;
}

.project-name {
    font-weight: 600;
}

.project-path {
    color: var(--color-text-secondary);
    font-family: monospace;
    font-size: 12px;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
    flex: 1;
}

.active-badge {
    padding: 2px 8px;
    border-radius: 20px;
    font-size: 11px;
    font-weight: 600;
    background: rgba(39, 174, 96, 0.1);
    color: var(--color-success);
}
</style>