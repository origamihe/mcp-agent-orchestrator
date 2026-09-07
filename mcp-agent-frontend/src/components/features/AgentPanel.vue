<template>
    <div class="agent-panel">
        <div class="panel-header">
            <h2>Agents</h2>
            <span class="subtitle">Agent directory</span>
        </div>

        <div class="agent-stats" v-if="agents.length > 0">
            <div class="stat-chip">
                <span class="chip-value">{{ agents.length }}</span>
                <span class="chip-label">Total</span>
            </div>
            <div class="stat-chip" v-for="(count, type) in typeCounts" :key="type">
                <span class="chip-value">{{ count }}</span>
                <span class="chip-label">{{ typeLabel(type) }}</span>
            </div>
        </div>

        <div v-if="agents.length === 0" class="empty-state">
            <CpuChipIcon class="empty-icon" />
            <p>No agents registered</p>
            <p class="empty-hint">Agents auto-register on system startup</p>
        </div>

        <div class="agent-list" v-else>
            <div
                v-for="agent in agents"
                :key="agent.agentId"
                class="agent-row"
                @click="$emit('navigate', 'agent-detail')"
            >
                <router-link :to="`/agents/${agent.agentId}`" class="agent-row-link" @click.stop>
                    <span class="agent-row-dot" :style="{ background: typeColor(agent.agentType) }"></span>
                    <div class="agent-row-main">
                        <span class="agent-row-name">{{ agent.agentName }}</span>
                        <span class="agent-row-desc">{{ agent.description }}</span>
                    </div>
                    <span class="agent-row-meta">{{ agent.modelId }}</span>
                    <span class="agent-row-count">{{ agent.sessionCount || 0 }} runs</span>
                    <button
                        class="btn-tertiary"
                        @click.stop="$emit('test-agent', agent.agentId)"
                    >
                        Test
                    </button>
                </router-link>
            </div>
        </div>

        <div class="section-separator"></div>

        <div class="workflow-section">
            <h3 class="section-title">Multi-Agent Workflows</h3>
            <div class="workflow-list">
                <button class="list-item workflow-item" @click="$emit('run-pipeline')">
                    <ArrowRightIcon class="wf-icon" />
                    <div class="wf-info">
                        <span class="wf-name">Pipeline</span>
                        <span class="wf-desc">Sequential agent processing</span>
                    </div>
                </button>
                <button class="list-item workflow-item" @click="$emit('run-parallel')">
                    <SquaresPlusIcon class="wf-icon" />
                    <div class="wf-info">
                        <span class="wf-name">Parallel</span>
                        <span class="wf-desc">Concurrent agent execution</span>
                    </div>
                </button>
                <button class="list-item workflow-item" @click="$emit('run-delegate')">
                    <ArrowPathRoundedSquareIcon class="wf-icon" />
                    <div class="wf-info">
                        <span class="wf-name">Smart Delegate</span>
                        <span class="wf-desc">Auto-select best agent</span>
                    </div>
                </button>
            </div>
        </div>
    </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import {
    CpuChipIcon,
    ArrowRightIcon,
    SquaresPlusIcon,
    ArrowPathRoundedSquareIcon,
} from '@heroicons/vue/24/outline'
import type { AgentCard } from '@/types/agent'

const props = defineProps<{
    agents: AgentCard[]
}>()

defineEmits<{
    (e: 'navigate', feature: string): void
    (e: 'test-agent', agentId: string): void
    (e: 'run-task', agentId: string): void
    (e: 'run-pipeline'): void
    (e: 'run-parallel'): void
    (e: 'run-delegate'): void
}>()

const typeCounts = computed(() => {
    const counts: Record<string, number> = {}
    for (const a of props.agents) {
        counts[a.agentType] = (counts[a.agentType] || 0) + 1
    }
    return counts
})

function typeLabel(type: string): string {
    const labels: Record<string, string> = {
        CHAT: 'Chat', CODE: 'Code', SEARCH: 'Search', DOCUMENT: 'Document',
        PLANNER: 'Planner', EXECUTOR: 'Executor', GENERAL: 'General',
    }
    return labels[type] || type
}

