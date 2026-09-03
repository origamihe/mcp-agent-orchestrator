import { ref, onUnmounted } from 'vue'
import type { FileLogEntry, LogStreamMessage } from '@/types/log'

export function useLogStream(module?: string, level?: string) {
    const socket = ref<WebSocket | null>(null)
    const isConnected = ref(false)
    const entries = ref<FileLogEntry[]>([])
    const error = ref<string | null>(null)
    let reconnectTimer: ReturnType<typeof setTimeout> | null = null

    function connect() {
        const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
        const host = window.location.host
        const params = new URLSearchParams()
        if (module) params.set('module', module)
        if (level) params.set('level', level)
        const query = params.toString()
        const url = `${protocol}//${host}/ws/logs${query ? '?' + query : ''}`

        try {
            const ws = new WebSocket(url)
            socket.value = ws

            ws.onopen = () => {
                isConnected.value = true
                error.value = null
            }

            ws.onmessage = (event) => {
                try {
                    const msg: LogStreamMessage = JSON.parse(event.data)
                    if (msg.type === 'logBatch' && msg.entries) {
                        entries.value = [...entries.value, ...msg.entries].slice(-200)
                    }
                } catch {
                    // ignore parse errors
                }
            }

            ws.onclose = () => {
                isConnected.value = false
                scheduleReconnect()
            }

            ws.onerror = () => {
                error.value = 'WebSocket connection error'
                isConnected.value = false
            }
        } catch (e: any) {
            error.value = e.message || 'Failed to connect'
        }
    }

    function scheduleReconnect() {
        if (reconnectTimer) return
        reconnectTimer = setTimeout(() => {
            reconnectTimer = null
            connect()
        }, 3000)
    }

    function disconnect() {
        if (reconnectTimer) {
            clearTimeout(reconnectTimer)
            reconnectTimer = null
        }
        if (socket.value) {
            socket.value.close()
            socket.value = null
        }
        isConnected.value = false
        entries.value = []
    }

    onUnmounted(() => {
        disconnect()
    })

    return {
        socket,
        isConnected,
        entries,
        error,
        connect,
        disconnect,
    }
}