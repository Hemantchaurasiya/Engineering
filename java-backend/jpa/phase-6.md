# Spring Data JPA Phase 6: Pagination, Sorting & Performance
## Complete Mastery Guide with Real-World Examples

---

## 6.1 Pagination and Sorting

### Understanding Pagination Concepts

Pagination is essential when dealing with large datasets. Instead of loading thousands of records at once, we load them in smaller, manageable chunks called "pages."

### Core Interfaces and Classes

#### 1. Pageable Interface
The `Pageable` interface defines pagination information:

```java
// Basic Pageable creation
Pageable pageable = PageRequest.of(0, 10); // Page 0, Size 10
Pageable pageableWithSort = PageRequest.of(0, 10, Sort.by("name"));
```

#### 2. Page Interface
The `Page` interface represents a page of data with metadata:

```java
public interface Page<T> extends Slice<T> {
    int getTotalPages();           // Total number of pages
    long getTotalElements();       // Total number of elements
    boolean hasContent();          // Whether page has content
    List<T> getContent();         // Actual content
    boolean isFirst();            // Is this the first page?
    boolean isLast();             // Is this the last page?
}
```

### Real-World Entity Setup

Let's create a complete e-commerce product catalog system:

```java
// Product Entity
@Entity
@Table(name = "products")
public class Product {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false)
    private String name;
    
    @Column(precision = 10, scale = 2)
    private BigDecimal price;
    
    @Column(name = "stock_quantity")
    private Integer stockQuantity;
    
    @Enumerated(EnumType.STRING)
    private ProductStatus status;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;
    
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    @Column(name = "rating")
    private Double rating;
    
    // Constructors, getters, setters
    public Product() {}
    
    public Product(String name, BigDecimal price, Integer stockQuantity, 
                   ProductStatus status, Category category) {
        this.name = name;
        this.price = price;
        this.stockQuantity = stockQuantity;
        this.status = status;
        this.category = category;
        this.createdAt = LocalDateTime.now();
    }
    
    // Getters and setters...
}

// Category Entity
@Entity
@Table(name = "categories")
public class Category {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false, unique = true)
    private String name;
    
    private String description;
    
    @OneToMany(mappedBy = "category", cascade = CascadeType.ALL)
    private List<Product> products = new ArrayList<>();
    
    // Constructors, getters, setters...
}

// Product Status Enum
public enum ProductStatus {
    ACTIVE, INACTIVE, OUT_OF_STOCK, DISCONTINUED
}
```

### Repository with Pagination Support

```java
@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {
    
    // Basic pagination methods are inherited from PagingAndSortingRepository
    // findAll(Pageable pageable) is available by default
    
    // Custom paginated queries
    Page<Product> findByStatus(ProductStatus status, Pageable pageable);
    
    Page<Product> findByPriceRange(BigDecimal minPrice, BigDecimal maxPrice, 
                                   Pageable pageable);
    
    Page<Product> findByCategoryName(String categoryName, Pageable pageable);
    
    // Custom query with pagination
    @Query("SELECT p FROM Product p WHERE p.name LIKE %:keyword% " +
           "AND p.status = :status")
    Page<Product> findByNameContainingAndStatus(
        @Param("keyword") String keyword, 
        @Param("status") ProductStatus status, 
        Pageable pageable);
    
    // Native query with pagination
    @Query(value = "SELECT * FROM products p " +
           "WHERE p.rating >= :minRating " +
           "ORDER BY p.rating DESC", 
           nativeQuery = true)
    Page<Product> findHighRatedProducts(@Param("minRating") Double minRating, 
                                       Pageable pageable);
}
```

### Service Layer Implementation

