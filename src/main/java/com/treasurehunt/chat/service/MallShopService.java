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
     * @param shopId 店铺ID（与前端 shopId / 库字段 shop_id 一致）
     * @return 店铺信息，如果不存在则返回null
     */
    public MallShopDO getShopById(Long shopId) {
        try {
            MallShopDO shop = mallShopMapper.selectByShopId(shopId);
            log.debug("查询店铺信息: shopId={}, shop={}", shopId, shop);
            return shop;
        } catch (Exception e) {
            log.error("查询店铺信息失败: shopId={}", shopId, e);
            return null;
        }
    }

    /**
     * 根据业务线和店铺ID查询店铺信息
     *
     * @param businessLine 业务线
     * @param shopId 店铺ID（与前端 shopId 一致）
     * @return 店铺信息，如果不存在则返回null
     */
    public MallShopDO getShopByBusinessLineAndId(String businessLine, Long shopId) {
        try {
            MallShopDO shop = mallShopMapper.selectByBusinessLineAndId(businessLine, shopId);
            log.debug("查询店铺信息(按业务线): businessLine={}, shopId={}, shop={}", businessLine, shopId, shop);
            return shop;
        } catch (Exception e) {
            log.error("查询店铺信息(按业务线)失败: businessLine={}, shopId={}", businessLine, shopId, e);
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
     * 根据业务线和租户ID查询店铺列表
     */
    public List<MallShopDO> getShopsByBusinessLineAndTenantId(String businessLine, Long tenantId) {
        try {
            List<MallShopDO> shops = mallShopMapper.selectByBusinessLineAndTenantId(businessLine, tenantId);
            log.debug("查询租户店铺列表(按业务线): businessLine={}, tenantId={}, shopCount={}",
                    businessLine, tenantId, shops.size());
            return shops;
        } catch (Exception e) {
            log.error("查询租户店铺列表(按业务线)失败: businessLine={}, tenantId={}", businessLine, tenantId, e);
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
     * 根据业务线、租户ID和状态查询店铺列表
     */
    public List<MallShopDO> getShopsByBusinessLineAndTenantIdAndStatus(String businessLine, Long tenantId, String shopStatus) {
        try {
            List<MallShopDO> shops = mallShopMapper.selectByBusinessLineAndTenantIdAndStatus(businessLine, tenantId, shopStatus);
            log.debug("查询租户店铺列表(按业务线+状态): businessLine={}, tenantId={}, shopStatus={}, shopCount={}",
                    businessLine, tenantId, shopStatus, shops.size());
            return shops;
        } catch (Exception e) {
            log.error("查询租户店铺列表(按业务线+状态)失败: businessLine={}, tenantId={}, shopStatus={}",
                    businessLine, tenantId, shopStatus, e);
            return Collections.emptyList();
        }
    }
    
    /**
     * 根据店铺ID和租户ID查询店铺信息（用于权限校验）
     *
     * @param shopId 店铺ID（与前端 shopId 一致）
     * @param tenantId 租户ID
     * @return 店铺信息，如果不存在或不属于该租户则返回null
     */
    public MallShopDO getShopByIdAndTenantId(Long shopId, Long tenantId) {
        try {
            MallShopDO shop = mallShopMapper.selectByIdAndTenantId(shopId, tenantId);
            log.debug("查询店铺信息(权限校验): shopId={}, tenantId={}, shop={}", shopId, tenantId, shop);
            return shop;
        } catch (Exception e) {
            log.error("查询店铺信息(权限校验)失败: shopId={}, tenantId={}", shopId, tenantId, e);
            return null;
        }
    }

    /**
     * 验证店铺是否属于指定租户
     *
     * @param shopId 店铺ID（与前端 shopId 一致）
     * @param tenantId 租户ID
     * @return 是否属于该租户
     */
    public boolean validateShopBelongsToTenant(Long shopId, Long tenantId) {
        MallShopDO shop = getShopByIdAndTenantId(shopId, tenantId);
        return shop != null;
    }

    /**
     * 验证店铺是否处于活跃状态
     *
     * @param shopId 店铺ID（与前端 shopId 一致）
     * @return 是否活跃
     */
    public boolean isShopActive(Long shopId) {
        MallShopDO shop = getShopById(shopId);
        return shop != null && "active".equals(shop.getShopStatus());
    }
}


