# SPRING BOOT

## - Spring Introduction -

(i) The main objective of Spring framework is to make J2EE application development was easier.

(ii) The main objective of Spring boot is to make Spring application development was easier.

---

## (i) Steps to implement Spring MVC application :-

- **Step 1** - Create maven web projects
- **Step 2** - Add required Spring modules dependencies in Pom.xml
  - a) Spring core
  - b) Spring core
  - c) Spring web
  - d) Spring web mvc
  - e) validation
  - f) Jackson

- **Step 3** - Configure web.xml or web app initializer
- **Step 4** - Configure DispatcherServlet
- **Step 5** - DispatcherServlet Configuration
  - 1) View resolver
  - 2) DefaultServletHandler

- **Step 6** - DataSource Configuration
- **Step 7** - Create Controller
- **Step 8** - implement RequestMapping

> **NOTE-(i)** Out of 8 points, 1 to 6 points will be same for all the projects  
> and  
> Only 7 and 8 point will be varied from project to project

---

## Spring Application Development - (Drawbacks) :-

Over a period of time Spring unknowingly used boilerplate code.

**(ii) Version compatibility issues :-**  
We don't know which version will compatible with what version.

### # Spring Application Development - (Drawbacks) :-

1. **Lot of manual Configuration** - xml config, Java Config, autowiring & Component Scan

   - **Autowired and Component Scan:** - Should work for undefined classes (we have Source Code)
   - **Framework related classes:** - DataSource, JdbcTemplate, RadisTemplate (@Bean)
   - Regular Spring lighter interns of code but heavy weight with Configuration
   - → Time Consuming
   - → Complex
   - → Understand memorize tags

2. Dependency Management is too hard.
3. Difficult for new Commerce to tryout Spring for

**result-** To overcome all these problems Spring was introduced a module called Spring boot

---

## (i) To overcome all these problems Spring was introduced a module is called Spring boot.

(ii) Spring boot doesn't replacement of Spring framework ie. it is one of the module like Spring core, mvc, dao etc.

(iii) Sprint boot = all Spring modules.

(iv) Using Spring we can develop End 2 End Application but it will take lot of time to deliver the product.

**End 2 End Application means:-**
1. Standalone applications
2. web applications
3. distributed applications.

(v) Whatever regular to Spring framework is doing, Same will do Spring boot but the deliver the product quickly into market with the help of Spring boot features.

(vi) Spring Boot is new way of Creating Spring based applications

(vii) Spring boot aims to Simplify the process of developing production ready Spring based applications.

---

## Spring boot Features :-

1. Dependency management is easier.
2. Auto Configuration
3. Embeded Server
4. Actuator - (Addurs all non function requirements like health, monites, metrics) (production ready / deployable features)
5. Dev tools. - (automatiey reload and deploy)
6. Spring Application without xml Configuration.
7. Opnionated but higuly Customizable
8. Spring boot CLI.

---

## 1. Dependency management is easier :-

→ Spring boot was introduced Concept is starter dependencies  
  1. Spring boot parent starter pom  
  2. Spring boot starter (features)

### 1) Spring boot parent starter pom :-

→ it (spring) would integrate with So many third party technologies like mongodb, redis Cache, Kafka etc.

→ it will Configure all the Spring modules and this party libraries version information.

→ Spring boot version is Compatible with Spring version which will be taken care by Spring boot module.

### 2) Spring boot starter (feature)

→ Spring-boot-starter-web  
→ Spring-boot-starter-Jdbc  
→ Spring-boot-starter-redis

```
web application → Spring-boot-Starter-web → Spring-care
                                           → Spring-mvc
                                           → Spring-jackson
                                           → validator
```

**OTE-** Spring boot Starter dependency is used to bring all the required dependency of that features.

For web application:- (add spring-boot-starter-web)  
it will bring all the required dependencies to develop web application.

For Jdbc :- (add Spring-boot-starter-Jdbc)  
it will bring all the required Jars to develop Jdbc application.

→ **Spring-boot-Starter-parent pom:-** it is used to provide the information of all the libraries.

→ **Spring-boot-starter-feature dependency:-** it is used to get the required libraries to develop that features ie. developer no need to add the libraries, just add only one Starter dependency

---

## 2 Feature- AutoConfiguration :-

**Auto Configuration:-** Configure the beans using xml or Java Config or autowires with component.

→ There are Some drawbacks on xml Config, we moved to Java config  
→ autowire is used to reduce/remove the Configuration either in xml Config or Java Config  
→ eventhrough we are using autowire but still we are depending on manual Configuration  
ie. we can't apply @Component for all the classes.