```java
@Service
@Transactional(readOnly = true)
public class ProductService {
    
    private final ProductRepository productRepository;
    
    public ProductService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }
    
    // Basic pagination
    public Page<Product> getAllProducts(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return productRepository.findAll(pageable);
    }
    
    // Pagination with sorting
    public Page<Product> getAllProductsSorted(int page, int size, 
                                            String sortBy, String sortDir) {
        Sort sort = sortDir.equalsIgnoreCase("desc") 
            ? Sort.by(sortBy).descending() 
            : Sort.by(sortBy).ascending();
        
        Pageable pageable = PageRequest.of(page, size, sort);
        return productRepository.findAll(pageable);
    }
    
    // Multiple sorting criteria
    public Page<Product> getProductsWithMultipleSort(int page, int size) {
        Sort sort = Sort.by("category.name").ascending()
                       .and(Sort.by("price").descending())
                       .and(Sort.by("rating").descending());
        
        Pageable pageable = PageRequest.of(page, size, sort);
        return productRepository.findAll(pageable);
    }
    
    // Complex filtering with pagination
    public Page<Product> searchProducts(String keyword, ProductStatus status,
                                      BigDecimal minPrice, BigDecimal maxPrice,
                                      int page, int size, String sortBy) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(sortBy));
        
        if (keyword != null && status != null) {
            return productRepository.findByNameContainingAndStatus(keyword, status, pageable);
        } else if (minPrice != null && maxPrice != null) {
            return productRepository.findByPriceRange(minPrice, maxPrice, pageable);
        } else {
            return productRepository.findAll(pageable);
        }
    }
    
    // Category-based pagination
    public Page<Product> getProductsByCategory(String categoryName, 
                                             int page, int size) {
        Pageable pageable = PageRequest.of(page, size, 
                                         Sort.by("name").ascending());
        return productRepository.findByCategoryName(categoryName, pageable);
    }
}
```

### REST Controller Implementation

```java
@RestController
@RequestMapping("/api/products")
public class ProductController {
    
    private final ProductService productService;
    
    public ProductController(ProductService productService) {
        this.productService = productService;
    }
    
    @GetMapping
    public ResponseEntity<Page<Product>> getAllProducts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "name") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {
        
        Page<Product> products = productService.getAllProductsSorted(
            page, size, sortBy, sortDir);
        return ResponseEntity.ok(products);
    }
    
    @GetMapping("/search")
    public ResponseEntity<Page<Product>> searchProducts(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) ProductStatus status,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "name") String sortBy) {
        
        Page<Product> products = productService.searchProducts(
            keyword, status, minPrice, maxPrice, page, size, sortBy);
        return ResponseEntity.ok(products);
    }
    
    @GetMapping("/category/{categoryName}")
    public ResponseEntity<Page<Product>> getProductsByCategory(
            @PathVariable String categoryName,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        
        Page<Product> products = productService.getProductsByCategory(
            categoryName, page, size);
        return ResponseEntity.ok(products);
    }
}
```

### Advanced Sorting Examples

```java
@Service
public class AdvancedSortingService {
    
    private final ProductRepository productRepository;
    
    // Custom sort with null handling
    public Page<Product> getProductsWithNullHandling(int page, int size) {
        Sort sort = Sort.by(
            Sort.Order.asc("category.name"),
            Sort.Order.desc("rating").nullsLast(),  // Nulls at end
            Sort.Order.asc("price").nullsFirst()    // Nulls at beginning
        );
        
        Pageable pageable = PageRequest.of(page, size, sort);
        return productRepository.findAll(pageable);
    }
    
    // Case-insensitive sorting
    public Page<Product> getProductsCaseInsensitive(int page, int size) {
        Sort sort = Sort.by(
            Sort.Order.asc("name").ignoreCase()
        );
        
        Pageable pageable = PageRequest.of(page, size, sort);
        return productRepository.findAll(pageable);
    }
    
    // Dynamic sorting based on user preference
    public Page<Product> getProductsWithDynamicSort(int page, int size, 
                                                   Map<String, String> sortCriteria) {
        List<Sort.Order> orders = new ArrayList<>();
        
        sortCriteria.forEach((property, direction) -> {
            Sort.Direction dir = "desc".equalsIgnoreCase(direction) 
                ? Sort.Direction.DESC : Sort.Direction.ASC;
            orders.add(new Sort.Order(dir, property));
        });
        
        Sort sort = Sort.by(orders);
        Pageable pageable = PageRequest.of(page, size, sort);
        return productRepository.findAll(pageable);
    }
}
```

---

## 6.2 Performance Optimization

### Understanding the N+1 Problem

The N+1 problem occurs when you execute one query to fetch N records, then execute N additional queries to fetch related data.

```java
// BAD: This will cause N+1 problem
public List<Product> getBadProducts() {
    List<Product> products = productRepository.findAll(); // 1 query
    
    // This will execute N queries (one for each product)
    products.forEach(product -> {
        System.out.println(product.getCategory().getName()); // N queries!
    });
    
    return products;
}
```

### Solution 1: Fetch Joins

