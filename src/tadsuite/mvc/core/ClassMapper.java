package tadsuite.mvc.core;

import java.util.concurrent.ConcurrentHashMap;

import tadsuite.mvc.Application;
import tadsuite.mvc.utils.Utils;

public class ClassMapper {

	private static final long CACHE_TIME=5000;
	private static ConcurrentHashMap<String, ClassMappingResult> CACHE_MAP=new ConcurrentHashMap<String, ClassMappingResult>();
	
	public static ClassMappingResult parse(String url) {
		ClassMappingResult cache=CACHE_MAP.get(url);
		if (cache!=null) {
			if (cache.parseTime>Utils.now().getTime()-CACHE_TIME) {
				return cache;
			}
		}
		String prefix=url.substring(0, url.lastIndexOf("/")+1); //该字符串总是以“/”开头，并且以“/”结尾
		while (prefix.length()>0) {
			if (Application.getControllerMap().containsKey(prefix)) {// fit with app_path
				return mapping(Application.getControllerMap().get(prefix), url);
			} else if (prefix.lastIndexOf("/")<=0) { // only has one '/'
				return null;
			}
			prefix=prefix.substring(0, prefix.substring(0, prefix.length()-1).lastIndexOf("/")+1);
		}
		return null;
	}
	
	
	private static ClassMappingResult mapping(ClassMappingRule rule, String url) {
		StringBuilder clazz=new StringBuilder(rule.classPackage).append(".");
		String methodName=Application.getDefaultMethodName();
		String path=url.substring(rule.urlPrefix.length());//该字符串总是不以“/”开头

		int lastSeperator=path.lastIndexOf("/");
		int lastDot=path.lastIndexOf(".");
		if (lastDot>lastSeperator) {//最后一个“/”后面还有一个“.”（即扩展名），去除它
			path=path.substring(0, lastDot);
		}

		boolean foundClassName=false;
		boolean convertNextChar=lastSeperator==-1;
		int lastUnderline=path.lastIndexOf("_");
		for (int i=0; i<path.length(); i++) {
			char c=path.charAt(i);
			if (c=='/') {
				clazz.append(".");
				convertNextChar= i==lastSeperator;
				
			} else if (c=='_') {
				if (i>lastSeperator) {//最后一段（类名部分）
					if (i==lastUnderline) {//最后一个下划线，分隔类名与方法名
						if (i<path.length()-1) {
							methodName=path.substring(i+1);
						}
						break;
					} else {
						convertNextChar=true;
					}
				} else {//包名部分，保留“_”
					clazz.append("_");
				}
				
			} else {
				if (convertNextChar) {
					clazz.append(String.valueOf(c).toUpperCase());
					foundClassName=true;
					convertNextChar=false;
				} else {
					clazz.append(c);
				}
			}
		}
		if (!foundClassName) {
			clazz.append(Application.getDefaultClassName()); // 设置默认类
		}
		
		String className=clazz.toString();
		String basePath=path.substring(0, path.lastIndexOf("/")+1);
		String rewriteURL=path.substring(path.lastIndexOf("/")+1);
		return mappingClass(rule, url, basePath, rewriteURL, className, methodName);
	}
	
	/**
	 * 递归计算映射的类
	 * @param path 匹配的URL（格式如：path/filename，不以“/”开头）
	 * @param className
	 * @param methodName
	 */
	private static ClassMappingResult mappingClass(ClassMappingRule rule, String url, String basePath, String rewriteURL, String className, String methodName) {
		if (className.length()>rule.classPackage.length() && Utils.regi("^[a-z_]{1}([a-z_0-9]{0,}[\\.]){1,}[A-Z]{1}[a-z0-9A-Z_]{0,}$", className)) {
			boolean isRuterClass=className.endsWith(Application.getRouterClassName());
			try {
				Class clazz = Class.forName(className);
				
				ClassMappingResult mappingItem=new ClassMappingResult();
				mappingItem.clazz=clazz;
				mappingItem.methodName=methodName;
				mappingItem.basePath=rule.urlPrefix+basePath;
				mappingItem.rewriteURL=isRuterClass ? rewriteURL : "";
				mappingItem.defaultTemplate=(rule.templatePrefix!=null && rule.templatePrefix.length()>1 ? rule.templatePrefix : rule.urlPrefix)+basePath+clazz.getSimpleName()+Application.getTemplateExtension(); //templatePrefix默认值是“/”，如果是“/”或空，则应该换为basePath
				mappingItem.parseTime=Utils.now().getTime();
				CACHE_MAP.put(url, mappingItem);
				return mappingItem;
				
			} catch (ClassNotFoundException e) {
				
				if (!isRuterClass) {//如果之前加载的不是路由类，则切换为路由类
					className=className.substring(0, className.lastIndexOf(".")+1)+Application.getRouterClassName();
					methodName=Application.getDefaultMethodName();
					return mappingClass(rule, url, basePath, rewriteURL, className, methodName);
					
				} else {//否则尝试加载上级路由类
					//递归计算
					String packageName=className.lastIndexOf(".")!=-1 ? className.substring(0, className.lastIndexOf(".")) : "";
					String parentPackageName=packageName.lastIndexOf(".")!=-1 ? packageName.substring(0, packageName.lastIndexOf(".")) : "";
					if (parentPackageName.length()>=rule.classPackage.length()) {
						String parentRouter=parentPackageName+"."+Application.getRouterClassName();
						String parentPath=basePath.length()>0 ? basePath.substring(0, basePath.substring(0, basePath.length()-1).lastIndexOf("/")+1) : "";
						String parentRewriteURL=basePath.substring(parentPath.length())+rewriteURL;
						return mappingClass(rule, url, parentPath, parentRewriteURL, parentRouter, Application.getDefaultMethodName());
					} else {
						return null;
					}
				}
			}
		}
		return null;
	}
	
}
