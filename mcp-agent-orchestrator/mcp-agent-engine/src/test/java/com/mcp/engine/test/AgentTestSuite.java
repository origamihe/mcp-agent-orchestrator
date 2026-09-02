package com.mcp.engine.test;

import org.junit.platform.suite.api.ExcludeTags;
import org.junit.platform.suite.api.SelectPackages;
import org.junit.platform.suite.api.Suite;

/**
 * Agent Test Suite - 两套测试体系，职责互补
 *
 * ============================================================
 * 第一层：日常快速测试（mvn test，不调用 LLM）
 * ============================================================
 * 约 90-95% 的测试，全部 Mock LLM，快速反馈 (< 30s)
 *
 * 能力测试 (T1-T10，按 Capability 分类)
 * T1  ContextPipeline         - 上下文流水线完整性 (8 cases)
 * T2  Memory                  - 记忆新增/更新/删除/召回 (12 cases)
 * T3  Workspace               - 项目状态持久化 (8 cases)
 * T4  ToolCalling             - 工具路由与执行 (10 cases)
 * T5  PromptAssembly          - Layer 顺序与完整性 (6 cases)
 * T6  Multi-turn              - 多轮上下文一致性 (8 cases)
 * T7  Reflection              - 自我修正能力 (5 cases)
 * T8  Regression              - 防重构回归 (10 cases)
 * T9  E2E                     - 端到端全链路 (8 cases)
 * T10 Chaos                   - 混沌工程 (14 cases)
 * MemoryConflict              - 记忆冲突解决 (8 cases)
 * MemoryConflictResolver      - 冲突解决器 (8 cases)
 * LongTermMemoryEvolution     - 长期记忆演化 (12 cases)
 * DeepReflection              - 深度反思 (12 cases)
 * StressTest                  - Prompt Injection / 超长上下文 (12 cases)
 * AgentBenchmark              - Prompt 组装验证 (30 cases, Mock LLM)
 *
 * ============================================================
 * 第二层：LLM 依赖测试（mvn test -P llm-test，真实调用 LLM）
 * ============================================================
 * 约 5-10% 的测试，验证模型真实行为，按需运行
 *
 * RealAgentBenchmark          - 真实 LLM 基准 (16 cases)
 *   ├── Memory    (4 cases)  - 记住/覆盖/合并偏好
 *   ├── Reflection(3 cases)  - 自我纠错/识别不确定性/重试
 *   ├── Planner   (3 cases)  - 任务规划/无需工具判断/选择工具
 *   ├── Prompt    (3 cases)  - 格式/角色扮演/字数限制
 *   └── Reasoning (3 cases)  - 逻辑推理/多步推理/代码理解
 *
 * AgentBehaviorTest           - LLM 行为验证 (10 cases)
 *   ├── Memory Merge (3 cases)  - 确认记住/覆盖不丢失/独立存储
 *   ├── Reflection   (2 cases)  - 承认错误/表达不确定性
 *   ├── Planner      (2 cases)  - 任务分解/不过度规划
 *   ├── Skill        (2 cases)  - 生成代码/绝对路径
 *   └── Reasoning    (1 case)   - 因果推理
 *
 * BenchmarkReplay            - 可重放基准对比 (1 case)
 *   └── 从 benchmark-results/ 重放已保存对话，对比模型能力
 *
 * ============================================================
 * 使用方式
 * ============================================================
 * 日常开发：mvn test                       (仅第一层，不调 LLM)
 * LLM 验证：mvn test -P llm-test           (仅第二层，调 LLM)
 * 全部测试：mvn test -P all-tests          (两层都跑)
 * 指定模型：mvn test -P llm-test -Dollama.model=qwen3:8b
 * 指定 URL：mvn test -P llm-test -Dollama.base.url=http://localhost:11434
 *
 * IDE 运行：直接运行此类，默认排除 @Tag("llm") 测试
 *
 * ============================================================
 * 测试分层原则
 * ============================================================
 * 90-95% 测试不调用大模型 → 验证 Java 工程和 Agent 流程正确性
 * 5-10%  测试调用大模型    → 验证 Prompt、推理能力和 Agent 行为
 * 离线 Benchmark          → 版本发布前验证，与上一版本对比
 *
 * 两者互补，职责完全不同。
 */
@Suite
@SelectPackages({"com.mcp.engine.test", "com.mcp.engine.trace"})
@ExcludeTags("llm")
public class AgentTestSuite {
}