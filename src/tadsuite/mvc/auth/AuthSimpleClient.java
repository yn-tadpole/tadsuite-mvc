package tadsuite.mvc.auth;

import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

import tadsuite.mvc.Application;
import tadsuite.mvc.core.MvcControllerBase;
import tadsuite.mvc.core.MvcRequest;
import tadsuite.mvc.jdbc.Jdbc;
import tadsuite.mvc.jdbc.JdbcParams;
import tadsuite.mvc.logging.LogFactory;
import tadsuite.mvc.logging.Logger;
import tadsuite.mvc.utils.Constants;
import tadsuite.mvc.utils.Utils;


/**
 * 本类可实现简单登录（不实现单点登录，避免大多数数据交换）。
 * @author TADPOLE
 */

public final class AuthSimpleClient extends AuthClient {
	
	private static final int VALIDATE_WRONG_PASSWORD_NUMBERS=60; //同一IP允许连续简单方式输入密码次数，超过则增加验证码
	private static final int LOCK_WRONG_PASSWORD_NUMBERS=90; //同一IP允许连续输入密码次数，超过则锁定IP
	private static final int LOCK_WRONG_VALCODE_NUMBERS=300; //同一IP允许连续输入错误的验证码次数，超过则锁定IP
	private static final int LOCK_USERNAME_NUMBERS=30; //同一用户名允许尝试密码的次数，超过则锁定用户
	private String denyNumLockerForWrongPassword;
	private String denyNumLockerForWrongValidateCode;
	
	/**存储用户会话的SESSION名称*/
	private String  stateSessionName;
	private String  stateValStringCookieName;
	/**存储用户交换信息的SESSION名称*/
	private String  stateIdCookieName;
	private String  stateIdValStringCookieName;
	/**存储用户交换信息的SESSION名称*/
	private static final String  SWAP_KEY_SESSION_NAME="SWAP_KEY";
	/**用户会话类，用户登录成功后将在SESSION中存储该类的一个实例*/
	
	private static final String VIRTUAL_USERNAME="*tadpole*";

	//Configuration
	private boolean useValidationCode;
	private String passwordEncrypt;
	private int expireMinute;
	private boolean canKeepState;
	private int cookieSaveTime=-1;
	private String cookieDomain;
	private String cookiePath;
	private boolean bindClientIP;
	private String dataSource;
	private String tableName;
	private String tableColList;
		
	private MvcRequest request;
	protected Jdbc jdbc;
	private long timer=0;
	private boolean ignoreValString=false;
	private Logger authLogger=LogFactory.getLogger(Constants.LOGGER_NAME_AUTH);
	
	private String authType="Simple";
	
