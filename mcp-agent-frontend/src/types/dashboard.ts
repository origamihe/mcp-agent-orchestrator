import type { RunInfo } from './run'

export interface DashboardOverview {
    agentCount: number
    activeAgentCount: number
    activeRunCount: number
    toolCount: number
    hostCount: number
    connectedHostCount: number
    uptime: string
    recentRuns: RunInfo[]
    runtimeHealth: RuntimeHealth
}

export interface RuntimeHealth {
    gateway: HealthStatus
    agentEngine: HealthStatus
    mcpHosts: HealthStatus
    memory: HealthStatus
    sandbox: HealthStatus
}

export interface HealthStatus {
    status: 'healthy' | 'degraded' | 'unhealthy'
    message?: string
    detail?: string
}