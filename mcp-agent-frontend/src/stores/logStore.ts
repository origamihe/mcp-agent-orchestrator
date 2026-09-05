import { ref } from 'vue'
import { defineStore } from 'pinia'
import type { LogEntry, LogQuery, LogStatistics } from '@/types/log'
import * as logsApi from '@/api/logs'

export const useLogStore = defineStore('log', () => {
    const logs = ref<LogEntry[]>([])
    const isLoading = ref(false)
    const error = ref<string | null>(null)
    const totalCount = ref(0)
    const currentPage = ref(0)
    const pageSize = ref(50)
    const hasMore = ref(false)
    const statistics = ref<LogStatistics | null>(null)

    async function fetchLogs(query?: LogQuery) {
        isLoading.value = true
        error.value = null
        try {
            const response = await logsApi.fetchLogs(query)
            logs.value = response.items
            totalCount.value = response.totalCount
            currentPage.value = response.page
            pageSize.value = response.pageSize
            hasMore.value = response.hasMore
        } catch (e: any) {
            error.value = e.message || '获取日志失败'
        } finally {
            isLoading.value = false
        }
    }

    async function fetchStatistics() {
        try {
            statistics.value = await logsApi.fetchLogStatistics()
        } catch (e: any) {
            error.value = e.message || '获取统计信息失败'
        }
    }

    async function fetchLogById(id: number): Promise<LogEntry | null> {
        try {
            return await logsApi.fetchLogById(id)
        } catch (e: any) {
            error.value = e.message || '获取日志详情失败'
            return null
        }
    }

    return {
        logs,
        isLoading,
        error,
        totalCount,
        currentPage,
        pageSize,
        hasMore,
        statistics,
        fetchLogs,
        fetchStatistics,
        fetchLogById,
    }
})