package com.rcpawn.log.mapper;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.rcpawn.common.entity.GatewayLogDTO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
// 指定表名，DTO 里的驼峰会自动映射到下划线字段
@TableName("gateway_log")
public interface GatewayLogMapper extends BaseMapper<GatewayLogDTO> {
}