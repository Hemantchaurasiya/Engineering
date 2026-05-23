# Java Source file Structure

---

## Page 1

### 1. If No any public class in the Java file. Save the file with any name.

**Ex:**
```
class A {
}
class B {
}
class C {
}
class D {
}
```
filename:- Hemant.Java

---

### 2. If Any public class in the Java file. Save the file with same public class Name.

**NOTE-** Only One public class are allowed in one Java file.

---

### 3. For every class present in the program. A Separate .class file will be generated.

---

### 4. A Java program Can Contain Any no. of classes where every class contain main() method.

**Ex:**
```java
class A {
    public static void main() {
        Sys ("A class main()");
    }
}

class B {
    public static void main() {
        Sys ("B class main");
    }
}
```

---

## Page 2

```java
class C {
    public static void main() {
        Sys ("c class main");
    }
}

class D {
}
```

filename:- Durga.Java

- A class → A class main
- B class → B class main
- C class → **R.T.Error** because no main() method are in that class.

Run → Durga.Java → **Error** (This .class file is not present)

---

## Page 3 - Fully qualified name

```java
class Test {
    public static void main() {
        Java.util.ArrayList = new ArrayList<>();
    }
}
```

### Import statement

- **Explicit import** → `import Java.util.ArrayList;`
- **Implicit import** → `import Java.util.*;`

**Ex-** `import Java.util.*;` — Implicit import

**Recommended:** Explicit import because Readability of programming is good.

---

### # Important points about import statement:-

1. Java.lang package are already available in our program So no need to import it.

2. Default package (current working directory) import are not required.

**Ex-** whenever we are importing a package all classes and interfaces present in that package by default available but not Subpackage classes.

```
Java
└── util  ← not available
    └── regex x → import Shra.x;  pattern x
```

---

## Page 4

**Ex:**
```java
class Test {
    public static void main(String erargs) {
        pattern p = Pattern.Compile(ob);
    }
}
```

1. `import Java.*;`  X
2. `import Java.util.*;`  X
3. `import Java.util.regex.*;`  ✓

---

### # Package:- Package is a encapsulation mechanism to group related classes and interface into Single unit.

**Advantage:-**
1. Naming Conflicts
2. modularity
3. maintainability
4. Security

**Ex-** Package com.hemant;

**NOTE-** Inside any Java program only One package statement are allowed.

**Ex-**
```
package pack1;  ✓
package pack2;  X  // Compile time error.
public class Test {
}
```

**Command:** `Javac -d . Test.Java`

---

## Page 5

**IE-** In any Java program first line package statement is Compulsory. (only one package statement)

```
import Java.util.*              Ex:   package pack1;  ✓
package pack1;  X                     import Java.util.*;    order is
public class Test {                   public class Test {    important
}                                     }
```

---

### Class level modifiers:-
1. modifiers are describe the behavior of the class.
2. modifiers decide where the class is accessible.
3. Specify class information to JVM by using Corresponding modifiers.

### # For top table class (outer class) level modifiers:-

1. public
2. \<default\>
3. abstract
4. final
5. strictfp

### # For inner table class (inner class) level modifiers:-

1. private
2. protected
3. static

---

## Page 6

### 1. Public modifiers:- Any where accessible. (All places)

**Ex:**
```
package pack1;          |   package pack2;
public class A {        |   import pack1.A;
}                       |   public class B {
                        |       p.s.v. main() {
                        |           A a = new A();  // accessible
                        |       }
                        |   }
```

### 2. Default modifiers:- Accessible only in current package.

**Ex:**
```
package pack1;          |   package pack2;
class A {               |   import pack1.A;
}                       |   public class B {
                        |       p.s.v. main() {
                        |           A a = new A();  // Not accessible
                        |       }
                        |   }
```

### 3. Abstract modifiers:-
- → class  ✓
- → method  ✓
- → variable  X

Abstract modifiers is applicable for class level and method level not for variable.

### Abstract method:-
**Use:-** If we don't know about implementation of method then use abstract keyword method();

**NOTE-**
1. Abstract method has only declaration not implementation.
2. not Contain body.

---

## Page 7

**IE-** Chied class is responsible to provide implementation of abstract method.

**G-** The method which have only declaration not implementation is called abstract method.

```java
public class Fruit {
    public abstract String getTest();  // Abstract method.
}
```

