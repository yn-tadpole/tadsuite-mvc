package tadsuite.mvc;

import java.io.File;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

import javax.servlet.ServletContext;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import freemarker.template.Configuration;
import freemarker.template.DefaultObjectWrapper;
import tadsuite.mvc.auth.AuthClientConfig;
import tadsuite.mvc.core.ClassMappingRule;
import tadsuite.mvc.core.MvcControllerBase;
import tadsuite.mvc.jdbc.DataSourceConfig;
import tadsuite.mvc.logging.LogFactory;
import tadsuite.mvc.logging.Logger;
import tadsuite.mvc.security.WebSecurityRule;
import tadsuite.mvc.utils.Constants;
import tadsuite.mvc.utils.Utils;

public class Application {
	
	public static final String CONFIG_LOCATION="/WEB-INF/tadsuite.xml";
	public static final String LOG4J_CONFIG_LOCATION="/WEB-INF/tadsuite-log4j2.xml";
	public static final String SECURITY_OPTION_CHECK_REFERER="R";
	public static final String SECURITY_OPTION_CHECK_POST_METHOD="P";
	public static final String SECURITY_OPTION_CHECK_CSRF_TOKEN="T";
	public static final String SECURITY_OPTION_CHECK_SIGNATURE="S";
	
	private static ConcurrentHashMap<String, Long> LOCKED_MAP=new ConcurrentHashMap<String, Long>();
	private static ConcurrentHashMap<String, Integer> DENY_NUM_MAP=new ConcurrentHashMap<String, Integer>();
	private static ConcurrentHashMap<String, Long> DENY_NUM_TIME_MAP=new ConcurrentHashMap<String, Long>();
	
	private static boolean readingConfig=false, configReadSuccess=false, tadsuiteMvcEnabled=false;
	private static Date systemStartupTime;
	private static String systemName, systemTitle, workDir;
	private static String defaultLocale, defaultDateFormat, defaultDateTimeFormat, defaultNumberFormat;
	private static String defaultDataSourceName, defaultAuthClientAppId, configReadResultMessage;

	private static ArrayList<String> forbiddenUrlList = new ArrayList<String>();
	private static ArrayList<String> resourcePrefixList = new ArrayList<String>();
	private static ArrayList<String> resourceUrlList = new ArrayList<String>();
	private static LinkedHashMap<String, ClassMappingRule> controllerMap = new LinkedHashMap<String, ClassMappingRule>();
	private static LinkedHashMap<String, DataSourceConfig> dataSourceMap = new LinkedHashMap<String, DataSourceConfig>();
	private static LinkedHashMap<String, AuthClientConfig> authClientMap = new LinkedHashMap<String, AuthClientConfig>();
	private static LinkedHashMap<String, String> authProtechedUrlMap = new LinkedHashMap<String, String>();
	private static LinkedHashMap<String, String> authExcludedMap = new LinkedHashMap<String, String>();
	private static LinkedHashMap<String, String> parameterMap = new LinkedHashMap<String, String>();
	private static LinkedHashMap<String, LinkedHashMap<String, String>> localeMap=new LinkedHashMap<String, LinkedHashMap<String, String>>();
	private static LinkedHashMap<String, LinkedHashMap<String, String>> localeTextMap=new LinkedHashMap<String, LinkedHashMap<String, String>>();
	
	private static Configuration templateConfig=null;
	private static String defaultClassName="Welcome";
	private static String routerClassName="Router";
	private static String defaultMethodName="execute";
	private static String templateExtension=".htm";
	private static boolean useVersionTemplate=false;
	private static String errorResultTemplate, loginResultTemplate, infoResultTemplate, csrfResultTemplate, templateVersionMark;
	private static int templateRefreshDelay=0;
	
	private static int parameterValueMaxLength=300;
	private static String csrfTokenName="token";
	private static String csrfTokenSeedName="seed";
	private static String signatureName="signature";
	private static String[] securityDefaultOption=new String[] {};
	private static LinkedHashMap<String, String> securityExcludedMap = new LinkedHashMap<String, String>();
	private static ArrayList<WebSecurityRule> securityCheckRuleList = new ArrayList<WebSecurityRule>();
	
