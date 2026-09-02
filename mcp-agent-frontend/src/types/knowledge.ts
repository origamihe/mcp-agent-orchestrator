export interface KnowledgeCollection {
    id: string
    name: string
    description: string
    documentCount: number
    chunkCount: number
    embeddingModel?: string
    createdAt: string
    updatedAt: string
}

export interface KnowledgeDocument {
    id: string
    collectionId: string
    title: string
    source: string
    format: string
    size: number
    chunkCount: number
    metadata?: Record<string, unknown>
    createdAt: string
}

export interface KnowledgeChunk {
    id: string
    documentId: string
    content: string
    embedding?: number[]
    metadata?: Record<string, unknown>
    index: number
}

export interface RetrievalStats {
    totalDocuments: number
    totalChunks: number
    totalEmbeddings: number
    averageChunkSize: number
}