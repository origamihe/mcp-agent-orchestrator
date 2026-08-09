<template>
    <div class="skills-panel">
        <div class="panel-header">
            <h2>技能管理</h2>
            <span class="subtitle">管理 Agent 的工具和生成能力</span>
        </div>

        <div class="skills-grid">
            <div class="skill-card">
                <div class="skill-icon" style="background: rgba(255, 107, 107, 0.1);">
                    <PresentationChartBarIcon class="icon" style="color: #ff6b6b;" />
                </div>
                <div class="skill-info">
                    <span class="skill-name">PPT 生成</span>
                    <span class="skill-desc">基于自然语言描述生成专业演示文稿</span>
                </div>
                <button class="skill-btn" @click="$emit('navigate', 'ppt-generator')">
                    使用
                </button>
            </div>

            <div class="skill-card">
                <div class="skill-icon" style="background: rgba(74, 144, 226, 0.1);">
                    <DocumentTextIcon class="icon" style="color: #4a90e2;" />
                </div>
                <div class="skill-info">
                    <span class="skill-name">Word 文档生成</span>
                    <span class="skill-desc">根据主题生成结构化的 Word 文档</span>
                </div>
                <button class="skill-btn" @click="$emit('navigate', 'docx-generator')">
                    使用
                </button>
            </div>

            <div class="skill-card">
                <div class="skill-icon" style="background: rgba(39, 174, 96, 0.1);">
                    <GlobeAltIcon class="icon" style="color: #27ae60;" />
                </div>
                <div class="skill-info">
                    <span class="skill-name">联网搜索</span>
                    <span class="skill-desc">多源搜索（Google + DDG + Wikipedia）</span>
                </div>
                <button class="skill-btn" @click="$emit('navigate', 'web-search')">
                    使用
                </button>
            </div>

            <div class="skill-card">
                <div class="skill-icon" style="background: rgba(118, 75, 162, 0.1);">
                    <CpuChipIcon class="icon" style="color: #764ba2;" />
                </div>
                <div class="skill-info">
                    <span class="skill-name">角色管理</span>
                    <span class="skill-desc">配置自定义 System Prompt 和角色预设</span>
                </div>
                <button class="skill-btn" @click="$emit('navigate', 'prompt-manager')">
                    管理
                </button>
            </div>

            <div class="skill-card">
                <div class="skill-icon" style="background: rgba(243, 156, 18, 0.1);">
                    <ChatBubbleLeftRightIcon class="icon" style="color: #f39c12;" />
                </div>
                <div class="skill-info">
                    <span class="skill-name">QQ Bot</span>
                    <span class="skill-desc">通过 QQ 群聊/私聊与 Agent 交互</span>
                </div>
                <button class="skill-btn" @click="$emit('navigate', 'qq-bot')">
                    配置
                </button>
            </div>

            <div class="skill-card disabled">
                <div class="skill-icon" style="background: rgba(0,0,0,0.05);">
                    <CodeBracketIcon class="icon" style="color: #999;" />
                </div>
                <div class="skill-info">
                    <span class="skill-name">代码生成</span>
                    <span class="skill-desc">通过 IDE Host 使用（需连接 IDE）</span>
                </div>
                <span class="skill-tag">需 IDE</span>
            </div>
        </div>
    </div>
</template>

<script setup lang="ts">
import {
    PresentationChartBarIcon,
    DocumentTextIcon,
    GlobeAltIcon,
    CpuChipIcon,
    ChatBubbleLeftRightIcon,
    CodeBracketIcon,
} from '@heroicons/vue/24/outline'

defineEmits<{
    (e: 'navigate', feature: string): void
}>()
</script>

<style scoped>
.skills-panel {
    padding: 32px;
    max-width: 900px;
}

.panel-header {
    margin-bottom: 28px;
}

.panel-header h2 {
    font-size: 24px;
    font-weight: 700;
    color: var(--color-text);
}

.subtitle {
    display: block;
    font-size: 13px;
    color: var(--color-text-secondary);
    margin-top: 4px;
}

.skills-grid {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
    gap: 16px;
}

.skill-card {
    background: rgba(255,255,255,0.7);
    backdrop-filter: blur(20px);
    border-radius: 16px;
    padding: 24px;
    border: 1px solid rgba(255,255,255,0.8);
    box-shadow: 0 4px 16px rgba(0,0,0,0.04);
    display: flex;
    flex-direction: column;
    gap: 16px;
    transition: all 0.2s;
}

.skill-card:hover:not(.disabled) {
    box-shadow: 0 6px 24px rgba(102, 126, 234, 0.1);
    transform: translateY(-1px);
}

.skill-card.disabled {
    opacity: 0.5;
}

.skill-icon {
    width: 48px;
    height: 48px;
    border-radius: 14px;
    display: flex;
    align-items: center;
    justify-content: center;
}

.skill-icon .icon {
    width: 24px;
    height: 24px;
}

.skill-info {
    display: flex;
    flex-direction: column;
    gap: 4px;
}

.skill-name {
    font-size: 16px;
    font-weight: 600;
    color: var(--color-text);
}

.skill-desc {
    font-size: 13px;
    color: var(--color-text-secondary);
    line-height: 1.5;
}

.skill-btn {
    padding: 10px;
    background: rgba(102, 126, 234, 0.08);
    border: 1px solid rgba(102, 126, 234, 0.2);
    border-radius: 10px;
    color: #667eea;
    font-size: 13px;
    font-weight: 500;
    cursor: pointer;
    transition: all 0.2s;
}

.skill-btn:hover {
    background: rgba(102, 126, 234, 0.15);
}

.skill-tag {
    font-size: 11px;
    background: rgba(0,0,0,0.05);
    color: #999;
    padding: 4px 12px;
    border-radius: 10px;
    text-align: center;
}
</style>