import { ref } from 'vue'
import { defineStore } from 'pinia'
import type { DashboardOverview } from '@/types/dashboard'
import * as dashboardApi from '@/api/dashboard'

export const useDashboardStore = defineStore('dashboard', () => {
    const overview = ref<DashboardOverview | null>(null)
    const isLoading = ref(false)
    const error = ref<string | null>(null)

    async function fetchOverview() {
        isLoading.value = true
        error.value = null
        try {
            overview.value = await dashboardApi.fetchDashboardOverview()
        } catch (e: any) {
            error.value = e.message || '获取仪表盘数据失败'
        } finally {
            isLoading.value = false
        }
    }

    async function fetchHealth() {
        try {
            return await dashboardApi.fetchRuntimeHealth()
        } catch (e: any) {
            error.value = e.message || '获取健康状态失败'
        }
    }

    return {
        overview,
        isLoading,
        error,
        fetchOverview,
        fetchHealth,
    }
})