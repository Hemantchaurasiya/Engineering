# Phase 4: Advanced Entity Mapping - Complete Guide

## 4.1 Entity Relationships

Entity relationships are the backbone of any complex application. Let's explore each relationship type with detailed examples.

### @OneToOne Relationship

**Real-world scenario**: User and UserProfile relationship - each user has exactly one profile.

#### Unidirectional @OneToOne

```java
@Entity
@Table(name = "users")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(unique = true, nullable = false)
    private String username;
    
    @Column(nullable = false)
    private String email;
    
    // Unidirectional - only User knows about UserProfile
    @OneToOne(cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @JoinColumn(name = "profile_id", referencedColumnName = "id")
    private UserProfile profile;
    
    // constructors, getters, setters
    public User() {}
    
    public User(String username, String email) {
        this.username = username;
        this.email = email;
    }
    
    // getters and setters...
}

@Entity
@Table(name = "user_profiles")
public class UserProfile {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    private String firstName;
    private String lastName;
    
    @Column(length = 1000)
    private String bio;
    
    @Temporal(TemporalType.DATE)
    private Date birthDate;
    
    private String phoneNumber;
    
    // constructors, getters, setters
    public UserProfile() {}
    
    public UserProfile(String firstName, String lastName) {
        this.firstName = firstName;
        this.lastName = lastName;
    }
    
    // getters and setters...
}
```

#### Bidirectional @OneToOne

```java
@Entity
@Table(name = "users")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(unique = true, nullable = false)
    private String username;
    
    @Column(nullable = false)
    private String email;
    
    // Owner side of the relationship
    @OneToOne(cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @JoinColumn(name = "profile_id", referencedColumnName = "id")
    private UserProfile profile;
    
    // Helper method to maintain bidirectional relationship
    public void setProfile(UserProfile profile) {
        this.profile = profile;
        if (profile != null) {
            profile.setUser(this);
        }
    }
    
    // other methods...
}

@Entity
@Table(name = "user_profiles")
public class UserProfile {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    private String firstName;
    private String lastName;
    
    // Non-owner side - uses mappedBy
    @OneToOne(mappedBy = "profile", fetch = FetchType.LAZY)
    private User user;
    
    // Helper method
    public void setUser(User user) {
        this.user = user;
    }
    
    // other methods...
}
```

**Repository Usage:**
```java
@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    
    @Query("SELECT u FROM User u LEFT JOIN FETCH u.profile WHERE u.username = :username")
    Optional<User> findByUsernameWithProfile(@Param("username") String username);
}
```

### @OneToMany and @ManyToOne Relationships

**Real-world scenario**: Blog system with Posts and Comments

```java
@Entity
@Table(name = "posts")
public class Post {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false)
    private String title;
    
    @Column(length = 5000)
    private String content;
    
    @CreationTimestamp
    private LocalDateTime createdAt;
    
    @UpdateTimestamp
    private LocalDateTime updatedAt;
    
    // One post has many comments
    @OneToMany(mappedBy = "post", 
               cascade = CascadeType.ALL, 
               orphanRemoval = true,
               fetch = FetchType.LAZY)
    private List<Comment> comments = new ArrayList<>();
    
    // Author relationship
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id", nullable = false)
    private User author;
    
    // Helper methods for managing bidirectional relationships
    public void addComment(Comment comment) {
        comments.add(comment);
        comment.setPost(this);
    }
    
    public void removeComment(Comment comment) {
        comments.remove(comment);
        comment.setPost(null);
    }
    
    // constructors, getters, setters...
}

@Entity
@Table(name = "comments")
public class Comment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false, length = 1000)
    private String content;
    
    @CreationTimestamp
    private LocalDateTime createdAt;
    
    // Many comments belong to one post
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id", nullable = false)
    private Post post;
    
    // Comment author
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id", nullable = false)
    private User author;
    
    // constructors, getters, setters...
}
```

### @ManyToMany Relationships

**Real-world scenario**: Student-Course enrollment system

```java
@Entity
@Table(name = "students")
public class Student {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false)
    private String firstName;
    
    @Column(nullable = false)
    private String lastName;
    
    @Column(unique = true, nullable = false)
    private String studentNumber;
    
    // Many students can enroll in many courses
    @ManyToMany(cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    @JoinTable(
        name = "student_course_enrollment",
        joinColumns = @JoinColumn(name = "student_id"),
        inverseJoinColumns = @JoinColumn(name = "course_id")
    )
    private Set<Course> enrolledCourses = new HashSet<>();
    
    // Helper methods
    public void enrollInCourse(Course course) {
        enrolledCourses.add(course);
        course.getStudents().add(this);
    }
    
    public void unenrollFromCourse(Course course) {
        enrolledCourses.remove(course);
        course.getStudents().remove(this);
    }
    
    // constructors, getters, setters...
}

@Entity
@Table(name = "courses")
public class Course {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(unique = true, nullable = false)
    private String courseCode;
    
    @Column(nullable = false)
    private String title;
    
    private String description;
    
    private Integer creditHours;
    
    // Many courses can have many students
    @ManyToMany(mappedBy = "enrolledCourses")
    private Set<Student> students = new HashSet<>();
    
    // constructors, getters, setters...
}
```

### Advanced @ManyToMany with Additional Attributes

**Real-world scenario**: Student-Course with enrollment date and grade

```java
@Entity
@Table(name = "enrollments")
public class Enrollment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne
    @JoinColumn(name = "student_id")
    private Student student;
    
    @ManyToOne
    @JoinColumn(name = "course_id")
    private Course course;
    
    @CreationTimestamp
    private LocalDateTime enrollmentDate;
    
    @Enumerated(EnumType.STRING)
    private EnrollmentStatus status = EnrollmentStatus.ENROLLED;
    
    private String grade;
    private Double finalScore;
    
    // constructors, getters, setters...
}

public enum EnrollmentStatus {
    ENROLLED, COMPLETED, WITHDRAWN, FAILED
}
```

### Cascade Types Explained

