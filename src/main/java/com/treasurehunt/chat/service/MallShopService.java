package com.treasurehunt.chat.service;

import com.treasurehunt.chat.domain.MallShopDO;
import com.treasurehunt.chat.mapper.MallShopMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

/**
 * 店铺信息管理服务
 * 负责店铺信息的查询和管理
 */
@Slf4j
@Service
public class MallShopService {
    
    @Autowired
    private MallShopMapper mallShopMapper;
    
    /**
     * 根据店铺ID查询店铺信息
     * 
     * @param id 店铺主键ID
     * @return 店铺信息，如果不存在则返回null
     */
    public MallShopDO getShopById(Long id) {
        try {
            MallShopDO shop = mallShopMapper.selectById(id);
            log.debug("查询店铺信息: id={}, shop={}", id, shop);
            return shop;
        } catch (Exception e) {
            log.error("查询店铺信息失败: id={}", id, e);
            return null;
        }
    }
    
    /**
     * 根据租户ID查询店铺列表
     * 
     * @param tenantId 租户ID
     * @return 店铺列表
     */
    public List<MallShopDO> getShopsByTenantId(Long tenantId) {
        try {
            List<MallShopDO> shops = mallShopMapper.selectByTenantId(tenantId);
            log.debug("查询租户店铺列表: tenantId={}, shopCount={}", tenantId, shops.size());
            return shops;
        } catch (Exception e) {
            log.error("查询租户店铺列表失败: tenantId={}", tenantId, e);
            return Collections.emptyList();
        }
    }
    
    /**
     * 根据租户ID和状态查询店铺列表
     * 
     * @param tenantId 租户ID
     * @param shopStatus 店铺状态
     * @return 店铺列表
     */
    public List<MallShopDO> getShopsByTenantIdAndStatus(Long tenantId, String shopStatus) {
        try {
            List<MallShopDO> shops = mallShopMapper.selectByTenantIdAndStatus(tenantId, shopStatus);
            log.debug("查询租户店铺列表(按状态): tenantId={}, shopStatus={}, shopCount={}", 
                    tenantId, shopStatus, shops.size());
            return shops;
        } catch (Exception e) {
            log.error("查询租户店铺列表(按状态)失败: tenantId={}, shopStatus={}", tenantId, shopStatus, e);
            return Collections.emptyList();
        }
    }
    
    /**
     * 根据店铺ID和租户ID查询店铺信息（用于权限校验）
     * 
     * @param id 店铺主键ID
     * @param tenantId 租户ID
     * @return 店铺信息，如果不存在或不属于该租户则返回null
     */
    public MallShopDO getShopByIdAndTenantId(Long id, Long tenantId) {
        try {
            MallShopDO shop = mallShopMapper.selectByIdAndTenantId(id, tenantId);
            log.debug("查询店铺信息(权限校验): id={}, tenantId={}, shop={}", id, tenantId, shop);
            return shop;
        } catch (Exception e) {
            log.error("查询店铺信息(权限校验)失败: id={}, tenantId={}", id, tenantId, e);
            return null;
        }
    }
    
    /**
     * 验证店铺是否属于指定租户
     * 
     * @param id 店铺主键ID
     * @param tenantId 租户ID
     * @return 是否属于该租户
     */
    public boolean validateShopBelongsToTenant(Long id, Long tenantId) {
        MallShopDO shop = getShopByIdAndTenantId(id, tenantId);
        return shop != null;
    }
    
    /**
     * 验证店铺是否处于活跃状态
     * 
     * @param id 店铺主键ID
     * @return 是否活跃
     */
    public boolean isShopActive(Long id) {
        MallShopDO shop = getShopById(id);
        return shop != null && "active".equals(shop.getShopStatus());
    }
}

