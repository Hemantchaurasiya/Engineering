## **Java Stream API Mastery Roadmap**

### **Phase 1: Basics of Java Stream API**

1. **What is Stream API?**
    - Definition and purpose
    - Difference between Stream API and Collections
    - Stream vs Iterator
2. **Types of Streams**
    - Sequential Stream
    - Parallel Stream
3. **How to create Streams**
    - From Collections (e.g. `list.stream()`)
    - From Arrays (`Arrays.stream()`)
    - Using Stream.of(), Stream.iterate(), Stream.generate()

---

### **Phase 2: Intermediate Concepts**

1. **Core Stream Operations**
    - **Intermediate Operations** (returns Stream)
        - `filter()`
        - `map()`
        - `flatMap()`
        - `distinct()`
        - `sorted()`
        - `limit()`, `skip()`
        - `peek()`
    - **Terminal Operations** (returns value or side-effect)
        - `collect()`
        - `forEach()`
        - `count()`
        - `reduce()`
        - `min()`, `max()`
        - `anyMatch()`, `allMatch()`, `noneMatch()`
        - `findFirst()`, `findAny()`

---

### **Phase 3: Advanced Usage**

1. **Collectors (java.util.stream.Collectors)**
    - `toList()`, `toSet()`, `toMap()`
    - `joining()`
    - `groupingBy()`, `partitioningBy()`
    - `counting()`
    - `mapping()`
    - `summarizingInt()`, `averagingInt()` etc.
2. **Custom Collectors**
    - Creating a custom collector using `Collector.of()`
3. **FlatMap vs Map with Examples**
    - Nested collections
    - One-to-many transformation

---

### **Phase 4: Functional Interfaces & Lambda Mastery**

1. **Functional Interfaces in Stream API**
    - `Function<T, R>`
    - `Predicate<T>`
    - `Consumer<T>`
    - `Supplier<T>`
    - How these are used in `map()`, `filter()`, etc.
2. **Method References vs Lambda Expressions**
    - When and why to use method references
    - Examples: `String::toUpperCase`, `System.out::println`

---

### **Phase 5: Performance and Debugging**

1. **Stream vs Loop Performance**
    - When to use stream vs traditional for-loop
    - Cost of boxing/unboxing
    - Lazy evaluation in Streams
2. **Debugging Streams**
    - Using `peek()` effectively
    - Common mistakes (e.g., forgetting terminal operations)
3. **Parallel Streams**
    - Performance benefits
    - Thread-safety considerations
    - ForkJoinPool and how parallel stream works internally

---

### **Phase 6: Real-World Use Cases**

1. **Stream API Use Cases**
- Sorting employees by salary
- Grouping orders by status
- Filtering and transforming data
- Aggregating statistics (min, max, avg)
- Removing duplicates from nested lists
1. **Stream API with Optional, Files, and Dates**
- `Optional.stream()` (Java 9+)
- Reading files with `Files.lines()`
- Stream operations with `LocalDate` / `LocalDateTime`
1. **Combining Streams with DTOs and Java Records**
- Mapping entities to DTOs
- Stream pipelines in service layers

---

### **Phase 7: Testing and Best Practices**

1. **Testing Stream Pipelines**
- Using `AssertJ`, `JUnit` for verifying stream results
1. **Best Practices**
- Avoid side-effects in streams
- Prefer method references where possible
- Keep pipelines short and readable
- Don't overuse parallel streams

---

### **Bonus: Java 9+ Enhancements**

- `takeWhile()`, `dropWhile()`, `ofNullable()`
- Improvements to `Collectors`, e.g., `Collectors.filtering()`

---

### 🧪 A. Intermediate Operations

*(These are lazy – they don’t process data until terminal operation is called)*

| Method | Description | Example |
| --- | --- | --- |
| `filter(Predicate)` | Filters elements | `.filter(n -> n > 5)` |
| `map(Function)` | Transforms elements | `.map(String::toUpperCase)` |
| `flatMap(Function)` | Flattens nested structures | `.flatMap(list -> list.stream())` |
| `distinct()` | Removes duplicates | `.distinct()` |
| `sorted()` | Sorts in natural order | `.sorted()` |
| `sorted(Comparator)` | Sorts with custom comparator | `.sorted(Comparator.reverseOrder())` |
| `limit(n)` | Limits to `n` elements | `.limit(5)` |
| `skip(n)` | Skips first `n` elements | `.skip(2)` |
| `peek()` | Performs an action for debugging | `.peek(System.out::println)` |

