package tadsuite.mvc.jdbc;

import java.io.InputStream;
import java.math.BigDecimal;
import java.sql.Blob;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import javax.sql.DataSource;

import tadsuite.mvc.utils.NameMapper;
import tadsuite.mvc.utils.NameMapper.MAP_POLICY;

/**该类使用DataSource数据源
 * 对JdbcExcutor类进行了一些封装
 * 注意：本类有成员变量，不能作为单例的Bean使用。
 * 该类创建后可以可以不考虑关闭（因为每个方法执行后会关闭打开的连接）
 */

public class Jdbc {
	
	public final static String version="v1.0.0";
	public final static String versionNote="20151011";
	public static enum RESULT_TYPE {
		SINGLE_CELL_OBJ, SINGLE_ROW_MAP, SINGLE_COLUMN_ArrayList, ArrayList, PAGED_ArrayList;
	}
	//public Logger jdbcLogger=LogFactory.getLogger(Constants.LOGGER_NAME_JDBC);
	public JdbcExecutor executor;
	
	public Jdbc() {
		executor=new JdbcExecutor();
	}
	
	public Jdbc(String dataSourceConfigName) {
		executor=new JdbcExecutor(dataSourceConfigName);
	}
	
	public Jdbc(DataSource datasource, String dbType) {
		executor=new JdbcExecutor(datasource, dbType);
	}
	
	public Jdbc(DataSource datasource, String dbType, String tablePrefix) {
		executor=new JdbcExecutor(datasource, dbType, tablePrefix);
	}
	
	public Jdbc(JdbcExecutor jdbcExecutor) {
		executor=jdbcExecutor;
	}
	
	/**
	 * 使用相同的数据源，再创建一个对象，并与当前对象建立关联（当前对象关闭时将自动关闭）
	 * 将使用不同的Connection及Statement等对象
	 */
	public Jdbc clone() {
		Jdbc newObject=new Jdbc(executor.clone());
		return newObject;
	}

	/**
	 * 再创建一个对象，并与当前对象建立关联（当前对象关闭时将自动关闭）
	 * @param dataSourceConfigName
	 * @return
	 */
	public Jdbc create(String dataSourceConfigName) {
		Jdbc newObject=new Jdbc(executor.create(dataSourceConfigName));
		return newObject;
	}

	/**
	 * 再创建一个对象，并与当前对象建立关联（当前对象关闭时将自动关闭）
	 * @param dataSourceConfigName
	 * @param tablePrefix
	 * @return
	 */
	public Jdbc create(String dataSourceConfigName, String tablePrefix) {
		Jdbc newObject=new Jdbc(executor.create(dataSourceConfigName));
		newObject.setTablePrefix(tablePrefix);
		return newObject;
	}

	
	/**
	 * 再创建一个对象，并与当前对象建立关联（当前对象关闭时将自动关闭）
	 * @param dataSourceConfigName
	 * @param tablePrefix
	 * @return
	 */
	public Jdbc create(DataSource datasource, String dbType) {
		Jdbc newObject=new Jdbc(executor.create(datasource, dbType));
		return newObject;
	}

	
	/**
	 * 再创建一个对象，并与当前对象建立关联（当前对象关闭时将自动关闭）
	 * @param dataSourceConfigName
	 * @param tablePrefix
	 * @return
	 */
	public Jdbc create(DataSource datasource, String dbType, String tablePrefix) {
		Jdbc newObject=new Jdbc(executor.create(datasource, dbType));
		newObject.setTablePrefix(tablePrefix);
		return newObject;
	}
	
	/**
	 * 注意：执行SQL的所有方法中只有query方法不即时关闭连接，其它执行SQL的方法都将自动关闭连接
	 */
	public void close() {
		executor.close();
	}
	
	public Jdbc query(String sql) {
		return _query(-1, 1, sql, null, new Object[]{});
	}
	
	public Jdbc query(String sql, JdbcParams params) {
		return _query(-1, 1, sql, params, new Object[]{});
	}
	
	public Jdbc query(String sql, Object... args) {
		return _query(-1, 1, sql, null, args);
	}

	@SuppressWarnings("unused")
	@Deprecated
	/**
	 * 不能同时使用JdbcParams params和 Object... args参数（但此方法必须定义，否则将会错误地调用query(String sql, Object... args)）
	 * @param sql
	 * @param params
	 * @param args
	 * @return
	 */
	public Jdbc query(String sql, JdbcParams params, Object... args) {
		throw new RuntimeException("不能同时使用JdbcParams params和 Object... args参数");
	}

