# Spring Data JPA Phase 1: Foundation & Prerequisites

## 1.1 Core Java Concepts Review

### Object-Oriented Programming

Understanding OOP is crucial for Spring Data JPA as entities are classes that represent database tables.

```java
// Base entity class demonstrating inheritance
public abstract class BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @CreatedDate
    private LocalDateTime createdAt;
    
    @LastModifiedDate
    private LocalDateTime updatedAt;
    
    // Encapsulation with getters and setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}

// Inheritance example - User entity extending BaseEntity
@Entity
@Table(name = "users")
public class User extends BaseEntity {
    @Column(unique = true, nullable = false)
    private String email;
    
    private String firstName;
    private String lastName;
    
    // Polymorphism - method overriding
    @Override
    public String toString() {
        return "User{" +
                "id=" + getId() +
                ", email='" + email + '\'' +
                ", firstName='" + firstName + '\'' +
                ", lastName='" + lastName + '\'' +
                '}';
    }
    
    // Constructor, getters, setters...
    public User() {}
    
    public User(String email, String firstName, String lastName) {
        this.email = email;
        this.firstName = firstName;
        this.lastName = lastName;
    }
}
```

### Collections Framework

Collections are essential for managing entity relationships and query results.

```java
@Entity
public class Department {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    private String name;
    
    // List - ordered collection, allows duplicates
    @OneToMany(mappedBy = "department", cascade = CascadeType.ALL)
    private List<Employee> employees = new ArrayList<>();
    
    // Set - no duplicates, useful for unique relationships
    @ManyToMany
    @JoinTable(
        name = "department_skills",
        joinColumns = @JoinColumn(name = "department_id"),
        inverseJoinColumns = @JoinColumn(name = "skill_id")
    )
    private Set<Skill> requiredSkills = new HashSet<>();
    
    // Map - key-value pairs, useful for metadata
    @ElementCollection
    @CollectionTable(name = "department_metadata")
    @MapKeyColumn(name = "property_name")
    @Column(name = "property_value")
    private Map<String, String> metadata = new HashMap<>();
    
    // Helper methods for bidirectional relationships
    public void addEmployee(Employee employee) {
        employees.add(employee);
        employee.setDepartment(this);
    }
    
    public void removeEmployee(Employee employee) {
        employees.remove(employee);
        employee.setDepartment(null);
    }
}

// Service class demonstrating collections usage
@Service
public class DepartmentService {
    
    public List<Employee> getEmployeesSortedByName(Department dept) {
        return dept.getEmployees().stream()
            .sorted(Comparator.comparing(Employee::getLastName))
            .collect(Collectors.toList());
    }
    
    public Set<String> getAllSkillNames(Department dept) {
        return dept.getRequiredSkills().stream()
            .map(Skill::getName)
            .collect(Collectors.toSet());
    }
}
```

### Generics

Generics provide type safety in repositories and service classes.

```java
// Generic repository interface
public interface BaseRepository<T, ID> extends JpaRepository<T, ID> {
    
    // Generic method for finding by any field
    <F> Optional<T> findByField(String fieldName, F fieldValue);
    
    // Generic method for batch operations
    <S extends T> List<S> saveAllAndReturn(Iterable<S> entities);
}

// Generic service class
@Service
public abstract class BaseService<T, ID> {
    
    protected final BaseRepository<T, ID> repository;
    
    public BaseService(BaseRepository<T, ID> repository) {
        this.repository = repository;
    }
    
    public Optional<T> findById(ID id) {
        return repository.findById(id);
    }
    
    public T save(T entity) {
        return repository.save(entity);
    }
    
    public List<T> saveAll(List<T> entities) {
        return repository.saveAll(entities);
    }
    
    // Abstract method for subclasses to implement
    public abstract List<T> findBySearchCriteria(String criteria);
}

// Concrete implementation with specific types
@Service
public class UserService extends BaseService<User, Long> {
    
    private final UserRepository userRepository;
    
    public UserService(UserRepository userRepository) {
        super(userRepository);
        this.userRepository = userRepository;
    }
    
    @Override
    public List<User> findBySearchCriteria(String criteria) {
        return userRepository.findByFirstNameContainingOrLastNameContaining(criteria, criteria);
    }
    
    // Wildcards example
    public List<? extends User> findActiveUsers() {
        return userRepository.findByActiveTrue();
    }
    
    // Bounded wildcards
    public <T extends User> void updateUsers(List<T> users) {
        repository.saveAll(users);
    }
}
```

