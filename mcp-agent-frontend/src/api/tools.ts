import http from './client'
import type { ToolInfo } from '@/types/tool'

export async function fetchTools(): Promise<ToolInfo[]> {
    return http.get('/api/tools') as unknown as ToolInfo[]
}

export async function fetchToolByName(name: string): Promise<ToolInfo> {
    return http.get(`/api/tools/${name}`) as unknown as ToolInfo
}

export async function fetchToolRisk(name: string): Promise<{ riskLevel: string; assessment: string }> {
    return http.get(`/api/tools/${name}/risk`) as unknown as { riskLevel: string; assessment: string }
}