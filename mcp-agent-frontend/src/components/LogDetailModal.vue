<template>
    <Teleport to="body">
        <div v-if="visible" class="modal-overlay" @click.self="close">
            <div class="modal-container">
                <div class="modal-header">
                    <div class="modal-title-row">
                        <span :class="['level-badge', `level-${entry.level.toLowerCase()}`]">{{ entry.level }}</span>
                        <span class="modal-time">{{ formatTime(entry.timestamp) }}</span>
                    </div>
                    <button class="modal-close" @click="close">&times;</button>
                </div>

                <div class="modal-body">
                    <div class="detail-section">
                        <h4 class="section-title">日志消息</h4>
                        <div class="message-box">{{ entry.message }}</div>
                    </div>

                    <div class="detail-grid">
                        <div class="detail-item">
                            <span class="detail-label">Logger</span>
                            <span class="detail-value">{{ entry.logger }}</span>
                        </div>
                        <div class="detail-item">
                            <span class="detail-label">Thread</span>
                            <span class="detail-value">{{ entry.thread }}</span>
                        </div>
                        <div class="detail-item" v-if="entry.agentId">
                            <span class="detail-label">Agent</span>
                            <span class="detail-value">{{ entry.agentId }}</span>
                        </div>
                        <div class="detail-item" v-if="entry.module">
                            <span class="detail-label">Module</span>
                            <span class="detail-value">{{ entry.module }}</span>
                        </div>
                        <div class="detail-item" v-if="entry.sessionId">
                            <span class="detail-label">Session</span>
                            <span class="detail-value">{{ entry.sessionId }}</span>
                        </div>
                        <div class="detail-item" v-if="entry.runId">
                            <span class="detail-label">Run</span>
                            <span class="detail-value">{{ entry.runId }}</span>
                        </div>
                    </div>
                </div>

                <div class="modal-footer">
                    <button class="btn-copy" @click="copyEntry">复制</button>
                    <button class="btn-close" @click="close">关闭</button>
                </div>
            </div>
        </div>
    </Teleport>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { LogEntry, FileLogEntry } from '@/types/log'

const props = defineProps<{
    visible: boolean
    entry: LogEntry | FileLogEntry
}>()

const emit = defineEmits<{
    (e: 'close'): void
}>()

function close() {
    emit('close')
}

function formatTime(dateStr: string): string {
    if (!dateStr) return '--'
    return new Date(dateStr).toLocaleString('zh-CN', {
        year: 'numeric', month: '2-digit', day: '2-digit',
        hour: '2-digit', minute: '2-digit', second: '2-digit',
    })
}

async function copyEntry() {
    const lines = []
    if ((props.entry as any).timestamp) lines.push(`Time: ${(props.entry as any).timestamp}`)
    if ((props.entry as any).level) lines.push(`Level: ${(props.entry as any).level}`)
    if ((props.entry as any).logger) lines.push(`Logger: ${(props.entry as any).logger}`)
    if ((props.entry as any).message) lines.push(`Message: ${(props.entry as any).message}`)
    if ((props.entry as any).thread) lines.push(`Thread: ${(props.entry as any).thread}`)
    if ((props.entry as any).agentId) lines.push(`Agent: ${(props.entry as any).agentId}`)
    if ((props.entry as any).module) lines.push(`Module: ${(props.entry as any).module}`)
    if ((props.entry as any).sessionId) lines.push(`Session: ${(props.entry as any).sessionId}`)
    if ((props.entry as any).runId) lines.push(`Run: ${(props.entry as any).runId}`)

    try {
        await navigator.clipboard.writeText(lines.join('\n'))
    } catch {
        const textarea = document.createElement('textarea')
        textarea.value = lines.join('\n')
        document.body.appendChild(textarea)
        textarea.select()
        document.execCommand('copy')
        document.body.removeChild(textarea)
    }
}
</script>

