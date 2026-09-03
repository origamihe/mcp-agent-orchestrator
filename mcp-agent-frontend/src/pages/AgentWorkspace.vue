<template>
    <div class="page">
        <div class="page-header">
            <button class="btn-back" @click="$router.push('/agents')">← 返回 Agent 列表</button>
            <h2>{{ agentName }} · Workspace</h2>
            <span class="connection-status" :class="{ connected: isConnected }">
                {{ isConnected ? '已连接' : '未连接' }}
            </span>
        </div>
        <div class="workspace-layout">
            <div class="panel trace-panel">
                <div class="panel-header-row">
                    <h3><ArrowPathRoundedSquareIcon class="panel-icon" /> Execution Trace</h3>
                    <button class="btn-clear" @click="traces = []" v-if="traces.length">清空</button>
                </div>
                <div class="trace-content">
                    <div v-if="traces.length === 0" class="placeholder">
                        <p>暂无执行记录</p>
                        <span class="hint">与 Agent 对话后将在此显示执行追踪</span>
                    </div>
                    <div v-for="(trace, idx) in traces" :key="idx" :class="['trace-item', trace.type]">
                        <div class="trace-header">
                            <span :class="['trace-type-badge', trace.type]">{{ traceLabel(trace.type) }}</span>
                            <span class="trace-time">{{ formatTime(trace.timestamp) }}</span>
                        </div>
                        <p class="trace-detail">{{ trace.detail }}</p>
                        <div v-if="trace.duration" class="trace-meta">
                            <span>耗时: {{ trace.duration }}ms</span>
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
                    <h3><WrenchScrewdriverIcon class="panel-icon" /> Tool Monitor</h3>
                    <button class="btn-clear" @click="toolCalls = []" v-if="toolCalls.length">清空</button>
                </div>
                <div class="tool-monitor-content">
                    <div v-if="toolCalls.length === 0" class="placeholder">
                        <p>工具调用监控</p>
                        <span class="hint">Agent 调用工具后将在此显示详情</span>
                    </div>
                    <div v-for="(call, idx) in toolCalls" :key="idx" :class="['tool-call-item', call.status]">
                        <div class="tool-call-header">
                            <span class="tool-call-name">{{ call.toolName }}</span>
                            <span :class="['tool-call-status', call.status]">{{ statusLabel(call.status) }}</span>
                        </div>
                        <div class="tool-call-params" v-if="call.params">
                            <span class="param-label">参数:</span>
                            <code>{{ truncate(call.params, 120) }}</code>
                        </div>
                        <div class="tool-call-result" v-if="call.result">
                            <span class="param-label">结果:</span>
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
import { useWebSocket } from '@/composables/useWebSocket'
import { useAgentStore } from '@/stores/agentStore'
import { useAppStore } from '@/stores/app'
import type { LlmModelInfo } from '@/types/llm'
import { ArrowPathRoundedSquareIcon, WrenchScrewdriverIcon } from '@heroicons/vue/24/outline'
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
const agentName = computed(() => agentStore.currentAgent?.agentName || agentId.value)
const selectedModelId = computed(() => appStore.selectedModelId)

const wsUrl = `${location.protocol === 'https:' ? 'wss:' : 'ws:'}//${location.host}/ws/mcp`
const { isConnected, connect, send, lastMessage } = useWebSocket(wsUrl)

function traceLabel(type: string): string {
    const labels: Record<string, string> = { llm: 'LLM', tool: '工具', policy: '策略', error: '错误' }
    return labels[type] || type
}

function statusLabel(status: string): string {
    const labels: Record<string, string> = { running: '执行中', success: '成功', error: '失败' }
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
    padding: 16px 24px;
    height: 100%;
    overflow: hidden;
    display: flex;
    flex-direction: column;
}

.page-header {
    display: flex;
    align-items: center;
    gap: 16px;
    margin-bottom: 16px;
    flex-shrink: 0;
}

.btn-back {
    padding: 6px 14px;
    border-radius: 8px;
    border: 1px solid rgba(0,0,0,0.1);
    background: rgba(255,255,255,0.7);
    cursor: pointer;
    font-size: 13px;
    color: var(--color-text-secondary);
}

.connection-status {
    font-size: 12px;
    padding: 4px 12px;
    border-radius: 20px;
    background: rgba(231, 76, 60, 0.1);
    color: #e74c3c;
    margin-left: auto;
}

.connection-status.connected {
    background: rgba(39, 174, 96, 0.1);
    color: #27ae60;
}