1. To remove @Bean or \<bean\> Configuration we are using @Component but we can't apply @Component for all the classes.
2. We can apply @Component only which we have Source Code classes but can't apply for framework classes like DataSource, JdbcTemplate, RestTemplate, MongoTemplate.
3. If we want use framework class then we should Configure them as either @Bean or \<bean\> element.

**NOTE-** Springboot will remove all the Configuration code, it will enable autoconfiguration with the help of @SpringBootApplication.

**AutoConfiguration:-** instead of Configure the beans (undefine or predefined /framework beans) by the developer Springboot will take Core to configure and create

---

## Feature- Embeded Server :-

→ Server inside the application is called embeded Server.

→ Springboot application itself having Server is called embeded Servers.

```
Source Code + Tomcate Config = Springboot Application
```

→ This more useful in cloud.

### Feature:- Actuator :-

→ It is used to addurs all non functional requirements like health check, monitor, metrics, beans ...etc.

→ This is production ready features

### Features:- Dev tools :-

→ It is used to improve the developer productivity  
→ If any changes done by the developer, dev tools will take care Compile, regenerate Jar, deploy application into Server automatically

### Feature:- Spring boot without xml Configuration.

### Feature:- Opnionate but higuly Customizable :
Spring boot has provided default version, default require libraries, but Still we Can use our own dependencies

### Feature:- Spring boot CLI :- Command line Interface use to quick

---

## # Summary on Spring boot features:

→ Developer should not waste their time by Spending on manual work like

  a) manual Configuration  
  b) manual dependencies  
  c) setup monitor application  
  d) deploy application into external Server  
  e) redeployment for every changes.

→ Spring boot will take care all the above things and ask to developer focus on only coding.

---

## # How to Create Spring boot application:-

1. Using Eclipse IDE
2. Using https://start.spring.io
3. Using STS IDE.

---

## Spring core:-

→ Spring boot is used to develop end 2 end Application.  
→ using Spring boot we can develop Standalone application, web application, distributed application.

### a) Standalone:-

→ ApplicationContext context = new AnnotationConfigApplicationContext (JavaConfig);

### b) webapplication:-

→ ApplicationContext context = new AnnotationConfigServletWebServerApplicationContext (JavaConfig);

### c) reactive application:-

→ ApplicationContext Context = new AnnotationConfigReactivewebserverApplicationContext (JavaConfig);

---

## # Dependency Injection :-

1. Setter Injection
2. Constructor Injection
3. Fieldlevel Injection (Autowired)

```
A Object                    xml/Java
B Object          ←         Config

                  Configuration.
Spring Container → ApplicationContext Context
                   = new AnnotationApplication
Context.getBean(B.class);   Context (Configuration);
```

---

## # If we use Spring core, then developer responsible:

  a) write Configuration  
  b) Create the ioc Containers.

## # how to load the properties file

→ @PropertySource (" classpath: filename");

## # How many ways read the properties file

1. Environment object
2. @value annotation.

---

## Q- Spring Boot ?

(i) is developer, create Spring Containers ?  
(ii) is developer will write the Configuration.

## Q- what will happen when SpringApplication.run() will excicute ?

**Ans:- (i)** Spring Application Constructor will take care to identify which Container is required.

deduceFromClassPath() method will check in classpath based on classes find in classpath then Corresponding Spring Containers will be Created.

(ii) Create a empty environment Object.

(iii) detects the Configuration for our Application and loads into environment object. (application.properties / application.yml)

---

## Q- what will happen when SpringApplication.run() will excicute ?

1. Creates an empty environment object

2. detects external Configuration of our application and loads into environment object.

3. Print Spring boot banner.

4. Identifies type of WebApplicationType

   a) If Spring mvc Jars found in the classpath then treat the webApplicationType = WEB and it creates AnnotationConfigServletWebServerApplicationContext.

   b) If Spring webflux Jars are found then treat the webApplicationType = Reactive then create AnnotationConfig ReactiveWebServer ApplicationContext.

   c) If none of the above then treats the webApplication Type = NONE then it create AnnotationApplicationContext object will be Created

5. Instantiate the Spring factories and register with ioc Containers.

6. executes ApplicationContext Initializer || it will detect all the Configuration

7. PrepareContext

8. refreshContext // all the beans will be stored in Containers.

9. during the above stages, it will publisb various types of events and invokes listenns to perform operation.

