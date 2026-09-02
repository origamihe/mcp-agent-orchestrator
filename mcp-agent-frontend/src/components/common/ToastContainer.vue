<template>
    <Teleport to="body">
        <div class="toast-container" :class="`position-${position}`">
            <TransitionGroup name="toast">
                <div
                    v-for="toast in toasts"
                    :key="toast.id"
                    :class="['toast', `toast-${toast.type}`]"
                    @click="dismiss(toast.id)"
                >
                    <div class="toast-icon">
                        <CheckCircleIcon v-if="toast.type === 'success'" class="icon" />
                        <ExclamationTriangleIcon v-else-if="toast.type === 'warning'" class="icon" />
                        <XCircleIcon v-else-if="toast.type === 'error'" class="icon" />
                        <InformationCircleIcon v-else class="icon" />
                    </div>
                    <div class="toast-body">
                        <span v-if="toast.title" class="toast-title">{{ toast.title }}</span>
                        <span class="toast-message">{{ toast.message }}</span>
                    </div>
                    <button class="toast-close" @click.stop="dismiss(toast.id)">&times;</button>
                </div>
            </TransitionGroup>
        </div>
    </Teleport>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import {
    CheckCircleIcon,
    ExclamationTriangleIcon,
    XCircleIcon,
    InformationCircleIcon,
} from '@heroicons/vue/24/outline'

const props = withDefaults(defineProps<{
    position?: 'top-right' | 'top-center' | 'bottom-right'
}>(), {
    position: 'top-right',
})

interface Toast {
    id: string
    type: 'success' | 'error' | 'warning' | 'info'
    message: string
    title?: string
    duration: number
}

const toasts = ref<Toast[]>([])

function addToast(type: Toast['type'], message: string, title?: string, duration = 4000) {
    const id = `${Date.now()}-${Math.random().toString(36).slice(2, 8)}`
    toasts.value.push({ id, type, message, title, duration })
    if (duration > 0) {
        setTimeout(() => dismiss(id), duration)
    }
}

function dismiss(id: string) {
    toasts.value = toasts.value.filter((t) => t.id !== id)
}

function success(msg: string, title?: string) {
    addToast('success', msg, title)
}
function error(msg: string, title?: string) {
    addToast('error', msg, title, 6000)
}
function warning(msg: string, title?: string) {
    addToast('warning', msg, title)
}
function info(msg: string, title?: string) {
    addToast('info', msg, title)
}

defineExpose({ success, error, warning, info })
</script>

<style scoped>
.toast-container {
    position: fixed;
    z-index: 99999;
    display: flex;
    flex-direction: column;
    gap: 8px;
    pointer-events: none;
    max-width: 420px;
}

.position-top-right {
    top: 20px;
    right: 20px;
}

.position-top-center {
    top: 20px;
    left: 50%;
    transform: translateX(-50%);
}

.position-bottom-right {
    bottom: 20px;
    right: 20px;
}

.toast {
    display: flex;
    align-items: flex-start;
    gap: 12px;
    padding: 14px 18px;
    border-radius: 14px;
    background: rgba(255,255,255,0.95);
    backdrop-filter: blur(20px);
    box-shadow: 0 8px 32px rgba(0,0,0,0.12);
    border: 1px solid rgba(255,255,255,0.8);
    pointer-events: all;
    cursor: pointer;
    transition: transform 0.2s;
}

.toast:hover {
    transform: scale(1.02);
}

.toast-icon {
    flex-shrink: 0;
    margin-top: 1px;
}

.icon {
    width: 20px;
    height: 20px;
}

.toast-success .icon { color: #27ae60; }
.toast-error .icon { color: #e74c3c; }
.toast-warning .icon { color: #f39c12; }
.toast-info .icon { color: #667eea; }

.toast-body {
    flex: 1;
    display: flex;
    flex-direction: column;
    gap: 2px;
}

.toast-title {
    font-size: 14px;
    font-weight: 600;
}

.toast-message {
    font-size: 13px;
    color: var(--color-text-secondary);
    line-height: 1.4;
}

.toast-close {
    flex-shrink: 0;
    background: none;
    border: none;
    cursor: pointer;
    font-size: 18px;
    color: var(--color-text-secondary);
    padding: 0;
    line-height: 1;
    opacity: 0.5;
    transition: opacity 0.2s;
}

.toast-close:hover {
    opacity: 1;
}

.toast-enter-active {
    transition: all 0.4s cubic-bezier(0.16, 1, 0.3, 1);
}

.toast-leave-active {
    transition: all 0.3s ease-in;
}

.toast-enter-from {
    opacity: 0;
    transform: translateX(40px);
}

.toast-leave-to {
    opacity: 0;
    transform: translateX(-40px);
}
</style>