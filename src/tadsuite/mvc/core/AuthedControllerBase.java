package tadsuite.mvc.core;

import tadsuite.mvc.core.MvcControllerBase;

public class AuthedControllerBase extends MvcControllerBase {

	
	@Override
	protected void init() {
		super.init();
		//auth的初始化放在了MvcControllerBase的init方法中
		//不放在本类的init方法中，是为也不让登录因错误的类继承关系而失效(例如：本应继承AuthedControllerBase类，却错误地继承MvcControllerBase)。
		if (auth==null) {
			setMvcView(RESULT_TEXT, "AuthClient configuration is lost. Check tadsuite.xml >Authencation>ProtechedURL>Rule to protect this url");
			return;
		}
		String authPath=auth.getAuthPath();
		int index=auth.getAuthPath().lastIndexOf("/");
		rootMap.put("auth", auth.getInfoMap());
		rootMap.put("auth_path", authPath);
		rootMap.put("auth_path_base", index!=-1 ? authPath.substring(0, index)  : authPath);
	}

}
