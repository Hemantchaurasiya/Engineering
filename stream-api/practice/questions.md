## Java Stream API Scenario-Based Questions and Solutions

✅ Basic Stream API Usage

### How would you convert a list of strings into a list of their lengths using Stream API?

```java
public static List<Integer> stringLengths(List<String> input) {
    return input.stream()
                .map(String::length)
                .collect(Collectors.toList());
}
```
### Given a list of integers, how do you find the square of even numbers and collect them into a list?

```java
public static List<Integer> squareEvenNumbers(List<Integer> numbers) {
          return numbers.stream()
                        .filter(n -> n % 2 == 0)
                        .map(n -> n * n)
                        .collect(Collectors.toList());
      }
```
### How do you remove duplicate elements from a list using streams?

```java
public static List<Integer> removeDuplicates(List<Integer> numbers) {
    return numbers.stream()
                .distinct()
                .collect(Collectors.toList());
}
```

### Given a list of strings, how do you filter out null and empty values using Stream API?

```java
public static List<String> filterNullEmpty(List<String> input) {
    return input.stream()
                .filter(s -> s != null && !s.isEmpty())
                .collect(Collectors.toList());
}
```

### How can you convert a list of strings to uppercase using Stream API?
```java
 public static List<String> toUpperCaseList(List<String> input) {
    return input.stream()
                .map(String::toUpperCase)
                .collect(Collectors.toList());
}
```


✅ Advanced Operations

### How do you group a list of employees by department using Stream API?

```java
class Employee {
        String name;
        String department;
        double salary;

        Employee(String name, String department, double salary) {
            this.name = name;
            this.department = department;
            this.salary = salary;
        }

        public String getName() { return name; }
        public String getDepartment() { return department; }
        public double getSalary() { return salary; }

        @Override
        public String toString() {
            return name + "(" + department + ", $" + salary + ")";
        }
    }

    public static Map<String, List<Employee>> groupByDepartment(List<Employee> employees) {
        return employees.stream()
                        .collect(Collectors.groupingBy(Employee::getDepartment));
    }
```

### How do you count the number of employees in each department using Stream API?
 
```java
public static Map<String, Long> countByDepartment(List<Employee> employees) {
        return employees.stream()
                        .collect(Collectors.groupingBy(Employee::getDepartment, Collectors.counting()));
    }
```

### How do you find the highest salaried employee in each department using streams?

```java
public static Map<String, Optional<Employee>> highestSalaryInDepartment(List<Employee> employees) {
        return employees.stream()
                        .collect(Collectors.groupingBy(
                            Employee::getDepartment,
                            Collectors.maxBy(Comparator.comparing(Employee::getSalary))));
    }
```

### How do you sort a list of objects (e.g., employees) by a field like salary using Stream API?

```java
public static List<Employee> sortBySalary(List<Employee> employees) {
        return employees.stream()
                        .sorted(Comparator.comparing(Employee::getSalary))
                        .collect(Collectors.toList());
    }
```

### How do you calculate the average salary of all employees using streams?

```java
public static double averageSalary(List<Employee> employees) {
        return employees.stream()
                        .mapToDouble(Employee::getSalary)
                        .average()
                        .orElse(0.0);
    }
```

✅ Reduction and Collectors
### How do you use reduce() to calculate the sum of a list of integers?

```java
public static int sumWithReduce(List<Integer> numbers) {
        return numbers.stream()
                      .reduce(0, Integer::sum);
    }
```

### What is the difference between reduce() and collect() in Stream API? Give examples.

```java
public static void reduceVsCollectExample() {
        List<String> list = Arrays.asList("a", "b", "c");

        // Using reduce
        String reduced = list.stream().reduce("", String::concat);

        // Using collect
        String collected = list.stream().collect(Collectors.joining());

        System.out.println("Reduced: " + reduced);
        System.out.println("Collected: " + collected);
    }
```

### How do you join a list of strings with a comma separator using Stream API?

```java
 public static String joinStrings(List<String> items) {
        return items.stream()
                    .collect(Collectors.joining(", "));
    }
```