### Syntax of abstract method:-
1. `public abstract void m1() {}`  X
2. `public void m1();`  X
3. `public abstract void m1();`  ✓
4. `public void m1() {}`  X  (bottom brackets not bottom semicolon)

### # Abstract class:- (restriction)
1. No one is allowed to Create object of Abstract class.
2. Abstract class is not Complete implementation.
3. For Abstract class Object creation are not possible.

### Relation between Abstract class and Abstract method?

**1-** If atleast one abstract method present in the class then must this class as Abstract class.

```java
Abstract class Test {
    public abstract void m1();   // ✓
}
```

```java
class Test {
    public abstract void m1();   // X
}
```

---

## Page 8 (Point 1)

**NOTE-** If no any abstract method are present in class then this class mark as a abstract or not. yes. we declare as abstract class with abstract keyword with class.

**Ex-**
```java
abstract class Test {
    public void m1() {}    // dummy implementations
    public void m2() {}
}
```

**NOTE-** Abstract class Contains zero numbers of abstract method also possible.

---

**Point 2-** Chied class is responsible to provide implementation of abstract method (all abstract method) of Abstract class (mandatory). Otherwise chied class is also declare as a Abstract class.

---

## Page 9 - Member modifiers

### Member modifiers:- (used with method and variables)

### 1. public:- Firstly check the class visibility then check method visibility. (global level)

### 2. Default:- In current package accessible (package level)

### 3. Private:- Can not access outside the class (class level) only accessible within Same class.

### 4. Protected:- within the package anywhere we Can access but outside the package only child classes accessible by child class. We should use child class reference variable to access protected member.

**Ex:**
```
package pack1;                      |   package pack2;
class A {                           |   public class B extends A {
    protected void m1() {           |       p.s.v. mains(String[] args) {
        Sys ("A class protected");  |           A a = new A();   X
    }                               |           a.m1();
}                                   |       Child ← B b = new B();  ✓
                                    |   class       b.m1();
                                    |   reference   A a = new B();  X
                                    |   variable        a.m1();
                                    |       }
                                    |   }
```

---

## Page 10 - Visibility Table

| visibility | public | protected | default | private |
|---|---|---|---|---|
| 1. within the Same class | ✓ | ✓ | ✓ | ✓ |
| 2. from child class of Same package | ✓ | ✓ | ✓ | X |
| 3. from Non-Child Com of Same package | ✓ | ✓ | ✓ | X |
| 4. from child class of outside the package | ✓ | ✓ (we should use child reference only) | X | X |
| 5. from non-child class of outside the package | ✓ | X | X | X |

**private < default < protected < public**

---

## Page 11

### Interface:-
1. Any Service Requirement Specification (SRS).
2. Every method Inside interface by default public and Abstract. before Java 8.

### interface declaration and implementation:-

```java
interface Inter {
    public void m1();
    public void m2();
}
```

- When we implement any method of interface must be public method.

```java
abstract class Serviceprovider implements Inter {
    public void m1() {
    }
}
```

**E-** If we implement and interface in class the this class is responsible to make implementation of every method that are declare in interface. if not define in class then make class as abstract class. Now chied class is responsible to provide implementation of unimplemented method.

```java
class SubServiceProvider extends Serviceprovider {
    public void m2() {
    }
}
```

---

## Page 12

### # Data Hiding:- Hiding internal details. outside person should not access data directly.

**Ex-**
```java
class Account {
    private double balance;   // data hiding behind method

    public double getbalance() {   // method is the only way
        // validation              // to access data outside
        return balance;            // the class.
    }
}
```

**Advantage:-** Security of data.

---

### # Abstraction:- (Not implemented Or Not Completed Or partial Completed)

→ Hiding internal implementation
→ Just highlight set of Services. what we are going to offer.

**Advantage:-**
1. Security
2. Don't know internal implementation.
3. Enhancement.
4. maintainability.

---

### # Encapsulation:- Group of data member and their Corresponding methods in Single unit is called encapsulation.

**Ex-** Every Java class is the example of encapsulation.

---

## Page 13

```java
class Student {
    String name;    // data member
    int age;
    int roll;       // group
    int marks;
    public void read() {}    // member function
    public void write() {}
}
```

