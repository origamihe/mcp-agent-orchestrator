<template>
    <div class="page">
        <div class="page-header">
            <h2>系统日志</h2>
            <span class="subtitle">结构化日志 — 按级别、模块、Agent 筛选</span>
        </div>

        <div class="tab-bar">
            <button :class="['tab-btn', { active: activeTab === 'events' }]" @click="activeTab = 'events'">
                事件日志
            </button>
            <button :class="['tab-btn', { active: activeTab === 'files' }]" @click="switchToFilesTab">
                文件日志
            </button>
        </div>

        <template v-if="activeTab === 'events'">
            <div v-if="logStore.statistics" class="stats-bar">
                <span class="stat-item stat-error">ERROR {{ logStore.statistics.levelCounts?.error || 0 }}</span>
                <span class="stat-item stat-warn">WARN {{ logStore.statistics.levelCounts?.warn || 0 }}</span>
                <span class="stat-item stat-info">INFO {{ logStore.statistics.levelCounts?.info || 0 }}</span>
                <span class="stat-item stat-debug">DEBUG {{ logStore.statistics.levelCounts?.debug || 0 }}</span>
                <span class="stat-item stat-audit">AUDIT {{ logStore.statistics.levelCounts?.audit || 0 }}</span>
                <span class="stat-total">共 {{ logStore.totalCount }} 条</span>
            </div>

            <LogCharts v-if="logStore.statistics" />

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
                <select v-model="autoRefreshInterval" class="filter-select" @change="toggleAutoRefresh">
                    <option :value="0">关闭自动刷新</option>
                    <option :value="5">5 秒</option>
                    <option :value="10">10 秒</option>
                    <option :value="30">30 秒</option>
                    <option :value="60">60 秒</option>
                </select>
                <span v-if="autoRefreshTimer" class="auto-refresh-indicator">● 自动刷新中</span>
                <div class="toolbar-spacer"></div>
                <button class="btn-export" @click="exportEventsJSON" title="导出 JSON">JSON</button>
                <button class="btn-export" @click="exportEventsCSV" title="导出 CSV">CSV</button>
            </div>

            <div v-if="logStore.isLoading" class="loading">加载中...</div>
            <div v-else-if="logStore.logs.length === 0" class="empty-state">
                <DocumentTextIcon class="empty-icon" />
                <p>暂无事件日志</p>
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
                        <tr v-for="log in logStore.logs" :key="log.id" :class="['log-row', `log-${log.level}`]" @click="openDetail(log)">
                            <td class="time-cell">{{ formatTime(log.timestamp) }}</td>
                            <td><span :class="['level-badge', `level-${log.level}`]">{{ log.level.toUpperCase() }}</span></td>
                            <td class="module-cell">{{ log.module }}</td>
                            <td class="message-cell">{{ log.message }}</td>
                            <td class="agent-cell">{{ log.agentId || '--' }}</td>
                        </tr>
                    </tbody>
                </table>
                <div class="pagination-bar">
                    <span class="page-info">第 {{ logStore.currentPage + 1 }} 页 / 共 {{ totalPages }} 页 ({{ logStore.totalCount }} 条)</span>
                    <div class="page-actions">
                        <button class="btn-page" :disabled="logStore.currentPage <= 0" @click="prevPage">上一页</button>
                        <button class="btn-page" :disabled="!logStore.hasMore" @click="nextPage">下一页</button>
                    </div>
                </div>
            </div>
        </template>

        <template v-if="activeTab === 'files'">
            <div class="log-toolbar">
                <select v-model="fileModule" class="filter-select" @change="loadFileLogs">
                    <option v-for="mod in fileModules" :key="mod" :value="mod">{{ mod }}</option>
                </select>
                <select v-model="fileLevel" class="filter-select" @change="loadFileLogs">
                    <option value="">全部级别</option>
                    <option value="ERROR">ERROR</option>
                    <option value="WARN">WARN</option>
                    <option value="INFO">INFO</option>
                    <option value="DEBUG">DEBUG</option>
                </select>
                <input v-model="fileSearch" placeholder="搜索..." class="search-input" @keyup.enter="loadFileLogs" />
                <button class="btn-refresh" @click="loadFileLogs" :disabled="fileLoading">
                    {{ fileLoading ? '加载中...' : '刷新' }}
                </button>
                <button
                    :class="['btn-stream', { active: streamConnected }]"
                    @click="toggleStream"
                    :disabled="fileLoading">
                    {{ streamConnected ? '● 实时' : '○ 实时' }}
                </button>
                <select v-model="fileAutoRefreshInterval" class="filter-select" @change="toggleFileAutoRefresh">
                    <option :value="0">关闭自动刷新</option>
                    <option :value="5">5 秒</option>
                    <option :value="10">10 秒</option>
                    <option :value="30">30 秒</option>
                    <option :value="60">60 秒</option>
                </select>
                <span v-if="fileAutoRefreshTimer" class="auto-refresh-indicator">● 自动刷新中</span>
                <div class="toolbar-spacer"></div>
                <button class="btn-export" @click="exportFileJSON" title="导出 JSON">JSON</button>
                <button class="btn-export" @click="exportFileCSV" title="导出 CSV">CSV</button>
            </div>

            <div v-if="streamConnected" class="stream-indicator">
                <span class="stream-dot"></span> 实时监控中 — 每 2 秒自动拉取最新日志
            </div>

            <div v-if="fileLoading && !streamConnected" class="loading">加载中...</div>
            <div v-else-if="fileEntries.length === 0 && !streamConnected" class="empty-state">
                <DocumentTextIcon class="empty-icon" />
                <p>暂无文件日志</p>
            </div>
            <div class="log-table-wrapper" v-else>
                <table class="log-table">
                    <thead>
                        <tr>
                            <th class="col-time">Time</th>
                            <th class="col-level">Level</th>
                            <th class="col-module">Logger</th>
                            <th class="col-message">Message</th>
                            <th class="col-agent">Thread</th>
                        </tr>
                    </thead>
                    <tbody>
                        <tr v-for="(entry, idx) in displayEntries" :key="idx"
                            :class="['log-row', `log-${entry.level.toLowerCase()}`]" @click="openDetail(entry)">
                            <td class="time-cell">{{ formatTime(entry.timestamp) }}</td>
                            <td><span :class="['level-badge', `level-${entry.level.toLowerCase()}`]">{{ entry.level }}</span></td>
                            <td class="module-cell">{{ entry.logger }}</td>
                            <td class="message-cell">{{ entry.message }}</td>
                            <td class="agent-cell">{{ entry.thread }}</td>
                        </tr>
                    </tbody>
                </table>
                <div class="pagination-bar" v-if="!streamConnected">
                    <span class="page-info">共 {{ fileTotalCount }} 条</span>
                    <div class="page-actions">
                        <button class="btn-page" :disabled="filePage <= 0" @click="filePage--; loadFileLogs()">上一页</button>
                        <button class="btn-page" :disabled="fileEntries.length < filePageSize" @click="filePage++; loadFileLogs()">下一页</button>
                    </div>
                </div>
            </div>
        </template>
    </div>

    <LogDetailModal
        :visible="showDetailModal"
        :entry="selectedEntry!"
        @close="closeDetail"
    />
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, watch } from 'vue'
import { DocumentTextIcon } from '@heroicons/vue/24/outline'
import { useLogStore } from '@/stores/logStore'
import { fetchLogModules, fetchFileLogs } from '@/api/logs'
import { useLogStream } from '@/composables/useLogStream'
import { exportLogsAsJSON, exportLogsAsCSV, exportFileLogsAsJSON, exportFileLogsAsCSV } from '@/composables/useLogExport'
import LogCharts from '@/components/LogCharts.vue'
import LogDetailModal from '@/components/LogDetailModal.vue'
import type { LogLevel, LogEntry, FileLogEntry } from '@/types/log'

