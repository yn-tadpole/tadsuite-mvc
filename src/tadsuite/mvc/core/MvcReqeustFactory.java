package tadsuite.mvc.core;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public abstract class MvcReqeustFactory {
	
	public static MvcRequest buildRequest(HttpServletRequest request, HttpServletResponse response) {
		return new MvcHttpRequest(request, response);
	}
}
