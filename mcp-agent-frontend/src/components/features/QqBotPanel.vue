<template>
  <div class="qqbot-panel">
    <div class="qqbot-header">
      <div class="qqbot-title-row">
        <BoltIcon class="qqbot-icon" />
        <div>
          <h3>QQ AI Bot</h3>
          <p class="qqbot-subtitle">将 AI Agent 接入 QQ，实现智能自动回复</p>
        </div>
      </div>
      <div class="qqbot-status-badge" :class="botStatusClass">
        <span class="status-dot"></span>
        {{ botStatusText }}
      </div>
    </div>

    <div class="qqbot-body">
      <!-- Bot 配置卡片 -->
      <div class="config-card">
        <h4><Cog6ToothIcon class="section-icon" /> Bot 配置</h4>
        <div class="config-form">
          <div class="form-group">
            <label>QQ 号</label>
            <input v-model="botConfig.qqNumber" placeholder="请输入 Bot 的 QQ 号" />
          </div>
          <div class="form-group">
            <label>OneBot HTTP 地址</label>
            <input v-model="botConfig.onebotUrl" placeholder="例如：http://localhost:3001" />
          </div>
          <div class="form-group">
            <label>Access Token（可选）</label>
            <input v-model="botConfig.accessToken" type="password" placeholder="OneBot 的 access_token" />
          </div>
          <div class="form-row">
            <div class="form-group flex-1">
              <label>回复模式</label>
              <select v-model="botConfig.replyMode">
                <option value="all">回复所有消息</option>
                <option value="at_only">仅回复 @机器人 的消息</option>
                <option value="keyword">仅回复包含关键词的消息</option>
              </select>
            </div>
            <div class="form-group flex-1" v-if="botConfig.replyMode === 'keyword'">
              <label>触发关键词（逗号分隔）</label>
              <input v-model="botConfig.keywords" placeholder="AI, 助手, 小助手" />
            </div>
          </div>
          <div class="form-group">
            <label>系统角色 Prompt</label>
            <textarea
                v-model="botConfig.systemPrompt"
                rows="3"
                placeholder="设定 Bot 的人设和行为，例如：你是一个友好的QQ群助手，名叫小智..."
            ></textarea>
          </div>
          <div class="form-actions">
            <button class="btn-save" @click="saveConfig">💾 保存配置</button>
            <button class="btn-test" @click="testConnection" :disabled="testing">
              {{ testing ? '测试中...' : '🔗 测试连接' }}
            </button>
          </div>
        </div>
      </div>

      <!-- Bot 启停控制 -->
      <div class="control-card">
        <h4><PlayCircleIcon class="section-icon" /> 运行控制</h4>
        <div class="control-row">
          <button
              class="btn-start"
              :disabled="botRunning || !props.isConnected"
              @click="startBot"
          >
            ▶ 启动 Bot
          </button>
          <button
              class="btn-stop"
              :disabled="!botRunning"
              @click="stopBot"
          >
            ⏹ 停止 Bot
          </button>
          <span class="control-hint" v-if="!props.isConnected">
            ⚠️ 请先确保 WebSocket 已连接
          </span>
        </div>
      </div>

      <!-- 消息日志 -->
      <div class="log-card">
        <div class="log-header">
          <h4><QueueListIcon class="section-icon" /> 消息日志</h4>
          <button class="btn-clear" @click="clearLogs">清空</button>
        </div>
        <div class="log-list" ref="logContainerRef">
          <div
              v-for="(log, idx) in messageLogs"
              :key="idx"
              :class="['log-item', log.direction]"
          >
            <div class="log-meta">
              <span class="log-time">{{ formatTime(log.timestamp) }}</span>
              <span class="log-sender">{{ log.sender }}</span>
              <span class="log-group" v-if="log.groupId">群:{{ log.groupId }}</span>
            </div>
            <div class="log-content">{{ log.content }}</div>
            <div class="log-reply" v-if="log.reply">{{ log.reply }}</div>
          </div>
          <div v-if="messageLogs.length === 0" class="log-empty">
            暂无消息记录，启动 Bot 后将在此显示实时消息
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, onUnmounted, nextTick } from 'vue'
import {
  BoltIcon,
  Cog6ToothIcon,
  PlayCircleIcon,
  QueueListIcon,
} from '@heroicons/vue/24/outline'
import http from '@/utils/request.ts'

