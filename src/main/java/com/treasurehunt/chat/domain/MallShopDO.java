package com.treasurehunt.chat.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * 店铺信息表
 * 管理店铺的基本信息和状态
 */
@Data
@TableName("mall_shop")
public class MallShopDO {

    /** 自增主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 租户ID（所属商户） */
    @TableField("tenant_id")
    private Long tenantId;

    /** 店铺名称 */
    @TableField("shop_name")
    private String shopName;

    /** 
     * 店铺状态：
     * - active: 活跃
     * - inactive: 禁用
     */
    @TableField("shop_status")
    private String shopStatus;

    /** 店铺图标URL */
    @TableField("shop_icon")
    private String shopIcon;

    /** 联系电话 */
    @TableField("contact_phone")
    private String contactPhone;

    /** 创建时间 */
    @TableField("created_at")
    private Date createdAt;

    /** 更新时间 */
    @TableField("updated_at")
    private Date updatedAt;
}

