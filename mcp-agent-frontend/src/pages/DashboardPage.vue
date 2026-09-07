<template>
    <div class="page">
        <div class="page-header">
            <h2>MCP Agent Console</h2>
            <div class="header-status">
                <span :class="['status-dot', isHealthy ? 'healthy' : 'unhealthy']"></span>
                <span class="status-label">{{ isHealthy ? 'System healthy' : 'System degraded' }}</span>
                <span class="header-meta">{{ dashboardStore.overview?.agentCount ?? '--' }} Agents · {{ dashboardStore.overview?.activeRunCount ?? '--' }} Active Runs · {{ dashboardStore.overview?.toolCount ?? '--' }} Tools</span>
            </div>
        </div>

        <div class="dashboard-grid">
            <div class="dashboard-main">
                <div class="section">
                    <h3 class="section-title">Active Agents</h3>
                    <div v-if="agentStore.isLoading" class="loading">Loading...</div>
                    <div v-else-if="activeAgents.length === 0" class="empty-state">
                        <p>No active agents</p>
                    </div>
                    <div v-else class="agent-list">
                        <div
                            v-for="agent in activeAgents"
                            :key="agent.agentId"
                            class="list-item agent-row"
                            @click="$router.push(`/agents/${agent.agentId}`)"
                        >
                            <span :class="['agent-dot', agent.status === 'online' ? 'online' : 'idle']"></span>
                            <div class="agent-row-info">
                                <span class="agent-row-name">{{ agent.agentName }}</span>
                                <span class="agent-row-meta">{{ agent.status }} · {{ agent.modelId }}</span>
                            </div>
                            <span class="agent-row-extra">{{ agent.sessionCount || 0 }} sessions</span>
                        </div>
                    </div>
                </div>

                <div class="section-separator"></div>

                <div class="section">
                    <h3 class="section-title">Recent Runs</h3>
                    <div v-if="runStore.isLoading" class="loading">Loading...</div>
                    <div v-else-if="recentRuns.length === 0" class="empty-state">
                        <p>No recent runs</p>
                    </div>
                    <div v-else class="run-list">
                        <div
                            v-for="run in recentRuns"
                            :key="run.id"
                            class="list-item run-row"
                            @click="$router.push(`/runs/${run.id}`)"
                        >
                            <div class="run-row-info">
                                <span class="run-row-id">Run #{{ run.id?.toString().slice(-4) || run.id }}</span>
                                <span class="run-row-agent">{{ run.agentName }}</span>
                            </div>
                            <span class="run-row-intent">{{ run.intent }}</span>
                            <span :class="['status-badge', `status-${run.status}`]">{{ statusLabel(run.status) }}</span>
                        </div>
                    </div>
                </div>
            </div>

            <div class="dashboard-side">
                <div class="section">
                    <h3 class="section-title">Runtime Health</h3>
                    <div v-if="!dashboardStore.overview?.runtimeHealth" class="loading">Loading...</div>
                    <div v-else class="health-list">
                        <div v-for="(item, key) in dashboardStore.overview.runtimeHealth" :key="key" class="health-item">
                            <span :class="['health-dot', (item as any).status]"></span>
                            <div class="health-item-info">
                                <span class="health-item-name">{{ key }}</span>
                                <span class="health-item-meta">{{ (item as any).message || (item as any).status }}</span>
                            </div>
                        </div>
                    </div>
                </div>

                <div class="section-separator"></div>

                <div class="section">
                    <h3 class="section-title">System Info</h3>
                    <div class="info-list">
                        <div class="info-item">
                            <span class="info-label">Uptime</span>
                            <span class="info-value">{{ dashboardStore.overview?.uptime || '--' }}</span>
                        </div>
                        <div class="info-item">
                            <span class="info-label">Hosts</span>
                            <span class="info-value">{{ dashboardStore.overview?.hostCount ?? '--' }} ({{ dashboardStore.overview?.connectedHostCount ?? 0 }} connected)</span>
                        </div>
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
    return agentStore.agents.filter((a) => a.status === 'online' || a.status === 'active').slice(0, 8)
})

