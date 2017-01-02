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

public class MvcExecutor {
	
	/**
	 * 执行Controller，返回值代表是否还需要返回到容器进行进一步处理（仅当RESULT_BYPASS时为真）
	 * @param mvcRequest
	 * @return
	 */
	public static boolean execute(MvcRequest mvcRequest) {
		
		if (!executeController(mvcRequest)) {
			return false;
		}
		String templatePath=mvcRequest.getTemplatePath();
		
		if (templatePath.equals(MvcControllerBase.RESULT_BYPASS)) {
			return true; // 如果要求返回容器进行处理。
		} else if (templatePath.equals(MvcControllerBase.RESULT_END)) {
			logging(mvcRequest);
			return false;
		} else {
			if (templatePath.equals(MvcControllerBase.RESULT_JSON)) {
				showJSON(mvcRequest);
			} else if (templatePath.equals(MvcControllerBase.RESULT_XML)) {
				showXML(mvcRequest);
			} else if (templatePath.equals(MvcControllerBase.RESULT_TEXT)) {
				showTEXT(mvcRequest);
			} else if (templatePath.equals(MvcControllerBase.RESULT_SCRIPT)) {
				showSCRIPT(mvcRequest);
			} else {
				if (templatePath.equals(MvcControllerBase.RESULT_ERROR)) {
					templatePath = Application.getErrorResultTemplate();
				} else if (templatePath.equals(MvcControllerBase.RESULT_INFO)) {
					templatePath = Application.getInfoResultTemplate();
				} else if (templatePath.equals(MvcControllerBase.RESULT_LOGIN)) {
					templatePath=Application.getLoginResultTemplate();
				} else if (templatePath.equals(MvcControllerBase.RESULT_CSRF)) {
					templatePath = Application.getCsrfResultTemplate();
				} else if (!templatePath.startsWith("/")) {// 如果模板不是“/”开头，则应进行相对路径计算
					String defaultTemplate=mvcRequest.getClassMappingResult().defaultTemplate;
					templatePath=defaultTemplate.substring(0, defaultTemplate.lastIndexOf("/")+1)+templatePath;
				}
				if (templatePath.endsWith("/") || templatePath.endsWith("/" + Application.getTemplateExtension())) { //修正默认类的模板
					templatePath = templatePath.substring(0, templatePath.lastIndexOf("/") + 1) + Application.getDefaultClassName() + Application.getTemplateExtension();
				}
				mvcRequest.setTemplatePath(templatePath);
				processFreemarkerTemplate(mvcRequest);
			}
			logging(mvcRequest);
			return false;
		}
	}
	
	
	/**
	 * 执行Controller的方法：startAction->execute(or other methodName)-> finishAction
	 * 返回值表示执行是否成功，执行成功后会写入templatePath
	 * @param mvcRequest
	 * @return
	 */
	@SuppressWarnings("unchecked")
	private static boolean executeController(MvcRequest mvcRequest) {
		mvcRequest.setAttribute(Constants.START_TIME, System.currentTimeMillis());
		ClassMappingResult mappingResult=mvcRequest.getClassMappingResult();
		boolean success=false;
		
		try {
			
			Object obj = mappingResult.clazz.newInstance();
			try {
				Method startActionMethod = mappingResult.clazz.getMethod("startAction", MvcRequest.class);
				startActionMethod.invoke(obj, mvcRequest);
				
				Method doActionMethod = mappingResult.clazz.getMethod(mappingResult.methodName);
				doActionMethod.invoke(obj);
				mvcRequest.setAttribute(Constants.LOGIC_FINISHED_TIME, System.currentTimeMillis());
				
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
			} catch (Exception e) {
				//throw e;
			} finally {
				Method finishActionMethod = mappingResult.clazz.getMethod("finishAction", LinkedHashMap.class);
				String templatePath=(String)finishActionMethod.invoke(obj, mvcRequest.getRootMap());
				if (templatePath == null) {
					// Controller返回空值，则使用与URL一致的默认模板，这里不能直接返回null，因为报错才返回null
					templatePath = mappingResult.defaultTemplate;
				}
				mvcRequest.setTemplatePath(templatePath);
				success=true;
			}
			
		} catch (ClassNotFoundException e) {
			showErrorLogging(mvcRequest, e, "404 Controller Class Not Found.");
			mvcRequest.getResponse().sendError(404);
			
		} catch (NoClassDefFoundError e) {
			// 经测试，如果URL中类名的大小写有误会导致此异常，运行时找不到相关的包也会导致此错误。
			showErrorLogging(mvcRequest, e, "404 Controller Class Not Defined.");
			
		} catch (NoSuchMethodException e) {
			showErrorLogging(mvcRequest, e, "404 Controller Method Not Found. ");
			mvcRequest.getResponse().sendError(404);
			
		} catch (Throwable e) {
			showErrorLogging(mvcRequest, e, "500 Controller Error");
			mvcRequest.getResponse().sendError(500);
		}
		return success;
	}
	
