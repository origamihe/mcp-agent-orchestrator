export type RunStatus = 'pending' | 'running' | 'completed' | 'failed' | 'cancelled'

export interface RunInfo {
    id: string
    agentId: string
    agentName: string
    sessionId: string
    intent: string
    status: RunStatus
    duration: number
    toolCallCount: number
    tokenUsage: TokenUsage
    createdAt: string
    completedAt?: string
}

export interface RunDetail extends RunInfo {
    trace: TraceSpan[]
    messages: RunMessage[]
    policyChecks: PolicyCheckResult[]
}

export interface TraceSpan {
    id: string
    parentId?: string
    operation: string
    startTime: string
    endTime: string
    duration: number
    status: 'success' | 'error' | 'warning'
    metadata?: Record<string, unknown>
    children?: TraceSpan[]
}

export interface RunMessage {
    role: 'user' | 'assistant' | 'system' | 'tool'
    content: string
    timestamp: string
    toolCallId?: string
}

export interface PolicyCheckResult {
    capability: string
    passed: boolean
    reason?: string
    timestamp: string
}

export interface TokenUsage {
    promptTokens: number
    completionTokens: number
    totalTokens: number
}