	/**
	 * 初始化用户会话
	 * @param appId
	 * @param controller
	 * @param ignoreValString 用于在特殊情况下忽略Cookie中的验证串以及User-Agent检查，以适应如FLASH上传之类的进程能正常工作。
	 */
	public AuthSimpleClient(AuthClientConfig config, MvcControllerBase controller, boolean isIgnoreValString) {
		
		timer=System.currentTimeMillis();
		this.request=controller.request;
		this.config=config;
		denyNumLockerForWrongPassword="DENY_NUM_WP_"+request.getRemoteAddr();
		denyNumLockerForWrongValidateCode="DENY_NUM_WV_"+request.getRemoteAddr();
		ignoreValString=isIgnoreValString;
		
		loginTemplate=config.get("loginTemplate", MvcControllerBase.RESULT_LOGIN);
		useValidationCode=config.get("useValidationCode", "true").equals("true") || config.get("useValidationCode", "true").equals("Y") || config.get("useValidationCode", "true").equals("1");
		bindClientIP=config.get("bindClientIP", "true").equals("true") || config.get("bindClientIP", "true").equals("Y") || config.get("bindClientIP", "true").equals("1");
		canKeepState=config.get("canKeepState", "false").equals("true") || config.get("canKeepState", "false").equals("Y") || config.get("canKeepState", "false").equals("1");
		expireMinute=Utils.parseInt(config.get("expireMinute", "60"), 60);
		passwordEncrypt=config.get("passwordEncrypt", "");
		cookieDomain=config.get("cookieDomain", ""); //不能加默认服务器名称，防止负载均衡丢失会话 request.getServerName()
		cookiePath=config.get("cookiePath", request.getContextPath()+"/");//简单会话可以加路径
		dataSource=config.get("dataSource", "");
		tableName = config.get("tableName", "");
		tableColList = config.get("tableColList", "");
		
		stateSessionName="STATE_"+Utils.md5(config.authAppId+request.getContextPath());
		stateValStringCookieName = "S" + Utils.md5("SVSCN" + config.authAppId + request.getContextPath()).toUpperCase();
		stateIdCookieName = "S" + Utils.md5("SICN" + config.authAppId + request.getContextPath()).toUpperCase();
		stateIdValStringCookieName = "S" + Utils.md5("SIVCN" + config.authAppId + request.getContextPath()).toUpperCase();
		logined=false;
		
		if (config.authAppId.length()<1) {
			MvcControllerBase.endExecuting("AuthClient - lost AuthAppId.");
			return;
		}
		if (dataSource.length()>0) {
			jdbc=controller.jdbc.create(dataSource);
		} else {
			jdbc=controller.jdbc;
		}
		
		request.getResponse().setHeader("Pragma","No-cache");
		request.getResponse().setHeader("Cache-Control","no-cache, no-store");
		request.getResponse().setDateHeader("Expires", 0);
		
		String authAction = request.readInput("auth_action");
		if (authAction.equals("login")) {// 已提交用户名密码
			String result = login(request.readInput("fdUsername"), request.readInput("fdPassword"), request.readInput("fdValidationStr"), request.readInput("fdSaveState").equals("1"));
			MvcControllerBase.endExecuting(MvcControllerBase.RESULT_TEXT, buildResult(true, result.equals("success") ? "success" : "error", result));
			return;

		} else if (authAction.equals("getval")) {//获取验证码
			generateValidateCode();
			MvcControllerBase.endExecuting();
			return;

		} else if (authAction.equals("checkval")) {// 已提交用户名密码
			String result = checkValidateCode(request.readInput("fdValidationStr"));
			MvcControllerBase.endExecuting(MvcControllerBase.RESULT_TEXT, result);
			return;

		} else if (authAction.equals("logout")) { // 检测是否请求退出系统
			logout();
			String url = request.readInput("url").length() > 0 ? request.readInput("url") : request.getContextPath() + "/";
			request.getResponse().sendRedirect(url);
			MvcControllerBase.endExecuting();
			return;
		}

		try {
			state = request.sessionRead(stateSessionName) != null ? (AuthUserState) request.sessionRead(stateSessionName) : null;
		} catch (ClassCastException e) {
			state = null;
		}
		if (state != null) {// 如果存在登录会话
			String valString = (String) request.cookieRead(stateValStringCookieName);
			String valStringSession = buildSessionValString(request, state.stateId, bindClientIP);
			if (ignoreValString || valStringSession.equals(valString)) {
				if (refreshUserState(false)) {
					logined = true;
					long totalTime = System.currentTimeMillis() - timer;
					Logger performanceLogger = LogFactory.getLogger(Constants.LOGGER_NAME_PERFORMANCE);
					if (totalTime > 3000) {
						performanceLogger.warn("AuthClient init finished - {}ms", totalTime);
					} else if (totalTime > 1000) {
						performanceLogger.info("AuthClient init finished - {}ms", totalTime);
					} else if (performanceLogger.isDebugEnabled()) {
						performanceLogger.debug("AuthClient init finished - {}ms", totalTime);
					}
					return;
				} else {//会话超时，不再尝试重建会话
					state=null;
					return;
				}

			} else {// 会话无效，只有Session存在，Cookie丢失，才会出现这种情况，最早因为Cookie作用范围的BUG导致此问题，正常情况下除非出现Session劫持，否则不会再现。
				authLogger.warn("AuthClient - wrong validate string : {}, should be : {}, from : {}", valString, valStringSession, request.getRemoteAddr());
				state = null;
				request.sessionDelete(stateSessionName);
				request.cookieDelete(stateValStringCookieName, cookiePath, cookieDomain);
				request.sessionReset();
			}
		}
		
		// 没有会话，或者会话验证为无效，则尝试使用Cookie值恢复会话。由于恢复会话仅对同一IP，所以认为可以保证安全。
		String stateId = request.cookieRead(stateIdCookieName);
		String stateIdValString = request.cookieRead(stateIdValStringCookieName);
		if (stateIdValString != null && stateIdValString.equals(buildCookieValString(request, stateId))) {
			if (!recoveryUserState(stateId)) {
				request.cookieDelete(stateIdCookieName, cookiePath, cookieDomain);
				request.cookieDelete(stateIdValStringCookieName, cookiePath, cookieDomain);
			}
		}
	}
	
