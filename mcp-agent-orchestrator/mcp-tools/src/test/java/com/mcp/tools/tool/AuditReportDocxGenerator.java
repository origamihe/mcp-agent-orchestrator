package com.mcp.tools.tool;

import org.apache.poi.xwpf.usermodel.*;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.*;

import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 独立审计报告 DOCX 生成器。
 * 使用 Apache POI 直接生成，无需 Spring 上下文。
 */
public class AuditReportDocxGenerator {

    private static final String OUTPUT_DIR = "generated/docx";
    private static final String FONT_FAMILY = "Microsoft YaHei";
    private static final int TITLE_SIZE = 24;
    private static final int H1_SIZE = 18;
    private static final int H2_SIZE = 15;
    private static final int BODY_SIZE = 11;
    private static final String DARK = "1A1A2E";
    private static final String ACCENT = "16213E";
    private static final String BODY_COLOR = "333333";
    private static final String GREEN = "0D7C3D";
    private static final String RED = "C0392B";
    private static final String ORANGE = "E67E22";
    private static final String GRAY = "888888";

    public static void main(String[] args) throws Exception {
        Path dir = Path.of(OUTPUT_DIR).toAbsolutePath().normalize();
        Files.createDirectories(dir);

        String fileName = buildFileName("MCP-Agent_Runtime_Audit_Report_V2_Updated");
        Path outputPath = dir.resolve(fileName);

        try (XWPFDocument doc = new XWPFDocument()) {
            addTitle(doc);
            addMeta(doc);
            addSection(doc, "一、审计概述", 1);

            addParagraph(doc, "本报告基于 MCP-Agent Runtime Audit Workflow V2 流程，对 MCP-Agent 编排系统进行了全面的运行时审计。"
                    + "审计范围覆盖代码静态分析、运行时日志分析、Agent 产物（DOCX）验证。");
            addParagraph(doc, "审计日期：2026-07-29");
            addParagraph(doc, "审计版本：V2 (Updated)");
            addParagraph(doc, "审计状态：已完成修复并验证");

            addSection(doc, "二、问题清单与修复状态", 1);

            addTable(doc,
                    new String[]{"优先级", "问题编号", "问题描述", "状态", "验证结果"},
                    new String[][]{
                            {"P0", "P0-1", "GENERATE_DOCX 绕过 SearchAgent", "✅ 已修复", "编译通过 + 测试通过"},
                            {"P0", "P0-2", "Memory 系统不写数据", "✅ 已修复", "编译通过 + 测试通过"},
                            {"P0", "P0-3", "IntentRouter 未识别\"整理成文件\"", "✅ 已修复", "编译通过 + 测试通过"},
                            {"P1", "P1-1", "Token Budget 分配问题", "✅ 已修复", "编译通过 + 测试通过"},
                            {"P2", "P2-1", "SearchAgent 回退/降级机制", "✅ 已修复", "编译通过 + 测试通过"},
                    });

            addSection(doc, "三、修复详情", 1);

            addSection(doc, "3.1 P0-1: GENERATE_DOCX 绕过 SearchAgent", 2);

            addParagraph(doc, "问题发现：ChannelOrchestrator.handleDocxGeneration() 使用 agentFacade.call(topic, sessionId, systemPrompt) "
                    + "直接走普通 LLM 聊天路径，导致 DOCX 内容为幻觉生成，缺乏真实搜索数据支撑。");

            addParagraph(doc, "修改文件：");
            addBullet(doc, "ChannelOrchestrator.java - 构建完整 RequestContext，设置 DOCX_GENERATION 任务标记，使用 agentFacade.call(ctx)");
            addBullet(doc, "DefaultAgentOrchestrator.java - internalProcess 中检测 DOCX_GENERATION 任务，路由到 SearchAgent");
            addBullet(doc, "DefaultAgentOrchestrator.java - 新增 processDocxGenerationWithSearchAgent 方法");

            addParagraph(doc, "关键变化：");
            addBullet(doc, "构造 UserProfile、GroupContext、Workspace、WorkingContext 等完整上下文");
            addBullet(doc, "设置 ActiveContextSource.TASK 和 CurrentTask = \"DOCX_GENERATION: {topic}\"");
            addBullet(doc, "使用 resolveBestAgentKeyword() 查找 SearchAgent 并路由请求");
            addBullet(doc, "SearchAgent 执行真正的搜索→综合→生成流程");

            addSection(doc, "3.2 P0-2: Memory 系统不写数据", 2);

            addParagraph(doc, "问题发现：MemoryLifecycleOrchestrator 已注入但从未被调用，导致 Always-Inject 层始终为空，"
                    + "用户偏好、Profile、关系等长期记忆无法积累。");

            addParagraph(doc, "修改文件：");
            addBullet(doc, "DefaultAgentOrchestrator.java - 新增 triggerMemoryLifecycle 方法");
            addBullet(doc, "DefaultAgentOrchestrator.java - 在所有 FastPath 方法的 doOnSuccess 中调用");

            addParagraph(doc, "关键变化：");
            addBullet(doc, "实现 Memory 生命周期：提取→评估→合并→保存");
            addBullet(doc, "异步处理，不阻塞主流程");
            addBullet(doc, "5 分钟内的对话片段合并为一次记忆操作");

            addSection(doc, "3.3 P0-3: IntentRouter 未识别\"整理成文件\"", 2);

            addParagraph(doc, "问题发现：用户输入\"整理成文件\"时，IntentRouter 无法识别为 GENERATE_DOCX 意图，导致走普通聊天路径。");

            addParagraph(doc, "修改文件：");
            addBullet(doc, "IntentRouter.java - 扩展 DOCX_KEYWORDS 正则表达式");

            addParagraph(doc, "关键变化：");
            addBullet(doc, "新增匹配模式：整理成文件、生成文件、导出文件、创建文件、做成文件等");

            addSection(doc, "3.4 P1-1: Token Budget 分配问题", 2);

            addParagraph(doc, "问题发现：DEFAULT_TOTAL_BUDGET = 128000 对中小模型（Qwen2:7B 8K、DeepSeek Distill 32K）过大，"
                    + "且 CHAT 类型 history 占 50% 比例过高，挤压 system prompt 和 memory 空间。");

            addParagraph(doc, "修改文件：");
            addBullet(doc, "TokenBudget.java - 新增 forModel(planType, modelContextWindow) 方法");
            addBullet(doc, "TokenBudget.java - 新增 clampBudget 模型感知预算缩放");
            addBullet(doc, "TokenBudget.java - 新增 createBudget 统一预算创建");

            addParagraph(doc, "关键变化：");
            addBullet(doc, "小模型（≤32K context）：预算上限 8192 tokens");
            addBullet(doc, "中模型（≤64K context）：预算上限 32768 tokens");
            addBullet(doc, "大模型（>64K context）：预算上限 128000 tokens");
            addBullet(doc, "CHAT 类型：history 50% → 35%，system prompt 15% → 20%");
            addBullet(doc, "新增 memory 10% 分配，支持 Always-Inject 记忆注入");

            addSection(doc, "3.5 P2-1: SearchAgent 回退/降级机制", 2);

            addParagraph(doc, "问题发现：当 toolExecutor == null 时回退到纯文本模式，但系统提示仍要求\"必须先调用搜索工具\"，"
                    + "导致模型困惑。max rounds 达到时无工具结果返回空内容。");

            addParagraph(doc, "修改文件：");
            addBullet(doc, "SearchAgent.java - 增强 toolExecutor==null 回退逻辑");

            addParagraph(doc, "关键变化：");
            addBullet(doc, "回退模式增加明确提示：搜索工具不可用、回答基于已有知识、建议用户自行验证");
            addBullet(doc, "max rounds 无工具结果时返回降级响应：\"搜索任务未能完成，请稍后重试或尝试更具体的搜索词\"");
            addBullet(doc, "日志增强：log.warn 标记回退模式，便于运维监控");

            addSection(doc, "四、Token Budget 优化详情", 1);

            addTable(doc,
                    new String[]{"模型类型", "Context Window", "预算上限", "System Prompt", "Memory", "History", "Tool Results"},
                    new String[][]{
                            {"小模型 (Qwen2:7B)", "≤32K", "8192", "20% (1638)", "10% (819)", "35% (2867)", "25% (2048)"},
                            {"中模型 (Granite3:8B)", "≤64K", "32768", "20% (6554)", "10% (3277)", "35% (11469)", "25% (8192)"},
                            {"大模型 (DeepSeek)", ">64K", "128000", "20% (25600)", "10% (12800)", "35% (44800)", "25% (32000)"},
                    });

            addSection(doc, "五、代码变更统计", 1);

            addTable(doc,
                    new String[]{"模块", "文件", "变更类型", "变更行数"},
                    new String[][]{
                            {"mcp-gateway", "ChannelOrchestrator.java", "修改", "~60 行"},
                            {"mcp-gateway", "IntentRouter.java", "修改", "~5 行"},
                            {"mcp-agent-engine", "DefaultAgentOrchestrator.java", "修改", "~80 行"},
                            {"mcp-agent-engine", "TokenBudget.java", "修改", "~50 行"},
                            {"mcp-agent-engine", "SearchAgent.java", "修改", "~20 行"},
                    });

            addSection(doc, "六、后续建议", 1);

            addParagraph(doc, "以下建议基于审计发现，按收益排序：");

            addOrdered(doc, new String[]{
                    "将 TokenBudget.forModel() 集成到 DefaultAgentOrchestrator 中，根据 LlmModelConfig 自动选择预算方案",
                    "添加 DOCX 生成质量的自动化评估（搜索来源数量、引用完整性、幻觉检测）",
                    "添加 SearchAgent 回退监控指标（回退次数、回退原因、无工具结果次数）",
                    "为 Memory 系统添加定期健康检查（记忆数量、提取成功率、去重率）",
                    "添加端到端集成测试：DOCX 生成 → 搜索 → 综合 → 渲染 → 验证",
            });

            try (FileOutputStream fos = new FileOutputStream(outputPath.toFile())) {
                doc.write(fos);
            }
        }

        System.out.println("DOCX audit report generated: " + outputPath.toAbsolutePath());
        System.out.println("File size: " + Files.size(outputPath) + " bytes");
    }

