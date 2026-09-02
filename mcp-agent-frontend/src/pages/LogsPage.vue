<template>
    <div class="page">
        <div class="page-header">
            <h2>系统日志</h2>
            <span class="subtitle">结构化日志 — 按级别、模块、Agent 筛选</span>
        </div>

        <div class="log-toolbar">
            <select v-model="filterLevel" class="filter-select" @change="loadLogs">
                <option value="">全部级别</option>
                <option value="debug">Debug</option>
                <option value="info">Info</option>
                <option value="warn">Warn</option>
                <option value="error">Error</option>
                <option value="audit">Audit</option>
            </select>
            <input v-model="searchText" placeholder="搜索日志..." class="search-input" @keyup.enter="loadLogs" />
            <button class="btn-refresh" @click="loadLogs" :disabled="logStore.isLoading">
                {{ logStore.isLoading ? '刷新中...' : '刷新' }}
            </button>
        </div>

        <div v-if="logStore.isLoading" class="loading">加载中...</div>
        <div v-else-if="logStore.logs.length === 0" class="empty-state">
            <DocumentTextIcon class="empty-icon" />
            <p>暂无日志</p>
        </div>
        <div class="log-table-wrapper" v-else>
            <table class="log-table">
                <thead>
                    <tr>
                        <th class="col-time">Time</th>
                        <th class="col-level">Level</th>
                        <th class="col-module">Module</th>
                        <th class="col-message">Message</th>
                        <th class="col-agent">Agent</th>
                    </tr>
                </thead>
                <tbody>
                    <tr v-for="log in logStore.logs" :key="log.id" :class="['log-row', `log-${log.level}`]">
                        <td class="time-cell">{{ formatTime(log.timestamp) }}</td>
                        <td><span :class="['level-badge', `level-${log.level}`]">{{ log.level.toUpperCase() }}</span></td>
                        <td class="module-cell">{{ log.module }}</td>
                        <td class="message-cell">{{ log.message }}</td>
                        <td class="agent-cell">{{ log.agentId || '--' }}</td>
                    </tr>
                </tbody>
            </table>
        </div>
    </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { DocumentTextIcon } from '@heroicons/vue/24/outline'
import { useLogStore } from '@/stores/logStore'
import type { LogLevel } from '@/types/log'

const logStore = useLogStore()

const filterLevel = ref('')
const searchText = ref('')

function formatTime(dateStr: string): string {
    if (!dateStr) return '--'
    return new Date(dateStr).toLocaleString('zh-CN', {
        month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit', second: '2-digit',
    })
}

async function loadLogs() {
    await logStore.fetchLogs({
        level: (filterLevel.value as LogLevel) || undefined,
        search: searchText.value || undefined,
        limit: 100,
    })
}

onMounted(() => {
    loadLogs()
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

.log-toolbar {
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

.search-input {
    flex: 1;
    max-width: 320px;
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

.log-table-wrapper {
    background: rgba(255,255,255,0.7);
    backdrop-filter: blur(20px);
    border-radius: 16px;
    border: 1px solid rgba(255,255,255,0.8);
    overflow: hidden;
    box-shadow: 0 4px 16px rgba(0,0,0,0.04);
}

.log-table {
    width: 100%;
    border-collapse: collapse;
}

.log-table th {
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

.log-table td {
    padding: 12px 20px;
    font-size: 13px;
    border-bottom: 1px solid rgba(0,0,0,0.04);
}

.log-row.log-error { background: rgba(231, 76, 60, 0.04); }
.log-row.log-warn { background: rgba(243, 156, 18, 0.03); }
.log-row.log-audit { background: rgba(102, 126, 234, 0.04); }

.col-time { width: 140px; }
.col-level { width: 80px; }
.col-module { width: 120px; }
.col-agent { width: 120px; }

.time-cell {
    font-family: monospace;
    font-size: 12px;
    color: var(--color-text-secondary);
    white-space: nowrap;
}

.level-badge {
    padding: 3px 10px;
    border-radius: 20px;
    font-size: 11px;
    font-weight: 600;
    font-family: monospace;
}

.level-debug { background: #f5f5f5; color: #757575; }
.level-info { background: #e3f2fd; color: #1565c0; }
.level-warn { background: #fff3e0; color: #ef6c00; }
.level-error { background: #ffebee; color: #c62828; }
.level-audit { background: rgba(102, 126, 234, 0.1); color: #667eea; }

.module-cell {
    font-family: monospace;
    font-size: 12px;
}

.message-cell {
    max-width: 400px;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
}

.agent-cell {
    font-family: monospace;
    font-size: 12px;
    color: var(--color-text-secondary);
}
</style>