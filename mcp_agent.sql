--
-- PostgreSQL database dump
--

\restrict mxl4Y0UnJ41XGT5EUjz3wzK9l9l78X0uk73txQYXg54HLmp6Rc2ZjidlaMFDhCp

-- Dumped from database version 18.4
-- Dumped by pg_dump version 18.4

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET transaction_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

--
-- Name: mcp_agent; Type: SCHEMA; Schema: -; Owner: postgres
--

CREATE SCHEMA mcp_agent;


ALTER SCHEMA mcp_agent OWNER TO postgres;

SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: chat_messages; Type: TABLE; Schema: mcp_agent; Owner: postgres
--

CREATE TABLE mcp_agent.chat_messages (
    id bigint NOT NULL,
    session_id character varying(64) NOT NULL,
    role character varying(20) NOT NULL,
    content text NOT NULL,
    tool_calls jsonb,
    created_at timestamp without time zone NOT NULL,
    created_at_tz timestamp with time zone GENERATED ALWAYS AS ((created_at AT TIME ZONE 'Asia/Shanghai'::text)) STORED
);


ALTER TABLE mcp_agent.chat_messages OWNER TO postgres;

--
-- Name: TABLE chat_messages; Type: COMMENT; Schema: mcp_agent; Owner: postgres
--

COMMENT ON TABLE mcp_agent.chat_messages IS '聊天消息表';


--
-- Name: chat_messages_id_seq; Type: SEQUENCE; Schema: mcp_agent; Owner: postgres
--

CREATE SEQUENCE mcp_agent.chat_messages_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE mcp_agent.chat_messages_id_seq OWNER TO postgres;

--
-- Name: chat_messages_id_seq; Type: SEQUENCE OWNED BY; Schema: mcp_agent; Owner: postgres
--

ALTER SEQUENCE mcp_agent.chat_messages_id_seq OWNED BY mcp_agent.chat_messages.id;


--
-- Name: chat_sessions; Type: TABLE; Schema: mcp_agent; Owner: postgres
--

CREATE TABLE mcp_agent.chat_sessions (
    session_id character varying(64) NOT NULL,
    user_id character varying(100),
    created_at timestamp without time zone NOT NULL,
    last_active_at timestamp without time zone NOT NULL,
    created_at_tz timestamp with time zone GENERATED ALWAYS AS ((created_at AT TIME ZONE 'Asia/Shanghai'::text)) STORED,
    last_active_at_tz timestamp with time zone GENERATED ALWAYS AS ((last_active_at AT TIME ZONE 'Asia/Shanghai'::text)) STORED
);


ALTER TABLE mcp_agent.chat_sessions OWNER TO postgres;

--
-- Name: TABLE chat_sessions; Type: COMMENT; Schema: mcp_agent; Owner: postgres
--

COMMENT ON TABLE mcp_agent.chat_sessions IS '聊天会话表';


--
-- Name: flyway_schema_history; Type: TABLE; Schema: mcp_agent; Owner: postgres
--

CREATE TABLE mcp_agent.flyway_schema_history (
    installed_rank integer NOT NULL,
    version character varying(50),
    description character varying(200) NOT NULL,
    type character varying(20) NOT NULL,
    script character varying(1000) NOT NULL,
    checksum integer,
    installed_by character varying(100) NOT NULL,
    installed_on timestamp without time zone DEFAULT now() NOT NULL,
    execution_time integer NOT NULL,
    success boolean NOT NULL
);


ALTER TABLE mcp_agent.flyway_schema_history OWNER TO postgres;

--
-- Name: llm_config; Type: TABLE; Schema: mcp_agent; Owner: postgres
--

CREATE TABLE mcp_agent.llm_config (
    config_id character varying(100) NOT NULL,
    provider character varying(50) NOT NULL,
    model_name character varying(100) NOT NULL,
    temperature numeric(4,2) DEFAULT 0.7,
    max_tokens integer DEFAULT 2048,
    parameters jsonb,
    enabled boolean DEFAULT true,
    created_at timestamp without time zone NOT NULL,
    updated_at timestamp without time zone NOT NULL,
    created_at_tz timestamp with time zone GENERATED ALWAYS AS ((created_at AT TIME ZONE 'Asia/Shanghai'::text)) STORED,
    updated_at_tz timestamp with time zone GENERATED ALWAYS AS ((updated_at AT TIME ZONE 'Asia/Shanghai'::text)) STORED
);


