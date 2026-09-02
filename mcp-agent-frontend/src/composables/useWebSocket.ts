import { ref, onUnmounted } from 'vue'

export interface WebSocketOptions {
    reconnectInterval?: number
    maxReconnectAttempts?: number
    heartbeatInterval?: number
}

export function useWebSocket(url: string, options: WebSocketOptions = {}) {
    const {
        reconnectInterval = 3000,
        maxReconnectAttempts = 5,
        heartbeatInterval = 30000,
    } = options

    const socket = ref<WebSocket | null>(null)
    const isConnected = ref(false)
    const lastMessage = ref<string>('')
    const error = ref<string | null>(null)

    let reconnectAttempts = 0
    let reconnectTimer: ReturnType<typeof setTimeout> | null = null
    let heartbeatTimer: ReturnType<typeof setInterval> | null = null
    let messageQueue: Record<string, unknown>[] = []
    let intentionalClose = false

    function connect() {
        if (socket.value && (socket.value.readyState === WebSocket.OPEN || socket.value.readyState === WebSocket.CONNECTING)) {
            return
        }

        intentionalClose = false
        try {
            socket.value = new WebSocket(url)
        } catch {
            error.value = 'WebSocket 连接创建失败'
            scheduleReconnect()
            return
        }

        socket.value.onopen = () => {
            isConnected.value = true
            error.value = null
            reconnectAttempts = 0
            startHeartbeat()
            flushMessageQueue()
        }

        socket.value.onmessage = (event) => {
            if (event.data === 'pong') return
            lastMessage.value = event.data
        }

        socket.value.onerror = () => {
            error.value = 'WebSocket 连接错误'
        }

        socket.value.onclose = () => {
            isConnected.value = false
            stopHeartbeat()
            if (!intentionalClose) {
                scheduleReconnect()
            }
        }
    }

    function send(data: Record<string, unknown>) {
        if (socket.value && isConnected.value) {
            socket.value.send(JSON.stringify(data))
        } else {
            messageQueue.push(data)
        }
    }

    function disconnect() {
        intentionalClose = true
        stopHeartbeat()
        clearReconnectTimer()
        socket.value?.close()
        socket.value = null
        isConnected.value = false
    }

    function scheduleReconnect() {
        if (reconnectAttempts >= maxReconnectAttempts) {
            error.value = `WebSocket 重连失败，已尝试 ${maxReconnectAttempts} 次`
            return
        }
        clearReconnectTimer()
        reconnectAttempts++
        const delay = reconnectInterval * Math.min(reconnectAttempts, 5)
        reconnectTimer = setTimeout(() => {
            connect()
        }, delay)
    }

    function clearReconnectTimer() {
        if (reconnectTimer) {
            clearTimeout(reconnectTimer)
            reconnectTimer = null
        }
    }

    function startHeartbeat() {
        stopHeartbeat()
        heartbeatTimer = setInterval(() => {
            if (socket.value && isConnected.value) {
                socket.value.send('ping')
            }
        }, heartbeatInterval)
    }

    function stopHeartbeat() {
        if (heartbeatTimer) {
            clearInterval(heartbeatTimer)
            heartbeatTimer = null
        }
    }

    function flushMessageQueue() {
        while (messageQueue.length > 0) {
            const msg = messageQueue.shift()
            if (msg && socket.value && isConnected.value) {
                socket.value.send(JSON.stringify(msg))
            }
        }
    }

    onUnmounted(() => {
        disconnect()
    })

    return {
        socket,
        isConnected,
        lastMessage,
        error,
        connect,
        send,
        disconnect,
    }
}