	/**判断用户是否登录成功，失败则显示登录对话框
	 * @param loginTemplateUrl 登录对话框模板路径
	 * @param noteMessage 登录时的提示信息
	 */
	public void checkLogin() {
		
		Application.sysLock_check(request.getRemoteAddr(), true);
		
		if (logined) {//只判断登录，不判断是否有进入的权限。(!bCheckAppId && logined) || testWithAppId("", "", currentAppId, false)
			return;
		} else {
			int wrongPasswordCount=Application.readDenyNum(denyNumLockerForWrongPassword);
			if (wrongPasswordCount>=VALIDATE_WRONG_PASSWORD_NUMBERS) {//要考虑同一WIFI的问题
				useValidationCode=true;
			}
			
			request.generateTokenMark(true, null);
			request.getRootMap().put("auth_type", "simple");
			request.getRootMap().put("auth_path", "");
			request.getRootMap().put("auth_useValidationCode", useValidationCode);
			request.getRootMap().put("auth_canKeepState", canKeepState);
			request.getRootMap().put("auth_passwordEncrypt", passwordEncrypt);
			request.getRootMap().put("auth_swapKey", generateSwapKey());
			MvcControllerBase.endExecuting(loginTemplate, "");
			return;
		}
	}
	
	private void generateValidateCode() {
		int backgroundColorR=Math.max(0, Math.min(255, Utils.parseInt(request.readInput("r"), 200)));
		int backgroundColorG=Math.max(0, Math.min(255, Utils.parseInt(request.readInput("g"), 200)));
		int backgroundColorB=Math.max(0, Math.min(255, Utils.parseInt(request.readInput("b"), 200)));
		int width=Math.max(60, Math.min(160, Utils.parseInt(request.readInput("w"), 80)));
		int height=Math.max(20, Math.min(60, Utils.parseInt(request.readInput("h"), 30)));
		request.getResponse().setContentType("image/png");
		ValidationImageUtils.generate("VALCODE"+config.authAppId, 4, width, height, backgroundColorR, backgroundColorG, backgroundColorB, request);
	}

	private String checkValidateCode(String validationStr) {
		request.checkCsrfToken();
		Application.sysLock_check(request.getRemoteAddr(), true);
		
		String sessionValidationStr=(String) request.sessionRead("VALCODE"+config.authAppId);
		if (sessionValidationStr==null || sessionValidationStr.length()<1 || !validationStr.equalsIgnoreCase(sessionValidationStr)) {
			String strRemoteIP=request.getRemoteAddr();
			authLogger.trace("AuthClient - invalid Validation Code : {}", strRemoteIP);
			int wrongCount=Application.readDenyNum(denyNumLockerForWrongValidateCode);
			if (wrongCount>=LOCK_WRONG_VALCODE_NUMBERS) {
				Application.sysLock_lock(strRemoteIP, 60);
				Application.deleteDenyNum(denyNumLockerForWrongPassword); //锁定IP后可以清除密码输入错误的次数
				Application.deleteDenyNum(denyNumLockerForWrongValidateCode); //锁定IP后可以清除输入错误的次数
				authLogger.warn("AuthClient - too times for wrong validation code : {}", strRemoteIP);
			} else {
				Application.writeDenyNum(denyNumLockerForWrongValidateCode, wrongCount+1);
			}
			return "0";
		} else {
			return "1";
		}
	}