	public Jdbc query(int maxRows, String sql) {
		return _query(maxRows, 1, sql, null, new Object[]{});
	}
	
	public Jdbc query(int maxRows, String sql, JdbcParams params) {
		return _query(maxRows, 1, sql, params, new Object[]{});
	}
	
	public Jdbc query(int maxRows, String sql, Object... args) {
		return _query(maxRows, 1, sql, null, args);
	}
	
	@SuppressWarnings("unused")
	@Deprecated
	/**
	 * 不能同时使用JdbcParams params和 Object... args参数（但此方法必须定义，否则将会错误地调用query(int maxRows, String sql, Object... args)）
	 * @param sql
	 * @param params
	 * @param args
	 * @return
	 */
	public Jdbc query(int maxRows, String sql, JdbcParams params, Object... args) {
		throw new RuntimeException("不能同时使用JdbcParams params和 Object... args参数");
	}
	
	public Jdbc query(int pgSize, int pgCurrent, String sql) {
		return _query(pgSize, pgCurrent, sql, null, new Object[]{});
	}
	
	public Jdbc query(int pgSize, int pgCurrent, String sql, JdbcParams params) {
		return _query(pgSize, pgCurrent, sql, params, new Object[]{});
	}
	
	public Jdbc query(int pgSize, int pgCurrent, String sql, Object... args) {
		return _query(pgSize, pgCurrent, sql, null, args);
	}
	
	@SuppressWarnings("unused")
	@Deprecated
	/**
	 * 不能同时使用JdbcParams params和 Object... args参数（但此方法必须定义，否则将会错误地调用query(int pgSize, int pgCurrent, String sql, Object... args)
	 * @param sql
	 * @param params
	 * @param args
	 * @return
	 */
	public Jdbc query(int pgSize, int pgCurrent, String sql, JdbcParams params, Object... args) {
		throw new RuntimeException("不能同时使用JdbcParams params和 Object... args参数");
	}
	
	private Jdbc _query(int pgSize, int pgCurrent, String sql, JdbcParams params, Object... args) {
		executor.createQuery(pgSize, pgCurrent, sql);
		if (params!=null) {
			executor.setJdbcParams(params);
			if (args.length>0) {
				throw new RuntimeException("不能同时使用JdbcParams params和 Object... args参数");
			}
		} else if (args.length>0) {
			executor.setArgArray(args);
		}
		executor.executeQuery();
		return this;
	}
	

///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	
	/**
	 * 查询SQL，返回单个值（第一行第一列）
	 * @param sql
	 * @param requiredType
	 * @return
	 */
	public <T> T queryObject(Class<T> requiredType, String sql) {
		return queryObject(requiredType, sql, null, new Object[]{});
	}
	
	/**
	 * 查询SQL，返回单个值（第一行第一列），使用“?”绑定参数
	 * @param sql
	 * @param requiredType
	 * @param args
	 * @return
	 */
	public <T> T queryObject(Class<T> requiredType, String sql, Object... args) {
		return queryObject(requiredType, sql, null, args);
	}
	
	/**
	 * 查询SQL，返回单个值（第一行第一列），使用“:xxx”方式绑定参数
	 * @param sql
	 * @param requiredType
	 * @param params
	 * @return
	 */
	public <T> T queryObject(Class<T> requiredType, String sql, JdbcParams params) {
		return queryObject(requiredType, sql, params, new Object[]{});
	}
	
	/**
	 * 查询SQL，返回单个值（第一行第一列），使用“?”、“:xxx”方式绑定参数，或者不绑定参数
	 * @param sql
	 * @param requiredType
	 * @param params
	 * @param args
	 * @return
	 */
	private <T> T queryObject(Class<T> requiredType, String sql, JdbcParams params, Object... args) {
		_query(1, 1, sql, params, args);
		if (executor.readRow()) {
			return executor.readCell(1, requiredType);
		}
		return null;
	}
	
	/**
	 * 检查一个SQL是否有执行结果（即是否至少有一行结果集）
	 * @param sql
	 * @return
	 */
	public boolean hasRow(String sql) {
		return hasRow(sql, null, new Object[]{});
	}
	
	/**
	 * 检查一个SQL是否有执行结果（即是否至少有一行结果集）
	 * @param sql
	 * @return
	 */
	public boolean hasRow(String sql, JdbcParams params) {
		return hasRow(sql, params, new Object[]{});
	}
	
	/**
	 * 检查一个SQL是否有执行结果（即是否至少有一行结果集）
	 * @param sql
	 * @return
	 */
	public boolean hasRow(String sql, Object... args) {
		return hasRow(sql, null, args);
	}
	
