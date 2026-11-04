# Java Programming Notes

## Object Class
* The Object class is present in the `java.lang` package.
* Object class is the parent class for all classes.
* Every class in Java is directly or indirectly derived from the Object class.
* The Object class methods are available to all Java classes.
* Object class acts as a root of the inheritance hierarchy in any Java program.

### Methods of Object Class
**1. `toString()`**
* The `toString()` provides a string representation of an object.
* It is used to convert an object to String.
* NOTE: Default behavior of `toString()` is to print class name, then `@`, then unsigned hexadecimal representation of the hash code of the object (By default).
* Example: `Employee@234321`.
* NOTE: It is always recommended to override `toString()` method to get our own String representation of Object.
* NOTE: Whenever we try to print any Object reference, internally `toString()` method is Called.
* Example: `Student s=new Student(); System.out.println(s);` is equal to `System.out.println(s.toString())`.
* NOTE: अगर हमे अपने Object की instance variable की value print करवानी है। तो `toString()` method Override करना पडेगा नहीं तो hash code print हो जायेगा।.

**2. `hashCode()`**
* For every object, JVM generates a unique number which is hashcode.
* A common misconception about this method is that the `hashCode()` method returns the address of Object, which is not correct.
* It converts the internal address of the object to an integer internally using an algorithm.
* `hashCode()` is native because in Java it is impossible to find the address of an Object So it uses native language like C, C++ to find the address of the object.
* Use of hash Code method: It returns a hash value that is used to search an object in a Collection.
* NOTE: Override of `hashCode()` method needs to be done such that for every object it generates a unique number.
* Example: For a `Student` class, we can return the roll no. from the hash code method as it is unique.

**3. `equals(Object obj)`**
* It completely compares 'this' object (the Object on which the method is called) to the given object.
* It gives a generic way to compare objects for equality.
* It is recommended to override the `equals` method to get our own equality condition on objects.
* NOTE: It is generally necessary to override the `hashCode` method whenever `equals` method is overridden, so as to maintain the general contract for the `hashCode` method, which states that equal objects must have equal hash codes.

### HashCode and Equals Contract
* `hascode()` implemented, `equals` implemented: Calls `hascode`. If not same, equal method not Called. If Same, equal method Called.
* `hascode` not implement, `equals` implemented: Calls Object class `hascode()`. If not Same: equal method not called. If Same: equal method Called.
* IMPORTANT: When we implement `equals` method then we must be implement `hashCode()`.
* But Object class `hascode` always give different object repetely. Only give Same hashCode when Same add.
* In Case `hashcode` not implement `equals` does not Called.

**4. `getClass()`**
* It returns the class object of "this" object and is used to get the actual runtime class of the object.
* It can also be used to get metadata of this class.
* It is final So we don't override it.
* NOTE: After Loading a class file, JVM will create an Object of type `java.lang.class` in the Heap area.
* We can use this class Object to get class Level in formation.
* It is widely used in Reflection.

**5. `finalize()`**
* object destroy होने से पहले Call होता है.
* This method is Called Just before an object is garbage Collected.
* It is called by the Garbage Collector on an object when the garbage Collector determines that there is no more references to the object.
* NOTE: We would override `finalize()` to dispose of system resources, perform clean-up activities and minimize memory leak.
* The `finalize` method is Called Just Once, on an object even through that object is eligible for garbage Collection multiple times.

**6. `clone()`**
* It returns a new Object that is exactly the same as this object.

---

## Modifier Types
To use a modifier, you include its keyword in the definition of a class, method or variable.

**A) Access Control Modifiers**
Java provides a number of access modifiers to set access levels for classes, variables, methods, and constructors. The four access levels are:
* **Default:** visible to the package, the default. No modifiers are needed.
* **Private:** visible to the class only.
* **Public:** visible to the world.
* **Protected:** visible to the package and all Subclass.

