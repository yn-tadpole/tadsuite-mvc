package tadsuite.mvc;

import java.io.IOException;
import javax.servlet.AsyncContext;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.annotation.WebFilter;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.logging.log4j.ThreadContext;

import tadsuite.mvc.core.ClassMapper;
import tadsuite.mvc.core.ClassMappingResult;
import tadsuite.mvc.core.MvcHttpRequest;
import tadsuite.mvc.core.MvcRequest;
import tadsuite.mvc.core.MvcRouter;
import tadsuite.mvc.logging.LogFactory;
import tadsuite.mvc.logging.Logger;
import tadsuite.mvc.security.DenyExcpetion;
import tadsuite.mvc.security.WebSecurityFirewall;
import tadsuite.mvc.utils.Constants;
import tadsuite.mvc.utils.Utils;


@WebFilter(filterName="TadsuiteDispatcherFilter", urlPatterns="/*", asyncSupported=true)
public class DispatcherFilter implements Filter {

	public void init(FilterConfig fConfig) throws ServletException {
		//do nothing
	}
	
	public void destroy() {
		//do nothing
	}
	
	@Override
	public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain chain) throws IOException, ServletException {
		HttpServletRequest request = (HttpServletRequest) servletRequest;
		HttpServletResponse response = (HttpServletResponse) servletResponse;
		MvcRequest mvcRequest=new MvcHttpRequest(request, response);
		
        String url=mvcRequest.getURL();
		
		if (url==null || url.equals("")) {
			try {
				response.sendRedirect(request.getContextPath()+"/");
			} catch (IOException e) {
				e.printStackTrace();
			}
			return;
			
		} else if (Application.isForbiddenURL(url)) {
			Logger rootLogger=LogFactory.getRootLogger();
			StringBuffer sb=new StringBuffer()
					.append("** ").append(Application.getSystemName()).append(" - ").append(mvcRequest.getRemoteAddr()).append(", Access Forbidden URL: ").append(url).append("\n")
					.append(Constants.DASH_LINE);
			rootLogger.warn(sb.toString());
			response.sendError(404);
			return;
			
		}
		
		request.setCharacterEncoding(Constants.ENCODING);
		
		//WebSecurityFirewall在isResourceURL后，资源URL不受保护
		try {
			WebSecurityFirewall.check(mvcRequest);
		} catch (DenyExcpetion e) {
			Logger rootLogger=LogFactory.getRootLogger();
			StringBuffer sb=new StringBuffer()
					.append("** ").append(Application.getSystemName()).append(" - ").append(mvcRequest.getRemoteAddr()).append(", Web Security Firewall Deny URL: ").append(url).append(" -- ").append(e.getMessage()).append("\n")
					.append(Constants.DASH_LINE);
			rootLogger.warn(sb.toString());
			response.sendError(404);
			return;
		}
		
		if (Application.isResourceURL(url)) {
			chain.doFilter(request, response);
			return;
		}
		
		ClassMappingResult mappingResult=ClassMapper.parse(url);
		if (mappingResult==null) {
			String fileName=url.substring(url.lastIndexOf("/")+1);
			if (fileName.length()>0 && fileName.substring(0, 1).equals(fileName.substring(0,1).toLowerCase()) && fileName.indexOf(".")==-1 && fileName.indexOf("_")==-1) {
				response.sendRedirect(request.getContextPath()+url+"/");
				return;
			}
			response.sendError(404);
			return;
		}
		
        //在子线程中执行业务调用，并由其负责输出响应，主线程退出
        AsyncContext ctx = servletRequest.startAsync();
        ctx.setTimeout(0); //暂不限制超时
        new Thread(new MvcExecutor(ctx, mappingResult)).start();
	}
	
	
	//内部类，进行异步执行//////////////////////////////////////////////////////
	private class MvcExecutor implements Runnable {
		private AsyncContext context;
		private HttpServletRequest request;
		private HttpServletResponse response;
		private ClassMappingResult mappingResult;
		
	    public MvcExecutor(AsyncContext ctx, ClassMappingResult mappingResult){
	    	this.context=ctx;
	    	request=(HttpServletRequest) ctx.getRequest();
	    	response=(HttpServletResponse) ctx.getResponse();
	    	this.mappingResult=mappingResult;
	    }

	    public void run() {
	    	long startTime= System.currentTimeMillis();
			MvcRequest mvcRequest=new MvcHttpRequest(request, response);
	    	mvcRequest.setClassMappingResult(mappingResult);
	    	String ip=mvcRequest.getRemoteAddr();
	    	String fullURL=mvcRequest.getFullURL();
	    	
			ThreadContext.push(Utils.uuid()); // Add the fishtag;
			try {
				request.setAttribute(Constants.START_TIME, startTime);
				request.setAttribute(Constants.CONTEXT_PATH, request.getContextPath());
				response.setCharacterEncoding(Constants.ENCODING);
				if (response.getContentType()==null || response.getContentType().equals("")) {
					response.setContentType("text/html");
				}
				
				MvcRouter.processRequest(mvcRequest);
				
				boolean ignoreLogging=request.getAttribute(Constants.INGNORE_STATISTIC)!=null ? (boolean)request.getAttribute(Constants.INGNORE_STATISTIC) : false;
				if (!ignoreLogging) {
					long authInitTime= request.getAttribute(Constants.AUTH_ININTED_TIME)!=null ? (long) request.getAttribute(Constants.AUTH_ININTED_TIME) : 0;
					long logicFinishedTime= request.getAttribute(Constants.LOGIC_FINISHED_TIME)!=null ? (long) request.getAttribute(Constants.LOGIC_FINISHED_TIME) : 0;
					long finishedTime=System.currentTimeMillis();
					
					long totalTime=finishedTime-startTime;
					long initTime=authInitTime==0 ? -1 : authInitTime-startTime;
					long logicTime=logicFinishedTime==0 ? -1 : authInitTime==0 ? logicFinishedTime-startTime : logicFinishedTime-authInitTime;
					long templateTime=logicFinishedTime==0 ? -1 : finishedTime-logicFinishedTime;
					
					Logger performanceLogger=LogFactory.getLogger(Constants.LOGGER_NAME_PERFORMANCE);
					ClassMappingResult mapping=mvcRequest.getClassMappingResult();
					StringBuffer sb=new StringBuffer()
							.append(Application.getSystemName()).append(" :").append(totalTime).append("ms - ")
							.append(request.getMethod().toString().toLowerCase()).append(" - ").append(fullURL)
							.append(" from ").append(ip)
							.append(" mapping to: ").append(mapping!=null ? mapping.clazz.getName() : "null")
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
				
			} catch (Exception e) {
				//如果出现异常，说明Request异步处理超时，或者中间件级的错误
				
				Logger errorLogger=LogFactory.getLogger(Constants.LOGGER_NAME_ERROR);
				errorLogger.catching(e);
				long totalTime=System.currentTimeMillis()-startTime;
				StringBuffer sb=new StringBuffer()
						.append("** ").append(Application.getSystemName()).append(":").append(totalTime).append("ms - ").append(ip).append(", Error for URL: ").append(fullURL).append("\n")
						.append(Constants.DASH_LINE).append("\n");
				errorLogger.warn(sb.toString());
				try {
					response.sendError(500);
				} catch (IOException e1) {
					e1.printStackTrace();
				}
				
			} finally {
				ThreadContext.pop();
			}
			context.complete();
		}
	}
}


