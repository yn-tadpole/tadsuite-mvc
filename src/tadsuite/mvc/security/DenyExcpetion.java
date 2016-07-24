package tadsuite.mvc.security;

public class DenyExcpetion  extends RuntimeException {
	private String message;

	public DenyExcpetion(String message) {
		this.message=message;
	}
	
	@Override
	public Throwable fillInStackTrace() {
		//super.fillInStackTrace();
		return this;
	}
	
	@Override
	public String getMessage() {
		return this.message;
	}
	
}