	private static ServletContext servlet;
	
	public static void init(ServletContext srv) {
		servlet=srv;
		systemStartupTime=Utils.now();
		workDir=servlet.getRealPath("/").replace(File.separator, "/");
		if (workDir.endsWith("/")) {
			workDir=workDir.substring(0, workDir.length()-1);
		}
		
		reloadConfigurationFile();
		
		System.setProperty("work_dir", workDir);
		System.setProperty("work_dir_parent", workDir.lastIndexOf("/")!=-1 ? workDir.substring(0, workDir.lastIndexOf("/")) : workDir);
		System.setProperty("system_name", systemName);
		System.setProperty("logger_name_auth", Constants.LOGGER_NAME_AUTH);
		System.setProperty("logger_name_logic_mgr", Constants.LOGGER_NAME_LOGIC_MGR);
		System.setProperty("logger_name_logic_app", Constants.LOGGER_NAME_LOGIC_APP);
		System.setProperty("logger_name_error", Constants.LOGGER_NAME_ERROR);
		System.setProperty("logger_name_jdbc", Constants.LOGGER_NAME_JDBC);
		System.setProperty("logger_name_performance", Constants.LOGGER_NAME_PERFORMANCE);
		System.setProperty("logger_name_security", Constants.LOGGER_NAME_SECURITY);
		
		System.setProperty("Log4jContextSelector", "org.apache.logging.log4j.core.async.AsyncLoggerContextSelector");
		
		LoggerContext context =(LoggerContext)LogManager.getContext(false); //false is important
		File file=new File(servlet.getRealPath(LOG4J_CONFIG_LOCATION));
		context.setConfigLocation(file.toURI());
		Logger rootLogger=LogFactory.getRootLogger();
		
		StringBuffer sb=new StringBuffer("\n");
		sb.append(Constants.STAR_LINE).append("\n");
		sb.append(String.format("**  %s [%s] startup at %s ", systemName, systemTitle, Utils.dateFormat(systemStartupTime, "yyyy-MM-dd HH:mm"))).append("\n");
		sb.append(String.format("** context path: %s, work directory: %s", servlet.getContextPath().length()>0 ? servlet.getContextPath() : "/", workDir)).append("\n");
		
		//long seconds=Duration.between(systemStartupTime, LocalDateTime.now()).getSeconds();
		if (configReadSuccess) {
			sb.append("** Initialization finished successfully.").append("\n"); // in "+seconds+"s
		} else {
			sb.append("** Initialization finished with error! ------------------------> "+configReadResultMessage).append("\n"); // in "+seconds+"s
		}
		sb.append(Constants.DASH_LINE);
		rootLogger.warn(sb.toString());
	}
	