**IE-** Any Component follows data hiding and abstraction that Component is said to be encapsulated Component.

```
Encapsulation = Data hiding + Abstraction
```

```java
class Account {
    private double balance;   // data hiding (not access data member directly)

    public double getBalance() {
        // validation    // hiding internal implementation.
        return balance;
    }

    public void setBalance(double amount) {
        // validation
        this.balance = this.balance + amount;
    }
}   // Encapsulation.
```

```
Balance   ←→   hiding data + hiding internal implementation
enquiry
Deposit
         GUI Screen
```

---

## Page 14

**Advantage:-**
1. Security (biggest advantage)
2. Enhancement
3. maintainability
4. modularity

### # Tightly encapsulated class:-
⇒ Every variable is present inside class is private Such type class is called Tightly or encapsulated class. (100% data hiding)

**Ex-**
```java
public class Account {
    private double balance;
    public double getbalence() {
        return balance;
    }
}
```

**Ex-**
```java
class A {
    private int x = 10;
}

class B extends A {
    protected int y = 20;   X
}

class C extends A {
    private int z = 30;   ✓
}
```

---

**e:-** if parent class is not tightly encapsulated then chied class also not be tightly encapsulated.

---

## Page 15 - Inheritance Introduction

### # Inheritance Introduction:-
1. IS-A Relationship
2. Code reusability
3. extends keyword.

**Ex-**
```java
class Parent {
    public void m1() {
        Sys ("parent");
    }
}

class Chied extends Parent {
    public void m2() {
        Sys ("chied");
    }
}
```

---

## Page 16

```java
public static void main() {
    // propose
    // 1
    Parent p = new Parent();
    p.m1();   ✓
    p.m2();   X  Compile error

    // 2
    Chied c = new Chied();
    c.m1();   ✓
    c.m2();   ✓

    // 3
    Parent p = new Chied();
    p.m1();   ✓
    p.m2();   X  Compile error.

    // 4
    Chied c = new Parent();   X  Compile error.
}
```

**NOTE-** Parent class reference variable is held chied class object. But chied class reference variable is not held parent class object.

---

### # without inheritance:-

```
class Houseloan {      |   class Personelloan {   |   class Veclicleloan {
    300 method         |       300 method         |       300 method
}                      |   }                      |   }
```

---

## Page 17

### with Inheritance:-

```java
class Loan {
    250 method (common method)
}

class Homeloan extends Loan {
    50 method
}

class Personelloan extends Loan {
    50 method
}

class Veclicleloan extends Loan {
    50 method
}
```

**Advantage:-**
1. Code Reusability (biggest advantage)
2. Reduce development Time.

---

### # Object class:-

```
                        Object
          ↓          ↓         ↓        ↓          ↓
       String   StringBuffer  Number   math     Throwable
                                                ↓        ↓
                                           Exception    Error
                                               ↓
                                   RuntimeException   IoException
```

**OTE-** Object class is the parent class of all classes in Java.

---

## Page 18 - Types of Inheritance

### Types of Inheritance:-

**1. Single inheritance**
```
[class A]
    ↑  extends
[class B]
```
One parent class and one child class  ✓

**2. multiple Inheritance** (multiple parent class and one child class)
```
[Class A]   [Class B]
         ↘↙
        [class C]      X
```
**NOTE-** Java don't Support multiple inheritance using class.

**3. multilevel inheritance:-**
```
[class A]
    ↑
[class B]
    ↑
[class C]
```
✓

**4. Hierarchical Inheritance** (Single parent class and multiple child classes)
```
          [Class A]
      ↓       ↓       ↓
  [ClassB] [ClassC] [ClassD]
```
✓

**5. Hybrid Inheritance:-**
```
      [class A]
          ↑
    X  [class B]
          ↑
       [class C]
      ↓         ↓
  [class D]  [class E]
         ↓
      [class F]
```
**NOTE-** Java don't Support hybrid Inheritance.

---

## Page 19

### Multiple Inheritance:- (multiple implementation of Same method)

```
[Class A] m()      [Class B] m()
               ↘↙
            [class C]
            c.m() (which class m() method call)
```
Diamond access problem

**IE-** Java don't Support for multiple inheritance because of ambiguity problem.

### # How to Solve this problem with interface.

