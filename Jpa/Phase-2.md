# Phase 2: JPA Fundamentals - Complete Guide with Examples

## 2.1 JPA Specification Understanding

### What is JPA?
Java Persistence API (JPA) is a specification that defines how to persist data between Java objects and relational databases. It's not an implementation but a standard that provides:
- Object-Relational Mapping (ORM)
- Entity lifecycle management
- Query language (JPQL)
- Criteria API for type-safe queries

### JPA vs JDBC: Key Differences

| Aspect | JDBC | JPA |
|--------|------|-----|
| **Level** | Low-level database access | High-level ORM |
| **Code Complexity** | More boilerplate code | Less boilerplate |
| **Database Independence** | Database-specific SQL | Database-agnostic JPQL |
| **Object Mapping** | Manual mapping | Automatic mapping |
| **Caching** | No built-in caching | First/Second level cache |
| **Lazy Loading** | Not supported | Built-in support |

#### JDBC Example (Traditional Approach):
```java
// JDBC - Manual, error-prone, lots of boilerplate
public User findUserById(Long id) {
    String sql = "SELECT * FROM users WHERE id = ?";
    try (Connection conn = dataSource.getConnection();
         PreparedStatement stmt = conn.prepareStatement(sql)) {
        
        stmt.setLong(1, id);
        ResultSet rs = stmt.executeQuery();
        
        if (rs.next()) {
            User user = new User();
            user.setId(rs.getLong("id"));
            user.setName(rs.getString("name"));
            user.setEmail(rs.getString("email"));
            return user;
        }
        return null;
    } catch (SQLException e) {
        throw new RuntimeException(e);
    }
}
```

#### JPA Example (Modern Approach):
```java
// JPA - Clean, simple, type-safe
@Entity
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String name;
    private String email;
    // getters/setters
}

// Usage
User user = entityManager.find(User.class, id);
```

### JPA Implementations

#### 1. Hibernate (Most Popular)
```xml
<!-- Maven dependency -->
<dependency>
    <groupId>org.hibernate</groupId>
    <artifactId>hibernate-entitymanager</artifactId>
    <version>5.6.14.Final</version>
</dependency>
```

#### 2. EclipseLink
```xml
<dependency>
    <groupId>org.eclipse.persistence</groupId>
    <artifactId>eclipselink</artifactId>
    <version>3.0.3</version>
</dependency>
```

#### 3. OpenJPA
```xml
<dependency>
    <groupId>org.apache.openjpa</groupId>
    <artifactId>openjpa</artifactId>
    <version>3.2.2</version>
</dependency>
```

### Entity Lifecycle States

Understanding entity states is crucial for effective JPA usage:

```java
@Entity
public class Product {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    private String name;
    private BigDecimal price;
    
    // constructors, getters, setters
    public Product() {}
    
    public Product(String name, BigDecimal price) {
        this.name = name;
        this.price = price;
    }
}

// Demonstrating Entity Lifecycle
@Service
@Transactional
public class ProductService {
    
    @PersistenceContext
    private EntityManager entityManager;
    
    public void demonstrateEntityLifecycle() {
        // 1. NEW STATE - Entity just created, not managed
        Product newProduct = new Product("Laptop", new BigDecimal("999.99"));
        System.out.println("NEW: " + entityManager.contains(newProduct)); // false
        
        // 2. MANAGED STATE - Entity is tracked by persistence context
        entityManager.persist(newProduct);
        System.out.println("MANAGED: " + entityManager.contains(newProduct)); // true
        
        // Auto-dirty checking - changes are automatically detected
        newProduct.setPrice(new BigDecimal("899.99")); // Will update DB
        
        // 3. DETACHED STATE - Entity was managed but no longer tracked
        entityManager.detach(newProduct);
        System.out.println("DETACHED: " + entityManager.contains(newProduct)); // false
        
        // Changes to detached entity won't be saved
        newProduct.setName("Gaming Laptop"); // Won't update DB
        
        // 4. REMOVED STATE - Entity marked for deletion
        Product managedProduct = entityManager.merge(newProduct); // Re-attach
        entityManager.remove(managedProduct);
        System.out.println("REMOVED: " + entityManager.contains(managedProduct)); // true until flush
    }
}
```

### Persistence Context Deep Dive

The Persistence Context is JPA's "first-level cache" - a crucial concept:

