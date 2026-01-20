package com.rcpawn.log.mapper;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.rcpawn.common.entity.GatewayLogDTO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
//@TableName("gateway_log")
public interface GatewayLogMapper extends BaseMapper<GatewayLogDTO> {
}