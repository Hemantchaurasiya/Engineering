## **Phase 3: Terminal Operations**

Terminal operations trigger the actual processing of a stream. Once called, the stream is **consumed and cannot be reused**.

---

### **1. `forEach()`**

Executes an action for each element.

```java

List<String> names = List.of("Alice", "Bob", "Charlie");

names.stream()
     .forEach(System.out::println);

```

---

### **2. `collect()`**

Collects the elements into a data structure like a `List`, `Set`, or `Map`.

### **To List:**

```java

List<Integer> squares = List.of(1, 2, 3, 4)
    .stream()
    .map(n -> n * n)
    .collect(Collectors.toList());

```

### **To Set:**

```java

Set<String> uniqueNames = List.of("a", "b", "a")
    .stream()
    .collect(Collectors.toSet());

```

### **To Map:**

```java

Map<String, Integer> nameLengthMap = List.of("Alice", "Bob")
    .stream()
    .collect(Collectors.toMap(name -> name, name -> name.length()));

```

---

### **3. `count()`**

Counts the number of elements.

```java

long count = List.of("a", "b", "c", "a")
    .stream()
    .distinct()
    .count();  // Output: 3

```

---

### **4. `min()` and `max()`**

Find the min/max element using a comparator.

```java

Optional<Integer> min = List.of(5, 3, 9, 1)
    .stream()
    .min(Integer::compareTo);  // Output: 1

Optional<Integer> max = List.of(5, 3, 9, 1)
    .stream()
    .max(Integer::compareTo);  // Output: 9

```

---

### **5. `reduce()`**

Reduces the stream to a single value (like sum, product, etc.)

### **Sum:**

```java

int sum = List.of(1, 2, 3, 4)
    .stream()
    .reduce(0, Integer::sum);  // Output: 10

```

### **Longest String:**

```java

Optional<String> longest = List.of("hi", "hello", "worldwide")
    .stream()
    .reduce((s1, s2) -> s1.length() > s2.length() ? s1 : s2);  // Output: "worldwide"

```

---

### **Practice Exercises (Want code too?):**

1. Count number of even numbers in a list.
2. Collect words with more than 3 letters into a `List`.
3. Find the longest word in a list using `reduce()`.
4. Create a `Map<String, Integer>` from a list of words with word as key and length as value.
5. Calculate product of all numbers using `reduce()`.
---

### **1. Count number of even numbers in a list**

```java

import java.util.List;

public class EvenCount {
    public static void main(String[] args) {
        List<Integer> numbers = List.of(2, 3, 4, 5, 6, 7, 8);

        long evenCount = numbers.stream()
                                .filter(n -> n % 2 == 0)
                                .count();

        System.out.println("Even numbers count: " + evenCount);
    }
}

```

---

### **2. Collect words with more than 3 letters into a `List`**

```java

import java.util.List;
import java.util.stream.Collectors;

public class WordsFilter {
    public static void main(String[] args) {
        List<String> words = List.of("hi", "hello", "bye", "world", "awe");

        List<String> result = words.stream()
                                   .filter(w -> w.length() > 3)
                                   .collect(Collectors.toList());

        System.out.println("Words with more than 3 letters: " + result);
    }
}

```

---

### **3. Find the longest word in a list using `reduce()`**

```java
import java.util.List;
import java.util.Optional;

public class LongestWord {
    public static void main(String[] args) {
        List<String> words = List.of("hi", "hello", "worldwide", "sun");

        Optional<String> longest = words.stream()
                                        .reduce((w1, w2) -> w1.length() > w2.length() ? w1 : w2);

        longest.ifPresent(w -> System.out.println("Longest word: " + w));
    }
}

```

---

### **4. Create a Map<String, Integer> from a list of words (word → length)**

```java
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class WordLengthMap {
    public static void main(String[] args) {
        List<String> words = List.of("apple", "banana", "kiwi");

        Map<String, Integer> wordLengthMap = words.stream()
            .collect(Collectors.toMap(word -> word, word -> word.length()));

        wordLengthMap.forEach((word, length) ->
            System.out.println(word + " → " + length));
    }
}

```

---

### **5. Calculate product of all numbers using `reduce()`**

```java
import java.util.List;

public class ProductReduce {
    public static void main(String[] args) {
        List<Integer> numbers = List.of(2, 3, 4, 5);

        int product = numbers.stream()
                             .reduce(1, (a, b) -> a * b);

        System.out.println("Product: " + product);
    }
}

```

---