	/**使用所提供的用户名、密码登录系统，仅限Simple方式的认证。
	 * @param jdbc  数据库对象
	  */
	private String login(String username, String password, String validationStr, boolean keepState) {
		request.checkCsrfToken();
		cookieSaveTime=canKeepState && keepState ? 24*60*60  : -1;
		
		String strRemoteIP=request.getRemoteAddr();
		if (!Application.sysLock_check(strRemoteIP, false)) {
			authLogger.info("AuthClient - ip has been locked : {}", request.getRemoteAddr());
			return request.readLocaleText("This_IP_Has_Been_Blocked");
		}
		int wrongPasswordCount=Application.readDenyNum(denyNumLockerForWrongPassword);
		if (wrongPasswordCount>=VALIDATE_WRONG_PASSWORD_NUMBERS) {//要考虑同一WIFI的问题
			useValidationCode=true;
		}
		if (wrongPasswordCount>=LOCK_WRONG_PASSWORD_NUMBERS-1) {//要考虑同一WIFI的问题
			Application.sysLock_lock(strRemoteIP, 60);
			Application.deleteDenyNum(denyNumLockerForWrongValidateCode); //锁定IP后可以清除输入错误的次数
			Application.deleteDenyNum(denyNumLockerForWrongPassword); //锁定IP后可以清除密码输入错误的次数
			authLogger.warn("AuthClient - too times for wrong password : {}", request.getRemoteAddr());
			return request.readLocaleText("Too_Times_To_Try_Password");
		}
		
		if (useValidationCode) {
			String sessionValidationStr=(String) request.sessionRead("VALCODE"+config.authAppId);
			request.sessionWrite("VALCODE"+config.authAppId, ""); //清除，以便强制刷新验证码
			if (sessionValidationStr==null || sessionValidationStr.length()<4 || !validationStr.equalsIgnoreCase(sessionValidationStr)) {
				authLogger.trace("AuthClient - invalid Validation Code : {}", request.getRemoteAddr());
				int wrongCount=Application.readDenyNum(denyNumLockerForWrongValidateCode);
				if (wrongCount>=LOCK_WRONG_VALCODE_NUMBERS) {
					Application.sysLock_lock(strRemoteIP, 60);
					Application.deleteDenyNum(denyNumLockerForWrongValidateCode); //锁定IP后可以清除输入错误的次数
					Application.deleteDenyNum(denyNumLockerForWrongPassword); //锁定IP后可以清除密码输入错误的次数
					authLogger.warn("AuthClient - too times for wrong validation code : {}", strRemoteIP);
				} else {
					Application.writeDenyNum(denyNumLockerForWrongValidateCode, wrongCount+1);
				}
				return request.readLocaleText("Validation_Code_Error");
			}
		}
		if (username.length()<1 || username.length()>50) {
			return request.readLocaleText("Username_Is_Required");
		}
		String denyNumLockerForUsername="DENY_NUM_USER_"+username;
		int wrongPasswordForUsername=Application.readDenyNum(denyNumLockerForUsername);
		if (wrongPasswordForUsername>=LOCK_USERNAME_NUMBERS) {//这里不再锁IP，而不保留会该用户的锁定（一小时）
			authLogger.warn("AuthClient - too times for wrong password for user : {} ", username);
			return request.readLocaleText("Too_Times_To_Try_Password");
		}
				
		StringBuffer colString=new StringBuffer();
		LinkedHashMap<String, String> colMap=new LinkedHashMap<String, String>();
		for (String item : tableColList.split(",")) {
			int index=item.indexOf(":");
			if (index!=-1) {
				String sysCol=item.substring(0, index).trim();
				String tableCol=item.substring(index+1).trim();
				colMap.put(sysCol, tableCol);
				colString.append(tableCol.length()>0 ? tableCol : "''").append(" as ").append(sysCol).append(", ");
			}
		}
		if (colString.length()<3) {
			return "System Configuration Error : simple_TableColList is too short.";
		}
		colString.delete(colString.length()-2, colString.length());
		
		Map<String, Object> row=jdbc.queryRow("select  "+colString.toString()+" from "+tableName+" where "+colMap.get("username")+"=?", username);
		if (row==null) {
			if (request.getRemoteAddr().equals(request.getLocalAddr())) {//在服务器本机上操作，则检查系统是否没有用户
				Map<String, Object> firstRow=jdbc.queryRow("select "+colMap.get("id")+" from "+tableName);
				if (firstRow!=null) { //系统用户表是空的
					if (username.equals(VIRTUAL_USERNAME)) {
						return generateUserState(colMap, firstRow, Utils.uuid(), Utils.uuid(), username, true, false);
					}
				}
			} 
			authLogger.trace("AuthClient - wrong username : {}, {}", request.getRemoteAddr(), username);
			Application.writeDenyNum(denyNumLockerForWrongPassword, wrongPasswordCount+1);
			return wrongPasswordCount>=VALIDATE_WRONG_PASSWORD_NUMBERS-1 && !useValidationCode ? "validate" : request.readLocaleText("Wrong_Username_Or_Password")+"!";
		
		} else {//找到了当前用户的数据
			
			String swapKey=(String)request.sessionRead(SWAP_KEY_SESSION_NAME);
			if (!password.equals(Utils.sha256(row.get("password")+swapKey))) {
				authLogger.trace("AuthClient - wrong password for user : {}, from: {}", username, request.getRemoteAddr());
				Application.writeDenyNum(denyNumLockerForWrongPassword, wrongPasswordCount+1);
				Application.writeDenyNum(denyNumLockerForUsername, wrongPasswordForUsername+1);
				return wrongPasswordCount>=VALIDATE_WRONG_PASSWORD_NUMBERS-1 && !useValidationCode ? "validate" : request.readLocaleText("Wrong_Username_Or_Password");
			} 
			if (colMap.containsKey("status")) {
				if (!String.valueOf(row.get("status")).equals("1")) {
					authLogger.trace("AuthClient - access locked user : {}, from: {}", username, request.getRemoteAddr());
					return request.readLocaleText("This_User_Has_Been_Locked");
				}
			}
			if (colMap.containsKey("allow_ip")) {
				if (((String)row.get("allow_ip")).length()>0 && !Utils.regi((String)row.get("allow_ip"), request.getRemoteAddr())) {
					authLogger.trace("AuthClient - access user : {}, from: {}, which not in allow range.", username, request.getRemoteAddr());
					return request.readLocaleText("This_IP_Is_Not_Allowed");
				}
			}
			if (colMap.containsKey("deny_ip")) {
				if (((String)row.get("deny_ip")).length()>0 && Utils.regi((String)row.get("deny_ip"), request.getRemoteAddr())) {
					authLogger.trace("AuthClient - access user : {}, from: {}, which in deny range.", username, request.getRemoteAddr());
					return request.readLocaleText("This_IP_Has_Been_Blocked");
				}
			}
			Application.writeDenyNum(denyNumLockerForWrongPassword, 0);//登录成功应该清除错误次数，否则永远都累计是不正确的。
			Application.writeDenyNum(denyNumLockerForWrongValidateCode, 0);
			return generateUserState(colMap, row, Utils.uuid(), Utils.uuid(), username, false, false);
		}
	}
	
