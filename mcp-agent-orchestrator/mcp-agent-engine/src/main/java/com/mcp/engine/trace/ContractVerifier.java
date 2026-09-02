package com.mcp.engine.trace;

import java.util.ArrayList;
import java.util.List;

/**
 * 契约验证器 — 对一组 SessionEvent 执行所有已注册的 ExecutionContract 并生成验证报告。
 *
 * 使用方式：
 * <pre>
 * ContractVerifier verifier = ContractVerifier.createDefault();
 * ContractReport report = verifier.verify(trace.getEvents());
 * if (!report.allPassed()) {
 *     report.violations().forEach(v -> log.warn("Contract violation: {}", v));
 * }
 * </pre>
 */
public class ContractVerifier {

    private final List<ExecutionContract> contracts;

    private ContractVerifier(List<ExecutionContract> contracts) {
        this.contracts = List.copyOf(contracts);
    }

    public static ContractVerifier createDefault() {
        List<ExecutionContract> contracts = new ArrayList<>();
        contracts.add(ExecutionContract.contextClassificationMustExist());
        contracts.add(ExecutionContract.systemPromptMustExist());
        contracts.add(ExecutionContract.searchAgentMustExecuteTools());
        contracts.add(ExecutionContract.searchAgentMustHaveToolResults());
        contracts.add(ExecutionContract.docxGenerationMustRouteToSearch());
        contracts.add(ExecutionContract.toolCallMustHaveResult());
        return new ContractVerifier(contracts);
    }

    public static ContractVerifier of(List<ExecutionContract> contracts) {
        return new ContractVerifier(contracts);
    }

    public ContractReport verify(List<SessionEvent> events) {
        List<ExecutionContract.ContractResult> results = new ArrayList<>();
        int passed = 0;
        int failed = 0;

        for (ExecutionContract contract : contracts) {
            ExecutionContract.ContractResult result = contract.verify(events);
            results.add(result);
            if (result.passed()) {
                passed++;
            } else {
                failed++;
            }
        }

        return new ContractReport(passed, failed, results);
    }

    public record ContractReport(int passed, int failed, List<ExecutionContract.ContractResult> results) {
        public boolean allPassed() {
            return failed == 0;
        }

        public List<ExecutionContract.ContractResult> violations() {
            return results.stream().filter(r -> !r.passed()).toList();
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("Contract Verification: ").append(passed).append(" passed, ").append(failed).append(" failed");
            if (failed > 0) {
                sb.append("\nViolations:");
                for (ExecutionContract.ContractResult r : violations()) {
                    sb.append("\n  - [").append(r.contractName()).append("] ").append(r.detail());
                }
            }
            return sb.toString();
        }
    }
}