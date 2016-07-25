package tadsuite.mvc.core;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import tadsuite.mvc.Application;
import tadsuite.mvc.logging.LogFactory;
import tadsuite.mvc.security.WebSecurityFirewall;
import tadsuite.mvc.utils.Constants;
import tadsuite.mvc.utils.Utils;

public class MvcHttpRequest implements MvcRequest {
	
	private HttpServletRequest httpRequest;
	private HttpServletResponse httpResponse;
	private MvcResponse response=null;
	private String currentLocale=null;
	private LinkedHashMap<String, Object> rootMap=null;
	private LinkedHashMap<String, String> finalMap=null;
	private ClassMappingResult classMappingResult=null;
	
	public MvcHttpRequest(ServletRequest request, ServletResponse response) {
		this((HttpServletRequest)request, (HttpServletResponse) response);
	}

	public MvcHttpRequest(HttpServletRequest request, HttpServletResponse response) {
		this.httpRequest=request;
		this.httpResponse=response;
	}
	
	public MvcResponse getResponse() {
		if (response==null) {
			response=new MvcHttpResponse(httpResponse);
		}
		return response;
	}
	
	public HttpServletRequest getHttpRequest() {		
		return httpRequest;
	}
	
	public HttpServletResponse getHttpResponse() {
		return httpResponse;
	}
	
	public LinkedHashMap<String, Object> getRootMap() {
		if (rootMap==null) {
			rootMap=new LinkedHashMap<String, Object>();
		}
		return rootMap;
	}

	public LinkedHashMap<String, String> getFinalMap() {
		if (finalMap==null) {
			finalMap=new LinkedHashMap<String, String>();
		}
		return finalMap;
	}

	public ClassMappingResult getClassMappingResult() {
		return classMappingResult;
	}
	
	public void setClassMappingResult(ClassMappingResult classMappingResult) {
		this.classMappingResult=classMappingResult;
	}

	public String getScheme() {
		return httpRequest.getScheme();
	}

	public String getServerName() {
		return httpRequest.getServerName();
	}

	public int getServerPort() {
		return httpRequest.getServerPort();
	}

	public String getServerPath() {
		return httpRequest.getScheme()+"://"+httpRequest.getServerName()+(httpRequest.getServerPort()!=80 && httpRequest.getServerPort()!=443 ? ":"+httpRequest.getServerPort() : "");
	}

	public String getContextPath() {
		return httpRequest.getContextPath();
	}
	
	/**使用getRealPath应慎重，因为WAR包部署时将返回null，相对路径也可能返回null*/
	public String getRealPath(String url) {
		if (!url.startsWith("/")) {//注意，这样写是因为URL不是绝对路径的话有些版本的Tomcat会返回空值。
			url="/"+url;
		}
		return httpRequest.getServletContext().getRealPath(url);
	}

	public String getRequestURI() {
		return httpRequest.getRequestURI();
	}

	public String getQueryString() {
		return httpRequest.getQueryString();
	}
	
	
	public String getURL() {
		return httpRequest.getRequestURI().substring(httpRequest.getContextPath().length());
	}

	public String getFullURL() {
		return getURL()+(httpRequest.getQueryString()!=null && httpRequest.getQueryString().length()>0 ? "?"+httpRequest.getQueryString() : "");
	}
	
	public String getMethod() {
		return httpRequest.getMethod().toLowerCase();
	}
	
	public Object getAttribute(String name) {
		return httpRequest.getAttribute(name);
	}
	
	public void setAttribute(String name, Object obj) {
		httpRequest.setAttribute(name, obj);
	}
	
	public String getRemoteAddr() {
		String xForward=httpRequest.getHeader("X-Forwarded");
		if (xForward!=null) {
			String ip=xForward.substring(0, xForward.indexOf(",")).trim();
			if (ip.length()>1) {
				return ip;
			}
		}
		String xForwardFor=httpRequest.getHeader("X-Forwarded-For");
		if (xForwardFor!=null && xForwardFor.length()>0) {
			return xForwardFor;
		}
		return httpRequest.getRemoteAddr(); 
	}
	
