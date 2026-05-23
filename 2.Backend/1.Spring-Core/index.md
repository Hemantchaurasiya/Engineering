# SPRING CORE

---

## Introduction :-

(i) The main goal of Spring framework is to make J2EE application development was easier.

**J2EE :-** Servlet , JSP , RMI , EJB -- etc.

---

### Drawbacks of J2EE without Spring :-

1. **Tightly coupling** – Application should extends Servlet , EJB -- etc.

2. **Heavy weight :-** Application startup will take more extra processing

3. **Boilerplate code :-** Common code is getting repeated in multiple places todo Some activity

4. **Cross cutting Concerns :-** J2EE does not Support inbuilt , need to implement manually

**NOTE-** Because of all there drawback of J2EE application there is a person called Rod Johnsen.

He is from Sun microsystem, he was disappointed with J2EE features and started something was Called interface21 was renamed as Spring

---

## Spring Advantage :-

(i) make Application development easier.
(ii) Light weight
(iii) modularity.

- → Spring core
- → Spring mvc
- → Spring batch
- → Spring webmvc
- → Spring dao
- → Spring AoP
- → Spring boot
- → Spring transaction
- → Spring Security.

(iv) **PoJo development :-**
Spring does not force your application to extend or implement any Spring framework classes

(v) **Popular :-** loosely coupled and Unit Test Code

---

## Q- Why Spring is sustain in market ?

**Ans:-** because Spring provide API to Communicate with different technology.

- ex- Spring Redis -- Redis Cache
- ex- MongoTemplate -- Mongo DB
               -- KAFKA

---

## SPRING CORE

**(1)** Spring core is the base module for all of the other module.

**Ex-**
```
public class Account {
    // state   -- instance variable
    // behaviour -- methods / function
}
```

---

### SOLID principle :-

**(1) SRP :-** Single Responsibility principle
( every class should have one Responsibility )

- **Ex-** class → User details + Account details  ✗
- **Ex-** class → Either User details or Account detail ✓

**Points :-**
(i) Every project would have multiple classes if we follow SRP.

(ii) Every class developer should create the object.

(iii) when application is running these objects should interact with another object.

```
class A {          class B {
  B b;   <--depend
  m() {                amount() { };
    b.amount();
  }                }
}
```

---

→ So, class A is depend on class B.

So,
- A class → dependent / source class
- B class → dependency / target class.

**(2)** It is (Spring core) used to mange the object dependencies.

**dependent :-** It is an object which is depending on another object to get some information.

**dependency :-** It is an object required by another object to carryout the functionality

**Example-**
```java
public class Pedigree {
    public void eat() {
        Sys("eat pedigree");
    }
}

public class Dog {
    private Pedigree p;
    public Dog() {
        this.p = new Pedigree();
    }
    public void eat() {
        this.p.eat();
    }
}

public class DogOwner {
    Dog d = new Dog();
}
```

---

**Diagram:**

```
(DogOwner) --dependent/source object--> (Dog) --dependent of dependency--> (Pedigree) --dependency/target object
```

→ what is **dis**advantage with this approach:-

1. Proper encapsulation is maintained because dependents doesn't know the internal details of their dependency.

→ what is **disadvantage** with this approach:-

1. Tightly coupled → Dog eats only Pedigree

2. we can't write a unit test case.

**(3)** In project development there would multiple classes , if we want Communicate one object with another object then we should Communicate via interface not direct classes.

**Ex-**
```java
public interface Food {
    public void eat();
}
```

```java
public class Pedigree implements Food {
    public void eat() {
    }
}

public class Bread implements Food {
    public void eat() {
    }
}

public class Meat implements Food {
    public void eat() {
    }
}

public class Dog {
    private Food f;
    public Dog() {
        this.f = f;
    }
    public void eat() {
        this.f.eat();
    }
}

public class DogOwner {
    Food f = new Bread();
    Dog dog = new Dog(f);
}
```

→ what is **advantage** with this approach:-

