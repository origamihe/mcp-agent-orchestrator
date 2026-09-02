import { ref } from 'vue'
import { defineStore } from 'pinia'
import type { MemoryEntry, MemoryCreateRequest, MemorySearchQuery, MemorySearchResult } from '@/types/memory'
import * as memoryApi from '@/api/memory'

export const useMemoryStore = defineStore('memory', () => {
    const memories = ref<MemoryEntry[]>([])
    const searchResults = ref<MemorySearchResult[]>([])
    const isLoading = ref(false)
    const error = ref<string | null>(null)

    async function fetchMemories(params?: { type?: string; agentId?: string; limit?: number; offset?: number }) {
        isLoading.value = true
        error.value = null
        try {
            memories.value = await memoryApi.fetchMemories(params)
        } catch (e: any) {
            error.value = e.message || '获取记忆列表失败'
        } finally {
            isLoading.value = false
        }
    }

    async function createMemory(data: MemoryCreateRequest) {
        try {
            const entry = await memoryApi.createMemory(data)
            memories.value.unshift(entry)
            return entry
        } catch (e: any) {
            error.value = e.message || '创建记忆失败'
            throw e
        }
    }

    async function deleteMemory(id: string) {
        try {
            await memoryApi.deleteMemory(id)
            memories.value = memories.value.filter((m) => m.id !== id)
        } catch (e: any) {
            error.value = e.message || '删除记忆失败'
        }
    }

    async function searchMemories(query: MemorySearchQuery) {
        isLoading.value = true
        error.value = null
        try {
            searchResults.value = await memoryApi.searchMemories(query)
        } catch (e: any) {
            error.value = e.message || '搜索记忆失败'
        } finally {
            isLoading.value = false
        }
    }

    return {
        memories,
        searchResults,
        isLoading,
        error,
        fetchMemories,
        createMemory,
        deleteMemory,
        searchMemories,
    }
})