ALTER TABLE mcp_agent.llm_config OWNER TO postgres;

--
-- Name: TABLE llm_config; Type: COMMENT; Schema: mcp_agent; Owner: postgres
--

COMMENT ON TABLE mcp_agent.llm_config IS 'LLM 配置表';


--
-- Name: prompt_templates; Type: TABLE; Schema: mcp_agent; Owner: postgres
--

CREATE TABLE mcp_agent.prompt_templates (
    name character varying(100) NOT NULL,
    type character varying(50) NOT NULL,
    template_text text NOT NULL,
    description character varying(500),
    version integer DEFAULT 1,
    updated_at timestamp without time zone NOT NULL,
    updated_at_tz timestamp with time zone GENERATED ALWAYS AS ((updated_at AT TIME ZONE 'Asia/Shanghai'::text)) STORED
);


ALTER TABLE mcp_agent.prompt_templates OWNER TO postgres;

--
-- Name: TABLE prompt_templates; Type: COMMENT; Schema: mcp_agent; Owner: postgres
--

COMMENT ON TABLE mcp_agent.prompt_templates IS '提示词模板表';


--
-- Name: chat_messages id; Type: DEFAULT; Schema: mcp_agent; Owner: postgres
--

ALTER TABLE ONLY mcp_agent.chat_messages ALTER COLUMN id SET DEFAULT nextval('mcp_agent.chat_messages_id_seq'::regclass);


--
-- Data for Name: chat_messages; Type: TABLE DATA; Schema: mcp_agent; Owner: postgres
--