	/**
	 * 检查一个SQL是否有执行结果（即是否至少有一行结果集）
	 * @param sql
	 * @return
	 */
	private boolean hasRow(String sql, JdbcParams params, Object... args) {
		_query(1, 1, sql, params, args);
		return executor.readRow();
	}
	
	
///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	/**
	 * 查询SQL，返回单行（第一行）的Map
	 * @param sql
	 * @return
	 */
	public LinkedHashMap<String, Object> queryRow(String sql) {
		return queryRow(MAP_POLICY.LOWER_CASE, sql, null, new Object[]{});
	}
	
	public LinkedHashMap<String, Object> queryRow(MAP_POLICY colNameMapPolicy, String sql) {
		return queryRow(colNameMapPolicy, sql, null, new Object[]{});
	}
	
	/**
	 * 查询SQL，返回单行（第一行）的Map，使用“?”绑定参数
	 * @param sql
	 * @param args
	 * @return
	 */
	public LinkedHashMap<String, Object> queryRow(String sql, Object... args) {
		return queryRow(MAP_POLICY.LOWER_CASE, sql, null, args);
	}
	
	public LinkedHashMap<String, Object> queryRow(MAP_POLICY colNameMapPolicy, String sql, Object... args) {
		return queryRow(colNameMapPolicy, sql, null, args);
	}
	
	/**
	 * 查询SQL，返回单行（第一行）的Map，使用“:xxx”方式绑定参数
	 * @param sql
	 * @param params
	 * @return
	 */
	public LinkedHashMap<String, Object> queryRow(String sql, JdbcParams params) {
		return queryRow(MAP_POLICY.LOWER_CASE, sql, params, new Object[]{});
	}
	
	public LinkedHashMap<String, Object> queryRow(MAP_POLICY colNameMapPolicy, String sql, JdbcParams params) {
		return queryRow(colNameMapPolicy, sql, params, new Object[]{});
	}
	
	/**
	 *  查询SQL，返回单行（第一行）的Map，使用“:xxx”方式绑定参数，使用“?”、“:xxx”方式绑定参数，或者不绑定参数
	 * @param sql
	 * @param params
	 * @param args
	 * @return
	 */
	private LinkedHashMap<String, Object> queryRow(MAP_POLICY colNameMapPolicy, String sql, JdbcParams params, Object... args) {
		_query(1, 1, sql, params, args);
		if (executor.readRow()) {
			return executor.readResultMap(new NameMapper(colNameMapPolicy));
		}
		return null;
	}
	
	
///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	/**
	 * 查询SQL，返回单列（第一列）的ArrayList
	 * @param sql
	 * @param elementType
	 * @return
	 */
	public <T> ArrayList<T> queryColumn(Class<T> elementType, String sql) {
		return queryColumn(elementType, sql, null, new Object[]{});
	}
	
	/**
	 * 查询SQL，返回单列（第一列）的ArrayList，使用“?”绑定参数
	 * @param sql
	 * @param elementType
	 * @param args
	 * @return
	 */
	public <T> ArrayList<T> queryColumn(Class<T> elementType, String sql, Object... args) {
		return queryColumn(elementType, sql, null, args);
	}
	
	/**
	 * 查询SQL，返回单列（第一列）的ArrayList，使用“:xxx”方式绑定参数
	 * @param sql
	 * @param elementType
	 * @param params
	 * @return
	 */
	public <T> ArrayList<T> queryColumn(Class<T> elementType, String sql, JdbcParams params) {
		return queryColumn(elementType, sql, params, new Object[]{});
	}
	
	/**
	 * 查询SQL，返回单列（第一列）的ArrayList，使用“?”、“:xxx”方式绑定参数，或者不绑定参数
	 * @param sql
	 * @param elementType
	 * @param params
	 * @param args
	 * @return
	 */
	private <T> ArrayList<T> queryColumn(Class<T> elementType, String sql, JdbcParams params, Object... args) {
		_query(-1, 1, sql, params, args);
		ArrayList<T> t=new ArrayList<T>();
		while (executor.readRow()) {
			t.add(executor.readCell(1, elementType));
		}
		return t;
	}
	
	
///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	/**
	 * 查询SQL，返回一个ArrayList-Map结果集
	 * @param sql
	 * @return
	 */
	public ArrayList<LinkedHashMap<String, Object>> queryList(String sql) {
		return queryList(-1, MAP_POLICY.LOWER_CASE, sql, null, new Object[]{});
	}

