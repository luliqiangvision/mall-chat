package com.treasurehunt.chat.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.treasurehunt.chat.domain.MallShopDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 店铺信息 Mapper（MyBatis-Plus）
 * 负责店铺信息的CRUD操作
 */
@Mapper
public interface MallShopMapper extends BaseMapper<MallShopDO> {
    
    /**
     * 根据店铺ID查询店铺信息
     * 
     * @param id 店铺主键ID
     * @return 店铺信息
     */
    @Select("SELECT * FROM mall_shop WHERE id = #{id}")
    MallShopDO selectById(@Param("id") Long id);
    
    /**
     * 根据租户ID查询店铺列表
     * 
     * @param tenantId 租户ID
     * @return 店铺列表
     */
    @Select("SELECT * FROM mall_shop WHERE tenant_id = #{tenantId} AND shop_status = 'active' ORDER BY shop_name")
    List<MallShopDO> selectByTenantId(@Param("tenantId") Long tenantId);
    
    /**
     * 根据租户ID和状态查询店铺列表
     * 
     * @param tenantId 租户ID
     * @param shopStatus 店铺状态
     * @return 店铺列表
     */
    @Select("SELECT * FROM mall_shop WHERE tenant_id = #{tenantId} AND shop_status = #{shopStatus} ORDER BY shop_name")
    List<MallShopDO> selectByTenantIdAndStatus(@Param("tenantId") Long tenantId, @Param("shopStatus") String shopStatus);
    
    /**
     * 根据店铺ID和租户ID查询店铺信息（用于权限校验）
     * 
     * @param id 店铺主键ID
     * @param tenantId 租户ID
     * @return 店铺信息
     */
    @Select("SELECT * FROM mall_shop WHERE id = #{id} AND tenant_id = #{tenantId}")
    MallShopDO selectByIdAndTenantId(@Param("id") Long id, @Param("tenantId") Long tenantId);
}

