<template>
    <div class="chat-panel">
        <div class="chat-header">
            <span class="chat-title"><ChatBubbleLeftRightIcon /> 智能对话</span>
            <span v-if="enableExpertMode" class="expert-active-badge">🐝 专家模式 · 深度思考中</span>
            <div class="header-actions">
                <button class="btn-history" @click="showHistoryPanel = !showHistoryPanel" title="历史记录">
                    <ClockIcon /> 历史
                </button>
                <button class="btn-clear" @click="handleClearChat" title="清空当前对话">
                    <TrashIcon />
                </button>
            </div>
            <div class="model-selector" v-if="props.models && props.models.length > 0">
                <label>模型：</label>
                <select :value="props.selectedModelId" @change="$emit('update:selectedModelId', ($event.target as HTMLSelectElement).value)">
                    <option v-for="m in props.models" :key="m.configId" :value="m.configId">{{ m.provider }} / {{ m.modelName }}</option>
                </select>
            </div>
            <span v-else-if="currentModelLabel" class="model-badge">{{ currentModelLabel }}</span>
        </div>
        <div class="messages" ref="messagesContainer">
            <div
                v-for="msg in messages"
                :key="msg.id"
                :class="['message', msg.role]"
            >
                <button class="msg-delete-btn" @click="removeMessage(msg.id)" title="删除此消息">&times;</button>
                <strong>{{ msg.role === 'user' ? '你' : 'Agent' }}:</strong>
                <div class="message-content" v-html="renderContent(msg.content)"></div>
                <span class="message-time">{{ formatTimestamp(msg.timestamp) }}</span>
            </div>
            <div v-if="isProcessing" class="message assistant typing">
                <em>{{ typingText }}</em>
            </div>
        </div>
        <div class="role-selector" v-if="availableRoles.length > 0">
            <label><UserGroupIcon /> 角色：</label>
            <select v-model="selectedRole">
                <option value="">不使用角色（默认）</option>
                <option v-for="r in availableRoles" :key="r.name" :value="r.name">{{ r.name }}</option>
            </select>
            <span v-if="selectedRole" class="role-active">当前：{{ selectedRole }}</span>
        </div>
        <div class="input-area">
            <label class="web-search-toggle" :class="{ active: enableWebSearch }">
                <input type="checkbox" v-model="enableWebSearch" />
                <MagnifyingGlassIcon class="toggle-icon" />
                <span>联网搜索</span>
            </label>
            <label class="expert-mode-toggle" :class="{ active: enableExpertMode }">
                <input type="checkbox" v-model="enableExpertMode" />
                <CpuChipIcon class="toggle-icon" />
                <span>专家模式</span>
            </label>
            <input
                v-model="inputText"
                @keyup.enter="handleSend"
                :placeholder="inputPlaceholder"
                :disabled="!isConnected"
            />
            <button @click="handleSend" :disabled="!isConnected || !inputText.trim() || isProcessing">
                {{ enableExpertMode ? '深度分析' : enableWebSearch ? '搜索' : '发送' }}
            </button>
        </div>
        <div v-if="showHistoryPanel" class="history-overlay" @click.self="showHistoryPanel = false">
            <div class="history-panel">
                <div class="history-panel-header">
                    <h4><ClockIcon /> 历史会话</h4>
                    <button class="btn-close" @click="showHistoryPanel = false">&times;</button>
                </div>
                <div v-if="sessions.length === 0" class="history-empty">暂无历史会话</div>
                <div v-for="s in sessions" :key="s.sessionId" class="history-item">
                    <div class="history-item-info" @click="handleLoadSession(s.sessionId)">
                        <span class="history-item-id" :title="s.firstMessage || s.sessionId">{{ truncateTitle(s.firstMessage) || truncateId(s.sessionId) }}</span>
                        <span class="history-item-meta">{{ s.messageCount }} 条消息 · {{ formatSessionDate(s.lastActiveAt) }}</span>
                    </div>
                    <button class="btn-delete-session" @click.stop="handleDeleteSession(s.sessionId)" title="删除会话">
                        <TrashIcon />
                    </button>
                </div>
                <div v-if="isLoadingHistory" class="history-loading">加载中...</div>
            </div>
        </div>
    </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, nextTick, onMounted } from 'vue'
