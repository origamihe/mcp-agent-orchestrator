# mcp-agent-orchestrator

MCP Agent Orchestrator — 基于 MCP (Model Context Protocol) 的多渠道 AI Agent 编排后端

基于 Spring Boot 3.4 + Spring AI 构建的 Agent 编排引擎，提供 Agent 调度、Prompt 组装、上下文管理、工具调用、沙箱安全、多渠道适配等核心能力。

## 架构文档

- [Architecture Contract](./docs/Architecture-Contract.md) — 架构契约：核心组件的职责边界、生命周期规则、互操作契约
- [MCP-Agent Workflow](./docs/MCP-Agent%20Workflow.md) — 架构审查工作流：所有 Pipeline 定义（Chat、Context-Aware FastPath、Artifact Recall、Streaming、Model Routing、Agent Collaboration、Document Generation 等）
- [MCP-Agent Test Workflow](./docs/MCP-Agent%20Test%20Workflow.md) — 测试工作流：验证架构契约合规性的测试流程
- [metrics-history](./docs/metrics-history.md) — 架构指标历史：Provider Count、Pipeline Count、Context Count 等指标追踪

## 技术栈

| 技术 | 版本 | 说明 |
|------|------|------|
| Spring Boot | 3.4.5 | 应用框架 |
| Spring AI | 1.1.0-M2 | AI 集成（Ollama） |
| Spring WebFlux | — | 响应式 HTTP 客户端 |
| Spring Data JPA | — | ORM 数据访问 |
| Flyway | 11.14.1 | 数据库版本管理 |
| PostgreSQL JDBC | 42.7.5 | 数据库驱动 |
| Lombok | 1.18.34 | 代码简化 |
| MapStruct | 1.6.0 | 对象映射 |
| Apache POI | 5.3.0 | DOCX/PPTX 文件生成 |
| Jackson | — | JSON 序列化 |
| Java | 21+ | 编译与运行 |

## 模块结构

```
mcp-agent-orchestrator/
├── mcp-bom/              # Bill of Materials — 统一版本管理
├── mcp-common/           # 公共模块
│   ├── context/          # 上下文对象 (RequestContext, BuildContext, PromptContext, MemoryIdentity, SessionState, WorkingContext 等)
│   ├── tool/             # 工具风险等级 (ToolRiskLevel L0-L5)
│   └── UserProfileService, GroupContext, Workspace
├── mcp-core/             # 核心模块 — Prompt 构建管线
│   ├── context/          # PromptContextBuilder, ContextProvider, ContextAssembler, PromptPolicy
│   └── 8 个 ContextProvider (Relationship, Workspace, Artifact, GroupConversation 等)
├── mcp-agent-engine/     # Agent 引擎
│   ├── agent/            # Agent 实现 (ChatAgent, CodeAgent, SearchAgent, ResearchSynthesizer)
│   ├── artifact/         # 文档召回 (KeywordRecallStrategy, EmbeddingRecallStrategy, HybridRetriever)
│   ├── context/          # ContextRequirement, ContextBundle, TokenBudget
│   ├── delivery/         # 投递渠道 (QQ, Webhook, Email)
│   ├── loop/             # Agent 循环 (LoopStateMachine, AgentTaskScheduler)
│   ├── memory/           # 记忆系统 (MemoryLifecycleOrchestrator, GroupConversationContextAssembler)
│   ├── orchestrator/     # 编排器 (DefaultAgentOrchestrator, AgentCollaborationOrchestrator, MultiAgentOrchestrator)
│   ├── planner/          # 规划器 (PlanExecutor, PlanDAGConverter)
│   ├── reflection/       # 反思系统 (ReflectionManager, PromptEnricher, SkillLibraryService)
│   ├── retry/            # 重试管理 (RetryManager)
│   ├── routing/          # 模型路由 (ModelRouter, ModelFallbackChain)
│   ├── runtime/          # Agent 运行时 (AgentRuntime, PromptAssemblyResult)
│   ├── skill/            # 技能管线 (SkillPipeline, SkillComposer)
│   ├── trace/            # 执行追踪 (SessionEventStore, ContractVerifier, ExecutionContract)
│   └── workspace/        # 工作空间服务
├── mcp-gateway/          # API 网关
│   ├── ws/               # WebSocket 处理 (McpWebSocketHandler, HostWebSocketHandler, WebSocketAuthToken)
│   ├── host/             # Host 桥接 (HostBridge, CapabilityRouter, CapabilityAuthorization, CapabilityAuditLog)
│   ├── controller/       # REST Controller (Agent, Run, Memory, Knowledge, Policy, Host, Dashboard, Log 等)
│   └── 10+ Controller, 54 个后端 API
├── mcp-llm/              # LLM 集成模块
│   └── SpringAiLlmClient, LlmClient, LlmMetricsCollector
├── mcp-tools/            # 工具系统
│   ├── sandbox/          # 沙箱体系 (SandboxPolicy, WorkspaceSandbox, ProcessSandboxExecutor)
│   └── ToolExecutor, ToolRegistry, DocumentGenerator
├── mcp-starter/          # Spring Boot 启动器
│   └── McpOrchestratorApplication, application.yml
└── docs/                 # 架构文档
    ├── Architecture-Contract.md
    ├── MCP-Agent Workflow.md
    ├── MCP-Agent Test Workflow.md
    └── metrics-history.md
```