1. Loosely coupled.
2. Unit Test Code.

→ what is **disadvantage** with this approach:-

1. Breaks encapsulation
2. It creates unnecessary dependencies between un related classes or objects.

**NOTE-** To overcome to all these problem we will go to dependency injection.

**Ex-**
```java
public class DogOwner {
    private Dog dog;
    public DogOwner(Dog dog) {
        this.dog = dog;
    }
}
```

---

→ **Advantage with this approach (dependency injection)**

1. Dog is loosely coupled.
2. Unit-testable code
3. No broken encapsulation. ie. no need internal details of dependencies.

**NOTE-** Object is created automatically by IOC Container.
                      OR
           called dependency injection        spring

**(4)** Using this module (Spring Core) Spring will create the required objects and Supply to the application
So, developer no need create any object in application development.
That's why application is loosely coupled.

**Imp:-**

**Q-** How Spring will Provide the object ?
      How to get the object from Spring ?

**Ans:-** To implement Spring Core we should follow 3 steps :-

1. Configuration
2. Dependency Injector
3. Use the beans or get the beans.
   ( get the beans and use wherever required )

---

## 1) Configuration :-

**Types:-**
1. XML Configuration
2. Java Configuration
3. Annotation.

**what is Configuration:-**

ws:- (i) what object to create
      (ii) how to create them
      (iii) what dependencies inject.

**(i) what object to create:-**
DogOwner , Dog , Food , Pedigree , Bread , Meat

**(ii) how to create them :-**
- (i) Create a pedigree object using default Constructor
- (ii) Create dog object & Inject into pedigree object
- (iii) Create DogOwner object & Inject Dog object which is Created in Step 2.

**XML-Configuration**

```xml
<beans>
  <bean id="Pedigree" class="Com.Package.class" />

  <bean id="Dog" class="Com.package.Dog">
    <Constructor-arg ref="Pedigree" />   --> Constructor injection
  </bean>

  <bean id="dogowner" class="Com.Package.DogOwner">
    <Constructor-arg ref="Dogowner" />   --> Constructor injection
  </bean>
</beans>
```

---

**Diagram:**

```
[XML Configuration]  [Java Configuration]  [Annotation]
         \                  |                   /
          \                 |                  /
       [Dependency Injector / BeanFactory]
                    |
         BeanFactory factory = new XMLBeanFactory
                    |
         (pedigree)  (dog)  (dogowner)
              beanfactory Container <-- Dependency Injector
```

**Dependency Injector:-** Dependency injector will parse/read Configuration and understand what to do.

**Container :-(i)** Spring Container holds all the objects references created by dependency injector.

(ii) if we want object it from Container instead Create manually.

---

## Q- what happens when we create BeanFactory object ?

**Ans:-** `BeanFactory factory = new XMLBeanFactory(new ClasspathResource("xml file"));`

1. It will read xml file and validate it, if the xml is valid then

2. XMLBeanFactory Creates in memory logical memory partition inside JVM.

3. Loads the Spring bean Configuration file, and place metadata in the logical metadata memory partition.

4. The logical memory partition created by XMLBeanFactory is called IOC Container.

5. and it returns the reference of IOC Container as BeanFactory

6. `bean DogOwner dogowner = beanFactory.getBean("dogowner")`

    (i) beanfactory will goes to IOC Container (logical memory) and Searching for the bean with given Id ("dogowner"), If the dogowner is not found then it will throws exception and exit application

    (ii) Once the bean definition found then it will read the corresponding class name and Load the Class into JVM memory and instantiate the object of the class.

---

### (iii) IOC Principle :-

Collaboration of objects and managing the life Cycle object is called IOC.

**Collaboration:-** managing dependencies.

**life cycle:-** An object instantiation and destruction

**Q-** How to manage the dependencies:-

**Ans:-**
1. **dependency lookup :-** if the developer write the code to manually goto Container and get the object is Called " dependency lookup".

2. **dependency injection :-** it will provides the automatically get the dependencies objects

