import http from './client'
import type { LogEntry, LogQuery } from '@/types/log'

export async function fetchLogs(query?: LogQuery): Promise<LogEntry[]> {
    return http.get('/api/logs', { params: query }) as unknown as LogEntry[]
}