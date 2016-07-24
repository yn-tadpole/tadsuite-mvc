package tadsuite.mvc.jdbc;

import java.io.InputStream;
import java.math.BigDecimal;
import java.sql.Blob;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;

import javax.sql.DataSource;

import com.alibaba.druid.sql.PagerUtils;
import com.alibaba.druid.util.JdbcConstants;

import tadsuite.mvc.logging.LogFactory;
import tadsuite.mvc.logging.Logger;
import tadsuite.mvc.utils.Constants;
import tadsuite.mvc.utils.NameMapper;
import tadsuite.mvc.utils.Utils;

/**JdbcExecutor类提供一个数据库操作的工具。
  * 增加对多种数据库分页的支持，允许使用${xxx}的绑定变量(xxx为字符串)，以及查询结果的Map读取与ArrayList读取
 * 20130430 允许使用多个同名的数据库变量，在单引号“'”中的内容不会做转义识别，即允许在SQL的“'”中使用“?”、“${”等字符
 * 20140222 允许使用#{xxx}方式的绑定变量(xxx为字符串)，该方式实际为拼接SQL，应只在特殊情况下使用（例如：SQL中使用（0=#{xxx} or xxx...）如果不使用#{xxx}方式拼接，则执行计划无法被优化，xxx...部分的计算总是无法避免）
 * 						修正默认数据库类型的错误
 * 20150830 去除连接管理部分，使用第三方数据源
 * 
 * ###注意：本类有成员变量，不能作为单例的Bean使用。#########################################
 * 创建本类后如果执行了查询，应显式调用close方法。
 */

public class JdbcExecutor {

	public final static String version="v4.0.0";
	public final static String versionNote="20150830";
	public static enum DTYPE {
		STRING, NSTRING, NUMSTRING, DATE, DECIMAL, LONG, INT, FLOAT, DOUBLE, NULL
	}
	public static final String PARAMS_START="${";
	public static final String FIX_VALUE_START="#{";
	public static final String PARAMS_END="}";
	public static final String FIX_VALUE_END="}";
	
	private boolean bConnected, bRunningTransaction=false;
	private int rowTotal, pgSize, pgCurrent, pgCount;
	
	public DataSource datasource;
	public String tablePrefix="", dbType;

	public Connection conn=null;
	public Statement statement=null;
	public ResultSet result=null;
	private PreparedStatement query=null, pageCountQuery=null;

	private boolean bUsePageQuery=false, hasFixValue=false, bSQLReady=false;
	private String querySQL="";
	private long startTime=0;

	private int paramsCount=0;
	private HashMap<String, ArrayList<Integer>> paramsIndexMap=new HashMap<String, ArrayList<Integer>>();
	private HashMap<String, Object> paramsValueMap=new HashMap<String, Object>();
	private HashMap<String, DTYPE> paramsTypeMap=new HashMap<String, DTYPE>();
	private HashMap<Integer, DTYPE> bindTypeMap=new HashMap<Integer, DTYPE>();
	private HashMap<Integer, Object> bindValueMap=new HashMap<Integer, Object>();
	private HashMap<Integer, Object> bindSQLValueMap=new HashMap<Integer, Object>();
	private Logger JdbcExecutorLogger;
	
	private ArrayList<JdbcExecutor> chlidrenList=new ArrayList<>();

	public JdbcExecutor() {
		DataSourceConfig config=DataSourceManager.lookupDefaultDataSourceConfig();
		DataSource source=DataSourceManager.lookupDataSource(config);
		init(source, config.dbType, config.tablePrefix);
	}

	public JdbcExecutor(String dataSourceConfigName) {
		DataSourceConfig config=DataSourceManager.lookupDataSourceConfig(dataSourceConfigName);
		DataSource source=DataSourceManager.lookupDataSource(config);
		init(source, config.dbType, config.tablePrefix);
	}
	
	public JdbcExecutor(DataSource datasource, String dbType) {
		init(datasource, dbType, "");
	}
	
	public JdbcExecutor(DataSource datasource, String dbType, String tablePrefix) {
		init(datasource, dbType, tablePrefix);
	}

	/**
	 * 使用相同的数据源，再创建一个对象，并与当前对象建立关联（当前对象关闭时将自动关闭）
	 * 将使用不同的Connection及Statement等对象
	 */
	public JdbcExecutor clone() {
		JdbcExecutor newObject=new JdbcExecutor(datasource, dbType, tablePrefix);
		chlidrenList.add(newObject);
		return newObject;
	}
	
	/**
	 * 指定数据源，再创建一个对象，并与当前对象建立关联（当前对象关闭时将自动关闭）
	 * 将使用不同的Connection及Statement等对象
	 * @param source
	 * @param dbType
	 * @return
	 */
	public JdbcExecutor create(DataSource datasource, String dbType) {
		return create(datasource, dbType, "");
	}
	
	/**
	 * 指定数据源，再创建一个对象，并与当前对象建立关联（当前对象关闭时将自动关闭）
	 * 将使用不同的Connection及Statement等对象
	 * @param source
	 * @param dbType
	 * @return
	 */
	public JdbcExecutor create(String dataSourceConfigName) {
		DataSourceConfig config=DataSourceManager.lookupDataSourceConfig(dataSourceConfigName);
		DataSource source=DataSourceManager.lookupDataSource(config);
		return create(source, config.dbType, config.tablePrefix);
	}
	
	/**
	 * 指定数据源，再创建一个对象，并与当前对象建立关联（当前对象关闭时将自动关闭）
	 * @param tablePrefix
	 * @param source
	 * @param dbType
	 * @return
	 */
	private JdbcExecutor create(DataSource datasource, String dbType, String tablePrefix) {
		JdbcExecutor newObject=new JdbcExecutor(datasource, dbType, tablePrefix);
		chlidrenList.add(newObject);
		return newObject;
	}
	
	private void init(DataSource datasource, String dbType, String tablePrefix) {
		this.datasource=datasource;
		this.tablePrefix=tablePrefix;
		this.dbType=dbType;
		bConnected=false;
		JdbcExecutorLogger=LogFactory.getLogger(Constants.LOGGER_NAME_JDBC);
	}

