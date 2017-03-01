package tadsuite.mvc.utils;

import java.io.File;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.dom4j.Attribute;
import org.dom4j.Document;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;

import com.alibaba.druid.support.json.JSONParser;

/**
 * 本类提供一个XML工具类
 * @author tadpole
 *
 */
public class XML {

	private Object xml;

	private XML(String xmlString) {
		try {
			Document document = DocumentHelper.parseText(xmlString);
			Element root = document.getRootElement();

	        // iterate through child elements of root
	        for ( Iterator i = root.elementIterator(); i.hasNext(); ) {
	            Element element = (Element) i.next();
	            // do something
	        }

	        // iterate through child elements of root with element name "foo"
	        for ( Iterator i = root.elementIterator( "foo" ); i.hasNext(); ) {
	            Element foo = (Element) i.next();
	            // do something
	        }

	        // iterate through attributes of root 
	        for ( Iterator i = root.attributeIterator(); i.hasNext(); ) {
	            Attribute attribute = (Attribute) i.next();
	            // do something
	        }
		} catch (Exception e) {
			xml=new LinkedHashMap<String, Object>();
		}
	}

	private XML(Object xml) {
		this.xml=xml;
	}

	public static XML parse(String xmlString) {
		XML xml= new XML(xmlString);
		return xml;
	}
	
	public XML xml(String... keys) {
		try {
			Object obj=xml;
			for (String key : keys) {
				obj=((Map) obj).get(key);
			}
			return new XML(obj);
		} catch (Exception e) {
			return null;
		}
	}

	public List list() {
		return (List) xml;
	}
	
	public List list(String... keys) {
		try {
			Object obj=xml;
			for (String key : keys) {
				obj=((Map) obj).get(key);
			}
			return (List) obj;
		} catch (Exception e) {
			return null;
		}
	}
	
	public Map map() {
		return (Map) xml;
	}
	
	public Map map(String... keys) {
		try {
			Object obj=xml;
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
			Object obj=xml;
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
			Object obj=xml;
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
			Object obj=xml;
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
			Object obj=xml;
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
	
	
	public String stringfy() {
		return stringfy(xml);
	}

	public static String stringfy(Object value) {
		StringBuilder xmlBuffer=new StringBuilder("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<xml>");
		stringfyItem(value, xmlBuffer, "");
		xmlBuffer.append("</xml>");
		return xmlBuffer.toString();
	}

	public static String stringfy(List list) {
		StringBuilder xmlBuffer=new StringBuilder("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<xml>");
		stringfyItem(list, xmlBuffer, "");
		xmlBuffer.append("</xml>");
		return xmlBuffer.toString();
	}

	public static String stringfy(Map map) {
		StringBuilder xmlBuffer=new StringBuilder("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<xml>");
		stringfyItem(map, xmlBuffer, "");
		xmlBuffer.append("</xml>");
		return xmlBuffer.toString();
	}
	
	private static boolean stringfyItem(Object object, StringBuilder xmlBuffer, String prefix) {
		String type=object!=null ? object.getClass().getSimpleName() : "null";
		if (type.equals("String")) {
			xmlBuffer.append("<![CDATA[").append((String) object).append("]]>");
			return false;
		} else if (type.equals("Boolean")) {
			xmlBuffer.append((Boolean) object ? "true" : "false");
			return false;
		} else if (type.endsWith("Map")) {
			xmlBuffer.append("\n");
			Map map=(Map) object;
			for (Object key : map.keySet()) {
				boolean isValidKey=Utils.regi("^[a-zA-Z]{1}[a-zA-Z0-9_\\-]{0,}$", (String)key);
				xmlBuffer.append(prefix).append("<").append(isValidKey ? key : "key name=\""+key+"\"").append(">");
				boolean bWrapRow=stringfyItem(((Map) object).get(key), xmlBuffer, prefix+"	");
				xmlBuffer.append(bWrapRow ? prefix : "").append("</").append(isValidKey ? key : "key").append(">").append("\n");
			}
			return true;
		} else if (type.endsWith("List")) {
			xmlBuffer.append("\n");
			List list=(List) object;
			for (int i=0; i<((List)object).size(); i++) {
				xmlBuffer.append(prefix).append("<item index=\"").append(i).append("\">");
				boolean bWrapRow=stringfyItem(list.get(i), xmlBuffer, prefix+"	");
				xmlBuffer.append(bWrapRow ? prefix : "").append("</item>").append("\n");
			}
			return true;
		} else {
			xmlBuffer.append(type.equals("null") ? "" : object.toString());
			return false;
		}
	}
}
