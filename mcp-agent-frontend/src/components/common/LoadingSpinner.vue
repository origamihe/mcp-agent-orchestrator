<template>
    <div class="loading">
        <span class="loading-spinner"></span>
        <span v-if="text" class="loading-text">{{ text }}</span>
    </div>
</template>

<script setup lang="ts">
defineProps<{
    text?: string
}>()
</script>

<style scoped>
.loading {
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 12px;
    padding: 40px 0;
    color: var(--color-text-secondary);
}
.loading-spinner {
    width: 32px;
    height: 32px;
    border: 3px solid rgba(102, 126, 234, 0.15);
    border-top-color: #667eea;
    border-radius: 50%;
    animation: spin 0.8s linear infinite;
}
.loading-text {
    font-size: 13px;
}
@keyframes spin {
    to { transform: rotate(360deg); }
}
</style>