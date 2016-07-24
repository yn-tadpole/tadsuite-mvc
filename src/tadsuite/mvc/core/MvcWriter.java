package tadsuite.mvc.core;

import java.io.PrintWriter;
import java.util.Map;

public class MvcWriter extends PrintWriter {
	
	private Map<String, String> finalMap;

	public MvcWriter(PrintWriter out, Map<String, String> map) {
		super(out);
		finalMap=map;
	}
	
	/*不能重复覆盖此方法，因为经经测试调用write(String str)方法后还会调用此方法。 
	@Override
	public void write(String str, int off, int len) {		
		super.write(str, off, len);
	}*/
	
	@Override
	public void write(String str) {
		//System.out.println(">==============write(String str)>>>>>>>>>"+str);
		super.write(processFinalMap(str));
	}
	
	/*不能重复覆盖此方法，因为经经测试调用write(char[] buf)方法后还会调用此方法。 
	@Override
	public void write(char[] buf, int off, int len) {
		System.out.println(">==============write(char[] buf, int off, int len)>>>>>>>>>"+String.valueOf(buf));
		super.write(buf, off, len);
	}*/

	@Override
	public void write(char[] buf) {
		//System.out.println(">==============write(char[] buf)>>>>>>>>>"+String.valueOf(buf));
		super.write(processFinalMap(String.valueOf(buf)));
	}

	private String processFinalMap(String str) {
		if (finalMap!=null && finalMap.size()>0) {
			for (String key : finalMap.keySet()) {
				str=str.replace(key, finalMap.get(key));
			}
		}
		return str;
	}
}
