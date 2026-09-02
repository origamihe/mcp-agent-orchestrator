<template>
    <div class="page">
        <ChatPanel
            ref="chatPanelRef"
            :isConnected="isConnected"
            :selectedModelId="selectedModelId"
            :models="availableModels"
            v-model:selectedRole="selectedRole"
            @send-message="handleSendMessage"
            @update:selectedModelId="handleModelChange"
        />
    </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import ChatPanel from '@/components/features/ChatPanel.vue'
import { useWebSocket } from '@/composables/useWebSocket'
import type { LlmModelInfo } from '@/types/llm'
import http from '@/utils/request'

const chatPanelRef = ref<any>(null)
const selectedModelId = ref('')
const selectedRole = ref('')
const availableModels = ref<LlmModelInfo[]>([])

const wsUrl = `${location.protocol === 'https:' ? 'wss:' : 'ws:'}//${location.host}/ws/mcp`
const { isConnected, connect, send } = useWebSocket(wsUrl)

function handleSendMessage(payload: any) {
    send(payload)
}

function handleModelChange(modelId: string) {
    selectedModelId.value = modelId
}

onMounted(async () => {
    try {
        const res = await http.get('/mcp/configs')
        availableModels.value = (res as any) || []
        if (availableModels.value.length > 0) {
            selectedModelId.value = availableModels.value[0].configId
        }
    } catch { /* ignore */ }
    connect()
})
</script>

<style scoped>
.page {
    height: 100%;
    overflow: hidden;
}
</style>