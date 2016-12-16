package tadsuite.mvc.utils;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import tadsuite.mvc.utils.Utils;

public class Utils {
	
	public static final String ID_PATTERN="[0-9a-zA-Z\\-_]{1,36}";
	
	public static enum FORMAT {RAW, ID, LETTER, HTML, TEXT, CODE, INT, LONG, FLOAT, DOUBLE, NUMBER, DATE, DATETIME}
	
	public static boolean isId(String str) {
		return !str.equals("_") && Utils.regi(ID_PATTERN, str);
	}
	/**
	 * 生成16位数字，前13位为当前时间毫秒值，后3位为随机数
	 * @return
	 */
	public static long generateNo() {
		return (Utils.now().getTime()-383673600000L)*1000+Math.round(Math.random()*1000);
	}

	public static String checkNull(String val, String defaultValue) {
		if (val==null || val.length()<1) {
			return defaultValue;
		} else {
			return val;
		}
	}
	
	public static boolean isInt(String val) {
		try {
			if (val==null) {
				return false;
			}
			Integer.parseInt(val.replaceAll(",", ""));
			return true;
		} catch (NumberFormatException e) {
			return false;
		}
	}
	public static int parseInt(String val, int defaultValue) {
		try {
			if (val==null) {
				return defaultValue;
			}
			return Integer.parseInt(val.replaceAll(",", ""));
		} catch (NumberFormatException e) {
			return defaultValue;
		}
	}
	public static boolean isLong(String val) {
		try {
			if (val==null) {
				return false;
			}
			Long.parseLong(val.replaceAll(",", ""));
			return true;
		} catch (NumberFormatException e) {
			return false;
		}
	}
	public static long parseLong(String val, long defaultValue) {
		try {
			if (val==null) {
				return defaultValue;
			}
			return Long.parseLong(val.replaceAll(",", ""));
		} catch (NumberFormatException e) {
			return defaultValue;
		}
	}
	public static boolean isDouble(String val) {
		try {
			if (val==null) {
				return false;
			}
			Double.parseDouble(val.replaceAll(",", ""));
			return true;
		} catch (NumberFormatException e) {
			return false;
		}
	}
	public static double parseDouble(String val, double defaultValue) {
		try {
			if (val==null) {
				return defaultValue;
			}
			return Double.parseDouble(val.replaceAll(",", ""));
		} catch (NumberFormatException e) {
			return defaultValue;
		}
	}
	public static boolean isFloat(String val) {
		try {
			if (val==null) {
				return false;
			}
			Float.parseFloat(val.replaceAll(",", ""));
			return true;
		} catch (NumberFormatException e) {
			return false;
		}
	}
	public static float parseFloat(String val, float defaultValue) {
		try {
			if (val==null) {
				return defaultValue;
			}
			return Float.parseFloat(val.replaceAll(",", ""));
		} catch (NumberFormatException e) {
			return defaultValue;
		}
	}
	public static boolean isNumber(String val) {//-.487, .487也是数字
		return val!=null && val.length()>0 && regi("^[\\-]{0,1}[0-9]{0,}[\\.]{0,1}[0-9]{0,}$", val);
	}
	
	public static String escapeSQL(String value) {
		return value.trim().replaceAll("'", "''");
	}
	
	public static String array2String(String[] a, String separator) {
	    StringBuilder result = new StringBuilder();
	    if (a.length > 0) {
	        result.append(a[0]);
	        for (int i=1, j=a.length; i<j; i++) {
	            result.append(separator);
	            result.append(a[i]);
	        }
	    }
	    return result.toString();
	}
	
