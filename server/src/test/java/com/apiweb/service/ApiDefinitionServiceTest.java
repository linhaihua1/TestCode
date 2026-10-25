package com.apiweb.service;

import com.apiweb.common.BizException;
import com.apiweb.common.ErrorCode;
import com.apiweb.converter.ApiDefinitionConverter;
import com.apiweb.dto.apidefinition.ApiDefinitionReq;
import com.apiweb.entity.ApiDefinitionEntity;
import com.apiweb.mapper.ApiDefinitionMapper;
import com.apiweb.mapper.CaseMapper;
import com.apiweb.openapi.CaseGenerator;
import com.apiweb.openapi.SwaggerImporter;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ApiDefinitionService} 写入侧唯一性校验测试。
 *
 * <p>Mock 服务靠 {@code projectId + method + path} 反查接口，重复定义会让命中结果不可预期；
 * 导入侧一直有去重，手工创建/编辑曾缺失校验，这里把行为固化。</p>
 */
@ExtendWith(MockitoExtension.class)
class ApiDefinitionServiceTest {

    @Mock
    private ApiDefinitionMapper apiMapper;
    @Mock
    private ApiDefinitionConverter converter;
    @Mock
    private SwaggerImporter swaggerImporter;
    @Mock
    private CaseGenerator caseGenerator;
    @Mock
    private CaseMapper caseMapper;
    @Mock
    private CaseStepService caseStepService;

    @InjectMocks
    private ApiDefinitionService service;

    private ApiDefinitionReq req() {
        ApiDefinitionReq req = new ApiDefinitionReq();
        req.setProjectId("p1");
        req.setMethod("GET");
        req.setPath("/users/login");
        req.setName("登录");
        return req;
    }

    private ApiDefinitionEntity entity() {
        ApiDefinitionEntity e = new ApiDefinitionEntity();
        e.setId("api-1");
        e.setProjectId("p1");
        e.setMethod("GET");
        e.setPath("/users/login");
        e.setName("登录");
        return e;
    }

    @Test
    @DisplayName("同项目内已存在同方法同路径 -> 抛 1005 冲突，不写库")
    void createShouldRejectDuplicatePath() {
        when(converter.toEntity(any(ApiDefinitionReq.class))).thenReturn(entity());
        when(apiMapper.selectCount(any(Wrapper.class))).thenReturn(1L);

        assertThatThrownBy(() -> service.create(req()))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getCode())
                        .isEqualTo(ErrorCode.CONFLICT));

        verify(apiMapper, never()).insert(any(ApiDefinitionEntity.class));
    }

    @Test
    @DisplayName("无重复时正常创建")
    void createShouldPersistWhenPathUnique() {
        ApiDefinitionEntity saved = entity();
        when(converter.toEntity(any(ApiDefinitionReq.class))).thenReturn(saved);
        when(apiMapper.selectCount(any(Wrapper.class))).thenReturn(0L);

        service.create(req());

        verify(apiMapper).insert(saved);
    }

    @Test
    @DisplayName("更新时排除自身，改回原路径不应误判冲突")
    void updateShouldExcludeSelf() {
        when(apiMapper.selectById("api-1")).thenReturn(entity());
        when(converter.toEntity(any(ApiDefinitionReq.class))).thenReturn(entity());
        when(apiMapper.selectCount(any(Wrapper.class))).thenReturn(0L);

        service.update("api-1", req());

        verify(apiMapper).updateById(any(ApiDefinitionEntity.class));
    }

    @Test
    @DisplayName("更新时与其他接口撞路径 -> 抛 1005 冲突，不写库")
    void updateShouldRejectDuplicatePath() {
        when(apiMapper.selectById("api-1")).thenReturn(entity());
        when(converter.toEntity(any(ApiDefinitionReq.class))).thenReturn(entity());
        when(apiMapper.selectCount(any(Wrapper.class))).thenReturn(1L);

        assertThatThrownBy(() -> service.update("api-1", req()))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getCode())
                        .isEqualTo(ErrorCode.CONFLICT));

        verify(apiMapper, never()).updateById(any(ApiDefinitionEntity.class));
    }

    @Test
    @DisplayName("更新不存在的接口 -> 1004 不存在")
    void updateShouldFailWhenApiMissing() {
        when(apiMapper.selectById("nope")).thenReturn(null);

        assertThatThrownBy(() -> service.update("nope", req()))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getCode())
                        .isEqualTo(ErrorCode.NOT_FOUND));
    }
}
