package tadsuite.mvc.auth;

import tadsuite.mvc.Application;
import tadsuite.mvc.core.MvcControllerBase;
import tadsuite.mvc.core.MvcRequest;
import tadsuite.mvc.utils.Constants;
import tadsuite.mvc.utils.Utils;

public abstract class AuthClient {
	
	private static final String valStringSampleDefault="isj@ilkj$c%ds^&*()_";
	protected static String valStringSample="";
	
	protected AuthUserState state;
	protected boolean logined=false;
	protected AuthClientConfig config;
	
	protected String loginTemplate;
	
	public static AuthClient getInstance(String authAppId, MvcControllerBase controller) {
		String attrName=Constants.AUTH_CLIENT_ATTR_NAME_PREFIX+authAppId;
		
		if (controller.request.getAttribute(attrName)!=null) {
			return (AuthClient) controller.request.getAttribute(attrName);
		}
		
		AuthClientConfig config=Application.getAuthClientMap().get(authAppId);
		AuthClient auth;
		
		if (config.authType.equals("Simple")) {
			auth=new AuthSimpleClient(config, controller, false);
			
		} else if (config.authType.equals("SSO")) {
			auth=new AuthSsoClient(config, controller, false);
			
		} else if (config.authType.equals("OAuth_Alipay")) {
			//TODO OAuth_Alipay
			auth=new AuthSimpleClient(config, controller, false);
			
		} else {
			throw new RuntimeException("AuthClient - Unsupported Auth Type '"+authAppId+":"+config.authType+"'.");
		}
		
		controller.request.setAttribute(attrName, auth);
		return auth;
	}

	public abstract void checkLogin();
	
	public AuthUserState getUserState() {
		return logined ? state : null;
	}

	public boolean isLogined() {
		return logined;
	}

	protected static String buildSessionValString(MvcRequest request, String stateId, boolean bindClientIP) {//该串通常存客户端，仅对应特定sessionId
		String sample=valStringSample!=null && valStringSample.length()>0 ? valStringSample : valStringSampleDefault;
		String value= bindClientIP //有时不绑定IP是为了解决多路网络环境下，IP地址发生变化导致会话丢失
				? Utils.getValidationStr(stateId, sample, request.getRemoteAddr(), request.getSessionId(), request.getHeader("User-Agent"))
				: Utils.getValidationStr(stateId, sample, request.getSessionId(), request.getHeader("User-Agent"));
		return str2uuid(value);
	}
	
	public static String buildCookieValString(MvcRequest request, String stateId) {//对于Cookie中的stateId进行验证应加IP参数，其它情况不加IP参数//注意：AuthClient中的版本应与AuthServer中的版本一致
		String sample=valStringSample!=null && valStringSample.length()>0 ? valStringSample : valStringSampleDefault;		
		return str2uuid(Utils.getValidationStr(stateId, sample, request.getRemoteAddr(), request.getHeader("User-Agent"))); //该项会导致负载均衡时会话丢失, request.getServerName()
	}
	
	//让字符串更像一个UUID
	private static String str2uuid(String str) {
		StringBuffer value=new StringBuffer(Utils.md5(str)); 
		value.insert(7, '-').insert(12, '-').insert(17, '-').insert(22, '-');
		return value.toString();
	}
	
	public static String buildResult(boolean bScript, String code, String message) {
		String script= "try {LOGIN_API.serverCallback(\""+code+"\", \""+message.replaceAll("\n", "\\n").replaceAll("\"", "\\\"")+"\");} catch (e) {alert('LOGIN_API调用错误');}";
		return bScript ? script : "<html>\n<head>\n<meta http-equiv=\"Content-Type\" content=\"text/html; charset=utf-8\" />\n<script type=\"text/javascript\">\n"+script+"</script>\n</head>\n<body>OK!</body>\n</html>";
	}
}
