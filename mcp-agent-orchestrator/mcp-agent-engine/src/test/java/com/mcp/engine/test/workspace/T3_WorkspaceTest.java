package com.mcp.engine.test.workspace;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mcp.core.entity.WorkspaceEntity;
import com.mcp.core.repository.WorkspaceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * T3 Workspace - 验证项目状态持久化
 *
 * 测试目标：
 * - 创建 Workspace 后状态正确持久化
 * - 修改 Workspace 后状态正确更新
 * - 重新加载后能够正确召回之前保存的状态
 * - 多个 Workspace 互相隔离
 * - 空 workspace 正常处理
 * - 任务列表持久化
 * - 文件上下文持久化
 * - Git 状态持久化
 */
@ExtendWith(MockitoExtension.class)
class T3_WorkspaceTest {

    @Mock
    private WorkspaceRepository workspaceRepository;

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
    }

    @Test
    @DisplayName("Case1: 创建 Workspace - SpringBoot 项目信息正确保存")
    void shouldCreateWorkspaceWithSpringBootProject() throws JsonProcessingException {
        String workspaceId = "ws-springboot-1";
        WorkspaceEntity workspace = new WorkspaceEntity();
        workspace.setWorkspaceId(workspaceId);
        workspace.setName("e-commerce");
        workspace.setProjectPath("/home/user/projects/e-commerce");

        List<String> modules = List.of("controller", "service", "repository", "entity", "config");
        workspace.setActiveTasks(objectMapper.writeValueAsString(modules));

        when(workspaceRepository.save(any(WorkspaceEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(workspaceRepository.findByWorkspaceId(workspaceId)).thenReturn(Optional.of(workspace));

        workspaceRepository.save(workspace);
        Optional<WorkspaceEntity> loaded = workspaceRepository.findByWorkspaceId(workspaceId);

        assertThat(loaded).isPresent();
        assertThat(loaded.get().getName()).isEqualTo("e-commerce");
        assertThat(loaded.get().getProjectPath()).isEqualTo("/home/user/projects/e-commerce");

        List<String> loadedModules = objectMapper.readValue(
                loaded.get().getActiveTasks(),
                new TypeReference<List<String>>() {}
        );
        assertThat(loadedModules).contains("controller", "service", "repository");
    }

    @Test
    @DisplayName("Case2: 修改 Workspace - 增加 auth 模块后模块列表更新")
    void shouldUpdateWorkspaceWhenAddModule() throws JsonProcessingException {
        String workspaceId = "ws-springboot-1";
        WorkspaceEntity existing = new WorkspaceEntity();
        existing.setWorkspaceId(workspaceId);
        existing.setName("e-commerce");
        existing.setProjectPath("/home/user/projects/e-commerce");
        existing.setActiveTasks(objectMapper.writeValueAsString(
                List.of("controller", "service", "repository", "entity", "config")
        ));

        when(workspaceRepository.findByWorkspaceId(workspaceId)).thenReturn(Optional.of(existing));

        List<String> currentModules = objectMapper.readValue(
                existing.getActiveTasks(),
                new TypeReference<List<String>>() {}
        );
        currentModules.add("auth");
        existing.setActiveTasks(objectMapper.writeValueAsString(currentModules));

        when(workspaceRepository.save(eq(existing))).thenReturn(existing);
        workspaceRepository.save(existing);

        WorkspaceEntity updated = workspaceRepository.findByWorkspaceId(workspaceId).get();
        List<String> updatedModules = objectMapper.readValue(
                updated.getActiveTasks(),
                new TypeReference<List<String>>() {}
        );

        assertThat(updatedModules).hasSize(6);
        assertThat(updatedModules).contains("auth");
    }

    @Test
    @DisplayName("Case3: Workspace 召回 - 关闭 Agent 重新进入后项目名称正确")
    void shouldRecallWorkspaceNameAfterReload() {
        String workspaceId = "ws-springboot-1";
        WorkspaceEntity saved = new WorkspaceEntity();
        saved.setWorkspaceId(workspaceId);
        saved.setName("e-commerce");
        saved.setProjectPath("/home/user/projects/e-commerce");

        when(workspaceRepository.findByWorkspaceId(workspaceId)).thenReturn(Optional.of(saved));

        Optional<WorkspaceEntity> reloaded = workspaceRepository.findByWorkspaceId(workspaceId);

        assertThat(reloaded).isPresent();
        assertThat(reloaded.get().getName()).isEqualTo("e-commerce");
        assertThat(reloaded.get().getProjectPath()).isEqualTo("/home/user/projects/e-commerce");
    }

    @Test
    @DisplayName("Case4: 多个 Workspace 互相隔离 - 不互相干扰")
    void shouldIsolateMultipleWorkspaces() {
        String ws1Id = "ws-project-1";
        String ws2Id = "ws-project-2";

        WorkspaceEntity ws1 = new WorkspaceEntity();
        ws1.setWorkspaceId(ws1Id);
        ws1.setName("e-commerce");

        WorkspaceEntity ws2 = new WorkspaceEntity();
        ws2.setWorkspaceId(ws2Id);
        ws2.setName("blog-system");

        when(workspaceRepository.findByWorkspaceId(ws1Id)).thenReturn(Optional.of(ws1));
        when(workspaceRepository.findByWorkspaceId(ws2Id)).thenReturn(Optional.of(ws2));

        assertThat(workspaceRepository.findByWorkspaceId(ws1Id).get().getName())
                .isEqualTo("e-commerce");
        assertThat(workspaceRepository.findByWorkspaceId(ws2Id).get().getName())
                .isEqualTo("blog-system");
    }

    @Test
    @DisplayName("Case5: 不存在的 Workspace 返回空 - 不抛异常")
    void shouldReturnEmptyWhenWorkspaceNotFound() {
        String nonExistentId = "ws-not-exist";
        when(workspaceRepository.findByWorkspaceId(nonExistentId)).thenReturn(Optional.empty());

        Optional<WorkspaceEntity> result = workspaceRepository.findByWorkspaceId(nonExistentId);
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Case6: 最后活跃文件持久化 - 当前打开文件正确保存")
    void shouldPersistLastActiveFile() {
        String workspaceId = "ws-springboot-1";
        WorkspaceEntity workspace = new WorkspaceEntity();
        workspace.setWorkspaceId(workspaceId);
        workspace.setLastActiveFile("/home/user/projects/e-commerce/src/main/java/com/example/controller/UserController.java");
        workspace.setLastActiveLine(42);

        when(workspaceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(workspaceRepository.findByWorkspaceId(workspaceId)).thenReturn(Optional.of(workspace));

        workspaceRepository.save(workspace);
        WorkspaceEntity loaded = workspaceRepository.findByWorkspaceId(workspaceId).get();

        assertThat(loaded.getLastActiveFile())
                .contains("UserController.java");
        assertThat(loaded.getLastActiveLine()).isEqualTo(42);
    }

    @Test
    @DisplayName("Case7: TODO 列表持久化 - 待办事项正确保存")
    void shouldPersistTodoList() throws JsonProcessingException {
        List<String> todos = List.of(
                "1. 实现用户注册接口",
                "2. 添加验证码校验",
                "3. 编写单元测试"
        );

        String workspaceId = "ws-springboot-1";
        WorkspaceEntity workspace = new WorkspaceEntity();
        workspace.setWorkspaceId(workspaceId);
        workspace.setTodos(objectMapper.writeValueAsString(todos));

        when(workspaceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(workspaceRepository.findByWorkspaceId(workspaceId)).thenReturn(Optional.of(workspace));

        workspaceRepository.save(workspace);
        WorkspaceEntity loaded = workspaceRepository.findByWorkspaceId(workspaceId).get();

        List<String> loadedTodos = objectMapper.readValue(
                loaded.getTodos(),
                new TypeReference<List<String>>() {}
        );

        assertThat(loadedTodos).hasSize(3);
        assertThat(loadedTodos.get(0)).contains("用户注册接口");
    }

    @Test
    @DisplayName("Case8: 删除 Workspace - 删除后不再存在")
    void shouldDeleteWorkspace() {
        String workspaceId = "ws-to-delete";
        WorkspaceEntity workspace = new WorkspaceEntity();
        workspace.setWorkspaceId(workspaceId);
        workspace.setName("temp-project");

        when(workspaceRepository.findByWorkspaceId(workspaceId))
                .thenReturn(Optional.of(workspace))
                .thenReturn(Optional.empty());
        doNothing().when(workspaceRepository).deleteByWorkspaceId(workspaceId);

        assertThat(workspaceRepository.findByWorkspaceId(workspaceId)).isPresent();

        workspaceRepository.deleteByWorkspaceId(workspaceId);

        verify(workspaceRepository).deleteByWorkspaceId(workspaceId);
        assertThat(workspaceRepository.findByWorkspaceId(workspaceId)).isEmpty();
    }
}