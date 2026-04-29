package com.treasurehunt.chat.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.treasurehunt.chat.domain.MallShopDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 店铺信息 Mapper（MyBatis-Plus）
 * 负责店铺信息的CRUD操作
 */
@Mapper
public interface MallShopMapper extends BaseMapper<MallShopDO> {

    /**
     * 按业务店铺 ID（shop_id）查询，与 {@link BaseMapper#selectById}（按自增 id）区分
     */
    MallShopDO selectByShopId(@Param("shopId") Long shopId);

    /**
     * 根据业务线和店铺ID查询店铺信息
     */
    MallShopDO selectByBusinessLineAndId(@Param("businessLine") String businessLine, @Param("shopId") Long shopId);

    /**
     * 根据租户ID查询店铺列表
     */
    List<MallShopDO> selectByTenantId(@Param("tenantId") Long tenantId);

    /**
     * 根据业务线和租户ID查询店铺列表
     */
    List<MallShopDO> selectByBusinessLineAndTenantId(@Param("businessLine") String businessLine,
                                                     @Param("tenantId") Long tenantId);

    /**
     * 根据租户ID和状态查询店铺列表
     */
    List<MallShopDO> selectByTenantIdAndStatus(@Param("tenantId") Long tenantId, @Param("shopStatus") String shopStatus);

    /**
     * 根据业务线、租户ID和状态查询店铺列表
     */
    List<MallShopDO> selectByBusinessLineAndTenantIdAndStatus(@Param("businessLine") String businessLine,
                                                              @Param("tenantId") Long tenantId,
                                                              @Param("shopStatus") String shopStatus);

    /**
     * 根据店铺ID和租户ID查询店铺信息（用于权限校验）
     */
    MallShopDO selectByIdAndTenantId(@Param("shopId") Long shopId, @Param("tenantId") Long tenantId);

    /**
     * 根据业务线、店铺ID和租户ID查询店铺信息（用于权限校验）
     */
    MallShopDO selectByBusinessLineAndIdAndTenantId(@Param("businessLine") String businessLine,
                                                     @Param("shopId") Long shopId,
                                                     @Param("tenantId") Long tenantId);
}
