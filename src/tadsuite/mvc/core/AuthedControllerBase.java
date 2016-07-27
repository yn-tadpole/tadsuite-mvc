package tadsuite.mvc.core;

import java.util.LinkedHashMap;

import tadsuite.mvc.Application;
import tadsuite.mvc.auth.AuthClient;
import tadsuite.mvc.auth.AuthUserState;
import tadsuite.mvc.core.MvcControllerBase;

public class AuthedControllerBase extends MvcControllerBase {

	protected AuthUserState auth;
	
	@Override
	protected void init() {
		super.init();
		
		AuthClient authClient=AuthClient.getInstance(Application.getDefaultAuthClientAppId(), this);
		
		authClient.checkLogin();
		auth=authClient.getUserState();
		
		LinkedHashMap<String, Object> map=new LinkedHashMap<>();
		map.put("name", auth.getName());
		map.put("stateId", auth.getStateId());
		map.put("userId", auth.getUserId());
		map.put("dpmId", auth.getDpmId());
		map.put("dpmName", auth.getDpmName());
		map.put("loginTime", auth.getLoginTime());
		map.put("loginIp", auth.getLoginIP());
		map.put("loginCount", auth.getLoginCount());
		map.put("prevLoginIp", auth.getPrevLoginIP());
		map.put("prevLoginTime", auth.getPrevLoginTime());
		rootMap.put("auth", map);
		rootMap.put("auth_path", auth.getAuthPath());
	}

}
