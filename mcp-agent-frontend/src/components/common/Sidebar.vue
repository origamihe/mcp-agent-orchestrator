<template>
    <nav :class="['sidebar', { collapsed: !isExpanded }]">
        <router-link to="/" class="sidebar-brand" @click="isExpanded = true">
            <span class="brand-text">{{ isExpanded ? 'MCP Agent' : 'MCP' }}</span>
            <span v-if="isExpanded" class="brand-subtitle">Console</span>
        </router-link>

        <div class="sidebar-nav">
            <div class="nav-section">
                <router-link
                    to="/"
                    :class="['nav-item', { active: isActive('/') }]"
                    :title="'Overview'"
                >
                    <HomeIcon class="nav-icon" />
                    <span v-if="isExpanded" class="nav-label">Overview</span>
                </router-link>
            </div>

            <div class="nav-section">
                <span v-if="isExpanded" class="nav-section-label">Agent</span>
                <router-link
                    v-for="item in agentItems"
                    :key="item.path"
                    :to="item.path"
                    :class="['nav-item', { active: isActive(item.path) }]"
                    :title="item.label"
                >
                    <component :is="item.icon" class="nav-icon" />
                    <span v-if="isExpanded" class="nav-label">{{ item.label }}</span>
                </router-link>
            </div>

            <div class="nav-section">
                <span v-if="isExpanded" class="nav-section-label">Knowledge</span>
                <router-link
                    v-for="item in knowledgeItems"
                    :key="item.path"
                    :to="item.path"
                    :class="['nav-item', { active: isActive(item.path) }]"
                    :title="item.label"
                >
                    <component :is="item.icon" class="nav-icon" />
                    <span v-if="isExpanded" class="nav-label">{{ item.label }}</span>
                </router-link>
            </div>

            <div class="nav-section">
                <span v-if="isExpanded" class="nav-section-label">Control</span>
                <router-link
                    v-for="item in controlItems"
                    :key="item.path"
                    :to="item.path"
                    :class="['nav-item', { active: isActive(item.path) }]"
                    :title="item.label"
                >
                    <component :is="item.icon" class="nav-icon" />
                    <span v-if="isExpanded" class="nav-label">{{ item.label }}</span>
                </router-link>
            </div>

            <div class="nav-section">
                <span v-if="isExpanded" class="nav-section-label">System</span>
                <router-link
                    v-for="item in systemItems"
                    :key="item.path"
                    :to="item.path"
                    :class="['nav-item', { active: isActive(item.path) }]"
                    :title="item.label"
                >
                    <component :is="item.icon" class="nav-icon" />
                    <span v-if="isExpanded" class="nav-label">{{ item.label }}</span>
                </router-link>
            </div>

            <div class="nav-section nav-section-last">
                <router-link
                    to="/settings"
                    :class="['nav-item', { active: isActive('/settings') }]"
                    :title="'Settings'"
                >
                    <Cog6ToothIcon class="nav-icon" />
                    <span v-if="isExpanded" class="nav-label">Settings</span>
                </router-link>
            </div>
        </div>

        <button class="sidebar-toggle" @click="isExpanded = !isExpanded" :title="isExpanded ? 'Collapse' : 'Expand'">
            <ChevronLeftIcon v-if="isExpanded" class="toggle-icon" />
            <ChevronRightIcon v-else class="toggle-icon" />
        </button>
    </nav>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useRoute } from 'vue-router'
import {
    HomeIcon,
    SquaresPlusIcon,
    ClockIcon,
    BookOpenIcon,
    CircleStackIcon,
    WrenchScrewdriverIcon,
    ShieldCheckIcon,
    ComputerDesktopIcon,
    ChatBubbleLeftRightIcon,
    DocumentTextIcon,
    Cog6ToothIcon,
    ChevronLeftIcon,
    ChevronRightIcon,
} from '@heroicons/vue/24/outline'

const route = useRoute()
const isExpanded = ref(true)

