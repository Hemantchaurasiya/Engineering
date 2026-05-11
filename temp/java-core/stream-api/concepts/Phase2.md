## **Phase 2: Intermediate Concepts of Java Stream API**

This phase is about mastering **core intermediate operations** that power the functional style of streams. These are commonly used in real-world scenarios.

---

### **1. `filter()`**

**Use:** Filters elements based on a predicate.

```java
java
CopyEdit
List<String> names = List.of("Alice", "Bob", "Ankit", "Brian");
names.stream()
     .filter(name -> name.startsWith("A"))
     .forEach(System.out::println);

```

**Output:**

```
nginx
CopyEdit
Alice
Ankit

```

---

### **2. `map()`**

**Use:** Transforms each element using a function.

```java
java
CopyEdit
List<String> names = List.of("alice", "bob", "charlie");
names.stream()
     .map(String::toUpperCase)
     .forEach(System.out::println);

```

**Output:**

```
nginx
CopyEdit
ALICE
BOB
CHARLIE

```

---

### **3. `flatMap()`**

**Use:** Flattens nested streams.

```java
java
CopyEdit
List<List<String>> namesNested = List.of(
    List.of("a", "b"),
    List.of("c", "d")
);

namesNested.stream()
           .flatMap(Collection::stream)
           .forEach(System.out::println);

```

**Output:**

```
css
CopyEdit
a
b
c
d

```

---

### **4. `distinct()`**

**Use:** Removes duplicate elements.

```java
java
CopyEdit
List<Integer> numbers = List.of(1, 2, 2, 3, 4, 4, 5);
numbers.stream()
       .distinct()
       .forEach(System.out::println);

```

**Output:**

```
CopyEdit
1
2
3
4
5

```

---

### **5. `sorted()`**

**Use:** Sorts elements (natural order or with comparator).

```java
java
CopyEdit
List<String> names = List.of("Charlie", "Alice", "Bob");
names.stream()
     .sorted()
     .forEach(System.out::println);

```

**Custom sort:**

```java
java
CopyEdit
names.stream()
     .sorted((a, b) -> b.compareTo(a)) // reverse alphabetical
     .forEach(System.out::println);

```

---

### **6. `limit()` and `skip()`**

**limit(n):** Returns the first `n` elements.

**skip(n):** Skips the first `n` elements.

```java
java
CopyEdit
List<Integer> numbers = List.of(10, 20, 30, 40, 50);

numbers.stream().limit(3).forEach(System.out::println); // 10 20 30
numbers.stream().skip(2).forEach(System.out::println);  // 30 40 50

```

---

### **7. `peek()`**

