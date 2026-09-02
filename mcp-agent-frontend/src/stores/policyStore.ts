import { ref } from 'vue'
import { defineStore } from 'pinia'
import type { PolicyRule, PolicyUpdateRequest } from '@/types/policy'
import * as policiesApi from '@/api/policies'

export const usePolicyStore = defineStore('policy', () => {
    const policies = ref<PolicyRule[]>([])
    const currentPolicy = ref<PolicyRule | null>(null)
    const isLoading = ref(false)
    const error = ref<string | null>(null)

    async function fetchPolicies() {
        isLoading.value = true
        error.value = null
        try {
            policies.value = await policiesApi.fetchPolicies()
        } catch (e: any) {
            error.value = e.message || '获取策略列表失败'
        } finally {
            isLoading.value = false
        }
    }

    async function fetchPolicyByCapability(capability: string) {
        try {
            currentPolicy.value = await policiesApi.fetchPolicyByCapability(capability)
        } catch (e: any) {
            error.value = e.message || '获取策略详情失败'
        }
    }

    async function updatePolicy(capability: string, data: PolicyUpdateRequest) {
        try {
            const updated = await policiesApi.updatePolicy(capability, data)
            currentPolicy.value = updated
            const idx = policies.value.findIndex((p) => p.capability === capability)
            if (idx !== -1) {
                policies.value[idx] = updated
            }
        } catch (e: any) {
            error.value = e.message || '更新策略失败'
            throw e
        }
    }

    return {
        policies,
        currentPolicy,
        isLoading,
        error,
        fetchPolicies,
        fetchPolicyByCapability,
        updatePolicy,
    }
})