## 核心架构概念

### 域对象模型

| 对象 | 模块 | 职责 |
|------|------|------|
| `RequestContext` | mcp-common | 跨模块请求传输对象 (gateway → engine) |
| `BuildContext` | mcp-core | Prompt 构建期间的唯一工作上下文 |
| `PromptContext` | mcp-core | 所有 ContextProvider 填充完成后的渲染模型 |
| `MemoryIdentity` | mcp-common | 身份标识（平台、sessionId、userId、groupId） |
| `SessionState` | mcp-common | 会话配置状态（Mutable） |
| `WorkingContext` | mcp-common | 运行时工作上下文（Mutable, 生命周期 = 一次任务） |
| `ContextRequirement` | mcp-common | 上下文加载需求等级（NONE/CONVERSATION/DOCUMENT/WORKSPACE/SEARCH） |
| `ActiveContextSource` | mcp-common | 活跃上下文来源枚举（7 种） |

### Pipeline 架构

后端实现 12 条 Pipeline/Orchestrator：

- **Chat Pipeline**: User → determineContextRequirement → processContextAwareFastPath → LLM → Reply
- **Context-Aware FastPath**: 按需加载上下文（NONE/CONVERSATION/DOCUMENT/WORKSPACE/SEARCH）
- **Artifact Recall Pipeline**: 文档召回（KeywordRecallStrategy + EmbeddingRecallStrategy + HybridRetriever）
- **Streaming Pipeline**: 逐 token 流式推送
- **Model Routing + Fallback Pipeline**: 6 级任务复杂度分类 + 多模型 Fallback
- **Agent Collaboration Pipeline**: 3 种链式协作（Search→Code→Chat / Code→Review→Chat / Search→Chat）
- **Document Generation Pipeline**: PDF/XLSX/HTML/DOCX 文件生成
- **Prompt A/B Testing Pipeline**: 变体选择 + 效果追踪
- **Memory Pipeline**: 记忆生命周期管理
- **Service Pipeline**: 多渠道投递（QQ/Webhook/Email）
- **Skill Pipeline**: 技能组合与意图匹配
- **Plan Executor**: PlanDAG → Pipeline 转换 + 执行

### 安全模型

| 设施 | 位置 | 功能 |
|------|------|------|
| `ToolRiskLevel` | mcp-common | L0-L5 风险分级 |
| `SandboxPolicy` | mcp-tools | 按风险等级决定沙箱策略（NONE/WORKSPACE_ISOLATION/PROCESS_SANDBOX/BLOCKED） |
| `WorkspaceSandbox` | mcp-tools | 路径规范化 + workspace 边界 + 符号链接处理 |
| `ProcessSandboxExecutor` | mcp-tools | 注入超时/输出限制约束参数 |
| `CapabilityAuthorization` | mcp-gateway | session→riskLevel 授权映射 |
| `CapabilityAuditLog` | mcp-gateway | 结构化审计日志 |
| `WebSocketAuthToken` | mcp-gateway | Token 生成/验证 |

### 架构指标 (最新)

| 指标 | 数值 |
|------|------|
| Provider Count | 8 |
| Context Count | 17 |
| Pipeline/Orchestrator Count | 12 |
| RecallStrategy Count | 4 |
| ContextRequirement Levels | 5 |
| PromptPolicy Count | 7 |
| ModelRouter Complexity Levels | 6 |
| Test Count | 715 (PASS: 715, FAIL: 0) |

## 环境要求

| 组件 | 版本 | 用途 |
|------|------|------|
| JDK | 21+ | 编译与运行 |
| Maven | 3.8+ | 构建 |
| PostgreSQL | 16+（推荐 18） | 数据持久化 |
| Ollama | 最新版 | 本地 LLM 推理 |

## 快速开始

### 1. 配置数据库

```bash
psql -U postgres
CREATE DATABASE mcp_agent;
\c mcp_agent
CREATE SCHEMA mcp_agent;
\q
```

编辑 `mcp-starter/src/main/resources/application.yml`：

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/mcp_agent?currentSchema=mcp_agent
    username: postgres
    password: 你的密码
```

> 数据库表结构由 Flyway 自动管理，首次启动时会自动创建。

### 2. 安装 Ollama 并拉取模型

```bash
ollama pull gemma4
ollama list
```

在 `application.yml` 中配置：

```yaml
spring:
  ai:
    ollama:
      base-url: http://localhost:11434
      chat:
        options:
          model: gemma4
          temperature: 0.3
```

### 3. 构建与运行

```bash
cd mcp-agent-orchestrator
mvn clean package -DskipTests
mvn spring-boot:run
```

服务启动后监听 `http://localhost:8080`。

## 跨模块关联

- 前端文档：[mcp-agent-frontend/docs/](../mcp-agent-frontend/docs/)
- Rider 插件文档：[rider-mcp-plugin/docs/](../rider-mcp-plugin/docs/)
- 跨模块安全审计：[rider-mcp-plugin 跨模块安全审计](../rider-mcp-plugin/docs/audit-report.md)