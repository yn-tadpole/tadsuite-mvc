package tadsuite.mvc.logging;

import org.apache.logging.log4j.LogManager;

public class Log4j2Logger implements Logger {
	
	private org.apache.logging.log4j.Logger log4j2Logger;
	
	public Log4j2Logger() {
		log4j2Logger=LogManager.getRootLogger();
	}

	public Log4j2Logger(String name) {
		log4j2Logger=LogManager.getLogger(name);
	}
	public void fatal(String message, Object... params) {
		log4j2Logger.fatal(message, params);
	}

	public void error(String message, Object... params) {
		log4j2Logger.error(message, params);
	}

	public void warn(String message, Object... params) {
		log4j2Logger.warn(message, params);
	}

	public void info(String message, Object... params) {
		log4j2Logger.info(message, params);
	}

	public void debug(String message, Object... params) {
		log4j2Logger.debug(message, params);
	}

	public void trace(String message, Object... params) {
		log4j2Logger.trace(message, params);
	}
	
	public void catching(Throwable t) {
		log4j2Logger.catching(t);
	}
	
	public String getName() {
		return log4j2Logger.getName();
	}

	public boolean isFatalEnabled() {
		return log4j2Logger.isFatalEnabled();
	}
	
	public boolean isErrorEnabled() {
		return log4j2Logger.isErrorEnabled();
	}
	
	public boolean isWarnEnabled() {
		return log4j2Logger.isWarnEnabled();
	}
	
	public boolean isInfoEnabled() {
		return log4j2Logger.isInfoEnabled();
	}
	
	public boolean isDebugEnabled() {
		return log4j2Logger.isDebugEnabled();
	}
	
	public boolean isTraceEnabled() {
		return log4j2Logger.isTraceEnabled();
	}

}