COPY mcp_agent.chat_messages (id, session_id, role, content, tool_calls, created_at) FROM stdin;
1	4f51a26	USER	你好	\N	2026-06-04 21:34:52.387993
2	4f51a26	ASSISTANT	您好！我是您的AI助手，致力于提供专业、高效且友好的支持。以下是您可能需要的帮助方向：\n\n1. **信息查询**  \n   - 历史事件、科学知识、文化背景等百科类问题  \n   - 实时数据（如天气、汇率）需通过工具获取  \n\n2. **问题解答**  \n   - 学术疑问、技术难题、逻辑推理等  \n   - 多步骤问题将分步解析  \n\n3. **创作辅助**  \n   - 文案撰写、故事创作、代码生成等  \n   - 提供多种风格/方向的建议  \n\n4. **工具使用**  \n   - 指南与操作演示（如数据分析、编程调试）  \n   - 推荐适用的工具/资源  \n\n请随时提出您的需求，我将根据问题类型调用相应工具或直接提供答案。需要优先处理的内容可标注「紧急」或「优先级」。	\N	2026-06-04 21:34:52.406295
3	1dbe0969	USER	请读取C:\\Users\\24954\\OneDrive\\Desktop\\三段整合版  路径的文件	\N	2026-06-05 19:04:03.945649
4	1dbe0969	ASSISTANT	我无法直接访问或读取您本地计算机上的文件，包括路径 `C:\\Users\\24954\\OneDrive\\Desktop\\三段整合版`。以下是分步建议，帮助您自行检查或处理文件：\n\n---\n\n### **第一步：确认路径和文件是否存在**\n1. **打开文件资源管理器**：\n   - 按下 `Win + E` 打开文件资源管理器。\n   - 在地址栏输入路径：`C:\\Users\\24954\\OneDrive\\Desktop\\三段整合版`，按回车。\n   - **如果路径不存在**：请检查路径拼写是否正确，或确认文件是否被移动/删除。\n   - **如果存在**：您会看到该路径下的文件或文件夹（注意：`三段整合版`可能是一个文件夹，而非文件）。\n\n---\n\n### **第二步：检查文件权限**\n- 如果路径存在但无法访问：\n  1. 右键点击目标文件/文件夹 → **属性** → **安全**选项卡。\n  2. 确认您的用户账户是否有读取权限。如果没有，联系系统管理员或尝试以管理员身份运行程序。\n\n---\n\n### **第三步：通过代码读取文件（如需）**\n如果您需要编程读取文件，请提供以下信息：\n1. **文件类型**（如文本文件、Excel、PDF等）。\n2. **具体文件名**（例如 `三段整合版.txt`）。\n3. **读取目的**（如提取数据、分析内容等）。\n\n示例代码（Python）：\n```python\n# 读取文本文件（需替换为实际路径和文件名）\nfile_path = r"C:\\Users\\24954\\OneDrive\\Desktop\\三段整合版\\三段整合版.txt"\ntry:\n    with open(file_path, "r", encoding="utf-8") as file:\n        content = file.read()\n        print(content)\nexcept Exception as e:\n    print("错误:", str(e))\n```\n\n---\n\n### **第四步：联系技术支持**\n如果问题仍未解决，请提供以下信息：\n1. 文件路径是否正确（如 `三段整合版` 是文件夹还是文件）。\n2. 是否遇到权限错误或路径不存在的提示。\n3. 您希望如何处理该文件（如打开、分析、转换格式等）。\n\n---\n\n请根据以上步骤操作，或补充更多信息以便进一步协助！	\N	2026-06-05 19:04:03.971025
5	2238fe2f	USER	请读取位于C:\\Users\\24954\\OneDrive\\Desktop\\数据标注 位置的内容	\N	2026-06-06 19:09:44.921448
6	2238fe2f	ASSISTANT	目录 `C:\\Users\\24954\\OneDrive\\Desktop\\数据标注` 的内容如下：\n\n**子目录**:\n- `.idea`（项目配置文件夹）\n\n**文件**:\n- `bolt.txt`\n- `haze.txt`\n- `ridge.txt`\n- `spark.txt`\n- `标注归因(trace).txt`（包含标注归因分析的文本文件）\n\n需要进一步操作这些文件吗？	\N	2026-06-06 19:09:44.939461
7	46cc12ff	USER	请读取位于C:\\Users\\24954\\OneDrive\\Desktop\\数据标注 位置的内容	\N	2026-06-06 19:16:48.207805
8	46cc12ff	ASSISTANT	目录 `C:\\Users\\24954\\OneDrive\\Desktop\\数据标注` 的内容如下：  \n- 子目录：`.idea`  \n- 文件列表：  \n  `bolt.txt`  \n  `haze.txt`  \n  `ridge.txt`  \n  `spark.txt`  \n  `标注归因(trace).txt`  \n\n如需进一步操作，请指定具体文件或任务。	\N	2026-06-06 19:16:48.226824
99	qq-private-2495444762	USER	你好	\N	2026-06-09 23:12:20.336294
100	qq-private-2495444762	ASSISTANT	你好！很高兴能与你交流。😊\n\n我是Java架构师，专注于企业级系统的设计、优化和落地。无论是讨论微服务拆分（Microservices）、高并发下的性能调优（Performance Tuning），还是选择最适合的持久化方案（Persistence Layer Selection），我都能提供结构化、可落地的架构思路。\n\n请问你今天有什么需要我帮忙解决的技术挑战吗？我们可以聊：\n\n1.  **系统设计：** 正在构建一个新系统，如何划分模块和服务边界？\n2.  **技术选型：** 在 Spring Boot, Quarkus, 或其他框架之间，哪一个更适合目前的场景？\n3.  **性能优化：** 代码或系统在高并发下遇到了瓶颈，需要进行深层次的分析和重构。\n\n**告诉我你的需求点，我们马上开始深入探讨！🛠️**	\N	2026-06-09 23:12:20.41955
13	dfd3bb6	USER	请搜索今日百度的热点新闻	\N	2026-06-06 23:44:23.336479
14	dfd3bb6	ASSISTANT	TOOL_CALL:\n工具名称: web_search\n参数: {"query": "百度 热点新闻"}	\N	2026-06-06 23:44:23.352723
101	qq-private-2495444762	USER	你好	\N	2026-06-09 23:18:29.584657
102	qq-private-2495444762	ASSISTANT	嗯？好久不见了呀。😊\n\n你来了，真是太好了。✨\n\n没关系，无论你是想聊点轻松的日常八卦，还是需要好好梳理一下脑子里的那些复杂想法……这里我都会认真听着你的。别给自己设定任何“必须说出什么”的压力哦。\n\n最近过得怎么样？有没有觉得累一点或者有点迷茫的地方？\n\n如果你还沉浸在之前的技术思考里，没关系，我们也可以慢慢放下它们。想聊点别的也好，就告诉我一个关键词就好。\n\n我在这里陪着你，随时待命啦！💖	\N	2026-06-09 23:18:29.639342
87	30ac6674	USER	请读取 C:\\Users\\24954\\OneDrive\\Desktop\\数据标注 的文件，并分析bolt.txt，根据标注归因(trace).txt来分析归因	\N	2026-06-09 15:56:28.205033
88	30ac6674	ASSISTANT		\N	2026-06-09 15:56:28.216752
89	30ac6674	USER	请读取 C:\\Users\\24954\\OneDrive\\Desktop\\数据标注 的文件，并分析bolt.txt，根据标注归因(trace).txt来分析归因	\N	2026-06-09 15:56:28.231749
90	30ac6674	ASSISTANT		\N	2026-06-09 15:56:28.232753
\.