**B) Non-Access Modifiers**
Java Provides a number of other non-access modifiers to achieve many functionality.
* **Static:** The static modifiers for Creating Class method and variable and block.
* **Final:** The final modifiers for finalizing the implementations of classes, methods and variables.
* **Abstract:** The Abstract modifier for Creating abstract Classes and methods.
* **Synchronized & Volatile:** The Synchronized and volatile modifiers, which are used for threads.

### Access Modifiers with Class
* Modifiers can be used for class member variables and functions.
* **Outer class and inner class:** For outer class, there can be only two possibilities: `public class` or `default class`.
* NOTE: Outer class private और protected नही बना सकते.
* NOTE: लेकिन inner class private, public, protected और default भी बना सकते है.
* NOTE: एक Java File मे एक ही public class हो सकती है और file का नाम भी उसी public class के नाम पर होना चाहिए जिसमे main method होता है.
* NOTE: Only `public` class can be accessed directly from outside the package.
* NOTE: Java package मे एक से ज्यादा public class हो सकती है लेकिन Java File मे एक ही public class नही हो सकती.

### Members with Modifiers
* जो member class मे private होते है वो बाहर से access नही होते.
* जो member protected होते है उन्हे Same class तो access कर ही सकते है और package मे रखी सभी Class भी access कर सकते है लेकिन दूसरे Package मे child class ही उसे access कर सकती है.
* `public` member को कही से भी access किया जा सकता है.
* `default` member को Same package मे ही access किया जा सकता है.

---

## Static Keyword Details
* अगर class के अन्दर member function बनाते है। तो उन्हें instance member function कहा जाता है। जो हर Object के लिए अलग - अलग बनते है.
* लेकिन Static variable पूरी class के लिए एक वार ही बनता है जब class load होती है। और हर object उसी को Share करता है.
* और Static variable और static method बिना object के use किये जाते है using class name.
* Static variable object का variable नही होता है। ये पूरी class का variable होता है.
* Static member function only static variable को ही use कर सकता है ना कि instance variable को.
* member Function (instance member function) के अन्दर static variable नही बना सकते है। लेकिन Static inner class बना सकते है.

The static keyword is used for memory management mainly.
**1. Static variable:** The static variable can be used to refer to the common property of all object. The static variable gets memory only once in the class area at the time of class loading. Java Static property is shared to all objects.
**2. Static method:** A static method belongs to the class rather than the object of a class. A static method can be invoked without the need for creating an instance of a class. A static method can access static data member and can change the value of it.
* *Restriction for the static method:* The static method cannot use non-static data member or call non-static method directly. `this` and `super` cannot be used in static context.
**3. Static block:** It is used to initialize the static data member. It is executed before the main method at the time of class loading.

---

## Final Keyword
* `final` keyword is used to restrict the user.
* Final instance variable (class के अन्दर वाला).
* Final static variable.
* Final local variable (function के अन्दर वाला).
* Final class.
* Final method.
* Initialize करने के तीन तरीके है: assign value directly, initializer block द्वारा, Constructor द्वारा.
* Cannot change the value of final variable.
* Cannot override final method.
* Cannot inherit final class.
* `final` method is inherited but can not override it.
* `final` variable की value change नही कर सकते.
* `final` method को override नहीं कर सकता.
* Final class को inherit नही कर सकते.

---

## Static Member in Inheritance in Java
* A class inherits from its direct superclass all concrete methods (both static and instance) in the superclass.
* Parent class के Static और instance member function दोनो ही inherit होते है। लेकिन उनके Signature different होना चाहिए.
* और अगर Same Signature का function हुआ तो child class का function parent class के function को Hide कर देता है (function hiding).
* Static Function मे Hiding होती है और non-static (instance) मे overriding होती है.
* It is Compile time error if a static method hides an instance method.
* It is Compile time error if an instance method overrides a static method.
* Static member variables do not inherit (ये hide हो सकते है).

---

