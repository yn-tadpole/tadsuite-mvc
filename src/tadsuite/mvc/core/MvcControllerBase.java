package tadsuite.mvc.core;

import java.util.LinkedHashMap;

import tadsuite.mvc.Application;
import tadsuite.mvc.auth.AuthUserState;
import tadsuite.mvc.auth.Authentication;
import tadsuite.mvc.jdbc.Jdbc;
import tadsuite.mvc.utils.Constants;
import tadsuite.mvc.utils.Utils;
import tadsuite.mvc.utils.Utils.FORMAT;


/**--MvcControllerBase -------------------------------------------------------------<br>
 *----使用tadsuite解决方案的应用程序中，凡是需要从客户端通过URL访问的类须继承此类或此类的子类。<br>
 *----继承本类的类可重写init()方法进行初始化操作，但必须在新方法的第一行调用super.init()，否则执行时会抛出异常。<br>
 * ----继承本类的类可重写controller()方法实现业务逻辑，不强制调用super.controller()。<br>
 * ----继承本类的类可重写cleanup()方法进行清理操作，但必须在新方法的最后一行调用super.cleanup()，否则执行时会抛出异常。<br>
 * ----该类的execute()方法将会被系统自动执行，execute()方法会顺序调用init(); controller(); cleanup();<br>
 * <br>
 * ----该类中可以调用setMvcView或endExecuting方法来定义执行结果，详细说明见相关方法。
 * ----如果没有调用上述方法，则execute访问默认值RESULT_SUCCESS，系统将加载该类的默认模板（URL相匹配）<br>
 * **/
public class MvcControllerBase extends MvcBase {
	public static final String RESULT_SUCCESS=null; //不返回任何值，因为将自动加载默认模板
	public static final String RESULT_TEXT="TEXT";
	public static final String RESULT_SCRIPT="SCRIPT";
	public static final String RESULT_ERROR="ERROR";
	public static final String RESULT_CSRF="CSRF";
	public static final String RESULT_XML="XML";
	public static final String RESULT_JSON="JSON";
	public static final String RESULT_INFO="INFO";
	public static final String RESULT_LOGIN="LOGIN";
	//因为已经进入异步调用，不再支持此项。public static final String RESULT_BYPASS="BYPASS";
	public static final String RESULT_END="END";

	protected String template=RESULT_SUCCESS;  /**可以给template指定一个值，让它加载一个自定义的模板，如果其值以“/”开头，将直接定位，如果不是“/”开头则先找预定义的ResultMap，找不到则当做一个相对路径来计算**/
	
	protected LinkedHashMap<String, Object> rootMap;
	protected LinkedHashMap<String, String> finalMap; //可以对模板的渲染结果进行替换（键》值）
	protected ClassMappingResult mappingResult;
	
//	public String systemName, systemTitle, defaultLocale, defaultDataSourceName, defaultAuthClientAppId, className, methodName, appPath, basePath, rewriteURL, contextPath, serverPath, requestURI;
	
	/**Performance Log should been ignored when file downloads and etc.*/
	public boolean ignorePerformanceStatics=false, allowOutputFormat=false;
	public MvcRequest request;
	public Jdbc jdbc; //default data source
	public AuthUserState auth=null;
	
	private boolean controllerInited=false, controllerCleaned=false, controllerExecuting=false, resultSetted=false, bindSystemVariables=true;
	
	/**--Controller的初始化---------------------------------------------------------------------------------------------------**
	 * 此方法由startAction()在执行controller()前调用，可用于完成动作执行前的初始化准备<br />
	 * 例如：建立数据库连接，用户身份验证<br />
	 * <strong>注意：如果子类中需要覆盖此方法，务必在新方法的第一行执行super.init()</strong>
	 * **/
	protected void init(){
		//如果已经初始化，则忽略
		if (this.controllerInited) {
			return;
		}
		
		//要放置在这里，而不放置在startAction中，是为了让子类可以通过重写而实现在登录页显示更多内容
		Authentication.init(this);
		request.setAttribute(Constants.AUTH_ININTED_TIME, System.currentTimeMillis());
		
		this.controllerInited=true;//此行是必须的
	}
	
	
	/**--Controller的默认方法---------------------------------------------------------------------------------------------------<br />
	 * 子类一般通过覆盖此方法来实现不同的业务逻辑调用*/
	public void execute() {
		//blank
	}
	
	
	/**--Controller的清理操作---------------------------------------------------------------------------------------------------**
	 * 此方法由finishAction()调用，可用于完成动作执行后的清理工作<br />
	 * 例如：关闭数据库连接，释放相关对象等<br />
	 * <strong>注意：系统会保证此方法绝对会被调用（即使产生了异常），<br />      如果子类中需要覆盖此方法，务必在新方法的最后一行执行super.cleanup()</strong>
	 * **/
	protected void cleanup(){
		if (this.controllerCleaned || !this.controllerInited){
			this.controllerCleaned=true;//此行是必须的
			return;
		}
		this.controllerCleaned=true;//此行是必须的
	}
	
