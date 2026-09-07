<template>
    <div class="page">
        <div class="page-header">
            <h2>Runs</h2>
            <span class="subtitle">Execution history & token usage</span>
        </div>

        <div class="run-toolbar">
            <select v-model="filterStatus" class="filter-select" @change="loadRuns">
                <option value="">All statuses</option>
                <option value="pending">Pending</option>
                <option value="running">Running</option>
                <option value="completed">Completed</option>
                <option value="failed">Failed</option>
                <option value="cancelled">Cancelled</option>
            </select>
            <select v-model="filterAgent" class="filter-select" @change="loadRuns">
                <option value="">All Agents</option>
                <option v-for="a in agentStore.agents" :key="a.agentId" :value="a.agentId">{{ a.agentName }}</option>
            </select>
            <div class="toolbar-spacer"></div>
            <button class="btn-secondary" @click="loadRuns" :disabled="runStore.isLoading">
                {{ runStore.isLoading ? 'Loading...' : 'Refresh' }}
            </button>
        </div>

        <div v-if="runStore.isLoading" class="loading">Loading...</div>

        <div v-else-if="filteredRuns.length === 0" class="empty-state">
            <ClockIcon class="empty-icon" />
            <p>No run records</p>
        </div>

        <div class="run-table-wrapper" v-else>
            <table class="run-table">
                <thead>
                    <tr>
                        <th>Agent</th>
                        <th>Intent</th>
                        <th>Status</th>
                        <th>Duration</th>
                        <th>Tool Calls</th>
                        <th>Tokens</th>
                        <th>Time</th>
                        <th></th>
                    </tr>
                </thead>
                <tbody>
                    <tr v-for="run in filteredRuns" :key="run.id" @click="viewDetail(run.id)" class="clickable-row">
                        <td class="agent-cell">{{ run.agentName }}</td>
                        <td class="intent-cell">{{ run.intent }}</td>
                        <td><StatusBadge :type="runStatusType(run.status)" :text="statusLabel(run.status)" /></td>
                        <td class="mono-cell">{{ formatDuration(run.duration) }}</td>
                        <td class="mono-cell">{{ run.toolCallCount }}</td>
                        <td class="mono-cell">{{ run.tokenUsage.totalTokens }}</td>
                        <td class="mono-cell">{{ formatDate(run.createdAt) }}</td>
                        <td><button class="btn-tertiary" @click.stop="viewDetail(run.id)">Detail</button></td>
                    </tr>
                </tbody>
            </table>
        </div>
    </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ClockIcon } from '@heroicons/vue/24/outline'
import StatusBadge from '@/components/common/StatusBadge.vue'
import { useRunStore } from '@/stores/runStore'
import { useAgentStore } from '@/stores/agentStore'
import type { RunStatus } from '@/types/run'

const router = useRouter()
const runStore = useRunStore()
const agentStore = useAgentStore()

const filterStatus = ref('')
const filterAgent = ref('')

function statusLabel(status: RunStatus): string {
    const labels: Record<RunStatus, string> = {
        pending: 'Pending', running: 'Running', completed: 'Done', failed: 'Failed', cancelled: 'Cancelled',
    }
    return labels[status] || status
}

function runStatusType(status: string): 'success' | 'warning' | 'error' | 'info' | 'neutral' {
    const map: Record<string, string> = {
        completed: 'success', failed: 'error', running: 'info', pending: 'warning', cancelled: 'neutral',
    }
    return (map[status] || 'neutral') as any
}

function formatDuration(ms: number): string {
    if (ms < 1000) return `${ms}ms`
    if (ms < 60000) return `${(ms / 1000).toFixed(1)}s`
    return `${(ms / 60000).toFixed(1)}m`
}

function formatDate(dateStr: string): string {
    if (!dateStr) return ''
    return new Date(dateStr).toLocaleString('zh-CN', {
        month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit',
    })
}

function viewDetail(id: string) {
    router.push(`/runs/${id}`)
}

const filteredRuns = computed(() => {
    let list = runStore.runs
    if (filterStatus.value) {
        list = list.filter((r) => r.status === filterStatus.value)
    }
    return list
})

async function loadRuns() {
    await runStore.fetchRuns({
        agentId: filterAgent.value || undefined,
    })
}

onMounted(() => {
    loadRuns()
    if (agentStore.agents.length === 0) {
        agentStore.fetchAgents()
    }
})
</script>

<style scoped>
.page-header {
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
    margin-top: 4px;
    display: block;
}

.run-toolbar {
    display: flex;
    gap: 10px;
    margin-bottom: 20px;
    align-items: center;
}

.filter-select {
    padding: 8px 14px;
    border-radius: var(--radius-sm);
    border: 1px solid var(--color-border);
    background: var(--color-surface);
    font-size: 13px;
    color: var(--color-text);
}

.toolbar-spacer {
    flex: 1;
}

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

.run-table-wrapper {
    background: var(--color-surface);
    border-radius: var(--radius-lg);
    border: 1px solid var(--color-border);
    overflow: hidden;
}

.run-table {
    width: 100%;
    border-collapse: collapse;
}

.run-table th {
    text-align: left;
    padding: 12px 18px;
    font-size: 12px;
    font-weight: 600;
    color: var(--color-text-secondary);
    text-transform: uppercase;
    letter-spacing: 0.5px;
    border-bottom: 1px solid var(--color-border);
    background: rgba(0,0,0,0.015);
}

.run-table td {
    padding: 12px 18px;
    font-size: 13px;
    border-bottom: 1px solid var(--color-border);
}

.run-table tbody tr:last-child td {
    border-bottom: none;
}

.clickable-row {
    cursor: pointer;
    transition: background 0.15s ease;
}

.clickable-row:hover {
    background: var(--accent-bg);
}

.intent-cell {
    max-width: 200px;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
}

.mono-cell {
    font-size: 12px;
    color: var(--color-text-secondary);
    font-family: monospace;
}

.agent-cell {
    font-weight: 500;
}

.btn-tertiary {
    padding: 4px 12px;
    border-radius: 6px;
    border: none;
    background: none;
    cursor: pointer;
    font-size: 12px;
    color: var(--color-accent);
    font-weight: 500;
}

.btn-tertiary:hover {
    background: var(--accent-bg);
    box-shadow: none;
}
</style>