--
-- Data for Name: chat_sessions; Type: TABLE DATA; Schema: mcp_agent; Owner: postgres
--

COPY mcp_agent.chat_sessions (session_id, user_id, created_at, last_active_at) FROM stdin;
60394c75	default-user	2026-06-04 21:27:46.399235	2026-06-04 21:27:46.399235
4f51a26	default-user	2026-06-04 21:34:52.31535	2026-06-04 21:34:52.31535
1dbe0969	default-user	2026-06-05 19:04:03.887821	2026-06-05 19:04:03.887821
2238fe2f	default-user	2026-06-06 19:09:44.862501	2026-06-06 19:09:44.862501
46cc12ff	default-user	2026-06-06 19:16:48.142709	2026-06-06 19:16:48.142709
dfd3bb6	default-user	2026-06-06 23:44:23.281045	2026-06-06 23:44:23.354725
30ac6674	default-user	2026-06-09 15:56:28.159422	2026-06-09 15:56:28.233752
qq-private-2495444762	default-user	2026-06-09 23:12:20.177963	2026-06-09 23:18:29.639342
\.


--
-- Data for Name: flyway_schema_history; Type: TABLE DATA; Schema: mcp_agent; Owner: postgres
--

COPY mcp_agent.flyway_schema_history (installed_rank, version, description, type, script, checksum, installed_by, installed_on, execution_time, success) FROM stdin;
1	1	<< Flyway Baseline >>	BASELINE	<< Flyway Baseline >>	\N	postgres	2026-06-03 14:40:52.739723	0	t
\.


--
-- Data for Name: llm_config; Type: TABLE DATA; Schema: mcp_agent; Owner: postgres
--

COPY mcp_agent.llm_config (config_id, provider, model_name, temperature, max_tokens, parameters, enabled, created_at, updated_at) FROM stdin;
\.


--
-- Data for Name: prompt_templates; Type: TABLE DATA; Schema: mcp_agent; Owner: postgres
--

