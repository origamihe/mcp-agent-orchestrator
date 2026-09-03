# mcp-agent-orchestrator

基于 MCP (Model Context Protocol) 的多渠道 AI Agent 编排平台，支持 QQ Bot（OneBot/NapCat）、WebSocket 实时通信、中文 TTS 语音合成（CosyVoice）、DOCX/PPT 文件生成。

## 架构

```
┌─────────────────────────────────────────────────┐
│                  mcp-agent-frontend              │
│                   (Vue.js 3)                     │
│           WebSocket ↔ REST API                   │
└──────────────────────┬──────────────────────────┘
                       │
┌──────────────────────▼──────────────────────────┐
│              mcp-agent-orchestrator              │
│                 (Java / Spring)                  │
│  ┌───────────┐  ┌──────────┐  ┌──────────────┐  │
│  │ mcp-gateway│  │mcp-engine│  │  mcp-tools   │  │
│  │ 渠道适配层  │  │Agent编排 │  │ 文件生成/工具 │  │
│  │ QQ/Web/API │  │ LLM调度  │  │DOCX/PPT/CosyVoice│  │
│  └───────────┘  └──────────┘  └──────────────┘  │
│                      │                           │
│  ┌───────────────────▼────────────────────────┐  │
│  │        Host Bridge / CapabilityRouter       │  │
│  │      (安全控制面: Auth + Sandbox + Audit)    │  │
│  └───────────────────┬────────────────────────┘  │
└──────────────────────┼──────────────────────────┘
                       │ WebSocket (ws://, ?token)
┌──────────────────────▼──────────────────────────┐
│              rider-mcp-plugin                   │
│            (IntelliJ Plugin / Kotlin)            │
│  ┌───────────┐  ┌──────────┐  ┌──────────────┐  │
│  │Capability │  │  Diff    │  │  Path        │  │
│  │ Adapter   │  │ Applier  │  │  Validator   │  │
│  │ 13 个能力  │  │          │  │  纵深防御     │  │
│  └───────────┘  └──────────┘  └──────────────┘  │
└─────────────────────────────────────────────────┘
```

## 功能特性

- **多渠道接入**：QQ Bot（OneBot 协议）、Web 前端、REST API、IDE 插件（Rider/IntelliJ）
- **语音模式**：中文 TTS 语音合成（CosyVoice），QQ 语音消息回复
- **文件生成**：AI 驱动的 DOCX、PPT、PDF、XLSX、HTML 文件生成
- **实时监控**：WebSocket 推送文件下载链接到前端监控面板
- **Agent 编排**：可配置的 LLM Agent 会话管理、多 Agent 协作流水线
- **安全控制面**：L0-L5 工具风险分级、Workspace/Process/Container 三级沙箱隔离、Capability 授权与审计
- **Agent 管理控制台**：14 个页面，涵盖 Agent 生命周期、Runtime 监控、数据管理、安全治理

## 环境要求

| 组件 | 版本要求 | 用途 |
|------|----------|------|
| **JDK** | 21+ | 后端编译与运行 |
| **Maven** | 3.8+ | 后端构建 |
| **Node.js** | 20.19+ 或 22.12+ | 前端开发与构建 |
| **Python** | 3.10+ | CosyVoice TTS Server |
| **PostgreSQL** | 16+（推荐 18） | 数据持久化 |
| **Ollama** | 最新版 | 本地 LLM 推理 |

## 技术栈

### 后端 (mcp-agent-orchestrator)

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

### 前端 (mcp-agent-frontend)

| 技术 | 版本 | 说明 |
|------|------|------|
| Vue | 3.5 | 前端框架 |
| TypeScript | 6.0 | 类型安全 |
| Vite | 8.0 | 构建工具 |
| Pinia | 3.0 | 状态管理 |
| Vue Router | 4.6 | 路由 |
| Axios | 1.9 | HTTP 客户端 |
| SASS | 1.89 | CSS 预处理 |

### 外部服务

| 服务 | 端口 | 说明 |
|------|------|------|
| Ollama | 11434 | 本地 LLM 推理 |
| PostgreSQL | 5432 | 数据库 |
| CosyVoice TTS Server | 5001 | 中文 TTS 引擎（CosyVoice） |
| NapCatQQ (OneBot) | 3002 | QQ Bot 协议适配 |
| 后端服务 | 8080 | Spring Boot 主服务 |
| 前端开发服务器 | 5173 | Vite Dev Server |

## 快速开始

### 1. 克隆项目

```bash
git clone https://github.com/your-username/mcp-agent-orchestrator.git
cd mcp-agent-orchestrator
```

### 2. 安装第三方依赖

本项目依赖以下第三方开源项目，请自行下载并放置到对应目录：

#### CosyVoice（中文 TTS 引擎）

- **仓库**：https://github.com/FunAudioLLM/CosyVoice
- **许可证**：Apache License 2.0
- **安装**：将 CosyVoice 克隆或下载到 `cosyvoice-server/` 目录下

