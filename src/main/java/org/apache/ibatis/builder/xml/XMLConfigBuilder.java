/**
 *    Copyright 2009-2019 the original author or authors.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package org.apache.ibatis.builder.xml;

import org.apache.ibatis.builder.BaseBuilder;
import org.apache.ibatis.builder.BuilderException;
import org.apache.ibatis.datasource.DataSourceFactory;
import org.apache.ibatis.executor.ErrorContext;
import org.apache.ibatis.executor.loader.ProxyFactory;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.io.VFS;
import org.apache.ibatis.logging.Log;
import org.apache.ibatis.mapping.DatabaseIdProvider;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.parsing.XNode;
import org.apache.ibatis.parsing.XPathParser;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.reflection.DefaultReflectorFactory;
import org.apache.ibatis.reflection.MetaClass;
import org.apache.ibatis.reflection.ReflectorFactory;
import org.apache.ibatis.reflection.factory.ObjectFactory;
import org.apache.ibatis.reflection.wrapper.ObjectWrapperFactory;
import org.apache.ibatis.session.*;
import org.apache.ibatis.transaction.TransactionFactory;
import org.apache.ibatis.type.JdbcType;

import javax.sql.DataSource;
import java.io.InputStream;
import java.io.Reader;
import java.util.Properties;

/**
 * @author Clinton Begin
 * @author Kazuki Shimizu
 *
 * 继承 BaseBuilder 抽象类，主要负责解析mybatis-config.xml 配置文件
 */
public class XMLConfigBuilder extends BaseBuilder {

  /**
   * 是否已解析，只能解析一次
   */
  private boolean parsed;
  /**
   * 封装 java XPath，构建XPath解析器
   */
  private final XPathParser parser;
  /**
   * 环境
   */
  private String environment;

  /**
   * localReflectorFactory 对象
   */
  private final ReflectorFactory localReflectorFactory = new DefaultReflectorFactory();

  public XMLConfigBuilder(Reader reader) {
    this(reader, null, null);
  }

  public XMLConfigBuilder(Reader reader, String environment) {
    this(reader, environment, null);
  }

  public XMLConfigBuilder(Reader reader, String environment, Properties props) {
    this(new XPathParser(reader, true, props, new XMLMapperEntityResolver()), environment, props);
  }

  public XMLConfigBuilder(InputStream inputStream) {
    this(inputStream, null, null);
  }

  public XMLConfigBuilder(InputStream inputStream, String environment) {
    this(inputStream, environment, null);
  }

  public XMLConfigBuilder(InputStream inputStream, String environment, Properties props) {
    this(new XPathParser(inputStream, true, props, new XMLMapperEntityResolver()), environment, props);
  }

  private XMLConfigBuilder(XPathParser parser, String environment, Properties props) {
    //在父类BaseBuilder中构建Configuration 对象
    super(new Configuration());
    ErrorContext.instance().resource("SQL Mapper Configuration");
    //设置Configuration的 variables 熟悉
    this.configuration.setVariables(props);
    //默认为解析过 mybatis主配置文件
    this.parsed = false;
    this.environment = environment;
    this.parser = parser;
  }

  public Configuration parse() {
    // 若已解析，抛出 BuilderException 异常 ,第一次解析时为false，解析后值为 true
    if (parsed) {
      throw new BuilderException("Each XMLConfigBuilder can only be used once.");
    }
    parsed = true;// 标记为已解析
    // 解析 mybatis-config.xml中的 configuration 节点
    parseConfiguration(parser.evalNode("/configuration"));
    return configuration;
  }