	/**
	 * 将字符串按指定分隔符分隔为数组，并去除可能存在的空格与空元素
	 * @param arrayString
	 * @param seperator
	 * @return
	 */
	public static String[] splitString(String arrayString, char seperator) {
		StringBuffer sb=new StringBuffer();
		boolean prevCharIsSeperator=true;
		for (int i=0; i<arrayString.length(); i++) {
			char c=arrayString.charAt(i);
			if (c==' ' || (c==seperator && prevCharIsSeperator)) {//如果前一个字符是“,”，则忽略“,”
				continue;
			}
			sb.append(c);
			prevCharIsSeperator=c==seperator;
		}
		int length=sb.length();
		if (length>0 && sb.charAt(length-1)==seperator) {
			sb.delete(length-1, length);
		}
		//System.out.println(">>>"+arrayString+">>>>"+sb.toString()+">>>");
		return sb.toString().split(String.valueOf(seperator));
	}
	
	
	public static String htmlEncode(String txt) {
		StringBuilder out = new StringBuilder();
	    for(int i=0, j=txt.length(); i<j; i++) {
	    	char c = txt.charAt(i);
	        if(c=='<') {
	           out.append("&lt;");
	        } else if (c=='>') {
	        	 out.append("&gt;");
	        } else if (c=='"') {
	        	 out.append("&quot;");
	        } else if (c=='\'') {
	        	 out.append(c);//这是错误的，out.append("&#x27");
	        } else if (c=='/') {
	        	 out.append(c);//这是错误的，out.append("&#x2F");
	        } else {
	            out.append(c);
	        }
	    }
	    return out.toString();
	}
	
	public static String htmlEncodeWithBr(String txt) {
		StringBuilder out = new StringBuilder();
	    for(int i=0, j=txt.length(); i<j; i++) {
	    	char c = txt.charAt(i);
	        if(c=='<') {
	           out.append("&lt;");
	        } else if (c=='>') {
	        	 out.append("&gt;");
	        } else if (c=='"') {
	        	 out.append("&quot;");
	        } else if (c=='\'') {
	        	 out.append(c);//这是错误的，out.append("&#x27");
	        } else if (c=='/') {
	        	 out.append(c);//这是错误的，out.append("&#x2F");
	        } else if (c=='\n') {
	        	 out.append("<br />").append(c);
	        } else {
	            out.append(c);
	        }
	    }
	    return out.toString();
	}
	
	public static String sanitizeHtml(String value) {
		value = value.replaceAll("(?i)eval\\((.*)\\)", "");
		value = value.replaceAll("(?i)[\\\"\\\'][\\s]*javascript:(.*)[\\\"\\\']", "\"\"");
		value = value.replaceAll("(?i)[\\\"\\\'][\\s]*vb:(.*)[\\\"\\\']", "\"\"");
		value = value.replaceAll("(?i)[\\s]on*=[\\\"\\\']", "");
		value = value.replaceAll("(?i)<script(.*)>", "");
		value = value.replaceAll("(?i)</script>", "");
		value = value.replaceAll("(?i)<iframe(.*)>", "");
		value = value.replaceAll("(?i)</iframe>", "");
		return value;
	}
	
	public static String urlEncode(String value) {
		try {
			return java.net.URLEncoder.encode(value, "UTF8");
		} catch (UnsupportedEncodingException e) {
			//
		}
		return "UnsupportedEncoding";
	}
	public static String urlDecode(String value) {
		try {
			return java.net.URLDecoder.decode(value, "UTF8");
		} catch (UnsupportedEncodingException e) { 
			//
		}
		return "UnsupportedEncoding";
	}
	
	
	