### How do you partition a list of employees based on salary > 50,000 using streams?

```java
public static Map<Boolean, List<Employee>> partitionBySalary(List<Employee> employees) {
        return employees.stream()
                        .collect(Collectors.partitioningBy(e -> e.getSalary() > 50000));
    }
```

### How do you collect stream results into a Map where the key is department and value is list of employees?

```java
public static Map<String, List<Employee>> employeesByDept(List<Employee> employees) {
        return employees.stream()
                        .collect(Collectors.groupingBy(Employee::getDepartment));
    }

```

✅ FlatMap and Map

### What is the difference between map() and flatMap() in Java Streams? Give real examples.

```java
public static List<String> mapVsFlatMapExample(List<String> sentences) {
        // map example - produces Stream<Stream<String>>
        List<Stream<String>> mapped = sentences.stream()
                                               .map(s -> Arrays.stream(s.split(" ")))
                                               .collect(Collectors.toList());

        // flatMap example - produces Stream<String>
        List<String> flatMapped = sentences.stream()
                                           .flatMap(s -> Arrays.stream(s.split(" ")))
                                           .collect(Collectors.toList());

        return flatMapped;
    }
```

### You have a list of lists of integers. How can you flatten it to a single list using Stream API?

```java
// 17. Flatten list of lists of integers
    public static List<Integer> flattenListOfLists(List<List<Integer>> listOfLists) {
        return listOfLists.stream()
                          .flatMap(Collection::stream)
                          .collect(Collectors.toList());
    }
```

### Given a list of sentences, how do you create a list of all words (splitting by space)?

```java
public static List<String> wordsFromSentences(List<String> sentences) {
        return sentences.stream()
                        .flatMap(s -> Arrays.stream(s.split(" ")))
                        .collect(Collectors.toList());
    }
```

✅ Parallel Streams

### What are parallel streams? When would you use them, and what are the caveats?
```java
public static int sumWithParallelStream(List<Integer> numbers) {
        return numbers.parallelStream()
                      .reduce(0, Integer::sum);
    }
```

### How do you process a large list in parallel using Stream API, and ensure thread safety?
```java
public static List<Integer> threadSafeParallelProcessing(List<Integer> numbers) {
        return numbers.parallelStream()
                      .map(n -> n * 2)
                      .collect(Collectors.toList());
    }
```

✅ Custom Collectors and Usage

### How do you write a custom collector to collect stream elements in a specific format?
```java
 public static List<Integer> paginate(List<Integer> list, int page, int size) {
        return list.stream()
                   .skip((long) (page - 1) * size)
                   .limit(size)
                   .collect(Collectors.toList());
    }

```
### How would you implement a frequency map using streams (e.g., word count)?
```java
 public static Optional<Integer> secondHighest(List<Integer> numbers) {
        return numbers.stream()
                      .distinct()
                      .sorted(Comparator.reverseOrder())
                      .skip(1)
                      .findFirst();
    }
```

✅ Edge Cases and Pitfalls

### What happens if you reuse a stream after a terminal operation?
```java
public static Map<String, Long> countFrequencies(List<String> words) {
        return words.stream()
                    .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
    }
```

### Why is it recommended to avoid using side effects (like modifying external lists) inside map() or forEach()?
```java
 public static boolean allEven(List<Integer> numbers) {
        return numbers.stream().allMatch(n -> n % 2 == 0);
    }

```

### What are the differences between findFirst(), findAny(), and limit() in a parallel stream context?
```java
public static boolean anyNegative(List<Integer> numbers) {
        return numbers.stream().anyMatch(n -> n < 0);
    }
```

✅ Real-Life Scenarios

### You have a list of transactions with date and amount. How would you get the total transaction amount for the current month using streams?
```java

```

### How would you find the first 5 distinct items that start with a certain prefix from a list?
```java

```

### How can you check if all employees in a list are above a certain age using streams?
```java

```

### Given a list of names, how can you count the number of names starting with each alphabet letter?
```java

```

### How would you implement pagination using Stream API?
```java

```
