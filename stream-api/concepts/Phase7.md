## **Phase 7: Real-World Usage of Stream API in Spring Boot Projects**

In this phase, you’ll learn how to **effectively apply Stream API** inside **Spring Boot applications**, especially in:

- Service layers
- Controllers
- DTO mapping
- Repositories (in-memory and database)
- Real-world transformation pipelines

---

### **Core Topics We'll Cover:**

| Topic | Description |
| --- | --- |
| 1. Stream usage in `@Service` layer | Business logic processing |
| 2. Stream API in `Controller` | Streaming JSON responses |
| 3. Entity to DTO Mapping with Streams | Efficient list transformations |
| 4. Grouping and Filtering in-memory data | Use cases like reports, dashboards |
| 5. Stream API + Optional + Null safety | Real production handling |
| 6. Integrating Stream with JPA Projections | Using streams after DB query |
| 7. Using Stream with WebFlux | (Bonus) Reactive + Stream pipelines |

---

### **Example Use Case:**

Imagine a Spring Boot app for managing **Students & Courses**.

You need to return:

- Average scores by subject
- Top N performers
- Students grouped by grade
- Custom reports from multiple lists (joins)

---

## **Mini Project: Student Performance Dashboard**

### **Goal:**

Build a Spring Boot REST API that processes student data using **Java Stream API** to:

- Group by grade
- Calculate average marks
- Identify top performers
- Map entities to DTOs efficiently

---

### **Project Structure:**

```
CopyEdit
student-dashboard/
├── controller/
│   └── StudentController.java
├── dto/
│   └── StudentDTO.java
├── entity/
│   └── Student.java
├── repository/
│   └── StudentRepository.java
├── service/
│   └── StudentService.java
└── StudentDashboardApplication.java

```

---

### **1. Entity: `Student`**

```java

@Entity
public class Student {
    @Id
    private Long id;
    private String name;
    private String grade;
    private double marks;

    // Constructors, Getters, Setters
}

```

---

### **2. DTO: `StudentDTO`**

```java

public class StudentDTO {
    private String name;
    private double marks;

    public StudentDTO(String name, double marks) {
        this.name = name;
        this.marks = marks;
    }

    // Getters, Setters
}

```

---

### **3. Repository:**

```java

@Repository
public interface StudentRepository extends JpaRepository<Student, Long> {
}

```

---

### **4. Service Layer – `StudentService` with Stream API**

```java

@Service
public class StudentService {

    @Autowired
    private StudentRepository repository;

    public Map<String, Double> averageMarksByGrade() {
        return repository.findAll().stream()
            .collect(Collectors.groupingBy(
                Student::getGrade,
                Collectors.averagingDouble(Student::getMarks)
            ));
    }

    public List<StudentDTO> topPerformers(int limit) {
        return repository.findAll().stream()
            .sorted(Comparator.comparingDouble(Student::getMarks).reversed())
            .limit(limit)
            .map(s -> new StudentDTO(s.getName(), s.getMarks()))
            .collect(Collectors.toList());
    }

    public Map<String, List<String>> studentNamesByGrade() {
        return repository.findAll().stream()
            .collect(Collectors.groupingBy(
                Student::getGrade,
                Collectors.mapping(Student::getName, Collectors.toList())
            ));
    }
}

```

---

### **5. Controller:**

```java

@RestController
@RequestMapping("/api/students")
public class StudentController {

    @Autowired
    private StudentService service;

    @GetMapping("/average-marks")
    public Map<String, Double> getAverageMarksByGrade() {
        return service.averageMarksByGrade();
    }

    @GetMapping("/top/{count}")
    public List<StudentDTO> getTopPerformers(@PathVariable int count) {
        return service.topPerformers(count);
    }

    @GetMapping("/by-grade")
    public Map<String, List<String>> getStudentNamesByGrade() {
        return service.studentNamesByGrade();
    }
}

```

---

### **6. Sample Output:**

**GET `/api/students/average-marks`**

```json

{
  "A": 85.5,
  "B": 78.2,
  "C": 67.0
}

```

**GET `/api/students/top/3`**

```json

[
  {"name": "Ravi", "marks": 95.0},
  {"name": "Priya", "marks": 93.0},
  {"name": "Sonal", "marks": 89.5}
]

```

---

### **What You Practiced in Real-World:**

- Entity-DTO transformation with `.map()`
- Grouping and aggregation in the service layer
- Streaming and limiting top-N data
- Mapping nested results in clean APIs

---

**Bonus: Java Stream API + WebFlux (Reactive Streams)** — ideal for **reactive microservices or high-throughput APIs**.

---

## **Stream API + WebFlux (Reactive Streams)**

### **Goal:**

Learn how to combine **Stream-like operations** with **Project Reactor** (`Flux`, `Mono`) to build **reactive pipelines** in Spring Boot.

---

## **Key Differences: Stream vs Reactive**