```java
@Entity
public class Order {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    // CascadeType.PERSIST: When saving order, save order items too
    // CascadeType.MERGE: When updating order, update order items too
    // CascadeType.REMOVE: When deleting order, delete order items too
    // CascadeType.REFRESH: When refreshing order, refresh order items too
    // CascadeType.DETACH: When detaching order, detach order items too
    @OneToMany(mappedBy = "order", 
               cascade = CascadeType.ALL,  // Equivalent to all above
               orphanRemoval = true)       // Delete orphaned items
    private List<OrderItem> items = new ArrayList<>();
    
    @ManyToOne(cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    @JoinColumn(name = "customer_id")
    private Customer customer;
}
```

### Fetch Types: EAGER vs LAZY

```java
@Entity
public class Department {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    private String name;
    
    // LAZY: Employees loaded only when accessed
    @OneToMany(mappedBy = "department", fetch = FetchType.LAZY)
    private List<Employee> employees = new ArrayList<>();
    
    // EAGER: Manager loaded immediately with department
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "manager_id")
    private Employee manager;
}
```

**Best Practices for Fetch Types:**
- Use LAZY by default for collections and associations
- Use EAGER only when you're sure the data is always needed
- Use fetch joins in queries when you need eager loading for specific operations

## 4.2 Inheritance Mapping

JPA provides three strategies for mapping inheritance hierarchies to database tables.

### Single Table Inheritance (@Inheritance(strategy = InheritanceType.SINGLE_TABLE))

**Use case**: Different types of vehicles in a rental system

```java
@Entity
@Table(name = "vehicles")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "vehicle_type", discriminatorType = DiscriminatorType.STRING)
public abstract class Vehicle {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false)
    private String brand;
    
    @Column(nullable = false)
    private String model;
    
    @Column(nullable = false)
    private Integer year;
    
    @Column(name = "license_plate", unique = true)
    private String licensePlate;
    
    @Enumerated(EnumType.STRING)
    private VehicleStatus status = VehicleStatus.AVAILABLE;
    
    // constructors, getters, setters...
}

@Entity
@DiscriminatorValue("CAR")
public class Car extends Vehicle {
    private Integer numberOfDoors;
    private String fuelType;
    private Boolean isAutomatic;
    
    // Car-specific methods
    public boolean isCompact() {
        return numberOfDoors <= 2;
    }
    
    // constructors, getters, setters...
}

@Entity
@DiscriminatorValue("MOTORCYCLE")
public class Motorcycle extends Vehicle {
    private Integer engineCapacity;
    private Boolean hasSidecar;
    
    // constructors, getters, setters...
}

@Entity
@DiscriminatorValue("TRUCK")
public class Truck extends Vehicle {
    private Double loadCapacity;
    private Integer numberOfAxles;
    
    // constructors, getters, setters...
}

public enum VehicleStatus {
    AVAILABLE, RENTED, MAINTENANCE, RETIRED
}
```

**Repository for inheritance:**
```java
@Repository
public interface VehicleRepository extends JpaRepository<Vehicle, Long> {
    
    List<Car> findCarsByFuelType(String fuelType);
    
    @Query("SELECT m FROM Motorcycle m WHERE m.engineCapacity >= :minCapacity")
    List<Motorcycle> findMotorcyclesByMinEngineCapacity(@Param("minCapacity") Integer minCapacity);
    
    @Query("SELECT v FROM Vehicle v WHERE TYPE(v) = :vehicleType")
    List<Vehicle> findByVehicleType(@Param("vehicleType") Class<? extends Vehicle> vehicleType);
}
```

### Table Per Class (@Inheritance(strategy = InheritanceType.TABLE_PER_CLASS))

```java
@Entity
@Inheritance(strategy = InheritanceType.TABLE_PER_CLASS)
public abstract class Account {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO) // Important: Use AUTO or TABLE
    private Long id;
    
    @Column(unique = true, nullable = false)
    private String accountNumber;
    
    @Column(nullable = false)
    private BigDecimal balance;
    
    @ManyToOne
    @JoinColumn(name = "customer_id")
    private Customer customer;
    
    // constructors, getters, setters...
}

@Entity
@Table(name = "savings_accounts")
public class SavingsAccount extends Account {
    private BigDecimal interestRate;
    private BigDecimal minimumBalance;
    
    public void applyInterest() {
        BigDecimal interest = getBalance().multiply(interestRate).divide(BigDecimal.valueOf(100));
        setBalance(getBalance().add(interest));
    }
    
    // constructors, getters, setters...
}

@Entity
@Table(name = "checking_accounts")
public class CheckingAccount extends Account {
    private BigDecimal overdraftLimit;
    private BigDecimal monthlyFee;
    
    public boolean canWithdraw(BigDecimal amount) {
        return getBalance().add(overdraftLimit).compareTo(amount) >= 0;
    }
    
    // constructors, getters, setters...
}
```

### Joined Table (@Inheritance(strategy = InheritanceType.JOINED))

```java
@Entity
@Table(name = "employees")
@Inheritance(strategy = InheritanceType.JOINED)
public abstract class Employee {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false)
    private String firstName;
    
    @Column(nullable = false)
    private String lastName;
    
    @Column(unique = true, nullable = false)
    private String employeeNumber;
    
    @Column(nullable = false)
    private BigDecimal baseSalary;
    
    @Temporal(TemporalType.DATE)
    private Date hireDate;
    
    // constructors, getters, setters...
}

@Entity
@Table(name = "full_time_employees")
@PrimaryKeyJoinColumn(name = "employee_id")
public class FullTimeEmployee extends Employee {
    private Integer vacationDays;
    private BigDecimal healthInsuranceContribution;
    private BigDecimal retirementContribution;
    
    public BigDecimal calculateTotalCompensation() {
        return getBaseSalary()
            .add(healthInsuranceContribution)
            .add(retirementContribution);
    }
    
    // constructors, getters, setters...
}

@Entity
@Table(name = "part_time_employees")
@PrimaryKeyJoinColumn(name = "employee_id")
public class PartTimeEmployee extends Employee {
    private Integer hoursPerWeek;
    private BigDecimal hourlyRate;
    
    public BigDecimal calculateMonthlySalary() {
        return hourlyRate.multiply(BigDecimal.valueOf(hoursPerWeek * 4));
    }
    
    // constructors, getters, setters...
}

@Entity
@Table(name = "contractors")
@PrimaryKeyJoinColumn(name = "employee_id")
public class Contractor extends Employee {
    private LocalDate contractStartDate;
    private LocalDate contractEndDate;
    private BigDecimal projectRate;
    
    public boolean isContractActive() {
        LocalDate now = LocalDate.now();
        return now.isAfter(contractStartDate) && now.isBefore(contractEndDate);
    }
    
    // constructors, getters, setters...
}
```

