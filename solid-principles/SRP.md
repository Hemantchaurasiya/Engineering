# Single Responsibility Principle (SRP)

## 1. What is Single Responsibility Principle?
The Single Responsibility Principle (SRP) is one of the SOLID principles of object-oriented design. It states that a class should have only one reason to change, meaning it should have only one responsibility. A responsibility is a specific role or function the class fulfills within an application.

### Key Points:
- A class should focus on a single responsibility.
- This makes the class easier to understand, test, and maintain.
- It reduces the chances of unintended side effects when making changes.

---

## 2. Where to Use Single Responsibility Principle?
The SRP can be applied in any project, especially when designing classes and modules. Some specific scenarios include:
- When a class is performing multiple, unrelated tasks.
- When a class grows large and becomes difficult to maintain or test.
- When responsibilities can be clearly divided among multiple classes.

---

## 3. Why Do We Use Single Responsibility Principle?
The SRP is used to:
- **Improve Code Readability**: Easier to understand classes and their purposes.
- **Enhance Maintainability**: Simplifies changes and debugging by isolating responsibilities.
- **Enable Reusability**: Smaller, focused classes can be reused in other parts of the application.
- **Facilitate Testing**: Unit testing is easier with smaller, single-purpose classes.
- **Avoid Coupling**: Reduces dependencies among unrelated functionalities.

---

## 4. Pros and Cons of Single Responsibility Principle
### Pros:
1. Improved clarity and simplicity in code design.
2. Easier debugging and maintenance.
3. Increased modularity and reusability of code.
4. Reduced chances of regression errors.

### Cons:
1. Can lead to more classes in the system, potentially increasing complexity.
2. May require extra time during initial development to design responsibilities properly.

---

## 5. Analogy of Single Responsibility Principle
**Library Books**:
Think of a library where books are arranged by categories like fiction, science, history, etc. If all books were randomly arranged without categories, it would be hard to find a specific book. Similarly, a class with multiple responsibilities becomes messy and hard to manage. Categorizing books into sections is like assigning a single responsibility to a class.

---

## 6. Real-World Use Case of Single Responsibility Principle
### Use Case:
Consider an application that processes and sends email notifications. Instead of having a single class that handles user information, email content generation, and email sending, these responsibilities can be separated into three classes:
1. `UserManager`: Manages user data.
2. `EmailContentGenerator`: Creates email content.
3. `EmailSender`: Handles the sending of emails.

This separation ensures each class has a single responsibility, making the application more modular and maintainable.

---

## 7. Code Example of Real-World Use Case Using C++
```cpp
#include <iostream>
#include <string>

// Class responsible for managing user data
class UserManager {
public:
    std::string getUserEmail(int userId) {
        // In a real application, fetch the user's email from a database
        return "user@example.com";
    }
};

// Class responsible for generating email content
class EmailContentGenerator {
public:
    std::string generateContent(const std::string& userName) {
        return "Hello " + userName + ",\nWelcome to our service!";
    }
};

// Class responsible for sending emails
class EmailSender {
public:
    void sendEmail(const std::string& email, const std::string& content) {
        std::cout << "Sending email to: " << email << "\n";
        std::cout << "Content: \n" << content << "\n";
    }
};

int main() {
    UserManager userManager;
    EmailContentGenerator contentGenerator;
    EmailSender emailSender;

    // Example usage
    int userId = 1; // Example user ID
    std::string userEmail = userManager.getUserEmail(userId);
    std::string emailContent = contentGenerator.generateContent("John Doe");
    emailSender.sendEmail(userEmail, emailContent);

    return 0;
}
```

### Explanation of Code:
1. **UserManager**: Responsible for retrieving user-related data.
2. **EmailContentGenerator**: Responsible for creating the email content.
3. **EmailSender**: Responsible for sending the email.

This design adheres to the SRP as each class has a clearly defined responsibility.

---

# **Single Responsibility Principle (SRP) in Java**

## **1. Definition of SRP**

The **Single Responsibility Principle (SRP)** states that:

> “A class should have only one reason to change.”
> 

This means that a class should only have **one responsibility** or **one purpose** in the system. It should focus on doing a single task effectively.

✅ **Key Idea:** Each class should encapsulate only one functionality.

---

## **2. Why is a Single Responsibility Important?**

### **Benefits of SRP:**

1. **Improves Maintainability** – If a class has a single responsibility, it's easier to modify without affecting unrelated functionalities.
2. **Enhances Readability** – Code is easier to understand and modify.
3. **Reduces Coupling** – Changes in one responsibility do not impact another.
4. **Facilitates Unit Testing** – A well-separated class is easier to test.
5. **Encourages Reusability** – Single-purpose classes can be reused in multiple places.

---

## **3. Identifying Responsibilities in a Class**

To identify responsibilities, ask:

- Does this class have **more than one reason to change**?
- Does this class perform **multiple unrelated tasks**?
- Is the class responsible for **both data manipulation and UI representation**?