```java
@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {
    
    // JPQL Fetch Join - Solves N+1 problem
    @Query("SELECT p FROM Product p JOIN FETCH p.category")
    List<Product> findAllWithCategory();
    
    // Multiple fetch joins
    @Query("SELECT DISTINCT p FROM Product p " +
           "JOIN FETCH p.category " +
           "LEFT JOIN FETCH p.reviews")
    List<Product> findAllWithCategoryAndReviews();
    
    // Paginated fetch join
    @Query(value = "SELECT p FROM Product p JOIN FETCH p.category",
           countQuery = "SELECT COUNT(p) FROM Product p")
    Page<Product> findAllWithCategoryPaginated(Pageable pageable);
}
```

### Solution 2: Entity Graphs

Entity Graphs provide a more flexible way to define what should be eagerly loaded:

```java
@Entity
@NamedEntityGraph(
    name = "Product.category",
    attributeNodes = @NamedAttributeNode("category")
)
@NamedEntityGraph(
    name = "Product.full",
    attributeNodes = {
        @NamedAttributeNode("category"),
        @NamedAttributeNode("reviews")
    }
)
public class Product {
    // entity definition...
}

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {
    
    // Using named entity graph
    @EntityGraph("Product.category")
    List<Product> findByStatus(ProductStatus status);
    
    // Using ad-hoc entity graph
    @EntityGraph(attributePaths = {"category", "reviews"})
    Page<Product> findAll(Pageable pageable);
    
    // Dynamic entity graph
    @EntityGraph(attributePaths = {"category"})
    @Query("SELECT p FROM Product p WHERE p.price > :price")
    List<Product> findExpensiveProducts(@Param("price") BigDecimal price);
}
```

### Solution 3: Batch Fetching

```java
// Configure batch fetching in application.properties
# spring.jpa.properties.hibernate.default_batch_fetch_size=10

// Or use @BatchSize annotation
@Entity
public class Category {
    @Id
    private Long id;
    
    @OneToMany(mappedBy = "category")
    @BatchSize(size = 10) // Load up to 10 products at once
    private List<Product> products;
}
```

### Query Optimization Examples

```java
@Service
@Transactional(readOnly = true)
public class OptimizedProductService {
    
    private final ProductRepository productRepository;
    
    // Optimized method - No N+1 problem
    public List<ProductDTO> getOptimizedProducts() {
        return productRepository.findAllWithCategory()
            .stream()
            .map(this::convertToDTO)
            .collect(Collectors.toList());
    }
    
    // Using projection to avoid loading unnecessary data
    @Query("SELECT new com.example.dto.ProductSummaryDTO(" +
           "p.id, p.name, p.price, c.name) " +
           "FROM Product p JOIN p.category c " +
           "WHERE p.status = :status")
    List<ProductSummaryDTO> findProductSummary(@Param("status") ProductStatus status);
    
    // Batch loading with proper pagination
    public Page<Product> getBatchOptimizedProducts(Pageable pageable) {
        return productRepository.findAll(pageable);
    }
    
    private ProductDTO convertToDTO(Product product) {
        return new ProductDTO(
            product.getId(),
            product.getName(),
            product.getPrice(),
            product.getCategory().getName() // Safe - already loaded
        );
    }
}

// DTO Classes
public class ProductDTO {
    private Long id;
    private String name;
    private BigDecimal price;
    private String categoryName;
    
    // Constructor, getters, setters...
}

public class ProductSummaryDTO {
    private Long id;
    private String name;
    private BigDecimal price;
    private String categoryName;
    
    public ProductSummaryDTO(Long id, String name, BigDecimal price, String categoryName) {
        this.id = id;
        this.name = name;
        this.price = price;
        this.categoryName = categoryName;
    }
    
    // Getters and setters...
}
```

### Connection Pool Configuration

```yaml
# application.yml
spring:
  datasource:
    # HikariCP Configuration (Default in Spring Boot 2+)
    hikari:
      maximum-pool-size: 20        # Maximum pool size
      minimum-idle: 5              # Minimum idle connections
      connection-timeout: 30000    # 30 seconds
      idle-timeout: 600000         # 10 minutes
      max-lifetime: 1800000        # 30 minutes
      leak-detection-threshold: 60000  # 1 minute
      
  jpa:
    properties:
      hibernate:
        # Batch processing
        jdbc.batch_size: 25
        order_inserts: true
        order_updates: true
        
        # Query optimization
        query.plan_cache_max_size: 128
        query.plan_parameter_metadata_max_size: 32
        
        # Connection management
        connection.provider_disables_autocommit: true
        
        # Statistics for monitoring
        generate_statistics: true
        
    show-sql: false  # Disable in production
    
# Monitoring
management:
  endpoints:
    web:
      exposure:
        include: health,metrics,info
  endpoint:
    health:
      show-details: always
  metrics:
    export:
      simple:
        enabled: true
```