	public ArrayList<LinkedHashMap<String, Object>> queryList(MAP_POLICY colNameMapPolicy, String sql) {
		return queryList(-1, colNameMapPolicy, sql, null, new Object[]{});
	}
	
	/**
	 * 查询SQL，返回一个ArrayList-Map结果集，使用“?”绑定参数
	 * @param sql
	 * @param args
	 * @return
	 */
	public ArrayList<LinkedHashMap<String, Object>> queryList(String sql, Object... args) {
		return queryList(-1, MAP_POLICY.LOWER_CASE, sql, null, args);
	}

	public ArrayList<LinkedHashMap<String, Object>> queryList(MAP_POLICY colNameMapPolicy, String sql, Object... args) {
		return queryList(-1, colNameMapPolicy, sql, null, args);
	}
	
	/**
	 * 查询SQL，返回一个ArrayList-Map结果集，使用“:xxx”方式绑定参数
	 * @param sql
	 * @param params
	 * @return
	 */
	public ArrayList<LinkedHashMap<String, Object>> queryList(String sql, JdbcParams params) {
		return queryList(-1, MAP_POLICY.LOWER_CASE, sql, params, new Object[]{});
	}

	public ArrayList<LinkedHashMap<String, Object>> queryList(MAP_POLICY colNameMapPolicy, String sql, JdbcParams params) {
		return queryList(-1, colNameMapPolicy, sql, params, new Object[]{});
	}
	
	/**
	 * 查询SQL，返回前x行结果集
	 * @param maxRows
	 * @param sql
	 * @return
	 */
	public ArrayList<LinkedHashMap<String, Object>> queryList(int maxRows, String sql) {
		return queryList(maxRows, MAP_POLICY.LOWER_CASE, sql, null, new Object[]{});
	}

	public ArrayList<LinkedHashMap<String, Object>> queryList(int maxRows, MAP_POLICY colNameMapPolicy, String sql) {
		return queryList(maxRows, colNameMapPolicy, sql, null, new Object[]{});
	}
	
	/**
	 * 查询SQL，返回前x行结果集，使用“?”绑定参数
	 * @param maxRows
	 * @param sql
	 * @param args
	 * @return
	 */
	public ArrayList<LinkedHashMap<String, Object>> queryList(int maxRows, String sql, Object... args) {
		return queryList(maxRows, MAP_POLICY.LOWER_CASE, sql, null, args);
	}

	public ArrayList<LinkedHashMap<String, Object>> queryList(int maxRows, MAP_POLICY colNameMapPolicy, String sql, Object... args) {
		return queryList(maxRows, colNameMapPolicy, sql, null, args);
	}
	
	/**
	 * 查询SQL，返回前x行结果集，使用“:xxx”方式绑定参数
	 * @param maxRows
	 * @param sql
	 * @param params
	 * @return
	 */
	public ArrayList<LinkedHashMap<String, Object>> queryList(int maxRows, String sql, JdbcParams params) {
		return queryList(maxRows, MAP_POLICY.LOWER_CASE, sql, params, new Object[]{});
	}
	
	public ArrayList<LinkedHashMap<String, Object>> queryList(int maxRows, MAP_POLICY colNameMapPolicy, String sql, JdbcParams params) {
		return queryList(maxRows, colNameMapPolicy, sql, params, new Object[]{});
	}
	
	/**
	 * 查询SQL，返回前x行结果集，使用“?”、“:xxx”方式绑定参数，或者不绑定参数
	 * @param maxRows
	 * @param sql
	 * @param params
	 * @param args
	 * @return
	 */
	private ArrayList<LinkedHashMap<String, Object>> queryList(int maxRows, MAP_POLICY colNameMapPolicy, String sql, JdbcParams params, Object... args) {
		_query(maxRows, 1, sql, params, args);
		ArrayList<LinkedHashMap<String, Object>> t=new ArrayList<LinkedHashMap<String, Object>>();
		NameMapper jdbcRowMapper=new NameMapper(colNameMapPolicy);
		while (executor.readRow()) {
			t.add(executor.readResultMap(jdbcRowMapper));
		}
		return t;
	}
	
///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	/**
	 * 查询SQL，返回指定页的结果集
	 * @param pgSize
	 * @param pgCurrent
	 * @param sql
	 * @return
	 */
	public JdbcPage queryPage(int pgSize, int pgCurrent, String sql) {
		return queryPage(pgSize, pgCurrent, MAP_POLICY.LOWER_CASE, sql, null, new Object[]{});
	}
	
	public JdbcPage queryPage(int pgSize, int pgCurrent, MAP_POLICY colNameMapPolicy, String sql) {
		return queryPage(pgSize, pgCurrent, colNameMapPolicy, sql, null, new Object[]{});
	}
	
