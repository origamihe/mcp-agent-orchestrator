import http from './client'
import type { SessionInfo } from '@/types/session'

export async function fetchSessions(params?: { agentId?: string; status?: string }): Promise<SessionInfo[]> {
    return http.get('/api/sessions', { params }) as unknown as SessionInfo[]
}