## Constructors
* Constructor not inherited by subclass.
* NOTE: Subclass का Constructor Parent class के Constructor को call करता है। जब subclass का object बनाता है। तो पहले Subclass का Constructor अपना Code चलाने से पहले parent class के Constructor को call करता है। फिर अपना code चलाता है.
* Explicit call to the super class constructor from subclass constructor can be made using `super()`.
* NOTE: Subclass का Constructor parent class के Constructor को explicit तरीके से भी Call करते है उसके लिए `super` keyword का use करते है। और ये keyword Constructor की पहली Line मे होना चाहिए.
* Basically parent class मे argument Constructor हो तो child class के constructor मे `super` keyword का use करना ही पड़ता है (explicit Call).
* NOTE: implicit तरीके से derived class का Constructor Super class के default constructor को ही Call करता है.
* You can write a subclass constructor that invokes the constructor of the superclass, either implicitly or by using the keyword `super`.

### Constructor Chaining
* Constructor can call other constructor of the same class or super class.
* `this` keyword same class के Constructor को Call करने के लिए होता है.
* `Super` keyword parent class के Constructor को Call करने के लिए होता है.
* NOTE: `this()` and `super()` دوनो एक साथ use नहीं कर सकते.
* Such series of invocation of Constructor is known as Constructor chaining.
* First line of constructor is either `super()` or `this()` (by default `super()`).
* Constructor never contains `super()` and `this()` both.

### Rules for Creating Constructor
* It is a special type of method which is used to initialize the object.
* Constructor is member function of a class.
* Constructor name must be the same as class name.
* A Constructor must have no explicit return type.
* Constructor object के बनते ही अपने आप Call होता है। और object की value को initialize करता है.
* अगर हम default Constructor नही बनाते है तो JVM Compiler Constructor बना देता है.
* Argument Constructor भी बना सकते है जिसके द्वारा Object बनते Time भी value as a argument pass कर सकते है.
* Constructor cannot be abstract, static, final and synchronized.
* We can use access modifiers while declaring a constructor to control the object creation.
* We can have private, protected or default or public Constructor in Java.
* **Types:** 1. Default Constructor 2. Argumented constructor.

---

## Package in Java
* A Package is a group of similar types of classes, interfaces and sub-packages.
* Packages are nothing more than the way we organize files into different directories according to their functionality, usability or category they should belong to.
* Files in one directory (or Package) would have different functionality from those of another classes.
* NOTE: Package के अन्दर classes का नाम Same नही हो सकते.
* Packaging also helps us to avoid class name collision when we use the same class name as that of others.
* The benefits of using package reflect the ease of maintenance, organization and increases collaboration among developers.
* **Advantage of Java package:** Java package is used to categorize the classes and interfaces so that they can be easily maintained. It provides access protection. It removes naming collision.
* NOTE: If you import a package, subpackages will not be imported.

---

## Initialization Block in Java
* Initialization block का काम variable को initialize करना होता है जो हम initialization block के अन्दर लिखते है.
* जो भी code Instance Initializer block मे लिखा होता है JVM वो code Constructor की पहली Line मे लिख देता हे। इसलिए instance initializer block Constructor के पहले चलता है.
* It runs each time when object of the class is created (instance initializer block).
* **Types of Initialization block:** 1. instance initialization block 2. Static initialization block.
* Static initialization block बनाने के लिए `static` keyword का use करते है.
* Static block object बनने के पहले ही चल जायेगा क्योकि ये पूरी class का block होता है ना कि particular object का.
* Static initialization block एक वार ही चलता है जब class load होती है.
* NOTE: initialization block हम कितने भी बना सकते है। लेकिन Compiler इन सभी block का एक block बना देगा। और Constructor की पहली Line मे लिख देगा (instance Initialization block).
* NOTE: Constructor के शुरुआती Lines के initialization code compiler देता है तो पहले initialization block चलता है। और फिर Constructor का Code चलता है.
* NOTE: `this` or `super` keyword cannot be used in initialization block.
* NOTE: instance initialization block object के बनने पर चलती है। लेकिन static initialization block एक बार ही चला है जब Class load होती है.
* NOTE: `return` keyword cannot be used in initialization block.

