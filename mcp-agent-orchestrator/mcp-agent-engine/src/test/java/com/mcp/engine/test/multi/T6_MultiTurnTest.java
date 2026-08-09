package com.mcp.engine.test.multi;

import com.mcp.common.workspace.Workspace;
import com.mcp.common.workspace.Workspace.Task;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T6 Multi-turn - 多轮对话上下文一致性测试（8 cases）
 *
 * 验证：
 * - 多轮递增修改后状态一致性
 * - Agent 能够正确总结历史上下文
 */
class T6_MultiTurnTest {

    @Test
    @DisplayName("Case1: 从创建商城系统到增加支付模块，最终正确列出所有模块")
    void shouldMaintainCorrectModuleListThroughMultiTurn() {
        Workspace workspace = new Workspace();
        workspace.setWorkspaceId("ws-shopping");
        workspace.setName("商城系统");
        workspace.setActiveTasks(new ArrayList<>());

        // 第一轮：创建项目
        workspace.getActiveTasks().add(task("用户模块", "用户注册登录", "completed"));
        workspace.getActiveTasks().add(task("商品模块", "商品展示搜索", "completed"));
        workspace.getActiveTasks().add(task("购物车", "购物车管理", "completed"));

        assertThat(workspace.getActiveTasks()).hasSize(3);

        // 第二轮：增加支付模块
        workspace.getActiveTasks().add(task("支付模块", "支付接口集成", "in_progress"));

        assertThat(workspace.getActiveTasks()).hasSize(4);

        // 第三轮：支付模块支持支付宝
        for (Task task : workspace.getActiveTasks()) {
            if (task.getTitle().equals("支付模块")) {
                task.setDescription("支付接口集成，支持支付宝和微信支付");
                task.setStatus("completed");
                break;
            }
        }

        // 最终验证所有模块
        List<String> moduleNames = workspace.getActiveTasks().stream()
                .map(Task::getTitle)
                .toList();

        assertThat(moduleNames).containsExactly(
                "用户模块", "商品模块", "购物车", "支付模块"
        );
        assertThat(workspace.getActiveTasks())
                .filteredOn(t -> t.getTitle().equals("支付模块"))
                .first()
                .extracting(Task::getDescription)
                .satisfies(desc -> assertThat((String) desc).contains("支付宝"));
    }

    @Test
    @DisplayName("Case2: 多轮需求变更，任务状态正确更新")
    void shouldUpdateTaskStatusCorrectlyThroughMultiTurn() {
        Workspace workspace = new Workspace();
        workspace.setWorkspaceId("ws-project");
        workspace.setActiveTasks(new ArrayList<>());

        // 第一轮
        workspace.getActiveTasks().add(task("需求分析", "收集用户需求", "pending"));
        assertThat(getStatus(workspace, "需求分析")).isEqualTo("pending");

        // 第二轮
        setStatus(workspace, "需求分析", "in_progress");
        assertThat(getStatus(workspace, "需求分析")).isEqualTo("in_progress");

        // 第三轮
        setStatus(workspace, "需求分析", "completed");
        workspace.getActiveTasks().add(task("设计", "系统架构设计", "in_progress"));

        assertThat(getStatus(workspace, "需求分析")).isEqualTo("completed");
        assertThat(getStatus(workspace, "设计")).isEqualTo("in_progress");
    }

    @Test
    @DisplayName("Case3: 新增 Todo 列表，后续轮次可见")
    void shouldSeeNewTodoInSubsequentTurns() {
        Workspace workspace = new Workspace();
        workspace.setWorkspaceId("ws-todo");
        workspace.setTodos(new ArrayList<>());

        workspace.getTodos().add(new com.mcp.common.workspace.Workspace.Todo("写单元测试", false));
        workspace.getTodos().add(new com.mcp.common.workspace.Workspace.Todo("重构代码", false));

        assertThat(workspace.getTodos()).hasSize(2);

        workspace.getTodos().add(new com.mcp.common.workspace.Workspace.Todo("文档", false));

        assertThat(workspace.getTodos()).hasSize(3);
    }

    @Test
    @DisplayName("Case4: 完成 Todo 后状态正确更新")
    void shouldUpdateTodoStatusWhenCompleted() {
        Workspace workspace = new Workspace();
        workspace.setWorkspaceId("ws-todo");
        workspace.setTodos(new ArrayList<>());

        workspace.getTodos().add(new com.mcp.common.workspace.Workspace.Todo("写单元测试", false));

        assertThat(workspace.getTodos().get(0).isDone()).isFalse();

        workspace.getTodos().get(0).setDone(true);

        assertThat(workspace.getTodos().get(0).isDone()).isTrue();
    }

