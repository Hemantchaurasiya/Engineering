# Phase 3: Spring Data JPA Core - Complete Mastery Guide

## 3.1 Spring Data JPA Setup

### Project Setup with Dependencies

#### Maven Configuration (pom.xml)
```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0">
    <modelVersion>4.0.0</modelVersion>
    
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.2.0</version>
    </parent>
    
    <groupId>com.example</groupId>
    <artifactId>spring-data-jpa-mastery</artifactId>
    <version>1.0.0</version>
    
    <dependencies>
        <!-- Spring Boot JPA Starter -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-jpa</artifactId>
        </dependency>
        
        <!-- Database Drivers -->
        <dependency>
            <groupId>com.h2database</groupId>
            <artifactId>h2</artifactId>
            <scope>runtime</scope>
        </dependency>
        
        <dependency>
            <groupId>org.postgresql</groupId>
            <artifactId>postgresql</artifactId>
            <scope>runtime</scope>
        </dependency>
        
        <!-- Validation -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
        </dependency>
        
        <!-- Testing -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>
</project>
```

#### Gradle Configuration (build.gradle)
```gradle
plugins {
    id 'org.springframework.boot' version '3.2.0'
    id 'io.spring.dependency-management' version '1.1.4'
    id 'java'
}

dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
    implementation 'org.springframework.boot:spring-boot-starter-validation'
    
    runtimeOnly 'com.h2database:h2'
    runtimeOnly 'org.postgresql:postgresql'
    
    testImplementation 'org.springframework.boot:spring-boot-starter-test'
}
```

### Database Configuration

#### Single DataSource Configuration

**application.yml**
```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/ecommerce_db
    username: ${DB_USERNAME:admin}
    driver-class-name: org.postgresql.Driver
    password: ${DB_PASSWORD:password}
    
    # HikariCP Connection Pool Settings
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
      idle-timeout: 300000
      max-lifetime: 600000
      connection-timeout: 20000
      leak-detection-threshold: 60000
      
  jpa:
    database-platform: org.hibernate.dialect.PostgreSQLDialect
    hibernate:
      ddl-auto: validate  # Use 'create-drop' for development
    show-sql: true
    format-sql: true
    properties:
      hibernate:
        # Performance optimizations
        jdbc.batch_size: 25
        jdbc.batch_versioned_data: true
        order_inserts: true
        order_updates: true
        # Logging
        type.descriptor.sql.BasicBinder: TRACE
        
  # Profile-specific configurations
  profiles:
    active: development
    
---
# Development Profile
spring:
  profiles: development
  datasource:
    url: jdbc:h2:mem:testdb
    driver-class-name: org.h2.Driver
    username: sa
    password: 
  h2:
    console:
      enabled: true
      path: /h2-console
  jpa:
    hibernate:
      ddl-auto: create-drop

---
# Production Profile  
spring:
  profiles: production
  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: false
    properties:
      hibernate:
        generate_statistics: true
        session.events.log.LOG_QUERIES_SLOWER_THAN_MS: 25
```

#### Java-based Configuration

```java
@Configuration
@EnableJpaRepositories(
    basePackages = "com.example.repository",
    entityManagerFactoryRef = "entityManagerFactory",
    transactionManagerRef = "transactionManager"
)
public class JpaConfiguration {
    
    @Bean
    @Primary
    @ConfigurationProperties("spring.datasource.primary")
    public DataSourceProperties primaryDataSourceProperties() {
        return new DataSourceProperties();
    }
    
    @Bean
    @Primary
    public DataSource primaryDataSource() {
        return primaryDataSourceProperties()
            .initializeDataSourceBuilder()
            .build();
    }
    
    @Bean
    @Primary
    public LocalContainerEntityManagerFactoryBean entityManagerFactory(
            @Qualifier("primaryDataSource") DataSource dataSource) {
        
        LocalContainerEntityManagerFactoryBean factory = 
            new LocalContainerEntityManagerFactoryBean();
        
        factory.setDataSource(dataSource);
        factory.setPackagesToScan("com.example.entity");
        factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        
        Properties jpaProperties = new Properties();
        jpaProperties.setProperty("hibernate.hbm2ddl.auto", "validate");
        jpaProperties.setProperty("hibernate.dialect", 
            "org.hibernate.dialect.PostgreSQLDialect");
        jpaProperties.setProperty("hibernate.show_sql", "true");
        jpaProperties.setProperty("hibernate.format_sql", "true");
        
        factory.setJpaProperties(jpaProperties);
        return factory;
    }
    
    @Bean
    @Primary
    public PlatformTransactionManager transactionManager(
            @Qualifier("entityManagerFactory") EntityManagerFactory emf) {
        return new JpaTransactionManager(emf);
    }
}
```

### Multiple DataSources Configuration

