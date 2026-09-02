<template>
    <div class="page">
        <div class="page-header">
            <h2>会话管理</h2>
            <span class="subtitle">Agent 会话状态、消息计数与运行统计</span>
        </div>

        <div class="session-toolbar">
            <select v-model="filterStatus" class="filter-select" @change="loadSessions">
                <option value="">全部状态</option>
                <option value="active">Active</option>
                <option value="idle">Idle</option>
                <option value="closed">Closed</option>
            </select>
            <select v-model="filterAgent" class="filter-select" @change="loadSessions">
                <option value="">全部 Agent</option>
                <option v-for="a in agentStore.agents" :key="a.agentId" :value="a.agentId">{{ a.agentName }}</option>
            </select>
            <button class="btn-refresh" @click="loadSessions" :disabled="sessionStore.isLoading">
                {{ sessionStore.isLoading ? '刷新中...' : '刷新' }}
            </button>
        </div>

        <div v-if="sessionStore.isLoading" class="loading">加载中...</div>
        <div v-else-if="sessionStore.sessions.length === 0" class="empty-state">
            <ChatBubbleLeftRightIcon class="empty-icon" />
            <p>暂无活跃会话</p>
        </div>
        <div class="session-table-wrapper" v-else>
            <table class="session-table">
                <thead>
                    <tr>
                        <th>Agent</th>
                        <th>User</th>
                        <th>Status</th>
                        <th>Messages</th>
                        <th>Runs</th>
                        <th>First Message</th>
                        <th>Last Active</th>
                    </tr>
                </thead>
                <tbody>
                    <tr v-for="session in sessionStore.sessions" :key="session.sessionId" class="clickable-row">
                        <td class="agent-cell">{{ session.agentName }}</td>
                        <td class="user-cell">{{ session.userId }}</td>
                        <td><span :class="['status-badge', `status-${session.status}`]">{{ statusLabel(session.status) }}</span></td>
                        <td class="num-cell">{{ session.messageCount }}</td>
                        <td class="num-cell">{{ session.runCount }}</td>
                        <td class="msg-cell">{{ truncate(session.firstMessage || '--', 40) }}</td>
                        <td class="time-cell">{{ formatDate(session.lastActiveAt) }}</td>
                    </tr>
                </tbody>
            </table>
        </div>
    </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ChatBubbleLeftRightIcon } from '@heroicons/vue/24/outline'
import { useSessionStore } from '@/stores/sessionStore'
import { useAgentStore } from '@/stores/agentStore'

const sessionStore = useSessionStore()
const agentStore = useAgentStore()

const filterStatus = ref('')
const filterAgent = ref('')

function statusLabel(status: string): string {
    const labels: Record<string, string> = { active: '活跃', idle: '空闲', closed: '已关闭' }
    return labels[status] || status
}

function truncate(text: string, maxLen: number): string {
    if (!text) return '--'
    return text.length > maxLen ? text.slice(0, maxLen) + '...' : text
}

function formatDate(dateStr: string): string {
    if (!dateStr) return '--'
    return new Date(dateStr).toLocaleString('zh-CN', {
        month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit',
    })
}

async function loadSessions() {
    await sessionStore.fetchSessions({
        agentId: filterAgent.value || undefined,
        status: filterStatus.value || undefined,
    })
}

onMounted(() => {
    loadSessions()
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
    display: block;
    margin-top: 4px;
}

.session-toolbar {
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

.btn-refresh:disabled { opacity: 0.5; }

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

.session-table-wrapper {
    background: rgba(255,255,255,0.7);
    backdrop-filter: blur(20px);
    border-radius: 16px;
    border: 1px solid rgba(255,255,255,0.8);
    overflow: hidden;
    box-shadow: 0 4px 16px rgba(0,0,0,0.04);
}

.session-table {
    width: 100%;
    border-collapse: collapse;
}

.session-table th {
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

.session-table td {
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

.agent-cell { font-weight: 600; }
.user-cell { font-family: monospace; color: var(--color-text-secondary); }
.num-cell { font-family: monospace; text-align: center; }
.msg-cell { max-width: 200px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.time-cell { font-size: 12px; color: var(--color-text-secondary); white-space: nowrap; }

.status-badge {
    padding: 3px 10px;
    border-radius: 20px;
    font-size: 12px;
    font-weight: 600;
}

.status-active { background: #e8f5e9; color: #2e7d32; }
.status-idle { background: #fff3e0; color: #ef6c00; }
.status-closed { background: #f5f5f5; color: #757575; }
</style>