const agentItems = [
    { path: '/agents', label: 'Agents', icon: SquaresPlusIcon },
    { path: '/runs', label: 'Runs', icon: ClockIcon },
]

const knowledgeItems = [
    { path: '/knowledge', label: 'Knowledge', icon: BookOpenIcon },
    { path: '/memory', label: 'Memory', icon: CircleStackIcon },
]

const controlItems = [
    { path: '/tools', label: 'Tools', icon: WrenchScrewdriverIcon },
    { path: '/policies', label: 'Policies', icon: ShieldCheckIcon },
]

const systemItems = [
    { path: '/hosts', label: 'Hosts', icon: ComputerDesktopIcon },
    { path: '/sessions', label: 'Sessions', icon: ChatBubbleLeftRightIcon },
    { path: '/logs', label: 'Logs', icon: DocumentTextIcon },
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
    width: 232px;
    background: var(--color-bg);
    color: var(--color-text);
    display: flex;
    flex-direction: column;
    flex-shrink: 0;
    border-right: 1px solid var(--color-border);
    transition: width 0.25s cubic-bezier(0.4, 0, 0.2, 1);
    overflow: hidden;
}

.sidebar.collapsed {
    width: 60px;
}

.sidebar-brand {
    padding: 22px 18px 18px;
    text-decoration: none;
    display: block;
    text-align: center;
    border-bottom: 1px solid var(--color-border);
    transition: padding 0.25s;
}

.sidebar.collapsed .sidebar-brand {
    padding: 18px 10px 14px;
}

.brand-text {
    font-size: 20px;
    font-weight: 700;
    color: var(--color-primary);
    letter-spacing: -0.3px;
}

.sidebar.collapsed .brand-text {
    font-size: 16px;
}

.brand-subtitle {
    display: block;
    font-size: 11px;
    color: var(--color-text-secondary);
    margin-top: 1px;
    letter-spacing: 0.8px;
    font-weight: 500;
}

.sidebar-nav {
    list-style: none;
    padding: 12px 10px;
    flex: 1;
    overflow-y: auto;
    display: flex;
    flex-direction: column;
    gap: 2px;
}

.sidebar.collapsed .sidebar-nav {
    padding: 12px 6px;
}

.nav-section {
    display: flex;
    flex-direction: column;
    gap: 2px;
}

.nav-section-last {
    margin-top: auto;
}

.nav-section-label {
    font-size: 10px;
    font-weight: 600;
    color: var(--color-text-secondary);
    text-transform: uppercase;
    letter-spacing: 1px;
    padding: 14px 14px 6px;
    display: block;
}

.nav-item {
    display: flex;
    align-items: center;
    gap: 10px;
    padding: 9px 14px;
    cursor: pointer;
    font-size: 13px;
    font-weight: 500;
    border-radius: 8px;
    color: var(--color-text-secondary);
    text-decoration: none;
    white-space: nowrap;
    transition: background 0.15s ease, color 0.15s ease;
}

.sidebar.collapsed .nav-item {
    padding: 10px;
    justify-content: center;
    gap: 0;
}

.nav-item:hover {
    background: var(--accent-bg);
    color: var(--color-text);
}

.nav-item.active {
    background: var(--accent-bg);
    color: var(--color-accent);
}

.nav-item.active .nav-icon {
    color: var(--color-accent);
}

.nav-icon {
    width: 20px;
    height: 20px;
    flex-shrink: 0;
    color: var(--color-text-secondary);
    transition: color 0.15s ease;
}

.nav-item:hover .nav-icon {
    color: var(--color-text);
}

.sidebar-toggle {
    display: flex;
    align-items: center;
    justify-content: center;
    padding: 10px;
    border: none;
    border-top: 1px solid var(--color-border);
    background: none;
    cursor: pointer;
    color: var(--color-text-secondary);
    transition: color 0.15s ease;
    border-radius: 0;
}

.sidebar-toggle:hover {
    color: var(--color-accent);
    box-shadow: none;
}

.toggle-icon {
    width: 18px;
    height: 18px;
}
</style>