### @MappedSuperclass

**Use case**: Common auditing fields across entities

```java
@MappedSuperclass
public abstract class AuditableEntity {
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    @CreatedBy
    @Column(name = "created_by", updatable = false)
    private String createdBy;
    
    @LastModifiedBy
    @Column(name = "updated_by")
    private String updatedBy;
    
    @Version
    private Long version;
    
    // getters and setters...
}

@Entity
@Table(name = "products")
public class Product extends AuditableEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false)
    private String name;
    
    private String description;
    
    @Column(nullable = false)
    private BigDecimal price;
    
    // Product inherits all auditing fields
    // constructors, getters, setters...
}
```

## 4.3 Composite Keys and Embedded Objects

### Composite Primary Keys with @EmbeddedId

```java
@Embeddable
public class OrderItemId implements Serializable {
    @Column(name = "order_id")
    private Long orderId;
    
    @Column(name = "product_id")
    private Long productId;
    
    // Default constructor required
    public OrderItemId() {}
    
    public OrderItemId(Long orderId, Long productId) {
        this.orderId = orderId;
        this.productId = productId;
    }
    
    // equals and hashCode are MANDATORY
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        OrderItemId that = (OrderItemId) o;
        return Objects.equals(orderId, that.orderId) && 
               Objects.equals(productId, that.productId);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(orderId, productId);
    }
    
    // getters and setters...
}

@Entity
@Table(name = "order_items")
public class OrderItem {
    @EmbeddedId
    private OrderItemId id;
    
    @ManyToOne
    @MapsId("orderId")  // Maps the orderId portion of composite key
    @JoinColumn(name = "order_id")
    private Order order;
    
    @ManyToOne
    @MapsId("productId")  // Maps the productId portion of composite key
    @JoinColumn(name = "product_id")
    private Product product;
    
    @Column(nullable = false)
    private Integer quantity;
    
    @Column(name = "unit_price", nullable = false)
    private BigDecimal unitPrice;
    
    public BigDecimal getTotalPrice() {
        return unitPrice.multiply(BigDecimal.valueOf(quantity));
    }
    
    // constructors, getters, setters...
}
```

### Alternative with @IdClass

```java
@IdClass(OrderItemId.class)
@Entity
@Table(name = "order_items_alt")
public class OrderItemAlt {
    @Id
    @Column(name = "order_id")
    private Long orderId;
    
    @Id
    @Column(name = "product_id")
    private Long productId;
    
    @ManyToOne
    @JoinColumn(name = "order_id", insertable = false, updatable = false)
    private Order order;
    
    @ManyToOne
    @JoinColumn(name = "product_id", insertable = false, updatable = false)
    private Product product;
    
    private Integer quantity;
    private BigDecimal unitPrice;
    
    // constructors, getters, setters...
}
```

### Embedded Value Objects

```java
@Embeddable
public class Address {
    @Column(name = "street_address")
    private String streetAddress;
    
    private String city;
    
    @Column(name = "state_province")
    private String stateProvince;
    
    @Column(name = "postal_code")
    private String postalCode;
    
    private String country;
    
    // Embedded objects should be value objects (immutable)
    public Address() {}
    
    public Address(String streetAddress, String city, String stateProvince, 
                   String postalCode, String country) {
        this.streetAddress = streetAddress;
        this.city = city;
        this.stateProvince = stateProvince;
        this.postalCode = postalCode;
        this.country = country;
    }
    
    public String getFullAddress() {
        return String.format("%s, %s, %s %s, %s", 
            streetAddress, city, stateProvince, postalCode, country);
    }
    
    // getters (no setters for immutability)...
}

@Entity
@Table(name = "customers")
public class Customer {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    private String firstName;
    private String lastName;
    
    @Embedded
    private Address billingAddress;
    
    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "streetAddress", column = @Column(name = "shipping_street_address")),
        @AttributeOverride(name = "city", column = @Column(name = "shipping_city")),
        @AttributeOverride(name = "stateProvince", column = @Column(name = "shipping_state_province")),
        @AttributeOverride(name = "postalCode", column = @Column(name = "shipping_postal_code")),
        @AttributeOverride(name = "country", column = @Column(name = "shipping_country"))
    })
    private Address shippingAddress;
    
    // constructors, getters, setters...
}
```

## 4.4 Advanced Mapping Features

### @SecondaryTable - Multiple Table Mapping

```java
@Entity
@Table(name = "users")
@SecondaryTable(name = "user_details", 
    pkJoinColumns = @PrimaryKeyJoinColumn(name = "user_id"))
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    // Fields mapped to primary table
    @Column(table = "users")
    private String username;
    
    @Column(table = "users")
    private String email;
    
    // Fields mapped to secondary table
    @Column(table = "user_details")
    private String firstName;
    
    @Column(table = "user_details")
    private String lastName;
    
    @Column(table = "user_details")
    private String biography;
    
    @Column(table = "user_details", name = "profile_picture_url")
    private String profilePictureUrl;
    
    // constructors, getters, setters...
}
```

### @Formula - Calculated Properties

