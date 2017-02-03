package tadsuite.mvc;

import java.io.IOException;
//import java.util.concurrent.ArrayBlockingQueue;
//import java.util.concurrent.ThreadPoolExecutor;
//import java.util.concurrent.TimeUnit;

//import javax.servlet.AsyncContext;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.annotation.WebFilter;
import javax.servlet.http.HttpServletRequest;
//import javax.servlet.http.HttpServletResponse;
import org.apache.logging.log4j.ThreadContext;

import tadsuite.mvc.core.ClassMapper;
import tadsuite.mvc.core.ClassMappingResult;
import tadsuite.mvc.core.MvcHttpRequest;
import tadsuite.mvc.core.MvcRequest;
import tadsuite.mvc.core.MvcExecutor;
import tadsuite.mvc.logging.LogFactory;
import tadsuite.mvc.logging.Logger;
import tadsuite.mvc.security.DenyExcpetion;
import tadsuite.mvc.security.WebSecurityFirewall;
import tadsuite.mvc.utils.Constants;
import tadsuite.mvc.utils.Utils;


/***
 * 2017024由于异步工作模式无法在AJP协议下正常工作（Tomcat测试无法正常响应），关闭异步工作模式
 * @author tadpole
 *
 */
@WebFilter(filterName="TadsuiteDispatcherFilter", urlPatterns="/*")//, asyncSupported=true
public class DispatcherFilter implements Filter {
	
	//private ThreadPoolExecutor executor=null;
	
	//private final int corePoolSize=100, maximumPoolSize=600, keepAliveTimeSecond=3, queueSize=100;

	public void init(FilterConfig fConfig) throws ServletException {
		//executor=new ThreadPoolExecutor(corePoolSize, maximumPoolSize, keepAliveTimeSecond, TimeUnit.SECONDS, new ArrayBlockingQueue<Runnable>(queueSize), new ThreadPoolExecutor.DiscardOldestPolicy());
	}
	
	public void destroy() {
		//if (executor!=null) {
		//	executor.shutdown();
		//}
	}
	