import { useChatHistory } from '@/composables/useChatHistory'
import { useAgentTask } from '@/composables/useAgentTask'
import type { WebSocketMessage, PromptInfo } from '@/types/agent'
import { formatTimestamp } from '@/utils/format'
import http from '@/utils/request'
import { ChatBubbleLeftRightIcon, UserGroupIcon, CpuChipIcon, ClockIcon, TrashIcon, MagnifyingGlassIcon } from '@heroicons/vue/24/outline'

const props = defineProps<{
    isConnected: boolean
    selectedModelId: string
    models?: Array<{ configId: string; provider: string; modelName: string }>
    selectedRole?: string
}>()

const emit = defineEmits<{
    (e: 'send-message', payload: WebSocketMessage): void
    (e: 'processing', value: boolean): void
    (e: 'update:selectedModelId', value: string): void
    (e: 'update:selectedRole', value: string): void
}>()

const { messages, sessions, isLoadingHistory, addMessage: _addMessage, removeMessage, clearHistory, fetchSessions, loadSession, deleteSession } = useChatHistory()
const { isProcessing, executeTask } = useAgentTask((payload) => {
    emit('send-message', payload)
})

function addMessage(role: 'user' | 'assistant' | 'system', content: string) {
    if (role === 'assistant' && expertPhase.value > 0) {
        if (expertPhase.value === 3) {
            _addMessage('assistant', content)
            scrollToBottom()
        }
        handleExpertPhase(content)
        return
    }
    _addMessage(role, content)
    scrollToBottom()
}

const inputText = ref('')
const enableWebSearch = ref(false)
const enableExpertMode = ref(false)
const expertPhase = ref(0)
const expertContext = ref({ question: '', analysis: '', critique: '' })
const messagesContainer = ref<HTMLElement | null>(null)
const availableRoles = ref<PromptInfo[]>([])
const showHistoryPanel = ref(false)
const selectedRole = computed({
    get: () => props.selectedRole ?? '',
    set: (val) => emit('update:selectedRole', val)
})

const currentModelLabel = computed(() => {
    if (!props.selectedModelId) return null
    const model = props.models?.find(m => m.configId === props.selectedModelId)
    return model ? `${model.provider} / ${model.modelName}` : '模型已选中'
})

const inputPlaceholder = computed(() => {
    if (enableExpertMode.value) return '🐝 专家模式：请详细描述您的问题... (按 Enter 发送)'
    if (enableWebSearch.value) return '输入搜索关键词... (按 Enter 搜索)'
    return '输入消息... (按 Enter 发送)'
})

defineExpose({ addMessage, clearHistory, markDone: () => { if (expertPhase.value === 0) isProcessing.value = false } })

// ========== 专家模式：三阶段 Prompt 构建 ==========

function buildExpertPhase1Prompt(question: string): string {
    return `你是一位资深领域专家，请对以下问题进行深度分析。

## 分析要求
1. **拆解问题**：将问题分解为核心组成部分，识别关键概念
2. **多角度分析**：从至少3个不同维度或视角分析问题
3. **推理过程**：展示你的思考链路，包含关键假设和逻辑推导
4. **边界条件**：识别问题的边界、局限性和适用范围
5. **初步结论**：给出结构化的初步分析结果

## 用户问题
${question}

请开始你的深度分析：`
}