```bash
cd cosyvoice-server
git clone https://github.com/FunAudioLLM/CosyVoice.git
```

#### NapCatQQ（QQ Bot 框架）

- **仓库**：https://github.com/NapNeko/NapCatQQ
- **许可证**：NapNeko 自定义许可证
- **安装**：从官方仓库下载最新 Release，放置到 `NapCat/` 目录

```bash
# 请从 https://github.com/NapNeko/NapCatQQ/releases 下载最新版本
# 解压到 NapCat/ 目录
```

### 3. 配置数据库 (PostgreSQL)

#### 安装 PostgreSQL

从 [PostgreSQL 官网](https://www.postgresql.org/download/) 下载安装（推荐 v18），安装时记住设置的 `postgres` 用户密码。

#### 创建数据库

```bash
# 登录 PostgreSQL
psql -U postgres

# 创建数据库和 schema
CREATE DATABASE mcp_agent;
\c mcp_agent
CREATE SCHEMA mcp_agent;
\q
```

#### 修改数据库连接配置

编辑 `mcp-agent-orchestrator/mcp-starter/src/main/resources/application.yml`：

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/mcp_agent?currentSchema=mcp_agent
    username: postgres
    password: 你的密码
```

> 数据库表结构由 Flyway 自动管理，首次启动时会自动创建。

### 4. 安装 Ollama 并拉取模型

```bash
# 安装 Ollama (https://ollama.com/download)
# 拉取模型（推荐 gemma4 或 qwen3.5:8b）
ollama pull gemma4

# 验证模型
ollama list
```

在 `application.yml` 中配置模型名称：

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

### 5. 启动 TTS 服务（可选：语音模式需要）

语音模式需要 CosyVoice TTS Server 提供中文语音合成服务：

```bash
# 方式一：使用 PowerShell 启动脚本（Windows）
cd cosyvoice-server
.\start.ps1

# 方式二：手动启动
cd cosyvoice-server
# 创建并激活虚拟环境
python -m venv .venv
.venv\Scripts\activate
# 安装依赖
pip install fastapi uvicorn soundfile numpy torch librosa
# 启动服务
python main.py
```

> 启动后 CosyVoice TTS Server 在 `http://127.0.0.1:5001`，首次启动会自动从 ModelScope 下载模型。

### 6. 配置 QQ Bot（可选）

1. 从 [NapCatQQ Releases](https://github.com/NapNeko/NapCatQQ/releases) 下载最新版本，解压到 `NapCat/` 目录
2. 按 NapCat 文档完成 QQ 登录和 OneBot 配置
3. 确保 OneBot HTTP 服务运行在 `http://127.0.0.1:3002`

在 `application.yml` 中配置 QQ Bot 参数：

```yaml
channel:
  qq:
    enabled: true
    onebot-url: http://127.0.0.1:3002
    access-token: "agent123"
    qq-number: "你的QQ号"
    reply-mode: all          # all | at_only | keyword
    keywords: ""
```

### 7. 构建后端

```bash
cd mcp-agent-orchestrator
mvn clean package -DskipTests
```

### 8. 启动前端

```bash
cd mcp-agent-frontend
npm install
npm run dev
```

## 完整启动流程

推荐的启动顺序：

```
┌─────────────────────────────────────────────────────────┐
│ 第1步：基础服务                                          │
│   ├── PostgreSQL  (端口 5432)                            │
│   └── Ollama     (端口 11434)   →  ollama serve          │
├─────────────────────────────────────────────────────────┤
│ 第2步：TTS 服务（可选）                                   │
│   └── CosyVoice TTS Server (端口 5001) → python main.py  │
├─────────────────────────────────────────────────────────┤
│ 第3步：QQ Bot（可选）                                     │
│   └── NapCatQQ           (端口 3002)  →  napcat.exe      │
├─────────────────────────────────────────────────────────┤
│ 第4步：后端主服务                                        │
│   └── Spring Boot        (端口 8080)  →  mvn spring-boot:run │
├─────────────────────────────────────────────────────────┤
│ 第5步：前端开发服务器                                     │
│   └── Vite Dev Server     (端口 5173)  →  npm run dev    │
└─────────────────────────────────────────────────────────┘
```

## 项目结构

```
mcp-agent-orchestrator/
├── mcp-agent-orchestrator/          # 后端（Java / Spring Boot）
│   ├── mcp-bom/                     # 依赖版本管理
│   ├── mcp-common/                  # 公共模块（上下文对象、DTO、接口）
│   ├── mcp-core/                    # 核心模块（Prompt 构建管线、ContextProvider）
│   ├── mcp-agent-engine/            # Agent 引擎（编排、Pipeline、规划、记忆、反射）
│   ├── mcp-gateway/                 # 网关模块（WebSocket、REST API、Host 桥接、安全控制面）
│   ├── mcp-llm/                     # LLM 客户端（Ollama 集成）
│   ├── mcp-tools/                   # 工具模块（沙箱体系、文件生成、工具注册）
│   ├── mcp-starter/                 # 启动模块（配置、Flyway 迁移）
│   └── docs/                        # 架构文档
│       ├── Architecture-Contract.md
│       ├── MCP-Agent Workflow.md
│       ├── MCP-Agent Test Workflow.md
│       └── metrics-history.md
├── mcp-agent-frontend/              # 前端（Vue 3 + TypeScript）
│   ├── src/
│   │   ├── pages/                   # 15 个路由页面
│   │   ├── stores/                  # 11 个 Pinia Store
│   │   ├── api/                     # 9 个 API 模块
│   │   ├── types/                   # 14 个类型文件
│   │   ├── components/              # 7 个通用组件
│   │   └── composables/             # 6 个组合式 API
│   └── docs/                        # 架构文档
│       ├── architecture-workflow.md
│       └── product-redesign.md
├── rider-mcp-plugin/                # IDE 插件（Kotlin / IntelliJ Platform）
│   ├── src/
│   │   └── main/kotlin/com/mcp/plugin/
│   │       ├── capability/          # 13 个能力定义 + 适配器
│   │       ├── transport/           # WebSocket 传输层
│   │       ├── util/                # 语言检测 + 路径安全校验
│   │       └── toolwindow/          # 聊天面板
│   └── docs/                        # 安全审计文档
│       ├── architecture-workflow.md
│       ├── audit-report.md
│       └── code-review-findings.md
├── cosyvoice-server/                # CosyVoice TTS 服务（Python）
│   ├── main.py                      # FastAPI TTS 服务（端口 5001）
│   ├── start.ps1                    # PowerShell 启动脚本
│   └── CosyVoice/                   # CosyVoice 模型源码
└── README.md
```

## 文档索引

| 模块 | README | 架构文档 |
|------|--------|----------|
| 前端 | [mcp-agent-frontend/README.md](mcp-agent-frontend/README.md) | [架构工作流与数据流](mcp-agent-frontend/docs/architecture-workflow.md) · [产品重定位](mcp-agent-frontend/docs/product-redesign.md) |
| 后端 | [mcp-agent-orchestrator/README.md](mcp-agent-orchestrator/README.md) | [架构契约](mcp-agent-orchestrator/docs/Architecture-Contract.md) · [Agent 工作流](mcp-agent-orchestrator/docs/MCP-Agent%20Workflow.md) · [指标历史](mcp-agent-orchestrator/docs/metrics-history.md) |
| 插件 | [rider-mcp-plugin/README.md](rider-mcp-plugin/README.md) | [架构与信任边界](rider-mcp-plugin/docs/architecture-workflow.md) · [安全审计](rider-mcp-plugin/docs/audit-report.md) |

## 许可证

本项目采用 [Apache License 2.0](LICENSE)。

本项目使用了以下第三方开源项目，各项目的许可证条款适用于其各自的代码：

| 项目 | 许可证 | 用途 |
|------|--------|------|
| [CosyVoice](https://github.com/FunAudioLLM/CosyVoice) | Apache 2.0 | 中文 TTS 语音合成 |
| [NapCatQQ](https://github.com/NapNeko/NapCatQQ) | NapNeko 自定义许可证 | QQ Bot 协议适配 |

详细信息请参阅 [THIRD-PARTY-NOTICES.md](THIRD-PARTY-NOTICES.md)。

## 常见问题

### Q: 启动报错 "Connection refused" 连接 PostgreSQL？
确保 PostgreSQL 服务已启动，且 `application.yml` 中的用户名密码正确。

### Q: TTS 语音合成失败？
1. 检查 CosyVoice TTS Server 是否正确启动（`http://127.0.0.1:5001/health`）
2. 确认模型已下载完成（首次启动会自动从 ModelScope 下载，可能需要几分钟）
3. 检查 Python 依赖是否完整安装（`fastapi`, `uvicorn`, `soundfile`, `torch`, `librosa`）
4. 如果使用零样本克隆模式，需提供 `prompt_wav` 参数

### Q: QQ Bot 无法接收到消息？
1. 确保 NapCat 已正确登录 QQ 并开启 OneBot HTTP 服务
2. 检查 `channel.qq.onebot-url` 和 `access-token` 配置是否匹配
3. 查看后端日志确认 OneBot 连接状态

### Q: Ollama 模型响应很慢？
1. 检查机器是否有 GPU（Ollama 默认使用 CPU 推理）
2. 可尝试更小的模型如 `qwen3.5:4b` 或 `llama3.2:3b-instruct-q4_K_M`
3. 调整 `temperature` 参数降低推理复杂度

## 免责声明

本项目仅供学习和研究使用。使用 QQ Bot 功能时，请遵守 QQ 平台的服务条款和相关法律法规。开发者不对因使用本项目而产生的任何问题承担责任。