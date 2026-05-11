Here are **scenario-based Object-Oriented Programming (OOP)** interview questions designed to test your understanding of OOP principles (Encapsulation, Inheritance, Polymorphism, Abstraction), design decisions, and real-world applications. These are especially helpful for Java Developer interviews.

---

### **1. Encapsulation**

**Scenario:**

You're working on a banking application. The `BankAccount` class has fields like `balance`, `accountNumber`, and `accountHolderName`.

**Question:**

How would you ensure that `balance` cannot be modified directly from outside the class, but can only be updated through deposit and withdrawal methods?

---

### **2. Inheritance**

**Scenario:**

You’re building a school management system. You have classes like `Person`, `Student`, and `Teacher`.

**Question:**

How would you design the class hierarchy using inheritance? What properties and behaviors would you put in the parent class vs. child classes?

---

### **3. Polymorphism**

**Scenario:**

In an e-commerce system, you have different payment methods: `CreditCard`, `UPI`, `PayPal`.

**Question:**

How would you use polymorphism to handle different payment methods in a unified way? How would you call the `processPayment()` method regardless of the payment type?

---

### **4. Abstraction**

**Scenario:**

You are designing a system for a vehicle rental company. Vehicles can be cars, bikes, or trucks.

**Question:**

How would you use abstraction to represent common behaviors of vehicles while hiding implementation details?

---

### **5. Interface vs Abstract Class**

**Scenario:**

You’re building a plugin system where plugins can be developed by third parties.

**Question:**

Would you use an interface or an abstract class for the plugin contract? Why?

---

### **6. Real-World Modeling**

**Scenario:**

Design a class structure for a library system where books can be borrowed by members. A book can be reserved, issued, or returned.

**Question:**

How would you model this using OOP? Which classes would you create and how would they interact?

---

### **7. Composition vs Inheritance**

**Scenario:**

You have a `Car` class that has an `Engine`. You’re debating between inheritance and composition.

**Question:**

Would you inherit `Engine` in `Car`, or would you include it as a field? Why?

---

### **8. SOLID Principles (Single Responsibility)**

**Scenario:**

In a report generation class, you handle fetching data, formatting it, and printing it.

**Question:**

Which OOP principle is violated here and how would you refactor it?

---

### **9. Method Overloading vs Overriding**

**Scenario:**

You have a `Printer` class with multiple ways to print documents: by string, by file, or by URL.

**Question:**

Would you use method overloading or overriding? Explain with examples.

---

### **10. Open/Closed Principle**

**Scenario:**

You're adding a new shape (`Triangle`) to an existing shape area calculator, which already supports `Circle` and `Rectangle`.

**Question:**

How would you design your classes so that you can add new shapes without modifying existing code?

---

### **11. Dependency Injection & Loose Coupling**

**Scenario:**

You have a `NotificationService` that sends emails. Later, you need to support SMS and push notifications.

**Question:**

How would you design your classes to support multiple notification types without modifying `NotificationService` every time?

---

### **12. Object Lifecycle**

**Scenario:**

In a Java-based shopping app, `Cart` objects are created for every user session.

**Question:**

How would you manage the lifecycle of `Cart` objects so they are created per session and garbage collected afterward?

---

### **13. Interface Segregation**

**Scenario:**

You have a `Printer` interface with methods `print()`, `scan()`, and `fax()`. But some printers only print.

**Question:**

What design improvement would you make here to follow interface segregation principle?

---

### **14. Aggregation vs Composition**

**Scenario:**

A `University` has `Departments`, and `Departments` have `Professors`. If a department is deleted, professors still exist.

**Question:**

How would you model this relationship: aggregation or composition? Why?

---

### **15. Covariant Return Type**

**Scenario:**

You have a base class `Document` with a method `getContent()` returning a `DocumentContent`. A subclass `PdfDocument` returns `PdfContent`.

**Question:**