```java
@Configuration
@EnableJpaRepositories(
    basePackages = "com.example.repository.primary",
    entityManagerFactoryRef = "primaryEntityManagerFactory",
    transactionManagerRef = "primaryTransactionManager"
)
public class PrimaryDataSourceConfig {
    
    @Bean
    @Primary
    @ConfigurationProperties("spring.datasource.primary")
    public DataSourceProperties primaryDataSourceProperties() {
        return new DataSourceProperties();
    }
    
    @Bean
    @Primary
    @ConfigurationProperties("spring.datasource.primary.hikari")
    public HikariDataSource primaryDataSource() {
        return primaryDataSourceProperties()
                .initializeDataSourceBuilder()
                .type(HikariDataSource.class)
                .build();
    }
    
    @Bean
    @Primary
    public LocalContainerEntityManagerFactoryBean primaryEntityManagerFactory(
            @Qualifier("primaryDataSource") DataSource dataSource) {
        return createEntityManagerFactory(dataSource, "com.example.entity.primary");
    }
    
    @Bean
    @Primary
    public PlatformTransactionManager primaryTransactionManager(
            @Qualifier("primaryEntityManagerFactory") EntityManagerFactory emf) {
        return new JpaTransactionManager(emf);
    }
}

@Configuration
@EnableJpaRepositories(
    basePackages = "com.example.repository.secondary",
    entityManagerFactoryRef = "secondaryEntityManagerFactory",
    transactionManagerRef = "secondaryTransactionManager"
)
public class SecondaryDataSourceConfig {
    
    @Bean
    @ConfigurationProperties("spring.datasource.secondary")
    public DataSourceProperties secondaryDataSourceProperties() {
        return new DataSourceProperties();
    }
    
    @Bean
    @ConfigurationProperties("spring.datasource.secondary.hikari")
    public HikariDataSource secondaryDataSource() {
        return secondaryDataSourceProperties()
                .initializeDataSourceBuilder()
                .type(HikariDataSource.class)
                .build();
    }
    
    @Bean
    public LocalContainerEntityManagerFactoryBean secondaryEntityManagerFactory(
            @Qualifier("secondaryDataSource") DataSource dataSource) {
        return createEntityManagerFactory(dataSource, "com.example.entity.secondary");
    }
    
    @Bean
    public PlatformTransactionManager secondaryTransactionManager(
            @Qualifier("secondaryEntityManagerFactory") EntityManagerFactory emf) {
        return new JpaTransactionManager(emf);
    }
}
```

## 3.2 Repository Pattern Deep Dive

### Repository Interface Hierarchy

Spring Data JPA provides a hierarchy of repository interfaces, each adding more functionality:

```java
// Base Repository - marker interface
public interface Repository<T, ID> {}

// CRUD Repository - basic CRUD operations
public interface CrudRepository<T, ID> extends Repository<T, ID> {
    <S extends T> S save(S entity);
    <S extends T> Iterable<S> saveAll(Iterable<S> entities);
    Optional<T> findById(ID id);
    boolean existsById(ID id);
    Iterable<T> findAll();
    Iterable<T> findAllById(Iterable<ID> ids);
    long count();
    void deleteById(ID id);
    void delete(T entity);
    void deleteAllById(Iterable<? extends ID> ids);
    void deleteAll(Iterable<? extends T> entities);
    void deleteAll();
}

// Paging and Sorting Repository
public interface PagingAndSortingRepository<T, ID> extends Repository<T, ID> {
    Iterable<T> findAll(Sort sort);
    Page<T> findAll(Pageable pageable);
}

// JPA Repository - combines all + adds JPA-specific methods
public interface JpaRepository<T, ID> extends 
    PagingAndSortingRepository<T, ID>, CrudRepository<T, ID> {
    
    List<T> findAll();
    List<T> findAll(Sort sort);
    List<T> findAllById(Iterable<ID> ids);
    <S extends T> List<S> saveAll(Iterable<S> entities);
    void flush();
    <S extends T> S saveAndFlush(S entity);
    <S extends T> List<S> saveAllAndFlush(Iterable<S> entities);
    void deleteAllInBatch(Iterable<T> entities);
    void deleteAllByIdInBatch(Iterable<ID> ids);
    void deleteAllInBatch();
    T getOne(ID id);  // Deprecated, use getReferenceById
    T getReferenceById(ID id);
}
```

### Real-World Entity Examples

```java
@Entity
@Table(name = "users")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false, unique = true)
    private String email;
    
    @Column(nullable = false)
    private String firstName;
    
    @Column(nullable = false)  
    private String lastName;
    
    @Column(name = "phone_number")
    private String phoneNumber;
    
    @Enumerated(EnumType.STRING)
    private UserStatus status = UserStatus.ACTIVE;
    
    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
    
    @UpdateTimestamp  
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    // Relationships
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Order> orders = new ArrayList<>();
    
    @OneToOne(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private UserProfile profile;
    
    // Constructors, getters, setters, equals, hashCode
    public User() {}
    
    public User(String email, String firstName, String lastName) {
        this.email = email;
        this.firstName = firstName;
        this.lastName = lastName;
    }
    
    // ... getters and setters
}

@Entity
@Table(name = "products")
public class Product {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false)
    private String name;
    
    @Column(length = 1000)
    private String description;
    
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;
    
    @Column(name = "stock_quantity")
    private Integer stockQuantity = 0;
    
    @Column(name = "is_active")
    private Boolean isActive = true;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;
    
    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
    
    // Constructors, getters, setters
}

@Entity
@Table(name = "orders")
public class Order {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "order_status")
    private OrderStatus status = OrderStatus.PENDING;
    
    @Column(name = "total_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalAmount;
    
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<OrderItem> orderItems = new ArrayList<>();
    
    @CreationTimestamp
    @Column(name = "order_date", updatable = false)
    private LocalDateTime orderDate;
    
    // Constructors, getters, setters
}

// Enums
public enum UserStatus {
    ACTIVE, INACTIVE, SUSPENDED, DELETED
}

public enum OrderStatus {
    PENDING, CONFIRMED, SHIPPED, DELIVERED, CANCELLED
}
```

### Custom Repository Interfaces