	public static void reloadConfigurationFile() {
		if (readingConfig) {
			return;
		}
		readingConfig=true;
		Logger rootLogger=LogFactory.getRootLogger();
		try {
			DocumentBuilderFactory factory=DocumentBuilderFactory.newInstance();
			DocumentBuilder builder=factory.newDocumentBuilder();
			Document doc=builder.parse(servlet.getRealPath(CONFIG_LOCATION).replace(File.separator, "/"));
			Element root=doc.getDocumentElement();
			
			//Read Global Configuration.
			if (root.getElementsByTagName("Global").getLength()>0) {
				Element globalElement=(Element)root.getElementsByTagName("Global").item(0);
				defaultLocale=Utils.checkNull(globalElement.getAttribute("defaultLocale"), "");
			}
			
			//Read Resource Configuration.
			ArrayList<String> newForbiddenList=new ArrayList<String>();
			Element forbiddenElement=(Element)root.getElementsByTagName("ForbiddenURL").item(0);
			NodeList forbiddenRuleList=forbiddenElement.getElementsByTagName("Rule");
			for (int i=0; i<forbiddenRuleList.getLength(); i++) {
				Element route=(Element)forbiddenRuleList.item(i);
				String prefix=route.getAttribute("prefix");
				if (prefix!=null && prefix.length()>0) {
					newForbiddenList.add(prefix);
				}
			}
			forbiddenUrlList=newForbiddenList;
			
			//Read Resource Configuration.
			ArrayList<String> newResourcePrefixList=new ArrayList<String>();
			ArrayList<String> newResourceUrlList=new ArrayList<String>();
			Element resourcesElement=(Element)root.getElementsByTagName("ResourcesURL").item(0);
			NodeList resourceRuleList=resourcesElement.getElementsByTagName("Rule");
			for (int i=0; i<resourceRuleList.getLength(); i++) {
				Element route=(Element)resourceRuleList.item(i);
				String prefix=route.getAttribute("prefix");
				if (prefix!=null && prefix.length()>0) {
					newResourcePrefixList.add(prefix);
				}
				String url=route.getAttribute("url");
				if (url!=null && url.length()>0) {
					newResourceUrlList.add(url);
				}
			}
			resourcePrefixList=newResourcePrefixList;
			resourceUrlList=newResourceUrlList;
			
			//Read Security Configuration.
			if (root.getElementsByTagName("WebSecurity").getLength()>0) {
				readSecurityOptions((Element)root.getElementsByTagName("WebSecurity").item(0));
			}
			
			//Read Mvc Configuration.
			/*
			if (root.getElementsByTagName("TadsuiteMvc").getLength()>0) {
				Element tadsuiteMvc=(Element)root.getElementsByTagName("TadsuiteMvc").item(0);
				tadsuiteMvcEnabled="true".equals(tadsuiteMvc.getAttribute("enabled"));
				if (tadsuiteMvcEnabled) {
					readTadsuiteMvcOptions(tadsuiteMvc);
				}
			} else {
				tadsuiteMvcEnabled=false;
			}*/
			tadsuiteMvcEnabled=true;
			readTadsuiteMvcOptions(root);
			
			//Read DataSources Configuration.
			if (root.getElementsByTagName("DataSources").getLength()>0) {
				readDataSourceOptions((Element)root.getElementsByTagName("DataSources").item(0));
			}
			
			//Read AuthClient Configuration.
			if (root.getElementsByTagName("Authencation").getLength()>0) {
				readAuthClientOptions((Element)root.getElementsByTagName("Authencation").item(0));
			}
			
			//Read Parameter Configuration.
			if (root.getElementsByTagName("Parameters").getLength()>0) {
				readParameterOptions((Element)root.getElementsByTagName("Parameters").item(0));
			}

			//Read Locale
			readLocaleOptions(builder);

			configReadSuccess=true;
			configReadResultMessage="";
			//rootLogger.trace("** Reload Configuration File Success, for {} [{}].", systemName, systemTitle);
			
		} catch (Exception e) {
			rootLogger.error("** Reload Configuration File Failture, for {} [{}].", systemName, systemTitle);
			rootLogger.catching(e);
			configReadSuccess=false;
			configReadResultMessage=e.getMessage();
			return;
		}
		readingConfig=false;
	}
	
	private static void readSecurityOptions(Element element) {
		parameterValueMaxLength=Utils.parseInt(element.getAttribute("parameterValueMaxLength"), 300);
		csrfTokenName=Utils.checkNull(element.getAttribute("csrfTokenName"), "token");
		csrfTokenSeedName=Utils.checkNull(element.getAttribute("csrfTokenSeedName"), "seed");
		signatureName=Utils.checkNull(element.getAttribute("signatureName"), "signature");
		
		
		securityDefaultOption=Utils.splitString(Utils.checkNull(element.getAttribute("defaultCheckOptions"), ""), ',');
		
		//Read Rule Configuration.
		LinkedHashMap<String, String> newSecurityExcludedMap= new LinkedHashMap<String, String>();
		ArrayList<WebSecurityRule> newSecurityCheckRuleList = new ArrayList<WebSecurityRule>();
		NodeList ruleList=element.getElementsByTagName("Rule");
		for (int i=0; i<ruleList.getLength(); i++) {
			Element node=(Element)ruleList.item(i);
			String excluded=node.getAttribute("excludedURL");
			String prefix=node.getAttribute("prefix");
			String suffix=node.getAttribute("suffix");
			String checkOptions=node.getAttribute("checkOptions");
			if (excluded.length()>0) {
				newSecurityExcludedMap.put(excluded, excluded);
			}
			if ((prefix.length()>0 || suffix.length()>0) ) { //&& checkOptions.length()>0否则就无法旁路某项了
				WebSecurityRule rule=new WebSecurityRule();
				rule.prefix=prefix;
				rule.suffix=suffix;
				rule.checkOptions=Utils.splitString(checkOptions, ',');
				newSecurityCheckRuleList.add(rule);
			}
		}
		securityExcludedMap=newSecurityExcludedMap;
		securityCheckRuleList=newSecurityCheckRuleList;
	}
	
	
	
