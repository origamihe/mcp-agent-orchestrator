export type LogLevel = 'debug' | 'info' | 'warn' | 'error' | 'audit'

export interface LogEntry {
    id: string
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
    startTime?: string
    endTime?: string
    search?: string
    limit?: number
    offset?: number
}