	public String getLocalAddr() {
		return httpRequest.getLocalAddr();
	}
	

	public String getHeader(String parameter) {
		String value = httpRequest.getHeader(parameter);
		if (value == null) {
			int x=parameter.lastIndexOf(":");
			if (x==-1) {
				return "";
			} else {
				value=httpRequest.getHeader(parameter.substring(0, x));
				if (value==null) {
					return "";
				}
			}
		}
		return checkXss(parameter, value);
	}

	public String getParameter(String parameter) {
		String value = httpRequest.getParameter(parameter);
		if (value == null) {
			int x=parameter.lastIndexOf(":");
			if (x==-1) {
				return "";
			} else {
				value=httpRequest.getParameter(parameter.substring(0, x));
				if (value==null) {
					return "";
				}
			}
		}
		return checkXss(parameter, value);
	}

	public String[] getParameterValues(String parameter) {
		String[] values = httpRequest.getParameterValues(parameter);
		if (values==null) {
			int x=parameter.lastIndexOf(":");
			if (x==-1) {
				return new String[]{};
			} else {
				values=httpRequest.getParameterValues(parameter.substring(0, x));
				if (values==null) {
					return new String[]{};
				}
			}
		}
		
		int count = values.length;
		String[] encodedValues = new String[count];
		for (int i = 0; i < count; i++) {
			encodedValues[i] = checkXss(parameter, values[i]);
		}
		return encodedValues;
	}
	
	public Map<String, String> readParameterMap() {
		LinkedHashMap<String, String> params = new LinkedHashMap<String, String>();
		Set<String> paramsKey = httpRequest.getParameterMap().keySet();
        for(String key : paramsKey){
            params.put(key, getParameter(key));
        }
        return params;
	}

	private String checkXss(String name, String value) {
		if (name.endsWith(":original") || name.endsWith(":raw")) {//参数名以“.original”或“.raw”结尾
			return value;
		}
		if (value.indexOf("multipart/related")!=-1 && value.indexOf("boundary")!=-1) {
			if (!name.endsWith(":html")) {
				LogFactory.getLogger(Constants.LOGGER_NAME_SECURITY).error("CSRF - MHTML Cross Site Attack. ");
				//throw new RuntimeException("MHTML Cross Site Attack ? --"+value);
				return ""; //(multipart/related) is not been allowed.
			}
		}
		if (name.indexOf(":")!=-1) {
			if (name.endsWith(":html")) {//参数名以“.html”结尾
				return Utils.sanitizeHtml(value);
				
			} else if (name.endsWith(":code")) {//参数名以“.code”结尾
				return Utils.htmlEncodeWithBr(value);
				
			} else if (name.endsWith(":text")) {//参数名以“.text”结尾
				return Utils.htmlEncode(value);
				
			} else if (name.endsWith(":int")) {//参数名以“.int”结尾
				return Utils.isInt(value) ? value : "";
				
			} else if (name.endsWith(":long")) {//参数名以“.long”结尾
				return Utils.isLong(value) ? value : "";
				
			} else if (name.endsWith(":number")) {//参数名以“.number”结尾
				return Utils.isNumber(value) ? value : "";
				
			} else if (name.endsWith(":float")) {//参数名以“.float”结尾
				return Utils.isFloat(value) ? value : "";
				
			} else if (name.endsWith(":double")) {//参数名以“.double”结尾
				return Utils.isDouble(value) ? value : "";

			} else if (name.endsWith(":date")) {//参数名以“.date”结尾
				return Utils.isDate(value, "yyyy-MM-dd") ? value : "";

			} else if (name.endsWith(":datetime")) {//参数名以“.datetime”结尾
				return Utils.isDate(value, "yyyy-MM-dd HH:mm") ? value : "";
			}
		}
		//默认只取第一行（要取多行必须以“.html/.code/.text”结尾的参数进行读取）
		int x=value.indexOf("\n");
		if (x!=-1) {
			value=value.substring(0, x);
		}
		if (value.length()>Application.getParameterValueMaxLength()) {
			value=value.substring(0, Application.getParameterValueMaxLength());
		}
		return Utils.htmlEncode(Utils.sanitizeHtml(value));
	}
	