---

## Q- How the autoConfiguration will work?

→ @SpringBootApplication annotation is responsible for enabling Spring boot auto Configuration.

```
@SpringBootApplication = @ComponentScan
                        + @EnableAutoConfiguration
                        + @SpringBootConfiguration
```

### 1. @SpringBootConfiguration :-

→ This is like Spring Configuration class.  
→ Just for naming convention Spring boot uses @SpringBootConfiguration

### 2. @ComponentScan :- (it is used to Source code classes)

→ It will Scan the Component classes at package level, class level  
→ default package name is current package.  
→ It is used to Scan user defined packages, classes.

### 3. @EnableAutoConfiguration :- (it is used to predefined Framework classes)

→ This is used to provide the framework  
→ It is used to enabled autoconfiguration for the framework related classes.

---

## How to load the properties file?

**Ans:- (i)** In Spring boot properties filename should be either application.properties or application.yml file.

(ii) Spring boot will load these properties during application Startup and no need to use @PropertySource

(iii) If we want load custom/our own properties file then we Should load using @PropertySource annotation

(iv) If we want load custom/our own yml file then @PropertySource does not Support. then either load  
  1. Convert yml file into properties file  
  2. load the properties files using @propertysource annotation

## # Configuration properties:-  Configuration File

**application.properties**

```
book.isbn = 89778
book.author = Sreenu
book.title = Spring boot in action
book.price = 400.
```

→ public class BookController {  
    @value("${book.isbn}") → use propcanes in our Controller file

no. of properties = no. of @value annotations.

---

In order to inject the dependent values into BookController then we have to write @value annotation on each property of the class.  
if the no. of attributes more then it takes time to write the annotation an inject values.

To overcome this problem Spring boot has introduced ConfigurationPropertiesBeanPostProcessor, this will take care to create the bean definition, so that it would be automatically register with ioc Container and will be invoked for each bean definition

```
Cx- @ConfigurationProperties (prefix = "book")
public class Book {

  Private String isbn;
  Private String author;   access value inside
  private String title;    Configuration.properties
  private String price;    file without using
                           @value annotation
  @Autowired               again and again for
  Book b;                  every field.
}
```

### How to read the type based properties file
### How to read the properties file without @value annotation
→ we should use 2 annotation:-

1. **@EnableConfigurationProperties**

→ this is used to Create the ConfigurationPropertiesBeanPost Processer Object.

2. **@ConfigurationProperties (prefix = "book");**

---

## Q- what are the profiles/environment? what is the purpose of it? (@profile)

**Ans:-** we Can use the profiles to switch from one environment to another environment.

### In Spring Core:-

**For dev environment:-**

```java
@Configuration
@PropertySource("classpath: db-dev.properties")
@Profile("dev")
public class DevConfig {

}
```

**For test environment:-**

```java
@Configuration
@PropertySource("classpath: dev.test.properties")
@Profile("test")
public class TestConfig {

}
```

### Spring boot:-

No need write any Configuration to load profiles.

In realtime spring.properties.active = {environmentname}  
Should have/be Send dockerfile, cicd pipeline.

---

## # Runners:- (only run when application is Start)

→ If we want perform onetime Startup activities  
  activity after ioc Container has been created

1. read values from Cache.
2. Some application will display the Countries list State list.

**two type of runners:-**
1. CommandLine Runner
2. Application Runner.

**NOTE-** Using Spring Boot - we Can avoid @Bean, @PropertySource, @Profile, @Import annotation.

we will use @value, @Component, @Autowired, @Qual

---

## # Spring mvc:-

→ Using mvc we Can develope web applications and distributed application (Rest API's)

**webapplication:-**

```
              Handler
         ②→  mapping
Request  →  Dispatches  ←ModelAndView→  Controller
  ①          Servlet      ④
                ↓
              ⑤ → ViewResolver
                ↓
               view
```

### Controller: Java:-
```java
@Controller
public class ProductController {

  @RequestMapping("/products")
  public String getproducts (model m) {
    return "Success";
  }

  @RequestMapping("productdetails")
  public String getProduct (model m) {
    return "Success";
  }
}
```

---

### 2. web.xml:-

1. Configure dispatcher Servlet

```xml
<Servlet>
  <Servlet-name> dispatcher </Servlet-name>
  <Servlet-class>org.SpringFramework.web.Servlet.DispatcherServlet</Servlet-class>
</Servlet>

<servlet-mapping>
  <Servlet-name> dispatcher </Servlet-name>
  <url-pattern> / </url-pattern>
</servlet-mapping>
```

### 3. Dispatcher-Servlet.xml:-

→ It is used to activate  
1. handler mapping  
2. Controller.

### 4. Write Controller, Service and Dao (layers).

**diagram:-**

```
                    Create Container
                          ↑
                Handler mapping
                     ↑
Dispatcher  ←  Controller name  →  Controller
Servlet     ←  modelandview     →  Internal View resolver

Servlet Container                Spring Container
```

---

## In Spring mvc developer has the responsible to write Configurations manually and deploy application.

a) Configuration - xml or Java config  
  - xml - web.xml and dispatcher-servlet.xml  
  - JavaConfig - ApplicationInitializer extends AbstractAnnotationConfigDispatcherServletInitializer, mvcConfig extends webmvcConfigurerAdapter.

b) deploy application manually