```java
// Custom repository interface
public interface CustomUserRepository {
    List<User> findActiveUsersWithOrders();
    List<User> findUsersByRegistrationDateRange(LocalDateTime start, LocalDateTime end);
    UserStatistics getUserStatistics(Long userId);
}

// Implementation
@Repository
@Transactional
public class CustomUserRepositoryImpl implements CustomUserRepository {
    
    @PersistenceContext
    private EntityManager entityManager;
    
    @Override
    @Transactional(readOnly = true)
    public List<User> findActiveUsersWithOrders() {
        String jpql = """
            SELECT DISTINCT u FROM User u 
            LEFT JOIN FETCH u.orders o 
            WHERE u.status = :status 
            AND u.orders IS NOT EMPTY
            ORDER BY u.createdAt DESC
            """;
            
        return entityManager.createQuery(jpql, User.class)
                .setParameter("status", UserStatus.ACTIVE)
                .getResultList();
    }
    
    @Override
    @Transactional(readOnly = true)
    public List<User> findUsersByRegistrationDateRange(LocalDateTime start, LocalDateTime end) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<User> query = cb.createQuery(User.class);
        Root<User> root = query.from(User.class);
        
        Predicate datePredicate = cb.between(root.get("createdAt"), start, end);
        query.select(root).where(datePredicate).orderBy(cb.desc(root.get("createdAt")));
        
        return entityManager.createQuery(query).getResultList();
    }
    
    @Override
    @Transactional(readOnly = true)
    public UserStatistics getUserStatistics(Long userId) {
        String jpql = """
            SELECT new com.example.dto.UserStatistics(
                u.id,
                u.firstName,
                u.lastName,
                COUNT(o.id),
                COALESCE(SUM(o.totalAmount), 0),
                COALESCE(AVG(o.totalAmount), 0)
            )
            FROM User u
            LEFT JOIN u.orders o
            WHERE u.id = :userId
            GROUP BY u.id, u.firstName, u.lastName
            """;
            
        return entityManager.createQuery(jpql, UserStatistics.class)
                .setParameter("userId", userId)
                .getSingleResult();
    }
}

// DTO for statistics
public record UserStatistics(
    Long userId,
    String firstName,
    String lastName,
    Long totalOrders,
    BigDecimal totalSpent,
    BigDecimal averageOrderValue
) {}
```

## 3.3 Basic CRUD Operations in Detail

### Repository Interface Examples

```java
@Repository
public interface UserRepository extends JpaRepository<User, Long>, CustomUserRepository {
    // Spring Data JPA provides all basic CRUD operations automatically
    
    // Additional query methods (covered in next section)
    Optional<User> findByEmail(String email);
    List<User> findByStatus(UserStatus status);
    boolean existsByEmail(String email);
    long countByStatus(UserStatus status);
}

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {
    List<Product> findByIsActiveTrue();
    List<Product> findByCategoryId(Long categoryId);
    
    @Query("SELECT p FROM Product p WHERE p.stockQuantity > 0 AND p.isActive = true")
    List<Product> findAvailableProducts();
}
```

### CRUD Operations with Service Layer

```java
@Service
@Transactional
public class UserService {
    
    private final UserRepository userRepository;
    
    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }
    
    // CREATE operations
    public User createUser(CreateUserRequest request) {
        // Validate email uniqueness
        if (userRepository.existsByEmail(request.email())) {
            throw new UserAlreadyExistsException("Email already exists: " + request.email());
        }
        
        User user = new User(request.email(), request.firstName(), request.lastName());
        user.setPhoneNumber(request.phoneNumber());
        
        return userRepository.save(user);
    }
    
    public List<User> createUsers(List<CreateUserRequest> requests) {
        List<User> users = requests.stream()
                .map(request -> new User(request.email(), request.firstName(), request.lastName()))
                .collect(Collectors.toList());
                
        return userRepository.saveAll(users);
    }
    
    // READ operations
    @Transactional(readOnly = true)
    public User getUserById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException("User not found with id: " + id));
    }
    
    @Transactional(readOnly = true)
    public User getUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("User not found with email: " + email));
    }
    
    @Transactional(readOnly = true)
    public List<User> getAllActiveUsers() {
        return userRepository.findByStatus(UserStatus.ACTIVE);
    }
    
    @Transactional(readOnly = true)
    public boolean userExists(Long id) {
        return userRepository.existsById(id);
    }
    
    @Transactional(readOnly = true)
    public long getTotalActiveUsers() {
        return userRepository.countByStatus(UserStatus.ACTIVE);
    }
    
    // UPDATE operations
    public User updateUser(Long id, UpdateUserRequest request) {
        User user = getUserById(id);
        
        // Update fields if provided
        if (request.firstName() != null) {
            user.setFirstName(request.firstName());
        }
        if (request.lastName() != null) {
            user.setLastName(request.lastName());
        }
        if (request.phoneNumber() != null) {
            user.setPhoneNumber(request.phoneNumber());
        }
        
        return userRepository.save(user); // save() handles both insert and update
    }
    
    public User saveAndFlushUser(User user) {
        // Immediately flushes changes to database
        return userRepository.saveAndFlush(user);
    }
    
    // DELETE operations
    public void deleteUser(Long id) {
        if (!userRepository.existsById(id)) {
            throw new UserNotFoundException("User not found with id: " + id);
        }
        userRepository.deleteById(id);
    }
    
    public void softDeleteUser(Long id) {
        User user = getUserById(id);
        user.setStatus(UserStatus.DELETED);
        userRepository.save(user);
    }
    
    public void deleteUsers(List<Long> ids) {
        userRepository.deleteAllById(ids);
    }
    
    // Batch operations
    public void deleteAllInactiveUsers() {
        List<User> inactiveUsers = userRepository.findByStatus(UserStatus.INACTIVE);
        userRepository.deleteAllInBatch(inactiveUsers);
    }
}
```

### Batch Operations Performance Examples

