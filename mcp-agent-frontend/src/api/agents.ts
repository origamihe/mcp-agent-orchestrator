import http from './client'
import type { AgentCard } from '@/types/agent'
import type { AgentConfig } from '@/types/agent-config'

export async function fetchAgents(): Promise<AgentCard[]> {
    return http.get('/api/agents') as unknown as AgentCard[]
}

export async function fetchAgentById(id: string): Promise<AgentCard> {
    return http.get(`/api/agents/${id}`) as unknown as AgentCard
}

export async function fetchAgentConfig(id: string): Promise<AgentConfig> {
    return http.get(`/api/agents/${id}/config`) as unknown as AgentConfig
}

export async function updateAgentConfig(id: string, config: Partial<AgentConfig>): Promise<AgentConfig> {
    return http.put(`/api/agents/${id}/config`, config) as unknown as AgentConfig
}

export async function fetchAgentPrompt(id: string): Promise<{ name: string; templateText: string }> {
    return http.get(`/api/agents/${id}/prompt`) as unknown as { name: string; templateText: string }
}

export async function updateAgentPrompt(id: string, prompt: { templateText: string }): Promise<void> {
    return http.put(`/api/agents/${id}/prompt`, prompt) as unknown as void
}

export async function fetchAgentTools(id: string): Promise<string[]> {
    return http.get(`/api/agents/${id}/tools`) as unknown as string[]
}

export async function fetchAgentRuns(id: string): Promise<unknown[]> {
    return http.get(`/api/agents/${id}/runs`) as unknown as unknown[]
}

export async function fetchAgentPermissions(id: string): Promise<unknown> {
    return http.get(`/api/agents/${id}/permissions`) as unknown as unknown
}

export async function updateAgentPermissions(id: string, permissions: unknown): Promise<void> {
    return http.put(`/api/agents/${id}/permissions`, permissions) as unknown as void
}