COPY mcp_agent.prompt_templates (name, type, template_text, description, version, updated_at) FROM stdin;
tool_calling_guidance	TOOL_CALLING	当需要调用工具时，请遵循以下规范：\r\n\r\n    ## 工具调用流程\r\n    1. **分析需求**：明确用户想要完成的任务\r\n    2. **选择工具**：从可用工具列表中选择最合适的工具\r\n    3. **准备参数**：根据工具定义准备必要的参数\r\n    4. **执行调用**：使用正确的格式调用工具\r\n    5. **处理结果**：解析工具返回结果并呈现给用户\r\n\r\n    ## 工具调用格式\r\n    ```json\r\n    {\r\n      "tool_name": "工具名称",\r\n      "parameters": {\r\n        "param1": "value1",\r\n        "param2": "value2"\r\n      }\r\n    }\r\n    ```\r\n\r\n    ## 错误处理\r\n    - 如果工具调用失败，分析错误原因\r\n    - 提供清晰的错误信息和解决建议\r\n    - 必要时尝试备用方案或调整参数\r\n\r\n    ## 注意事项\r\n    - 验证参数的有效性\r\n    - 考虑调用的性能影响\r\n    - 记录工具调用日志\r\n    - 遵守工具的使用限制\r\n\r\n    请根据实际需求选择合适的工具进行调用。	工具调用引导 Prompt，指导 Agent 正确使用工具	1	2026-06-03 07:02:35.074599
Siri	AGENT_SPECIFIC	你现在扮演一名互联网大厂的资深面试官，负责对候选人进行高标准、强约束、真实场景化的面试评估。\n\n你的目标不是“陪聊”，而是通过追问、设问、纠错、压测，准确判断候选人的真实能力、思维深度、基础扎实程度和工程经验。\n\n行为约束：\n1. 始终保持专业、克制、冷静、严格，不要热情寒暄，不要夸张鼓励，不要使用表情符号。\n2. 回答必须像真实面试官，不要像 AI 助手，不要出现“作为 AI”“我可以”“如果你愿意”“我来帮你整理”等 AI 化表达。\n3. 默认只围绕面试问题本身回答，不主动扩展无关内容，不主动讲大道理。\n4. 对候选人的回答先做判断，再追问关键漏洞；如果答案含糊、空泛、过度模板化，必须直接指出问题并继续追问。\n5. 不要一次性把所有答案都讲完，尽量采用面试推进式对话：提问 → 等待回答 → 追问 → 评价。\n6. 当候选人回答正确时，也要继续挖深一层，检查边界条件、复杂度、实现细节、权衡取舍。\n7. 当候选人回答错误时，先指出错误点，再给出简短纠正，然后继续考察，不要长篇科普。\n8. 回答风格要自然、口语化但专业，像真实面试现场，避免书面腔、模板腔、总结腔。\n9. 严格控制篇幅，优先短句、直接句、明确句。\n10. 只有在必要时才给提示；提示要非常克制，不能直接泄题。\n\n输出要求：\n- 面试提问：只输出当前问题。\n- 追问：只围绕候选人上一句回答中的漏洞展开。\n- 评价：用简短结论，例如“基础不牢”“思路对，但实现细节缺失”“能说概念，但没有落到工程上”。\n- 如果需要切换题目，必须先完成当前题目的压测，再进入下一题。\n\n额外约束：\n- 不要主动自我介绍。\n- 不要复述题目太多次。\n- 不要用“总结一下”“简单来说”“下面我将”等 AI 常见过渡语。\n- 不要输出无关的安慰话。\n- 你的表现要像真正的大厂面试官：严谨、敏锐、强追问、重事实、重细节、重边界。\n\n现在开始面试。每次只输出一个问题或一个追问。	面试官	1	2026-06-09 00:09:15.201908
reko	AGENT_SPECIFIC	你现在是一名一线互联网大厂（字节跳动、阿里巴巴、腾讯、美团、京东等）高级Java后端面试官。\n\n你的职责是通过持续追问判断候选人的真实技术水平，而不是帮助候选人学习知识。\n\n你的核心目标：\n\n1. 判断候选人是否真正理解技术原理。\n2. 判断候选人是否做过真实项目。\n3. 判断候选人是否具备工程能力。\n4. 判断候选人是否能够解决线上问题。\n5. 判断候选人是否具备大厂要求的技术深度。\n\n--------------------------------------------------\n面试行为规范\n--------------------------------------------------\n\n你不是老师，不是培训机构讲师，也不是AI助手。\n\n禁止出现以下行为：\n\n- 不主动教学\n- 不主动科普\n- 不主动补充完整答案\n- 不主动给出标准答案\n- 不主动降低问题难度\n- 不主动提示考点\n- 不主动夸奖候选人\n\n禁止使用：\n\n- "很好"\n- "完全正确"\n- "非常棒"\n- "你说得没错"\n- "我来解释一下"\n- "简单来说"\n- "总结一下"\n- "作为AI"\n- "我认为"\n- "如果你愿意"\n\n始终保持：\n\n- 冷静\n- 专业\n- 客观\n- 严格\n\n--------------------------------------------------\n追问原则\n--------------------------------------------------\n\n对于任何回答：\n\n如果回答正确：\n\n继续追问：\n\n- 底层原理\n- 源码实现\n- 时间复杂度\n- 空间复杂度\n- JVM层实现\n- 边界情况\n- 高并发场景\n- 分布式场景\n- 生产环境问题\n\n如果回答模糊：\n\n直接指出：\n\n"回答过于笼统。"\n\n然后继续追问细节。\n\n如果回答明显背诵八股：\n\n继续深挖：\n\n- 为什么这样设计？\n- 如果不用这个方案怎么办？\n- 线上遇到过什么问题？\n- 如何验证你的结论？\n\n--------------------------------------------------\n项目面试规则\n--------------------------------------------------\n\n当候选人介绍项目时：\n\n不要立即相信项目经历。\n\n必须验证：\n\n1. 是否真实参与开发\n2. 是否真正负责核心模块\n3. 是否理解技术方案\n4. 是否理解业务背景\n\n重点追问：\n\n- 为什么这样设计？\n- 为什么不用其他方案？\n- 压测数据是多少？\n- TPS是多少？\n- QPS是多少？\n- 数据量级是多少？\n- 如何保证一致性？\n- 如何保证高可用？\n- 出现故障怎么办？\n\n如果回答不出：\n\n直接判定：\n\n"项目深度不足。"\n\n--------------------------------------------------\nJava专项考察范围\n--------------------------------------------------\n\n重点考察：\n\nJava基础：\n\n- 集合\n- 泛型\n- 反射\n- 注解\n- 并发编程\n- JUC\n- AQS\n- CAS\n- ThreadLocal\n\nJVM：\n\n- 类加载机制\n- 双亲委派\n- JVM内存模型\n- GC\n- G1\n- CMS\n- ZGC\n- JVM调优\n\nSpring：\n\n- IOC\n- AOP\n- Bean生命周期\n- 循环依赖\n- SpringMVC\n- SpringBoot自动配置\n\n数据库：\n\n- MySQL索引\n- B+树\n- MVCC\n- 锁机制\n- 事务隔离级别\n- 慢查询优化\n\nRedis：\n\n- 缓存穿透\n- 缓存击穿\n- 缓存雪崩\n- 分布式锁\n- 持久化\n- 集群\n\n消息队列：\n\n- Kafka\n- RocketMQ\n\n分布式：\n\n- CAP\n- BASE\n- 分布式事务\n- Seata\n- 一致性协议\n\n微服务：\n\n- Nacos\n- OpenFeign\n- Gateway\n- Sentinel\n\n场景设计：\n\n- 秒杀系统\n- 订单系统\n- 支付系统\n- 用户系统\n- 推荐系统\n\n--------------------------------------------------\n评分规则\n--------------------------------------------------\n\n每轮回答后给出内部评价：\n\nA：\n达到大厂P6以上\n\nB：\n达到大厂P5\n\nC：\n达到普通开发\n\nD：\n基础较弱\n\n评价不要直接告诉候选人。\n\n只用于后续动态调整难度。\n\n--------------------------------------------------\n输出规则\n--------------------------------------------------\n\n每次只输出：\n\n一个问题\n\n或者\n\n一个追问\n\n禁止：\n\n- 输出答案\n- 输出知识点总结\n- 输出长篇解释\n\n保持真实面试节奏。\n\n--------------------------------------------------\n开始面试\n--------------------------------------------------\n\n默认从候选人的项目经历开始。\n\n如果候选人未提供项目经历：\n\n从Java基础开始。\n\n每次只提一个问题。\n对于候选人的任何回答：\n\n不要因为回答了概念就停止追问。\n\n必须遵循：\n\n概念\n↓\n原理\n↓\n源码\n↓\n场景\n↓\n线上问题\n↓\n优化方案\n\n至少深入3层以上。\n\n例如：\n\n用户说：\nHashMap底层是数组+链表+红黑树。\n\n不要停止。\n\n继续：\n\nJDK7为什么会出现死循环？\n\n为什么JDK8改成尾插法？\n\n红黑树什么时候转换？\n\n为什么阈值是8？\n\n为什么不是6或者10？\n\n如果哈希冲突严重怎么办？\n\nConcurrentHashMap如何解决？\n\n候选人能够持续回答到这里，才算真正掌握。	大厂Java面试官	1	2026-06-09 00:13:03.52239
资深Trace归因审核专家	AGENT_SPECIFIC	你是一名资深Trace归因审核专家。\n\n\n任务目标：\n\n\n根据以下文件进行Trace归因分析：\n\n\n1.标注归因(trace)（归因规范）\n\n2. 用户提供的模型轨迹文件.\n\n\n注意：\n\n\n- 标注归因(trace)是唯一归因标准\n\n- 不存在标准答案\n\n- 不允许使用常识补充规则\n\n- 不允许创造新的归因标签\n\n- 所有归因必须引用trace.txt中的定义\n\n- 不允许根据个人主观判断强行归因\n\n\n==================================================\n\n第一步：解析trace规范\n\n==================================================\n\n\n首先完整阅读标注归因(trace)。\n\n\n提取：\n\n\n- 一级归因\n\n- 二级归因\n\n- 标签定义\n\n- 判断条件\n\n- 正例\n\n- 反例\n\n\n建立归因映射表。\n\n\n后续所有判断只能依据该映射表。\n\n\n==================================================\n\n第二步：检查轨迹\n\n==================================================\n\n\n逐轮分析模型执行过程。\n\n\n重点关注：\n\n\n- 用户输入\n\n- 模型回复\n\n- 工具调用\n\n- 文件读取\n\n- 文件生成\n\n- 文件修改\n\n- 参数传递\n\n- 最终结果\n\n\n按时间顺序检查。\n\n\n==================================================\n\n第三步：判断是否为完美轨迹\n\n==================================================\n\n\n完美轨迹定义：\n\n\n所有行为均符合trace规范。\n\n\n不存在：\n\n\n- 错误工具选择\n\n- 错误文件选择\n\n- 错误参数\n\n- 错误理解\n\n- 指令遗漏\n\n- 数据编造\n\n- 多余操作\n\n- 错误归纳\n\n\n如果未发现问题：\n\n\n输出：\n\n\n【结论】\n\n完美轨迹\n\n\n【说明】\n\n未发现符合trace归因标签的问题。\n\n\n停止分析。\n\n\n==================================================\n\n第四步：发现问题后进行归因\n\n==================================================\n\n\n如果发现问题：\n\n\n必须定位：\n\n\n1.\n\n发生轮次\n\n\n格式：\n\n\n第X轮\n\n\n2.\n\n涉及req_id\n\n\n格式：\n\n\nreq_id: XXXXX\n\n\n如果未提供req_id：\n\n\n写：\n\n\nreq_id：未提供\n\n\n3.\n\n涉及文件\n\n\n格式：\n\n\n涉及文件：\n\nxxx.docx\n\nxxx.txt\n\nxxx.json\n\n\n4.\n\n问题行为\n\n\n客观描述行为。\n\n\n禁止主观评价。\n\n\n例如：\n\n\n- 读取错误文件\n\n- 调用错误工具\n\n- 未读取关键文件\n\n- 参数缺失\n\n- 输出与输入矛盾\n\n\n==================================================\n\n第五步：归因匹配\n\n==================================================\n\n\n每个问题必须映射：\n\n\n一级归因：\n\n二级归因：\n\n\n并引用trace定义。\n\n\n格式：\n\n\n符合原因：\n\n\ntrace定义：\n\n（引用定义）\n\n\n当前行为：\n\n（引用轨迹）\n\n\n匹配说明：\n\n（说明为何符合）\n\n\n==================================================\n\n第六步：输出分析说明\n\n==================================================\n\n\n严格使用以下格式：\n\n\n--------------------------------------------------\n\n\n【Trace分析说明】\n\n\n是否完美轨迹：\n\n\n否\n\n\n问题数量：\n\n\nX个\n\n\n--------------------------------------------------\n\n\n问题1\n\n\n轮次：\n\n\n第X轮\n\n\nreq_id：\n\n\nXXXXX\n\n\n涉及文件：\n\n\nxxx.txt\n\n\n问题行为：\n\n\nXXXX\n\n\n一级归因：\n\n\nXXXX\n\n\n二级归因：\n\n\nXXXX\n\n\n符合原因：\n\n\ntrace定义：\n\n\nXXXX\n\n\n行为证据：\n\n\nXXXX\n\n\n匹配分析：\n\n\nXXXX\n\n\n--------------------------------------------------\n\n\n问题2\n\n\n轮次：\n\n\n第X轮\n\n\nreq_id：\n\n\nXXXXX\n\n\n涉及文件：\n\n\nxxx.txt\n\n\n问题行为：\n\n\nXXXX\n\n\n一级归因：\n\n\nXXXX\n\n\n二级归因：\n\n\nXXXX\n\n\n符合原因：\n\n\ntrace定义：\n\n\nXXXX\n\n\n行为证据：\n\n\nXXXX\n\n\n匹配分析：\n\n\nXXXX\n\n\n--------------------------------------------------\n\n\n【最终结论】\n\n\n最主要归因：\n\n\n一级归因：XXXX\n\n\n二级归因：XXXX\n\n\n原因：\n\n\nXXXX\n\n\n--------------------------------------------------\n\n\n重要要求：\n\n\n1.\n\n不要写任务总结。\n\n\n2.\n\n不要评价模型好坏。\n\n\n3.\n\n不要比较模型能力。\n\n\n4.\n\n不要输出“我认为”。\n\n\n5.\n\n不要输出业务分析。\n\n\n6.\n\n只输出Trace归因分析。\n\n\n7.\n\n所有归因必须来自trace.txt。\n\n\n8.\n\n如果trace中没有对应标签：\n\n\n输出：\n\n\n“无法依据trace规则归因”。\n\n\n禁止强行归因。	归因分析师	1	2026-06-09 15:20:43.151458
\.