### Second-Level Cache Configuration

```java
// Enable second-level cache
@Configuration
@EnableCaching
public class CacheConfig {
    
    @Bean
    public CacheManager cacheManager() {
        return new ConcurrentMapCacheManager("products", "categories");
    }
}

// Entity with caching
@Entity
@Cacheable
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE, region = "products")
public class Product {
    // entity definition...
}

// Repository with caching
@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {
    
    @Cacheable("products")
    @Query("SELECT p FROM Product p WHERE p.status = :status")
    List<Product> findCachedByStatus(@Param("status") ProductStatus status);
    
    @CacheEvict(value = "products", allEntries = true)
    @Modifying
    @Query("UPDATE Product p SET p.status = :status WHERE p.id = :id")
    void updateProductStatus(@Param("id") Long id, @Param("status") ProductStatus status);
}

# Cache configuration in application.yml
spring:
  jpa:
    properties:
      hibernate:
        cache:
          use_second_level_cache: true
          use_query_cache: true
          region.factory_class: org.hibernate.cache.ehcache.EhCacheRegionFactory
```

---

## 6.3 Lazy Loading Strategies

### Understanding Lazy Loading

Lazy loading defers the loading of associated entities until they are actually accessed.

```java
@Entity
public class Product {
    @Id
    private Long id;
    
    // Lazy loading (default for @ManyToOne)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;
    
    // Lazy loading (default for collections)
    @OneToMany(mappedBy = "product", fetch = FetchType.LAZY)
    private List<Review> reviews = new ArrayList<>();
    
    // Eager loading (use sparingly)
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "brand_id")
    private Brand brand;
}
```

### Handling LazyInitializationException

```java
@Service
@Transactional(readOnly = true)
public class ProductService {
    
    private final ProductRepository productRepository;
    
    // WRONG: Will cause LazyInitializationException
    @Transactional(readOnly = true)
    public Product getBadProduct(Long id) {
        Product product = productRepository.findById(id).orElse(null);
        // Transaction ends here
        return product;
    }
    
    public void processProduct(Long id) {
        Product product = getBadProduct(id);
        // This will throw LazyInitializationException
        System.out.println(product.getCategory().getName());
    }
    
    // CORRECT: Access lazy properties within transaction
    @Transactional(readOnly = true)
    public ProductDTO getGoodProduct(Long id) {
        Product product = productRepository.findById(id).orElse(null);
        if (product != null) {
            // Access lazy properties within transaction
            return new ProductDTO(
                product.getId(),
                product.getName(),
                product.getPrice(),
                product.getCategory().getName(), // Safe - within transaction
                product.getReviews().size()      // Safe - within transaction
            );
        }
        return null;
    }
    
    // Using fetch joins to avoid lazy loading issues
    @Transactional(readOnly = true)
    public Product getProductWithCategory(Long id) {
        return productRepository.findByIdWithCategory(id);
    }
    
    // Force initialization of lazy collections
    @Transactional(readOnly = true)
    public Product getProductWithReviews(Long id) {
        Product product = productRepository.findById(id).orElse(null);
        if (product != null) {
            Hibernate.initialize(product.getReviews()); // Force initialization
        }
        return product;
    }
}

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {
    
    @Query("SELECT p FROM Product p JOIN FETCH p.category WHERE p.id = :id")
    Product findByIdWithCategory(@Param("id") Long id);
    
    @EntityGraph(attributePaths = {"category", "reviews"})
    Optional<Product> findWithDetailsById(Long id);
}
```

### DTO Projections to Avoid Entity Loading

