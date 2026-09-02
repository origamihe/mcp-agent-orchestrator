<template>
    <div class="page">
        <div class="page-header">
            <button class="btn-back" @click="$router.push('/runs')">← 返回 Runs 列表</button>
            <h2>Run 详情</h2>
            <span class="run-id">{{ run?.id }}</span>
        </div>

        <div v-if="runStore.isLoading" class="loading">加载中...</div>
        <div v-else-if="!run" class="empty-state">Run 未找到</div>
        <template v-else>
            <div class="detail-grid">
                <div class="info-card">
                    <h4>概要</h4>
                    <dl>
                        <dt>Agent</dt><dd>{{ run.agentName }}</dd>
                        <dt>Intent</dt><dd>{{ run.intent }}</dd>
                        <dt>Status</dt><dd><span :class="['status-badge', `status-${run.status}`]">{{ statusLabel(run.status) }}</span></dd>
                        <dt>Duration</dt><dd>{{ formatDuration(run.duration) }}</dd>
                        <dt>Tool Calls</dt><dd>{{ run.toolCallCount }}</dd>
                    </dl>
                </div>
                <div class="info-card">
                    <h4>Token 用量</h4>
                    <dl>
                        <dt>Prompt</dt><dd>{{ run.tokenUsage.promptTokens }}</dd>
                        <dt>Completion</dt><dd>{{ run.tokenUsage.completionTokens }}</dd>
                        <dt>Total</dt><dd class="token-total">{{ run.tokenUsage.totalTokens }}</dd>
                    </dl>
                </div>
                <div class="info-card">
                    <h4>时间</h4>
                    <dl>
                        <dt>Created</dt><dd>{{ formatFullDate(run.createdAt) }}</dd>
                        <dt>Completed</dt><dd>{{ run.completedAt ? formatFullDate(run.completedAt) : '--' }}</dd>
                    </dl>
                </div>
            </div>

            <div class="section" v-if="run.trace?.length">
                <h3>Execution Trace</h3>
                <div class="trace-list">
                    <div v-for="span in run.trace" :key="span.id" :class="['trace-span', span.status]">
                        <div class="trace-header">
                            <span class="trace-op">{{ span.operation }}</span>
                            <span class="trace-duration">{{ formatDuration(span.duration) }}</span>
                        </div>
                        <div class="trace-meta">
                            {{ formatFullDate(span.startTime) }} → {{ formatFullDate(span.endTime) }}
                        </div>
                    </div>
                </div>
            </div>

            <div class="section" v-if="run.messages?.length">
                <h3>Messages</h3>
                <div class="message-list">
                    <div v-for="(msg, idx) in run.messages" :key="idx" :class="['message-item', msg.role]">
                        <span class="msg-role">{{ msg.role }}</span>
                        <p class="msg-content">{{ msg.content }}</p>
                        <span class="msg-time">{{ formatFullDate(msg.timestamp) }}</span>
                    </div>
                </div>
            </div>

            <div class="section" v-if="run.policyChecks?.length">
                <h3>Policy Checks</h3>
                <div class="policy-list">
                    <div v-for="(check, idx) in run.policyChecks" :key="idx" :class="['policy-item', check.passed ? 'passed' : 'failed']">
                        <span class="policy-capability">{{ check.capability }}</span>
                        <span>{{ check.passed ? '✅' : '❌' }}</span>
                        <span v-if="check.reason" class="policy-reason">{{ check.reason }}</span>
                    </div>
                </div>
            </div>
        </template>
    </div>
</template>

<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { useRunStore } from '@/stores/runStore'
import type { RunStatus } from '@/types/run'

const route = useRoute()
const runStore = useRunStore()

const runId = computed(() => route.params.id as string)
const run = computed(() => runStore.currentRun)

function statusLabel(status: RunStatus): string {
    const labels: Record<RunStatus, string> = {
        pending: '等待中', running: '执行中', completed: '已完成', failed: '失败', cancelled: '已取消',
    }
    return labels[status] || status
}

function formatDuration(ms: number): string {
    if (ms < 1000) return `${ms}ms`
    if (ms < 60000) return `${(ms / 1000).toFixed(1)}s`
    return `${(ms / 60000).toFixed(1)}m`
}

function formatFullDate(dateStr: string): string {
    if (!dateStr) return '--'
    return new Date(dateStr).toLocaleString('zh-CN')
}

onMounted(() => {
    runStore.fetchRunById(runId.value)
})
</script>

