package tadsuite.mvc.core;

import java.util.LinkedHashMap;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import tadsuite.mvc.utils.Utils.FORMAT;

public interface MvcRequest {
	
	public LinkedHashMap<String, Object> getRootMap();
	public LinkedHashMap<String, String> getFinalMap();
	public ClassMappingResult getClassMappingResult();
	public void setClassMappingResult(ClassMappingResult classMappingResult);
	public String getTemplatePath();
	public void setTemplatePath(String templatePath);

	public MvcResponse getResponse();
	public HttpServletRequest getHttpRequest();
	public HttpServletResponse getHttpResponse();
	
	public String getScheme();
	public String getServerName();
	public int getServerPort();
	public String getServerPath();
	public String getContextPath();
	public String getRealPath(String url);
	public String getRequestURI();
	public String getQueryString();
	public String getSessionId();
	public String getURL();
	public String getFullURL();
	public String getMethod();
	public String getRemoteAddr();	
	public String getLocalAddr();
	
	public Object getAttribute(String name);
	public void setAttribute(String name, Object obj);
	
	public String getParameter(String parameter);
	public String[] getParameterValues(String parameter);
	public Map<String, String> readParameterMap();
	
	public String getHeader(String parameter);
	public String readInput(String index);
	public String readInput(String index, int maxLength);
	public String readInput(String index, int maxLength, String defaultValue);
	public String readInput(String index, FORMAT format);
	public String readId(String index);
	public String readLetter(String index);
	public int readInt(String index, int defaultValue);
	public long readLong(String index, long defaultValue);
	public float readFloat(String index, float defaultValue);
	public double readDouble(String index, double defaultValue);
	public String[] readInputArray(String index);
	public Map<String, String> readInputMap(String indexString);
	public String readInputEscapeSQL(String index);
	public String[] readInputArrayEscapeSQL(String index);
	
	public Object sessionRead(String key);	
	public void sessionReset();
	public void sessionWrite(String key, Object value);	
	public void sessionDelete(String key);
	
	public String cookieRead(String key);
	public void cookieWrite(String key, String value);
	public void cookieWrite(String key, String value, String path);
	public void cookieWrite(String key, String value, String path, String domain);
	public void cookieWrite(String key, String value, int expirySecond);
	public void cookieWrite(String key, String value, int expirySecond, String path);
	public void cookieWrite(String key, String value, int expireSecond, String path, String domain, boolean httpOnly);
	public void cookieDelete(String key);
	public void cookieDelete(String key, String path);
	public void cookieDelete(String key, String path, String domain);
	
	public void checkReferer();
	public void checkPostMethod();
	public void checkCsrfToken();
	public void generateTokenMark(boolean override);
	public void generateTokenMark(boolean override, String defaultValue);
	
	public void setCharacterEncoding(String encoding);
	
	public String getCurrentLocale();
	public String readLocaleText(String title);
}
