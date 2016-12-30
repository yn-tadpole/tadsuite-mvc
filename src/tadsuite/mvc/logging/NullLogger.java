package tadsuite.mvc.logging;

public class NullLogger implements Logger {
	
	public NullLogger() {
	}

	@SuppressWarnings("unused")
	public NullLogger(String name) {
		//
	}
	public void fatal(String message, Object... params) {
		//log4j2Logger.fatal(message, params);
	}

	public void error(String message, Object... params) {
		//log4j2Logger.error(message, params);
	}

	public void warn(String message, Object... params) {
		//log4j2Logger.warn(message, params);
	}

	public void info(String message, Object... params) {
		//log4j2Logger.info(message, params);
	}

	public void debug(String message, Object... params) {
		//log4j2Logger.debug(message, params);
	}

	public void trace(String message, Object... params) {
		//log4j2Logger.trace(message, params);
	}
	
	public void catching(Throwable t) {
		//log4j2Logger.catching(t);
	}
	
	public String getName() {
		return ""; //return log4j2Logger.getName();
	}

	public boolean isFatalEnabled() {
		return false; //return log4j2Logger.isFatalEnabled();
	}
	
	public boolean isErrorEnabled() {
		return false; //return log4j2Logger.isErrorEnabled();
	}
	
	public boolean isWarnEnabled() {
		return false; //return log4j2Logger.isWarnEnabled();
	}
	
	public boolean isInfoEnabled() {
		return false; //return log4j2Logger.isInfoEnabled();
	}
	
	public boolean isDebugEnabled() {
		return false; //return log4j2Logger.isDebugEnabled();
	}
	
	public boolean isTraceEnabled() {
		return false; //return log4j2Logger.isTraceEnabled();
	}

}