### Annotations

Annotations drive Spring Data JPA's behavior and configuration.

```java
// Custom validation annotation
@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = EmailValidator.class)
public @interface ValidEmail {
    String message() default "Invalid email format";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}

// Validator implementation
public class EmailValidator implements ConstraintValidator<ValidEmail, String> {
    
    private static final String EMAIL_PATTERN = 
        "^[a-zA-Z0-9_+&*-]+(?:\\.[a-zA-Z0-9_+&*-]+)*@" +
        "(?:[a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,7}$";
    
    @Override
    public boolean isValid(String email, ConstraintValidatorContext context) {
        return email != null && email.matches(EMAIL_PATTERN);
    }
}

// Using annotations in entity
@Entity
@Table(name = "customers")
@EntityListeners(AuditingEntityListener.class)
public class Customer {
    
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "customer_seq")
    @SequenceGenerator(name = "customer_seq", sequenceName = "customer_sequence", allocationSize = 1)
    private Long id;
    
    @ValidEmail
    @Column(unique = true, nullable = false)
    private String email;
    
    @NotBlank(message = "First name is required")
    @Size(min = 2, max = 50, message = "First name must be between 2 and 50 characters")
    private String firstName;
    
    @NotBlank(message = "Last name is required")
    @Size(min = 2, max = 50, message = "Last name must be between 2 and 50 characters")  
    private String lastName;
    
    @Past(message = "Birth date must be in the past")
    private LocalDate birthDate;
    
    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime createdAt;
    
    @LastModifiedDate
    private LocalDateTime updatedAt;
    
    @CreatedBy
    @Column(updatable = false)
    private String createdBy;
    
    @LastModifiedBy
    private String lastModifiedBy;
}
```

### Reflection API

Understanding reflection helps in debugging and creating dynamic queries.

```java
@Component
public class EntityInspector {
    
    // Inspect entity annotations
    public void inspectEntity(Class<?> entityClass) {
        System.out.println("Inspecting entity: " + entityClass.getSimpleName());
        
        // Check if class has @Entity annotation
        if (entityClass.isAnnotationPresent(Entity.class)) {
            Entity entityAnnotation = entityClass.getAnnotation(Entity.class);
            System.out.println("Entity name: " + entityAnnotation.name());
        }
        
        // Check for @Table annotation
        if (entityClass.isAnnotationPresent(Table.class)) {
            Table tableAnnotation = entityClass.getAnnotation(Table.class);
            System.out.println("Table name: " + tableAnnotation.name());
        }
        
        // Inspect fields
        Field[] fields = entityClass.getDeclaredFields();
        for (Field field : fields) {
            System.out.println("Field: " + field.getName() + " - Type: " + field.getType().getSimpleName());
            
            if (field.isAnnotationPresent(Id.class)) {
                System.out.println("  -> Primary Key");
            }
            
            if (field.isAnnotationPresent(Column.class)) {
                Column column = field.getAnnotation(Column.class);
                System.out.println("  -> Column: " + column.name() + 
                                 ", Nullable: " + column.nullable() +
                                 ", Unique: " + column.unique());
            }
        }
    }
    
    // Dynamic field access
    public Object getFieldValue(Object entity, String fieldName) {
        try {
            Field field = entity.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            return field.get(entity);
        } catch (Exception e) {
            throw new RuntimeException("Failed to get field value", e);
        }
    }
    
    // Dynamic field setting
    public void setFieldValue(Object entity, String fieldName, Object value) {
        try {
            Field field = entity.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(entity, value);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set field value", e);
        }
    }
}

// Usage example
@Service
public class DynamicQueryService {
    
    private final EntityInspector entityInspector;
    
    public DynamicQueryService(EntityInspector entityInspector) {
        this.entityInspector = entityInspector;
    }
    
    public void analyzeEntity(Object entity) {
        Class<?> entityClass = entity.getClass();
        entityInspector.inspectEntity(entityClass);
        
        // Get all field values dynamically
        Field[] fields = entityClass.getDeclaredFields();
        for (Field field : fields) {
            Object value = entityInspector.getFieldValue(entity, field.getName());
            System.out.println(field.getName() + " = " + value);
        }
    }
}
```

