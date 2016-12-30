package tadsuite.mvc.auth;

import tadsuite.mvc.Application;
import tadsuite.mvc.core.MvcControllerBase;

public class Authentication {

	public static void init(MvcControllerBase controller) {
		String url=controller.request.getURL();
		if (Application.getAuthExcludedMap().containsKey(url)) {
			return;
		}
		for (String prefix : Application.getAuthProtechedUrlMap().keySet()) {
			if (url.startsWith(prefix)) {
				String authAppId=Application.getAuthProtechedUrlMap().get(prefix);
				AuthClient authClient=AuthClient.getInstance(authAppId, controller);
				authClient.checkLogin();
				if (controller.auth==null) {
					controller.auth=authClient.getUserState();
				}
				controller.authMap.put(authAppId, authClient.getUserState());
			}
		}
	}
}