```java
@Component
public class PersistenceContextDemo {
    
    @PersistenceContext
    private EntityManager entityManager;
    
    @Transactional
    public void demonstratePersistenceContext() {
        // First-level cache in action
        Product product1 = entityManager.find(Product.class, 1L); // DB query
        Product product2 = entityManager.find(Product.class, 1L); // No DB query!
        
        System.out.println(product1 == product2); // true - same instance
        
        // Identity guarantee within persistence context
        Product product3 = entityManager.createQuery(
            "SELECT p FROM Product p WHERE p.id = :id", Product.class)
            .setParameter("id", 1L)
            .getSingleResult();
            
        System.out.println(product1 == product3); // true - same instance
    }
    
    @Transactional
    public void demonstrateAutomaticDirtyChecking() {
        Product product = entityManager.find(Product.class, 1L);
        
        // Just modify the entity - no explicit save needed
        product.setName("Updated Product Name");
        
        // At transaction commit, JPA automatically detects changes
        // and generates UPDATE SQL
        entityManager.flush(); // Force immediate sync to DB
    }
}
```

## 2.2 Entity Mapping Basics

### Creating Your First Entity

```java
@Entity // Marks this class as a JPA entity
@Table(name = "products") // Optional: specify table name
public class Product {
    
    @Id // Marks the primary key field
    @GeneratedValue(strategy = GenerationType.IDENTITY) // Auto-increment
    private Long id;
    
    @Column(name = "product_name", length = 100, nullable = false)
    private String name;
    
    @Column(precision = 10, scale = 2) // For BigDecimal
    private BigDecimal price;
    
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    @Column(name = "is_active")
    private Boolean active;
    
    @Lob // Large Object - maps to CLOB/BLOB
    private String description;
    
    @Enumerated(EnumType.STRING) // Store enum as string
    private ProductStatus status;
    
    // JPA requires default constructor
    public Product() {}
    
    public Product(String name, BigDecimal price) {
        this.name = name;
        this.price = price;
        this.createdAt = LocalDateTime.now();
        this.active = true;
        this.status = ProductStatus.ACTIVE;
    }
    
    // Standard getters and setters
    // hashCode() and equals() based on business key (not ID)
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Product)) return false;
        Product product = (Product) o;
        return Objects.equals(name, product.name);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(name);
    }
}

enum ProductStatus {
    ACTIVE, DISCONTINUED, OUT_OF_STOCK
}
```

### Primary Key Generation Strategies

```java
@Entity
public class GenerationStrategyExamples {
    
    // 1. IDENTITY - Database auto-increment (MySQL, SQL Server)
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long identityId;
    
    // 2. SEQUENCE - Database sequence (PostgreSQL, Oracle)
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "product_seq")
    @SequenceGenerator(name = "product_seq", sequenceName = "product_sequence", 
                      initialValue = 1, allocationSize = 50)
    private Long sequenceId;
    
    // 3. TABLE - Simulates sequence using table
    @Id
    @GeneratedValue(strategy = GenerationType.TABLE, generator = "product_table")
    @TableGenerator(name = "product_table", table = "id_generator",
                   pkColumnName = "gen_key", valueColumnName = "gen_value",
                   pkColumnValue = "product_id", allocationSize = 50)
    private Long tableId;
    
    // 4. AUTO - Let JPA choose appropriate strategy
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long autoId;
    
    // 5. Custom UUID Generation
    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    @Column(columnDefinition = "BINARY(16)")
    private UUID uuid;
}
```

### Data Type Mappings