```java
@Service
@Transactional
public class ProductService {
    
    private final ProductRepository productRepository;
    
    // Efficient batch operations
    @Transactional
    public List<Product> createProductsBatch(List<CreateProductRequest> requests) {
        List<Product> products = requests.stream()
                .map(this::convertToProduct)
                .collect(Collectors.toList());
        
        // saveAll() is optimized for batch operations
        return productRepository.saveAll(products);
    }
    
    @Transactional
    public void updateProductPricesBatch(List<ProductPriceUpdate> updates) {
        List<Long> productIds = updates.stream()
                .map(ProductPriceUpdate::productId)
                .collect(Collectors.toList());
        
        // Fetch all products in one query
        List<Product> products = productRepository.findAllById(productIds);
        
        // Create a map for quick lookup
        Map<Long, BigDecimal> priceUpdateMap = updates.stream()
                .collect(Collectors.toMap(
                    ProductPriceUpdate::productId,
                    ProductPriceUpdate::newPrice
                ));
        
        // Update prices
        products.forEach(product -> {
            BigDecimal newPrice = priceUpdateMap.get(product.getId());
            if (newPrice != null) {
                product.setPrice(newPrice);
            }
        });
        
        // Batch save
        productRepository.saveAll(products);
    }
    
    @Transactional
    public void deactivateProductsBatch(List<Long> productIds) {
        // More efficient than individual deletes
        productRepository.deleteAllByIdInBatch(productIds);
        
        // Or for soft delete:
        // List<Product> products = productRepository.findAllById(productIds);
        // products.forEach(product -> product.setIsActive(false));
        // productRepository.saveAll(products);
    }
    
    private Product convertToProduct(CreateProductRequest request) {
        Product product = new Product();
        product.setName(request.name());
        product.setDescription(request.description());
        product.setPrice(request.price());
        product.setStockQuantity(request.stockQuantity());
        return product;
    }
}
```

## 3.4 Query Methods Mastery

### Method Name Query Keywords

Spring Data JPA supports a rich set of keywords for building queries from method names:

```java
@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    
    // Equality
    List<User> findByFirstName(String firstName);
    List<User> findByFirstNameAndLastName(String firstName, String lastName);
    
    // Like / Contains
    List<User> findByFirstNameLike(String pattern); // Requires % wildcards
    List<User> findByFirstNameContaining(String name); // Automatic %name% wrapping
    List<User> findByFirstNameStartingWith(String prefix);
    List<User> findByFirstNameEndingWith(String suffix);
    
    // Case insensitive
    List<User> findByFirstNameIgnoreCase(String firstName);
    List<User> findByFirstNameContainingIgnoreCase(String name);
    
    // Null checks
    List<User> findByPhoneNumberIsNull();
    List<User> findByPhoneNumberIsNotNull();
    
    // Boolean
    List<User> findByStatusIs(UserStatus status);
    List<User> findByStatusEquals(UserStatus status); // Same as above
    List<User> findByStatusNot(UserStatus status);
    
    // Numerical comparisons
    List<User> findByIdLessThan(Long id);
    List<User> findByIdLessThanEqual(Long id);
    List<User> findByIdGreaterThan(Long id);
    List<User> findByIdGreaterThanEqual(Long id);
    List<User> findByIdBetween(Long start, Long end);
    
    // Collections
    List<User> findByStatusIn(Collection<UserStatus> statuses);
    List<User> findByStatusNotIn(Collection<UserStatus> statuses);
    
    // Date/Time
    List<User> findByCreatedAtAfter(LocalDateTime date);
    List<User> findByCreatedAtBefore(LocalDateTime date);
    List<User> findByCreatedAtBetween(LocalDateTime start, LocalDateTime end);
    
    // Ordering
    List<User> findByStatusOrderByCreatedAtDesc(UserStatus status);
    List<User> findByFirstNameOrderByLastNameAscCreatedAtDesc(String firstName);
    
    // Limiting results
    User findFirstByOrderByCreatedAtDesc();
    Optional<User> findTopByEmailOrderByCreatedAtDesc(String email);
    List<User> findTop5ByStatusOrderByCreatedAtDesc(UserStatus status);
    List<User> findFirst10ByOrderByCreatedAtDesc();
    
    // Existence
    boolean existsByEmail(String email);
    boolean existsByEmailAndStatus(String email, UserStatus status);
    
    // Counting
    long countByStatus(UserStatus status);
    long countByCreatedAtBetween(LocalDateTime start, LocalDateTime end);
    
    // Deleting
    void deleteByStatus(UserStatus status);
    long deleteByCreatedAtBefore(LocalDateTime date);
    List<User> removeByStatus(UserStatus status); // Same as delete but returns deleted entities
}
```

### Advanced Property Expression Examples

```java
@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {
    
    // Nested property access
    List<Order> findByUserEmail(String email);
    List<Order> findByUserFirstName(String firstName);
    List<Order> findByUserStatus(UserStatus status);
    
    // Deep nested properties
    List<Order> findByUserProfileAddress_City(String city);
    List<Order> findByUserProfileAddress_Country(String country);
    
    // Collection properties
    List<Order> findByOrderItemsProduct_Name(String productName);
    List<Order> findByOrderItemsProduct_Category_Name(String categoryName);
    
    // Complex combinations
    List<Order> findByUserEmailAndStatusAndTotalAmountGreaterThan(
        String email, OrderStatus status, BigDecimal amount);
    
    List<Order> findByUserStatusAndOrderDateBetweenOrderByTotalAmountDesc(
        UserStatus userStatus, LocalDateTime start, LocalDateTime end);
    
    // Using OR conditions
    List<Order> findByStatusOrTotalAmountGreaterThan(OrderStatus status, BigDecimal amount);
    
    // Complex boolean logic (requires careful method naming)
    List<Order> findByUserStatusAndStatusInAndTotalAmountBetween(
        UserStatus userStatus, 
        Collection<OrderStatus> orderStatuses,
        BigDecimal minAmount, 
        BigDecimal maxAmount
    );
}

@Repository  
public interface ProductRepository extends JpaRepository<Product, Long> {
    
    // Category relationship queries
    List<Product> findByCategoryName(String categoryName);
    List<Product> findByCategoryNameIgnoreCase(String categoryName);
    
    // Stock and pricing queries
    List<Product> findByStockQuantityGreaterThanAndIsActiveTrue(Integer minStock);
    List<Product> findByPriceBetweenAndIsActiveTrue(BigDecimal minPrice, BigDecimal maxPrice);
    
    // Text search in multiple fields
    @Query("SELECT p FROM Product p WHERE " +
           "LOWER(p.name) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
           "LOWER(p.description) LIKE LOWER(CONCAT('%', :searchTerm, '%'))")
    List<Product> searchByNameOrDescription(@Param("searchTerm") String searchTerm);
    
    // Using method names for complex queries
    List<Product> findByNameContainingIgnoreCaseOrDescriptionContainingIgnoreCase(
        String nameSearch, String descriptionSearch);
}
```