Example:

A **Report** class that:

- **Generates a report** (Business Logic)
- **Saves the report to a database** (Persistence Logic)
- **Prints the report** (Presentation Logic)

🔴 **Problem:** This class has **three reasons to change**, violating SRP.

---

## **4. Violating SRP: Real-World Example**

Consider an **Invoice** class that handles:

1. **Calculating the total invoice amount** (Business Logic)
2. **Saving invoice details to a database** (Persistence Logic)
3. **Printing the invoice** (Presentation Logic)

### ❌ **Bad Example: Violating SRP**

```java
java
CopyEdit
class Invoice {
    public void calculateTotal() {
        // Business logic for calculating invoice total
    }

    public void saveToDatabase() {
        // Database logic to save invoice
    }

    public void printInvoice() {
        // Printing logic for invoice
    }
}

```

🔴 **Why is this bad?**

- If the **business logic changes**, the entire class must be modified.
- If the **database structure changes**, the class is affected.
- If the **printing format changes**, it requires modifications.

---

## **5. Fixing SRP Violations with Refactoring**

We separate responsibilities into **three classes**:

1. **Invoice (Handles business logic)**
2. **InvoiceRepository (Handles database operations)**
3. **InvoicePrinter (Handles printing logic)**

### ✅ **Good Example: Applying SRP**

```java
java
CopyEdit
// Business Logic
class Invoice {
    public void calculateTotal() {
        // Logic to calculate total
    }
}

// Persistence Logic
class InvoiceRepository {
    public void save(Invoice invoice) {
        // Logic to save invoice to database
    }
}

// Presentation Logic
class InvoicePrinter {
    public void print(Invoice invoice) {
        // Logic to print invoice
    }
}

```

✅ **Benefits:**

- If the business logic changes, only `Invoice` needs modification.
- If the storage mechanism changes, only `InvoiceRepository` is affected.
- If the printing format changes, only `InvoicePrinter` is updated.

---

## **6. Relationship of SRP with Cohesion & Separation of Concerns (SoC)**

### **Cohesion**

- **High Cohesion**: A class is **focused** on a single task.
- **Low Cohesion**: A class is **doing too many things**, making it hard to maintain.

✅ **Applying SRP leads to high cohesion**, improving code quality.

### **Separation of Concerns (SoC)**

- **SRP is a form of SoC**.
- SoC states that **different functionalities should be handled by different components**.
- Example:
    - **Business logic** → Service Layer
    - **Persistence logic** → Repository Layer
    - **Presentation logic** → Controller Layer

---

## **7. Design Patterns That Support SRP**

Several **design patterns** help enforce SRP:

### **1. Factory Pattern**

- **Why?** It **separates object creation logic** from business logic.
- **Example:** Instead of creating an object inside a class, use a `Factory` to generate objects.

✅ **Example: Using Factory Pattern**

```java
java
CopyEdit
class InvoiceFactory {
    public static Invoice createInvoice() {
        return new Invoice();
    }
}

```

✅ **Benefit:** The **Invoice** class doesn’t handle object creation.

---

### **2. Observer Pattern**

- **Why?** It **decouples dependent classes**.
- **Example:** Instead of **Invoice** handling notifications, an **Observer** does.

✅ **Example: Using Observer Pattern**

```java
java
CopyEdit
interface InvoiceListener {
    void onInvoiceCreated(Invoice invoice);
}

class EmailNotifier implements InvoiceListener {
    public void onInvoiceCreated(Invoice invoice) {
        System.out.println("Sending email for invoice...");
    }
}

```

✅ **Benefit:** The **Invoice** class only deals with invoices, and email notifications are handled separately.

---

### **3. Strategy Pattern**

- **Why?** It **separates different behaviors** into different classes.
- **Example:** Instead of **Invoice** deciding how to calculate the total, use a strategy.

✅ **Example: Using Strategy Pattern**

```java
java
CopyEdit
interface TaxCalculator {
    double calculateTax(double amount);
}

class IndiaTaxCalculator implements TaxCalculator {
    public double calculateTax(double amount) {
        return amount * 0.18;
    }
}

class USATaxCalculator implements TaxCalculator {
    public double calculateTax(double amount) {
        return amount * 0.10;
    }
}

class Invoice {
    private TaxCalculator taxCalculator;

    public Invoice(TaxCalculator taxCalculator) {
        this.taxCalculator = taxCalculator;
    }

    public double calculateTotal(double amount) {
        return amount + taxCalculator.calculateTax(amount);
    }
}

```

✅ **Benefit:** Different tax calculations are **separated**, making code extensible.

---

## **8. Conclusion**

✅ **SRP ensures that each class has only one responsibility**.

✅ **Violating SRP leads to poor maintainability and high coupling**.

✅ **Using patterns like Factory, Observer, and Strategy helps enforce SRP**.

✅ **Following SRP leads to clean, modular, and reusable code**.
