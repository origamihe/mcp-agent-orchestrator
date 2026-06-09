<template>
    <div class="model-selector">
        <label class="selector-label">模型：</label>
        <select
            :value="selectedModelId"
            @change="handleChange"
            class="selector-dropdown"
        >
            <option value="">使用后端默认模型</option>
            <option
                v-for="model in models"
                :key="model.configId"
                :value="model.configId"
            >
                {{ model.provider }} / {{ model.modelName }}
            </option>
        </select>
    </div>
</template>

<script setup lang="ts">
import type { LlmModelInfo } from '@/types/llm'

defineProps<{
    selectedModelId: string
    models: LlmModelInfo[]
}>()

const emit = defineEmits<{
    (e: 'update:selectedModelId', value: string): void
}>()

function handleChange(event: Event) {
    const target = event.target as HTMLSelectElement
    emit('update:selectedModelId', target.value)
}
</script>

<style scoped>
.model-selector {
    display: flex;
    align-items: center;
    gap: 8px;
}

.selector-label {
    font-size: 14px;
    color: var(--color-text-secondary);
    white-space: nowrap;
}

.selector-dropdown {
    padding: 6px 12px;
    border: 1px solid var(--color-border);
    border-radius: var(--radius-sm);
    background: var(--color-surface);
    font-size: 14px;
    color: var(--color-text);
    cursor: pointer;
    outline: none;
    min-width: 200px;
}

.selector-dropdown:focus {
    border-color: var(--color-accent);
}
</style>