```java
@Entity
@Table(name = "products")
public class Product {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    private String name;
    
    @Column(name = "base_price")
    private BigDecimal basePrice;
    
    @Column(name = "tax_rate")
    private BigDecimal taxRate;
    
    @Column(name = "discount_percentage")
    private BigDecimal discountPercentage;
    
    // Calculated field using SQL formula
    @Formula("base_price * (1 - discount_percentage / 100) * (1 + tax_rate / 100)")
    private BigDecimal finalPrice;
    
    @Formula("(SELECT COUNT(*) FROM order_items oi WHERE oi.product_id = id)")
    private Long totalOrderCount;
    
    @Formula("(SELECT AVG(r.rating) FROM reviews r WHERE r.product_id = id)")
    private Double averageRating;
    
    // getters (no setters for calculated fields)...
}
```

### @Where and @Filter - Entity Filtering

```java
@Entity
@Table(name = "posts")
@Where(clause = "deleted = false")  // Applied to all queries
@FilterDef(name = "publishedFilter", 
           parameters = @ParamDef(name = "published", type = "boolean"))
@Filter(name = "publishedFilter", condition = "published = :published")
public class Post {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    private String title;
    private String content;
    
    @Column(name = "published", nullable = false)
    private Boolean published = false;
    
    @Column(name = "deleted", nullable = false)
    private Boolean deleted = false;
    
    @CreationTimestamp
    private LocalDateTime createdAt;
    
    // constructors, getters, setters...
}

// Usage in service
@Service
@Transactional
public class PostService {
    
    @Autowired
    private EntityManager entityManager;
    
    public List<Post> getPublishedPosts() {
        // Enable filter
        Session session = entityManager.unwrap(Session.class);
        session.enableFilter("publishedFilter").setParameter("published", true);
        
        // Execute query - filter will be automatically applied
        return entityManager.createQuery("SELECT p FROM Post p", Post.class)
                          .getResultList();
    }
}
```

### @Converter - Attribute Converters

```java
// Enum to String converter
@Converter(autoApply = true)
public class StatusConverter implements AttributeConverter<Status, String> {
    
    @Override
    public String convertToDatabaseColumn(Status status) {
        return status != null ? status.getCode() : null;
    }
    
    @Override
    public Status convertToEntityAttribute(String code) {
        return code != null ? Status.fromCode(code) : null;
    }
}

public enum Status {
    ACTIVE("A"), INACTIVE("I"), PENDING("P"), SUSPENDED("S");
    
    private final String code;
    
    Status(String code) {
        this.code = code;
    }
    
    public String getCode() {
        return code;
    }
    
    public static Status fromCode(String code) {
        for (Status status : values()) {
            if (status.code.equals(code)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown status code: " + code);
    }
}

// JSON converter
@Converter
public class JsonConverter implements AttributeConverter<Object, String> {
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    @Override
    public String convertToDatabaseColumn(Object attribute) {
        try {
            return attribute != null ? objectMapper.writeValueAsString(attribute) : null;
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Error converting to JSON", e);
        }
    }
    
    @Override
    public Object convertToEntityAttribute(String dbData) {
        try {
            return dbData != null ? objectMapper.readValue(dbData, Object.class) : null;
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Error converting from JSON", e);
        }
    }
}

@Entity
public class UserPreferences {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    // Uses StatusConverter automatically
    private Status accountStatus;
    
    // Uses JsonConverter explicitly
    @Convert(converter = JsonConverter.class)
    @Column(columnDefinition = "TEXT")
    private Map<String, Object> settings;
    
    // constructors, getters, setters...
}
```

### @EntityListeners - Entity Lifecycle Callbacks

```java
// Generic audit listener
public class AuditListener {
    
    @PrePersist
    public void prePersist(Object entity) {
        if (entity instanceof Auditable) {
            Auditable auditable = (Auditable) entity;
            auditable.setCreatedAt(LocalDateTime.now());
            auditable.setCreatedBy(getCurrentUsername());
        }
    }
    
    @PreUpdate
    public void preUpdate(Object entity) {
        if (entity instanceof Auditable) {
            Auditable auditable = (Auditable) entity;
            auditable.setUpdatedAt(LocalDateTime.now());
            auditable.setUpdatedBy(getCurrentUsername());
        }
    }
    
    private String getCurrentUsername() {
        // Get current user from security context
        return SecurityContextHolder.getContext()
                .getAuthentication()
                .getName();
    }
}

// Auditable interface
public interface Auditable {
    void setCreatedAt(LocalDateTime createdAt);
    void setUpdatedAt(LocalDateTime updatedAt);
    void setCreatedBy(String createdBy);
    void setUpdatedBy(String updatedBy);
}

// Entity using the listener
@Entity
@EntityListeners(AuditListener.class)
public class Document implements Auditable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    private String title;
    private String content;
    
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String createdBy;
    private String updatedBy;
    
    // Implement Auditable interface
    @Override
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
    
    // ... other auditable methods
    
    // Entity-specific callbacks
    @PostLoad
    @PostPersist
    @PostUpdate
    private void logAccess() {
        System.out.println("Document " + id + " was accessed at " + LocalDateTime.now());
    }
    
    @PreRemove
    private void beforeRemove() {
        System.out.println("Document " + id + " is about to be deleted");
    }
    
    // constructors, getters, setters...
}
```

## Real-world Example: E-commerce System

Here's a complete example that combines many of the concepts we've covered:

```java
// Base auditable entity
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseEntity {
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    @CreatedBy
    @Column(name = "created_by", updatable = false)
    private String createdBy;
    
    @LastModifiedBy
    @Column(name = "updated_by")
    private String updatedBy;
    
    @Version
    private Long version;
    
    // getters and setters...
}

// Customer entity
@Entity
@Table(name = "customers")
public class Customer extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false)
    private String firstName;
    
    @Column(nullable = false)
    private String lastName;
    
    @Column(unique = true, nullable = false)
    private String email;
    
    @Column(unique = true)
    private String phoneNumber;
    
    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "streetAddress", column = @Column(name = "billing_street")),
        @AttributeOverride(name = "city", column = @Column(name = "billing_city")),
        @AttributeOverride(name = "state", column = @Column(name = "billing_state")),
        @AttributeOverride(name = "zipCode", column = @Column(name = "billing_zip")),
        @AttributeOverride(name = "country", column = @Column(name = "billing_country"))
    })
    private Address billingAddress;
    
    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "streetAddress", column = @Column(name = "shipping_street")),
        @AttributeOverride(name = "city", column = @Column(name = "shipping_city")),
        @AttributeOverride(name = "state", column = @Column(name = "shipping_state")),
        @AttributeOverride(name = "zipCode", column = @Column(name = "shipping_zip")),
        @AttributeOverride(name = "country", column = @Column(name = "shipping_country"))
    })
    private Address shippingAddress;
    
    @OneToMany(mappedBy = "customer", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Order> orders = new ArrayList<>();
    
    @Enumerated(EnumType.STRING)
    private CustomerStatus status = CustomerStatus.ACTIVE;
    
    // Helper methods
    public String getFullName() {
        return firstName + " " + lastName;
    }
    
    public void addOrder(Order order) {
        orders.add(order);
        order.setCustomer(this);
    }
    
    // constructors, getters, setters...
}

// Address embeddable
@Embeddable
public class Address {
    @Column(name = "street_address")
    private String streetAddress;
    
    private String city;
    private String state;
    
    @Column(name = "zip_code")
    private String zipCode;
    
    private String country;
    
    public Address() {}
    
    public Address(String streetAddress, String city, String state, String zipCode, String country) {
        this.streetAddress = streetAddress;
        this.city = city;
        this.state = state;
        this.zipCode = zipCode;
        this.country = country;
    }
    
    public String getFormattedAddress() {
        return String.format("%s, %s, %s %s, %s", 
            streetAddress, city, state, zipCode, country);
    }
    
    // getters and setters...
}

// Order entity with composite relationships
@Entity
@Table(name = "orders")
public class Order extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(unique = true, nullable = false)
    private String orderNumber;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;
    
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> items = new ArrayList<>();
    
    @Enumerated(EnumType.STRING)
    private OrderStatus status = OrderStatus.PENDING;
    
    @Column(name = "order_date", nullable = false)
    private LocalDateTime orderDate;
    
    @Column(name = "total_amount")
    private BigDecimal totalAmount;
    
    // Calculated field
    @Formula("(SELECT SUM(oi.quantity * oi.unit_price) FROM order_items oi WHERE oi.order_id = id)")
    private BigDecimal calculatedTotal;
    
    // Helper methods
    public void addItem(OrderItem item) {
        items.add(item);
        item.setOrder(this);
        calculateTotal();
    }
    
    public void removeItem(OrderItem item) {
        items.remove(item);
        item.setOrder(null);
        calculateTotal();
    }
    
    private void calculateTotal() {
        totalAmount = items.stream()
            .map(item -> item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
    
    @PrePersist
    @PreUpdate
    private void updateTotal() {
        calculateTotal();
    }
    
    // constructors, getters, setters...
}

// OrderItem with composite key
@Entity
@Table(name = "order_items")
public class OrderItem {
    @EmbeddedId
    private OrderItemId id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("orderId")
    @JoinColumn(name = "order_id")
    private Order order;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("productId")
    @JoinColumn(name = "product_id")
    private Product product;
    
    @Column(nullable = false)
    private Integer quantity;
    
    @Column(name = "unit_price", nullable = false)
    private BigDecimal unitPrice;
    
    // Calculated methods
    public BigDecimal getSubtotal() {
        return unitPrice.multiply(BigDecimal.valueOf(quantity));
    }
    
    // constructors, getters, setters...
}

// Product with inheritance
@Entity
@Table(name = "products")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "product_type")
public abstract class Product extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false)
    private String name;
    
    private String description;
    
    @Column(nullable = false)
    private BigDecimal price;
    
    @Column(name = "stock_quantity")
    private Integer stockQuantity;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;
    
    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL)
    private List<ProductReview> reviews = new ArrayList<>();
    
    // Abstract method to be implemented by subclasses
    public abstract String getProductType();
    public abstract BigDecimal calculateShippingCost();
    
    // constructors, getters, setters...
}

@Entity
@DiscriminatorValue("PHYSICAL")
public class PhysicalProduct extends Product {
    private Double weight;
    private String dimensions;
    private Boolean fragile;
    
    @Override
    public String getProductType() {
        return "Physical Product";
    }
    
    @Override
    public BigDecimal calculateShippingCost() {
        BigDecimal baseCost = BigDecimal.valueOf(5.00);
        if (weight != null && weight > 10) {
            baseCost = baseCost.add(BigDecimal.valueOf(weight * 0.5));
        }
        if (fragile != null && fragile) {
            baseCost = baseCost.multiply(BigDecimal.valueOf(1.5));
        }
        return baseCost;
    }
    
    // constructors, getters, setters...
}

@Entity
@DiscriminatorValue("DIGITAL")
public class DigitalProduct extends Product {
    private String downloadUrl;
    private String licenseKey;
    private Integer downloadLimit;
    
    @Override
    public String getProductType() {
        return "Digital Product";
    }
    
    @Override
    public BigDecimal calculateShippingCost() {
        return BigDecimal.ZERO; // No shipping for digital products
    }
    
    // constructors, getters, setters...
}

// Category with self-referencing relationship
@Entity
@Table(name = "categories")
public class Category extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false)
    private String name;
    
    private String description;
    
    // Self-referencing relationship for hierarchical categories
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Category parent;
    
    @OneToMany(mappedBy = "parent", cascade = CascadeType.ALL)
    private List<Category> subcategories = new ArrayList<>();
    
    @OneToMany(mappedBy = "category")
    private List<Product> products = new ArrayList<>();
    
    // Helper methods
    public void addSubcategory(Category subcategory) {
        subcategories.add(subcategory);
        subcategory.setParent(this);
    }
    
    public boolean isRootCategory() {
        return parent == null;
    }
    
    public List<Category> getAllAncestors() {
        List<Category> ancestors = new ArrayList<>();
        Category current = this.parent;
        while (current != null) {
            ancestors.add(current);
            current = current.getParent();
        }
        return ancestors;
    }
    
    // constructors, getters, setters...
}

// Enums
public enum CustomerStatus {
    ACTIVE, INACTIVE, SUSPENDED, BANNED
}

public enum OrderStatus {
    PENDING, CONFIRMED, PROCESSING, SHIPPED, DELIVERED, CANCELLED, REFUNDED
}

// Composite key for OrderItem
@Embeddable
public class OrderItemId implements Serializable {
    @Column(name = "order_id")
    private Long orderId;
    
    @Column(name = "product_id")
    private Long productId;
    
    public OrderItemId() {}
    
    public OrderItemId(Long orderId, Long productId) {
        this.orderId = orderId;
        this.productId = productId;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        OrderItemId that = (OrderItemId) o;
        return Objects.equals(orderId, that.orderId) && 
               Objects.equals(productId, that.productId);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(orderId, productId);
    }
    
    // getters and setters...
}
```

