package tadsuite.mvc.auth;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import tadsuite.mvc.Application;
import tadsuite.mvc.core.MvcControllerBase;
import tadsuite.mvc.core.MvcRequest;
import tadsuite.mvc.jdbc.Jdbc;
import tadsuite.mvc.logging.LogFactory;
import tadsuite.mvc.logging.Logger;
import tadsuite.mvc.utils.Constants;
import tadsuite.mvc.utils.Utils;


/**
 * 本类实现单点登录
 * @author TADPOLE
 */

public final class AuthSsoClient extends AuthClient {
	
	/**存储用户会话的SESSION名称*/
	private String  stateSessionName;
	private String  stateValStringCookieName;
	/**存储用户交换信息的SESSION名称*/
	private String  stateIdCookieName;
	private String  stateIdValStringCookieName;
	/**存储用户交换信息的SESSION名称*/
	private static final String  SWAP_KEY_SESSION_NAME="SWAP_KEY";
	private String  swapKeyValStringCookieName;
	/**用户会话类，用户登录成功后将在SESSION中存储该类的一个实例*/
	
	private static ConcurrentHashMap<String, Long> STATE_MAP=new ConcurrentHashMap<String, Long>();
	private static Long lastCheckTime=0L;
	
	//Configuration
	private String authPath;
	private boolean useValidationCode;
	private String passwordEncrypt;
	private int expireMinute;
	private boolean canKeepState;
	private int cookieSaveTime=-1;
	private String cookieDomain;
	private String cookiePath;
	private boolean bindClientIP;
	private String dataSource;
	private String tablePrefix;
		
	private MvcRequest request;
	protected Jdbc jdbc;
	private long timer=0;
	private boolean ignoreValString=false;
	private Logger authLogger=LogFactory.getLogger(Constants.LOGGER_NAME_AUTH);
	