const logStore = useLogStore()

const activeTab = ref<'events' | 'files'>('events')
const filterLevel = ref('')
const searchText = ref('')

const autoRefreshInterval = ref(0)
const autoRefreshTimer = ref<ReturnType<typeof setInterval> | null>(null)

const fileModules = ref<string[]>([])
const fileModule = ref('mcp-agent-orchestrator')
const fileLevel = ref('')
const fileSearch = ref('')
const fileLoading = ref(false)
const fileEntries = ref<FileLogEntry[]>([])
const fileTotalCount = ref(0)
const filePage = ref(0)
const filePageSize = ref(100)

const fileAutoRefreshInterval = ref(0)
const fileAutoRefreshTimer = ref<ReturnType<typeof setInterval> | null>(null)

const selectedEntry = ref<LogEntry | FileLogEntry | null>(null)
const showDetailModal = ref(false)

const { isConnected: streamConnected, entries: streamEntries, connect: connectStream, disconnect: disconnectStream } =
    useLogStream(fileModule.value, fileLevel.value || undefined)

const displayEntries = computed(() => {
    if (streamConnected.value) {
        return streamEntries.value
    }
    return fileEntries.value
})

const totalPages = computed(() => {
    if (logStore.totalCount <= 0) return 1
    return Math.ceil(logStore.totalCount / logStore.pageSize)
})