---

## # SPRING-BOOT:-

→ No Configuration.  
→ No (xml) Configuration  
→ No Java Configuration  
→ No need to deploy application into Server  
→ In Spring boot Just write Controller and add mvc view properties in application.properties file

**Q-** How Springboot will Create the DispatcherServlet, HandderMapping, ViewResalves -- etc

**A:-** All these object will created those with the help of @EnableAutoConfiguration annotation

**Q-** How AutoConfiguration work in Spring boot?

**A:-** AutoConfiguration is implemented with @Configuration class

---

## 1. @ConditionalXXX annotations are used to Constraints then autoconfiguration should apply.

2. Usually autoconfiguration classes uses @ConditionalX annotations

**How to Create A object**

```java
@Configuration
@ConditionalOnclass ("com.citi.B") //check B object is present in class path or not
public class SpringConfig {        //if present then only Create A class object
                                   //because A object is dependent on B obj
  @Bean
  public A a() {
    return A();
  }
}
```

If we want register a bean when

1. A specific class present in classpath
2. A specific type of bean doesn't already registered in ApplicationContext.
3. A specific file exists on location
4. A specific property value is Configured in a Configured
5. A Specific System property is present/absent

---

## Class level Conditions:-

1. @ConditionalOnclass
2. @ConditionalOnMissing class

## 2. bean Conditions:-

3. @ConditionalOnBean (if B is present in Container)
4. @ConditionalOnMissing Bean (if A is not present in Container)

## 3. Property Conditions :-

5. @ConditionalOnProperty

## 4. Resource Conditions:-

6. @ConditionalOnPropertyResource

## 5. webApplication Conditions:-

7. @ConditionalWebApplication
8. @ConditionalNotWebApplication

## 3. Location of all the autoconfiguration class.

Spring-boot-autoconfigure.Jar  
`--META-INF`  
`--Spring.factories`

## 4. There are 150+ autoConfigure classes in Spring.factories file, but in my project i will use only 4 or 5 classes. They then why we should load all the autoconfigure classes.

## 5. what are the classes are present in my class path and present in my Containers their classes, ie. this can be possible with the help of @ConditionalXXX annotations.

---

## 6. DispatcherServletAutoConfiguration class is taking Care of Creating DispatcherServlet object.

---

## # Embeded Server :-

→ Server inside the application is called Embeded Server.

→ In Spring boot there are 4 embeded servers.

1. Tomcat  2. Jatty  3. understow  4. Netty (Reactive)

→ Default Embeded Server is Tomcat  
→ we Can override default behaviour.

→ we dont need to add any additional dependency, it will Come along with spring-boot-starter-web.

7. EmbeddedWebServer factory Constomizer AutoConfiguration is responsible to Create the Embeded Server based on EmbededServer present in the classpath.

8. In Spring boot main() method is the entry point if we use embeded Server then no need package as war, it Should be Jar only.

9. SpringApplication.run() will Start the application and initialize the tomcat then deploy the application into tomcat Server.

10. All tomcat Server Configuration Should be keep in application.properties file

---

## Q- How to remove default embeded Server?

**Ans:-** By default Tomcat is embeded Server which will bring with spring-boot-starter-web.  
If we want any other embeded Server then exclude tomcat and add new embeded server  
(include dependency in pom.xml file).

→ Spring boot default support packaging model is Jar file but we can enable packaging as war also.

To enable as war, Springboot main class (which is representing with @SpringBootApplication) should be extends with SpringBootServletInitializer.

→ SpringBootServletInitializer will have configure() method which returns SpringApplicationBuilder object. SpringApplicationBuilder class will take care to identify the required Containers and start the instantiating all the required beans.

