<template>
    <div class="not-found">
        <h1>404</h1>
        <p>页面未找到</p>
        <router-link to="/" class="btn-home">返回首页</router-link>
    </div>
</template>

<script setup lang="ts">
</script>

<style scoped>
.not-found {
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    height: 100%;
    gap: 16px;
}

.not-found h1 {
    font-size: 72px;
    font-weight: 650;
    color: var(--color-accent);
    opacity: 0.6;
}

.not-found p {
    font-size: 18px;
    color: var(--color-text-secondary);
}

.btn-home {
    padding: 10px 24px;
    border-radius: var(--radius-sm);
    background: var(--color-accent);
    color: #fff;
    text-decoration: none;
    font-weight: 500;
    font-size: 14px;
}

.btn-home:hover {
    box-shadow: var(--shadow-glow);
}
</style>