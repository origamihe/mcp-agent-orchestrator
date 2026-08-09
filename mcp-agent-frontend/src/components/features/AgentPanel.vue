<template>
    <div class="agent-panel">
        <div class="panel-header">
            <h2>Agent 管理</h2>
            <span class="subtitle">多 Agent 协作状态与路由</span>
        </div>

        <div class="agent-stats" v-if="agents.length > 0">
            <div class="stat-chip">
                <span class="chip-value">{{ agents.length }}</span>
                <span class="chip-label">Agent 总数</span>
            </div>
            <div class="stat-chip" v-for="(count, type) in typeCounts" :key="type">
                <span class="chip-value">{{ count }}</span>
                <span class="chip-label">{{ typeLabel(type) }}</span>
            </div>
        </div>

        <div v-if="agents.length === 0" class="empty-state">
            <CpuChipIcon class="empty-icon" />
            <p>暂无 Agent 注册</p>
            <p class="empty-hint">Agent 会在系统启动时自动注册</p>
        </div>

        <div class="agent-grid" v-else>
            <div
                v-for="agent in agents"
                :key="agent.agentId"
                :class="['agent-card', typeClass(agent.agentType)]"
            >
                <div class="agent-header">
                    <div class="agent-icon" :style="{ background: typeColor(agent.agentType) }">
                        <component :is="typeIcon(agent.agentType)" class="icon" />
                    </div>
                    <div class="agent-meta">
                        <span class="agent-name">{{ agent.agentName }}</span>
                        <span class="agent-type">{{ typeLabel(agent.agentType) }}</span>
                    </div>
                    <span class="version-tag">{{ agent.version }}</span>
                </div>

                <p class="agent-desc">{{ agent.description }}</p>

                <div class="agent-skills" v-if="agent.skills && agent.skills.length > 0">
                    <span class="section-label">技能</span>
                    <div class="skill-tags">
                        <span v-for="skill in agent.skills" :key="skill" class="skill-tag">
                            {{ skill }}
                        </span>
                    </div>
                </div>

                <div class="agent-tools" v-if="agent.toolNames && agent.toolNames.length > 0">
                    <span class="section-label">工具</span>
                    <div class="tool-tags">
                        <code v-for="tool in agent.toolNames" :key="tool" class="tool-tag">
                            {{ tool }}
                        </code>
                    </div>
                </div>

                <div class="agent-footer">
                    <button class="action-btn test" @click="testAgent(agent.agentId)">
                        测试
                    </button>
                    <button class="action-btn task" @click="runTask(agent.agentId)">
                        委派任务
                    </button>
                    <button class="action-btn config" @click="$emit('navigate', 'settings')">
                        配置
                    </button>
                </div>
            </div>
        </div>

        <div class="workflow-section">
            <h3>多 Agent 协作</h3>
            <div class="workflow-cards">
                <div class="workflow-card">
                    <div class="wf-icon" style="background: rgba(102, 126, 234, 0.1);">
                        <ArrowRightIcon class="icon" style="color: #667eea;" />
                    </div>
                    <span class="wf-name">流水线</span>
                    <span class="wf-desc">Agent 依次处理，输出传递</span>
                    <button class="wf-btn" @click="runPipeline()">执行</button>
                </div>
                <div class="workflow-card">
                    <div class="wf-icon" style="background: rgba(39, 174, 96, 0.1);">
                        <SquaresPlusIcon class="icon" style="color: #27ae60;" />
                    </div>
                    <span class="wf-name">并行执行</span>
                    <span class="wf-desc">多 Agent 同时处理，合并结果</span>
                    <button class="wf-btn" @click="runParallel()">执行</button>
                </div>
                <div class="workflow-card">
                    <div class="wf-icon" style="background: rgba(243, 156, 18, 0.1);">
                        <ArrowPathRoundedSquareIcon class="icon" style="color: #f39c12;" />
                    </div>
                    <span class="wf-name">智能委派</span>
                    <span class="wf-desc">根据技能自动选择最佳 Agent</span>
                    <button class="wf-btn" @click="runDelegate()">执行</button>
                </div>
            </div>
        </div>
    </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import {
    CpuChipIcon,
    CodeBracketIcon,
    ChatBubbleLeftRightIcon,
    GlobeAltIcon,
    DocumentTextIcon,
    ArrowRightIcon,
    SquaresPlusIcon,
    ArrowPathRoundedSquareIcon,
} from '@heroicons/vue/24/outline'
import type { AgentCard } from '@/types/agent'

