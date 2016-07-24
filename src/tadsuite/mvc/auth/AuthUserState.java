package tadsuite.mvc.auth;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;

import tadsuite.mvc.core.MvcControllerBase;
import tadsuite.mvc.jdbc.Jdbc;
import tadsuite.mvc.utils.Utils;

public class AuthUserState {
	
	/**用户基本信息*/
	public String  authAppId, stateId, userId, userName, dpmId, dpmName, loginIP, prevLoginIP;
	public Date loginTime, prevLoginTime;
	public int loginCount=0;
	public long stateRefreshTime=0; //会话数据刷新时间，在SSO模式下，会话状态需刷新至数据库，这里记录最后一次刷新的时间
	public long lastAccessTime=0; //访问时间，即最后一次使用该会话的时间，用于判断会话是否应该超时
	/**用户配置信息*/
	public LinkedHashMap<String, String> config=new LinkedHashMap<String, String>();
	/**该用户所拥有的所有权限，结构为：＜应用系统appId，＜权限全标识code.moduleId，＜授权单位ID，授权单位名称＞＞＞*/
	public LinkedHashMap<String, LinkedHashMap<String, LinkedHashMap<String, String>>> permissionContainer=new LinkedHashMap<String, LinkedHashMap<String, LinkedHashMap<String, String>>>();
	/**已对本用户授权的所单位Map（Map中存储单位详细信息），结构为：＜应用系统appId，＜授权单位ID，授权单位名称＞＞*/
	public LinkedHashMap<String, LinkedHashMap<String, String>> departmentMapContainer=new LinkedHashMap<String, LinkedHashMap<String, String>>();
	/**已对本用户授权的单位ID索引（ArrayList可按写入顺序读取），结构为：＜应用系统appId，＜授权单位ID＞*/
	public LinkedHashMap<String, ArrayList<String>> departmentListContainer=new LinkedHashMap<String, ArrayList<String>>();
	/**单位信息（以ID索引），结构为：＜应用系统appId，＜授权单位ID，＜授权单位信息MAP＜ID，INFO＞＞＞*/
	public LinkedHashMap<String, LinkedHashMap<String, LinkedHashMap<String, String>>> departmentDictionary=new LinkedHashMap<String, LinkedHashMap<String, LinkedHashMap<String, String>>>();
	
	public LinkedHashMap<String, Object> userDataMap=new LinkedHashMap<String, Object>();
	
	private Jdbc jdbcStandby;
	
	public AuthUserState(String authAppId, String stateId, String userId, String userName, String dpmId, String dpmName, String loginIp, int loginCount, Date loginTime, Date prevLoginTime, String prevLoginIp, Jdbc jdbcStandby) {
		lastAccessTime=Utils.now().getTime();
		stateRefreshTime=Utils.now().getTime();
		this.authAppId=authAppId;
		this.stateId=stateId;
		this.userId=userId;
		this.userName=userName;
		this.dpmId=dpmId;
		this.dpmName=dpmName;
		this.loginTime=loginTime;
		this.loginIP=loginIp;
		this.loginCount=loginCount;
		this.prevLoginTime=prevLoginTime;
		this.prevLoginIP=prevLoginIp;
		this.jdbcStandby=jdbcStandby;
	}
	
	/**
	 * 添加授权记录
	 * @param appId
	 * @param dpmId
	 * @param dpmName
	 * @param showInList
	 * @param duty
	 * @param sort
	 * @param permission
	 * @param roleName
	 * @param rolePermission
	 */
	public void addAuthorization(String appId, String dpmId, String dpmName, int showInList, String duty, int sort, String permission, String roleName, String rolePermission) {
		//生成dpmMap
		LinkedHashMap<String, String> dpmMap=new LinkedHashMap<String, String>();
		dpmMap.put("dpmId", dpmId);
		dpmMap.put("dpmName", dpmName);
		dpmMap.put("roleName", roleName);
		dpmMap.put("showInList", String.valueOf(showInList));
		dpmMap.put("duty", duty);
		dpmMap.put("sort", String.valueOf(sort));
		//将机构MAP写入departmentDictionary
		if (!departmentDictionary.containsKey(appId)) {
			departmentDictionary.put(appId, new LinkedHashMap<String, LinkedHashMap<String, String>>());
		}
		if (!departmentDictionary.get(appId).containsKey(dpmId)) {
			departmentDictionary.get(appId).put(dpmId, dpmMap);
		}
		
		//如果该authAppId还未写入
		if (!permissionContainer.containsKey(appId)) {
			permissionContainer.put(appId, new LinkedHashMap<String, LinkedHashMap<String, String>>());
			departmentMapContainer.put(appId, new LinkedHashMap<String, String>());
			departmentListContainer.put(appId, new ArrayList<String>());
		}
		
		//将机构数据dpmId写入到departmentMap
		departmentMapContainer.get(appId).put(dpmId, dpmName);
		//将机构ID索引写入到departmentList（使用departmentList是因为departmentMap会自动调整排序，而departmentList不会）
		departmentListContainer.get(appId).add(dpmId);
		//将permission写入到permissionMap。
		String[] array=(permission+rolePermission).indexOf("|")!=-1 ? (permission+rolePermission).split("\\|") : (permission+rolePermission).split("#");
		for (String key : array) {
			if (key.length()>0) {
				if (!permissionContainer.get(appId).containsKey(key)) {
					permissionContainer.get(appId).put(key, new LinkedHashMap<String, String>());
				}
				permissionContainer.get(appId).get(key).put(dpmId, dpmName);
			}
		}
	}
	
