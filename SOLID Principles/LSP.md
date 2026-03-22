# **Liskov Substitution Principle (LSP) in Java**

## **1. Definition of LSP**

The **Liskov Substitution Principle (LSP)** states that:

> “Objects of a superclass should be replaceable with objects of its subclasses without affecting the correctness of the program.”
> 

This means:

- A **subclass should extend** the behavior of a superclass **without altering its fundamental functionality**.
- A **subclass should not break the expectations** set by its parent class.

✅ **Key Idea:** **A subclass should be a proper substitute for its superclass.**

---

## **2. Why is LSP Crucial in OOP?**

LSP ensures:

1. **Code remains flexible and reusable** – You can substitute subclasses without breaking existing behavior.
2. **Avoids unexpected bugs** – Incorrect subclassing leads to runtime failures.
3. **Supports polymorphism effectively** – A superclass reference should be able to hold any of its subclasses **without breaking functionality**.
4. **Encourages proper inheritance hierarchies** – Avoids **incorrect IS-A relationships**.

🚨 **Without LSP, using polymorphism can lead to incorrect behavior.**

---

## **3. Violating LSP with Real-World Examples**

Consider a **Bird class** where we assume all birds can fly.

### ❌ **Bad Example: Violating LSP**

```java
java
CopyEdit
class Bird {
    public void fly() {
        System.out.println("Flying...");
    }
}

class Sparrow extends Bird {
    // Can fly, so this works fine.
}

class Penguin extends Bird {
    // Penguins cannot fly, but they still inherit the fly() method.
}

```

🔴 **Why is this bad?**

- `Penguin` **inherits** from `Bird`, but it **cannot fly**.
- If we use polymorphism (`Bird bird = new Penguin();`), calling `bird.fly();` **violates expectations**.

🚨 **LSP Violation:**

- **Penguin IS-A Bird**, but **it does not behave like one** in terms of flying.

---

## **4. Understanding IS-A vs. BEHAVES-LIKE-A**

- **IS-A relationship (Inheritance)** should mean **full substitutability**.
- If a subclass **does not fully behave like its parent**, it **violates LSP**.

✅ **Better Approach: Define Bird behaviors correctly**

```java
java
CopyEdit
abstract class Bird {
    abstract void eat();
}

abstract class FlyingBird extends Bird {
    abstract void fly();
}

class Sparrow extends FlyingBird {
    public void eat() { System.out.println("Sparrow eating..."); }
    public void fly() { System.out.println("Sparrow flying..."); }
}

class Penguin extends Bird {
    public void eat() { System.out.println("Penguin eating..."); }
}

```

✅ **Now, non-flying birds like Penguins don’t inherit an unwanted `fly()` method.**

✅ **This adheres to LSP, ensuring correct behavior.**

---

## **5. Guidelines for Creating Proper Inheritance Hierarchies**

To follow LSP:

1. **Subclasses should not remove expected behavior.**
2. **Avoid overriding methods that fundamentally change the superclass behavior.**
3. **Use interfaces or abstract classes to separate behaviors.**
4. **Prefer Composition over Inheritance** when behavior varies significantly.

---

## **6. Code Examples**

### ❌ **Bad Example: Violating LSP**

Let's say we have a `Rectangle` class.

```java
java
CopyEdit
class Rectangle {
    protected int width, height;

    public void setWidth(int width) { this.width = width; }
    public void setHeight(int height) { this.height = height; }
    public int getArea() { return width * height; }
}

```

We now create a **Square** class that extends `Rectangle`:

```java
java
CopyEdit
class Square extends Rectangle {
    @Override
    public void setWidth(int width) {
        this.width = width;
        this.height = width; // Always enforce height = width
    }

    @Override
    public void setHeight(int height) {
        this.width = height;
        this.height = height; // Always enforce width = height
    }
}

```

🚨 **LSP Violation:**

- `Square` **modifies** how `setWidth()` and `setHeight()` work.
- If a function expects a `Rectangle`, passing a `Square` **breaks expected behavior**.

**Example of Broken Behavior:**

