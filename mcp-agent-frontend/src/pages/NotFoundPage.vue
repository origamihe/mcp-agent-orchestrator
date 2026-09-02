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
    font-weight: 700;
    background: var(--gradient-dream);
    -webkit-background-clip: text;
    -webkit-text-fill-color: transparent;
    background-clip: text;
}

.not-found p {
    font-size: 18px;
    color: var(--color-text-secondary);
}

.btn-home {
    padding: 10px 24px;
    border-radius: 10px;
    background: var(--gradient-dream);
    color: #fff;
    text-decoration: none;
    font-weight: 500;
}
</style>