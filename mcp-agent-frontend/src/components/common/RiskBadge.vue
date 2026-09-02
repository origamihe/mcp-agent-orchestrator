<template>
    <span :class="['risk-badge', `risk-${level.toLowerCase()}`]">
        {{ level }}
    </span>
</template>

<script setup lang="ts">
defineProps<{
    level: string
}>()
</script>

<style scoped>
.risk-badge {
    padding: 3px 10px;
    border-radius: 20px;
    font-size: 12px;
    font-weight: 600;
    display: inline-flex;
}
.risk-l0 { background: #e8f5e9; color: #2e7d32; }
.risk-l1 { background: #c8e6c9; color: #388e3c; }
.risk-l2 { background: #fff9c4; color: #f9a825; }
.risk-l3 { background: #ffe0b2; color: #ef6c00; }
.risk-l4 { background: #ffccbc; color: #d84315; }
.risk-l5 { background: #ffcdd2; color: #c62828; }
</style>