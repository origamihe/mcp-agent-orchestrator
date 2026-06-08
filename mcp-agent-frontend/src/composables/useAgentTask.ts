import { ref } from 'vue'
import type { AgentFeature, WebSocketMessage } from '@/types/agent'

export function useAgentTask(
    sendWsMessage: (payload: WebSocketMessage) => void,
) {
    const currentFeature = ref<AgentFeature>('chat')
    const isProcessing = ref(false)

    function executeTask(
        feature: AgentFeature,
        message: string,
        modelConfigId?: string,
        parameters?: Record<string, unknown>,
        systemPromptName?: string,
    ) {
        currentFeature.value = feature
        isProcessing.value = true

        const payload: WebSocketMessage = {
            message,
            featureId: feature,
            parameters,
        }
        if (modelConfigId) {
            payload.modelConfigId = modelConfigId
        }
        if (systemPromptName) {
            payload.systemPromptName = systemPromptName
        }

        sendWsMessage(payload)
    }

    function markDone() {
        isProcessing.value = false
    }

    return {
        currentFeature,
        isProcessing,
        executeTask,
        markDone,
    }
}