	public String readInput(String index) {
		String str=getParameter(index);
		if (str!=null) {
			return str.trim();
		} else {
			return "";
		}
	}
	
	public String readInput(String index, int maxLength) {
		String value=readInput(index);
		return value.length()>maxLength ? value.substring(0, maxLength) : value;
	}
	
	public String readInput(String index, int maxLength, String defaultValue) {
		String value=readInput(index);
		return value.length()>maxLength ? value.substring(0, maxLength) : value.length()<1 ? defaultValue  : value;
	}
		
	public String[] readInputArray(String index) {
		String[] values=getParameterValues(index);
		if (values!=null) {
			for (int i=0, j=values.length; i<j; i++) {
				values[i]=values[i];
			}
			return values;
		} else {
			return new String[]{};
		}
	}
	
	public Map<String, String> readInputMap(String indexString) {
		LinkedHashMap<String, String> map=new LinkedHashMap<String, String>();
		for (String index : indexString.split(",")) {
			index=index.trim();
			if (index.startsWith("[list]")) {
				map.put(index, Utils.array2String(readInputArray(index), ","));
			} else if (index.length()>0) {
				map.put(index, readInput(index));
			}
		}
		return map;
	}
	
	public String readInputEscapeSQL(String index) {
		return Utils.escapeSQL(readInput(index));
	}
	
	public String[] readInputArrayEscapeSQL(String index) {
		String[] values=getParameterValues(index);
		if (values!=null) {
			for (int i=0, j=values.length; i<j; i++) {
				values[i]=Utils.escapeSQL(values[i]);
			}
			return values;
		} else {
			return new String[]{};
		}
	}



	public String getSessionId() {
		HttpSession session=httpRequest.getSession(false);
		if (session!=null) {
			return session.getId();
		} else {
			return "";
		}
	}
	
	public Object sessionRead(String key) {
		HttpSession session=httpRequest.getSession(false);
		return session!=null ? session.getAttribute(key) : null;
	}
	
	public void sessionReset(){
		HttpSession session=httpRequest.getSession(false);
		if (session!=null) {
			session.invalidate();
		}
	}
	
	public void sessionWrite(String key, Object value){
		if (value==null){
			sessionDelete(key);
		} else {
			httpRequest.getSession().setAttribute(key, value);
			httpRequest.getSession().setMaxInactiveInterval(60*60);
		}
	}
	
	public void sessionDelete(String key){
		HttpSession session=httpRequest.getSession(false);
		if (session!=null) {
			session.removeAttribute(key);
		}
	}
	
	
	public String cookieRead(String key){
		Cookie[] cookies=httpRequest.getCookies();
		if (cookies!=null) {
			for (int i=0; i<cookies.length; i++){
				if (cookies[i].getName().equals(key)){
					return Utils.urlDecode(cookies[i].getValue());
				}
			}
		}
		return "";
	}
	
	/**
	 *在当前目录写内存Cookie（由于忘传path容易导致不能正确修改上级路径建立的Cookie，或导致Cookie对上级路径不可见，不建议使用）
	 * @param request
	 * @param key
	 * @param value
	 */
	public void cookieWrite(String key, String value){
		cookieWrite(key, value, -1, null, null, true); //-1 代表内存Cookie，不存盘
	}

	/**
	 * 在指定目录写内存Cookie
	 * @param request
	 * @param key
	 * @param value
	 * @param path
	 */
	public void cookieWrite(String key, String value, String path){
		cookieWrite(key, value, -1, path, null, true); //-1 代表内存Cookie，不存盘
	}
	
