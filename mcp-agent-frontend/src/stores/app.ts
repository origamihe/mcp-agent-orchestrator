import { ref, computed } from 'vue'
import { defineStore } from 'pinia'
import type { LlmModelInfo } from '@/types/llm'
import type { AgentFeature } from '@/types/agent'

export const useAppStore = defineStore('app', () => {
    const isConnected = ref(false)
    const connectionStatus = ref('未连接')
    const selectedModelId = ref('')
    const activeFeature = ref<AgentFeature>('dashboard')

    const availableModels = ref<LlmModelInfo[]>([])

    const statusClass = computed(() => {
        if (isConnected.value) return 'status-connected'
        return 'status-disconnected'
    })

    const currentModel = computed(() => {
        if (!selectedModelId.value) return null
        return availableModels.value.find(
            (m) => m.configId === selectedModelId.value,
        ) ?? null
    })

    function setConnected(val: boolean) {
        isConnected.value = val
        connectionStatus.value = val ? '已连接' : '已断开'
    }

    function setModels(models: LlmModelInfo[]) {
        availableModels.value = models
    }

    function selectModel(configId: string) {
        selectedModelId.value = configId
    }

    function setActiveFeature(feature: AgentFeature) {
        activeFeature.value = feature
    }

    return {
        isConnected,
        connectionStatus,
        selectedModelId,
        activeFeature,
        availableModels,
        statusClass,
        currentModel,
        setConnected,
        setModels,
        selectModel,
        setActiveFeature,
    }
})