	public JdbcExecutor connect() {
		if (bConnected) {
			return this;
		}
		//必要的重置
		close();

		try {
			conn =datasource.getConnection();
			statement = conn.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);//ResultSet.TYPE_SCROLL_INSENSITIVE,	ResultSet.CONCUR_UPDATABLE
		} catch (Exception e){
			JdbcExecutorLogger.catching(e);
			throw (new RuntimeException("Database Error!#Can't get connection.", e));
		}
		bConnected=true;
		return this;
	}
	
	public JdbcExecutor close() {
		if (chlidrenList.size()>0) {
			for (JdbcExecutor child : chlidrenList) {
				child.close();
			}
		}
		
		bConnected=false;
		
		if (result!=null) {
			try {
				result.close();
			} catch (SQLException e) {
				JdbcExecutorLogger.catching(e);
			}
			result=null;
		}
		if (query!=null) {
			try {
				query.clearBatch();
				query.close();
			} catch (SQLException e) {
				JdbcExecutorLogger.catching(e);
			}
			query=null;
		}
		if (pageCountQuery!=null) {
			try {
				pageCountQuery.clearBatch();
				pageCountQuery.close();
			} catch (SQLException e) {
				JdbcExecutorLogger.catching(e);
			}
			pageCountQuery=null;
		}
		if (statement != null) {
			try {
				statement.clearBatch();
				statement.close();
			} catch (SQLException e) {
				JdbcExecutorLogger.catching(e);
			}
			statement=null;
		}
		
		if (conn != null) {
			try {
				if (bRunningTransaction) {
					commit();
					endTransaction();
				}
				conn.close();
			} catch (SQLException e) {
				JdbcExecutorLogger.catching(e);
			}
			conn=null;
		}
		return this;
	}

	public void setAutoCommit(boolean enable) throws SQLException {
		connect();
		if (bRunningTransaction) {
			try {
				conn.rollback();
			} catch (SQLException e) {
				JdbcExecutorLogger.catching(e);
			}
		}
		conn.setAutoCommit(enable);
		bRunningTransaction=!enable;
	}

	public JdbcExecutor startTransaction() throws SQLException {
		setAutoCommit(false);
		return this;
	}

	public JdbcExecutor endTransaction() {
		try {
			setAutoCommit(true);
		} catch (SQLException e) {
			JdbcExecutorLogger.catching(e);
		}
		return this;
	}

	public JdbcExecutor commit() throws SQLException {
		conn.commit();
		bRunningTransaction=false;
		return this;
	}

	public JdbcExecutor rollback() {
		try {
			conn.rollback();
			bRunningTransaction=false;
		} catch (SQLException e) {
			JdbcExecutorLogger.catching(e);
		}
		return this;
	}

	public JdbcExecutor select(String strSQL) {
		connect();
		try {
			if (result!=null) {
				result.close();
				result=null;
			}
			startTiming();
			result = statement.executeQuery(parseSqlForTablePrefix(strSQL, tablePrefix));
			endTiming();
			return this;

		} catch (SQLException e) {
			JdbcExecutorLogger.catching(e);
			throw (new RuntimeException("Database Error!#Can't execute query.#select(String strSQL):"+strSQL+";", e));
		}  catch (Exception e){
			JdbcExecutorLogger.catching(e);
			throw (new RuntimeException("Database Error!#Can't execute query.", e));
		}
	}

	public int update(String strSQL) {
		connect();
		try {
			if (result!=null) {
				result.close();
				result=null;
			}
			startTiming();
			int updateRow=statement.executeUpdate(parseSqlForTablePrefix(strSQL, tablePrefix));
			endTiming();
			return updateRow;
		} catch (SQLException e) {
			JdbcExecutorLogger.catching(e);
			throw (new RuntimeException("Database Error!#Can't execute query.#update(String strSQL):"+strSQL+";", e));
		}  catch (Exception e){
			JdbcExecutorLogger.catching(e);
			throw (new RuntimeException("Database Error!#Can't execute query.", e));
		}
	}
	
	public int[] executeBatch(String[] strSQL) {
		connect();
		try {
			if (result!=null) {
				result.close();
				result=null;
			}
			startTiming();
			
			
			for (String sql : strSQL) {
				statement.addBatch(parseSqlForTablePrefix(sql, tablePrefix));
			}
			int[] updateRow=statement.executeBatch();
			endTiming();
			return updateRow;
		} catch (SQLException e) {
			JdbcExecutorLogger.catching(e);
			throw (new RuntimeException("Database Error!#Can't execute query.#update(String strSQL):"+strSQL+";", e));
		}  catch (Exception e){
			JdbcExecutorLogger.catching(e);
			throw (new RuntimeException("Database Error!#Can't execute query.", e));
		}
	}

	/**使用绑定变量的方法查询数据库并实现分页，需要提供变量后调用executeQuery()，pgSize>0时不能调用executeUpdate()
	 * @param pgSize
	 * @param pgCurrent
	 * @param strSQL查询数据的sql语句
	 * @return
	 */
	public JdbcExecutor createQuery(int pgSize, int pgCurrent, String strSQL) {
		this.pgSize=pgSize;
		this.pgCurrent=Math.max(1, pgCurrent);
		bUsePageQuery=pgSize>0;
		bSQLReady=false;
		querySQL=parseSqlForTablePrefix(strSQL, tablePrefix);
		connect();
		try {
			if (result!=null) {
				result.close();									result=null;
			}
			if (query!=null) {
				query.clearBatch();						query.close();							query=null;
			}
			if (pageCountQuery!=null) {
				pageCountQuery.clearBatch();				pageCountQuery.close();				pageCountQuery=null;
			}
			
			convertParameters();
			hasFixValue=checkSpecialString(querySQL, FIX_VALUE_START);
			if (!hasFixValue) {
				prepareSQL();
			}
			return this;
		} catch (SQLException e) {
			JdbcExecutorLogger.catching(e);
			throw (new RuntimeException("Database Error!#Can't create query.#createQuery(int pgSize, int pgCurrent, String strSQL):"+querySQL+";", e));
		}  catch (Exception e){
			JdbcExecutorLogger.catching(e);
			throw (new RuntimeException("Database Error!#Can't create query.", e));
		}
	}
	/**使用绑定变量的方法查询数据库
	 * @param strSQL查询数据的sql语句
	 * @return
	 */
	public JdbcExecutor createQuery(String strSQL) {
		return createQuery(-1, 1, strSQL);
	}
	public JdbcExecutor createQuery(int rowCount, String strSQL) {
		return createQuery(rowCount, 1, strSQL);
	}
	
	
	/**
	 * 执行SQL查询（SELECT）
	 */
	public JdbcExecutor executeQuery() {
		try {			
			if (result!=null) {
				result.close();
				result=null;
			}
			if (!bSQLReady) {
				prepareSQL();
			}

			startTiming();
			if (!bUsePageQuery) {//未使用分页
				rowTotal=-1;
				pgCount=-1;
				result =query.executeQuery();

			} else {//使用了分页
				result= pageCountQuery.executeQuery();
				if (!result.next()) {
					throw (new RuntimeException("Database Error!#Can't split page."));
				}
				rowTotal=result.getInt(1);
				pgCount = (int) Math.ceil((double) rowTotal / (double) pgSize);

				
				if (pgCount<pgCurrent && pgCurrent>1) {//当前页码超出范围
					int offsetRow = Math.max((pgCurrent - 1) * pgSize, 0);
					int maxRows=pgSize;
					
					if (dbType.equalsIgnoreCase(JdbcConstants.POSTGRESQL)) {
						query.setInt(paramsCount+2, offsetRow);
						bindSQLValueMap.put(paramsCount+2, offsetRow);

					} else if (dbType.equalsIgnoreCase(JdbcConstants.MYSQL) || dbType.equalsIgnoreCase(JdbcConstants.MARIADB)) {
						query.setInt(paramsCount+1, offsetRow);
						bindSQLValueMap.put(paramsCount+1, offsetRow);

					} else if (dbType.equalsIgnoreCase(JdbcConstants.ORACLE)) {
						query.setInt(paramsCount+1, offsetRow+maxRows);
						query.setInt(paramsCount+2, offsetRow);
						bindSQLValueMap.put(paramsCount+1, offsetRow+maxRows);
						bindSQLValueMap.put(paramsCount+2, offsetRow);
					
					}
				}
				result = query.executeQuery();
			}
			endTiming();
			return this;

		} catch (SQLException e) {
			JdbcExecutorLogger.catching(e);
			throw (new RuntimeException("Database Error!#Can't execute query.#executeQuery(String strSQL):bUsePageQuery="+bUsePageQuery+"#preparedSQL="+currentSQLString(), e));
		}  catch (Exception e){
			JdbcExecutorLogger.catching(e);
			throw (new RuntimeException("Database Error!#Can't execute query.", e));
		}
	}

	/**
	 * 执行SQL（非SELECT）
	 * @return
	 */
	public int executeUpdate() {
		try {
			if (result!=null) {
				result.close();
				result=null;
			}
			if (!bSQLReady) {
				prepareSQL();
			}
			startTiming();
			int updateRow= query.executeUpdate();
			endTiming();
			return updateRow;
		} catch (SQLException e) {
			JdbcExecutorLogger.catching(e);
			throw (new RuntimeException("Database Error!#Can't execute query.#executeUpdate();#preparedSQL="+currentSQLString(), e));
		}  catch (Exception e) {
			JdbcExecutorLogger.catching(e);
			throw (new RuntimeException("Database Error!#Can't execute query.", e));
		}
	}
	
	
	private void prepareSQL() {
		hasFixValue=checkSpecialString(querySQL, FIX_VALUE_START);
		if (hasFixValue) {
			bindFixValue();
		}
		//Create Statement
		try {
			if (!bUsePageQuery) {
				query=conn.prepareStatement(querySQL, ResultSet.TYPE_FORWARD_ONLY,	ResultSet.CONCUR_READ_ONLY);
				
			} else {
				pageCountQuery=conn.prepareStatement(parseSqlForCountRows(querySQL, dbType), ResultSet.TYPE_FORWARD_ONLY,	ResultSet.CONCUR_READ_ONLY);
				
				int offsetRow = Math.max((pgCurrent - 1) * pgSize, 0);
				int maxRows=pgSize;
				
				
				if (dbType.equalsIgnoreCase(JdbcConstants.POSTGRESQL)) {
					//sql+" limit "+maxRows+" offset "+offsetRow;
					query=conn.prepareStatement(querySQL+" limit ? offset ? " , ResultSet.TYPE_FORWARD_ONLY,	ResultSet.CONCUR_READ_ONLY);
					query.setInt(paramsCount+1, maxRows);
					query.setInt(paramsCount+2, offsetRow);
					bindSQLValueMap.put(paramsCount+1, maxRows);
					bindSQLValueMap.put(paramsCount+2, offsetRow);

				} else if (dbType.equalsIgnoreCase(JdbcConstants.MYSQL) || dbType.equalsIgnoreCase(JdbcConstants.MARIADB)) {
					//sql+" limit "+offsetRow+", "+maxRows;
					query=conn.prepareStatement(querySQL+" limit ?, ?", ResultSet.TYPE_FORWARD_ONLY,	ResultSet.CONCUR_READ_ONLY);
					query.setInt(paramsCount+1, offsetRow);
					query.setInt(paramsCount+2, maxRows);
					bindSQLValueMap.put(paramsCount+1, offsetRow);
					bindSQLValueMap.put(paramsCount+2, maxRows);

				} else if (dbType.equalsIgnoreCase(JdbcConstants.ORACLE)) {
					//"select * from (select a.*, rownum rn from ("+sql+") a where rownum <="+(offsetRow+maxRows)+") b where rn >"+offsetRow;
					query=conn.prepareStatement("select * from (select a.*, rownum rn from ("+querySQL+") a where rownum <=?) b where rn >?" , ResultSet.TYPE_FORWARD_ONLY,	ResultSet.CONCUR_READ_ONLY);
					query.setInt(paramsCount+1, offsetRow+maxRows);
					query.setInt(paramsCount+2, offsetRow);
					bindSQLValueMap.put(paramsCount+1, offsetRow+maxRows);
					bindSQLValueMap.put(paramsCount+2, offsetRow);
				
				} else {
					query=conn.prepareStatement(PagerUtils.limit(querySQL, dbType, offsetRow, maxRows), ResultSet.TYPE_FORWARD_ONLY,	ResultSet.CONCUR_READ_ONLY);
				}
			}
		} catch (SQLException e) {
			JdbcExecutorLogger.catching(e);
			throw (new RuntimeException("Database Error!#Can't execute query.#convertSQL(String strSQL):"+querySQL+";", e));
		}  catch (Exception e){
			JdbcExecutorLogger.catching(e);
			throw (new RuntimeException("Database Error!#Can't execute query.", e));
		}
		
		//注意，必须先bSQLReady=true，再执行bindParamsValue();
		bSQLReady=true;
		if (hasFixValue) {
			bindParamsValue();
		}
	}
	
	/**
	 * 处理SQL中可能包含的绑定变量${xxx}
	 */
	private void convertParameters() {
		paramsCount=0;
		paramsIndexMap.clear();
		paramsValueMap.clear();
		paramsTypeMap.clear();
		bindValueMap.clear();
		bindTypeMap.clear();
		bindSQLValueMap.clear();
		if (!checkSpecialString(querySQL, PARAMS_START)) {
			paramsCount=(querySQL+"/*avoid end with question mark*/").split("\\?").length-1;
			return;
		} else if(checkSpecialString(querySQL, "?")) {//不能同时使用${}形式的参数和?形式的参数
			throw (new RuntimeException("Database Error!#Can't use both '${xxx}' type and '?' type pamameter!"));
		}
		
		StringBuilder newSQL=new StringBuilder();
		int index=1;
		String[] array=querySQL.split("'"); 
		
		for (int i=0; i<array.length; i++) {
			if (i%2!=0) {//属于单引号（'xxx'）中间的内容
				newSQL.append("'").append(array[i]).append("'");
			} else {
				int x=0, y=0, z=0, length=array[i].length();
				while (x<length) {
					y=array[i].indexOf(PARAMS_START, x);
					z=array[i].indexOf(PARAMS_END, y);
					if (y!=-1 && z!=-1) {//'${'和'}'都存在
						newSQL.append(array[i].substring(x, y)).append("?");						
						String name=array[i].substring(y+2, z); //“{xxx}”中间的“xxx”						
						if (!paramsIndexMap.containsKey(name)) {
							paramsIndexMap.put(name, new ArrayList<Integer>());
						}
						paramsIndexMap.get(name).add(index++);
						x=z+1;
					} else {
						newSQL.append(array[i].substring(x));
						x=length;
					}
				}
			}
		}
		paramsCount=index-1; //因为最后一个参数执行了++;
		querySQL=newSQL.toString();
	}
	
	/**
	 * 处理SQL中的固定值（#{xxx}），将其替换为实际的值
	 */
	private void bindFixValue() {
		StringBuilder newSQL=new StringBuilder();
		String[] array=querySQL.split("'"); 
		
		for (int i=0; i<array.length; i++) {
			if (i%2!=0) {//属于单引号（'xxx'）中间的内容
				newSQL.append("'").append(array[i]).append("'");
			} else {
				int x=0, y=0, z=0, length=array[i].length();
				while (x<length) {
					y=array[i].indexOf(FIX_VALUE_START, x);
					z=array[i].indexOf(FIX_VALUE_END, y);
					if (y!=-1 && z!=-1) {//'${'和'}'都存在
						newSQL.append(array[i].substring(x, y));						
						String name=array[i].substring(y+2, z); //“{xxx}”中间的“xxx”
						newSQL.append(readParamSQLString(paramsTypeMap.get(name), paramsValueMap.get(name)));
						x=z+1;
					} else {
						newSQL.append(array[i].substring(x));
						x=length;
					}
				}
			}
		}
		querySQL=newSQL.toString();
	}
	
	private void bindParamsValue() {
		for (String name : paramsIndexMap.keySet()) {
			DTYPE type=paramsTypeMap.get(name);
			Object value=paramsValueMap.get(name);
			for (Integer i : paramsIndexMap.get(name)) {
				switch (type) {
				case STRING : setString(i, (String) value); break;
				case NSTRING : setNString(i, (String) value); break;
				case NUMSTRING : setNumString(i, (String) value); break;
				case DATE : setDate(i, (java.util.Date) value); break;
				case DECIMAL : setDecimal(i, (BigDecimal) value); break;
				case LONG : setLong(i, (Long) value); break;
				case INT : setInt(i, (Integer) value); break;
				case DOUBLE : setDouble(i, (Double) value); break;
				case FLOAT : setFloat(i, (Float) value); break;
				case NULL : setNull(i, (Integer)value); break;
				}
			}
		}
		for (int index : bindValueMap.keySet()) {
			DTYPE type=bindTypeMap.get(index);
			Object value=bindValueMap.get(index);
			switch (type) {
			case STRING : setString(index, (String) value); break;
			case NSTRING : setNString(index, (String) value); break;
			case NUMSTRING : setNumString(index, (String) value); break;
			case DATE : setDate(index, (java.util.Date) value); break;
			case DECIMAL : setDecimal(index, (BigDecimal) value); break;
			case LONG : setLong(index, (Long) value); break;
			case INT : setInt(index, (Integer) value); break;
			case DOUBLE : setDouble(index, (Double) value); break;
			case FLOAT : setFloat(index, (Float) value); break;
			case NULL : setNull(index, (Integer)value); break;
			}
		}
	}

	private void startTiming() {
		startTime=System.currentTimeMillis();
	}
	private void endTiming() {
		long usedTime=System.currentTimeMillis()-startTime;
		if (usedTime>10000) {
			JdbcExecutorLogger.warn("JdbcExecutor-slow10000:{}ms - {}", usedTime, currentSQLString());
		} else if (usedTime>6000) {
			JdbcExecutorLogger.warn("JdbcExecutor-slow6000:{}ms - {}", usedTime, currentSQLString());
		} else if (usedTime>3000) {
			JdbcExecutorLogger.warn("JdbcExecutor-slow3000:{}ms - {}", usedTime, currentSQLString());
		} else if (usedTime>1000) {
			JdbcExecutorLogger.info("JdbcExecutor-slow1000:{}ms - {}", usedTime, currentSQLString());
		} else if (JdbcExecutorLogger.isDebugEnabled()) {
			JdbcExecutorLogger.debug("JdbcExecutor:{}ms - {}", usedTime, currentSQLString());
		}
	}
	
	private String currentSQLString() {
		StringBuilder sb=new StringBuilder();
		String[] array=querySQL.split("\\?");
		for (int i=0; i<array.length; i++) {
			sb.append(array[i]);
			if (bindSQLValueMap.containsKey(i+1)) {
				sb.append("/*").append(i+1).append("*/").append(bindSQLValueMap.get(i+1));
			}
		}
		return sb.toString();
	}
	
	private StringBuffer sqlValueCache=new StringBuffer();
	private String readParamSQLString(DTYPE type, Object value) {
		sqlValueCache.delete(0, sqlValueCache.length());
		switch (type) {
		case NSTRING :
		case STRING : sqlValueCache.append("'").append(value!=null ? ((String)value).replace("'", "''") : "").append("'"); break;
		case NUMSTRING : sqlValueCache.append(value!=null && Utils.isNumber((String)value) ? value : "null"); break;
		case DATE : sqlValueCache.append(value!=null ? sql_toDate("'"+Utils.dateFormat((java.util.Date)value, "yyyy-MM-dd HH:mm:ss")+"'", "'yyyy-MM-dd HH:mm:ss'") : "null"); break;
		case DECIMAL : sqlValueCache.append(value!=null ? (BigDecimal) value : "null"); break;
		case LONG : sqlValueCache.append(value!=null ? (Long) value : "null"); break;
		case INT : sqlValueCache.append(value!=null ? (Integer) value : "null"); break;
		case DOUBLE : sqlValueCache.append(value!=null ? (Double) value : "null"); break;
		case FLOAT : sqlValueCache.append(value!=null ? (Float) value : "null"); break;
		case NULL : sqlValueCache.append("null"); break;
		}
		return sqlValueCache.toString();
	}
	
	public ArrayList<LinkedHashMap<String, Object>> readMetaData() {
		return readMetaData(new NameMapper());
	}
	
	public ArrayList<LinkedHashMap<String, Object>> readMetaData(NameMapper rowMapper) {
		ArrayList<LinkedHashMap<String, Object>> list = new ArrayList<LinkedHashMap<String,Object>>();
		try {
			ResultSetMetaData metaData = result.getMetaData();
			int count = metaData.getColumnCount();
			for (int i = 1; i <= count; i++) {
				LinkedHashMap<String, Object> map = new LinkedHashMap<String, Object>();
				map.put("label", metaData.getColumnLabel(i).toLowerCase());
				map.put("name", rowMapper.convertName(metaData.getColumnLabel(i)));
				map.put("displaySize", metaData.getColumnDisplaySize(i));
				map.put("type", metaData.getColumnType(i));
				map.put("typeName", metaData.getColumnTypeName(i));
				map.put("typeSimpleName", readColumnSimpleType(metaData.getColumnType(i)));
				map.put("precision", metaData.getPrecision(i));
				map.put("scale", metaData.getScale(i));
				list.add(map);
			}
		} catch (SQLException e) {
			JdbcExecutorLogger.catching(e);
		}
		return list;
	}
	
	public LinkedHashMap<String, LinkedHashMap<String, Object>> readMetaDataMap() {
		return readMetaDataMap(new NameMapper());
	}
	
	public LinkedHashMap<String, LinkedHashMap<String, Object>> readMetaDataMap(NameMapper rowMapper) {
		LinkedHashMap<String, LinkedHashMap<String, Object>> metaMap = new LinkedHashMap<String, LinkedHashMap<String,Object>>();
		try {
			ResultSetMetaData metaData = result.getMetaData();
			int count = metaData.getColumnCount();
			for (int i = 1; i <= count; i++) {
				LinkedHashMap<String, Object> map = new LinkedHashMap<String, Object>();
				map.put("label", metaData.getColumnLabel(i).toLowerCase());
				map.put("name", rowMapper.convertName(metaData.getColumnLabel(i)));
				map.put("displaySize", metaData.getColumnDisplaySize(i));
				map.put("type", metaData.getColumnType(i));
				map.put("typeName", metaData.getColumnTypeName(i));
				map.put("typeSimpleName", readColumnSimpleType(metaData.getColumnType(i)));
				map.put("precision", metaData.getPrecision(i));
				map.put("scale", metaData.getScale(i));
				metaMap.put(metaData.getColumnName(i).toLowerCase(), map);
			}
		} catch (SQLException e) {
			JdbcExecutorLogger.catching(e);
		}
		return metaMap;
	}
	
	public String readResultJSON() {
		return Utils.json(readResultMap(new NameMapper()));
	}

	public String readResultJSON(NameMapper rowMapper) {
		return Utils.json(readResultMap(rowMapper));
	}

	public LinkedHashMap<String, Object> readResultMap() {
		return readResultMap(new NameMapper());
	}
	
	public LinkedHashMap<String, Object> readResultMap(NameMapper rowMapper) {
		if (result==null) {
			throw (new RuntimeException("Database Error!#None of query have been executed."));
		}
		LinkedHashMap<String, Object> map = new LinkedHashMap<String, Object>();
		try {
			ResultSetMetaData metaData = result.getMetaData();
			int count = metaData.getColumnCount();
			for (int i = 1; i <= count; i++) {
				map.put(rowMapper.convertName(metaData.getColumnLabel(i)), readCell(metaData, i));//result.getObject(i)
			}
		} catch (SQLException e) {
			JdbcExecutorLogger.catching(e);
		}
		return map;
	}
	
	public ArrayList<LinkedHashMap<String, Object>> readResultList() {
		return readResultList(new NameMapper());
	}

	public ArrayList<LinkedHashMap<String, Object>> readResultList(NameMapper rowMapper) {
		if (result==null) {
			throw (new RuntimeException("Database Error!#None of query have been executed."));
		}
		ArrayList<LinkedHashMap<String, Object>> list = new ArrayList<LinkedHashMap<String, Object>>();
		try {
			ResultSetMetaData metaData = result.getMetaData();
			int count = metaData.getColumnCount();
			while (result.next()) {
				LinkedHashMap<String, Object> map = new LinkedHashMap<String, Object>();
				for (int i = 1; i <= count; i++) {
					map.put(rowMapper.convertName(metaData.getColumnLabel(i)), readCell(metaData, i));//result.getObject(i)
				}
				list.add(map);
			}
		} catch (SQLException e) {
			JdbcExecutorLogger.catching(e);
		}
		return list;
	}
	
	@SuppressWarnings("unchecked")
	public <T> T readCell(int i, Class<T> requiredType) {
		try {
			if (Integer.class==requiredType) {
				return (T)(Object)result.getInt(i);

			} else if (Long.class==requiredType) {
				return (T)(Object)result.getLong(i);

			} else if (Float.class==requiredType) {
				return (T)(Object)result.getFloat(i);

			} else if (Double.class==requiredType) {
				return (T)(Object)result.getDouble(i);

			} else if (String.class==requiredType) {
				return (T)(Object)result.getString(i);

			} else if (Date.class==requiredType) {
				return (T)(Object)Utils.dateParse(result.getString(i), "yyyy-MM-dd HH:mm");
			
			} else {
				return (T)result.getObject(i);
			}
		} catch (SQLException e) {
			throw new RuntimeException("SQL Error on readCell()");
		}
	}
	
	public Object readCell(ResultSetMetaData metaData, int i) throws SQLException {
		switch (metaData.getColumnType(i)) {
		case Types.VARCHAR :
		case Types.CHAR :
		case Types.CLOB :
		case Types.LONGNVARCHAR :
		case Types.LONGVARCHAR :
		case Types.NCHAR :
		case Types.NCLOB :
		case Types.NVARCHAR :
			return result.getString(i)!=null ? result.getString(i) : "";
		case Types.BIGINT :
			return result.getLong(i);
		case Types.BIT :
		case Types.INTEGER :
		case Types.SMALLINT :
		case Types.TINYINT :
			return result.getInt(i);
		case Types.NUMERIC :
		case Types.DECIMAL :
			int scale=metaData.getScale(i);
			int precision=metaData.getPrecision(i);
			if (scale==0) {
				if (precision<=11) {
					return result.getInt(i);
				} else {
					return result.getLong(i);
				}
			} else {
				if (precision<=38) {
					return result.getFloat(i);
				} else {
					return result.getDouble(i);
				}
			}
			//特别注意：此处不能写为return precision<=11 ?  result.getInt(i) : result.getLong(i);，这样会永远返回一个Long对象，应该是因为一个赋值语句只能有一个结果类型
		default :
			return result.getObject(i);
		}
	}
	
	public String readColumnSimpleType(int type) {
		switch (type) {
		case Types.VARCHAR :
		case Types.CHAR :
		case Types.CLOB :
		case Types.LONGNVARCHAR :
		case Types.LONGVARCHAR :
		case Types.NCHAR :
		case Types.NCLOB :
		case Types.NVARCHAR :
			return "string";
		case Types.BIGINT :
		case Types.BIT :
		case Types.INTEGER :
		case Types.SMALLINT :
		case Types.TINYINT :
			return "int";
		case Types.NUMERIC :
		case Types.DECIMAL :
			return "decimal";
		default :
			return "other";
		}
	}


	/**
	 * 判断SQL语句中是否包含指定的字符，但忽略单引号（'xxx'）中间的内容（按“'”分隔后i%2==0的数组元素）
	 * @param sql
	 * @param str
	 * @return
	 */
	private boolean checkSpecialString(String sql, String str) {
		String[] array=sql.split("'");
		for (int i=0; i<array.length; i++) {
			if (i%2==0 && array[i].indexOf(str)!=-1) {
				return true;
			}
		}
		return false;
	}

	private ArrayList<Integer> readParamsIndex(String param) {
		if (!paramsIndexMap.containsKey(param)) {
			throw (new RuntimeException("Database Error!#parameter '"+param+"' is not exists!["+querySQL+"]"));
		} else {
			return paramsIndexMap.get(param);
		}
	}

	public JdbcExecutor setNString(String param, String value) {
		if (!bSQLReady) {
			paramsValueMap.put(param, value);
			paramsTypeMap.put(param, DTYPE.NSTRING);
			return this;
		}
		for (int index : readParamsIndex(param)) {
			setNString(index, value);
		}
		return this;
	}
	public JdbcExecutor setNString(int index, String value) {
		if (!bSQLReady) {
			bindValueMap.put(index, value);
			bindTypeMap.put(index, DTYPE.NSTRING);
			return this;
		}
		if (value==null) {
			value="";
		}
		try {
			if (bUsePageQuery) {
				pageCountQuery.setNString(index, value);
				query.setNString(index, value);
			} else {
				query.setNString(index, value);
			}
			bindSQLValueMap.put(index, readParamSQLString(DTYPE.NSTRING, value));
			return this;
		} catch (Exception e) {
			throw (new RuntimeException("Database Error!#Can't set parameter#setNString("+index+", \""+value+"\")"));
		}
	}
	
	/**
	 * 将Map中的值指地绑定到变量中
	 * @param paramsArrayString
	 * @param data
	 */
	public JdbcExecutor setParamsMap(String paramsArrayString, HashMap<String, String> data) {
		for (String item : paramsArrayString.split(",")) {
			item=item.trim();
			if (item.length()>0) {
				int x=item.indexOf("]");
				String type=x!=-1 ? item.substring(0, x+1)  : "[string]";
				String param=x!=-1 ? item.substring(x+1)  : item;
				if (type.equals("[string]")) {
					setString(param, data.get(param));
				} else if (type.equals("[int]")) {
					setInt(param, data.get(param));
				} else if (type.equals("[date]")) {
					setDate(param, Utils.dateParse(data.get(param), "yyyy-MM-dd HH:mm"));
				} else if (type.equals("[long]")) {
					setLong(param, data.get(param));
				} else {//浮点数，以及其它类型还是直接按字符串处理的好
					setString(param, data.get(param));
				}
			}
		}
		return this;
	}
	
	public JdbcExecutor setJdbcParams(JdbcParams params) {
		for (String item : params.keySet()) {
			String type=params.getTypeName(item);
			if ("String".equals(type)) {
				setString(item, (String)params.get(item));
			} else if ("Long".equals(type)) {
				setLong(item, (Long)params.get(item));
			} else if ("Int".equals(type)) {
				setInt(item, (Integer)params.get(item));
			} else if ("Float".equals(type)) {
				setFloat(item, (Float)params.get(item));
			} else if ("Double".equals(type)) {
				setDouble(item, (Double)params.get(item));
			} else if ("Datetime".equals(type)) {
				setDate(item, (Date)params.get(item));
			} else if ("Decimal".equals(type)) {
				setDecimal(item, (BigDecimal)params.get(item));
			} else {
				setString(item, String.valueOf(params.get(item)));
			}
		}
		return this;
	}
	
	public JdbcExecutor setArgArray(Object... args) {
		for (int index=0; index<args.length; index++) {
			setCell(args[index], index, args[index]);
		}
		return this;
	}
	
	public <T> void setCell(T t , int index, Object value){ 
		if (t instanceof String) {
			setString(index+1, (String)t);
		} else if (t instanceof Long) {
			setLong(index+1, (Long)t);
		} else if (t instanceof Integer) {
			setInt(index+1, (Integer)t);
		} else if (t instanceof Float) {
			setFloat(index+1, (Float)t);
		} else if (t instanceof Double) {
			setDouble(index+1, (Double)t);
		} else if (t instanceof Date) {
			setDate(index+1, (Date)t);
		} else if (t instanceof BigDecimal) {
			setDecimal(index+1, (BigDecimal)t);
		} else if (t instanceof JdbcParams) {
			throw new RuntimeException("调用错误：不能同时使用JdbcParams（名称绑定变量）和Args..（序号绑定变量）");
		} else {
			setString(index+1, String.valueOf(t));
		}
	}
	
	public JdbcExecutor setString(String param, String value, int maxLength) {
		return setString(param, value!=null && value.length()>maxLength ? value.substring(0, maxLength) : value);
	}
	
	public JdbcExecutor setString(String param, String value) {
		if (!bSQLReady) {
			paramsValueMap.put(param, value);
			paramsTypeMap.put(param, DTYPE.STRING);
			return this;
		}
		for (int index : readParamsIndex(param)) {
			setString(index, value);
		}
		return this;
	}
	public JdbcExecutor setString(int index, String value) {
		if (!bSQLReady) {
			bindValueMap.put(index, value);
			bindTypeMap.put(index, DTYPE.STRING);
			return this;
		}
		if (value==null) {
			value="";
		}
		try {
			if (bUsePageQuery) {
				pageCountQuery.setString(index, value);
				query.setString(index, value);
			} else {
				query.setString(index, value);
			}
			bindSQLValueMap.put(index, readParamSQLString(DTYPE.STRING, value));
			return this;
		} catch (Exception e) {
			throw (new RuntimeException("Database Error!#Can't set parameter#setString("+index+", \""+value+"\")"));
		}
	}
	
	public JdbcExecutor setNumString(String param, String value) {
		if (!bSQLReady) {
			paramsValueMap.put(param, value);
			paramsTypeMap.put(param, DTYPE.NUMSTRING);
			return this;
		}
		for (int index : readParamsIndex(param)) {
			setNumString(index, value);
		}
		return this;
	}
	
	public JdbcExecutor setNumString(int index, String value) {
		if (!bSQLReady) {
			bindValueMap.put(index, value);
			bindTypeMap.put(index, DTYPE.NUMSTRING);
			return this;
		}
		if (value!=null) {
			value=value.replaceAll(",", "");
		} else {
			setNull(index, Types.NUMERIC);
			return this;
		}
		try {
			if (bUsePageQuery) {
				pageCountQuery.setBigDecimal(index, new BigDecimal(value));
				query.setBigDecimal(index, new BigDecimal(value));
			} else {
				query.setBigDecimal(index, new BigDecimal(value));
			}
			bindSQLValueMap.put(index, readParamSQLString(DTYPE.NUMSTRING, value));
			return this;
		} catch (Exception e) {
			JdbcExecutorLogger.catching(e);
			throw (new RuntimeException("Database Error!#Can't set parameter#setBigDecimal("+index+", \""+value+"\")"));
		}
	}
	
	public JdbcExecutor setDate(String param, java.util.Date value) {
		return setTimestamp(param, value);
	}
	
	public JdbcExecutor setDate(int index, java.util.Date value) {
		return setTimestamp(index, value);
	}
	
	public JdbcExecutor setTimestamp(String param, java.util.Date value) {
		if (!bSQLReady) {
			paramsValueMap.put(param, value);
			paramsTypeMap.put(param, DTYPE.DATE);
			return this;
		}
		for (int index : readParamsIndex(param)) {
			setTimestamp(index, value);
		}
		return this;
	}
	public JdbcExecutor setTimestamp(int index, java.util.Date value) {
		if (!bSQLReady) {
			bindValueMap.put(index, value);
			bindTypeMap.put(index, DTYPE.DATE);
			return this;
		}
		if (value==null) {
			setNull(index,  java.sql.Types.TIMESTAMP);
			return this;
		}
		try {
			if (bUsePageQuery) {
				pageCountQuery.setTimestamp(index, new java.sql.Timestamp(value.getTime()));
				query.setTimestamp(index, new java.sql.Timestamp(value.getTime()));
			} else {
				query.setTimestamp(index, new java.sql.Timestamp(value.getTime()));
			}
			bindSQLValueMap.put(index, readParamSQLString(DTYPE.DATE, value));
			return this;
		} catch (Exception e) {
			JdbcExecutorLogger.catching(e);
			throw (new RuntimeException("Database Error!#Can't set parameter#setInt("+index+", "+value+")"));
		}
	}
	
	public JdbcExecutor setDecimal(String param, BigDecimal value) {
		if (!bSQLReady) {
			paramsValueMap.put(param, value);
			paramsTypeMap.put(param, DTYPE.DECIMAL);
			return this;
		}
		for (int index : readParamsIndex(param)) {
			setDecimal(index, value);
		}
		return this;
	}
	
	public JdbcExecutor setDecimal(int index, BigDecimal value) {
		if (!bSQLReady) {
			bindValueMap.put(index, value);
			bindTypeMap.put(index, DTYPE.DECIMAL);
			return this;
		}
		try {
			if (bUsePageQuery) {
				pageCountQuery.setBigDecimal(index, value);
				query.setBigDecimal(index, value);
			} else {
				query.setBigDecimal(index, value);
			}
			bindSQLValueMap.put(index, readParamSQLString(DTYPE.DECIMAL, value));
			return this;
		} catch (Exception e) {
			JdbcExecutorLogger.catching(e);
			throw (new RuntimeException("Database Error!#Can't set parameter#setDecimal("+index+", "+value+")"));
		}
	}
	
	public JdbcExecutor setDecimal(String param, String value) {
		if (!bSQLReady) {
			if (Utils.isNumber(value)) {
				paramsValueMap.put(param, new BigDecimal(value));
				paramsTypeMap.put(param, DTYPE.DECIMAL);
			} else {
				paramsValueMap.put(param, Types.DECIMAL);
				paramsTypeMap.put(param, DTYPE.NULL);
			}
			return this;
		}
		for (int index : readParamsIndex(param)) {
			setDecimal(index, value);
		}
		return this;
	}
	
	public JdbcExecutor setDecimal(int index, String value) {
		if (!bSQLReady) {
			if (Utils.isNumber(value)) {
				bindValueMap.put(index, new BigDecimal(value));
				bindTypeMap.put(index, DTYPE.DECIMAL);
			} else {
				bindValueMap.put(index, Types.DECIMAL);
				bindTypeMap.put(index, DTYPE.NULL);
			}
			return this;
		}
		if (value!=null && Utils.isNumber(value)) {
			setDecimal(index, new BigDecimal(value));
		} else {
			setNull(index, Types.DECIMAL);
		}
		return this;
	}
	
	public JdbcExecutor setDecimal(int index, String value, BigDecimal defaultValue) {
		if (!bSQLReady) {
			bindValueMap.put(index, Utils.isNumber(value) ? new BigDecimal(value) : defaultValue);
			bindTypeMap.put(index, DTYPE.DECIMAL);
			return this;
		}
		return setDecimal(index, Utils.isNumber(value) ? new BigDecimal(value) : defaultValue);
	}
	
	public JdbcExecutor setLong(String param, long value) {
		if (!bSQLReady) {
			paramsValueMap.put(param, value);
			paramsTypeMap.put(param, DTYPE.LONG);
			return this;
		}
		for (int index : readParamsIndex(param)) {
			setLong(index, value);
		}
		return this;
	}
	
	public JdbcExecutor setLong(int index, long value) {
		if (!bSQLReady) {
			bindValueMap.put(index, value);
			bindTypeMap.put(index, DTYPE.LONG);
			return this;
		}
		try {
			if (bUsePageQuery) {
				pageCountQuery.setLong(index, value);
				query.setLong(index, value);
			} else {
				query.setLong(index, value);
			}
			bindSQLValueMap.put(index, readParamSQLString(DTYPE.LONG, value));
			return this;
		} catch (Exception e) {
			JdbcExecutorLogger.catching(e);
			throw (new RuntimeException("Database Error!#Can't set parameter#setLong("+index+", "+value+")"));
		}
	}
	
	public JdbcExecutor setLong(String param, String value) {
		if (!bSQLReady) {
			if (Utils.isLong(value)) {
				paramsValueMap.put(param, Utils.parseLong(value, 0));
				paramsTypeMap.put(param, DTYPE.LONG);
			} else {
				paramsValueMap.put(param, Types.BIGINT);
				paramsTypeMap.put(param, DTYPE.NULL);
			}
			return this;
		}
		for (int index : readParamsIndex(param)) {
			setLong(index, value);
		}
		return this;
	}
	public JdbcExecutor setLong(int index, String value) {
		if (!bSQLReady) {
			if (Utils.isLong(value)) {
				bindValueMap.put(index, Utils.parseLong(value, 0));
				bindTypeMap.put(index, DTYPE.LONG);
			} else {
				bindValueMap.put(index, Types.BIGINT);
				bindTypeMap.put(index, DTYPE.NULL);
			}
			
			return this;
		}
		if (value!=null && Utils.isLong(value)) {
			setLong(index, Utils.parseLong(value, 0));
		} else {
			setNull(index, Types.BIGINT);
		}
		return this;
	}
	
	public JdbcExecutor setLong(int index, String value, long defaultValue) {
		if (!bSQLReady) {
			bindValueMap.put(index,  Utils.parseLong(value, defaultValue));
			bindTypeMap.put(index, DTYPE.LONG);
			return this;
		}
		return setLong(index, Utils.parseLong(value, defaultValue));
	}
	public JdbcExecutor setInt(String param, int value) {
		if (!bSQLReady) {
			paramsValueMap.put(param, value);
			paramsTypeMap.put(param, DTYPE.INT);
			return this;
		}
		for (int index : readParamsIndex(param)) {
			setInt(index, value);
		}
		return this;
	}
	public JdbcExecutor setInt(int index, int value) {
		if (!bSQLReady) {
			bindValueMap.put(index, value);
			bindTypeMap.put(index, DTYPE.INT);
			return this;
		}
		try {
			if (bUsePageQuery) {
				pageCountQuery.setInt(index, value);
				query.setInt(index, value);
			} else {
				query.setInt(index, value);
			}
			bindSQLValueMap.put(index, readParamSQLString(DTYPE.INT, value));
			return this;
		} catch (Exception e) {
			JdbcExecutorLogger.catching(e);
			throw (new RuntimeException("Database Error!#Can't set parameter#setInt("+index+", "+value+")"));
		}
	}
	
	public JdbcExecutor setInt(String param, String value) {
		if (!bSQLReady) {
			if (Utils.isInt(value)) {
				paramsValueMap.put(param, Utils.parseInt(value, 0));
				paramsTypeMap.put(param, DTYPE.INT);
			} else {
				paramsValueMap.put(param, Types.INTEGER);
				paramsTypeMap.put(param, DTYPE.NULL);
			}
			return this;
		}
		for (int index : readParamsIndex(param)) {
			setInt(index, value);
		}
		return this;
	}
	
	public JdbcExecutor setInt(int index, String value) {
		if (!bSQLReady) {
			if (Utils.isInt(value)) {
				bindValueMap.put(index, Utils.parseInt(value, 0));
				bindTypeMap.put(index, DTYPE.INT);
			} else {
				bindValueMap.put(index, Types.INTEGER);
				bindTypeMap.put(index, DTYPE.NULL);
			}
			return this;
		}
		if (value!=null && Utils.isInt(value)) {
			return setInt(index, Utils.parseInt(value, 0));
		} else {
			return setNull(index, Types.INTEGER);
		}
	}
	
	public JdbcExecutor setInt(int index, String value, int defaultValue) {
		if (!bSQLReady) {
			bindValueMap.put(index, Utils.parseInt(value, defaultValue));
			bindTypeMap.put(index, DTYPE.INT);
			return this;
		}
		return setInt(index, Utils.parseInt(value, defaultValue));
	}
	
	public JdbcExecutor setDouble(String param, double value) {
		if (!bSQLReady) {
			paramsValueMap.put(param, value);
			paramsTypeMap.put(param, DTYPE.DOUBLE);
			return this;
		}
		for (int index : readParamsIndex(param)) {
			setDouble(index, value);
		}
		return this;
	}
	
	public JdbcExecutor setDouble(int index, double value) {
		if (!bSQLReady) {
			bindValueMap.put(index, value);
			bindTypeMap.put(index, DTYPE.DOUBLE);
			return this;
		}
		try {
			if (bUsePageQuery) {
				pageCountQuery.setDouble(index, value);
				query.setDouble(index, value);
			} else {
				query.setDouble(index, value);
			}
			bindSQLValueMap.put(index, readParamSQLString(DTYPE.DOUBLE, value));
			return this;
		} catch (Exception e) {
			JdbcExecutorLogger.catching(e);
			throw (new RuntimeException("Database Error!#Can't set parameter#setDouble("+index+", "+value+")"));
		}
	}
	
	public JdbcExecutor setDouble(int index, String value) {
		if (!bSQLReady) {
			if (Utils.isDouble(value)) {
				bindValueMap.put(index, Utils.parseDouble(value, 0));
				bindTypeMap.put(index, DTYPE.DOUBLE);
			} else {
				bindValueMap.put(index, Types.DOUBLE);
				bindTypeMap.put(index, DTYPE.NULL);
			}
			return this;
		}
		if (value!=null && Utils.isDouble(value)) {
			return setDouble(index, Utils.parseDouble(value, 0));
		} else {
			return setNull(index, Types.DOUBLE);
		}
	}
	
	public JdbcExecutor setDouble(String param, String value, double defaultValue) {
		if (!bSQLReady) {
			paramsValueMap.put(param, Utils.parseDouble(value, defaultValue));
			paramsTypeMap.put(param, DTYPE.DOUBLE);
			return this;
		}
		for (int index : readParamsIndex(param)) {
			setDouble(index, value, defaultValue);
		}
		return this;
	}
	
	public JdbcExecutor setDouble(int index, String value, double defaultValue) {
		if (!bSQLReady) {
			bindValueMap.put(index, Utils.parseDouble(value, defaultValue));
			bindTypeMap.put(index, DTYPE.DOUBLE);
			return this;
		}
		return setDouble(index, Utils.parseDouble(value, defaultValue));
	}
	
	public JdbcExecutor setFloat(String param, float value) {
		if (!bSQLReady) {
			paramsValueMap.put(param, value);
			paramsTypeMap.put(param, DTYPE.FLOAT);
			return this;
		}
		for (int index : readParamsIndex(param)) {
			setFloat(index, value);
		}
		return this;
	}
	
	public JdbcExecutor setFloat(int index, float value) {
		if (!bSQLReady) {
			bindValueMap.put(index, value);
			bindTypeMap.put(index, DTYPE.FLOAT);
			return this;
		}
		try {
			if (bUsePageQuery) {
				pageCountQuery.setFloat(index, value);
				query.setFloat(index, value);
			} else {
				query.setFloat(index, value);
			}
			bindSQLValueMap.put(index, readParamSQLString(DTYPE.FLOAT, value));
			return this;
		} catch (Exception e) {
			JdbcExecutorLogger.catching(e);
			throw (new RuntimeException("Database Error!#Can't set parameter#setInt("+index+", "+value+")"));
		}
	}
	
	public JdbcExecutor setFloat(int index, String value) {
		if (!bSQLReady) {
			if (Utils.isFloat(value)) {
				bindValueMap.put(index, Utils.parseFloat(value, 0));
				bindTypeMap.put(index, DTYPE.FLOAT);
			} else {
				bindValueMap.put(index, Types.FLOAT);
				bindTypeMap.put(index, DTYPE.NULL);
			}
			return this;
		}
		if (value!=null && Utils.isFloat(value)) {
			return setFloat(index, Utils.parseFloat(value, 0));
		} else {
			return setNull(index, Types.FLOAT);
		}
	}
	
	public JdbcExecutor setFloat(String param, String value) {
		if (!bSQLReady) {
			if (Utils.isFloat(value)) {
				paramsValueMap.put(param, Utils.parseFloat(value, 0));
				paramsTypeMap.put(param, DTYPE.FLOAT);
			} else {
				paramsValueMap.put(param, Types.FLOAT);
				paramsTypeMap.put(param, DTYPE.NULL);
			}
			return this;
		}
		if (value!=null && Utils.isFloat(value)) {
			return setFloat(param, Utils.parseFloat(value, 0));
		} else {
			return setNull(param, Types.FLOAT);
		}
	}
	
	public JdbcExecutor setFloat(String param, String value, float defaultValue) {
		if (!bSQLReady) {
			paramsValueMap.put(param, Utils.parseFloat(value, defaultValue));
			paramsTypeMap.put(param, DTYPE.FLOAT);
			return this;
		}
		for (int index : readParamsIndex(param)) {
			setFloat(index, value, defaultValue);
		}
		return this;
	}
	
	public JdbcExecutor setFloat(int index, String value, float defaultValue) {
		if (!bSQLReady) {
			bindValueMap.put(index, Utils.parseFloat(value, defaultValue));
			bindTypeMap.put(index, DTYPE.FLOAT);
			return this;
		}
		return setFloat(index, Utils.parseFloat(value, defaultValue));
	}
	
	public JdbcExecutor setBlob(String param, Blob value) {
		if (!bSQLReady) {
			throw new RuntimeException("Database Error!#Can't set Blob parameter when using fix value#setBlob("+param+", ...)");
		}
		for (int index : readParamsIndex(param)) {
			setBlob(index, value);
		}
		return this;
	}
	
	public JdbcExecutor setBlob(int index, Blob value) {
		if (!bSQLReady) {
			throw new RuntimeException("Database Error!#Can't set Blob parameter when using fix value#setBlob("+index+", ...)");
		}
		try {
			if (bUsePageQuery) {
				pageCountQuery.setBlob(index, value);
				query.setBlob(index, value);
			} else {
				query.setBlob(index, value);
			}
			bindSQLValueMap.put(index, readParamSQLString(DTYPE.STRING, "blob"));
			return this;
		} catch (Exception e) {
			JdbcExecutorLogger.catching(e);
			throw (new RuntimeException("Database Error!#Can't set parameter#setBlob("+index+", ...)"));
		}
	}
	
	public JdbcExecutor setBinaryStream(String param, InputStream value) {
		if (!bSQLReady) {
			throw new RuntimeException("Database Error!#Can't set Blob parameter when using fix value#setBinaryStream("+param+", ...)");
		}
		for (int index : readParamsIndex(param)) {
			setBinaryStream(index, value);
		}
		return this;
	}
	
	public JdbcExecutor setBinaryStream(int index, InputStream value) {
		if (!bSQLReady) {
			throw new RuntimeException("Database Error!#Can't set Blob parameter when using fix value#setBinaryStream("+index+", ...)");
		}
		try {
			if (bUsePageQuery) {
				pageCountQuery.setBinaryStream(index, value);
				query.setBinaryStream(index, value);
			} else {
				query.setBinaryStream(index, value);
			}
			bindSQLValueMap.put(index, readParamSQLString(DTYPE.STRING, "stream"));
			return this;
		} catch (Exception e) {
			JdbcExecutorLogger.catching(e);
			throw (new RuntimeException("Database Error!#Can't set parameter#setBinaryStream("+index+", ...)"));
		}
	}
	
	public JdbcExecutor setNull(String param, int sqlType) {
		if (!bSQLReady) {
			paramsValueMap.put(param, sqlType);
			paramsTypeMap.put(param, DTYPE.NULL);
			return this;
		}
		for (int index : readParamsIndex(param)) {
			setNull(index, sqlType);
		}
		return this;
	}
	public JdbcExecutor setNull(int index, int sqlType) {
		if (!bSQLReady) {
			bindValueMap.put(index, sqlType);
			bindTypeMap.put(index, DTYPE.NULL);
			return this;
		}
		try {
			if (bUsePageQuery) {
				pageCountQuery.setNull(index, sqlType);
				query.setNull(index, sqlType);
			} else {
				query.setNull(index, sqlType);
			}
			bindSQLValueMap.put(index, readParamSQLString(DTYPE.NULL, "null"));
			return this;
		} catch (Exception e) {
			throw (new RuntimeException("Database Error!#Can't set parameter#setNull("+index+", "+sqlType+")"));
		}
	}

	public String getString(int index) {
		try {
			String value=result.getString(index);
			return value!=null ? value : "";
		}  catch (SQLException e) {
			JdbcExecutorLogger.catching(e);
			throw (new RuntimeException("Database Error!#Can't get data#get(int index):result.getString("+index+");", e));
		}  catch (Exception e){
			JdbcExecutorLogger.catching(e);
			throw (new RuntimeException("Database Error!#Can't get data", e));
		}
	}
	
	public String getString(String index) {
		try {
			String value=result.getString(index);
			return value!=null ? value : "";
		}  catch (SQLException e) {
			JdbcExecutorLogger.catching(e);
			throw (new RuntimeException("Database Error!#Can't get data#get(String index):result.getString(\""+index+"\");", e));
		}  catch (Exception e){
			JdbcExecutorLogger.catching(e);
			throw (new RuntimeException("Database Error!#Can't get data", e));
		}
	}
	
	public int getInt(String index) {
		try {
			return result.getInt(index);
		}  catch (SQLException e) {
			JdbcExecutorLogger.catching(e);
			throw (new RuntimeException("Database Error!#Can't get data#get(String index):result.getInt(\""+index+"\");", e));
		}  catch (Exception e){
			JdbcExecutorLogger.catching(e);
			throw (new RuntimeException("Database Error!#Can't get data", e));
		}
	}
	public int getInt(int index) {
		try {
			return result.getInt(index);
		}  catch (SQLException e) {
			JdbcExecutorLogger.catching(e);
			throw (new RuntimeException("Database Error!#Can't get data#get(String index):result.getInt(\""+index+"\");", e));
		}  catch (Exception e){
			JdbcExecutorLogger.catching(e);
			throw (new RuntimeException("Database Error!#Can't get data", e));
		}
	}

	public long getLong(String index) {
		try {
			return result.getLong(index);
		}  catch (SQLException e) {
			JdbcExecutorLogger.catching(e);
			throw (new RuntimeException("Database Error!#Can't get data#get(String index):result.getInt(\""+index+"\");", e));
		}  catch (Exception e){
			JdbcExecutorLogger.catching(e);
			throw (new RuntimeException("Database Error!#Can't get data", e));
		}
	}
	public long getLong(int index) {
		try {
			return result.getLong(index);
		}  catch (SQLException e) {
			JdbcExecutorLogger.catching(e);
			throw (new RuntimeException("Database Error!#Can't get data#get(String index):result.getLong(\""+index+"\");", e));
		}  catch (Exception e){
			JdbcExecutorLogger.catching(e);
			throw (new RuntimeException("Database Error!#Can't get data", e));
		}
	}
	
	public String getNumString(String index) {
		try {
			String value=result.getString(index);
			if (value==null) {
				return "";
			} else if (value.startsWith(".")) {
				return "0"+value;
			} else if (value.startsWith("-.")) {
				return "-0."+value.substring(2);
			} else {
				return value;
			}
		}  catch (SQLException e) {
			JdbcExecutorLogger.catching(e);
			throw (new RuntimeException("Database Error!#Can't get data#get(String index):result.getString(\""+index+"\");", e));
		}  catch (Exception e){
			JdbcExecutorLogger.catching(e);
			throw (new RuntimeException("Database Error!#Can't get data", e));
		}
	}
	public String getNumString(String index, String format) {
		DecimalFormat df = new DecimalFormat(format);
		try {
			if (result.getDouble(index) == 0.0D) {
				return "";
			} else {
				return df.format(result.getDouble(index));
			}
		}  catch (SQLException e) {
			return getString(index);
		}  catch (Exception e){
			JdbcExecutorLogger.catching(e);
			throw (new RuntimeException("Database Error!#Can't get data", e));
		}
	}

	public String getDateString(String index, String format) {
		try {
			return Utils.dateFormat(getString(index), "yyyy-MM-dd HH:mm:ss", format);
		}  catch (Exception e){
			JdbcExecutorLogger.catching(e);
			throw (new RuntimeException("Database Error!#Can't get data", e));
		}
	}
	
	public java.util.Date getDate(String index) {
		try {
			return result.getTimestamp(index);//Utils.dateParse(result.getString(index), "yyyy-MM-dd HH:mm:ss:S");
		}  catch (SQLException e) {
			JdbcExecutorLogger.catching(e);
			throw (new RuntimeException("Database Error!#Can't get data#get(String index):result.getDate(\""+index+"\");", e));
		}  catch (Exception e){
			JdbcExecutorLogger.catching(e);
			throw (new RuntimeException("Database Error!#Can't get data", e));
		}
	}
	public java.util.Date getDate(int index) {
		try {
			return result.getTimestamp(index); //Utils.dateParse(result.getString(index), "yyyy-MM-dd HH:mm:ss:S");
		}  catch (SQLException e) {
			JdbcExecutorLogger.catching(e);
			throw (new RuntimeException("Database Error!#Can't get data#get(String index):result.getDate(\""+index+"\");", e));
		}  catch (Exception e){
			JdbcExecutorLogger.catching(e);
			throw (new RuntimeException("Database Error!#Can't get data", e));
		}
	}
	
	public float getFloat(String index) {
		try {
			return result.getFloat(index);
		}  catch (SQLException e) {
			JdbcExecutorLogger.catching(e);
			throw (new RuntimeException("Database Error!#Can't get data#get(String index):result.getFloat(\""+index+"\");", e));
		}  catch (Exception e){
			JdbcExecutorLogger.catching(e);
			throw (new RuntimeException("Database Error!#Can't get data", e));
		}
	}
	
	public float getFloat(int index) {
		try {
			return result.getFloat(index);
		}  catch (SQLException e) {
			JdbcExecutorLogger.catching(e);
			throw (new RuntimeException("Database Error!#Can't get data#get(String index):result.getFloat(\""+index+"\");", e));
		}  catch (Exception e){
			JdbcExecutorLogger.catching(e);
			throw (new RuntimeException("Database Error!#Can't get data", e));
		}
	}
	
	public InputStream getBinaryStream(String index) {
		try {
			return result.getBinaryStream(index);
		}  catch (SQLException e) {
			JdbcExecutorLogger.catching(e);
			throw (new RuntimeException("Database Error!#Can't get data#get(String index):result.getBinaryStream(\""+index+"\");", e));
		}  catch (Exception e){
			JdbcExecutorLogger.catching(e);
			throw (new RuntimeException("Database Error!#Can't get data", e));
		}
	}
	
	public InputStream getBinaryStream(int index) {
		try {
			return result.getBinaryStream(index);
		}  catch (SQLException e) {
			JdbcExecutorLogger.catching(e);
			throw (new RuntimeException("Database Error!#Can't get data#get(String index):result.getBinaryStream(\""+index+"\");", e));
		}  catch (Exception e){
			JdbcExecutorLogger.catching(e);
			throw (new RuntimeException("Database Error!#Can't get data", e));
		}
	}
	
	public Blob getBlob(String index) {
		try {
			return result.getBlob(index);
		}  catch (SQLException e) {
			JdbcExecutorLogger.catching(e);
			throw (new RuntimeException("Database Error!#Can't get data#get(String index):result.getBlob(\""+index+"\");", e));
		}  catch (Exception e){
			JdbcExecutorLogger.catching(e);
			throw (new RuntimeException("Database Error!#Can't get data", e));
		}
	}

	public Blob getBlob(int index) {
		try {
			return result.getBlob(index);
		}  catch (SQLException e) {
			JdbcExecutorLogger.catching(e);
			throw (new RuntimeException("Database Error!#Can't get data#get(String index):result.getBlob(\""+index+"\");", e));
		}  catch (Exception e){
			JdbcExecutorLogger.catching(e);
			throw (new RuntimeException("Database Error!#Can't get data", e));
		}
	}

	public boolean readRow() {
		if (result==null) {
			throw (new RuntimeException("Database Error!#None of query have been executed."));
		}
		try {
			return result.next();
		} catch (SQLException e) {
			JdbcExecutorLogger.catching(e);
			throw (new RuntimeException("Database Error!#Can't read row#readRow():result.getRow();", e));
		}  catch (Exception e){
			JdbcExecutorLogger.catching(e);
			throw (new RuntimeException("Database Error!#Can't read row", e));
		}
	}

	public LinkedHashMap<String, Object> readPageInfo() {
		LinkedHashMap<String, Object> map=new LinkedHashMap<String, Object>();
		map.put("pgSize", pgSize);
		map.put("pgCurrent", pgCurrent);
		map.put("pgCount", pgCount);
		map.put("rowTotal", rowTotal);
		return map;
	}
	
	public int getRowTotal() {
		return Math.max(0, rowTotal);
	}

	public int getPgCount() {
		return pgCount;
	}

	public int getPgSize() {
		return pgSize;
	}

	public int getPgCurrent() {
		return pgCurrent;
	}

	@Deprecated
	public String buildPgString(String urlStr) {
		return buildPgString(10, urlStr);
	}

	@Deprecated
	public String buildPgString(int style, String urlStr) {
		StringBuilder sb = new StringBuilder();
		if (rowTotal < 1) {
			sb=null;
			return "";
		}
		String strURL = urlStr.indexOf("__") != -1 ? urlStr : "?" + urlStr+ "&pgCurrent=__pgCurrent__";
		if (style == 1) {
			sb.append("<table border=\"0\" cellpadding=\"0\" cellspacing=\"0\"><form name=\"pgSelectForm\" method=\"get\" action=\"\"><tr><td style=\"height:18pt; line-height:13pt;\">共"+ rowTotal+ "  条信息 第 "+ pgCurrent+ " 页 共 "+ pgCount+ " 页 <input type=button onclick=\"location='"+ strURL.replaceAll("__pgCurrent__", ""+ (pgCurrent - 1))+ "'\" value=上一页 style=\"border:#cccccc 1px solid; background-color:transparent; height:16pt\" class=showpg> <input type=button onclick=\"location='"+ strURL.replaceAll("__pgCurrent__", ""+(pgCurrent + 1))+ "'\" value=下一页 class=showpg style=\"border:#cccccc 1px solid; background-color:transparent; height:16pt\"> 跳到 ");
			sb.append("<input type=text size=2 id=pgSelectFormPgCurrent name=pgSelectFormPgCurrent style=\"border:#cccccc 1px solid; background-color:transparent; height:16pt\"> <input type=button name=Submit onClick=\"location='"+ strURL.replaceAll("__pgCurrent__","'+this.form.pgSelectFormPgCurrent.value+'")+ "'\" value=GO style='border:#cccccc 1px solid; background-color:transparent;font-family:arial; height:16pt'></td></form></tr></table>");
		} else if (style == 2) {
			sb.append("<a href='"+ strURL.replaceAll("__pgCurrent__", "1")+ "'>首页</a> <a href='"+ strURL.replaceAll("__pgCurrent__", ""+(pgCurrent - 1))+ "'>上页</a> "+ pgCurrent+ " / "+ pgCount+ " ("+ rowTotal+ ") <a href='"+ strURL.replaceAll("__pgCurrent__", ""+(pgCurrent + 1))+ "'>下页</a> <a href='"+ strURL.replaceAll("__pgCurrent__", "-1")+ "'>尾页</a></font>");
		} else {
			int i = pgCurrent - 5;
			if (i + 10 >= pgCount) {
				i = pgCurrent - 10;
			}
			if (i < 1) {
				i = 1;
			} else {
				sb.append(" <a href='"+ strURL.replaceAll("__pgCurrent__", ""+((i - 5 > 0) ? i - 5 : 1)) + "'><b>...</b></a> ");
			}
			int j = i + 10;
			for (; i <= j && i <= pgCount; i++) {
				if (i != pgCurrent) {
					sb.append(" <a href='"+ strURL.replaceAll("__pgCurrent__", ""+i)+ "'><b>" + i + "</b></a> ");
				} else {
					sb.append(" <b><font color=#ff0000>" + i + "</font></b> ");
				}
			}
			if (j < pgCount) {
				sb.append(" <a href='"+ strURL.replaceAll("__pgCurrent__",""+((j + 5 <= pgCount) ? j + 5 : pgCount)) + "'><b>...</b></a> ");
			}
			sb.append("  [第<b>" + pgCurrent + "</b>页/<b>共" + pgCount+ "页</b> (共<b>" + rowTotal + "</b>条信息)] 点击数字跳到相应的页");
		}
		String value=sb.toString();
		sb.delete(0, sb.length());
		sb=null;
		return value;
	}
	
	/**
	 * 处理SQL——只返回行数
	 * @param sql
	 * @param dbType
	 * @return
	 */
	public static String parseSqlForCountRows(String sql, String dbType) {
		return PagerUtils.count(sql, dbType);
	}
	
	public String parseSqlForCountRows(String sql) {
		return parseSqlForCountRows(sql, dbType);
	}
	
	/**
	 * 处理SQL——转换表名前辍：
	 * 		将"空格+tb_"或者“空格+t_”的字符串视为表名，如果存在表前辍而自动增加表前辍
	 * @param sql
	 */
	public static String parseSqlForTablePrefix(String sql, String tablePrefix) {
		if (tablePrefix==null || tablePrefix.length()<1) {
			return sql;
		}
		int l=sql.length();
		StringBuffer buffer=new StringBuffer();
		char a='-', b='-', c='-', d='-'; //最后的四个字符
		boolean inString=false; //当前是否在字符串范围内
		for (int i=0; i<l; i++) {
			a=b; b=c; c=d; d=sql.charAt(i);	//a, b, c, d顺序前移一个字符
			buffer.append(d); 
			if (d=='_') {//最新一个字符是“_”
				if (!inString) {//不再字符串范围内
					if (((a==' ' || a=='	' ) && b=='t' && c=='b') || ((b==' '  || b=='	') && c=='t')) {//遇到一个“ tb_”或“ t_”的结构（注意是空格或TAB开头） ，这里认为是遇到了一个表名
						int offset=(b==' ' ? 2 : 3); //插入表前辍的位置（如果倒数第3个字符<b>是空格，前插入至倒数第2位，否则应该插入至倒数第3位）
						buffer.insert(buffer.length()-offset, tablePrefix);
					}
				}
			} else if (d=='\'') {
				inString=!inString;
			}
		}
		return buffer.toString();
	}
	
	/**
	 * 处理SQL——只选取前x行数据
	 * @param sql
	 * @param maxRows
	 * @return
	 */
	public static String parseSqlForTopRows(String sql, String dbType, int maxRows) {
		if (maxRows<1) {
			return sql;
		}
		if (dbType.equalsIgnoreCase(JdbcConstants.POSTGRESQL)) {
			return sql+" limit "+maxRows;

		} else if (dbType.equalsIgnoreCase(JdbcConstants.MYSQL) || dbType.equalsIgnoreCase(JdbcConstants.MARIADB)) {
			return sql+" limit 0, "+maxRows;

		} else if (dbType.equalsIgnoreCase(JdbcConstants.ORACLE)) {
			return "select a.*, rownum rn from ("+sql+") a where rownum <= "+maxRows;
			
		} else if (dbType.equalsIgnoreCase(JdbcConstants.SQL_SERVER)) {
			return "select top "+maxRows+" * from("+sql+")";
			
		} else {
			return PagerUtils.limit(sql, dbType, 0, maxRows);
		}
	}
	
	public String parseSqlForTopRows(String sql, int maxRows) {
		return parseSqlForTopRows(sql, dbType, maxRows);
	}
	
	/**
	 * 处理SQL——只选取特定页的数据
	 * @param sql
	 * @param offsetRow
	 * @param maxRows
	 * @return
	 * @throws SQLException 
	 */
	public static String parseSqlForPage(String sql, String dbType, int pageSize, int pageCurrent) {
		if (pageSize<1) {
			return sql;
		}
		int maxRows=pageSize;
		int offsetRow=Math.max((pageCurrent-1)*pageSize, 0);
		
		if (dbType.equalsIgnoreCase(JdbcConstants.POSTGRESQL)) {
			return sql+" limit "+maxRows+" offset "+offsetRow;

		} else if (dbType.equalsIgnoreCase(JdbcConstants.MYSQL) || dbType.equalsIgnoreCase(JdbcConstants.MARIADB)) {
			return sql+" limit "+offsetRow+", "+maxRows;

		} else if (dbType.equalsIgnoreCase(JdbcConstants.ORACLE)) {
			return "select * from (select a.*, rownum rn from ("+sql+") a where rownum <="+(offsetRow+maxRows)+") b where rn >"+offsetRow;
		
		} else {
			return PagerUtils.limit(sql, dbType, offsetRow, maxRows);
		}
	}
	
	public String parseSqlForPage(String sql, int pageSize, int pageCurrent) {
		return parseSqlForPage(sql, dbType, pageSize, pgCurrent);
	}
	
	/**
	 * 处理不同数据库连接字符串方法的不一致，Oracle和PostgreSQL用||，而MySQL用concat函数
	 * @param a
	 * @param b
	 * @return
	 */
	public static String sql_joinString(String dbType, String... arg) {
		if (arg.length<2) {
			return arg[0];
		}
		StringBuilder sql=new StringBuilder();
		if (dbType.equalsIgnoreCase(JdbcConstants.MYSQL) || dbType.equalsIgnoreCase(JdbcConstants.MARIADB) ) {
			sql.append("concat(");
			for (int i=0; i<arg.length; i++) {
				sql.append(arg[i]).append(",");
			}
			sql.delete(sql.length()-1, sql.length());
			sql.append(")");
			return sql.toString();
		} else {
			for (int i=0; i<arg.length; i++) {
				sql.append(arg[i]).append("||");
			}
			sql.delete(sql.length()-2,  sql.length());
			return sql.toString();
		}
	}
	
	public String sql_joinString(String... args) {
		return sql_joinString(dbType, args);
	}

	/**
	 * 数据库方言：调用存储过程的语法
	 * @return
	 */
	public static String sql_callProcture(String dbType) {
		if (dbType.equalsIgnoreCase(JdbcConstants.MYSQL) || dbType.equalsIgnoreCase(JdbcConstants.MARIADB) || dbType.equalsIgnoreCase(JdbcConstants.POSTGRESQL)) {
			return "call";
		} else if (dbType.equalsIgnoreCase(JdbcConstants.ORACLE)) {
			return "execute";
		} else {
			return "execute";
		}
	}
	
	public String sql_callProcture() {
		return sql_callProcture(dbType);
	}

	/**
	 * 数据库方言：空值判断函数，调用示例nvl(a, 'xxx')
	 * @return
	 */
	public static String sql_nvl(String dbType) {
		if (dbType.equalsIgnoreCase(JdbcConstants.MYSQL) || dbType.equalsIgnoreCase(JdbcConstants.MARIADB)) {
			return "ifnull";
		} else if (dbType.equalsIgnoreCase(JdbcConstants.POSTGRESQL)) {
			return "coalesce";
		} else if (dbType.equalsIgnoreCase(JdbcConstants.ORACLE)) {
			return "nvl";
		} else {
			return "nvl";
		}
	}
	
	public String sql_nvl() {
		return sql_nvl(dbType);
	}
	
	/**
	 * 数据库方言：当前时间
	 * @return
	 */
	public static String sql_now(String dbType) {		
		if (dbType.equalsIgnoreCase(JdbcConstants.MYSQL) || dbType.equalsIgnoreCase(JdbcConstants.MARIADB)) {
			return "now()";
		} else if (dbType.equalsIgnoreCase(JdbcConstants.POSTGRESQL)) {
			return "current_timestamp";
		} else if (dbType.equalsIgnoreCase(JdbcConstants.ORACLE)) {
			return "sysdate";
		} else {
			return "sysdate";
		}
	}

	public String sql_now() {	
		return sql_now(dbType);
	}
	
	/**
	 * 数据库方言：字符串转换为时间
	 * @param time
	 * @param format
	 * @return
	 */	
	public static String sql_toDate(String time, String format, String dbType) {		
		if (dbType.equalsIgnoreCase(JdbcConstants.MYSQL) || dbType.equalsIgnoreCase(JdbcConstants.MARIADB)) {
			return "str_to_date("+time+", "+format+")";
		} else if (dbType.equalsIgnoreCase(JdbcConstants.ORACLE) || dbType.equalsIgnoreCase(JdbcConstants.POSTGRESQL)) {
			return "to_date("+time+", "+format+")";
		} else {
			return "to_date("+time+", "+format+")";
		}
	}
	
	public String sql_toDate(String time, String format) {
		return sql_toDate(time, format, dbType);
	}
	
}
