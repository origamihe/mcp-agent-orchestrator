<template>
    <div class="page">
        <div class="page-header">
            <button class="btn-secondary btn-back" @click="$router.push('/agents')">← Back to Agents</button>
            <div class="header-main" v-if="agent">
                <h2>{{ agent.agentName }}</h2>
                <StatusBadge :type="statusType" :text="agent.status || 'Unknown'" />
            </div>
            <span :class="['connection-status', { connected: isConnected }]">
                <span class="conn-dot"></span>
                {{ isConnected ? 'Connected' : 'Disconnected' }}
            </span>
        </div>

        <div class="workspace-layout">
            <div class="panel trace-panel">
                <div class="panel-header-row">
                    <h3>Execution Trace</h3>
                    <button class="btn-tertiary" @click="traces = []" v-if="traces.length">Clear</button>
                </div>
                <div class="trace-content">
                    <div v-if="traces.length === 0" class="empty-state">
                        <p>No execution records</p>
                        <span class="empty-hint">Traces will appear here during agent interaction</span>
                    </div>
                    <div v-for="(trace, idx) in traces" :key="idx" :class="['trace-item', trace.type]">
                        <div class="trace-header">
                            <span :class="['trace-type-badge', trace.type]">{{ traceLabel(trace.type) }}</span>
                            <span class="trace-time">{{ formatTime(trace.timestamp) }}</span>
                        </div>
                        <p class="trace-detail">{{ trace.detail }}</p>
                        <div v-if="trace.duration" class="trace-meta">
                            <span>{{ trace.duration }}ms</span>
                        </div>
                    </div>
                </div>
            </div>

            <div class="panel chat-panel">
                <ChatPanel
                    ref="chatPanelRef"
                    :isConnected="isConnected"
                    :selectedModelId="selectedModelId"
                    :models="availableModels"
                    v-model:selectedRole="selectedRole"
                    @send-message="handleSendMessage"
                    @update:selectedModelId="handleModelChange"
                />
            </div>

            <div class="panel tool-panel">
                <div class="panel-header-row">
                    <h3>Tool Monitor</h3>
                    <button class="btn-tertiary" @click="toolCalls = []" v-if="toolCalls.length">Clear</button>
                </div>
                <div class="tool-monitor-content">
                    <div v-if="toolCalls.length === 0" class="empty-state">
                        <p>Tool monitoring</p>
                        <span class="empty-hint">Tool calls will appear here</span>
                    </div>
                    <div v-for="(call, idx) in toolCalls" :key="idx" :class="['tool-call-item', call.status]">
                        <div class="tool-call-header">
                            <code class="tool-call-name">{{ call.toolName }}</code>
                            <span :class="['tool-call-status', call.status]">{{ statusLabel(call.status) }}</span>
                        </div>
                        <div class="tool-call-params" v-if="call.params">
                            <span class="param-label">Params:</span>
                            <code>{{ truncate(call.params, 120) }}</code>
                        </div>
                        <div class="tool-call-result" v-if="call.result">
                            <span class="param-label">Result:</span>
                            <code>{{ truncate(call.result, 120) }}</code>
                        </div>
                        <div class="tool-call-meta" v-if="call.duration">
                            <span>{{ call.duration }}ms</span>
                            <span>{{ formatTime(call.timestamp) }}</span>
                        </div>
                    </div>
                </div>
            </div>
        </div>
    </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { useRoute } from 'vue-router'
import ChatPanel from '@/components/features/ChatPanel.vue'
import StatusBadge from '@/components/common/StatusBadge.vue'
import { useWebSocket } from '@/composables/useWebSocket'
import { useAgentStore } from '@/stores/agentStore'
import { useAppStore } from '@/stores/app'
import type { LlmModelInfo } from '@/types/llm'
import http from '@/api/client'

const route = useRoute()
const agentStore = useAgentStore()
const appStore = useAppStore()

const chatPanelRef = ref<any>(null)
const selectedRole = ref('')
const availableModels = ref<LlmModelInfo[]>([])

interface TraceEntry {
    type: 'llm' | 'tool' | 'policy' | 'error'
    detail: string
    timestamp: number
    duration?: number
}

