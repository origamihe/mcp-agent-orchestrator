<template>
  <div class="prompt-manager">
    <div class="pm-header">
      <h3><UserGroupIcon class="pm-title-icon" /> 角色管理</h3>
      <p class="pm-desc">创建和管理 Agent 角色（System Prompt），让 Agent 扮演不同身份</p>
    </div>

    <div class="pm-body">
      <!-- 左侧：创建/编辑表单 -->
      <div class="pm-create">
        <h4><component :is="editing ? PencilSquareIcon : PlusCircleIcon" class="pm-section-icon" /> {{ editing ? '编辑角色' : '创建新角色' }}</h4>
        <div class="pm-form">
          <div class="form-group">
            <label>角色名称</label>
            <input
              v-model="form.name"
              :disabled="!!editing"
              :class="{ readonly: !!editing }"
              placeholder="例如：python_expert"
            />
          </div>
          <div class="form-row">
            <div class="form-group flex-1">
              <label>类型</label>
              <select v-model="form.type">
                <option value="AGENT_SPECIFIC">特定 Agent</option>
                <option value="SYSTEM">系统提示</option>
                <option value="TASK">任务提示</option>
                <option value="TOOL_CALLING">工具调用</option>
                <option value="SUMMARY">摘要</option>
              </select>
            </div>
          </div>
          <div class="form-group">
            <label>描述（可选）</label>
            <input v-model="form.description" placeholder="简短描述这个角色的用途" />
          </div>
          <div class="form-group">
            <label>System Prompt</label>
            <textarea
                v-model="form.templateText"
                placeholder="输入 System Prompt，例如：你是一个 Python 编程专家..."
                rows="5"
            ></textarea>
          </div>
          <div style="display:flex;gap:10px;flex-wrap:wrap;">
            <button @click="handleSave" :disabled="!form.name.trim() || !form.templateText.trim() || saving">
              {{ saving ? '保存中...' : editing ? '更新角色' : '保存角色' }}
            </button>
            <button v-if="editing" class="pm-btn-cancel" @click="cancelEdit">取消编辑</button>
          </div>
          <p v-if="createMsg" :class="['create-msg', createOk ? 'success' : 'error']">{{ createMsg }}</p>
        </div>
      </div>

      <!-- 右侧：已有角色卡片列表 -->
      <div class="pm-list">
        <div class="pm-list-toolbar">
          <h4>已有角色 ({{ prompts.length }})</h4>
          <span v-if="selectedRole" class="pm-selected-badge">已选：{{ selectedRole }}</span>
        </div>
        <div v-if="loading" class="pm-empty"><ArrowPathIcon class="pm-empty-icon animate-spin" /> 加载中...</div>
        <div v-else-if="createMsg && prompts.length === 0" class="pm-empty" style="color:#ff3b30">{{ createMsg }}</div>
        <div v-else-if="prompts.length === 0" class="pm-empty">暂无角色，请创建一个</div>
        <div class="pm-card-grid">
          <div
            v-for="p in prompts"
            :key="p.name"
            :class="['pm-card', { 'pm-card-selected': selectedRole === p.name, 'pm-card-expanded': expandedCards.has(p.name) }]"
          >
            <div class="pm-card-header" @click="toggleExpand(p.name)">
              <div class="pm-card-header-left">
                <span class="pm-card-radio" @click.stop="selectRole(p.name)">
                  <span v-if="selectedRole === p.name" class="pm-radio-dot"></span>
                </span>
                <span class="pm-card-name">{{ p.name }}</span>
                <span class="pm-card-type">{{ typeLabel(p.type) }}</span>
              </div>
              <component :is="expandedCards.has(p.name) ? ChevronUpIcon : ChevronDownIcon" class="pm-chevron-icon" />
            </div>
            <div class="pm-card-body" v-show="expandedCards.has(p.name)">
              <p class="pm-card-desc" v-if="p.description">{{ p.description }}</p>
              <pre class="pm-card-text">{{ p.templateText }}</pre>
              <div class="pm-card-actions">
                <button class="pm-btn-select" :class="{ active: selectedRole === p.name }" @click="selectRole(p.name)">
                  {{ selectedRole === p.name ? '✓ 已选择' : '选择此角色' }}
                </button>
                <button class="pm-btn-edit" @click="startEdit(p)"><PencilSquareIcon class="pm-btn-icon" /> 编辑</button>
                <button class="pm-delete" @click="handleDelete(p.name)"><TrashIcon class="pm-btn-icon" /> 删除</button>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import type { PromptInfo } from '@/types/agent'