## Repository Interfaces for Complex Relationships

```java
@Repository
public interface CustomerRepository extends JpaRepository<Customer, Long> {
    
    // Find customers with their orders
    @Query("SELECT DISTINCT c FROM Customer c LEFT JOIN FETCH c.orders WHERE c.status = :status")
    List<Customer> findActiveCustomersWithOrders(@Param("status") CustomerStatus status);
    
    // Find customers by address components
    List<Customer> findByBillingAddress_CityAndBillingAddress_State(String city, String state);
    
    // Custom query with embedded object
    @Query("SELECT c FROM Customer c WHERE c.shippingAddress.country = :country")
    List<Customer> findByShippingCountry(@Param("country") String country);
}

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {
    
    // Find orders with items and products
    @Query("SELECT DISTINCT o FROM Order o " +
           "LEFT JOIN FETCH o.items oi " +
           "LEFT JOIN FETCH oi.product " +
           "WHERE o.customer.id = :customerId")
    List<Order> findOrdersWithItemsByCustomerId(@Param("customerId") Long customerId);
    
    // Find orders by status with date range
    @Query("SELECT o FROM Order o WHERE o.status = :status " +
           "AND o.orderDate BETWEEN :startDate AND :endDate")
    List<Order> findByStatusAndDateRange(@Param("status") OrderStatus status,
                                       @Param("startDate") LocalDateTime startDate,
                                       @Param("endDate") LocalDateTime endDate);
    
    // Calculate total revenue
    @Query("SELECT SUM(o.totalAmount) FROM Order o WHERE o.status = 'DELIVERED'")
    BigDecimal calculateTotalRevenue();
}

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {
    
    // Find products by type using inheritance
    @Query("SELECT p FROM Product p WHERE TYPE(p) = PhysicalProduct")
    List<PhysicalProduct> findAllPhysicalProducts();
    
    @Query("SELECT p FROM Product p WHERE TYPE(p) = DigitalProduct")
    List<DigitalProduct> findAllDigitalProducts();
    
    // Find products with category hierarchy
    @Query("SELECT p FROM Product p WHERE p.category = :category " +
           "OR p.category.parent = :category")
    List<Product> findByCategoryOrParentCategory(@Param("category") Category category);
}

@Repository
public interface CategoryRepository extends JpaRepository<Category, Long> {
    
    // Find root categories
    List<Category> findByParentIsNull();
    
    // Find subcategories
    List<Category> findByParentId(Long parentId);
    
    // Find categories with products count
    @Query("SELECT c, COUNT(p) FROM Category c LEFT JOIN c.products p GROUP BY c")
    List<Object[]> findCategoriesWithProductCount();
}
```

## Service Layer Implementation

```java
@Service
@Transactional
public class OrderService {
    
    @Autowired
    private OrderRepository orderRepository;
    
    @Autowired
    private ProductRepository productRepository;
    
    @Autowired
    private CustomerRepository customerRepository;
    
    public Order createOrder(Long customerId, List<OrderItemRequest> itemRequests) {
        Customer customer = customerRepository.findById(customerId)
            .orElseThrow(() -> new EntityNotFoundException("Customer not found"));
        
        Order order = new Order();
        order.setCustomer(customer);
        order.setOrderNumber(generateOrderNumber());
        order.setOrderDate(LocalDateTime.now());
        order.setStatus(OrderStatus.PENDING);
        
        // Add items to order
        for (OrderItemRequest request : itemRequests) {
            Product product = productRepository.findById(request.getProductId())
                .orElseThrow(() -> new EntityNotFoundException("Product not found"));
            
            // Check stock availability
            if (product.getStockQuantity() < request.getQuantity()) {
                throw new InsufficientStockException("Not enough stock for product: " + product.getName());
            }
            
            OrderItem item = new OrderItem();
            item.setId(new OrderItemId(null, product.getId())); // Order ID will be set after save
            item.setProduct(product);
            item.setQuantity(request.getQuantity());
            item.setUnitPrice(product.getPrice());
            
            order.addItem(item);
            
            // Update stock
            product.setStockQuantity(product.getStockQuantity() - request.getQuantity());
        }
        
        Order savedOrder = orderRepository.save(order);
        
        // Update OrderItem IDs after order is saved
        savedOrder.getItems().forEach(item -> 
            item.getId().setOrderId(savedOrder.getId()));
        
        return savedOrder;
    }
    
    @Transactional(readOnly = true)
    public List<Order> getCustomerOrdersWithDetails(Long customerId) {
        return orderRepository.findOrdersWithItemsByCustomerId(customerId);
    }
    
    public Order updateOrderStatus(Long orderId, OrderStatus newStatus) {
        Order order = orderRepository.findById(orderId)
            .orElseThrow(() -> new EntityNotFoundException("Order not found"));
        
        validateStatusTransition(order.getStatus(), newStatus);
        order.setStatus(newStatus);
        
        return orderRepository.save(order);
    }
    
    private void validateStatusTransition(OrderStatus currentStatus, OrderStatus newStatus) {
        // Business logic for valid status transitions
        Map<OrderStatus, Set<OrderStatus>> validTransitions = Map.of(
            OrderStatus.PENDING, Set.of(OrderStatus.CONFIRMED, OrderStatus.CANCELLED),
            OrderStatus.CONFIRMED, Set.of(OrderStatus.PROCESSING, OrderStatus.CANCELLED),
            OrderStatus.PROCESSING, Set.of(OrderStatus.SHIPPED, OrderStatus.CANCELLED),
            OrderStatus.SHIPPED, Set.of(OrderStatus.DELIVERED),
            OrderStatus.DELIVERED, Set.of(OrderStatus.REFUNDED)
        );
        
        if (!validTransitions.getOrDefault(currentStatus, Set.of()).contains(newStatus)) {
            throw new InvalidStatusTransitionException(
                String.format("Cannot transition from %s to %s", currentStatus, newStatus));
        }
    }
    
    private String generateOrderNumber() {
        return "ORD-" + System.currentTimeMillis();
    }
}

// DTO for order item requests
public class OrderItemRequest {
    private Long productId;
    private Integer quantity;
    
    // constructors, getters, setters...
}

// Custom exceptions
public class InsufficientStockException extends RuntimeException {
    public InsufficientStockException(String message) {
        super(message);
    }
}

public class InvalidStatusTransitionException extends RuntimeException {
    public InvalidStatusTransitionException(String message) {
        super(message);
    }
}
```