	/**
	 * 查询SQL，返回指定页的结果集，使用“?”绑定参数
	 * @param pgSize
	 * @param pgCurrent
	 * @param sql
	 * @param args
	 * @return
	 */
	public JdbcPage queryPage(int pgSize, int pgCurrent, String sql, Object... args) {
		return queryPage(pgSize, pgCurrent, MAP_POLICY.LOWER_CASE, sql, null, args);
	}
	
	public JdbcPage queryPage(int pgSize, int pgCurrent, MAP_POLICY colNameMapPolicy, String sql, Object... args) {
		return queryPage(pgSize, pgCurrent, colNameMapPolicy, sql, null, args);
	}
	
	/**
	 * 查询SQL，返回指定页的结果集，使用“:xxx”方式绑定参数
	 * @param pgSize
	 * @param pgCurrent
	 * @param sql
	 * @param params
	 * @return
	 */
	public JdbcPage queryPage(int pgSize, int pgCurrent, String sql, JdbcParams params) {
		return queryPage(pgSize, pgCurrent, MAP_POLICY.LOWER_CASE, sql, params, new Object[]{});
	}
	
	public JdbcPage queryPage(int pgSize, int pgCurrent, MAP_POLICY colNameMapPolicy, String sql, JdbcParams params) {
		return queryPage(pgSize, pgCurrent, colNameMapPolicy, sql, params, new Object[]{});
	}
	
	/**
	 * 查询SQL，并进行分页处理，返回指定页的结果集，，使用“?”、“:xxx”方式绑定参数，或者不绑定参数
	 * @param pgSize
	 * @param pgCurrent
	 * @param sql
	 * @param params
	 * @param args
	 * @return
	 */
	private JdbcPage queryPage(int pgSize, int pgCurrent, MAP_POLICY colNameMapPolicy, String sql, JdbcParams params, Object... args) {
		/*String countSql=executor.parseSqlForCountRows(sql);
		int rowCount=queryObject(Integer.class, countSql, params, args);
		
		JdbcPage page=new JdbcPage(rowCount, pgSize, pgCurrent);
		String pagedSql=executor.parseSqlForPage(sql, page.getPageSize(), page.getCurrentPage());
		
		query(pagedSql, params, args);//注意，这里不调用jdbcExcutor.createQuery(pgSize, pgCurrent, strSQL)方法，是因为方法还会重新计算rowCount，并不适用于这里。
		
		ArrayList<LinkedHashMap<String, Object>> dataArrayList=new ArrayList<LinkedHashMap<String, Object>>();
		JdbcRowMapper jdbcRowMapper=new JdbcRowMapper(colNameMapPolicy);
		
		while (executor.readRow()) {
			dataArrayList.add(executor.readResultMap(jdbcRowMapper));
		}
		page.setDataList(dataArrayList);
		*/
		_query(pgSize, pgCurrent, sql, params, args);//注意，这里不调用jdbcExcutor.createQuery(pgSize, pgCurrent, strSQL)方法，是因为方法还会重新计算rowCount，并不适用于这里。
		
		JdbcPage page=new JdbcPage(executor.getRowTotal(), executor.getPgSize(), executor.getPgCurrent());
		
		ArrayList<LinkedHashMap<String, Object>> dataArrayList=new ArrayList<LinkedHashMap<String, Object>>();
		NameMapper jdbcRowMapper=new NameMapper(colNameMapPolicy);
		
		while (executor.readRow()) {
			dataArrayList.add(executor.readResultMap(jdbcRowMapper));
		}
		page.setDataList(dataArrayList);
		return page;
	}
	
///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	/**
	 * 向数据库表中插入记录，指定表名，给定表名，根据params自动生成SQL
	 * @param tableName
	 * @param params
	 * @return
	 */
	public int insert(String tableName, JdbcParams params) {
		if (params.size()<1) {
			throw new RuntimeException("parameter map is empty!");
		}
		StringBuffer sql=new StringBuffer("insert into ");
		StringBuffer sqlValues=new StringBuffer(" values(");
		sql.append(tableName.startsWith(executor.tablePrefix) ? tableName : executor.tablePrefix+tableName).append("(");
		for (String columnName : params.keySet()) {
			sql.append(columnName).append(", ");
			sqlValues.append("${").append(columnName).append("}, ");
		}
		sql.delete(sql.length()-2, sql.length()).append(") ");
		sqlValues.delete(sqlValues.length()-2, sqlValues.length()).append(")");
		sql.append(sqlValues);
		return execute(sql.toString(), params);
	}
	
///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	/**
	 * 执行一个数据库更新，自动生成SQL
	 * @param tableName
	 * @param valueParams
	 * @param whereConditionSql
	 * @return
	 */
	public int update(String tableName, JdbcParams valueParams, String whereConditionSql) {
		return update(tableName, valueParams, whereConditionSql, (JdbcParams) null);
	}
	