Because interface is take only method declaration not method definition. So when we are implement any interface in my class the class is responsible to provide the method implementation. (only one implementation) that why no any ambiguity problem with interface So Java Support multiple inheritance with interface.

**IE-**
1. If our class is not extending (extends) any class then our class extends Object class.

**Ex-**
```java
class A extends Object {
}
```

```
Object
  ↑
  A
```

2. If our class is extends any other class then our chied class is indirect to object class.

```
Object
  ↑
  B              class A extends B {
  ↑              }
  A
```

---

## Page 20 - Cyclic inheritance

### # Cyclic inheritance:- (Not Supported in Java.)

```java
class A extend A {
}
```

```java
class A extends B {
}

class B extends A {
}
```
Solution - make Single class

---

## Page 21 - method Signature

### method Signature:- m(int i)

**Ex:**
```java
class Test {
    public void m1(int i) {
    }                              ✓
    public void m2(int i, int j) {
    }
}
```

```java
class Test {
    public void m1(int i) {
    }                              X  Compile Time error
    public int m1(int i) {        Because method
    }                              Signature are Same
}
```

**IE-** No matter the data return type is Same or not But method Signature must be different for Method overloading, overloading.

Because Compiler find method on the basis of method Signature. This method Signature is register in method table.

---

### # Overloading:-

**IE-** If method have Same name but different argument in Same class is Called method overloading.

---

## Page 22

```
byte → short
           ↓ int → long → float → double
        char
```

**Ex:**
```java
class Test {
    public void m1() {
        Sop ("no-arg");
    }
    public void m1(int i) {        overloaded
        Sop ("int arg");           method
    }
    public void m1(double d) {
        Sop ("double arg");
    }
}
```

**NOTE-** Overloading is a Compiletime time polymorphism.
**NOTE-** Overloading is a Static polymorphism.
**NOTE-** Overloading is a Early Binding.

**NOTE-** Compiler is responsible to perform method Resolution based on Referenced type.

### # Automatic promotion in function overloading:-

**Ex-**
```java
public static void main() {
    Test t = new Test();
    t.m1(10);        // int arg
    t.m1(10.5f);     // double-arg
    t.m1('a');       // char → int arg (Because automatic promotion in function overloading)
    t.m1(10L);       // double-arg
}
```

**NOTE-** If method argument is not match then perform automatic promotion by the Compiler and then method not found then give Compile time error method not found. with this argument.

---

## Page 23 (Case 2)

**Ex (Case 2):**
```java
class Test {
    public void m(Object o) {
        Sop ("object version");
    }
    public void m(String s) {
        Sop ("String version");
    }
}

public static void main() {
    Test t = new Test();
    t.m1(new Object());   // object version
    t.m1("durga");        // String version
    t.m1(null);           // String version
}
```

**NOTE-** when passing parameters are Same then first priority of child class.

**NOTE-** In Function overloading Exact match always higher priority.

```
t.m1(new object());   // object version
```

**Case 3-**
```java
class Test {
    public void m(String s) { Sop ("String version")}
    public void m(StringBuffer s) {sop ("StringBuffer version");};
    public static void main() {
        Test t = new Test();
        t.m("durga")                    // String version
        t.m(new StringBuffer("durga")); // StringBuffer version
        t.m(null);                      // Compile error
    }
}
```

**NOTE-** Because String and StringBuffer not have parent-child.

---

## Page 24 (Case 4)

**Ex-**
```java
class Test {
    public void m(int i) {
        Sop ("General method");
    }
    public void m(int ... i) {
        Sop ("Generating var-arg-method");
    }
}

pSvm() {
    Test t = new Test();
    t.m();        // var-arg-method
    t.m(10, 20);  // var-arg-method
    t.m(10);      // General method
}
```

**NOTE-** Var-arg method Concept are Comes in Java 1.5 version when the older version Concept or newer version Concept are Comes together. then older version Concept higher priority in Java.

**Case 5-**
```java
class Test {
    public void m(int i, float f) {
        Sop ("int-float-version");
    }
    public void m(float f, int i) {
        Sop ("float-int version");
    }
}

main() {
    Test t = new Test();
    t.m1(10, 10.5f);   // int-float version
    t.m(10.5f, 10);    // float-int version
    t.m(10, 10);       // Compile Time error (not promot data type)
}
```

**NOTE-** Exact matching parameters higher priority.

---

