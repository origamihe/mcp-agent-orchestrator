import { createRouter, createWebHistory } from 'vue-router'

const router = createRouter({
    history: createWebHistory(import.meta.env.BASE_URL),
    routes: [
        {
            path: '/',
            name: 'dashboard',
            component: () => import('@/pages/DashboardPage.vue'),
        },
        {
            path: '/agents',
            name: 'agents',
            component: () => import('@/pages/AgentListPage.vue'),
        },
        {
            path: '/agents/:id',
            name: 'agent-detail',
            component: () => import('@/pages/AgentDetailPage.vue'),
        },
        {
            path: '/workspace/:agentId',
            name: 'workspace',
            component: () => import('@/pages/AgentWorkspace.vue'),
        },
        {
            path: '/memory',
            name: 'memory',
            component: () => import('@/pages/MemoryPage.vue'),
        },
        {
            path: '/knowledge',
            name: 'knowledge',
            component: () => import('@/pages/KnowledgePage.vue'),
        },
        {
            path: '/tools',
            name: 'tools',
            component: () => import('@/pages/ToolsPage.vue'),
        },
        {
            path: '/policies',
            name: 'policies',
            component: () => import('@/pages/PoliciesPage.vue'),
        },
        {
            path: '/runs',
            name: 'runs',
            component: () => import('@/pages/RunsPage.vue'),
        },
        {
            path: '/runs/:id',
            name: 'run-detail',
            component: () => import('@/pages/RunDetailPage.vue'),
        },
        {
            path: '/hosts',
            name: 'hosts',
            component: () => import('@/pages/HostsPage.vue'),
        },
        {
            path: '/logs',
            name: 'logs',
            component: () => import('@/pages/LogsPage.vue'),
        },
        {
            path: '/sessions',
            name: 'sessions',
            component: () => import('@/pages/SessionsPage.vue'),
        },
        {
            path: '/settings',
            name: 'settings',
            component: () => import('@/pages/SettingsPage.vue'),
        },
        {
            path: '/chat',
            name: 'chat',
            component: () => import('@/pages/ChatPage.vue'),
        },
        {
            path: '/:pathMatch(.*)*',
            name: 'not-found',
            component: () => import('@/pages/NotFoundPage.vue'),
        },
    ],
})

export default router