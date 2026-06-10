<template>
    <div class="docx-panel">
        <div class="docx-header">
            <h3><DocumentTextIcon /> 制作 Word 文档</h3>
            <p class="docx-desc">描述您需要的文档内容，Agent 将为您生成 Word 文档</p>
        </div>
        <div class="docx-form">
            <div class="form-group">
                <label>文档标题</label>
                <input v-model="title" placeholder="请输入文档标题" :disabled="!isConnected || isProcessing" />
            </div>
            <div class="form-group">
                <label>内容描述</label>
                <textarea
                    v-model="description"
                    placeholder="请详细描述文档内容..."
                    rows="6"
                    :disabled="!isConnected || isProcessing"
                ></textarea>
            </div>
            <button
                @click="handleGenerate"
                :disabled="!isConnected || !title.trim() || isProcessing"
            >
                生成 Word 文档
            </button>
        </div>
        <div v-if="downloadUrl" class="docx-download">
            <a :href="downloadUrl" target="_blank" class="download-link"><ArrowDownTrayIcon /> 下载生成的文档</a>
        </div>
        <div v-if="isProcessing" class="docx-loading">
            <em>正在生成 Word 文档...</em>
        </div>
    </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { DocumentTextIcon, ArrowDownTrayIcon } from '@heroicons/vue/24/outline'

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
        message: `文档标题: ${title.value.trim()}\n内容: ${description.value.trim()}`,
        modelConfigId: props.selectedModelId || null,
        featureId: 'docx-generator',
        parameters: { title: title.value.trim(), content: description.value.trim() },
    })
}

function setDownloadUrl(url: string) {
    downloadUrl.value = url
}

function markDone() {
    isProcessing.value = false
}

function addResult(msg: string) {
    isProcessing.value = false
    if (msg && msg.startsWith('/mcp/download/')) {
        downloadUrl.value = msg
    } else if (msg) {
        downloadUrl.value = msg
    }
}

defineExpose({ setDownloadUrl, markDone, addResult })
</script>

<style scoped>
.docx-panel {
    padding: 30px;
    height: 100%;
    display: flex;
    flex-direction: column;
    background: var(--gradient-subtle);
}

.docx-header {
    margin-bottom: 24px;
}

.docx-header h3 {
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

.docx-header h3 :deep(svg) {
    width: 32px;
    height: 32px;
}

.docx-desc {
    color: var(--color-text-secondary);
    font-size: 14px;
}

.docx-form {
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

.docx-form button {
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

.docx-form button:disabled {
    opacity: 0.5;
    cursor: not-allowed;
}

.docx-download {
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

.docx-loading {
    text-align: center;
    padding: 20px;
    color: var(--color-text-secondary);
}
</style>