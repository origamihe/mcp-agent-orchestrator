package com.mcp.common.planner;

import java.util.ArrayList;
import java.util.List;

/**
 * DAG 任务计划，包含一组节点和它们之间的依赖关系。
 */
public class PlanDAG {

    private String id;
    private String intent;
    private String reasoning;
    private List<PlanNode> nodes;
    private int estimatedComplexity;
    private List<String> risks;
    private String testStrategy;

    public PlanDAG() {
        this.nodes = new ArrayList<>();
        this.risks = new ArrayList<>();
        this.estimatedComplexity = 1;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final PlanDAG dag = new PlanDAG();

        public Builder id(String id) { dag.id = id; return this; }
        public Builder intent(String intent) { dag.intent = intent; return this; }
        public Builder reasoning(String reasoning) { dag.reasoning = reasoning; return this; }
        public Builder addNode(PlanNode node) { dag.nodes.add(node); return this; }
        public Builder nodes(List<PlanNode> nodes) { dag.nodes.addAll(nodes); return this; }
        public Builder estimatedComplexity(int estimatedComplexity) { dag.estimatedComplexity = estimatedComplexity; return this; }
        public Builder addRisk(String risk) { dag.risks.add(risk); return this; }
        public Builder testStrategy(String testStrategy) { dag.testStrategy = testStrategy; return this; }

        public PlanDAG build() {
            validateDAG(dag);
            return dag;
        }
    }

    private static void validateDAG(PlanDAG dag) {
        for (PlanNode node : dag.nodes) {
            for (String dep : node.getDependsOn()) {
                boolean found = dag.nodes.stream().anyMatch(n -> n.getId().equals(dep));
                if (!found) {
                    throw new IllegalArgumentException(
                            "Node '" + node.getId() + "' depends on non-existent node '" + dep + "'");
                }
            }
        }
        if (hasCycle(dag)) {
            throw new IllegalArgumentException("Plan DAG contains a cycle");
        }
    }

    static boolean hasCycle(PlanDAG dag) {
        java.util.Set<String> visited = new java.util.HashSet<>();
        java.util.Set<String> inStack = new java.util.HashSet<>();

        for (PlanNode node : dag.nodes) {
            if (hasCycleDfs(node.getId(), dag, visited, inStack)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasCycleDfs(String nodeId, PlanDAG dag,
                                        java.util.Set<String> visited,
                                        java.util.Set<String> inStack) {
        if (inStack.contains(nodeId)) return true;
        if (visited.contains(nodeId)) return false;

        visited.add(nodeId);
        inStack.add(nodeId);

        PlanNode node = dag.nodes.stream()
                .filter(n -> n.getId().equals(nodeId))
                .findFirst().orElse(null);
        if (node != null) {
            for (String dep : node.getDependsOn()) {
                if (hasCycleDfs(dep, dag, visited, inStack)) {
                    return true;
                }
            }
        }

        inStack.remove(nodeId);
        return false;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getIntent() { return intent; }
    public void setIntent(String intent) { this.intent = intent; }
    public String getReasoning() { return reasoning; }
    public void setReasoning(String reasoning) { this.reasoning = reasoning; }
    public List<PlanNode> getNodes() { return nodes; }
    public void setNodes(List<PlanNode> nodes) { this.nodes = nodes; }
    public int getEstimatedComplexity() { return estimatedComplexity; }
    public void setEstimatedComplexity(int estimatedComplexity) { this.estimatedComplexity = estimatedComplexity; }
    public List<String> getRisks() { return risks; }
    public void setRisks(List<String> risks) { this.risks = risks; }
    public String getTestStrategy() { return testStrategy; }
    public void setTestStrategy(String testStrategy) { this.testStrategy = testStrategy; }
}