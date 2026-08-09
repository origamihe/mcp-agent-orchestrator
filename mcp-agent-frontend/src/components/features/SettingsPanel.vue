<template>
    <div class="settings-panel">
        <div class="panel-header">
            <h2>系统配置</h2>
        </div>

        <div class="settings-section">
            <h3>模型配置</h3>
            <div class="setting-row">
                <div class="setting-info">
                    <span class="setting-label">当前模型</span>
                    <span class="setting-desc">选择 Agent 使用的 LLM 模型</span>
                </div>
                <select class="setting-select" v-model="selectedModel" @change="onModelChange">
                    <option value="">选择模型...</option>
                    <option v-for="m in models" :key="m.configId" :value="m.configId">
                        {{ m.modelName || m.configId }}
                    </option>
                </select>
            </div>
        </div>

        <div class="settings-section">
            <h3>宿主配置</h3>
            <div class="setting-row" v-for="ch in channelConfigs" :key="ch.key">
                <div class="setting-info">
                    <span class="setting-label">{{ ch.label }}</span>
                    <span class="setting-desc">{{ ch.desc }}</span>
                </div>
                <label class="toggle">
                    <input type="checkbox" :checked="ch.enabled" @change="toggleChannel(ch.key)" />
                    <span class="toggle-slider"></span>
                </label>
            </div>
        </div>

        <div class="settings-section">
            <h3>工作空间</h3>
            <div class="setting-row">
                <div class="setting-info">
                    <span class="setting-label">自动保存间隔</span>
                    <span class="setting-desc">每次交互后自动保存工作空间状态</span>
                </div>
                <span class="setting-value">每次交互</span>
            </div>
            <div class="setting-row">
                <div class="setting-info">
                    <span class="setting-label">工作空间保留天数</span>
                    <span class="setting-desc">超过此天数未活跃的工作空间将被归档</span>
                </div>
                <select class="setting-select" v-model="workspaceRetention">
                    <option value="7">7 天</option>
                    <option value="14">14 天</option>
                    <option value="30">30 天</option>
                    <option value="90">90 天</option>
                </select>
            </div>
        </div>

        <div class="settings-section">
            <h3>Agent 行为</h3>
            <div class="setting-row">
                <div class="setting-info">
                    <span class="setting-label">反思模式</span>
                    <span class="setting-desc">启用后 Agent 在回答前会进行自我反思</span>
                </div>
                <label class="toggle">
                    <input type="checkbox" v-model="reflectionEnabled" />
                    <span class="toggle-slider"></span>
                </label>
            </div>
            <div class="setting-row">
                <div class="setting-info">
                    <span class="setting-label">最大 Token</span>
                    <span class="setting-desc">单次回复的最大 Token 数</span>
                </div>
                <input class="setting-input" type="number" v-model="maxTokens" min="256" max="32768" step="256" />
            </div>
        </div>
    </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import type { LlmModelInfo } from '@/types/llm'

defineProps<{
    models: LlmModelInfo[]
}>()

const selectedModel = ref('')
const workspaceRetention = ref('30')
const reflectionEnabled = ref(false)
const maxTokens = ref(4096)

const channelConfigs = ref([
    { key: 'qq', label: 'QQ Bot', desc: 'OneBot v11 · NapCat 协议', enabled: true },
    { key: 'desktop', label: 'Desktop Host', desc: 'WebSocket 长连接', enabled: true },
    { key: 'ide', label: 'IDE Host', desc: 'Rider / VS Code 插件', enabled: true },
    { key: 'telegram', label: 'Telegram Bot', desc: 'Webhook 回调', enabled: false },
    { key: 'discord', label: 'Discord Bot', desc: 'Webhook 回调', enabled: false },
])

function onModelChange() {
    console.log('[Settings] Model changed:', selectedModel.value)
}

function toggleChannel(key: string) {
    const ch = channelConfigs.value.find(c => c.key === key)
    if (ch) {
        ch.enabled = !ch.enabled
        console.log('[Settings] Channel toggled:', key, ch.enabled)
    }
}
</script>

<style scoped>
.settings-panel {
    padding: 32px;
    max-width: 800px;
}

.panel-header {
    margin-bottom: 28px;
}

.panel-header h2 {
    font-size: 24px;
    font-weight: 700;
    color: var(--color-text);
}

.settings-section {
    background: rgba(255,255,255,0.7);
    backdrop-filter: blur(20px);
    border-radius: 16px;
    padding: 24px;
    margin-bottom: 20px;
    border: 1px solid rgba(255,255,255,0.8);
    box-shadow: 0 4px 16px rgba(0,0,0,0.04);
}

.settings-section h3 {
    font-size: 15px;
    font-weight: 600;
    color: var(--color-text);
    margin-bottom: 16px;
    padding-bottom: 12px;
    border-bottom: 1px solid rgba(0,0,0,0.06);
}

.setting-row {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 14px 0;
}

.setting-row + .setting-row {
    border-top: 1px solid rgba(0,0,0,0.04);
}

.setting-info {
    display: flex;
    flex-direction: column;
    gap: 2px;
}

.setting-label {
    font-size: 14px;
    font-weight: 500;
    color: var(--color-text);
}

.setting-desc {
    font-size: 12px;
    color: var(--color-text-secondary);
}

.setting-value {
    font-size: 13px;
    color: var(--color-text-secondary);
    font-weight: 500;
}

.setting-select {
    padding: 8px 14px;
    border-radius: 10px;
    border: 1px solid rgba(0,0,0,0.1);
    background: rgba(255,255,255,0.9);
    font-size: 13px;
    color: var(--color-text);
    cursor: pointer;
    min-width: 160px;
}

.setting-input {
    padding: 8px 14px;
    border-radius: 10px;
    border: 1px solid rgba(0,0,0,0.1);
    background: rgba(255,255,255,0.9);
    font-size: 13px;
    width: 120px;
    text-align: right;
}

.toggle {
    position: relative;
    display: inline-block;
    width: 48px;
    height: 26px;
    cursor: pointer;
}

.toggle input {
    opacity: 0;
    width: 0;
    height: 0;
}

.toggle-slider {
    position: absolute;
    top: 0;
    left: 0;
    right: 0;
    bottom: 0;
    background: #ccc;
    border-radius: 26px;
    transition: all 0.3s;
}

.toggle input:checked + .toggle-slider {
    background: linear-gradient(135deg, #667eea, #764ba2);
}

.toggle-slider::before {
    content: '';
    position: absolute;
    height: 20px;
    width: 20px;
    left: 3px;
    bottom: 3px;
    background: #fff;
    border-radius: 50%;
    transition: all 0.3s;
}

.toggle input:checked + .toggle-slider::before {
    transform: translateX(22px);
}
</style>