```java
@Entity
public class DataTypeMappingExample {
    
    @Id
    private Long id;
    
    // String mappings
    @Column(length = 50)
    private String shortText;
    
    @Lob
    private String longText; // CLOB
    
    @Column(columnDefinition = "TEXT")
    private String mediumText;
    
    // Numeric mappings
    private Integer intValue;
    private int primitiveInt;
    private Long longValue;
    private BigDecimal decimalValue;
    private Double doubleValue;
    private Float floatValue;
    
    // Date and Time mappings
    private LocalDate localDate;
    private LocalDateTime localDateTime;
    private LocalTime localTime;
    private Instant instant;
    private ZonedDateTime zonedDateTime;
    
    // Legacy date mappings (still supported)
    @Temporal(TemporalType.DATE)
    private Date dateOnly;
    
    @Temporal(TemporalType.TIME)
    private Date timeOnly;
    
    @Temporal(TemporalType.TIMESTAMP)
    private Date timestamp;
    
    // Boolean mappings
    private Boolean booleanWrapper;
    private boolean primitiveBoolean;
    
    // Enum mappings
    @Enumerated(EnumType.STRING)
    private Status stringEnum;
    
    @Enumerated(EnumType.ORDINAL)
    private Priority ordinalEnum;
    
    // Binary data
    @Lob
    private byte[] binaryData; // BLOB
    
    // JSON mapping (PostgreSQL, MySQL 5.7+)
    @Column(columnDefinition = "JSON")
    @Convert(converter = JsonConverter.class)
    private Map<String, Object> jsonData;
    
    // Array mapping (PostgreSQL)
    @Column(columnDefinition = "text[]")
    @Convert(converter = StringArrayConverter.class)
    private String[] tags;
}

// Custom converter example
@Converter
public class JsonConverter implements AttributeConverter<Map<String, Object>, String> {
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    @Override
    public String convertToDatabaseColumn(Map<String, Object> attribute) {
        try {
            return objectMapper.writeValueAsString(attribute);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Error converting to JSON", e);
        }
    }
    
    @Override
    public Map<String, Object> convertToEntityAttribute(String dbData) {
        try {
            return objectMapper.readValue(dbData, new TypeReference<Map<String, Object>>() {});
        } catch (IOException e) {
            throw new RuntimeException("Error parsing JSON", e);
        }
    }
}
```

## 2.3 EntityManager and Persistence Context

### EntityManager Interface Deep Dive

```java
@Service
@Transactional
public class EntityManagerDemo {
    
    @PersistenceContext
    private EntityManager entityManager;
    
    // Basic CRUD operations
    public Product createProduct(Product product) {
        entityManager.persist(product); // INSERT
        return product; // ID populated after persist
    }
    
    public Product findProduct(Long id) {
        return entityManager.find(Product.class, id); // SELECT by PK
    }
    
    public Product getProductReference(Long id) {
        // Returns proxy without hitting database
        // Use when you only need the ID for relationships
        return entityManager.getReference(Product.class, id);
    }
    
    public Product updateProduct(Product product) {
        return entityManager.merge(product); // UPDATE or INSERT
    }
    
    public void deleteProduct(Long id) {
        Product product = entityManager.find(Product.class, id);
        if (product != null) {
            entityManager.remove(product); // DELETE
        }
    }
    
    // Lifecycle management
    public void demonstrateLifecycleOperations() {
        Product product = new Product("Test Product", new BigDecimal("99.99"));
        
        // persist() - entity becomes managed
        entityManager.persist(product);
        
        // detach() - entity becomes detached
        entityManager.detach(product);
        
        // merge() - merge detached entity back
        product = entityManager.merge(product);
        
        // refresh() - reload from database
        entityManager.refresh(product);
        
        // flush() - synchronize to database
        entityManager.flush();
        
        // clear() - detach all entities
        entityManager.clear();
    }
}
```

### Advanced EntityManager Operations

```java
@Service
@Transactional
public class AdvancedEntityManagerOperations {
    
    @PersistenceContext
    private EntityManager entityManager;
    
    // Batch operations
    public void batchInsert(List<Product> products) {
        int batchSize = 50;
        for (int i = 0; i < products.size(); i++) {
            entityManager.persist(products.get(i));
            
            if (i % batchSize == 0) {
                entityManager.flush();
                entityManager.clear(); // Clear persistence context
            }
        }
        entityManager.flush();
        entityManager.clear();
    }
    
    // Bulk operations
    public int bulkUpdatePrices(BigDecimal multiplier) {
        String jpql = "UPDATE Product p SET p.price = p.price * :multiplier " +
                     "WHERE p.status = :status";
        
        return entityManager.createQuery(jpql)
                .setParameter("multiplier", multiplier)
                .setParameter("status", ProductStatus.ACTIVE)
                .executeUpdate(); // Returns number of affected rows
    }
    
    public int bulkDelete() {
        String jpql = "DELETE FROM Product p WHERE p.status = :status";
        
        return entityManager.createQuery(jpql)
                .setParameter("status", ProductStatus.DISCONTINUED)
                .executeUpdate();
    }
    
    // Native SQL queries
    public List<Object[]> getProductSummary() {
        String sql = """
            SELECT 
                status,
                COUNT(*) as count,
                AVG(price) as avg_price,
                SUM(price) as total_value
            FROM products 
            GROUP BY status
            """;
        
        return entityManager.createNativeQuery(sql).getResultList();
    }
    
    // Named queries (defined in entity)
    public List<Product> findExpensiveProducts(BigDecimal minPrice) {
        return entityManager.createNamedQuery("Product.findExpensive", Product.class)
                .setParameter("minPrice", minPrice)
                .getResultList();
    }
}

// Named query definition in entity
@Entity
@NamedQueries({
    @NamedQuery(
        name = "Product.findExpensive",
        query = "SELECT p FROM Product p WHERE p.price >= :minPrice ORDER BY p.price DESC"
    ),
    @NamedQuery(
        name = "Product.countByStatus",
        query = "SELECT COUNT(p) FROM Product p WHERE p.status = :status"
    )
})
public class Product {
    // ... entity definition
}
```