How can you override the method to return a more specific type?

---

### **16. Abstract Factory Pattern**

**Scenario:**

You’re developing a cross-platform UI library that should support both Windows and macOS.

**Question:**

How would you use OOP principles to create UI components like buttons and checkboxes differently for each OS without changing the client code?

---

### **17. Overriding `equals()` and `hashCode()`**

**Scenario:**

You're using a `HashSet` to store `Student` objects. Even though two students have the same ID, both are being stored.

**Question:**

What’s likely wrong with your class? How would you fix it?

---

### **18. Liskov Substitution Principle**

**Scenario:**

You have a class `Bird` with a method `fly()`. A subclass `Penguin` overrides it but throws an exception.

**Question:**

Why is this a violation of Liskov Substitution Principle? How would you redesign this?

---

### **19. Static vs Dynamic Binding**

**Scenario:**

You have a superclass `Animal` and a subclass `Dog`. You call a method on an `Animal` reference pointing to a `Dog` object.

**Question:**

Which version of the method will be called, and why?

---

### **20. Immutability**

**Scenario:**

You’re creating a class `UserProfile` that once created, should never change its data.

**Question:**

How would you make this class immutable in Java?

---

### **21. Builder Pattern**

**Scenario:**

You need to create complex `User` objects with optional fields like address, phone number, and profile picture.

**Question:**

How would you implement the creation of such objects cleanly and safely using OOP principles?

---

### **22. Multiple Inheritance**

**Scenario:**

Java doesn’t support multiple class inheritance, but you need a `SmartPhone` that behaves like a `Camera`, `Phone`, and `MP3Player`.

**Question:**

How would you achieve this using OOP principles in Java?

---

### **23. Class Responsibility**

**Scenario:**

You have a `User` class that stores data, validates input, connects to a database, and sends notifications.

**Question:**

Which OOP principle is being violated? How would you refactor?

---

### **24. Association vs Inheritance**

**Scenario:**

In a hospital system, `Doctor` and `Patient` both have `Address`.

**Question:**

Would you use inheritance to share the `Address` or use association? Why?

---

### **25. Runtime vs Compile-time Polymorphism**

**Scenario:**

You have overloaded methods `print(int)`, `print(String)` and a `print()` method overridden in child class.

**Question:**

Which of these demonstrate compile-time vs runtime polymorphism? Explain with examples.

---

### **26. Strategy Pattern**

**Scenario:**

You're building a travel app. Users can sort hotels by price, rating, or distance.

**Question:**

How would you design your sorting logic to allow easy switching between sorting strategies?

---

### **27. Abstract Class in Real-world Modeling**

**Scenario:**

You are designing a system for managing animals in a zoo. Animals can be mammals, reptiles, birds, etc.

**Question:**

How would you use abstract classes and interfaces to model this?

---

### **28. Tight Coupling Problem**

**Scenario:**

You have a `ReportGenerator` class that directly creates a `PDFWriter` object inside it.

**Question:**

What’s the issue with this design, and how would you fix it?

---

### **29. Custom Exception Design**

**Scenario:**

Your app has a `TransactionService` that can fail due to insufficient balance or invalid account.

**Question:**

How would you design custom exceptions to handle such cases cleanly?

---

### **30. Object Cloning**

**Scenario:**

You need to create a duplicate of an object but with a deep copy of its fields.

**Question:**

How would you implement object cloning while avoiding the default shallow copy behavior?

---

### **31. Observer Pattern**

**Scenario:**

You are building a stock market app where users should be notified when stock prices change.

**Question:**

How would you implement this using the Observer pattern? Which classes would be observers and subjects?

---

### **32. Factory Method Pattern**

**Scenario:**

You’re designing a document editor that can open and create different document types like Word, PDF, and Excel.

**Question:**

How would you use the Factory Method pattern to handle this without switching logic in the client code?

---

### **33. Law of Demeter (Least Knowledge Principle)**

