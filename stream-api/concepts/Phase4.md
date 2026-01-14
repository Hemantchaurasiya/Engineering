## **Phase 4: Advanced Collectors**

Java's `Collectors` utility class provides several useful methods for **grouping**, **partitioning**, and **summarizing** collections.

---

### **1. `Collectors.groupingBy()`**

Groups elements by a classifier function (like a `Map<Key, List<Value>>`).

### **Example: Group words by length**

```java

import java.util.*;
import java.util.stream.*;

public class GroupByLength {
    public static void main(String[] args) {
        List<String> words = List.of("apple", "bat", "cat", "banana", "dog");

        Map<Integer, List<String>> grouped = words.stream()
            .collect(Collectors.groupingBy(String::length));

        grouped.forEach((len, group) -> System.out.println(len + " → " + group));
    }
}

```

---

### **2. `Collectors.partitioningBy()`**

Partitions elements into two groups (true/false) using a predicate.

### **Example: Partition numbers into even and odd**

```java

import java.util.*;
import java.util.stream.*;

public class PartitionExample {
    public static void main(String[] args) {
        List<Integer> numbers = List.of(1, 2, 3, 4, 5, 6, 7);

        Map<Boolean, List<Integer>> partitioned = numbers.stream()
            .collect(Collectors.partitioningBy(n -> n % 2 == 0));

        System.out.println("Even: " + partitioned.get(true));
        System.out.println("Odd: " + partitioned.get(false));
    }
}

```

---

### **3. `Collectors.summarizingInt()`**

Returns stats like **count**, **sum**, **min**, **max**, and **average** for integers.

### **Example: Summarize employee salaries**

```java

import java.util.*;
import java.util.stream.*;

class Employee {
    String name;
    int salary;

    Employee(String name, int salary) {
        this.name = name;
        this.salary = salary;
    }
}

public class SummarizeExample {
    public static void main(String[] args) {
        List<Employee> employees = List.of(
            new Employee("Alice", 5000),
            new Employee("Bob", 7000),
            new Employee("Charlie", 6000)
        );

        IntSummaryStatistics stats = employees.stream()
            .collect(Collectors.summarizingInt(emp -> emp.salary));

        System.out.println("Count: " + stats.getCount());
        System.out.println("Sum: " + stats.getSum());
        System.out.println("Min: " + stats.getMin());
        System.out.println("Max: " + stats.getMax());
        System.out.println("Average: " + stats.getAverage());
    }
}

```

---

### **4. `Collectors.mapping()`**

Used with `groupingBy()` to apply a function to the grouped values.

### **Example: Group by length and collect first letter of each word**

```java

import java.util.*;
import java.util.stream.*;

public class GroupMappingExample {
    public static void main(String[] args) {
        List<String> words = List.of("apple", "ant", "bat", "banana", "cat");

        Map<Integer, List<Character>> result = words.stream()
            .collect(Collectors.groupingBy(
                String::length,
                Collectors.mapping(word -> word.charAt(0), Collectors.toList())
            ));

        result.forEach((len, letters) -> System.out.println(len + " → " + letters));
    }
}

```

---

### **Practice Ideas (Want code too?):**

1. Group students by grade.
2. Partition employees earning more than 50k.
3. Summarize product prices (min, max, avg).
4. Group words by length and collect the uppercase versions.
5. Count how many items fall into each category.

### **1. Group students by grade**

```java

import java.util.*;
import java.util.stream.*;

class Student {
    String name;
    String grade;

    Student(String name, String grade) {
        this.name = name;
        this.grade = grade;
    }
}

public class GroupStudents {
    public static void main(String[] args) {
        List<Student> students = List.of(
            new Student("Alice", "A"),
            new Student("Bob", "B"),
            new Student("Charlie", "A"),
            new Student("David", "C"),
            new Student("Eve", "B")
        );

        Map<String, List<String>> grouped = students.stream()
            .collect(Collectors.groupingBy(
                s -> s.grade,
                Collectors.mapping(s -> s.name, Collectors.toList())
            ));

        grouped.forEach((grade, names) ->
            System.out.println("Grade " + grade + ": " + names));
    }
}

```

---

### **2. Partition employees earning more than 50k**

