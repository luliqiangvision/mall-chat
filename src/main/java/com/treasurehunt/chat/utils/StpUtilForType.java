/*
 * Copyright 2020-2099 sa-token.cc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.treasurehunt.chat.utils;

import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.fun.SaFunction;
import cn.dev33.satoken.jwt.StpLogicJwtForSimple;
import cn.dev33.satoken.listener.SaTokenEventCenter;
import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.session.TokenSign;
import cn.dev33.satoken.stp.SaLoginModel;
import cn.dev33.satoken.stp.SaTokenInfo;
import cn.dev33.satoken.stp.StpLogic;

import java.util.List;

/**
 * @auther gaga
 * @description 支持多账号体系的通用认证工具类
 * 通过传入 type 参数来操作不同的账号体系
 * @date 2025/11/4
 */
public class StpUtilForType {

	private StpUtilForType() {}
	
	/**
	 * 常用的账号类型常量
	 */
	public static final String TYPE_MEMBER_LOGIN = "memberLogin";
	public static final String TYPE_AGENT_LOGIN = "agentLogin";
	
	/**
	 * 根据 type 获取对应的 StpLogic 对象
	 * 参考mall-portal的实现方式，强制使用StpLogicJwtForSimple，不依赖缓存
	 *
	 * @param type 账号类型
	 * @return StpLogic 对象
	 */
	private static StpLogic getStpLogic(String type) {
		StpLogic stpLogic = SaManager.getStpLogic(type);
		// 无论如何都使用StpLogicJwtForSimple，如果不存在或不是StpLogicJwtForSimple类型，则创建新的并注册
		if (stpLogic == null || !(stpLogic instanceof StpLogicJwtForSimple)) {
			stpLogic = new StpLogicJwtForSimple(type);
			SaManager.putStpLogic(stpLogic);
		}
		return stpLogic;
	}

	/**
	 * 检验当前会话是否已经登录，如未登录，则抛出异常
	 * 
	 * @param type 账号类型
	 */
	public static void checkLogin(String type) {
		getStpLogic(type).checkLogin();
	}

	/**
	 * 获取当前会话账号id，如果未登录，则抛出异常
	 *
	 * @param type 账号类型
	 * @return 账号id
	 */
	public static Object getLoginId(String type) {
		return getStpLogic(type).getLoginId();
	}

	/**
	 * 在当前会话写入指定 token 值
	 *
	 * @param type 账号类型
	 * @param tokenValue token 值
	 */
	public static void setTokenValue(String type, String tokenValue) {
		getStpLogic(type).setTokenValue(tokenValue);
	}

	/**
	 * 判断当前会话是否已经登录
	 *
	 * @param type 账号类型
	 * @return 已登录返回 true，未登录返回 false
	 */
	public static boolean isLogin(String type) {
		return getStpLogic(type).isLogin();
	}

	/**
	 * 会话登录
	 *
	 * @param type 账号类型
	 * @param id 账号id，建议的类型：（long | int | String）
	 */
	public static void login(String type, Object id) {
		getStpLogic(type).login(id);
	}

	/**
	 * 在当前客户端会话注销
	 * 
	 * @param type 账号类型
	 */
	public static void logout(String type) {
		getStpLogic(type).logout();
	}

	/**
	 * 获取当前 StpLogic 的账号类型
	 *
	 * @param type 账号类型
	 * @return 账号类型
	 */
	public static String getLoginType(String type) {
		return getStpLogic(type).getLoginType();
	}

	/**
	 * 安全的重置 StpLogic 对象
	 *
	 * @param type 账号类型
	 * @param newStpLogic 新的 StpLogic 对象
	 */
	public static void setStpLogic(String type, StpLogic newStpLogic) {
		SaManager.putStpLogic(newStpLogic);
		SaTokenEventCenter.doSetStpLogic(newStpLogic);
	}

	/**
	 * 获取 StpLogic 对象
	 *
	 * @param type 账号类型
	 * @return StpLogic 对象
	 */
	public static StpLogic getStpLogicPublic(String type) {
		return getStpLogic(type);
	}

	// ------------------- 获取 token 相关 -------------------

	public static String getTokenName(String type) {
		return getStpLogic(type).getTokenName();
	}

	public static void setTokenValue(String type, String tokenValue, int cookieTimeout) {
		getStpLogic(type).setTokenValue(tokenValue, cookieTimeout);
	}

	public static void setTokenValue(String type, String tokenValue, SaLoginModel loginModel) {
		getStpLogic(type).setTokenValue(tokenValue, loginModel);
	}

