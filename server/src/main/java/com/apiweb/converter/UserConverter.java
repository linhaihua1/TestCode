package com.apiweb.converter;

import com.apiweb.entity.UserEntity;
import com.apiweb.vo.user.UserVO;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import java.util.List;

/**
 * User 实体 ↔ DTO/VO 转换器（MapStruct 编译期生成实现）。
 *
 * <p>统一维护字段映射关系，避免手写 getter/setter 转换代码。
 * 注意：{@code UserVO} 不含 passwordHash 字段，MapStruct 自动忽略，
 * 从源头杜绝密码哈希外泄（无需也不应显式声明）。</p>
 */
@Mapper(componentModel = "spring")
public interface UserConverter {

    UserConverter INSTANCE = Mappers.getMapper(UserConverter.class);

    /** Entity → VO（passwordHash 自动不映射） */
    UserVO toVO(UserEntity entity);

    /** Entity 列表 → VO 列表 */
    List<UserVO> toVOList(List<UserEntity> entities);
}