  private void parseConfiguration(XNode root) {
    try {
      //issue #117 read properties first
      //解析 <properties /> 标签
      propertiesElement(root.evalNode("properties"));
      // 解析 <settings /> 标签
      Properties settings = settingsAsProperties(root.evalNode("settings"));
      // 加载自定义 VFS 实现类
      loadCustomVfs(settings);
      loadCustomLogImpl(settings);
      // 解析 <typeAliases /> 标签
      typeAliasesElement(root.evalNode("typeAliases"));
      //  解析 <plugins /> 标签
      pluginElement(root.evalNode("plugins"));
      // 解析 <objectFactory /> 标签
      objectFactoryElement(root.evalNode("objectFactory"));
      // 解析 <objectWrapperFactory /> 标签
      objectWrapperFactoryElement(root.evalNode("objectWrapperFactory"));
      // 解析 <reflectorFactory /> 标签
      reflectorFactoryElement(root.evalNode("reflectorFactory"));
      // 赋值 <settings /> 到 Configuration 属性
      settingsElement(settings);
      // read it after objectFactory and objectWrapperFactory issue #631
      // 解析 <environments /> 标签
      environmentsElement(root.evalNode("environments"));
      // 解析 <databaseIdProvider /> 标签 ，配置多数据源时使用
      databaseIdProviderElement(root.evalNode("databaseIdProvider"));
      // 解析 <typeHandlers /> 标签
      typeHandlerElement(root.evalNode("typeHandlers"));
      // 解析 <mappers /> 标签
      mapperElement(root.evalNode("mappers"));
    } catch (Exception e) {
      throw new BuilderException("Error parsing SQL Mapper Configuration. Cause: " + e, e);
    }
  }

  private Properties settingsAsProperties(XNode context) {
    if (context == null) {
      return new Properties();
    }
    // 将子标签，解析成 Properties 对象
    Properties props = context.getChildrenAsProperties();
    // Check that all settings are known to the configuration class
    // 校验每个属性，在 Configuration 中，有相应的 setting 方法，否则抛出 BuilderException 异常
    MetaClass metaConfig = MetaClass.forClass(Configuration.class, localReflectorFactory);
    for (Object key : props.keySet()) {
      if (!metaConfig.hasSetter(String.valueOf(key))) {
        throw new BuilderException("The setting " + key + " is not known.  Make sure you spelled it correctly (case sensitive).");
      }
    }
    return props;
  }

  //加载自定义 VFS 实现类
  private void loadCustomVfs(Properties props) throws ClassNotFoundException {
    // 获得 vfsImpl 属性值
    String value = props.getProperty("vfsImpl");
    if (value != null) {
      // 使用 , 作为分隔符，拆成 VFS 类名的数组
      String[] clazzes = value.split(",");
      for (String clazz : clazzes) {
        if (!clazz.isEmpty()) {
          //获取 类，该类是 VFS 的子类
          @SuppressWarnings("unchecked")
          Class<? extends VFS> vfsImpl = (Class<? extends VFS>)Resources.classForName(clazz);
          // 设置到 Configuration 中
          configuration.setVfsImpl(vfsImpl);
        }
      }
    }
  }

  private void loadCustomLogImpl(Properties props) {
    Class<? extends Log> logImpl = resolveClass(props.getProperty("logImpl"));
    configuration.setLogImpl(logImpl);
  }

  /**
   * 解析 <typeAliases /> 标签，将配置类注册到 typeAliasRegistry 中
   * @param parent
   */
  private void typeAliasesElement(XNode parent) {
    if (parent != null) {
      // 遍历 typeAliases 的每个子节点
      for (XNode child : parent.getChildren()) {
        // 指定为包的情况下，注册包下的每个类
        if ("package".equals(child.getName())) {
          String typeAliasPackage = child.getStringAttribute("name");
          configuration.getTypeAliasRegistry().registerAliases(typeAliasPackage);
        } else {
          // 指定为类的情况下，直接注册类和别名
          String alias = child.getStringAttribute("alias");
          String type = child.getStringAttribute("type");
          try {
            Class<?> clazz = Resources.classForName(type);
            // 注册到 typeAliasRegistry 中
            if (alias == null) {
              typeAliasRegistry.registerAlias(clazz);
            } else {
              typeAliasRegistry.registerAlias(alias, clazz);
            }
          } catch (ClassNotFoundException e) {
            // 若类不存在，则抛出 BuilderException 异常,在xml中就已经限定了type必填
            throw new BuilderException("Error registering typeAlias for '" + alias + "'. Cause: " + e, e);
          }
        }
      }
    }
  }

  /**
   * 解析 遍历 <plugins /> 标签
   * 并将拦截器添加到拦截器链中
   * @param parent
   * @throws Exception
   */
  private void pluginElement(XNode parent) throws Exception {
    if (parent != null) {
      // 遍历 <plugins /> 标签
      for (XNode child : parent.getChildren()) {
        // 获取插件的interceptor属性 <plugin interceptor="com.xxx.xxx.xxInterceptor">
        String interceptor = child.getStringAttribute("interceptor");
        // 获取 选定插件的一个或多个属性，并封装到 Properties 中
        Properties properties = child.getChildrenAsProperties();
        // 通过反射获取插件类
        Interceptor interceptorInstance = (Interceptor) resolveClass(interceptor).newInstance();
        interceptorInstance.setProperties(properties);
        // 添加到 configuration 中
        configuration.addInterceptor(interceptorInstance);
      }
    }
  }