import http from '@/utils/request'
import { UserGroupIcon, PencilSquareIcon, PlusCircleIcon, ArrowPathIcon, TrashIcon, ChevronUpIcon, ChevronDownIcon } from '@heroicons/vue/24/outline'

const prompts = ref<PromptInfo[]>([])
const saving = ref(false)
const loading = ref(false)
const createMsg = ref('')
const createOk = ref(false)
const editing = ref<PromptInfo | null>(null)
const selectedRole = ref('')
const expandedCards = ref(new Set<string>())

const form = ref({
  name: '',
  type: 'AGENT_SPECIFIC',
  description: '',
  templateText: '',
})

const typeLabel = (t: string) =>
    ({ system: '系统', task: '任务', agent_specific: '角色', tool_calling: '工具', summary: '摘要' })[t] || t

async function fetchPrompts() {
  loading.value = true
  try {
    const res = await http.get('/mcp/prompts')
    if (Array.isArray(res)) {
      prompts.value = (res ?? []).filter((p: PromptInfo) => p.type === 'agent_specific')
    } else {
      console.warn('Unexpected response type:', typeof res, res)
      prompts.value = []
}
  } catch (e: any) {
    console.error('获取角色列表失败:', e?.message || e)
    createOk.value = false
    createMsg.value = '无法加载角色列表，请确认后端服务已启动'
  } finally {
    loading.value = false
  }
}

function toggleExpand(name: string) {
  const next = new Set(expandedCards.value)
  if (next.has(name)) {
    next.delete(name)
  } else {
    next.add(name)
  }
  expandedCards.value = next
}

function selectRole(name: string) {
  selectedRole.value = selectedRole.value === name ? '' : name
}

function startEdit(p: PromptInfo) {
  editing.value = p
  form.value = { name: p.name, type: p.type, description: p.description || '', templateText: p.templateText }
  createMsg.value = ''
}

function cancelEdit() {
  editing.value = null
  form.value = { name: '', type: 'AGENT_SPECIFIC', description: '', templateText: '' }
  createMsg.value = ''
}

async function handleSave() {
  saving.value = true
  createMsg.value = ''
  try {
    await http.post('/mcp/prompts', {
      name: form.value.name.trim(),
      type: form.value.type,
      templateText: form.value.templateText.trim(),
      description: form.value.description.trim(),
    })
    createOk.value = true
    createMsg.value = editing.value ? `角色「${form.value.name}」已更新` : `角色「${form.value.name}」创建成功！`
    editing.value = null
    form.value = { name: '', type: 'AGENT_SPECIFIC', description: '', templateText: '' }
    await fetchPrompts()
  } catch (e: any) {
    createOk.value = false
    createMsg.value = e?.response?.data?.message || '保存失败'
  } finally {
    saving.value = false
  }
}

async function handleDelete(name: string) {
  if (!confirm(`确定删除角色「${name}」吗？`)) return
  try {
    await http.delete(`/mcp/prompts/${encodeURIComponent(name)}`)
    await fetchPrompts()
  } catch {
    console.error('删除失败')
  }
}

onMounted(fetchPrompts)
</script>