	private static void readTadsuiteMvcOptions(Element element) {

		if (templateConfig==null) {
			try {//此句要在创建templateConfig前调用，否则不再有效
				freemarker.log.Logger.selectLoggerLibrary(freemarker.log.Logger.LIBRARY_NONE);
			} catch (ClassNotFoundException e) {
				//ignore template log.
			}
			templateConfig = new Configuration();
			templateConfig.setDefaultEncoding(Constants.ENCODING);
			templateConfig.setOutputEncoding(Constants.ENCODING);
			templateConfig.setServletContextForTemplateLoading(servlet, null);
			templateConfig.setObjectWrapper(new DefaultObjectWrapper());
		}
		
		//Read Controller Configuration.
		Element controllersElement=(Element)element.getElementsByTagName("Controllers").item(0);
		defaultClassName=Utils.checkNull(controllersElement.getAttribute("defaultClass"), "Welcome");
		routerClassName=Utils.checkNull(controllersElement.getAttribute("routerClass"), "Router");
		defaultMethodName=Utils.checkNull(controllersElement.getAttribute("defaultMethod"), "execute");
		
		LinkedHashMap<String, ClassMappingRule> newRuleMap = new LinkedHashMap<String, ClassMappingRule>();
		NodeList controllerRuleList=controllersElement.getElementsByTagName("Rule");
		for (int i=0; i<controllerRuleList.getLength(); i++) {
			Element route=(Element)controllerRuleList.item(i);
			String prefix=route.getAttribute("prefix");
			String classPackage=route.getAttribute("package");
			String templatePrefix=route.getAttribute("templatePrefix");
			if (prefix!=null && classPackage!=null && classPackage.length()>0) {
				prefix=(prefix.startsWith("/") ? "" : "/")+prefix+(prefix.endsWith("/") ? "" : "/");
				if (classPackage.endsWith("*")  || classPackage.endsWith(".")) {//去除“.”或者“.*”
					classPackage=classPackage.substring(0, classPackage.lastIndexOf("."));
				}
				if (templatePrefix==null) {
					templatePrefix=prefix;
				} else if (!templatePrefix.endsWith("/")) {
					templatePrefix+="/";
				}
				ClassMappingRule rule=new ClassMappingRule();
				rule.urlPrefix=prefix;
				rule.classPackage=classPackage;
				rule.templatePrefix=templatePrefix;
				
				newRuleMap.put(prefix, rule);
			}
		}
		controllerMap=newRuleMap;
		
		//Read Templates Configuration.
		Element templateElement=(Element)element.getElementsByTagName("Templates").item(0);
		templateRefreshDelay=Utils.parseInt(templateElement.getAttribute("templateRefreshDelay"), 0);
		templateVersionMark=Utils.checkNull(templateElement.getAttribute("templateVersionMark"), "");
		useVersionTemplate=templateVersionMark != null && templateVersionMark.length()> 0;
		infoResultTemplate=templateElement.getElementsByTagName("InfoResultTemplate").getLength()>0 ? templateElement.getElementsByTagName("InfoResultTemplate").item(0).getTextContent() : "/_tadsuite/result_info.htm";
		loginResultTemplate=templateElement.getElementsByTagName("LoginResultTemplate").getLength()>0 ? templateElement.getElementsByTagName("LoginResultTemplate").item(0).getTextContent() : "/_tadsuite/result_login.htm";
		errorResultTemplate=templateElement.getElementsByTagName("ErrorResultTemplate").getLength()>0 ? templateElement.getElementsByTagName("ErrorResultTemplate").item(0).getTextContent() : "/_tadsuite/result_error.htm";
		csrfResultTemplate=templateElement.getElementsByTagName("CSRFResultTemplate").getLength()>0 ? templateElement.getElementsByTagName("CSRFResultTemplate").item(0).getTextContent() : "/_tadsuite/result_csrf.htm";
		
		templateConfig.setTemplateUpdateDelay(templateRefreshDelay);
			
	}
	