  /**
   * 解析 <objectFactory /> 节点
   * @param context
   * @throws Exception
   *
   * <objectFactory type="com.xxx.xxx.xxxObjectFactory">
   *         <property name="" value=""/>
   *         ...
   *     </objectFactory>
   */
  private void objectFactoryElement(XNode context) throws Exception {
    if (context != null) {
      String type = context.getStringAttribute("type");
      Properties properties = context.getChildrenAsProperties();
      // 通过反射的方式获取  ObjectFactory 对象
      ObjectFactory factory = (ObjectFactory) resolveClass(type).newInstance();
      factory.setProperties(properties);
      // 将 ObjectFactory 设置到 configuration
      configuration.setObjectFactory(factory);
    }
  }

  private void objectWrapperFactoryElement(XNode context) throws Exception {
    if (context != null) {
      String type = context.getStringAttribute("type");
      // 同样是反射获取对象
      ObjectWrapperFactory factory = (ObjectWrapperFactory) resolveClass(type).newInstance();
      // 设置 Configuration 的 objectWrapperFactory 属性
      configuration.setObjectWrapperFactory(factory);
    }
  }

  private void reflectorFactoryElement(XNode context) throws Exception {
    if (context != null) {
       String type = context.getStringAttribute("type");
       ReflectorFactory factory = (ReflectorFactory) resolveClass(type).newInstance();
       configuration.setReflectorFactory(factory);
    }
  }

  private void propertiesElement(XNode context) throws Exception {
    if (context != null) {
      // 解析子标签，并将值封装到 Properties 对象中 ， name，value
      Properties defaults = context.getChildrenAsProperties();
      // 读取 Properties的属性，  resource 和 url
      String resource = context.getStringAttribute("resource");
      String url = context.getStringAttribute("url");
      // resource 和 url 都存在的情况下，抛出 BuilderException 异常
      if (resource != null && url != null) {
        throw new BuilderException("The properties element cannot specify both a URL and a resource based property file reference.  Please specify one or the other.");
      }
      if (resource != null) {
        // 读取本地 Properties 配置文件到 defaults 中，会覆盖字标签中的同名属性值
        defaults.putAll(Resources.getResourceAsProperties(resource));
      } else if (url != null) {
        // 读取远程 Properties 配置文件到 defaults 中，会覆盖字标签中的同名属性值
        defaults.putAll(Resources.getUrlAsProperties(url));
      }
      /**
       *  获取方法参数传递的properties
       *  创建XMLConfigBuilder实例时赋值，this.configuration.setVariables(props);
       *  覆盖 defaults 中的同名配置属性
       */
      Properties vars = configuration.getVariables();
      if (vars != null) {
        defaults.putAll(vars);
      }
      // 设置 defaults 到 parser 和 configuration 中。
      parser.setVariables(defaults);
      configuration.setVariables(defaults);
    }
  }