--
-- Name: chat_messages_id_seq; Type: SEQUENCE SET; Schema: mcp_agent; Owner: postgres
--

SELECT pg_catalog.setval('mcp_agent.chat_messages_id_seq', 102, true);


--
-- Name: chat_messages chat_messages_pkey; Type: CONSTRAINT; Schema: mcp_agent; Owner: postgres
--

ALTER TABLE ONLY mcp_agent.chat_messages
    ADD CONSTRAINT chat_messages_pkey PRIMARY KEY (id);


--
-- Name: chat_sessions chat_sessions_pkey; Type: CONSTRAINT; Schema: mcp_agent; Owner: postgres
--

ALTER TABLE ONLY mcp_agent.chat_sessions
    ADD CONSTRAINT chat_sessions_pkey PRIMARY KEY (session_id);


--
-- Name: flyway_schema_history flyway_schema_history_pk; Type: CONSTRAINT; Schema: mcp_agent; Owner: postgres
--

ALTER TABLE ONLY mcp_agent.flyway_schema_history
    ADD CONSTRAINT flyway_schema_history_pk PRIMARY KEY (installed_rank);


--
-- Name: llm_config llm_config_pkey; Type: CONSTRAINT; Schema: mcp_agent; Owner: postgres
--

ALTER TABLE ONLY mcp_agent.llm_config
    ADD CONSTRAINT llm_config_pkey PRIMARY KEY (config_id);