	/**
	 * 初始化用户会话
	 * @param appId
	 * @param controller
	 * @param ignoreValString 用于在特殊情况下忽略Cookie中的验证串以及User-Agent检查，以适应如FLASH上传之类的进程能正常工作。
	 */
	public AuthSsoClient(AuthClientConfig config, MvcControllerBase controller, boolean isIgnoreValString) {
		
		timer=System.currentTimeMillis();
		this.request=controller.request;
		this.config=config;
		this.ignoreValString=isIgnoreValString;
		
		authPath=config.get("authPath", "");
		if (authPath.length()<1) {
			authPath=request.getContextPath()+"/Login";
		} else if (!authPath.endsWith("Login")) {
			if (!authPath.endsWith("/")) {
				authPath=authPath+"/";
			}
			authPath=authPath+"Login";
		}
		
		loginTemplate=config.get("loginTemplate", MvcControllerBase.RESULT_LOGIN);
		useValidationCode=request.cookieRead("Auth_useValidationCode").equals("Y") || config.get("useValidationCode", "true").equals("true") || config.get("useValidationCode", "true").equals("Y") || config.get("useValidationCode", "true").equals("1");
		passwordEncrypt=config.get("passwordEncrypt", "");
		expireMinute=Utils.parseInt(config.get("expireMinute", "60"), 60);
		canKeepState=config.get("canKeepState", "false").equals("true") || config.get("canKeepState", "false").equals("Y") || config.get("canKeepState", "false").equals("1");
		cookieSaveTime=canKeepState && request.cookieRead("Auth_saveState").equals("1") ? 24*60*60  : -1;
		cookieDomain=config.get("cookieDomain", ""); //不能加默认服务器名称，防止负载均衡丢失会话 request.getServerName()
		cookiePath=config.get("cookiePath", ""); //单点登录不能加路径
		bindClientIP=config.get("bindClientIP", "true").equals("true") || config.get("bindClientIP", "true").equals("Y") || config.get("bindClientIP", "true").equals("1");
		dataSource=config.get("dataSource", "");
		tablePrefix=config.get("tablePrefix", "");
		
		stateSessionName="STATE_"+Utils.md5(authPath); //config.authAppId+
		stateValStringCookieName="S"+Utils.md5("SVSCN"+config.authAppId+authPath).toUpperCase();
		stateIdCookieName="S"+Utils.md5("SICN"+authPath).toUpperCase(); ////config.authAppId+不附加AppId是为了同域下的不同App之间单点登录不需要跳到登录页
		stateIdValStringCookieName="S"+Utils.md5("SIVCN"+authPath).toUpperCase(); //config.authAppId+
		swapKeyValStringCookieName="S"+Utils.md5("SKVCN"+config.authAppId+ authPath).toUpperCase();
		logined=false;
		
		if (config.authAppId.length()<1) {
			throw new RuntimeException("AuthClient - lost AuthAppId.");
		}
		if (dataSource.length()>0) {
			jdbc=controller.jdbc.create(dataSource);
			if (tablePrefix.length()>0) {
				jdbc.setTablePrefix(tablePrefix);
			}
		} else {
			if (tablePrefix.length()>0) {
				jdbc=controller.jdbc.clone();
				jdbc.setTablePrefix(tablePrefix);
			} else {
				jdbc=controller.jdbc;
			}
		}
		
		request.getResponse().setHeader("Pragma","No-cache");
		request.getResponse().setHeader("Cache-Control","no-cache, no-store");
		request.getResponse().setDateHeader("Expires", 0);
		
		try {
			state=request.sessionRead(stateSessionName)!=null ? (AuthUserState)request.sessionRead(stateSessionName) : null;
		} catch (ClassCastException e) {
			state=null;
			request.sessionDelete(stateSessionName);
			request.sessionReset();
		}
		if (state!=null) {
			String valString=request.cookieRead(stateValStringCookieName);
			String sessionValString=buildSessionValString(request, state.stateId, bindClientIP);
			if (ignoreValString || sessionValString.equals(valString)) {
				String userId=jdbc.queryObject(String.class, "select user_id from t_auth_state where id=?", state.stateId);
				jdbc.close();//即时关闭
				if (userId==null) { //如果没有读取到会话记录，则有可能通过其它客户端退出了系统，退出后进行了重新登录
					state=null;
					request.sessionDelete(stateSessionName);
					request.cookieDelete(stateValStringCookieName, cookiePath, cookieDomain);
					//return; 不要返回，再尝试恢复
				} else if (!state.userId.equals(userId)) {//用户已经切换了登录身份，只清除USER_STATE_DATA+appMark
					state=null;
					request.sessionDelete(stateSessionName);
					request.cookieDelete(stateValStringCookieName, cookiePath, cookieDomain);
					//return; 不要返回，再尝试恢复
				} else if (!refreshUserState(false)) {//可能已经超时
					state=null;
					request.sessionDelete(stateSessionName);
					request.cookieDelete(stateValStringCookieName, cookiePath, cookieDomain);
					//return; 不要返回，再尝试恢复
				} else {
					logined=true;
				}
			} else {
				state=null;
				request.sessionDelete(stateSessionName);
				request.sessionReset();
			}
		}
		//如果state无效
		if (!logined) {
			String stateId=request.cookieRead(stateIdCookieName);
			String stateIdValString=request.cookieRead(stateIdValStringCookieName);
			
			if (stateIdValString!=null && stateIdValString.equals(buildCookieValString(request, stateId))) {
				generateSsoUserState(stateId, !canKeepState);
			}
			if (!logined) {//checkSwapKey会检查swapKey创建state && crossDomain
				checkSsoSwapKey();
			}
		}
		if (!logined) {//生成失败，可能因为服务器端已经退出
			if (request.sessionRead(stateSessionName)!=null) {
				request.sessionDelete(stateSessionName);
			}
			request.sessionReset();
			request.cookieDelete(stateValStringCookieName, cookiePath, cookieDomain);
			request.cookieDelete(stateIdCookieName, cookiePath, cookieDomain);
			request.cookieDelete(stateIdValStringCookieName, cookiePath, cookieDomain);
		}
		long totalTime=System.currentTimeMillis()-timer;
		Logger performanceLogger=LogFactory.getLogger(Constants.LOGGER_NAME_PERFORMANCE);
		if (totalTime>3000) {
			performanceLogger.warn("AuthClient init finished - {}ms", totalTime);
		} else if (totalTime>1000) {
			performanceLogger.info("AuthClient init finished - {}ms", totalTime);
		} else if (performanceLogger.isDebugEnabled()) {
			performanceLogger.debug("AuthClient init finished - {}ms", totalTime);
		}
	}
	
	
	/**判断用户是否登录成功，失败则显示登录对话框
	 * @param loginTemplateUrl 登录对话框模板路径
	 * @param noteMessage 登录时的提示信息
	 */
	public void checkLogin() {//, boolean bCheckAppId
		
		Application.sysLock_check(request.getRemoteAddr(), true);
		
		if (logined) {//只判断登录，不判断是否有进入的权限。(!bCheckAppId && logined) || testWithAppId("", "", currentAppId, false)
			return;
		} else {
			request.generateTokenMark(true, null);
			request.getRootMap().put("auth_type", "sso");
			request.getRootMap().put("auth_path", authPath);
			request.getRootMap().put("auth_canKeepState", canKeepState);
			request.getRootMap().put("auth_useValidationCode", useValidationCode);
			request.getRootMap().put("auth_passwordEncrypt", passwordEncrypt);
			request.getRootMap().put("auth_swapKey", generateSsoSwapKey());
			MvcControllerBase.endExecuting(loginTemplate, "");
			return;
		}
	}


