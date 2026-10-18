package com.apiweb.service;

import com.apiweb.converter.CaseStepConverter;
import com.apiweb.dto.casestep.CaseStepReq;
import com.apiweb.entity.CaseEntity;
import com.apiweb.entity.CaseStepEntity;
import com.apiweb.mapper.CaseMapper;
import com.apiweb.mapper.CaseStepMapper;
import com.apiweb.util.JsonUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * CaseStepService 单测：锁定「t_case.steps（执行真源）↔ t_case_step（投影表）」的无损双向同步。
 *
 * <p>回归背景：曾因二者漂移，导致「一键生成用例 / 批量保存步骤」后调试执行跑 0 个步骤。</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CaseStepServiceTest {

    @Mock
    private CaseStepMapper caseStepMapper;

    @Mock
    private CaseMapper caseMapper;

    @InjectMocks
    private CaseStepService caseStepService;

    /** 内存步骤表 */
    private final List<CaseStepEntity> store = new ArrayList<>();
    private int seq = 0;

    @BeforeEach
    void setUp() {
        store.clear();
        seq = 0;
        ReflectionTestUtils.setField(caseStepService, "caseStepConverter", CaseStepConverter.INSTANCE);

        when(caseStepMapper.insert(any(CaseStepEntity.class))).thenAnswer(inv -> {
            CaseStepEntity e = inv.getArgument(0);
            if (e.getId() == null) {
                e.setId("s" + (++seq));
            }
            store.removeIf(x -> e.getId().equals(x.getId()));
            store.add(e);
            return 1;
        });
        when(caseStepMapper.selectList(any())).thenAnswer(inv -> new ArrayList<>(store));
        when(caseStepMapper.selectById(anyString())).thenAnswer(inv ->
                store.stream().filter(x -> inv.getArgument(0).equals(x.getId())).findFirst().orElse(null));
        when(caseStepMapper.delete(any())).thenAnswer(inv -> {
            store.clear();
            return 1;
        });
        when(caseStepMapper.deleteById(anyString())).thenAnswer(inv -> {
            store.removeIf(x -> inv.getArgument(0).equals(x.getId()));
            return 1;
        });
        when(caseStepMapper.updateById(any(CaseStepEntity.class))).thenAnswer(inv -> {
            CaseStepEntity patch = inv.getArgument(0);
            store.stream().filter(x -> x.getId().equals(patch.getId())).findFirst().ifPresent(x -> {
                if (patch.getSortOrder() != null) x.setSortOrder(patch.getSortOrder());
                if (patch.getConfig() != null) x.setConfig(patch.getConfig());
            });
            return 1;
        });
    }

    @Test
    @DisplayName("replaceStepsFromJson：非身份字段无损落进 config（含脚本步骤），_id 不落库")
    void replaceStepsFromJson_shouldKeepTypeSpecificFieldsLossless() {
        String json = """
                [
                  {"type":"http","name":"登录","position":"TEST","enabled":true,"failStrategy":"stop",
                   "method":"POST","url":"{{baseUrl}}/login",
                   "headers":[{"key":"X","value":"1"}],"query":[],"body":"{}",
                   "assertions":[{"type":"STATUS","expected":"200"}],"extracts":[]},
                  {"type":"script","name":"脚本","position":"POST","enabled":false,"failStrategy":"continue",
                   "language":"javascript","script":"return 1","_id":"tmp-local-id"}
                ]
                """;

        caseStepService.replaceStepsFromJson("c1", json);

        assertEquals(2, store.size());
        CaseStepEntity first = store.get(0);
        assertEquals("http", first.getStepType());
        assertEquals("TEST", first.getPosition());
        Map<String, Object> cfg0 = JsonUtils.toMap(first.getConfig());
        assertEquals("POST", cfg0.get("method"));
        assertEquals("{{baseUrl}}/login", cfg0.get("url"));
        assertFalse(cfg0.containsKey("_id"));

        CaseStepEntity second = store.get(1);
        assertEquals("script", second.getStepType());
        assertFalse(second.getEnabled());
        Map<String, Object> cfg1 = JsonUtils.toMap(second.getConfig());
        assertEquals("javascript", cfg1.get("language"));
        assertEquals("return 1", cfg1.get("script"));
        assertFalse(cfg1.containsKey("_id"));
    }

    @Test
    @DisplayName("t_case.steps → 表 → t_case.steps 往返无损")
    void roundTrip_shouldBeLossless() {
        String json = """
                [{"type":"HTTP_REQUEST","name":"A","position":"TEST","enabled":true,"failStrategy":"stop",
                  "method":"GET","url":"http://x/a","headers":[],"query":[],"body":"",
                  "assertions":[],"extracts":[]},
                 {"type":"SCRIPT","name":"B","position":"POST","enabled":true,"failStrategy":"stop",
                  "language":"javascript","script":"1+1"}]
                """;

        caseStepService.replaceStepsFromJson("c1", json);
        caseStepService.syncCaseStepsJson("c1");

        List<Map<String, Object>> out = capturedSteps();
        assertEquals(2, out.size());
        assertEquals("HTTP_REQUEST", out.get(0).get("type"));
        assertEquals("GET", out.get(0).get("method"));
        assertEquals("http://x/a", out.get(0).get("url"));
        assertEquals("SCRIPT", out.get(1).get("type"));
        assertEquals("javascript", out.get(1).get("language"));
        assertEquals("1+1", out.get(1).get("script"));
    }

    @Test
    @DisplayName("saveBatch 后必须回写执行真源 t_case.steps")
    void saveBatch_shouldSyncCaseStepsJson() {
        CaseStepReq r1 = req("HTTP_REQUEST", "A", "{\"method\":\"GET\",\"url\":\"http://a\"}");
        CaseStepReq r2 = req("SCRIPT", "B", "{\"language\":\"javascript\",\"script\":\"x\"}");

        caseStepService.saveBatch("c1", List.of(r1, r2));

        ArgumentCaptor<CaseEntity> cap = ArgumentCaptor.forClass(CaseEntity.class);
        verify(caseMapper).updateById(cap.capture());
        assertEquals("c1", cap.getValue().getId());
        List<Map<String, Object>> out = JsonUtils.toList(cap.getValue().getSteps());
        assertEquals(2, out.size());
        assertEquals("http://a", out.get(0).get("url"));
        assertEquals("x", out.get(1).get("script"));
    }

    @Test
    @DisplayName("回写 JSON 时保持 PRE → TEST → POST 执行顺序")
    void syncCaseStepsJson_shouldOrderPreTestPost() {
        store.add(step("s1", "POST", 0));
        store.add(step("s2", "PRE", 0));
        store.add(step("s3", "TEST", 0));

        caseStepService.syncCaseStepsJson("c1");

        List<Map<String, Object>> out = capturedSteps();
        assertEquals(List.of("PRE", "TEST", "POST"),
                out.stream().map(m -> m.get("position")).toList());
    }

    @Test
    @DisplayName("replaceStepsFromJson：children 应还原为 parentId 关联")
    void replaceStepsFromJson_shouldNestChildren() {
        String json = """
                [{"type":"IF","name":"if","position":"TEST","enabled":true,"failStrategy":"stop",
                  "condition":"1==1","children":[
                     {"type":"SCRIPT","name":"inner","position":"TEST","enabled":true,"failStrategy":"stop",
                      "language":"javascript","script":"2"}]}]
                """;

        caseStepService.replaceStepsFromJson("c1", json);

        assertEquals(2, store.size());
        CaseStepEntity root = store.get(0);
        CaseStepEntity child = store.get(1);
        assertEquals("IF", root.getStepType());
        assertNull(root.getParentId());
        assertEquals(root.getId(), child.getParentId());
    }

    @Test
    @DisplayName("VARIABLE_ASSIGN：变量名 name 与步骤名冲突时应保留在 config 子对象")
    @SuppressWarnings("unchecked")
    void variableAssign_nameConflict_shouldKeepVariableNameInNestedConfig() {
        CaseStepReq r = req("VARIABLE_ASSIGN", "计算 sum",
                "{\"mode\":\"expression\",\"name\":\"sum\",\"expression\":\"1 + 2\"}");

        caseStepService.saveBatch("c1", List.of(r));

        List<Map<String, Object>> out = capturedSteps();
        assertEquals(1, out.size());
        assertEquals("计算 sum", out.get(0).get("name"));
        assertEquals("VARIABLE_ASSIGN", out.get(0).get("type"));
        Map<String, Object> nested = (Map<String, Object>) out.get(0).get("config");
        assertEquals("sum", nested.get("name"));
        assertEquals("expression", out.get(0).get("mode"));
        assertEquals("1 + 2", out.get(0).get("expression"));
    }

    @Test
    @DisplayName("VARIABLE_ASSIGN：JSON → 表 → JSON 往返保持步骤名与变量名")
    @SuppressWarnings("unchecked")
    void variableAssign_roundTrip_shouldPreserveBothNames() {
        CaseStepReq r = req("VARIABLE_ASSIGN", "计算 sum",
                "{\"mode\":\"expression\",\"name\":\"sum\",\"expression\":\"1 + 2\"}");
        caseStepService.saveBatch("c1", List.of(r));

        String stepsJson = capturedStepsJson();
        store.clear();
        caseStepService.replaceStepsFromJson("c1", stepsJson);

        assertEquals(1, store.size());
        assertEquals("计算 sum", store.get(0).getName());
        Map<String, Object> cfg = JsonUtils.toMap(store.get(0).getConfig());
        assertEquals("sum", cfg.get("name"));
        assertEquals("1 + 2", cfg.get("expression"));
    }

    @Test
    @DisplayName("replaceStepsFromJson(null)：部分更新未携带 steps 时不得清空既有步骤")
    void replaceStepsFromJson_null_shouldNotClearExisting() {
        caseStepService.replaceStepsFromJson("c1",
                "[{\"type\":\"SCRIPT\",\"name\":\"A\",\"script\":\"1\"}]");
        assertEquals(1, store.size());

        caseStepService.replaceStepsFromJson("c1", null);

        assertEquals(1, store.size(), "未传 steps 被误判为「清空」，步骤被删光");
    }

    @Test
    @DisplayName("replaceStepsFromJson(\"[]\")：显式空数组才表示清空")
    void replaceStepsFromJson_emptyArray_shouldClear() {
        caseStepService.replaceStepsFromJson("c1",
                "[{\"type\":\"SCRIPT\",\"name\":\"A\",\"script\":\"1\"}]");
        assertEquals(1, store.size());

        caseStepService.replaceStepsFromJson("c1", "[]");

        assertTrue(store.isEmpty(), "显式 [] 应清空步骤");
    }

    @Test
    @DisplayName("syncCaseStepsJson：父子成环的脏数据不得导致步骤被静默丢弃")
    void syncCaseStepsJson_cycleShouldNotLoseSteps() {
        CaseStepEntity a = step("a", "TEST", 0);
        a.setParentId("b");
        CaseStepEntity b = step("b", "TEST", 1);
        b.setParentId("a");
        store.add(a);
        store.add(b);

        caseStepService.syncCaseStepsJson("c1");

        List<Map<String, Object>> out = capturedSteps();
        assertEquals(2, out.size(), "成环导致节点无法被根节点串起，步骤被丢弃");
    }

    @Test
    @DisplayName("delete：父子成环时级联删除不得栈溢出")
    void delete_cycleShouldNotOverflow() {
        CaseStepEntity a = step("a", "TEST", 0);
        a.setParentId("b");
        CaseStepEntity b = step("b", "TEST", 1);
        b.setParentId("a");
        store.add(a);
        store.add(b);

        assertDoesNotThrow(() -> caseStepService.delete("a"));
        assertTrue(store.isEmpty());
    }

    private String capturedStepsJson() {
        ArgumentCaptor<CaseEntity> cap = ArgumentCaptor.forClass(CaseEntity.class);
        verify(caseMapper).updateById(cap.capture());
        return cap.getValue().getSteps();
    }

    private List<Map<String, Object>> capturedSteps() {
        return JsonUtils.toList(capturedStepsJson());
    }

    private CaseStepReq req(String type, String name, String config) {
        CaseStepReq r = new CaseStepReq();
        r.setCaseId("c1");
        r.setStepType(type);
        r.setName(name);
        r.setConfig(config);
        return r;
    }

    private CaseStepEntity step(String id, String position, int sortOrder) {
        CaseStepEntity e = new CaseStepEntity();
        e.setId(id);
        e.setCaseId("c1");
        e.setStepType("HTTP_REQUEST");
        e.setName(id);
        e.setPosition(position);
        e.setSortOrder(sortOrder);
        e.setEnabled(true);
        e.setFailStrategy("stop");
        e.setConfig("{}");
        return e;
    }
}
