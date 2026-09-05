import { ref } from 'vue'
import type { ChatMessage } from '@/types/agent'
import http from '@/api/client'

export interface ChatSessionInfo {
    sessionId: string
    userId: string
    createdAt: string
    lastActiveAt: string
    messageCount: number
    firstMessage?: string
}

export function useChatHistory() {
    const messages = ref<ChatMessage[]>([])
    const sessions = ref<ChatSessionInfo[]>([])
    const isLoadingHistory = ref(false)

    function addMessage(role: ChatMessage['role'], content: string) {
        messages.value.push({
            id: crypto.randomUUID(),
            role,
            content,
            timestamp: Date.now(),
        })
    }

    function removeMessage(id: string) {
        const idx = messages.value.findIndex(m => m.id === id)
        if (idx !== -1) {
            messages.value.splice(idx, 1)
        }
    }

    function clearHistory() {
        messages.value = []
    }

    function setMessages(newMessages: ChatMessage[]) {
        messages.value = newMessages
    }

    async function fetchSessions() {
        try {
            const res = await http.get('/mcp/chat-history/sessions') as unknown as ChatSessionInfo[]
            sessions.value = res ?? []
        } catch {
            console.error('获取历史会话列表失败')
        }
    }

    async function loadSession(sessionId: string) {
        isLoadingHistory.value = true
        try {
            const res = await http.get(`/mcp/chat-history/${sessionId}`) as unknown as Array<{
                id: number
                sessionId: string
                role: string
                content: string
                createdAt: string
            }>
            messages.value = (res ?? []).map(m => ({
                id: String(m.id),
                role: m.role as ChatMessage['role'],
                content: m.content,
                timestamp: new Date(m.createdAt).getTime(),
            }))
        } catch {
            console.error('加载历史记录失败')
        } finally {
            isLoadingHistory.value = false
        }
    }

    async function deleteSession(sessionId: string) {
        try {
            await http.delete(`/mcp/chat-history/session/${sessionId}`)
            sessions.value = sessions.value.filter(s => s.sessionId !== sessionId)
        } catch {
            console.error('删除会话失败')
        }
    }

    return {
        messages,
        sessions,
        isLoadingHistory,
        addMessage,
        removeMessage,
        clearHistory,
        setMessages,
        fetchSessions,
        loadSession,
        deleteSession,
    }
}