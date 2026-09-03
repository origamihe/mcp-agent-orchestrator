# mcp-agent-frontend

MCP Agent Console — Agent Management & Operations Console 前端

> **Chat is a View. Agent is the Object. Run is the Execution Unit. Policy is the Boundary.**

基于 Vue.js 3 + TypeScript + Pinia 构建的 MCP Agent 管理与运行控制台，提供 Agent 生命周期管理、实时监控、安全策略治理等能力。

## 架构文档

- [架构工作流与数据流](./docs/architecture-workflow.md) — 前端内部架构、组件工作流、状态管理和与 Gateway 的交互模式
- [产品重定位与改进路线图](./docs/product-redesign.md) — 从 "AI Chat UI" 到 "Agent Management & Operations Console" 的转型路线

## 技术栈

| 技术 | 版本 | 说明 |
|------|------|------|
| Vue | 3.5 | 前端框架 (Composition API + `<script setup>`) |
| TypeScript | 5.x | 类型安全 |
| Vite | 6.x | 构建工具 |
| Pinia | 3.0 | 状态管理 |
| Vue Router | 4.6 | SPA 路由 |
| Axios | 1.9 | HTTP 客户端 |
| SASS/SCSS | 1.89 | CSS 预处理 |
| Heroicons Vue | — | 图标库 |

## 项目结构

```
mcp-agent-frontend/
├── src/
│   ├── api/              # API 模块 (9 个)
│   │   ├── agents.ts     # Agent CRUD、Config、Prompt、Memory、Tools、Runs、Permissions
│   │   ├── memory.ts     # 记忆 API
│   │   ├── runs.ts       # 执行记录 API
│   │   ├── tools.ts      # 工具 API
│   │   ├── policies.ts   # 安全策略 API
│   │   ├── knowledge.ts  # 知识库 API
│   │   ├── hosts.ts      # 宿主 API
│   │   ├── sessions.ts   # 会话 API
│   │   ├── logs.ts       # 日志 API
│   │   └── client.ts     # Axios 实例 (Auth Token 注入)
│   ├── components/
│   │   ├── common/        # 通用组件 (7 个)
│   │   │   ├── Sidebar.vue         # 侧边栏导航
│   │   │   ├── StatusBar.vue       # 顶部状态栏
│   │   │   ├── StatusBadge.vue     # 状态标签
│   │   │   ├── RiskBadge.vue       # 风险等级标签
│   │   │   ├── EmptyState.vue      # 空状态展示
│   │   │   ├── LoadingSpinner.vue  # 加载动画
│   │   │   └── ToastContainer.vue  # Toast 通知容器
│   │   ├── LogCharts.vue           # 日志可视化图表
│   │   └── LogDetailModal.vue      # 日志详情弹窗
│   ├── composables/       # 组合式 API
│   │   ├── useWebSocket.ts    # WebSocket 连接管理 (心跳 + 重连 + 消息队列)
│   │   ├── useLogStream.ts    # 日志 WebSocket 实时流
│   │   ├── useLogExport.ts    # 日志导出
│   │   ├── useToast.ts        # Toast 通知
│   │   ├── useChatHistory.ts  # 聊天历史
│   │   └── useAgentTask.ts    # Agent 任务管理
│   ├── pages/             # 页面 (15 个路由页面)
│   │   ├── DashboardPage.vue     # 仪表盘
│   │   ├── AgentListPage.vue     # Agent 列表
│   │   ├── AgentDetailPage.vue   # Agent 详情 (7 Tab)
│   │   ├── AgentWorkspace.vue    # Agent 工作台 + Chat
│   │   ├── MemoryPage.vue        # 记忆管理
│   │   ├── KnowledgePage.vue     # 知识库管理
│   │   ├── ToolsPage.vue         # 工具管理
│   │   ├── PoliciesPage.vue      # 安全策略
│   │   ├── RunsPage.vue          # 执行历史
│   │   ├── RunDetailPage.vue     # Run 详情
│   │   ├── HostsPage.vue         # 宿主管理
│   │   ├── SessionsPage.vue      # 会话管理
│   │   ├── LogsPage.vue          # 系统日志
│   │   ├── SettingsPage.vue      # 系统配置
│   │   └── NotFoundPage.vue      # 404 页面
│   ├── router/
│   │   └── index.ts       # 路由配置 (15 条路由, 懒加载)
│   ├── stores/            # Pinia 状态管理 (11 个 Store)
│   │   ├── agentStore.ts      # Agent CRUD、列表、详情
│   │   ├── appStore.ts        # 应用全局状态、模型列表
│   │   ├── memoryStore.ts     # 记忆 CRUD、搜索
│   │   ├── runStore.ts        # 执行历史、Run 详情
│   │   ├── toolStore.ts       # 工具列表、风险等级
│   │   ├── policyStore.ts     # 安全策略矩阵
│   │   ├── knowledgeStore.ts  # 知识库、Collection
│   │   ├── hostStore.ts       # 宿主列表、状态
│   │   ├── sessionStore.ts    # 会话管理
│   │   ├── logStore.ts        # 日志查看、筛选
│   │   └── dashboardStore.ts  # 仪表盘数据、系统概览
│   ├── types/             # TypeScript 类型定义 (14 个)
│   │   ├── agent.ts, agent-config.ts
│   │   ├── memory.ts, run.ts, tool.ts, policy.ts
│   │   ├── knowledge.ts, host.ts, session.ts, log.ts
│   │   ├── dashboard.ts, llm.ts, document.ts, api.ts
│   │   └── index.ts
│   ├── App.vue            # 根组件
│   └── main.ts            # 应用入口
├── docs/
│   ├── architecture-workflow.md
│   └── product-redesign.md
├── index.html
├── vite.config.ts
├── tsconfig.json
└── package.json
```