interface BotConfig {
  qqNumber: string
  onebotUrl: string
  accessToken: string
  replyMode: string
  keywords: string
  systemPrompt: string
}

interface MessageLog {
  timestamp: number
  sender: string
  groupId?: string
  content: string
  direction: 'incoming' | 'outgoing'
  reply?: string
}

const props = defineProps<{
  isConnected: boolean
  selectedModelId?: string
}>()

const emit = defineEmits<{
  (e: 'send-message', payload: any): void
}>()

const botConfig = reactive<BotConfig>({
  qqNumber: '',
  onebotUrl: 'http://localhost:3002',
  accessToken: '',
  replyMode: 'all',
  keywords: '',
  systemPrompt: '你是一个友好、专业的QQ群AI助手。请用简洁、亲切的中文回复用户问题。',
})

const botRunning = ref(false)
const testing = ref(false)
const messageLogs = ref<MessageLog[]>([])
const logContainerRef = ref<HTMLElement | null>(null)

const botStatusClass = computed(() => ({
  'status-online': botRunning.value,
  'status-offline': !botRunning.value,
}))

const botStatusText = computed(() =>
    botRunning.value ? '运行中' : '已停止'
)

function formatTime(ts: number): string {
  const d = new Date(ts)
  return d.toLocaleTimeString('zh-CN', { hour12: false })
}

function scrollLogToBottom() {
  nextTick(() => {
    if (logContainerRef.value) {
      logContainerRef.value.scrollTop = logContainerRef.value.scrollHeight
    }
  })
}

async function saveConfig() {
  try {
    await http.post('/qqbot/config', botConfig)
    addLog('system', '', '配置已保存', 'outgoing')
    alert('配置保存成功！')
  } catch (e) {
    alert('配置保存失败: ' + (e as Error).message)
  }
}

async function testConnection() {
  testing.value = true
  try {
    const res = await http.post('/qqbot/test', {
      onebotUrl: botConfig.onebotUrl,
      accessToken: botConfig.accessToken,
    })
    alert('连接成功！Bot 信息: ' + JSON.stringify(res))
  } catch (e) {
    alert('连接失败: ' + (e as Error).message)
  } finally {
    testing.value = false
  }
}

async function startBot() {
  try {
    await http.post('/qqbot/start', botConfig)
    botRunning.value = true
    addLog('system', '', 'Bot 已启动', 'outgoing')
  } catch (e) {
    alert('启动失败: ' + (e as Error).message)
  }
}

async function stopBot() {
  try {
    await http.post('/qqbot/stop')
    botRunning.value = false
    addLog('system', '', 'Bot 已停止', 'outgoing')
  } catch (e) {
    alert('停止失败: ' + (e as Error).message)
  }
}

function addLog(
    direction: 'incoming' | 'outgoing' | 'system',
    sender: string,
    content: string,
    reply?: string,
    groupId?: string,
) {
  messageLogs.value.push({
    timestamp: Date.now(),
    sender,
    groupId,
    content,
    direction: direction === 'system' ? 'outgoing' : direction,
    reply,
  })
  scrollLogToBottom()
}

function clearLogs() {
  messageLogs.value = []
}

defineExpose({ addMessage: addLog })

async function fetchBotStatus() {
  try {
    const status = await http.get('/qqbot/status')
    botRunning.value = (status as any)?.running ?? false
    if ((status as any)?.config) {
      Object.assign(botConfig, (status as any).config)
    }
  } catch {
    // ignore
  }
}

onMounted(() => {
  fetchBotStatus()
})
</script>

<style scoped>
.qqbot-panel {
  height: 100%;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background: #f8fafc;
}

.qqbot-header {
  padding: 20px 24px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-shrink: 0;
}

.qqbot-title-row {
  display: flex;
  align-items: center;
  gap: 12px;
}

.qqbot-icon {
  width: 36px;
  height: 36px;
  opacity: 0.9;
}

.qqbot-title-row h3 {
  margin: 0;
  font-size: 20px;
  font-weight: 700;
}

.qqbot-subtitle {
  margin: 4px 0 0;
  font-size: 13px;
  opacity: 0.8;
}