## Page 25

### Method Reference type is always higher priority No matter what object we are assigning to this Reference variable (not run time Object)

```java
class Animal {
}

class Monkey extends Animal {
}

class Test {
    public void m(Animal a) {
        Sop ("Animal version");
    }
    public void m(Monkey m) {
        Sop ("monkey version");
    }
}

pSvmain() {
    Test t = new Test();

    Animal a = new Animal();
    t.m(a)    // Animal version

    Animal m = new Monkey();
    t.m(m);   // Animal version because Reference type is animal type
}
```

---

## Page 26 - Method Overriding

### # method Overriding:-

whatever method parent class has by default available in child class through inheritance if the child class not satisfied with parent method implementation that particular method child class is able to re-define this concept is method Overriding.

**Ex-**
```java
class P {
    public void property() {
        Sop ("property method");
    }
    public void many() {        // overridden method
        Sop ("Hemant ki Sadi");
    }
}

class C extends P {
    public void manay() {       // overriding method
        Sop ("Hariem ki Sadi");
    }
}

class Test {
    main() {
        P p = new P();
        P.many()    // parent method
        C c = new C();
        c.many()    // chied method

        P p1 = new C();    // check at run time
        P1.many();  // chied method.
    }
}
```

---

## Page 27 - Overriding Rules

### Overriding:-
1. Run-Time polymorphism
2. Dynamic polymorphism
3. Late binding.

**-** Run-time polymorphism perform by JVM. Run-time polymorphism always execute method who is the object of this method. no matter what reference variable are held object reference. (based on run-time object particular method is run).

---

### # Rules of method Overriding:-

**1-** In Overriding method Signature is Same (method name + method parameters both).

**2-** In Overriding return type must be Same till 1.4version but 1.5version return type is co-varient. (return type is different in overriding).

So Different return type  // Invalid in 1.4version
Different return type  // valid in 1.5version.

Covarient return type is applicable only for Reference type not for primitive type.

**e3-** Overriding Concept not applicable for private method. (private method are not override because these method not inherit from parent class to child class).

**e4-** Overriding Concept not applicable for final method. Because final method not re-declare. again.

---

## Page 28

| | Non Final | final | abstract | non-abstract | Parent method |
|---|---|---|---|---|---|
| ↓ | final | X | non-abstract | abstract | Child method |

| Synchronized | native | strictfp |
|---|---|---|
| ↓↑ | ↓↑ | ↓↑ |
| non-Synchronized | non-native | non-strict-fp |

**Rule 5-** In Overriding we can't Reduce Scope of modifiers.

**Ex-**
```java
class P {
    public void m() {}
}
class C extend P {
    protected void m() {}    X  (Reducing scope of access modifier)
}
```

**Ex-**
```java
class P {
    protected void m() {}
}
class C extends P {
    public void m() {}    ✓  (increase Scope of access modifier)
}
```

**Ex-**
```java
class P {
    protected void m() {}
}
class C extends P {
    protected void m() {}   ✓  (Same Scope of access modifier)
}
```

---

## Page 29

```
private < default < protected < public
```

| parent | public | protected | default | private |
|---|---|---|---|---|
| ↓ | ↓ | ↓ | ↓ | X |
| chied | public | protected/public | default/protected/public | Overriding concept not applicable for private access modifiers. |

---

### Overriding in Exception Handling:-

If chied class method throws any Checked exception Compulsory parent class method should throw the Same Check exception or its parent.

```
1:-  P: public void m() throws Exception      ✓
     C: public void m() {}

2:-  P: public void m()                       X
     C: public void m() throws Exception

3:-  P: public void m() throws Exception      ✓
     C: public void m() throws IoException

4:-  P: public void m() throws IoException    X
     C: public void m() throws Exception.

5:-  P: public void m() throws IoException    ✓
     C: public void m() throws FofException
```

---

## Page 30

```java
class P {
    public static void m1() {}
}
class C extends P {
    public void m1() {}     X
}
```

```java
class P {
    public void m1() {}
}
class C extends P {
    public static void m1() {}    X
}
```

**NOTE-** Overriding Static method to non-Static is not possible.
Overriding Non-Static method to Static method is not possible.

---

### # Method Hiding:- Static method override static method is Called method Hiding.

