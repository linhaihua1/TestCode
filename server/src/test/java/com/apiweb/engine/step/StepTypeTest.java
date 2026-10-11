package com.apiweb.engine.step;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link StepType} 步骤类型解析测试。
 *
 * <p>步骤类型是执行引擎的分发入口，前端 JSON 的 {@code type} 字段必须能被正确解析，
 * 且容器/非容器的判定直接决定用例执行是否中断，语义错误会导致整个用例被误停。
 */
class StepTypeTest {

    @Test
    @DisplayName("parse: 枚举名（大写）直接识别")
    void parseEnumName() {
        assertSame(StepType.HTTP_REQUEST, StepType.parse("HTTP_REQUEST"));
        assertSame(StepType.IF, StepType.parse("IF"));
        assertSame(StepType.WHILE, StepType.parse("WHILE"));
    }

    @Test
    @DisplayName("parse: 旧版简短写法兼容")
    void parseLegacyShortForm() {
        assertSame(StepType.HTTP_REQUEST, StepType.parse("http"));
        assertSame(StepType.IF, StepType.parse("if"));
        assertSame(StepType.FOR, StepType.parse("for"));
        assertSame(StepType.WHILE, StepType.parse("while"));
        assertSame(StepType.SCRIPT, StepType.parse("script"));
        assertSame(StepType.WAIT, StepType.parse("wait"));
    }

    @Test
    @DisplayName("parse: 空值 / 无法识别时默认 HTTP_REQUEST")
    void parseFallsBackToHttpRequest() {
        assertSame(StepType.HTTP_REQUEST, StepType.parse(null));
        assertSame(StepType.HTTP_REQUEST, StepType.parse(""));
        assertSame(StepType.HTTP_REQUEST, StepType.parse("   "));
        assertSame(StepType.HTTP_REQUEST, StepType.parse("not-a-step"));
    }

    @Test
    @DisplayName("isContainer: 6 种控制器返回 true，其余 false")
    void containerJudgement() {
        assertTrue(StepType.IF.isContainer());
        assertTrue(StepType.FOR.isContainer());
        assertTrue(StepType.WHILE.isContainer());
        assertTrue(StepType.TRANSACTION.isContainer());
        assertTrue(StepType.ONCE.isContainer());
        assertTrue(StepType.REF_PUBLIC_CASE.isContainer());

        assertFalse(StepType.HTTP_REQUEST.isContainer());
        assertFalse(StepType.SCRIPT.isContainer());
        assertFalse(StepType.WAIT.isContainer());
        assertFalse(StepType.VARIABLE_ASSIGN.isContainer());
    }

    @Test
    @DisplayName("10 种步骤类型齐备")
    void allTypesPresent() {
        assertEquals(10, StepType.values().length);
    }
}

/**
 * {@link AssertionType} 断言类型解析测试。
 */
class AssertionTypeTest {

    @Test
    @DisplayName("parse: 枚举名与旧版 source 取值均可识别")
    void parseCompatible() {
        assertSame(AssertionType.STATUS, AssertionType.parse("STATUS"));
        assertSame(AssertionType.STATUS, AssertionType.parse("status"));
        assertSame(AssertionType.BODY_CONTAINS, AssertionType.parse("body"));
        assertSame(AssertionType.HEADER, AssertionType.parse("header"));
        assertSame(AssertionType.JSONPATH, AssertionType.parse("jsonpath"));
        assertSame(AssertionType.RESPONSE_TIME, AssertionType.parse("responsetime"));
        assertSame(AssertionType.RESPONSE_TIME, AssertionType.parse("response_time"));
        assertSame(AssertionType.REGEX, AssertionType.parse("regex"));
        assertSame(AssertionType.XPATH, AssertionType.parse("xpath"));
    }

    @Test
    @DisplayName("parse: 空值 / 无法识别时回退 BODY_CONTAINS")
    void parseFallsBackToBodyContains() {
        assertSame(AssertionType.BODY_CONTAINS, AssertionType.parse(null));
        assertSame(AssertionType.BODY_CONTAINS, AssertionType.parse(""));
        assertSame(AssertionType.BODY_CONTAINS, AssertionType.parse("unknown-assertion"));
    }
}