**NOTE:-** Container will Store the objects references but object will be stored in JVM.

```
Objects:
[Dog] [DogOwner]     (pedigree)
 (pedigree)          (dog)         Reference variables
     JVM             (dogowner)
                  beanfactory Container
```

---

### (i) Container is Software or hardware ?

**Answer is No.**

→ Container is Just like Hashmap.

`Hashmap<key, value>` where key = bean id and value = classname

→ Spring will use reflections to create the objects inside JVM.

**Q-** what is difference between Dependency Injection (DI) and Inversion of Control (IOC) ?

**Ans:-** Inversion → A→B→C , if a class will have multiple dependencies then get all the dependencies through one class.

Yes, Dependency injection is the one type of Inversion of Control.

---

## Types of Spring Container ?

1. BeanFactory
2. ApplicationContext.

**Dependency Injection ?**

**Ans:-** It is the process of getting the objects from Spring Container , instead of created by the developers.

---

## Q- How many way to apply dependency injection ?

**Ans:-** Two way to apply dependency injections

1. Setter Injection
2. Constructor Injection.

**(1) Setter Injection :-** The dependent object is injected into targeted object via Setter method is Called Setter Injection.

**(2) Constructor Injection :-** The dependent object is injected into targeted object via Constructor Called Constructor injection.

**Ex-**

```
class B {           class A {             class A {
  // class code       B b;                  B b;
}                     A(B b) {              Public void setB(
                        this.b = b;         ) {
                      }                     }
                    }                     }
                    Constructor           Setter injection
                    injection
```

---

**Code Example (Setter & Constructor Injection):**

```java
Public class Writer {
    private Pen pen;
    public Writer(final Pen pen) {
        this.pen = pen;
    }
    public void write() {
        pen.write();
    }
}

public class FountainPen implements Pen {
    private Ink ink;
    public FountainPen(final Ink ink) {
        this.ink = ink;
    }
    public void write() {
        Sys("Writing with " + ink.getColor() + " ink of " +
            ink.getBrandName() + " brand");
    }
}

public class BlackInk implements Ink {
    public String getBrandName() {
        return "Parker";
    }
    public String getColor() {
        return "Black";
    }
}

public interface Pen {
    void write();
}

public interface Ink {
    String getBrandName();
    String getColor();
}
```

**xml bean file:**
```xml
<beans>
  <bean id="blackink" class="Com.st.BlackInk" />

  <bean id="fountainPen" class="Com.st.FountainPen">
    <Constructor-arg ref="blackink" />
  </bean>

  <bean id="writer" class="Com.st.Writer">
    <Constructor-arg ref="fountainPen" />
  </bean>
</beans>
```

**(main function)**
```java
ApplicationContext Context = new ClasspathXmlResource(xml file);
Object obj = (Writer) Context.getBean("writer");
(Writer)(Writer)
writer.write();
```

**Output:-** writing with Black ink of parker brand.

---

## Q- Spring project pom.xml (starting)

**Ans:-**
```xml
<dependencies>
  <dependency>
    <groupId>org.springframework</groupId>
    <artifactId>Spring core</artifactId>
    <version>4.2.5 RELEASE</version>
  </dependency>

  <dependency>
    <groupId>org.springframework</groupId>
    <artifactId>Spring Context</artifactId>
    <version>4.2.5 RELEASE</version>
  </dependency>
</dependencies>
```

**NOTE-** for Spring project two JAR (dependencies) are required
1. SpringCore
2. Spring Context

**Ex-**
```java
public static void main(String[] args) {
    ApplicationContext Context = new ClassPathXmlApplicationContext("Constructor-arg.xml");
    Writer writer = (Writer) Context.getBean("writer");
    writer.write();
}
```

**E-** This code is responsible for Create ioc Container and run Spring application (and load all meta-data in pxml file and load in memory).

---

## Q. Type of Spring Container ?

**Ans:-**
1. BeanFactory
2. Application Context

