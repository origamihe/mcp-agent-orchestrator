import { ref, computed } from 'vue'
import { defineStore } from 'pinia'
import type { ToolInfo } from '@/types/tool'
import * as toolsApi from '@/api/tools'

export const useToolStore = defineStore('tool', () => {
    const tools = ref<ToolInfo[]>([])
    const currentTool = ref<ToolInfo | null>(null)
    const isLoading = ref(false)
    const error = ref<string | null>(null)

    const toolCount = computed(() => tools.value.length)

    const riskDistribution = computed(() => {
        const dist: Record<string, number> = { L0: 0, L1: 0, L2: 0, L3: 0, L4: 0, L5: 0 }
        for (const t of tools.value) {
            const level = t.riskLevel || 'L0'
            dist[level] = (dist[level] || 0) + 1
        }
        return dist
    })

    async function fetchTools() {
        isLoading.value = true
        error.value = null
        try {
            tools.value = await toolsApi.fetchTools()
        } catch (e: any) {
            error.value = e.message || '获取工具列表失败'
        } finally {
            isLoading.value = false
        }
    }

    async function fetchToolByName(name: string) {
        try {
            currentTool.value = await toolsApi.fetchToolByName(name)
        } catch (e: any) {
            error.value = e.message || '获取工具详情失败'
        }
    }

    function clearCurrentTool() {
        currentTool.value = null
    }

    return {
        tools,
        currentTool,
        isLoading,
        error,
        toolCount,
        riskDistribution,
        fetchTools,
        fetchToolByName,
        clearCurrentTool,
    }
})