export interface SessionInfo {
    sessionId: string
    agentId: string
    agentName: string
    userId: string
    status: 'active' | 'idle' | 'closed'
    messageCount: number
    runCount: number
    createdAt: string
    lastActiveAt: string
    firstMessage?: string
}