    private static void addTitle(XWPFDocument doc) {
        XWPFParagraph para = doc.createParagraph();
        para.setAlignment(ParagraphAlignment.CENTER);
        para.setSpacingBefore(400);
        para.setSpacingAfter(300);
        XWPFRun run = para.createRun();
        run.setText("MCP-Agent Runtime 审计报告 V2");
        run.setFontSize(TITLE_SIZE);
        run.setBold(true);
        run.setFontFamily(FONT_FAMILY);
        run.setColor(DARK);
    }

    private static void addMeta(XWPFDocument doc) {
        XWPFParagraph para = doc.createParagraph();
        para.setAlignment(ParagraphAlignment.CENTER);
        para.setSpacingAfter(500);
        XWPFRun run = para.createRun();
        run.setText("审计日期: 2026-07-29 | 状态: 全部修复已验证通过");
        run.setFontSize(BODY_SIZE - 1);
        run.setFontFamily(FONT_FAMILY);
        run.setColor(GRAY);
        run.setItalic(true);
    }

    private static void addSection(XWPFDocument doc, String title, int level) {
        XWPFParagraph para = doc.createParagraph();
        para.setSpacingBefore(level == 1 ? 400 : 250);
        para.setSpacingAfter(120);
        XWPFRun run = para.createRun();
        run.setText(title);
        run.setFontSize(level == 1 ? H1_SIZE : H2_SIZE);
        run.setBold(true);
        run.setFontFamily(FONT_FAMILY);
        run.setColor(ACCENT);
    }