	/**
	 * 查当前登录用户是否具备当前系统的permission权限
	 * @param permission
	 * @return
	 */
	public boolean test(String permission) {
		return test("", permission, authAppId);
	}
	
	
	/*
	 * 查当前登录用户是否具备当前系统的permission权限
	 * @param permission
	 * @return
	 
	public boolean testPermission(String permission) {
		return testPermission("", permission, authAppId);
	}*/
	
	/**
	 * 查当前登录用户是否在单位dpmId(为空则检查任意单位)具备当前系统的permission权限
	 * @param dpmId
	 * @param permission
	 * @return
	 */
	public boolean test(String dpmId, String permission) {
		return test(dpmId, permission, authAppId);
	}
	
	/*
	 * 查当前登录用户是否在单位dpmId(为空则检查任意单位)具备当前系统的permission权限
	 * @param dpmId
	 * @param permission
	 * @return
	 
	public boolean testPermission(String dpmId, String permission) {
		return testPermission(dpmId, permission, authAppId);
	}*/
	
	/**检查当前登录用户是否在单位dpmId(为空则检查任意单位)具备appId子系统的permission权限
	 * 如果具备此权限则返回true，如果不具备此权限则返回false
	 * @param int dpmId  要检查的的单位，如果dpmId为-1，则检查任意单位。
	  * @param String  permission  要检查的权限代码，如果为null或空字符串则仅检查是否登录
	 * @param String  appId  要检查的子系统代码，如果permission为空时可以为空，否则不能为空
	 */
	public boolean test(String dpmId, String permission, String appId) {
		if (appId==null || appId.length()<1) {
			if (dpmId.length()>0 || permission.length()>0) {
				MvcControllerBase.endExecuting("Application Error#Call 'test()' should offer a valid 'appId'!");
				return false;
			} else {
				return true;//如果没有提供appId则只判断登录
			}
		}
		if (!permissionContainer.containsKey(appId)) {//如果未对子系统授权，则登录无效
			return false;
		}
		
		if (permission==null || permission.length()<1) {//如果不检查具体权限
			if (dpmId!=null && dpmId.length()>0) {//此时只检查是否已对指定机构授权
				return departmentMapContainer.get(appId).containsKey(dpmId);
			} else {//只检查用户是否对指定系统授权
				return true;
			}
		}
		
		String[] array1=permission.split("/");
		for (String substr : array1) {
			if (substr.indexOf("*")!=-1) {
				String[] array2=substr.split("\\*");
				int i=0, k=0; //i为当前检索的子项序号，k为通过的子项数量
				for (String item : array2) {
					if (item.length()>0) {
						i++;
						if (testPermissionItem(dpmId, item, appId)) {
							k++;
						} else {
							break; //退出for
						}
					}
				}
				array2=null;
				if (i==k) {
					return true;
				}
			} else if (substr.length()>0 && testPermissionItem(dpmId, substr, appId)) { 
				return true;
			}
		}
		array1=null;
		return false;//整个for循环中没有一个"/"满足要求,也可能没有使用"*"或使用了而没有使认证通过,则不通过权限检查
	}
	
	/*
	public boolean testPermission(String dpmId, String permission, String appId) {
		return test(dpmId, permission, appId);
	}*/
	
	
	/**
	 * 检查授权，对dpmArray任一意单位有授权即通过
	 * @param dpmArray
	 * @param permission
	 * @return
	 */
	public boolean test(String[] dpmArray, String permission) {
		LinkedHashMap holdDpm=readAuthedDpmMap(permission);
		if (holdDpm.size()<1) {
			return false;
		}
		for (String dpmId : dpmArray) {
			if (dpmId.length()>0 && holdDpm.containsKey(dpmId)) { //如果当前用户对该单位的上级单位中任意一个具备管理权限
				return true;
			}
		}
		return false;
	}
	
