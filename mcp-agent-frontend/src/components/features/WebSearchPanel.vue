<template>
    <div class="web-search-panel">
        <div class="search-header">
            <h3><MagnifyingGlassIcon /> 联网搜索</h3>
            <p class="search-desc">输入关键词，Agent 将联网搜索并汇总结果</p>
        </div>
        <div class="search-input-area">
            <input
                v-model="searchQuery"
                @keyup.enter="handleSearch"
                placeholder="请输入搜索关键词..."
                :disabled="!isConnected || isProcessing"
            />
            <button
                @click="handleSearch"
                :disabled="!isConnected || !searchQuery.trim() || isProcessing"
            >
                搜索
            </button>
        </div>
        <div class="search-results" v-if="searchResult">
            <div class="result-content">{{ searchResult }}</div>
        </div>
        <div v-if="isProcessing" class="search-loading">
            <em>正在搜索中...</em>
        </div>
    </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { MagnifyingGlassIcon } from '@heroicons/vue/24/outline'

const props = defineProps<{
    isConnected: boolean
    selectedModelId: string
}>()

const emit = defineEmits<{
    (e: 'send-message', payload: Record<string, unknown>): void
}>()

const searchQuery = ref('')
const searchResult = ref('')
const isProcessing = ref(false)

function handleSearch() {
    if (!props.isConnected || !searchQuery.value.trim() || isProcessing.value) return
    isProcessing.value = true
    searchResult.value = ''
    emit('send-message', {
        message: searchQuery.value.trim(),
        modelConfigId: props.selectedModelId || null,
        featureId: 'web-search',
    })
}

function addResult(content: string) {
    searchResult.value += content
}

function markDone() {
    isProcessing.value = false
}

defineExpose({ addResult, markDone })
</script>

<style scoped>
.web-search-panel {
    padding: 30px;
    height: 100%;
    display: flex;
    flex-direction: column;
    background: var(--gradient-subtle);
}

.search-header {
    margin-bottom: 24px;
}

.search-header h3 {
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

.search-header h3 :deep(svg) {
    width: 32px;
    height: 32px;
}

.search-desc {
    color: var(--color-text-secondary);
    font-size: 14px;
}

.search-input-area {
    display: flex;
    gap: 10px;
    margin-bottom: 24px;
    background: var(--glass-bg);
    backdrop-filter: var(--glass-blur);
    -webkit-backdrop-filter: var(--glass-blur);
    border: 1.5px solid var(--glass-border);
    border-radius: var(--radius-xl);
    padding: 20px;
    box-shadow: var(--glass-shadow);
}

.search-input-area input {
    flex: 1;
    padding: 12px 16px;
    border: 1px solid var(--color-border);
    border-radius: var(--radius-md);
    font-size: 16px;
    outline: none;
}

.search-input-area input:focus {
    border-color: var(--color-accent);
}

.search-input-area button {
    padding: 0 24px;
    background: var(--gradient-dream);
    color: #fff;
    border: none;
    border-radius: var(--radius-md);
    cursor: pointer;
    font-size: 15px;
    box-shadow: 0 4px 16px rgba(106,133,255,0.25);
}

.search-input-area button:disabled {
    opacity: 0.5;
    cursor: not-allowed;
}

.search-results {
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

.search-loading {
    text-align: center;
    padding: 20px;
    color: var(--color-text-secondary);
}
</style>