function buildExpertPhase2Prompt(): string {
    return `请以最严格的标准审视你上面给出的分析，找出所有可以改进的地方。

## 自审清单
1. **逻辑漏洞**：是否存在推理跳跃、循环论证或自相矛盾之处？
2. **信息缺失**：遗漏了哪些关键信息、数据或背景知识？
3. **视角盲区**：忽略了哪些重要的替代观点或反对意见？
4. **假设问题**：哪些假设可能不成立或被挑战？
5. **精度不足**：哪些部分需要更精确的表述或更深入的阐述？
6. **实用性**：结论是否具有可操作性？缺少什么实践指导？

请逐条指出具体问题，并说明应如何改进。要坦诚、具体，这是自我提升的关键步骤。`
}

function buildExpertPhase3Prompt(): string {
    return `基于你上面的深度分析和自我审视，请输出最终的精炼回答。

## 最终回答要求
1. **修正错误**：纠正自审中发现的所有问题和错误
2. **补充完善**：整合缺失的视角和信息
3. **结构清晰**：使用标题、列表等组织内容，层次分明
4. **权威专业**：语言精准、结论明确、有理有据
5. **可执行性**：提供具体的建议、步骤或行动方案
6. **总结摘要**：在末尾给出 TL;DR 一句话总结

请输出最终回答：`
}

// ==========================================

