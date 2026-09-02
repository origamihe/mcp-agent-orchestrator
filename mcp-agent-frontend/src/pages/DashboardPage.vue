<template>
    <div class="page">
        <div class="page-header">
            <h2>Agent Runtime</h2>
            <span :class="['status-dot', isHealthy ? 'healthy' : 'unhealthy']"></span>
            <span class="status-text">{{ isHealthy ? 'System Healthy' : 'System Degraded' }}</span>
            <span class="uptime">Uptime: {{ dashboardStore.overview?.uptime || '--' }}</span>
        </div>

        <div class="stat-grid">
            <div class="stat-card agents">
                <span class="stat-num">{{ dashboardStore.overview?.agentCount ?? '--' }}</span>
                <span class="stat-label">Agents</span>
                <span class="stat-sub">{{ dashboardStore.overview?.activeAgentCount ?? 0 }} active</span>
            </div>
            <div class="stat-card runs">
                <span class="stat-num">{{ dashboardStore.overview?.activeRunCount ?? '--' }}</span>
                <span class="stat-label">Active Runs</span>
            </div>
            <div class="stat-card tools">
                <span class="stat-num">{{ dashboardStore.overview?.toolCount ?? '--' }}</span>
                <span class="stat-label">Tools</span>
            </div>
            <div class="stat-card hosts">
                <span class="stat-num">{{ dashboardStore.overview?.hostCount ?? '--' }}</span>
                <span class="stat-label">Hosts</span>
                <span class="stat-sub">{{ dashboardStore.overview?.connectedHostCount ?? 0 }} connected</span>
            </div>
        </div>

        <div class="content-grid">
            <div class="section-card">
                <div class="section-header">
                    <h3>Active Agents</h3>
                    <router-link to="/agents" class="section-link">查看全部 →</router-link>
                </div>
                <div v-if="agentStore.isLoading" class="loading">加载中...</div>
                <div v-else-if="activeAgents.length === 0" class="empty">暂无活跃 Agent</div>
                <div v-else class="agent-list">
                    <div v-for="agent in activeAgents" :key="agent.agentId" class="agent-row" @click="$router.push(`/agents/${agent.agentId}`)">
                        <span :class="['agent-status-dot', agent.status === 'online' ? 'online' : 'idle']"></span>
                        <div class="agent-info">
                            <span class="agent-name">{{ agent.agentName }}</span>
                            <span class="agent-meta">{{ agent.status }} · {{ agent.modelId }}</span>
                        </div>
                        <span class="agent-badge">{{ agent.sessionCount || 0 }} sessions</span>
                    </div>
                </div>
            </div>

            <div class="section-card">
                <div class="section-header">
                    <h3>Recent Runs</h3>
                    <router-link to="/runs" class="section-link">查看全部 →</router-link>
                </div>
                <div v-if="runStore.isLoading" class="loading">加载中...</div>
                <div v-else-if="recentRuns.length === 0" class="empty">暂无执行记录</div>
                <div v-else class="run-list">
                    <div v-for="run in recentRuns" :key="run.id" class="run-row" @click="$router.push(`/runs/${run.id}`)">
                        <div class="run-info">
                            <span class="run-agent">{{ run.agentName }}</span>
                            <span class="run-intent">{{ run.intent }}</span>
                        </div>
                        <span :class="['run-status', run.status]">{{ statusLabel(run.status) }}</span>
                    </div>
                </div>
            </div>
        </div>

        <div class="section-card health-section">
            <div class="section-header">
                <h3>Runtime Health</h3>
            </div>
            <div v-if="!dashboardStore.overview?.runtimeHealth" class="loading">加载中...</div>
            <div v-else class="health-grid">
                <div v-for="(item, key) in dashboardStore.overview.runtimeHealth" :key="key" class="health-item">
                    <span :class="['health-dot', (item as any).status]"></span>
                    <div class="health-info">
                        <span class="health-name">{{ key }}</span>
                        <span class="health-meta">{{ (item as any).message || (item as any).status }}</span>
                    </div>
                </div>
            </div>
        </div>
    </div>
</template>

<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { useDashboardStore } from '@/stores/dashboardStore'
import { useAgentStore } from '@/stores/agentStore'
import { useRunStore } from '@/stores/runStore'
import type { RunStatus } from '@/types/run'

const dashboardStore = useDashboardStore()
const agentStore = useAgentStore()
const runStore = useRunStore()

const isHealthy = computed(() => {
    const health = dashboardStore.overview?.runtimeHealth
    if (!health) return true
    return Object.values(health).every((h: any) => h.status === 'healthy')
})

const activeAgents = computed(() => {
    return agentStore.agents.filter((a) => a.status === 'online' || a.status === 'active').slice(0, 5)
})

const recentRuns = computed(() => {
    return (dashboardStore.overview?.recentRuns || runStore.runs).slice(0, 5)
})

function statusLabel(status: RunStatus): string {
    const labels: Record<string, string> = { pending: '等待', running: '执行中', completed: '完成', failed: '失败', cancelled: '取消' }
    return labels[status] || status
}

onMounted(async () => {
    dashboardStore.fetchOverview()
    agentStore.fetchAgents()
    runStore.fetchRuns({ limit: 5 })
})
</script>

