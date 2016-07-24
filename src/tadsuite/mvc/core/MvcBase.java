package tadsuite.mvc.core;

public abstract class MvcBase {
	
	public static final int SERVICE_SUCCESS=110401;
	public static final int SERVICE_ERROR=22801;
	public static final int SERVICE_INVALID_INPUT=120801;
	public static final int SERVICE_DATABASE_EXCEPTION=120101;
	
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