<style scoped>
.prompt-manager {
  padding: 36px 40px;
  height: 100%;
  overflow-y: auto;
  background: linear-gradient(135deg, #f8f9fc 0%, #eef1f9 50%, #f5f0fc 100%);
}

/* ── Header ── */
.pm-header {
  margin-bottom: 32px;
  text-align: center;
}
.pm-header h3 {
  font-size: 26px;
  font-weight: 700;
  margin-bottom: 8px;
  background: var(--gradient-dream);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
}
.pm-title-icon {
  color: #667eea;
  flex-shrink: 0;
  width: 28px;
  height: 28px;
}

.pm-section-icon {
  width: 20px;
  height: 20px;
  flex-shrink: 0;
}

.pm-empty-icon {
  width: 20px;
  height: 20px;
  flex-shrink: 0;
}

.pm-btn-icon {
  width: 16px;
  height: 16px;
  flex-shrink: 0;
}
.pm-desc {
  color: var(--color-text-secondary, #6b7280);
  font-size: 14px;
  line-height: 1.6;
}

/* ── Section Title ── */
.pm-create h4, .pm-list h4 {
  font-size: 18px;
  font-weight: 600;
  margin-bottom: 16px;
  color: var(--color-text, #1d1d1f);
  display: flex;
  align-items: center;
  gap: 10px;
}

/* ── Form Card ── */
.pm-form {
  display: flex;
  flex-direction: column;
  gap: 16px;
  background: rgba(255,255,255,0.7);
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
  border: 1px solid rgba(255,255,255,0.8);
  border-radius: 20px;
  padding: 28px;
  box-shadow: 0 8px 32px rgba(106,133,255,0.08), 0 2px 8px rgba(0,0,0,0.04);
}

.form-group {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.form-group label {
  font-weight: 600;
  font-size: 13px;
  color: #4b5563;
  letter-spacing: 0.3px;
}
.form-group input,
.form-group select,
.form-group textarea {
  padding: 12px 16px;
  border: 1.5px solid #e5e7eb;
  border-radius: 14px;
  font-size: 14px;
  outline: none;
  font-family: inherit;
  background: rgba(255,255,255,0.85);
  transition: all 0.25s ease;
}
.form-group input:hover,
.form-group select:hover,
.form-group textarea:hover {
  border-color: #c4b5fd;
}
.form-group input:focus,
.form-group textarea:focus,
.form-group select:focus {
  border-color: #6a85ff;
  box-shadow: 0 0 0 4px rgba(106,133,255,0.12);
  background: #fff;
}
.form-row { display: flex; gap: 12px; }
.flex-1 { flex: 1; }

/* ── Buttons ── */
.pm-form button {
  align-self: flex-start;
  padding: 12px 32px;
  background: linear-gradient(135deg, #6a85ff, #b163e8);
  color: #fff;
  border: none;
  border-radius: 14px;
  cursor: pointer;
  font-size: 15px;
  font-weight: 600;
  letter-spacing: 0.5px;
  transition: all 0.3s ease;
  box-shadow: 0 4px 16px rgba(106,133,255,0.25);
}
.pm-form button:hover:not(:disabled) {
  transform: translateY(-2px);
  box-shadow: 0 8px 28px rgba(106,133,255,0.4);
}
.pm-form button:active:not(:disabled) {
  transform: translateY(0);
}
.pm-form button:disabled {
  opacity: 0.45;
  cursor: not-allowed;
  transform: none;
  box-shadow: none;
}

/* ── Message ── */
.create-msg {
  font-size: 13px;
  margin: 0;
  padding: 8px 16px;
  border-radius: 10px;
  font-weight: 500;
}
.create-msg.success {
  color: #059669;
  background: rgba(5,150,105,0.08);
}
.create-msg.error {
  color: #dc2626;
  background: rgba(220,38,38,0.06);
}

/* ── Cancel Button ── */
.pm-btn-cancel {
  padding: 12px 28px;
  background: rgba(255,255,255,0.6) !important;
  backdrop-filter: blur(10px);
  color: #6b7280 !important;
  border: 1.5px solid #e5e7eb !important;
  border-radius: 14px;
  cursor: pointer;
  font-size: 14px;
  font-weight: 500;
  transition: all 0.25s ease;
}
.pm-btn-cancel:hover {
  background: rgba(255,255,255,0.9) !important;
  border-color: #c4b5fd !important;
  transform: translateY(-1px);
}

/* ── Two-Column Body ── */
.pm-body {
  display: flex;
  gap: 32px;
  align-items: flex-start;
}

.pm-create {
  flex: 0 0 420px;
  max-width: 420px;
}

.pm-list {
  flex: 1;
  min-width: 0;
}

/* ── List Toolbar ── */
.pm-list-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
  flex-wrap: wrap;
  gap: 10px;
}

.pm-selected-badge {
  font-size: 12px;
  padding: 4px 14px;
  border-radius: 9999px;
  background: linear-gradient(135deg, #667eea, #b163e8);
  color: #fff;
  font-weight: 600;
  letter-spacing: 0.3px;
}
.pm-empty {
  color: #9ca3af;
  font-size: 15px;
  padding: 48px 20px;
  text-align: center;
  background: rgba(255,255,255,0.6);
  border-radius: 20px;
  backdrop-filter: blur(10px);
  border: 1.5px dashed #e5e7eb;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
}

/* ── Card ── */
.pm-card {
  background: rgba(255,255,255,0.72);
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
  border-radius: 20px;
  padding: 22px 24px;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  border: 1.5px solid rgba(255,255,255,0.8);
  box-shadow: 0 4px 20px rgba(106,133,255,0.06), 0 1px 4px rgba(0,0,0,0.03);
}
.pm-card:hover {
  border-color: #b163e8;
  box-shadow: 0 12px 36px rgba(106,133,255,0.12), 0 4px 12px rgba(177,99,232,0.1);
  transform: translateY(-3px);
  background: rgba(255,255,255,0.88);
}

.pm-card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  cursor: pointer;
  user-select: none;
}

.pm-card-header-left {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
  flex: 1;
}
.pm-card-name {
  font-weight: 700;
  font-size: 16px;
  color: #1f2937;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.pm-card-type {
  font-size: 11px;
  padding: 3px 12px;
  border-radius: 9999px;
  background: var(--gradient-dream);
  color: #fff;
  font-weight: 600;
  letter-spacing: 0.3px;
}
.pm-card-desc {
  font-size: 13px;
  color: #6b7280;
  margin-bottom: 10px;
  line-height: 1.6;
}
.pm-card-text {
  font-size: 12px;
  color: #6b7280;
  white-space: pre-wrap;
  background: rgba(248,249,252,0.7);
  padding: 12px 14px;
  border-radius: 12px;
  margin-bottom: 14px;
  max-height: 100px;
  overflow-y: auto;
  line-height: 1.6;
  border: 1px solid #f1f3f9;
}

/* ── Custom Radio ── */
.pm-card-radio {
  width: 20px;
  height: 20px;
  border-radius: 50%;
  border: 2px solid #d1d5db;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.25s ease;
}
.pm-card:hover .pm-card-radio {
  border-color: #b163e8;
}
.pm-card-selected .pm-card-radio {
  border-color: #667eea;
  background: linear-gradient(135deg, #667eea, #b163e8);
}
.pm-radio-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #fff;
}

/* ── Chevron ── */
.pm-chevron-icon {
  width: 18px;
  height: 18px;
  flex-shrink: 0;
  color: #9ca3af;
  transition: transform 0.3s ease;
}
.pm-card:hover .pm-chevron-icon {
  color: #667eea;
}

/* ── Card Body (Collapsible) ── */
.pm-card-body {
  margin-top: 14px;
  padding-top: 14px;
  border-top: 1px solid #f1f3f9;
}

/* ── Card Grid ── */
.pm-card-grid {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

/* ── Selected State ── */
.pm-card-selected {
  border-color: #667eea !important;
  box-shadow: 0 4px 20px rgba(106,133,255,0.15), 0 0 0 1px rgba(106,133,255,0.3) !important;
  background: rgba(255,255,255,0.92) !important;
}

/* ── Select Button ── */
.pm-btn-select {
  font-size: 13px;
  padding: 6px 16px;
  background: rgba(255,255,255,0.6);
  backdrop-filter: blur(10px);
  color: #667eea;
  border: 1.5px solid #c4b5fd;
  border-radius: 10px;
  cursor: pointer;
  font-weight: 500;
  transition: all 0.25s ease;
  display: inline-flex;
  align-items: center;
  gap: 4px;
}
.pm-btn-select:hover {
  background: rgba(106,133,255,0.08);
  border-color: #667eea;
}
.pm-btn-select.active {
  background: linear-gradient(135deg, #667eea, #b163e8);
  color: #fff;
  border-color: transparent;
}

/* ── Card Actions ── */
.pm-card-actions {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}
.pm-btn-edit {
  font-size: 13px;
  padding: 6px 18px;
  background: linear-gradient(135deg, #6a85ff, #8b5cf6);
  color: #fff;
  border: none;
  border-radius: 10px;
  cursor: pointer;
  font-weight: 500;
  transition: all 0.25s ease;
  box-shadow: 0 2px 8px rgba(106,133,255,0.2);
  display: inline-flex;
  align-items: center;
  gap: 6px;
}
.pm-btn-edit:hover {
  transform: translateY(-1px);
  box-shadow: 0 4px 16px rgba(106,133,255,0.35);
}
.pm-delete {
  font-size: 13px;
  padding: 6px 16px;
  background: rgba(255,255,255,0.6);
  backdrop-filter: blur(10px);
  color: #ef4444;
  border: 1.5px solid #fecaca;
  border-radius: 10px;
  cursor: pointer;
  font-weight: 500;
  transition: all 0.25s ease;
  display: inline-flex;
  align-items: center;
  gap: 6px;
}
.pm-delete:hover {
  background: #fef2f2;
  border-color: #ef4444;
  transform: translateY(-1px);
}

/* ── Readonly Input ── */
.form-group input.readonly {
  background: rgba(248,249,252,0.7);
  color: #9ca3af;
  cursor: not-allowed;
  border-color: #e5e7eb;
}
</style>