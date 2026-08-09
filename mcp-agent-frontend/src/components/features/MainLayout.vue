<template>
    <div class="main-layout">
        <Sidebar @navigate="handleNavigate" />
        <div class="main-content">
            <StatusBar
                :isConnected="isConnected"
                :selectedModelId="selectedModelId"
                :models="availableModels"
            />
            <div class="feature-area">
                <!-- Admin Console 面板 -->
                <DashboardPanel
                    v-if="activeFeature === 'dashboard'"
                    :channels="channelStatuses"
                    :workspaces="workspaceCount"
                    :uptime="uptime"
                    @navigate="handleNavigate"
                />
                <WorkspacePanel
                    v-else-if="activeFeature === 'workspaces'"
                    :workspaces="workspaceList"
                />
                <HostMonitorPanel
                    v-else-if="activeFeature === 'hosts'"
                    :channels="channelStatuses"
                    @navigate="handleNavigate"
                    @refresh="fetchChannelStatuses"
                />
                <SkillsPanel
                    v-else-if="activeFeature === 'skills'"
                    @navigate="handleNavigate"
                />
                <AgentPanel
                    v-else-if="activeFeature === 'agents'"
                    :agents="agentCards"
                    @navigate="handleNavigate"
                    @test-agent="handleTestAgent"
                    @run-task="handleRunTask"
                    @run-pipeline="handleRunPipeline"
                    @run-parallel="handleRunParallel"
                    @run-delegate="handleRunDelegate"
                />
                <SettingsPanel
                    v-else-if="activeFeature === 'settings'"
                    :models="availableModels"
                />

                <!-- 调试对话（保留） -->
                <ChatPanel
                    v-else-if="activeFeature === 'chat' || activeFeature === 'web-search'"
                    ref="chatPanelRef"
                    :isConnected="isConnected"
                    :selectedModelId="selectedModelId"
                    :models="availableModels"
                    v-model:selectedRole="selectedRole"
                    @send-message="handleSendMessage"
                    @update:selectedModelId="selectedModelId = $event"
                />
                <PptGenerator
                    v-else-if="activeFeature === 'ppt-generator'"
                    ref="pptPanelRef"
                    :isConnected="isConnected"
                    :selectedModelId="selectedModelId"
                    @send-message="handleSendMessage"
                />
                <DocxGenerator
                    v-else-if="activeFeature === 'docx-generator'"
                    ref="docxPanelRef"
                    :isConnected="isConnected"
                    :selectedModelId="selectedModelId"
                    @send-message="handleSendMessage"
                />
                <PromptManager v-else-if="activeFeature === 'prompt-manager'" />
                <QqBotPanel
                    v-else-if="activeFeature === 'qq-bot'"
                    ref="qqBotPanelRef"
                    :isConnected="isConnected"
                    :selectedModelId="selectedModelId"
                    @send-message="handleSendMessage"
                />
            </div>
        </div>
    </div>
</template>

<script setup lang="ts">
import { ref, watch, onMounted } from 'vue'
import type { AgentFeature, AgentCard, ChannelStatus, WorkspaceInfo } from '@/types/agent.ts'
import type { LlmModelInfo } from '@/types/llm.ts'
import Sidebar from '@/components/common/Sidebar.vue'
import StatusBar from '@/components/common/StatusBar.vue'
import DashboardPanel from '@/components/features/DashboardPanel.vue'
import WorkspacePanel from '@/components/features/WorkspacePanel.vue'
import HostMonitorPanel from '@/components/features/HostMonitorPanel.vue'
import SkillsPanel from '@/components/features/SkillsPanel.vue'
import AgentPanel from '@/components/features/AgentPanel.vue'
import SettingsPanel from '@/components/features/SettingsPanel.vue'
import ChatPanel from '@/components/features/ChatPanel.vue'
import PptGenerator from '@/components/features/PptGenerator.vue'
import DocxGenerator from '@/components/features/DocxGenerator.vue'
import QqBotPanel from '@/components/features/QqBotPanel.vue'
import PromptManager from '@/components/features/PromptManager.vue'
import { useWebSocket } from '@/composables/useWebSocket.ts'
import http from '@/utils/request.ts'