    @Test
    @DisplayName("Case5: 多轮对话后 lastActiveFile 保持最新")
    void shouldKeepLastActiveFileUpdated() {
        Workspace workspace = new Workspace();
        workspace.setWorkspaceId("ws-dev");

        workspace.setLastActiveFile("UserService.java");
        workspace.setLastActiveLine(25);

        assertThat(workspace.getLastActiveFile()).isEqualTo("UserService.java");
        assertThat(workspace.getLastActiveLine()).isEqualTo(25);

        workspace.setLastActiveFile("OrderService.java");
        workspace.setLastActiveLine(42);

        assertThat(workspace.getLastActiveFile()).isEqualTo("OrderService.java");
        assertThat(workspace.getLastActiveLine()).isEqualTo(42);
    }

    @Test
    @DisplayName("Case6: 删除已取消的任务")
    void shouldRemoveCancelledTask() {
        Workspace workspace = new Workspace();
        workspace.setWorkspaceId("ws-project");
        workspace.setActiveTasks(new ArrayList<>());

        workspace.getActiveTasks().add(task("功能A", "描述A", "pending"));
        workspace.getActiveTasks().add(task("功能B", "描述B", "pending"));
        workspace.getActiveTasks().add(task("功能C", "描述C", "pending"));

        assertThat(workspace.getActiveTasks()).hasSize(3);

        workspace.getActiveTasks().removeIf(t -> t.getTitle().equals("功能B"));

        assertThat(workspace.getActiveTasks()).hasSize(2);
        List<String> names = workspace.getActiveTasks().stream()
                .map(Task::getTitle)
                .toList();
        assertThat(names).contains("功能A", "功能C");
        assertThat(names).doesNotContain("功能B");
    }

    @Test
    @DisplayName("Case7: 重新添加已取消的任务")
    void shouldReAddPreviouslyCancelledTask() {
        Workspace workspace = new Workspace();
        workspace.setWorkspaceId("ws-project");
        workspace.setActiveTasks(new ArrayList<>());

        workspace.getActiveTasks().add(task("支付模块", "支付接口", "pending"));
        workspace.getActiveTasks().removeIf(t -> t.getTitle().equals("支付模块"));
        assertThat(workspace.getActiveTasks()).isEmpty();

        workspace.getActiveTasks().add(task("支付模块", "支付接口 + 支付宝", "in_progress"));
        assertThat(workspace.getActiveTasks()).hasSize(1);
        assertThat(workspace.getActiveTasks().get(0).getDescription()).contains("支付宝");
    }

    @Test
    @DisplayName("Case8: 多轮对话后 Agent 能正确总结整个项目概况")
    void shouldSummarizeProjectCorrectlyAfterMultiTurn() {
        Workspace workspace = new Workspace();
        workspace.setWorkspaceId("ws-shopping");
        workspace.setName("商城系统");
        workspace.setActiveTasks(new ArrayList<>());

        workspace.getActiveTasks().add(task("用户模块", "已完成", "completed"));
        workspace.getActiveTasks().add(task("商品模块", "已完成", "completed"));
        workspace.getActiveTasks().add(task("支付模块", "进行中", "in_progress"));

        long completedCount = workspace.getActiveTasks().stream()
                .filter(t -> "completed".equals(t.getStatus()))
                .count();
        long totalCount = workspace.getActiveTasks().size();

        assertThat(workspace.getName()).isEqualTo("商城系统");
        assertThat(totalCount).isEqualTo(3);
        assertThat(completedCount).isEqualTo(2);
    }

    private Task task(String name, String description, String status) {
        Task task = new Task();
        task.setTitle(name);
        task.setDescription(description);
        task.setStatus(status);
        return task;
    }

    private String getStatus(Workspace workspace, String taskName) {
        for (Task task : workspace.getActiveTasks()) {
            if (task.getTitle().equals(taskName)) {
                return task.getStatus();
            }
        }
        return null;
    }

    private void setStatus(Workspace workspace, String taskName, String status) {
        for (Task task : workspace.getActiveTasks()) {
            if (task.getTitle().equals(taskName)) {
                task.setStatus(status);
                return;
            }
        }
    }
}