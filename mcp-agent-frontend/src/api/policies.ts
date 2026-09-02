import http from './client'
import type { PolicyRule, PolicyUpdateRequest } from '@/types/policy'

export async function fetchPolicies(): Promise<PolicyRule[]> {
    return http.get('/api/policies') as unknown as PolicyRule[]
}

export async function fetchPolicyByCapability(capability: string): Promise<PolicyRule> {
    return http.get(`/api/policies/${capability}`) as unknown as PolicyRule
}

export async function updatePolicy(capability: string, data: PolicyUpdateRequest): Promise<PolicyRule> {
    return http.put(`/api/policies/${capability}`, data) as unknown as PolicyRule
}