const activeFeature = ref<AgentFeature>('dashboard')
const selectedModelId = ref('')
const selectedRole = ref('')
const availableModels = ref<LlmModelInfo[]>([])

const chatPanelRef = ref<any>(null)
const pptPanelRef = ref<any>(null)
const docxPanelRef = ref<any>(null)
const qqBotPanelRef = ref<any>(null)

const channelStatuses = ref<ChannelStatus[]>([])
const workspaceList = ref<WorkspaceInfo[]>([])
const workspaceCount = ref(0)
const agentCards = ref<AgentCard[]>([])
const uptime = ref('--')

const wsUrl = `${location.protocol === 'https:' ? 'wss:' : 'ws:'}//${location.host}/ws/mcp`
const { isConnected, connect, send, lastMessage } = useWebSocket(wsUrl)

watch(lastMessage, (msg) => {
    if (!msg) return
    switch (activeFeature.value) {
        case 'chat':
        case 'web-search':
            chatPanelRef.value?.addMessage('assistant', msg)
            chatPanelRef.value?.markDone()
            break
        case 'ppt-generator':
            pptPanelRef.value?.addResult(msg)
            break
        case 'docx-generator':
            docxPanelRef.value?.addResult(msg)
            break
        case 'prompt-manager':
        case 'dashboard':
        case 'workspaces':
        case 'hosts':
        case 'skills':
        case 'agents':
        case 'settings':
            break
        case 'qq-bot':
            if (msg && msg.startsWith('/mcp/download/')) {
                qqBotPanelRef.value?.addResult(msg)
            } else {
                qqBotPanelRef.value?.addMessage('assistant', msg)
            }
            break
    }
})

function handleNavigate(feature: AgentFeature) {
    activeFeature.value = feature
}

function handleSendMessage(payload: any) {
    send(payload)
}

async function fetchModels() {
    try {
        const res = (await http.get('/mcp/configs')) as unknown as LlmModelInfo[]
        availableModels.value = res ?? []
    } catch {
        console.error('获取模型列表失败')
    }
}

async function fetchChannelStatuses() {
    try {
        const res = await http.get('/channel/status')
        channelStatuses.value = (res as any) || []
    } catch {
        console.error('获取渠道状态失败')
    }
}

async function fetchWorkspaces() {
    try {
        const res = await http.get('/mcp/workspaces')
        workspaceList.value = (res as any) || []
        workspaceCount.value = workspaceList.value.length
    } catch {
        workspaceCount.value = 0
    }
}

async function fetchAgents() {
    try {
        const res = await http.get('/api/agents')
        agentCards.value = (res as any) || []
    } catch {
        agentCards.value = []
    }
}

function handleTestAgent(agentId: string) {
    console.log('[MainLayout] Test agent:', agentId)
}

function handleRunTask(agentId: string) {
    console.log('[MainLayout] Run task on agent:', agentId)
}

function handleRunPipeline() {
    console.log('[MainLayout] Run pipeline workflow')
}

function handleRunParallel() {
    console.log('[MainLayout] Run parallel workflow')
}

function handleRunDelegate() {
    console.log('[MainLayout] Run delegate workflow')
}

onMounted(() => {
    fetchModels()
    fetchChannelStatuses()
    fetchWorkspaces()
    fetchAgents()
    connect()
})
</script>

<style scoped>
.main-layout {
    display: flex;
    height: 100vh;
    overflow: hidden;
}

.main-content {
    flex: 1;
    display: flex;
    flex-direction: column;
    overflow: hidden;
}

.feature-area {
    flex: 1;
    overflow: hidden;
}
</style>