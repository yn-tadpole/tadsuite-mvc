package tadsuite.mvc.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;

import tadsuite.mvc.auth.AuthUserState;
import tadsuite.mvc.jdbc.Jdbc;

/**
 * 传递给该类（包括子类）各方法的参数，均认为是从客户端提供且未经验证的，必须验证其有效且SQL安全*/
public abstract class MvcServiceBase extends MvcBase {
	protected Jdbc jdbc;
	protected String message;
	protected AuthUserState auth;
	protected LinkedHashMap<String, Object> mapResult=new LinkedHashMap<String, Object>();
	protected ArrayList<LinkedHashMap<String, Object>> listResult=new ArrayList<LinkedHashMap<String, Object>>();
	protected MvcControllerBase controller;
	protected MvcRequest request;
	
	public MvcServiceBase(MvcControllerBase controller) {
		this.controller=controller;
		this.jdbc = controller.jdbc;
		this.request=controller.request;
	}
	
	public MvcServiceBase(MvcControllerBase controller, AuthUserState auth) {
		this.controller=controller;
		this.jdbc = controller.jdbc;
		this.request=controller.request;
		this.auth=auth;
	}
	
	public String getMessage() {
		return message;
	}
	
	public LinkedHashMap<String, Object> getMapResult() {
		return mapResult;
	}
	
	public ArrayList<LinkedHashMap<String, Object>> getListResult() {
		return listResult;
	}
}