```
          BeanFactory
    extends        implements
ApplicationContext      XMLBeanFactory
    implements
      → ClasspathXml ApplicationContext
      → FileSystemXml ApplicationContext
      → Annotation Config ApplicationContext
```

**NOTE-** Bean factory only Suitable for Standalone applications for Dependency Injection.

**NOTE-** ApplicationContext is suitable for :-
1. Standalone application
2. web application
3. AoP application
4. Application Events

**In memory:**

```
        (ink)
        (pen)             writer obj
        (writer) Container → Pen obj
                              Ink obj.  JVM
ApplicationContext Context
```

---

## Q- when we use Constructor and Setter Injection ?

| Constructor Injection | Setter Injection |
|---|---|
| 1) If the dependency is mandatory | 1) If the dependency is optional |
| 2) If the dependency is Final | 2) If the dependency is non final |
| 3) when there is Cyclic dependency, it is not supported | 3) It is possible for Cyclic dependency |

**NOTE-** we Can make our setter injection also mandatory.

**Sol:-** @Required -- we are forcibly make dependency as mandatory for setter injection.

→ We did application development for Constructor DI Setter DI using XML Configuration.

---

### Difficulties or Drawback of XML :-

1. Need to learn XML to work with XML Configuration

2. **TypeSafety :-** No typesafety. If we pass wrong reference also it will Consider.
   XML Can't recognize the error during Compiletime it would identity at runtime only.

3. **Readability :-** XML and Java in different place, need to keep switch between XML , Java which effects readability of the code.

4. **Maintaince :-** If too many Configuration then difficult to maintaince. Sometimes , dependencies duplicate id might be Configured.

→ To overcome these problems, we should use Java Configuration.

---

### Java Configuration :-

→ replace xml with Java config.

**Ex-**
```java
@Configuration
public class JavaConfig {

    @Bean                        // BlackInk blackInk
    public FountainPen fountainPen() {
        return new FountainPen(new blackInk);
    }

    @Bean
    public BlackInk blackInk() {
        return new BlackInk();
    }
}
```

---

### Java Configuration :- using @Configuration annotation.

**Ex:-**
```java
@Configuration   // <beans>
public class JavaConfig {

    @Bean                     // constructor injection
    public BlackInk blackInk() {
        return new BlackInk();
    }

    @Bean         // <beans>  class=""  id=""
    public FountainPen fountainPen(BlackInk blackInk) {
        return new FountainPen(blackInk);    // target object
    }

    @Bean
    public Writer writer(FountainPen fountainPen) {
        return new FountainPen(fountainPen);  // writer
    }
}
```

1) In xml Configuration we write:-

```java
→ ApplicationContext Context = new ClasspathXmlApplicationContext("xml file");
// Load xml file and Create Containers in memory.
```

2) In Java Configuration we write:-

```java
→ ApplicationContext Context = new AnnotationConfigApplicationContext(JavaConfig.class);
// Load Java file and create Containers in memory
```

---

## Q- If 100 bean are in project So no need to Create manually

**NOTE-** Using @Component , @Autowired annotation we Can reduce the more Configuration code.

**Q-** How to reduce ?
        → Scan all bean class using the help of @ComponentScan

**Ans:-**
```java
@Component
public class Writer {
}

@Component
public class FountainPen {
}

@Component
public class BlackInk {
}
```

**In XML:-** `<Component-Scan basepackage = "com.st.*" />`

**In Java:-** `@ComponentScan(basepackage = "com.st.*")`

**Q-** How to apply the dependency injection using @Component ?

**Ans:-** @Autowire is used to inject the target object into the source object.
- → @Autowired Can inject via Constructor
- → @Autowired Can inject via Setter
- → @Autowired Can inject via field

---

## Q- How many ways Configure bean in Spring ?

**Ans:-**
1. `<bean>`           (XML Configuration)
2. `@Bean`            (Java Configuration)
3. `@Component`       (annotation Configuration) (automatic Configuration)

