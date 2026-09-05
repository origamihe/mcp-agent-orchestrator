import http from './client'
import type { ToolInfo } from '@/types/tool'

export async function fetchTools(): Promise<ToolInfo[]> {
    return http.get('/api/tools') as unknown as ToolInfo[]
}

export async function fetchToolByName(name: string): Promise<ToolInfo> {
    return http.get(`/api/tools/${name}`) as unknown as ToolInfo
}