<template>
    <div class="page">
        <div class="page-header">
            <h2>安全策略</h2>
            <span class="subtitle">Capability Policy 矩阵 — 按能力维度配置风险等级与沙箱策略</span>
        </div>

        <div v-if="policyStore.isLoading" class="loading">加载中...</div>
        <div v-else-if="policyStore.policies.length === 0" class="empty-state">
            <ShieldCheckIcon class="empty-icon" />
            <p>暂无策略配置</p>
        </div>
        <div class="policy-grid" v-else>
            <div
                v-for="policy in policyStore.policies"
                :key="policy.capability"
                :class="['policy-card', { expanded: expandedPolicy === policy.capability }]"
                @click="togglePolicy(policy.capability)"
            >
                <div class="policy-header">
                    <div class="policy-capability">
                        <span class="capability-name">{{ policy.capability }}</span>
                        <span :class="['risk-badge', `risk-${policy.riskLevel.toLowerCase()}`]">{{ policy.riskLevel }}</span>
                    </div>
                    <span class="expand-icon">{{ expandedPolicy === policy.capability ? '−' : '+' }}</span>
                </div>

                <div class="policy-summary">
                    <span :class="['summary-chip', policy.sandboxEnabled ? 'active' : '']">
                        {{ policy.sandboxEnabled ? 'Sandbox' : '无 Sandbox' }}
                    </span>
                    <span :class="['summary-chip', policy.blocked ? 'danger' : '']">
                        {{ policy.blocked ? '已阻止' : '已放行' }}
                    </span>
                </div>

                <div v-if="expandedPolicy === policy.capability" class="policy-detail">
                    <div class="detail-section">
                        <h5>Sandbox</h5>
                        <p>类型: {{ policy.sandboxType }} | 状态: {{ policy.sandboxEnabled ? '已启用' : '未启用' }}</p>
                    </div>
                    <div class="detail-section">
                        <h5>阻断状态</h5>
                        <p>{{ policy.blocked ? '该能力已被完全阻断' : '该能力未被阻断' }}</p>
                    </div>
                </div>
            </div>
        </div>
    </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ShieldCheckIcon } from '@heroicons/vue/24/outline'
import { usePolicyStore } from '@/stores/policyStore'

const policyStore = usePolicyStore()
const expandedPolicy = ref<string | null>(null)

function togglePolicy(capability: string) {
    expandedPolicy.value = expandedPolicy.value === capability ? null : capability
    if (expandedPolicy.value) {
        policyStore.fetchPolicyByCapability(capability)
    }
}

onMounted(() => {
    policyStore.fetchPolicies()
})
</script>

<style scoped>
.page-header {
    margin-bottom: 24px;
}

.page-header h2 {
    font-size: 28px;
    font-weight: 650;
    letter-spacing: -0.3px;
}

.subtitle {
    font-size: 13px;
    color: var(--color-text-secondary);
    display: block;
    margin-top: 4px;
}

.loading, .empty-state {
    padding: 60px 0;
    text-align: center;
    color: var(--color-text-secondary);
}

.empty-icon {
    width: 40px;
    height: 40px;
    margin-bottom: 12px;
    opacity: 0.25;
}

.policy-grid {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(360px, 1fr));
    gap: 14px;
}

.policy-card {
    background: var(--color-surface);
    border-radius: var(--radius-md);
    padding: 20px;
    border: 1px solid var(--color-border);
    cursor: pointer;
    transition: background 0.15s ease, border-color 0.15s ease;
}

.policy-card:hover {
    background: var(--accent-bg);
    border-color: var(--color-accent);
}

.policy-card.expanded {
    border-color: var(--color-accent);
}

.policy-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 12px;
}

.policy-capability {
    display: flex;
    align-items: center;
    gap: 10px;
}

.capability-name {
    font-weight: 600;
    font-size: 15px;
}

.expand-icon {
    width: 28px;
    height: 28px;
    border-radius: 50%;
    background: rgba(0,0,0,0.04);
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 18px;
    color: var(--color-accent);
    font-weight: 600;
}

.policy-summary {
    display: flex;
    gap: 8px;
    flex-wrap: wrap;
}

.summary-chip {
    padding: 4px 10px;
    border-radius: 20px;
    font-size: 12px;
    background: rgba(0,0,0,0.03);
    color: var(--color-text-secondary);
}

.summary-chip.active {
    background: var(--accent-bg);
    color: var(--color-accent);
    font-weight: 500;
}

.summary-chip.danger {
    background: rgba(198, 40, 40, 0.06);
    color: #c62828;
}

.policy-detail {
    margin-top: 16px;
    padding-top: 16px;
    border-top: 1px solid var(--color-border);
    display: flex;
    flex-direction: column;
    gap: 14px;
}

.detail-section h5 {
    font-size: 12px;
    font-weight: 600;
    color: var(--color-text-secondary);
    text-transform: uppercase;
    letter-spacing: 0.5px;
    margin-bottom: 4px;
}

.detail-section p {
    font-size: 13px;
}

.agent-chips {
    display: flex;
    flex-wrap: wrap;
    gap: 6px;
}

.agent-chip {
    padding: 3px 10px;
    border-radius: 20px;
    font-size: 12px;
    background: rgba(39, 174, 96, 0.08);
    color: var(--color-success);
}

.restrict-chip {
    padding: 3px 10px;
    border-radius: 20px;
    font-size: 12px;
    background: rgba(255, 59, 48, 0.08);
    color: var(--color-danger);
}

.empty-hint {
    font-size: 12px;
    color: var(--color-text-secondary);
}

.update-time {
    font-size: 11px;
    color: var(--color-text-secondary);
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
</style>