```java
// Interface-based projection
public interface ProductProjection {
    Long getId();
    String getName();
    BigDecimal getPrice();
    String getCategoryName(); // From joined table
}

// Class-based projection
public class ProductSummary {
    private final Long id;
    private final String name;
    private final BigDecimal price;
    private final String categoryName;
    
    public ProductSummary(Long id, String name, BigDecimal price, String categoryName) {
        this.id = id;
        this.name = name;
        this.price = price;
        this.categoryName = categoryName;
    }
    
    // Getters...
}

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {
    
    // Interface projection - No entity loading
    @Query("SELECT p.id as id, p.name as name, p.price as price, " +
           "c.name as categoryName " +
           "FROM Product p JOIN p.category c " +
           "WHERE p.status = :status")
    Page<ProductProjection> findProjectionByStatus(
        @Param("status") ProductStatus status, 
        Pageable pageable);
    
    // Class projection - Constructor expression
    @Query("SELECT new com.example.dto.ProductSummary(" +
           "p.id, p.name, p.price, c.name) " +
           "FROM Product p JOIN p.category c " +
           "WHERE p.price BETWEEN :minPrice AND :maxPrice")
    List<ProductSummary> findProductSummaryByPriceRange(
        @Param("minPrice") BigDecimal minPrice,
        @Param("maxPrice") BigDecimal maxPrice);
}
```

### Open Session in View Pattern

```java
// Configuration (use with caution)
spring:
  jpa:
    open-in-view: true  # Default in Spring Boot, but consider disabling

// Better approach: Explicit transaction boundaries
@Service
public class ProductViewService {
    
    private final ProductRepository productRepository;
    
    @Transactional(readOnly = true)
    public List<ProductViewModel> getProductsForView() {
        List<Product> products = productRepository.findAll();
        
        // Transform to view models within transaction
        return products.stream()
            .map(this::toViewModel)
            .collect(Collectors.toList());
    }
    
    private ProductViewModel toViewModel(Product product) {
        ProductViewModel vm = new ProductViewModel();
        vm.setId(product.getId());
        vm.setName(product.getName());
        vm.setPrice(product.getPrice());
        vm.setCategoryName(product.getCategory().getName()); // Safe within transaction
        vm.setReviewCount(product.getReviews().size());      // Safe within transaction
        return vm;
    }
}
```

## Real-World Performance Testing

```java
@Component
@Slf4j
public class PerformanceTestService {
    
    private final ProductRepository productRepository;
    private final EntityManager entityManager;
    
    public void demonstrateN1Problem() {
        log.info("=== Demonstrating N+1 Problem ===");
        
        long start = System.currentTimeMillis();
        
        // This will cause N+1 queries
        List<Product> products = productRepository.findAll();
        products.forEach(product -> {
            log.info("Product: {} - Category: {}", 
                product.getName(), 
                product.getCategory().getName()); // Each access = 1 query
        });
        
        long end = System.currentTimeMillis();
        log.info("N+1 approach took: {} ms", (end - start));
    }
    
    public void demonstrateOptimizedApproach() {
        log.info("=== Demonstrating Optimized Approach ===");
        
        long start = System.currentTimeMillis();
        
        // This will use only 1 query with JOIN FETCH
        List<Product> products = productRepository.findAllWithCategory();
        products.forEach(product -> {
            log.info("Product: {} - Category: {}", 
                product.getName(), 
                product.getCategory().getName()); // No additional queries
        });
        
        long end = System.currentTimeMillis();
        log.info("Optimized approach took: {} ms", (end - start));
    }
    
    public void demonstrateProjectionPerformance() {
        log.info("=== Demonstrating Projection Performance ===");
        
        long start = System.currentTimeMillis();
        
        // Using projection - only selects needed columns
        List<ProductProjection> projections = 
            productRepository.findProjectionByStatus(ProductStatus.ACTIVE, 
                                                    Pageable.unpaged());
        
        projections.forEach(p -> {
            log.info("Product: {} - Price: {} - Category: {}", 
                p.getName(), p.getPrice(), p.getCategoryName());
        });
        
        long end = System.currentTimeMillis();
        log.info("Projection approach took: {} ms", (end - start));
    }
}
```

## Key Takeaways for Phase 6

1. **Always use pagination** for large datasets - never load everything at once
2. **Be aware of the N+1 problem** - use fetch joins, entity graphs, or projections
3. **Choose the right fetch strategy** - lazy by default, eager only when necessary
4. **Use projections** when you don't need full entities
5. **Configure connection pooling** properly for your application load
6. **Monitor performance** in production with proper metrics
7. **Test with realistic data volumes** - performance issues often appear with scale

Next, we'll move to **Phase 7: Transactions & Concurrency**, where we'll dive deep into transaction management, isolation levels, and handling concurrent access to data!