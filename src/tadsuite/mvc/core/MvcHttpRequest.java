package tadsuite.mvc.core;

import java.io.BufferedReader;
import java.io.IOException;
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
import tadsuite.mvc.utils.Utils.FORMAT;

public class MvcHttpRequest implements MvcRequest {
	
	private HttpServletRequest httpRequest;
	private HttpServletResponse httpResponse;
	private MvcResponse response=null;
	private String currentLocale=null;
	private LinkedHashMap<String, Object> rootMap=null;
	private LinkedHashMap<String, String> finalMap=null;
	private ClassMappingResult classMappingResult=null;
	private String templatePath;
	
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
	
	public String getTemplatePath() {
		return templatePath;
	}
	
	public void setTemplatePath(String templatePath) {
		this.templatePath=templatePath;
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
		//只有在使用了useProxy时才获取上一层代理地址，而且只获取最后一层代理的地址，防止IP地址伪造
		if (Application.getConfig("useProxy").equals("true") || Application.getConfig("useProxy").equals("Y") || Application.getConfig("useProxy").equals("1")) {
			String xForwardFor=httpRequest.getHeader("X-Forwarded-For");
			if (xForwardFor!=null && xForwardFor.length()>0) {
				return xForwardFor.substring(xForwardFor.lastIndexOf(",")+1).trim();
			}
		}
		return httpRequest.getRemoteAddr();
	}
	
	public String getLocalAddr() {
		return httpRequest.getLocalAddr();
	}
	

	public String getHeader(String parameter) {
		int x=parameter.lastIndexOf(":");
		String value=httpRequest.getHeader(x==-1 ? parameter : parameter.substring(0, x));
		return value == null ? "" : checkXss(parameter, value);
	}

	public String getParameter(String parameter) {
		int x=parameter.lastIndexOf(":");
		String value=httpRequest.getParameter(x==-1 ? parameter : parameter.substring(0, x));
		return value == null ? "" : checkXss(parameter, value);
	}

	public String[] getParameterValues(String parameter) {
		int x=parameter.lastIndexOf(":");
		String[] values=httpRequest.getParameterValues(x==-1 ? parameter : parameter.substring(0, x));
		if (values==null) {
			return new String[]{};
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
	
	@Override
	public String readRequestBody() {
		BufferedReader reader=null;
		StringBuffer body=new StringBuffer();
		try {
			reader=httpRequest.getReader();
			String input = null;
	        while((input = reader.readLine()) != null) {
	        	body.append(input).append("\n");
	        }
		} catch (IOException e) {
		} finally {
			if (reader!=null) {
				try {
					reader.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
		return body.toString();
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
			if (name.endsWith(":html")) {
				return Utils.sanitizeHtml(value);
				
			} else if (name.endsWith(":code")) {
				return Utils.htmlEncodeWithBr(value);

			} else if (name.endsWith(":text")) {
				return Utils.htmlEncode(value);

			} else if (name.endsWith(":id")) {
				return Utils.isId(value) ? value : "";
				
			} else if (name.endsWith(":int")) {
				return Utils.isInt(value) ? value : "";
				
			} else if (name.endsWith(":long")) {
				return Utils.isLong(value) ? value : "";
				
			} else if (name.endsWith(":number")) {
				return Utils.isNumber(value) ? value : "";

			} else if (name.endsWith(":letter")) {
				return Utils.regi("^[a-zA-Z0-9\\-_]$", value) ? value : "";
				
			} else if (name.endsWith(":float")) {
				return Utils.isFloat(value) ? value : "";
				
			} else if (name.endsWith(":double")) {
				return Utils.isDouble(value) ? value : "";

			} else if (name.endsWith(":date")) {
				return Utils.isDate(value, "yyyy-MM-dd") ? value : "";

			} else if (name.endsWith(":datetime")) {
				return Utils.isDate(value, "yyyy-MM-dd HH:mm") ? value : "";
			}
		}
		//默认只取第一行（要取多行必须以“.html/.code/.text”结尾的参数进行读取）
		//int x=value.indexOf("\n");
		//if (x!=-1) {
		//	value=value.substring(0, x);
		//}
		if (value.length()>Application.getParameterValueMaxLength()) {
			value=value.substring(0, Application.getParameterValueMaxLength());
		}
		return Utils.htmlEncode(Utils.sanitizeHtml(value));
	}
	
	public String readInput(String index) {
		return getParameter(index).trim();
	}
	
	public String readInput(String index, FORMAT format) {
		if (index.indexOf(":")!=-1) {
			throw new RuntimeException("Can't use both ':' type and 'FORMAT' pamameter!");
		}
		switch (format) {
		case ID:
			index=index+":id";
			break;
		case LETTER:
			index=index+":letter";
			break;
		case HTML:
			index=index+":html";
			break;
		case TEXT:
			index=index+":text";
			break;
		case CODE: 
			index=index+":code";
			break;
		case INT:
			index=index+":int";
			break;
		case LONG:
			index=index+":long";
			break;
		case FLOAT:
			index=index+":float";
			break;
		case DOUBLE:
			index=index+":double";
			break;
		case NUMBER:
			index=index+":number";
			break;
		case DATE:
			index=index+":date";
			break;
		case DATETIME:
			index=index+":datetime";
			break;
		case RAW:
			index=index+":raw";
			break;
		default:
			break;
		}
		return getParameter(index).trim();
	}
	
	public String readInput(String index, int maxLength) {
		String value=readInput(index);
		return value.length()>maxLength ? value.substring(0, maxLength) : value;
	}
	
	public String readId(String index) {
		String value=readInput(index).trim();
		return Utils.isId(value) ? value : "";
	}

	public String readLetter(String index) {
		String value=readInput(index).trim();
		return Utils.regi("^[a-zA-Z0-9\\-_]{1,100}$", value) ? value : "";
	}

	public String readText(String index) {
		String value=readInput(index);
		StringBuffer sb=new StringBuffer();
		boolean pass=false;
		for (int i=0; i<value.length(); i++) {
			char c=value.charAt(i);
			if (c=='<') {
				pass=true;
			} else if (c=='>') {
				pass=false;
			} else if (!pass)  {
				sb.append(c);
			}
		}
		return sb.toString();
	}
	
	public String readInput(String index, int maxLength, String defaultValue) {
		String value=readInput(index);
		return value.length()>maxLength ? value.substring(0, maxLength) : value.length()<1 ? defaultValue  : value;
	}
	
	public int readInt(String index, int defaultValue) {
		String value=readInput(index).trim();
		return Utils.parseInt(value!=null ? value.replaceAll(",", "") : "", defaultValue);
	}

	public long readLong(String index, long defaultValue) {
		String value=readInput(index).trim();
		return Utils.parseLong(value!=null ? value.replaceAll(",", "") : "", defaultValue);
	}

	public float readFloat(String index, float defaultValue) {
		String value=readInput(index).trim();
		return Utils.parseFloat(value!=null ? value.replaceAll(",", "") : "", defaultValue);
	}

	public double readDouble(String index, double defaultValue) {
		String value=readInput(index).trim();
		return Utils.parseDouble(value!=null ? value.replaceAll(",", "") : "", defaultValue);
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
	
	public void setCharacterEncoding(String encoding) {
		try {
			httpRequest.setCharacterEncoding(encoding);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}


}