### EntityManagerFactory Configuration

```java
// Java-based configuration
@Configuration
@EnableJpaRepositories
public class JpaConfig {
    
    @Bean
    public LocalContainerEntityManagerFactoryBean entityManagerFactory() {
        LocalContainerEntityManagerFactoryBean em = new LocalContainerEntityManagerFactoryBean();
        em.setDataSource(dataSource());
        em.setPackagesToScan("com.example.domain");
        em.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        
        Properties props = new Properties();
        props.setProperty("hibernate.hbm2ddl.auto", "validate");
        props.setProperty("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect");
        props.setProperty("hibernate.show_sql", "true");
        props.setProperty("hibernate.format_sql", "true");
        props.setProperty("hibernate.jdbc.batch_size", "50");
        props.setProperty("hibernate.order_inserts", "true");
        props.setProperty("hibernate.order_updates", "true");
        props.setProperty("hibernate.jdbc.batch_versioned_data", "true");
        
        em.setJpaProperties(props);
        return em;
    }
    
    @Bean
    public DataSource dataSource() {
        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setDriverClassName("org.postgresql.Driver");
        dataSource.setJdbcUrl("jdbc:postgresql://localhost:5432/jpa_demo");
        dataSource.setUsername("demo_user");
        dataSource.setPassword("demo_password");
        
        // Connection pool settings
        dataSource.setMaximumPoolSize(20);
        dataSource.setMinimumIdle(5);
        dataSource.setIdleTimeout(300000);
        dataSource.setConnectionTimeout(20000);
        dataSource.setLeakDetectionThreshold(60000);
        
        return dataSource;
    }
    
    @Bean
    public PlatformTransactionManager transactionManager() {
        JpaTransactionManager transactionManager = new JpaTransactionManager();
        transactionManager.setEntityManagerFactory(entityManagerFactory().getObject());
        return transactionManager;
    }
}
```

### persistence.xml Configuration (Alternative)

```xml
<?xml version="1.0" encoding="UTF-8"?>
<persistence xmlns="http://xmlns.jcp.org/xml/ns/persistence"
             xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
             xsi:schemaLocation="http://xmlns.jcp.org/xml/ns/persistence 
                                 http://xmlns.jcp.org/xml/ns/persistence/persistence_2_2.xsd"
             version="2.2">
    
    <persistence-unit name="jpa-demo" transaction-type="RESOURCE_LOCAL">
        <provider>org.hibernate.jpa.HibernatePersistenceProvider</provider>
        
        <!-- Entity classes -->
        <class>com.example.domain.Product</class>
        <class>com.example.domain.Category</class>
        <class>com.example.domain.User</class>
        
        <!-- Exclude unlisted classes -->
        <exclude-unlisted-classes>false</exclude-unlisted-classes>
        
        <properties>
            <!-- Database connection -->
            <property name="javax.persistence.jdbc.driver" value="org.postgresql.Driver"/>
            <property name="javax.persistence.jdbc.url" value="jdbc:postgresql://localhost:5432/jpa_demo"/>
            <property name="javax.persistence.jdbc.user" value="demo_user"/>
            <property name="javax.persistence.jdbc.password" value="demo_password"/>
            
            <!-- Hibernate specific -->
            <property name="hibernate.hbm2ddl.auto" value="validate"/>
            <property name="hibernate.dialect" value="org.hibernate.dialect.PostgreSQLDialect"/>
            <property name="hibernate.show_sql" value="true"/>
            <property name="hibernate.format_sql" value="true"/>
            
            <!-- Performance settings -->
            <property name="hibernate.jdbc.batch_size" value="50"/>
            <property name="hibernate.order_inserts" value="true"/>
            <property name="hibernate.order_updates" value="true"/>
            
            <!-- Connection pool (if using Hibernate's built-in pool) -->
            <property name="hibernate.connection.pool_size" value="10"/>
        </properties>
    </persistence-unit>
</persistence>
```