	/*
	public boolean testPermission(String[] dpmArray, String permission) {
		return test(dpmArray, permission);
	}*/
	
	public boolean testInAnyApp(String dpmId, String permission) {
		for (String appId : permissionContainer.keySet()) {
			if (test(dpmId, permission, appId)) {
				return true;
			}
		}
		return false;
	}
	
	/**
	 * 检查用户对指定的dpmId是否拥有指定权限，并且可以按bCheckParentsDept决定是否检查上级单位（上级单位的检查仅对SSO时可用）
	 * @param dpmId
	 * @param permission
	 * @param bCheckParentsDept
	 * @return
	 */
	public boolean test(String dpmId, String permission, boolean bCheckParentsDept) {
		return test(dpmId, permission, authAppId, bCheckParentsDept);
	}
	
	/**
	 * 检查用户对指定的dpmId是否拥有指定权限，并且可以按bCheckParentsDept决定是否检查上级单位（上级单位的检查仅对SSO时可用）
	 * @param dpmId
	 * @param permission
	 * @param bCheckParentsDept
	 * @return
	 */
	public boolean test(String dpmId, String permission, String appId, boolean bCheckParentsDept) {
		//如果需要检查上级单位
		if (bCheckParentsDept && dpmId.length()>1) {
			 LinkedHashMap<String, String> holdMap=readAuthedDpmMap(permission, appId);
			 if (holdMap.size()<1) {
				 return false;
			 } else if (holdMap.containsKey(dpmId)) {
				 return true;
			 }
			 //查看上级单位
			Jdbc jdbc=jdbcStandby.clone();
			List<String> upperIdList = jdbc.queryColumn(String.class, "select upper_id from t_department_relation where child_id=?", dpmId);
			jdbc.close();
			for (String upperId : upperIdList) {
				if (holdMap.containsKey(upperId)) {
					return true;
				}
			}
			return false;
		} else {
			return test(dpmId, permission, appId);
		}
	}
	
	public boolean testInAnyApp(String dpmId, String permission, boolean bCheckParents) {
		for (String appId : permissionContainer.keySet()) {
			if (test(dpmId, permission, appId, bCheckParents)) {
				return true;
			}
		}
		return false;
	}
	
	
	/**以LinkedHashMap<String, String>形式返回已对当前登录用户授权的所有单位
	 * @param appId
	 */
	public  LinkedHashMap<String, String> readAuthedDpmMap() {
		if (departmentMapContainer.containsKey(authAppId)) {
			return departmentMapContainer.get(authAppId);
		} else {
			return new LinkedHashMap<String, String>();
		}
	}
	/**以ArrayList<String>形式返回已对当前登录用户授权的所有单位
	 * @param appId
	 */
	public ArrayList<String> readAuthedDpmList() {
		if (departmentMapContainer.containsKey(authAppId)) {
			return departmentListContainer.get(authAppId);
		} else {
			return new ArrayList<String>();
		}
	}
	
	public ArrayList<LinkedHashMap<String, String>> readAuthedDpmList(String permission) {
		return readAuthedDpmList(permission, authAppId);
	}
	
	/**检查当前登录用户具备appId子系统的permission权限的单位列表（用ArrayList<LinkedHashMap<String, String>>包装）
	 * 如果具备此权限则返回true，如果不具备此权限则返回false
	 * @param String  permission  要检查的权限代码，如果为null或空字符串则仅检查是否登录
	 * @param String  appId  要检查的子系统代码，如果permission为空时可以为空，否则不能为空
	 */
	public ArrayList<LinkedHashMap<String, String>> readAuthedDpmList(String permission, String appId) {
		LinkedHashMap<String, String> map=readAuthedDpmMap(permission, appId);
		ArrayList<LinkedHashMap<String, String>> list = new ArrayList<LinkedHashMap<String,String>>();
		for (String key : map.keySet()) {
			list.add(departmentDictionary.get(appId).get(key));
		}
		return list;
	}
	
