<template>
    <div class="page">
        <div class="page-header">
            <button class="btn-back" @click="$router.push('/agents')">← 返回 Agent 列表</button>
            <h2>{{ agent?.agentName || 'Agent 详情' }}</h2>
            <span class="agent-id">{{ agent?.agentId }}</span>
            <StatusBadge v-if="agent" :type="statusType" :text="agent.status || '未知'" />
        </div>

        <div class="tabs">
            <button
                v-for="tab in tabs"
                :key="tab.id"
                :class="['tab', { active: activeTab === tab.id }]"
                @click="switchTab(tab.id)"
            >
                {{ tab.label }}
                <span v-if="tab.badge" class="tab-badge">{{ tab.badge }}</span>
            </button>
        </div>

        <div class="tab-content">
            <LoadingSpinner v-if="loading" text="加载中..." />

            <template v-else-if="agent">
                <div v-if="activeTab === 'overview'" class="tab-panel">
                    <div class="overview-grid">
                        <div class="info-card">
                            <h4>基本信息</h4>
                            <dl>
                                <dt>名称</dt><dd>{{ agent.agentName }}</dd>
                                <dt>类型</dt><dd><span class="type-badge">{{ typeLabel }}</span></dd>
                                <dt>版本</dt><dd>{{ agent.version }}</dd>
                                <dt>描述</dt><dd>{{ agent.description }}</dd>
                            </dl>
                        </div>
                        <div class="info-card">
                            <h4>技能</h4>
                            <div class="skill-tags" v-if="agent.skills?.length">
                                <span v-for="s in agent.skills" :key="s" class="skill-tag">{{ s }}</span>
                            </div>
                            <p v-else class="empty-hint">未配置技能</p>
                        </div>
                        <div class="info-card">
                            <h4>工具</h4>
                            <div class="tool-tags" v-if="agent.toolNames?.length">
                                <code v-for="t in agent.toolNames" :key="t" class="tool-tag">{{ t }}</code>
                            </div>
                            <p v-else class="empty-hint">未配置工具</p>
                        </div>
                        <div class="info-card">
                            <h4>快速操作</h4>
                            <div class="quick-actions">
                                <button class="action-btn" @click="$router.push(`/workspace/${agent.agentId}`)">
                                    进入 Workspace
                                </button>
                                <button class="action-btn secondary" @click="activeTab = 'runs'">
                                    查看执行历史
                                </button>
                            </div>
                        </div>
                    </div>
                </div>

                <div v-else-if="activeTab === 'config'" class="tab-panel">
                    <LoadingSpinner v-if="configLoading" text="加载配置中..." />
                    <div class="config-section" v-else-if="agentStore.currentConfig">
                        <div class="config-group">
                            <h4>Identity</h4>
                            <dl>
                                <dt>名称</dt><dd>{{ agentStore.currentConfig.identity.name }}</dd>
                                <dt>描述</dt><dd>{{ agentStore.currentConfig.identity.description }}</dd>
                                <dt>版本</dt><dd>{{ agentStore.currentConfig.identity.version }}</dd>
                                <dt>Agent 类型</dt><dd>{{ agentStore.currentConfig.identity.agentType }}</dd>
                            </dl>
                        </div>
                        <div class="config-group">
                            <h4>Model</h4>
                            <dl>
                                <dt>Provider</dt><dd>{{ agentStore.currentConfig.model.provider }}</dd>
                                <dt>Model</dt><dd>{{ agentStore.currentConfig.model.modelName }}</dd>
                                <dt>Temperature</dt><dd>{{ agentStore.currentConfig.model.temperature }}</dd>
                                <dt>Max Tokens</dt><dd>{{ agentStore.currentConfig.model.maxTokens }}</dd>
                            </dl>
                        </div>
                        <div class="config-group">
                            <h4>Behavior</h4>
                            <dl>
                                <dt>Persona</dt><dd>{{ agentStore.currentConfig.behavior.persona }}</dd>
                                <dt>Reflection</dt><dd>{{ agentStore.currentConfig.behavior.reflectionEnabled ? '启用' : '禁用' }}</dd>
                                <dt>Max Iterations</dt><dd>{{ agentStore.currentConfig.behavior.maxIterations }}</dd>
                            </dl>
                        </div>
                        <div class="config-group">
                            <h4>Context</h4>
                            <dl>
                                <dt>Memory</dt><dd>{{ agentStore.currentConfig.context.memoryEnabled ? '启用' : '禁用' }}</dd>
                                <dt>Knowledge</dt><dd>{{ agentStore.currentConfig.context.knowledgeEnabled ? '启用' : '禁用' }}</dd>
                                <dt>Workspace</dt><dd>{{ agentStore.currentConfig.context.workspaceEnabled ? '启用' : '禁用' }}</dd>
                                <dt>Context Window</dt><dd>{{ agentStore.currentConfig.context.contextWindowSize }}</dd>
                            </dl>
                        </div>
                        <div class="config-group">
                            <h4>Security</h4>
                            <dl>
                                <dt>Risk Level</dt><dd><RiskBadge :level="agentStore.currentConfig.security.riskLevel" /></dd>
                                <dt>Sandbox</dt><dd>{{ agentStore.currentConfig.security.sandboxEnabled ? agentStore.currentConfig.security.sandboxType : '禁用' }}</dd>
                                <dt>Confirmation</dt><dd>{{ agentStore.currentConfig.security.confirmationRequired ? '需要' : '不需要' }}</dd>
                            </dl>
                        </div>
                    </div>
                    <button v-else class="btn-load" @click="loadConfig" :disabled="configLoading">
                        {{ configLoading ? '加载中...' : '加载配置' }}
                    </button>
                </div>

                <div v-else-if="activeTab === 'prompt'" class="tab-panel">
                    <LoadingSpinner v-if="promptLoading" text="加载 Prompt 中..." />
                    <div v-else-if="prompt" class="prompt-editor">
                        <div class="prompt-header">
                            <span class="prompt-name">{{ prompt.name }}</span>
                            <button class="btn-save" @click="savePrompt" :disabled="promptSaving">
                                {{ promptSaving ? '保存中...' : '保存' }}
                            </button>
                        </div>
                        <textarea
                            v-model="promptTemplate"
                            class="prompt-textarea"
                            placeholder="输入 System Prompt..."
                            rows="15"
                        ></textarea>
                        <div class="prompt-hint">
                            <span>支持变量: {user_input}, {agent_name}, {current_date}, {tools_list}</span>
                        </div>
                    </div>
                    <button v-else class="btn-load" @click="loadPrompt" :disabled="promptLoading">
                        {{ promptLoading ? '加载中...' : '加载 Prompt' }}
                    </button>
                </div>

                <div v-else-if="activeTab === 'memory'" class="tab-panel">
                    <LoadingSpinner v-if="memoryLoading" text="加载记忆中..." />
                    <div v-else-if="agentMemories.length > 0" class="memory-list">
                        <div v-for="item in agentMemories" :key="item.id" class="memory-card">
                            <div class="memory-header">
                                <span :class="['type-badge', `type-${item.type}`]">{{ item.type }}</span>
                                <span class="memory-importance" v-if="'importance' in item">
                                    重要性: {{ (item as any).importance }}/10
                                </span>
                            </div>
                            <p class="memory-content">{{ 'content' in item ? item.content : (item as any).entry?.content }}</p>
                            <div class="memory-footer">
                                <span class="memory-meta">{{ formatDate(item.createdAt || (item as any).entry?.createdAt) }}</span>
                            </div>
                        </div>
                    </div>
                    <EmptyState v-else text="该 Agent 暂无关联记忆" />
                </div>

                <div v-else-if="activeTab === 'tools'" class="tab-panel">
                    <LoadingSpinner v-if="toolsLoading" text="加载工具中..." />
                    <div v-else-if="agentTools.length > 0" class="tools-section">
                        <div class="tools-header">
                            <span class="tools-count">共 {{ agentTools.length }} 个工具</span>
                        </div>
                        <div class="tool-grid">
                            <div v-for="tool in agentTools" :key="tool.name" class="tool-card">
                                <div class="tool-card-header">
                                    <code class="tool-name">{{ tool.name }}</code>
                                    <RiskBadge v-if="tool.riskLevel" :level="tool.riskLevel" />
                                </div>
                                <p class="tool-desc">{{ tool.description }}</p>
                                <div class="tool-meta">
                                    <span v-if="tool.sandboxEnabled">沙箱: {{ tool.sandboxType }}</span>
                                    <span>超时: {{ tool.timeout }}s</span>
                                </div>
                            </div>
                        </div>
                    </div>
                    <EmptyState v-else text="该 Agent 未配置工具" />
                </div>

                <div v-else-if="activeTab === 'runs'" class="tab-panel">
                    <LoadingSpinner v-if="runsLoading" text="加载执行历史中..." />
                    <div v-else-if="agentRuns.length > 0" class="runs-section">
                        <div class="run-list">
                            <div
                                v-for="run in agentRuns"
                                :key="run.id"
                                class="run-row"
                                @click="$router.push(`/runs/${run.id}`)"
                            >
                                <div class="run-info">
                                    <span class="run-intent">{{ run.intent }}</span>
                                    <span class="run-meta">
                                        {{ formatDuration(run.duration) }} · {{ run.toolCallCount }} tool calls
                                    </span>
                                </div>
                                <StatusBadge
                                    :type="runStatusType(run.status)"
                                    :text="runStatusLabel(run.status)"
                                />
                            </div>
                        </div>
                    </div>
                    <EmptyState v-else text="该 Agent 暂无执行记录" />
                </div>

                <div v-else-if="activeTab === 'permissions'" class="tab-panel">
                    <LoadingSpinner v-if="permLoading" text="加载权限中..." />
                    <div v-else-if="agentPermissions" class="permissions-section">
                        <div class="perm-group">
                            <h4>Allowed Hosts</h4>
                            <div class="perm-chips">
                                <span v-for="h in agentPermissions.allowedHosts" :key="h" class="perm-chip allowed">{{ h }}</span>
                                <span v-if="!agentPermissions.allowedHosts?.length" class="empty-hint">全部允许</span>
                            </div>
                        </div>
                        <div class="perm-group">
                            <h4>Allowed Workspaces</h4>
                            <div class="perm-chips">
                                <span v-for="w in agentPermissions.allowedWorkspaces" :key="w" class="perm-chip allowed">{{ w }}</span>
                                <span v-if="!agentPermissions.allowedWorkspaces?.length" class="empty-hint">全部允许</span>
                            </div>
                        </div>
                        <div class="perm-group">
                            <h4>Capability Permissions</h4>
                            <div class="perm-list">
                                <div v-for="(perm, cap) in agentPermissions.capabilities || {}" :key="cap" class="perm-row">
                                    <span class="perm-capability">{{ cap }}</span>
                                    <span :class="['perm-value', perm]">{{ perm }}</span>
                                </div>
                            </div>
                        </div>
                    </div>
                    <button v-else class="btn-load" @click="loadPermissions" :disabled="permLoading">
                        {{ permLoading ? '加载中...' : '加载权限' }}
                    </button>
                </div>
            </template>
            <EmptyState v-else text="Agent 未找到" />
        </div>
    </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { useAgentStore } from '@/stores/agentStore'
