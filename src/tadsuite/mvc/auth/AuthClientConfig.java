package tadsuite.mvc.auth;

import java.util.LinkedHashMap;

public class AuthClientConfig {
	
	public String authAppId="";
	public String authType;
	
	public LinkedHashMap<String, String> property;
	
	public String get(String title, String defaultValue) {
		return property.containsKey(title) ? property.get(title).length()>0 ? property.get(title) : defaultValue : defaultValue;
	}
	
	public String get(String title) {
		return property.containsKey(title) ? property.get(title).length()>0 ? property.get(title) : "" : "";
	}
}