### Real-World Query Method Examples

```java
@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    
    // User authentication
    Optional<User> findByEmailAndStatus(String email, UserStatus status);
    
    // Admin queries
    List<User> findByStatusAndCreatedAtBetween(
        UserStatus status, LocalDateTime start, LocalDateTime end);
    
    // Analytics queries  
    long countByStatusAndCreatedAtAfter(UserStatus status, LocalDateTime date);
    
    // Bulk operations
    @Modifying
    @Query("UPDATE User u SET u.status = :newStatus WHERE u.status = :oldStatus " +
           "AND u.createdAt < :cutoffDate")
    int updateUserStatusByDateCutoff(@Param("newStatus") UserStatus newStatus,
                                   @Param("oldStatus") UserStatus oldStatus,
                                   @Param("cutoffDate") LocalDateTime cutoffDate);
    
    // Complex business queries
    @Query("SELECT u FROM User u WHERE u.id IN " +
           "(SELECT DISTINCT o.user.id FROM Order o WHERE o.orderDate >= :date)")
    List<User> findUsersWithOrdersSince(@Param("date") LocalDateTime date);
    
    // Projection queries (covered in detail later)
    @Query("SELECT new com.example.dto.UserSummary(u.id, u.email, u.firstName, u.lastName) " +
           "FROM User u WHERE u.status = :status")
    List<UserSummary> findUserSummariesByStatus(@Param("status") UserStatus status);
}

@Service
@Transactional(readOnly = true)
public class UserQueryService {
    
    private final UserRepository userRepository;
    
    public UserQueryService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }
    
    // Business-specific queries using method names
    public List<User> getActiveUsersRegisteredThisMonth() {
        LocalDateTime startOfMonth = LocalDateTime.now().withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0);
        LocalDateTime now = LocalDateTime.now();
        
        return userRepository.findByStatusAndCreatedAtBetween(
            UserStatus.ACTIVE, startOfMonth, now);
    }
    
    public List<User> searchActiveUsersByName(String namePattern) {
        return userRepository.findByStatusAndFirstNameContainingIgnoreCaseOrLastNameContainingIgnoreCase(
            UserStatus.ACTIVE, namePattern, namePattern);
    }
    
    public boolean isEmailAvailable(String email) {
        return !userRepository.existsByEmailAndStatusNot(email, UserStatus.DELETED);
    }
    
    public long getActiveUserCount() {
        return userRepository.countByStatus(UserStatus.ACTIVE);
    }
    
    public List<User> getRecentlyRegisteredUsers(int days) {
        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(days);
        return userRepository.findByCreatedAtAfterOrderByCreatedAtDesc(cutoffDate);
    }
}
```

## 3.5 Custom Queries with @Query Annotation

### JPQL Queries

```java
@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {
    
    // Basic JPQL queries
    @Query("SELECT o FROM Order o WHERE o.user.email = :email")
    List<Order> findOrdersByUserEmail(@Param("email") String email);
    
    @Query("SELECT o FROM Order o WHERE o.status = :status AND o.totalAmount > :amount")
    List<Order> findOrdersByStatusAndAmountGreaterThan(
        @Param("status") OrderStatus status,
        @Param("amount") BigDecimal amount);
    
    // JOIN queries
    @Query("SELECT o FROM Order o " +
           "JOIN FETCH o.user u " +
           "WHERE u.status = :userStatus " +
           "AND o.orderDate >= :fromDate")
    List<Order> findOrdersWithUserByDateAndUserStatus(
        @Param("userStatus") UserStatus userStatus,
        @Param("fromDate") LocalDateTime fromDate);
    
    // Aggregate queries
    @Query("SELECT COUNT(o) FROM Order o WHERE o.user.id = :userId")
    long countOrdersByUserId(@Param("userId") Long userId);
    
    @Query("SELECT SUM(o.totalAmount) FROM Order o WHERE o.user.id = :userId")
    BigDecimal getTotalSpentByUser(@Param("userId") Long userId);
    
    @Query("SELECT AVG(o.totalAmount) FROM Order o WHERE o.status = :status")
    BigDecimal getAverageOrderAmountByStatus(@Param("status") OrderStatus status);
    
    // Complex business queries
    @Query("SELECT o FROM Order o WHERE o.user.id = :userId " +
           "AND o.orderDate BETWEEN :startDate AND :endDate " +
           "ORDER BY o.orderDate DESC")
    List<Order> findUserOrdersInDateRange(
        @Param("userId") Long userId,
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate);
    
    // Subqueries
    @Query("SELECT o FROM Order o WHERE o.user.id IN " +
           "(SELECT u.id FROM User u WHERE u.email LIKE %:emailDomain%)")
    List<Order> findOrdersByEmailDomain(@Param("emailDomain") String emailDomain);
    
    // Using constructor expressions for DTOs
    @Query("SELECT new com.example.dto.OrderSummary(" +
           "o.id, o.user.email, o.status, o.totalAmount, o.orderDate) " +
           "FROM Order o WHERE o.user.id = :userId")
    List<OrderSummary> getOrderSummariesByUser(@Param("userId") Long userId);
    
    // Conditional WHERE clauses using CASE WHEN
    @Query("SELECT o FROM Order o WHERE " +
           "(:status IS NULL OR o.status = :status) AND " +
           "(:minAmount IS NULL OR o.totalAmount >= :minAmount) AND " +
           "(:maxAmount IS NULL OR o.totalAmount <= :maxAmount)")
    List<Order> findOrdersWithOptionalFilters(
        @Param("status") OrderStatus status,
        @Param("minAmount") BigDecimal minAmount,
        @Param("maxAmount") BigDecimal maxAmount);
}

// DTO for projections
public record OrderSummary(
    Long id,
    String userEmail,
    OrderStatus status,
    BigDecimal totalAmount,
    LocalDateTime orderDate
) {}
```