	/**
	 * 执行一个数据库更新，自动生成SQL
	 * @param tableName
	 * @param valueParams
	 * @param whereConditionSql
	 * @param whereArgs
	 * @return
	 */
	public int update(String tableName, JdbcParams valueParams, String whereConditionSql, Object... whereArgs) {
		JdbcParams whereParams=new JdbcParams();
		int paramsCount=0;
		StringBuffer sql=new StringBuffer();
		for (int i=0; i<whereConditionSql.length(); i++) {
			char c=whereConditionSql.charAt(i);
			if (c=='?') {
				String paramName="p_"+paramsCount;
				whereParams.put(paramName, whereArgs[paramsCount]);
				paramsCount++;
				sql.append("${").append(paramName).append("}");
			} else {
				sql.append(c);
			}
		}
		return update(tableName, valueParams, sql.toString(), whereParams);
	}
	
	/**
	 * 执行一个数据库更新，自动生成SQL
	 * @param tableName
	 * @param valueParams
	 * @param whereConditionSql
	 * @param whereParams
	 * @return
	 */
	public int update(String tableName, JdbcParams valueParams, String whereConditionSql, JdbcParams whereParams) {
		if (valueParams.size()<1) {
			throw new RuntimeException("parameter map is empty!");
		} else if (whereConditionSql.length()<3) {
			throw new RuntimeException("where condition is empty!");
		}
		StringBuffer sql=new StringBuffer("update ");
		sql.append(tableName.startsWith(executor.tablePrefix) ? tableName : executor.tablePrefix+tableName).append(" set ");
		for (String columnName : valueParams.keySet()) {
			sql.append(columnName).append(" = ${").append(columnName).append("}, ");
		}
		sql.delete(sql.length()-2, sql.length()).append(" ");
		whereConditionSql=whereConditionSql.trim();
		if (whereConditionSql.length()>5 && "where".equalsIgnoreCase(whereConditionSql.substring(0, 5))) {
			sql.append(whereConditionSql);
		} else {
			sql.append("where ").append(whereConditionSql);
		}
		if (whereParams!=null) {
			valueParams.putAll(whereParams);
		}
		return execute(sql.toString(), valueParams);
	}
	
		

///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	/**
	 * 执行SQL更新，返回受影响行数
	 * @param sql
	 * @return
	 */
	public int execute(String sql) {
		return execute(sql, null, new Object[]{});
	}
	
	/**
	 *  执行SQL更新，返回受影响行数
	 * @param sql
	 * @param args
	 * @return
	 */
	public int execute(String sql, Object... args) {
		return execute(sql, null, args);
	}
	
	/**
	 *  执行SQL更新，返回受影响行数
	 * @param sql
	 * @param params
	 * @return
	 */
	public int execute(String sql, JdbcParams params) {
		return execute(sql, params, new Object[]{});
	}
	
	/**
	 *  执行SQL更新，返回受影响行数
	 * @param sql
	 * @param params
	 * @param args
	 * @return
	 */
	private int execute(String sql, JdbcParams params, Object... args) {
		executor.createQuery(sql);
		if (params!=null) {				
			executor.setJdbcParams(params);
			if (args.length>0) {
				throw new RuntimeException("不能同时使用JdbcParams params和 Object... args参数");
			}
		} else if (args.length>0) {
			executor.setArgArray(args);
		}
		return executor.executeUpdate();
	}
	
	/**
	 * 执行批量更新
	 * @param sql
	 * @return
	 */
	public int[] executeBatch(String... sql) {
		return executor.executeBatch(sql);
	}

		
	public String dialect_joinString(String... args) {
		return executor.sql_joinString(args);
	}
	public String sql_joinString(String... args) {
		return executor.sql_joinString(args);
	}

	public String dialect_callProcture() {
		return executor.sql_callProcture();
	}
	public String sql_callProcture() {
		return executor.sql_callProcture();
	}

	public String dialect_nvl() {
		return executor.sql_nvl();
	}
	public String sql_nvl() {
		return executor.sql_nvl();
	}
		
	public String dialect_now() {
		return executor.sql_now();
	}
	public String sql_now() {
		return executor.sql_now();
	}
	
