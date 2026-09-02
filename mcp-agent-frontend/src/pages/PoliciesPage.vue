<template>
    <div class="page">
        <div class="page-header">
            <h2>安全策略</h2>
            <span class="subtitle">Capability Policy 矩阵 — 按能力维度配置安全策略</span>
        </div>

        <div v-if="policyStore.isLoading" class="loading">加载中...</div>
        <div v-else-if="policyStore.policies.length === 0" class="empty-state">
            <ShieldCheckIcon class="empty-icon" />
            <p>暂无策略配置</p>
        </div>
        <div class="policy-grid" v-else>
            <div
                v-for="policy in policyStore.policies"
                :key="policy.id"
                :class="['policy-card', { expanded: expandedPolicy === policy.id }]"
                @click="togglePolicy(policy.id)"
            >
                <div class="policy-header">
                    <div class="policy-capability">
                        <span class="capability-name">{{ policy.capability }}</span>
                        <span :class="['risk-badge', `risk-${policy.riskLevel.toLowerCase()}`]">{{ policy.riskLevel }}</span>
                    </div>
                    <span class="expand-icon">{{ expandedPolicy === policy.id ? '−' : '+' }}</span>
                </div>

                <div class="policy-summary">
                    <span :class="['summary-chip', policy.workspaceRequired ? 'active' : '']">
                        {{ policy.workspaceRequired ? '需要 Workspace' : '无需 Workspace' }}
                    </span>
                    <span :class="['summary-chip', policy.confirmationRequired ? 'active' : '']">
                        {{ policy.confirmationRequired ? '需要确认' : '无需确认' }}
                    </span>
                    <span :class="['summary-chip', policy.sandboxEnabled ? 'active' : '']">
                        {{ policy.sandboxEnabled ? 'Sandbox' : '无 Sandbox' }}
                    </span>
                </div>

                <div v-if="expandedPolicy === policy.id" class="policy-detail">
                    <div class="detail-section">
                        <h5>Sandbox</h5>
                        <p>类型: {{ policy.sandboxType }} | 网络: {{ policy.networkEnabled ? '允许' : '禁止' }}</p>
                    </div>
                    <div class="detail-section">
                        <h5>Timeout</h5>
                        <p>{{ policy.timeout }}ms</p>
                    </div>
                    <div class="detail-section">
                        <h5>Allowed Agents</h5>
                        <div class="agent-chips">
                            <span v-for="agent in policy.allowedAgents" :key="agent" class="agent-chip">{{ agent }}</span>
                            <span v-if="policy.allowedAgents.length === 0" class="empty-hint">全部 Agent</span>
                        </div>
                    </div>
                    <div class="detail-section" v-if="policy.environmentRestrictions.length">
                        <h5>Environment Restrictions</h5>
                        <div class="agent-chips">
                            <span v-for="env in policy.environmentRestrictions" :key="env" class="restrict-chip">{{ env }}</span>
                        </div>
                    </div>
                    <div class="detail-section">
                        <h5>Audit</h5>
                        <p>{{ policy.auditEnabled ? '已启用审计' : '未启用审计' }}</p>
                    </div>
                    <div class="detail-section">
                        <span class="update-time">最后更新: {{ formatDate(policy.updatedAt) }}</span>
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

function togglePolicy(id: string) {
    expandedPolicy.value = expandedPolicy.value === id ? null : id
    if (expandedPolicy.value) {
        const policy = policyStore.policies.find((p) => p.id === id)
        if (policy) {
            policyStore.fetchPolicyByCapability(policy.capability)
        }
    }
}

function formatDate(dateStr: string): string {
    if (!dateStr) return ''
    return new Date(dateStr).toLocaleString('zh-CN')
}

onMounted(() => {
    policyStore.fetchPolicies()
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

.policy-grid {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(360px, 1fr));
    gap: 16px;
}

.policy-card {
    background: rgba(255,255,255,0.7);
    backdrop-filter: blur(20px);
    border-radius: 14px;
    padding: 20px;
    border: 1px solid rgba(255,255,255,0.8);
    cursor: pointer;
    transition: all 0.2s;
    box-shadow: 0 2px 12px rgba(0,0,0,0.03);
}

.policy-card:hover {
    box-shadow: 0 8px 24px rgba(0,0,0,0.08);
}

.policy-card.expanded {
    border-color: #667eea;
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
    color: #667eea;
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
    background: rgba(102, 126, 234, 0.08);
    color: #667eea;
    font-weight: 500;
}

.policy-detail {
    margin-top: 16px;
    padding-top: 16px;
    border-top: 1px solid rgba(0,0,0,0.06);
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
    color: #27ae60;
}

.restrict-chip {
    padding: 3px 10px;
    border-radius: 20px;
    font-size: 12px;
    background: rgba(231, 76, 60, 0.08);
    color: #e74c3c;
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