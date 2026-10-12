package com.apiweb.common;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 事务提交后执行工具。
 *
 * <p><b>为什么需要它</b>：异步执行链路（MQ 投递 / {@code @Async}）往往在 {@code @Transactional}
 * 方法内部被触发。此时被投递的记录（如待执行报告）尚在**未提交**的事务中，异步线程使用独立
 * 连接查询会读不到该行，导致「报告或数据不存在」而直接返回，报告永久停留在 PENDING。</p>
 *
 * <p>本工具把这类「派发」动作挂到当前事务的 {@code afterCommit} 回调上，保证异步消费方
 * 一定能看到已提交的数据；当方法不在事务中时则立即执行，语义不变。</p>
 *
 * <p>用法：</p>
 * <pre>{@code
 * AfterCommit.run(() -> taskProducer.sendTask(report.getId()));
 * }</pre>
 */
public final class AfterCommit {

    private AfterCommit() {
    }

    /**
     * 在当前事务提交后执行，若当前无活动事务则立即执行。
     *
     * @param action 待执行动作（空则忽略）
     */
    public static void run(Runnable action) {
        if (action == null) {
            return;
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }
}
