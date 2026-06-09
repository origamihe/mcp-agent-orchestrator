import { ref } from 'vue'
import type { ChatMessage } from '@/types/agent'

export function useChatHistory() {
    const messages = ref<ChatMessage[]>([])

    function addMessage(role: ChatMessage['role'], content: string) {
        messages.value.push({
            id: crypto.randomUUID(),
            role,
            content,
            timestamp: Date.now(),
        })
    }

    function clearHistory() {
        messages.value = []
    }

    return {
        messages,
        addMessage,
        clearHistory,
    }
}