## Advanced Repository Patterns

```java
// Custom repository interface
public interface CustomOrderRepository {
    List<Order> findOrdersWithComplexCriteria(OrderSearchCriteria criteria);
    OrderStatistics calculateOrderStatistics(LocalDateTime startDate, LocalDateTime endDate);
}

// Custom repository implementation
@Repository
public class CustomOrderRepositoryImpl implements CustomOrderRepository {
    
    @PersistenceContext
    private EntityManager entityManager;
    
    @Override
    public List<Order> findOrdersWithComplexCriteria(OrderSearchCriteria criteria) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Order> query = cb.createQuery(Order.class);
        Root<Order> order = query.from(Order.class);
        
        List<Predicate> predicates = new ArrayList<>();
        
        // Customer name filter
        if (criteria.getCustomerName() != null) {
            Join<Order, Customer> customer = order.join("customer");
            predicates.add(cb.or(
                cb.like(cb.lower(customer.get("firstName")), 
                       "%" + criteria.getCustomerName().toLowerCase() + "%"),
                cb.like(cb.lower(customer.get("lastName")), 
                       "%" + criteria.getCustomerName().toLowerCase() + "%")
            ));
        }
        
        // Status filter
        if (criteria.getStatus() != null) {
            predicates.add(cb.equal(order.get("status"), criteria.getStatus()));
        }
        
        // Date range filter
        if (criteria.getStartDate() != null && criteria.getEndDate() != null) {
            predicates.add(cb.between(order.get("orderDate"), 
                                    criteria.getStartDate(), criteria.getEndDate()));
        }
        
        // Amount range filter
        if (criteria.getMinAmount() != null) {
            predicates.add(cb.greaterThanOrEqualTo(order.get("totalAmount"), 
                                                 criteria.getMinAmount()));
        }
        
        if (criteria.getMaxAmount() != null) {
            predicates.add(cb.lessThanOrEqualTo(order.get("totalAmount"), 
                                              criteria.getMaxAmount()));
        }
        
        query.where(predicates.toArray(new Predicate[0]));
        query.orderBy(cb.desc(order.get("orderDate")));
        
        return entityManager.createQuery(query).getResultList();
    }
    
    @Override
    public OrderStatistics calculateOrderStatistics(LocalDateTime startDate, LocalDateTime endDate) {
        String jpql = """
            SELECT 
                COUNT(o),
                SUM(o.totalAmount),
                AVG(o.totalAmount),
                MIN(o.totalAmount),
                MAX(o.totalAmount)
            FROM Order o 
            WHERE o.orderDate BETWEEN :startDate AND :endDate
            AND o.status != 'CANCELLED'
            """;
        
        Object[] result = (Object[]) entityManager.createQuery(jpql)
            .setParameter("startDate", startDate)
            .setParameter("endDate", endDate)
            .getSingleResult();
        
        return new OrderStatistics(
            (Long) result[0],           // totalOrders
            (BigDecimal) result[1],     // totalRevenue
            (Double) result[2],         // averageOrderValue
            (BigDecimal) result[3],     // minOrderValue
            (BigDecimal) result[4]      // maxOrderValue
        );
    }
}

// Extended repository interface
public interface OrderRepository extends JpaRepository<Order, Long>, CustomOrderRepository {
    // Standard Spring Data JPA methods plus custom methods
}

// Search criteria class
public class OrderSearchCriteria {
    private String customerName;
    private OrderStatus status;
    private LocalDateTime startDate;
    private LocalDateTime endDate;
    private BigDecimal minAmount;
    private BigDecimal maxAmount;
    
    // constructors, getters, setters...
}

// Statistics DTO
public class OrderStatistics {
    private Long totalOrders;
    private BigDecimal totalRevenue;
    private Double averageOrderValue;
    private BigDecimal minOrderValue;
    private BigDecimal maxOrderValue;
    
    // constructors, getters, setters...
}
```

## Configuration and Setup

```java
@Configuration
@EnableJpaRepositories(basePackages = "com.example.repository")
@EnableJpaAuditing
public class JpaConfig {
    
    @Bean
    public AuditorAware<String> auditorProvider() {
        return () -> {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null || !authentication.isAuthenticated()) {
                return Optional.of("system");
            }
            return Optional.of(authentication.getName());
        };
    }
}

// Application properties
spring.datasource.url=jdbc:postgresql://localhost:5432/ecommerce
spring.datasource.username=your_username
spring.datasource.password=your_password
spring.datasource.driver-class-name=org.postgresql.Driver

spring.jpa.hibernate.ddl-auto=validate
spring.jpa.show-sql=true
spring.jpa.format-sql=true
spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect
spring.jpa.properties.hibernate.default_batch_fetch_size=16
spring.jpa.properties.hibernate.jdbc.batch_size=20
spring.jpa.properties.hibernate.order_inserts=true
spring.jpa.properties.hibernate.order_updates=true
```

## Testing Advanced Mappings

