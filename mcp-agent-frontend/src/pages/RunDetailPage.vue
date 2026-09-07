<template>
    <div class="page">
        <div class="page-header">
            <button class="btn-secondary btn-back" @click="$router.push('/runs')">← Back to Runs</button>
            <h2>Run Detail</h2>
            <span class="run-id">{{ run?.id }}</span>
        </div>

        <div v-if="runStore.isLoading" class="loading">Loading...</div>
        <div v-else-if="!run" class="empty-state">Run not found</div>
        <template v-else>
            <div class="detail-grid">
                <div class="info-section">
                    <h3 class="section-title">Summary</h3>
                    <div class="info-grid">
                        <div class="info-item"><span class="info-label">Agent</span><span class="info-value">{{ run.agentName }}</span></div>
                        <div class="info-item"><span class="info-label">Intent</span><span class="info-value">{{ run.intent }}</span></div>
                        <div class="info-item"><span class="info-label">Status</span><span class="info-value"><StatusBadge :type="runStatusType(run.status)" :text="statusLabel(run.status)" /></span></div>
                        <div class="info-item"><span class="info-label">Duration</span><span class="info-value mono">{{ formatDuration(run.duration) }}</span></div>
                        <div class="info-item"><span class="info-label">Tool Calls</span><span class="info-value mono">{{ run.toolCallCount }}</span></div>
                    </div>
                </div>
                <div class="info-section">
                    <h3 class="section-title">Tokens</h3>
                    <div class="info-grid">
                        <div class="info-item"><span class="info-label">Prompt</span><span class="info-value mono">{{ run.tokenUsage.promptTokens }}</span></div>
                        <div class="info-item"><span class="info-label">Completion</span><span class="info-value mono">{{ run.tokenUsage.completionTokens }}</span></div>
                        <div class="info-item"><span class="info-label">Total</span><span class="info-value mono token-total">{{ run.tokenUsage.totalTokens }}</span></div>
                    </div>
                </div>
                <div class="info-section">
                    <h3 class="section-title">Timeline</h3>
                    <div class="info-grid">
                        <div class="info-item"><span class="info-label">Created</span><span class="info-value mono">{{ formatFullDate(run.createdAt) }}</span></div>
                        <div class="info-item"><span class="info-label">Completed</span><span class="info-value mono">{{ run.completedAt ? formatFullDate(run.completedAt) : '--' }}</span></div>
                    </div>
                </div>
            </div>

            <div class="section-separator"></div>

            <div class="section" v-if="run.trace?.length">
                <h3 class="section-title">Execution Trace</h3>
                <div class="trace-list">
                    <div v-for="span in run.trace" :key="span.id" :class="['trace-span', span.status]">
                        <span :class="['trace-marker', span.status]"></span>
                        <div class="trace-content">
                            <div class="trace-header">
                                <span class="trace-op">{{ span.operation }}</span>
                                <span class="trace-duration mono">{{ formatDuration(span.duration) }}</span>
                            </div>
                            <div class="trace-meta mono">
                                {{ formatFullDate(span.startTime) }} → {{ formatFullDate(span.endTime) }}
                            </div>
                        </div>
                    </div>
                </div>
            </div>

            <div class="section-separator"></div>

            <div class="section" v-if="run.messages?.length">
                <h3 class="section-title">Messages</h3>
                <div class="message-list">
                    <div v-for="(msg, idx) in run.messages" :key="idx" :class="['message-item', msg.role]">
                        <span class="msg-role">{{ msg.role }}</span>
                        <p class="msg-content">{{ msg.content }}</p>
                        <span class="msg-time">{{ formatFullDate(msg.timestamp) }}</span>
                    </div>
                </div>
            </div>

            <div class="section-separator"></div>

            <div class="section" v-if="run.policyChecks?.length">
                <h3 class="section-title">Policy Checks</h3>
                <div class="policy-list">
                    <div v-for="(check, idx) in run.policyChecks" :key="idx" :class="['policy-item', check.passed ? 'passed' : 'failed']">
                        <span class="policy-capability">{{ check.capability }}</span>
                        <StatusBadge :type="check.passed ? 'success' : 'error'" :text="check.passed ? 'Passed' : 'Failed'" />
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
import StatusBadge from '@/components/common/StatusBadge.vue'
import { useRunStore } from '@/stores/runStore'
import type { RunStatus } from '@/types/run'