	/**--此方法为Controller的主函数--------------------------------------------------------------------------------------------------**
	 * 该方法将由Router自动调用，任何子类代码中均不能调用此方法，否则会抛出异常。<br />
	 * 该函数将自动调用init(); 。<br />
	 * **/
	
	
	public final void startAction(MvcRequest mvcRequest) {
		if (this.controllerExecuting) {
			throw new RuntimeException("Action are running. Forbidden for calling 'startAction' recirsivily.");
		}
		this.controllerExecuting=true;
		this.request=mvcRequest;
		this.rootMap=mvcRequest.getRootMap();
		this.finalMap=mvcRequest.getFinalMap();
		this.mappingResult=mvcRequest.getClassMappingResult();

		if (Application.getDefaultDataSourceName().length()>0) {
			jdbc=new Jdbc(Application.getDefaultDataSourceName());
		}

		init(); //这里不能加this.，因为就是要调用一个最新的方法
		if (!this.controllerInited) {
			//上一句已经执行init()，而this.bControllerInited却是false，则说明某类继承了此类、覆盖了本类的init()方法，而没有通过super.init()来执行本类的init()，这样会破坏程序的逻辑，所以要抛出异常
			throw new RuntimeException("Method 'init()' has been override, but didn't been called. your must call 'super.init()' and check the return value.");
		}
		
	}
	/**--此方法为Controller的主函数--------------------------------------------------------------------------------------------------**
	 * 该方法将由Router自动调用，任何子类代码中均不能调用此方法，否则会抛出异常。<br />
	 * 该函数将自动调用cleanup();函数。<br />
	 * @throws SecurityException 
	 * @throws NoSuchMethodException 
	 * **/
	public final String finishAction(LinkedHashMap<String, Object> rootMap)  {
		try {
			jdbc.close();
			
			if (allowOutputFormat  || request.getRemoteAddr().equals(request.getLocalAddr())) {//只在服务器指定允许时，或者在服务器本机，才可从客户端指定输出格式，以提高安全性
				if ("XML".equals(request.getParameter("outputFormat"))) {
					bindSystemVariables="Y".equals(request.getParameter("bindSysVar"));
					template=RESULT_XML;
				} else if ("JSON".equals(request.getParameter("outputFormat"))) {
					bindSystemVariables="Y".equals(request.getParameter("bindSysVar"));
					template=RESULT_JSON;
				}
			}
			if (bindSystemVariables) {//根据需要添加系统信息
				String serverPath=request.getServerPath();
				String contextPath=request.getContextPath();
				String requestURI=request.getRequestURI();
				String url=request.getFullURL();
				String url_id="U"+Utils.md5(Utils.uuid());
				String namespace=request.getParameter("namespace").length()>0 ? request.getParameter("namespace") : url_id;			
				rootMap.put("url", url);
				rootMap.put("request_uri", requestURI);
				rootMap.put("server_path", serverPath);
				rootMap.put("context_path", contextPath);
				rootMap.put("system_name", Application.getSystemName());
				rootMap.put("system_title", Application.getSystemTitle());
				rootMap.put("debugMode", Application.isDebugMode());
				rootMap.put("url_id", url_id);
				rootMap.put("namespace", namespace);
				rootMap.put("super_namespace", namespace.indexOf(".")!=-1 ? namespace.substring(0, namespace.lastIndexOf("."))  : namespace);
				rootMap.put("ticks", Utils.now().getTime());
				
				rootMap.put("localeMap", Application.getLocaleMap());
				rootMap.put("locale", Application.getLocaleMap().containsKey(request.getCurrentLocale()) ? Application.getLocaleMap().get(request.getCurrentLocale()) : new LinkedHashMap<String, String>());
				rootMap.put("localeName", request.getCurrentLocale());
				
			} else {
				if (rootMap.containsKey("auth")) {
					rootMap.remove("auth");//自动去除用户登录的身份信息。
				} 
				if (rootMap.containsKey("auth_path") && !rootMap.containsKey("auth_type")) {
					rootMap.remove("auth_path");
				}
				rootMap.remove("requestTokenName");
				rootMap.remove("requestSeedName");
				rootMap.remove("sessionToken");
			}
			cleanup();//这里不能加this.，因为就是要调用一个最新的方法
			if (!this.controllerCleaned) {
				//上一句已经执行cleanup()，而bCleaned却是false，则说明某类继承了此类、覆盖了本类的cleanup()方法，则没有通过super.cleanup()来执行本类的cleanup()，这样会破坏程序的逻辑，所以要抛出异常
				throw new RuntimeException("Method 'cleanup()' has been override, but didn't been called. your must call 'super.cleanup()'");
			}
		} catch (Exception e) {
		} finally {
			if (!this.controllerCleaned) {
				cleanup(); //如果在执行上面的cleanup();之前就抛出异常，则不会执行cleanup()，需要在此补充
				this.cleanup(); //稳妥起见，再次执行，以防子类中覆盖，造成本类的cleanup()方法未调用。
			}
		}
		return template; //如果没有调用过setMvcView方法，此处返回的是null，将会加载默认模板
	}
	