const props = defineProps<{
    agents: AgentCard[]
}>()

const emit = defineEmits<{
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
        CHAT: '对话',
        CODE: '代码',
        SEARCH: '搜索',
        DOCUMENT: '文档',
        PLANNER: '规划',
        EXECUTOR: '执行',
        GENERAL: '通用',
    }
    return labels[type] || type
}

function typeClass(type: string): string {
    return `type-${type.toLowerCase()}`
}

function typeColor(type: string): string {
    const colors: Record<string, string> = {
        CHAT: 'rgba(102, 126, 234, 0.1)',
        CODE: 'rgba(118, 75, 162, 0.1)',
        SEARCH: 'rgba(39, 174, 96, 0.1)',
        DOCUMENT: 'rgba(255, 107, 107, 0.1)',
        PLANNER: 'rgba(243, 156, 18, 0.1)',
        EXECUTOR: 'rgba(52, 152, 219, 0.1)',
        GENERAL: 'rgba(0, 0, 0, 0.05)',
    }
    return colors[type] || 'rgba(0,0,0,0.05)'
}

function typeIcon(type: string) {
    const icons: Record<string, any> = {
        CHAT: ChatBubbleLeftRightIcon,
        CODE: CodeBracketIcon,
        SEARCH: GlobeAltIcon,
        DOCUMENT: DocumentTextIcon,
        PLANNER: CpuChipIcon,
        EXECUTOR: CpuChipIcon,
        GENERAL: CpuChipIcon,
    }
    return icons[type] || CpuChipIcon
}

function testAgent(agentId: string) {
    emit('test-agent', agentId)
}

function runTask(agentId: string) {
    emit('run-task', agentId)
}

function runPipeline() {
    emit('run-pipeline')
}

function runParallel() {
    emit('run-parallel')
}

function runDelegate() {
    emit('run-delegate')
}
</script>

<style scoped>
.agent-panel {
    padding: 32px;
    max-width: 1000px;
}

.panel-header {
    margin-bottom: 28px;
}

.panel-header h2 {
    font-size: 24px;
    font-weight: 700;
    color: var(--color-text);
}

.subtitle {
    display: block;
    font-size: 13px;
    color: var(--color-text-secondary);
    margin-top: 4px;
}

.agent-stats {
    display: flex;
    gap: 12px;
    margin-bottom: 28px;
    flex-wrap: wrap;
}

.stat-chip {
    background: rgba(255,255,255,0.7);
    border: 1px solid rgba(255,255,255,0.8);
    border-radius: 12px;
    padding: 10px 18px;
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 2px;
    box-shadow: 0 2px 8px rgba(0,0,0,0.03);
    min-width: 80px;
}

.chip-value {
    font-size: 20px;
    font-weight: 700;
    color: #667eea;
}

.chip-label {
    font-size: 11px;
    color: var(--color-text-secondary);
}

.empty-state {
    text-align: center;
    padding: 60px 20px;
    color: var(--color-text-secondary);
}

.empty-icon {
    width: 56px;
    height: 56px;
    margin-bottom: 16px;
    opacity: 0.3;
}

.empty-state p {
    font-size: 16px;
    margin-bottom: 8px;
}

.empty-hint {
    font-size: 13px;
    opacity: 0.7;
}

.agent-grid {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
    gap: 16px;
    margin-bottom: 36px;
}

.agent-card {
    background: rgba(255,255,255,0.7);
    backdrop-filter: blur(20px);
    border-radius: 16px;
    padding: 24px;
    border: 1px solid rgba(255,255,255,0.8);
    box-shadow: 0 4px 16px rgba(0,0,0,0.04);
    transition: all 0.2s;
    border-left: 3px solid transparent;
}

