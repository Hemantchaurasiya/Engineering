### **Phase 5: Advanced Stream API**

This phase focuses on more advanced collectors, transformations, and best practices.

---

### **Topics Covered**

1. **`flatMap()` – Flattening Nested Structures**
2. **`Collectors.collectingAndThen()` – Post-processing Results**
3. **`Collectors.reducing()` – Custom Reduction**
4. **Custom `Collector` Implementation**
5. **Parallel Streams**
6. **Performance Tips & Pitfalls**
7. **Practical Exercises + Mini Project**

---

### **1. `flatMap()` – Flattening Nested Structures**

Used to flatten a stream of lists (or arrays) into a single stream of elements.

```java

List<List<String>> nested = List.of(
    List.of("Apple", "Banana"),
    List.of("Orange", "Kiwi")
);

List<String> flattened = nested.stream()
    .flatMap(List::stream)
    .collect(Collectors.toList());

System.out.println(flattened); // [Apple, Banana, Orange, Kiwi]

```

**Use Case**: Flatten list of product tags, categories, sub-orders, etc.

---

### **2. `collectingAndThen()` – Post-Processing Collection**

Wrap a collector to apply transformation after collecting.

```java

List<String> names = List.of("apple", "banana", "kiwi");

List<String> upperUnmodifiable = names.stream()
    .map(String::toUpperCase)
    .collect(Collectors.collectingAndThen(
        Collectors.toList(),
        Collections::unmodifiableList
    ));

```

**Use Case**: Post-process or wrap the result like making unmodifiable lists or converting to another format.

---

### **3. `Collectors.reducing()` – Custom Reduction**

Use `reducing()` for custom aggregation.

```java

List<Integer> nums = List.of(1, 2, 3, 4, 5);
int product = nums.stream()
    .collect(Collectors.reducing(1, (a, b) -> a * b)); // Output: 120

```

**Use Case**: Aggregate products, combine values, or apply complex reduction logic.

---

### **4. Custom Collector**

Build your own Collector with `Collector.of()`.

```java

Collector<String, StringBuilder, String> customJoiner =
    Collector.of(
        StringBuilder::new,
        StringBuilder::append,
        StringBuilder::append,
        StringBuilder::toString
    );

String joined = Stream.of("Java", "Stream", "API")
    .collect(customJoiner);

System.out.println(joined); // JavaStreamAPI

```

**Use Case**: When built-in collectors aren't flexible enough.

---

### **5. Parallel Streams**

```java

List<String> items = List.of("a", "b", "c", "d", "e");

items.parallelStream()
     .map(String::toUpperCase)
     .forEach(System.out::println);

```

**Pros**: Leverages multiple cores.

**Cons**: Use only with large data, stateless operations, and performance testing.

---

### **6. Performance Tips & Pitfalls**

- Avoid side-effects in streams (`forEach` with mutations).
- Do not mix stateful and parallel streams.
- Prefer `map` over `peek`.
- Use lazy evaluation wisely.
- Benchmark when needed.

---

### **7. Hands-On Exercises**

- Flatten a list of product tags (List<List<String>>).
- Collect unmodifiable product names list.
- Use reducing() to find product with highest price.
- Create a custom collector to join product names with `|`.
- Run analytics using parallel stream and benchmark.

---

### **1. `flatMap()` – Flattening Nested Structures**

```java

List<List<String>> productTags = List.of(
    List.of("tech", "gadget"),
    List.of("home", "kitchen"),
    List.of("fitness", "health")
);

List<String> allTags = productTags.stream()
    .flatMap(List::stream)
    .collect(Collectors.toList());

System.out.println("All Tags: " + allTags);

```

---

### **2. `collectingAndThen()` – Unmodifiable List of Uppercase Names**

```java

List<String> products = List.of("laptop", "keyboard", "mouse");

List<String> upperUnmodifiable = products.stream()
    .map(String::toUpperCase)
    .collect(Collectors.collectingAndThen(
        Collectors.toList(),
        Collections::unmodifiableList
    ));

System.out.println("Unmodifiable Uppercase: " + upperUnmodifiable);
// upperUnmodifiable.add("MONITOR"); // throws UnsupportedOperationException

```

---

### **3. `Collectors.reducing()` – Product with Maximum Price**

```java

class Product {
    String name;
    double price;
    Product(String name, double price) {
        this.name = name;
        this.price = price;
    }
}

List<Product> productList = List.of(
    new Product("TV", 499.99),
    new Product("Fridge", 899.99),
    new Product("Phone", 999.99)
);

Product maxPriced = productList.stream()
    .collect(Collectors.reducing(
        (p1, p2) -> p1.price > p2.price ? p1 : p2
    )).orElse(null);

System.out.println("Most Expensive: " + (maxPriced != null ? maxPriced.name : "None"));

```

---

### **4. Custom Collector – Join Product Names with Pipe Separator**

```java

List<String> names = List.of("TV", "Phone", "Tablet");

Collector<String, StringBuilder, String> pipeJoiner = Collector.of(
    StringBuilder::new,
    (sb, str) -> {
        if (sb.length() > 0) sb.append(" | ");
        sb.append(str);
    },
    StringBuilder::append,
    StringBuilder::toString
);

String joinedNames = names.stream().collect(pipeJoiner);
System.out.println("Joined Names: " + joinedNames);

```