### Native SQL Queries

```java
@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {
    
    // Simple native query
    @Query(value = "SELECT * FROM products WHERE stock_quantity > ?1", nativeQuery = true)
    List<Product> findProductsWithStockGreaterThan(Integer minStock);
    
    // Named parameters in native queries
    @Query(value = "SELECT * FROM products p " +
                   "WHERE p.price BETWEEN :minPrice AND :maxPrice " +
                   "AND p.is_active = true " +
                   "ORDER BY p.price ASC",
           nativeQuery = true)
    List<Product> findProductsByPriceRange(
        @Param("minPrice") BigDecimal minPrice,
        @Param("maxPrice") BigDecimal maxPrice);
    
    // Complex native queries with joins
    @Query(value = """
            SELECT p.*, c.name as category_name
            FROM products p
            INNER JOIN categories c ON p.category_id = c.id
            WHERE c.name = :categoryName
            AND p.stock_quantity > 0
            AND p.is_active = true
            ORDER BY p.created_at DESC
            """, nativeQuery = true)
    List<Object[]> findActiveProductsWithCategoryName(@Param("categoryName") String categoryName);
    
    // Aggregate native queries
    @Query(value = """
            SELECT 
                c.name as category_name,
                COUNT(p.id) as product_count,
                AVG(p.price) as average_price,
                SUM(p.stock_quantity) as total_stock
            FROM products p
            INNER JOIN categories c ON p.category_id = c.id
            WHERE p.is_active = true
            GROUP BY c.id, c.name
            ORDER BY product_count DESC
            """, nativeQuery = true)
    List<Object[]> getCategoryStatistics();
    
    // Database-specific features (PostgreSQL example)
    @Query(value = """
            SELECT p.*
            FROM products p
            WHERE to_tsvector('english', p.name || ' ' || COALESCE(p.description, ''))
            @@ plainto_tsquery('english', :searchTerm)
            AND p.is_active = true
            ORDER BY ts_rank(
                to_tsvector('english', p.name || ' ' || COALESCE(p.description, '')),
                plainto_tsquery('english', :searchTerm)
            ) DESC
            """, nativeQuery = true)
    List<Product> fullTextSearch(@Param("searchTerm") String searchTerm);
    
    // Window functions (PostgreSQL)
    @Query(value = """
            SELECT *,
                   ROW_NUMBER() OVER (PARTITION BY category_id ORDER BY price DESC) as price_rank
            FROM products
            WHERE is_active = true
            """, nativeQuery = true)
    List<Object[]> getProductsWithPriceRankByCategory();
}
```

### Modifying Queries

```java
@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    
    // Update queries
    @Modifying
    @Query("UPDATE User u SET u.status = :newStatus WHERE u.status = :oldStatus")
    int updateUserStatus(@Param("oldStatus") UserStatus oldStatus,
                        @Param("newStatus") UserStatus newStatus);
    
    @Modifying
    @Query("UPDATE User u SET u.firstName = :firstName, u.lastName = :lastName " +
           "WHERE u.id = :userId")
    int updateUserName(@Param("userId") Long userId,
                      @Param("firstName") String firstName,
                      @Param("lastName") String lastName);
    
    // Conditional updates
    @Modifying
    @Query("UPDATE User u SET u.status = :status " +
           "WHERE u.createdAt < :cutoffDate AND u.status = :currentStatus")
    int deactivateOldUsers(@Param("cutoffDate") LocalDateTime cutoffDate,
                          @Param("currentStatus") UserStatus currentStatus,
                          @Param("status") UserStatus status);
    
    // Delete queries
    @Modifying
    @Query("DELETE FROM User u WHERE u.status = :status AND u.createdAt < :cutoffDate")
    int deleteUsersByStatusAndDate(@Param("status") UserStatus status,
                                  @Param("cutoffDate") LocalDateTime cutoffDate);
    
    // Native update queries
    @Modifying
    @Query(value = "UPDATE users SET phone_number = :phoneNumber " +
                   "WHERE id = :userId", nativeQuery = true)
    int updateUserPhoneNumber(@Param("userId") Long userId,
                             @Param("phoneNumber") String phoneNumber);
    
    // Bulk operations with native queries
    @Modifying
    @Query(value = """
            UPDATE products 
            SET price = price * :multiplier 
            WHERE category_id = :categoryId 
            AND is_active = true
            """, nativeQuery = true)
    int updateProductPricesByCategory(@Param("categoryId") Long categoryId,
                                     @Param("multiplier") BigDecimal multiplier);
}
```

### Advanced Query Techniques

