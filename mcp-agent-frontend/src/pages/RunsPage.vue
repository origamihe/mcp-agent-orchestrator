<template>
    <div class="page">
        <div class="page-header">
            <h2>执行历史</h2>
            <span class="subtitle">追踪所有 Agent 执行记录与 Token 用量</span>
        </div>

        <div class="run-toolbar">
            <select v-model="filterStatus" class="filter-select" @change="loadRuns">
                <option value="">全部状态</option>
                <option value="pending">Pending</option>
                <option value="running">Running</option>
                <option value="completed">Completed</option>
                <option value="failed">Failed</option>
                <option value="cancelled">Cancelled</option>
            </select>
            <select v-model="filterAgent" class="filter-select" @change="loadRuns">
                <option value="">全部 Agent</option>
                <option v-for="a in agentStore.agents" :key="a.agentId" :value="a.agentId">{{ a.agentName }}</option>
            </select>
            <button class="btn-refresh" @click="loadRuns" :disabled="runStore.isLoading">
                {{ runStore.isLoading ? '刷新中...' : '刷新' }}
            </button>
        </div>

        <div v-if="runStore.isLoading" class="loading">加载中...</div>

        <div v-else-if="filteredRuns.length === 0" class="empty-state">
            <ClockIcon class="empty-icon" />
            <p>暂无执行记录</p>
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
                        <td>{{ run.agentName }}</td>
                        <td class="intent-cell">{{ run.intent }}</td>
                        <td><span :class="['status-badge', `status-${run.status}`]">{{ statusLabel(run.status) }}</span></td>
                        <td>{{ formatDuration(run.duration) }}</td>
                        <td>{{ run.toolCallCount }}</td>
                        <td class="token-cell">{{ run.tokenUsage.totalTokens }}</td>
                        <td class="time-cell">{{ formatDate(run.createdAt) }}</td>
                        <td><button class="btn-view" @click.stop="viewDetail(run.id)">详情</button></td>
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
        pending: '等待中', running: '执行中', completed: '已完成', failed: '失败', cancelled: '已取消',
    }
    return labels[status] || status
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
.page {
    padding: 24px 32px;
    height: 100%;
    overflow-y: auto;
}

.page-header {
    margin-bottom: 24px;
}

.page-header h2 {
    font-size: 22px;
    font-weight: 700;
}

.subtitle {
    font-size: 13px;
    color: var(--color-text-secondary);
    margin-top: 4px;
    display: block;
}

.run-toolbar {
    display: flex;
    gap: 12px;
    margin-bottom: 20px;
    align-items: center;
}

.filter-select {
    padding: 8px 14px;
    border-radius: 10px;
    border: 1px solid rgba(0,0,0,0.1);
    background: rgba(255,255,255,0.9);
    font-size: 13px;
}

.btn-refresh {
    padding: 8px 18px;
    border-radius: 10px;
    border: 1px solid #667eea;
    background: rgba(102, 126, 234, 0.08);
    color: #667eea;
    cursor: pointer;
    font-size: 13px;
    font-weight: 500;
}

.btn-refresh:disabled {
    opacity: 0.5;
}

.loading, .empty-state {
    padding: 60px 0;
    text-align: center;
    color: var(--color-text-secondary);
}

.empty-icon {
    width: 48px;
    height: 48px;
    margin-bottom: 12px;
    opacity: 0.3;
}

.run-table-wrapper {
    background: rgba(255,255,255,0.7);
    backdrop-filter: blur(20px);
    border-radius: 16px;
    border: 1px solid rgba(255,255,255,0.8);
    overflow: hidden;
    box-shadow: 0 4px 16px rgba(0,0,0,0.04);
}

.run-table {
    width: 100%;
    border-collapse: collapse;
}

.run-table th {
    text-align: left;
    padding: 14px 20px;
    font-size: 12px;
    font-weight: 600;
    color: var(--color-text-secondary);
    text-transform: uppercase;
    letter-spacing: 0.5px;
    border-bottom: 1px solid rgba(0,0,0,0.06);
    background: rgba(0,0,0,0.02);
}

.run-table td {
    padding: 14px 20px;
    font-size: 13px;
    border-bottom: 1px solid rgba(0,0,0,0.04);
}

.clickable-row {
    cursor: pointer;
    transition: background 0.2s;
}

.clickable-row:hover {
    background: rgba(102, 126, 234, 0.04);
}

.intent-cell {
    max-width: 200px;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
}

.token-cell, .time-cell {
    font-size: 12px;
    color: var(--color-text-secondary);
    font-family: monospace;
}

.status-badge {
    padding: 3px 10px;
    border-radius: 20px;
    font-size: 12px;
    font-weight: 600;
}

.status-pending { background: #fff3e0; color: #ef6c00; }
.status-running { background: #e3f2fd; color: #1565c0; }
.status-completed { background: #e8f5e9; color: #2e7d32; }
.status-failed { background: #ffebee; color: #c62828; }
.status-cancelled { background: #f5f5f5; color: #757575; }

.btn-view {
    padding: 4px 12px;
    border-radius: 6px;
    border: 1px solid #667eea;
    background: rgba(102, 126, 234, 0.06);
    color: #667eea;
    cursor: pointer;
    font-size: 12px;
}
</style>