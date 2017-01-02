package tadsuite.mvc;

import java.io.IOException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

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
import tadsuite.mvc.core.MvcExecutor;
import tadsuite.mvc.logging.LogFactory;
import tadsuite.mvc.logging.Logger;
import tadsuite.mvc.security.DenyExcpetion;
import tadsuite.mvc.security.WebSecurityFirewall;
import tadsuite.mvc.utils.Constants;
import tadsuite.mvc.utils.Utils;


@WebFilter(filterName="TadsuiteDispatcherFilter", urlPatterns="/*", asyncSupported=true)
public class DispatcherFilter implements Filter {
	
	private ThreadPoolExecutor executor=null;
	
	private final int corePoolSize=10, maximumPoolSize=200, keepAliveTimeSecond=3, queueSize=10;

	public void init(FilterConfig fConfig) throws ServletException {
		//do nothing
	}
	
	public void destroy() {
		if (executor!=null) {
			executor.shutdown();
		}
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
        //new Thread(new MvcExecutor(ctx, mappingResult)).start();
        if (executor==null) {
        	executor=new ThreadPoolExecutor(corePoolSize, maximumPoolSize, keepAliveTimeSecond, TimeUnit.SECONDS, new ArrayBlockingQueue<Runnable>(queueSize), new ThreadPoolExecutor.DiscardOldestPolicy());
        }
        executor.execute(new ThreadExecutor(ctx, mappingResult));
	}
	
	
	//内部类，进行异步执行//////////////////////////////////////////////////////
	private class ThreadExecutor implements Runnable {
		private AsyncContext context;
		private MvcRequest mvcRequest;
		private HttpServletRequest request;
		private HttpServletResponse response;
		
	    public ThreadExecutor(AsyncContext ctx, ClassMappingResult mappingResult){
	    	context=ctx;
	    	request=(HttpServletRequest) ctx.getRequest();
	    	response=(HttpServletResponse) ctx.getResponse();
	    	
	    	mvcRequest=new MvcHttpRequest(request, response);
	    	mvcRequest.setClassMappingResult(mappingResult);
	    }

	    public void run() {
	    	long startTime= System.currentTimeMillis();
	    	ThreadContext.push(Utils.uuid()); // Add the fishtag;
	    	boolean dispatchRequest=false;
			try {
				
				dispatchRequest=MvcExecutor.execute(mvcRequest);
		    	if (dispatchRequest) {
		    		context.dispatch();
		    		return;
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
				ThreadContext.pop();
				try {
		    		context.complete();
		    	} catch (Exception e) {
		    		//
		    	}
			}
		}
	}
}


