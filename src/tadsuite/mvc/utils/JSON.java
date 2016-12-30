package tadsuite.mvc.utils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class JSON {
	
	//private LinkedHashMap<String, Object> map;
	//private ArrayList<Object> list;
	//private Object obj;
	private Object value;

	public static JSON parse(String jsonString) {
		JSON json= new JSON();
		LinkedHashMap<String, Object> map=new LinkedHashMap<>();
		//string2Object(jsonString);
		return json;
	}
	
	private Object string2Object(String jsonString) {
		String str=jsonString.trim();
		if (str.startsWith("{")) {
			
		} else if (str.startsWith("[")) {
			
		} else {
			
		}
		return null;
	}
	
	public String toString() {
		StringBuilder buffer=new StringBuilder();
		object2String(value, buffer);
		return buffer.toString();
	}
	
	
	private static boolean object2String(Object object, StringBuilder buffer) {
		String type=object!=null ? object.getClass().getSimpleName() : "null";
		if (type.endsWith("Map")) {
			Map map=(Map) object;
			int i=0;
			buffer.append("{");
			for (Object key : map.keySet()) {
				buffer.append(i>0 ? ", " : "").append("\"").append(escapeJsonString(key)).append("\" : ");
				object2String(((Map) object).get(key), buffer);
				i++;
			}
			buffer.append("}");
			return true;
		} else if (type.endsWith("List")) {
			List list=(List) object;
			buffer.append("[");
			for (int i=0; i<((List)object).size(); i++) {
				buffer.append(i>0 ? ", " : "");
				object2String(list.get(i), buffer);
			}
			buffer.append("]");
			return true;
		} else if (type.equals("Integer") || type.equals("Long") || type.equals("Float") || type.equals("Double") || type.equals("Decimal")) {
			buffer.append(type.equals("null") ? "\"\"" : object);
			return false;
		} else if (type.equals("Boolean")) {
			buffer.append((Boolean) object ? "true" : "false");
			return false;
		} else if (type.endsWith("Date") || type.endsWith("Timestamp")) {
			buffer.append("\"").append(Utils.dateFormat(String.valueOf(object), "yyyy-MM-dd HH:mm", "yyyy-MM-dd HH:mm")).append("\"");
			return false;
		} else {
			buffer.append("\"").append(type.equals("null") ? "" : escapeJsonString(object)).append("\"");
			return false;
		}
	}
	
	private static String escapeJsonString(Object object) {
		return String.valueOf(object).replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\n");
	}
}