.workspace-layout {
    display: flex;
    flex: 1;
    gap: 16px;
    overflow: hidden;
}

.panel {
    background: rgba(255,255,255,0.7);
    backdrop-filter: blur(20px);
    border-radius: 16px;
    padding: 20px;
    border: 1px solid rgba(255,255,255,0.8);
    overflow: hidden;
    display: flex;
    flex-direction: column;
}

.panel-header-row {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 12px;
    flex-shrink: 0;
}

.panel h3 {
    font-size: 14px;
    font-weight: 600;
    display: flex;
    align-items: center;
    gap: 8px;
    margin: 0;
}

.panel-icon {
    width: 18px;
    height: 18px;
    color: #667eea;
}

.btn-clear {
    padding: 3px 10px;
    border-radius: 6px;
    border: 1px solid rgba(0,0,0,0.1);
    background: rgba(0,0,0,0.03);
    cursor: pointer;
    font-size: 12px;
    color: var(--color-text-secondary);
}

.trace-panel { flex: 1; }
.chat-panel { flex: 2; display: flex; flex-direction: column; }
.tool-panel { flex: 1; }

.chat-panel :deep(.chat-panel) {
    flex: 1;
    display: flex;
    flex-direction: column;
}

.trace-content, .tool-monitor-content {
    flex: 1;
    overflow-y: auto;
    display: flex;
    flex-direction: column;
    gap: 8px;
}

.placeholder {
    color: var(--color-text-secondary);
    font-size: 14px;
    text-align: center;
    padding: 40px 0;
}

.placeholder .hint {
    display: block;
    font-size: 12px;
    margin-top: 6px;
    opacity: 0.6;
}

.trace-item {
    padding: 10px 14px;
    border-radius: 10px;
    background: rgba(0,0,0,0.02);
    border-left: 3px solid #667eea;
}

.trace-item.llm { border-left-color: #667eea; }
.trace-item.tool { border-left-color: #27ae60; }
.trace-item.policy { border-left-color: #f39c12; }
.trace-item.error { border-left-color: #e74c3c; }

.trace-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 4px;
}

.trace-type-badge {
    font-size: 11px;
    font-weight: 600;
    padding: 2px 8px;
    border-radius: 4px;
}

.trace-type-badge.llm { background: rgba(102, 126, 234, 0.1); color: #667eea; }
.trace-type-badge.tool { background: rgba(39, 174, 96, 0.1); color: #27ae60; }
.trace-type-badge.policy { background: rgba(243, 156, 18, 0.1); color: #f39c12; }
.trace-type-badge.error { background: rgba(231, 76, 60, 0.1); color: #e74c3c; }

.trace-time {
    font-size: 11px;
    color: var(--color-text-secondary);
}

.trace-detail {
    font-size: 13px;
    line-height: 1.5;
}

.trace-meta {
    font-size: 11px;
    color: var(--color-text-secondary);
    margin-top: 4px;
}

.tool-call-item {
    padding: 10px 14px;
    border-radius: 10px;
    background: rgba(0,0,0,0.02);
    border-left: 3px solid #f39c12;
}

.tool-call-item.running { border-left-color: #f39c12; }
.tool-call-item.success { border-left-color: #27ae60; }
.tool-call-item.error { border-left-color: #e74c3c; }

.tool-call-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 6px;
}

.tool-call-name {
    font-weight: 600;
    font-size: 13px;
    font-family: monospace;
}

.tool-call-status {
    font-size: 11px;
    font-weight: 600;
    padding: 2px 8px;
    border-radius: 4px;
}

.tool-call-status.running { background: rgba(243, 156, 18, 0.1); color: #f39c12; }
.tool-call-status.success { background: rgba(39, 174, 96, 0.1); color: #27ae60; }
.tool-call-status.error { background: rgba(231, 76, 60, 0.1); color: #e74c3c; }

.tool-call-params, .tool-call-result {
    margin-bottom: 4px;
}

.param-label {
    font-size: 11px;
    color: var(--color-text-secondary);
    font-weight: 500;
}

.tool-call-params code, .tool-call-result code {
    font-size: 12px;
    display: block;
    margin-top: 2px;
    padding: 4px 8px;
    background: rgba(0,0,0,0.03);
    border-radius: 4px;
    word-break: break-all;
}

.tool-call-meta {
    display: flex;
    gap: 12px;
    font-size: 11px;
    color: var(--color-text-secondary);
    margin-top: 6px;
}
</style>