	/**
	 * 退出系统，仅限Simple。
	 */
	private void logout() {
		request.checkReferer();
		Logger authLogger=LogFactory.getLogger(Constants.LOGGER_NAME_AUTH);
		if (state!=null) {
			StringBuffer colString=new StringBuffer();
			LinkedHashMap<String, String> colMap=new LinkedHashMap<String, String>();
			for (String item : tableColList.split(",")) {
				int index=item.indexOf(":");
				if (index!=-1) {
					String sysCol=item.substring(0, index).trim();
					String tableCol=item.substring(index+1).trim();
					colMap.put(sysCol, tableCol);
					colString.append(tableCol.length()>0 ? tableCol : "''").append(" as ").append(sysCol).append(", ");
				}
			}
			if (colMap.containsKey("state_col") && colMap.get("state_col").length()>0 && colMap.containsKey("state_token") && colMap.get("state_token").length()>0) {
				jdbc.execute("update "+tableName+" set "+colMap.get("state_col")+"='', "+colMap.get("state_token")+"='' where "+colMap.get("id")+"=?", state.userId);
			}
			authLogger.info("AuthClient Logout  {} State -  ip:{} data:userId={}; name={}; dpmName={}; loginIP={}; loginTime={}; loginCount={}; stateId={}", authType, request.getRemoteAddr(), state.userId, state.userName, state.dpmName, state.loginIP, state.loginTime, state.loginCount, state.stateId);
		} else {
			authLogger.info("AuthClient Logout  {} State -  ip:{} repeat without state data.", authType, request.getRemoteAddr());
		}
		state=null;
		logined=false;
		request.sessionDelete(stateSessionName);
		request.sessionReset();
		request.cookieDelete(stateValStringCookieName, cookiePath);
		request.cookieDelete(stateIdCookieName, cookiePath, cookieDomain);
		request.cookieDelete(stateIdValStringCookieName, cookiePath, cookieDomain);
	}
	