function typeColor(type: string): string {
    const colors: Record<string, string> = {
        CHAT: 'var(--color-accent)',
        CODE: '#764ba2',
        SEARCH: '#27ae60',
        DOCUMENT: '#ff6b6b',
        PLANNER: '#f39c12',
        EXECUTOR: '#3498db',
        GENERAL: '#999',
    }
    return colors[type] || '#999'
}
</script>

<style scoped>
.agent-panel {
    padding: 0;
}

.panel-header {
    margin-bottom: 24px;
}

.panel-header h2 {
    font-size: 28px;
    font-weight: 650;
    letter-spacing: -0.3px;
}

.subtitle {
    font-size: 13px;
    color: var(--color-text-secondary);
    margin-top: 4px;
    display: block;
}

.agent-stats {
    display: flex;
    gap: 10px;
    margin-bottom: 24px;
    flex-wrap: wrap;
}

.stat-chip {
    background: var(--color-surface);
    border: 1px solid var(--color-border);
    border-radius: var(--radius-sm);
    padding: 8px 16px;
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 1px;
    min-width: 64px;
}

.chip-value {
    font-size: 18px;
    font-weight: 650;
    color: var(--color-accent);
}

.chip-label {
    font-size: 11px;
    color: var(--color-text-secondary);
    font-weight: 500;
}

.empty-state {
    text-align: center;
    padding: 60px 20px;
    color: var(--color-text-secondary);
}

.empty-icon {
    width: 40px;
    height: 40px;
    margin-bottom: 12px;
    opacity: 0.25;
}

.empty-state p {
    font-size: 14px;
}

.empty-hint {
    font-size: 12px;
    opacity: 0.6;
}

.agent-list {
    display: flex;
    flex-direction: column;
    border: 1px solid var(--color-border);
    border-radius: var(--radius-lg);
    overflow: hidden;
    background: var(--color-surface);
}

.agent-row {
    border-bottom: 1px solid var(--color-border);
}

.agent-row:last-child {
    border-bottom: none;
}

.agent-row-link {
    display: flex;
    align-items: center;
    gap: 14px;
    padding: 14px 20px;
    text-decoration: none;
    color: var(--color-text);
    transition: background 0.15s ease;
}

.agent-row-link:hover {
    background: var(--accent-bg);
}

.agent-row-dot {
    width: 8px;
    height: 8px;
    border-radius: 50%;
    flex-shrink: 0;
}

.agent-row-main {
    flex: 1;
    display: flex;
    flex-direction: column;
    gap: 2px;
    min-width: 0;
}

.agent-row-name {
    font-weight: 600;
    font-size: 14px;
}

.agent-row-desc {
    font-size: 12px;
    color: var(--color-text-secondary);
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
}

.agent-row-meta {
    font-size: 12px;
    color: var(--color-text-secondary);
    font-family: monospace;
}

.agent-row-count {
    font-size: 12px;
    color: var(--color-text-secondary);
    white-space: nowrap;
}

.workflow-section {
    margin-top: 8px;
}

.workflow-list {
    display: flex;
    flex-direction: column;
}

.workflow-item {
    display: flex;
    align-items: center;
    gap: 14px;
    background: none;
    border: 1px solid transparent;
    border-radius: var(--radius-sm);
    padding: 12px 16px;
    cursor: pointer;
    width: 100%;
    text-align: left;
    font-family: inherit;
    font-size: 13px;
    color: var(--color-text);
}

.workflow-item:hover {
    background: var(--accent-bg);
    border-color: var(--color-border);
    box-shadow: none;
}

.wf-icon {
    width: 20px;
    height: 20px;
    color: var(--color-accent);
    flex-shrink: 0;
}

.wf-info {
    display: flex;
    flex-direction: column;
    gap: 2px;
}

.wf-name {
    font-weight: 600;
    font-size: 14px;
}

.wf-desc {
    font-size: 12px;
    color: var(--color-text-secondary);
}
</style>