	public LinkedHashMap<String, String> readAuthedDpmMap(String permission) {
		return readAuthedDpmMap(permission, authAppId);
	}
	/**检查当前登录用户具备appId子系统的permission权限的单位列表（用LinkedHashMap包装）
	 * 如果具备此权限则返回true，如果不具备此权限则返回false
	 * @param String  permission  要检查的权限代码，如果为null或空字符串则仅检查是否登录
	 * @param String  appId  要检查的子系统代码，如果permission为空时可以为空，否则不能为空
	 */
	public LinkedHashMap<String, String> readAuthedDpmMap(String permission, String appId) {
		if (appId==null || appId.length()<1) {
			MvcControllerBase.endExecuting("Application Error#Call 'test()' should offer a valid 'appId'!");
			return null;
		}
		if (!permissionContainer.containsKey(appId)) {//如果未对子系统授权，则登录无效
			return new LinkedHashMap<String, String>();
		}
		if (permission==null || permission.length()<1) {
			return new LinkedHashMap<String, String>(departmentMapContainer.get(appId));
		}
		
		LinkedHashMap<String, String> cacheDpm=new LinkedHashMap<String, String>();
		
		String[] array1=permission.split("/");
		for (String substr : array1) {
			if (substr.indexOf("*")!=-1) {
				LinkedHashMap<String, String> andDpm=new LinkedHashMap<String, String>(); 
				String[] array2=substr.split("\\*");
				int i=0, k=0; //i为当前检索的子项序号，k为通过的子项数量
				for (String item : array2) {
					if (item.length()>0) {//忽略为空的权限
						i++;
						LinkedHashMap<String, String> map=testPermissionItem(item, appId);
						if (map!=null) {
							if (i==1) {//将符合第一个子项的单位计入缓存
								andDpm.putAll(map);
							} else {//后继的子项中的单位与缓存单位进行交集计算
								for (String dpm : andDpm.keySet()) {
									if (!map.containsKey(dpm)) {//如果andDpm中的单位不存在于holdDpm(最新检查结果)中，则移除它。
										andDpm.remove(dpm);
									}
								}
							}
							if (andDpm.size()<1) {//如果已经没有交集，或者第一个子项就没有符合的单位，则停止计算
								break; //for
							}
							k++;
						} else {
							break;  //退出for
						}
					}
				}
				array2=null;
				if (k==i) {//当前检查的子顶数等于已通过检查的子项数，则表示所有都通过
					cacheDpm.putAll(andDpm);
				}
				
			} else if (substr.length()>0) { //符合不为空的“/”子项者，加入到cacheDpm
				LinkedHashMap<String, String> map=testPermissionItem(substr, appId);
				if (map!=null) {
					cacheDpm.putAll(map);
				}
				
			}
		}
		array1=null;			
		return cacheDpm;
	}
	
	private boolean testPermissionItem(String dpmId, String key, String appId) {
		if (!permissionContainer.containsKey(appId)) {
			return false;
		}
		if (key.startsWith("u")) {//格式为u.xxxx
			return userId.equals(key.substring(2));
		} else {
			LinkedHashMap<String, String> dpmList=permissionContainer.get(appId).get(key);
			if (dpmList!=null) {
				return dpmId==null || dpmId.length()<1 || dpmList.containsKey(dpmId);
			} else {
				return false;
			}
		}
	}
	
	private LinkedHashMap<String, String> testPermissionItem(String key, String appId) {		
		if (!permissionContainer.containsKey(appId)) {
			return null;
		}
		if (key.startsWith("u")) {//格式为u.xxxx
			if (userId.equals(key.substring(2))) {
				return departmentMapContainer.get(appId);
			} else {
				return null;
			}
		} else {
			if (permissionContainer.get(appId).containsKey(key)) {
				return permissionContainer.get(appId).get(key);
			} else {
				return null;
			}
		}
	}
	
	/**
	 * @return  当前登录者的登录时间
	 */
	public Date getLoginTime() {
		return loginTime ;
	}
	/**
	 * @return  当前登录者的上次登录时间
	 */
	public Date getPrevLoginTime() {
		return prevLoginTime;
	}
	public String getUserId() {
		return userId;
	}
	/**
	 * @return  当前登录者的姓名
	 */
	public String getName() {
		return userName;
	}
	public String getDpmId() {
		return dpmId;
	}
	public String getDpmName() {
		return dpmName;
	}
	public String getLoginIP() {
		return loginIP;
	}
	public String getPrevLoginIP() {
		return prevLoginIP;
	}
	/**
	 * @return  当前登录者的登录总次数
	 */
	public int getLoginCount() {
		return loginCount;
	}
	
	public String getCurrentAppId() {
		return authAppId;
	}
	public String getStateId() {
		return stateId;
	}
	public String getConfig(String key) {
		return config.containsKey(key) ? config.get(key) : null;
	}
	public Object getData(String key) {
		return userDataMap.containsKey(key) ? userDataMap.get(key) : null;
	}
}