	public static String getTokenValue(String type) {
		return getStpLogic(type).getTokenValue();
	}

	public static String getTokenValueNotCut(String type) {
		return getStpLogic(type).getTokenValueNotCut();
	}

	public static SaTokenInfo getTokenInfo(String type) {
		return getStpLogic(type).getTokenInfo();
	}

	// ------------------- 登录相关操作 -------------------

	public static void login(String type, Object id, String device) {
		getStpLogic(type).login(id, device);
	}

	public static void login(String type, Object id, boolean isLastingCookie) {
		getStpLogic(type).login(id, isLastingCookie);
	}

	public static void login(String type, Object id, long timeout) {
		getStpLogic(type).login(id, timeout);
	}

	public static void login(String type, Object id, SaLoginModel loginModel) {
		getStpLogic(type).login(id, loginModel);
	}

	public static String createLoginSession(String type, Object id) {
		return getStpLogic(type).createLoginSession(id);
	}

	public static String createLoginSession(String type, Object id, SaLoginModel loginModel) {
		return getStpLogic(type).createLoginSession(id, loginModel);
	}

	// --- 注销 

	public static void logout(String type, Object loginId) {
		getStpLogic(type).logout(loginId);
	}

	public static void logout(String type, Object loginId, String device) {
		getStpLogic(type).logout(loginId, device);
	}

	public static void logoutByTokenValue(String type, String tokenValue) {
		getStpLogic(type).logoutByTokenValue(tokenValue);
	}

	public static void kickout(String type, Object loginId) {
		getStpLogic(type).kickout(loginId);
	}

	public static void kickout(String type, Object loginId, String device) {
		getStpLogic(type).kickout(loginId, device);
	}

	public static void kickoutByTokenValue(String type, String tokenValue) {
		getStpLogic(type).kickoutByTokenValue(tokenValue);
	}

	public static void replaced(String type, Object loginId, String device) {
		getStpLogic(type).replaced(loginId, device);
	}

	// 会话查询

	public static boolean isLogin(String type, Object loginId) {
		return getStpLogic(type).isLogin(loginId);
	}

	public static <T> T getLoginId(String type, T defaultValue) {
		return getStpLogic(type).getLoginId(defaultValue);
	}

	public static Object getLoginIdDefaultNull(String type) {
		return getStpLogic(type).getLoginIdDefaultNull();
	}

	public static String getLoginIdAsString(String type) {
		return getStpLogic(type).getLoginIdAsString();
	}

	public static int getLoginIdAsInt(String type) {
		return getStpLogic(type).getLoginIdAsInt();
	}

	public static long getLoginIdAsLong(String type) {
		return getStpLogic(type).getLoginIdAsLong();
	}

	public static Object getLoginIdByToken(String type, String tokenValue) {
		return getStpLogic(type).getLoginIdByToken(tokenValue);
	}

	public static Object getExtra(String type, String key) {
		return getStpLogic(type).getExtra(key);
	}

	public static Object getExtra(String type, String tokenValue, String key) {
		return getStpLogic(type).getExtra(tokenValue, key);
	}

	// ------------------- Account-Session 相关 -------------------

	public static SaSession getSessionByLoginId(String type, Object loginId, boolean isCreate) {
		return getStpLogic(type).getSessionByLoginId(loginId, isCreate);
	}

	public static SaSession getSessionBySessionId(String type, String sessionId) {
		return getStpLogic(type).getSessionBySessionId(sessionId);
	}

	public static SaSession getSessionByLoginId(String type, Object loginId) {
		return getStpLogic(type).getSessionByLoginId(loginId);
	}

	public static SaSession getSession(String type, boolean isCreate) {
		return getStpLogic(type).getSession(isCreate);
	}

	public static SaSession getSession(String type) {
		return getStpLogic(type).getSession();
	}

	// ------------------- Token-Session 相关 -------------------

	public static SaSession getTokenSessionByToken(String type, String tokenValue) {
		return getStpLogic(type).getTokenSessionByToken(tokenValue);
	}

	public static SaSession getTokenSession(String type) {
		return getStpLogic(type).getTokenSession();
	}

	public static SaSession getAnonTokenSession(String type) {
		return getStpLogic(type).getAnonTokenSession();
	}

	// ------------------- Active-Timeout token 最低活跃度 验证相关 -------------------

	public static void updateLastActiveToNow(String type) {
		getStpLogic(type).updateLastActiveToNow();
	}

	public static void checkActiveTimeout(String type) {
		getStpLogic(type).checkActiveTimeout();
	}

