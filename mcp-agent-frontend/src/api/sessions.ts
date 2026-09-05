import http from './client'
import type { SessionInfo } from '@/types/session'

export async function fetchSessions(params?: { userId?: string; agentId?: string }): Promise<SessionInfo[]> {
    return http.get('/mcp/chat-history/sessions', { params }) as unknown as SessionInfo[]
}