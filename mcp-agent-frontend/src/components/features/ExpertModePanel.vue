<template>
    <div class="expert-panel">
        <div class="expert-header">
            <h3><CpuChipIcon /> 专家模式</h3>
            <p class="expert-desc">多 Agent 协作，深入分析复杂任务</p>
        </div>
        <div class="expert-input-area">
            <textarea
                v-model="taskDescription"
                placeholder="请详细描述您的任务需求..."
                rows="4"
                :disabled="!isConnected || isProcessing"
            ></textarea>
            <button
                @click="handleExecute"
                :disabled="!isConnected || !taskDescription.trim() || isProcessing"
            >
                执行任务
            </button>
        </div>
        <div class="expert-result" v-if="result">
            <div class="result-content">{{ result }}</div>
        </div>
        <div v-if="isProcessing" class="expert-loading">
            <em>Agent 团队正在协作处理中...</em>
        </div>
    </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { CpuChipIcon } from '@heroicons/vue/24/outline'

const props = defineProps<{
    isConnected: boolean
    selectedModelId: string
}>()

const emit = defineEmits<{
    (e: 'send-message', payload: Record<string, unknown>): void
}>()

const taskDescription = ref('')
const result = ref('')
const isProcessing = ref(false)

function handleExecute() {
    if (!props.isConnected || !taskDescription.value.trim() || isProcessing.value) return
    isProcessing.value = true
    result.value = ''
    emit('send-message', {
        message: taskDescription.value.trim(),
        modelConfigId: props.selectedModelId || null,
        featureId: 'expert-mode',
    })
}

function addResult(content: string) {
    result.value += content
}

function markDone() {
    isProcessing.value = false
}

defineExpose({ addResult, markDone })
</script>

<style scoped>
.expert-panel {
    padding: 30px;
    height: 100%;
    display: flex;
    flex-direction: column;
    background: var(--gradient-subtle);
}

.expert-header {
    margin-bottom: 24px;
}

.expert-header h3 {
    font-size: 22px;
    margin-bottom: 8px;
    display: flex;
    align-items: center;
    gap: 10px;
    font-weight: 700;
    background: var(--gradient-dream);
    -webkit-background-clip: text;
    -webkit-text-fill-color: transparent;
    background-clip: text;
}

.expert-header h3 :deep(svg) {
    width: 32px;
    height: 32px;
}

.expert-desc {
    color: var(--color-text-secondary);
    font-size: 14px;
}

.expert-input-area {
    display: flex;
    flex-direction: column;
    gap: 12px;
    margin-bottom: 24px;
    background: var(--glass-bg);
    backdrop-filter: var(--glass-blur);
    -webkit-backdrop-filter: var(--glass-blur);
    border: 1.5px solid var(--glass-border);
    border-radius: var(--radius-xl);
    padding: 24px;
    box-shadow: var(--glass-shadow);
}

.expert-input-area textarea {
    padding: 12px 16px;
    border: 1px solid var(--color-border);
    border-radius: var(--radius-md);
    font-size: 15px;
    outline: none;
    resize: vertical;
    font-family: inherit;
}

.expert-input-area textarea:focus {
    border-color: var(--color-accent);
}

.expert-input-area button {
    align-self: flex-start;
    padding: 10px 32px;
    background: var(--gradient-dream);
    color: #fff;
    border: none;
    border-radius: var(--radius-md);
    cursor: pointer;
    font-size: 15px;
    box-shadow: 0 4px 16px rgba(106,133,255,0.25);
}

.expert-input-area button:disabled {
    opacity: 0.5;
    cursor: not-allowed;
}

.expert-result {
    flex: 1;
    overflow-y: auto;
    background: rgba(255,255,255,0.55);
    backdrop-filter: blur(10px);
    border: 1.5px solid rgba(255,255,255,0.6);
    border-radius: var(--radius-xl);
    padding: 20px;
    box-shadow: var(--glass-shadow);
}

.result-content {
    white-space: pre-wrap;
    line-height: 1.6;
}

.expert-loading {
    text-align: center;
    padding: 20px;
    color: var(--color-text-secondary);
}
</style>