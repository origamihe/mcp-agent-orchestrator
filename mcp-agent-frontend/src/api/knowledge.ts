import http from './client'
import type { KnowledgeCollection, KnowledgeDocument, RetrievalStats } from '@/types/knowledge'

export async function fetchCollections(): Promise<KnowledgeCollection[]> {
    return http.get('/api/knowledge/collections') as unknown as KnowledgeCollection[]
}

export async function fetchCollectionDocuments(collectionId: string): Promise<KnowledgeDocument[]> {
    return http.get(`/api/knowledge/${collectionId}/docs`) as unknown as KnowledgeDocument[]
}

export async function fetchRetrievalStats(): Promise<RetrievalStats> {
    return http.get('/api/knowledge/stats') as unknown as RetrievalStats
}