```java
java
CopyEdit
public static void resizeRectangle(Rectangle rect) {
    rect.setWidth(5);
    rect.setHeight(10);
    System.out.println("Expected Area: 50, Actual Area: " + rect.getArea());
}

public static void main(String[] args) {
    Rectangle rectangle = new Rectangle();
    resizeRectangle(rectangle); // Works fine

    Rectangle square = new Square();
    resizeRectangle(square); // Unexpected result due to LSP violation
}

```

🔴 **Problem:** We expect `getArea()` to return `5 * 10 = 50`, but for a `Square`, it will always have equal width and height.

---

## **7. Applying LSP Correctly**

✅ **Solution: Use separate classes without forcing inheritance**

```java
java
CopyEdit
interface Shape {
    int getArea();
}

class Rectangle implements Shape {
    protected int width, height;

    public Rectangle(int width, int height) {
        this.width = width;
        this.height = height;
    }

    public int getArea() { return width * height; }
}

class Square implements Shape {
    private int side;

    public Square(int side) {
        this.side = side;
    }

    public int getArea() { return side * side; }
}

```

✅ **Now, `Square` and `Rectangle` are separate, avoiding LSP violations.**

✅ **Each class behaves exactly as expected.**

---

## **8. Using Composition Over Inheritance for Better LSP Adherence**

Instead of forcing **inheritance**, use **composition** when objects have different behaviors.

### **Example: Using Composition**

```java
java
CopyEdit
interface Bird {
    void eat();
}

interface Flyable {
    void fly();
}

class Sparrow implements Bird, Flyable {
    public void eat() { System.out.println("Sparrow eating..."); }
    public void fly() { System.out.println("Sparrow flying..."); }
}

class Penguin implements Bird {
    public void eat() { System.out.println("Penguin eating..."); }
}

```

✅ **Now, only birds that can fly implement `Flyable`, avoiding unnecessary methods.**

✅ **Penguin and Sparrow are independent and behave as expected.**

---

## **9. Design Patterns Related to LSP**

### **1. Adapter Pattern**

- Used when two incompatible interfaces need to work together.
- Ensures that a class can substitute another without modifying existing code.

✅ **Example:**

```java
java
CopyEdit
interface MediaPlayer {
    void play(String audioType, String fileName);
}

class MP3Player implements MediaPlayer {
    public void play(String audioType, String fileName) {
        System.out.println("Playing MP3 file: " + fileName);
    }
}

```

Now, suppose we need to support MP4 files. Instead of modifying `MP3Player`, we use an **adapter**:

```java
java
CopyEdit
class MediaAdapter implements MediaPlayer {
    private AdvancedMediaPlayer advancedMusicPlayer;

    public MediaAdapter(String audioType) {
        if(audioType.equalsIgnoreCase("mp4")) {
            advancedMusicPlayer = new MP4Player();
        }
    }

    public void play(String audioType, String fileName) {
        if(audioType.equalsIgnoreCase("mp4")) {
            advancedMusicPlayer.playMP4(fileName);
        }
    }
}

```

✅ **Ensures `MediaPlayer` remains substitutable while adding new behavior.**

---

### **2. Composite Pattern**

- Used when objects need to be treated **uniformly**, whether they are individual or part of a group.

✅ **Example:**

```java
java
CopyEdit
interface Employee {
    void showDetails();
}

class Developer implements Employee {
    private String name;

    public Developer(String name) { this.name = name; }
    public void showDetails() { System.out.println("Developer: " + name); }
}

class Manager implements Employee {
    private List<Employee> employees = new ArrayList<>();

    public void addEmployee(Employee emp) { employees.add(emp); }
    public void showDetails() {
        for (Employee e : employees) e.showDetails();
    }
}

```

✅ **Ensures correct substitution and hierarchy management.**

---

## **10. Conclusion**

✅ **LSP ensures proper inheritance and correct behavior substitution.**

✅ **Use interfaces, composition, and correct hierarchy design.**

✅ **Patterns like Adapter & Composite help enforce LSP.**