	// ------------------- 过期时间相关 -------------------

	public static long getTokenTimeout(String type) {
		return getStpLogic(type).getTokenTimeout();
	}

	public static long getTokenTimeout(String type, String token) {
		return getStpLogic(type).getTokenTimeout(token);
	}

	public static long getSessionTimeout(String type) {
		return getStpLogic(type).getSessionTimeout();
	}

	public static long getTokenSessionTimeout(String type) {
		return getStpLogic(type).getTokenSessionTimeout();
	}

	public static long getTokenActiveTimeout(String type) {
		return getStpLogic(type).getTokenActiveTimeout();
	}

	public static void renewTimeout(String type, long timeout) {
		getStpLogic(type).renewTimeout(timeout);
	}

	public static void renewTimeout(String type, String tokenValue, long timeout) {
		getStpLogic(type).renewTimeout(tokenValue, timeout);
	}

	// ------------------- 角色认证操作 -------------------

	public static List<String> getRoleList(String type) {
		return getStpLogic(type).getRoleList();
	}

	public static List<String> getRoleList(String type, Object loginId) {
		return getStpLogic(type).getRoleList(loginId);
	}

	public static boolean hasRole(String type, String role) {
		return getStpLogic(type).hasRole(role);
	}

	public static boolean hasRole(String type, Object loginId, String role) {
		return getStpLogic(type).hasRole(loginId, role);
	}

	public static boolean hasRoleAnd(String type, String... roleArray) {
		return getStpLogic(type).hasRoleAnd(roleArray);
	}

	public static boolean hasRoleOr(String type, String... roleArray) {
		return getStpLogic(type).hasRoleOr(roleArray);
	}

	public static void checkRole(String type, String role) {
		getStpLogic(type).checkRole(role);
	}

	public static void checkRoleAnd(String type, String... roleArray) {
		getStpLogic(type).checkRoleAnd(roleArray);
	}

	public static void checkRoleOr(String type, String... roleArray) {
		getStpLogic(type).checkRoleOr(roleArray);
	}

	// ------------------- 权限认证操作 -------------------

	public static List<String> getPermissionList(String type) {
		return getStpLogic(type).getPermissionList();
	}

	public static List<String> getPermissionList(String type, Object loginId) {
		return getStpLogic(type).getPermissionList(loginId);
	}

	public static boolean hasPermission(String type, String permission) {
		return getStpLogic(type).hasPermission(permission);
	}

	public static boolean hasPermission(String type, Object loginId, String permission) {
		return getStpLogic(type).hasPermission(loginId, permission);
	}

	public static boolean hasPermissionAnd(String type, String... permissionArray) {
		return getStpLogic(type).hasPermissionAnd(permissionArray);
	}

	public static boolean hasPermissionOr(String type, String... permissionArray) {
		return getStpLogic(type).hasPermissionOr(permissionArray);
	}

	public static void checkPermission(String type, String permission) {
		getStpLogic(type).checkPermission(permission);
	}

	public static void checkPermissionAnd(String type, String... permissionArray) {
		getStpLogic(type).checkPermissionAnd(permissionArray);
	}

	public static void checkPermissionOr(String type, String... permissionArray) {
		getStpLogic(type).checkPermissionOr(permissionArray);
	}

	// ------------------- id 反查 token 相关操作 -------------------

	public static String getTokenValueByLoginId(String type, Object loginId) {
		return getStpLogic(type).getTokenValueByLoginId(loginId);
	}

	public static String getTokenValueByLoginId(String type, Object loginId, String device) {
		return getStpLogic(type).getTokenValueByLoginId(loginId, device);
	}

	public static List<String> getTokenValueListByLoginId(String type, Object loginId) {
		return getStpLogic(type).getTokenValueListByLoginId(loginId);
	}

	public static List<String> getTokenValueListByLoginId(String type, Object loginId, String device) {
		return getStpLogic(type).getTokenValueListByLoginId(loginId, device);
	}

	public static List<TokenSign> getTokenSignListByLoginId(String type, Object loginId, String device) {
		return getStpLogic(type).getTokenSignListByLoginId(loginId, device);
	}

	public static String getLoginDevice(String type) {
		return getStpLogic(type).getLoginDevice();
	}

	// ------------------- 会话管理 -------------------

	public static List<String> searchTokenValue(String type, String keyword, int start, int size, boolean sortType) {
		return getStpLogic(type).searchTokenValue(keyword, start, size, sortType);
	}