	public static boolean regi(String p, String str){
		return Pattern.compile(p).matcher(str).find();
	}
	public static String getValidationStr(String... args){
		final String sample="$().##(IIll11)OO|I|l|WWMMX000XY*YYY+YY-00000+";
		StringBuilder sb=new StringBuilder(args.length<sample.length() ? sample.substring(0, sample.length()-args.length) : sample).append(sample);
		for (int i=0; i<args.length; i++){
			sb.append(args[i]).append(i);
		}
		return sha256(sb.toString());
	}
	public static String md5key(String... args){
		final String sample="$().##(IIll11)OO|I|l|WWMMX000XY*YYY+YY-00000+";
		StringBuilder sb=new StringBuilder(args.length<sample.length() ? sample.substring(0, sample.length()-args.length) : sample).append(sample);
		for (int i=0; i<args.length; i++){
			sb.append(args[i]).append(i);
		}
		return md5(sb.toString());
	}
	public static String timeString(long base, String time, String format) {
		return timeString(base, dateParse(time, format));
	}
	public static String timeString(long base, Date time) {
		if (time==null) {
			return "";
		}
		long value=Math.abs(base-time.getTime())/1000; //切换为秒
		if (value<0) {
			return "";
		}
		if (value>2419200) {
			return ""; //28天以上不显示
		} else if (value>86400) { //1天以上
			return value/86400+"天";
		} else if (value>3600) { //1小时以上
			return value/3600+"小时";
		} else if (value>60) { //1分钟以上
			return value/60+"分钟";
		} else {
			return "1分钟";
		}
	}
	public static Date now() {
		GregorianCalendar gCalendar= new GregorianCalendar();
		return gCalendar.getTime();
	}
	public static GregorianCalendar calendar() {
		GregorianCalendar gCalendar= new GregorianCalendar();
		return gCalendar;
	}
	public static String now(String format) {
		GregorianCalendar gCalendar= new GregorianCalendar();
		return dateFormat(gCalendar.getTime(), format);
	}
	public static String dateFormat(Date date, String format){
		SimpleDateFormat df=new SimpleDateFormat(format);
		return df.format(date);
	}
	public static String dateFormat(String value, String oldFormat, String newFormat){
		if (value==null) {
			return "";
		}
		try {
			SimpleDateFormat df_old = new SimpleDateFormat(oldFormat);
			SimpleDateFormat df_new = new SimpleDateFormat(newFormat);
			java.util.Date date=df_old.parse(value);
			return df_new.format(date);
		} catch (Exception e) {
			return value;
		} 
	}
	public static boolean isDate(String value, String format) {
		if (value==null) {
			return false;
		}
		try {
			SimpleDateFormat df = new SimpleDateFormat(format);
			df.parse(value);
			return true;
		} catch (Exception e) {
			return false;
		}
	}
	
	public static Date dateParse(String value, String format) {
		if (value==null) {
			return null;
		}
		try {
			SimpleDateFormat df = new SimpleDateFormat(format);
			return df.parse(value);
		} catch (Exception e) {
			return null;
		}
	}	
	
	public static String numberFormat(Object value, String format){
		if (value==null) {
			return "";
		}
		try {
			DecimalFormat df = new DecimalFormat();
            df.applyPattern(format);
            return value instanceof String ? df.format(Utils.parseDouble((String)value, 0)) : df.format(value);
		} catch (Exception e) {
			return String.valueOf(value);
		} 
	}
	public static String map2JoinedString(HashMap<String, String> map, String joinString) {
		if (map.size()<1) {
			return "";
		}
		StringBuilder sb=new StringBuilder("");
		for (String item : map.keySet()) {
			sb.append(joinString).append(item);
		}
		if (sb.length()>0) {
			return sb.delete(0, joinString.length()).toString();
		} else {
			return "";
		}
	}

	public static String takeOutFileName(String filePath) {
        int pos = filePath.lastIndexOf(File.separator);
        if (pos > 0) {
            return filePath.substring(pos + 1);
        } else {
            return filePath;
        }
    }
	
	public static String size2String(long size) {
		if (size>1048576) {
			return (size/1048576)+"M";
		} else if (size>1024) {
			return size/1024+"K";
		} else if (size==0) {
			return "0K";
		} else {
			return "1K"; //最小1K，return (size)+"字节";
		}
	}
	
	public static String uuid() {
		return UUID.randomUUID().toString();
	}
	
	public static String uuid_short() {
		StringBuilder sb=new StringBuilder();
		for (String str : UUID.randomUUID().toString().split("\\-")) {
			sb.append(str);
		}
		return sb.toString();
	}
	

	public static String json(Object value) {
		StringBuilder buffer=new StringBuilder();
		readJSONItem(value, buffer);
		return buffer.toString();
	}

