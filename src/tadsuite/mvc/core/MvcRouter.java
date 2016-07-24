package tadsuite.mvc.core;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Locale;

import freemarker.template.Template;
import freemarker.template.TemplateException;
import tadsuite.mvc.Application;
import tadsuite.mvc.logging.LogFactory;
import tadsuite.mvc.logging.Logger;
import tadsuite.mvc.utils.Constants;
import tadsuite.mvc.utils.Utils;

public class MvcRouter {
	
	@SuppressWarnings("unchecked")
	public static void processRequest(MvcRequest mvcRequest) {
		long startTime=System.currentTimeMillis();
		
		ClassMappingResult mappingResult=mvcRequest.getClassMappingResult();
		try {
			
			Object obj = mappingResult.clazz.newInstance();
			try {
				
				Method startActionMethod = mappingResult.clazz.getMethod("startAction", MvcRequest.class);
				startActionMethod.invoke(obj, mvcRequest);
				
				Method doActionMethod = mappingResult.clazz.getMethod(mappingResult.methodName);
				doActionMethod.invoke(obj);
				
			} catch (ExecuteEndExcpetion e) {
				Method setMvcViewMethod = mappingResult.clazz.getMethod("setMvcView", String.class, String.class);
				setMvcViewMethod.invoke(obj, e.getCode(), e.getMessage());
				
			} catch (InvocationTargetException result) {//反射方法中抛出的异常都为此类型
				try {//如果尝试转换为EndExecutingExcpetion失败，则不管它。
					ExecuteEndExcpetion cause=(ExecuteEndExcpetion)result.getCause();
					Method setMvcViewMethod = mappingResult.clazz.getMethod("setMvcView", String.class, String.class);
					setMvcViewMethod.invoke(obj, cause.getCode(), cause.getMessage());
				} catch (Exception e ) {
					throw result.getCause();
				}
			} catch (Exception e) {//doActionMethod方法未定义等情况
				throw e;
			} finally {
				Method finishActionMethod = mappingResult.clazz.getMethod("finishAction", LinkedHashMap.class);
				mappingResult.templatePath=(String)finishActionMethod.invoke(obj, mvcRequest.getRootMap());
			}
			
		} catch (ClassNotFoundException e) {
			long totalTime=System.currentTimeMillis()-startTime;
			showErrorLogging(mvcRequest, mappingResult, e, "404 Controller Class Not Found, time: "+totalTime);
			mvcRequest.getResponse().sendError(404);
			return;
			
		} catch (NoClassDefFoundError e) {
			// 经测试，如果URL中类名的大小写有误会导致此异常，运行时找不到相关的包也会导致此错误。
			long totalTime=System.currentTimeMillis()-startTime;
			showErrorLogging(mvcRequest, mappingResult, e, "404 Controller Class Not Defined, time: "+totalTime);
			return;
			
		} catch (NoSuchMethodException e) {
			long totalTime=System.currentTimeMillis()-startTime;
			showErrorLogging(mvcRequest, mappingResult, e, "404 Controller Method Not Found, time: "+totalTime);
			mvcRequest.getResponse().sendError(404);
			return;
			
		} catch (Throwable e) {
			long totalTime=System.currentTimeMillis()-startTime;
			showErrorLogging(mvcRequest, mappingResult, e, "500 Controller Error, time: "+totalTime);
			mvcRequest.getResponse().sendError(500);
			return;
		}
		
		mvcRequest.setAttribute(Constants.LOGIC_FINISHED_TIME, System.currentTimeMillis());
		
		// 读取模板并进行处理
		try {
			if (mappingResult.templatePath == null) {
				// Controller返回空值，则使用与URL一致的默认模板
				mappingResult.templatePath = mappingResult.templatePrefix+mappingResult.clazz.getSimpleName()+Application.getTemplateExtension();
				
			} else if (mappingResult.templatePath.equals(MvcControllerBase.RESULT_JSON)) {
				showJSON(mvcRequest, mappingResult);
				return;
				
			} else if (mappingResult.templatePath.equals(MvcControllerBase.RESULT_XML)) {
				showXML(mvcRequest, mappingResult);
				return;
				
			} else if (mappingResult.templatePath.equals(MvcControllerBase.RESULT_TEXT)) {
				showTEXT(mvcRequest, mappingResult);
				return;
				
			} else if (mappingResult.templatePath.equals(MvcControllerBase.RESULT_SCRIPT)) {
				showSCRIPT(mvcRequest, mappingResult);
				return;
				
			} else if (mappingResult.templatePath.equals(MvcControllerBase.RESULT_END)) {
				String message=(String)mvcRequest.getRootMap().get("result_message");
				if (message!=null) {
					Logger errorLogger=LogFactory.getLogger(Constants.LOGGER_NAME_ERROR);
					errorLogger.error("ERROR RESULT END:"+message);
				}
				return; // 如果返回结果为需要中断执行，则退出处理过程
				
			//异步调用，不再支持，} else if (mappingResult.templatePath.equals(MvcControllerBase.RESULT_BYPASS)) {
			//return true; // 如果返回结果为服务器动作执行，不经过模板层。
				
			} else if (mappingResult.templatePath.equals(MvcControllerBase.RESULT_ERROR)) {
				mappingResult.templatePath = Application.getErrorResultTemplate();
				
			} else if (mappingResult.templatePath.equals(MvcControllerBase.RESULT_INFO)) {
				mappingResult.templatePath = Application.getInfoResultTemplate();
				
			} else if (mappingResult.templatePath.equals(MvcControllerBase.RESULT_LOGIN)) {
				mappingResult.templatePath=Application.getLoginResultTemplate();
				
			} else if (mappingResult.templatePath.equals(MvcControllerBase.RESULT_CSRF)) {
				mappingResult.templatePath = Application.getCsrfResultTemplate();
				
			} else if (!mappingResult.templatePath.startsWith("/")) {// 如果Controller的返回值以“/”开头，则不做处理直接加载该模板
				//否则按Controller所在路径计算模板路径
				//mappingResult.templatePath = routeResult.get("templatePrefix")+(routeResult.get("templatePrefix").equals("") || routeResult.get("templatePrefix").endsWith("/") ? "" : "/")+ mappingResult.templatePath;
				//mappingResult.templatePath = routeResult.get("templatePath").substring(0, routeResult.get("templatePath").lastIndexOf("/")+1)+mappingResult.templatePath;
				mappingResult.templatePath=mappingResult.templatePrefix+mappingResult.templatePath;
			}
			
			if (mappingResult.templatePath.endsWith("/") || mappingResult.templatePath.endsWith("/" + Application.getTemplateExtension())) { //修正默认类的模板
				mappingResult.templatePath = mappingResult.templatePath.substring(0, mappingResult.templatePath.lastIndexOf("/") + 1) + Application.getDefaultClassName() + Application.getTemplateExtension();
			}
			
			Template template=null;
			if (Application.isUseVersionTemplate()) {
				String versionTplPath = mappingResult.templatePath.substring(0, mappingResult.templatePath.length() - Application.getTemplateExtension().length())+ Application.getTemplateVersionMark() + Application.getTemplateExtension();
				try {
					//errorLogger.warn("Load Template:"+versionTplPath);
					template = Application.getTemplateConfig().getTemplate(versionTplPath);
					mappingResult.templatePath=versionTplPath;
				} catch (IOException e) {
					/* DON'T REMOVE_METHOD_NAME_HERE!!
					int k=versionTplPath.indexOf("_", versionTplPath.lastIndexOf("/")+1);
					if (k!=-1) {
						String defaultVersionTplPath=versionTplPath.substring(0, k)+ templateVersionMark +templateExtension; //remove method name;
						try {
							//errorLogger.warn("Load Template:"+defaultVersionTplPath);
							template = templateConfig.getTemplate(defaultVersionTplPath);
							mappingResult.templatePath=defaultVersionTplPath;
						} catch (IOException e2) {}						
					}*/
				}
			}
			if (template==null) {
				try {
					//errorLogger.warn("Load Template:"+mappingResult.templatePath);
					template = Application.getTemplateConfig().getTemplate(mappingResult.templatePath);
				} catch (IOException e) {
					/* DON'T REMOVE_METHOD_NAME_HERE!!
					int k=mappingResult.templatePath.indexOf("_", mappingResult.templatePath.lastIndexOf("/")+1);
					if (k!=-1) {
						String defaultTplPath=mappingResult.templatePath.substring(0, k)+templateExtension; //remove method name;
						try {
							//errorLogger.warn("Load Template:"+defaultTplPath);
							template = templateConfig.getTemplate(defaultTplPath);
							mappingResult.templatePath=defaultTplPath;
						} catch (IOException e2) {}	
					}*/
				}
			}
			if (template==null) {
				Logger errorLogger=LogFactory.getLogger(Constants.LOGGER_NAME_ERROR);
				errorLogger.warn("** Template Not Exists({})", mappingResult.templatePath);
				return;
			}
			String currentLocale=mvcRequest.getCurrentLocale();
			if (!Application.getDefaultLocale().equals(currentLocale)) {
				try {
					template.setLocale(new Locale(currentLocale));
					template.setDateFormat(Application.getLocaleMap().get(currentLocale).get("dateFormat"));
					template.setDateTimeFormat(Application.getLocaleMap().get(currentLocale).get("dateTimeFormat"));
					template.setNumberFormat(Application.getLocaleMap().get(currentLocale).get("numberFormat"));
				} catch (Exception e) {				
				}
			}
			mvcRequest.getResponse().setCharacterEncoding(template.getOutputEncoding());
			Object attrContentType = template.getCustomAttribute("content_type");
			if (attrContentType != null) {
				mvcRequest.getResponse().setContentType(attrContentType.toString());
			} else if (mvcRequest.getResponse().getContentType()==null || mvcRequest.getResponse().getContentType().equals("")) {
				mvcRequest.getResponse().setContentType("text/html");
			}
			mvcRequest.getRootMap().put("execute_time", System.currentTimeMillis() - startTime);
			mvcRequest.getRootMap().put("app_startup_time", Application.getSystemStartupTime());
			//template.process(rootMap, response.getWriter());
			template.process(mvcRequest.getRootMap(), new MvcWriter(mvcRequest.getResponse().getWriter(), mvcRequest.getFinalMap()));
			
		} catch (TemplateException e) {
			Logger errorLogger=LogFactory.getLogger(Constants.LOGGER_NAME_ERROR);
			errorLogger.warn("** Parse Template Failture ({}). ", mappingResult.templatePath);
			errorLogger.catching(e);
			
		} catch (IOException e) {
			Logger errorLogger=LogFactory.getLogger(Constants.LOGGER_NAME_ERROR);
			errorLogger.warn("** Template IO Error ({}).", mappingResult.templatePath);
			errorLogger.catching(e);
			
		} catch (Exception e) {
			Logger errorLogger=LogFactory.getLogger(Constants.LOGGER_NAME_ERROR);
			errorLogger.warn("** Template Process Failture({}).", mappingResult.templatePath);
			errorLogger.catching(e);
		}
	}