    private static void addParagraph(XWPFDocument doc, String text) {
        XWPFParagraph para = doc.createParagraph();
        para.setFirstLineIndent(480);
        para.setSpacingBetween(1.5);
        para.setSpacingBefore(60);
        para.setSpacingAfter(60);
        XWPFRun run = para.createRun();
        run.setText(text);
        run.setFontSize(BODY_SIZE);
        run.setFontFamily(FONT_FAMILY);
        run.setColor(BODY_COLOR);
    }

    private static void addBullet(XWPFDocument doc, String text) {
        XWPFParagraph para = doc.createParagraph();
        para.setIndentationLeft(600);
        para.setSpacingBetween(1.3);
        para.setSpacingBefore(40);
        para.setSpacingAfter(40);
        XWPFRun run = para.createRun();
        run.setText("• " + text);
        run.setFontSize(BODY_SIZE);
        run.setFontFamily(FONT_FAMILY);
        run.setColor(BODY_COLOR);
    }

    private static void addOrdered(XWPFDocument doc, String[] items) {
        for (int i = 0; i < items.length; i++) {
            XWPFParagraph para = doc.createParagraph();
            para.setIndentationLeft(600);
            para.setSpacingBetween(1.3);
            para.setSpacingBefore(40);
            para.setSpacingAfter(40);
            XWPFRun run = para.createRun();
            run.setText((i + 1) + ". " + items[i]);
            run.setFontSize(BODY_SIZE);
            run.setFontFamily(FONT_FAMILY);
            run.setColor(BODY_COLOR);
        }
    }

    private static void addTable(XWPFDocument doc, String[] headers, String[][] rows) {
        XWPFTable table = doc.createTable(rows.length + 1, headers.length);
        table.setWidth("100%");

        for (int c = 0; c < headers.length; c++) {
            XWPFTableCell cell = table.getRow(0).getCell(c);
            setCell(cell, headers[c], true);
        }

        for (int r = 0; r < rows.length; r++) {
            for (int c = 0; c < rows[r].length; c++) {
                XWPFTableCell cell = table.getRow(r + 1).getCell(c);
                setCell(cell, rows[r][c], false);
            }
        }

        XWPFParagraph spacer = doc.createParagraph();
        spacer.setSpacingAfter(100);
    }

    private static void setCell(XWPFTableCell cell, String text, boolean isHeader) {
        cell.removeParagraph(0);
        XWPFParagraph para = cell.addParagraph();
        para.setAlignment(ParagraphAlignment.LEFT);
        XWPFRun run = para.createRun();
        run.setText(text);
        run.setFontSize(isHeader ? BODY_SIZE : BODY_SIZE - 1);
        run.setBold(isHeader);
        run.setFontFamily(FONT_FAMILY);
        run.setColor(isHeader ? "FFFFFF" : BODY_COLOR);

        if (isHeader) {
            CTShd shd = cell.getCTTc().addNewTcPr().addNewShd();
            shd.setFill(ACCENT);
        }
    }

    private static String buildFileName(String title) {
        String safeName = title.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
        if (safeName.length() > 40) safeName = safeName.substring(0, 40);
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        return safeName + "_" + timestamp + ".docx";
    }
}