→ @Component is a Java bean, which is used to declare at class level.
→ If you will not declare any name to @Component then default name as classname.
→ we Can configure our own name to @Component

```java
Pack.st                          Pack.st.abc
@Component                       @Component
public class A {                 public class B {
  B b;                           }
  // Constructor injection
  @Autowired
  public A(B b) {                Repo@Com.abc
    this.b = b;                  @Component
  }                              public class C {
                                 }
  // setter injection
  @Autowired
  public void set(B b) {
    this.b = b;
  }

  // Field level injection
  @Autowired
  private B b;
}
```

---

**In XML-**
```xml
<beans>
  <Component-Scan basepackage = "com.st, com...." />
  // It will scan all the classes of com.st and its packages.
</beans>
```

**In Java:-**
```java
@Configuration
@ComponentScan(basePackage = {"com.st", "com...."})
public class JavaConfig {
}
```

**Q-** How ComponentScan will work ?

**Ans:-** Using `<Component-Scan>` element in xml.
           or `@ComponentScan` annotation in Java Config.

---

## Topics Summary :-

1. Introduction Spring framework.
2. Drawbacks of J2EE and Advantages of Spring framework
3. what IOC & How it works.
4. Internals execution flow when we Create
   `Application Context = new classpathXmlResourceApplicationContext(" ");`
5. Dependency Injection & types of DI.
   a) Setter DI
   b) Constructor DI

6. Examples:-
   1. SI & CI using XML Configuration.
   2. Drawback of XML Configuration.
      a) need to learn xml to work with xml Config
      b) Typesafety
      c) performance
      d) Readability
      e) maintaince
      f) condition based bean creation is (that is) not possible with xml Configuration.
   3. SI & CI using Java Configuration
   4. SI, CI & FI using Java Config, xml config with @Component , @Autowired
   5. How to resolve the Conflicts interfaces with multiple implementation classes using @Primary , @Qualifier annotation.

---

## 7) Spring Core Annotations.

1. @Configuration    // replace xml file Configuration
2. @Bean             // replace bean element
3. @Component        // reduce the manual Configuration
4. @ComponentScan
5. @Autowired        → inject bean inside ioc Container
6. @ImportResource   // import xml file in Java Config
7. @Import           // import Java Config to Java Config
8. @Primary
9. @Qualifier.

---

## # Dependency Injection (DI) :-

**DI →** ( setter injection and constructor injection )
1. using XML Configuration        `<bean>`
2. using Java Configuration       `@Bean`

**IE-** using these two method to implement dependency injection (DI). Developer will write Configuration manually. (manual Configuration)

**Autowiring :-** it is possible to automatic configuration with the help of @Component @Autowired annotation

**@Component :-** is used to make POJO class as Spring bean

**@Autowired :-** is used to create the dependency injection (DI)

**Autowired have Three type injection:-**
1. Constructor Injection (CI)  ┐  XML Configuration
2. Setter Injection (SI)       ├  Java Configuration.
3. Field Injection (FI)        ┘

**-** who is going to process @Component , @Autowired ?

**-** Spring has provided Some Source Code to behave their functionality.

---

1. The process of Spring framework looking for identifying presence of @Component annotation and then creating the object is called Component Scan. (ComponentScan).

2. @Component annotation detection is not enabled by default ie it is default behaviour.
   ie. @ComponentScan disabled by default.

3. we need to explicitly enable it.

**Q:-** Any reason why it is disabled ?

**Ans:-** There will be 100 Jars in project, @Component could be add any where in classpath.

If @ComponentScan enabled then Spring has to Search in every class in classpath.
it will take so much of processing time on scanning

**#** If we want work with @Component then we have to enable it using @ComponentScan annotation.
@ComponentScan annotation enable explicitly using XML Config or Java Config

**#** By default will Scan current package only.
- → `@ComponentScan(basepackage = "com.st")`
- → `@ComponentScan(basepackage = {"com.st", "com.abc"})`
- → `@ComponentScan(basepackageClasses = B.class)`
- → `@ComponentScan(basepackageClasses = {A.class, B.class})`