	/***
	 * 从认证客户端发起一个认证信息交换。
	 * 如果客户端未设置单点登录，此过程将被忽略
	 * 生成的key存在在modal中以便通过客户端浏览器传递给认证服务器，同时也存在在SESSION中，以防止反复生成。
	 */
	private String generateSsoSwapKey() {
		boolean bGenerated=false;
		String swapKey=(String)request.sessionRead(SWAP_KEY_SESSION_NAME);
		if (swapKey!=null) {
			boolean hasRow=jdbc.hasRow("select value from t_auth_swap where token=?", swapKey);
			if (hasRow) {//生成验证请求字（如果已经生成了验证字，需要验证它是否还存在）
				bGenerated=true;
			}
		}
		if (!bGenerated) {
			swapKey=Utils.md5(request.getSessionId());
			jdbc.execute("insert into t_auth_swap(token, ip, create_time, value) values(? ,? , ?, '')", swapKey, request.getRemoteAddr(), Utils.now());
			request.sessionReset();
			request.sessionWrite(SWAP_KEY_SESSION_NAME, swapKey);
			request.cookieWrite(swapKeyValStringCookieName, buildSessionValString(request, swapKey, bindClientIP), cookiePath, cookieDomain);
		}
		return swapKey;
	}
	
	/***检查交换信息，仅限SSO方式的认证。
	 * 检查客户端是否有userStateId或swapKeyValue；
	 * 如果有swapKeyValue，则检查认证服务器是否已提示交换成功（VALUE列中包含userStateId）， 当服务器已返回userStateId，则生成对话对象并存在到SESSION中（即单点登录成功）。
	 * 如果有userStateId，则直接生成用户会话
	 * @return 是否交换认证信息成功
	 */
	private void checkSsoSwapKey() {
		String swapKey=(String)request.sessionRead(SWAP_KEY_SESSION_NAME);
		String swapKeyValString=request.cookieRead(swapKeyValStringCookieName);
		if (swapKey!=null) {
			if (!swapKeyValString.equalsIgnoreCase(buildSessionValString(request, swapKey, bindClientIP))) {
				//验证失败的swapKey需要删除
				request.sessionDelete(SWAP_KEY_SESSION_NAME);
				request.cookieDelete(swapKeyValStringCookieName, cookiePath, cookieDomain);
				return;
			}
			//这种情况是已生成一个验证请求字，但登录会话不存在
			String stateId=jdbc.queryObject(String.class, "select value from t_auth_swap where token=?", swapKey);
			if (stateId!=null) {
				if (stateId.length()>0) {//如果认证服务器已经响应了交换请求则读取它，并将其从数据库中删除
					jdbc.execute("delete from t_auth_swap where token=?", swapKey);
					generateSsoUserState(stateId, false); //这里使用false是因为要实现Session超时，但服务器未重启时能够正常重建
					//已使用的swapKey需要删除
					request.sessionDelete(SWAP_KEY_SESSION_NAME);
					request.cookieDelete(swapKeyValStringCookieName, cookiePath, cookieDomain);
					return;
				}//已找到但未交换成功的先保留
			} else {
				//没找到的swapKey需要删除
				request.sessionDelete(SWAP_KEY_SESSION_NAME);
				request.cookieDelete(swapKeyValStringCookieName, cookiePath, cookieDomain);
				return;
			}
		}
	}
	
