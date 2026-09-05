<template>
    <div class="page">
        <div class="page-header">
            <h2>记忆管理</h2>
            <span class="subtitle">管理 Agent 长期记忆、短期记忆与项目上下文</span>
        </div>

        <div class="memory-toolbar">
            <select v-model="filterType" class="filter-select" @change="loadMemories">
                <option value="">全部类型</option>
                <option value="PROFILE">用户资料</option>
                <option value="IDENTITY">身份信息</option>
                <option value="PREFERENCE">喜好</option>
                <option value="HABIT">习惯</option>
                <option value="GOAL">长期目标</option>
                <option value="PROJECT">项目</option>
                <option value="FACT">事实</option>
                <option value="RELATION">人物关系</option>
                <option value="SKILL">技能</option>
                <option value="SCHEDULE">日程</option>
                <option value="TEMPORARY">临时信息</option>
                <option value="EVENT">重要事件</option>
            </select>
            <input v-model="searchQuery" placeholder="搜索记忆..." class="search-input" @keyup.enter="handleSearch" />
            <button class="btn-search" @click="handleSearch">搜索</button>
            <button class="btn-add" @click="toggleAddForm">+ 添加记忆</button>
        </div>

        <div v-if="showAddForm" class="add-form">
            <textarea v-model="newContent" placeholder="输入记忆内容..." rows="3"></textarea>
            <div class="add-form-row">
                <input v-model="newImportance" type="number" min="0" max="10" placeholder="重要性 (0-10)" class="filter-select" />
                <button class="btn-save" @click="handleAddMemory" :disabled="!newContent.trim()">保存</button>
                <button class="btn-cancel" @click="toggleAddForm">取消</button>
            </div>
        </div>

        <div v-if="memoryStore.isLoading" class="loading">加载中...</div>

        <div v-else-if="memoryStore.memories.length === 0 && searchResults.length === 0" class="empty-state">
            <CircleStackIcon class="empty-icon" />
            <p>{{ searchQuery ? '未找到匹配的记忆' : '暂无记忆数据' }}</p>
        </div>

        <div class="memory-list" v-else>
            <div v-for="item in displayedItems" :key="item.id" class="memory-card">
                <div class="memory-header">
                    <span :class="['type-badge', `type-${item.type}`]">{{ typeLabel(item.type) }}</span>
                    <span class="memory-score" v-if="'score' in item">相关度: {{ (item as any).score.toFixed(2) }}</span>
                    <span class="memory-importance">重要性: {{ item.type === 'memory' ? normalizeImportance((item as any).importance) : '-' }}/10</span>
                </div>
                <p class="memory-content">{{ 'content' in item ? item.content : (item as any).entry?.content }}</p>
                <div class="memory-footer">
                    <span class="memory-meta">{{ formatDate(item.createdAt || (item as any).entry?.createdAt) }}</span>
                    <button class="btn-delete" @click="handleDelete(item.id)" title="删除">×</button>
                </div>
            </div>
        </div>
    </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { CircleStackIcon } from '@heroicons/vue/24/outline'
import { useMemoryStore } from '@/stores/memoryStore'
import type { MemorySearchResult, MemoryType } from '@/types/memory'

const memoryStore = useMemoryStore()

const filterType = ref<MemoryType | ''>('')
const searchQuery = ref('')
const showAddForm = ref(false)
const newContent = ref('')
const newImportance = ref(5)
const searchResults = ref<MemorySearchResult[]>([])

const filteredMemories = computed(() => {
    let list = memoryStore.memories
    if (filterType.value) {
        list = list.filter((m) => m.type === filterType.value)
    }
    return list
})

const displayedItems = computed(() => {
    if (searchResults.value.length > 0) {
        return searchResults.value.map((r) => ({
            ...r.entry,
            score: r.score,
            type: 'memory' as const,
        })) as any[]
    }
    return filteredMemories.value.map((m) => ({ ...m, type: 'memory' as const })) as any[]
})

function typeLabel(type: string): string {
    const labels: Record<string, string> = {
        PROFILE: '用户资料', IDENTITY: '身份信息', PREFERENCE: '喜好',
        HABIT: '习惯', GOAL: '长期目标', PROJECT: '项目',
        FACT: '事实', RELATION: '人物关系', SKILL: '技能',
        SCHEDULE: '日程', TEMPORARY: '临时信息', EVENT: '重要事件',
    }
    return labels[type] || type
}

function normalizeImportance(value: number | undefined | null): number {
    if (value === undefined || value === null) return 0
    if (value <= 10) return Math.round(value)
    return Math.round((value / 100) * 10)
}

function formatDate(dateStr: string): string {
    if (!dateStr) return ''
    return new Date(dateStr).toLocaleString('zh-CN', {
        month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit',
    })
}

function toggleAddForm() {
    showAddForm.value = !showAddForm.value
    if (!showAddForm.value) {
        newContent.value = ''
        newImportance.value = 5
    }
}

async function handleAddMemory() {
    if (!newContent.value.trim()) return
    await memoryStore.createMemory({
        content: newContent.value.trim(),
        importance: newImportance.value,
    })
    toggleAddForm()
}

async function handleDelete(id: number) {
    await memoryStore.deleteMemory(id)
}