**Ex-**
```java
class P {
    public static void m() {}
}

class C extends P {
    public static void m() {}    // method Hiding.
}
```

**NOTE-** It is not method Overriding. But it is method Hiding. (Both parent class and child class method is static)

**NOTE-** In method Hiding method resolution will always takes care by Compiler based on reference type.

---

## Page 31

**NOTE-**
1. If both method (parent class or child class) are Static then method overriding. So method resolution based on reference type.

2. If both method (parent class or child class) are not Static then method overriding. So method resolution based on runtime object by the JVM.

---

### Q- Difference between method Hiding and method Overriding

**Ans:-**

| Method Hiding | Method Overriding |
|---|---|
| 1. Both parent class and child class method should be Static | 1. Both parent class and child class method should be non-Static |
| 2. Method Resolution always takes care by Compiler based on reference type | 2. method Resolution always takes Care by Jvm based on runtime object |
| 3. method Hiding is Compile time polymorphism Or Static polymorphism Or Early binding. | 3. Overriding is Runtime polymorphism or late Binding or Dynamic polymorphism. |

---

## Page 32 - Variable Hiding / Variable Shadowing

**Ex-**
```java
class P {
    public void m(int ... i) {
        Sop ("parent");
    }
}

class C extends P {
    public void m1(int i) {
        Sop ("chied");
    }
}
```

**NOTE-** It is overloading, but not overriding because parameters are different.

### # variable Hiding or variable Shadowing:-

**Ex-**
```java
class P {
    String s = "Parent";
}
class C extends P {                   variable Hiding
    String s = "child";    ✓         or
}                                     variable Shadowing.

class Test {
    main() {
        P p = new P();
        Sop(p.s);
        C c = new C();
        Sop(c.s);
        P p1 = new C();
        Sop(p1.s);
    }
}
```

**NOTE-** Variable Resolution always takes care by Compiler based on reference type.

---

## Page 33 - Comparing between overloading and overriding

### # Comparing between overloading and overriding.

| Property | Overloading | Overriding |
|---|---|---|
| 1. Argument Type | must be Same (at least one) | must be Same (Including order) |
| 2. method Name | must be Same | must be Same |
| 3. Private/final/Static method | Can be overloaded | Cannot be overridden |
| 4. return type | No Restriction | must be Same (1.4v) Co-varient return type |
| 5. throws clause | No Restriction | if child class method throws any checked Exception, Compulsory parent class method should throw same check exception or parent. |
| 6. method Resolution | Compiler based on Reference type | by Jvm based on Runtime object |
| 7. other names | Compile time polymorphism, Early binding, Static polymorphism | RunTime polymorphism, Late binding, Dynamic polymorphism |

---

### # Polymorphism:-
- poly means many.
- morphism means form.

⇒ One names but many functionality.

---

## Page 34 - OOP Diagram

```
           Polymorphism
          ↙             ↘
  Compile-Time          Run-Time
  polymorphism          polymorphism
     ↙      ↓                ↓
Overloading  Overriding    Overriding
```

A Boy Start Love with the word friendship, but GIRL end Love with the Same word friendship. word is the Same but attitude is different. This beautiful concept of oops is nothing but polymorphism.

```
           Encapsulation
                ↓
            Security
                ↓
           OOPS ← Reusability → Inheritance
                ↓
           Flexibility
                ↓
           polymorphism
```

**3-pillars of oops**

---

## Page 35 - Object Typecasting

### # Object Typecasting:-

```java
Object o = new String("durga");
StringBuffer sb = (StringBuffer) o;
        a                b    c   d
```

```
⇒ A b = (c) d;
```

\# Converting d type object to c type object and assigning c type object with A type reference variable.

**⇒ 1. Compile time checking-1:-** The typed and type c must have Some relationship. (Either parent to child Or child to parent Or Same type.)

**2. Compile time checking-2:-** type c and must be A types. c must be Either Same as A type or its chied type.

**3. Run-Time checking:-** Type of d must be Same as type of c. and Either its parent (d or parent) or Same type.

---

## Page 36 - manhas of Object Typecasting

### # manhas of Object Typecasting:-

**A b = (c) d;**

- **manha-1** (At compile time):- The type of 'a' and 'c' must have Some relationship (Either parent to child Or child to parent Or Same type) otherwise we will get Compile time error.

