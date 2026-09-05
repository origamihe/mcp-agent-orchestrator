import http from './client'
import type { MemoryEntry, MemoryCreateRequest, MemorySearchQuery, MemorySearchResult } from '@/types/memory'

export async function fetchMemories(params?: { sessionId?: string; userId?: string; limit?: number }): Promise<MemoryEntry[]> {
    return http.get('/api/memory', { params }) as unknown as MemoryEntry[]
}

export async function createMemory(data: MemoryCreateRequest): Promise<MemoryEntry> {
    return http.post('/api/memory', data) as unknown as MemoryEntry
}

export async function deleteMemory(id: number): Promise<void> {
    return http.delete(`/api/memory/${id}`) as unknown as void
}

export async function searchMemories(query: MemorySearchQuery): Promise<MemorySearchResult[]> {
    return http.get('/api/memory/search', { params: query }) as unknown as MemorySearchResult[]
}