	private static void processFreemarkerTemplate(MvcRequest mvcRequest) {
		Template template=null;
		String templatePath=mvcRequest.getTemplatePath();
		// 读取模板并进行处理
		try {
			if (Application.isUseVersionTemplate()) {
				String versionTplPath = templatePath.substring(0, templatePath.length() - Application.getTemplateExtension().length())+ Application.getTemplateVersionMark() + Application.getTemplateExtension();
				try {
					//errorLogger.warn("Load Template:"+versionTplPath);
					template = Application.getTemplateConfig().getTemplate(versionTplPath);
					templatePath=versionTplPath;
					mvcRequest.setTemplatePath(versionTplPath);
				} catch (IOException e) {
				}
			}
			if (template==null) {
				try {
					//errorLogger.warn("Load Template:"+templatePath);
					template = Application.getTemplateConfig().getTemplate(templatePath);
				} catch (IOException e) {
				}
			}
			if (template==null) {
				Logger errorLogger=LogFactory.getLogger(Constants.LOGGER_NAME_ERROR);
				errorLogger.warn("** Template Not Exists or Has Compile Errors({})", templatePath);
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
			mvcRequest.getRootMap().put("execute_time", mvcRequest.getAttribute(Constants.START_TIME)!=null ? System.currentTimeMillis() - (long)mvcRequest.getAttribute(Constants.START_TIME) : -1);
			mvcRequest.getRootMap().put("app_startup_time", Application.getSystemStartupTime());
			//template.process(rootMap, response.getWriter());
			template.process(mvcRequest.getRootMap(), new MvcWriter(mvcRequest.getResponse().getWriter(), mvcRequest.getFinalMap()));
			
		} catch (TemplateException e) {
			Logger errorLogger=LogFactory.getLogger(Constants.LOGGER_NAME_ERROR);
			errorLogger.warn("** Parse Template Failture ({}). ", templatePath);
			errorLogger.catching(e);
			
		} catch (IOException e) {
			Logger errorLogger=LogFactory.getLogger(Constants.LOGGER_NAME_ERROR);
			errorLogger.warn("** Template IO Error ({}).", templatePath);
			errorLogger.catching(e);
			
		} catch (Exception e) {
			Logger errorLogger=LogFactory.getLogger(Constants.LOGGER_NAME_ERROR);
			errorLogger.warn("** Template Process Failture({}).", templatePath);
			errorLogger.catching(e);
		}
	}


	public static void showTEXT(MvcRequest request) {
		request.getResponse().setContentType("text/html");
		request.getResponse().setCharacterEncoding(Constants.ENCODING);
		try {
			request.getResponse().getWriter().print(request.getRootMap().get("result_message"));
		} catch (IOException e) {
			showErrorLogging(request, e, ", show as TEXT.");
		}
	}
	
	public static void showSCRIPT(MvcRequest request) {
		request.getResponse().setContentType("text/html");
		request.getResponse().setCharacterEncoding(Constants.ENCODING);
		try {
			request.getResponse().getWriter().print("<html>\n<meta http-equiv=\"Content-Type\" content=\"text/html; charset=utf-8\" />\n<script type=\"text/javascript\" language=\"javascript\">\n");
			request.getResponse().getWriter().print(request.getRootMap().get("result_message"));
			request.getResponse().getWriter().print("\n</script>\n</html>");
		} catch (IOException e) {
			showErrorLogging(request, e, ", show as SCRIPT.");
		}
	}
	
	public static void showJSON(MvcRequest request) {
		request.getResponse().setContentType("text/html");//request.getResponse().setContentType("application/json");采用该方式会导致jQuery无法正确parse返回的数据。
		request.getResponse().setCharacterEncoding(Constants.ENCODING);
		try {
			//由于该类无法正常处理时间类型，不得不自己写request.getResponse().getWriter().print(JSONValue.toJSONString(rootMap));
			request.getResponse().getWriter().print(Utils.json(request.getRootMap()));
		} catch (IOException e) {
			showErrorLogging(request, e, ", show as JSON.");
		}
	}

	public static void showXML(MvcRequest request) {
		request.getResponse().setContentType("application/xml");//如果使用text/xml则可能导致使用us-ascii码编码
		request.getResponse().setCharacterEncoding(Constants.ENCODING);
		try {
			request.getResponse().getWriter().print(Utils.xml(request.getRootMap()));
		} catch (IOException e) {
			showErrorLogging(request, e, ", show as XML.");
		}
	}
	
	private static void showErrorLogging(MvcRequest request, Throwable e, String text) {
		Logger errorLogger=LogFactory.getLogger(Constants.LOGGER_NAME_ERROR);
		errorLogger.catching(e);
		StringBuffer sb=new StringBuffer("** ")
				.append(Application.getSystemName()).append(" : ")
				.append(request.getMethod()).append(" - ").append(request.getFullURL())
				.append(" from ").append(request.getRemoteAddr())
				.append(", mapping to : ").append(request.getClassMappingResult().clazz.getName()).append(".").append(request.getClassMappingResult().methodName).append("() ").append(text)
				.append("\n").append(Constants.DASH_LINE);
		errorLogger.error(sb.toString());
	}
	
	private static void logging(MvcRequest mvcRequest) {
		boolean ignoreLogging=mvcRequest.getAttribute(Constants.INGNORE_STATISTIC)!=null ? (boolean)mvcRequest.getAttribute(Constants.INGNORE_STATISTIC) : false;
		long startTime=mvcRequest.getAttribute(Constants.START_TIME)!=null ? (long) mvcRequest.getAttribute(Constants.START_TIME) : 0;
		if (startTime>0 && !ignoreLogging) {
			long authInitTime= mvcRequest.getAttribute(Constants.AUTH_ININTED_TIME)!=null ? (long) mvcRequest.getAttribute(Constants.AUTH_ININTED_TIME) : 0;
			long logicFinishedTime= mvcRequest.getAttribute(Constants.LOGIC_FINISHED_TIME)!=null ? (long) mvcRequest.getAttribute(Constants.LOGIC_FINISHED_TIME) : 0;
			long finishedTime=System.currentTimeMillis();
			
			long totalTime=finishedTime-startTime;
			long initTime=authInitTime==0 ? -1 : authInitTime-startTime;
			long logicTime=logicFinishedTime==0 ? -1 : authInitTime==0 ? logicFinishedTime-startTime : logicFinishedTime-authInitTime;
			long templateTime=logicFinishedTime==0 ? -1 : finishedTime-logicFinishedTime;
			
			Logger performanceLogger=LogFactory.getLogger(Constants.LOGGER_NAME_PERFORMANCE);
			
			StringBuffer sb=new StringBuffer()
					.append(Application.getSystemName()).append(" :").append(totalTime).append("ms - ")
					.append(mvcRequest.getMethod().toString().toLowerCase()).append(" - ").append(mvcRequest.getFullURL())
					.append(" from ").append(mvcRequest.getRemoteAddr())
					.append(" mapping to: ").append(mvcRequest.getClassMappingResult().clazz.getName())
					.append(" , template: ").append(mvcRequest.getTemplatePath())
					.append(", total time ").append(totalTime).append("ms ").append(", init ").append(initTime).append("ms ").append(", logic ").append(logicTime).append("ms ").append(", template ").append(templateTime).append("ms. ")
					.append("\n").append(Constants.DASH_LINE);
			if (totalTime>6000) {
				performanceLogger.warn(sb.toString());
			} else if (totalTime>3000 && performanceLogger.isInfoEnabled()) {
				performanceLogger.info(sb.toString());
			} else if (performanceLogger.isDebugEnabled()) {
				performanceLogger.debug(sb.toString());
			}
		}
	}
}