- **manha-2** (At compile time):- 'c' must be Either Same or derived type of 'A' otherwise we will get Compile time error.

- **manha-3** (At Runtime):- The underlying original object type of 'c' otherwise we will get runtime exception Saying : ClassCastException.

---

## Page 37 - Constructor

### # Constructor:-

#### Need of Constructor:-
Constructor is responsible to initialize the object.

```java
Student s = new Student();
```
⇒ New keyword is responsible to create an object
⇒ And Constructor is used to perform initialization of object.

- Step 1- Object Creation (with new keyword)
- Step 2- Object initialization (Constructor call)

**Ex-**
```java
class Student {
    String name;
    int roll;
    Student() {}
    Student(String name, int roll) {
        this.roll = roll;
        this.name = name;
    }
}

main() {
    Student S = new Student("Hemant", 100);
}
```

```
Phase 1-      name = null      Object creation and default value
              marks = 0        assigned by JVM
              roll
                  S

Phase 2-      name = Hemant    initialization done by Constructor.
              roll = 100
                  S
```

---

## Page 38 - Rules of Constructor

### # Rules of Constructor:-

**Rule 1-** Name of the Constructor is Same as class name.

**Rule 2-** Return type Concept is not applicable for Constructor. if we are add return type then Java Compiler treated like Java method not Constructor.

**Rule 3-** public, default, Protected, private access modifiers are applicable for Constructor.

### Default Constructor:- (Created by Compiler)

If we not add Any Constructor in our class then Java Compiler will add Default Constructor automatically.

### Prototype of Default Constructor:-
1. It is always no-arg Constructor.
2. access modifier of Constructor is Same as class modifiers. This rule is applicable for only (default and public)
3. default Constructor Contains only one line in body. `Super();`

**NOTE-** The first line of Constructor Either Super() or this(). If programmers don't write anything Compiler write by default super().

---

## Page 39

**Case 1-**
```java
class Test {
    Test() {
        Sop ("constructor");
        Super();   // Compile time error
    }
}
```

**Case 2-**
```java
class Test {
    Test() {
        Super()
        this();   // Compile time error  (this() is second line)
        Sop ("constructor");
    }
}
```

**NOTE-** In constructor we are not use (super and this) both Simultaneously.

**Case 3-**
```java
class Test {
    public void m1() {
        Super();    // Compile time error   Call to super must be
        Sop ("method");               first statement inside
    }                                 constructor only.
}
```

```
Super()  →  1. we Can use only inside Constructor
this()   →  2. we Should use only in first line
            3. we Can use only one but not both Simultaneously
```

---

## Page 40 - this and super

```java
class P {
    String s = "parent variable";
}
class C extends P {
    String s = "child variable";
    public void m1() {
        Sop(s)          // child variable
        Sop(this.s)     // child variable
        Sop(Super.s)    // parent variable.
    }
}
```

**NOTE-** In static method we are not use this and super keyword. this and Super keyword is related to instance variable.

---

## Page 41 - Constructor Overloading

### # Constructor Overloading:-

**Ex-**
```java
class Test {
    Test(double d) {
        this(10);
        Sop ("double-arg Constructor");
    }
    Test(int i) {
        this();
        Sop ("int-arg-Constructor");
    }
    Test() {
        Sop ("no-arg-Constructor");
    }
}

main() {
    Test k1 = new Test(10.5)
}
```

**Output-**
```
→ no-arg-Constructor
  int-arg Constructor
  double-arg-Constructor.
```

### # inheritance and overriding in Constructor

**NOTE-** Inheritance and overriding is not applicable for Constructor.

---

## Page 42 - Abstract class Constructor

**Ex-**
```java
abstract class Test {
    int x;
    Test(int x) {
        this.x = x;
    }
}
```

**NOTE-** Constructor is applicable for abstract class.

**Ex-**
```java
class P {
    P(int i) {
        Super();
    }
}

class C extends P {
    C() {
        Super();   // Compile time error (because parent class not Contains any no-arg-constructor)
    }
}
```

**Ex-**
```java
class P {
    P() throws IoException {
    }
}

class C extends P {
    C() {
        Super();   // Compile time error
    }              // Super keyword call Super class Constructor or
}                  // Super class constructor throws exception but
                   // Child class Constructor not handle that exception
                   // So Compiler throw an error.
```