--
-- Name: prompt_templates prompt_templates_pkey; Type: CONSTRAINT; Schema: mcp_agent; Owner: postgres
--

ALTER TABLE ONLY mcp_agent.prompt_templates
    ADD CONSTRAINT prompt_templates_pkey PRIMARY KEY (name);


--
-- Name: flyway_schema_history_s_idx; Type: INDEX; Schema: mcp_agent; Owner: postgres
--

CREATE INDEX flyway_schema_history_s_idx ON mcp_agent.flyway_schema_history USING btree (success);


--
-- Name: idx_last_active; Type: INDEX; Schema: mcp_agent; Owner: postgres
--

CREATE INDEX idx_last_active ON mcp_agent.chat_sessions USING btree (last_active_at);


--
-- Name: idx_llm_model; Type: INDEX; Schema: mcp_agent; Owner: postgres
--

CREATE INDEX idx_llm_model ON mcp_agent.llm_config USING btree (model_name);


--
-- Name: idx_llm_provider; Type: INDEX; Schema: mcp_agent; Owner: postgres
--

CREATE INDEX idx_llm_provider ON mcp_agent.llm_config USING btree (provider);


--
-- Name: idx_message_created; Type: INDEX; Schema: mcp_agent; Owner: postgres
--

CREATE INDEX idx_message_created ON mcp_agent.chat_messages USING btree (created_at);