```java

import java.util.*;
import java.util.stream.*;

class Employee {
    String name;
    int salary;

    Employee(String name, int salary) {
        this.name = name;
        this.salary = salary;
    }
}

public class PartitionEmployees {
    public static void main(String[] args) {
        List<Employee> employees = List.of(
            new Employee("Alice", 60000),
            new Employee("Bob", 48000),
            new Employee("Charlie", 70000),
            new Employee("David", 45000)
        );

        Map<Boolean, List<Employee>> partitioned = employees.stream()
            .collect(Collectors.partitioningBy(e -> e.salary > 50000));

        System.out.println("Earning > 50k:");
        partitioned.get(true).forEach(e -> System.out.println(e.name));

        System.out.println("\nEarning <= 50k:");
        partitioned.get(false).forEach(e -> System.out.println(e.name));
    }
}

```

---

### **3. Summarize product prices (min, max, avg)**

```java

import java.util.*;
import java.util.stream.*;

class Product {
    String name;
    double price;

    Product(String name, double price) {
        this.name = name;
        this.price = price;
    }
}

public class ProductSummary {
    public static void main(String[] args) {
        List<Product> products = List.of(
            new Product("Book", 15.5),
            new Product("Pen", 2.5),
            new Product("Laptop", 850.0),
            new Product("Bag", 45.0)
        );

        DoubleSummaryStatistics stats = products.stream()
            .collect(Collectors.summarizingDouble(p -> p.price));

        System.out.println("Min: " + stats.getMin());
        System.out.println("Max: " + stats.getMax());
        System.out.println("Average: " + stats.getAverage());
        System.out.println("Sum: " + stats.getSum());
    }
}

```

---

### **4. Group words by length and collect the uppercase versions**

```java

import java.util.*;
import java.util.stream.*;

public class GroupWordsUppercase {
    public static void main(String[] args) {
        List<String> words = List.of("apple", "bat", "banana", "cat", "ball");

        Map<Integer, List<String>> grouped = words.stream()
            .collect(Collectors.groupingBy(
                String::length,
                Collectors.mapping(String::toUpperCase, Collectors.toList())
            ));

        grouped.forEach((len, group) ->
            System.out.println(len + " → " + group));
    }
}

```

---

### **5. Count how many items fall into each category**

```java

import java.util.*;
import java.util.stream.*;

class Item {
    String name;
    String category;

    Item(String name, String category) {
        this.name = name;
        this.category = category;
    }
}

public class ItemCategoryCount {
    public static void main(String[] args) {
        List<Item> items = List.of(
            new Item("Apple", "Fruit"),
            new Item("Banana", "Fruit"),
            new Item("Carrot", "Vegetable"),
            new Item("Tomato", "Vegetable"),
            new Item("Pear", "Fruit")
        );

        Map<String, Long> categoryCounts = items.stream()
            .collect(Collectors.groupingBy(i -> i.category, Collectors.counting()));

        categoryCounts.forEach((category, count) ->
            System.out.println(category + ": " + count));
    }
}

```

---

**Mini Project** that integrates everything from **Phases 1 to 4** of Java Stream API learning — including filtering, mapping, sorting, collecting, grouping, partitioning, and summarizing.

---

## **Mini Project: Product Analytics Dashboard (Console-Based)**

### **Project Idea:**

Create a small product analytics system that:

- Filters products by availability
- Sorts products by price
- Groups products by category
- Partitions products by expensive vs affordable
- Summarizes product price stats
- Maps product names in uppercase by category

---

### **Step 1: Product Model**

```java

class Product {
    String name;
    String category;
    double price;
    boolean inStock;

    Product(String name, String category, double price, boolean inStock) {
        this.name = name;
        this.category = category;
        this.price = price;
        this.inStock = inStock;
    }

    @Override
    public String toString() {
        return name + " - $" + price + " (" + (inStock ? "Available" : "Out of stock") + ")";
    }
}

```

---

### **Step 2: Product List (Sample Data)**

```java

List<Product> products = List.of(
    new Product("iPhone", "Electronics", 999.99, true),
    new Product("Samsung TV", "Electronics", 1200.00, false),
    new Product("Banana", "Groceries", 0.99, true),
    new Product("Milk", "Groceries", 1.49, true),
    new Product("T-shirt", "Clothing", 19.99, false),
    new Product("Jeans", "Clothing", 39.99, true),
    new Product("Laptop", "Electronics", 1500.00, true)
);

```

---

### **Step 3: Analytics Functions**

### **A. Filter in-stock products**

```java

List<Product> available = products.stream()
    .filter(p -> p.inStock)
    .collect(Collectors.toList());

System.out.println("Available Products:");
available.forEach(System.out::println);

```

### **B. Sort by price descending**

```java

System.out.println("\nProducts sorted by price (desc):");
products.stream()
    .sorted(Comparator.comparingDouble(Product::getPrice).reversed())
    .forEach(System.out::println);

```

