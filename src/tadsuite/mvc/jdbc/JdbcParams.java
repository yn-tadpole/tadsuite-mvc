package tadsuite.mvc.jdbc;

import java.math.BigDecimal;
import java.sql.Types;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

import tadsuite.mvc.core.MvcRequest;
import tadsuite.mvc.utils.NameMapper;
import tadsuite.mvc.utils.Utils;
import tadsuite.mvc.utils.NameMapper.MAP_POLICY;

public class JdbcParams extends LinkedHashMap<String, Object> {
	
	private LinkedHashMap<String, Integer> typeMap=new LinkedHashMap<String, Integer>();
	private LinkedHashMap<String, String> typeNameMap=new LinkedHashMap<String, String>();
	
	public JdbcParams() {		
	}
	
	public static JdbcParams createNew() {
		return new JdbcParams();
	}
	
	/**
	 * 按照paramsArrayString数组串，从request对象中批量绑定
	 * @param paramsArrayString 参数列表字符串，以“,”分隔可通过[xxx]指定类型
	 * @param map
	 * @return
	 */
	public JdbcParams putFromRequest(String paramsArrayString, MvcRequest request, MAP_POLICY colNameMapPolicy) {
		int optionMarkNum=0;
		int i=0, j=0;
		for (; j<paramsArrayString.length(); j++) {//
			char c=paramsArrayString.charAt(j);
			if (c=='[') {
				optionMarkNum++;
			} else if (c==']') {
				optionMarkNum--;
			}
			if (optionMarkNum==0 && c==',') {//seperator
				String key=paramsArrayString.substring(i, j).trim();
				i=j+1;
				if (key.length()>0) {
					String parameterName=NameMapper.convertName(key.substring(key.lastIndexOf("]")+1), colNameMapPolicy);
					put(key, request.readInput(parameterName));
				}
			}
		}
		if (i<paramsArrayString.length()) {
			String lastKey=paramsArrayString.substring(i).trim();
			if (lastKey.length()>0) {
				String parameterName=NameMapper.convertName(lastKey.substring(lastKey.lastIndexOf("]")+1), colNameMapPolicy);
				put(lastKey, request.readInput(parameterName));
			}
		}
		/*option中也可能出现“,”所以不能使用split
		for (String key : paramsArrayString.split(",")) {
			key=key.trim();
			if (key.length()>0) {
				String parameterName=NameMapper.convertName(key.substring(key.lastIndexOf("]")+1), colNameMapPolicy);
				put(key, request.readInput(parameterName));
			}
		}*/
		return this;
	}
	
	/**
	 * 按照paramsArrayString数组串，从Map中批量绑定数据（只会尝试绑定paramsArrayString中列出的参数）
	 * 如果Map的value为Object类型，则批量调用put(String, Object)，通过value的类型猜测参数类型\n
	 * 如果Map的value为String类型，则批量调用put(String, String)，则通过key的标注猜测参数类型
	 * @param paramsArrayString 参数列表字符串，以“,”分隔可通过[xxx]指定类型
	 * @param map
	 * @return
	 */
	public JdbcParams putFromMap(String paramsArrayString, Map<? extends String, ? extends Object> map) {
		for (String key : paramsArrayString.split(",")) {
			key=key.trim();
			if (key.length()>0) {
				put(key, map.get(key));
			}
		}
		return this;
	}

	/**
	 * 通过Map批量绑定参数（将尝试绑定Map中的全部参数）\n
	 * 如果Map的value为Object类型，则批量调用put(String, Object)，通过value的类型猜测参数类型\n
	 * 如果Map的value为String类型，则批量调用put(String, String)，则通过key的标注猜测参数类型
	 */
	@Override
	public void putAll(Map<? extends String, ? extends Object> m) {
		for (String key : m.keySet()) {
			put(key, m.get(key));
		}
	}
	
	/**
	 * 绑定值为字符串的参数，可在key中标注类型，否则按字符串处理
	 * @param key
	 * @param value
	 * @return
	 */
	public JdbcParams put(String key, String value) {
		int x=key.lastIndexOf("]");
		String type=x!=-1 ? key.substring(0, x).trim().toLowerCase()  : "[string";
		String paramName=x!=-1 ? key.substring(x+1)  : key;
		int y=type.indexOf(",");
		String pattern=y!=-1 ? type.substring(y+1) : "";
		
		if (type.startsWith("[string")) {
			if (pattern.length()>0) {//[string,x] 格式，可限制长度
				if (Utils.regi("^[0-9]{1,}$", pattern)) {//[string,x] 格式，可限制长度
					int max=Utils.parseInt(pattern, 50);
					if (value.length()>max) {
						value=value.substring(0, max);
					}
				} else {//否则按正则表达式处理
					if (!Utils.regi(paramName, value)) {
						value="";
					}
				}
			}
			putString(paramName, value);
			return this;
			
		} else if (type.startsWith("[date")) {
			String format="yyyy-MM-dd HH:mm:ss";
			if (pattern.length()>0) {
				format=pattern;
			}
			putDate(paramName, value, format);
			
		} else if (type.startsWith("[double")) {
			putNumString(paramName, value); //putDouble(param, value);
			
		} else if (type.startsWith("[float")) {
			putNumString(paramName, value); //putFloat(param, value);

		} else if (type.startsWith("[long")) {
			putLong(paramName, value);
			
		} else if (type.startsWith("[int")) {
			putInt(paramName, value);
			
		} else if (type.startsWith("[decimal")) {
			putDecimal(paramName, value);
			
		} else {
			putString(paramName, value);
			
		}
		return this;
	}
	
	  
	/**
	 * 绑定值为Object的参数，根据Object的类型判断参数类型，无法确定的按字符串处理
	 */
	@Override
	public JdbcParams put(String key, Object value) {
		if (value!=null) {
			if (value instanceof String) {
				put(key, (String)value);
				
			} else if (value instanceof Date) {
				putDate(key, (Date)value);
				
			} else if (value instanceof Double) {
				putDouble(key, (double)value);
				
			} else if (value instanceof Float) {
				putFloat(key, (float)value);
				
			} else if (value instanceof Long) {
				putLong(key, (long)value);
				
			} else if (value instanceof Integer) {
				putInt(key, (int)value);
				
			} else if (value instanceof BigDecimal) {
				putDecimal(key, (BigDecimal)value);
				
			} else {
				put(key, value.toString());
				
			}
		}
		return this;
	}

