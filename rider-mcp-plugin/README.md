# rider-mcp-plugin

Rider MCP Plugin — IntelliJ IDEA / Rider 平台的 MCP 宿主插件

> **Agent Host Runtime — 将 LLM/Agent 的推理结果转化为 IDE 宿主机的实际操作**

基于 Kotlin + IntelliJ Platform SDK 构建的 IDE 插件，作为 MCP Agent 系统的宿主端，通过 WebSocket 与 Gateway 通信，执行 Agent 请求的能力调用（文件读写、终端命令、代码诊断等）。

## 架构文档

- [架构工作流与信任边界](./docs/architecture-workflow.md) — 系统全景图、信任边界分析、核心工作流时序图、Capability 风险等级映射、路径安全架构
- [跨模块安全审计](./docs/audit-report.md) — Agent → WebSocket → Plugin → IDE/OS 信任边界审计，10 维度验证，P0-P3 风险分类
- [安全审计发现](./docs/code-review-findings.md) — 代码级审计发现与修复详情

## 技术栈

| 技术 | 说明 |
|------|------|
| Kotlin | 开发语言 |
| IntelliJ Platform SDK | IDE 插件框架 |
| Gradle | 构建工具 |
| WebSocket | 与 Gateway 实时通信 |
| Gson | JSON 序列化 |

## 项目结构

```
rider-mcp-plugin/
├── src/
│   ├── main/
│   │   ├── kotlin/com/mcp/plugin/
│   │   │   ├── capability/
│   │   │   │   ├── Capability.kt         # 13 个能力定义
│   │   │   │   └── CapabilityAdapter.kt  # 能力执行适配器
│   │   │   ├── diff/
│   │   │   │   └── DiffApplier.kt        # Diff 应用器
│   │   │   ├── event/
│   │   │   │   ├── IdeEvent.kt           # IDE 事件定义
│   │   │   │   └── IdeEventBus.kt        # IDE 事件总线
│   │   │   ├── prompt/
│   │   │   │   └── PromptRegistry.kt     # Prompt 注册中心
│   │   │   ├── settings/
│   │   │   │   └── McpPluginSettingsConfigurable.kt  # 配置 UI
│   │   │   ├── toolwindow/
│   │   │   │   ├── ChatPanel.kt          # 聊天面板
│   │   │   │   └── ChatToolWindowFactory.kt
│   │   │   ├── transport/
│   │   │   │   ├── Transport.kt          # 传输层接口
│   │   │   │   └── WebSocketTransport.kt # WebSocket 传输实现
│   │   │   ├── util/
│   │   │   │   ├── LanguageDetector.kt   # 语言检测 (18 种语言)
│   │   │   │   └── PathValidator.kt      # 路径安全校验 (统一入口)
│   │   │   ├── McpPlugin.kt              # 插件入口
│   │   │   ├── McpPluginSettings.kt      # 插件配置
│   │   │   └── ProjectCloseListener.kt   # 项目关闭监听
│   │   └── resources/META-INF/
│   │       └── plugin.xml                # 插件描述
│   └── test/
│       └── kotlin/com/mcp/plugin/util/
│           └── LanguageDetectorTest.kt   # 语言检测测试 (18 用例)
├── docs/
│   ├── architecture-workflow.md
│   ├── audit-report.md
│   └── code-review-findings.md
├── build.gradle.kts
└── settings.gradle.kts
```

## 能力清单 (13 个 Capability)

| Capability | 风险等级 | 隔离策略 | 说明 |
|------------|---------|----------|------|
| `get_editor_state` | L0 | NONE | 获取编辑器状态 |
| `get_open_files` | L0 | NONE | 获取打开文件列表 |
| `get_diagnostics` | L1 | NONE | 获取代码诊断信息 |
| `get_git_status` | L1 | NONE | 获取 Git 状态 |
| `get_git_diff` | L1 | NONE | 获取 Git Diff |
| `search_files` | L1 | NONE | 搜索文件 |
| `read_file` | L1 | NONE | 读取文件 |
| `read_directory` | L1 | NONE | 读取目录 |
| `open_file` | L1 | NONE | 打开文件 |
| `write_file` | L2 | WORKSPACE_ISOLATION | 写入文件 |
| `apply_diff` | L2 | WORKSPACE_ISOLATION | 应用 Diff |
| `apply_full_content` | L2 | WORKSPACE_ISOLATION | 应用完整内容 |
| `run_terminal` | L3 | PROCESS_SANDBOX | 执行终端命令 |