function formatTime(dateStr: string): string {
    if (!dateStr) return '--'
    return new Date(dateStr).toLocaleString('zh-CN', {
        month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit', second: '2-digit',
    })
}

async function loadLogs(page?: number) {
    const currentPage = page ?? logStore.currentPage
    await logStore.fetchLogs({
        level: (filterLevel.value as LogLevel) || undefined,
        search: searchText.value || undefined,
        limit: logStore.pageSize,
        offset: currentPage * logStore.pageSize,
    })
}

function prevPage() {
    if (logStore.currentPage > 0) loadLogs(logStore.currentPage - 1)
}

function nextPage() {
    if (logStore.hasMore) loadLogs(logStore.currentPage + 1)
}

async function switchToFilesTab() {
    activeTab.value = 'files'
    if (fileModules.value.length === 0) {
        try {
            fileModules.value = await fetchLogModules()
            if (fileModules.value.length > 0 && !fileModules.value.includes(fileModule.value)) {
                fileModule.value = fileModules.value[0]
            }
        } catch {
            fileModules.value = ['mcp-agent-orchestrator', 'orchestrator', 'agent', 'llm', 'memory', 'prompt', 'performance']
        }
    }
    await loadFileLogs()
}

async function loadFileLogs() {
    fileLoading.value = true
    try {
        const resp = await fetchFileLogs(fileModule.value, {
            level: fileLevel.value || undefined,
            search: fileSearch.value || undefined,
            limit: filePageSize.value,
            offset: filePage.value * filePageSize.value,
        })
        fileEntries.value = resp.items
        fileTotalCount.value = resp.totalCount
    } catch {
        fileEntries.value = []
        fileTotalCount.value = 0
    } finally {
        fileLoading.value = false
    }
}

function toggleStream() {
    if (streamConnected.value) {
        disconnectStream()
    } else {
        connectStream()
    }
}

function toggleAutoRefresh() {
    stopAutoRefresh()
    if (autoRefreshInterval.value > 0) {
        autoRefreshTimer.value = setInterval(() => {
            loadLogs()
            logStore.fetchStatistics()
        }, autoRefreshInterval.value * 1000)
    }
}

function stopAutoRefresh() {
    if (autoRefreshTimer.value) {
        clearInterval(autoRefreshTimer.value)
        autoRefreshTimer.value = null
    }
}

function toggleFileAutoRefresh() {
    stopFileAutoRefresh()
    if (fileAutoRefreshInterval.value > 0) {
        fileAutoRefreshTimer.value = setInterval(() => {
            loadFileLogs()
        }, fileAutoRefreshInterval.value * 1000)
    }
}

function stopFileAutoRefresh() {
    if (fileAutoRefreshTimer.value) {
        clearInterval(fileAutoRefreshTimer.value)
        fileAutoRefreshTimer.value = null
    }
}

function exportEventsJSON() {
    exportLogsAsJSON(logStore.logs)
}

function exportEventsCSV() {
    exportLogsAsCSV(logStore.logs)
}

function exportFileJSON() {
    exportFileLogsAsJSON(fileEntries.value)
}

function exportFileCSV() {
    exportFileLogsAsCSV(fileEntries.value)
}

function openDetail(entry: LogEntry | FileLogEntry) {
    selectedEntry.value = entry
    showDetailModal.value = true
}

function closeDetail() {
    showDetailModal.value = false
}

watch(fileModule, () => {
    if (activeTab.value === 'files') {
        filePage.value = 0
        loadFileLogs()
    }
})

onMounted(() => {
    loadLogs()
    logStore.fetchStatistics()
})

onUnmounted(() => {
    if (streamConnected.value) disconnectStream()
    stopAutoRefresh()
    stopFileAutoRefresh()
})
</script>

<style scoped>
.page {
    padding: 24px 32px;
    height: 100%;
    overflow-y: auto;
}