	public String dialect_toDate(String time, String format) {
		return executor.sql_toDate(time, format);
	}
	public String sql_toDate(String time, String format) {
		return executor.sql_toDate(time, format);
	}

	public String getTablePrefix() {
		return executor.tablePrefix;
	}

	public void setTablePrefix(String tablePrefix) {
		executor.tablePrefix=tablePrefix;
	}

	public String getDbType() {
		return executor.dbType;
	}
	
	public void setDbType(String dbType) {
		executor.dbType=dbType;
	}
	
	public Jdbc startTransaction() throws SQLException {
		executor.startTransaction();
		return this;
	}

	public Jdbc endTransaction() {
		executor.endTransaction();
		return this;
	}

	public Jdbc commit() throws SQLException {
		executor.commit();
		return this;
	}

	public Jdbc rollback() {
		executor.rollback();
		return this;
	}
	
	public boolean readRow() {
		return executor.readRow();
	}
	
	public LinkedHashMap<String, Object> readPageInfo() {
		return executor.readPageInfo();
	}
	
	public String readResultJSON() {
		return executor.readResultJSON();
	}
	
	public String readResultJSON(NameMapper rowMapper) {
		return executor.readResultJSON(rowMapper);
	}
	
	public LinkedHashMap<String, Object> readResultMap() {
		return executor.readResultMap();
	}
	
	public LinkedHashMap<String, Object> readResultMap(NameMapper rowMapper) {
		return executor.readResultMap(rowMapper);
	}
	
	public ArrayList<LinkedHashMap<String, Object>> readResultList() {
		return executor.readResultList();
	}
	
	public ArrayList<LinkedHashMap<String, Object>> readResultList(NameMapper rowMapper) {
		return executor.readResultList(rowMapper);
	}
	
	public LinkedHashMap<String, LinkedHashMap<String, Object>> readMetaDataMap() {
		return executor.readMetaDataMap();
	}
	
	public LinkedHashMap<String, LinkedHashMap<String, Object>> readMetaDataMap(NameMapper rowMapper) {
		return executor.readMetaDataMap(rowMapper);
	}

	public String getString(String index) {
		return executor.getString(index);
	}

	public String getString(int index) {
		return executor.getString(index);
	}

	public int getInt(String index) {
		return executor.getInt(index);
	}

	public int getInt(int index) {
		return executor.getInt(index);
	}
	
	public long getLong(String index) {
		return executor.getLong(index);
	}

	public long getLong(int index) {
		return executor.getLong(index);
	}

	public float getFloat(String index) {
		return executor.getFloat(index);
	}

	public float getFloat(int index) {
		return executor.getFloat(index);
	}

	public String getNumString(String index) {
		return executor.getNumString(index);
	}

	public String getNumString(String index, String format) {
		return executor.getNumString(index, format);
	}

	public String getDateString(String index, String format) {
		return executor.getDateString(index, format);
	}

	public java.util.Date getDate(String index) {
		return executor.getDate(index);
	}

	public java.util.Date getDate(int index) {
		return executor.getDate(index);
	}

	public InputStream getBinaryStream(String index) {
		return executor.getBinaryStream(index);
	}
	
	public InputStream getBinaryStream(int index) {
		return executor.getBinaryStream(index);
	}

	public Blob getBlob(String index) {
		return executor.getBlob(index);
	}

	public Blob getBlob(int index) {
		return executor.getBlob(index);
	}
	
	public int getRowTotal() {
		return executor.getRowTotal();
	}
	
	public int getPgCount() {
		return executor.getPgCount();
	}

	public int getPgSize() {
		return executor.getPgSize();
	}

	public int getPgCurrent() {
		return executor.getPgCurrent();
	}
	
	///////////////////////////////////////////////////////////////////
	//放置一些废弃的方法，仅为保留兼容性，不建议使用
	
	//@Deprecated
	/**
	 * 应使用startTransaction()/endTransaction()
	 * @param enable
	 * @throws SQLException
	 */
	public void setAutoCommit(boolean enable) throws SQLException {
		executor.setAutoCommit(enable);
	}
	
	//@Deprecated
	/**
	 * 应使用query
	 * @param strSQL
	 */
	public void select(String strSQL) {
		query(strSQL);
	}
		
	//@Deprecated
	/**
	 * 应改调用query方法
	 * @param pgSize
	 * @param pgCurrent
	 * @param strSQL
	 * @return
	 */
	public Jdbc createQuery(int pgSize, int pgCurrent, String strSQL) {
		executor.createQuery(pgSize, pgCurrent, strSQL);
		return this;
	}
	
