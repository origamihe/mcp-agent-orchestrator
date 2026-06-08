<template>
    <div class="chat-panel">
        <div class="chat-header">
            <span class="chat-title"><ChatBubbleLeftRightIcon /> 智能对话</span>
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
                <strong>{{ msg.role === 'user' ? '你' : 'Agent' }}:</strong>
                <p>{{ msg.content }}</p>
                <span class="message-time">{{ formatTimestamp(msg.timestamp) }}</span>
            </div>
            <div v-if="isProcessing" class="message assistant typing">
                <em>Agent 正在思考...</em>
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
            <input
                v-model="inputText"
                @keyup.enter="handleSend"
                placeholder="输入消息... (按 Enter 发送)"
                :disabled="!isConnected"
            />
            <button @click="handleSend" :disabled="!isConnected || !inputText.trim()">
                发送
            </button>
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
import { ChatBubbleLeftRightIcon, UserGroupIcon } from '@heroicons/vue/24/outline'

const props = defineProps<{
    isConnected: boolean
    selectedModelId: string
    models?: Array<{ configId: string; provider: string; modelName: string }>
}>()

const emit = defineEmits<{
    (e: 'send-message', payload: WebSocketMessage): void
    (e: 'processing', value: boolean): void
    (e: 'update:selectedModelId', value: string): void
}>()

const { messages, addMessage, clearHistory } = useChatHistory()
const { isProcessing, executeTask } = useAgentTask((payload) => {
    emit('send-message', payload)
})

const inputText = ref('')
const messagesContainer = ref<HTMLElement | null>(null)
const availableRoles = ref<PromptInfo[]>([])
const selectedRole = ref('')

const currentModelLabel = computed(() => {
    if (!props.selectedModelId) return null
    const model = props.models?.find(m => m.configId === props.selectedModelId)
    return model ? `${model.provider} / ${model.modelName}` : '模型已选中'
})

defineExpose({ addMessage, clearHistory, markDone: () => { isProcessing.value = false } })

function handleSend() {
    if (!props.isConnected || !inputText.value.trim() || isProcessing.value) return
    addMessage('user', inputText.value.trim())
    executeTask('chat', inputText.value.trim(), props.selectedModelId || undefined, undefined, selectedRole.value || undefined)
    inputText.value = ''
    scrollToBottom()
}

function scrollToBottom() {
    nextTick(() => {
        messagesContainer.value?.scrollTo({
            top: messagesContainer.value.scrollHeight,
            behavior: 'smooth',
        })
    })
}

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

onMounted(fetchRoles)

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

.message-time {
    display: block;
    font-size: 11px;
    margin-top: 4px;
    opacity: 0.6;
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
</style>