.page-header {
    margin-bottom: 16px;
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

.tab-bar {
    display: flex;
    gap: 4px;
    margin-bottom: 20px;
    padding: 4px;
    background: rgba(0,0,0,0.04);
    border-radius: 12px;
    width: fit-content;
}

.tab-btn {
    padding: 8px 20px;
    border-radius: 10px;
    border: none;
    background: transparent;
    font-size: 13px;
    font-weight: 500;
    cursor: pointer;
    color: var(--color-text-secondary);
    transition: all 0.2s;
}

.tab-btn.active {
    background: #fff;
    color: #667eea;
    box-shadow: 0 1px 3px rgba(0,0,0,0.08);
}

.stats-bar {
    display: flex;
    gap: 16px;
    margin-bottom: 20px;
    padding: 12px 18px;
    background: rgba(255,255,255,0.7);
    backdrop-filter: blur(20px);
    border-radius: 12px;
    border: 1px solid rgba(255,255,255,0.8);
    font-size: 13px;
    font-weight: 600;
    flex-wrap: wrap;
}

.stat-item {
    padding: 4px 12px;
    border-radius: 8px;
}

.stat-error { background: rgba(231, 76, 60, 0.12); color: #e74c3c; }
.stat-warn { background: rgba(243, 156, 18, 0.12); color: #e67e22; }
.stat-info { background: rgba(52, 152, 219, 0.12); color: #2980b9; }
.stat-debug { background: rgba(149, 165, 166, 0.12); color: #7f8c8d; }
.stat-audit { background: rgba(102, 126, 234, 0.12); color: #667eea; }
.stat-total { margin-left: auto; color: var(--color-text-secondary); font-weight: 400; }

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

.btn-stream {
    padding: 8px 18px;
    border-radius: 10px;
    border: 1px solid rgba(0,0,0,0.12);
    background: rgba(255,255,255,0.9);
    color: #999;
    cursor: pointer;
    font-size: 13px;
    font-weight: 500;
    transition: all 0.2s;
}

.btn-stream.active {
    border-color: #27ae60;
    background: rgba(39, 174, 96, 0.08);
    color: #27ae60;
}

.stream-indicator {
    display: flex;
    align-items: center;
    gap: 8px;
    margin-bottom: 16px;
    padding: 8px 14px;
    background: rgba(39, 174, 96, 0.06);
    border-radius: 8px;
    font-size: 12px;
    color: #27ae60;
    font-weight: 500;
}

.stream-dot {
    width: 8px;
    height: 8px;
    border-radius: 50%;
    background: #27ae60;
    animation: pulse 1.5s infinite;
}

@keyframes pulse {
    0%, 100% { opacity: 1; }
    50% { opacity: 0.3; }
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
.log-row { cursor: pointer; transition: background 0.15s; }
.log-row:hover { background: rgba(102, 126, 234, 0.06) !important; }

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

.pagination-bar {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 12px 20px;
    border-top: 1px solid rgba(0,0,0,0.06);
    background: rgba(0,0,0,0.01);
}

.page-info {
    font-size: 12px;
    color: var(--color-text-secondary);
}

.page-actions {
    display: flex;
    gap: 8px;
}

.btn-page {
    padding: 6px 14px;
    border-radius: 8px;
    border: 1px solid rgba(0,0,0,0.12);
    background: rgba(255,255,255,0.9);
    font-size: 12px;
    cursor: pointer;
    color: #333;
}

.btn-page:disabled {
    opacity: 0.4;
    cursor: not-allowed;
}

.btn-page:hover:not(:disabled) {
    background: rgba(102, 126, 234, 0.08);
    border-color: #667eea;
    color: #667eea;
}

.toolbar-spacer {
    flex: 1;
}

.auto-refresh-indicator {
    font-size: 12px;
    color: #667eea;
    font-weight: 500;
    white-space: nowrap;
    animation: pulse 2s infinite;
}

.btn-export {
    padding: 6px 14px;
    border-radius: 8px;
    border: 1px solid rgba(0,0,0,0.12);
    background: rgba(255,255,255,0.9);
    font-size: 12px;
    cursor: pointer;
    color: #555;
    font-weight: 500;
    transition: all 0.15s;
}

.btn-export:hover {
    background: rgba(102, 126, 234, 0.08);
    border-color: #667eea;
    color: #667eea;
}
</style>