	public void generateSsoUserState(String stateId) {
		generateSsoUserState(stateId, true);
	}

	/**
	 * 在Session和Cookie中生成会话
	 * @param stateId
	 * @param checkSecurity 是否执行安全检查
	 * @return
	 */
	public void generateSsoUserState(String stateId, boolean checkSecurity) {
		if (checkSecurity && STATE_MAP!=null) {//此处对stateId进行检测，如果该stateId已经被某一客户端建立了会话，则不允许再建立。这样既保证多机集群可以使用Cookie创建会话，又保证不被会话劫持			
			if (STATE_MAP.containsKey(stateId)) {
				return;
			}
		}
		Map<String, Object> map=jdbc.queryRow("select id, user_id, name, dpm_id, dpm_name, val_str, login_num, login_time, login_ip, prev_login_time, prev_login_ip, active_time from t_auth_state where id=?", stateId);
		if (map==null) {
			return;
		}		
		String userId=(String)map.get("user_id");
		String userName=(String)map.get("name");
		String dpmId=(String)map.get("dpm_id");
		String dpmName=(String)map.get("dpm_name");
		Date loginTime=(Date)map.get("login_time");
		int loginCount=(Integer)map.get("login_num");
		String loginIP=(String)map.get("login_ip");
		Date prevLoginTime=(Date)map.get("prev_login_time");
		String prevLoginIP=(String)map.get("prev_login_ip");
		
		state=new AuthUserState(config, authPath, stateId, userId, userName, dpmId, dpmName, loginIP, loginCount, loginTime, prevLoginTime, prevLoginIP, jdbc);
		state.userDataMap.putAll(map);
		
		ArrayList<LinkedHashMap<String, Object>> userDpmList=new ArrayList<>();
		userDpmList=jdbc.queryList("select d.id, d.type_id, d.name from t_department d where id=? or id in (select upper_id from t_department_relation r where r.upper_id=d.id and r.child_id=(select dpm_id from t_user where id=?)) order by level_count desc"
				, dpmId, userId);
		
		ArrayList<LinkedHashMap<String, Object>> authList = jdbc.queryList("select " +//这里不能使用a.dpm_id，因为它的值可能是U
				" 		a.user_id, "+jdbc.sql_nvl()+"(d.id, '_') as dpm_id, a.app_id, dt.id as dpm_type_id, dt.title as dpm_type_title, a.is_show_in_list, a.role_id, a.permission_list, a.duty, a.val_str, a.sort " +
				"		, r.title as role_title, r.permission_list as role_permission_list, d.name as dpm_name " +
				" from t_user u " +
				"		join t_user_auth a on a.user_id=u.id or a.user_id=u.type_id " +//20130430增加a.user_id=u.type_id条件，可以读取对所属用户分类的授权
				"		left join t_department d on (d.id=a.dpm_id or (a.dpm_id='U' and d.id=u.dpm_id)) " +
				"		left join t_department_type dt on (dt.id=a.dpm_id ) " +
				"		left join t_application_role r on r.id=a.role_id " +
				" where u.id=? order by d.level_count, d.sort, a.id", userId);
		for (Map<String, Object> row : authList) {
			String authDpmId=null, authDpmName=null;
			String authDpmTypeId=(String)row.get("dpm_type_id");
			if (authDpmTypeId.length()>0 && userDpmList.size()>0) {
				for (int i=userDpmList.size()-1; i>=0; i--) {
					if (userDpmList.get(i).get("type_id").equals(authDpmTypeId)) {
						authDpmId=(String)userDpmList.get(i).get("id");
						authDpmName=(String)userDpmList.get(i).get("name");
						break;
					}
					if (authDpmName==null) {
						authDpmId="_";
						authDpmName="〈未找到〉";
					}
				}
			} else {
				authDpmId=(String)row.get("dpm_id");
				authDpmName=(String)row.get("dpm_name");
			}
			state.addAuthorization((String)row.get("app_id"), authDpmId, authDpmName, (Integer)row.get("is_show_in_list"), (String)row.get("duty"), (Integer)row.get("sort"), (String)row.get("permission_list"), (String)row.get("role_title"), (String)row.get("role_permission_list"));
		}
		long totalTime=System.currentTimeMillis()-timer;
		Logger authLogger=LogFactory.getLogger(Constants.LOGGER_NAME_AUTH);
		authLogger.info("AuthClient Create  SSO State - {}ms ip:{} data:userId={}; name={}; dpmName={}; loginIP={}; loginTime={}; loginCount={}; stateId={}", totalTime, request.getRemoteAddr(), userId, userName, dpmName, loginIP, loginTime, loginCount, stateId);
		
		request.sessionReset();
		request.sessionWrite(stateSessionName, state);
		request.cookieWrite(stateValStringCookieName, buildSessionValString(request, stateId, bindClientIP), cookiePath, cookieDomain);
		request.cookieWrite(stateIdCookieName, stateId, cookieSaveTime, cookiePath, cookieDomain, true);
		request.cookieWrite(stateIdValStringCookieName, buildCookieValString(request, stateId), cookieSaveTime, cookiePath, cookieDomain, true);
		request.cookieDelete(swapKeyValStringCookieName, cookiePath, cookieDomain);
		request.generateTokenMark(true, null);
		refreshUserState(true);//由于创建时没有刷新用户状态，所以应该刷新一下。
		logined=true;
		
		if (STATE_MAP!=null) {
			STATE_MAP.put(stateId, Utils.now().getTime());
		}
	}
	