  private void settingsElement(Properties props) {
    configuration.setAutoMappingBehavior(AutoMappingBehavior.valueOf(props.getProperty("autoMappingBehavior", "PARTIAL")));
    configuration.setAutoMappingUnknownColumnBehavior(AutoMappingUnknownColumnBehavior.valueOf(props.getProperty("autoMappingUnknownColumnBehavior", "NONE")));
    configuration.setCacheEnabled(booleanValueOf(props.getProperty("cacheEnabled"), true));
    configuration.setProxyFactory((ProxyFactory) createInstance(props.getProperty("proxyFactory")));
    configuration.setLazyLoadingEnabled(booleanValueOf(props.getProperty("lazyLoadingEnabled"), false));
    configuration.setAggressiveLazyLoading(booleanValueOf(props.getProperty("aggressiveLazyLoading"), false));
    configuration.setMultipleResultSetsEnabled(booleanValueOf(props.getProperty("multipleResultSetsEnabled"), true));
    configuration.setUseColumnLabel(booleanValueOf(props.getProperty("useColumnLabel"), true));
    configuration.setUseGeneratedKeys(booleanValueOf(props.getProperty("useGeneratedKeys"), false));
    configuration.setDefaultExecutorType(ExecutorType.valueOf(props.getProperty("defaultExecutorType", "SIMPLE")));
    configuration.setDefaultStatementTimeout(integerValueOf(props.getProperty("defaultStatementTimeout"), null));
    configuration.setDefaultFetchSize(integerValueOf(props.getProperty("defaultFetchSize"), null));
    configuration.setMapUnderscoreToCamelCase(booleanValueOf(props.getProperty("mapUnderscoreToCamelCase"), false));
    configuration.setSafeRowBoundsEnabled(booleanValueOf(props.getProperty("safeRowBoundsEnabled"), false));
    configuration.setLocalCacheScope(LocalCacheScope.valueOf(props.getProperty("localCacheScope", "SESSION")));
    configuration.setJdbcTypeForNull(JdbcType.valueOf(props.getProperty("jdbcTypeForNull", "OTHER")));
    configuration.setLazyLoadTriggerMethods(stringSetValueOf(props.getProperty("lazyLoadTriggerMethods"), "equals,clone,hashCode,toString"));
    configuration.setSafeResultHandlerEnabled(booleanValueOf(props.getProperty("safeResultHandlerEnabled"), true));
    configuration.setDefaultScriptingLanguage(resolveClass(props.getProperty("defaultScriptingLanguage")));
    configuration.setDefaultEnumTypeHandler(resolveClass(props.getProperty("defaultEnumTypeHandler")));
    configuration.setCallSettersOnNulls(booleanValueOf(props.getProperty("callSettersOnNulls"), false));
    configuration.setUseActualParamName(booleanValueOf(props.getProperty("useActualParamName"), true));
    configuration.setReturnInstanceForEmptyRow(booleanValueOf(props.getProperty("returnInstanceForEmptyRow"), false));
    configuration.setLogPrefix(props.getProperty("logPrefix"));
    configuration.setConfigurationFactory(resolveClass(props.getProperty("configurationFactory")));
  }

  /**
   * 解析 <environments /> 标签
   * @param context
   * @throws Exception
   *
   *         <environment id="development">
   *             <transactionManager type="JDBC">
   *                 <property name="" value=""/>
   *             </transactionManager>
   *             <dataSource type="UNPOOLED">
   *                 <property name="driver" value="org.hsqldb.jdbcDriver"/>
   *                 <property name="url" value="jdbc:hsqldb:mem:automapping"/>
   *                 <property name="username" value="sa"/>
   *             </dataSource>
   *         </environment>
   */
  private void environmentsElement(XNode context) throws Exception {
    if (context != null) {
      // environment 属性为空，从 default 属性获得
      if (environment == null) {
        environment = context.getStringAttribute("default");
      }
      for (XNode child : context.getChildren()) {
        // 判断 environment 是否匹配
        String id = child.getStringAttribute("id");
        // environment 和 id 是否匹配
        if (isSpecifiedEnvironment(id)) {
          //解析 `<transactionManager />` 标签，返回 TransactionFactory 对象
          TransactionFactory txFactory = transactionManagerElement(child.evalNode("transactionManager"));
          //  解析 `<dataSource />` 标签，返回 DataSourceFactory 对象
          DataSourceFactory dsFactory = dataSourceElement(child.evalNode("dataSource"));
          DataSource dataSource = dsFactory.getDataSource();
          // 创建 Environment.Builder 对象
          Environment.Builder environmentBuilder = new Environment.Builder(id)
              .transactionFactory(txFactory)
              .dataSource(dataSource);
          // 构造 Environment 对象，并设置到 configuration 中
          configuration.setEnvironment(environmentBuilder.build());
        }
      }
    }
  }

  private void databaseIdProviderElement(XNode context) throws Exception {
    DatabaseIdProvider databaseIdProvider = null;
    if (context != null) {
      String type = context.getStringAttribute("type");
      // awful patch to keep backward compatibility 保证兼容问题
      if ("VENDOR".equals(type)) {
          type = "DB_VENDOR";
      }
      Properties properties = context.getChildrenAsProperties();
      // 反射获取对象
      databaseIdProvider = (DatabaseIdProvider) resolveClass(type).newInstance();
      databaseIdProvider.setProperties(properties);
    }
    Environment environment = configuration.getEnvironment();
    if (environment != null && databaseIdProvider != null) {
      // 获得对应的 databaseId 编号
      String databaseId = databaseIdProvider.getDatabaseId(environment.getDataSource());
      //设置到 configuration 中
      configuration.setDatabaseId(databaseId);
    }
  }

