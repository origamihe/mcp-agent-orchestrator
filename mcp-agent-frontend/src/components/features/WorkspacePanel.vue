<template>
    <div class="workspace-panel">
        <div class="panel-header">
            <h2>工作空间</h2>
            <span class="count-badge" v-if="workspaces.length > 0">{{ workspaces.length }} 个活跃</span>
        </div>

        <div v-if="workspaces.length === 0" class="empty-state">
            <FolderOpenIcon class="empty-icon" />
            <p>暂无活跃工作空间</p>
            <p class="empty-hint">连接 IDE 或 Desktop Host 后，工作空间将自动创建</p>
        </div>

        <div class="workspace-list" v-else>
            <div
                v-for="ws in workspaces"
                :key="ws.workspaceId"
                class="workspace-card"
            >
                <div class="ws-header">
                    <div class="ws-icon">
                        <FolderOpenIcon class="icon" />
                    </div>
                    <div class="ws-meta">
                        <span class="ws-name">{{ ws.name || ws.workspaceId }}</span>
                        <span class="ws-id">{{ ws.workspaceId }}</span>
                    </div>
                    <span class="ws-time" v-if="ws.lastActiveAt">{{ formatTime(ws.lastActiveAt) }}</span>
                </div>

                <div class="ws-body" v-if="ws.projectPath">
                    <div class="ws-row">
                        <span class="ws-label">项目路径</span>
                        <span class="ws-value ws-path">{{ ws.projectPath }}</span>
                    </div>
                </div>

                <div class="ws-body" v-if="ws.lastActiveFile">
                    <div class="ws-row">
                        <span class="ws-label">上次编辑</span>
                        <span class="ws-value">{{ ws.lastActiveFile }}
                            <span v-if="ws.lastActiveLine">:{{ ws.lastActiveLine }}</span>
                        </span>
                    </div>
                </div>

                <div class="ws-body" v-if="ws.gitState">
                    <div class="ws-row">
                        <span class="ws-label">Git</span>
                        <span class="ws-value">{{ ws.gitState.branch }}</span>
                    </div>
                    <div class="ws-row" v-if="ws.gitState.status">
                        <span class="ws-label">状态</span>
                        <code class="ws-git-status">{{ ws.gitState.status }}</code>
                    </div>
                </div>

                <div class="ws-body" v-if="ws.activeTasks && ws.activeTasks.length > 0">
                    <div class="ws-row">
                        <span class="ws-label">任务</span>
                        <span class="ws-value">{{ activeTaskCount(ws) }} 个进行中</span>
                    </div>
                </div>
            </div>
        </div>
    </div>
</template>

<script setup lang="ts">
import { FolderOpenIcon } from '@heroicons/vue/24/outline'
import type { WorkspaceInfo } from '@/types/agent'

defineProps<{
    workspaces: WorkspaceInfo[]
}>()

function formatTime(iso: string): string {
    try {
        const d = new Date(iso)
        const now = new Date()
        const diff = now.getTime() - d.getTime()
        const mins = Math.floor(diff / 60000)
        if (mins < 1) return '刚刚'
        if (mins < 60) return `${mins} 分钟前`
        const hours = Math.floor(mins / 60)
        if (hours < 24) return `${hours} 小时前`
        return `${Math.floor(hours / 24)} 天前`
    } catch {
        return iso
    }
}

function activeTaskCount(ws: WorkspaceInfo): number {
    return (ws.activeTasks || []).filter(t => t.status === 'IN_PROGRESS').length
}
</script>

<style scoped>
.workspace-panel {
    padding: 32px;
    max-width: 900px;
}

.panel-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: 28px;
}

.panel-header h2 {
    font-size: 24px;
    font-weight: 700;
    color: var(--color-text);
}

.count-badge {
    font-size: 13px;
    background: rgba(102, 126, 234, 0.1);
    color: #667eea;
    padding: 6px 14px;
    border-radius: 20px;
    font-weight: 500;
}

.empty-state {
    text-align: center;
    padding: 60px 20px;
    color: var(--color-text-secondary);
}

.empty-icon {
    width: 56px;
    height: 56px;
    margin-bottom: 16px;
    opacity: 0.3;
}

.empty-state p {
    font-size: 16px;
    margin-bottom: 8px;
}

.empty-hint {
    font-size: 13px;
    opacity: 0.7;
}

.workspace-list {
    display: flex;
    flex-direction: column;
    gap: 16px;
}

.workspace-card {
    background: rgba(255,255,255,0.7);
    backdrop-filter: blur(20px);
    border-radius: 16px;
    padding: 24px;
    border: 1px solid rgba(255,255,255,0.8);
    box-shadow: 0 4px 16px rgba(0,0,0,0.04);
    transition: all 0.2s;
}

.workspace-card:hover {
    box-shadow: 0 6px 24px rgba(102, 126, 234, 0.1);
    transform: translateY(-1px);
}

.ws-header {
    display: flex;
    align-items: center;
    gap: 14px;
    margin-bottom: 16px;
}

.ws-icon {
    width: 40px;
    height: 40px;
    border-radius: 12px;
    background: rgba(102, 126, 234, 0.1);
    display: flex;
    align-items: center;
    justify-content: center;
}

.ws-icon .icon {
    width: 20px;
    height: 20px;
    color: #667eea;
}

.ws-meta {
    flex: 1;
    display: flex;
    flex-direction: column;
}

.ws-name {
    font-size: 16px;
    font-weight: 600;
    color: var(--color-text);
}

.ws-id {
    font-size: 12px;
    color: var(--color-text-secondary);
}

.ws-time {
    font-size: 12px;
    color: var(--color-text-secondary);
    background: rgba(0,0,0,0.04);
    padding: 4px 10px;
    border-radius: 8px;
}

.ws-body {
    background: rgba(0,0,0,0.02);
    border-radius: 10px;
    padding: 12px 16px;
    margin-bottom: 10px;
}

.ws-row {
    display: flex;
    justify-content: space-between;
    align-items: flex-start;
    padding: 4px 0;
    font-size: 13px;
}

.ws-label {
    color: var(--color-text-secondary);
    flex-shrink: 0;
    margin-right: 16px;
}

.ws-value {
    color: var(--color-text);
    font-weight: 500;
    text-align: right;
    word-break: break-all;
}

.ws-path {
    font-family: 'SF Mono', 'Cascadia Code', monospace;
    font-size: 12px;
    background: rgba(0,0,0,0.04);
    padding: 2px 8px;
    border-radius: 4px;
}

.ws-git-status {
    font-family: 'SF Mono', 'Cascadia Code', monospace;
    font-size: 11px;
    background: rgba(0,0,0,0.04);
    padding: 2px 8px;
    border-radius: 4px;
    max-width: 300px;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: pre-wrap;
}
</style>