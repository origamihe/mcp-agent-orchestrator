export type LogLevel = 'debug' | 'info' | 'warn' | 'error' | 'audit'

export interface LogEntry {
    id: number
    level: LogLevel
    module: string
    message: string
    agentId?: string
    sessionId?: string
    runId?: string
    metadata?: Record<string, unknown>
    timestamp: string
}

export interface LogQuery {
    level?: LogLevel
    module?: string
    agentId?: string
    sessionId?: string
    runId?: string
    eventType?: string
    startTime?: string
    endTime?: string
    search?: string
    limit?: number
    offset?: number
}

export interface LogPageResponse {
    items: LogEntry[]
    totalCount: number
    page: number
    pageSize: number
    hasMore: boolean
}

export interface LogStatistics {
    totalCount: number
    levelCounts: Record<string, number>
    moduleCounts: Record<string, number>
    recentErrors: LogEntry[]
}

export interface FileLogEntry {
    timestamp: string
    level: string
    thread: string
    logger: string
    message: string
}

export interface FileLogPageResponse {
    items: FileLogEntry[]
    totalCount: number
    module: string
    fileName: string
    error?: string
}

export interface LogStreamMessage {
    type: 'logBatch'
    entries: FileLogEntry[]
}