package com.apiweb.mapper;

import com.apiweb.entity.RecycleBinConfigEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 回收站自动清理配置 Mapper。
 */
@Mapper
public interface RecycleBinConfigMapper extends BaseMapper<RecycleBinConfigEntity> {
}