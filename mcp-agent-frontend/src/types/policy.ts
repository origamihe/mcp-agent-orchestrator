import type { ToolRiskLevel } from './tool'

export interface PolicyRule {
    capability: string
    riskLevel: ToolRiskLevel
    sandboxType: 'NONE' | 'WORKSPACE_ISOLATION' | 'PROCESS_SANDBOX' | 'BLOCKED'
    sandboxEnabled: boolean
    blocked: boolean
}

export interface PolicyUpdateRequest {
    riskLevel: ToolRiskLevel
}