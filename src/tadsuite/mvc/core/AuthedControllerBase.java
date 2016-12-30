package tadsuite.mvc.core;

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
		rootMap.put("auth", auth.getInfoMap());
		rootMap.put("auth_path", auth.getAuthPath());
	}

}