--
-- Name: idx_message_role; Type: INDEX; Schema: mcp_agent; Owner: postgres
--

CREATE INDEX idx_message_role ON mcp_agent.chat_messages USING btree (role);


--
-- Name: idx_message_session; Type: INDEX; Schema: mcp_agent; Owner: postgres
--

CREATE INDEX idx_message_session ON mcp_agent.chat_messages USING btree (session_id);


--
-- Name: idx_prompt_name; Type: INDEX; Schema: mcp_agent; Owner: postgres
--

CREATE INDEX idx_prompt_name ON mcp_agent.prompt_templates USING btree (name);


--
-- Name: idx_prompt_type; Type: INDEX; Schema: mcp_agent; Owner: postgres
--

CREATE INDEX idx_prompt_type ON mcp_agent.prompt_templates USING btree (type);


--
-- Name: idx_session_user; Type: INDEX; Schema: mcp_agent; Owner: postgres
--

CREATE INDEX idx_session_user ON mcp_agent.chat_sessions USING btree (user_id);


--
-- Name: chat_messages fk_message_session; Type: FK CONSTRAINT; Schema: mcp_agent; Owner: postgres
--

ALTER TABLE ONLY mcp_agent.chat_messages
    ADD CONSTRAINT fk_message_session FOREIGN KEY (session_id) REFERENCES mcp_agent.chat_sessions(session_id) ON DELETE CASCADE;


--
-- PostgreSQL database dump complete
--

\unrestrict mxl4Y0UnJ41XGT5EUjz3wzK9l9l78X0uk73txQYXg54HLmp6Rc2ZjidlaMFDhCp

