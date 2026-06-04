<template>
    <div class="chat-container">
        <div class="header">
            <h1>MCP AI Agent</h1>
            <span :class="statusClass">{{ connectionStatus }}</span>
        </div>

        <div class="messages" ref="messagesRef">
            <div v-for="(msg, index) in messages" :key="index"
                 :class="['message', msg.type]">
                <strong>{{ msg.type === 'user' ? '你' : 'Agent' }}:</strong>
                <p>{{ msg.content }}</p>
            </div>
        </div>

        <div class="input-area">
            <input v-model="inputMessage"
                   @keyup.enter="sendMessage"
                   placeholder="输入消息... (按 Enter 发送)"
                   :disabled="!isConnected" />
            <button @click="sendMessage" :disabled="!isConnected || !inputMessage.trim()">
                发送
            </button>
        </div>
    </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'

const messages = ref<Array<{ type: 'user' | 'agent', content: string }>>([])
const inputMessage = ref('')
const socket = ref<WebSocket | null>(null)
const isConnected = ref(false)
const connectionStatus = ref('未连接')
const messagesRef = ref<HTMLElement | null>(null)

const connectWebSocket = () => {
  socket.value = new WebSocket('ws://localhost:8080/ws/mcp')

  socket.value.onopen = () => {
    isConnected.value = true
    connectionStatus.value = '已连接'
    addMessage('agent', 'Agent 已上线，可以开始对话了！')
  }

  socket.value.onmessage = (event) => {
    addMessage('agent', event.data)
  }

  socket.value.onclose = () => {
    isConnected.value = false
    connectionStatus.value = '已断开'
  }

  socket.value.onerror = (error) => {
    console.error('WebSocket 错误:', error)
    connectionStatus.value = '连接错误'
  }
}

const sendMessage = () => {
  if (!socket.value || !inputMessage.value.trim() || !isConnected.value) return

  const msg = inputMessage.value.trim()
  addMessage('user', msg)

  socket.value.send(msg)
  inputMessage.value = ''
}

const addMessage = (type: 'user' | 'agent', content: string) => {
  messages.value.push({ type, content })
  // 自动滚动到底部
  setTimeout(() => {
    messagesRef.value?.scrollTo({
      top: messagesRef.value.scrollHeight,
      behavior: 'smooth'
    })
  }, 100)
}

onMounted(() => {
  connectWebSocket()
})

onUnmounted(() => {
  socket.value?.close()
})
</script>

<style scoped>
    .chat-container {
        max-width: 800px;
        margin: 0 auto;
        height: 100vh;
        display: flex;
        flex-direction: column;
        background: #f5f5f5;
    }

    .header {
        padding: 15px;
        background: #2c3e50;
        color: white;
        display: flex;
        justify-content: space-between;
        align-items: center;
    }

    .messages {
        flex: 1;
        overflow-y: auto;
        padding: 20px;
        display: flex;
        flex-direction: column;
        gap: 15px;
    }

    .message {
        max-width: 75%;
        padding: 12px 16px;
        border-radius: 12px;
    }

        .message.user {
            align-self: flex-end;
            background: #3498db;
            color: white;
        }

        .message.agent {
            align-self: flex-start;
            background: white;
            border: 1px solid #ddd;
        }

    .input-area {
        padding: 15px;
        background: white;
        display: flex;
        gap: 10px;
        border-top: 1px solid #ddd;
    }

    input {
        flex: 1;
        padding: 12px;
        border: 1px solid #ddd;
        border-radius: 8px;
        font-size: 16px;
    }

    button {
        padding: 0 24px;
        background: #27ae60;
        color: white;
        border: none;
        border-radius: 8px;
        cursor: pointer;
    }

        button:disabled {
            background: #95a5a6;
        }
</style>