interface ToolCallEntry {
    toolName: string
    status: 'running' | 'success' | 'error'
    params?: string
    result?: string
    duration?: number
    timestamp: number
}

const traces = ref<TraceEntry[]>([])
const toolCalls = ref<ToolCallEntry[]>([])

const agentId = computed(() => route.params.agentId as string)
const agent = computed(() => agentStore.currentAgent)
const selectedModelId = computed(() => appStore.selectedModelId)

const statusType = computed(() => {
    const map: Record<string, 'success' | 'warning' | 'error' | 'info' | 'neutral'> = {
        online: 'success', active: 'success', running: 'info',
        idle: 'warning', offline: 'neutral', error: 'error',
    }
    return map[agent.value?.status || ''] || 'neutral'
})

const wsUrl = `${location.protocol === 'https:' ? 'wss:' : 'ws:'}//${location.host}/ws/mcp`
const { isConnected, connect, send, lastMessage } = useWebSocket(wsUrl)

function traceLabel(type: string): string {
    const labels: Record<string, string> = { llm: 'LLM', tool: 'Tool', policy: 'Policy', error: 'Error' }
    return labels[type] || type
}

function statusLabel(status: string): string {
    const labels: Record<string, string> = { running: 'Running', success: 'Success', error: 'Error' }
    return labels[status] || status
}

function formatTime(ts: number): string {
    return new Date(ts).toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit', second: '2-digit' })
}

function truncate(text: string, maxLen: number): string {
    if (!text) return ''
    return text.length > maxLen ? text.slice(0, maxLen) + '...' : text
}

function handleSendMessage(payload: any) {
    send({ ...payload, agentId: agentId.value })
}

function handleModelChange(modelId: string) {
    appStore.selectModel(modelId)
}

watch(lastMessage, (msg) => {
    if (!msg) return

    try {
        const data = typeof msg === 'string' ? JSON.parse(msg) : msg

        if (data.type === 'trace' || data.operation) {
            traces.value.push({
                type: data.traceType || 'llm',
                detail: data.operation || data.message || JSON.stringify(data),
                timestamp: Date.now(),
                duration: data.duration,
            })
            if (traces.value.length > 50) {
                traces.value = traces.value.slice(-50)
            }
        }

        if (data.type === 'tool_call' || data.toolName) {
            const existing = toolCalls.value.find(
                (t) => t.toolName === (data.toolName || data.tool) && t.status === 'running'
            )
            if (existing) {
                existing.status = data.status || 'success'
                existing.result = data.result || data.output
                existing.duration = data.duration
            } else {
                toolCalls.value.push({
                    toolName: data.toolName || data.tool || 'unknown',
                    status: data.status || 'running',
                    params: data.params || data.input,
                    timestamp: Date.now(),
                })
            }
            if (toolCalls.value.length > 30) {
                toolCalls.value = toolCalls.value.slice(-30)
            }
        }
    } catch {
        traces.value.push({
            type: 'llm',
            detail: typeof msg === 'string' ? msg : JSON.stringify(msg),
            timestamp: Date.now(),
        })
    }
})

onMounted(async () => {
    try {
        const res = await http.get('/mcp/configs') as any
        availableModels.value = res ?? []
        if (availableModels.value.length > 0 && !appStore.selectedModelId) {
            appStore.selectModel(availableModels.value[0].configId)
        }
    } catch { /* ignore */ }
    connect()
    agentStore.fetchAgentById(agentId.value)
})
</script>

<style scoped>
.page {
    padding: 20px 28px;
    height: 100%;
    overflow: hidden;
    display: flex;
    flex-direction: column;
}

.page-header {
    display: flex;
    align-items: center;
    gap: 14px;
    margin-bottom: 16px;
    flex-shrink: 0;
}

.header-main {
    display: flex;
    align-items: center;
    gap: 10px;
    flex: 1;
}

.header-main h2 {
    font-size: 22px;
    font-weight: 650;
    margin: 0;
}

.connection-status {
    font-size: 12px;
    padding: 4px 12px;
    border-radius: 20px;
    background: rgba(231, 76, 60, 0.08);
    color: #c62828;
    display: flex;
    align-items: center;
    gap: 6px;
}

.connection-status.connected {
    background: rgba(39, 174, 96, 0.08);
    color: #2e7d32;
}

