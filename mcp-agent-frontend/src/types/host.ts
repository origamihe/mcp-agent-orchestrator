export interface HostInfo {
    id: string
    channelType: 'qq' | 'desktop' | 'ide' | 'telegram' | 'discord'
    name: string
    enabled: boolean
    connected: boolean
    lastActiveAt?: string
    capabilities: HostCapability[]
    projects: HostProject[]
    status: Record<string, unknown>
}

export interface HostCapability {
    name: string
    description: string
    enabled: boolean
    riskLevel: 'L0' | 'L1' | 'L2' | 'L3' | 'L4' | 'L5'
}

export interface HostProject {
    projectId: string
    name: string
    path: string
    activeWorkspace: boolean
}