<style scoped>
.page {
    padding: 24px 32px;
    height: 100%;
    overflow-y: auto;
}

.page-header {
    display: flex;
    align-items: center;
    gap: 16px;
    margin-bottom: 24px;
}

.btn-back {
    padding: 6px 14px;
    border-radius: 8px;
    border: 1px solid rgba(0,0,0,0.1);
    background: rgba(255,255,255,0.7);
    cursor: pointer;
    font-size: 13px;
    color: var(--color-text-secondary);
}

.run-id {
    font-size: 12px;
    color: var(--color-text-secondary);
    font-family: monospace;
    background: rgba(0,0,0,0.04);
    padding: 4px 10px;
    border-radius: 6px;
}

.loading, .empty-state {
    color: var(--color-text-secondary);
    font-size: 14px;
    text-align: center;
    padding: 40px 0;
}

.detail-grid {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
    gap: 16px;
    margin-bottom: 24px;
}

.info-card {
    background: rgba(255,255,255,0.7);
    backdrop-filter: blur(20px);
    border-radius: 16px;
    padding: 24px;
    border: 1px solid rgba(255,255,255,0.8);
    box-shadow: 0 4px 16px rgba(0,0,0,0.04);
}

.info-card h4 {
    font-size: 14px;
    font-weight: 600;
    margin-bottom: 14px;
    color: #667eea;
}

.info-card dl {
    display: grid;
    grid-template-columns: 100px 1fr;
    gap: 8px;
    font-size: 13px;
}

.info-card dt {
    color: var(--color-text-secondary);
    font-weight: 500;
}

.token-total {
    font-weight: 700;
    color: #667eea;
}

.status-badge {
    padding: 3px 10px;
    border-radius: 20px;
    font-size: 12px;
    font-weight: 600;
}

.status-pending { background: #fff3e0; color: #ef6c00; }
.status-running { background: #e3f2fd; color: #1565c0; }
.status-completed { background: #e8f5e9; color: #2e7d32; }
.status-failed { background: #ffebee; color: #c62828; }
.status-cancelled { background: #f5f5f5; color: #757575; }

.section {
    margin-bottom: 24px;
}

.section h3 {
    font-size: 16px;
    font-weight: 600;
    margin-bottom: 14px;
}

.trace-list {
    display: flex;
    flex-direction: column;
    gap: 8px;
}

.trace-span {
    background: rgba(255,255,255,0.7);
    border-radius: 10px;
    padding: 14px 18px;
    border-left: 4px solid #667eea;
    border: 1px solid rgba(255,255,255,0.8);
}

.trace-span.error { border-left-color: #e74c3c; }
.trace-span.warning { border-left-color: #f39c12; }

.trace-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 4px;
}

.trace-op {
    font-weight: 600;
    font-size: 14px;
}

.trace-duration {
    font-size: 12px;
    color: var(--color-text-secondary);
    font-family: monospace;
}

.trace-meta {
    font-size: 12px;
    color: var(--color-text-secondary);
}

.message-list {
    display: flex;
    flex-direction: column;
    gap: 10px;
}

.message-item {
    background: rgba(255,255,255,0.7);
    border-radius: 12px;
    padding: 14px 18px;
    border: 1px solid rgba(255,255,255,0.8);
}

.message-item.user { border-left: 3px solid #667eea; }
.message-item.assistant { border-left: 3px solid #27ae60; }
.message-item.tool { border-left: 3px solid #f39c12; }

.msg-role {
    font-size: 11px;
    font-weight: 600;
    text-transform: uppercase;
    color: var(--color-text-secondary);
    display: block;
    margin-bottom: 6px;
}

.msg-content {
    font-size: 14px;
    line-height: 1.6;
    margin-bottom: 6px;
}

.msg-time {
    font-size: 11px;
    color: var(--color-text-secondary);
}

.policy-list {
    display: flex;
    flex-direction: column;
    gap: 8px;
}

.policy-item {
    background: rgba(255,255,255,0.7);
    border-radius: 10px;
    padding: 12px 18px;
    border: 1px solid rgba(255,255,255,0.8);
    display: flex;
    gap: 12px;
    align-items: center;
    font-size: 13px;
}

.policy-item.passed { border-left: 3px solid #27ae60; }
.policy-item.failed { border-left: 3px solid #e74c3c; }

.policy-capability {
    font-weight: 600;
    min-width: 120px;
}

.policy-reason {
    color: var(--color-text-secondary);
    font-size: 12px;
}
</style>