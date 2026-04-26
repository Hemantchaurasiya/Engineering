## **Phase 1: Basics of Java Stream API**

### **1. What is the Java Stream API?**

**Definition:**
The Stream API is a new abstraction introduced in **Java 8** that allows you to process data in a **functional style**. It provides a high-level way to operate on collections (like `List`, `Set`) using a pipeline of operations like **filtering**, **mapping**, **reducing**, etc.

**Key Points:**

- It does **not store data**.
- It's **not** a data structure.
- It works **on collections** (like `List`, `Set`) and **arrays**.
- Operations are **lazy**, meaning they’re only performed when a **terminal operation** is called.

---

### **2. Difference between Stream API and Collections**

| Feature | Collection | Stream |
| --- | --- | --- |
| Stores Data | Yes | No |
| Data Consumption | Can be used multiple times | Can be consumed **once** |
| Evaluation | Eager | Lazy |
| Operations | External Iteration (`for`, `while`) | Internal Iteration (`filter`, `map`, etc.) |
| Parallel Processing | Manually managed threads | Built-in support via `parallelStream()` |

---

### **3. Stream vs Iterator**

| Feature | Iterator | Stream |
| --- | --- | --- |
| Traversal | One-direction | One-direction |
| Data Structure | Directly tied to Collection | Can be from Collection, Array, or Generated |
| Lazy | No | Yes |
| Functional | No | Yes |
| Support for Filter, Map, Reduce | No | Yes |
| Reusability | No | No |

---

### **4. How to Create Streams**

### a. From a **Collection** (most common)

```java

List<String> names = List.of("Alice", "Bob", "Charlie");
Stream<String> stream = names.stream();

```

### b. From an **Array**

```java

int[] numbers = {1, 2, 3, 4};
IntStream intStream = Arrays.stream(numbers);

```

### c. Using `Stream.of()`

```java

Stream<String> stream = Stream.of("Java", "Kotlin", "Scala");

```

### d. Using `Stream.iterate()` (infinite stream)

```java

Stream<Integer> infiniteStream = Stream.iterate(0, n -> n + 2); // even numbers

```

### e. Using `Stream.generate()` (supplier-based infinite stream)

```java

Stream<Double> randomNumbers = Stream.generate(Math::random);

```

---

### **Hands-on Practice Exercise**

1. Create a list of integers and use `.stream()` to print only even numbers.
2. Use `Stream.of()` to create a stream of fruits and print them.
3. Create an infinite stream of odd numbers using `Stream.iterate()` and limit it to first 5.

### **1. Print only even numbers using `.stream()`**

```java



public class EvenNumbers {
    public static void main(String[] args) {
        List<Integer> numbers = List.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);

        numbers.stream()
               .filter(n -> n % 2 == 0)
               .forEach(System.out::println);
    }
}

```

**Output:**

```

2
4
6
8
10

```

---

### **2. Create a stream of fruits and print them using `Stream.of()`**

```java
import java.util.stream.Stream;

public class FruitStream {
    public static void main(String[] args) {
        Stream.of("Apple", "Banana", "Mango", "Orange")
              .forEach(System.out::println);
    }
}

```

**Output:**

```
mathematica
Apple
Banana
Mango
Orange

```

---

### **3. Infinite stream of odd numbers using `Stream.iterate()` and `limit()`**

```java

import java.util.stream.Stream;

public class OddNumbers {
    public static void main(String[] args) {
        Stream.iterate(1, n -> n + 2)
              .limit(5)
              .forEach(System.out::println);
    }
}

```

**Output:**

```
1
3
5
7
9

```

---

## **Project: Movie Library Filter using Stream API**

### **Goal:**

- Create a list of movies (title, genre, rating).
- Ask user to filter movies by **genre** or **rating**.
- Use Stream API to filter and display the results.

---

### **Step-by-Step Features:**

1. Movie class with fields: `title`, `genre`, `rating`
2. List of sample movies
3. Menu-driven input (e.g., filter by genre or rating)
4. Stream API for filtering and displaying results

---

### **Java Code:**

```java

import java.util.*;
import java.util.stream.*;

class Movie {
    private String title;
    private String genre;
    private double rating;

    public Movie(String title, String genre, double rating) {
        this.title = title;
        this.genre = genre;
        this.rating = rating;
    }

    public String getTitle() { return title; }
    public String getGenre() { return genre; }
    public double getRating() { return rating; }

    @Override
    public String toString() {
        return title + " (" + genre + ") - Rating: " + rating;
    }
}

public class MovieLibraryApp {

    public static void main(String[] args) {
        List<Movie> movies = List.of(
            new Movie("Inception", "Sci-Fi", 8.8),
            new Movie("The Godfather", "Crime", 9.2),
            new Movie("The Dark Knight", "Action", 9.0),
            new Movie("Titanic", "Romance", 7.8),
            new Movie("Avengers: Endgame", "Action", 8.4),
            new Movie("La La Land", "Romance", 8.0)
        );

        Scanner scanner = new Scanner(System.in);

        while (true) {
            System.out.println("\n--- Movie Filter ---");
            System.out.println("1. Filter by Genre");
            System.out.println("2. Filter by Minimum Rating");
            System.out.println("3. Exit");
            System.out.print("Choose option: ");
            int choice = scanner.nextInt();
            scanner.nextLine(); // consume newline

            if (choice == 1) {
                System.out.print("Enter genre (e.g., Action, Romance): ");
                String genre = scanner.nextLine();

                movies.stream()
                      .filter(m -> m.getGenre().equalsIgnoreCase(genre))
                      .forEach(System.out::println);

            } else if (choice == 2) {
                System.out.print("Enter minimum rating: ");
                double rating = scanner.nextDouble();

                movies.stream()
                      .filter(m -> m.getRating() >= rating)
                      .forEach(System.out::println);

            } else if (choice == 3) {
                System.out.println("Goodbye!");
                break;
            } else {
                System.out.println("Invalid option.");
            }
        }

        scanner.close();
    }
}

```

---

### **Try This Out:**

- Run the program
- Try filtering by `"Action"` or `"Romance"`
- Try entering different rating thresholds like `8.5`, `9.0`, etc.

---