package com.apiweb.engine;

/**
 * 执行机节点状态枚举（取代散落的 "online" / "busy" / "offline" 字面量）。
 *
 * <p>对应 {@code t_executor_node.status}。
 */
public enum ExecutorStatus {

    /** 在线空闲 */
    ONLINE("online"),
    /** 在线且正在执行任务 */
    BUSY("busy"),
    /** 心跳超时已下线 */
    OFFLINE("offline");

    private final String code;

    ExecutorStatus(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
