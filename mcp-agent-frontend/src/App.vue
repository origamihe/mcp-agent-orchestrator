<template>
    <div class="app-shell">
        <Sidebar />
        <div class="app-main">
            <StatusBar
                :isConnected="isConnected"
                :selectedModelId="selectedModelId"
                :models="availableModels"
            />
            <div class="app-content">
                <router-view />
            </div>
        </div>
        <ToastContainer ref="toastRef" />
    </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import Sidebar from '@/components/common/Sidebar.vue'
import StatusBar from '@/components/common/StatusBar.vue'
import ToastContainer from '@/components/common/ToastContainer.vue'
import { useWebSocket } from '@/composables/useWebSocket'
import { registerToastContainer } from '@/composables/useToast'
import { useAppStore } from '@/stores/app'
import http from '@/api/client'

const appStore = useAppStore()
const toastRef = ref()

const wsUrl = `${location.protocol === 'https:' ? 'wss:' : 'ws:'}//${location.host}/ws/mcp`
const { isConnected, connect } = useWebSocket(wsUrl)

const { selectedModelId, availableModels } = appStore

onMounted(async () => {
    registerToastContainer(toastRef.value)

    try {
        const res = await http.get('/mcp/configs') as any
        appStore.setModels(res ?? [])
    } catch { /* ignore */ }

    connect()
})
</script>

<style scoped>
.app-shell {
    display: flex;
    height: 100vh;
    overflow: hidden;
}

.app-main {
    flex: 1;
    display: flex;
    flex-direction: column;
    overflow: hidden;
}

.app-content {
    flex: 1;
    overflow: hidden;
}
</style>