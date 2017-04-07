package tadsuite.mvc.utils;

import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.dom4j.Attribute;
import org.dom4j.Document;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.Node;

/**
 * 本类提供一个XML工具类，适用于将小型的XML文档映射为Map、List结构
 * @author tadpole
 *
 */
public class Xml {

	private boolean docParsed=false;
	private boolean elementParsed=false;
	private  XmlElement rootElement;
	private List<XmlElement> rootList;
	private Document document;

	public Xml(String xmlString) {
		try {
			document = DocumentHelper.parseText(xmlString);
			docParsed=true;
		} catch (Exception e) {
			docParsed=false;
		}
	}

	public static Xml parse(String xmlString) {
		Xml xml= new Xml(xmlString);
		return xml;
	}
	
	public List xpathList(String xpath) {
		return docParsed ? document.selectNodes(xpath) : null;
	}

	public Node xpathNode(String xpath) {
		return docParsed ? document.selectSingleNode(xpath) : null;
	}
	
	private void checkElement() {
		if (docParsed && !elementParsed) {
			Element root = document.getRootElement();
			rootElement=parseElement(root);
			rootList=new ArrayList<>();
			rootList.add(rootElement);
		}
	}
	
	private XmlElement parseElement(Element element) {
		XmlElement xml=new XmlElement();
		
        for ( Iterator i = element.elementIterator(); i.hasNext(); ) {
            Element child = (Element) i.next();
            String name=child.getName();
           if (!xml.children.containsKey(name)) {
        	   xml.children.put(name, new ArrayList<XmlElement>());
           }
           xml.children.get(name).add(parseElement(child));
        }
        xml.value=element.getText();

        for ( Iterator i = element.attributeIterator(); i.hasNext(); ) {
            Attribute attribute = (Attribute) i.next();
            xml.attr.put(attribute.getName(), attribute.getValue());
        }
        return xml;
	}
	

	public XmlElement root() {
		checkElement();
		return rootElement;
	}

	public XmlElement element(String... keys) {
		checkElement();
		try {
			XmlElement element=rootElement;
			for (String key : keys) {
				element=element.children.get(key).get(0);
			}
			return element;
		} catch (Exception e) {
			return new XmlElement();
		}
	}

	public List<XmlElement> list(String... keys) {
		checkElement();
		try {
			List<XmlElement> list = rootList;
			for (String key : keys) {
				list=list.get(0).children.get(key);
			}
			return list;
		} catch (Exception e) {
			return new ArrayList<XmlElement>();
		}
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
	
	public class XmlElement {
		public LinkedHashMap<String, String> attr=new LinkedHashMap<>();
		public LinkedHashMap<String, ArrayList<XmlElement>> children=new LinkedHashMap<>();
		public String value=null;
		
		public String attr(String key) {
			return attr.get(key);
		}
		
		public String str() {
			return value!=null ? value : "";
		}
		
		public Date date() {
			return date("yyyy-MM-dd HH:mm");
		}
		
		public Date date(String format) {
			try {
				return Utils.dateParse(value, format);
			} catch (Exception e) {
				return null;
			}
		}
		
		public boolean bool() {
			try {
				return value!=null ? !value.equals("0") && !value.equalsIgnoreCase("N") && !value.equals("false") : false;
			} catch (Exception e) {
				return false;
			}
		}

		public <T> T number(Class<T> requiredType) {
			return number(requiredType, 0);
		}

		@SuppressWarnings("unchecked")
		public <T> T number(Class<T> requiredType, int defaultValue) {
			try {
				
				if (value==null) {
					return (T)(Object) defaultValue;
				}
				if (Integer.class==requiredType) {
					return (T)(Object)Utils.parseInt(String.valueOf(value), defaultValue);

				} else if (Long.class==requiredType) {
					return (T)(Object)Utils.parseLong(String.valueOf(value), defaultValue);

				} else if (Float.class==requiredType) {
					return (T)(Object)Utils.parseFloat(String.valueOf(value), defaultValue);

				} else if (Double.class==requiredType) {
					return (T)(Object)Utils.parseDouble(String.valueOf(value), defaultValue);

				} else {
					return (T)(Object) defaultValue;
				}
			} catch (Exception e) {
				return (T)(Object)defaultValue;
			}
		}
	}
}