---

## Input/Output in Java
Java brings various streams with its I/O packages that helps the user to perform all the input output operations. There are two ways by which we can take input from the user or from a file: `BufferedReader` class and `Scanner` class.

### BufferedReader
* It is a simple class that is used to read sequence of characters.
* It has a simple function that reads a character and another `read` which reads an arrays of characters and a `readLine()` function which reads a line.
* `InputStreamReader()` is a function that converts the input stream of bytes into a stream of characters so that it can be read as a stream of characters as `BufferedReader` expects.

### Scanner class
* It is an advanced version of `BufferedReader` which was added in later version of Java.
* The Scanner can read formatted input.
* The Scanner is much easier to read as we don't have to write throws as there is no exception thrown by it.
* It contains predefined functions to read an Integer, Character and other data types as well.

### Differences between BufferedReader and Scanner
* `BufferedReader` is a very basic way to read input generally used to read a sequence of characters. It gives an edge over scanner because it is faster than Scanner because Scanner does lots of post-processing for parsing the input as seen in `nextInt()`, `nextFloat()`.
* `BufferedReader` is more flexible as we can specify the size of stream input to be read (BufferedReader reads larger input than Scanner).
* `BufferedReader` is preferred while dealing with multiple threads as it is synchronized.
* For decent input on easy readability, the Scanner is preferred on BufferedReader.
* `BufferedReader` is synchronous while Scanner is not. `BufferedReader` should be used if we are working with multiple threads.
* `BufferedReader` has a significantly larger buffer memory than Scanner.
* `BufferedReader` is a bit faster as compared to Scanner because the Scanner does the parsing of input data and BufferedReader simply reads a sequence of character.

---

## Exception Handling
* Exception handling is a powerful mechanism to handle runtime errors so that the normal flow of the application can be maintained.
* Exception is an abnormal condition.
* An exception is an event that disrupts the normal flow of the program; it is an object which is thrown at runtime.
* NOTE: Maintain the normal flow of application.

### Hierarchy
`Throwable`
* `Exception` -> `IOException`, `SQLException`, `ClassNotFoundException`, `RuntimeException` (`ArithmeticException`, `NullPointerException`, `NumberFormatException`, `IndexOutOfBoundsException` -> `ArrayIndexOutOfBoundsException`, `StringIndexOutOfBoundsException`).
* `Error` -> Stack overflow error, virtual machine error, out of memory error.

### Types of Exceptions
* **Checked Exception:** Checked exceptions are checked at compile time. The classes that directly inherit the Throwable class except `RuntimeException` and errors. Example: `IOException`, `SQLException`.
* **Unchecked Exception:** Unchecked exceptions are not checked at compile time but they check at runtime. The classes that inherit the `RuntimeException`. Example: `ArithmeticException`, `NullPointerException`, `ArrayIndexOutOfBoundsException`.

### Blocks and Keywords
* **`try` block:** try block मे ऐसा code लिखते है जिसमे exception आने की सम्भावना हो । और try block अकेला use नही कर सकते इसके साथ Catch block या Finally block दोनो मे से कोई एक use कर सकते है. It must be used within the method.
* **`catch` block:** Catch block is used to handle the exception. Catch block अकेला use नहीं सकते इसके साथ try block होना ही चाहिए.
* **`finally` block:** Is used to execute necessary code of the program. Executed whether an exception occurs or not. (Finally block चलता ही चलता है चाहे exception आये या नहीं). Is finally block used for resource deallocation. Finally the block must be at the end of all catch blocks. Finally block will be called whether an exception occurs or not. Finally will not execute only in case JVM shutdown due to power failure, `System.exit()`.
* **`throw` keyword:** Is used to throw an exception.
* **`throws` keyword:** Is used to declare exception. It is always used with method signature.

