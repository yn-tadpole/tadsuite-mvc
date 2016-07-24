package tadsuite.mvc.security;

import java.util.concurrent.ConcurrentHashMap;

import tadsuite.mvc.Application;
import tadsuite.mvc.core.MvcRequest;
import tadsuite.mvc.logging.LogFactory;
import tadsuite.mvc.utils.Constants;
import tadsuite.mvc.utils.Utils;

public class WebSecurityFirewall {
	
	/**
	 * 检查WebSecurity
	 * @param request
	 * @param response
	 * @return
	 */
	public final static void check(MvcRequest request) {
		String url=request.getURL();
		
		if (Application.getSecurityExcludedMap().containsKey(url)) {
			return;
		}
		boolean foundRule=false;
		for (WebSecurityRule rule : Application.getSecurityCheckRuleList()) {
			if (url.startsWith(rule.prefix) && url.endsWith(rule.suffix)) {
				foundRule=true;
				checkSecurityOption(rule.checkOptions, request);
			}
		}
		// default rule
		if (!foundRule) {
			checkSecurityOption(Application.getSecurityDefaultOption(), request);
		}
	}
	
	public final static void checkSecurityOption(String[] optionsArray, MvcRequest request) {
		for (String option : optionsArray) {
			if (option.equals(Application.SECURITY_OPTION_CHECK_REFERER)) {
				checkReferer(request);
				
			} else if (option.equals(Application.SECURITY_OPTION_CHECK_POST_METHOD)) {
				checkPostMethod(request);

			} else if (option.equals(Application.SECURITY_OPTION_CHECK_CSRF_TOKEN)) {
				checkCsrfToken(request);
				
			} else if (option.equals(Application.SECURITY_OPTION_CHECK_SIGNATURE)) {
				checkSignature(request);
			}
		}
	}
	
	public final static void checkReferer(MvcRequest request) {
		String serverPath=request.getScheme()+"://"+request.getServerName()+(request.getServerPort()!=80 && request.getServerPort()!=443 ? ":"+request.getServerPort() : "");
		String contextPath=request.getContextPath();
		
		String referer=request.getHeader("Referer");
		if (referer==null || !referer.startsWith(serverPath+contextPath)) {
			throw new DenyExcpetion("CSRF - Request Referer is Invalid.");
		}
	}
	
	public final static void checkPostMethod(MvcRequest request) {
		if (!request.getMethod().equalsIgnoreCase("POST")) {
			throw new DenyExcpetion("CSRF - Request Method should be POST.");
		}
	}
	