| Java Stream API | Project Reactor (`Flux`, `Mono`) |
| --- | --- |
| Synchronous, blocking | Asynchronous, non-blocking |
| Works with in-memory data | Handles data from any source (DB, HTTP, etc.) |
| No backpressure support | Supports backpressure |
| Good for small datasets | Great for streaming large/continuous data |

---

## **Use Case: Streaming Student Scores (Reactive API)**

### **Project Structure:**

```
CopyEdit
student-reactive-dashboard/
├── controller/
│   └── ReactiveStudentController.java
├── model/
│   └── Student.java
├── service/
│   └── ReactiveStudentService.java
└── StudentReactiveApplication.java

```

---

### **1. Student Model**

```java

public class Student {
    private String name;
    private String grade;
    private double marks;

    // Constructors, Getters, Setters
}

```

---

### **2. Service using Flux**

```java

@Service
public class ReactiveStudentService {

    private final List<Student> students = List.of(
        new Student("Ravi", "A", 95.0),
        new Student("Sonal", "B", 78.5),
        new Student("Priya", "A", 88.0),
        new Student("Amit", "C", 66.0)
    );

    public Flux<Student> getAllStudents() {
        return Flux.fromIterable(students);
    }

    public Flux<Student> getTopPerformers(double minScore) {
        return Flux.fromIterable(students)
                   .filter(s -> s.getMarks() >= minScore)
                   .sort(Comparator.comparing(Student::getMarks).reversed());
    }

    public Mono<Double> getAverageMarks() {
        return Flux.fromIterable(students)
                   .map(Student::getMarks)
                   .collect(Collectors.averagingDouble(Double::doubleValue));
    }
}

```

---

### **3. Controller**

```java

@RestController
@RequestMapping("/reactive/students")
public class ReactiveStudentController {

    @Autowired
    private ReactiveStudentService service;

    @GetMapping
    public Flux<Student> allStudents() {
        return service.getAllStudents();
    }

    @GetMapping("/top")
    public Flux<Student> topStudents(@RequestParam double min) {
        return service.getTopPerformers(min);
    }

    @GetMapping("/average")
    public Mono<Double> averageMarks() {
        return service.getAverageMarks();
    }
}

```

---

### **4. Sample Responses**

**GET `/reactive/students/top?min=85`**

```json

[
  {"name": "Ravi", "grade": "A", "marks": 95.0},
  {"name": "Priya", "grade": "A", "marks": 88.0}
]

```

**GET `/reactive/students/average`**

```json

81.875

```

---

## **Key Concepts Practiced:**

- `Flux.fromIterable()` for reactive stream from list
- `filter`, `map`, `sort` like Java Stream
- `Mono` for single value (like `Optional`)
- Works asynchronously and non-blocking

---

## **Reactive MongoDB Integration in Spring Boot**

We'll update the `Student` project to fetch and process **data reactively** from MongoDB using **Spring Data Reactive MongoDB**.

---

### **Step 1: Add Dependencies**

In `pom.xml` (for Maven):

```xml
xml
CopyEdit
<dependencies>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-webflux</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-mongodb-reactive</artifactId>
    </dependency>
</dependencies>

```

---

### **Step 2: Define MongoDB Configuration**

In `application.yml` or `application.properties`:

```yaml
yaml
CopyEdit
spring:
  data:
    mongodb:
      uri: mongodb://localhost:27017/studentdb

```

---

### **Step 3: Define `Student` Document**

```java

@Document(collection = "students")
public class Student {
    @Id
    private String id;
    private String name;
    private String grade;
    private double marks;

    // Constructors, Getters, Setters
}

```

---

### **Step 4: Create Reactive Repository**

```java

@Repository
public interface StudentReactiveRepository extends ReactiveMongoRepository<Student, String> {

    Flux<Student> findByMarksGreaterThan(double minMarks);

    Flux<Student> findByGrade(String grade);
}

```

---

### **Step 5: Update Service Layer**

```java

@Service
public class ReactiveStudentService {

    @Autowired
    private StudentReactiveRepository repository;

    public Flux<Student> getAllStudents() {
        return repository.findAll();
    }

    public Flux<Student> getTopPerformers(double minScore) {
        return repository.findByMarksGreaterThan(minScore)
                         .sort(Comparator.comparingDouble(Student::getMarks).reversed());
    }

    public Mono<Double> getAverageMarks() {
        return repository.findAll()
                         .map(Student::getMarks)
                         .collect(Collectors.averagingDouble(Double::doubleValue));
    }

    public Flux<String> getNamesByGrade(String grade) {
        return repository.findByGrade(grade)
                         .map(Student::getName);
    }
}

```

---

### **Step 6: Controller (Same as Before)**

No change needed if you're using the same `ReactiveStudentController`.

---

### **Step 7: Sample MongoDB Document**

```json
{
  "_id": "1",
  "name": "Ravi",
  "grade": "A",
  "marks": 91.0
}

```