.agent-card.type-code { border-left-color: #764ba2; }
.agent-card.type-search { border-left-color: #27ae60; }
.agent-card.type-chat { border-left-color: #667eea; }
.agent-card.type-document { border-left-color: #ff6b6b; }
.agent-card.type-planner { border-left-color: #f39c12; }
.agent-card.type-executor { border-left-color: #3498db; }

.agent-card:hover {
    box-shadow: 0 6px 24px rgba(102, 126, 234, 0.1);
    transform: translateY(-1px);
}

.agent-header {
    display: flex;
    align-items: center;
    gap: 12px;
    margin-bottom: 12px;
}

.agent-icon {
    width: 42px;
    height: 42px;
    border-radius: 12px;
    display: flex;
    align-items: center;
    justify-content: center;
}

.agent-icon .icon {
    width: 20px;
    height: 20px;
    color: #667eea;
}

.agent-meta {
    flex: 1;
    display: flex;
    flex-direction: column;
}

.agent-name {
    font-size: 15px;
    font-weight: 600;
    color: var(--color-text);
}

.agent-type {
    font-size: 11px;
    color: var(--color-text-secondary);
}

.version-tag {
    font-size: 10px;
    background: rgba(0,0,0,0.04);
    padding: 2px 8px;
    border-radius: 6px;
    color: #999;
}

.agent-desc {
    font-size: 13px;
    color: var(--color-text-secondary);
    line-height: 1.5;
    margin-bottom: 14px;
}

.agent-skills, .agent-tools {
    margin-bottom: 12px;
}

.section-label {
    font-size: 11px;
    font-weight: 600;
    color: var(--color-text-secondary);
    text-transform: uppercase;
    display: block;
    margin-bottom: 6px;
}

.skill-tags {
    display: flex;
    flex-wrap: wrap;
    gap: 6px;
}

.skill-tag {
    font-size: 11px;
    padding: 3px 10px;
    border-radius: 8px;
    background: rgba(102, 126, 234, 0.08);
    color: #667eea;
    font-weight: 500;
}

.tool-tags {
    display: flex;
    flex-wrap: wrap;
    gap: 6px;
}

.tool-tag {
    font-size: 11px;
    padding: 3px 10px;
    border-radius: 8px;
    background: rgba(0,0,0,0.04);
    color: #666;
    font-family: 'SF Mono', 'Cascadia Code', monospace;
}

.agent-footer {
    display: flex;
    gap: 8px;
    margin-top: 4px;
}

.action-btn {
    flex: 1;
    padding: 8px;
    border-radius: 10px;
    font-size: 12px;
    font-weight: 500;
    cursor: pointer;
    border: none;
    transition: all 0.2s;
}

.action-btn.test {
    background: rgba(39, 174, 96, 0.08);
    color: #27ae60;
}

.action-btn.test:hover {
    background: rgba(39, 174, 96, 0.15);
}

.action-btn.task {
    background: rgba(102, 126, 234, 0.08);
    color: #667eea;
}

.action-btn.task:hover {
    background: rgba(102, 126, 234, 0.15);
}

.action-btn.config {
    background: rgba(0,0,0,0.04);
    color: #666;
}

.action-btn.config:hover {
    background: rgba(0,0,0,0.08);
}

.workflow-section {
    background: rgba(255,255,255,0.7);
    backdrop-filter: blur(20px);
    border-radius: 16px;
    padding: 24px;
    border: 1px solid rgba(255,255,255,0.8);
    box-shadow: 0 4px 16px rgba(0,0,0,0.04);
}

.workflow-section h3 {
    font-size: 16px;
    font-weight: 600;
    margin-bottom: 16px;
    color: var(--color-text);
}

.workflow-cards {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
    gap: 16px;
}

.workflow-card {
    background: rgba(0,0,0,0.02);
    border-radius: 12px;
    padding: 20px;
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 8px;
    text-align: center;
}

.wf-icon {
    width: 48px;
    height: 48px;
    border-radius: 14px;
    display: flex;
    align-items: center;
    justify-content: center;
}

.wf-icon .icon {
    width: 24px;
    height: 24px;
}

.wf-name {
    font-size: 14px;
    font-weight: 600;
    color: var(--color-text);
}

.wf-desc {
    font-size: 12px;
    color: var(--color-text-secondary);
}

.wf-btn {
    padding: 8px 24px;
    background: rgba(102, 126, 234, 0.08);
    border: 1px solid rgba(102, 126, 234, 0.2);
    border-radius: 10px;
    color: #667eea;
    font-size: 13px;
    font-weight: 500;
    cursor: pointer;
    transition: all 0.2s;
    margin-top: 4px;
}

.wf-btn:hover {
    background: rgba(102, 126, 234, 0.15);
}
</style>