	public static String json(List list) {
		StringBuilder buffer=new StringBuilder();
		readJSONItem(list, buffer);
		return buffer.toString();
	}

	public static String json(Map map) {
		StringBuilder buffer=new StringBuilder();
		readJSONItem(map, buffer);
		return buffer.toString();
	}
	
	private static boolean readJSONItem(Object object, StringBuilder buffer) {
		String type=object!=null ? object.getClass().getSimpleName() : "null";
		if (type.endsWith("Map")) {
			Map map=(Map) object;
			int i=0;
			buffer.append("{");
			for (Object key : map.keySet()) {
				buffer.append(i>0 ? ", " : "").append("\"").append(escapeJsonString(key)).append("\" : ");
				readJSONItem(((Map) object).get(key), buffer);
				i++;
			}
			buffer.append("}");
			return true;
		} else if (type.endsWith("List")) {
			List list=(List) object;
			buffer.append("[");
			for (int i=0; i<((List)object).size(); i++) {
				buffer.append(i>0 ? ", " : "");
				readJSONItem(list.get(i), buffer);
			}
			buffer.append("]");
			return true;
		} else if (type.equals("Integer") || type.equals("Long") || type.equals("Float") || type.equals("Double") || type.equals("Decimal")) {
			buffer.append(type.equals("null") ? "\"\"" : object);
			return false;
		} else if (type.equals("Boolean")) {
			buffer.append((Boolean) object ? "true" : "false");
			return false;
		} else if (type.endsWith("Date") || type.endsWith("Timestamp")) {
			buffer.append("\"").append(Utils.dateFormat(String.valueOf(object), "yyyy-MM-dd HH:mm", "yyyy-MM-dd HH:mm")).append("\"");
			return false;
		} else {
			buffer.append("\"").append(type.equals("null") ? "" : escapeJsonString(object)).append("\"");
			return false;
		}
	}
	
	private static String escapeJsonString(Object object) {
		return String.valueOf(object).replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\n");
	}
	
	public static String xml(Object object) {
		StringBuilder xmlBuffer=new StringBuilder("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<xml>");
		readXMLItem(object, xmlBuffer, "");
		xmlBuffer.append("</xml>");
		return xmlBuffer.toString();
	}

	private static boolean readXMLItem(Object object, StringBuilder xmlBuffer, String prefix) {
		String type=object!=null ? object.getClass().getSimpleName() : "null";
		if (type.equals("String")) {
			xmlBuffer.append("<![CDATA[").append((String) object).append("]]>");
			return false;
		} else if (type.equals("Boolean")) {
			xmlBuffer.append((Boolean) object ? "true" : "false");
			return false;
		} else if (type.endsWith("Map")) {
			xmlBuffer.append("\n");
			Map map=(Map) object;
			for (Object key : map.keySet()) {
				boolean isValidKey=Utils.regi("^[a-zA-Z]{1}[a-zA-Z0-9_\\-]{0,}$", (String)key);
				xmlBuffer.append(prefix).append("<").append(isValidKey ? key : "key name=\""+key+"\"").append(">");
				boolean bWrapRow=readXMLItem(((Map) object).get(key), xmlBuffer, prefix+"	");
				xmlBuffer.append(bWrapRow ? prefix : "").append("</").append(isValidKey ? key : "key").append(">").append("\n");
			}
			return true;
		} else if (type.endsWith("List")) {
			xmlBuffer.append("\n");
			List list=(List) object;
			for (int i=0; i<((List)object).size(); i++) {
				xmlBuffer.append(prefix).append("<item index=\"").append(i).append("\">");
				boolean bWrapRow=readXMLItem(list.get(i), xmlBuffer, prefix+"	");
				xmlBuffer.append(bWrapRow ? prefix : "").append("</item>").append("\n");
			}
			return true;
		} else {
			xmlBuffer.append(type.equals("null") ? "" : object.toString());
			return false;
		}
	}

	public	 static String md5(String str) {
		return digest_standard(str, "MD5");
	}
	
	public	 static String md5x(String str) {
		return digest_extend(str, "MD5");
	}
	
