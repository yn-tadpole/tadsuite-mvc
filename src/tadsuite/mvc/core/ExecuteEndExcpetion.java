package tadsuite.mvc.core;

public class ExecuteEndExcpetion  extends RuntimeException {
	private String code, message;

	public ExecuteEndExcpetion(String code, String message) {
		this.code=code;
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
	
	public String getCode() {
		return this.code;
	}
}
