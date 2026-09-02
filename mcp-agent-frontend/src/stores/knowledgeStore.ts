import { ref } from 'vue'
import { defineStore } from 'pinia'
import type { KnowledgeCollection, KnowledgeDocument, RetrievalStats } from '@/types/knowledge'
import * as knowledgeApi from '@/api/knowledge'

export const useKnowledgeStore = defineStore('knowledge', () => {
    const collections = ref<KnowledgeCollection[]>([])
    const currentDocuments = ref<KnowledgeDocument[]>([])
    const stats = ref<RetrievalStats | null>(null)
    const isLoading = ref(false)
    const error = ref<string | null>(null)

    async function fetchCollections() {
        isLoading.value = true
        error.value = null
        try {
            collections.value = await knowledgeApi.fetchCollections()
        } catch (e: any) {
            error.value = e.message || '获取知识库列表失败'
        } finally {
            isLoading.value = false
        }
    }

    async function fetchCollectionDocuments(collectionId: string) {
        try {
            currentDocuments.value = await knowledgeApi.fetchCollectionDocuments(collectionId)
        } catch (e: any) {
            error.value = e.message || '获取文档列表失败'
        }
    }

    async function fetchRetrievalStats() {
        try {
            stats.value = await knowledgeApi.fetchRetrievalStats()
        } catch (e: any) {
            error.value = e.message || '获取统计信息失败'
        }
    }

    return {
        collections,
        currentDocuments,
        stats,
        isLoading,
        error,
        fetchCollections,
        fetchCollectionDocuments,
        fetchRetrievalStats,
    }
})