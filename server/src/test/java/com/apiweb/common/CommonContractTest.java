package com.apiweb.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * {@link Result} 统一响应结构测试。
 *
 * <p>这是所有 Controller 返回值的载体，前端 axios interceptor 据此解包。
 * 若 code/message 约定被破坏，会导致前端「成功了却提示失败」或反之。
 */
class ResultTest {

    @Test
    @DisplayName("ok(data) 设置 code=0/message=ok/data")
    void okWithData() {
        Result<String> r = Result.ok("hello");
        assertEquals(ErrorCode.SUCCESS, r.getCode());
        assertEquals("ok", r.getMessage());
        assertEquals("hello", r.getData());
    }

    @Test
    @DisplayName("ok() 无参返回 data=null")
    void okWithoutData() {
        Result<Void> r = Result.ok();
        assertEquals(ErrorCode.SUCCESS, r.getCode());
        assertNull(r.getData());
    }

    @Test
    @DisplayName("fail(code,message) 返回 data=null")
    void failReturnsNullData() {
        Result<String> r = Result.fail(ErrorCode.NOT_FOUND, "不存在");
        assertEquals(ErrorCode.NOT_FOUND, r.getCode());
        assertEquals("不存在", r.getMessage());
        assertNull(r.getData());
    }
}

/**
 * {@link PageResult} 统一分页结构测试。
 */
class PageResultTest {

    @Test
    @DisplayName("of(Page) 正确填充 records/total/page/size")
    void ofFromMybatisPage() {
        var page = new com.baomidou.mybatisplus.extension.plugins.pagination.Page<Object>(2, 10);
        page.setRecords(List.of("a", "b"));
        page.setTotal(25);
        PageResult<Object> r = PageResult.of(page);
        assertEquals(List.of("a", "b"), r.getRecords());
        assertEquals(25, r.getTotal());
        assertEquals(2, r.getPage());
        assertEquals(10, r.getSize());
    }

    @Test
    @DisplayName("of(records,total,page,size) 手动构建")
    void ofFromManual() {
        PageResult<String> r = PageResult.of(List.of("x"), 100, 1, 20);
        assertEquals(List.of("x"), r.getRecords());
        assertEquals(100, r.getTotal());
        assertEquals(1, r.getPage());
        assertEquals(20, r.getSize());
    }
}

/**
 * {@link BizException} 业务异常工厂方法测试。
 *
 * <p>重点保护每个便捷工厂方法映射到正确错误码，前端据此做不同交互。
 */
class BizExceptionTest {

    @Test
    @DisplayName("通用工厂 of(code,message)")
    void ofPreservesCodeAndMessage() {
        BizException e = BizException.of(ErrorCode.VARIABLE_REFERENCED, "被 5 处引用");
        assertEquals(ErrorCode.VARIABLE_REFERENCED, e.getCode());
        assertEquals("被 5 处引用", e.getMessage());
    }

    @Test
    @DisplayName("各便捷工厂映射到正确错误码")
    void factoryMethodsMapCorrectCode() {
        assertEquals(ErrorCode.NOT_FOUND, BizException.notFound("x").getCode());
        assertEquals(ErrorCode.BAD_REQUEST, BizException.badRequest("x").getCode());
        assertEquals(ErrorCode.FORBIDDEN, BizException.forbidden("x").getCode());
        assertEquals(ErrorCode.UNAUTHORIZED, BizException.unauthorized("x").getCode());
        assertEquals(ErrorCode.LOGIN_FAILED, BizException.loginFailed("x").getCode());
        assertEquals(ErrorCode.CONFLICT, BizException.conflict("x").getCode());
    }

    @Test
    @DisplayName("业务校验工厂方法映射正确错误码")
    void businessValidationFactories() {
        assertEquals(ErrorCode.INVALID_VARIABLE_NAME, BizException.invalidVariableName("x").getCode());
        assertEquals(ErrorCode.VARIABLE_REFERENCED, BizException.variableReferenced("x").getCode());
        assertEquals(ErrorCode.CASE_NAME_EMPTY, BizException.caseNameEmpty().getCode());
        assertEquals(ErrorCode.TASK_NAME_EMPTY, BizException.taskNameEmpty().getCode());
        assertEquals(ErrorCode.TASK_EMPTY_CASES, BizException.taskEmptyCases().getCode());
        assertEquals(ErrorCode.LOOP_MAX_REQUIRED, BizException.loopMaxRequired().getCode());
    }

    @Test
    @DisplayName("BizException 是 RuntimeException 子类")
    void isRuntimeException() {
        assertSame(true, RuntimeException.class.isAssignableFrom(BizException.class));
    }
}

/**
 * {@link ErrorCode} 错误码常量契约测试。
 *
 * <p>错误码是全平台前后端共享的契约（开发文档 §6），一旦数值漂移会导致前端误判。
 * 这里锁定关键码值，防止无意识改动。
 */
class ErrorCodeTest {

    @Test
    @DisplayName("核心错误码数值稳定")
    void coreCodesStable() {
        assertEquals(0, ErrorCode.SUCCESS);
        assertEquals(1001, ErrorCode.BAD_REQUEST);
        assertEquals(1002, ErrorCode.UNAUTHORIZED);
        assertEquals(1003, ErrorCode.FORBIDDEN);
        assertEquals(1004, ErrorCode.NOT_FOUND);
        assertEquals(1005, ErrorCode.CONFLICT);
        assertEquals(1006, ErrorCode.LOGIN_FAILED);
        assertEquals(500, ErrorCode.INTERNAL_ERROR);
    }

    @Test
    @DisplayName("业务校验/导入/执行错误码分区稳定")
    void domainCodesStable() {
        // 业务校验 2001~2006
        assertEquals(2001, ErrorCode.INVALID_VARIABLE_NAME);
        assertEquals(2006, ErrorCode.LOOP_MAX_REQUIRED);
        // 导入 3001~3004
        assertEquals(3001, ErrorCode.SWAGGER_PARSE_FAILED);
        assertEquals(3004, ErrorCode.INVALID_URL);
        // 执行 4001~4004
        assertEquals(4001, ErrorCode.DEBUG_EXECUTION_FAILED);
        assertEquals(4004, ErrorCode.REPORT_GENERATE_FAILED);
    }
}
