<template>
    <div class="status-bar">
        <div class="status-left">
            <span :class="['status-dot', statusClass]"></span>
            <span class="status-text">{{ statusText }}</span>
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

const statusText = computed(() => (props.isConnected ? 'Connected' : 'Disconnected'))

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
    padding: 8px 24px;
    background: var(--color-bg);
    border-bottom: 1px solid var(--color-border);
    flex-shrink: 0;
}

.status-left {
    display: flex;
    align-items: center;
    gap: 8px;
}

.status-dot {
    width: 7px;
    height: 7px;
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
    font-size: 12px;
    color: var(--color-text-secondary);
    font-weight: 500;
}

.status-right {
    display: flex;
    align-items: center;
}

.current-model {
    font-size: 12px;
    color: var(--color-text-secondary);
    padding: 3px 10px;
    background: var(--color-surface);
    border: 1px solid var(--color-border);
    border-radius: 6px;
    font-weight: 500;
}
</style>