	/**
	 * 刷新用户会话数据，并适时清理不需要的数据
	 */
	private boolean refreshUserState(boolean isNewState) {
		long time=Utils.now().getTime();
		if (!isNewState && state.lastAccessTime<time-expireMinute*60000) {//timeout
			return false;
		}
		state.lastAccessTime=time;
		
		if (isNewState || state.stateRefreshTime<time-5000) {//5秒内不刷新用户活跃时间
			jdbc.execute("update t_auth_state set active_time="+jdbc.sql_now()+" where id=?", state.stateId);
			state.stateRefreshTime=time;
		}
		
		if (lastCheckTime<time-60000L*5L) {//5分钟内不刷新会话数据（注意，每个应用都会执行此过程，频率不宜太高）
			synchronized (lastCheckTime) {
				if (lastCheckTime<time-60000L*5L) {
					lastCheckTime=time;
				}
			}
			if (STATE_MAP!=null) {
				long finalExpireTime=time-60000L*60L*24L; //登录一天后强制清除application中的记忆
				for(String key : STATE_MAP.keySet()) {
					if (STATE_MAP.get(key)<finalExpireTime) {
						STATE_MAP.remove(key);
					}
				}
			}
			
			Calendar cal=Calendar.getInstance();
			cal.add(Calendar.MINUTE, 0-expireMinute);
			
			jdbc.execute("delete from t_auth_swap where create_time<?", cal.getTime());
			
			//清除已经超时的会话
			jdbc.execute("update t_auth_log set is_online=0, exit_time="+jdbc.sql_now()+", last_active_time=(select active_time from t_auth_state s where s.id=t_auth_log.state_id) where is_online=1 and state_id in (select id from t_auth_state where active_time<?) ", cal.getTime());
			int k=jdbc.execute("delete from t_auth_state where active_time<?", cal.getTime());
			if (k>0) {
				authLogger.info("AuthClient - cleanup {} user states whitch inactive in {} minutes.", k, expireMinute);
			}
		}
		
		return true;
	}

}
