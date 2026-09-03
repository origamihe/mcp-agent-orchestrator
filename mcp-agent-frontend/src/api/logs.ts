import http from './client'
import type { LogEntry, LogPageResponse, LogStatistics, LogQuery, FileLogPageResponse, FileLogEntry } from '@/types/log'

export async function fetchLogs(query?: LogQuery): Promise<LogPageResponse> {
    return http.get('/api/logs', { params: query }) as unknown as LogPageResponse
}

export async function fetchLogById(id: string): Promise<LogEntry> {
    return http.get(`/api/logs/${id}`) as unknown as LogEntry
}

export async function fetchLogStatistics(): Promise<LogStatistics> {
    return http.get('/api/logs/statistics') as unknown as LogStatistics
}

export async function fetchLogModules(): Promise<string[]> {
    return http.get('/api/logs/files/modules') as unknown as string[]
}

export interface FileLogQuery {
    level?: string
    startTime?: string
    endTime?: string
    search?: string
    limit?: number
    offset?: number
}

export async function fetchFileLogs(module: string, query?: FileLogQuery): Promise<FileLogPageResponse> {
    return http.get(`/api/logs/files/${module}`, { params: query }) as unknown as FileLogPageResponse
}