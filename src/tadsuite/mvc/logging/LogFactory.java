package tadsuite.mvc.logging;

public class LogFactory {
	
	public static Logger getLogger(String name) {
		return new Log4j2Logger(name);
	}

	public static Logger getRootLogger() {
		return new Log4j2Logger();
	}
	
	public static Logger getNullLogger() {
		return new NullLogger();
	}
}
