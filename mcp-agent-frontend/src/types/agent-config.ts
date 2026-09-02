import type { ToolRiskLevel } from './tool'

export interface AgentConfig {
    identity: AgentIdentity
    model: AgentModelConfig
    behavior: AgentBehavior
    context: AgentContext
    capabilities: AgentCapabilities
    security: AgentSecurity
}

export interface AgentIdentity {
    name: string
    description: string
    avatar?: string
    version: string
    agentType: string
}

export interface AgentModelConfig {
    provider: string
    modelName: string
    temperature: number
    maxTokens: number
    topP?: number
    frequencyPenalty?: number
    presencePenalty?: number
}

export interface AgentBehavior {
    persona: string
    systemPrompt: string
    promptPolicy?: string
    reflectionEnabled: boolean
    maxIterations: number
}

export interface AgentContext {
    memoryEnabled: boolean
    knowledgeEnabled: boolean
    workspaceEnabled: boolean
    ideContextEnabled: boolean
    contextWindowSize: number
}

export interface AgentCapabilities {
    tools: string[]
    mcpServers: string[]
    hostCapabilities: string[]
}

export interface AgentSecurity {
    riskLevel: ToolRiskLevel
    permissions: string[]
    sandboxEnabled: boolean
    sandboxType: 'none' | 'process' | 'docker' | 'workspace'
    confirmationRequired: boolean
    allowedHosts: string[]
    allowedWorkspaces: string[]
}