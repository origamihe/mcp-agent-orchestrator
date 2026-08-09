package com.mcp.engine.trace;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Trace 收集器 — 负责收集和存储每次 Agent 调用的 TraceRecord。
 *
 * 默认实现为 NOOP（不收集），生产环境可注入持久化实现。
 * 测试环境使用 InMemoryCollector 进行验证。
 */
public interface TraceCollector {

    void record(TraceRecord record);

    /**
     * 默认 NOOP 实现 — 生产环境不收集 Trace 时使用。
     */
    TraceCollector NOOP = record -> {};

    /**
     * 内存收集器 — 用于测试和开发环境。
     * 保留最近 N 条记录，便于验证 Trace 是否正确生成。
     */
    class InMemory implements TraceCollector {
        private final List<TraceRecord> records = new ArrayList<>();
        private final int maxSize;

        public InMemory(int maxSize) {
            this.maxSize = maxSize;
        }

        public InMemory() {
            this(100);
        }

        @Override
        public void record(TraceRecord record) {
            if (records.size() >= maxSize) {
                records.remove(0);
            }
            records.add(record);
        }

        public List<TraceRecord> getRecords() {
            return Collections.unmodifiableList(records);
        }

        public TraceRecord getLatest() {
            return records.isEmpty() ? null : records.get(records.size() - 1);
        }

        public int size() {
            return records.size();
        }

        public void clear() {
            records.clear();
        }
    }
}