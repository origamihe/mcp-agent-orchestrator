<template>
    <div class="status-bar">
        <div class="status-left">
            <span :class="['status-dot', statusClass]"></span>
            <span class="status-text">{{ statusText }}</span>
            <span v-if="currentModelName" class="current-model">{{ currentModelName }}</span>
        </div>
        <div class="status-right">
            <span v-if="currentModelName" class="current-model">{{ currentModelName }}</span>
        </div>
    </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { LlmModelInfo } from '@/types/llm'

const props = defineProps<{
    isConnected: boolean
    selectedModelId: string
    models: LlmModelInfo[]
}>()

const statusText = computed(() => (props.isConnected ? '已连接' : '未连接'))

const statusClass = computed(() =>
    props.isConnected ? 'status-connected' : 'status-disconnected',
)

const currentModelName = computed(() => {
    if (!props.selectedModelId) return null
    const model = props.models.find(m => m.configId === props.selectedModelId)
    return model ? `${model.provider} / ${model.modelName}` : null
})
</script>

<style scoped>
.status-bar {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 8px 20px;
    background: rgba(255,255,255,0.55);
    backdrop-filter: blur(20px);
    -webkit-backdrop-filter: blur(20px);
    border-bottom: 1.5px solid rgba(255,255,255,0.5);
    flex-shrink: 0;
}

.status-left {
    display: flex;
    align-items: center;
    gap: 8px;
}

.current-model {
    font-size: 12px;
    color: var(--color-text-secondary);
    padding: 2px 10px;
    background: #f0f2f5;
    border-radius: 9999px;
    font-weight: 500;
}

.status-dot {
    width: 8px;
    height: 8px;
    border-radius: 50%;
    display: inline-block;
}

.status-connected {
    background: var(--color-success);
}

.status-disconnected {
    background: var(--color-danger);
}

.status-text {
    font-size: 13px;
    color: var(--color-text-secondary);
}

.status-right {
    display: flex;
    align-items: center;
}
</style>