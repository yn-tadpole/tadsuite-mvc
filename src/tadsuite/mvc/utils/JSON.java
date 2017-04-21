package tadsuite.mvc.utils;

import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.alibaba.druid.support.json.JSONParser;

/**
 * 本类提供一个JSON工具类，借助了Druid的相关JSONParser类
 * @author tadpole
 *
 */
public class JSON {

	private Object json;

	private JSON(String jsonString) {
		try {
			JSONParser parser=new JSONParser(jsonString);
			json=parser.parse();
		} catch (Exception e) {
			json=new LinkedHashMap<String, Object>();
		}
	}

	private JSON(Object json) {
		this.json=json;
	}

	public static JSON parse(String jsonString) {
		JSON json= new JSON(jsonString);
		return json;
	}
	
	public JSON json(String... keys) {
		try {
			Object obj=json;
			for (String key : keys) {
				obj=((Map) obj).get(key);
			}
			return new JSON(obj);
		} catch (Exception e) {
			return null;
		}
	}

	public List list() {
		return (List) json;
	}
	
	public List list(String... keys) {
		try {
			Object obj=json;
			for (String key : keys) {
				obj=((Map) obj).get(key);
			}
			return (List) obj;
		} catch (Exception e) {
			return null;
		}
	}
	
	public Map map() {
		return (Map) json;
	}
	
	public Map map(String... keys) {
		try {
			Object obj=json;
			for (String key : keys) {
				obj=((Map) obj).get(key);
			}
			return (Map) obj;
		} catch (Exception e) {
			return null;
		}
	}
	
	public String str(String... keys) {
		try {
			Object obj=json;
			for (String key : keys) {
				obj=((Map) obj).get(key);
			}
			return obj!=null ? String.valueOf(obj) : "";
		} catch (Exception e) {
			return "";
		}
	}
	
	public Date date(String... keys) {
		try {
			Object obj=json;
			for (String key : keys) {
				obj=((Map) obj).get(key);
			}
			return Utils.dateParse((String)obj, "yyyy-MM-dd HH:mm");
		} catch (Exception e) {
			return null;
		}
	}
	
	public boolean bool(String... keys) {
		try {
			Object obj=json;
			for (String key : keys) {
				obj=((Map) obj).get(key);
			}
			return obj!=null ? (boolean) obj : false;
		} catch (Exception e) {
			return false;
		}
	}

	public <T> T number(Class<T> requiredType, String... keys) {
		return number(requiredType, 0, keys);
	}

	@SuppressWarnings("unchecked")
	public <T> T number(Class<T> requiredType, int defaultValue, String... keys) {
		try {
			Object obj=json;
			try {
				for (String key : keys) {
					obj=((Map) obj).get(key);
				}
			} catch (Exception e) {
				return (T)(Object) defaultValue;
			}
			if (obj==null) {
				return (T)(Object) defaultValue;
			}
			String type=obj.getClass().getSimpleName();
			if (Integer.class==requiredType) {
				return (T)(type.equals("Integer") ? obj : (Object)Utils.parseInt(String.valueOf(obj), defaultValue));

			} else if (Long.class==requiredType) {
				return (T)(type.equals("Long") ? obj : (Object)Utils.parseLong(String.valueOf(obj), defaultValue));

			} else if (Float.class==requiredType) {
				return (T)(type.equals("Float") ? obj : (Object)Utils.parseFloat(String.valueOf(obj), defaultValue));

			} else if (Double.class==requiredType) {
				return (T)(type.equals("Double") ? obj : (Object)Utils.parseDouble(String.valueOf(obj), defaultValue));

			} else {
				return (T) obj;
			}
		} catch (Exception e) {
			return (T)(Object)defaultValue;
		}
	}
	
	public String toString() {
		return stringfy();
	}
	
	public String stringfy() {
		return stringfy(json);
	}

	public static String stringfy(Object value) {
		StringBuilder buffer=new StringBuilder();
		stringfyItem(value, buffer);
		return buffer.toString();
	}

	public static String stringfy(List list) {
		StringBuilder buffer=new StringBuilder();
		stringfyItem(list, buffer);
		return buffer.toString();
	}

	public static String stringfy(Map map) {
		StringBuilder buffer=new StringBuilder();
		stringfyItem(map, buffer);
		return buffer.toString();
	}
	
	private static boolean stringfyItem(Object object, StringBuilder buffer) {
		String type=object!=null ? object.getClass().getSimpleName() : "null";
		if (type.endsWith("Map")) {
			Map map=(Map) object;
			int i=0;
			buffer.append("{");
			for (Object key : map.keySet()) {
				buffer.append(i>0 ? ", " : "").append("\"").append(escapeJsonString(key)).append("\" : ");
				stringfyItem(((Map) object).get(key), buffer);
				i++;
			}
			buffer.append("}");
			return true;
		} else if (type.endsWith("List")) {
			List list=(List) object;
			buffer.append("[");
			for (int i=0; i<((List)object).size(); i++) {
				buffer.append(i>0 ? ", " : "");
				stringfyItem(list.get(i), buffer);
			}
			buffer.append("]");
			return true;
		} else if (type.equals("Integer") || type.equals("Long") || type.equals("Float") || type.equals("Double") || type.equals("Decimal")) {
			buffer.append(object);
			return false;
		} else if (type.equals("Boolean")) {
			buffer.append((Boolean) object ? "true" : "false");
			return false;
		} else if (type.endsWith("Date") || type.endsWith("Timestamp")) {
			buffer.append("\"").append(escapeJsonString(Utils.dateFormat(String.valueOf(object), "yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd HH:mm:ss"))).append("\"");
			return false;
		} else {
			buffer.append("\"").append(type.equals("null") ? "null" : escapeJsonString(object)).append("\"");
			return false;
		}
	}
	
	private static String escapeJsonString(Object object) {
		return String.valueOf(object).replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\n");
	}
}