.conn-dot {
    width: 6px;
    height: 6px;
    border-radius: 50%;
    background: currentColor;
}

.workspace-layout {
    display: flex;
    flex: 1;
    gap: 14px;
    overflow: hidden;
}

.panel {
    background: var(--color-surface);
    border-radius: var(--radius-lg);
    padding: 18px;
    border: 1px solid var(--color-border);
    overflow: hidden;
    display: flex;
    flex-direction: column;
}

.trace-panel { width: 260px; min-width: 220px; }
.chat-panel { flex: 1; }
.tool-panel { width: 270px; min-width: 220px; }

@media (max-width: 1000px) {
    .workspace-layout { flex-direction: column; }
    .trace-panel, .tool-panel { width: 100%; max-height: 200px; }
}

.panel-header-row {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 10px;
    flex-shrink: 0;
}

.panel h3 {
    font-size: 14px;
    font-weight: 600;
    margin: 0;
}

.btn-tertiary {
    padding: 3px 10px;
    border-radius: 6px;
    border: none;
    background: none;
    cursor: pointer;
    font-size: 12px;
    color: var(--color-text-secondary);
}

.btn-tertiary:hover {
    background: rgba(0,0,0,0.04);
    box-shadow: none;
}

.trace-content, .tool-monitor-content {
    flex: 1;
    overflow-y: auto;
}

.empty-state {
    text-align: center;
    padding: 30px 10px;
    color: var(--color-text-secondary);
}

.empty-state p {
    font-size: 14px;
    font-weight: 500;
}

.empty-hint {
    font-size: 12px;
    opacity: 0.6;
}

.trace-item {
    padding: 10px 12px;
    border-radius: var(--radius-sm);
    margin-bottom: 6px;
    border-left: 3px solid var(--color-accent);
    background: var(--accent-bg);
}

.trace-item.error { border-left-color: var(--color-danger); background: rgba(198, 40, 40, 0.04); }
.trace-item.policy { border-left-color: #f39c12; background: rgba(243, 156, 18, 0.04); }

.trace-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 4px;
}

.trace-type-badge {
    font-size: 11px;
    font-weight: 600;
    padding: 1px 8px;
    border-radius: 4px;
}

.trace-type-badge.llm { background: #e3f2fd; color: #1565c0; }
.trace-type-badge.tool { background: #e8f5e9; color: #2e7d32; }
.trace-type-badge.policy { background: #fff3e0; color: #ef6c00; }
.trace-type-badge.error { background: #ffebee; color: #c62828; }

.trace-time {
    font-size: 11px;
    color: var(--color-text-secondary);
}

.trace-detail {
    font-size: 13px;
    line-height: 1.4;
    margin: 0;
}

.trace-meta {
    font-size: 11px;
    color: var(--color-text-secondary);
    margin-top: 4px;
}

.tool-call-item {
    padding: 10px 12px;
    border-radius: var(--radius-sm);
    margin-bottom: 6px;
    border: 1px solid var(--color-border);
    background: var(--color-surface);
}

.tool-call-item.running { border-color: #e3f2fd; background: #f8fbff; }
.tool-call-item.success { border-color: #e8f5e9; }
.tool-call-item.error { border-color: #ffebee; }

.tool-call-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 4px;
}

.tool-call-name {
    font-size: 12px;
    font-weight: 600;
}

.tool-call-status {
    font-size: 11px;
    font-weight: 500;
    padding: 1px 8px;
    border-radius: 4px;
}

.tool-call-status.running { background: #fff3e0; color: #ef6c00; }
.tool-call-status.success { background: #e8f5e9; color: #2e7d32; }
.tool-call-status.error { background: #ffebee; color: #c62828; }

.tool-call-params, .tool-call-result {
    font-size: 12px;
    margin-top: 4px;
}

.param-label {
    color: var(--color-text-secondary);
    font-size: 11px;
}

.tool-call-params code, .tool-call-result code {
    font-size: 11px;
    color: var(--color-text-secondary);
    word-break: break-all;
}

.tool-call-meta {
    display: flex;
    justify-content: space-between;
    font-size: 11px;
    color: var(--color-text-secondary);
    margin-top: 4px;
}
</style>