<style scoped>
.modal-overlay {
    position: fixed;
    inset: 0;
    background: rgba(0, 0, 0, 0.3);
    backdrop-filter: blur(4px);
    display: flex;
    align-items: center;
    justify-content: center;
    z-index: 1000;
    animation: fadeIn 0.15s ease;
}

.modal-container {
    background: #fff;
    border-radius: 16px;
    width: 560px;
    max-width: 90vw;
    max-height: 80vh;
    display: flex;
    flex-direction: column;
    box-shadow: 0 20px 60px rgba(0, 0, 0, 0.15);
    animation: slideUp 0.2s ease;
}

.modal-header {
    display: flex;
    align-items: flex-start;
    justify-content: space-between;
    padding: 20px 24px 12px;
    border-bottom: 1px solid #eee;
}

.modal-title-row {
    display: flex;
    align-items: center;
    gap: 12px;
}

.modal-time {
    font-size: 13px;
    color: #999;
    font-family: monospace;
}

.modal-close {
    background: none;
    border: none;
    font-size: 22px;
    color: #999;
    cursor: pointer;
    padding: 0;
    line-height: 1;
    width: 32px;
    height: 32px;
    display: flex;
    align-items: center;
    justify-content: center;
    border-radius: 8px;
}

.modal-close:hover {
    background: #f5f5f5;
    color: #333;
}

.modal-body {
    padding: 16px 24px;
    overflow-y: auto;
    flex: 1;
}

.detail-section {
    margin-bottom: 16px;
}

.section-title {
    font-size: 12px;
    font-weight: 600;
    color: #999;
    text-transform: uppercase;
    margin-bottom: 8px;
    letter-spacing: 0.5px;
}

.message-box {
    background: #f8f9fa;
    border-radius: 10px;
    padding: 14px 16px;
    font-size: 13px;
    font-family: monospace;
    line-height: 1.6;
    white-space: pre-wrap;
    word-break: break-all;
    color: #333;
    max-height: 300px;
    overflow-y: auto;
}

.detail-grid {
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: 8px;
}

.detail-item {
    padding: 8px 12px;
    background: #f8f9fa;
    border-radius: 8px;
}

.detail-label {
    font-size: 11px;
    color: #999;
    display: block;
    margin-bottom: 2px;
}

.detail-value {
    font-size: 13px;
    color: #333;
    font-family: monospace;
    word-break: break-all;
}

.modal-footer {
    display: flex;
    gap: 10px;
    justify-content: flex-end;
    padding: 16px 24px;
    border-top: 1px solid #eee;
}

.btn-copy {
    padding: 8px 20px;
    border-radius: 10px;
    border: 1px solid #667eea;
    background: rgba(102, 126, 234, 0.08);
    color: #667eea;
    font-size: 13px;
    font-weight: 500;
    cursor: pointer;
}

.btn-copy:hover {
    background: rgba(102, 126, 234, 0.15);
}

.btn-close {
    padding: 8px 20px;
    border-radius: 10px;
    border: 1px solid rgba(0, 0, 0, 0.12);
    background: #fff;
    color: #666;
    font-size: 13px;
    cursor: pointer;
}

.btn-close:hover {
    background: #f5f5f5;
}

.level-badge {
    padding: 3px 10px;
    border-radius: 12px;
    font-size: 11px;
    font-weight: 600;
    font-family: monospace;
}

.level-error { background: #ffebee; color: #c62828; }
.level-warn { background: #fff3e0; color: #e65100; }
.level-info { background: #e3f2fd; color: #1565c0; }
.level-debug { background: #f5f5f5; color: #666; }
.level-audit { background: #ede7f6; color: #4527a0; }

@keyframes fadeIn {
    from { opacity: 0; }
    to { opacity: 1; }
}

@keyframes slideUp {
    from { opacity: 0; transform: translateY(20px); }
    to { opacity: 1; transform: translateY(0); }
}
</style>