export type AgentFeature =
    | 'chat'           // 基础聊天
    | 'web-search'     // 联网搜索
    | 'expert-mode'    // 专家模式
    | 'ppt-generator'  // 制作PPT
    | 'docx-generator' // 制作Word文档
    | 'prompt-manager' // 角色管理

export interface AgentFeatureMeta {
    id: AgentFeature
    name: string
    icon?: string
    description: string
    enabled: boolean
}

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