---

### ✅ B. Terminal Operations

*(Triggers processing, produces a result or side-effect)*

| Method | Description | Returns |
| --- | --- | --- |
| `collect()` | Collects result to collection | `List`, `Set`, `Map` |
| `forEach()` | Performs action for each element | `void` |
| `count()` | Counts elements | `long` |
| `reduce()` | Reduces to a single value | `Optional<T>` |
| `min()`, `max()` | Finds min/max using comparator | `Optional<T>` |
| `anyMatch()`, `allMatch()`, `noneMatch()` | Matching elements with condition | `boolean` |
| `findFirst()`, `findAny()` | Finds element | `Optional<T>` |

---

## 📦 4. Collectors

The `Collectors` class provides **factory methods** for **common collect operations**.

| Collector | Description | Example |
| --- | --- | --- |
| `toList()` | Collects to `List` | `collect(Collectors.toList())` |
| `toSet()` | Collects to `Set` | `collect(Collectors.toSet())` |
| `toMap()` | Collects to `Map` | `collect(Collectors.toMap(...))` |
| `joining()` | Concatenates strings | `collect(Collectors.joining(", "))` |
| `counting()` | Counts elements | `collect(Collectors.counting())` |
| `groupingBy()` | Groups by classifier | `collect(Collectors.groupingBy(...))` |
| `partitioningBy()` | Partitions by predicate | `collect(Collectors.partitioningBy(...))` |
| `summarizingInt()` | Summary statistics (count, sum, avg) | `collect(Collectors.summarizingInt(...))` |

# Streams API methods

## ✅ 1. INTERMEDIATE OPERATIONS

> Definition: Operations that transform a Stream into another Stream. They are lazy and executed only when a terminal operation is invoked.
> 

---

### 🔹 `filter(Predicate)`

- ✔ Filters elements that match a condition
- 🎯 Use Case: Get even numbers from a list

```java

List<Integer> numbers = List.of(1, 2, 3, 4, 5);

List<Integer> evens = numbers.stream()
    .filter(n -> n % 2 == 0)
    .collect(Collectors.toList());

System.out.println(evens); // [2, 4]

```

---

### 🔹 `map(Function)`

- ✔ Transforms each element
- 🎯 Use Case: Convert names to uppercase

```java

List<String> names = List.of("john", "jane");

List<String> upper = names.stream()
    .map(String::toUpperCase)
    .collect(Collectors.toList());

System.out.println(upper); // [JOHN, JANE]

```

---

### 🔹 `flatMap(Function)`

- ✔ Flattens nested structures
- 🎯 Use Case: Convert list of lists into a single list

```java

List<List<String>> nested = List.of(List.of("A", "B"), List.of("C", "D"));

List<String> flat = nested.stream()
    .flatMap(List::stream)
    .collect(Collectors.toList());

System.out.println(flat); // [A, B, C, D]

```

---

### 🔹 `distinct()`

- ✔ Removes duplicates
- 🎯 Use Case: Get unique elements

```java

List<Integer> nums = List.of(1, 2, 2, 3);

List<Integer> distinct = nums.stream()
    .distinct()
    .collect(Collectors.toList());

System.out.println(distinct); // [1, 2, 3]

```

---

### 🔹 `sorted()` / `sorted(Comparator)`

- ✔ Sorts elements
- 🎯 Use Case: Sort numbers

```java

List<Integer> unsorted = List.of(4, 1, 3, 2);

List<Integer> sorted = unsorted.stream()
    .sorted()
    .collect(Collectors.toList());

System.out.println(sorted); // [1, 2, 3, 4]

```

---

### 🔹 `limit(n)`

- ✔ Limits stream size
- 🎯 Use Case: Get top 3 items

```java

List<Integer> limited = List.of(10, 20, 30, 40).stream()
    .limit(2)
    .collect(Collectors.toList());

System.out.println(limited); // [10, 20]

```

---

### 🔹 `skip(n)`

- ✔ Skips n elements
- 🎯 Use Case: Pagination

```java

List<Integer> skipped = List.of(10, 20, 30, 40, 50).stream()
    .skip(2)
    .collect(Collectors.toList());

System.out.println(skipped); // [30, 40, 50]

```

---

### 🔹 `peek(Consumer)`

- ✔ For debugging (logs/intermediate state)
- 🎯 Use Case: Print while streaming

```java

List<Integer> peeked = List.of(1, 2, 3).stream()
    .peek(n -> System.out.println("Processing: " + n))
    .collect(Collectors.toList());

```

