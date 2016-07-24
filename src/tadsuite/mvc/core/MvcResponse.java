package tadsuite.mvc.core;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;

import javax.servlet.http.Cookie;

public interface MvcResponse {
	
	public void addCookie(Cookie cookie);
	
	public void sendError(int code);
	
	public String getContentType();
	
	public void setContentType(String contentType);
	
	public void setCharacterEncoding(String encoding);
	
	public void setHeader(String name, String value);
	
	public void setDateHeader(String name, long time);
	
	public void sendRedirect(String url);
	
	public PrintWriter getWriter() throws IOException;
	
	public OutputStream getOutputStream() throws IOException;
	
	public void reset();
	
	public void flushBuffer() throws IOException;
}
