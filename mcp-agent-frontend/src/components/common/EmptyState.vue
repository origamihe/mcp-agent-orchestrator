<template>
    <div class="empty-state">
        <component :is="icon" class="empty-icon" v-if="icon" />
        <p class="empty-text">{{ text }}</p>
        <slot />
    </div>
</template>

<script setup lang="ts">
import type { Component } from 'vue'

defineProps<{
    text?: string
    icon?: Component
}>()
</script>

<style scoped>
.empty-state {
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
.empty-text {
    font-size: 14px;
}
</style>