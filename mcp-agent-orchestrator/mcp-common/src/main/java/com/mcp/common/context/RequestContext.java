package com.mcp.common.context;

import com.mcp.common.identity.GroupContext;
import com.mcp.common.identity.MemoryIdentity;
import com.mcp.common.identity.UserProfile;
import com.mcp.common.channel.SessionState;
import com.mcp.common.channel.WorkingContext;
import com.mcp.common.workspace.Workspace;
import lombok.Builder;
import lombok.Data;

/**
 * 统一请求上下文 — 贯穿整个处理链路的单一上下文对象。
 *
 * 解决以往需要层层传递 UserProfile、GroupContext、SessionState 等参数导致的
 * "参数管道"问题。未来新增字段（如 language、device、permission）只需在此类增加，
 * 无需修改任何接口签名。
 */
@Data
@Builder
public class RequestContext {

    /** 用户身份标识 */
    private final MemoryIdentity identity;

    /** 用户个人资料（包含角色、权限、关系） */
    private final UserProfile userProfile;

    /** 群组上下文（包含群名、群成员、权限等） */
    private final GroupContext groupContext;

    /** 会话状态 */
    private final SessionState sessionState;

    /** 运行时工作上下文 */
    private final WorkingContext workingContext;

    /** 绑定的工作空间 */
    private final Workspace workspace;

    /** 用户输入消息 */
    private final String userMessage;

    /** 调用方提供的系统 Prompt（可为 null，从数据库读取） */
    private final String systemPrompt;

    /** 指定的模型配置 ID（可为 null，使用默认） */
    private final String modelConfigId;
}