async function handleSearch() {
    if (!searchQuery.value.trim()) {
        searchResults.value = []
        return
    }
    await memoryStore.searchMemories({
        query: searchQuery.value.trim(),
        limit: 20,
    })
    searchResults.value = memoryStore.searchResults
}

async function loadMemories() {
    await memoryStore.fetchMemories({
        limit: 50,
    })
}

onMounted(() => {
    loadMemories()
})
</script>

<style scoped>
.page {
    padding: 24px 32px;
    height: 100%;
    overflow-y: auto;
}

.page-header {
    margin-bottom: 24px;
}

.page-header h2 {
    font-size: 22px;
    font-weight: 700;
}

.subtitle {
    font-size: 13px;
    color: var(--color-text-secondary);
    margin-top: 4px;
    display: block;
}

.memory-toolbar {
    display: flex;
    gap: 12px;
    margin-bottom: 20px;
    align-items: center;
    flex-wrap: wrap;
}

.filter-select, .search-input {
    padding: 8px 14px;
    border-radius: 10px;
    border: 1px solid rgba(0,0,0,0.1);
    background: rgba(255,255,255,0.9);
    font-size: 13px;
}

.search-input {
    flex: 1;
    max-width: 300px;
}

.btn-search {
    padding: 8px 18px;
    border-radius: 10px;
    border: 1px solid #667eea;
    background: rgba(102, 126, 234, 0.08);
    color: #667eea;
    cursor: pointer;
    font-size: 13px;
    font-weight: 500;
}

.btn-add {
    padding: 8px 18px;
    border-radius: 10px;
    border: none;
    background: var(--gradient-dream);
    color: #fff;
    cursor: pointer;
    font-size: 13px;
    font-weight: 500;
}

.add-form {
    background: rgba(255,255,255,0.7);
    border-radius: 12px;
    padding: 16px;
    margin-bottom: 20px;
    border: 1px solid rgba(255,255,255,0.8);
}

.add-form textarea {
    width: 100%;
    border-radius: 8px;
    border: 1px solid rgba(0,0,0,0.1);
    padding: 10px;
    font-size: 13px;
    resize: vertical;
    margin-bottom: 12px;
    box-sizing: border-box;
}

.add-form-row {
    display: flex;
    gap: 10px;
    align-items: center;
}

.btn-save {
    padding: 8px 18px;
    border-radius: 10px;
    border: none;
    background: #27ae60;
    color: #fff;
    cursor: pointer;
    font-size: 13px;
}

.btn-save:disabled {
    opacity: 0.5;
    cursor: not-allowed;
}

.btn-cancel {
    padding: 8px 18px;
    border-radius: 10px;
    border: 1px solid rgba(0,0,0,0.1);
    background: rgba(255,255,255,0.7);
    cursor: pointer;
    font-size: 13px;
}

.loading, .empty-state {
    padding: 60px 0;
    text-align: center;
    color: var(--color-text-secondary);
}

.empty-icon {
    width: 48px;
    height: 48px;
    margin-bottom: 12px;
    opacity: 0.3;
}

.memory-list {
    display: flex;
    flex-direction: column;
    gap: 12px;
}

.memory-card {
    background: rgba(255,255,255,0.7);
    backdrop-filter: blur(20px);
    border-radius: 14px;
    padding: 18px 20px;
    border: 1px solid rgba(255,255,255,0.8);
    box-shadow: 0 2px 12px rgba(0,0,0,0.03);
}

.memory-header {
    display: flex;
    gap: 12px;
    align-items: center;
    margin-bottom: 10px;
}

.type-badge {
    padding: 3px 10px;
    border-radius: 20px;
    font-size: 11px;
    font-weight: 600;
}

.type-PROFILE { background: #e8f5e9; color: #2e7d32; }
.type-IDENTITY { background: #e3f2fd; color: #1565c0; }
.type-PREFERENCE { background: #fff3e0; color: #ef6c00; }
.type-HABIT { background: #f3e5f5; color: #7b1fa2; }
.type-GOAL { background: #e0f7fa; color: #00838f; }
.type-PROJECT { background: #fce4ec; color: #c62828; }
.type-FACT { background: #f1f8e9; color: #558b2f; }
.type-RELATION { background: #e8eaf6; color: #283593; }
.type-SKILL { background: #fff8e1; color: #f57f17; }
.type-SCHEDULE { background: #e0f2f1; color: #00695c; }
.type-TEMPORARY { background: #fbe9e7; color: #bf360c; }
.type-EVENT { background: #ede7f6; color: #4527a0; }

.memory-score, .memory-importance {
    font-size: 12px;
    color: var(--color-text-secondary);
    background: rgba(0,0,0,0.03);
    padding: 3px 8px;
    border-radius: 6px;
}

.memory-content {
    font-size: 14px;
    line-height: 1.6;
    color: var(--color-text);
    margin-bottom: 10px;
}

.memory-footer {
    display: flex;
    justify-content: space-between;
    align-items: center;
}

.memory-meta {
    font-size: 12px;
    color: var(--color-text-secondary);
}

.btn-delete {
    padding: 2px 8px;
    border: none;
    background: none;
    color: #e74c3c;
    cursor: pointer;
    font-size: 18px;
    border-radius: 4px;
}

.btn-delete:hover {
    background: rgba(231, 76, 60, 0.1);
}
</style>