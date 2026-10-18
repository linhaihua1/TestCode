package com.apiweb.service;

import com.apiweb.converter.CaseConverter;
import com.apiweb.dto.casee.CaseReq;
import com.apiweb.entity.CaseEntity;
import com.apiweb.entity.CaseVersionEntity;
import com.apiweb.mapper.CaseMapper;
import com.apiweb.mapper.CaseReviewMapper;
import com.apiweb.mapper.CaseVersionMapper;
import com.apiweb.mapper.DebugRecordMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * CaseService 的步骤同步契约测试。
 *
 * <p>回归背景：用例更新 / 版本恢复时若不同步步骤表，
 * {@code t_case.steps}（执行真源）与 {@code t_case_step}（投影表）会漂移：
 * 直接调试执行新步骤，而被引用（REF_PUBLIC_CASE）和步骤管理接口仍是旧步骤。</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CaseServiceTest {

    @Mock
    private CaseMapper caseMapper;
    @Mock
    private CaseVersionMapper caseVersionMapper;
    @Mock
    private CaseReviewMapper caseReviewMapper;
    @Mock
    private CaseStepService caseStepService;
    @Mock
    private DebugRecordMapper debugRecordMapper;
    @Mock
    private ExecutionSupportService executionSupport;
    @Mock
    private OssService ossService;
    @Mock
    private CaseConverter converter;

    @InjectMocks
    private CaseService caseService;

    private static CaseEntity caseWithSteps(String steps) {
        CaseEntity c = new CaseEntity();
        c.setId("c1");
        c.setName("用例");
        c.setVersion(1);
        c.setTags("[]");
        c.setSteps(steps);
        return c;
    }

    @Test
    @DisplayName("update：请求未携带 steps 时不得清空步骤表")
    void update_withoutSteps_shouldNotClearSteps() {
        CaseEntity existing = caseWithSteps("[{\"type\":\"SCRIPT\",\"name\":\"s\"}]");
        when(caseMapper.selectById("c1")).thenReturn(existing);
        when(converter.toEntity(any())).thenReturn(new CaseEntity()); // 请求未带 steps

        CaseReq req = new CaseReq();
        req.setName("改名");
        caseService.update("c1", req);

        // null 表示「未提供」，由 replaceStepsFromJson 内部跳过；绝不能传空/旧值去重建
        verify(caseStepService).replaceStepsFromJson("c1", null);

        ArgumentCaptor<CaseEntity> captor = ArgumentCaptor.forClass(CaseEntity.class);
        verify(caseMapper).updateById(captor.capture());
        assertEquals(existing.getSteps(), captor.getValue().getSteps(),
                "steps 未回填原值，版本快照会丢步骤");
    }

    @Test
    @DisplayName("update：请求携带 steps 时按新值重建步骤表")
    void update_withSteps_shouldRebuildSteps() {
        CaseEntity existing = caseWithSteps("[]");
        when(caseMapper.selectById("c1")).thenReturn(existing);
        CaseEntity incoming = new CaseEntity();
        incoming.setSteps("[{\"type\":\"HTTP_REQUEST\",\"url\":\"http://x\"}]");
        when(converter.toEntity(any())).thenReturn(incoming);

        CaseReq req = new CaseReq();
        req.setName("用例");
        req.setSteps("[{\"type\":\"HTTP_REQUEST\",\"url\":\"http://x\"}]");
        caseService.update("c1", req);

        verify(caseStepService).replaceStepsFromJson(eq("c1"), contains("http://x"));
    }

    @Test
    @DisplayName("restoreVersion：恢复后必须同步步骤表，避免双写漂移")
    void restoreVersion_shouldSyncStepsTable() {
        when(caseMapper.selectById("c1")).thenReturn(caseWithSteps("[]"));

        CaseVersionEntity snapshot = new CaseVersionEntity();
        snapshot.setCaseId("c1");
        snapshot.setVersion(1);
        snapshot.setSnapshot("{\"name\":\"旧名\",\"steps\":[{\"type\":\"SCRIPT\",\"name\":\"旧步骤\"}]}");
        when(caseVersionMapper.selectOne(any())).thenReturn(snapshot);

        caseService.restoreVersion("c1", 1);

        verify(caseStepService).replaceStepsFromJson(eq("c1"), contains("旧步骤"));
    }
}
