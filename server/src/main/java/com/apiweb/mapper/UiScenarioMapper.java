package com.apiweb.mapper;

import com.apiweb.entity.UiScenarioEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * UI 测试场景数据访问层（MyBatis-Plus）。
 *
 * <p>继承 {@code BaseMapper<UiScenarioEntity>} 即具备单表 CRUD 能力（selectById / insert /
 * updateById / deleteById 及 Lambda 条件构造器查询），因此本接口通常无需声明方法；
 * 若需要多表关联或动态 SQL，请在此新增方法并在
 * {@code resources/mapper} 下编写对应的 XML 映射文件。</p>
 *
 * <p>注意：所有查询默认不会过滤逻辑删除字段（deleted_at），需要排除已删除数据时应
 * 显式追加 {@code .isNull(Entity::getDeletedAt)} 条件。</p>
 */
@Mapper
public interface UiScenarioMapper extends BaseMapper<UiScenarioEntity> {
}
