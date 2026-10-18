package com.apiweb.engine;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.script.ScriptEngine;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link JsEngineProvider} 契约测试（回归守卫）。
 *
 * <p>背景：早期实现统一用 {@code getEngineByName("js")} 查找引擎，但 JDK 15 起已移除内置 Nashorn，
 * 运行时 JS 引擎来自 Rhino（注册名为 {@code rhino/javascript}，<b>不含 {@code "js"}</b>），
 * 导致脚本步骤 / 脚本断言 / 变量表达式 / DYNAMIC Mock 全部静默降级或失败。
 * 本测试锁死「引擎可解析」与「脚本返回 Java 布尔」两个关键契约。</p>
 */
class JsEngineProviderTest {

    @Test
    @DisplayName("当前运行时应能解析出可用的 JS 引擎")
    void jsEngineShouldBeAvailable() {
        assertThat(JsEngineProvider.available())
                .as("classpath 需包含 JSR-223 JS 引擎（org.mozilla:rhino）")
                .isTrue();
        assertThat(JsEngineProvider.create()).isNotNull();
    }

    @Test
    @DisplayName("每次 create 返回独立实例，且可执行基础表达式")
    void createShouldReturnUsableIndependentEngine() throws Exception {
        ScriptEngine a = JsEngineProvider.create();
        ScriptEngine b = JsEngineProvider.create();
        // ScriptEngine 非线程安全，必须每次取新实例
        assertThat(a).isNotSameAs(b);

        a.put("x", 2);
        a.put("y", 3);
        Object sum = a.eval("x + y");
        assertThat(((Number) sum).intValue()).isEqualTo(5);

        // 独立作用域：b 不应看到 a 注入的变量
        assertThat(String.valueOf(b.eval("typeof x"))).isEqualTo("undefined");
    }

    @Test
    @DisplayName("条件脚本返回 Java 布尔（引擎消费方依赖 Boolean.TRUE.equals 判定）")
    void evalShouldReturnJavaBoolean() throws Exception {
        ScriptEngine engine = JsEngineProvider.create();
        engine.put("status", 200);

        assertThat(engine.eval("status === 200"))
                .as("IF/WHILE 条件求值依赖返回 java.lang.Boolean")
                .isEqualTo(Boolean.TRUE);
        assertThat(engine.eval("status === 404")).isEqualTo(Boolean.FALSE);
    }
}
