<template>
    <span :class="['badge', `badge-${type}`]">
        <slot>{{ text }}</slot>
    </span>
</template>

<script setup lang="ts">
defineProps<{
    type?: 'success' | 'warning' | 'error' | 'info' | 'neutral'
    text?: string
}>()
</script>

<style scoped>
.badge {
    padding: 3px 10px;
    border-radius: 20px;
    font-size: 12px;
    font-weight: 500;
    display: inline-flex;
    align-items: center;
    gap: 4px;
}
.badge-success { background: #e8f5e9; color: #2e7d32; }
.badge-warning { background: #fff3e0; color: #ef6c00; }
.badge-error { background: #ffebee; color: #c62828; }
.badge-info { background: #e3f2fd; color: #1565c0; }
.badge-neutral { background: rgba(0,0,0,0.04); color: var(--color-text-secondary); }
</style>