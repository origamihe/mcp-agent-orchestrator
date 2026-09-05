export type MemoryType =
    | 'PROFILE'
    | 'IDENTITY'
    | 'PREFERENCE'
    | 'HABIT'
    | 'GOAL'
    | 'PROJECT'
    | 'FACT'
    | 'RELATION'
    | 'SKILL'
    | 'SCHEDULE'
    | 'TEMPORARY'
    | 'EVENT'

export interface MemoryEntry {
    id: number
    type: MemoryType
    content: string
    importance: number
    agentId?: string
    sessionId?: string
    projectId?: string
    metadata?: Record<string, unknown>
    createdAt: string
    updatedAt: string
}

export interface MemoryCreateRequest {
    sessionId?: string
    userId?: string
    content: string
    importance?: number
    metadata?: Record<string, unknown>
}

export interface MemorySearchQuery {
    query: string
    sessionId?: string
    limit?: number
}

export interface MemorySearchResult {
    entry: MemoryEntry
    score: number
}