package tadsuite.mvc.core;

public abstract class MvcBase {
	
	public static final int SERVICE_SUCCESS=110401;
	public static final int SERVICE_ERROR=22801;
	public static final int SERVICE_INVALID_INPUT=120801;
	public static final int SERVICE_DATABASE_EXCEPTION=120101;
	
	public static final String RESULT_SUCCESS=null; //不返回任何值，因为将自动加载默认模板
	public static final String RESULT_TEXT="TEXT";
	public static final String RESULT_SCRIPT="SCRIPT";
	public static final String RESULT_ERROR="ERROR";
	public static final String RESULT_CSRF="CSRF";
	public static final String RESULT_XML="XML";
	public static final String RESULT_JSON="JSON";
	public static final String RESULT_INFO="INFO";
	public static final String RESULT_LOGIN="LOGIN";
	public static final String RESULT_END="END";
	//异步调用，不再支持此项。public static final String RESULT_BYPASS="BYPASS";
	
	
	private long timer=0;
	
	public long timer() {
		if (timer==0) {
			timer=System.currentTimeMillis();
		}
		
		long now=System.currentTimeMillis();
		long time = now-timer;
		timer=now;
		return time;
	}
	
}
