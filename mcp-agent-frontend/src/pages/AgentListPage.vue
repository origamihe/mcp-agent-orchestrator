<template>
    <div class="page">
        <AgentPanel
            :agents="agentStore.agents"
            @navigate="handleNavigate"
            @test-agent="handleTestAgent"
            @run-task="handleRunTask"
            @run-pipeline="handleRunPipeline"
            @run-parallel="handleRunParallel"
            @run-delegate="handleRunDelegate"
        />
    </div>
</template>

<script setup lang="ts">
import { onMounted } from 'vue'
import { useRouter } from 'vue-router'
import AgentPanel from '@/components/features/AgentPanel.vue'
import { useAgentStore } from '@/stores/agentStore'

const router = useRouter()
const agentStore = useAgentStore()

function handleNavigate(feature: string) {
    router.push(feature === 'settings' ? '/settings' : '/')
}

function handleTestAgent(agentId: string) {
    router.push(`/workspace/${agentId}`)
}

function handleRunTask(agentId: string) {
    router.push(`/workspace/${agentId}`)
}

function handleRunPipeline() {
    console.log('[AgentListPage] Run pipeline workflow')
}

function handleRunParallel() {
    console.log('[AgentListPage] Run parallel workflow')
}

function handleRunDelegate() {
    console.log('[AgentListPage] Run delegate workflow')
}

onMounted(() => {
    agentStore.fetchAgents()
})
</script>

<style scoped>
.page {
    height: 100%;
    overflow-y: auto;
}
</style>