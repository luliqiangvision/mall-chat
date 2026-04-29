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

    /** 技术主键（自增，ORM 主键） */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 店铺业务ID（与前端 shopId 一致，库内唯一；业务上以此标识店铺，非自增主键 id）
     */
    @TableField("shop_id")
    private Long shopId;

    /** 租户ID（所属商户） */
    @TableField("tenant_id")
    private Long tenantId;

    /** 业务线标识（由网关透传 X-Business-Line） */
    @TableField("business_line")
    private String businessLine;

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
