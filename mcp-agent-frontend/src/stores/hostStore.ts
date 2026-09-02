import { ref } from 'vue'
import { defineStore } from 'pinia'
import type { HostInfo } from '@/types/host'
import * as hostsApi from '@/api/hosts'

export const useHostStore = defineStore('host', () => {
    const hosts = ref<HostInfo[]>([])
    const currentHost = ref<HostInfo | null>(null)
    const isLoading = ref(false)
    const error = ref<string | null>(null)

    async function fetchHosts() {
        isLoading.value = true
        error.value = null
        try {
            hosts.value = await hostsApi.fetchHosts()
        } catch (e: any) {
            error.value = e.message || '获取宿主列表失败'
        } finally {
            isLoading.value = false
        }
    }

    async function fetchHostById(id: string) {
        try {
            currentHost.value = await hostsApi.fetchHostById(id)
        } catch (e: any) {
            error.value = e.message || '获取宿主详情失败'
        }
    }

    return {
        hosts,
        currentHost,
        isLoading,
        error,
        fetchHosts,
        fetchHostById,
    }
})