import { useMemoryStore } from '@/stores/memoryStore'
import { useRunStore } from '@/stores/runStore'
import { useToast } from '@/composables/useToast'
import StatusBadge from '@/components/common/StatusBadge.vue'
import RiskBadge from '@/components/common/RiskBadge.vue'
import EmptyState from '@/components/common/EmptyState.vue'
import LoadingSpinner from '@/components/common/LoadingSpinner.vue'
import * as agentsApi from '@/api/agents'
import type { MemoryEntry } from '@/types/memory'
import type { RunSummary } from '@/types/run'

const route = useRoute()
const agentStore = useAgentStore()
const memoryStore = useMemoryStore()
const runStore = useRunStore()
const toast = useToast()

const activeTab = ref('overview')
const tabs = [
    { id: 'overview', label: '概览' },
    { id: 'config', label: '配置' },
    { id: 'prompt', label: 'Prompt' },
    { id: 'memory', label: '记忆' },
    { id: 'tools', label: '工具' },
    { id: 'runs', label: '执行历史' },
    { id: 'permissions', label: '权限' },
]

const agentId = computed(() => route.params.id as string)
const agent = computed(() => agentStore.currentAgent)
const loading = computed(() => agentStore.isLoading)

const configLoading = ref(false)
const promptLoading = ref(false)
const promptSaving = ref(false)
const memoryLoading = ref(false)
const toolsLoading = ref(false)
const runsLoading = ref(false)
const permLoading = ref(false)

