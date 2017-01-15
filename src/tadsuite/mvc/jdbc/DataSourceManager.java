package tadsuite.mvc.jdbc;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.sql.DataSource;

import com.alibaba.druid.filter.Filter;
import com.alibaba.druid.filter.stat.StatFilter;
import com.alibaba.druid.pool.DruidDataSource;
import com.alibaba.druid.wall.WallConfig;
import com.alibaba.druid.wall.WallFilter;

import tadsuite.mvc.Application;
import tadsuite.mvc.logging.LogAdapterForDuridFilter;
import tadsuite.mvc.utils.Utils;

public class DataSourceManager {
		
	public static DataSourceConfig lookupDefaultDataSourceConfig() {
		return Application.getDataSourceMap().get(Application.getDefaultDataSourceName());
	}
	
	public static DataSourceConfig lookupDataSourceConfig(String name) {
		return Application.getDataSourceMap().get(name);
	}

	public static DataSource lookupDataSource(DataSourceConfig config) {
		if (config.jndiName.length()>0) {
			return lookupJndiDataSource(config.jndiName);
			
		} else {
			return lookupDruidDataSource(config);
		}
	}

	public static DataSource lookupDataSource(String jndiName) {
		return lookupJndiDataSource(jndiName);
	}
	
	private static DataSource lookupJndiDataSource(String name) {
		Context ctx=null;
		try {
			ctx = new InitialContext();
			return (DataSource) ctx.lookup(name);
		} catch (NamingException e) {
			try {
				return (DataSource) ctx.lookup("java:comp/env/"+name); //兼容Tomcat
			} catch (Exception e2) {
				return null;
			}
		} finally {
			if (ctx!=null) {
				try {
					ctx.close();
				} catch (NamingException e) {					
				} 
				ctx=null;
			}
		}
	}
	
	private static ConcurrentHashMap<String, DruidDataSource> druidDataSourcePool=new ConcurrentHashMap<String, DruidDataSource>();

	
	public static DataSource lookupDataSource(String url, String username, String password, String dbType) {
		String name="DS"+Utils.md5(url+username+password+dbType);
		if (druidDataSourcePool.containsKey(name)) {
			return druidDataSourcePool.get(name);
		}
		synchronized (druidDataSourcePool) {
			if (druidDataSourcePool.containsKey(name)) {
				return druidDataSourcePool.get(name);
			}
			
			DruidDataSource datasource=new DruidDataSource();
			datasource.setUrl(url);
			datasource.setUsername(username);
			datasource.setPassword(password);
			datasource.setInitialSize(1);
			datasource.setMaxActive(3);
			datasource.setMaxWait(60000);
			datasource.setTimeBetweenEvictionRunsMillis(60000);
			datasource.setMinEvictableIdleTimeMillis(60000);
			datasource.setMaxEvictableIdleTimeMillis(600000);
			
			WallConfig wallConfig=new WallConfig();			
			wallConfig.setMultiStatementAllow(true);
			wallConfig.setStrictSyntaxCheck(false);
			wallConfig.setCommentAllow(true);//commentAllow 	false 	是否允许语句中存在注释，Oracle的用户不用担心，Wall能够识别hints和注释的区别	
			wallConfig.setDeleteWhereNoneCheck(true);//deleteWhereNoneCheck 	false 	检查DELETE语句是否无where条件，这是有风险的，但不是SQL注入类型的风险
			wallConfig.setUpdateWhereNoneCheck(true);//updateWhereNoneCheck 	false 	检查UPDATE语句是否无where条件，这是有风险的，但不是SQL注入类型的风险
			wallConfig.setConditionAndAlwayTrueAllow(true);//conditionAndAlwayTrueAllow 	false 	检查查询条件(WHERE/HAVING子句)中是否包含AND永真条件
			wallConfig.setConditionAndAlwayFalseAllow(true);//conditionAndAlwayFalseAllow 	false 	检查查询条件(WHERE/HAVING子句)中是否包含AND永假条件
			wallConfig.setConditionDoubleConstAllow(true);
			
			WallFilter wallFilter=new WallFilter();
			wallFilter.setConfig(wallConfig);
			wallFilter.setDbType(dbType);
			
			StatFilter statFilter=new StatFilter();
			LogAdapterForDuridFilter logFiler=new LogAdapterForDuridFilter();						
			
			List<Filter> filters=new ArrayList<Filter>();
			filters.add(wallFilter);
			filters.add(logFiler);
			filters.add(statFilter);
			
			datasource.setProxyFilters(filters);
			
			druidDataSourcePool.put(name, datasource);
			return datasource;
		}
	}
	
