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
                <ChatPanel
                    v-if="activeFeature === 'chat' || activeFeature === 'web-search' || activeFeature === 'expert-mode'"
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
import type { AgentFeature } from '@/types/agent.ts'
import type { LlmModelInfo } from '@/types/llm.ts'
import Sidebar from '@/components/common/Sidebar.vue'
import StatusBar from '@/components/common/StatusBar.vue'
import ChatPanel from '@/components/features/ChatPanel.vue'
import PptGenerator from '@/components/features/PptGenerator.vue'
import DocxGenerator from '@/components/features/DocxGenerator.vue'
import QqBotPanel from '@/components/features/QqBotPanel.vue'
import PromptManager from '@/components/features/PromptManager.vue'
import { useWebSocket } from '@/composables/useWebSocket.ts'
import http from '@/utils/request.ts'

const activeFeature = ref<AgentFeature>('chat')
const selectedModelId = ref('')
const selectedRole = ref('')
const availableModels = ref<LlmModelInfo[]>([])

const chatPanelRef = ref<any>(null)
const pptPanelRef = ref<any>(null)
const docxPanelRef = ref<any>(null)
const qqBotPanelRef = ref<any>(null)

const wsUrl = `${location.protocol === 'https:' ? 'wss:' : 'ws:'}//${location.host}/ws/mcp`
const { isConnected, connect, send, lastMessage } = useWebSocket(wsUrl)

watch(lastMessage, (msg) => {
    if (!msg) return
    switch (activeFeature.value) {
        case 'chat':
        case 'web-search':
        case 'expert-mode':
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

onMounted(() => {
    fetchModels()
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