	public	 static String sha1(String str) {
		return digest_standard(str, "SHA-1");
	}
	
	public	 static String sha1x(String str) {
		return digest_extend(str, "SHA-1");
	}
	
	public	 static String sha256(String str) {
		return digest_standard(str, "SHA-256");
	}
	
	public	 static String sha256x(String str) {
		return digest_extend(str, "SHA-256");
	}
	
	public	 static String sha512(String str) {
		return digest_standard(str, "SHA-512");
	}
	
	public	 static String sha512x(String str) {
		return digest_extend(str, "SHA-512");
	}
	
	public static String digest_standard(String origin, String algo) {
		String resultString = null;
		try {
			resultString = new String(origin);
			MessageDigest md = MessageDigest.getInstance(algo);
			resultString = byteArrayToHexString_standard(md.digest(resultString.getBytes()));
		} catch (Exception ex) {
			resultString = "";
		}
		return resultString;
	}
	
	public static String digest_extend(String origin, String algo) {
		String resultString = null;
		try {
			resultString = new String(origin);
			MessageDigest md = MessageDigest.getInstance(algo);
			resultString = byteArrayToHexString_extend(md.digest(resultString.getBytes()));
		} catch (Exception ex) {
			resultString = "";
		}
		return resultString;
	}
	
	private static String byteArrayToHexString_standard(byte[] b) {
		StringBuilder resultSb = new StringBuilder();
		for (int i = 0; i < b.length; i++) {
			resultSb.append(byteToHexString_standard(b[i]));
		}
		return resultSb.toString();
	}
	
	private static String byteArrayToHexString_extend(byte[] b) {
		StringBuilder resultSb = new StringBuilder();
		for (int i = 0; i < b.length; i++) {
			resultSb.append(byteToHexString_extend(b[i]));
		}
		return resultSb.toString();
	}
	
	private final static String[] hexDigits = { "0", "1", "2", "3", "4", "5",
		"6", "7", "8", "9", "a", "b", "c", "d", "e", "f" };
	
	private static String byteToHexString_standard(byte b) {
		int n = b;
		if (n < 0)
			n = 256 + n;
		int d1 = n / 16;
		int d2 = n % 16;
		return hexDigits[d1] + hexDigits[d2];	//standard;
	}
	
	private final static String[] hexDigits_extend = { "!", "@", "#", "$", "%", "^",
		"&", "*", "(", "|", "}", "[", "<", "+", "/", ";" };
	private static String byteToHexString_extend(byte b) {
		int n = b;
		if (n < 0)
			n = 256 + n;
		int d1 = n / 16;
		int d2 = n % 16;
		return hexDigits_extend[d2] + hexDigits_extend[d1];	//standard use: return hexDigits[d1] + hexDigits[d2];
	}
	
	public static String readClientIp(HttpServletRequest request) {
		String xForward=request.getHeader("X-Forwarded");
		if (xForward!=null) {
			String ip=xForward.substring(0, xForward.indexOf(",")).trim();
			if (ip.length()>1) {
				return ip;
			}
		}
		String xForwardFor=request.getHeader("X-Forwarded-For");
		if (xForwardFor!=null && xForwardFor.length()>0) {
			return xForwardFor;
		}
		return request.getRemoteAddr(); 
	}
	
	public static Object sessionRead(HttpServletRequest request, String key){
		HttpSession session=request.getSession(false);
		return session!=null ? session.getAttribute(key) : null;
	}
	
	public static void sessionReset(HttpServletRequest request){
		HttpSession session=request.getSession(false);
		if (session!=null) {
			session.invalidate();
		}
	}
	
	public static void sessionWrite(HttpServletRequest request, String key, Object value){
		if (value==null){
			sessionDelete(request, key);
		} else {
			request.getSession().setAttribute(key, value);
		}
	}
	
	public static void sessionDelete(HttpServletRequest request, String key){
		HttpSession session=request.getSession(false);
		if (session!=null) {
			session.removeAttribute(key);
		}
	}
}