	private static DataSource lookupDruidDataSource(DataSourceConfig config) {
		if (druidDataSourcePool.containsKey(config.name)) {
			return druidDataSourcePool.get(config.name);
		}
		synchronized (druidDataSourcePool) {
			if (druidDataSourcePool.containsKey(config.name)) {
				return druidDataSourcePool.get(config.name);
			}
			
			DruidDataSource datasource=new DruidDataSource();
			if (config.property.containsKey("driverClass") && config.property.get("driverClass").length()>0) {
				datasource.setDriverClassName(config.property.get("driverClass"));
			}
			if (config.property.containsKey("url") && config.property.get("url").length()>0) {
				datasource.setUrl(config.property.get("url"));
			}
			if (config.property.containsKey("username") && config.property.get("username").length()>0) {
				datasource.setUsername(config.property.get("username"));
			}
			if (config.property.containsKey("password") && config.property.get("password").length()>0) {
				datasource.setPassword(config.property.get("password"));
			}
			if (config.property.containsKey("connectionProperties") && config.property.get("connectionProperties").length()>0) {
				datasource.setConnectionProperties(config.property.get("connectionProperties"));
			}
			if (config.property.containsKey("initialSize") && config.property.get("initialSize").length()>0) {
				datasource.setInitialSize(Utils.parseInt(config.property.get("initialSize"), 1));
			}
			if (config.property.containsKey("maxActive") && config.property.get("maxActive").length()>0) {
				datasource.setMaxActive(Utils.parseInt(config.property.get("maxActive"), 20));
			}
			if (config.property.containsKey("maxWait") && config.property.get("maxWait").length()>0) {
				datasource.setMaxWait(Utils.parseInt(config.property.get("maxWait"), 60000));
			}
			if (config.property.containsKey("timeBetweenEvictionRunsMillis") && config.property.get("timeBetweenEvictionRunsMillis").length()>0) {
				datasource.setTimeBetweenEvictionRunsMillis(Utils.parseInt(config.property.get("timeBetweenEvictionRunsMillis"), 60000));
			}
			if (config.property.containsKey("validationQuery") && config.property.get("validationQuery").length()>0) {
				datasource.setValidationQuery(config.property.get("validationQuery"));
			}
			if (config.property.containsKey("testWhileIdle") && config.property.get("testWhileIdle").length()>0) {
				datasource.setTestWhileIdle(config.property.get("testWhileIdle").equals("true"));
			}
			if (config.property.containsKey("testOnBorrow") && config.property.get("testOnBorrow").length()>0) {
				datasource.setTestOnBorrow(config.property.get("testOnBorrow").equals("true"));
			}
			if (config.property.containsKey("testOnReturn") && config.property.get("testOnReturn").length()>0) {
				datasource.setTestOnReturn(config.property.get("testOnReturn").equals("true"));
			}
			if (config.property.containsKey("poolPreparedStatements") && config.property.get("poolPreparedStatements").length()>0) {
				datasource.setPoolPreparedStatements(config.property.get("poolPreparedStatements").equals("true"));
			}
			if (config.property.containsKey("maxPoolPreparedStatementPerConnectionSize") && config.property.get("maxPoolPreparedStatementPerConnectionSize").length()>0) {
				datasource.setMaxPoolPreparedStatementPerConnectionSize(Utils.parseInt(config.property.get("maxPoolPreparedStatementPerConnectionSize"), 20));
			}
			if (config.property.containsKey("useGlobalDataSourceStat") && config.property.get("useGlobalDataSourceStat").length()>0) {
				datasource.setUseGlobalDataSourceStat(config.property.get("useGlobalDataSourceStat").equals("true"));
			}
			datasource.setMinEvictableIdleTimeMillis(Utils.parseInt(config.property.get("minEvictableIdleTimeMillis"), 60000));
			datasource.setMaxEvictableIdleTimeMillis(Utils.parseInt(config.property.get("maxEvictableIdleTimeMillis"), 600000));
			
			WallConfig wallConfig=new WallConfig();			
			String wallFilterRule=config.property.get("wallFilterRule");//readonly/basic/all
			if (wallFilterRule!=null && wallFilterRule.equalsIgnoreCase("readonly")) {//readonly
				wallConfig.setSelelctAllow(true);//selelctAllow 	true 	是否允许执行SELECT语句
				wallConfig.setSelectAllColumnAllow(true);//selectAllColumnAllow 	true 	是否允许执行SELECT * FROM T这样的语句。如果设置为false，不允许执行select * from t，但select * from (select id, name from t) a。这个选项是防御程序通过调用select *获得数据表的结构信息。
				wallConfig.setSelectIntoAllow(false);//selectIntoAllow 	true 	SELECT查询中是否允许INTO字句
				wallConfig.setDeleteAllow(false);//deleteAllow 	true 	是否允许执行DELETE语句
				wallConfig.setUpdateAllow(false);//updateAllow 	true 	是否允许执行UPDATE语句
				wallConfig.setInsertAllow(false);//insertAllow 	true 	是否允许执行INSERT语句
				wallConfig.setReplaceAllow(false);//replaceAllow 	true 	是否允许执行REPLACE语句
				wallConfig.setMergeAllow(false);//mergeAllow 	true 	是否允许执行MERGE语句，这个只在Oracle中有用
				wallConfig.setCallAllow(false);//callAllow 	true 	是否允许通过jdbc的call语法调用存储过程
			}
			if (wallFilterRule!=null && !wallFilterRule.equalsIgnoreCase("all")) {//readonly, basic
				wallConfig.setSetAllow(false);//setAllow 	true 	是否允许使用SET语法
				wallConfig.setTruncateAllow(false);//truncateAllow 	true 	truncate语句是危险，缺省打开，若需要自行关闭
				wallConfig.setCreateTableAllow(false);//createTableAllow 	true 	是否允许创建表
				wallConfig.setAlterTableAllow(false);//alterTableAllow 	true 	是否允许执行Alter Table语句
				wallConfig.setDropTableAllow(false);//dropTableAllow 	true 	是否允许修改表
				wallConfig.setNoneBaseStatementAllow(false);//noneBaseStatementAllow 	false 	是否允许非以上基本语句的其他语句，缺省关闭，通过这个选项就能够屏蔽DDL。
				wallConfig.setUseAllow(false);//useAllow 	true 	是否允许执行mysql的use语句，缺省打开
				wallConfig.setDescribeAllow(false);//describeAllow 	true 	是否允许执行mysql的describe语句，缺省打开
				wallConfig.setShowAllow(false);//showAllow 	true 	是否允许执行mysql的show语句，缺省打开
			
			} else { //all
				wallConfig.setMultiStatementAllow(true);//multiStatementAllow 	false 	是否允许一次执行多条语句，缺省关闭
			}
			wallConfig.setStrictSyntaxCheck(false);
			wallConfig.setCommentAllow(true);//commentAllow 	false 	是否允许语句中存在注释，Oracle的用户不用担心，Wall能够识别hints和注释的区别			
			//commitAllow 	true 	是否允许执行commit操作
			//rollbackAllow 	true 	是否允许执行roll back操作
			//selectWhereAlwayTrueCheck 	true 	检查SELECT语句的WHERE子句是否是一个永真条件
			//selectHavingAlwayTrueCheck 	true 	检查SELECT语句的HAVING子句是否是一个永真条件
			//deleteWhereAlwayTrueCheck 	true 	检查DELETE语句的WHERE子句是否是一个永真条件
			wallConfig.setDeleteWhereNoneCheck(true);//deleteWhereNoneCheck 	false 	检查DELETE语句是否无where条件，这是有风险的，但不是SQL注入类型的风险
			//updateWhereAlayTrueCheck 	true 	检查UPDATE语句的WHERE子句是否是一个永真条件
			wallConfig.setUpdateWhereNoneCheck(true);//updateWhereNoneCheck 	false 	检查UPDATE语句是否无where条件，这是有风险的，但不是SQL注入类型的风险
			wallConfig.setConditionAndAlwayTrueAllow(true);//conditionAndAlwayTrueAllow 	false 	检查查询条件(WHERE/HAVING子句)中是否包含AND永真条件
			wallConfig.setConditionAndAlwayFalseAllow(true);//conditionAndAlwayFalseAllow 	false 	检查查询条件(WHERE/HAVING子句)中是否包含AND永假条件
			wallConfig.setConditionDoubleConstAllow(true);
			//conditionLikeTrueAllow 	true 	检查查询条件(WHERE/HAVING子句)中是否包含LIKE永真条件
			
			WallFilter wallFilter=new WallFilter();
			wallFilter.setConfig(wallConfig);
			wallFilter.setDbType(config.dbType);
			
			StatFilter statFilter=new StatFilter();
			LogAdapterForDuridFilter logFiler=new LogAdapterForDuridFilter();						
			
			List<Filter> filters=new ArrayList<Filter>();
			filters.add(wallFilter);
			filters.add(logFiler);
			filters.add(statFilter);
			
			datasource.setProxyFilters(filters);
			
			druidDataSourcePool.put(config.name, datasource);
			return datasource;
		}
	}
}