	public static void showTEXT(MvcRequest request, ClassMappingResult mappingResult) {
		request.getResponse().setContentType("text/html");
		request.getResponse().setCharacterEncoding(Constants.ENCODING);
		try {
			request.getResponse().getWriter().print(request.getRootMap().get("result_message"));
		} catch (IOException e) {
			showErrorLogging(request, mappingResult, e, ", show as TEXT.");
		}
	}
	
	public static void showSCRIPT(MvcRequest request, ClassMappingResult mappingResult) {
		request.getResponse().setContentType("text/html");
		request.getResponse().setCharacterEncoding(Constants.ENCODING);
		try {
			request.getResponse().getWriter().print("<html>\n<meta http-equiv=\"Content-Type\" content=\"text/html; charset=utf-8\" />\n<script type=\"text/javascript\" language=\"javascript\">\n");
			request.getResponse().getWriter().print(request.getRootMap().get("result_message"));
			request.getResponse().getWriter().print("\n</script>\n</html>");
		} catch (IOException e) {
			showErrorLogging(request, mappingResult, e, ", show as SCRIPT.");
		}
	}
	
	public static void showJSON(MvcRequest request, ClassMappingResult mappingResult) {
		request.getResponse().setContentType("text/html");//request.getResponse().setContentType("application/json");采用该方式会导致jQuery无法正确parse返回的数据。
		request.getResponse().setCharacterEncoding(Constants.ENCODING);
		try {
			//由于该类无法正常处理时间类型，不得不自己写request.getResponse().getWriter().print(JSONValue.toJSONString(rootMap));
			request.getResponse().getWriter().print(Utils.json(request.getRootMap()));
		} catch (IOException e) {
			showErrorLogging(request, mappingResult, e, ", show as JSON.");
		}
	}

	public static void showXML(MvcRequest request, ClassMappingResult mappingResult) {
		request.getResponse().setContentType("application/xml");//如果使用text/xml则可能导致使用us-ascii码编码
		request.getResponse().setCharacterEncoding(Constants.ENCODING);
		try {
			request.getResponse().getWriter().print(Utils.xml(request.getRootMap()));
		} catch (IOException e) {
			showErrorLogging(request, mappingResult, e, ", show as XML.");
		}
	}
	
	private static void showErrorLogging(MvcRequest request, ClassMappingResult mappingResult, Throwable e, String text) {
		Logger errorLogger=LogFactory.getLogger(Constants.LOGGER_NAME_ERROR);
		errorLogger.catching(e);
		StringBuffer sb=new StringBuffer("** ")
				.append(Application.getSystemName()).append(" : ")
				.append(request.getMethod()).append(" - ").append(request.getFullURL())
				.append(" from ").append(request.getRemoteAddr())
				.append(", mapping to : ").append(mappingResult.clazz.getName()).append(".").append(mappingResult.methodName).append("() ").append(text)
				.append("\n").append(Constants.DASH_LINE);
		errorLogger.error(sb.toString());
	}
	
}
