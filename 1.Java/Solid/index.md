# SOLID Principles

## Pre Requisite :- OOP's Concepts

---

**Q. Why we Should learn Solid principles ?**

**Ans:-** The goal of SOLID principles is a coding standards that all the developers should have a clear concept for developing software properly to avoid bad design.

SOLID principles will help to reduce code complexity, the coupling between classes, seperating responsibilities for each class and defining their relations.

---

## Introduction :-

- In Java, SOLID principles are an Object Oriented approach that are applied to software structure design.

- It is Conceputalized by Robert C. Martin.

- These 5 principles have changed the world of Object Oriented programing, also changed the way of writing Software.

- It also ensures that the software is modular, easy to understand, debug and refactor.

---

## SOLID Acronym :-

- **S** - Single Responsibility Principle (SRP)
- **O** - Open Closed Principle (OCP)
- **L** - Liskov Substitution Principle (LSP)
- **I** - Interface Segregation Principle (ISP)
- **D** - Dependency Inversion Principle (DIP)

---

## 1. Single Responsibility Principle (SRP) :-

This principle state that

- a) A class should have only one reason to change
- b) A class should have a Single responsibility or Single Job or Single purpose

**Example 1 -** In Single Controller class we are implementing all these below features:
1. validations
2. business logic
3. Calling backend Systems
4. request and response preparation

This will impact Code maintainbility, code readability, code understandability.. etc  
So it will voilates the SRP.

To overcome this problems, spit Controller class into multiple class.

---

**SRP Solution - Mapping responsibilities to separate classes:**

1. validations → validator class
2. business logic → Service class or business class or process class or logic class
3. Calling backend system → DAO class, Service client class
4. request and response preparation → RequestBuilder class, responseBuilder class

**NOTE -** If we are implementing SRP there would more classes present in your project as each class will have only one responsibility.

**Example 2 - Bank Services:**

1. deposite → deposite class
2. withdraw → withdraw class
3. Cret loans → Cret loans class
4. Send OTP → Send OTP class

To achieve SRP then we should a seperate class that perform Single functionality only.

---

## 2. Open Closed Principle (OCP) :-

This principle states that Software entities (classes, modules, function etc.) should be open for extension but closed for modification.

i.e. we should able to extend class behaviour without modifying existing code.

**Example:**

```java
public class NotificationService {
    public void sendOTP(String medium) {
        // send OTP via email
        // send OTP via whatapp
        // send OTP via sms
    }
}
```

**Problem:-** Current logic will send notifications via email, sms and whatapp but in future if we want send notifications via telegram then we need to modify source code in NotificationService.

To Overcome this problems, what OCP Says  
"Open for extension but close for modification"

i.e. It is not recommended to modify the Notification Service for each OTP feature, it will voilate OCP.

**Solution:-** To Overcome this we need to design our code in such a way that everyone can reuse your feature by just extending it and if the need any customization then can extend it and their feature on top of it.

We should design as below:

```java
public interface NotificationService {
    public void SendOTP(String medium);
}
```

**1. Email Notification**
```java
public class EmailNotification implements NotificationService {
    public void sendOTP(String medium) {
    }
}
```

**2. Mobile Notification**
```java
public class SmsNotification implements NotificationService {
    public void sendOTP(String medium) {
    }
}
```

---

## 3. Liskov Substitution Principle (LSP) :-

This principles State that "Derived or Child classes must be substitutable for their base or parent classes".

i.e. If class A if child class of class B, then we should able replace B with A without interrupting the behaviour of the program.

```
class B {        Class A extends B {
}                }

B b = new A();
```

**Example -**

```java
public abstract class SocialMedia {
    public abstract void chatWithFriend();
    public abstract void groupVedioCall();
    public abstract void publishPosts();
    public abstract void SendPhotosAndVidios();
}
```

Some of the social media are facebook, whatsapp

```java
public class facebook extends SocialMedia {
    public abstract void chatWithFriend() {
    }
    public abstract void groupVedioCall() {
    }
    public abstract void publishPost() {
    }
    public abstract void SendPhotosAndVedios() {
    }
}
```

```java
public class whatsapp extends SocialMedia {
    public abstract void chatWithFriend() { }
    public abstract void groupVediocall() { }
    public abstract void publishpost() { }
    public abstract void SendPhotosAndVedios() { }
}
```

**NOTE -** SocialMedia sm = new whatsapp(); // wrong  
because whatsapp doesn't Support upload/publishpost for friends, it just for chatting only

This application doesn't Support follow the LSP.

**Solution:-** To overcome this problem we should write the code as below which will follow LSP.

```java
public interface SocialMedia {
    public abstract void chatWithFriend();
    public abstract void SendPhotosAndVidios();
}

public interface SocialManager {
    public abstract void publishPosts(Object post);
}

public interface VidioCallManager {
    public abstract void groupVedioCall(String[] users);
}
```

**NOTE -** we have Segregate Specific functiononality to Seperate classes to follow LSP.

