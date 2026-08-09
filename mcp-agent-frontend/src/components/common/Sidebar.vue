<template>
    <nav class="sidebar">
        <div class="sidebar-logo">
            <span class="logo-text">MCP Agent</span>
            <span class="logo-subtitle">Admin Console</span>
        </div>
        <ul class="sidebar-nav">
            <li
                v-for="item in navItems"
                :key="item.id"
                :class="['nav-item', { active: activeTab === item.id }]"
                @click="handleNavClick(item.id)"
            >
                <component :is="iconComponent(item.icon)" class="nav-icon" />
                <span class="nav-label">{{ item.label }}</span>
                <span v-if="item.badge" class="nav-badge">{{ item.badge }}</span>
            </li>
        </ul>
        <div class="sidebar-footer">
            <span class="version">v2.0 · Workspace First</span>
        </div>
    </nav>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import type { AgentFeature } from '@/types/agent'
import {
  HomeIcon,
  FolderOpenIcon,
  ComputerDesktopIcon,
  ChatBubbleLeftRightIcon,
  CpuChipIcon,
  UserGroupIcon,
  Cog6ToothIcon,
  BoltIcon,
  SquaresPlusIcon,
} from '@heroicons/vue/24/outline'

const activeTab = ref<AgentFeature>('dashboard')

const navItems = [
    { id: 'dashboard' as AgentFeature, label: '仪表盘', icon: 'home' },
    { id: 'workspaces' as AgentFeature, label: '工作空间', icon: 'folder-open' },
    { id: 'hosts' as AgentFeature, label: '宿主管理', icon: 'computer-desktop' },
    { id: 'agents' as AgentFeature, label: 'Agent 管理', icon: 'squares-plus' },
    { id: 'chat' as AgentFeature, label: '调试对话', icon: 'chat-bubble' },
    { id: 'skills' as AgentFeature, label: '技能管理', icon: 'cpu-chip' },
    { id: 'prompt-manager' as AgentFeature, label: '角色管理', icon: 'user-group' },
    { id: 'settings' as AgentFeature, label: '系统配置', icon: 'cog' },
]

const emit = defineEmits<{
    (e: 'navigate', feature: AgentFeature): void
}>()

const iconMap: Record<string, any> = {
  'home': HomeIcon,
  'folder-open': FolderOpenIcon,
  'computer-desktop': ComputerDesktopIcon,
  'chat-bubble': ChatBubbleLeftRightIcon,
  'cpu-chip': CpuChipIcon,
  'user-group': UserGroupIcon,
  'cog': Cog6ToothIcon,
  'bolt': BoltIcon,
  'squares-plus': SquaresPlusIcon,
}

function iconComponent(name: string) {
  return iconMap[name] || null
}

function handleNavClick(id: AgentFeature) {
    activeTab.value = id
    emit('navigate', id)
}
</script>

<style scoped>
.sidebar {
    width: 240px;
    background: linear-gradient(180deg, rgba(255,255,255,0.85) 0%, rgba(255,255,255,0.6) 100%);
    backdrop-filter: blur(30px);
    -webkit-backdrop-filter: blur(30px);
    color: var(--color-text);
    display: flex;
    flex-direction: column;
    flex-shrink: 0;
    border-right: 1.5px solid rgba(255,255,255,0.6);
    box-shadow: 4px 0 24px rgba(106, 133, 255, 0.08);
}

.sidebar-logo {
    padding: 24px 20px 16px;
    text-align: center;
    border-bottom: 1.5px solid rgba(255,255,255,0.5);
}

.logo-text {
    font-size: 22px;
    font-weight: 700;
    background: var(--gradient-dream);
    -webkit-background-clip: text;
    -webkit-text-fill-color: transparent;
    background-clip: text;
}

.logo-subtitle {
    display: block;
    font-size: 11px;
    color: var(--color-text-secondary);
    margin-top: 2px;
    letter-spacing: 1px;
}

.sidebar-nav {
    list-style: none;
    padding: 16px 12px;
    flex: 1;
    display: flex;
    flex-direction: column;
    gap: 4px;
}

.nav-item {
    display: flex;
    align-items: center;
    gap: 12px;
    padding: 12px 16px;
    cursor: pointer;
    transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
    font-size: 15px;
    font-weight: 500;
    border-radius: 14px;
    color: #444;
}

.nav-item:hover {
    background: rgba(255, 255, 255, 0.7);
    box-shadow: 0 4px 16px rgba(106, 133, 255, 0.12);
    transform: translateY(-1px);
    color: #667eea;
}

.nav-item.active {
    background: var(--gradient-dream);
    color: #fff;
    box-shadow: 0 4px 16px rgba(106, 133, 255, 0.3);
}

.nav-icon {
  transition: transform 0.3s ease;
  width: 24px;
  height: 24px;
}

.nav-item:hover .nav-icon {
    transform: scale(1.1);
}

.nav-badge {
    margin-left: auto;
    background: rgba(255, 82, 82, 0.15);
    color: #ff5252;
    font-size: 11px;
    font-weight: 600;
    padding: 2px 8px;
    border-radius: 10px;
}

.sidebar-footer {
    padding: 16px 20px;
    border-top: 1.5px solid rgba(255,255,255,0.5);
    text-align: center;
}

.version {
    font-size: 12px;
    color: var(--color-text-secondary);
}
</style>