const prompt = ref<{ name: string; templateText: string } | null>(null)
const promptTemplate = ref('')
const agentMemories = ref<MemoryEntry[]>([])
const agentTools = ref<any[]>([])
const agentRuns = ref<RunSummary[]>([])
const agentPermissions = ref<any>(null)

const typeLabel = computed(() => {
    const labels: Record<string, string> = {
        CHAT: '对话', CODE: '代码', SEARCH: '搜索', DOCUMENT: '文档',
        PLANNER: '规划', EXECUTOR: '执行', GENERAL: '通用',
    }
    return labels[agent.value?.agentType || ''] || agent.value?.agentType || '未知'
})

const statusType = computed(() => {
    const map: Record<string, 'success' | 'warning' | 'error' | 'info' | 'neutral'> = {
        online: 'success', active: 'success', running: 'info',
        idle: 'warning', offline: 'neutral', error: 'error',
    }
    return map[agent.value?.status || ''] || 'neutral'
})

function runStatusType(status: string): 'success' | 'warning' | 'error' | 'info' | 'neutral' {
    const map: Record<string, string> = {
        completed: 'success', failed: 'error', running: 'info', pending: 'warning', cancelled: 'neutral',
    }
    return (map[status] || 'neutral') as any
}

function runStatusLabel(status: string): string {
    const labels: Record<string, string> = { pending: '等待', running: '执行中', completed: '完成', failed: '失败', cancelled: '取消' }
    return labels[status] || status
}