```java
public class facebook implements SocialMedia, SocialPost media, VedioCallManager {
    // write implementation for all the features
}

public class Instagram implements SocialMedia, SocialPostMedia {
    // write implementation for all the features.
}
```

**Benefits of LSP:-**
1. Code Reusability
2. Easier maintenance
3. Reduced Coupling

---

## 4. Interface Segregation Principle - (ISP) :-

→ No client should be forced to depend on methods that it does not use.

**Problem:-** Polluting the interface in project development.

**Example 1:-**
```java
public interface Animal {
    func fly();
    func eat();
}

interface Flyable {
    func fly()
}

interface Feedable {
    func eat();
}
```

**Example - UPIPayments:**

```java
public interface UPIPayments {
    public void payMoney();
    public void getScratchCard();
    public void getCashBackAsCreditBalance();
}
```

UPI payment are google pay and paytm.

**Problem:-** Google pay supports all the above features and this can be implement UPIPayments interface but paytm doesn't support getCashBackAsCreditBalance() feature, So here we shuldn't force paytm client to override this method by implementing UPIPayments interface.

**Solution:-** we need to Segregate interface based on client need, So it support ISP we can design something like below:

```java
public interface UPIPayments {
    public void payMoney();
    public void getScratchcard();
}

Public interface CashBackManager {
    public void getCashBackAsCreditBalance();
}
```

→ No client should be forced to depend on methods that it does not use.

→ This principle states that we should split our interfaces into smaller and more Specific ones.

ISP, ask you to Create a different interfaces for different responsibilities.

i.e. don't group unrelated behaviours in one interface, we should break if we have already an interface with many responsibilities and the implementor does need all this stuff.

---

## 5. Dependency Inversion Principle (DIP) :-

→ This principle states that we must use abstract classes and interfaces instead of concrete implementation.

**Problem:-** we will goto Hyderabad Central for shopping to buy something, and we decide to pay for it using Card we will give Card to check for making the payment, that clerk guy doesn't bother to check what kind of card you have given.

Even if we given a debit Card of Credit Card it not even matter, they will Simply swipe it.

This is what abstraction between clerk and Customer to relay on card processing.

**Example:-**

```java
public class DebitCard {
    public void doTransaction(int amount) {
        Sys("txn done with debitcard");
    }
}

Public class CreditCard {
    public void doTransaction(int amount) {
        Sys("txn done with creditcard")
    }
}
```

Now with these 2 Cards customer went shopping mall and purchased some orders and decided to pay using CreditCard.

```java
public class ShoppingMall {
    private DebitCard debitcard;
    public ShoppingMall(DebitCard debitcard) {
        this.debitcard = debitcard;
    }
    public void dopayment(Object order, int amount) {
        debitCard.doTransaction(amount);
    }
    public static void main(String[] args) {
        DebitCard debitcard = new DebitCard();
        ShoppingMall shoppingmall = new ShoppingMall(debitcard);
        Shopigmall.dopayment("order item name", 1000);
    }
}
```

**NOTE -** If we observe this is wrong design of Coding, now ShoppingMall is tightly coupled with DebitCard. If user is trying pay the bill using credit card then he will get error like some error in your Card.

If we remove debitCard from constructor and inject CreditCard, which is not good approach to write the code. Now it would tightly coupled with Credit Card.

If we want for DIP we need to design our application in such a way that ShoppingMall application should accept any type of Card (It shouldn't care whether it is debit card or credit card).

To Simplify this designing principle we need write interface Called BankCard.

```java
Public interface BankCard {
    public void doTransaction(int amount);
}

Public class CreditCard implement BankCard {
    public void doTransaction(int amount) {
    }
}

public class DebitCard implement BankCard {
    public void doTransaction(int amount) {
        Sys("txn done with debitcard");
    }
}
```

```java
public class Shopping Mall {
    Private BankCard bankCard;
    public ShoppingMall(BankCard bankCard) {
        this.bankcard = bankCard;
    }
    public void dopayment(Object order, int amount) {
        debitCard.doTransaction(amount);
    }
    public static void main(String args[]) {
        BankCard bankcard = new CreditCard();
        ShoppingMall shoppingmall = new ShoppingMall(bankcard);
        Shoppingmall.dopayment("order item name", 10000);
    }
}
```

**NOTE -** If we observe Shopping mall is loosely coupled with BankCard, any type of card process the payment without any impact, which proofs DIP.

---

## Summary :-

1. **SRP :-** A class should only One responsibility and one reason to Change/modify that class.

2. **OCP :-** Open for extension and Closed for modification.  
   i.e. not recommended to modify the logic for every feature, try to write new class for each feature.

3. **LSP :-** A Derived or Child classes should be Substitutable for their base or parent class.

4. **ISP :-** Don't group unrelated behaviours in one interface, we should break/split interface into Smaller or more Specific one.  
   i.e. ask to Create different interfaces for different responsibilities.

5. **DIP :-** Use interface or abstract class instead of Concrete classes to Supply the dependent object.  
   i.e. Object should be loosely Coupled using interfaces or abstract classes.  
   Don't tightly coupled with Concrete classes.