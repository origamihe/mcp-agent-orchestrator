<template>
    <div class="ppt-panel">
        <div class="ppt-header">
            <h3><PresentationChartBarIcon /> 制作 PPT</h3>
            <p class="ppt-desc">描述您需要的演示文稿内容，Agent 将为您生成</p>
        </div>
        <div class="ppt-form">
            <div class="form-group">
                <label>PPT 标题</label>
                <input v-model="title" placeholder="请输入 PPT 标题" :disabled="!isConnected || isProcessing" />
            </div>
            <div class="form-group">
                <label>内容描述</label>
                <textarea
                    v-model="description"
                    placeholder="请描述每页幻灯片的内容..."
                    rows="6"
                    :disabled="!isConnected || isProcessing"
                ></textarea>
            </div>
            <button
                @click="handleGenerate"
                :disabled="!isConnected || !title.trim() || isProcessing"
            >
                生成 PPT
            </button>
        </div>
        <div v-if="downloadUrl" class="ppt-download">
            <a :href="downloadUrl" target="_blank" class="download-link"><ArrowDownTrayIcon /> 下载生成的 PPT</a>
        </div>
        <div v-if="isProcessing" class="ppt-loading">
            <em>正在生成 PPT...</em>
        </div>
    </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { PresentationChartBarIcon, ArrowDownTrayIcon } from '@heroicons/vue/24/outline'

const props = defineProps<{
    isConnected: boolean
    selectedModelId: string
}>()

const emit = defineEmits<{
    (e: 'send-message', payload: Record<string, unknown>): void
}>()

const title = ref('')
const description = ref('')
const downloadUrl = ref('')
const isProcessing = ref(false)

function handleGenerate() {
    if (!props.isConnected || !title.value.trim() || isProcessing.value) return
    isProcessing.value = true
    downloadUrl.value = ''
    emit('send-message', {
        message: `PPT 标题: ${title.value.trim()}\n内容: ${description.value.trim()}`,
        modelConfigId: props.selectedModelId || null,
        featureId: 'ppt-generator',
        parameters: { title: title.value.trim(), content: description.value.trim() },
    })
}

function setDownloadUrl(url: string) {
    downloadUrl.value = url
}

function markDone() {
    isProcessing.value = false
}

defineExpose({ setDownloadUrl, markDone })
</script>

<style scoped>
.ppt-panel {
    padding: 30px;
    height: 100%;
    display: flex;
    flex-direction: column;
    background: var(--gradient-subtle);
}

.ppt-header {
    margin-bottom: 24px;
}

.ppt-header h3 {
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

.ppt-header h3 :deep(svg) {
    width: 32px;
    height: 32px;
}

.ppt-desc {
    color: var(--color-text-secondary);
    font-size: 14px;
}

.ppt-form {
    display: flex;
    flex-direction: column;
    gap: 16px;
    max-width: 600px;
    background: var(--glass-bg);
    backdrop-filter: var(--glass-blur);
    -webkit-backdrop-filter: var(--glass-blur);
    border: 1.5px solid var(--glass-border);
    border-radius: var(--radius-xl);
    padding: 28px;
    box-shadow: var(--glass-shadow);
}

.form-group {
    display: flex;
    flex-direction: column;
    gap: 6px;
}

.form-group label {
    font-weight: 500;
    font-size: 14px;
}

.form-group input,
.form-group textarea {
    padding: 10px 14px;
    border: 1px solid var(--color-border);
    border-radius: var(--radius-md);
    font-size: 15px;
    outline: none;
    font-family: inherit;
}

.form-group input:focus,
.form-group textarea:focus {
    border-color: var(--color-accent);
}

.form-group textarea {
    resize: vertical;
}

.ppt-form button {
    align-self: flex-start;
    padding: 10px 32px;
    background: var(--gradient-dream);
    color: #fff;
    border: none;
    border-radius: var(--radius-md);
    cursor: pointer;
    font-size: 15px;
    margin-top: 8px;
    box-shadow: 0 4px 16px rgba(106,133,255,0.25);
}

.ppt-form button:disabled {
    opacity: 0.5;
    cursor: not-allowed;
}

.ppt-download {
    margin-top: 24px;
    padding: 16px 20px;
    background: rgba(255,255,255,0.55);
    backdrop-filter: blur(10px);
    border: 1.5px solid rgba(255,255,255,0.6);
    border-radius: var(--radius-xl);
    box-shadow: var(--glass-shadow);
}

.download-link {
    color: #667eea;
    font-weight: 600;
    text-decoration: none;
    display: flex;
    align-items: center;
    gap: 6px;
}

.download-link:hover {
    text-decoration: underline;
}

.download-link :deep(svg) {
    width: 16px;
    height: 16px;
}

.ppt-loading {
    text-align: center;
    padding: 20px;
    color: var(--color-text-secondary);
}
</style>