import type { ToolRiskLevel } from './tool'

export interface PolicyRule {
    id: string
    capability: string
    riskLevel: ToolRiskLevel
    allowedAgents: string[]
    workspaceRequired: boolean
    confirmationRequired: boolean
    sandboxEnabled: boolean
    sandboxType: 'none' | 'process' | 'docker' | 'workspace'
    timeout: number
    networkEnabled: boolean
    environmentRestrictions: string[]
    auditEnabled: boolean
    updatedAt: string
}

export interface CapabilityPolicyMatrix {
    capability: string
    levels: Record<ToolRiskLevel, PolicyRule | null>
}

export interface PolicyUpdateRequest {
    capability: string
    allowedAgents?: string[]
    workspaceRequired?: boolean
    confirmationRequired?: boolean
    sandboxEnabled?: boolean
    sandboxType?: 'none' | 'process' | 'docker' | 'workspace'
    timeout?: number
    networkEnabled?: boolean
    auditEnabled?: boolean
}