## 页面路由

| 路由 | 页面组件 | 功能 |
|------|---------|------|
| `/` | DashboardPage | 系统健康状态、Agent 活跃概览、最近 Runs、Runtime Health |
| `/agents` | AgentListPage | Agent 卡片列表 |
| `/agents/:id` | AgentDetailPage | Agent 详情 (Overview / Config / Prompt / Memory / Tools / Runs / Permissions) |
| `/workspace/:agentId` | AgentWorkspace | Agent 工作台 + Chat 视图 |
| `/memory` | MemoryPage | 记忆 CRUD、类型筛选 |
| `/knowledge` | KnowledgePage | Collection 管理、文档上传 |
| `/tools` | ToolsPage | 工具列表、风险等级展示 |
| `/policies` | PoliciesPage | Policy 矩阵、Policy 详情 |
| `/runs` | RunsPage | 执行历史列表 |
| `/runs/:id` | RunDetailPage | Trace、Tool Calls、Token Usage |
| `/hosts` | HostsPage | Host 列表、详情 |
| `/sessions` | SessionsPage | 会话列表、状态筛选 |
| `/logs` | LogsPage | 事件日志、文件日志、实时流、可视化、导出 |
| `/settings` | SettingsPage | Models、Providers、System Config |
| `*` | NotFoundPage | 404 |

## 通信层

| 端点 | 协议 | 用途 |
|------|------|------|
| `ws://localhost:8080/ws/mcp` | WebSocket | 实时消息推送 |
| `ws://localhost:8080/ws/host` | WebSocket | Host 连接 |
| `ws://localhost:8080/ws/logs` | WebSocket | 实时日志流 |
| `http://localhost:8080/api/*` | HTTP REST | CRUD 操作 |

## 环境要求

- **Node.js**: 20.19+ 或 22.12+
- 后端服务需要运行在 `http://localhost:8080`

## 快速开始

```sh
# 安装依赖
npm install

# 启动开发服务器 (http://localhost:5173)
npm run dev

# 类型检查
npm run type-check

# 生产构建
npm run build
```

## 与后端的对应关系

前端通过 REST API 与后端 Controller 交互，具体映射关系详见 [架构工作流文档 §九](./docs/architecture-workflow.md#九前端-api-后端-controller-映射表-phase-7-完成)。

## 跨模块关联

- 后端文档：[mcp-agent-orchestrator/docs/](../mcp-agent-orchestrator/docs/)
- Rider 插件文档：[rider-mcp-plugin/docs/](../rider-mcp-plugin/docs/)
- 跨模块安全审计：[rider-mcp-plugin 跨模块安全审计](../rider-mcp-plugin/docs/audit-report.md)