	private boolean recoveryUserState(String stateId) {
		if (stateId.length()<10) {
			return false;
		}
		StringBuffer colString=new StringBuffer();
		LinkedHashMap<String, String> colMap=new LinkedHashMap<String, String>();
		for (String item : tableColList.split(",")) {
			int index=item.indexOf(":");
			if (index!=-1) {
				String sysCol=item.substring(0, index).trim();
				String tableCol=item.substring(index+1).trim();
				colMap.put(sysCol, tableCol);
				colString.append(tableCol.length()>0 ? tableCol : "''").append(" as ").append(sysCol).append(", ");
			}
		}
		if (colString.length()<3) {
			return false;
		} else if (!colMap.containsKey("state_col") || colMap.get("state_col").length()<1 || !colMap.containsKey("state_token") || colMap.get("state_token").length()<1 || !colMap.containsKey("login_ip") || colMap.get("login_ip").length()<1) {
			authLogger.trace("AuthClient - user table hasn't column: state_col, state_token, login_ip . couldn't recoery user state for {}, {}", request.getRemoteAddr(), stateId);
			return false;
		}
		colString.delete(colString.length()-2, colString.length());
		
		Map<String, Object> row=jdbc.queryRow("select  "+colString.toString()+" from "+tableName+" where "+colMap.get("state_col")+"=? and "+colMap.get("login_ip")+"=?", stateId, request.getRemoteAddr());
		if (row==null) {
			authLogger.trace("AuthClient - wrong stateId to recovery : {}, {}", request.getRemoteAddr(), stateId);
			return false;
		} else {
			Date loginTime=(Date)row.get("login_time");
			if (cookieSaveTime==-1 && loginTime!=null && loginTime.getTime()<Utils.now().getTime()-expireMinute*60000) {
				authLogger.trace("AuthClient - timeout stateId to recovery : {}, {}", request.getRemoteAddr(), stateId);
				return false;
			}
			authLogger.warn("AuthClient State Recovery /////////////////////////////////////////////////////////////////////////////////");
			generateUserState(colMap, row, stateId, (String)row.get("state_token"), "", false, true);
			return true;
		}
	}
	