## Real-World Use Cases and Examples

### Complete Product Management System

```java
// Domain Model
@Entity
@Table(name = "products")
public class Product {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false, length = 100)
    private String name;
    
    @Column(precision = 10, scale = 2, nullable = false)
    private BigDecimal price;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProductStatus status;
    
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    @Version
    private Long version; // For optimistic locking
    
    // Constructors, getters, setters
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}

// Service Layer
@Service
@Transactional
public class ProductService {
    
    @PersistenceContext
    private EntityManager entityManager;
    
    public Product createProduct(String name, BigDecimal price) {
        Product product = new Product();
        product.setName(name);
        product.setPrice(price);
        product.setStatus(ProductStatus.ACTIVE);
        
        entityManager.persist(product);
        return product;
    }
    
    @Transactional(readOnly = true)
    public Product findProductById(Long id) {
        return entityManager.find(Product.class, id);
    }
    
    @Transactional(readOnly = true)
    public List<Product> findActiveProducts() {
        String jpql = "SELECT p FROM Product p WHERE p.status = :status ORDER BY p.name";
        return entityManager.createQuery(jpql, Product.class)
                .setParameter("status", ProductStatus.ACTIVE)
                .getResultList();
    }
    
    public Product updatePrice(Long productId, BigDecimal newPrice) {
        Product product = entityManager.find(Product.class, productId);
        if (product != null) {
            product.setPrice(newPrice);
            // No explicit merge needed - entity is managed
        }
        return product;
    }
    
    public void discontinueProduct(Long productId) {
        Product product = entityManager.find(Product.class, productId);
        if (product != null) {
            product.setStatus(ProductStatus.DISCONTINUED);
        }
    }
    
    public void deleteProduct(Long productId) {
        Product product = entityManager.getReference(Product.class, productId);
        entityManager.remove(product);
    }
}

// REST Controller
@RestController
@RequestMapping("/api/products")
public class ProductController {
    
    private final ProductService productService;
    
    public ProductController(ProductService productService) {
        this.productService = productService;
    }
    
    @PostMapping
    public ResponseEntity<Product> createProduct(@RequestBody CreateProductRequest request) {
        Product product = productService.createProduct(request.getName(), request.getPrice());
        return ResponseEntity.status(HttpStatus.CREATED).body(product);
    }
    
    @GetMapping("/{id}")
    public ResponseEntity<Product> getProduct(@PathVariable Long id) {
        Product product = productService.findProductById(id);
        return product != null ? ResponseEntity.ok(product) : ResponseEntity.notFound().build();
    }
    
    @GetMapping
    public List<Product> getActiveProducts() {
        return productService.findActiveProducts();
    }
    
    @PutMapping("/{id}/price")
    public ResponseEntity<Product> updatePrice(@PathVariable Long id, 
                                             @RequestBody UpdatePriceRequest request) {
        Product product = productService.updatePrice(id, request.getPrice());
        return product != null ? ResponseEntity.ok(product) : ResponseEntity.notFound().build();
    }
    
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProduct(@PathVariable Long id) {
        productService.deleteProduct(id);
        return ResponseEntity.noContent().build();
    }
}
```

## Key Takeaways from Phase 2

1. **JPA is a Specification**: Understand that JPA defines the contract, while Hibernate, EclipseLink, etc., are implementations.

2. **Entity Lifecycle is Critical**: Master the four states (New, Managed, Detached, Removed) for effective entity management.

3. **Persistence Context = First-Level Cache**: Automatic dirty checking and identity guarantee within a transaction.

4. **EntityManager is Your Interface**: Core API for all database operations in JPA.

5. **Proper Entity Design**: Use appropriate annotations, generation strategies, and data types.

## Next Steps

Now that you have a solid foundation in JPA fundamentals, you're ready for **Phase 3: Spring Data JPA Core**, where we'll explore:
- Repository pattern implementation
- Query method conventions
- Custom queries with @Query
- Advanced repository features

Practice the concepts in this phase by building a simple application with entities, EntityManager operations, and basic CRUD functionality before moving forward.