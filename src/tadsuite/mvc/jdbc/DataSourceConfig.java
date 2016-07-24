package tadsuite.mvc.jdbc;

import java.util.LinkedHashMap;

public class DataSourceConfig {

	public String name;
	public String dbType;
	public String tablePrefix;
	public String jndiName;
	public LinkedHashMap<String, String> property;
}