	private static void readDataSourceOptions(Element element) {
		//Read DataSource Configuration.
		LinkedHashMap<String, DataSourceConfig> newDataSourceMap = new LinkedHashMap<String, DataSourceConfig>();
		NodeList dataSourceList=element.getElementsByTagName("DataSource");
		for (int i=0; i<dataSourceList.getLength(); i++) {
			Element dataSource=(Element)dataSourceList.item(i);
			if (dataSource.getAttribute("name").length()>0) {
				if (i==0) {
					defaultDataSourceName=dataSource.getAttribute("name");
				}
				DataSourceConfig config=new DataSourceConfig();
				config.name=dataSource.getAttribute("name");
				config.tablePrefix=dataSource.getAttribute("tablePrefix");
				config.dbType=dataSource.getAttribute("dbType");
				config.jndiName=dataSource.getAttribute("jndiName");
				
				NodeList propertyList=dataSource.getElementsByTagName("property");
				for (int j=0; j<propertyList.getLength(); j++) {
					Element property=(Element)propertyList.item(j);
					config.property.put(property.getAttribute("name"), property.getAttribute("value"));
				}
				newDataSourceMap.put(dataSource.getAttribute("name"), config);
			}
		}
		dataSourceMap=newDataSourceMap;
	}
	
	private static void readAuthClientOptions(Element element) {
		LinkedHashMap<String, AuthClientConfig> newAuthClientMap = new LinkedHashMap<String, AuthClientConfig>();
		if (element.getElementsByTagName("AuthClients").getLength()>0) {
			NodeList authClientList=((Element) element.getElementsByTagName("AuthClients").item(0)).getElementsByTagName("AuthClient");
			for (int i=0; i<authClientList.getLength(); i++) {
				Element authClient=(Element)authClientList.item(i);
				if (authClient.getAttribute("authAppId").length()>0) {
					if (i==0) {
						defaultAuthClientAppId=authClient.getAttribute("authAppId");
					}
					AuthClientConfig config=new AuthClientConfig();
					config.authAppId=authClient.getAttribute("authAppId");
					config.authType=authClient.getAttribute("authType");
					
					NodeList propertyList=authClient.getElementsByTagName("property");
					for (int j=0; j<propertyList.getLength(); j++) {
						Element property=(Element)propertyList.item(j);
						config.property.put(property.getAttribute("name"), property.getAttribute("value"));
					}
					
					newAuthClientMap.put(authClient.getAttribute("authAppId"), config);
				}
			}
			authClientMap=newAuthClientMap;
		}
		
		//Read Resource Configuration.
		LinkedHashMap<String, String> newAuthExcludedMap= new LinkedHashMap<String, String>();
		LinkedHashMap<String, String> newAuthProtechedUrlMap=new LinkedHashMap<String, String>();
		Element protechedUrlElement=(Element)element.getElementsByTagName("ProtechedURL").item(0);
		if (element.getElementsByTagName("ProtechedURL").getLength()>0) {
			NodeList protechedRuleList=protechedUrlElement.getElementsByTagName("Rule");
			for (int i=0; i<protechedRuleList.getLength(); i++) {
				Element route=(Element)protechedRuleList.item(i);
				String excluded=route.getAttribute("excludedURL");
				String prefix=route.getAttribute("prefix");
				String appId=route.getAttribute("authAppId");
				if (excluded.length()>0) {
					newAuthExcludedMap.put(excluded, excluded);
				}  
				if (prefix!=null && prefix.length()>0) {
					newAuthProtechedUrlMap.put(prefix, appId);
				}
			}
		}
		authProtechedUrlMap=newAuthProtechedUrlMap;
		authExcludedMap=newAuthExcludedMap;
	}
	