```java
@Service
@Transactional
public class AdvancedQueryService {
    
    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    
    // Constructor injection
    public AdvancedQueryService(OrderRepository orderRepository,
                              UserRepository userRepository,
                              ProductRepository productRepository) {
        this.orderRepository = orderRepository;
        this.userRepository = userRepository;
        this.productRepository = productRepository;
    }
    
    // Dynamic queries with optional parameters
    public List<Order> findOrdersWithFilters(OrderSearchCriteria criteria) {
        return orderRepository.findOrdersWithOptionalFilters(
            criteria.status(),
            criteria.minAmount(),
            criteria.maxAmount()
        );
    }
    
    // Combining multiple repository operations
    @Transactional
    public OrderAnalytics getOrderAnalytics(Long userId, LocalDateTime fromDate, LocalDateTime toDate) {
        long totalOrders = orderRepository.countOrdersByUserId(userId);
        BigDecimal totalSpent = orderRepository.getTotalSpentByUser(userId);
        List<OrderSummary> recentOrders = orderRepository.getOrderSummariesByUser(userId);
        
        return new OrderAnalytics(totalOrders, totalSpent, recentOrders);
    }
    
    // Batch updates with @Modifying
    @Transactional
    public BatchUpdateResult updateUserStatuses(UserStatusUpdate update) {
        int updatedCount = userRepository.updateUserStatus(update.oldStatus(), update.newStatus());
        
        return new BatchUpdateResult(updatedCount, "User statuses updated successfully");
    }
    
    // Using native queries for complex operations
    @Transactional(readOnly = true)
    public List<CategoryStatistics> getCategoryAnalytics() {
        List<Object[]> results = productRepository.getCategoryStatistics();
        
        return results.stream()
            .map(row -> new CategoryStatistics(
                (String) row[0],  // category_name
                ((Number) row[1]).longValue(),  // product_count
                (BigDecimal) row[2],  // average_price
                ((Number) row[3]).intValue()   // total_stock
            ))
            .collect(Collectors.toList());
    }
    
    // Full-text search implementation
    @Transactional(readOnly = true)
    public List<Product> searchProducts(String searchTerm) {
        if (searchTerm == null || searchTerm.trim().isEmpty()) {
            return Collections.emptyList();
        }
        
        // Use database-specific full-text search if available
        return productRepository.fullTextSearch(searchTerm.trim());
    }
}

// DTOs for advanced operations
public record OrderSearchCriteria(
    OrderStatus status,
    BigDecimal minAmount,
    BigDecimal maxAmount
) {}

public record OrderAnalytics(
    long totalOrders,
    BigDecimal totalSpent,
    List<OrderSummary> recentOrders
) {}

public record UserStatusUpdate(
    UserStatus oldStatus,
    UserStatus newStatus
) {}

public record BatchUpdateResult(
    int updatedCount,
    String message
) {}

public record CategoryStatistics(
    String categoryName,
    long productCount,
    BigDecimal averagePrice,
    int totalStock
) {}
```

### SpEL (Spring Expression Language) in Queries

```java
@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    
    // Using SpEL for dynamic table names (advanced use case)
    @Query("SELECT u FROM #{#entityName} u WHERE u.status = :status")
    List<User> findByStatusUsingSpEL(@Param("status") UserStatus status);
    
    // SpEL with method parameters
    @Query("SELECT u FROM User u WHERE u.email = ?#{[0]} AND u.status = ?#{[1]}")
    Optional<User> findByEmailAndStatusSpEL(String email, UserStatus status);
    
    // SpEL with security context (when Spring Security is integrated)
    @Query("SELECT u FROM User u WHERE u.createdBy = ?#{authentication.name}")
    List<User> findUsersByCurrentUser();
    
    // SpEL for conditional queries
    @Query("SELECT u FROM User u WHERE " +
           "(:#{#criteria.email} IS NULL OR u.email = :#{#criteria.email}) AND " +
           "(:#{#criteria.status} IS NULL OR u.status = :#{#criteria.status})")
    List<User> findUsersWithSpELCriteria(@Param("criteria") UserSearchCriteria criteria);
}

public record UserSearchCriteria(
    String email,
    UserStatus status
) {}
```

### Query Performance Tips and Best Practices

```java
@Service
@Transactional(readOnly = true)
public class OptimizedQueryService {
    
    private final UserRepository userRepository;
    private final EntityManager entityManager;
    
    public OptimizedQueryService(UserRepository userRepository, EntityManager entityManager) {
        this.userRepository = userRepository;
        this.entityManager = entityManager;
    }
    
    // Use projections to avoid loading unnecessary data
    @Query("SELECT new com.example.dto.UserProjection(u.id, u.email, u.firstName) " +
           "FROM User u WHERE u.status = :status")
    List<UserProjection> findUserProjections(@Param("status") UserStatus status);
    
    // Fetch joins to avoid N+1 problem
    @Query("SELECT DISTINCT u FROM User u " +
           "LEFT JOIN FETCH u.orders o " +
           "WHERE u.status = :status")
    List<User> findUsersWithOrdersEfficently(@Param("status") UserStatus status);
    
    // Batch fetching with size hints
    @QueryHints({
        @QueryHint(name = "org.hibernate.fetchSize", value = "50"),
        @QueryHint(name = "org.hibernate.readOnly", value = "true")
    })
    @Query("SELECT u FROM User u WHERE u.createdAt >= :date")
    Stream<User> findUsersAsStream(@Param("date") LocalDateTime date);
    
    // Using pagination for large datasets
    @Query("SELECT u FROM User u WHERE u.status = :status ORDER BY u.createdAt DESC")
    Page<User> findUsersPaginated(@Param("status") UserStatus status, Pageable pageable);
    
    // Example of using the Stream for large datasets
    public void processLargeUserDataset(LocalDateTime fromDate) {
        try (Stream<User> userStream = findUsersAsStream(fromDate)) {
            userStream
                .filter(user -> user.getEmail().endsWith("@company.com"))
                .forEach(this::processUser);
        }
    }
    
    private void processUser(User user) {
        // Process individual user
        System.out.println("Processing user: " + user.getEmail());
    }
}

public record UserProjection(
    Long id,
    String email,
    String firstName
) {}
```

## Real-World Use Case: E-commerce Order Management

Let's put everything together with a comprehensive real-world example:

