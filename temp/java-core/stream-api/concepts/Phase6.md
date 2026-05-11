## **Phase 6: Advanced Stream Combinations, Custom Collectors & Performance Tips**

This phase focuses on **mastering the Stream API** at a **professional level**, including:

- Combining collectors
- Building custom collectors
- Handling edge cases
- Optimizing stream performance

---

### **Topics Covered:**

1. **Combining Collectors** with `collectingAndThen`, `groupingBy + mapping`
2. **Advanced `groupingBy` + `partitioningBy`**
3. **Creating Your Own Custom Collector**
4. **Stream Performance Tips (Sequential vs Parallel)**
5. **Avoiding Common Mistakes & Memory Leaks**
6. **When Not to Use Streams**
7. **Using `teeing` Collector (Java 12+)**

---

### **Exercises & Real Examples**

### **1. Combining Collectors**

```java

List<String> names = List.of("John", "Jane", "Jill", "Jack");

String result = names.stream()
    .collect(Collectors.collectingAndThen(
        Collectors.joining(", "),
        joined -> "Names: [" + joined + "]"
    ));

System.out.println(result);
// Output: Names: [John, Jane, Jill, Jack]

```

---

### **2. Advanced `groupingBy`**

```java

Map<Boolean, List<String>> partitioned = names.stream()
    .collect(Collectors.partitioningBy(name -> name.startsWith("J")));

System.out.println(partitioned);

```

Or:

```java

Map<Character, List<String>> groupedByFirstChar = names.stream()
    .collect(Collectors.groupingBy(name -> name.charAt(0)));

System.out.println(groupedByFirstChar);

```

---

### **3. Custom Collector (Word Count Example)**

```java

public static Collector<String, ?, Map<String, Integer>> toWordCount() {
    return Collector.of(
        HashMap::new,
        (map, word) -> map.merge(word, 1, Integer::sum),
        (map1, map2) -> { map2.forEach((k, v) -> map1.merge(k, v, Integer::sum)); return map1; }
    );
}

```

Usage:

```java

List<String> words = List.of("apple", "banana", "apple", "orange");
Map<String, Integer> wordCount = words.stream().collect(toWordCount());
System.out.println(wordCount);

```

---

### **4. Performance: Parallel vs Sequential**

Use `parallelStream()` **only** when:

- Large dataset
- CPU-bound (not IO-bound)
- No side effects
- Stateless and associative operations

Example:

```java

long start = System.currentTimeMillis();

IntStream.range(1, 1000000)
    .parallel()
    .sum();

System.out.println("Took: " + (System.currentTimeMillis() - start));

```

---

### **5. Avoiding Pitfalls**

- **Avoid modifying external variables** inside streams
- **Avoid nesting streams too deeply**
- Do not **re-use** a stream
- Prefer **method references** where possible

---

### **6. `teeing` Collector (Java 12+)**

```java

List<Integer> numbers = List.of(10, 20, 30, 40, 50);

var result = numbers.stream().collect(Collectors.teeing(
    Collectors.summingInt(n -> n),
    Collectors.averagingInt(n -> n),
    (sum, avg) -> Map.of("sum", sum, "average", avg)
));

System.out.println(result);

```

---

### ✅ Summary

| Concept | Key Usage Example |
| --- | --- |
| `collectingAndThen` | Post-process collected result |
| `groupingBy` | Group elements by property |
| `partitioningBy` | Divide into two groups (true/false) |
| Custom Collector | Build reusable aggregators |
| `teeing` | Combine two collectors into one result |
| Parallel Streams | Use with care for large, CPU-bound tasks |

---

## **Mini Project: E-Commerce Order Analytics Dashboard**

### **Project Goal:**

You’re building a dashboard that processes a list of orders to extract advanced insights using Java Streams.

---

### **Entities:**

```java

public class Order {
    private String orderId;
    private String customer;
    private double amount;
    private String category;
    private LocalDate date;

    // Constructors, Getters, Setters
}

```

---

### **Dataset (mock):**

```java

List<Order> orders = List.of(
    new Order("O1", "Alice", 1200, "Electronics", LocalDate.of(2024, 1, 1)),
    new Order("O2", "Bob", 300, "Books", LocalDate.of(2024, 1, 2)),
    new Order("O3", "Alice", 100, "Books", LocalDate.of(2024, 1, 3)),
    new Order("O4", "Diana", 700, "Furniture", LocalDate.of(2024, 1, 4)),
    new Order("O5", "Bob", 2000, "Electronics", LocalDate.of(2024, 1, 5)),
    new Order("O6", "Diana", 150, "Books", LocalDate.of(2024, 1, 6))
);

```

---

### **Analytics to Perform (via Streams):**

1. **Total Sales per Category** (`groupingBy + summingDouble`)
2. **Highest Order per Customer** (`groupingBy + collectingAndThen`)
3. **Partition Orders Above ₹1000** (`partitioningBy`)
4. **Top Spending Customer** (`groupingBy + summingDouble + maxBy`)
5. **Summary (Sum & Average)** using `teeing` collector

---

### **Code Implementation:**

```java

// 1. Total Sales per Category
Map<String, Double> salesByCategory = orders.stream()
    .collect(Collectors.groupingBy(
        Order::getCategory,
        Collectors.summingDouble(Order::getAmount)
    ));

// 2. Highest Order per Customer
Map<String, Order> highestOrder = orders.stream()
    .collect(Collectors.groupingBy(
        Order::getCustomer,
        Collectors.collectingAndThen(
            Collectors.maxBy(Comparator.comparingDouble(Order::getAmount)),
            Optional::get
        )
    ));

// 3. Partition Orders > 1000
Map<Boolean, List<Order>> partitioned = orders.stream()
    .collect(Collectors.partitioningBy(o -> o.getAmount() > 1000));

// 4. Top Spending Customer
Map.Entry<String, Double> topCustomer = orders.stream()
    .collect(Collectors.groupingBy(
        Order::getCustomer,
        Collectors.summingDouble(Order::getAmount)
    ))
    .entrySet().stream()
    .max(Map.Entry.comparingByValue())
    .orElseThrow();

// 5. Total & Average Sales using teeing (Java 12+)
Map<String, Double> summary = orders.stream()
    .collect(Collectors.teeing(
        Collectors.summingDouble(Order::getAmount),
        Collectors.averagingDouble(Order::getAmount),
        (sum, avg) -> Map.of("Total", sum, "Average", avg)
    ));

```

---

### **Sample Output:**

```java

System.out.println("Sales by Category: " + salesByCategory);
System.out.println("Highest Order per Customer: " + highestOrder);
System.out.println("Orders > ₹1000: " + partitioned.get(true));
System.out.println("Top Customer: " + topCustomer);
System.out.println("Sales Summary: " + summary);

```

---