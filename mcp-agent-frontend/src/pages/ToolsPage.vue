<template>
    <div class="page">
        <div class="page-header">
            <h2>工具管理</h2>
            <span class="subtitle">MCP 工具注册、风险等级与沙箱配置</span>
        </div>

        <div class="risk-overview">
            <div v-for="(count, level) in riskDistribution" :key="level" :class="['risk-chip', `risk-${level.toLowerCase()}`]">
                <span class="risk-count">{{ count }}</span>
                <span class="risk-label">{{ level }}</span>
            </div>
            <span class="total-chip">共 {{ toolStore.toolCount }} 个工具</span>
        </div>

        <div v-if="toolStore.isLoading" class="loading">加载中...</div>
        <div v-else-if="toolStore.tools.length === 0" class="empty-state">
            <WrenchScrewdriverIcon class="empty-icon" />
            <p>暂无工具注册</p>
        </div>
        <div class="tool-grid" v-else>
            <div
                v-for="tool in toolStore.tools"
                :key="tool.name"
                :class="['tool-card', `risk-border-${tool.riskLevel.toLowerCase()}`]"
                @click="selectTool(tool.name)"
            >
                <div class="tool-header">
                    <span class="tool-name">{{ tool.name }}</span>
                    <span :class="['risk-badge', `risk-${tool.riskLevel.toLowerCase()}`]">{{ tool.riskLevel }}</span>
                </div>
                <p class="tool-desc">{{ tool.description }}</p>
                <div class="tool-meta">
                    <span :class="['status-dot', tool.status]"></span>
                    <span>{{ statusLabel(tool.status) }}</span>
                    <span class="tool-timeout">超时: {{ tool.timeout }}s</span>
                </div>
                <div class="tool-detail" v-if="selectedTool === tool.name">
                    <div class="detail-row">
                        <strong>Authorization</strong>
                        <span>{{ tool.authorization.confirmationMode }}</span>
                    </div>
                    <div class="detail-row">
                        <strong>Sandbox</strong>
                        <span>{{ tool.sandbox.enabled ? tool.sandbox.type : '禁用' }}</span>
                    </div>
                    <div class="detail-row">
                        <strong>Network</strong>
                        <span>{{ tool.sandbox.networkEnabled ? '允许' : '禁止' }}</span>
                    </div>
                    <div class="detail-row">
                        <strong>Output Limit</strong>
                        <span>{{ tool.outputLimit }}</span>
                    </div>
                    <div class="detail-row" v-if="tool.allowedAgents.length">
                        <strong>Allowed Agents</strong>
                        <span class="agent-list">{{ tool.allowedAgents.join(', ') }}</span>
                    </div>
                </div>
            </div>
        </div>
    </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { WrenchScrewdriverIcon } from '@heroicons/vue/24/outline'
import { useToolStore } from '@/stores/toolStore'

const toolStore = useToolStore()
const selectedTool = ref<string | null>(null)

const riskDistribution = computed(() => toolStore.riskDistribution)

function statusLabel(status: string): string {
    const labels: Record<string, string> = {
        enabled: '已启用', disabled: '已禁用',
        confirmation_required: '需确认', restricted: '受限',
    }
    return labels[status] || status
}

function selectTool(name: string) {
    selectedTool.value = selectedTool.value === name ? null : name
    if (selectedTool.value) {
        toolStore.fetchToolByName(name)
    }
}

onMounted(() => {
    toolStore.fetchTools()
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

.risk-overview {
    display: flex;
    gap: 10px;
    margin-bottom: 24px;
    flex-wrap: wrap;
    align-items: center;
}

.risk-chip {
    padding: 8px 16px;
    border-radius: 12px;
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 2px;
    min-width: 50px;
}

.risk-count {
    font-size: 18px;
    font-weight: 700;
}

.risk-label {
    font-size: 11px;
    font-weight: 600;
}

.risk-l0 { background: #e8f5e9; color: #2e7d32; }
.risk-l1 { background: #c8e6c9; color: #388e3c; }
.risk-l2 { background: #fff9c4; color: #f9a825; }
.risk-l3 { background: #ffe0b2; color: #ef6c00; }
.risk-l4 { background: #ffccbc; color: #d84315; }
.risk-l5 { background: #ffcdd2; color: #c62828; }

.total-chip {
    padding: 8px 16px;
    border-radius: 12px;
    font-size: 13px;
    background: rgba(0,0,0,0.04);
    color: var(--color-text-secondary);
    margin-left: auto;
}

.loading, .empty-state {
    padding: 60px 0;
    text-align: center;
    color: var(--color-text-secondary);
}

.empty-icon {
    width: 48px;
    height: 48px;
    margin-bottom: 12px;
    opacity: 0.3;
}

.tool-grid {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(340px, 1fr));
    gap: 16px;
}

.tool-card {
    background: rgba(255,255,255,0.7);
    backdrop-filter: blur(20px);
    border-radius: 14px;
    padding: 20px;
    border: 1px solid rgba(255,255,255,0.8);
    cursor: pointer;
    transition: all 0.2s;
    box-shadow: 0 2px 12px rgba(0,0,0,0.03);
}

.tool-card:hover {
    box-shadow: 0 8px 24px rgba(0,0,0,0.08);
    transform: translateY(-2px);
}

.risk-border-l0 { border-left: 3px solid #2e7d32; }
.risk-border-l1 { border-left: 3px solid #388e3c; }
.risk-border-l2 { border-left: 3px solid #f9a825; }
.risk-border-l3 { border-left: 3px solid #ef6c00; }
.risk-border-l4 { border-left: 3px solid #d84315; }
.risk-border-l5 { border-left: 3px solid #c62828; }

.tool-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 10px;
}

.tool-name {
    font-weight: 600;
    font-size: 15px;
    font-family: monospace;
}

.risk-badge {
    padding: 3px 10px;
    border-radius: 20px;
    font-size: 12px;
    font-weight: 600;
}

.risk-l0 { background: #e8f5e9; color: #2e7d32; }
.risk-l1 { background: #c8e6c9; color: #388e3c; }
.risk-l2 { background: #fff9c4; color: #f9a825; }
.risk-l3 { background: #ffe0b2; color: #ef6c00; }
.risk-l4 { background: #ffccbc; color: #d84315; }
.risk-l5 { background: #ffcdd2; color: #c62828; }

.tool-desc {
    font-size: 13px;
    color: var(--color-text-secondary);
    margin-bottom: 12px;
    line-height: 1.5;
}

.tool-meta {
    display: flex;
    gap: 12px;
    align-items: center;
    font-size: 12px;
    color: var(--color-text-secondary);
}

.status-dot {
    width: 7px;
    height: 7px;
    border-radius: 50%;
}

.status-dot.enabled { background: #27ae60; }
.status-dot.disabled { background: #bdc3c7; }
.status-dot.confirmation_required { background: #f39c12; }
.status-dot.restricted { background: #e74c3c; }

.tool-timeout {
    font-family: monospace;
    margin-left: auto;
}

.tool-detail {
    margin-top: 14px;
    padding-top: 14px;
    border-top: 1px solid rgba(0,0,0,0.06);
    display: flex;
    flex-direction: column;
    gap: 8px;
}

.detail-row {
    display: flex;
    justify-content: space-between;
    font-size: 13px;
}

.detail-row strong {
    color: var(--color-text-secondary);
    font-weight: 500;
}

.agent-list {
    max-width: 180px;
    text-align: right;
    font-size: 12px;
    color: var(--color-text-secondary);
}
</style>