### Lambda Expressions & Stream API

Modern Java features that enhance data processing in JPA applications.

```java
@Service
public class UserAnalyticsService {
    
    private final UserRepository userRepository;
    
    public UserAnalyticsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }
    
    // Lambda expressions with method references
    public List<String> getUserEmails() {
        return userRepository.findAll()
            .stream()
            .map(User::getEmail)  // Method reference
            .collect(Collectors.toList());
    }
    
    // Complex stream operations
    public Map<String, Long> getUserCountByDomain() {
        return userRepository.findAll()
            .stream()
            .filter(user -> user.getEmail() != null)
            .map(user -> user.getEmail().substring(user.getEmail().indexOf('@') + 1))
            .collect(Collectors.groupingBy(
                Function.identity(),
                Collectors.counting()
            ));
    }
    
    // Parallel processing for large datasets
    public List<UserDto> getActiveUsersDto() {
        return userRepository.findByActiveTrue()
            .parallelStream()
            .map(this::convertToDto)
            .sorted(Comparator.comparing(UserDto::getLastName))
            .collect(Collectors.toList());
    }
    
    // Custom collectors
    public String generateUserReport() {
        return userRepository.findAll()
            .stream()
            .collect(Collector.of(
                StringBuilder::new,
                (sb, user) -> sb.append(user.getFirstName())
                               .append(" ")
                               .append(user.getLastName())
                               .append("\n"),
                StringBuilder::append,
                StringBuilder::toString
            ));
    }
    
    // Optional handling with streams
    public Optional<User> findUserWithHighestId() {
        return userRepository.findAll()
            .stream()
            .max(Comparator.comparing(User::getId));
    }
    
    // Functional interface usage
    public List<User> filterUsers(Predicate<User> condition) {
        return userRepository.findAll()
            .stream()
            .filter(condition)
            .collect(Collectors.toList());
    }
    
    private UserDto convertToDto(User user) {
        return UserDto.builder()
            .id(user.getId())
            .email(user.getEmail())
            .firstName(user.getFirstName())
            .lastName(user.getLastName())
            .build();
    }
}

// Usage examples
@RestController
public class UserController {
    
    private final UserAnalyticsService analyticsService;
    
    public UserController(UserAnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }
    
    @GetMapping("/users/analytics/domains")
    public Map<String, Long> getUsersByDomain() {
        return analyticsService.getUserCountByDomain();
    }
    
    @GetMapping("/users/filter")
    public List<User> getFilteredUsers() {
        // Using lambda expressions as predicates
        return analyticsService.filterUsers(
            user -> user.getEmail().endsWith("@company.com") &&
                   user.getFirstName().startsWith("J")
        );
    }
}
```

### Exception Handling

Proper exception handling is crucial for robust JPA applications.

