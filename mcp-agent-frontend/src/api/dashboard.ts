import http from './client'
import type { DashboardOverview } from '@/types/dashboard'

export async function fetchDashboardOverview(): Promise<DashboardOverview> {
    return http.get('/api/dashboard/overview') as unknown as DashboardOverview
}

export async function fetchRuntimeHealth(): Promise<unknown> {
    return http.get('/api/dashboard/health') as unknown as unknown
}