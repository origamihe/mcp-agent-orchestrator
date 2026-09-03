export type AgentFeature =
    | 'dashboard'       // 仪表盘
    | 'workspaces'       // 工作空间管理
    | 'hosts'            // 宿主管理
    | 'agents'           // Agent 管理
    | 'chat'             // 基础聊天（保留，降级为调试工具）
    | 'web-search'       // 联网搜索
    | 'skills'           // 技能管理（PPT/Word/角色）
    | 'prompt-manager'   // 角色管理
    | 'qq-bot'           // QQ机器人
    | 'settings'         // 系统配置

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

/** 工作空间 */
export interface WorkspaceInfo {
    workspaceId: string
    name: string
    projectPath?: string
    lastActiveFile?: string
    lastActiveLine?: number
    lastActiveAt?: string
    activeTasks?: TaskInfo[]
    todos?: TodoInfo[]
    gitState?: GitStateInfo
    terminalState?: TerminalStateInfo
}

export interface TaskInfo {
    id: string
    title: string
    status: 'TODO' | 'IN_PROGRESS' | 'DONE' | 'BLOCKED'
    priority: 'HIGH' | 'MEDIUM' | 'LOW'
    description?: string
    createdAt?: string
}

export interface TodoInfo {
    id: string
    content: string
    completed: boolean
}

export interface GitStateInfo {
    branch: string
    status: string
    diff?: string
}

export interface TerminalStateInfo {
    cwd?: string
    lastCommand?: string
    lastOutput?: string
}

/** 宿主/渠道 */
export interface HostInfo {
    channelType: string
    enabled: boolean
    connected: boolean
    lastActiveAt?: string
    status: Record<string, any>
}

/** 渠道状态 */
export interface ChannelStatus {
    channel: string
    enabled: boolean
    connected?: boolean
    [key: string]: any
}

/** 系统概览 */
export interface SystemOverview {
    channels: ChannelStatus[]
    workspaces: number
    activeSessions: number
    uptime: string
}

/** Agent 能力卡片（A2A 协议） */
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
    host?: string
    sessionCount?: number
}

/** Agent 匹配结果 */
export interface AgentMatch {
    agentId: string
    card: AgentCard
    matchedSkills: string[]
    score: number
    exactMatch: boolean
}

/** A2A 任务请求 */
export interface A2aTaskRequest {
    taskId: string
    fromAgentId: string
    toAgentId: string
    task: string
    context?: string
    params?: Record<string, any>
    timestamp: number
}

/** A2A 任务响应 */
export interface A2aTaskResponse {
    taskId: string
    fromAgentId: string
    toAgentId: string
    result: string
    success: boolean
    errorMessage?: string
    timestamp: number
}