**Q-** what difference between normal Jar and Spring boot Jar?

**Ans:-** normal Jar:- no main manifest attribute, in SpringBoot -Jar-app-1.0.SNAPSHOT.Jar.

it Contains only project related, class files will be there, we Can't execute the Spring boot application with only .class files.

---

## Q- How does it executes? what happend when we run Spring boot Jar application?

**Ans:-** Spring-boot-Jar\

```
├ META-INF
│ └ manifest.mf
│     Main.class : org.Springframwork.boot.loader.JarLauncher
│     Start-class: Com.sreenutech.SpringBootJarAppApplication

├ BOOT-INF
│ └ classes
│     └ *.class (application classes location)
│
│ └ lib
│     └ .classes (required dependent Jars)

├ org
│ └ Spring framework
│     └ boot
│         └ loader
│             Launcher.class
│             JarLauncher.class
│             WarLauncher.class.
```

→ Springboot will use customized class loaders to load the .class files based on the boot Jar/war directory Structure.

---

→ Launcher.class is an Abstract class for which there are 2 implementation classes.

1. JarLauncher.class
2. WarLauncher.class.

→ When we run the Java -Jar filename.Jar, it will perform below things.

1. Java -Jar filename.Jar is the input to Jvm
2. JVM will invokes the default class loaders and to load .class of the Jar
3. The default class loaders only loads the .class files directly packages inside the Jar which are nothing but  
   - Org.Springframwork.boot.launcher  
   - Org.Springframwork.boot.JarLauncher  
   - Org.Springframework.boot.warLauncher

4. Upon loading the class files the JVM will read the META-INF/manifest.mf file and pickup the Main Class and executes it in our Case it JarLaun

5. JarLauncher will loads all the .class files and dependent Jars packaged inside our Jar files under BOOT-lib and BOOT-INF/classes directory

6. Then it goes to META-INF/manifest.mf and look for Start-class attributes and read the main class name of our application and Calls the main() method to begin execution of our Applia

---

## Q- when to use external Server and embeded Server?

**Ans:-** Embeded Server would prefer if the application expecting more traffic and need to create more instances at runtime  
(i-e) - instances are Scale out Scale down.

→ It is more Suitable for cloud based deployed.  
→ embeded Server is very value in microservice architecture

1. autoscale
2. availability
3. cost wise benifts.

**External Server:-**

1. Architecture would be monolithic
2. Onprime servers would be use this because they knows traffic would be limited.

---

## # Actuators :-

→ when we build our application post completion of the testing we Can't deliver the application into production environment, because client will expect monitor the application in production.

1. healthcheck
2. metrics
3. info
4. Threadsdumps
5. logs

Post Completion of our development, the developer has to Spend lot of time in building required utilities and integrate them into application to make it production ready.

1. the developer has to Spend lot of time
2. Cost of making for application utilities
3. delay in delivering the application in production.

To overcome all the above problems, Spring boot has introduced new features is called Actuators.

→ Spring boot actuators is prepackaged endpoints that the are Commonly required in monitoing and managing in application in production environment, which are build by the Spring boot developers that Can easily integrate with Spring boot applications.

→ Using Actuator we Can make an de-production application as production ready.

→ To enable Actuators within our application we need to Spring-boot-Starter-actuator dependency in pom.xml

The actuator module has provided bunch of endpoints which we Can use in monitor the application.

---

## Actuator Endpoints:

1. `/info` - Provide the information about your application like name, endusers.

2. `/healthcheck` - to check readyness and liveness of our application.

3. `/env` - to see all the environment variable Configured property or not.

4. `/beans` - all the bean defination for the ioc Containers.

5. `/metrics` - memory, cpu utilization

6. `/loggers` - display the information about loggers and look their Configurations.

By default all the above endpoints are accessible with a prefix /actuator/endpointsname.

```
http:// <domainame> /actuator/info
http:// <domainame> /actuator/metrics
```

These endpoints are exposed in 2 ways:

1. Jmx (Java management extension)
2. web (Http endpoints)

→ Jmx is a protocol used for managing the J2ee application Server.

---

→ we should use Jmx programs in accessing the endpoints.

→ Http endpoints like rest api, where we Can send http request and can access them.

**NOTE-** These endpoints to be accessed we need to enable and expose them, unless exposed those are not going to be accessible

By default all the endpoints are enabled except shutdown endpoint due to Security reason. If this endpoints is disabled, it means remove from the application.

**application.properties or application.yml:-**