```java
@RestController
@RequestMapping("/api/orders")
public class OrderController {
    
    private final OrderService orderService;
    
    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }
    
    @GetMapping("/user/{userId}")
    public ResponseEntity<Page<OrderSummary>> getUserOrders(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) OrderStatus status) {
        
        Pageable pageable = PageRequest.of(page, size, Sort.by("orderDate").descending());
        Page<OrderSummary> orders = orderService.getUserOrders(userId, status, pageable);
        
        return ResponseEntity.ok(orders);
    }
    
    @GetMapping("/search")
    public ResponseEntity<List<Order>> searchOrders(
            @RequestParam(required = false) String userEmail,
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(required = false) BigDecimal minAmount,
            @RequestParam(required = false) BigDecimal maxAmount) {
        
        OrderSearchCriteria criteria = new OrderSearchCriteria(userEmail, status, minAmount, maxAmount);
        List<Order> orders = orderService.searchOrders(criteria);
        
        return ResponseEntity.ok(orders);
    }
    
    @PostMapping("/{orderId}/cancel")
    public ResponseEntity<Void> cancelOrder(@PathVariable Long orderId) {
        orderService.cancelOrder(orderId);
        return ResponseEntity.ok().build();
    }
}

@Service
@Transactional
public class OrderService {
    
    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    
    public OrderService(OrderRepository orderRepository, UserRepository userRepository) {
        this.orderRepository = orderRepository;
        this.userRepository = userRepository;
    }
    
    @Transactional(readOnly = true)
    public Page<OrderSummary> getUserOrders(Long userId, OrderStatus status, Pageable pageable) {
        if (status != null) {
            return orderRepository.findOrderSummariesByUserIdAndStatus(userId, status, pageable);
        }
        return orderRepository.findOrderSummariesByUserId(userId, pageable);
    }
    
    @Transactional(readOnly = true)
    public List<Order> searchOrders(OrderSearchCriteria criteria) {
        return orderRepository.findOrdersByCriteria(
            criteria.userEmail(),
            criteria.status(),
            criteria.minAmount(),
            criteria.maxAmount()
        );
    }
    
    public void cancelOrder(Long orderId) {
        Order order = orderRepository.findById(orderId)
            .orElseThrow(() -> new OrderNotFoundException("Order not found: " + orderId));
        
        if (order.getStatus() != OrderStatus.PENDING) {
            throw new IllegalOrderStateException("Cannot cancel order in status: " + order.getStatus());
        }
        
        order.setStatus(OrderStatus.CANCELLED);
        orderRepository.save(order);
    }
    
    // Bulk operations example
    @Transactional
    public BatchProcessResult processExpiredOrders() {
        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(30);
        
        int updatedCount = orderRepository.updateExpiredPendingOrders(
            OrderStatus.PENDING, 
            OrderStatus.EXPIRED, 
            cutoffDate
        );
        
        return new BatchProcessResult(updatedCount, "Expired orders processed");
    }
}

// Extended repository with all query methods
@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {
    
    // Method name queries
    List<Order> findByUserIdOrderByOrderDateDesc(Long userId);
    List<Order> findByStatusAndOrderDateAfter(OrderStatus status, LocalDateTime date);
    long countByUserIdAndStatus(Long userId, OrderStatus status);
    
    // Custom JPQL queries
    @Query("SELECT new com.example.dto.OrderSummary(" +
           "o.id, o.user.email, o.status, o.totalAmount, o.orderDate) " +
           "FROM Order o WHERE o.user.id = :userId")
    Page<OrderSummary> findOrderSummariesByUserId(@Param("userId") Long userId, Pageable pageable);
    
    @Query("SELECT new com.example.dto.OrderSummary(" +
           "o.id, o.user.email, o.status, o.totalAmount, o.orderDate) " +
           "FROM Order o WHERE o.user.id = :userId AND o.status = :status")
    Page<OrderSummary> findOrderSummariesByUserIdAndStatus(
        @Param("userId") Long userId, 
        @Param("status") OrderStatus status, 
        Pageable pageable);
    
    @Query("SELECT o FROM Order o WHERE " +
           "(:userEmail IS NULL OR o.user.email = :userEmail) AND " +
           "(:status IS NULL OR o.status = :status) AND " +
           "(:minAmount IS NULL OR o.totalAmount >= :minAmount) AND " +
           "(:maxAmount IS NULL OR o.totalAmount <= :maxAmount)")
    List<Order> findOrdersByCriteria(
        @Param("userEmail") String userEmail,
        @Param("status") OrderStatus status,
        @Param("minAmount") BigDecimal minAmount,
        @Param("maxAmount") BigDecimal maxAmount);
    
    // Modifying queries
    @Modifying
    @Query("UPDATE Order o SET o.status = :newStatus " +
           "WHERE o.status = :oldStatus AND o.orderDate < :cutoffDate")
    int updateExpiredPendingOrders(
        @Param("oldStatus") OrderStatus oldStatus,
        @Param("newStatus") OrderStatus newStatus,
        @Param("cutoffDate") LocalDateTime cutoffDate);
}

public record OrderSearchCriteria(
    String userEmail,
    OrderStatus status,
    BigDecimal minAmount,
    BigDecimal maxAmount
) {}

public record BatchProcessResult(
    int processedCount,
    String message
) {}
```

This completes Phase 3 of the Spring Data JPA mastery roadmap! You now have comprehensive knowledge of:

1. **Spring Data JPA Setup** - Project configuration, database setup, multiple datasources
2. **Repository Pattern** - Interface hierarchy, custom repositories, implementation strategies  
3. **CRUD Operations** - Basic operations, batch processing, performance optimization
4. **Query Methods** - Method naming conventions, property expressions, complex queries
5. **Custom Queries** - @Query annotation, JPQL, native SQL, modifying queries, SpEL

**Key Takeaways for Phase 3:**

- Always use method name queries for simple operations
- Use @Query with JPQL for complex business logic
- Use native queries only when JPQL is insufficient
- Consider performance implications of your queries
- Use projections to avoid loading unnecessary data
- Batch operations for better performance
- Always validate your queries with proper testing

**Practice Recommendations:**
1. Create a sample e-commerce project and implement all repository patterns shown
2. Practice writing different types of queries for the same data
3. Experiment with performance testing using different query approaches
4. Try implementing search functionality using various query techniques

Ready to move to **Phase 4: Advanced Entity Mapping** when you are!