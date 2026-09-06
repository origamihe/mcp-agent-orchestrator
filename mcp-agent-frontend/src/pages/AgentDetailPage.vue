<template>
    <div class="page">
        <div class="page-header">
            <button class="btn-secondary btn-back" @click="$router.push('/agents')">← Back to Agents</button>
            <div class="header-main" v-if="agent">
                <h2>{{ agent.agentName }}</h2>
                <span class="agent-id">{{ agent.agentId }}</span>
                <StatusBadge :type="statusType" :text="agent.status || 'Unknown'" />
            </div>
        </div>

        <div class="tabs">
            <button
                v-for="tab in tabs"
                :key="tab.id"
                :class="['tab', { active: activeTab === tab.id }]"
                @click="switchTab(tab.id)"
            >
                {{ tab.label }}
            </button>
        </div>

        <div class="tab-content">
            <LoadingSpinner v-if="loading" text="Loading..." />

            <template v-else-if="agent">
                <div v-if="activeTab === 'overview'" class="tab-panel">
                    <div class="section">
                        <h3 class="section-title">Agent Info</h3>
                        <div class="info-grid">
                            <div class="info-item">
                                <span class="info-label">Name</span>
                                <span class="info-value">{{ agent.agentName }}</span>
                            </div>
                            <div class="info-item">
                                <span class="info-label">Type</span>
                                <span class="info-value type-badge">{{ typeLabel }}</span>
                            </div>
                            <div class="info-item">
                                <span class="info-label">Version</span>
                                <span class="info-value">{{ agent.version }}</span>
                            </div>
                            <div class="info-item">
                                <span class="info-label">Description</span>
                                <span class="info-value">{{ agent.description }}</span>
                            </div>
                        </div>
                    </div>

                    <div class="section-separator"></div>

                    <div class="section" v-if="agent.skills?.length">
                        <h3 class="section-title">Skills</h3>
                        <div class="chip-list">
                            <span v-for="s in agent.skills" :key="s" class="chip skill-chip">{{ s }}</span>
                        </div>
                    </div>

                    <div class="section-separator"></div>

                    <div class="section" v-if="agent.toolNames?.length">
                        <h3 class="section-title">Tools</h3>
                        <div class="chip-list">
                            <code v-for="t in agent.toolNames" :key="t" class="chip tool-chip">{{ t }}</code>
                        </div>
                    </div>

                    <div class="section-separator"></div>

                    <div class="section">
                        <h3 class="section-title">Actions</h3>
                        <div class="action-row">
                            <button class="btn-primary" @click="$router.push(`/workspace/${agent.agentId}`)">
                                Open Workspace
                            </button>
                            <button class="btn-secondary" @click="activeTab = 'runs'">
                                View Runs
                            </button>
                        </div>
                    </div>
                </div>

                <div v-else-if="activeTab === 'config'" class="tab-panel">
                    <LoadingSpinner v-if="configLoading" text="Loading config..." />
                    <div class="config-section" v-else-if="agentStore.currentConfig">
                        <div class="section">
                            <h3 class="section-title">Identity</h3>
                            <div class="info-grid">
                                <div class="info-item"><span class="info-label">Name</span><span class="info-value">{{ agentStore.currentConfig.identity.name }}</span></div>
                                <div class="info-item"><span class="info-label">Description</span><span class="info-value">{{ agentStore.currentConfig.identity.description }}</span></div>
                                <div class="info-item"><span class="info-label">Version</span><span class="info-value">{{ agentStore.currentConfig.identity.version }}</span></div>
                                <div class="info-item"><span class="info-label">Agent Type</span><span class="info-value">{{ agentStore.currentConfig.identity.agentType }}</span></div>
                            </div>
                        </div>
                        <div class="section-separator"></div>
                        <div class="section">
                            <h3 class="section-title">Model</h3>
                            <div class="info-grid">
                                <div class="info-item"><span class="info-label">Provider</span><span class="info-value">{{ agentStore.currentConfig.model.provider }}</span></div>
                                <div class="info-item"><span class="info-label">Model</span><span class="info-value">{{ agentStore.currentConfig.model.modelName }}</span></div>
                                <div class="info-item"><span class="info-label">Temperature</span><span class="info-value">{{ agentStore.currentConfig.model.temperature }}</span></div>
                                <div class="info-item"><span class="info-label">Max Tokens</span><span class="info-value">{{ agentStore.currentConfig.model.maxTokens }}</span></div>
                            </div>
                        </div>
                        <div class="section-separator"></div>
                        <div class="section">
                            <h3 class="section-title">Behavior</h3>
                            <div class="info-grid">
                                <div class="info-item"><span class="info-label">Persona</span><span class="info-value">{{ agentStore.currentConfig.behavior.persona }}</span></div>
                                <div class="info-item"><span class="info-label">Reflection</span><span class="info-value">{{ agentStore.currentConfig.behavior.reflectionEnabled ? 'Enabled' : 'Disabled' }}</span></div>
                                <div class="info-item"><span class="info-label">Max Iterations</span><span class="info-value">{{ agentStore.currentConfig.behavior.maxIterations }}</span></div>
                            </div>
                        </div>
                        <div class="section-separator"></div>
                        <div class="section">
                            <h3 class="section-title">Context</h3>
                            <div class="info-grid">
                                <div class="info-item"><span class="info-label">Memory</span><span class="info-value">{{ agentStore.currentConfig.context.memoryEnabled ? 'Enabled' : 'Disabled' }}</span></div>
                                <div class="info-item"><span class="info-label">Knowledge</span><span class="info-value">{{ agentStore.currentConfig.context.knowledgeEnabled ? 'Enabled' : 'Disabled' }}</span></div>
                                <div class="info-item"><span class="info-label">Workspace</span><span class="info-value">{{ agentStore.currentConfig.context.workspaceEnabled ? 'Enabled' : 'Disabled' }}</span></div>
                                <div class="info-item"><span class="info-label">Context Window</span><span class="info-value">{{ agentStore.currentConfig.context.contextWindowSize }}</span></div>
                            </div>
                        </div>
                        <div class="section-separator"></div>
                        <div class="section">
                            <h3 class="section-title">Security</h3>
                            <div class="info-grid">
                                <div class="info-item"><span class="info-label">Risk Level</span><span class="info-value"><RiskBadge :level="agentStore.currentConfig.security.riskLevel" /></span></div>
                                <div class="info-item"><span class="info-label">Sandbox</span><span class="info-value">{{ agentStore.currentConfig.security.sandboxEnabled ? agentStore.currentConfig.security.sandboxType : 'Disabled' }}</span></div>
                                <div class="info-item"><span class="info-label">Confirmation</span><span class="info-value">{{ agentStore.currentConfig.security.confirmationRequired ? 'Required' : 'Not required' }}</span></div>
                            </div>
                        </div>
                    </div>
                    <button v-else class="btn-secondary" @click="loadConfig" :disabled="configLoading">
                        {{ configLoading ? 'Loading...' : 'Load Config' }}
                    </button>
                </div>

                <div v-else-if="activeTab === 'prompt'" class="tab-panel">
                    <LoadingSpinner v-if="promptLoading" text="Loading prompt..." />
                    <div v-else-if="prompt" class="section">
                        <div class="prompt-header">
                            <span class="prompt-name">{{ prompt.name }}</span>
                            <button class="btn-primary" @click="savePrompt" :disabled="promptSaving">
                                {{ promptSaving ? 'Saving...' : 'Save' }}
                            </button>
                        </div>
                        <textarea
                            v-model="promptTemplate"
                            class="prompt-textarea"
                            placeholder="Enter system prompt..."
                            rows="15"
                        ></textarea>
                        <div class="prompt-hint">
                            <span>Variables: {user_input}, {agent_name}, {current_date}, {tools_list}</span>
                        </div>
                    </div>
                    <button v-else class="btn-secondary" @click="loadPrompt" :disabled="promptLoading">
                        {{ promptLoading ? 'Loading...' : 'Load Prompt' }}
                    </button>
                </div>

                <div v-else-if="activeTab === 'memory'" class="tab-panel">
                    <LoadingSpinner v-if="memoryLoading" text="Loading memories..." />
                    <div v-else-if="agentMemories.length > 0" class="memory-list">
                        <div v-for="item in agentMemories" :key="item.id" class="memory-item">
                            <div class="memory-item-header">
                                <span :class="['type-badge', `type-${item.type}`]">{{ item.type }}</span>
                                <span class="memory-item-importance" v-if="'importance' in item">
                                    {{ (item as any).importance }}/10
                                </span>
                            </div>
                            <p class="memory-item-content">{{ 'content' in item ? item.content : (item as any).entry?.content }}</p>
                            <span class="memory-item-meta">{{ formatDate(item.createdAt || (item as any).entry?.createdAt) }}</span>
                        </div>
                    </div>
                    <EmptyState v-else text="No memories for this agent" />
                </div>

                <div v-else-if="activeTab === 'tools'" class="tab-panel">
                    <LoadingSpinner v-if="toolsLoading" text="Loading tools..." />
                    <div v-else-if="agentTools.length > 0" class="tools-section">
                        <span class="tools-count">{{ agentTools.length }} tools</span>
                        <div class="tool-list">
                            <div v-for="tool in agentTools" :key="tool.name" class="tool-item">
                                <div class="tool-item-header">
                                    <code class="tool-item-name">{{ tool.name }}</code>
                                    <RiskBadge v-if="tool.riskLevel" :level="tool.riskLevel" />
                                </div>
                                <p class="tool-item-desc">{{ tool.description }}</p>
                                <div class="tool-item-meta">
                                    <span v-if="tool.sandboxEnabled">Sandbox: {{ tool.sandboxType }}</span>
                                    <span>Timeout: {{ tool.timeout }}s</span>
                                </div>
                            </div>
                        </div>
                    </div>
                    <EmptyState v-else text="No tools configured" />
                </div>

                <div v-else-if="activeTab === 'runs'" class="tab-panel">
                    <LoadingSpinner v-if="runsLoading" text="Loading runs..." />
                    <div v-else-if="agentRuns.length > 0" class="run-list">
                        <div
                            v-for="run in agentRuns"
                            :key="run.id"
                            class="list-item run-row"
                            @click="$router.push(`/runs/${run.id}`)"
                        >
                            <div class="run-row-info">
                                <span class="run-row-intent">{{ run.intent }}</span>
                                <span class="run-row-meta">
                                    {{ formatDuration(run.duration) }} · {{ run.toolCallCount }} tool calls
                                </span>
                            </div>
                            <StatusBadge
                                :type="runStatusType(run.status)"
                                :text="runStatusLabel(run.status)"
                            />
                        </div>
                    </div>
                    <EmptyState v-else text="No run history" />
                </div>

                <div v-else-if="activeTab === 'permissions'" class="tab-panel">
                    <LoadingSpinner v-if="permLoading" text="Loading permissions..." />
                    <div v-else-if="agentPermissions" class="permissions-section">
                        <div class="section">
                            <h3 class="section-title">Allowed Hosts</h3>
                            <div class="chip-list">
                                <span v-for="h in agentPermissions.allowedHosts" :key="h" class="chip allowed-chip">{{ h }}</span>
                                <span v-if="!agentPermissions.allowedHosts?.length" class="empty-hint">All allowed</span>
                            </div>
                        </div>
                        <div class="section-separator"></div>
                        <div class="section">
                            <h3 class="section-title">Allowed Workspaces</h3>
                            <div class="chip-list">
                                <span v-for="w in agentPermissions.allowedWorkspaces" :key="w" class="chip allowed-chip">{{ w }}</span>
                                <span v-if="!agentPermissions.allowedWorkspaces?.length" class="empty-hint">All allowed</span>
                            </div>
                        </div>
                        <div class="section-separator"></div>
                        <div class="section">
                            <h3 class="section-title">Capability Permissions</h3>
                            <div class="perm-list">
                                <div v-for="(perm, cap) in agentPermissions.capabilities || {}" :key="cap" class="perm-item">
                                    <span class="perm-cap">{{ cap }}</span>
                                    <span :class="['perm-value', perm]">{{ perm }}</span>
                                </div>
                            </div>
                        </div>
                    </div>
                    <button v-else class="btn-secondary" @click="loadPermissions" :disabled="permLoading">
                        {{ permLoading ? 'Loading...' : 'Load Permissions' }}
                    </button>
                </div>
            </template>
            <EmptyState v-else text="Agent not found" />
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
import type { RunInfo } from '@/types/run'
import type { PromptInfo } from '@/types/agent'