.qqbot-status-badge {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 16px;
  border-radius: 20px;
  font-size: 14px;
  font-weight: 600;
}

.status-online {
  background: rgba(255, 255, 255, 0.25);
  backdrop-filter: blur(8px);
}

.status-offline {
  background: rgba(0, 0, 0, 0.2);
}

.status-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
}

.status-online .status-dot {
  background: #4ade80;
  box-shadow: 0 0 8px #4ade80;
  animation: pulse 2s infinite;
}

.status-offline .status-dot {
  background: #94a3b8;
}

@keyframes pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.5; }
}

.qqbot-body {
  flex: 1;
  overflow-y: auto;
  padding: 20px 24px;
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.config-card, .control-card, .log-card {
  background: white;
  border-radius: 16px;
  padding: 20px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.06);
  border: 1px solid #e2e8f0;
}

.config-card h4, .control-card h4, .log-header h4 {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 0 0 16px;
  font-size: 16px;
  color: #1e293b;
}

.section-icon {
  width: 20px;
  height: 20px;
  color: #667eea;
}

.config-form {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.form-group {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.form-group label {
  font-size: 13px;
  font-weight: 600;
  color: #475569;
}

.form-group input,
.form-group select,
.form-group textarea {
  padding: 10px 14px;
  border: 1.5px solid #e2e8f0;
  border-radius: 10px;
  font-size: 14px;
  outline: none;
  transition: border-color 0.2s;
  font-family: inherit;
}

.form-group input:focus,
.form-group select:focus,
.form-group textarea:focus {
  border-color: #667eea;
  box-shadow: 0 0 0 3px rgba(102, 126, 234, 0.1);
}

.form-row {
  display: flex;
  gap: 12px;
}

.flex-1 {
  flex: 1;
}

.form-actions {
  display: flex;
  gap: 10px;
  margin-top: 4px;
}

.btn-save, .btn-test {
  padding: 10px 20px;
  border: none;
  border-radius: 10px;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s;
}

.btn-save {
  background: linear-gradient(135deg, #667eea, #764ba2);
  color: white;
}

.btn-save:hover {
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(102, 126, 234, 0.4);
}

.btn-test {
  background: #f1f5f9;
  color: #475569;
}

.btn-test:hover {
  background: #e2e8f0;
}

.btn-test:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.control-row {
  display: flex;
  align-items: center;
  gap: 12px;
}

.btn-start, .btn-stop {
  padding: 10px 24px;
  border: none;
  border-radius: 10px;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s;
}

.btn-start {
  background: #10b981;
  color: white;
}

.btn-start:hover:not(:disabled) {
  background: #059669;
  transform: translateY(-1px);
}

.btn-stop {
  background: #ef4444;
  color: white;
}

.btn-stop:hover:not(:disabled) {
  background: #dc2626;
}

.btn-start:disabled, .btn-stop:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.control-hint {
  font-size: 13px;
  color: #f59e0b;
}

.log-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.log-header h4 {
  margin: 0;
}

.btn-clear {
  padding: 6px 14px;
  background: #f1f5f9;
  border: none;
  border-radius: 8px;
  font-size: 13px;
  cursor: pointer;
  color: #64748b;
}

.btn-clear:hover {
  background: #e2e8f0;
}

.log-list {
  max-height: 400px;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-top: 12px;
}

.log-item {
  padding: 10px 14px;
  border-radius: 10px;
  font-size: 13px;
}

.log-item.incoming {
  background: #f0f9ff;
  border-left: 3px solid #3b82f6;
}

.log-item.outgoing {
  background: #f0fdf4;
  border-left: 3px solid #10b981;
}

.log-meta {
  display: flex;
  gap: 10px;
  font-size: 11px;
  color: #94a3b8;
  margin-bottom: 4px;
}

.log-sender {
  font-weight: 600;
  color: #475569;
}

.log-content {
  color: #1e293b;
  word-break: break-all;
}

.log-reply {
  margin-top: 6px;
  padding: 8px 12px;
  background: #dbeafe;
  border-radius: 8px;
  color: #1e40af;
  font-size: 12px;
}

.log-empty {
  text-align: center;
  padding: 40px;
  color: #94a3b8;
  font-size: 14px;
}
</style>