	@Override
	public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain chain) throws IOException, ServletException {
		//在子线程中执行业务调用，并由其负责输出响应，主线程退出
       // AsyncContext ctx = servletRequest.startAsync();
        //ctx.setTimeout(0); //暂不限制超时
        //executor.execute(new ThreadExecutor(ctx));
		
		MvcRequest mvcRequest=new MvcHttpRequest((HttpServletRequest) servletRequest, servletResponse);
		String url=mvcRequest.getURL();
		
		if (url==null || url.equals("")) {
			mvcRequest.getResponse().sendRedirect(mvcRequest.getContextPath()+"/");
			return;
			
		} else if (Application.isForbiddenURL(url)) {
			Logger rootLogger=LogFactory.getRootLogger();
			StringBuffer sb=new StringBuffer()
					.append("** ").append(Application.getSystemName()).append(" - ").append(mvcRequest.getRemoteAddr()).append(", Access Forbidden URL: ").append(url).append("\n")
					.append(Constants.DASH_LINE);
			rootLogger.warn(sb.toString());
			mvcRequest.getResponse().sendError(404);
			return;
		}
		
		mvcRequest.setCharacterEncoding(Constants.ENCODING);
		
		//WebSecurityFirewall在isResourceURL后，资源URL不受保护
		try {
			WebSecurityFirewall.check(mvcRequest);
		} catch (DenyExcpetion e) {
			Logger rootLogger=LogFactory.getRootLogger();
			StringBuffer sb=new StringBuffer()
					.append("** ").append(Application.getSystemName()).append(" - ").append(mvcRequest.getRemoteAddr()).append(", Web Security Firewall Deny URL: ").append(url).append(" -- ").append(e.getMessage()).append("\n")
					.append(Constants.DASH_LINE);
			rootLogger.warn(sb.toString());
			mvcRequest.getResponse().sendError(404);
			return;
		}
		
		if (Application.isResourceURL(url)) {
			chain.doFilter(servletRequest, servletResponse);
			return;
		}
		
		ClassMappingResult mappingResult=ClassMapper.parse(url);
		if (mappingResult==null) {
			String fileName=url.substring(url.lastIndexOf("/")+1);
			if (fileName.length()>0 && fileName.substring(0, 1).equals(fileName.substring(0,1).toLowerCase()) && fileName.indexOf(".")==-1 && fileName.indexOf("_")==-1) {
				mvcRequest.getResponse().sendRedirect(mvcRequest.getContextPath()+url+"/");
				return;
			}
			mvcRequest.getResponse().sendError(404);
			return;
		}

    	mvcRequest.setClassMappingResult(mappingResult);
    	
    	long startTime= System.currentTimeMillis();
    	boolean dispatchRequest=false;
		try {
			
			ThreadContext.push(Utils.uuid()); // Add the fishtag;
			dispatchRequest=MvcExecutor.execute(mvcRequest);
			ThreadContext.pop();
			
	    	if (dispatchRequest) {
	    		chain.doFilter(servletRequest, servletResponse);
	    	}
			
		} catch (Exception e) {
			//如果出现异常，说明Request异步处理超时，或者中间件级的错误
			Logger errorLogger=LogFactory.getLogger(Constants.LOGGER_NAME_ERROR);
			errorLogger.catching(e);
			long totalTime=System.currentTimeMillis()-startTime;
			StringBuffer sb=new StringBuffer()
					.append("****** ").append(Application.getSystemName()).append(":").append(totalTime).append("ms - ").append(mvcRequest.getRemoteAddr()).append(", Error for URL: ").append(mvcRequest.getFullURL()).append("\n")
					.append(Constants.DASH_LINE).append("\n");
			errorLogger.warn(sb.toString());
			
		} finally {
		}
	}
	
	/*
	//内部类，进行异步执行//////////////////////////////////////////////////////
	private class ThreadExecutor implements Runnable {
		private AsyncContext context;
		private MvcRequest mvcRequest;
		
	    public ThreadExecutor(AsyncContext ctx){
	    	context=ctx;
	    	mvcRequest=new MvcHttpRequest((HttpServletRequest) ctx.getRequest(), (HttpServletResponse) ctx.getResponse());
	    }

	    public void run() {
	    	
	    	long startTime= System.currentTimeMillis();
	    	boolean dispatchRequest=false;
			try {
				if (!prepare()) {
					return;
				}
				
				ThreadContext.push(Utils.uuid()); // Add the fishtag;
				dispatchRequest=MvcExecutor.execute(mvcRequest);
				ThreadContext.pop();
				
		    	if (dispatchRequest) {
		    		context.dispatch();
		    	} else {
		    		context.complete();
		    	}
				
			} catch (Exception e) {
				//如果出现异常，说明Request异步处理超时，或者中间件级的错误
				Logger errorLogger=LogFactory.getLogger(Constants.LOGGER_NAME_ERROR);
				errorLogger.catching(e);
				long totalTime=System.currentTimeMillis()-startTime;
				StringBuffer sb=new StringBuffer()
						.append("****** ").append(Application.getSystemName()).append(":").append(totalTime).append("ms - ").append(mvcRequest.getRemoteAddr()).append(", Error for URL: ").append(mvcRequest.getFullURL()).append("\n")
						.append(Constants.DASH_LINE).append("\n");
				errorLogger.warn(sb.toString());
				
			} finally {
			}
		}
	    
	    private boolean prepare() {
	    	
	    	String url=mvcRequest.getURL();
			
			if (url==null || url.equals("")) {
				mvcRequest.getResponse().sendRedirect(mvcRequest.getContextPath()+"/");
				return false;
				
			} else if (Application.isForbiddenURL(url)) {
				Logger rootLogger=LogFactory.getRootLogger();
				StringBuffer sb=new StringBuffer()
						.append("** ").append(Application.getSystemName()).append(" - ").append(mvcRequest.getRemoteAddr()).append(", Access Forbidden URL: ").append(url).append("\n")
						.append(Constants.DASH_LINE);
				rootLogger.warn(sb.toString());
				mvcRequest.getResponse().sendError(404);
				return false;
				
			}
			
			mvcRequest.setCharacterEncoding(Constants.ENCODING);
			
			//WebSecurityFirewall在isResourceURL后，资源URL不受保护
			try {
				WebSecurityFirewall.check(mvcRequest);
			} catch (DenyExcpetion e) {
				Logger rootLogger=LogFactory.getRootLogger();
				StringBuffer sb=new StringBuffer()
						.append("** ").append(Application.getSystemName()).append(" - ").append(mvcRequest.getRemoteAddr()).append(", Web Security Firewall Deny URL: ").append(url).append(" -- ").append(e.getMessage()).append("\n")
						.append(Constants.DASH_LINE);
				rootLogger.warn(sb.toString());
				mvcRequest.getResponse().sendError(404);
				return false;
			}
			
			if (Application.isResourceURL(url)) {
				context.dispatch();
				return false;
			}
			
			ClassMappingResult mappingResult=ClassMapper.parse(url);
			if (mappingResult==null) {
				String fileName=url.substring(url.lastIndexOf("/")+1);
				if (fileName.length()>0 && fileName.substring(0, 1).equals(fileName.substring(0,1).toLowerCase()) && fileName.indexOf(".")==-1 && fileName.indexOf("_")==-1) {
					mvcRequest.getResponse().sendRedirect(mvcRequest.getContextPath()+url+"/");
					return false;
				}
				mvcRequest.getResponse().sendError(404);
				return false;
			}

	    	mvcRequest.setClassMappingResult(mappingResult);
	    	return true;
	    }
	}*/
}


