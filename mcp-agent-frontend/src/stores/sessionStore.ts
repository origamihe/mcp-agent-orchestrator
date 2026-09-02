import { ref } from 'vue'
import { defineStore } from 'pinia'
import type { SessionInfo } from '@/types/session'
import * as sessionsApi from '@/api/sessions'

export const useSessionStore = defineStore('session', () => {
    const sessions = ref<SessionInfo[]>([])
    const isLoading = ref(false)
    const error = ref<string | null>(null)

    async function fetchSessions(params?: { agentId?: string; status?: string }) {
        isLoading.value = true
        error.value = null
        try {
            sessions.value = await sessionsApi.fetchSessions(params)
        } catch (e: any) {
            error.value = e.message || '获取会话列表失败'
        } finally {
            isLoading.value = false
        }
    }

    return {
        sessions,
        isLoading,
        error,
        fetchSessions,
    }
})