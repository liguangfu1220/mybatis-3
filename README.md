MyBatis SQL Mapper Framework for Java
=====================================

[![Build Status](https://travis-ci.org/mybatis/mybatis-3.svg?branch=master)](https://travis-ci.org/mybatis/mybatis-3)
[![Coverage Status](https://coveralls.io/repos/mybatis/mybatis-3/badge.svg?branch=master&service=github)](https://coveralls.io/github/mybatis/mybatis-3?branch=master)
[![Maven central](https://maven-badges.herokuapp.com/maven-central/org.mybatis/mybatis/badge.svg)](https://maven-badges.herokuapp.com/maven-central/org.mybatis/mybatis)
[![License](http://img.shields.io/:license-apache-brightgreen.svg)](http://www.apache.org/licenses/LICENSE-2.0.html)
[![Stack Overflow](http://img.shields.io/:stack%20overflow-mybatis-brightgreen.svg)](http://stackoverflow.com/questions/tagged/mybatis)
[![Project Stats](https://www.openhub.net/p/mybatis/widgets/project_thin_badge.gif)](https://www.openhub.net/p/mybatis)

![mybatis](http://mybatis.github.io/images/mybatis-logo.png)

The MyBatis SQL mapper framework makes it easier to use a relational database with object-oriented applications.
MyBatis couples objects with stored procedures or SQL statements using a XML descriptor or annotations.
Simplicity is the biggest advantage of the MyBatis data mapper over object relational mapping tools.

Essentials
----------

* [See the docs](http://mybatis.github.io/mybatis-3)
* [Download Latest](https://github.com/mybatis/mybatis-3/releases)
* [Download Snapshot](https://oss.sonatype.org/content/repositories/snapshots/org/mybatis/mybatis/)


notes：
1、mybatis整体架构图：
    
    1、基础支持层
    2、核心处理层  
    3、接口层 
    
![Image text](https://raw.githubusercontent.com/liguangfu1220/mybatis-3/master/src/site/resources/note/mybatis-architecture.png)
    
2、基础支持层

    基础支持层，包含整个 MyBatis 的基础模块，这些模块为核心处理层的功能提供了良好的支撑。
    2.1 反射模块
        对应 reflection 包。
        Java 中的反射虽然功能强大，但对大多数开发人员来说，写出高质量的反射代码还是 有一定难度的。MyBatis 中专门提供了反射模块，
        该模块对 Java 原生的反射进行了良好的封装，提了更加简洁易用的 API，方便上层使调用，并且对反射操作进行了一系列优化，例如
        缓存了类的元数据，提高了反射操作的性能。
    2.2 类型转换模块
        对应 type 包。
        ① MyBatis 为简化配置文件提供了别名机制，该机制是类型转换模块的主要功能之一。
        ② 类型转换模块的另一个功能是实现 JDBC 类型与 Java 类型之间的转换，该功能在为 SQL 语句绑定实参以及映射查询结果集时都会
        涉及：
        在为 SQL 语句绑定实参时，会将数据由 Java 类型转换成 JDBC 类型。
        而在映射结果集时，会将数据由 JDBC 类型转换成 Java 类型。
    2.3 日志模块
        对应 logging 包。
        无论在开发测试环境中，还是在线上生产环境中，日志在整个系统中的地位都是非常重要的。良好的日志功能可以帮助开发人员和测试人
        员快速定位 Bug 代码，也可以帮助运维人员快速定位性能瓶颈等问题。
        目前的 Java 世界中存在很多优秀的日志框架，例如 Log4j、 Log4j2、Slf4j 等。
        MyBatis 作为一个设计优良的框架，除了提供详细的日志输出信息，还要能够集成多种日志框架，其日志模块的一个主要功能就是集成
        第三方日志框架。
    2.4 IO 模块（资源加载模块）
        对应 io 包。    
        资源加载模块，主要是对类加载器进行封装，确定类加载器的使用顺序，并提供了加载类文件以及其他资源文件的功能。
    2.5 解析器模块
        对应 parsing 包。
        解析器模块，主要提供了两个功能:
        一个功能，是对 XPath 进行封装，为 MyBatis 初始化时解析 mybatis-config.xml 配置文件以及映射配置文件提供支持。
        另一个功能，是为处理动态 SQL 语句中的占位符提供支持。
    2.6 数据源模块
        对应 datasource 包。
        数据源是实际开发中常用的组件之一。现在开源的数据源都提供了比较丰富的功能，例如，连接池功能、检测连接状态等，选择性能优秀
        的数据源组件对于提升 ORM 框架乃至整个应用的性能都是非常重要的。
        MyBatis 自身提供了相应的数据源实现，当然 MyBatis 也提供了与第三方数据源集成的接口，这些功能都位于数据源模块之中。
    2.7 事务模块
        对应 transaction 包。
        MyBatis 对数据库中的事务进行了抽象，其自身提供了相应的事务接口和简单实现。
        在很多场景中，MyBatis 会与 Spring 框架集成，并由 Spring 框架管理事务。
    2.8 缓存模块
        对应 cache 包。
        在优化系统性能时，优化数据库性能是非常重要的一个环节，而添加缓存则是优化数据库时最有效的手段之一。正确、合理地使用缓存可
        以将一部分数据库请求拦截在缓存这一层。
        MyBatis 中提供了一级缓存和二级缓存，而这两级缓存都是依赖于基础支持层中的缓 存模块实现的。这里需要读者注意的是，
        MyBatis 中自带的这两级缓存与 MyBatis 以及整个应用是运行在同一个 JVM 中的，共享同一块堆内存。如果这两级缓存中的数据量较
        大， 则可能影响系统中其他功能的运行，
        所以当需要缓存大量数据时，优先考虑使用 Redis、Memcache 等缓存产品。
    2.9 Binding模块
        对应 binding 包。
        在调用 SqlSession 相应方法执行数据库操作时，需要指定映射文件中定义的 SQL 节点，如果出现拼写错误，我们只能在运行时才能
        发现相应的异常。
        为了尽早发现这种错误，MyBatis 通过 Binding 模块，将用户自定义的 Mapper 接口与映射配置文件关联起来，系统可以通过调用自
        定义 Mapper 接口中的方法执行相应的 SQL 语句完成数据库操作，从而避免上述问题。
        值得读者注意的是，开发人员无须编写自定义 Mapper 接口的实现，MyBatis 会自动为其创建动态代理对象。在有些场景中，自定义 
        Mapper 接口可以完全代替映射配置文件但有的映射规则和 SQL 语句的定义还是写在映射配置文件中比较方便，例如动态 SQL 语句的
        定义。
    2.10 注解模块
        对应 annotations 包。
        随着 Java 注解的慢慢流行，MyBatis 提供了注解的方式，使得我们方便的在 Mapper 接口上编写简单的数据库 SQL 操作代码，而无
        需像之前一样，必须编写 SQL 在 XML 格式的 Mapper 文件中。
        虽然说，实际场景下，大家还是喜欢在 XML 格式的 Mapper 文件中编写响应的 SQL 操作。
    2.11 异常模块
        对应 exceptions 包。
        定义了 MyBatis 专有的 PersistenceException 和 TooManyResultsException 异常。
        
3、核心处理器

    在核心处理层中，实现了 MyBatis 的核心处理流程，其中包括 MyBatis 的初始化以及完成一次数据库操作的涉及的全部流程 。
    3.1 配置解析
    对应 builder 和 mapping 模块。前者为配置解析过程，后者主要为 SQL 操作解析后的映射。
        在 MyBatis 初始化过程中，会加载 mybatis-config.xml 配置文件、映射配置文件以及 Mapper 接口中的注解信息，解析后的配置信
        息会形成相应的对象并保存到 Configuration 对象中。例如：<resultMap>节点(即 ResultSet 的映射规则) 会被解析成 ResultMap 
        对象。
        <result> 节点(即属性映射)会被解析成 ResultMapping 对象。
        之后，利用该 Configuration 对象创建 SqlSessionFactory对象。待 MyBatis 初始化之后，开发人员可以通过初始化得到 
        SqlSessionFactory 创建 SqlSession 对象并完成数据库操作。
    3.2 解析
        对应 scripting 模块。
        拼凑 SQL 语句是一件烦琐且易出错的过程，为了将开发人员从这项枯燥无趣的工作中 解脱出来，MyBatis 实现动态 SQL 语句的功能，
        提供了多种动态 SQL语句对应的节点。例如<where> 节点、<if> 节点、<foreach> 节点等 。通过这些节点的组合使用， 开发人 员
        可以写出几乎满足所有需求的动态 SQL 语句。
        MyBatis 中的 scripting 模块，会根据用户传入的实参，解析映射文件中定义的动态 SQL 节点，并形成数据库可执行的 SQL 语句。
        之后会处理 SQL 语句中的占位符，绑定用户传入的实参。
    3.3 SQL 执行
        对应 executor 和 cursor 模块。前者对应执行器，后者对应执行结果的游标。
        SQL 语句的执行涉及多个组件 ，其中比较重要的是 Executor、StatementHandler、ParameterHandler 和 ResultSetHandler 。
        Executor 主要负责维护一级缓存和二级缓存，并提供事务管理的相关操作，它会将数据库相关操作委托给 StatementHandler完成。
        StatementHandler 首先通过 ParameterHandler 完成 SQL 语句的实参绑定，然后通过 java.sql.Statement 对象执行 SQL 语句并
        得到结果集，最后通过 ResultSetHandler 完成结果集的映射，得到结果对象并返回。
        ![Image text](https://raw.githubusercontent.com/liguangfu1220/mybatis-3/master/src/site/resources/note/sql-process.png)
    3.4 插件层
        对应 plugin 模块。
        Mybatis 自身的功能虽然强大，但是并不能完美切合所有的应用场景，因此 MyBatis 提供了插件接口，我们可以通过添加用户自定义
        插件的方式对 MyBatis 进行扩展。用户自定义插件也可以改变 Mybatis 的默认行为，例如，我们可以拦截 SQL 语句并对其进行重写。
        由于用户自定义插件会影响 MyBatis 的核心行为，在使用自定义插件之前，开发人员需要了解 MyBatis 内部的原理，这样才能编写出
        安全、高效的插件。

4、接口层

    对应 session 模块。
    接口层相对简单，其核心是 SqlSession 接口，该接口中定义了 MyBatis 暴露给应用程序调用的 API，也就是上层应用与 MyBatis 交互的
    桥梁。
    接口层在接收到调用请求时，会调用核心处理层的相应模块来完成具体的数据库操作。

5、总结

    mybatis整体架构分析，网上找的，如下：
    ![Image text](https://raw.githubusercontent.com/liguangfu1220/mybatis-3/master/src/site/resources/note/source-atc.jpg)
    