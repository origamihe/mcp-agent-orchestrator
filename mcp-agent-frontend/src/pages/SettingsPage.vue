<template>
    <div class="page">
        <div class="page-header">
            <h2>系统配置</h2>
            <span class="subtitle">模型管理、提供者配置与认证设置</span>
        </div>

        <div class="settings-grid">
            <div class="settings-card">
                <div class="card-header">
                    <h3>Model Providers</h3>
                    <span class="card-count">{{ availableModels.length }} 个模型</span>
                </div>
                <div v-if="availableModels.length === 0" class="empty">暂无模型配置</div>
                <div v-else class="model-list">
                    <div v-for="model in availableModels" :key="model.configId" class="model-item">
                        <div class="model-info">
                            <span class="model-name">{{ model.modelId }}</span>
                            <span class="model-provider">{{ model.provider }}</span>
                        </div>
                        <span :class="['model-badge', model.enabled ? 'enabled' : 'disabled']">
                            {{ model.enabled ? 'Enabled' : 'Disabled' }}
                        </span>
                    </div>
                </div>
            </div>

            <div class="settings-card">
                <div class="card-header">
                    <h3>Gateway</h3>
                </div>
                <div class="config-list">
                    <div class="config-item">
                        <span class="config-label">API Base</span>
                        <code class="config-value">{{ apiBase }}</code>
                    </div>
                    <div class="config-item">
                        <span class="config-label">WebSocket</span>
                        <code class="config-value">{{ wsBase }}</code>
                    </div>
                    <div class="config-item">
                        <span class="config-label">Auth Token</span>
                        <code class="config-value token">{{ tokenMasked }}</code>
                    </div>
                </div>
            </div>

            <div class="settings-card">
                <div class="card-header">
                    <h3>Security</h3>
                </div>
                <div class="config-list">
                    <div class="config-item">
                        <span class="config-label">Sandbox Default</span>
                        <span class="config-value">ProcessSandbox</span>
                    </div>
                    <div class="config-item">
                        <span class="config-label">Default Risk Level</span>
                        <span class="risk-badge risk-l3">L3</span>
                    </div>
                    <div class="config-item">
                        <span class="config-label">Audit Logging</span>
                        <span class="config-value enabled">Enabled</span>
                    </div>
                    <div class="config-item">
                        <span class="config-label">Confirmation Mode</span>
                        <span class="config-value">Always</span>
                    </div>
                </div>
            </div>

            <div class="settings-card">
                <div class="card-header">
                    <h3>Runtime</h3>
                </div>
                <div class="config-list">
                    <div class="config-item">
                        <span class="config-label">Max Concurrent Runs</span>
                        <span class="config-value">5</span>
                    </div>
                    <div class="config-item">
                        <span class="config-label">Default Timeout</span>
                        <span class="config-value">300s</span>
                    </div>
                    <div class="config-item">
                        <span class="config-label">Max Tool Calls</span>
                        <span class="config-value">50</span>
                    </div>
                    <div class="config-item">
                        <span class="config-label">Max Context Size</span>
                        <span class="config-value">32768</span>
                    </div>
                </div>
            </div>
        </div>
    </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import type { LlmModelInfo } from '@/types/llm'
import http from '@/utils/request'

const availableModels = ref<LlmModelInfo[]>([])

const apiBase = computed(() => `${location.protocol}//${location.host}/api`)
const wsBase = computed(() => `${location.protocol === 'https:' ? 'wss:' : 'ws:'}//${location.host}/ws/mcp`)

const tokenMasked = computed(() => {
    const token = localStorage.getItem('ws_token') || '未设置'
    if (token.length <= 8) return token
    return token.slice(0, 4) + '****' + token.slice(-4)
})

onMounted(async () => {
    try {
        const res = await http.get('/mcp/configs')
        availableModels.value = (res as any) || []
    } catch { /* ignore */ }
})
</script>

<style scoped>
.page {
    padding: 24px 32px;
    height: 100%;
    overflow-y: auto;
}

.page-header {
    margin-bottom: 24px;
}

.page-header h2 {
    font-size: 22px;
    font-weight: 700;
}

.subtitle {
    font-size: 13px;
    color: var(--color-text-secondary);
    display: block;
    margin-top: 4px;
}

.settings-grid {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(380px, 1fr));
    gap: 16px;
}

.settings-card {
    background: rgba(255,255,255,0.7);
    backdrop-filter: blur(20px);
    border-radius: 16px;
    padding: 24px;
    border: 1px solid rgba(255,255,255,0.8);
    box-shadow: 0 4px 16px rgba(0,0,0,0.04);
}

.card-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 16px;
}

.card-header h3 {
    font-size: 16px;
    font-weight: 600;
}

.card-count {
    font-size: 12px;
    color: var(--color-text-secondary);
    background: rgba(0,0,0,0.04);
    padding: 3px 10px;
    border-radius: 20px;
}

.empty {
    font-size: 13px;
    color: var(--color-text-secondary);
    text-align: center;
    padding: 20px 0;
}

.model-list {
    display: flex;
    flex-direction: column;
    gap: 8px;
}

.model-item {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 10px 14px;
    background: rgba(0,0,0,0.02);
    border-radius: 10px;
}

.model-info {
    display: flex;
    flex-direction: column;
    gap: 2px;
}

.model-name {
    font-weight: 600;
    font-size: 14px;
    font-family: monospace;
}

.model-provider {
    font-size: 12px;
    color: var(--color-text-secondary);
}

.model-badge {
    padding: 3px 10px;
    border-radius: 20px;
    font-size: 12px;
    font-weight: 600;
}

.model-badge.enabled { background: #e8f5e9; color: #2e7d32; }
.model-badge.disabled { background: #f5f5f5; color: #757575; }

.config-list {
    display: flex;
    flex-direction: column;
    gap: 10px;
}

.config-item {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 8px 0;
    border-bottom: 1px solid rgba(0,0,0,0.04);
}

.config-item:last-child {
    border-bottom: none;
}

.config-label {
    font-size: 13px;
    color: var(--color-text-secondary);
}

.config-value {
    font-size: 13px;
    font-weight: 500;
    font-family: monospace;
}

.config-value.enabled {
    color: #27ae60;
}

.config-value.token {
    letter-spacing: 1px;
}

.risk-badge {
    padding: 3px 10px;
    border-radius: 20px;
    font-size: 12px;
    font-weight: 600;
}

.risk-l3 { background: #ffe0b2; color: #ef6c00; }
</style>