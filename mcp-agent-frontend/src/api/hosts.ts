import http from './client'
import type { HostInfo } from '@/types/host'

export async function fetchHosts(): Promise<HostInfo[]> {
    return http.get('/api/hosts') as unknown as HostInfo[]
}

export async function fetchHostById(id: string): Promise<HostInfo> {
    return http.get(`/api/hosts/${id}`) as unknown as HostInfo
}