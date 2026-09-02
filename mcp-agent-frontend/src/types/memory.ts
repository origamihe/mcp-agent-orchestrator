export type MemoryType =
    | 'long_term'
    | 'short_term'
    | 'project'
    | 'user'
    | 'session'
    | 'semantic'
    | 'episodic'

export interface MemoryEntry {
    id: string
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
    type: MemoryType
    content: string
    importance?: number
    agentId?: string
    sessionId?: string
    projectId?: string
    metadata?: Record<string, unknown>
}

export interface MemorySearchQuery {
    query: string
    type?: MemoryType
    agentId?: string
    limit?: number
    offset?: number
}

export interface MemorySearchResult {
    entry: MemoryEntry
    score: number
}