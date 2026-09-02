import http from './client'
import type { RunInfo, RunDetail } from '@/types/run'

export async function fetchRuns(params?: { agentId?: string; status?: string; limit?: number; offset?: number }): Promise<RunInfo[]> {
    return http.get('/api/runs', { params }) as unknown as RunInfo[]
}

export async function fetchRunById(id: string): Promise<RunDetail> {
    return http.get(`/api/runs/${id}`) as unknown as RunDetail
}

export async function fetchRunTrace(id: string): Promise<unknown> {
    return http.get(`/api/runs/${id}/trace`) as unknown as unknown
}