	public static List<String> searchSessionId(String type, String keyword, int start, int size, boolean sortType) {
		return getStpLogic(type).searchSessionId(keyword, start, size, sortType);
	}

	public static List<String> searchTokenSessionId(String type, String keyword, int start, int size, boolean sortType) {
		return getStpLogic(type).searchTokenSessionId(keyword, start, size, sortType);
	}

	// ------------------- 账号封禁 -------------------

	public static void disable(String type, Object loginId, long time) {
		getStpLogic(type).disable(loginId, time);
	}

	public static boolean isDisable(String type, Object loginId) {
		return getStpLogic(type).isDisable(loginId);
	}

	public static void checkDisable(String type, Object loginId) {
		getStpLogic(type).checkDisable(loginId);
	}

	public static long getDisableTime(String type, Object loginId) {
		return getStpLogic(type).getDisableTime(loginId);
	}

	public static void untieDisable(String type, Object loginId) {
		getStpLogic(type).untieDisable(loginId);
	}

	// ------------------- 分类封禁 -------------------

	public static void disable(String type, Object loginId, String service, long time) {
		getStpLogic(type).disable(loginId, service, time);
	}

	public static boolean isDisable(String type, Object loginId, String service) {
		return getStpLogic(type).isDisable(loginId, service);
	}

	public static void checkDisable(String type, Object loginId, String... services) {
		getStpLogic(type).checkDisable(loginId, services);
	}

	public static long getDisableTime(String type, Object loginId, String service) {
		return getStpLogic(type).getDisableTime(loginId, service);
	}

	public static void untieDisable(String type, Object loginId, String... services) {
		getStpLogic(type).untieDisable(loginId, services);
	}

	// ------------------- 阶梯封禁 -------------------

	public static void disableLevel(String type, Object loginId, int level, long time) {
		getStpLogic(type).disableLevel(loginId, level, time);
	}

	public static void disableLevel(String type, Object loginId, String service, int level, long time) {
		getStpLogic(type).disableLevel(loginId, service, level, time);
	}

	public static boolean isDisableLevel(String type, Object loginId, int level) {
		return getStpLogic(type).isDisableLevel(loginId, level);
	}

	public static boolean isDisableLevel(String type, Object loginId, String service, int level) {
		return getStpLogic(type).isDisableLevel(loginId, service, level);
	}

	public static void checkDisableLevel(String type, Object loginId, int level) {
		getStpLogic(type).checkDisableLevel(loginId, level);
	}

	public static void checkDisableLevel(String type, Object loginId, String service, int level) {
		getStpLogic(type).checkDisableLevel(loginId, service, level);
	}

	public static int getDisableLevel(String type, Object loginId) {
		return getStpLogic(type).getDisableLevel(loginId);
	}

	public static int getDisableLevel(String type, Object loginId, String service) {
		return getStpLogic(type).getDisableLevel(loginId, service);
	}

	// ------------------- 临时身份切换 -------------------

	public static void switchTo(String type, Object loginId) {
		getStpLogic(type).switchTo(loginId);
	}

	public static void endSwitch(String type) {
		getStpLogic(type).endSwitch();
	}

	public static boolean isSwitch(String type) {
		return getStpLogic(type).isSwitch();
	}

	public static void switchTo(String type, Object loginId, SaFunction function) {
		getStpLogic(type).switchTo(loginId, function);
	}

	// ------------------- 二级认证 -------------------

	public static void openSafe(String type, long safeTime) {
		getStpLogic(type).openSafe(safeTime);
	}

	public static void openSafe(String type, String service, long safeTime) {
		getStpLogic(type).openSafe(service, safeTime);
	}

	public static boolean isSafe(String type) {
		return getStpLogic(type).isSafe();
	}

	public static boolean isSafe(String type, String service) {
		return getStpLogic(type).isSafe(service);
	}

	public static boolean isSafe(String type, String tokenValue, String service) {
		return getStpLogic(type).isSafe(tokenValue, service);
	}

	public static void checkSafe(String type) {
		getStpLogic(type).checkSafe();
	}

	public static void checkSafe(String type, String service) {
		getStpLogic(type).checkSafe(service);
	}

	public static long getSafeTime(String type) {
		return getStpLogic(type).getSafeTime();
	}

	public static long getSafeTime(String type, String service) {
		return getStpLogic(type).getSafeTime(service);
	}

	public static void closeSafe(String type) {
		getStpLogic(type).closeSafe();
	}

	public static void closeSafe(String type, String service) {
		getStpLogic(type).closeSafe(service);
	}
}