```java
// Custom exceptions
@ResponseStatus(HttpStatus.NOT_FOUND)
public class EntityNotFoundException extends RuntimeException {
    
    public EntityNotFoundException(String entityName, Object id) {
        super(String.format("%s not found with id: %s", entityName, id));
    }
    
    public EntityNotFoundException(String entityName, String field, Object value) {
        super(String.format("%s not found with %s: %s", entityName, field, value));
    }
}

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class InvalidDataException extends RuntimeException {
    
    public InvalidDataException(String message) {
        super(message);
    }
    
    public InvalidDataException(String message, Throwable cause) {
        super(message, cause);
    }
}

// Exception handling in service layer
@Service
@Transactional
public class UserService {
    
    private final UserRepository userRepository;
    private final Logger logger = LoggerFactory.getLogger(UserService.class);
    
    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }
    
    public User findById(Long id) {
        return userRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("User", id));
    }
    
    public User findByEmail(String email) {
        try {
            return userRepository.findByEmail(email)
                .orElseThrow(() -> new EntityNotFoundException("User", "email", email));
        } catch (DataAccessException e) {
            logger.error("Database error while finding user by email: {}", email, e);
            throw new ServiceException("Failed to retrieve user", e);
        }
    }
    
    public User createUser(CreateUserRequest request) {
        try {
            // Validate request
            validateCreateUserRequest(request);
            
            // Check if user already exists
            if (userRepository.existsByEmail(request.getEmail())) {
                throw new InvalidDataException("User with email already exists: " + request.getEmail());
            }
            
            User user = new User(request.getEmail(), request.getFirstName(), request.getLastName());
            return userRepository.save(user);
            
        } catch (DataIntegrityViolationException e) {
            logger.error("Data integrity violation while creating user", e);
            throw new InvalidDataException("Invalid user data", e);
        } catch (Exception e) {
            logger.error("Unexpected error while creating user", e);
            throw new ServiceException("Failed to create user", e);
        }
    }
    
    private void validateCreateUserRequest(CreateUserRequest request) {
        List<String> errors = new ArrayList<>();
        
        if (request.getEmail() == null || request.getEmail().trim().isEmpty()) {
            errors.add("Email is required");
        }
        
        if (request.getFirstName() == null || request.getFirstName().trim().isEmpty()) {
            errors.add("First name is required");
        }
        
        if (request.getLastName() == null || request.getLastName().trim().isEmpty()) {
            errors.add("Last name is required");
        }
        
        if (!errors.isEmpty()) {
            throw new InvalidDataException("Validation failed: " + String.join(", ", errors));
        }
    }
}

// Global exception handler
@ControllerAdvice
public class GlobalExceptionHandler {
    
    private final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    
    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleEntityNotFound(EntityNotFoundException e) {
        ErrorResponse error = new ErrorResponse("ENTITY_NOT_FOUND", e.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }
    
    @ExceptionHandler(InvalidDataException.class)
    public ResponseEntity<ErrorResponse> handleInvalidData(InvalidDataException e) {
        ErrorResponse error = new ErrorResponse("INVALID_DATA", e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }
    
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrityViolation(DataIntegrityViolationException e) {
        logger.error("Data integrity violation", e);
        ErrorResponse error = new ErrorResponse("DATA_INTEGRITY_ERROR", "Data integrity constraint violated");
        return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
    }
    
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException e) {
        String message = e.getConstraintViolations()
            .stream()
            .map(ConstraintViolation::getMessage)
            .collect(Collectors.joining(", "));
        
        ErrorResponse error = new ErrorResponse("VALIDATION_ERROR", message);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }
    
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneral(Exception e) {
        logger.error("Unexpected error", e);
        ErrorResponse error = new ErrorResponse("INTERNAL_ERROR", "An unexpected error occurred");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }
}

// Error response DTO
public class ErrorResponse {
    private String code;
    private String message;
    private LocalDateTime timestamp;
    
    public ErrorResponse(String code, String message) {
        this.code = code;
        this.message = message;
        this.timestamp = LocalDateTime.now();
    }
    
    // Getters and setters
}
```

## Key Takeaways for Phase 1

1. **OOP Principles**: Essential for entity design and relationship mapping
2. **Collections**: Critical for handling entity relationships and query results
3. **Generics**: Provide type safety in repositories and services
4. **Annotations**: Drive JPA behavior and validation
5. **Reflection**: Useful for dynamic operations and debugging
6. **Lambda/Streams**: Modern approach to data processing
7. **Exception Handling**: Crucial for robust application behavior

## Practice Exercises

1. Create a simple entity hierarchy with inheritance
2. Implement a generic service class with type parameters
3. Build custom validation annotations
4. Use streams to process entity collections
5. Implement proper exception handling patterns

## Next Steps

Once you've mastered these foundational concepts with hands-on practice, we'll move to Phase 2: JPA Fundamentals, where we'll dive into:
- JPA Specification Understanding
- Entity Mapping Basics
- Entity Manager and Persistence Context

Would you like me to continue with Phase 2, or do you want to practice these Phase 1 concepts first with specific examples or exercises?