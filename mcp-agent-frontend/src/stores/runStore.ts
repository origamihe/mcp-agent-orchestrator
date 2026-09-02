import { ref } from 'vue'
import { defineStore } from 'pinia'
import type { RunInfo, RunDetail } from '@/types/run'
import * as runsApi from '@/api/runs'

export const useRunStore = defineStore('run', () => {
    const runs = ref<RunInfo[]>([])
    const currentRun = ref<RunDetail | null>(null)
    const isLoading = ref(false)
    const error = ref<string | null>(null)

    async function fetchRuns(params?: { agentId?: string; status?: string; limit?: number; offset?: number }) {
        isLoading.value = true
        error.value = null
        try {
            runs.value = await runsApi.fetchRuns(params)
        } catch (e: any) {
            error.value = e.message || '获取 Runs 列表失败'
        } finally {
            isLoading.value = false
        }
    }

    async function fetchRunById(id: string) {
        isLoading.value = true
        error.value = null
        try {
            currentRun.value = await runsApi.fetchRunById(id)
        } catch (e: any) {
            error.value = e.message || '获取 Run 详情失败'
        } finally {
            isLoading.value = false
        }
    }

    async function fetchRunTrace(id: string) {
        try {
            return await runsApi.fetchRunTrace(id)
        } catch (e: any) {
            error.value = e.message || '获取 Trace 失败'
        }
    }

    function clearCurrentRun() {
        currentRun.value = null
    }

    return {
        runs,
        currentRun,
        isLoading,
        error,
        fetchRuns,
        fetchRunById,
        fetchRunTrace,
        clearCurrentRun,
    }
})