package com.apiweb.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link AfterCommit} 契约测试。
 *
 * <p>锁定「派发动作必须在事务提交后执行」这一语义，防止 UI / 性能报告再次出现
 * 「异步线程读不到未提交记录 → 报告永久 PENDING」的回归。</p>
 */
class AfterCommitTest {

    @Test
    @DisplayName("无活动事务时立即执行")
    void runsImmediatelyWithoutTransaction() {
        AtomicInteger counter = new AtomicInteger();
        AfterCommit.run(counter::incrementAndGet);
        assertEquals(1, counter.get());
    }

    @Test
    @DisplayName("null 动作安全忽略")
    void ignoresNullAction() {
        AfterCommit.run(null);
        assertFalse(TransactionSynchronizationManager.isSynchronizationActive());
    }

    @Test
    @DisplayName("有活动事务时推迟到 afterCommit 才执行")
    void defersUntilAfterCommit() {
        AtomicInteger counter = new AtomicInteger();
        TransactionSynchronizationManager.initSynchronization();
        try {
            AfterCommit.run(counter::incrementAndGet);
            // 提交前不得执行
            assertEquals(0, counter.get());

            // 模拟事务提交
            for (TransactionSynchronization s : TransactionSynchronizationManager.getSynchronizations()) {
                s.afterCommit();
            }
            assertEquals(1, counter.get());
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    @DisplayName("多个动作按注册顺序在提交后依次执行")
    void preservesOrder() {
        StringBuilder sb = new StringBuilder();
        TransactionSynchronizationManager.initSynchronization();
        try {
            AfterCommit.run(() -> sb.append("A"));
            AfterCommit.run(() -> sb.append("B"));
            assertTrue(sb.isEmpty());

            for (TransactionSynchronization s : TransactionSynchronizationManager.getSynchronizations()) {
                s.afterCommit();
            }
            assertEquals("AB", sb.toString());
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }
}
