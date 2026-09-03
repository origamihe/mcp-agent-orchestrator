<template>
    <div class="charts-grid">
        <div class="chart-card">
            <h3 class="chart-title">日志级别分布</h3>
            <div class="chart-body">
                <svg :viewBox="`0 0 ${levelChartWidth} ${levelChartHeight}`" class="chart-svg">
                    <g v-for="(bar, idx) in levelBars" :key="idx">
                        <rect
                            :x="bar.x"
                            :y="bar.y"
                            :width="bar.width"
                            :height="barHeight"
                            :rx="4"
                            :fill="bar.color"
                            class="chart-bar"
                        />
                        <text
                            :x="bar.x + bar.width + 6"
                            :y="bar.y + barHeight / 2 + 4"
                            class="chart-label"
                            fill="#666"
                            font-size="11"
                        >{{ bar.label }} ({{ bar.count }})</text>
                    </g>
                </svg>
            </div>
        </div>

        <div class="chart-card">
            <h3 class="chart-title">模块分布</h3>
            <div class="chart-body">
                <svg :viewBox="`0 0 ${moduleChartWidth} ${moduleChartHeight}`" class="chart-svg">
                    <g v-for="(bar, idx) in moduleBars" :key="idx">
                        <rect
                            :x="bar.x"
                            :y="bar.y"
                            :width="bar.width"
                            :height="barHeight"
                            :rx="4"
                            :fill="bar.color"
                            class="chart-bar"
                        />
                        <text
                            :x="bar.x + bar.width + 6"
                            :y="bar.y + barHeight / 2 + 4"
                            class="chart-label"
                            fill="#666"
                            font-size="11"
                        >{{ bar.label }} ({{ bar.count }})</text>
                    </g>
                </svg>
            </div>
        </div>

        <div class="chart-card chart-card-wide">
            <h3 class="chart-title">最近错误</h3>
            <div class="error-list">
                <div v-for="(err, idx) in recentErrors" :key="idx" class="error-item">
                    <span class="error-time">{{ formatTime(err.timestamp) }}</span>
                    <span class="level-badge level-error">ERROR</span>
                    <span class="error-message">{{ err.message }}</span>
                </div>
                <div v-if="recentErrors.length === 0" class="empty-chart">暂无错误</div>
            </div>
        </div>
    </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useLogStore } from '@/stores/logStore'

const logStore = useLogStore()

const levelChartWidth = 300
const levelChartHeight = 160
const barHeight = 24
const maxBarWidth = 180

const moduleChartWidth = 300
const moduleChartHeight = 200

const levelColors: Record<string, string> = {
    error: '#e74c3c',
    warn: '#e67e22',
    info: '#2980b9',
    debug: '#95a5a6',
    audit: '#667eea',
}

const moduleColors = ['#667eea', '#2980b9', '#27ae60', '#e67e22', '#e74c3c', '#8e44ad', '#2c3e50']

const levelBars = computed(() => {
    const counts = logStore.statistics?.levelCounts || {}
    const levels = ['error', 'warn', 'info', 'debug', 'audit']
    const maxCount = Math.max(1, ...levels.map(l => counts[l] || 0))
    const gap = 4

    return levels.map((level, idx) => {
        const count = counts[level] || 0
        const width = Math.max(4, (count / maxCount) * maxBarWidth)
        const y = idx * (barHeight + gap)
        return {
            x: 0,
            y,
            width,
            label: level.toUpperCase(),
            count,
            color: levelColors[level] || '#999',
        }
    })
})

const moduleBars = computed(() => {
    const counts = logStore.statistics?.moduleCounts || {}
    const entries = Object.entries(counts)
        .sort((a, b) => b[1] - a[1])
        .slice(0, 7)
    const maxCount = Math.max(1, ...entries.map(([, c]) => c))
    const gap = 4

    return entries.map(([name, count], idx) => {
        const width = Math.max(4, (count / maxCount) * maxBarWidth)
        const y = idx * (barHeight + gap)
        const shortName = name.length > 20 ? name.substring(0, 20) + '...' : name
        return {
            x: 0,
            y,
            width,
            label: shortName,
            count,
            color: moduleColors[idx % moduleColors.length],
        }
    })
})

const recentErrors = computed(() => {
    return logStore.statistics?.recentErrors || []
})

function formatTime(dateStr: string): string {
    if (!dateStr) return '--'
    return new Date(dateStr).toLocaleString('zh-CN', {
        month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit',
    })
}
</script>

<style scoped>
.charts-grid {
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: 16px;
    margin-bottom: 20px;
}

.chart-card {
    background: rgba(255,255,255,0.7);
    backdrop-filter: blur(20px);
    border-radius: 14px;
    border: 1px solid rgba(255,255,255,0.8);
    padding: 16px 20px;
    box-shadow: 0 2px 12px rgba(0,0,0,0.03);
}

.chart-card-wide {
    grid-column: 1 / -1;
}

.chart-title {
    font-size: 13px;
    font-weight: 600;
    color: #333;
    margin-bottom: 12px;
}

.chart-body {
    overflow: hidden;
}

.chart-svg {
    width: 100%;
    height: auto;
}

.chart-bar {
    transition: width 0.4s ease;
}

.chart-label {
    font-family: monospace;
}

.error-list {
    display: flex;
    flex-direction: column;
    gap: 6px;
    max-height: 200px;
    overflow-y: auto;
}

.error-item {
    display: flex;
    align-items: center;
    gap: 10px;
    padding: 6px 10px;
    background: rgba(231, 76, 60, 0.04);
    border-radius: 8px;
    font-size: 12px;
}

.error-time {
    font-family: monospace;
    color: #999;
    white-space: nowrap;
    min-width: 120px;
}

.error-message {
    color: #555;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
    flex: 1;
}

.empty-chart {
    text-align: center;
    padding: 20px;
    color: #999;
    font-size: 13px;
}

.level-badge {
    padding: 2px 8px;
    border-radius: 12px;
    font-size: 10px;
    font-weight: 600;
    font-family: monospace;
    white-space: nowrap;
}

.level-error { background: #ffebee; color: #c62828; }
</style>