	/**
	 * 在指定目录写内存Cookie
	 * @param request
	 * @param key
	 * @param value
	 * @param path
	 * @param domain
	 */
	public void cookieWrite(String key, String value, String path, String domain){
		cookieWrite(key, value, -1, path, domain, true); //-1 代表内存Cookie，不存盘
	}
	
	
	/**
	 * 在当前目录写指定超时时间的Cookie（由于忘传path容易导致不能正确修改上级路径建立的Cookie，或导致Cookie对上级路径不可见，不建议使用）
	 * @param request
	 * @param key
	 * @param value
	 * @param expirySecond
	 */
	public void cookieWrite(String key, String value, int expirySecond){
		cookieWrite(key, value, expirySecond, null, null, true);
	}
	
	/**
	 * 在指定目录写指定超时时间的Cookie
	 * @param request
	 * @param key
	 * @param value
	 * @param expirySecond
	 * @param path
	 */
	public void cookieWrite(String key, String value, int expirySecond, String path){
		cookieWrite(key, value, expirySecond, path, null, true);
	}
	
	/**
	 * 写Cookie
	 * @param request
	 * @param key
	 * @param value
	 * @param expirySecond
	 * @param path
	 * @param domain
	 */
	public void cookieWrite(String key, String value, int expireSecond, String path, String domain, boolean httpOnly){
		Cookie[] cookies=httpRequest.getCookies();
		Cookie cookie=null;
		if (cookies!=null) {
			for (int i=0; i<cookies.length; i++){
				if (cookies[i].getName().equals(key)){
					cookie=cookies[i];
				}
			}
		}
		if (cookie==null) {//找不到则创建
			cookie=new Cookie(key, Utils.urlEncode(value!=null ? Utils.urlEncode(value) : null));
		} else {
			cookie.setValue(Utils.urlEncode(value!=null ? Utils.urlEncode(value) : null));
		}
		cookie.setMaxAge(expireSecond);
		if (domain!=null && domain.length()>0) {
			cookie.setDomain(domain);
		}
		if (path!=null && path.length()>0) {
			cookie.setPath(path);
		} else {
			cookie.setPath("/"); //未指定path时写入“/”，避免不同调用路径执行形成不统一结果
		}
		try {cookie.setHttpOnly(httpOnly);} catch (NoSuchMethodError e) {} //Sevlet 3.0才支持
		getResponse().addCookie(cookie); //注意：这里不能使用response，因为它可能是空的
	}

	/**
	 * 删除Cookie
	 * @param request
	 * @param key
	 * @param path
	 */
	public void cookieDelete(String key){
		cookieWrite(key, "", 0, null, null, true); 
	}

	/**
	 * 删除指定路径下的Cookie
	 * @param request
	 * @param key
	 * @param path
	 */
	public void cookieDelete(String key, String path){
		cookieWrite(key, "", 0, path, null, true); 
	}
	
	/**
	 * 删除指定域、指定路径下的Cookie
	 * @param request
	 * @param key
	 * @param path
	 * @param domain
	 */
	public void cookieDelete(String key, String path, String domain){
		cookieWrite(key, "", 0, path, domain, true); 
	}
	
	public void checkReferer() {
		WebSecurityFirewall.checkReferer(this);
	}
	
	public void checkPostMethod() {
		WebSecurityFirewall.checkPostMethod(this);
	}
	public void checkCsrfToken() {
		WebSecurityFirewall.checkCsrfToken(this);
	}
	
	public void generateTokenMark(boolean override) {
		WebSecurityFirewall.generateTokenMark(this, override);
	}
	
	public void generateTokenMark(boolean override, String defaultValue) {
		WebSecurityFirewall.generateTokenMark(this, override, defaultValue);
	}
	
	public String getCurrentLocale() {
		if (currentLocale==null) {
			currentLocale=readInput("locale").length()>0 ? readInput("locale") : cookieRead("locale").length()>0 ? cookieRead("locale") : sessionRead("locale")!=null ? (String) sessionRead("locale") : Application.getDefaultLocale();
			if (!Application.getLocaleMap().containsKey(currentLocale)) {
				currentLocale=Application.getDefaultLocale();
			}
		}
		return currentLocale;
	}
	
	public String readLocaleText(String title) {
		return Application.readLocaleText(getCurrentLocale(), title);
	}
}
