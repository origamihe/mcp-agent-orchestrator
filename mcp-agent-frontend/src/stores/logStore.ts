import { ref } from 'vue'
import { defineStore } from 'pinia'
import type { LogEntry, LogQuery } from '@/types/log'
import * as logsApi from '@/api/logs'

export const useLogStore = defineStore('log', () => {
    const logs = ref<LogEntry[]>([])
    const isLoading = ref(false)
    const error = ref<string | null>(null)

    async function fetchLogs(query?: LogQuery) {
        isLoading.value = true
        error.value = null
        try {
            logs.value = await logsApi.fetchLogs(query)
        } catch (e: any) {
            error.value = e.message || '获取日志失败'
        } finally {
            isLoading.value = false
        }
    }

    return {
        logs,
        isLoading,
        error,
        fetchLogs,
    }
})