	/**
	 * 检查CSRF标识，注意客户端进行计算的URL必须是相对路径或绝对路径，不能使用“?”开头的短路径，也不能是带http的全路径
	 * @param request
	 */
	public final static void checkCsrfToken(MvcRequest request) {
		String tokenMark=(String)request.sessionRead(Constants.CSRF_TOKEN_MARK_SESSION_NAME);
		String queryString=removeBuildinParameters(request.getQueryString());
		String urlRelation=request.getRequestURI().substring(request.getContextPath().length()+1)+(queryString.length()>0 ? "?"+queryString : "");
		
		if (tokenMark==null) {
			LogFactory.getLogger(Constants.LOGGER_NAME_SECURITY).warn("CSRF - Session Token Mark is Missing. From {} ->{}", request.getRemoteAddr(), urlRelation);
			//负载均衡会导致此现象，忽略它。throw new DenyExcpetion("Session Token is Missing.");
		} else {
			String requestToken=request.getParameter(Application.getCsrfTokenName());
			String requestSeed=request.getParameter(Application.getCsrfTokenSeedName());
			if (requestToken==null || requestToken.length()<1) {
				LogFactory.getLogger(Constants.LOGGER_NAME_SECURITY).warn("CSRF - Request Token is Missing. From {} ->{}", request.getRemoteAddr(), urlRelation);
				throw new DenyExcpetion("Request Token is Missing.");
			} else if (requestSeed==null || requestSeed.length()<1) {
				LogFactory.getLogger(Constants.LOGGER_NAME_SECURITY).warn("CSRF - Request Seed is Missing. From {} ->{}", request.getRemoteAddr(), urlRelation);
				throw new DenyExcpetion("Request Seed is Missing.");
			} else {
				String urlAbs=request.getContextPath()+"/"+urlRelation;
				String token1=Utils.getValidationStr(tokenMark, requestSeed, urlRelation);
				String token2=Utils.getValidationStr(tokenMark, requestSeed, urlAbs);
				String token3=Utils.getValidationStr(tokenMark, requestSeed, queryString.length()>0 ? "?"+queryString : urlRelation); //有时客户端的URL是?xxxx形式
				String token4=Utils.getValidationStr(tokenMark, requestSeed, urlRelation.substring(urlRelation.lastIndexOf("/")+1));
				boolean isAgentMached=request.getHeader("User-Agent").equals((String)request.sessionRead(Constants.CSRF_AGENT_SESSION_NAME));
				if ((!requestToken.equalsIgnoreCase(token1) && !requestToken.equalsIgnoreCase(token2) && !requestToken.equalsIgnoreCase(token3) && !requestToken.equalsIgnoreCase(token4)) || !isAgentMached) {
					LogFactory.getLogger(Constants.LOGGER_NAME_SECURITY).warn("CSRF - Token is invalidate. From {} ->{}", request.getRemoteAddr(), urlRelation);
					LogFactory.getLogger(Constants.LOGGER_NAME_SECURITY).warn("          sessionToken  {} requestSeed {} URL {}", tokenMark, requestSeed, urlRelation);
					LogFactory.getLogger(Constants.LOGGER_NAME_SECURITY).warn("          requestToken {} , SHOULD BE:{}", requestToken, token1+"/"+token2+"/"+token3+"/"+token4);
					LogFactory.getLogger(Constants.LOGGER_NAME_SECURITY).warn("          {} ", isAgentMached ? "but User-Agent matched." : "and User-Agent is not matched!!!");
					throw new DenyExcpetion("Request Token is Invalid");
				}
				
				//防止请求重放攻击
				@SuppressWarnings("unchecked")
				ConcurrentHashMap<String, String> usedToken=(ConcurrentHashMap<String, String>)request.sessionRead(Constants.CSRF_USED_TOKEN_SESSION_NAME);
				if (usedToken==null) {
					usedToken=new ConcurrentHashMap<String, String>();
					request.sessionWrite(Constants.CSRF_USED_TOKEN_SESSION_NAME, usedToken);
				}
				if (usedToken.containsKey(token1) || usedToken.containsKey(token2)) {
					throw new DenyExcpetion("Request Token is been Used!");
				} else {
					usedToken.put(token1, token1);
					usedToken.put(token2, token2);
				}
			}
			
			/*为防止因时钟不同步导致无法提交请求。这里不再使用时间判断
			String requestTime=readParameter(CSRF_TOKEN_TIME_IN_REQUEST);
			if (requestToken==null || requestToken.length()<1) {
				throw new EndExecutingExcpetion("Request Token is Missing.");
			} else if (requestTime==null || !Utils.isDate(requestTime, "yyyy-MM-dd HH:mm:ss")) {
				throw new EndExecutingExcpetion("Request Time is Missing.");
			} else {
				long time=Utils.dateParse(requestTime, "yyyy-MM-dd HH:mm:ss").getTime();
				long expireNormal=Utils.parseInt(Utils.config(this, "RequestTimeExpireMinute"), 1)*1000;
				long expireMultipart=Utils.parseInt(Utils.config(this, "RequestTimeExpireMinute_Multipart"), 120)*1000;
				long now=Utils.now().getTime();
				boolean isMultipart=request.getMethod().equalsIgnoreCase("POST") && request.getContentType().toLowerCase().startsWith("multipart/");
				if ((isMultipart && time+expireMultipart<now) || (!isMultipart && time+expireNormal<now)) {
					throw new EndExecutingExcpetion("Request Time Expired");
				} else if (!requestToken.equalsIgnoreCase(Utils.getValidationStr(sessionToken, requestTime, request.getRequestURI(), request.getHeader("User-Agent")))) {
					throw new EndExecutingExcpetion("Request Token is Invalid");
				}
			}
			*/			
		}
	}
	
	public static final void generateTokenMark(MvcRequest request, boolean override) {
		generateTokenMark(request, override, null);
	}
	
	public static final void generateTokenMark(MvcRequest request, boolean override, String defaultValue) {
		String tokenMark=(String)request.sessionRead(Constants.CSRF_TOKEN_MARK_SESSION_NAME);
		if (override || tokenMark==null) {
			tokenMark=defaultValue!=null ? defaultValue : Utils.uuid();
			request.sessionWrite(Constants.CSRF_TOKEN_MARK_SESSION_NAME, tokenMark);
			request.sessionWrite(Constants.CSRF_AGENT_SESSION_NAME, request.getHeader("User-Agent"));
		}
		request.getRootMap().put("csrfTokenName", Application.getCsrfTokenName());
		request.getRootMap().put("csrfTokenSeedName", Application.getCsrfTokenSeedName());
		request.getRootMap().put("csrfTokenMark", tokenMark);
	}
	
	private static String removeBuildinParameters(String queryString) {
		if (queryString==null || queryString.length()<1) {
			return "";
		}
		
		StringBuffer sb=new StringBuffer();
		boolean isFirstItem=true;
		for (String item : queryString.split("&")) {
			if (item.length()>0) {
				int x=item.indexOf("=");
				if (x!=-1) {
					String name=item.substring(0, x);
					String value=item.substring(x+1);
					if (!name.equals(Application.getCsrfTokenName()) && !name.equals(Application.getCsrfTokenSeedName()) && !name.equals(Application.getSignatureName())) {
						if (isFirstItem) {
							isFirstItem=false;
						} else {
							sb.append("&");
						}
						sb.append(name).append("=").append(value);
					}
				} else {
					sb.append(item);
				}
			}
		}
		return sb.toString();
	}
	
	public final static void checkSignature(MvcRequest request) {
		//TODO checkSignature
	}

	
}