	public final void cacheJDBC(Jdbc jdbc) {
		//jdbcPool.add(jdbc);
	}
	
	/**此方法设置一个返回结果
	 * 只应在CotrollerBase的子类方法中<strong>一次性</strong>调用此方法，调用后应结束执行过程。
	 * 该方法只设置返回结果，并不中断执行过程。如果后继代码再次调用此方法，将抛出异常。
	 * @param String result 结果代码（可使用本类的相关常量也可以是一个模板路径，模板路径可以是绝对或相对，但是相对路径中不允许使用../）
	 */
	public final boolean setMvcView(String template) {
		if (template.equals(RESULT_JSON) || template.equals(RESULT_XML) || template.equals(RESULT_TEXT) || template.equals(RESULT_SCRIPT)) {
			return setMvcView(template, false);
		} else {
			return setMvcView(template, true);
		}
	}
	/**此方法设置一个返回结果，并发送一条信息
	 * 发送的信息存储在modal中，名称为：result_message
	 * 只应在CotrollerBase的子类方法中<strong>一次性</strong>调用此方法，调用后应结束执行过程。
	 * 该方法只设置返回结果，并不中断执行过程。如果后继代码再次调用此方法，将抛出异常。
	 * @param String result  结果代码（可使用本类的相关常量也可以是一个模板路径，模板路径可以是绝对或相对，但是相对路径中不允许使用../）
	 * @param String result_message  要发送的信息
	 */
	public final boolean setMvcView(String template, String message) {
		if (!setMvcView(template)) {
			return false;
		}
		rootMap.put("result_message", message);
		return true;
	}
	/**此方法设置一个返回结果
	 * 只应在CotrollerBase的子类方法中<strong>一次性</strong>调用此方法，调用后应结束执行过程。
	 * 该方法只设置返回结果，并不中断执行过程。如果后继代码再次调用此方法，将抛出异常。
	 * @param String template 结果代码（可使用本类的相关常量也可以是一个模板路径，模板路径可以是绝对或相对，但是相对路径中不允许使用../）
	 * @param boolean bBindSystemVariables 是否在返回结果中包含系统数据（即namespace、request_uri等，一般只在返回结果是JSON时需要为false以使返回结果简洁化）
	 */
	private final boolean setMvcView(String template, boolean bBindSystemVariables) {
		if (this.resultSetted) {
			return false; //throw new RuntimeException("Method 'setMvcView()' can only been called once.");
		}
		this.bindSystemVariables=bBindSystemVariables;
		this.template=template;
		this.resultSetted=true;
		return true;
		
	}
	/**此静态方法设置一个返回结果、发送一条信息，并中断执行过程。
	 * 不能在cleanup()方法中使用此方法。
	 * 一般情况下，在MvcControllerBase及其子类中使用setMvcView(String result, String result_message)来返回结果，并正常结束执行过程，仅在业务逻辑类中使用此方法来中断执行过程。（例如：权限不足，未登录系统等）
	 * 该方法抛出一个EndExecutingExcpetion，抛出后由execute()方法处理。execute()结束init()或controller()的执行，进入cleanup()过程，将抛出的代码和信息使用setMvcView()设置后，结束执行过程
	 * 发送的信息存储在modal中，名称为：result_message
	 * @param String result  结果代码（可使用本类的相关常量也可以是一个模板路径，模板路径可以是绝对或相对，但是相对路径中不允许使用../）
	 * @param String result_message  要发送的信息
	 */
	public final static void endExecuting() throws ExecuteEndExcpetion {
		//---throw new EndExecutingExcpetion("", "");这是一个重大BUG，这样会导致系统加载默认模板继续模板处理过程。
		throw new ExecuteEndExcpetion(RESULT_END, null);
	}
	
	public final static void endExecuting(String message) throws ExecuteEndExcpetion {
		throw new ExecuteEndExcpetion(RESULT_END, message);
	}
	
	public final static void endExecuting(String template, String message) throws ExecuteEndExcpetion {
		throw new ExecuteEndExcpetion(template, message);
	}
	
	public String localeText(String title) {
		return request.readLocaleText(title);
	}

	public String readInput(String index) {
		return request.readInput(index);
	}
	
	public String readInput(String index, FORMAT format) {
		return request.readInput(index, format);
	}
	
	public String readInput(String index, int maxLength) {
		return request.readInput(index, maxLength);
	}
	
	public String readInput(String index, int maxLength, String defaultValue) {
		return request.readInput(index, maxLength, defaultValue);
	}
	
	public String[] readInputArray(String index) {
		return request.readInputArray(index);
	}
	
	public String readId(String index) {
		return request.readId(index);
	}
	
	public String readLetter(String index) {
		return request.readLetter(index);
	}
	
	public int readInt(String index, int defaultValue) {
		return request.readInt(index, defaultValue);
	}

	public long readLong(String index, long defaultValue) {
		return request.readLong(index, defaultValue);
	}

	public float readFloat(String index, float defaultValue) {
		return request.readFloat(index, defaultValue);
	}

	public double readDouble(String index, double defaultValue) {
		return request.readDouble(index, defaultValue);
	}
}