  private TransactionFactory transactionManagerElement(XNode context) throws Exception {
    if (context != null) {
      String type = context.getStringAttribute("type");
      // 获取子节点属性，并封装到 Properties
      Properties props = context.getChildrenAsProperties();
      //  反射获取类
      TransactionFactory factory = (TransactionFactory) resolveClass(type).newInstance();
      factory.setProperties(props);
      return factory;
    }
    throw new BuilderException("Environment declaration requires a TransactionFactory.");
  }

  private DataSourceFactory dataSourceElement(XNode context) throws Exception {
    if (context != null) {
      String type = context.getStringAttribute("type");
      Properties props = context.getChildrenAsProperties();
      DataSourceFactory factory = (DataSourceFactory) resolveClass(type).newInstance();
      factory.setProperties(props);
      return factory;
    }
    throw new BuilderException("Environment declaration requires a DataSourceFactory.");
  }

  private void typeHandlerElement(XNode parent) {
    if (parent != null) {
      for (XNode child : parent.getChildren()) {
        // 如果是 package 标签，则扫描该包
        if ("package".equals(child.getName())) {
          String typeHandlerPackage = child.getStringAttribute("name");
          typeHandlerRegistry.register(typeHandlerPackage);
          // 如果是 typeHandler 标签，则注册该 typeHandler 信息
        } else {
          String javaTypeName = child.getStringAttribute("javaType");
          String jdbcTypeName = child.getStringAttribute("jdbcType");
          String handlerTypeName = child.getStringAttribute("handler");
          Class<?> javaTypeClass = resolveClass(javaTypeName);
          JdbcType jdbcType = resolveJdbcType(jdbcTypeName);
          Class<?> typeHandlerClass = resolveClass(handlerTypeName);
          // 注册 typeHandler
          if (javaTypeClass != null) {
            if (jdbcType == null) {
              typeHandlerRegistry.register(javaTypeClass, typeHandlerClass);
            } else {
              typeHandlerRegistry.register(javaTypeClass, jdbcType, typeHandlerClass);
            }
          } else {
            typeHandlerRegistry.register(typeHandlerClass);
          }
        }
      }
    }
  }

  /**
   *     <mappers>
   *         <mapper resource="org/apache/ibatis/autoconstructor/AutoConstructorMapper.xml" url="" class=""/>
   *         <package name=""/>
   *     </mappers>
   * @param parent
   * @throws Exception
   */
  private void mapperElement(XNode parent) throws Exception {
    if (parent != null) {
      for (XNode child : parent.getChildren()) {
        // 包扫描
        if ("package".equals(child.getName())) {
          String mapperPackage = child.getStringAttribute("name");
          configuration.addMappers(mapperPackage);
        } else {
          // 如果是 mapper 标签,获得 resource、url、class 属性
          String resource = child.getStringAttribute("resource");
          String url = child.getStringAttribute("url");
          String mapperClass = child.getStringAttribute("class");
          if (resource != null && url == null && mapperClass == null) {
            ErrorContext.instance().resource(resource);
            InputStream inputStream = Resources.getResourceAsStream(resource);
            XMLMapperBuilder mapperParser = new XMLMapperBuilder(inputStream, configuration, resource, configuration.getSqlFragments());
            mapperParser.parse();
          } else if (resource == null && url != null && mapperClass == null) {
            ErrorContext.instance().resource(url);
            InputStream inputStream = Resources.getUrlAsStream(url);
            XMLMapperBuilder mapperParser = new XMLMapperBuilder(inputStream, configuration, url, configuration.getSqlFragments());
            mapperParser.parse();
          } else if (resource == null && url == null && mapperClass != null) {
            Class<?> mapperInterface = Resources.classForName(mapperClass);
            configuration.addMapper(mapperInterface);
          } else {
            throw new BuilderException("A mapper element may only specify a url, resource or class, but not more than one.");
          }
        }
      }
    }
  }

  private boolean isSpecifiedEnvironment(String id) {
    if (environment == null) {
      throw new BuilderException("No environment specified.");
    } else if (id == null) {
      throw new BuilderException("Environment requires an id attribute.");
    } else if (environment.equals(id)) {
      return true;
    }
    return false;
  }

}