### **C. Group by category**

```java

System.out.println("\nGrouped by Category:");
Map<String, List<Product>> grouped = products.stream()
    .collect(Collectors.groupingBy(p -> p.category));
grouped.forEach((category, list) -> {
    System.out.println(category + ":");
    list.forEach(p -> System.out.println(" - " + p));
});

```

### **D. Partition into expensive (> $100) and affordable**

```java

System.out.println("\nExpensive vs Affordable:");
Map<Boolean, List<Product>> partitioned = products.stream()
    .collect(Collectors.partitioningBy(p -> p.price > 100));
System.out.println("Expensive:");
partitioned.get(true).forEach(System.out::println);
System.out.println("Affordable:");
partitioned.get(false).forEach(System.out::println);

```

### **E. Price statistics**

```java

DoubleSummaryStatistics stats = products.stream()
    .collect(Collectors.summarizingDouble(p -> p.price));
System.out.println("\nPrice Stats:");
System.out.println("Count: " + stats.getCount());
System.out.println("Min: " + stats.getMin());
System.out.println("Max: " + stats.getMax());
System.out.println("Avg: " + stats.getAverage());
System.out.println("Sum: " + stats.getSum());

```

### **F. Group names in uppercase by category**

```java

System.out.println("\nUppercase Product Names by Category:");
Map<String, List<String>> nameMapping = products.stream()
    .collect(Collectors.groupingBy(
        p -> p.category,
        Collectors.mapping(p -> p.name.toUpperCase(), Collectors.toList())
    ));
nameMapping.forEach((cat, names) -> System.out.println(cat + ": " + names));

```

---

### **Want a Full Combined Java Class?**

**Product Analytics Dashboard** mini-project. It includes filtering, sorting, grouping, partitioning, summarizing, and mapping—all using the Stream API:

---

### **`ProductAnalyticsApp.java`**

```java

import java.util.*;
import java.util.stream.*;

class Product {
    String name;
    String category;
    double price;
    boolean inStock;

    Product(String name, String category, double price, boolean inStock) {
        this.name = name;
        this.category = category;
        this.price = price;
        this.inStock = inStock;
    }

    public double getPrice() {
        return price;
    }

    @Override
    public String toString() {
        return name + " - $" + price + " (" + (inStock ? "Available" : "Out of stock") + ")";
    }
}

public class ProductAnalyticsApp {
    public static void main(String[] args) {
        List<Product> products = List.of(
            new Product("iPhone", "Electronics", 999.99, true),
            new Product("Samsung TV", "Electronics", 1200.00, false),
            new Product("Banana", "Groceries", 0.99, true),
            new Product("Milk", "Groceries", 1.49, true),
            new Product("T-shirt", "Clothing", 19.99, false),
            new Product("Jeans", "Clothing", 39.99, true),
            new Product("Laptop", "Electronics", 1500.00, true)
        );

        // A. Filter in-stock products
        List<Product> available = products.stream()
            .filter(p -> p.inStock)
            .collect(Collectors.toList());
        System.out.println("Available Products:");
        available.forEach(System.out::println);

        // B. Sort by price descending
        System.out.println("\nProducts sorted by price (desc):");
        products.stream()
            .sorted(Comparator.comparingDouble(Product::getPrice).reversed())
            .forEach(System.out::println);

        // C. Group by category
        System.out.println("\nGrouped by Category:");
        Map<String, List<Product>> grouped = products.stream()
            .collect(Collectors.groupingBy(p -> p.category));
        grouped.forEach((category, list) -> {
            System.out.println(category + ":");
            list.forEach(p -> System.out.println(" - " + p));
        });

        // D. Partition into expensive (> $100) and affordable
        System.out.println("\nExpensive vs Affordable:");
        Map<Boolean, List<Product>> partitioned = products.stream()
            .collect(Collectors.partitioningBy(p -> p.price > 100));
        System.out.println("Expensive:");
        partitioned.get(true).forEach(System.out::println);
        System.out.println("Affordable:");
        partitioned.get(false).forEach(System.out::println);

        // E. Price statistics
        DoubleSummaryStatistics stats = products.stream()
            .collect(Collectors.summarizingDouble(p -> p.price));
        System.out.println("\nPrice Stats:");
        System.out.println("Count: " + stats.getCount());
        System.out.println("Min: $" + stats.getMin());
        System.out.println("Max: $" + stats.getMax());
        System.out.println("Avg: $" + stats.getAverage());
        System.out.println("Sum: $" + stats.getSum());

        // F. Group names in uppercase by category
        System.out.println("\nUppercase Product Names by Category:");
        Map<String, List<String>> nameMapping = products.stream()
            .collect(Collectors.groupingBy(
                p -> p.category,
                Collectors.mapping(p -> p.name.toUpperCase(), Collectors.toList())
            ));
        nameMapping.forEach((cat, names) ->
            System.out.println(cat + ": " + names));
    }
}

```