	//@Deprecated
	/**
	 * 应改调用query方法
	 * @param strSQL
	 * @return
	 */
	public Jdbc createQuery(String strSQL) {
		executor.createQuery(strSQL);
		return this;
	}
	
	//@Deprecated
	/**
	 * 应改调用query方法
	 * @param rowCount
	 * @param strSQL
	 * @return
	 */
	public Jdbc createQuery(int rowCount, String strSQL) {
		executor.createQuery(rowCount, strSQL);
		return this;
	}
	
	//@Deprecated
	/**
	 * 应改调用query方法
	 * @return
	 */
	public Jdbc executeQuery() {
		executor.executeQuery();
		return this;
	}
	
	//@Deprecated
	/**
	 * 应改调用execute、update、insert或delete方法
	 * @return
	 */
	public int executeUpdate() {
		return executor.executeUpdate();
	}
	
	//@Deprecated
	public Jdbc setString(String param, String value) {
		executor.setString(param, value);
		return this;
	}

	//@Deprecated
	public Jdbc setString(int index, String value) {
		executor.setString(index, value);
		return this;
	}
	
	//@Deprecated
	public Jdbc setNumString(String param, String value) {
		executor.setNumString(param, value);
		return this;
	}
	
	//@Deprecated
	public Jdbc setNumString(int index, String value) {
		executor.setNumString(index, value);
		return this;
	}

	//@Deprecated
	public Jdbc setDate(String param, Date value) {
		executor.setDate(param, value);
		return this;
	}

	//@Deprecated
	public Jdbc setDate(int index, Date value) {
		executor.setDate(index, value);
		return this;
	}

	//@Deprecated
	public Jdbc setDecimal(String param, BigDecimal value) {
		executor.setDecimal(param, value);
		return this;
	}

	//@Deprecated
	public Jdbc setDecimal(int index, BigDecimal value) {
		executor.setDecimal(index, value);
		return this;
	}

	//@Deprecated
	public Jdbc setLong(String param, long value) {
		executor.setLong(param, value);
		return this;
	}
	public Jdbc setLong(String param, String value) {
		executor.setLong(param, value);
		return this;
	}

	//@Deprecated
	public Jdbc setLong(int index, long value) {
		executor.setLong(index, value);
		return this;
	}
	public Jdbc setLong(int index, String value) {
		executor.setLong(index, value);
		return this;
	}

	//@Deprecated
	public Jdbc setInt(String param, int value) {
		executor.setInt(param, value);
		return this;
	}
	public Jdbc setInt(String param, String value) {
		executor.setInt(param, value);
		return this;
	}

	//@Deprecated
	public Jdbc setInt(int index, int value) {
		executor.setInt(index, value);
		return this;
	}
	public Jdbc setInt(int index, String value) {
		executor.setInt(index, value);
		return this;
	}

	//@Deprecated
	public Jdbc setDouble(String param, double value) {
		executor.setDouble(param, value);
		return this;
	}

	//@Deprecated
	public Jdbc setDouble(int index, double value) {
		executor.setDouble(index, value);
		return this;
	}

	//@Deprecated
	public Jdbc setFloat(String param, float value) {
		executor.setFloat(param, value);
		return this;
	}
	public Jdbc setFloat(String param, String value) {
		executor.setFloat(param, value);
		return this;
	}

	//@Deprecated
	public Jdbc setFloat(int index, float value) {
		executor.setFloat(index, value);
		return this;
	}
	public Jdbc setFloat(int index, String value) {
		executor.setFloat(index, value);
		return this;
	}

	//@Deprecated
	public Jdbc setBlob(String param, Blob value) {
		executor.setBlob(param, value);
		return this;
	}

	//@Deprecated
	public Jdbc setBlob(int index, Blob value) {
		executor.setBlob(index, value);
		return this;
	}

	//@Deprecated
	public Jdbc setBinaryStream(String param, InputStream value) {
		executor.setBinaryStream(param, value);
		return this;
	}

	//@Deprecated
	public Jdbc setBinaryStream(int index, InputStream value) {
		executor.setBinaryStream(index, value);
		return this;
	}

	//@Deprecated
	public Jdbc setNull(String param, int sqlType) {
		executor.setNull(param, sqlType);
		return this;
	}

	//@Deprecated
	public Jdbc setNull(int index, int sqlType) {
		executor.setNull(index, sqlType);
		return this;
	}
	
	public void setIgnoreLogging(boolean ignoreLogging) {
		executor.ignoreLogging=ignoreLogging;
	}
	
	public boolean isIgnoreLogging() {
		return executor.ignoreLogging;
	}
	
}