const route = useRoute()
const runStore = useRunStore()

const runId = computed(() => route.params.id as string)
const run = computed(() => runStore.currentRun)

function statusLabel(status: RunStatus): string {
    const labels: Record<RunStatus, string> = {
        pending: 'Pending', running: 'Running', completed: 'Done', failed: 'Failed', cancelled: 'Cancelled',
    }
    return labels[status] || status
}

function runStatusType(status: string): 'success' | 'warning' | 'error' | 'info' | 'neutral' {
    const map: Record<string, string> = {
        completed: 'success', failed: 'error', running: 'info', pending: 'warning', cancelled: 'neutral',
    }
    return (map[status] || 'neutral') as any
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
.page-header {
    display: flex;
    align-items: center;
    gap: 14px;
    margin-bottom: 24px;
}

.page-header h2 {
    font-size: 28px;
    font-weight: 650;
    letter-spacing: -0.3px;
    margin: 0;
}

.run-id {
    font-size: 12px;
    color: var(--color-text-secondary);
    font-family: monospace;
    background: var(--color-surface);
    border: 1px solid var(--color-border);
    padding: 3px 10px;
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
    gap: 20px;
    margin-bottom: 0;
}

.info-section {
    padding: 20px 24px;
    border: 1px solid var(--color-border);
    border-radius: var(--radius-md);
    background: var(--color-surface);
}

.section-title {
    font-size: 16px;
    font-weight: 600;
    margin-bottom: 14px;
}

.info-grid {
    display: grid;
    grid-template-columns: 100px 1fr;
    gap: 8px;
    font-size: 13px;
}

.info-item {
    display: contents;
}

.info-label {
    color: var(--color-text-secondary);
    font-weight: 500;
    padding: 4px 0;
}

.info-value {
    padding: 4px 0;
    font-weight: 500;
}

.mono {
    font-family: monospace;
    font-size: 12px;
}

.token-total {
    font-weight: 700;
    color: var(--color-accent);
}

.section {
    margin-bottom: 0;
}

.trace-list {
    display: flex;
    flex-direction: column;
    gap: 0;
    position: relative;
    padding-left: 24px;
}

.trace-list::before {
    content: '';
    position: absolute;
    left: 7px;
    top: 0;
    bottom: 0;
    width: 2px;
    background: var(--color-border);
}

.trace-span {
    position: relative;
    padding: 10px 0 10px 20px;
    border-left: none;
    border-radius: 0;
    background: none;
    border: none;
}

.trace-marker {
    position: absolute;
    left: -28px;
    top: 14px;
    width: 10px;
    height: 10px;
    border-radius: 50%;
    background: var(--color-accent);
    border: 2px solid var(--color-surface);
    z-index: 1;
}

.trace-marker.error { background: var(--color-danger); }
.trace-marker.warning { background: #f39c12; }
.trace-marker.completed { background: var(--color-success); }
.trace-marker.running { background: #3498db; }

.trace-content {
    padding: 10px 14px;
    border: 1px solid var(--color-border);
    border-radius: var(--radius-sm);
    background: var(--color-surface);
}

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
}

.trace-meta {
    font-size: 12px;
    color: var(--color-text-secondary);
}

.message-list {
    display: flex;
    flex-direction: column;
    gap: 8px;
}

.message-item {
    padding: 14px 18px;
    border: 1px solid var(--color-border);
    border-radius: var(--radius-md);
    background: var(--color-surface);
    border-left: 3px solid var(--color-accent);
}

.message-item.user { border-left-color: var(--color-accent); }
.message-item.assistant { border-left-color: var(--color-success); }
.message-item.tool { border-left-color: #f39c12; }

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
    display: flex;
    align-items: center;
    gap: 12px;
    padding: 10px 14px;
    border: 1px solid var(--color-border);
    border-radius: var(--radius-sm);
    background: var(--color-surface);
}

.policy-capability {
    font-weight: 500;
    font-size: 13px;
    flex: 1;
}

.policy-reason {
    font-size: 12px;
    color: var(--color-text-secondary);
}
</style>