---

→ Autowired Field level injection is not recommended because

1. we Can't write unit testing as dependencies hidden
2. we Can't write Conditions.

→ If the class doesn't have @Component, still we Can use @ComponentScan with @Bean, xml Config

ie. @ComponentScan will work with @Bean, xml Configuration

→ Sometimes DataSource , JdbcTemplate , RestTemplate etc. doesn't have @Component ie. we should go with @Bean annotation only

→ In realtime all the below Combination we should use

a) xml Config
b) xml with Java Config
c) Java Config with autowired.

---

## @Lazy :-

This annotation is used to create the object when first request will come Instead of Scan and create the objects during startup.

---

## @Primary :-

→ It will give Priority to one bean then another beans.
→ we Can't use Primary morethan one bean, ie its like Switch Statement, default bean it will give

→ If you are not sure what is the default then dont use @Primary annotations.

---

## @Qualifier :-

→ It is used dependency inject as "byName"
→ It is used to Setting name or alias name to bean
→ based on qualifier it will execute corresponding backend System

**Diagram (Card example):**

```
                  CreditCardService → CreditCardDao → cc
                       Impl
Card Info → CardService → DebitCardService  → DebitCardDao  → dc
Controller              Impl
                  GreenCardService  → GreenCard → gc
                       Impl              dao
```

```java
CardInfoController {

  @Autowired
  @Qualifier("cc")
  CreditCardServiceImpl svcImple;

  @Autowired
  @Qualifier("dc")
  DebitCardServiceImpl svcImple;

  @Autowired
  @Qualifier("gc")
  GreenCardServiceImpl svcImple;
}
```

---

## Scope - Lifetime

**Bean Scope -** Lifetime of a bean in the Container.
( when a bean gets Create by Container and when it gets destroyed ).

### Types of Scopes :-

1. **Singleton**   ┐  Spring core Scope
2. **Prototype**   ┘
3. Request
4. Session
5. Application
6. Global

**(1) Singleton :-** "One object per Container per bean definition"

→ Singleton beans will be created during Container Startup and will Stay in Container untill it gets Container or destroyed

→ Singleton Scope = Container live

→ Singleton Scope is not Same as Java Singleton design pattern.
   i.e. Java Singleton design pattern is used only one object with be created for whole application.

```xml
<bean id="abc" class="ABC">
// per bean definition only one object will be Created.

<bean id="abcl" class="ABC">
// Create another object with another new id.
```

**(2) Prototype :-** "multiple objects per bean definition per Container"

→ whenever there a need for the bean a new object will be Created.

→ its like use and throw object. If need create object

→ Spring Container always holds reference in Singleton bean which it has created, as it is reused using that reference.

→ Spring Container doesn't hold any reference of the prototype beans which it has Created.

→ Once object is Completed of prototype then it will get garbage Collected by the JVM

**NOTE=** By default all the Spring beans are Singleton Scope.

**xml Config:-** `<bean id="blackInk" class="com.st.BlackInk" scope="Prototype">`

**Java Config:-** `@Scope(-)` // If we will not declare then default Scope is Singleton.

---

## # Scope Combinations :-

```
class A {       class B {
}               }

class B injected into class A
i.e. class A is depending on class B.
```

**Case1:-** A, B are Singleton. One Singleton bean injected to another Singleton bean.

**Case2:-** A, B are prototype. One Prototype bean injected to another prototype bean.

**Case3:-** A is prototype, B is Singleton. One Singleton bean injected to another prototype bean.

**Case4:-** A is Singleton, B is prototype. One Prototype bean injected to another Singleton bean.

**NOTE-** "Never injected a shorter lived bean into a longer lived bean"

**Case1:-** Singleton injected to another Singleton bean.

```
A - Singleton
B - Singleton
→ a, b objects

NOTE- For N no of request, objects will be only one for both A and B.
```

