package com.apiweb.engine.executor;

import com.apiweb.config.EngineProperties;
import com.apiweb.engine.CaseRunner;
import com.apiweb.engine.EngineDtos;
import com.apiweb.engine.StepContext;
import com.apiweb.engine.StepDef;
import com.apiweb.engine.VariablesResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.beans.factory.ObjectProvider;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 控制类执行器（FOR / IF / WAIT）的字段名契约测试。
 *
 * <p><b>回归背景</b>：后端读的字段名与前端写入的字段名不一致，
 * 导致「循环次数、else 分支、等待时长」被静默忽略 —— 不报错、但行为完全不对：
 * <ul>
 *   <li>FOR 读 {@code count}，前端写 {@code loopCount} → 永远只循环 1 次</li>
 *   <li>IF 用 {@code cfg("elseBranch")} 取列表 → 被字符串化后恒为空，else 永不执行</li>
 *   <li>WAIT 读 {@code ms}，前端写 {@code waitMs} → 永远等默认 1000ms</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ControlStepExecutorTest {

    @Mock
    private CaseRunner caseRunner;

    @Mock
    private ObjectProvider<CaseRunner> provider;

    @BeforeEach
    void setUp() {
        when(provider.getIfAvailable()).thenReturn(caseRunner);
    }

    private static StepContext ctx() {
        return new StepContext(new VariablesResolver(new HashMap<>()),
                EngineDtos.ExecutionResult.builder().result("success").build());
    }

    private static StepDef step(String type, Map<String, Object> config) {
        StepDef s = new StepDef();
        s.setType(type);
        s.setName(type);
        s.setConfig(config);
        return s;
    }

    @Test
    @DisplayName("FOR：使用前端字段 loopCount 循环 5 次")
    void forStep_shouldHonorLoopCount() {
        StepDef s = step("FOR", Map.of("mode", "count", "loopCount", 5));
        s.setChildren(List.of(new StepDef()));

        new ForStepExecutor(provider).execute(s, ctx());

        verify(caseRunner, times(5)).runSteps(anyList(), any());
    }

    @Test
    @DisplayName("FOR：兼容历史字段 count")
    void forStep_shouldFallbackToCount() {
        StepDef s = step("FOR", Map.of("mode", "count", "count", 3));
        s.setChildren(List.of(new StepDef()));

        new ForStepExecutor(provider).execute(s, ctx());

        verify(caseRunner, times(3)).runSteps(anyList(), any());
    }

    @Test
    @DisplayName("FOR：loopCount 缺失时至少执行 1 次")
    void forStep_shouldDefaultToOne() {
        StepDef s = step("FOR", Map.of("mode", "count"));
        s.setChildren(List.of(new StepDef()));

        new ForStepExecutor(provider).execute(s, ctx());

        verify(caseRunner, times(1)).runSteps(anyList(), any());
    }

    @Test
    @DisplayName("IF：条件为 false 时必须执行 elseBranch（且取到的是 else 分支步骤）")
    @SuppressWarnings("unchecked")
    void ifStep_shouldRunElseBranchWhenConditionFalse() {
        when(caseRunner.evalCondition(anyString(), any())).thenReturn(false);

        StepDef thenStep = new StepDef();
        thenStep.setName("then-step");
        StepDef s = step("IF", Map.of(
                "condition", "1==2",
                "elseBranch", List.of(Map.of("type", "SCRIPT", "name", "else-step"))));
        s.setChildren(List.of(thenStep));

        new IfStepExecutor(provider).execute(s, ctx());

        ArgumentCaptor<List<StepDef>> captor = ArgumentCaptor.forClass(List.class);
        verify(caseRunner).runSteps(captor.capture(), any());
        assertEquals(1, captor.getValue().size(), "else 分支为空，说明 elseBranch 未解析出来");
        assertEquals("else-step", captor.getValue().get(0).getName());
    }

    @Test
    @DisplayName("IF：条件为 true 时执行 children，不执行 elseBranch")
    void ifStep_shouldRunChildrenWhenConditionTrue() {
        when(caseRunner.evalCondition(anyString(), any())).thenReturn(true);

        StepDef thenStep = new StepDef();
        thenStep.setName("then-step");
        StepDef s = step("IF", Map.of(
                "condition", "1==1",
                "elseBranch", List.of(Map.of("type", "SCRIPT", "name", "else-step"))));
        s.setChildren(List.of(thenStep));

        new IfStepExecutor(provider).execute(s, ctx());

        ArgumentCaptor<List<StepDef>> captor = ArgumentCaptor.forClass(List.class);
        verify(caseRunner).runSteps(captor.capture(), any());
        assertEquals("then-step", captor.getValue().get(0).getName());
    }

    @Test
    @DisplayName("IF：elseBranch 为 JSON 字符串时同样能解析")
    @SuppressWarnings("unchecked")
    void ifStep_shouldParseElseBranchFromJsonString() {
        when(caseRunner.evalCondition(anyString(), any())).thenReturn(false);

        StepDef s = step("IF", Map.of(
                "condition", "1==2",
                "elseBranch", "[{\"type\":\"SCRIPT\",\"name\":\"else-from-string\"}]"));

        new IfStepExecutor(provider).execute(s, ctx());

        ArgumentCaptor<List<StepDef>> captor = ArgumentCaptor.forClass(List.class);
        verify(caseRunner).runSteps(captor.capture(), any());
        assertEquals("else-from-string", captor.getValue().get(0).getName());
    }

    @Test
    @DisplayName("WAIT：使用前端字段 waitMs（而非默认 1000ms）")
    void waitStep_shouldHonorWaitMs() {
        StepDef s = step("WAIT", Map.of("mode", "fixed", "waitMs", 150));

        long start = System.currentTimeMillis();
        new WaitStepExecutor(new EngineProperties()).execute(s, ctx());
        long cost = System.currentTimeMillis() - start;

        assertTrue(cost >= 130, "实际仅等待 " + cost + "ms，waitMs 未生效");
        assertTrue(cost < 900, "实际等待 " + cost + "ms，疑似用了默认 1000ms");
    }

    @Test
    @DisplayName("WAIT：兼容历史字段 ms")
    void waitStep_shouldFallbackToMs() {
        StepDef s = step("WAIT", Map.of("mode", "fixed", "ms", 150));

        long start = System.currentTimeMillis();
        new WaitStepExecutor(new EngineProperties()).execute(s, ctx());
        long cost = System.currentTimeMillis() - start;

        assertTrue(cost >= 130, "实际仅等待 " + cost + "ms，ms 未生效");
    }

    @Test
    @DisplayName("IF：无 elseBranch 时执行空列表，不抛错")
    void ifStep_withoutElseBranch_shouldNotThrow() {
        when(caseRunner.evalCondition(anyString(), any())).thenReturn(false);

        StepDef s = step("IF", Map.of("condition", "1==2"));
        s.setChildren(List.of(new StepDef()));

        new IfStepExecutor(provider).execute(s, ctx());

        verify(caseRunner).runSteps(eq(List.of()), any());
    }
}