---

### **5. Parallel Stream – Convert to Uppercase & Print (non-deterministic order)**

```java

List<String> words = List.of("alpha", "beta", "gamma", "delta", "epsilon");

words.parallelStream()
     .map(String::toUpperCase)
     .forEach(System.out::println);

```

> Note: Use .forEachOrdered() for maintaining order in parallel streams.
> 

---

### Bonus: **Benchmark Parallel vs Sequential Stream**

```java

List<Integer> bigList = IntStream.rangeClosed(1, 1_000_000).boxed().toList();

// Sequential
long start = System.currentTimeMillis();
long sum = bigList.stream().mapToLong(Integer::longValue).sum();
System.out.println("Sequential sum: " + sum + ", time: " + (System.currentTimeMillis() - start) + " ms");

// Parallel
start = System.currentTimeMillis();
long parallelSum = bigList.parallelStream().mapToLong(Integer::longValue).sum();
System.out.println("Parallel sum: " + parallelSum + ", time: " + (System.currentTimeMillis() - start) + " ms");

```

---

**realistic mini project** next that combines all these: `flatMap`, `reducing`, `collectingAndThen`, custom `Collector`, and `parallelStream`?

## **Mini Project: Product Analytics System**

### **Project Overview:**

We will simulate a simple product catalog and use the Stream API to perform advanced analytics:

- **Flatten nested tags (flatMap)**
- **Get an unmodifiable sorted product list (collectingAndThen)**
- **Find most expensive product (reducing)**
- **Custom collector to join product names (Collector.of)**
- **Use parallelStream for analytics**

---

### **Step 1: Define Product Class**

```java

import java.util.List;

public class Product {
    private String name;
    private double price;
    private List<String> tags;

    public Product(String name, double price, List<String> tags) {
        this.name = name;
        this.price = price;
        this.tags = tags;
    }

    public String getName() { return name; }
    public double getPrice() { return price; }
    public List<String> getTags() { return tags; }

    @Override
    public String toString() {
        return name + " ($" + price + ")";
    }
}

```

---

### **Step 2: Main Class with Stream Analytics**

```java

import java.util.*;
import java.util.stream.*;
import java.util.function.*;
import java.util.concurrent.*;

public class ProductAnalyticsApp {
    public static void main(String[] args) {
        List<Product> products = List.of(
            new Product("Laptop", 1200.0, List.of("tech", "office")),
            new Product("Phone", 999.99, List.of("tech", "gadget")),
            new Product("Fridge", 800.0, List.of("home", "appliance")),
            new Product("Shoes", 150.0, List.of("fashion", "fitness")),
            new Product("TV", 1500.0, List.of("tech", "entertainment"))
        );

        // 1. flatMap – All Tags
        List<String> allTags = products.stream()
            .flatMap(p -> p.getTags().stream())
            .distinct()
            .collect(Collectors.toList());
        System.out.println("All Tags: " + allTags);

        // 2. collectingAndThen – Sorted Product Names (Unmodifiable)
        List<String> sortedNames = products.stream()
            .map(Product::getName)
            .sorted()
            .collect(Collectors.collectingAndThen(
                Collectors.toList(),
                Collections::unmodifiableList
            ));
        System.out.println("Sorted Product Names: " + sortedNames);

        // 3. reducing – Most Expensive Product
        Product mostExpensive = products.stream()
            .collect(Collectors.reducing((p1, p2) -> p1.getPrice() > p2.getPrice() ? p1 : p2))
            .orElse(null);
        System.out.println("Most Expensive: " + mostExpensive);

        // 4. Custom Collector – Join Names
        Collector<String, StringBuilder, String> nameJoiner = Collector.of(
            StringBuilder::new,
            (sb, str) -> {
                if (sb.length() > 0) sb.append(" | ");
                sb.append(str);
            },
            StringBuilder::append,
            StringBuilder::toString
        );

        String joinedNames = products.stream()
            .map(Product::getName)
            .collect(nameJoiner);
        System.out.println("Joined Product Names: " + joinedNames);

        // 5. parallelStream – Total Price Calculation
        double total = products.parallelStream()
            .mapToDouble(Product::getPrice)
            .sum();
        System.out.println("Total Price (Parallel): $" + total);
    }
}

```

---

### Output Example:

```
mathematica
CopyEdit
All Tags: [tech, office, gadget, home, appliance, fashion, fitness, entertainment]
Sorted Product Names: [Fridge, Laptop, Phone, Shoes, TV]
Most Expensive: TV ($1500.0)
Joined Product Names: Laptop | Phone | Fridge | Shoes | TV
Total Price (Parallel): $4649.99

```

---

Would you like the following next?

- **CSV integration to load products?**
- **Make it modular with services and utils?**
- **Add user input to run commands?**

Let me know how deep you want to take this.

## **Advanced Product Analytics System – Full Version**

### **Goals**:

1. **Read product data from CSV**
2. **Modularize code (model, service, util)**
3. **Enable user interaction to run analytics on-demand**

---

### **STEP 1: Project Structure**

```
kotlin
CopyEdit
product-analytics/
│
├── data/
│   └── products.csv
├── model/
│   └── Product.java
├── service/
│   └── ProductAnalyticsService.java
├── util/
│   └── CSVReaderUtil.java
└── ProductAnalyticsApp.java

```

---

### **STEP 2: `products.csv` (in `/data`)**

```
csv
CopyEdit
name,price,tags
Laptop,1200.0,tech;office
Phone,999.99,tech;gadget
Fridge,800.0,home;appliance
Shoes,150.0,fashion;fitness
TV,1500.0,tech;entertainment

```

---

### **STEP 3: `model/Product.java`**

```java

package model;

import java.util.List;

public class Product {
    private String name;
    private double price;
    private List<String> tags;

    public Product(String name, double price, List<String> tags) {
        this.name = name;
        this.price = price;
        this.tags = tags;
    }

    public String getName() { return name; }
    public double getPrice() { return price; }
    public List<String> getTags() { return tags; }

    @Override
    public String toString() {
        return name + " ($" + price + ")";
    }
}

```

---

### **STEP 4: `util/CSVReaderUtil.java`**

```java

package util;

import model.Product;

import java.io.BufferedReader;
import java.io.FileReader;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

public class CSVReaderUtil {
    public static List<Product> loadProductsFromCSV(String filePath) {
        List<Product> products = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new FileReader(Paths.get(filePath).toFile()))) {
            reader.readLine(); // skip header
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(",");
                String name = parts[0];
                double price = Double.parseDouble(parts[1]);
                List<String> tags = Arrays.asList(parts[2].split(";"));
                products.add(new Product(name, price, tags));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return products;
    }
}

```

---

### **STEP 5: `service/ProductAnalyticsService.java`**

```java

package service;

import model.Product;

import java.util.*;
import java.util.stream.*;

public class ProductAnalyticsService {
    public void printAllTags(List<Product> products) {
        List<String> allTags = products.stream()
            .flatMap(p -> p.getTags().stream())
            .distinct()
            .collect(Collectors.toList());

        System.out.println("All Tags: " + allTags);
    }

    public void printSortedNames(List<Product> products) {
        List<String> sortedNames = products.stream()
            .map(Product::getName)
            .sorted()
            .collect(Collectors.collectingAndThen(
                Collectors.toList(),
                Collections::unmodifiableList
            ));
        System.out.println("Sorted Names (Unmodifiable): " + sortedNames);
    }

    public void printMostExpensive(List<Product> products) {
        Product expensive = products.stream()
            .collect(Collectors.reducing((p1, p2) -> p1.getPrice() > p2.getPrice() ? p1 : p2))
            .orElse(null);
        System.out.println("Most Expensive Product: " + expensive);
    }

    public void printJoinedNames(List<Product> products) {
        String joined = products.stream()
            .map(Product::getName)
            .collect(Collector.of(
                StringBuilder::new,
                (sb, str) -> {
                    if (sb.length() > 0) sb.append(" | ");
                    sb.append(str);
                },
                StringBuilder::append,
                StringBuilder::toString
            ));
        System.out.println("Joined Product Names: " + joined);
    }

    public void printTotalPriceParallel(List<Product> products) {
        double total = products.parallelStream()
            .mapToDouble(Product::getPrice)
            .sum();
        System.out.println("Total Price (Parallel): $" + total);
    }
}

```

---

### **STEP 6: `ProductAnalyticsApp.java` (Main Interactive Runner)**

```java

import model.Product;
import service.ProductAnalyticsService;
import util.CSVReaderUtil;

import java.util.List;
import java.util.Scanner;

public class ProductAnalyticsApp {
    public static void main(String[] args) {
        List<Product> products = CSVReaderUtil.loadProductsFromCSV("data/products.csv");
        ProductAnalyticsService service = new ProductAnalyticsService();

        Scanner scanner = new Scanner(System.in);
        int choice;

        do {
            System.out.println("\n=== Product Analytics Menu ===");
            System.out.println("1. Show All Unique Tags");
            System.out.println("2. Show Sorted Product Names (Unmodifiable)");
            System.out.println("3. Show Most Expensive Product");
            System.out.println("4. Show Joined Product Names");
            System.out.println("5. Show Total Price (Parallel)");
            System.out.println("0. Exit");
            System.out.print("Enter your choice: ");

            choice = scanner.nextInt();
            switch (choice) {
                case 1 -> service.printAllTags(products);
                case 2 -> service.printSortedNames(products);
                case 3 -> service.printMostExpensive(products);
                case 4 -> service.printJoinedNames(products);
                case 5 -> service.printTotalPriceParallel(products);
                case 0 -> System.out.println("Exiting...");
                default -> System.out.println("Invalid option!");
            }
        } while (choice != 0);
    }
}

```

---

### ✅ Features Demonstrated:

- `flatMap`, `collectingAndThen`, `reducing`, custom `Collector.of`, `parallelStream`
- Modular design
- CSV data loader
- Interactive CLI menu

---