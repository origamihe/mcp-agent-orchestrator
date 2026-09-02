import http from './client'
import type { DashboardOverview } from '@/types/dashboard'
import type { LlmModelInfo } from '@/types/llm'

export async function fetchDashboardOverview(): Promise<DashboardOverview> {
    return http.get('/api/dashboard/overview') as unknown as DashboardOverview
}

export async function fetchRuntimeHealth(): Promise<unknown> {
    return http.get('/api/dashboard/health') as unknown as unknown
}

export async function fetchModels(): Promise<LlmModelInfo[]> {
    return http.get('/mcp/configs') as unknown as LlmModelInfo[]
}

export async function fetchChannelStatuses(): Promise<unknown> {
    return http.get('/channel/status') as unknown as unknown
}

export async function fetchWorkspaces(): Promise<unknown> {
    return http.get('/mcp/workspaces') as unknown as unknown
}