const recentRuns = computed(() => {
    return (dashboardStore.overview?.recentRuns || runStore.runs).slice(0, 6)
})

function statusLabel(status: RunStatus): string {
    const labels: Record<string, string> = { pending: 'Pending', running: 'Running', completed: 'Done', failed: 'Failed', cancelled: 'Cancelled' }
    return labels[status] || status
}

onMounted(async () => {
    dashboardStore.fetchOverview()
    agentStore.fetchAgents()
    runStore.fetchRuns()
})
</script>

<style scoped>
.dashboard-grid {
    display: grid;
    grid-template-columns: 1fr 320px;
    gap: 36px;
}

@media (max-width: 900px) {
    .dashboard-grid {
        grid-template-columns: 1fr;
    }
}

.header-status {
    display: flex;
    align-items: center;
    gap: 10px;
    margin-top: 8px;
}

.status-dot {
    width: 8px;
    height: 8px;
    border-radius: 50%;
    flex-shrink: 0;
}

.status-dot.healthy { background: var(--color-success); }
.status-dot.unhealthy { background: var(--color-danger); }

.status-label {
    font-size: 14px;
    font-weight: 500;
    color: var(--color-text);
}

.header-meta {
    font-size: 13px;
    color: var(--color-text-secondary);
    margin-left: 8px;
}

.section {
    margin-bottom: 0;
}

.section-title {
    font-size: 18px;
    font-weight: 600;
    margin-bottom: 16px;
}

.agent-list, .run-list {
    display: flex;
    flex-direction: column;
}

.agent-row, .run-row {
    display: flex;
    align-items: center;
    gap: 14px;
    padding: 12px 16px;
    border-radius: var(--radius-sm);
    cursor: pointer;
    transition: background 0.15s ease;
    border: 1px solid transparent;
}

.agent-row:hover, .run-row:hover {
    background: var(--accent-bg);
    border-color: var(--color-border);
}

.agent-dot {
    width: 7px;
    height: 7px;
    border-radius: 50%;
    flex-shrink: 0;
}

.agent-dot.online { background: var(--color-success); }
.agent-dot.idle { background: #bdc3c7; }

.agent-row-info {
    flex: 1;
    display: flex;
    flex-direction: column;
    gap: 2px;
}

.agent-row-name {
    font-weight: 600;
    font-size: 14px;
}

.agent-row-meta {
    font-size: 12px;
    color: var(--color-text-secondary);
}

.agent-row-extra {
    font-size: 12px;
    color: var(--color-text-secondary);
}

.run-row-info {
    display: flex;
    flex-direction: column;
    gap: 2px;
    flex: 1;
}

.run-row-id {
    font-weight: 600;
    font-size: 14px;
}

.run-row-agent {
    font-size: 12px;
    color: var(--color-text-secondary);
}

.run-row-intent {
    font-size: 13px;
    color: var(--color-text-secondary);
    max-width: 200px;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
}

.health-list {
    display: flex;
    flex-direction: column;
    gap: 4px;
}

.health-item {
    display: flex;
    align-items: center;
    gap: 10px;
    padding: 10px 14px;
    border-radius: var(--radius-sm);
    border: 1px solid transparent;
}

.health-dot {
    width: 7px;
    height: 7px;
    border-radius: 50%;
    flex-shrink: 0;
}

.health-dot.healthy { background: var(--color-success); }
.health-dot.warning { background: #f39c12; }
.health-dot.error { background: var(--color-danger); }

.health-item-info {
    display: flex;
    flex-direction: column;
    gap: 1px;
}

.health-item-name {
    font-size: 13px;
    font-weight: 500;
}

.health-item-meta {
    font-size: 12px;
    color: var(--color-text-secondary);
}

.info-list {
    display: flex;
    flex-direction: column;
}

.info-item {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 10px 14px;
    border-radius: var(--radius-sm);
}

.info-label {
    font-size: 13px;
    color: var(--color-text-secondary);
}

.info-value {
    font-size: 13px;
    font-weight: 500;
}
</style>