package com.treasurehunt.chat.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.Builder;

import java.io.Serializable;

/**
 * 店铺信息VO
 * 用于返回店铺的基本信息
 */
@Data
@Builder
@Schema(description = "店铺信息")
public class MallShopVO implements Serializable {
    
    @Schema(description = "店铺ID（与前端 shopId 一致）")
    private Long shopId;
    
    @Schema(description = "租户ID（所属商户）")
    private Long tenantId;
    
    @Schema(description = "店铺名称")
    private String shopName;
    
    @Schema(description = "店铺状态：active-活跃,inactive-禁用")
    private String shopStatus;
    
    @Schema(description = "店铺图标URL")
    private String shopIcon;
    
    @Schema(description = "联系电话")
    private String contactPhone;
    
    private static final long serialVersionUID = 1L;
}