---

## ✅ 2. TERMINAL OPERATIONS

> Definition: Operations that trigger the processing of the stream and produce a result.
> 

---

### 🔹 `forEach(Consumer)`

- ✔ Performs action on each element
- 🎯 Use Case: Print elements

```java

List<String> names = List.of("John", "Jane");

names.stream().forEach(System.out::println);

```

---

### 🔹 `toArray()`

- ✔ Collects to array
- 🎯 Use Case: Convert stream to array

```java

String[] array = List.of("A", "B", "C").stream()
    .toArray(String[]::new);

```

---

### 🔹 `reduce()`

- ✔ Combines elements into a single value
- 🎯 Use Case: Sum of numbers

```java

int sum = List.of(1, 2, 3).stream()
    .reduce(0, Integer::sum);

System.out.println(sum); // 6

```

---

### 🔹 `collect()`

- ✔ Collects result to collection or summary
- 🎯 Use Case: Grouping, partitioning, summarizing (see section 3)

---

### 🔹 `count()`

- ✔ Counts elements
- 🎯 Use Case: Count names starting with "J"

```java

long count = List.of("John", "Jane", "Tom").stream()
    .filter(name -> name.startsWith("J"))
    .count();

System.out.println(count); // 2

```

---

### 🔹 `min(Comparator)` / `max(Comparator)`

- ✔ Finds min/max
- 🎯 Use Case: Find shortest string

```java

Optional<String> min = List.of("apple", "banana", "kiwi").stream()
    .min(Comparator.comparing(String::length));

System.out.println(min.get()); // kiwi

```

---

### 🔹 `anyMatch()`, `allMatch()`, `noneMatch()`

- ✔ Checks condition match
- 🎯 Use Case: Validation checks

```java

boolean hasEven = List.of(1, 2, 3).stream().anyMatch(n -> n % 2 == 0);
boolean allEven = List.of(2, 4, 6).stream().allMatch(n -> n % 2 == 0);
boolean noneNegative = List.of(1, 2, 3).stream().noneMatch(n -> n < 0);

```

---

### 🔹 `findFirst()` / `findAny()`

- ✔ Gets first or any match
- 🎯 Use Case: Get first number > 10

```java

Optional<Integer> first = List.of(5, 12, 18).stream()
    .filter(n -> n > 10)
    .findFirst();

System.out.println(first.get()); // 12

```

---

## ✅ 3. COLLECTORS

> Definition: A collector is used to accumulate the stream elements into a collection, summary, or grouped result.
> 

---

### 🔹 `Collectors.toList()`

- ✔ Collects elements into a List

```java

List<String> list = List.of("A", "B").stream()
    .collect(Collectors.toList());

```

---

### 🔹 `Collectors.toSet()`

- ✔ Collects elements into a Set

```java

Set<String> set = List.of("A", "B", "A").stream()
    .collect(Collectors.toSet());

```

---

### 🔹 `Collectors.joining()`

- ✔ Joins strings
- 🎯 Use Case: Convert to CSV

```java

String joined = List.of("A", "B", "C").stream()
    .collect(Collectors.joining(", "));

System.out.println(joined); // A, B, C

```

---

### 🔹 `Collectors.groupingBy()`

- ✔ Groups elements by key
- 🎯 Use Case: Group employees by department

```java

Map<String, List<Employee>> grouped = employees.stream()
    .collect(Collectors.groupingBy(Employee::getDept));

```

---

### 🔹 `Collectors.partitioningBy()`

- ✔ Partitions based on predicate
- 🎯 Use Case: Split students by pass/fail

```java

Map<Boolean, List<Integer>> partitioned = List.of(40, 80, 90, 50).stream()
    .collect(Collectors.partitioningBy(score -> score >= 60));

```

---

### 🔹 `Collectors.counting()`

- ✔ Counts number of elements

```java

long count = List.of("A", "B", "C").stream()
    .collect(Collectors.counting());

```

---

### 🔹 `Collectors.summingInt()`

- ✔ Sums integer fields
- 🎯 Use Case: Total age of people

```java

int totalAge = people.stream()
    .collect(Collectors.summingInt(Person::getAge));

```

---

### 🔹 `Collectors.summarizingInt()`

- ✔ Summary stats: count, sum, avg, min, max

```java

IntSummaryStatistics stats = List.of(1, 2, 3, 4).stream()
    .collect(Collectors.summarizingInt(Integer::intValue));

System.out.println(stats.getAverage()); // 2.5

```