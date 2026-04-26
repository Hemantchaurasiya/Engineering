# ✅ Java Stream API Cheatsheet – Methods with Use Cases

---

## 🔹 **1. Intermediate Operations**

*(These return another Stream and are lazy – executed only when a terminal operation is called)*

| Method | Definition / Summary | Use Case Example |
| --- | --- | --- |
| `filter(Predicate)` | Filters elements based on a condition | Get students with marks > 80 |
| `map(Function)` | Transforms each element | Get a list of student names |
| `flatMap(Function)` | Flattens nested streams | Flatten `List<List<String>>` into `List<String>` |
| `distinct()` | Removes duplicates (based on `equals()`) | Unique product names |
| `sorted()` | Sorts elements in natural order | Alphabetical sorting |
| `sorted(Comparator)` | Sorts using a custom comparator | Sort students by marks descending |
| `limit(long n)` | Truncates stream to first `n` elements | Top 3 salaries |
| `skip(long n)` | Skips the first `n` elements | Get 6th to 10th records |
| `peek(Consumer)` | Performs an action on each element (mainly for debugging/logging) | Print elements while processing |
| `mapToInt(ToIntFunction)` | Converts to `IntStream` | Used in numeric operations (sum, average, etc.) |
| `mapToDouble(ToDoubleFunction)` | Converts to `DoubleStream` | Convert price strings to double |
| `mapToLong(ToLongFunction)` | Converts to `LongStream` | Convert big numeric values |
| `boxed()` | Converts primitive stream (IntStream, etc.) to Stream of wrapper objects | Convert `IntStream` to `Stream<Integer>` |

---

## 🔹 **2. Terminal Operations**

*(These trigger stream processing and return non-stream results like int, boolean, object, etc.)*

| Method | Definition / Summary | Use Case Example |
| --- | --- | --- |
| `forEach(Consumer)` | Performs an action for each element (non-deterministic order) | Print each item |
| `toArray()` | Converts stream to array | Convert to `String[]` |
| `reduce(...)` | Aggregates values into a single result | Sum, Max, Min, Concatenation |
| `collect(Collector)` | Collects elements into collections or result containers | List, Set, Map, String |
| `count()` | Returns count of elements | Number of employees |
| `anyMatch(Predicate)` | Checks if any element matches condition | Is there a student with grade A? |
| `allMatch(Predicate)` | Checks if all match condition | Are all values > 0? |
| `noneMatch(Predicate)` | Checks if no element matches | Are all students absent? |
| `findFirst()` | Returns first element (Optional) | Get first student in list |
| `findAny()` | Returns any element (Optional, faster in parallel streams) | Get any logged-in user |
| `min(Comparator)` | Finds min element | Student with lowest marks |
| `max(Comparator)` | Finds max element | Product with highest price |

---

## 🔹 **3. Collectors (Used with `.collect()`)**

| Collector | Definition / Summary | Use Case Example |
| --- | --- | --- |
| `Collectors.toList()` | Collects into a `List` | Convert stream to list |
| `Collectors.toSet()` | Collects into a `Set` | Unique records |
| `Collectors.toMap(k, v)` | Collects into a `Map` | Map of name to score |
| `Collectors.joining()` | Joins elements into a string | CSV from names |
| `Collectors.counting()` | Returns number of elements | Count of matching values |
| `Collectors.averagingInt()` | Average of int values | Avg student age |
| `Collectors.averagingDouble()` | Average of double values | Avg price |
| `Collectors.summingInt()` | Sum of int values | Total quantity sold |
| `Collectors.summarizingInt()` | Summary stats (min, max, avg, sum, count) for int | Summary of salaries |
| `Collectors.groupingBy(Function)` | Groups by a classifier function | Group students by grade |
| `Collectors.partitioningBy(Predicate)` | Partition into two groups (true/false) | Passed vs Failed |
| `Collectors.mapping(mapper, collector)` | First map, then collect | Extract names and join |
| `Collectors.collectingAndThen()` | Post-processing after collect | Make collected list immutable |
| `Collectors.reducing(...)` | Collector version of reduce | Reduce with collector |

---

## 🔹 **4. Stream Sources**

| Method | Description | Example |
| --- | --- | --- |
| `Collection.stream()` | Creates a sequential stream | `list.stream()` |
| `Collection.parallelStream()` | Creates a parallel stream | `list.parallelStream()` |
| `Stream.of(...)` | Creates a stream from values | `Stream.of("a", "b", "c")` |
| `Stream.empty()` | Returns an empty stream | Use in default or fallback logic |
| `Arrays.stream(array)` | Creates a stream from array | `Arrays.stream(new int[]{1, 2, 3})` |
| `Stream.generate(Supplier)` | Generates an infinite stream | `Stream.generate(Math::random).limit(5)` |
| `Stream.iterate(seed, f)` | Iterates values starting from a seed | `Stream.iterate(1, n -> n + 1).limit(10)` |
| `Files.lines(Path)` | Stream lines from a file | `Files.lines(Paths.get("file.txt"))` |

---

## ✅ Summary Table

| Category | Common Methods |
| --- | --- |
| Intermediate Ops | `filter`, `map`, `flatMap`, `sorted`, `limit`, `skip`, `distinct`, `peek` |
| Terminal Ops | `forEach`, `collect`, `count`, `reduce`, `findFirst`, `allMatch`, `max` etc. |
| Collectors | `toList`, `groupingBy`, `joining`, `averagingInt`, `partitioningBy`, etc. |
| Stream Sources | `stream()`, `Stream.of()`, `generate()`, `iterate()`, `Files.lines()` |