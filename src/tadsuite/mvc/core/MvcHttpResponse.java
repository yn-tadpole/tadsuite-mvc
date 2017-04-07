package tadsuite.mvc.core;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletResponse;

public class MvcHttpResponse implements MvcResponse {

	private HttpServletResponse httpResponse;
	
	public MvcHttpResponse(HttpServletResponse response) {
		this.httpResponse=response;
	}
	
	public void setContentType(String contentType) {
		httpResponse.setContentType(contentType);
	}
	
	public String getContentType() {
		return httpResponse.getContentType();
	}
	
	public void setCharacterEncoding(String encoding) {
		httpResponse.setCharacterEncoding(encoding);
	}

	public PrintWriter getWriter() throws IOException {
		return httpResponse.getWriter();
	}

	public void reset() {
		httpResponse.reset();
	}

	public void flushBuffer() throws IOException {
		httpResponse.flushBuffer();
	}

	public OutputStream getOutputStream() throws IOException {
		return httpResponse.getOutputStream();
	}
	
	public void addCookie(Cookie cookie) {
		httpResponse.addCookie(cookie);
	}
	
	public void sendError(int code) {
		try {
			httpResponse.sendError(code);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public void setHeader(String name, String value) {
		httpResponse.setHeader(name, value);
	}
			
	public void setDateHeader(String name, long time) {
		httpResponse.setDateHeader(name, time);
	}
	
	public void sendRedirect(String url) {
		try {
			httpResponse.sendRedirect(url);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
}