## 安全模型

### 信任边界

```
Agent → CapabilityRouter (Gateway)
              ↓
         [1] Identity — "你是谁？"
         [2] Authorization — "你能做什么？" (IDE L0-L3, Agent L0-L1)
         [3] Session Binding — "你能操作哪个 Host？"
         [4] Risk Policy — ToolRiskLevel → SandboxPolicy
         [5] Sandbox — WorkspaceSandbox / ProcessSandboxExecutor
         [6] Audit — CapabilityAuditLog
              ↓
         WebSocket (ws://, ?token=xxx)
              ↓
         Plugin
              ↓
         [纵深防御] PathValidator (路径安全校验)
              ↓
         IDE / OS
```

### 安全设施

| 设施 | 层 | 功能 |
|------|-----|------|
| `WebSocketAuthToken` | Gateway | Token 认证 |
| `CapabilityAuthorization` | Gateway | session→riskLevel 授权映射 |
| `SandboxPolicy` | Gateway | 按风险等级决定沙箱策略 |
| `WorkspaceSandbox` | Gateway | 路径规范化 + workspace 边界 |
| `ProcessSandboxExecutor` | Gateway | 注入 _timeout/_outputLimit 约束 |
| `CapabilityAuditLog` | Gateway | 结构化审计日志 |
| `PathValidator` | Plugin | 路径安全校验（纵深防御） |

### Plugin 端路径安全架构

```
CapabilityAdapter          DiffApplier
      │                        │
      └────────┬───────────────┘
               │
               ▼
         PathValidator  (唯一路径安全入口)
               │
               ├── normalizePath()        → Path.of().normalize()
               ├── isPathInWorkspace()    → startsWith(workspaceRoot)
               └── isSensitivePath()      → 敏感目录/文件过滤
```

## 通信

| 端点 | 协议 | 用途 |
|------|------|------|
| `ws://localhost:8080/ws/host?token=xxx` | WebSocket | Host 连接 (Token 认证) |

### 消息类型

```
Agent → Gateway → Plugin:
  capability_call  → CapabilityRouter.call() → sendTo(sessionId) → Plugin → CapabilityAdapter.execute()
  reply            → ChannelOrchestrator → WebSocket → Plugin → ChatPanel.display()

Plugin → Gateway → Agent:
  hello            → HostBridge.handleHello() → 注册 Host + 授权
  event            → HostBridge.handleEvent() → HostEventBus.publish()
  chat             → HostBridge.handleChat() → ChannelOrchestrator → Agent
  capability_result→ HostBridge.handleCapabilityResult() → CapabilityRouter.resolveResult()
```

## 测试

| 测试套件 | 用例数 | 覆盖范围 |
|----------|--------|----------|
| CapabilityRouterSecurityTest | 25 | P0-1~P0-3 + P1-1~P1-2 + P1-5 |
| LanguageDetectorTest | 18 | 18 种语言 + 大小写 + fallback |
| **总计** | **43** | |

## 构建与安装

```bash
# 构建插件
cd rider-mcp-plugin
./gradlew buildPlugin

# 插件输出在 build/distributions/ 目录
# 在 Rider/IntelliJ 中通过 Settings → Plugins → Install Plugin from Disk 安装
```

## 配置

安装后通过 `Settings → Tools → MCP Agent` 配置：

- **WebSocket URL**: Gateway 连接地址（默认 `ws://localhost:8080/ws/host`）
- **Auth Token**: 认证 Token

## 修复状态

全部 20 项安全审计发现已修复 ✅（P0-1~P3-9），详见 [audit-report.md](./docs/audit-report.md)。

## 跨模块关联

- 前端文档：[mcp-agent-frontend/docs/](../mcp-agent-frontend/docs/)
- 后端文档：[mcp-agent-orchestrator/docs/](../mcp-agent-orchestrator/docs/)
- 后端架构契约：[mcp-agent-orchestrator Architecture Contract](../mcp-agent-orchestrator/docs/Architecture-Contract.md)