	private static void readParameterOptions(Element element) {
		LinkedHashMap<String, String> newParameterMap=new LinkedHashMap<String, String>();
		NodeList parameterList=element.getChildNodes();
		for (int i=0; i<parameterList.getLength(); i++) {
			Node parameter=parameterList.item(i);
			if (!parameter.getNodeName().equals("#text")) {
				newParameterMap.put(parameter.getNodeName(), parameter.getTextContent());
			}
		}
		parameterMap=newParameterMap;
		systemName=servlet.getServletContextName();	
		systemTitle=parameterMap.containsKey("SystemTitle") ? parameterMap.get("SystemTitle") : systemName;
	}
	
	private static void readLocaleOptions(DocumentBuilder builder) {
		//Read Locale Configuration.
		LinkedHashMap<String, LinkedHashMap<String, String>> newLocaleMap=new LinkedHashMap<String, LinkedHashMap<String, String>>();
		LinkedHashMap<String, LinkedHashMap<String, String>> newLocaleTextMap=new LinkedHashMap<String, LinkedHashMap<String, String>>();
		File folder=new File(servlet.getRealPath("/WEB-INF/locale/"));
		if (folder.exists()) {
			String[] fileList=folder.list();
			for (int i=0; i<fileList.length; i++) {
				if (fileList[i].endsWith(".xml")) {
					String file=(servlet.getRealPath("/WEB-INF/locale/"+fileList[i])).replace(File.separator, "/");
					try {
						Document localeFile=builder.parse(file);
						int lastSeperator=file.lastIndexOf("/");
						String code=file.substring(lastSeperator+1, file.indexOf(".", lastSeperator));
						Element localeRoot=localeFile.getDocumentElement();
						LinkedHashMap<String, String> locale=new LinkedHashMap<String, String>();
						locale.put("dateFormat", localeRoot.getAttribute("dateFormat"));
						locale.put("dateTimeFormat", localeRoot.getAttribute("dateTimeFormat"));
						locale.put("numberFormat", localeRoot.getAttribute("numberFormat"));
						newLocaleMap.put(code, locale);
						
						NodeList textList=localeRoot.getChildNodes();
						LinkedHashMap<String, String> localeText=new LinkedHashMap<String, String>();
						for (int k=0; k<textList.getLength(); k++) {
							Node text=textList.item(k);
							if (!text.getNodeName().equals("#text")) {
								localeText.put(text.getNodeName(), text.getTextContent());
							}
						}
						newLocaleTextMap.put(code, localeText);
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
			}
		}
		localeMap=newLocaleMap;
		localeTextMap=newLocaleTextMap;
		
		if (defaultLocale.length()>0 && localeMap.containsKey(defaultLocale)) {
			defaultDateFormat=localeMap.get(defaultLocale).get("dateFormat");
			defaultDateTimeFormat=localeMap.get(defaultLocale).get("dateTimeFormat");
			defaultNumberFormat=localeMap.get(defaultLocale).get("numberFormat");
		} else {
			defaultDateFormat="";
			defaultDateTimeFormat="";
			defaultNumberFormat="";
		}
		if (tadsuiteMvcEnabled) {
			if (defaultLocale!=null && defaultLocale.length()>0) {
				try {
					templateConfig.setLocale(new Locale(defaultLocale));
				} catch (Exception e) {				
				}
			}
			if (defaultDateFormat!=null && defaultDateFormat.length()>0) {
				try {
					templateConfig.setDateFormat(defaultDateFormat);
				} catch (Exception e) {	
				}
			}
			if (defaultDateTimeFormat!=null && defaultDateTimeFormat.length()>0) {
				try {
					templateConfig.setDateTimeFormat(defaultDateTimeFormat);
				} catch (Exception e) {	
				}
			}
			if (defaultNumberFormat!=null && defaultNumberFormat.length()>0) {
				try {
					templateConfig.setNumberFormat(defaultNumberFormat);
				} catch (Exception e) {	
				}
			}
		}
	}
	
	public static String readLocaleConfig(String locale, String configTitle) {
		if (localeMap.containsKey(locale)) {
			return localeMap.get(locale).containsKey(configTitle) ? (String)localeMap.get(locale).get(configTitle) : "";
		}
		return "";
	}
	public static String readLocaleText(String locale, String title) {
		if (localeTextMap.containsKey(locale)) {
			return localeTextMap.get(locale).containsKey(title) ? localeTextMap.get(locale).get(title) : title;
		}
		return title;
	}
	

	public static boolean isbConfigReadSuccess() {
		return configReadSuccess;
	}
	
	public static Date getSystemStartupTime() {
		return systemStartupTime;
	}

	public static String getSystemName() {
		return systemName;
	}

	public static String getSystemTitle() {
		return systemTitle;
	}

	public static String getWorkDir() {
		return workDir;
	}

	public static String getDefaultLocale() {
		return defaultLocale;
	}

	public static String getDefaultDateFormat() {
		return defaultDateFormat;
	}

	public static String getDefaultDateTimeFormat() {
		return defaultDateTimeFormat;
	}

	public static String getDefaultNumberFormat() {
		return defaultNumberFormat;
	}

	public static String getDefaultDataSourceName() {
		return defaultDataSourceName;
	}

	public static String getDefaultAuthClientAppId() {
		return defaultAuthClientAppId;
	}

	public static String getConfigReadResultMessage() {
		return configReadResultMessage;
	}

	public static String getConfig(String parameterName) {
		return parameterMap.containsKey(parameterName) ? parameterMap.get(parameterName) : "";
	}
	
	public static boolean isDebugMode() {
		return "Y".equals(parameterMap.get("debugMode")) || "1".equals(parameterMap.get("debugMode")) || "true".equals(parameterMap.get("debugMode"));
	}

	public static LinkedHashMap<String, LinkedHashMap<String, String>> getLocaleMap() {
		return localeMap;
	}

	public static LinkedHashMap<String, LinkedHashMap<String, String>> getLocaleTextMap() {
		return localeTextMap;
	}

	public static boolean isTadsuiteMvcEnabled() {
		return tadsuiteMvcEnabled;
	}

	public static boolean isConfigReadSuccess() {
		return configReadSuccess;
	}


	public static LinkedHashMap<String, ClassMappingRule> getControllerMap() {
		return controllerMap;
	}

	public static LinkedHashMap<String, DataSourceConfig> getDataSourceMap() {
		return dataSourceMap;
	}

	public static LinkedHashMap<String, AuthClientConfig> getAuthClientMap() {
		return authClientMap;
	}
	
	public static LinkedHashMap<String, String> getAuthProtechedUrlMap() {
		return authProtechedUrlMap;
	}
	
	public static LinkedHashMap<String, String> getAuthExcludedMap() {
		return authExcludedMap;
	}

	public static Configuration getTemplateConfig() {
		return templateConfig;
	}

	public static String getDefaultClassName() {
		return defaultClassName;
	}

	public static String getRouterClassName() {
		return routerClassName;
	}

	public static String getDefaultMethodName() {
		return defaultMethodName;
	}

	public static boolean isUseVersionTemplate() {
		return useVersionTemplate;
	}

	public static String getTemplateExtension() {
		return templateExtension;
	}

	public static String getErrorResultTemplate() {
		return errorResultTemplate;
	}

	public static String getLoginResultTemplate() {
		return loginResultTemplate;
	}


	public static String getInfoResultTemplate() {
		return infoResultTemplate;
	}

	public static String getCsrfResultTemplate() {
		return csrfResultTemplate;
	}

	public static String getTemplateVersionMark() {
		return templateVersionMark;
	}
	

	public static int getParameterValueMaxLength() {
		return parameterValueMaxLength;
	}
	
	public static String getCsrfTokenName() {
		return csrfTokenName;
	}

	public static String getCsrfTokenSeedName() {
		return csrfTokenSeedName;
	}

	public static String getSignatureName() {
		return signatureName;
	}

	public static String[] getSecurityDefaultOption() {
		return securityDefaultOption;
	}

	public static LinkedHashMap<String, String> getSecurityExcludedMap() {
		return securityExcludedMap;
	}

	public static ArrayList<WebSecurityRule> getSecurityCheckRuleList() {
		return securityCheckRuleList;
	}

	/**
	 * 判断一个URL是否是一个禁止访问的URL
	 * @param url
	 * @return
	 */
	public static boolean isForbiddenURL(String url) {
		if (url.indexOf("/.")!=-1) {// ||  url.indexOf("/.svn/")!=-1 || url.indexOf("/.git/")!=-1;
			return true; //凡是“.开头的文件或文件夹都不允许访问”
			
		} else if (url.substring(url.lastIndexOf(".")+1).toLowerCase().startsWith("jsp")) {
			return isResourceURL(url); //如果url是.jsp或类似扩展名，则先判断它是不是位于资源文件夹（因为资源文件夹将旁路执行，会导致JSP会执行）
		} else {
			for (String prefix : forbiddenUrlList) {
				if (url.startsWith(prefix)) {
					return true;
				}
			}
			return false;
		}
	}
	
	/**
	 * 判断一个url是否应是资源URL（直接PASS不初始化会话或映射Controller）
	 * @param url
	 * @return
	 */
	public static boolean isResourceURL(String url) {
		if (resourceUrlList.contains(url)) {
			return true;
		}
		for (String prefix : resourcePrefixList) {
			if (url.startsWith(prefix)) {
				return true;
			}
		}
		return false;
	}
	
	public static void sysLock_lock(String mark, int minute){
		sysLock_cleanup();
		LOCKED_MAP.put(mark, Utils.now().getTime()+1000L*60L*(long)minute);
	}
	
	public static void sysLock_unlock(String mark){
		LOCKED_MAP.remove(mark);
	}
	
	public static boolean sysLock_check(String mark, boolean bEndExecuting) {
		if (!LOCKED_MAP.containsKey(mark)) {
			return true;
		} else if (LOCKED_MAP.get(mark)<Utils.now().getTime()) {
			sysLock_unlock(mark);
			return true;
		}
		if (bEndExecuting) {
			MvcControllerBase.endExecuting(MvcControllerBase.RESULT_ERROR, "当前IP地址被限制访问。提示：请勿多次猜测密码。");
		} 
		return false;
	}
	
	public static void sysLock_cleanup(){
		long now=Utils.now().getTime();
		for (String key : LOCKED_MAP.keySet()) {
			if (LOCKED_MAP.get(key)<now) {
				LOCKED_MAP.remove(key);
			}
		}
	}
	
	public static void writeDenyNum(String key, int value) {
		if (value>0) {
			DENY_NUM_MAP.put(key, value);
			DENY_NUM_TIME_MAP.put(key, Utils.now().getTime()+1000L*60L*60); //denyNum保留60分钟
			cleanupDenyNum();
		} else {
			DENY_NUM_MAP.remove(key);
			DENY_NUM_TIME_MAP.remove(key);
		}
	}
	
	public static int readDenyNum(String key) {
		if (!DENY_NUM_MAP.containsKey(key)) {
			return 0;
		} else if (DENY_NUM_TIME_MAP.get(key)<Utils.now().getTime()) {
			deleteDenyNum(key);
			return 0;
		}
		return DENY_NUM_MAP.containsKey(key) ? DENY_NUM_MAP.get(key) : 0;
	}
	
	public static void deleteDenyNum(String key) {
		DENY_NUM_MAP.remove(key);
		DENY_NUM_TIME_MAP.remove(key);
	}
	
	public static void cleanupDenyNum() {
		long now=Utils.now().getTime();
		for (String key : DENY_NUM_TIME_MAP.keySet()) {
			if (DENY_NUM_TIME_MAP.get(key)<now) {
				DENY_NUM_MAP.remove(key);
				DENY_NUM_TIME_MAP.remove(key);
			}
		}
	}
	
}
