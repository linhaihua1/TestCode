package com.apiweb.controller;

import com.apiweb.common.Result;
import com.apiweb.entity.ExecutorNodeEntity;
import com.apiweb.service.ExecutorHeartbeatService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 执行机资源池：心跳注册/上报 + 资源池查询。
 * 执行机（可以是独立部署的同一 jar 或专用执行机进程）周期性调用 /heartbeat 保持在线。
 */
@RestController
@RequestMapping("/api/v1/executors")
@RequiredArgsConstructor
public class ExecutorController {

    private final ExecutorHeartbeatService heartbeatService;

    /**
     * 心跳注册/上报（免 token：执行机使用固定注册密钥，此处简化为直接上报）。
     */
    @PostMapping("/heartbeat")
    public Result<ExecutorNodeEntity> heartbeat(@RequestBody Map<String, Object> body) {
        String nodeId = String.valueOf(body.getOrDefault("nodeId",
                ExecutorHeartbeatService.localNodeId()));
        String name = body.get("name") == null ? null : String.valueOf(body.get("name"));
        @SuppressWarnings("unchecked")
        List<String> capabilities = (List<String>) body.get("capabilities");
        return Result.ok(heartbeatService.heartbeat(nodeId, name, capabilities));
    }

    /**
     * 查询资源池（在线执行机列表）。
     */
    @GetMapping("/pool")
    public Result<List<ExecutorNodeEntity>> pool() {
        return Result.ok(heartbeatService.resourcePool());
    }

    /**
     * 任务执行完成回调（执行机释放占用）。
     */
    @PostMapping("/{nodeId}/release")
    public Result<Void> release(@PathVariable String nodeId) {
        heartbeatService.bindTask(nodeId, null);
        return Result.ok();
    }
}
