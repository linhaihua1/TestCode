package com.apiweb.service;

import com.apiweb.common.BizException;
import com.apiweb.converter.GlobalVariableConverter;
import com.apiweb.dto.globalvariable.GlobalVariableReq;
import com.apiweb.entity.GlobalVariableEntity;
import com.apiweb.mapper.GlobalVariableMapper;
import com.apiweb.security.AesGcm;
import com.apiweb.vo.globalvariable.GlobalVariableVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 全局变量业务服务。
 *
 * <p>SECRET 类型变量入库前用 AES-256-GCM 加密；对外返回时脱敏为掩码。
 * TIMESTAMP/RANDOM 类型的值由后端生成，忽略前端传入值。</p>
 */
@Service
@RequiredArgsConstructor
public class GlobalVariableService {

    private static final String TYPE_SECRET = "SECRET";
    private static final String TYPE_RANDOM = "RANDOM";
    private static final String TYPE_TIMESTAMP = "TIMESTAMP";
    private static final String MASK = "**********";

    private final GlobalVariableMapper globalVariableMapper;
    private final GlobalVariableConverter globalVariableConverter;

    /**
     * 查询项目的全局变量列表（按变量名升序）；SECRET 值脱敏为掩码。
     */
    public List<GlobalVariableVO> list(String projectId) {
        List<GlobalVariableEntity> vars = globalVariableMapper.selectList(
                new LambdaQueryWrapper<GlobalVariableEntity>()
                        .eq(GlobalVariableEntity::getProjectId, projectId)
                        .orderByAsc(GlobalVariableEntity::getName));
        for (GlobalVariableEntity v : vars) {
            if (TYPE_SECRET.equalsIgnoreCase(v.getType())) {
                v.setValue(MASK);
            }
        }
        return globalVariableConverter.toVOList(vars);
    }

    /**
     * 内部使用：拿到解密后的明文（用例执行时由 VariableMerger 调用）。
     */
    public String resolveValue(String varName, String projectId) {
        GlobalVariableEntity v = globalVariableMapper.selectOne(
                new LambdaQueryWrapper<GlobalVariableEntity>()
                        .eq(GlobalVariableEntity::getProjectId, projectId)
                        .eq(GlobalVariableEntity::getName, varName));
        if (v == null) {
            return null;
        }
        if (TYPE_SECRET.equalsIgnoreCase(v.getType())) {
            try {
                return AesGcm.decrypt(v.getValue());
            } catch (Exception e) {
                throw BizException.badRequest("密钥解密失败: " + varName);
            }
        }
        return v.getValue();
    }

    /**
     * 新建全局变量：SECRET 加密入库，返回前脱敏。
     */
    @Transactional
    public GlobalVariableVO create(GlobalVariableReq req) {
        GlobalVariableEntity variable = new GlobalVariableEntity();
        variable.setProjectId(req.getProjectId());
        variable.setName(req.getName());
        variable.setType(req.getType());
        variable.setValue(req.getValue());
        variable.setEncrypted(req.getEncrypted());
        variable.setDescription(req.getDescription());

        prepareGeneratedValue(variable);
        variable.setValue(encryptIfSecret(variable));
        globalVariableMapper.insert(variable);

        if (TYPE_SECRET.equalsIgnoreCase(variable.getType())) {
            variable.setValue(MASK);
        }
        return globalVariableConverter.toVO(variable);
    }

    /**
     * 更新全局变量：SECRET 保留原密文（前端只回传掩码），非 SECRET 重新生成/加密。
     */
    @Transactional
    public void update(String id, GlobalVariableReq req) {
        GlobalVariableEntity variable = new GlobalVariableEntity();
        variable.setProjectId(req.getProjectId());
        variable.setName(req.getName());
        variable.setType(req.getType());
        variable.setValue(req.getValue());
        variable.setEncrypted(req.getEncrypted());
        variable.setDescription(req.getDescription());

        prepareGeneratedValue(variable);
        if (TYPE_SECRET.equalsIgnoreCase(variable.getType())) {
            // 保留原密文：前端只传掩码，不应重新加密掩码
            GlobalVariableEntity origin = globalVariableMapper.selectById(id);
            if (origin == null) {
                throw BizException.notFound("变量不存在");
            }
            variable.setValue(origin.getValue());
        } else {
            variable.setValue(encryptIfSecret(variable));
        }
        variable.setId(id);
        globalVariableMapper.updateById(variable);
    }

    /**
     * 删除全局变量（物理删除）。
     */
    @Transactional
    public void delete(String id) {
        globalVariableMapper.deleteById(id);
    }

    /** TIMESTAMP/RANDOM 类型由后端生成值，忽略前端传入。 */
    private void prepareGeneratedValue(GlobalVariableEntity v) {
        if (TYPE_TIMESTAMP.equalsIgnoreCase(v.getType())) {
            v.setValue(String.valueOf(System.currentTimeMillis()));
        } else if (TYPE_RANDOM.equalsIgnoreCase(v.getType())) {
            v.setValue(String.valueOf(System.nanoTime() ^ System.currentTimeMillis()));
        }
    }

    private String encryptIfSecret(GlobalVariableEntity v) {
        if (!TYPE_SECRET.equalsIgnoreCase(v.getType())) {
            return v.getValue();
        }
        if (v.getValue() == null || v.getValue().isBlank()) {
            return "";
        }
        if (AesGcm.isCipherText(v.getValue())) {
            return v.getValue(); // 已是密文
        }
        return AesGcm.encrypt(v.getValue());
    }
}
