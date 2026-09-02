import { ref, computed } from 'vue'
import { defineStore } from 'pinia'
import type { AgentCard } from '@/types/agent'
import type { AgentConfig } from '@/types/agent-config'
import * as agentsApi from '@/api/agents'

export const useAgentStore = defineStore('agent', () => {
    const agents = ref<AgentCard[]>([])
    const currentAgent = ref<AgentCard | null>(null)
    const currentConfig = ref<AgentConfig | null>(null)
    const isLoading = ref(false)
    const error = ref<string | null>(null)

    const agentCount = computed(() => agents.value.length)

    const typeCounts = computed(() => {
        const counts: Record<string, number> = {}
        for (const a of agents.value) {
            counts[a.agentType] = (counts[a.agentType] || 0) + 1
        }
        return counts
    })

    async function fetchAgents() {
        isLoading.value = true
        error.value = null
        try {
            agents.value = await agentsApi.fetchAgents()
        } catch (e: any) {
            error.value = e.message || '获取 Agent 列表失败'
            console.error('[AgentStore] fetchAgents:', e)
        } finally {
            isLoading.value = false
        }
    }

    async function fetchAgentById(id: string) {
        isLoading.value = true
        error.value = null
        try {
            currentAgent.value = await agentsApi.fetchAgentById(id)
        } catch (e: any) {
            error.value = e.message || '获取 Agent 详情失败'
        } finally {
            isLoading.value = false
        }
    }

    async function fetchAgentConfig(id: string) {
        try {
            currentConfig.value = await agentsApi.fetchAgentConfig(id)
        } catch (e: any) {
            error.value = e.message || '获取 Agent 配置失败'
        }
    }

    async function updateAgentConfig(id: string, config: Partial<AgentConfig>) {
        try {
            const updated = await agentsApi.updateAgentConfig(id, config)
            currentConfig.value = updated
        } catch (e: any) {
            error.value = e.message || '更新 Agent 配置失败'
            throw e
        }
    }

    function clearCurrentAgent() {
        currentAgent.value = null
        currentConfig.value = null
    }

    return {
        agents,
        currentAgent,
        currentConfig,
        isLoading,
        error,
        agentCount,
        typeCounts,
        fetchAgents,
        fetchAgentById,
        fetchAgentConfig,
        updateAgentConfig,
        clearCurrentAgent,
    }
})