const route = useRoute()
const agentStore = useAgentStore()
const memoryStore = useMemoryStore()
const runStore = useRunStore()
const toast = useToast()

const activeTab = ref('overview')
const tabs = [
    { id: 'overview', label: 'Overview' },
    { id: 'config', label: 'Config' },
    { id: 'prompt', label: 'Prompt' },
    { id: 'memory', label: 'Memory' },
    { id: 'tools', label: 'Tools' },
    { id: 'runs', label: 'Runs' },
    { id: 'permissions', label: 'Permissions' },
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

const prompt = ref<PromptInfo | null>(null)
const promptTemplate = ref('')
const agentMemories = ref<MemoryEntry[]>([])
const agentTools = ref<any[]>([])
const agentRuns = ref<RunInfo[]>([])
const agentPermissions = ref<any>(null)

const typeLabel = computed(() => {
    const labels: Record<string, string> = {
        CHAT: 'Chat', CODE: 'Code', SEARCH: 'Search', DOCUMENT: 'Document',
        PLANNER: 'Planner', EXECUTOR: 'Executor', GENERAL: 'General',
    }
    return labels[agent.value?.agentType || ''] || agent.value?.agentType || 'Unknown'
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
    const labels: Record<string, string> = { pending: 'Pending', running: 'Running', completed: 'Done', failed: 'Failed', cancelled: 'Cancelled' }
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
    try { await agentStore.fetchAgentConfig(agentId.value) } catch { toast.error('Failed to load config') }
    finally { configLoading.value = false }
}

async function loadPrompt() {
    promptLoading.value = true
    try {
        prompt.value = await agentsApi.fetchAgentPrompt(agentId.value)
        promptTemplate.value = prompt.value?.templateText ?? ''
    } catch { toast.error('Failed to load prompt') }
    finally { promptLoading.value = false }
}

async function savePrompt() {
    if (!prompt.value) return
    promptSaving.value = true
    try {
        await agentsApi.updateAgentPrompt(agentId.value, { templateText: promptTemplate.value })
        prompt.value.templateText = promptTemplate.value
        toast.success('Prompt saved')
    } catch { toast.error('Failed to save prompt') }
    finally { promptSaving.value = false }
}

async function loadMemories() {
    memoryLoading.value = true
    try {
        await memoryStore.fetchMemories()
        agentMemories.value = memoryStore.memories.filter((m) => m.agentId === agentId.value)
    } catch { toast.error('Failed to load memories') }
    finally { memoryLoading.value = false }
}

async function loadTools() {
    toolsLoading.value = true
    try {
        const names = await agentsApi.fetchAgentTools(agentId.value)
        agentTools.value = names.map((name: string) => ({
            name, description: '', riskLevel: 'L2', sandboxEnabled: false, sandboxType: 'none', timeout: 30,
        }))
    } catch { toast.error('Failed to load tools') }
    finally { toolsLoading.value = false }
}

async function loadRuns() {
    runsLoading.value = true
    try {
        await runStore.fetchRuns({ agentId: agentId.value })
        agentRuns.value = runStore.runs
    } catch { toast.error('Failed to load runs') }
    finally { runsLoading.value = false }
}

async function loadPermissions() {
    permLoading.value = true
    try { agentPermissions.value = await agentsApi.fetchAgentPermissions(agentId.value) } catch { toast.error('Failed to load permissions') }
    finally { permLoading.value = false }
}

async function loadAgent() {
    if (agentId.value) await agentStore.fetchAgentById(agentId.value)
}

watch(agentId, () => { agentStore.clearCurrentAgent(); activeTab.value = 'overview'; loadAgent() }, { immediate: false })
onMounted(() => { loadAgent() })
</script>

<style scoped>
.page-header {
    display: flex;
    align-items: flex-start;
    gap: 16px;
    margin-bottom: 24px;
    flex-wrap: wrap;
}

.btn-back {
    padding: 6px 14px;
    font-size: 13px;
}

.header-main {
    display: flex;
    align-items: center;
    gap: 12px;
    flex: 1;
}

.header-main h2 {
    font-size: 28px;
    font-weight: 650;
    letter-spacing: -0.3px;
    margin: 0;
}

.agent-id {
    font-size: 12px;
    color: var(--color-text-secondary);
    background: var(--color-surface);
    border: 1px solid var(--color-border);
    padding: 3px 10px;
    border-radius: 6px;
    font-family: monospace;
}

.tabs {
    display: flex;
    gap: 2px;
    margin-bottom: 28px;
    border-bottom: 1px solid var(--color-border);
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
    transition: color 0.15s, border-color 0.15s;
    display: flex;
    align-items: center;
    gap: 6px;
    border-radius: 0;
}

.tab:hover {
    box-shadow: none;
}

.tab.active {
    color: var(--color-accent);
    border-bottom-color: var(--color-accent);
    font-weight: 600;
}

.tab-content {
    min-height: 300px;
}

.tab-panel {
    animation: fadeIn 0.2s ease;
}

@keyframes fadeIn {
    from { opacity: 0; }
    to { opacity: 1; }
}

.section {
    margin-bottom: 0;
}

.section-title {
    font-size: 18px;
    font-weight: 600;
    margin-bottom: 14px;
}

.info-grid {
    display: grid;
    grid-template-columns: 140px 1fr;
    gap: 10px;
    font-size: 13px;
}

.info-item {
    display: contents;
}

.info-label {
    color: var(--color-text-secondary);
    font-weight: 500;
    padding: 6px 0;
}

.info-value {
    padding: 6px 0;
    font-weight: 500;
}

.type-badge {
    padding: 2px 10px;
    border-radius: 20px;
    font-size: 12px;
    background: var(--accent-bg);
    color: var(--color-accent);
    font-weight: 500;
    display: inline-block;
}

.chip-list {
    display: flex;
    flex-wrap: wrap;
    gap: 6px;
}

.chip {
    padding: 4px 12px;
    border-radius: 20px;
    font-size: 12px;
    font-weight: 500;
}

.skill-chip {
    background: rgba(39, 174, 96, 0.08);
    color: var(--color-success);
}

.tool-chip {
    background: rgba(0,0,0,0.04);
    font-family: monospace;
    font-size: 12px;
}

.action-row {
    display: flex;
    gap: 10px;
}

.empty-hint {
    color: var(--color-text-secondary);
    font-size: 13px;
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

.prompt-textarea {
    width: 100%;
    padding: 16px;
    border-radius: var(--radius-md);
    border: 1px solid var(--color-border);
    background: var(--color-surface);
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
    background: var(--accent-bg);
    border-radius: var(--radius-sm);
}

.memory-list {
    display: flex;
    flex-direction: column;
    gap: 8px;
}

.memory-item {
    padding: 14px 18px;
    border: 1px solid var(--color-border);
    border-radius: var(--radius-md);
    background: var(--color-surface);
}

.memory-item-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 8px;
}

.type-badge {
    padding: 3px 10px;
    border-radius: 20px;
    font-size: 12px;
    font-weight: 500;
}

.type-badge.type-memory { background: #e3f2fd; color: #1565c0; }
.type-badge.type-project { background: #e8f5e9; color: #2e7d32; }
.type-badge.type-session { background: #fff3e0; color: #ef6c00; }
.type-badge.type-user { background: var(--accent-bg); color: var(--color-accent); }

.memory-item-importance {
    font-size: 12px;
    color: var(--color-text-secondary);
}

.memory-item-content {
    font-size: 14px;
    line-height: 1.5;
    margin-bottom: 8px;
}

.memory-item-meta {
    font-size: 12px;
    color: var(--color-text-secondary);
}

.tools-count {
    font-size: 13px;
    color: var(--color-text-secondary);
    margin-bottom: 12px;
    display: block;
}

.tool-list {
    display: flex;
    flex-direction: column;
    gap: 8px;
}

.tool-item {
    padding: 14px 18px;
    border: 1px solid var(--color-border);
    border-radius: var(--radius-md);
    background: var(--color-surface);
}

.tool-item-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 8px;
}

.tool-item-name {
    font-weight: 600;
    font-size: 14px;
}

.tool-item-desc {
    font-size: 13px;
    color: var(--color-text-secondary);
    margin-bottom: 8px;
    line-height: 1.4;
}

.tool-item-meta {
    display: flex;
    gap: 14px;
    font-size: 12px;
    color: var(--color-text-secondary);
}

.run-list {
    display: flex;
    flex-direction: column;
}

.run-row {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 12px 16px;
}

.run-row-info {
    display: flex;
    flex-direction: column;
    gap: 4px;
}

.run-row-intent {
    font-weight: 600;
    font-size: 14px;
}

.run-row-meta {
    font-size: 12px;
    color: var(--color-text-secondary);
}

.perm-list {
    display: flex;
    flex-direction: column;
    gap: 8px;
}

.perm-item {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 8px 14px;
    border: 1px solid var(--color-border);
    border-radius: var(--radius-sm);
    background: var(--color-surface);
}

.perm-cap {
    font-weight: 500;
    font-size: 13px;
}

.perm-value {
    font-size: 12px;
    font-weight: 500;
    padding: 2px 10px;
    border-radius: 20px;
}

.perm-value.allow { background: #e8f5e9; color: #2e7d32; }
.perm-value.deny { background: #ffebee; color: #c62828; }

.allowed-chip {
    background: rgba(39, 174, 96, 0.08);
    color: var(--color-success);
}
</style>