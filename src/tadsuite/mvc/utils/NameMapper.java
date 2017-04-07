package tadsuite.mvc.utils;

public class NameMapper {
	
	public static enum MAP_POLICY {
		NORMAL, 	//保持不变
		UPPER_CASE,		//转换为大写
		LOWER_CASE,	//转换为小写
		UNDERLINE_TO_PROPERTIES,		//下划线转换为驼峰写法,		
		UNDERLINE_TO_FORM_FIELD,		//驼峰写法转换表单元素的写法（fdXxxYyy）
		PROPERTIES_TO_UNDERLINE,		//驼峰写法转换为下划线
		PROPERTIES_TO_FORM_FIELD		//下划线转换为表单元素的写法（fdXxxYyy）
	}
	
	private MAP_POLICY mapPolicy;

	
	public NameMapper() {
		this.mapPolicy=MAP_POLICY.LOWER_CASE;
	}
	
	public NameMapper(MAP_POLICY colNameMapPolicy) {
		this.mapPolicy=colNameMapPolicy;
	}
	
	public MAP_POLICY getMapPolicy() {
		return mapPolicy;
	}

	public void setMapPolicy(MAP_POLICY mapPolicy) {
		this.mapPolicy = mapPolicy;
	}
	
	public String convertName(String columnName) {
		return convertName(columnName, mapPolicy);
	}

	public static String convertName(String key, MAP_POLICY mapPolicy) {
		int y=key.lastIndexOf(":");
		String name, suffix;
		if (y!=-1) {
			name=key.substring(0, y);
			suffix=key.substring(y);
		} else {
			name=key;
			suffix="";
		}
		switch (mapPolicy) {
		case NORMAL : return key;
		case UPPER_CASE : return name.toUpperCase()+suffix;
		case LOWER_CASE : return name.toLowerCase()+suffix;
		case UNDERLINE_TO_PROPERTIES : return convertUnderscoreNameToPropertyName(name)+suffix;
		case UNDERLINE_TO_FORM_FIELD : return convertUnderscoreNameToFormFieldName(name)+suffix;
		case PROPERTIES_TO_UNDERLINE : return convertPropertyNameToUnderscoreName(name)+suffix;
		case PROPERTIES_TO_FORM_FIELD : return convertPropertyNameToFormFieldName(name)+suffix;
		default : return name;
		}
	}

	public static String convertUnderscoreNameToPropertyName(String name) {
		StringBuilder result = new StringBuilder();
		boolean nextIsUpper = false;
		if (name != null && name.length() > 0) {
			if (name.length() > 1 && name.substring(1,2).equals("_")) {
				result.append(name.substring(0, 1).toUpperCase());
			} else {
				result.append(name.substring(0, 1).toLowerCase());
			}
			for (int i = 1; i < name.length(); i++) {
				String s = name.substring(i, i + 1);
				if (s.equals("_")) {
					nextIsUpper = true;
				} else {
					if (nextIsUpper) {
						result.append(s.toUpperCase());
						nextIsUpper = false;
					} else {
						result.append(s.toLowerCase());
					}
				}
			}
		}
		return result.toString();
	}

	public static String convertUnderscoreNameToFormFieldName(String name) {
		StringBuilder result = new StringBuilder();
		boolean nextIsUpper = false;
		if (name != null && name.length() > 0) {
			result.append("fd");
			nextIsUpper=true;
			for (int i = 0; i < name.length(); i++) {
				String s = name.substring(i, i + 1);
				if (s.equals("_")) {
					nextIsUpper = true;
				} else {
					if (nextIsUpper) {
						result.append(s.toUpperCase());
						nextIsUpper = false;
					} else {
						result.append(s.toLowerCase());
					}
				}
			}
		}
		return result.toString();
	}
	
	public static String convertPropertyNameToUnderscoreName(String name) {
		StringBuilder result = new StringBuilder();
		if (name != null && name.length() > 0) {			
			for (int i = 1; i < name.length(); i++) {
				String s = name.substring(i, i + 1);
				String lowerCase=s.toLowerCase();
				if (!s.equals(lowerCase)) {//如果与其小写转换结果不相等，只有可能是大写，不会是数字或小写
					result.append("_").append(lowerCase);
				} else {
					result.append(s);
				}
			}
		}
		return result.toString();
	}
	
	public static String convertPropertyNameToFormFieldName(String name) {
		StringBuilder result = new StringBuilder();
		if (name != null && name.length() > 0) {			
			result.append("fd").append(name.substring(0, 1).toUpperCase()).append(name.substring(1));
		}
		return result.toString();
	}
}