	private String generateUserState(LinkedHashMap<String, String> colMap, Map<String, Object> userDataMap, String stateId, String token, String username, boolean isVirtualUser, boolean isRecovery) {
		state=new AuthUserState(config, "", stateId
				, (String) userDataMap.get("id"), (String) userDataMap.get("name")
				, colMap.containsKey("dpm_id") ? (String) userDataMap.get("dpm_id") : "_", colMap.containsKey("dpm_name") ? (String) userDataMap.get("dpm_name") : ""
				, request.getRemoteAddr()
				, colMap.containsKey("login_count") ? (Integer) userDataMap.get("login_count") : -1
				, Utils.now(), colMap.containsKey("login_time") ? (Date) userDataMap.get("login_time") : null
				, colMap.containsKey("login_ip") ? (String) userDataMap.get("login_ip") : ""
				, jdbc);
		state.userDataMap.putAll(userDataMap);
		if (colMap.containsKey("permission")) {
			state.addAuthorization(config.authAppId, "", "", 1, "", 1, (String)userDataMap.get("permission"), "", "");
		}
		if (!isRecovery) {
			StringBuffer sql=new StringBuffer();
			JdbcParams params=JdbcParams.createNew();
			if (colMap.containsKey("state_col") && colMap.get("state_col").length()>0) {
				sql.append(colMap.get("state_col")+"=${state_id}, ");
				params.putString("state_id", stateId); 
			}
			if (colMap.containsKey("state_token") && colMap.get("state_token").length()>0) { 
				sql.append(colMap.get("state_token")+"=${state_token}, ");
				params.putString("state_token", token); 
			}
			if (colMap.containsKey("login_count") && colMap.get("login_count").length()>0) { 
				sql.append(colMap.get("login_count")+"=1+"+jdbc.sql_nvl()+"("+colMap.get("login_count")+", 0), ");
			}
			if (colMap.containsKey("login_time") && colMap.get("login_time").length()>0) { 
				sql.append(colMap.get("login_time")+"="+jdbc.sql_now()+", ");
			}
			if (colMap.containsKey("login_ip") && colMap.get("login_ip").length()>0) { 
				sql.append(colMap.get("login_ip")+"=${login_ip}, ");
				params.putString("login_ip", request.getRemoteAddr()); 
			}
			if (sql.length()>2) {
				sql.delete(sql.length()-2,  sql.length()); //去除最后一个“,
				params.putString("username", username); 
				jdbc.execute("update "+tableName+" set "+sql+ " where "+colMap.get("username")+"=${username}", params);
			}
		}
		request.sessionReset();
		request.sessionWrite(stateSessionName, state);
		request.cookieWrite(stateValStringCookieName, buildSessionValString(request, stateId, bindClientIP), cookiePath, cookieDomain);
		request.cookieWrite(stateIdCookieName, stateId, cookieSaveTime, cookiePath, cookieDomain, true);
		request.cookieWrite(stateIdValStringCookieName, buildCookieValString(request, stateId), cookieSaveTime, cookiePath, cookieDomain, true);
		request.generateTokenMark(true, token);
		logined=true;

		if (isVirtualUser) {
			authLogger.warn("AuthClient Virtual User Login /////////////////////////////////////////////////////////////////////////////////");
		}
		authLogger.info("AuthClient Login  {} State -  ip:{} data:userId={}; name={}; dpmName={}; loginIP={}; loginTime={}; loginCount={}; stateId={}", authType, request.getRemoteAddr(), state.userId, state.userName, state.dpmName, state.loginIP, state.loginTime, state.loginCount, state.stateId);
		
		return "success";
	}
	
	private String generateSwapKey() {
		String swapKey=Utils.md5(request.getSessionId());
		request.sessionWrite(SWAP_KEY_SESSION_NAME, swapKey);
		return swapKey;
	}

	
	/**
	 * 刷新用户会话数据，并适时清理不需要的数据
	 */
	private boolean refreshUserState(boolean isNewState) {
		long time=Utils.now().getTime();
		if (cookieSaveTime==-1 && !isNewState && state.lastAccessTime<time-expireMinute*60000) {//timeout
			return false;
		}
		state.lastAccessTime=time;
		return true;
	}

	
}
