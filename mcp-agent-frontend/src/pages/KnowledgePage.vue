<template>
    <div class="page">
        <div class="page-header">
            <h2>知识库管理</h2>
            <span class="subtitle">RAG 知识库集合、文档管理与检索统计</span>
        </div>

        <div v-if="knowledgeStore.isLoading" class="loading">加载中...</div>
        <div v-else-if="knowledgeStore.collections.length === 0" class="empty-state">
            <BookOpenIcon class="empty-icon" />
            <p>暂无知识库集合</p>
        </div>
        <template v-else>
            <div class="stats-bar" v-if="knowledgeStore.stats">
                <div class="stat-chip">
                    <span class="stat-num">{{ knowledgeStore.stats.totalDocuments }}</span>
                    <span class="stat-label">文档总数</span>
                </div>
                <div class="stat-chip">
                    <span class="stat-num">{{ knowledgeStore.stats.totalChunks }}</span>
                    <span class="stat-label">Chunks</span>
                </div>
                <div class="stat-chip">
                    <span class="stat-num">{{ knowledgeStore.stats.avgRetrievalTime }}ms</span>
                    <span class="stat-label">平均检索</span>
                </div>
            </div>

            <div class="collection-grid">
                <div
                    v-for="col in knowledgeStore.collections"
                    :key="col.id"
                    :class="['collection-card', { expanded: selectedCollection === col.id }]"
                    @click="selectCollection(col.id)"
                >
                    <div class="collection-header">
                        <span class="collection-name">{{ col.name }}</span>
                        <span class="collection-count">{{ col.documentCount }} 文档</span>
                    </div>
                    <p class="collection-desc" v-if="col.description">{{ col.description }}</p>
                    <div class="collection-meta">
                        <span>嵌入模型: {{ col.embeddingModel }}</span>
                        <span>更新: {{ formatDate(col.updatedAt) }}</span>
                    </div>
                    <div v-if="selectedCollection === col.id" class="collection-docs">
                        <div class="doc-list" v-if="knowledgeStore.currentDocuments.length">
                            <div v-for="doc in knowledgeStore.currentDocuments" :key="doc.id" class="doc-item">
                                <span class="doc-name">{{ doc.title }}</span>
                                <span class="doc-chunks">{{ doc.chunkCount }} chunks</span>
                            </div>
                        </div>
                        <p v-else class="empty-hint">暂无文档</p>
                    </div>
                </div>
            </div>
        </template>
    </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { BookOpenIcon } from '@heroicons/vue/24/outline'
import { useKnowledgeStore } from '@/stores/knowledgeStore'

const knowledgeStore = useKnowledgeStore()
const selectedCollection = ref<string | null>(null)

function selectCollection(id: string) {
    selectedCollection.value = selectedCollection.value === id ? null : id
    if (selectedCollection.value) {
        knowledgeStore.fetchCollectionDocuments(id)
    }
}

function formatDate(dateStr: string): string {
    if (!dateStr) return ''
    return new Date(dateStr).toLocaleString('zh-CN')
}

onMounted(() => {
    knowledgeStore.fetchCollections()
    knowledgeStore.fetchRetrievalStats()
})
</script>

<style scoped>
.page-header {
    margin-bottom: 24px;
}

.page-header h2 {
    font-size: 28px;
    font-weight: 650;
    letter-spacing: -0.3px;
}

.subtitle {
    font-size: 13px;
    color: var(--color-text-secondary);
    display: block;
    margin-top: 4px;
}

.loading, .empty-state {
    padding: 60px 0;
    text-align: center;
    color: var(--color-text-secondary);
}

.empty-icon {
    width: 40px;
    height: 40px;
    margin-bottom: 12px;
    opacity: 0.25;
}

.stats-bar {
    display: flex;
    gap: 10px;
    margin-bottom: 24px;
    flex-wrap: wrap;
}

.stat-chip {
    padding: 10px 18px;
    border-radius: var(--radius-sm);
    background: var(--color-surface);
    border: 1px solid var(--color-border);
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 2px;
    min-width: 72px;
}

.stat-num {
    font-size: 18px;
    font-weight: 650;
    color: var(--color-accent);
}

.stat-label {
    font-size: 11px;
    color: var(--color-text-secondary);
    font-weight: 500;
}

.collection-grid {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(360px, 1fr));
    gap: 14px;
}

.collection-card {
    background: var(--color-surface);
    border-radius: var(--radius-md);
    padding: 20px;
    border: 1px solid var(--color-border);
    cursor: pointer;
    transition: background 0.15s ease, border-color 0.15s ease;
}

.collection-card:hover {
    background: var(--accent-bg);
    border-color: var(--color-accent);
}

.collection-card.expanded {
    border-color: var(--color-accent);
}

.collection-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 8px;
}

.collection-name {
    font-weight: 600;
    font-size: 15px;
}

.collection-count {
    font-size: 12px;
    color: var(--color-text-secondary);
    background: rgba(0,0,0,0.04);
    padding: 3px 10px;
    border-radius: 20px;
}

.collection-desc {
    font-size: 13px;
    color: var(--color-text-secondary);
    margin-bottom: 10px;
    line-height: 1.5;
}

.collection-meta {
    display: flex;
    gap: 16px;
    font-size: 12px;
    color: var(--color-text-secondary);
}

.collection-docs {
    margin-top: 14px;
    padding-top: 14px;
    border-top: 1px solid var(--color-border);
}

.doc-list {
    display: flex;
    flex-direction: column;
    gap: 6px;
}

.doc-item {
    display: flex;
    justify-content: space-between;
    padding: 8px 12px;
    background: rgba(0,0,0,0.02);
    border-radius: var(--radius-sm);
    font-size: 13px;
}

.doc-name {
    font-weight: 500;
}

.doc-chunks {
    font-size: 12px;
    color: var(--color-text-secondary);
}

.empty-hint {
    font-size: 13px;
    color: var(--color-text-secondary);
    text-align: center;
    padding: 12px 0;
}
</style>