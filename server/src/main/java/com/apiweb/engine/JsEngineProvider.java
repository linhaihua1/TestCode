package com.apiweb.engine;

import lombok.extern.slf4j.Slf4j;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;

/**
 * JavaScript 执行引擎提供者（集中式单入口）。
 *
 * <h3>背景</h3>
 * <p>平台有多处能力需要执行 JS：脚本步骤（{@code SCRIPT}）、脚本断言、
 * 变量表达式、DYNAMIC Mock。早期实现各自 {@code new ScriptEngineManager().getEngineByName("js")}，
 * 存在两个问题：
 * <ol>
 *   <li><b>引擎名不匹配</b>：JSR-223 的引擎名由各实现自行注册。JDK 15 起内置 Nashorn 已被移除，
 *       当前运行时的 JS 引擎来自 classpath 中的 Rhino（由 JMeter 传递引入），其注册名为
 *       {@code rhino / javascript / ecmascript}，<b>不含 {@code "js"}</b>——导致
 *       {@code getEngineByName("js")} 恒为 {@code null}，脚本类能力静默降级或直接失败。</li>
 *   <li><b>重复开销</b>：{@code new ScriptEngineManager()} 每次都会扫描 classpath 上的引擎工厂，
 *       在高频断言 / 步骤场景下是明显浪费。</li>
 * </ol>
 *
 * <h3>策略</h3>
 * <p>缓存进程级唯一的 {@link ScriptEngineManager}（构造时扫描一次），按能力优先级依次尝试
 * 常见引擎名，命中即返回一个新的 {@link ScriptEngine}。
 * {@link ScriptEngine} <b>非线程安全</b>，调用方应每次取新实例（尤其并发执行场景）。
 *
 * <h3>引擎优先级</h3>
 * <p>GraalJS（{@code graal.js}）&gt; Nashorn（{@code nashorn}）&gt; 通用 {@code js}
 * &gt; {@code javascript} &gt; Rhino（{@code rhino}）。
 * 若后续引入能力更强的引擎（如 GraalVM JS），无需改动业务代码即可自动优先使用。
 */
@Slf4j
public final class JsEngineProvider {

    /** 候选引擎名（按能力优先级排序） */
    private static final String[] CANDIDATE_NAMES =
            {"graal.js", "nashorn", "js", "javascript", "rhino"};

    /** 进程级唯一的管理器（构造时扫描一次 classpath 引擎工厂，之后复用） */
    private static final ScriptEngineManager MANAGER = new ScriptEngineManager();

    /** 类初始化时探测到的可用引擎名（{@code null} 表示当前 JVM 无任何 JS 引擎） */
    private static final String AVAILABLE_NAME = detect();

    private JsEngineProvider() {}

    /**
     * 创建一个新的 JS 引擎实例。
     *
     * @return 新的 {@link ScriptEngine}；当前 JVM 无可用 JS 引擎时返回 {@code null}
     */
    public static ScriptEngine create() {
        return AVAILABLE_NAME == null ? null : MANAGER.getEngineByName(AVAILABLE_NAME);
    }

    /**
     * 当前 JVM 是否存在可用的 JS 引擎。
     *
     * @return 存在返回 {@code true}
     */
    public static boolean available() {
        return AVAILABLE_NAME != null;
    }

    /**
     * 探测可用的引擎名（仅在类初始化时执行一次）。
     */
    private static String detect() {
        for (String name : CANDIDATE_NAMES) {
            if (MANAGER.getEngineByName(name) != null) {
                log.info("JS 执行引擎已就绪：{}", name);
                return name;
            }
        }
        log.warn("当前 JVM 未发现任何可用的 JS 引擎，脚本步骤 / 脚本断言 / 变量表达式 / DYNAMIC Mock 将不可用");
        return null;
    }
}