---

**Case2:-** Prototype Injected to another Prototype.

```
req1 → (a1)(b1)     A- Prototype
req2 → (a2)(b2)     b- prototype
req3 → (a3)(b2)

NOTE- for every request new object will be created.
```

**Case3:-** Singleton object injected to prototype object.

```
req1 → (a1)(b1)    a = Prototype
req2 → (a2)        b = Singleton
req3 → (a3)

NOTE- Every request new A object will be created but B object is only once as it is Singleton.
```

**Case4:-** Prototype Injected into Singleton.

```
req1 → (a1)        a = Prototype
req2 → X           b = Singleton
req3 → O

NOTE- During Application Startup, Singleton bean are Created
at that time only 'A' will be injected into 'B'
So it will injected Same object if a not Created again
Never injected a Shorter-Lived bean into Longer-lived bean
"Never Inject prototype into Singleton"
Even though is B is a prototype but it behaves Singleton
```


---

## Q- when use Singleton and prototype ?

**Ans:-**
- If the class will not have any instance variable then preferred Singleton.
  ie. (class doesn't hold any state).

- If the class will have any instance variables (holding State) then preferred Prototype.
  because instance variable will be varied from object to object.

---

## # @Lazy annotation :-

1. Spring @Lazy annotation indicates that a bean will be Lazily initialized.

2. The @Lazy annotation can be annotated with class level, method level.

   a) @Component, @Configuration – use @Lazy annotation
   b) @Bean – use @Lazy annotation at method level.

3. The default scope of bean is Singleton, Generally Singleton beans are pre initialized to discover the error in the Configuration.

4. To initialize a bean Lazily we Can use @Lazy annotation in Java Config or use lazy-init attribute annotation in Java Config or use lazy-init attribute in `<bean>` element in xml Configuration based on the `<bean>` element.

5. If we want early initialize with @Lazy annotation then use `@Lazy(value = false)`

6. Lazy Initialization of bean means.

   a) Bean will not initialized untill referenced / called by another bean.
   b) explicitly called that bean from ApplicationContext

6. @Lazy annotation Can also use with @Autowired

7. This annotation is introduced in Spring 3.0

---

## Q- How to load the properties files and get the values from properties files in Spring ?

**Ans:-** Sample.properties

```
username = sreenu
password = welcome123
```

```java
Java.util.Properties p = new Properties();
p.load("Sample.properties");

String uname = p.getProperty("uname");
String pwd = p.getProperty("password");
```

**@PropertySource(classpath : {filename path})**
It is used load the properties file.

---

## # Read the value from properties file

**1) Using Environment class object (this is provided by Spring).**

→ `org.springframework.core.env.Environment` class is Configured as bean in Spring Container, this would happen during application Startup. // Autowired

**2) Using @Value annotation.**

---

## Q- How to get the bean from the Container, using @Autowired.

**Sol:-**

```
@PropertySource(filename)   Load      (using environment class object)
"classpath = filename"      blackink.brand               ①  environment.getProperty("blackink.brand")
                            = parker (from                             OR
                              properties)             ②  @Value("${blackink.brand}")
                  Container
environment                           (using @Value annotation)
```

→ Load the property file in Container:-

`@PropertySource("classpath : filename"):`

→ Read the value from properties file using environment class object.

```java
environment.get
@Autowired
private Environment environment;

environment.getProperty("blackink.brand")
environment.getProperty("blackink.color")
```

→ Read the value from properties file using @Value annotation.

```java
@Value("${blackink.brand}")
@Value("${blackink.color}")
```

---

## @PropertySource annotation

1. @PropertySource annotation is used to load the properties file

2. To get the values from properties file object using Environment Object or @Value annotation.

3. PropertySourcesPlaceholderConfigurer class will recognize the @Value annotation.

4. we Can get set default value of property keys in `@Value("${propertykey : default value 9")`

   a) if the property key is not found in properties file then it will get default value.

   b) if property key is present in properties file and default value then always will give property file key value only.

   c) If the property key is not found in properties file and default value not provided then it will throw an error.