### Execution Rules
* NOTE: Try block के बाद हम कितने भी Catch block लगा सकते है। वो अपने parameter को match करेगा तो चलेगा नही强 आगे वाला catch block check किया जायेगा अगर एक भी match नही किया तो JAVA का default Catch block चलेगा.
* try के तुरन्त बाद Catch या finally block लिखना compulsory होता है.
* जब try block मे कोई exception नही आती तो Catch नही चलता है। लेकिन finally block चलता है.
* और अगर try block मे कोई exception आती है तो Catch block चलता है। और finally भी चलता है.
* Java का default Catch block program को end कर देता है। और try Catch के बाद वाली Code नही चलता है.

---

## Threads in Java
* A thread is an independent path of execution within a program.
* Many threads can run concurrently within a program.
* Multithreading refers to two or more tasks executing concurrently within a single program.
* NOTE: Operating System processor uses multithreading concept for scheduling.
* Every thread in Java is created and controlled by the `java.lang.Thread` class.

### Two ways to create thread in Java:
1. Implement the `Runnable` interface (`java.lang.Runnable`).
2. By Extending the `Thread` class (`java.lang.Thread`).

### Thread States
A Java thread is always in one of several states which could be running, sleeping, dead etc.
1. **New thread state (only once):** A thread is in this state when the instantiation of a Thread object creates a new thread but does not start it running. A thread starts life in the Ready-to-run state. You can call only the `start()` or `stop()` method when the thread is in this state. Calling any method besides `start()` or `stop()` causes an `IllegalThreadStateException`.
2. **Runnable State:** When the `start()` method is invoked on a New Thread(), it gets to the runnable state or running state by calling the `run()` method. A Runnable thread may actually be running or may be awaiting its turn to run.
3. **Not Runnable State:** A thread becomes not Runnable when one of the following four events occurs:
    * When `sleep()` method is invoked and it sleeps for a specified amount of time.
    * When `suspend()` method is invoked.
    * When the `wait` method is invoked and the thread waits for notification of a free resource or waits for the completion of another thread or waits to acquire a lock on an object.
    * The thread is blocking on I/O and waits for its completion.
    * *Switching from not runnable to runnable:* If a thread has been put to sleep, then the specified number of milliseconds must elapse. If a thread has been suspended, then its `resume()` method must be invoked. If a thread is waiting on a condition variable whatever object owns the variable must relinquish it by calling either `notify()` or `notifyAll()`. If a thread is blocked on I/O, then the I/O must complete.
4. **Dead State:** A thread enters this state when the `run()` method has finished executing or when the `stop()` method is invoked. Once in this state the thread cannot ever run again.

### Thread Priority
* In Java we can specify the priority of each thread relative to other threads.
* Those threads having higher priority get greater access to available resources than lower priority threads.
* A Java thread inherits its priority from the thread that created it.
* You can modify its thread priority at any time after creation using `setPriority()` method and retrieve the thread priority value using `getPriority()` method.
* Constants defined in the Thread class: `MIN_PRIORITY (0)` Lowest Priority, `NORM_PRIORITY (5)` Default priority, `MAX_PRIORITY (10)` Highest Priority.

### Synchronizing Multiple Threads
* Issue with multithreading: when we start two or more threads within a program, there may be a situation when multiple threads try to access the same resource.
* So there is a need to synchronize the action of multiple threads and make sure that only one thread can access the resource at a given point in time.
* Asynchronous process को Synchronous बनाना.
* Syntax: `synchronized (objectIdentifier) { // access shared variable and other shared task }`.

---

## Java 8 Features
Java 8 provides following features for Java programming:
1. Lambda expressions
2. Method reference
3. Functional interface
4. Stream API
5. Default method
6. Static method in interface
7. Optional class
8. Collectors class
9. For Each method
10. Parallel array Sorting
11. Type of repeating annotations
12. IO Enhancements
13. Concurrency Enhancements
14. JDBC Enhancements
15. Nashorn Javascript Engine