function handleSend() {
    if (!props.isConnected || !inputText.value.trim() || isProcessing.value) return
    const msg = inputText.value.trim()
    const hasUrl = /https?:\/\/[^\s，。；！？、"'<>`]+/.test(msg)

    if (enableExpertMode.value) {
        expertPhase.value = 1
        expertContext.value.question = msg
        addMessage('user', '🐝 专家模式: ' + msg)
        const phase1Prompt = buildExpertPhase1Prompt(msg)
        executeTask('chat', phase1Prompt, props.selectedModelId || undefined, undefined, selectedRole.value || undefined)
    } else {
        const feature = (enableWebSearch.value || hasUrl) ? 'web-search' : 'chat'
        const prefix = (enableWebSearch.value || hasUrl) ? '🔍 联网搜索: ' : ''
        addMessage('user', prefix + msg)
        executeTask(feature, msg, props.selectedModelId || undefined, undefined, selectedRole.value || undefined)
    }
    inputText.value = ''
}

// 简易 Markdown 渲染：将纯文本转为支持换行和基础格式的 HTML
function sanitizeUrl(url: string): string | null {
    if (!url) return null
    const trimmed = url.trim()
    const lower = trimmed.toLowerCase()
    if (lower.startsWith('javascript:') || lower.startsWith('data:') || lower.startsWith('vbscript:')) {
        return null
    }
    return trimmed.replace(/"/g, '&quot;').replace(/'/g, '&#39;')
}

function renderContent(text: string): string {
    if (!text) return ''
    let html = text
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
    html = html.replace(/```(\w*)\n?([\s\S]*?)```/g, '<pre><code>$2</code></pre>')
    html = html.replace(/`([^`]+)`/g, '<code>$1</code>')
    html = html.replace(/\*\*(.+?)\*\*/g, '<b>$1</b>')
    html = html.replace(/\*(.+?)\*/g, '<i>$1</i>')
    html = html.replace(/\[(.+?)\]\((.+?)\)/g, (_: string, text: string, url: string) => {
        const safe = sanitizeUrl(url)
        return safe ? `<a href="${safe}" target="_blank" rel="noopener noreferrer">${text}</a>` : text
    })
    html = html.replace(/^### (.+)$/gm, '<h4>$1</h4>')
    html = html.replace(/^## (.+)$/gm, '<h3>$1</h3>')
    html = html.replace(/^# (.+)$/gm, '<h2>$1</h2>')
    html = html.replace(/^\|(.+)\|$/gm, (match) => {
        const cells = match.split('|').filter(c => c.trim())
        if (cells.length < 2) return match
        const tag = match.includes('---') ? '' : '<tr>' + cells.map(c => `<td>${c.trim()}</td>`).join('') + '</tr>'
        return tag || match
    })
    html = html.replace(/(<\/tr>\n?<tr>)/g, '</tr><tr>')
    if (html.includes('<td>')) {
        html = '<table>' + html + '</table>'
    }
    html = html.replace(/\n/g, '<br>')
    return html
}

function handleClearChat() {
    clearHistory()
}

function handleLoadSession(sessionId: string) {
    loadSession(sessionId)
    showHistoryPanel.value = false
}

function handleDeleteSession(sessionId: string) {
    deleteSession(sessionId)
}

function formatSessionDate(dateStr: string): string {
    if (!dateStr) return ''
    const d = new Date(dateStr)
    return d.toLocaleString('zh-CN', { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' })
}

function truncateId(id: string): string {
    if (!id) return ''
    return id.length > 12 ? id.substring(0, 12) + '...' : id
}

function truncateTitle(text?: string): string {
    if (!text) return ''
    return text.length > 20 ? text.substring(0, 20) + '...' : text
}

function scrollToBottom() {
    nextTick(() => {
        messagesContainer.value?.scrollTo({
            top: messagesContainer.value.scrollHeight,
            behavior: 'smooth',
        })
    })
}

const typingText = computed(() => {
    if (enableExpertMode.value && expertPhase.value > 0) {
        const labels = ['', '🐝 Phase 1/3 · 深度分析中...', '🔍 Phase 2/3 · 自我审视中...', '✨ Phase 3/3 · 最终合成中...']
        return labels[expertPhase.value] || 'Agent 正在思考...'
    }
    return 'Agent 正在思考...'
})

function handleExpertPhase(content: string) {
    if (expertPhase.value === 1) {
        expertContext.value.analysis = content
        expertPhase.value = 2
        nextTick(() => { executeTask('expert-mode', buildExpertPhase2Prompt(), props.selectedModelId || undefined) })
    } else if (expertPhase.value === 2) {
        expertContext.value.critique = content
        expertPhase.value = 3
        nextTick(() => { executeTask('expert-mode', buildExpertPhase3Prompt(), props.selectedModelId || undefined) })
    } else if (expertPhase.value === 3) {
        expertPhase.value = 0
    }
}

watch(enableWebSearch, (val) => {
    if (val) enableExpertMode.value = false
})

watch(enableExpertMode, (val) => {
    if (val) enableWebSearch.value = false
})

watch(
    () => messages.value.length,
    () => scrollToBottom(),
)

watch(
    () => isProcessing.value,
    (val) => emit('processing', val),
)

async function fetchRoles() {
    try {
        const all = (await http.get('/mcp/prompts')) as unknown as PromptInfo[]
        availableRoles.value = (all ?? []).filter(p => p.type === 'agent_specific')
    } catch { /* ignore */ }
}

onMounted(() => {
    fetchRoles()
    fetchSessions()
})

watch(() => props.models, (models) => {
    if (models && models.length > 0 && !props.selectedModelId) {
        emit('update:selectedModelId', models[0].configId)
    }
}, { immediate: true })
</script>

<style scoped>
.chat-panel {
    display: flex;
    flex-direction: column;
    height: 100%;
    background: var(--gradient-subtle);
}

.chat-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 12px 24px;
    border-bottom: 1.5px solid rgba(255,255,255,0.5);
    background: rgba(255,255,255,0.55);
    backdrop-filter: blur(20px);
    -webkit-backdrop-filter: blur(20px);
}

.chat-title {
    font-size: 16px;
    font-weight: 700;
    color: var(--color-text);
    display: flex;
    align-items: center;
    gap: 8px;
    background: var(--gradient-dream);
    -webkit-background-clip: text;
    -webkit-text-fill-color: transparent;
    background-clip: text;
}

.model-badge {
    padding: 5px 14px;
    background: #f0f2f5;
    border-radius: 9999px;
    font-size: 12px;
    color: var(--color-text-secondary);
    font-weight: 500;
}

.model-selector {
    display: flex;
    align-items: center;
    gap: 6px;
}
.model-selector label {
    font-size: 12px;
    color: var(--color-text-secondary);
    white-space: nowrap;
}
.model-selector select {
    padding: 4px 10px;
    border: 1px solid var(--color-border);
    border-radius: var(--radius-sm);
    background: var(--color-surface);
    font-size: 13px;
    color: var(--color-text);
    cursor: pointer;
    outline: none;
}

.model-selector {
    display: flex;
    align-items: center;
    gap: 6px;
}
.model-selector label {
    font-size: 12px;
    color: var(--color-text-secondary);
    white-space: nowrap;
}
.model-selector select {
    padding: 4px 10px;
    border: 1px solid var(--color-border);
    border-radius: var(--radius-sm);
    background: var(--color-surface);
    font-size: 13px;
    color: var(--color-text);
    cursor: pointer;
    outline: none;
}

.messages {
    flex: 1;
    overflow-y: auto;
    padding: 20px;
    display: flex;
    flex-direction: column;
    gap: 15px;
}

.message {
    max-width: 75%;
    padding: 12px 16px;
    border-radius: var(--radius-lg);
    position: relative;
}

.message.user {
    align-self: flex-end;
    background: var(--gradient-dream);
    color: #fff;
    box-shadow: 0 4px 16px rgba(106,133,255,0.25);
}

.message.assistant {
    align-self: flex-start;
    background: rgba(255,255,255,0.7);
    backdrop-filter: blur(10px);
    -webkit-backdrop-filter: blur(10px);
    border: 1.5px solid rgba(255,255,255,0.6);
    box-shadow: 0 4px 16px rgba(0,0,0,0.04);
}

.message.typing {
    opacity: 0.7;
}

.message {
    position: relative;
}

.msg-delete-btn {
    position: absolute;
    top: -6px;
    right: -6px;
    width: 18px;
    height: 18px;
    background: rgba(239, 68, 68, 0.9);
    color: #fff;
    border: none;
    border-radius: 50%;
    font-size: 14px;
    line-height: 16px;
    cursor: pointer;
    display: none;
    padding: 0;
    align-items: center;
    justify-content: center;
}

.message:hover .msg-delete-btn {
    display: flex;
}

.msg-delete-btn:hover {
    background: rgba(220, 38, 38, 1);
}

.message-time {
    display: block;
    font-size: 11px;
    margin-top: 4px;
    opacity: 0.6;
}

.message-content {
    line-height: 1.7;
    word-break: break-word;
}
.message-content :deep(h2) { font-size: 18px; margin: 12px 0 6px; font-weight: 700; }
.message-content :deep(h3) { font-size: 16px; margin: 10px 0 5px; font-weight: 600; }
.message-content :deep(h4) { font-size: 14px; margin: 8px 0 4px; font-weight: 600; }
.message-content :deep(p) { margin: 6px 0; }
.message-content :deep(b) { font-weight: 700; }
.message-content :deep(i) { font-style: italic; }
.message-content :deep(a) { color: #2563eb; text-decoration: underline; }
.message-content :deep(code) {
    background: rgba(0,0,0,0.06);
    padding: 2px 6px;
    border-radius: 4px;
    font-size: 0.9em;
    font-family: 'SF Mono', 'Fira Code', monospace;
}
.message-content :deep(pre) {
    background: rgba(0,0,0,0.06);
    padding: 10px 14px;
    border-radius: 8px;
    overflow-x: auto;
    margin: 8px 0;
    font-size: 0.85em;
}
.message-content :deep(pre code) {
    background: none;
    padding: 0;
}
.message-content :deep(table) {
    border-collapse: collapse;
    margin: 8px 0;
    width: 100%;
    font-size: 13px;
}
.message-content :deep(td) {
    border: 1px solid rgba(0,0,0,0.1);
    padding: 6px 10px;
}
.message-content :deep(ul), .message-content :deep(ol) {
    margin: 4px 0;
    padding-left: 20px;
}
.message.user .message-content :deep(code) {
    background: rgba(255,255,255,0.2);
}
.message.user .message-content :deep(pre) {
    background: rgba(255,255,255,0.15);
}
.message.user .message-content :deep(a) {
    color: #fff;
    text-decoration: underline;
}

.input-area {
    padding: 15px;
    background: rgba(255,255,255,0.55);
    backdrop-filter: blur(20px);
    -webkit-backdrop-filter: blur(20px);
    display: flex;
    gap: 10px;
    border-top: 1.5px solid rgba(255,255,255,0.5);
}

.role-selector {
    padding: 10px 20px;
    background: rgba(255,255,255,0.45);
    backdrop-filter: blur(10px);
    display: flex;
    align-items: center;
    gap: 8px;
    font-size: 13px;
    border-top: 1.5px solid rgba(255,255,255,0.4);
}

.role-selector :deep(svg) {
    width: 16px;
    height: 16px;
}

.chat-title :deep(svg) {
    width: 24px;
    height: 24px;
}

.header-actions {
    display: flex;
    gap: 8px;
    align-items: center;
}

.btn-history,
.btn-clear {
    display: flex;
    align-items: center;
    gap: 4px;
    padding: 6px 10px;
    border: 1px solid var(--color-border);
    border-radius: 9999px;
    background: var(--color-surface);
    font-size: 12px;
    cursor: pointer;
    color: var(--color-text-secondary);
    transition: all 0.2s;
}

.btn-history:hover,
.btn-clear:hover {
    border-color: var(--color-accent);
    color: var(--color-accent);
}

.btn-history :deep(svg) {
    width: 14px;
    height: 14px;
}

.btn-clear :deep(svg) {
    width: 14px;
    height: 14px;
}

.web-search-toggle {
    display: flex;
    align-items: center;
    gap: 5px;
    padding: 6px 12px;
    border: 1.5px solid var(--color-border);
    border-radius: var(--radius-md);
    cursor: pointer;
    font-size: 13px;
    color: var(--color-text-secondary);
    transition: all 0.25s ease;
    white-space: nowrap;
    user-select: none;
    flex-shrink: 0;
}

.web-search-toggle input[type="checkbox"] {
    display: none;
}

.web-search-toggle .toggle-icon {
    width: 16px;
    height: 16px;
}

.web-search-toggle.active {
    border-color: var(--color-accent);
    background: linear-gradient(135deg, rgba(102, 126, 234, 0.1), rgba(118, 75, 162, 0.08));
    color: var(--color-accent);
    box-shadow: 0 0 0 3px rgba(106, 133, 255, 0.12);
}

.expert-mode-toggle {
    display: flex;
    align-items: center;
    gap: 5px;
    padding: 6px 12px;
    border: 1.5px solid var(--color-border);
    border-radius: var(--radius-md);
    cursor: pointer;
    font-size: 13px;
    color: var(--color-text-secondary);
    transition: all 0.25s ease;
    white-space: nowrap;
    user-select: none;
    flex-shrink: 0;
}

.expert-mode-toggle input[type="checkbox"] {
    display: none;
}

.expert-mode-toggle .toggle-icon {
    width: 16px;
    height: 16px;
}

.expert-mode-toggle.active {
    border-color: #f59e0b;
    background: linear-gradient(135deg, rgba(245, 158, 11, 0.12), rgba(251, 191, 36, 0.08));
    color: #d97706;
    box-shadow: 0 0 0 3px rgba(245, 158, 11, 0.15);
    font-weight: 600;
}

.expert-active-badge {
    padding: 4px 14px;
    background: linear-gradient(135deg, rgba(245, 158, 11, 0.15), rgba(251, 191, 36, 0.1));
    border: 1px solid rgba(245, 158, 11, 0.3);
    border-radius: 9999px;
    font-size: 12px;
    font-weight: 600;
    color: #d97706;
    animation: expertPulse 2s ease-in-out infinite;
}

@keyframes expertPulse {
    0%, 100% { box-shadow: 0 0 0 0 rgba(245, 158, 11, 0.4); }
    50% { box-shadow: 0 0 0 6px rgba(245, 158, 11, 0); }
}

input {
    flex: 1;
    padding: 12px;
    border: 1px solid var(--color-border);
    border-radius: var(--radius-md);
    font-size: 16px;
    outline: none;
}

input:focus {
    border-color: var(--color-accent);
}

button {
    padding: 0 24px;
    background: var(--color-success);
    color: #fff;
    border: none;
    border-radius: var(--radius-md);
    cursor: pointer;
    font-size: 15px;
    transition: opacity 0.2s;
    white-space: nowrap;
    flex-shrink: 0;
}

button:disabled {
    background: var(--color-text-secondary);
    cursor: not-allowed;
}

button:not(:disabled):hover {
    opacity: 0.9;
}

.role-selector {
    display: flex;
    align-items: center;
    gap: 10px;
    padding: 10px 15px;
    background: var(--color-surface);
    border-top: 1px solid var(--color-border);
}

.role-selector label {
    font-size: 13px;
    font-weight: 500;
    color: var(--color-text-secondary);
    white-space: nowrap;
}

.role-selector select {
    padding: 6px 12px;
    border: 1px solid var(--color-border);
    border-radius: 9999px;
    font-size: 13px;
    outline: none;
    cursor: pointer;
}

.role-selector select:focus {
    border-color: var(--color-accent);
}

.role-active {
    font-size: 12px;
    color: var(--color-accent);
    font-weight: 500;
}

.history-overlay {
    position: fixed;
    inset: 0;
    background: rgba(0, 0, 0, 0.4);
    display: flex;
    align-items: center;
    justify-content: center;
    z-index: 1000;
    backdrop-filter: blur(2px);
}

.history-panel {
    background: #fff;
    border-radius: 12px;
    width: 420px;
    max-height: 70vh;
    overflow: hidden;
    display: flex;
    flex-direction: column;
    box-shadow: 0 20px 60px rgba(0, 0, 0, 0.3);
}

.history-panel-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 16px 20px;
    border-bottom: 1px solid var(--color-border);
}

.history-panel-header h4 {
    margin: 0;
    font-size: 16px;
    font-weight: 600;
    color: var(--color-text);
    display: flex;
    align-items: center;
    gap: 8px;
}

.history-panel-header h4 :deep(svg) {
    width: 20px;
    height: 20px;
}

.btn-close {
    width: 28px;
    height: 28px;
    border: none;
    background: transparent;
    font-size: 24px;
    line-height: 28px;
    cursor: pointer;
    color: var(--color-text-secondary);
    padding: 0;
    display: flex;
    align-items: center;
    justify-content: center;
    border-radius: 6px;
}

.btn-close:hover {
    background: rgba(0, 0, 0, 0.05);
    color: var(--color-text);
}

.history-empty {
    padding: 40px 20px;
    text-align: center;
    color: var(--color-text-secondary);
}

.history-item {
    display: flex;
    align-items: center;
    padding: 12px 16px;
    border-bottom: 1px solid rgba(0, 0, 0, 0.05);
    cursor: pointer;
}

.history-item:hover {
    background: rgba(106, 133, 255, 0.05);
}

.history-item-info {
    flex: 1;
    overflow: hidden;
}

.history-item-id {
    display: block;
    font-weight: 500;
    color: var(--color-text);
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
}

.history-item-meta {
    font-size: 12px;
    color: var(--color-text-secondary);
}

.btn-delete-session {
    width: 32px;
    height: 32px;
    border: none;
    background: transparent;
    cursor: pointer;
    display: flex;
    align-items: center;
    justify-content: center;
    border-radius: 6px;
    color: #ef4444;
    opacity: 0.7;
}

.btn-delete-session:hover {
    background: rgba(239, 68, 68, 0.1);
    opacity: 1;
}

.btn-delete-session :deep(svg) {
    width: 16px;
    height: 16px;
}

.history-loading {
    padding: 20px;
    text-align: center;
    color: var(--color-text-secondary);
}
</style>