<style scoped>
.page {
    padding: 24px 32px;
    height: 100%;
    overflow-y: auto;
}

.page-header {
    display: flex;
    align-items: center;
    gap: 12px;
    margin-bottom: 24px;
}

.page-header h2 {
    font-size: 22px;
    font-weight: 700;
}

.status-dot {
    width: 10px;
    height: 10px;
    border-radius: 50%;
}

.status-dot.healthy { background: #27ae60; box-shadow: 0 0 8px rgba(39, 174, 96, 0.4); }
.status-dot.unhealthy { background: #e74c3c; box-shadow: 0 0 8px rgba(231, 76, 60, 0.4); }

.status-text {
    font-size: 14px;
    font-weight: 500;
}

.uptime {
    margin-left: auto;
    font-size: 12px;
    color: var(--color-text-secondary);
    font-family: monospace;
}

.stat-grid {
    display: grid;
    grid-template-columns: repeat(4, 1fr);
    gap: 16px;
    margin-bottom: 24px;
}

.stat-card {
    background: rgba(255,255,255,0.7);
    backdrop-filter: blur(20px);
    border-radius: 16px;
    padding: 24px;
    border: 1px solid rgba(255,255,255,0.8);
    box-shadow: 0 4px 16px rgba(0,0,0,0.04);
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 4px;
    position: relative;
    overflow: hidden;
}

.stat-card::before {
    content: '';
    position: absolute;
    top: 0;
    left: 0;
    right: 0;
    height: 3px;
}

.stat-card.agents::before { background: #667eea; }
.stat-card.runs::before { background: #27ae60; }
.stat-card.tools::before { background: #f39c12; }
.stat-card.hosts::before { background: #e74c3c; }

.stat-num {
    font-size: 32px;
    font-weight: 700;
}

.stat-label {
    font-size: 13px;
    color: var(--color-text-secondary);
    font-weight: 500;
}

.stat-sub {
    font-size: 11px;
    color: var(--color-text-secondary);
    opacity: 0.7;
}

.content-grid {
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: 16px;
    margin-bottom: 24px;
}

.section-card {
    background: rgba(255,255,255,0.7);
    backdrop-filter: blur(20px);
    border-radius: 16px;
    padding: 24px;
    border: 1px solid rgba(255,255,255,0.8);
    box-shadow: 0 4px 16px rgba(0,0,0,0.04);
}

.section-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 16px;
}

.section-header h3 {
    font-size: 16px;
    font-weight: 600;
}

.section-link {
    font-size: 13px;
    color: #667eea;
    text-decoration: none;
}

.loading, .empty {
    font-size: 13px;
    color: var(--color-text-secondary);
    text-align: center;
    padding: 20px 0;
}

.agent-list, .run-list {
    display: flex;
    flex-direction: column;
    gap: 8px;
}

.agent-row, .run-row {
    display: flex;
    align-items: center;
    gap: 12px;
    padding: 10px 14px;
    border-radius: 10px;
    background: rgba(0,0,0,0.02);
    cursor: pointer;
    transition: background 0.2s;
}

.agent-row:hover, .run-row:hover {
    background: rgba(102, 126, 234, 0.06);
}

.agent-status-dot {
    width: 8px;
    height: 8px;
    border-radius: 50%;
    flex-shrink: 0;
}

.agent-status-dot.online { background: #27ae60; }
.agent-status-dot.idle { background: #bdc3c7; }

.agent-info {
    display: flex;
    flex-direction: column;
    flex: 1;
}

.agent-name {
    font-weight: 600;
    font-size: 14px;
}

.agent-meta {
    font-size: 12px;
    color: var(--color-text-secondary);
}

.agent-badge {
    font-size: 11px;
    padding: 3px 10px;
    border-radius: 20px;
    background: rgba(102, 126, 234, 0.08);
    color: #667eea;
}

.run-info {
    display: flex;
    flex-direction: column;
    flex: 1;
}

.run-agent {
    font-weight: 600;
    font-size: 14px;
}

.run-intent {
    font-size: 12px;
    color: var(--color-text-secondary);
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
    max-width: 240px;
}

.run-status {
    font-size: 12px;
    font-weight: 600;
    padding: 3px 10px;
    border-radius: 20px;
}

.run-status.completed { background: #e8f5e9; color: #2e7d32; }
.run-status.failed { background: #ffebee; color: #c62828; }
.run-status.running { background: #e3f2fd; color: #1565c0; }
.run-status.pending { background: #fff3e0; color: #ef6c00; }

.health-section {
    margin-bottom: 24px;
}

.health-grid {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
    gap: 12px;
}

.health-item {
    display: flex;
    align-items: center;
    gap: 12px;
    padding: 12px 16px;
    border-radius: 10px;
    background: rgba(0,0,0,0.02);
}

.health-dot {
    width: 10px;
    height: 10px;
    border-radius: 50%;
    flex-shrink: 0;
}

.health-dot.healthy { background: #27ae60; }
.health-dot.degraded { background: #f39c12; }
.health-dot.unhealthy { background: #e74c3c; }

.health-info {
    display: flex;
    flex-direction: column;
}

.health-name {
    font-size: 13px;
    font-weight: 600;
    text-transform: capitalize;
}

.health-meta {
    font-size: 12px;
    color: var(--color-text-secondary);
}
</style>