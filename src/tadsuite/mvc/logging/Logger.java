package tadsuite.mvc.logging;


public interface Logger {
	
	public void fatal(String message, Object... params);

	public void error(String message, Object... params);

	public void warn(String message, Object... params);

	public void info(String message, Object... params);

	public void debug(String message, Object... params);

	public void trace(String message, Object... params);
	
	public void catching(Throwable t);
	
	public String getName();

	public boolean isFatalEnabled();
	
	public boolean isErrorEnabled();
	
	public boolean isWarnEnabled();
	
	public boolean isInfoEnabled();
	
	public boolean isDebugEnabled();
	
	public boolean isTraceEnabled();

}