```properties
management.endpoint.endpointname.enabled = true/
management.endpoint.shutdown.enabled = true
management.endpoint.enabled-by-default = false // will disable all the endpoints.
management.endpoint.info.enabled = true
```

By default all the endpoints exposed through Jmx technology but when it comes to web endpoints only 2 endpoints exposed by default:

1. info
2. healthcheck.

If we want expose the web endpoint we need more enable them using below entry.

---

**application.properties or application.yml:**

```properties
management.endpoint.Jmx.exposere.include = info, metrics
management.endpoint.Jmx.exposure.exclude = info, metrics

management.endpoint.web.exposure.include = info, metrics
management.endpoint.web.exposure.exclude = info, metrics

management.endpoint.web.exposure.include*
// all the endpoints are exposed.
```

```
enabled endpoint = enabling an endpoint means including that endpoint as part of our application

expose endpoint = making it accessible to the public.
```

---

## # Spring-boot Database integration :-

→ In regular Spring based application development to Connect with database we need to Create the DataSource object and JdbcTemplate object as below.

```java
@Configuration
public class JavaConfig {

  @Bean
  public DriverManagerDataSource dataSource() {
    DriverManagerDataSource datasource = new DriverManagerDataSource();
    datasource.setUrl(" ");
    datasource.setDriverclassName
    datasource.setUsername
    datasource.setpassword
    return datasource;
  }

  @Bean
  public JdbcTemplate JdbcTemplate (Datasource datasource) {
    return new JdbcTemplate (datasource);
  }
}

@Component
public class ProductDao {

  @Autowired
  JdbcTemplate JdbcTemplate;
}
```

---

**application.properties or application.yml file:**

```properties
Spring.datasource.url = mysql://localhost:3306/test
Spring.datasource.username = root
Spring.datasource.driver.class = com.mysql.driver.Driver
```

→ Springboot will take Care Create DataSource and JdbcTemplate objects.

→ To implement this we should add Spring boot-Starter-Jdbc dependency in pom.xml.

→ During application Startup, it will verify Spring.datasource.XXX property is Configured in properties or yml file, if it is Configured then only DataSource, JdbcTemplate objects will be created else it will give errors.

→ By default Springboot will support only one DataSource object will be provided with autoconfiguration.

→ Spring by default doesn't Support multiple DataSource Object, we need to write manual configuration.

---

**application.properties:**

```properties
app.datasource.first.driver-class = oracle driver class
app.datasource.first.url = dburl
app.datasource.first.username = username
app.datasource.first.password = password.
app.datasource.second.driver-class = mysql driver class
app.datasource.second.url = dburl
app.datasource.second.username = username
app.datasource.second.password = password
```

```java
@Configuration
public class MultipleDataSourceConfiguration {

  //first database

  @Bean
  @ConfigurationOnProperties("app.datasource.first")
  public DataSource firstDataSourceProperties() {
    return new DataSourceProperties();
  }

  @Bean
  @ConfigurationOnProperties("app.datasource.first/second")
  public DataSource firstDataSource() {
    return new FirstDataSourceProperties().
           inHtialize DataSourceBuilder().type(DataSource.class).build();
  }
}
```

---

```java
@Bean
public JdbcTemplate firstJdbcTemplate() {
  return new JdbcTemplate(firstDataSource);
}
```

---

## # Spring Data JPA :-

→ Generally DAO layer is used to Communicate with database

→ The DAO layer usually Contains a lot of boilerplate Code that Should be Simplified in order to reduce no. of lines of code and make the code reusable.

→ The main goal of Spring data JPA is to avoid Complete code in DAO layer.

→ Spring data JPA has provided 4 interfaces. these interfaces will take care to Connect database and perform required operations.

→ So, developer no need write Single line code in DAO layers.

---

```
Repository <T, ID>
        |
      extends
        |
CrudRepository <T, ID>
        |
      extends
        |
PagingAndSortingRepository <T, ID>
        |
      extends
        |
JpaRepository
```

we need to apply Some annotation on pojo class  
So that JPA can convert resultset object into Java object and Java object and database Serialize object.

1. @Entity
2. @Table
3. @Id
4. @Column
5. @GeneratedValue

→ To implement Spring data Jpa we Should add Spring-boot-Starter-Jpa dependency in Pom.xml file.

→ add datasource properties in application.properties or yml files.

---

**Case 1-** no code in dao. ie. use predefined method of JPA interface.

**Case 2-** our own method.

**Case 3-** generated queries.