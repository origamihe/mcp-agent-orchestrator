import { ref, onUnmounted } from 'vue'

export function useWebSocket(url: string) {
    const socket = ref<WebSocket | null>(null)
    const isConnected = ref(false)
    const lastMessage = ref<string>('')
    const error = ref<string | null>(null)

    function connect() {
        socket.value = new WebSocket(url)

        socket.value.onopen = () => {
            isConnected.value = true
            error.value = null
        }

        socket.value.onmessage = (event) => {
            lastMessage.value = event.data
        }

        socket.value.onerror = () => {
            error.value = 'WebSocket 连接错误'
        }

        socket.value.onclose = () => {
            isConnected.value = false
        }
    }

    function send(data: Record<string, unknown>) {
        if (socket.value && isConnected.value) {
            socket.value.send(JSON.stringify(data))
        }
    }

    function disconnect() {
        socket.value?.close()
        socket.value = null
        isConnected.value = false
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