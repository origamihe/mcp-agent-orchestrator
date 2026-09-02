<template>
    <nav :class="['sidebar', { collapsed: !isExpanded }]">
        <router-link to="/" class="sidebar-logo" @click="isExpanded = true">
            <span class="logo-text">{{ isExpanded ? 'MCP Agent' : 'MCP' }}</span>
            <span v-if="isExpanded" class="logo-subtitle">Admin Console</span>
        </router-link>
        <ul class="sidebar-nav">
            <li
                v-for="item in navItems"
                :key="item.path"
            >
                <router-link
                    :to="item.path"
                    :class="['nav-item', { active: isActive(item.path) }]"
                    :title="item.label"
                >
                    <component :is="item.icon" class="nav-icon" />
                    <span v-if="isExpanded" class="nav-label">{{ item.label }}</span>
                    <span v-if="isExpanded && item.badge" class="nav-badge">{{ item.badge }}</span>
                </router-link>
            </li>
        </ul>
        <button class="sidebar-toggle" @click="isExpanded = !isExpanded" :title="isExpanded ? '收起' : '展开'">
            <ChevronLeftIcon v-if="isExpanded" class="toggle-icon" />
            <ChevronRightIcon v-else class="toggle-icon" />
        </button>
        <div class="sidebar-footer" v-if="isExpanded">
            <span class="version">v2.0 · Workspace First</span>
        </div>
    </nav>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useRoute } from 'vue-router'
import {
    HomeIcon,
    ComputerDesktopIcon,
    ChatBubbleLeftRightIcon,
    Cog6ToothIcon,
    SquaresPlusIcon,
    BookOpenIcon,
    WrenchScrewdriverIcon,
    ShieldCheckIcon,
    ClockIcon,
    DocumentTextIcon,
    CircleStackIcon,
    ChevronLeftIcon,
    ChevronRightIcon,
} from '@heroicons/vue/24/outline'

const route = useRoute()
const isExpanded = ref(true)

const navItems = [
    { path: '/', label: '仪表盘', icon: HomeIcon },
    { path: '/agents', label: 'Agent 管理', icon: SquaresPlusIcon },
    { path: '/runs', label: '执行历史', icon: ClockIcon },
    { path: '/memory', label: '记忆管理', icon: CircleStackIcon },
    { path: '/knowledge', label: '知识库', icon: BookOpenIcon },
    { path: '/tools', label: '工具管理', icon: WrenchScrewdriverIcon },
    { path: '/policies', label: '安全策略', icon: ShieldCheckIcon },
    { path: '/hosts', label: '宿主管理', icon: ComputerDesktopIcon },
    { path: '/sessions', label: '会话', icon: ChatBubbleLeftRightIcon },
    { path: '/logs', label: '日志', icon: DocumentTextIcon },
    { path: '/settings', label: '系统配置', icon: Cog6ToothIcon },
]

function isActive(path: string): boolean {
    if (path === '/') {
        return route.path === '/'
    }
    return route.path.startsWith(path)
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
    transition: width 0.3s cubic-bezier(0.4, 0, 0.2, 1);
    overflow: hidden;
}

.sidebar.collapsed {
    width: 64px;
}

.sidebar-logo {
    padding: 24px 20px 16px;
    text-align: center;
    border-bottom: 1.5px solid rgba(255,255,255,0.5);
    text-decoration: none;
    display: block;
    transition: padding 0.3s;
}

.sidebar.collapsed .sidebar-logo {
    padding: 20px 12px 14px;
}

.logo-text {
    font-size: 22px;
    font-weight: 700;
    background: var(--gradient-dream);
    -webkit-background-clip: text;
    -webkit-text-fill-color: transparent;
    background-clip: text;
}

.sidebar.collapsed .logo-text {
    font-size: 18px;
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

.sidebar.collapsed .sidebar-nav {
    padding: 16px 8px;
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
    text-decoration: none;
    white-space: nowrap;
}

.sidebar.collapsed .nav-item {
    padding: 12px;
    justify-content: center;
    gap: 0;
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
    flex-shrink: 0;
}

.nav-item:hover .nav-icon {
    transform: scale(1.1);
}

.nav-badge {
    margin-left: auto;
    color: #ff5252;
    font-size: 11px;
    font-weight: 600;
    padding: 2px 8px;
    border-radius: 10px;
}

.sidebar-toggle {
    display: flex;
    align-items: center;
    justify-content: center;
    padding: 12px;
    border: none;
    border-top: 1.5px solid rgba(255,255,255,0.5);
    background: none;
    cursor: pointer;
    color: var(--color-text-secondary);
    transition: color 0.2s;
}

.sidebar-toggle:hover {
    color: #667eea;
}

.toggle-icon {
    width: 20px;
    height: 20px;
}

.sidebar-footer {
    padding: 12px 20px;
    border-top: 1.5px solid rgba(255,255,255,0.5);
    text-align: center;
}

.version {
    font-size: 12px;
    color: var(--color-text-secondary);
}
</style>