---

### **To Run This Code:**

1. Save it as `ProductAnalyticsApp.java`
2. Compile: `javac ProductAnalyticsApp.java`
3. Run: `java ProductAnalyticsApp`

---

enhance the mini project by **loading product data from a CSV file** instead of hardcoding it.

---

### **Step-by-Step Plan:**

1. Create a CSV file with product data
2. Read the file in Java
3. Parse each line into a `Product` object
4. Use the parsed list in your analytics

---

### **1. CSV File: `products.csv`**

```
pgsql
CopyEdit
name,category,price,inStock
iPhone,Electronics,999.99,true
Samsung TV,Electronics,1200.00,false
Banana,Groceries,0.99,true
Milk,Groceries,1.49,true
T-shirt,Clothing,19.99,false
Jeans,Clothing,39.99,true
Laptop,Electronics,1500.00,true

```

Put this file in the root directory of your Java project.

---

### **2. Updated Java Code:**

### **`ProductAnalyticsAppWithCSV.java`**

```java

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.*;

class Product {
    String name;
    String category;
    double price;
    boolean inStock;

    Product(String name, String category, double price, boolean inStock) {
        this.name = name;
        this.category = category;
        this.price = price;
        this.inStock = inStock;
    }

    public double getPrice() {
        return price;
    }

    @Override
    public String toString() {
        return name + " - $" + price + " (" + (inStock ? "Available" : "Out of stock") + ")";
    }
}

public class ProductAnalyticsAppWithCSV {

    public static void main(String[] args) {
        List<Product> products = loadProductsFromCSV("products.csv");

        if (products.isEmpty()) {
            System.out.println("No products loaded.");
            return;
        }

        // Perform analytics (reuse previous methods)
        System.out.println("Available Products:");
        products.stream()
            .filter(p -> p.inStock)
            .forEach(System.out::println);

        System.out.println("\nSorted by Price (Desc):");
        products.stream()
            .sorted(Comparator.comparingDouble(Product::getPrice).reversed())
            .forEach(System.out::println);

        System.out.println("\nGrouped by Category:");
        products.stream()
            .collect(Collectors.groupingBy(p -> p.category))
            .forEach((cat, list) -> {
                System.out.println(cat + ":");
                list.forEach(p -> System.out.println(" - " + p));
            });

        System.out.println("\nPartitioned (Expensive > $100):");
        Map<Boolean, List<Product>> partitioned = products.stream()
            .collect(Collectors.partitioningBy(p -> p.price > 100));
        System.out.println("Expensive:");
        partitioned.get(true).forEach(System.out::println);
        System.out.println("Affordable:");
        partitioned.get(false).forEach(System.out::println);

        System.out.println("\nPrice Summary:");
        DoubleSummaryStatistics stats = products.stream()
            .collect(Collectors.summarizingDouble(p -> p.price));
        System.out.println(stats);

        System.out.println("\nUppercase Names by Category:");
        Map<String, List<String>> nameMapping = products.stream()
            .collect(Collectors.groupingBy(
                p -> p.category,
                Collectors.mapping(p -> p.name.toUpperCase(), Collectors.toList())
            ));
        nameMapping.forEach((cat, names) -> System.out.println(cat + ": " + names));
    }

    private static List<Product> loadProductsFromCSV(String fileName) {
        List<Product> productList = new ArrayList<>();
        try (BufferedReader reader = Files.newBufferedReader(Paths.get(fileName))) {
            reader.readLine(); // Skip header
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(",");
                if (parts.length == 4) {
                    String name = parts[0].trim();
                    String category = parts[1].trim();
                    double price = Double.parseDouble(parts[2].trim());
                    boolean inStock = Boolean.parseBoolean(parts[3].trim());
                    productList.add(new Product(name, category, price, inStock));
                }
            }
        } catch (IOException e) {
            System.err.println("Error reading file: " + e.getMessage());
        }
        return productList;
    }
}

```

---

Que - Would you like to add **menu-driven options** next so the user can select which analytics to run?

Ans - Awesome! Let's upgrade the app to a **menu-driven console application** where users can choose analytics options using numbers.

---

### **Updated `ProductAnalyticsAppWithMenu.java`**

This version:

- Loads data from `products.csv`
- Displays a menu
- Executes the selected analytics

---

```java

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.*;

class Product {
    String name;
    String category;
    double price;
    boolean inStock;

    Product(String name, String category, double price, boolean inStock) {
        this.name = name;
        this.category = category;
        this.price = price;
        this.inStock = inStock;
    }

    public double getPrice() {
        return price;
    }

    @Override
    public String toString() {
        return name + " - $" + price + " (" + (inStock ? "Available" : "Out of stock") + ")";
    }
}

public class ProductAnalyticsAppWithMenu {

    public static void main(String[] args) {
        List<Product> products = loadProductsFromCSV("products.csv");
        if (products.isEmpty()) {
            System.out.println("No products loaded.");
            return;
        }

        Scanner scanner = new Scanner(System.in);
        while (true) {
            System.out.println("\n--- Product Analytics Menu ---");
            System.out.println("1. Show In-Stock Products");
            System.out.println("2. Sort Products by Price (Descending)");
            System.out.println("3. Group Products by Category");
            System.out.println("4. Partition Products by Price (> $100)");
            System.out.println("5. Show Price Statistics");
            System.out.println("6. Uppercase Product Names by Category");
            System.out.println("0. Exit");
            System.out.print("Choose an option: ");

            int choice = scanner.nextInt();
            System.out.println();

            switch (choice) {
                case 1 -> showInStockProducts(products);
                case 2 -> sortByPriceDesc(products);
                case 3 -> groupByCategory(products);
                case 4 -> partitionByPrice(products);
                case 5 -> showPriceStats(products);
                case 6 -> showUppercaseNames(products);
                case 0 -> {
                    System.out.println("Exiting...");
                    return;
                }
                default -> System.out.println("Invalid choice.");
            }
        }
    }

    private static List<Product> loadProductsFromCSV(String fileName) {
        List<Product> productList = new ArrayList<>();
        try (BufferedReader reader = Files.newBufferedReader(Paths.get(fileName))) {
            reader.readLine(); // Skip header
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(",");
                if (parts.length == 4) {
                    String name = parts[0].trim();
                    String category = parts[1].trim();
                    double price = Double.parseDouble(parts[2].trim());
                    boolean inStock = Boolean.parseBoolean(parts[3].trim());
                    productList.add(new Product(name, category, price, inStock));
                }
            }
        } catch (IOException e) {
            System.err.println("Error reading file: " + e.getMessage());
        }
        return productList;
    }

    private static void showInStockProducts(List<Product> products) {
        System.out.println("Available Products:");
        products.stream()
            .filter(p -> p.inStock)
            .forEach(System.out::println);
    }

    private static void sortByPriceDesc(List<Product> products) {
        System.out.println("Products Sorted by Price (High to Low):");
        products.stream()
            .sorted(Comparator.comparingDouble(Product::getPrice).reversed())
            .forEach(System.out::println);
    }

    private static void groupByCategory(List<Product> products) {
        System.out.println("Products Grouped by Category:");
        Map<String, List<Product>> grouped = products.stream()
            .collect(Collectors.groupingBy(p -> p.category));
        grouped.forEach((category, list) -> {
            System.out.println(category + ":");
            list.forEach(p -> System.out.println(" - " + p));
        });
    }

    private static void partitionByPrice(List<Product> products) {
        Map<Boolean, List<Product>> partitioned = products.stream()
            .collect(Collectors.partitioningBy(p -> p.price > 100));
        System.out.println("Expensive Products (> $100):");
        partitioned.get(true).forEach(System.out::println);
        System.out.println("Affordable Products (<= $100):");
        partitioned.get(false).forEach(System.out::println);
    }

    private static void showPriceStats(List<Product> products) {
        DoubleSummaryStatistics stats = products.stream()
            .collect(Collectors.summarizingDouble(p -> p.price));
        System.out.println("Price Statistics:");
        System.out.println("Count: " + stats.getCount());
        System.out.println("Min: $" + stats.getMin());
        System.out.println("Max: $" + stats.getMax());
        System.out.println("Avg: $" + stats.getAverage());
        System.out.println("Sum: $" + stats.getSum());
    }

    private static void showUppercaseNames(List<Product> products) {
        Map<String, List<String>> groupedNames = products.stream()
            .collect(Collectors.groupingBy(
                p -> p.category,
                Collectors.mapping(p -> p.name.toUpperCase(), Collectors.toList())
            ));
        System.out.println("Uppercase Product Names by Category:");
        groupedNames.forEach((cat, names) -> System.out.println(cat + ": " + names));
    }
}

```

---