function formatDuration(ms: number): string {
    if (!ms) return '--'
    if (ms < 1000) return `${ms}ms`
    if (ms < 60000) return `${(ms / 1000).toFixed(1)}s`
    return `${(ms / 60000).toFixed(1)}m`
}

function formatDate(dateStr: string): string {
    if (!dateStr) return ''
    return new Date(dateStr).toLocaleString('zh-CN')
}

function switchTab(tabId: string) {
    activeTab.value = tabId
    if (tabId === 'config') loadConfig()
    else if (tabId === 'prompt') loadPrompt()
    else if (tabId === 'memory') loadMemories()
    else if (tabId === 'tools') loadTools()
    else if (tabId === 'runs') loadRuns()
    else if (tabId === 'permissions') loadPermissions()
}

async function loadConfig() {
    configLoading.value = true
    try {
        await agentStore.fetchAgentConfig(agentId.value)
    } catch {
        toast.error('加载配置失败')
    } finally {
        configLoading.value = false
    }
}

async function loadPrompt() {
    promptLoading.value = true
    try {
        prompt.value = await agentsApi.fetchAgentPrompt(agentId.value)
        promptTemplate.value = prompt.value.templateText
    } catch {
        toast.error('加载 Prompt 失败')
    } finally {
        promptLoading.value = false
    }
}

async function savePrompt() {
    if (!prompt.value) return
    promptSaving.value = true
    try {
        await agentsApi.updateAgentPrompt(agentId.value, { templateText: promptTemplate.value })
        prompt.value.templateText = promptTemplate.value
        toast.success('Prompt 已保存')
    } catch {
        toast.error('保存 Prompt 失败')
    } finally {
        promptSaving.value = false
    }
}

async function loadMemories() {
    memoryLoading.value = true
    try {
        await memoryStore.fetchMemories({ agentId: agentId.value })
        agentMemories.value = memoryStore.memories
    } catch {
        toast.error('加载记忆失败')
    } finally {
        memoryLoading.value = false
    }
}

async function loadTools() {
    toolsLoading.value = true
    try {
        const names = await agentsApi.fetchAgentTools(agentId.value)
        agentTools.value = names.map((name: string) => ({
            name,
            description: '',
            riskLevel: 'L2',
            sandboxEnabled: false,
            sandboxType: 'none',
            timeout: 30,
        }))
    } catch {
        toast.error('加载工具失败')
    } finally {
        toolsLoading.value = false
    }
}

async function loadRuns() {
    runsLoading.value = true
    try {
        await runStore.fetchRuns({ agentId: agentId.value })
        agentRuns.value = runStore.runs as RunSummary[]
    } catch {
        toast.error('加载执行历史失败')
    } finally {
        runsLoading.value = false
    }
}

async function loadPermissions() {
    permLoading.value = true
    try {
        agentPermissions.value = await agentsApi.fetchAgentPermissions(agentId.value)
    } catch {
        toast.error('加载权限失败')
    } finally {
        permLoading.value = false
    }
}

async function loadAgent() {
    if (agentId.value) {
        await agentStore.fetchAgentById(agentId.value)
    }
}

watch(agentId, () => {
    agentStore.clearCurrentAgent()
    activeTab.value = 'overview'
    loadAgent()
}, { immediate: false })