```
java.lang.IllegalArgumentException : Could not resolve placeholder 'blackink.brand' in String value "${blackink.brand}"
```

---

## # Spring Core annotation :-

1. @Configuration
2. @Bean
3. @Component
4. @ComponentScan
5. @Autowired
6. @ImportResource
7. @Import
8. @Scope
9. @Lazy
10. @Primary
11. @Qualifier
12. @PropertySource
13. @Value
14. @Profile
15. @Required - To make Setter injection also mandatory, on top of setter method you should declare @Required

---

## Q- How to load the properties file based on environment ?

**Ans:-** In realtime we will have different environments or profiles.

**CICD Pipeline Diagram:**

```
Developer develop application code
            ↓
Commit the code into git repo
            ↓
        Jenkins
            ↓
        maven Jdk
    1) Compile Code
    2) execute JUnit testcases
    3) execute Code quality    }  CI
    4) execute Sonar reports
    5) build the Jar.
    6) build the image.
    7) push the image into docker hub or ecs
    8) deploy image into.
       a) dev  b) test  c) uat   }  CD
    9) deploy into production
```

```
    dev          test        production
     ↓             ↓              ↓
dev database   test database   production database
```

---

**Properties files:**

```
Sample.properties
Sample-dev.properties    // dev backend Configuration
Sample-test.properties   // test backend Configuration
Sample-prod.properties   // production backend Configuration
```

**# How to find the environment based properties file**

**(i) without Spring :-**

Configure profile name (dev, test, prod) inside server Configuration.

**Catalina.properties**
```
evenEnvironment = dev.
```

during application Startup, all the value will be set to JVM.
`System.setproperty("environment", dev);`

**Application Code:-**
```java
String env = System.getproperty("env");
String filename = "Sample-" + env + ".properties";
Java.util.properties p = new Properties();
p.load("sample.properties");

String uname = p.getProperty("username");
String pwd = p.getProperty("password");
```

---

→ @Profile annotation is used to get the environment value from the JVM which was settled during application Startup.

**Examples :-**
1. environments or profiles - dev, test, uat prod

2. how to load environment based properties file

**Sol:-** Using @Profile annotation.

3. If it is Standalone application, we have to Set the environment using the below options.

   a) `System.setProperty("environment" , "dev")`
   b) `spring.profiles.active = dev`

If it is web application, we have to Set the environment at server level.
- Tomcate : `Catalina.properties ⟹ env = dev`
- Jboss : `Server.xml ⟹ env = dev`

If we use docker then Set the environment name inside docker file.

whenever we Set the environment value, during application Startup it would be Set to JVM.

**Q-** How to get the value from JVM ?

1. `String env = System.getproperty("environment")`
2. Using @Profile annotation, this will use internally.

---

**Ex-** `@Profile("dev")` // to load dev properties file to classpath

→ @Profile annotation is used at class level, method level.

a) if @Profile will use at class level then no. of properties = no. of Configuration classes.

b) if @Profile will use at method level then One Configuration class which will have no. of methods.
   - no. of profiles = no. of methods
   (each method will take care to load the each profile properties file)

---

## Examples :-

1. CI using xml Configuration
2. SI using xml Configuration
3. SI using Java Configuration
4. SI using Java Configuration
5. Import xml Config with Java Config
6. Import Java Config with Java Config
7. Integrate xml Config, Java Config with Java Config
8. How to resolve the ambiguity using @Primary and @Qualifier.
9. Scopes (Singleton, Prototype) using Java Config
10. Scopes (Singleton, prototype) using autowired
11. Scopes (singleton, prototype) using xml
12. @Lazy annotation examples
13. primitives , Collection examples.
14. How to load properties files using xml
15. How to load properties files using Java Config
16. How to load properties files using autowired
17. profiles using class level
18. profiles using method level
19. profiles using server level
20. profiles using xml and Java Config