**Use:** For debugging inside stream pipelines (acts like `forEach`, but doesn't consume the stream).

```java
java
CopyEdit
List<String> names = List.of("Anna", "Bob", "Carol");

names.stream()
     .peek(name -> System.out.println("Before map: " + name))
     .map(String::toUpperCase)
     .peek(name -> System.out.println("After map: " + name))
     .forEach(System.out::println);

```

---

### **Practice Exercises:**

1. From a list of strings, print the length of each string using `map()`.
2. From a list of numbers, remove duplicates, sort them, and print.
3. Flatten a list of sentences (List<String>) into words using `flatMap()`.

### **Exercise 1: Print the length of each string using `map()`**

```java
java
CopyEdit
import java.util.List;

public class StringLengths {
    public static void main(String[] args) {
        List<String> words = List.of("Java", "Streams", "Functional", "Programming");

        words.stream()
             .map(String::length)
             .forEach(System.out::println);
    }
}

```

**Output:**

```
CopyEdit
4
7
10
11

```

---

### **Exercise 2: Remove duplicates, sort, and print numbers**

```java
java
CopyEdit
import java.util.List;

public class DistinctSortedNumbers {
    public static void main(String[] args) {
        List<Integer> numbers = List.of(5, 3, 1, 2, 3, 5, 4, 1);

        numbers.stream()
               .distinct()
               .sorted()
               .forEach(System.out::println);
    }
}

```

**Output:**

```
CopyEdit
1
2
3
4
5

```

---

### **Exercise 3: Flatten a list of sentences into words using `flatMap()`**

```java
java
CopyEdit
import java.util.List;
import java.util.Arrays;

public class FlattenSentences {
    public static void main(String[] args) {
        List<String> sentences = List.of(
            "Java is powerful",
            "Streams are useful",
            "Coding is fun"
        );

        sentences.stream()
                 .flatMap(sentence -> Arrays.stream(sentence.split(" ")))
                 .forEach(System.out::println);
    }
}

```

**Output:**

```
kotlin
CopyEdit
Java
is
powerful
Streams
are
useful
Coding
is
fun

```

---

**mini interactive project** using intermediate Stream API features like `map()`, `filter()`, `flatMap()`, `distinct()`, and `sorted()`.

---

## **Mini Project: Student Dashboard App**

### **Goal:**

- Maintain a list of students with:
    - Name
    - List of courses
    - Marks (Map of course to score)
- Filter high scorers
- Extract all enrolled courses (distinct, sorted)
- Show student names with their average scores

---

### **Step-by-Step Features:**

1. Class `Student` with:
    - `name`
    - `courses` (List of Strings)
    - `marks` (Map<String, Integer>)
2. Create sample student data
3. Menu options:
    - 
        1. Show students scoring above 80 in all subjects
    - 
        1. List all distinct courses sorted alphabetically
    - 
        1. Show average marks for each student

---

### **Java Code:**

```java
java
CopyEdit
import java.util.*;
import java.util.stream.*;

class Student {
    private String name;
    private List<String> courses;
    private Map<String, Integer> marks;

    public Student(String name, List<String> courses, Map<String, Integer> marks) {
        this.name = name;
        this.courses = courses;
        this.marks = marks;
    }

    public String getName() { return name; }
    public List<String> getCourses() { return courses; }
    public Map<String, Integer> getMarks() { return marks; }

    public double getAverageMarks() {
        return marks.values().stream()
                    .mapToInt(i -> i)
                    .average()
                    .orElse(0);
    }

    @Override
    public String toString() {
        return name + " - Avg: " + String.format("%.2f", getAverageMarks());
    }
}

public class StudentDashboard {

    public static void main(String[] args) {
        List<Student> students = List.of(
            new Student("Alice",
                List.of("Math", "Science"),
                Map.of("Math", 90, "Science", 85)),

            new Student("Bob",
                List.of("Math", "English"),
                Map.of("Math", 70, "English", 65)),

            new Student("Charlie",
                List.of("Science", "English"),
                Map.of("Science", 88, "English", 91)),

            new Student("David",
                List.of("Math", "Science", "English"),
                Map.of("Math", 95, "Science", 90, "English", 92))
        );

        Scanner sc = new Scanner(System.in);

        while (true) {
            System.out.println("\n--- Student Dashboard ---");
            System.out.println("1. Students with >80 in all subjects");
            System.out.println("2. List all distinct courses (sorted)");
            System.out.println("3. Show average marks of each student");
            System.out.println("4. Exit");
            System.out.print("Choose option: ");
            int choice = sc.nextInt();

            if (choice == 1) {
                System.out.println("\nStudents with >80 in all subjects:");
                students.stream()
                        .filter(s -> s.getMarks().values().stream().allMatch(m -> m > 80))
                        .forEach(s -> System.out.println(s.getName()));

            } else if (choice == 2) {
                System.out.println("\nAll Distinct Courses:");
                students.stream()
                        .flatMap(s -> s.getCourses().stream())
                        .distinct()
                        .sorted()
                        .forEach(System.out::println);

            } else if (choice == 3) {
                System.out.println("\nAverage Marks of Each Student:");
                students.stream()
                        .forEach(System.out::println);

            } else if (choice == 4) {
                System.out.println("Goodbye!");
                break;

            } else {
                System.out.println("Invalid option.");
            }
        }

        sc.close();
    }
}

```

---

### **Try it out:**

- Option 1 filters students with all subject marks > 80
- Option 2 gives a sorted distinct list of all courses
- Option 3 prints each student with their average marks using `map()`

---