onMounted(() => {
    loadAgent()
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

.agent-id {
    font-size: 12px;
    color: var(--color-text-secondary);
    background: rgba(0,0,0,0.04);
    padding: 4px 10px;
    border-radius: 6px;
    font-family: monospace;
}

.tabs {
    display: flex;
    gap: 4px;
    margin-bottom: 24px;
    border-bottom: 1px solid rgba(0,0,0,0.06);
    padding-bottom: 0;
}

.tab {
    padding: 10px 20px;
    border: none;
    background: none;
    cursor: pointer;
    font-size: 14px;
    color: var(--color-text-secondary);
    border-bottom: 2px solid transparent;
    transition: all 0.2s;
    display: flex;
    align-items: center;
    gap: 6px;
}

.tab.active {
    color: var(--color-text);
    border-bottom-color: #667eea;
    font-weight: 600;
}

.tab-badge {
    background: #667eea;
    color: #fff;
    font-size: 10px;
    padding: 1px 6px;
    border-radius: 10px;
    font-weight: 600;
}

.tab-content {
    min-height: 300px;
}

.tab-panel {
    animation: fadeIn 0.3s ease;
}

@keyframes fadeIn {
    from { opacity: 0; transform: translateY(8px); }
    to { opacity: 1; transform: translateY(0); }
}

.empty-hint {
    color: var(--color-text-secondary);
    font-size: 13px;
    padding: 20px 0;
    text-align: center;
}

.overview-grid {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
    gap: 16px;
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
    font-size: 15px;
    font-weight: 600;
    margin-bottom: 16px;
}

.info-card dl {
    display: grid;
    grid-template-columns: 80px 1fr;
    gap: 8px;
    font-size: 13px;
}

.info-card dt {
    color: var(--color-text-secondary);
    font-weight: 500;
}

.type-badge {
    padding: 2px 10px;
    border-radius: 20px;
    font-size: 12px;
    background: rgba(102, 126, 234, 0.1);
    color: #667eea;
    font-weight: 600;
}

.skill-tags, .tool-tags {
    display: flex;
    flex-wrap: wrap;
    gap: 6px;
}

.skill-tag {
    padding: 4px 12px;
    border-radius: 20px;
    font-size: 12px;
    background: rgba(39, 174, 96, 0.1);
    color: #27ae60;
}

.tool-tag {
    padding: 4px 10px;
    border-radius: 6px;
    font-size: 12px;
    background: rgba(0,0,0,0.04);
    font-family: monospace;
}

.quick-actions {
    display: flex;
    flex-direction: column;
    gap: 8px;
}

.action-btn {
    padding: 10px 18px;
    border-radius: 10px;
    border: none;
    background: var(--gradient-dream);
    color: #fff;
    cursor: pointer;
    font-size: 13px;
    font-weight: 500;
}

.action-btn.secondary {
    background: rgba(102, 126, 234, 0.08);
    color: #667eea;
}

.config-section {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
    gap: 16px;
}

.config-group {
    background: rgba(255,255,255,0.7);
    border-radius: 12px;
    padding: 20px;
    border: 1px solid rgba(255,255,255,0.8);
}

.config-group h4 {
    font-size: 14px;
    font-weight: 600;
    margin-bottom: 12px;
    color: #667eea;
}

.config-group dl {
    display: grid;
    grid-template-columns: 100px 1fr;
    gap: 6px;
    font-size: 13px;
}

.config-group dt {
    color: var(--color-text-secondary);
    font-weight: 500;
}

.btn-load {
    margin-top: 16px;
    padding: 10px 24px;
    border-radius: 10px;
    border: 1px solid #667eea;
    background: rgba(102, 126, 234, 0.08);
    color: #667eea;
    cursor: pointer;
    font-size: 13px;
    font-weight: 500;
}

.btn-load:disabled {
    opacity: 0.5;
}

.prompt-editor {
    background: rgba(255,255,255,0.7);
    border-radius: 16px;
    padding: 24px;
    border: 1px solid rgba(255,255,255,0.8);
}

.prompt-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 16px;
}

.prompt-name {
    font-weight: 600;
    font-size: 15px;
}

.btn-save {
    padding: 8px 20px;
    border-radius: 10px;
    border: none;
    background: var(--gradient-dream);
    color: #fff;
    cursor: pointer;
    font-size: 13px;
    font-weight: 500;
}

.btn-save:disabled {
    opacity: 0.5;
}

.prompt-textarea {
    width: 100%;
    padding: 16px;
    border-radius: 10px;
    border: 1px solid rgba(0,0,0,0.1);
    background: rgba(0,0,0,0.02);
    font-size: 14px;
    font-family: monospace;
    line-height: 1.6;
    resize: vertical;
    min-height: 300px;
}

.prompt-hint {
    margin-top: 12px;
    font-size: 12px;
    color: var(--color-text-secondary);
    padding: 8px 14px;
    background: rgba(102, 126, 234, 0.04);
    border-radius: 8px;
}

.memory-list {
    display: flex;
    flex-direction: column;
    gap: 10px;
}

.memory-card {
    background: rgba(255,255,255,0.7);
    border-radius: 12px;
    padding: 16px 20px;
    border: 1px solid rgba(255,255,255,0.8);
}

.memory-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 8px;
}

