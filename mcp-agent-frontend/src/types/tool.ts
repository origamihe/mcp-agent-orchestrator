export type ToolRiskLevel = 'L0' | 'L1' | 'L2' | 'L3' | 'L4' | 'L5'

export interface ToolInfo {
    name: string
    description: string
    riskLevel?: ToolRiskLevel
    status: 'enabled' | 'disabled' | 'confirmation_required' | 'restricted'
    authorization: ToolAuthorization
    sandbox: SandboxConfig
    timeout: number
    outputLimit: number
    allowedAgents: string[]
    category?: string
    version?: string
}

export interface ToolAuthorization {
    required: boolean
    confirmationMode: 'always' | 'per_session' | 'never'
    approverRoles?: string[]
}

export interface SandboxConfig {
    enabled: boolean
    type: 'none' | 'process' | 'docker' | 'workspace'
    workspaceRequired: boolean
    networkEnabled: boolean
    environmentRestrictions: string[]
}