**Scenario:**

In a `Customer` class, you often see chains like `customer.getAddress().getCity().getName()`.

**Question:**

What’s the problem with this kind of chaining? How would you apply the Law of Demeter to fix it?

---

### **34. Singleton Pattern**

**Scenario:**

You’re developing a logging utility that should only have one instance throughout the application.

**Question:**

How would you implement the Singleton pattern in a thread-safe manner?

---

### **35. Object Collaboration**

**Scenario:**

In an e-commerce app, you have `Order`, `Cart`, `Product`, and `User` classes.

**Question:**

How should these classes collaborate using OOP principles to place an order?

---

### **36. Delegation Principle**

**Scenario:**

Your `Manager` class needs to perform report generation, but that’s handled by `ReportGenerator`.

**Question:**

How would you use delegation to separate responsibilities and improve modularity?

---

### **37. Adapter Pattern**

**Scenario:**

Your system uses a legacy payment gateway, but a new interface is expected by your modern application.

**Question:**

How would you use the Adapter pattern to make the old system work with the new one?

---

### **38. Null Object Pattern**

**Scenario:**

Instead of returning `null` when a `User` is not found in the database, you want to return a default object.

**Question:**

What are the benefits of using the Null Object pattern here?

---

### **39. Preventing Inheritance**

**Scenario:**

You’ve designed a class `SecurityManager` that should not be extended for safety reasons.

**Question:**

How would you prevent this class from being subclassed?

---

### **40. Composition Root**

**Scenario:**

In a large application using dependency injection, you want to manage all object wiring in one place.

**Question:**

What is a composition root, and why is it important for application architecture?

---

### **41. Open/Closed Principle**

**Scenario:**

You built a `DiscountCalculator` that applies a flat discount. Now you need to support seasonal, loyalty, and coupon discounts.

**Question:**

How would you design your code so that new discount types can be added without modifying the existing logic?

---

### **42. Template Method Pattern**

**Scenario:**

You're building a report system where different types of reports (PDF, HTML, Excel) have common steps but some differ.

**Question:**

How would you apply the Template Method pattern to enforce a consistent process?

---

### **43. Encapsulation Breach**

**Scenario:**

You have a `BankAccount` class where the balance is a public variable.

**Question:**

What’s the risk of this design? How does encapsulation solve this?

---

### **44. Composition Over Inheritance**

**Scenario:**

You built a class `Bird` with a `fly()` method, but now you need to support ostriches and penguins.

**Question:**

Why is using composition better here than inheritance?

---

### **45. Command Pattern**

**Scenario:**

You’re building a text editor with undo/redo features. Each action (cut, paste, delete) should be reversible.

**Question:**

How would you model this using the Command pattern?

---

### **46. Method Overriding vs Overloading**

**Scenario:**

You have methods `draw(Shape shape)` and `draw(Circle circle)` in a class. You call `draw()` with a `Circle` object assigned to a `Shape` reference.

**Question:**

Which method will be called? Why?

---

### **47. Interface vs Abstract Class**

**Scenario:**

You're designing a `Vehicle` base for cars, bikes, and electric scooters.

**Question:**

When would you choose an interface over an abstract class for the common behavior?

---

### **48. Data Hiding vs Abstraction**

**Scenario:**

You created a class `Employee` with private fields and public methods for operations like calculateSalary().

**Question:**

How is this an example of both data hiding and abstraction?

---

### **49. SOLID Violation Debugging**

**Scenario:**

You get a bug where adding a new type of user breaks other existing functionality.

**Question:**

Which SOLID principle might have been violated and how would you fix it?

---

### **50. Fluent Interfaces**

**Scenario:**

You’re chaining method calls like:

```java
java
CopyEdit
User user = new User().setName("Alice").setAge(30).setEmail("alice@example.com");

```

**Question:**

What design principle enables this? How would you implement a fluent interface in your class?