```java
@DataJpaTest
class AdvancedMappingTest {
    
    @Autowired
    private TestEntityManager entityManager;
    
    @Autowired
    private OrderRepository orderRepository;
    
    @Test
    void testCompositeKeyMapping() {
        // Given
        Customer customer = new Customer("John", "Doe", "john@example.com");
        entityManager.persistAndFlush(customer);
        
        Product product = new PhysicalProduct();
        product.setName("Test Product");
        product.setPrice(BigDecimal.valueOf(99.99));
        entityManager.persistAndFlush(product);
        
        Order order = new Order();
        order.setCustomer(customer);
        order.setOrderNumber("TEST-001");
        order.setOrderDate(LocalDateTime.now());
        
        OrderItem item = new OrderItem();
        item.setProduct(product);
        item.setQuantity(2);
        item.setUnitPrice(product.getPrice());
        
        order.addItem(item);
        
        // When
        Order savedOrder = orderRepository.save(order);
        entityManager.flush();
        entityManager.clear();
        
        // Then
        Order foundOrder = orderRepository.findById(savedOrder.getId()).orElseThrow();
        assertThat(foundOrder.getItems()).hasSize(1);
        assertThat(foundOrder.getItems().get(0).getId().getOrderId()).isEqualTo(savedOrder.getId());
        assertThat(foundOrder.getItems().get(0).getId().getProductId()).isEqualTo(product.getId());
    }
    
    @Test
    void testInheritanceMapping() {
        // Test physical product
        PhysicalProduct physicalProduct = new PhysicalProduct();
        physicalProduct.setName("Physical Item");
        physicalProduct.setPrice(BigDecimal.valueOf(50.00));
        physicalProduct.setWeight(2.5);
        physicalProduct.setFragile(true);
        
        entityManager.persistAndFlush(physicalProduct);
        
        // Test digital product
        DigitalProduct digitalProduct = new DigitalProduct();
        digitalProduct.setName("Digital Item");
        digitalProduct.setPrice(BigDecimal.valueOf(25.00));
        digitalProduct.setDownloadUrl("https://example.com/download");
        
        entityManager.persistAndFlush(digitalProduct);
        
        // Verify inheritance works
        List<Product> allProducts = entityManager.getEntityManager()
            .createQuery("SELECT p FROM Product p", Product.class)
            .getResultList();
        
        assertThat(allProducts).hasSize(2);
        assertThat(allProducts.stream().anyMatch(p -> p instanceof PhysicalProduct)).isTrue();
        assertThat(allProducts.stream().anyMatch(p -> p instanceof DigitalProduct)).isTrue();
    }
    
    @Test
    void testEmbeddedObjects() {
        Customer customer = new Customer();
        customer.setFirstName("Jane");
        customer.setLastName("Smith");
        customer.setEmail("jane@example.com");
        
        Address billingAddress = new Address("123 Main St", "Springfield", "IL", "62701", "USA");
        Address shippingAddress = new Address("456 Oak Ave", "Springfield", "IL", "62702", "USA");
        
        customer.setBillingAddress(billingAddress);
        customer.setShippingAddress(shippingAddress);
        
        entityManager.persistAndFlush(customer);
        entityManager.clear();
        
        Customer found = entityManager.find(Customer.class, customer.getId());
        assertThat(found.getBillingAddress().getCity()).isEqualTo("Springfield");
        assertThat(found.getShippingAddress().getStreetAddress()).isEqualTo("456 Oak Ave");
    }
}
```

## Common Pitfalls and Best Practices

### 1. N+1 Query Problem Prevention

```java
// BAD: Will cause N+1 queries
@GetMapping("/orders")
public List<OrderDTO> getAllOrders() {
    List<Order> orders = orderRepository.findAll();
    return orders.stream()
        .map(order -> {
            OrderDTO dto = new OrderDTO();
            dto.setCustomerName(order.getCustomer().getFullName()); // N+1 here!
            dto.setItemCount(order.getItems().size()); // Another N+1!
            return dto;
        })
        .collect(Collectors.toList());
}

// GOOD: Use fetch joins
@Query("SELECT DISTINCT o FROM Order o " +
       "LEFT JOIN FETCH o.customer " +
       "LEFT JOIN FETCH o.items")
List<Order> findAllWithCustomerAndItems();
```

### 2. Proper Bidirectional Relationship Management

```java
@Entity
public class Post {
    @OneToMany(mappedBy = "post", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Comment> comments = new ArrayList<>();
    
    // ALWAYS provide helper methods for bidirectional relationships
    public void addComment(Comment comment) {
        comments.add(comment);
        comment.setPost(this);
    }
    
    public void removeComment(Comment comment) {
        comments.remove(comment);
        comment.setPost(null);
    }
}
```

### 3. Cascade and Orphan Removal Guidelines

```java
// Use orphanRemoval = true for dependent entities
@OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
private List<OrderItem> items;

// Be careful with CascadeType.REMOVE on @ManyToOne
@ManyToOne(cascade = {CascadeType.PERSIST, CascadeType.MERGE}) // Don't use REMOVE
@JoinColumn(name = "category_id")
private Category category;
```

## Key Takeaways for Phase 4

1. **Relationship Design**: Always consider the real-world business rules when designing entity relationships
2. **Bidirectional Helpers**: Always provide helper methods to maintain both sides of bidirectional relationships
3. **Fetch Strategy**: Start with LAZY and use EAGER or fetch joins only when necessary
4. **Inheritance Strategy**: Choose the inheritance strategy based on your query patterns and database design preferences
5. **Composite Keys**: Use them when the business domain naturally has composite identifiers
6. **Embedded Objects**: Perfect for value objects that don't have their own identity
7. **Performance**: Always consider the performance implications of your mapping choices

## Next Steps

Before moving to Phase 5 (Query Mastery), make sure you:

1. Practice creating entities with different relationship types
2. Experiment with all three inheritance strategies
3. Create custom repository implementations
4. Test your mappings thoroughly
5. Understand when to use each cascade type
6. Practice with embedded objects and composite keys

The concepts in Phase 4 form the foundation for building complex, real-world applications. Master these patterns, and you'll be able to model virtually any business domain with Spring Data JPA!