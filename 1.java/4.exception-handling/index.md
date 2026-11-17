# Java Exception Handling

## Table of Contents
1. Introduction
2. Exception Handling
3. Runtime Stack Mechanism
4. Default Exception Handling in Java
5. Exception Hierarchy
6. Checked vs Unchecked Exceptions
7. Flow of try-catch Block
8. Methods of Exception (Throwable Class Methods)
9. Finally Block
10. Finally vs Return Statement
11. throw Keyword
12. throws Keyword
13. Exception Handling Keywords Summary
14. Various Possible Compile-time Errors in Exception Handling
15. Difference Between final, finally, and finalize
16. Customised or User-defined Exceptions

---

# Introduction to Exception Handling

## Exception
An unwanted, unexpected event that disturbs the normal flow of a program is called an **Exception**.

## Exception Handling
Something may go wrong in a program, but we should not terminate the program abruptly.  
Handling such situations gracefully is called **Exception Handling**.

## Purpose of Exception Handling
The main purpose of Exception Handling is:

- Graceful termination of the program
- Avoid abnormal termination
- Continue program execution using an alternative solution

---

# Runtime Stack Mechanism

For every thread, JVM creates a separate runtime stack.

## Example

```java
class Test {
    public static void main(String[] args) {
        doStuff();
    }

    public static void doStuff() {
        doMoreStuff();
    }

    public static void doMoreStuff() {
        System.out.println("Hello");
    }
}
```

## Activation Record / Stack Frame

```text
doMoreStuff()
doStuff()
main()
```

(Runtime stack of Main Thread)

---

# Default Exception Handling in Java

## Example

```java
class Test {
    public static void main(String[] args) {
        doStuff();
    }

    public static void doStuff() {
        doMoreStuff();
    }

    public static void doMoreStuff() {
        System.out.println(10 / 0);
    }
}
```

## Explanation

- `doMoreStuff()` method is responsible for creating the Exception object with JVM help.
- Exception Name: `ArithmeticException`
- Stack Trace:

```text
doMoreStuff() -> doStuff() -> main()
```

- Caller method is responsible for handling the exception.
- Here, `main()` is the caller method.

If the caller method does not handle the exception:

- JVM terminates the program abnormally
- Removes stack frames from runtime stack
- Default Exception Handler handles the exception

---

# Exception Hierarchy

```text
Throwable
 ├── Exception
 └── Error
```

- **Exceptions** are recoverable
- **Errors** are non-recoverable and mostly occur due to lack of system resources

---

# Checked vs Unchecked Exceptions
Any Exception (checked or unchecked) are occure runtime only.

## Checked Exceptions

Exceptions checked by the compiler for smooth execution at runtime.

Example:

- `FileNotFoundException`

## Unchecked Exceptions

Exceptions not checked by the compiler.

Examples:

- `RuntimeException`
- `ArithmeticException`
- `NullPointerException`

### Important Note

- `Error`, `RuntimeException`, and their child classes are unchecked exceptions.
- Remaining exceptions are checked exceptions.

---

# Flow of try-catch Block

## Example Flow

```java
try {
    Statement1;
    Statement2; // Exception occurs here
    Statement3;
} catch (Exception e) {
    Statement4;
}

Statement5;
```

## Output Flow

```text
Statement1
Statement2
Statement4
Statement5
```

### Important Note

When an exception occurs inside the try block:

- Remaining statements inside try block will not execute

### Best Practice

Write only risky code inside the `try` block.

---

# Methods of Exception (Throwable Class Methods)

```java
e.printStackTrace();
```

- Prints Exception Name, Description, and Stack Trace

```java
e.toString();
```

- Prints Exception Name and Description

```java
e.getMessage();
```

- Prints only Description

---

# Finally Block

- `finally` block executes whether exception occurs or not
- Executes whether exception is handled or not

## Main Advantage

- Resource cleanup
- Maintain clean code

---

# Finally vs Return Statement

First `finally` block executes, then return statement executes.

## Example

```java
class Test {
    public static int main() {
        try {
            return 777;
        } catch (Exception e) {
            return 888;
        } finally {
            return 999;
        }
    }
}
```

## Output

```text
999
```

---

# throw Keyword

## Purpose

To hand over an Exception object to JVM manually.

## Example

```java
class Test {
    public static void main(String[] args) {
        throw new ArithmeticException("message");
    }
}
```

## Important Notes

- If exception reference is null, JVM throws `NullPointerException`
- After `throw` statement, no statement can execute directly

Example:

```java
throw new ArithmeticException();
System.out.println("Hello"); // Compile-time error
```

- `throw` keyword is used only for throwable types

---

# throws Keyword

## Purpose

To delegate responsibility of exception handling to the caller.

## Example

```java
public static void main(String[] args) throws InterruptedException {
    Thread.sleep(1000);
}
```

## Important Notes

- Recommended approach is `try-catch`, not `throws`
- Mainly used for checked exceptions
- Used to convince compiler
- Does not prevent abnormal termination
- Can be used with methods and constructors
- Cannot be used with classes

---

# Exception Handling Keywords Summary

| Keyword | Purpose |
|---|---|
| `try` | Maintain risky code |
| `catch` | Maintain handling code |
| `finally` | Maintain cleanup code |
| `throw` | Hand over exception object to JVM manually |
| `throws` | Delegate exception handling responsibility |

---

# Various Possible Compile-time Errors in Exception Handling

- `try` without `catch` or `finally`
- `catch` without `try`
- `finally` without `try`
- Unreported exception must be caught or declared to be thrown
- Exception has already been caught

---

# Difference Between final, finally, and finalize

## final

`final` is a keyword.

### Usage

- With class → inheritance not possible
- With method → overriding not possible
- With variable → reassignment not possible

---

## finally

`finally` is a block associated with `try-catch`.

### Usage

- Used for cleanup activities

---

## finalize

`finalize()` is a method.

### Usage

- Called by Garbage Collector before destroying an object
- Used for cleanup activities

---

# Customised or User-defined Exceptions

Exceptions defined by programmers are called **User-defined Exceptions**.

## Example

```java
class TooYoungException extends RuntimeException {

    TooYoungException(String msg) {
        super(msg);
    }
}
```

## Important Note

`throw` keyword is best suitable for user-defined exceptions.

---