	/**
	 * 绑定参数，并指定类型及类型名称
	 * @param key
	 * @param value
	 * @param type
	 * @param typeName
	 * @return
	 */
	public JdbcParams put(String key, Object value, int type, String typeName) {
		super.put(key, value);
		typeMap.put(key, type);
		typeNameMap.put(key, typeName);
		return this;
	}
	
	public JdbcParams putString(String key, String value) {
		return put(key, value, Types.VARCHAR, "String");
	}

	public JdbcParams putString(String key, String value, int maxLength) {
		return put(key, (Object) (value==null ? "" : value.length()>maxLength ? value.substring(0, maxLength) : value), Types.VARCHAR, "String");
	}


	public JdbcParams putNumString(String key, String value) {
		value=value!=null ? value.replaceAll(",", "") : "";
		if (!Utils.isNumber(value)) {
			value="";
		}
		return put(key, value, Types.VARCHAR, "String");
	}

	public JdbcParams putLong(String key, String value) {
		try {
			return put(key, Long.parseLong(value.replaceAll(",", "")), Types.BIGINT, "Long");
		} catch (Exception e) {
			return put(key, null, Types.BIGINT, "Long");
		}
	}
	
	public JdbcParams putLong(String key, long value) {
		return put(key, value, Types.BIGINT, "Long");
	}

	public JdbcParams putInt(String key, String value) {
		try {
			return put(key, Integer.parseInt(value.replaceAll(",", "")), Types.INTEGER, "Int");
		} catch (Exception e) {
			return put(key, null, Types.INTEGER, "Int");
		}
	}

	public JdbcParams putInt(String key, int value) {
		return put(key, value, Types.INTEGER, "Int");
	}

	public JdbcParams putFloat(String key, String value) {
		try {
			return put(key, Float.parseFloat(value.replaceAll(",", "")), Types.FLOAT, "Float");
		} catch (Exception e) {
			return put(key, null, Types.FLOAT, "Float");
		}
	}
	
	public JdbcParams putFloat(String key, float value) {
		return put(key, value, Types.FLOAT, "Float");
	}

	public JdbcParams putDouble(String key, String value) {
		try {
			return put(key, Double.parseDouble(value.replaceAll(",", "")), Types.DOUBLE, "Double");
		} catch (Exception e) {
			return put(key, null, Types.DOUBLE, "Double");
		}
	}

	public JdbcParams putDouble(String key, double value) {
		return put(key, value, Types.DOUBLE, "Double");
	}
	
	public JdbcParams putDate(String key, Date value) {
		return put(key, value, Types.TIMESTAMP, "Datetime");
	}

	public JdbcParams putDate(String key, String dateString, String format) {
		try {
			Date datetime=Utils.dateParse(dateString, format);
			return put(key, datetime, Types.TIMESTAMP, "Datetime");
		} catch (Exception e) {
			return put(key, null, Types.TIMESTAMP, "Datetime");
		}
	}
	
	public JdbcParams putDecimal(String key, BigDecimal value) {
		return put(key, value, Types.DECIMAL, "Decimal");
	}

	public JdbcParams putDecimal(String key, String value) {
		try {
			return put(key, new BigDecimal(value), Types.DECIMAL, "Decimal");
		} catch (Exception e) {
			return put(key, null, Types.DECIMAL, "Decimal");
		}
	}
	
	public JdbcParams putDecimal(String key, String value, BigDecimal defaultValue) {
		try {
			return put(key, new BigDecimal(value), Types.DECIMAL, "Decimal");
		} catch (Exception e) {
			return put(key, defaultValue, Types.DECIMAL, "Decimal");
		}
	}

	public boolean hasValue(String paramName) {
		return containsKey(paramName);
	}

	public Object getValue(String paramName) {
		return get(paramName);
	}

	public int getSqlType(String paramName) {
		return typeMap.get(paramName);
	}

	public String getTypeName(String paramName) {
		return typeNameMap.get(paramName);
	}
	
}
