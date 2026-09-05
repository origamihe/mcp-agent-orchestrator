export type AgentFeature =
    | 'dashboard'
    | 'agents'
    | 'chat'
    | 'web-search'
    | 'settings'

export interface ChatMessage {
    id: string
    role: 'user' | 'assistant' | 'system'
    content: string
    timestamp: number
}

export interface PromptInfo {
    name: string
    type: string
    templateText: string
    description: string
    version: number
    updatedAt: string
}

export interface WebSocketMessage {
    message: string
    modelConfigId?: string | null
    systemPromptName?: string
    featureId?: AgentFeature
    parameters?: Record<string, any>
}

export interface AgentCard {
    agentId: string
    agentName: string
    description: string
    agentType: string
    skills: string[]
    toolNames: string[]
    inputSchema?: Record<string, string>
    outputSchema?: Record<string, string>
    supportsStreaming?: boolean
    maxConcurrentTasks?: number
    version?: string
    status?: 'online' | 'idle' | 'offline' | 'active'
    model?: string
    modelId?: string
    modelName?: string
    promptName?: string
    host?: string
    sessionCount?: number
}