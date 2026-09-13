package com.apiweb.engine.step;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ExtractType} 解析测试。
 *
 * <p>前端变量提取下拉的取值可能来自历史数据（小写）或新版本（大写），
 * 后端必须大小写不敏感且能兜底，避免因为一个拼写差异导致提取静默失效。
 */
class ExtractTypeTest {

    @Test
    @DisplayName("parse: 大写 / 小写 / 混合大小写均可识别")
    void parseIsCaseInsensitive() {
        assertSame(ExtractType.JSONPATH, ExtractType.parse("JSONPATH"));
        assertSame(ExtractType.JSONPATH, ExtractType.parse("jsonpath"));
        assertSame(ExtractType.JSONPATH, ExtractType.parse("JsonPath"));

        assertSame(ExtractType.REGEX, ExtractType.parse("regex"));
        assertSame(ExtractType.HEADER, ExtractType.parse("header"));
        assertSame(ExtractType.COOKIE, ExtractType.parse("COOKIE"));
        assertSame(ExtractType.XPATH, ExtractType.parse("xpath"));
    }

    @Test
    @DisplayName("parse: 首尾空白被忽略")
    void parseTrimsWhitespace() {
        assertSame(ExtractType.HEADER, ExtractType.parse("  header  "));
    }

    @Test
    @DisplayName("parse: 空值 / 无法识别时回退默认提取方式")
    void parseFallsBackToDefault() {
        assertSame(ExtractType.DEFAULT, ExtractType.parse(null));
        assertSame(ExtractType.DEFAULT, ExtractType.parse(""));
        assertSame(ExtractType.DEFAULT, ExtractType.parse("   "));
        assertSame(ExtractType.DEFAULT, ExtractType.parse("not-an-extract-type"));
    }

    @Test
    @DisplayName("5 种提取方式齐备，且默认值为 JSONPath")
    void allTypesPresent() {
        assertEquals(5, ExtractType.values().length);
        assertSame(ExtractType.JSONPATH, ExtractType.DEFAULT);
        for (ExtractType t : ExtractType.values()) {
            assertSame(t, ExtractType.parse(t.name()));
        }
        assertTrue(ExtractType.values().length >= 5);
    }
}