.type-badge {
    padding: 3px 10px;
    border-radius: 20px;
    font-size: 12px;
    font-weight: 600;
}

.type-badge.type-memory { background: #e3f2fd; color: #1565c0; }
.type-badge.type-project { background: #e8f5e9; color: #2e7d32; }
.type-badge.type-session { background: #fff3e0; color: #ef6c00; }
.type-badge.type-user { background: rgba(102, 126, 234, 0.1); color: #667eea; }

.memory-importance {
    font-size: 12px;
    color: var(--color-text-secondary);
}

.memory-content {
    font-size: 14px;
    line-height: 1.5;
}

.memory-footer {
    margin-top: 8px;
}

.memory-meta {
    font-size: 12px;
    color: var(--color-text-secondary);
}

.tools-section {
    background: rgba(255,255,255,0.7);
    border-radius: 16px;
    padding: 24px;
    border: 1px solid rgba(255,255,255,0.8);
}

.tools-header {
    margin-bottom: 16px;
}

.tools-count {
    font-size: 13px;
    color: var(--color-text-secondary);
}

.tool-grid {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
    gap: 12px;
}

.tool-card {
    background: rgba(255,255,255,0.7);
    border-radius: 12px;
    padding: 16px;
    border: 1px solid rgba(255,255,255,0.8);
}

.tool-card-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 8px;
}

.tool-name {
    font-weight: 600;
    font-size: 14px;
}

.tool-desc {
    font-size: 13px;
    color: var(--color-text-secondary);
    margin-bottom: 8px;
    line-height: 1.4;
}

.tool-meta {
    display: flex;
    gap: 14px;
    font-size: 12px;
    color: var(--color-text-secondary);
}

.runs-section {
    background: rgba(255,255,255,0.7);
    border-radius: 16px;
    padding: 24px;
    border: 1px solid rgba(255,255,255,0.8);
}

.run-list {
    display: flex;
    flex-direction: column;
    gap: 8px;
}

.run-row {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 12px 16px;
    border-radius: 10px;
    background: rgba(0,0,0,0.02);
    cursor: pointer;
    transition: background 0.2s;
}

.run-row:hover {
    background: rgba(102, 126, 234, 0.06);
}

.run-info {
    display: flex;
    flex-direction: column;
    gap: 4px;
}

.run-intent {
    font-weight: 600;
    font-size: 14px;
}

.run-meta {
    font-size: 12px;
    color: var(--color-text-secondary);
}

.permissions-section {
    background: rgba(255,255,255,0.7);
    border-radius: 16px;
    padding: 24px;
    border: 1px solid rgba(255,255,255,0.8);
}

.perm-group {
    margin-bottom: 20px;
}

.perm-group:last-child {
    margin-bottom: 0;
}

.perm-group h4 {
    font-size: 14px;
    font-weight: 600;
    margin-bottom: 10px;
    color: #667eea;
}

.perm-chips {
    display: flex;
    flex-wrap: wrap;
    gap: 6px;
}

.perm-chip {
    padding: 3px 10px;
    border-radius: 20px;
    font-size: 12px;
}

.perm-chip.allowed {
    background: rgba(39, 174, 96, 0.08);
    color: #27ae60;
}

.perm-list {
    display: flex;
    flex-direction: column;
    gap: 8px;
}

.perm-row {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 8px 14px;
    background: rgba(0,0,0,0.02);
    border-radius: 8px;
    font-size: 13px;
}

.perm-capability {
    font-weight: 500;
}

.perm-value {
    padding: 2px 10px;
    border-radius: 20px;
    font-size: 12px;
    font-weight: 600;
}

.perm-value.allowed { background: #e8f5e9; color: #2e7d32; }
.perm-value.denied { background: #ffebee; color: #c62828; }
.perm-value.confirmation { background: #fff3e0; color: #ef6c00; }
</style>