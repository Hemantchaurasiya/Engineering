# Phase 10: Testing - Comprehensive Spring Data JPA Testing

## 10.1 Unit Testing with @DataJpaTest

`@DataJpaTest` provides a lightweight testing slice that configures only JPA-related components.

### Basic Repository Testing

```java
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class ProductRepositoryTest {
    
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:13")
        .withDatabaseName("testdb")
        .withUsername("test")
        .withPassword("test");
    
    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }
    
    @Autowired
    private TestEntityManager entityManager;
    
    @Autowired
    private ProductRepository productRepository;
    
    @Test
    void whenFindByName_thenReturnProduct() {
        // Given
        Product product = new Product("Test Product", new BigDecimal("19.99"), "Test Description");
        entityManager.persistAndFlush(product);
        
        // When
        List<Product> found = productRepository.findByNameContainingIgnoreCase("Test");
        
        // Then
        assertThat(found).hasSize(1);
        assertThat(found.get(0).getName()).isEqualTo("Test Product");
        assertThat(found.get(0).getPrice()).isEqualTo(new BigDecimal("19.99"));
    }
    
    @Test
    void whenFindByPriceRange_thenReturnMatchingProducts() {
        // Given
        Product cheap = new Product("Cheap Product", new BigDecimal("5.00"), "Cheap");
        Product expensive = new Product("Expensive Product", new BigDecimal("50.00"), "Expensive");
        Product medium = new Product("Medium Product", new BigDecimal("25.00"), "Medium");
        
        entityManager.persist(cheap);
        entityManager.persist(expensive);
        entityManager.persist(medium);
        entityManager.flush();
        
        // When
        List<Product> found = productRepository.findByPriceBetween(
            new BigDecimal("10.00"), new BigDecimal("30.00"));
        
        // Then
        assertThat(found).hasSize(1);
        assertThat(found.get(0).getName()).isEqualTo("Medium Product");
    }
    
    @Test
    void whenDeleteByCategory_thenProductsAreRemoved() {
        // Given
        Category electronics = new Category("Electronics");
        Category books = new Category("Books");
        entityManager.persist(electronics);
        entityManager.persist(books);
        
        Product phone = new Product("Phone", new BigDecimal("500.00"), "Smartphone");
        phone.setCategory(electronics);
        Product laptop = new Product("Laptop", new BigDecimal("1000.00"), "Gaming Laptop");
        laptop.setCategory(electronics);
        Product book = new Product("Java Book", new BigDecimal("40.00"), "Programming Book");
        book.setCategory(books);
        
        entityManager.persist(phone);
        entityManager.persist(laptop);
        entityManager.persist(book);
        entityManager.flush();
        
        // When
        int deletedCount = productRepository.deleteByCategoryId(electronics.getId());
        
        // Then
        assertThat(deletedCount).isEqualTo(2);
        
        List<Product> remaining = productRepository.findAll();
        assertThat(remaining).hasSize(1);
        assertThat(remaining.get(0).getName()).isEqualTo("Java Book");
    }
    
    @Test
    void testCustomQuery_findTopSellingProducts() {
        // Given - Create products with different sales
        Product product1 = createProductWithSales("Product 1", 100);
        Product product2 = createProductWithSales("Product 2", 200);
        Product product3 = createProductWithSales("Product 3", 50);
        
        // When
        List<Product> topSelling = productRepository.findTopSellingProducts(PageRequest.of(0, 2));
        
        // Then
        assertThat(topSelling).hasSize(2);
        assertThat(topSelling.get(0).getName()).isEqualTo("Product 2"); // Highest sales
        assertThat(topSelling.get(1).getName()).isEqualTo("Product 1"); // Second highest
    }
    
    private Product createProductWithSales(String name, int salesCount) {
        Product product = new Product(name, new BigDecimal("10.00"), "Description");
        product.setSalesCount(salesCount);
        return entityManager.persistAndFlush(product);
    }
}

// Testing custom repository implementation
@DataJpaTest
class CustomProductRepositoryImplTest {
    
    @Autowired
    private TestEntityManager entityManager;
    
    @Autowired
    private ProductRepository productRepository;
    
    @Test
    void testCustomSearchMethod() {
        // Given
        Category electronics = new Category("Electronics");
        entityManager.persist(electronics);
        
        Product product1 = new Product("iPhone 13", new BigDecimal("999.00"), "Latest iPhone");
        product1.setCategory(electronics);
        product1.setTags(Set.of("apple", "smartphone", "premium"));
        
        Product product2 = new Product("Samsung Galaxy", new BigDecimal("899.00"), "Android phone");
        product2.setCategory(electronics);
        product2.setTags(Set.of("samsung", "android", "smartphone"));
        
        entityManager.persist(product1);
        entityManager.persist(product2);
        entityManager.flush();
        
        // When
        ProductSearchCriteria criteria = ProductSearchCriteria.builder()
            .name("phone")
            .minPrice(new BigDecimal("800.00"))
            .maxPrice(new BigDecimal("1000.00"))
            .categoryId(electronics.getId())
            .tags(Set.of("smartphone"))
            .build();
        
        List<Product> results = productRepository.searchProducts(criteria);
        
        // Then
        assertThat(results).hasSize(2);
        assertThat(results).extracting(Product::getName)
            .containsExactlyInAnyOrder("iPhone 13", "Samsung Galaxy");
    }
}
```

### Testing JPA Relationships and Cascading

```java
@DataJpaTest
class OrderRepositoryTest {
    
    @Autowired
    private TestEntityManager entityManager;
    
    @Autowired
    private OrderRepository orderRepository;
    
    @Test
    void testCascadingOperations() {
        // Given
        Customer customer = new Customer("John Doe", "john@example.com");
        entityManager.persist(customer);
        
        Order order = new Order();
        order.setCustomer(customer);
        order.setOrderDate(LocalDateTime.now());
        order.setStatus(OrderStatus.PENDING);
        
        OrderItem item1 = new OrderItem("Product A", 2, new BigDecimal("10.00"));
        OrderItem item2 = new OrderItem("Product B", 1, new BigDecimal("20.00"));
        
        order.addItem(item1);
        order.addItem(item2);
        
        // When
        Order savedOrder = orderRepository.save(order);
        entityManager.flush();
        entityManager.clear();
        
        // Then
        Order foundOrder = orderRepository.findById(savedOrder.getId()).orElseThrow();
        assertThat(foundOrder.getItems()).hasSize(2);
        assertThat(foundOrder.getTotalAmount()).isEqualTo(new BigDecimal("40.00"));
        
        // Test cascade delete
        orderRepository.delete(foundOrder);
        entityManager.flush();
        
        // Order items should be deleted due to cascade
        assertThat(entityManager.find(OrderItem.class, item1.getId())).isNull();
        assertThat(entityManager.find(OrderItem.class, item2.getId())).isNull();
    }
    
    @Test
    void testLazyLoadingInTest() {
        // Given
        Customer customer = new Customer("Jane Doe", "jane@example.com");
        entityManager.persist(customer);
        
        Order order = new Order();
        order.setCustomer(customer);
        order.setOrderDate(LocalDateTime.now());
        
        OrderItem item = new OrderItem("Test Product", 1, new BigDecimal("15.00"));
        order.addItem(item);
        
        entityManager.persistAndFlush(order);
        entityManager.clear();
        
        // When - Test lazy loading
        Order foundOrder = orderRepository.findById(order.getId()).orElseThrow();
        
        // Then - This should trigger lazy loading
        assertThat(foundOrder.getItems()).isNotEmpty();
        assertThat(foundOrder.getItems().size()).isEqualTo(1);
    }
    
    @Test
    void testNPlusOneProblem() {
        // Given - Create multiple orders
        Customer customer = new Customer("Test Customer", "test@example.com");
        entityManager.persist(customer);
        
        List<Order> orders = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            Order order = new Order();
            order.setCustomer(customer);
            order.setOrderDate(LocalDateTime.now());
            
            OrderItem item = new OrderItem("Product " + i, 1, new BigDecimal("10.00"));
            order.addItem(item);
            
            orders.add(order);
            entityManager.persist(order);
        }
        entityManager.flush();
        entityManager.clear();
        
        // When - Find orders without join fetch (N+1 problem)
        List<Order> foundOrders = orderRepository.findByCustomerId(customer.getId());
        
        // Then - Accessing items will trigger N+1 queries
        for (Order order : foundOrders) {
            assertThat(order.getItems()).isNotEmpty(); // This triggers additional queries
        }
        
        // Better approach - use fetch join
        List<Order> ordersWithItems = orderRepository.findByCustomerIdWithItems(customer.getId());
        assertThat(ordersWithItems).hasSize(5);
        
        for (Order order : ordersWithItems) {
            assertThat(order.getItems()).isNotEmpty(); // No additional queries
        }
    }
}

// Testing Specifications
@DataJpaTest
class ProductSpecificationTest {
    
    @Autowired
    private TestEntityManager entityManager;
    
    @Autowired
    private ProductRepository productRepository;
    
    @BeforeEach
    void setUp() {
        Category electronics = new Category("Electronics");
        Category books = new Category("Books");
        entityManager.persist(electronics);
        entityManager.persist(books);
        
        // Create test products
        Product phone = new Product("iPhone", new BigDecimal("999.00"), "Smartphone");
        phone.setCategory(electronics);
        phone.setActive(true);
        
        Product laptop = new Product("MacBook", new BigDecimal("1299.00"), "Laptop");
        laptop.setCategory(electronics);
        laptop.setActive(true);
        
        Product book = new Product("Java Guide", new BigDecimal("49.99"), "Programming book");
        book.setCategory(books);
        book.setActive(false);
        
        entityManager.persist(phone);
        entityManager.persist(laptop);
        entityManager.persist(book);
        entityManager.flush();
    }
    
    @Test
    void testProductSpecifications() {
        // Test individual specifications
        Specification<Product> activeSpec = ProductSpecifications.isActive();
        List<Product> activeProducts = productRepository.findAll(activeSpec);
        assertThat(activeProducts).hasSize(2);
        
        Specification<Product> priceSpec = ProductSpecifications.priceGreaterThan(new BigDecimal("100.00"));
        List<Product> expensiveProducts = productRepository.findAll(priceSpec);
        assertThat(expensiveProducts).hasSize(2);
        
        // Test combined specifications
        Specification<Product> combined = activeSpec.and(priceSpec);
        List<Product> activeExpensiveProducts = productRepository.findAll(combined);
        assertThat(activeExpensiveProducts).hasSize(2);
        
        // Test with sorting and pagination
        Pageable pageable = PageRequest.of(0, 1, Sort.by("price").descending());
        Page<Product> page = productRepository.findAll(combined, pageable);
        
        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).getName()).isEqualTo("MacBook");
        assertThat(page.getTotalElements()).isEqualTo(2);
    }
    
    @Test
    void testDynamicSpecifications() {
        // Build specification dynamically
        ProductSearchCriteria criteria = ProductSearchCriteria.builder()
            .name("book")
            .minPrice(new BigDecimal("40.00"))
            .maxPrice(new BigDecimal("60.00"))
            .includeInactive(true)
            .build();
        
        Specification<Product> spec = ProductSpecifications.fromCriteria(criteria);
        List<Product> results = productRepository.findAll(spec);
        
        assertThat(results).hasSize(1);
        assertThat(results.get(0).getName()).isEqualTo("Java Guide");
    }
}
```

## 10.2 Integration Testing with @SpringBootTest

### Full Application Context Testing

```java
@SpringBootTest
@Testcontainers
@Transactional
class ProductServiceIntegrationTest {
    
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:13")
        .withDatabaseName("integration_test")
        .withUsername("test")
        .withPassword("test");
    
    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
    }
    
    @Autowired
    private ProductService productService;
    
    @Autowired
    private CategoryService categoryService;
    
    @Autowired
    private ProductRepository productRepository;
    
    @Autowired
    private TestRestTemplate restTemplate;
    
    @Test
    void testProductCreationWithCategory() {
        // Given
        CreateCategoryRequest categoryRequest = new CreateCategoryRequest("Electronics", "Electronic devices");
        Category category = categoryService.createCategory(categoryRequest);
        
        CreateProductRequest productRequest = CreateProductRequest.builder()
            .name("Test Smartphone")
            .price(new BigDecimal("599.99"))
            .description("Latest smartphone")
            .categoryId(category.getId())
            .build();
        
        // When
        Product product = productService.createProduct(productRequest);
        
        // Then
        assertThat(product.getId()).isNotNull();
        assertThat(product.getName()).isEqualTo("Test Smartphone");
        assertThat(product.getCategory().getId()).isEqualTo(category.getId());
        
        // Verify persistence
        Optional<Product> saved = productRepository.findById(product.getId());
        assertThat(saved).isPresent();
        assertThat(saved.get().getCategory().getName()).isEqualTo("Electronics");
    }
    
    @Test
    void testProductSearchWithPagination() {
        // Given - Create multiple products
        Category category = categoryService.createCategory(
            new CreateCategoryRequest("Test Category", "Description"));
        
        for (int i = 1; i <= 25; i++) {
            CreateProductRequest request = CreateProductRequest.builder()
                .name("Product " + i)
                .price(new BigDecimal(i * 10))
                .description("Description " + i)
                .categoryId(category.getId())
                .build();
            productService.createProduct(request);
        }
        
        // When
        PageRequest pageRequest = PageRequest.of(0, 10, Sort.by("name"));
        Page<Product> firstPage = productService.findAllProducts(pageRequest);
        
        // Then
        assertThat(firstPage.getContent()).hasSize(10);
        assertThat(firstPage.getTotalElements()).isEqualTo(25);
        assertThat(firstPage.getTotalPages()).isEqualTo(3);
        assertThat(firstPage.isFirst()).isTrue();
        assertThat(firstPage.hasNext()).isTrue();
        
        // Test second page
        PageRequest secondPageRequest = PageRequest.of(1, 10, Sort.by("name"));
        Page<Product> secondPage = productService.findAllProducts(secondPageRequest);
        
        assertThat(secondPage.getContent()).hasSize(10);
        assertThat(secondPage.isFirst()).isFalse();
        assertThat(secondPage.hasNext()).isTrue();
    }
    
    @Test
    void testTransactionalBehavior() {
        // Given
        CreateCategoryRequest categoryRequest = new CreateCategoryRequest("Test", "Test category");
        Category category = categoryService.createCategory(categoryRequest);
        
        CreateProductRequest validProduct = CreateProductRequest.builder()
            .name("Valid Product")
            .price(new BigDecimal("10.00"))
            .categoryId(category.getId())
            .build();
        
        CreateProductRequest invalidProduct = CreateProductRequest.builder()
            .name("") // Invalid - empty name
            .price(new BigDecimal("-10.00")) // Invalid - negative price
            .categoryId(category.getId())
            .build();
        
        // When & Then - Valid product creation should succeed
        Product product = productService.createProduct(validProduct);
        assertThat(product.getId()).isNotNull();
        
        // Invalid product creation should fail and rollback
        assertThatThrownBy(() -> productService.createProduct(invalidProduct))
            .isInstanceOf(ValidationException.class);
        
        // Verify the valid product still exists
        assertThat(productRepository.findById(product.getId())).isPresent();
    }
}

// Testing with Profiles
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(locations = "classpath:application-test.properties")
class ProductServiceProfileTest {
    
    @Autowired
    private ProductService productService;
    
    @MockBean
    private ExternalPricingService pricingService;
    
    @Test
    void testProductCreationWithMockedExternalService() {
        // Given
        when(pricingService.calculatePrice(any()))
            .thenReturn(new BigDecimal("99.99"));
        
        CreateProductRequest request = CreateProductRequest.builder()
            .name("Test Product")
            .basePrice(new BigDecimal("80.00"))
            .build();
        
        // When
        Product product = productService.createProduct(request);
        
        // Then
        assertThat(product.getPrice()).isEqualTo(new BigDecimal("99.99"));
        verify(pricingService).calculatePrice(argThat(req -> 
            req.getBasePrice().equals(new BigDecimal("80.00"))));
    }
}
```

### Multi-Database Integration Testing

```java
@SpringBootTest
@Testcontainers
class MultiDatabaseIntegrationTest {
    
    @Container
    static PostgreSQLContainer<?> primaryDb = new PostgreSQLContainer<>("postgres:13")
        .withDatabaseName("primary")
        .withUsername("test")
        .withPassword("test");
    
    @Container
    static MySQLContainer<?> secondaryDb = new MySQLContainer<>("mysql:8.0")
        .withDatabaseName("secondary")
        .withUsername("test")
        .withPassword("test");
    
    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.primary.url", primaryDb::getJdbcUrl);
        registry.add("spring.datasource.primary.username", primaryDb::getUsername);
        registry.add("spring.datasource.primary.password", primaryDb::getPassword);
        
        registry.add("spring.datasource.secondary.url", secondaryDb::getJdbcUrl);
        registry.add("spring.datasource.secondary.username", secondaryDb::getUsername);
        registry.add("spring.datasource.secondary.password", secondaryDb::getPassword);
    }
    
    @Autowired
    private UserService userService;
    
    @Autowired
    private UserActivityService activityService;
    
    @Test
    void testCrossDBTransaction() {
        // Given
        CreateUserRequest userRequest = CreateUserRequest.builder()
            .username("testuser")
            .email("test@example.com")
            .password("password123")
            .build();
        
        // When
        User user = userService.createUser(userRequest);
        
        // Then - User should be created in primary database
        assertThat(user.getId()).isNotNull();
        assertThat(user.getUsername()).isEqualTo("testuser");
        
        // Activity should be logged in secondary database
        List<UserActivity> activities = activityService.getUserActivities(user.getId());
        assertThat(activities).hasSize(1);
        assertThat(activities.get(0).getActivity()).isEqualTo("USER_CREATED");
    }
    
    @Test
    void testTransactionFailureHandling() {
        // Given - Create a scenario where secondary DB operation fails
        CreateUserRequest userRequest = CreateUserRequest.builder()
            .username("failuser")
            .email("fail@example.com")
            .password("password123")
            .build();
        
        // Mock the activity service to throw an exception
        // This would test the rollback behavior across databases
        
        // Implementation depends on your transaction configuration
        // (JTA, ChainedTransactionManager, etc.)
    }
}
```

## 10.3 Performance Testing

### Query Performance Testing

```java
@SpringBootTest
@Testcontainers
class ProductRepositoryPerformanceTest {
    
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:13")
        .withDatabaseName("perf_test")
        .withUsername("test")
        .withPassword("test");
    
    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.jpa.properties.hibernate.generate_statistics", () -> "true");
    }
    
    @Autowired
    private ProductRepository productRepository;
    
    @Autowired
    private CategoryRepository categoryRepository;
    
    @Autowired
    private TestEntityManager entityManager;
    
    private SessionFactory sessionFactory;
    
    @PostConstruct
    void setUp() {
        sessionFactory = entityManager.getEntityManager()
            .getEntityManagerFactory()
            .unwrap(SessionFactory.class);
    }
    
    @BeforeEach
    void clearStatistics() {
        sessionFactory.getStatistics().clear();
    }
    
    @Test
    void testNPlusOneProblem() {
        // Given - Create test data
        setupTestData(10, 5); // 10 categories, 5 products each
        
        // When - Query without fetch join (N+1 problem)
        Statistics stats = sessionFactory.getStatistics();
        stats.clear();
        
        List<Product> products = productRepository.findAll();
        for (Product product : products) {
            product.getCategory().getName(); // Trigger lazy loading
        }
        
        long queriesWithoutFetchJoin = stats.getPrepareStatementCount();
        
        // Then - Clear and test with fetch join
        stats.clear();
        
        List<Product> productsWithCategory = productRepository.findAllWithCategory();
        for (Product product : productsWithCategory) {
            product.getCategory().getName(); // No additional queries
        }
        
        long queriesWithFetchJoin = stats.getPrepareStatementCount();
        
        // Verify fetch join reduces query count
        assertThat(queriesWithFetchJoin).isLessThan(queriesWithoutFetchJoin);
        assertThat(queriesWithFetchJoin).isEqualTo(1); // Should be just one query
    }
    
    @Test
    void testBatchProcessingPerformance() {
        // Given
        List<Product> products = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            products.add(new Product("Product " + i, new BigDecimal("10.00"), "Description"));
        }
        
        Statistics stats = sessionFactory.getStatistics();
        stats.clear();
        
        long startTime = System.currentTimeMillis();
        
        // When - Test batch insert
        productRepository.saveAll(products);
        productRepository.flush();
        
        long endTime = System.currentTimeMillis();
        
        // Then
        long executionTime = endTime - startTime;
        long insertStatements = stats.getEntityInsertCount();
        
        assertThat(insertStatements).isEqualTo(1000);
        assertThat(executionTime).isLessThan(5000); // Should complete within 5 seconds
        
        // Log performance metrics
        System.out.println("Batch insert time: " + executionTime + "ms");
        System.out.println("Statements executed: " + stats.getPrepareStatementCount());
    }
    
    @Test
    void testPaginationPerformance() {
        // Given
        setupTestData(5, 200); // 5 categories, 200 products each = 1000 products
        
        Statistics stats = sessionFactory.getStatistics();
        
        // When - Test different pagination strategies
        Pageable firstPage = PageRequest.of(0, 20);
        Pageable middlePage = PageRequest.of(25, 20); // Page 25 of 50
        Pageable lastPage = PageRequest.of(49, 20); // Last page
        
        // Test first page performance
        stats.clear();
        long startTime = System.currentTimeMillis();
        Page<Product> firstPageResult = productRepository.findAll(firstPage);
        long firstPageTime = System.currentTimeMillis() - startTime;
        
        // Test middle page performance
        stats.clear();
        startTime = System.currentTimeMillis();
        Page<Product> middlePageResult = productRepository.findAll(middlePage);
        long middlePageTime = System.currentTimeMillis() - startTime;
        
        // Test last page performance
        stats.clear();
        startTime = System.currentTimeMillis();
        Page<Product> lastPageResult = productRepository.findAll(lastPage);
        long lastPageTime = System.currentTimeMillis() - startTime;
        
        // Then
        assertThat(firstPageResult.getContent()).hasSize(20);
        assertThat(middlePageResult.getContent()).hasSize(20);
        assertThat(lastPageResult.getContent()).hasSize(20);
        
        // Log performance comparison
        System.out.println("First page time: " + firstPageTime + "ms");
        System.out.println("Middle page time: " + middlePageTime + "ms");
        System.out.println("Last page time: " + lastPageTime + "ms");
        
        // Generally, OFFSET performance degrades with larger offsets
        // Consider using cursor-based pagination for better performance
    }
    
    private void setupTestData(int categoryCount, int productsPerCategory) {
        List<Category> categories = new ArrayList<>();
        for (int i = 0; i < categoryCount; i++) {
            categories.add(new Category("Category " + i, "Description " + i));
        }
        categoryRepository.saveAll(categories);
        
        List<Product> products = new ArrayList<>();
        for (Category category : categories) {
            for (int j = 0; j < productsPerCategory; j++) {
                Product product = new Product(
                    category.getName() + " Product " + j,
                    new BigDecimal("10.00"),
                    "Description"
                );
                product.setCategory(category);
                products.add(product);
            }
        }
        productRepository.saveAll(products);
    }
}
```

### Load Testing with Custom Metrics

```java
@SpringBootTest
@ExtendWith(MockitoExtension.class)
class ProductServiceLoadTest {
    
    @Autowired
    private ProductService productService;
    
    @MockBean
    private ExternalInventoryService inventoryService;
    
    private ExecutorService executorService;
    
    @BeforeEach
    void setUp() {
        executorService = Executors.newFixedThreadPool(10);
        when(inventoryService.checkStock(anyString())).thenReturn(true);
    }
    
    @AfterEach
    void tearDown() {
        executorService.shutdown();
    }
    
    @Test
    void testConcurrentProductCreation() throws InterruptedException {
        // Given
        int numberOfThreads = 10;
        int productsPerThread = 10;
        CountDownLatch latch = new CountDownLatch(numberOfThreads);
        List<Future<List<Product>>> futures = new ArrayList<>();
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);
        
        // When - Create products concurrently
        for (int i = 0; i < numberOfThreads; i++) {
            final int threadId = i;
            Future<List<Product>> future = executorService.submit(() -> {
                List<Product> createdProducts = new ArrayList<>();
                try {
                    for (int j = 0; j < productsPerThread; j++) {
                        CreateProductRequest request = CreateProductRequest.builder()
                            .name("Product-" + threadId + "-" + j)
                            .price(new BigDecimal("10.00"))
                            .description("Concurrent test product")
                            .build();
                        
                        Product product = productService.createProduct(request);
                        createdProducts.add(product);
                        successCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    errorCount.incrementAndGet();
                    e.printStackTrace();
                } finally {
                    latch.countDown();
                }
                return createdProducts;
            });
            futures.add(future);
        }
        
        // Wait for all threads to complete
        boolean completed = latch.await(30, TimeUnit.SECONDS);
        
        // Then
        assertThat(completed).isTrue();
        assertThat(successCount.get()).isEqualTo(numberOfThreads * productsPerThread);
        assertThat(errorCount.get()).isEqualTo(0);
        
        // Verify all products were created
        List<Product> allProducts = productService.findAllProducts(Pageable.unpaged()).getContent();
        assertThat(allProducts.size()).isGreaterThanOrEqualTo(numberOfThreads * productsPerThread);
    }
    
    @Test
    void testDatabaseConnectionPoolUnderLoad() throws InterruptedException {
        // Given - Simulate high database load
        int numberOfRequests = 50;
        CountDownLatch latch = new CountDownLatch(numberOfRequests);
        List<Long> responseTimes = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger timeouts = new AtomicInteger(0);
        
        // When
        for (int i = 0; i < numberOfRequests; i++) {
            final int requestId = i;
            executorService.submit(() -> {
                try {
                    long startTime = System.currentTimeMillis();
                    
                    // Simulate various database operations
                    List<Product> products = productService.findProductsByPriceRange(
                        new BigDecimal("0.00"), new BigDecimal("1000.00"));
                    
                    long endTime = System.currentTimeMillis();
                    responseTimes.add(endTime - startTime);
                    
                } catch (Exception e) {
                    if (e.getCause() instanceof SQLException) {
                        timeouts.incrementAndGet();
                    }
                } finally {
                    latch.countDown();
                }
            });
        }
        
        boolean completed = latch.await(60, TimeUnit.SECONDS);
        
        // Then
        assertThat(completed).isTrue();
        
        // Analyze performance
        double avgResponseTime = responseTimes.stream()
            .mapToLong(Long::longValue)
            .average()
            .orElse(0.0);
        
        long maxResponseTime = responseTimes.stream()
            .mapToLong(Long::longValue)
            .max()
            .orElse(0L);
        
        System.out.println("Average response time: " + avgResponseTime + "ms");
        System.out.println("Max response time: " + maxResponseTime + "ms");
        System.out.println("Timeout count: " + timeouts.get());
        
        // Performance assertions
        assertThat(avgResponseTime).isLessThan(1000); // Average should be under 1 second
        assertThat(timeouts.get()).isEqualTo(0); // No timeouts should occur
    }
}
```

## 10.4 Test Data Management

### Using @Sql for Data Setup

```java
@SpringBootTest
@Testcontainers
@Sql(scripts = "/test-data.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(scripts = "/cleanup.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class ProductServiceDataTest {
    
    @Autowired
    private ProductService productService;
    
    @Test
    void testProductSearchWithPreloadedData() {
        // Given - Data loaded from test-data.sql
        
        // When
        List<Product> electronicsProducts = productService.findByCategory("Electronics");
        List<Product> booksProducts = productService.findByCategory("Books");
        
        // Then
        assertThat(electronicsProducts).isNotEmpty();
        assertThat(booksProducts).isNotEmpty();
        
        // Verify specific products from test data
        assertThat(electronicsProducts)
            .extracting(Product::getName)
            .contains("Test Laptop", "Test Phone");
    }
}

// test-data.sql
/*
INSERT INTO categories (id, name, description) VALUES 
(1, 'Electronics', 'Electronic devices'),
(2, 'Books', 'Books and literature');

INSERT INTO products (id, name, price, description, category_id) VALUES
(1, 'Test Laptop', 999.99, 'Test laptop for integration testing', 1),
(2, 'Test Phone', 599.99, 'Test smartphone for integration testing', 1),
(3, 'Test Book', 29.99, 'Test programming book', 2);
*/

// cleanup.sql
/*
DELETE FROM products;
DELETE FROM categories;
*/
```

### Test Data Builders and Factories

```java
// Product test data builder
public class ProductTestDataBuilder {
    private String name = "Test Product";
    private BigDecimal price = new BigDecimal("10.00");
    private String description = "Test description";
    private Category category;
    private boolean active = true;
    private LocalDateTime createdAt = LocalDateTime.now();
    
    public static ProductTestDataBuilder aProduct() {
        return new ProductTestDataBuilder();
    }
    
    public ProductTestDataBuilder withName(String name) {
        this.name = name;
        return this;
    }
    
    public ProductTestDataBuilder withPrice(BigDecimal price) {
        this.price = price;
        return this;
    }
    
    public ProductTestDataBuilder withDescription(String description) {
        this.description = description;
        return this;
    }
    
    public ProductTestDataBuilder withCategory(Category category) {
        this.category = category;
        return this;
    }
    
    public ProductTestDataBuilder inactive() {
        this.active = false;
        return this;
    }
    
    public ProductTestDataBuilder createdAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
        return this;
    }
    
    public Product build() {
        Product product = new Product();
        product.setName(name);
        product.setPrice(price);
        product.setDescription(description);
        product.setCategory(category);
        product.setActive(active);
        product.setCreatedAt(createdAt);
        return product;
    }
    
    public CreateProductRequest buildRequest() {
        return CreateProductRequest.builder()
            .name(name)
            .price(price)
            .description(description)
            .categoryId(category != null ? category.getId() : null)
            .build();
    }
}

// Usage in tests
@DataJpaTest
class ProductTestDataBuilderUsageTest {
    
    @Autowired
    private TestEntityManager entityManager;
    
    @Test
    void testUsingDataBuilder() {
        // Given
        Category electronics = new Category("Electronics", "Electronic devices");
        entityManager.persist(electronics);
        
        Product expensiveProduct = ProductTestDataBuilder.aProduct()
            .withName("Expensive Gadget")
            .withPrice(new BigDecimal("999.99"))
            .withCategory(electronics)
            .build();
        
        Product cheapProduct = ProductTestDataBuilder.aProduct()
            .withName("Cheap Item")
            .withPrice(new BigDecimal("5.99"))
            .withCategory(electronics)
            .inactive()
            .build();
        
        entityManager.persist(expensiveProduct);
        entityManager.persist(cheapProduct);
        entityManager.flush();
        
        // When & Then
        assertThat(expensiveProduct.getName()).isEqualTo("Expensive Gadget");
        assertThat(expensiveProduct.getPrice()).isEqualTo(new BigDecimal("999.99"));
        assertThat(expensiveProduct.isActive()).isTrue();
        
        assertThat(cheapProduct.getName()).isEqualTo("Cheap Item");
        assertThat(cheapProduct.isActive()).isFalse();
    }
}
```

### Database State Management for Tests

```java
// Custom test execution listener for database state
public class DatabaseStateTestExecutionListener implements TestExecutionListener {
    
    @Override
    public void beforeTestMethod(TestContext testContext) throws Exception {
        // Save database state before test
        ApplicationContext context = testContext.getApplicationContext();
        DatabaseStateManager stateManager = context.getBean(DatabaseStateManager.class);
        stateManager.saveState(testContext.getTestMethod().getName());
    }
    
    @Override
    public void afterTestMethod(TestContext testContext) throws Exception {
        // Restore database state after test
        ApplicationContext context = testContext.getApplicationContext();
        DatabaseStateManager stateManager = context.getBean(DatabaseStateManager.class);
        stateManager.restoreState(testContext.getTestMethod().getName());
    }
}

@Component
@Profile("test")
public class DatabaseStateManager {
    
    @Autowired
    private JdbcTemplate jdbcTemplate;
    
    private Map<String, List<String>> savedStates = new ConcurrentHashMap<>();
    
    public void saveState(String stateName) {
        List<String> tableData = new ArrayList<>();
        
        // Get all table names
        List<String> tableNames = jdbcTemplate.queryForList(
            "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'",
            String.class);
        
        // Save data from each table
        for (String tableName : tableNames) {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList("SELECT * FROM " + tableName);
            // Convert to INSERT statements
            for (Map<String, Object> row : rows) {
                String insertStatement = buildInsertStatement(tableName, row);
                tableData.add(insertStatement);
            }
        }
        
        savedStates.put(stateName, tableData);
    }
    
    public void restoreState(String stateName) {
        if (!savedStates.containsKey(stateName)) {
            return;
        }
        
        // Clear all tables
        clearAllTables();
        
        // Restore data
        List<String> insertStatements = savedStates.get(stateName);
        for (String statement : insertStatements) {
            jdbcTemplate.execute(statement);
        }
    }
    
    private void clearAllTables() {
        // Disable foreign key constraints
        jdbcTemplate.execute("SET REFERENTIAL_INTEGRITY FALSE");
        
        // Get all table names and clear them
        List<String> tableNames = jdbcTemplate.queryForList(
            "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'",
            String.class);
        
        for (String tableName : tableNames) {
            jdbcTemplate.execute("DELETE FROM " + tableName);
        }
        
        // Re-enable foreign key constraints
        jdbcTemplate.execute("SET REFERENTIAL_INTEGRITY TRUE");
    }
    
    private String buildInsertStatement(String tableName, Map<String, Object> row) {
        StringBuilder columns = new StringBuilder();
        StringBuilder values = new StringBuilder();
        
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            if (columns.length() > 0) {
                columns.append(", ");
                values.append(", ");
            }
            columns.append(entry.getKey());
            values.append("'").append(entry.getValue()).append("'");
        }
        
        return String.format("INSERT INTO %s (%s) VALUES (%s)", 
                           tableName, columns.toString(), values.toString());
    }
}
```

## 10.5 Test Slices and Custom Test Configuration

### Custom Test Slice for Repository Layer

```java
// Custom test slice annotation
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@BootstrapWith(CustomRepositoryTestContextBootstrapper.class)
@ExtendWith(SpringExtension.class)
@OverrideAutoConfiguration(enabled = false)
@TypeExcludeFilters(CustomRepositoryTypeExcludeFilter.class)
@Transactional
@AutoConfigureBatabase
@AutoConfigureJpa
@AutoConfigureTestEntityManager
@ImportAutoConfiguration
public @interface CustomRepositoryTest {
    
    @AliasFor(annotation = ImportAutoConfiguration.class, attribute = "exclude")
    Class<?>[] excludeAutoConfiguration() default {};
    
    @AliasFor(annotation = ImportAutoConfiguration.class, attribute = "classes")
    Class<?>[] includeAutoConfiguration() default {};
    
    boolean showSql() default false;
}

// Usage of custom test slice
@CustomRepositoryTest(showSql = true)
@TestPropertySource(properties = {
    "spring.jpa.show-sql=true",
    "spring.jpa.properties.hibernate.format_sql=true"
})
class CustomRepositorySliceTest {
    
    @Autowired
    private TestEntityManager entityManager;
    
    @Autowired
    private ProductRepository productRepository;
    
    @Test
    void testCustomRepositoryMethod() {
        // Test implementation using custom slice
        Product product = new Product("Test", new BigDecimal("10.00"), "Description");
        entityManager.persist(product);
        
        List<Product> found = productRepository.findByNameContainingIgnoreCase("test");
        assertThat(found).hasSize(1);
    }
}
```

### Mock Configuration for External Dependencies

```java
@TestConfiguration
public class MockExternalServicesConfig {
    
    @Bean
    @Primary
    public ExternalPricingService mockPricingService() {
        ExternalPricingService mock = mock(ExternalPricingService.class);
        
        // Default behavior
        when(mock.calculatePrice(any())).thenAnswer(invocation -> {
            PriceCalculationRequest request = invocation.getArgument(0);
            return request.getBasePrice().multiply(new BigDecimal("1.1")); // 10% markup
        });
        
        return mock;
    }
    
    @Bean
    @Primary
    public ExternalInventoryService mockInventoryService() {
        ExternalInventoryService mock = mock(ExternalInventoryService.class);
        
        // Default behavior - always in stock
        when(mock.checkStock(anyString())).thenReturn(true);
        when(mock.reserveStock(anyString(), anyInt())).thenReturn(true);
        when(mock.getAvailableQuantity(anyString())).thenReturn(100);
        
        return mock;
    }
    
    @Bean
    @Primary
    public NotificationService mockNotificationService() {
        return mock(NotificationService.class);
    }
}

// Test using mock configuration
@SpringBootTest
@Import(MockExternalServicesConfig.class)
class ProductServiceWithMocksTest {
    
    @Autowired
    private ProductService productService;
    
    @Autowired
    private ExternalPricingService pricingService;
    
    @Autowired
    private ExternalInventoryService inventoryService;
    
    @Autowired
    private NotificationService notificationService;
    
    @Test
    void testProductCreationWithExternalServices() {
        // Given
        CreateProductRequest request = CreateProductRequest.builder()
            .name("Test Product")
            .basePrice(new BigDecimal("100.00"))
            .sku("TEST-001")
            .build();
        
        // When
        Product product = productService.createProduct(request);
        
        // Then
        assertThat(product.getPrice()).isEqualTo(new BigDecimal("110.00")); // 10% markup from mock
        
        // Verify external service interactions
        verify(pricingService).calculatePrice(argThat(req -> 
            req.getBasePrice().equals(new BigDecimal("100.00"))));
        verify(inventoryService).checkStock("TEST-001");
        verify(notificationService).sendProductCreatedNotification(eq(product.getId()));
    }
    
    @Test
    void testProductCreationWithInventoryFailure() {
        // Given
        when(inventoryService.checkStock("OUT-OF-STOCK")).thenReturn(false);
        
        CreateProductRequest request = CreateProductRequest.builder()
            .name("Out of Stock Product")
            .basePrice(new BigDecimal("50.00"))
            .sku("OUT-OF-STOCK")
            .build();
        
        // When & Then
        assertThatThrownBy(() -> productService.createProduct(request))
            .isInstanceOf(OutOfStockException.class)
            .hasMessageContaining("Product is out of stock");
        
        // Verify no notification was sent for failed creation
        verify(notificationService, never()).sendProductCreatedNotification(any());
    }
}
```

## 10.6 Advanced Testing Patterns

### Contract Testing with Spring Cloud Contract

```java
// Contract definition (Groovy DSL)
/*
Contract.make {
    description "Should return product by ID"
    request {
        method GET()
        url("/api/products/1")
        headers {
            contentType(applicationJson())
        }
    }
    response {
        status OK()
        body([
            id: 1,
            name: "Test Product",
            price: 19.99,
            description: "Test product description"
        ])
        headers {
            contentType(applicationJson())
        }
    }
}
*/

// Contract test base class
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@Import(MockExternalServicesConfig.class)
public abstract class ProductContractTestBase {
    
    @Autowired
    private WebApplicationContext context;
    
    @Autowired
    private ProductService productService;
    
    @BeforeEach
    void setUp() {
        RestAssuredMockMvc.webAppContextSetup(context);
        
        // Setup test data for contracts
        setupContractTestData();
    }
    
    private void setupContractTestData() {
        // Create test data that matches contract expectations
        when(productService.findById(1L)).thenReturn(
            Optional.of(new Product(1L, "Test Product", new BigDecimal("19.99"), "Test product description"))
        );
    }
}
```

### Mutation Testing Configuration

```java
// PIT mutation testing configuration
@Component
@Profile("test")
public class MutationTestConfiguration {
    
    // Custom mutators for JPA-specific code
    public static final String ENTITY_MUTATOR = "ENTITY_MUTATOR";
    public static final String REPOSITORY_MUTATOR = "REPOSITORY_MUTATOR";
    
    // Exclude certain classes from mutation testing
    public static List<String> getExcludedClasses() {
        return Arrays.asList(
            "com.example.entity.*",  // Don't mutate entity classes
            "com.example.config.*",  // Don't mutate configuration
            "com.example.*Test*",    // Don't mutate test classes
            "com.example.*TestData*" // Don't mutate test data builders
        );
    }
    
    // Target specific classes for mutation
    public static List<String> getTargetClasses() {
        return Arrays.asList(
            "com.example.service.*",
            "com.example.repository.impl.*",
            "com.example.util.*"
        );
    }
}
```

### Chaos Engineering for Database Tests

```java
@SpringBootTest
@Testcontainers
class ChaosEngineeringTest {
    
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:13")
        .withDatabaseName("chaos_test")
        .withUsername("test")
        .withPassword("test");
    
    @Autowired
    private ProductService productService;
    
    @Autowired
    private DataSource dataSource;
    
    @Test
    void testServiceBehaviorWithDatabaseInterruption() throws Exception {
        // Given - Create some initial data
        CreateProductRequest request = CreateProductRequest.builder()
            .name("Chaos Test Product")
            .price(new BigDecimal("25.00"))
            .build();
        
        Product product = productService.createProduct(request);
        assertThat(product.getId()).isNotNull();
        
        // When - Simulate database interruption
        CompletableFuture<Void> chaosTask = CompletableFuture.runAsync(() -> {
            try {
                Thread.sleep(1000); // Wait a bit
                postgres.stop(); // Stop the database container
                Thread.sleep(2000); // Keep it down for 2 seconds
                postgres.start(); // Restart it
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        
        // Then - Test service behavior during and after interruption
        List<Exception> exceptions = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch latch = new CountDownLatch(10);
        
        // Multiple threads trying to access the service
        for (int i = 0; i < 10; i++) {
            CompletableFuture.runAsync(() -> {
                try {
                    for (int j = 0; j < 5; j++) {
                        try {
                            productService.findById(product.getId());
                            Thread.sleep(100);
                        } catch (Exception e) {
                            exceptions.add(e);
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    latch.countDown();
                }
            });
        }
        
        boolean completed = latch.await(30, TimeUnit.SECONDS);
        chaosTask.get(30, TimeUnit.SECONDS);
        
        // Verify the system recovers
        assertThat(completed).isTrue();
        
        // After chaos, service should work normally
        Optional<Product> recovered = productService.findById(product.getId());
        assertThat(recovered).isPresent();
        assertThat(recovered.get().getName()).isEqualTo("Chaos Test Product");
        
        // Some exceptions are expected during the chaos
        System.out.println("Exceptions during chaos: " + exceptions.size());
        exceptions.forEach(e -> System.out.println("Exception: " + e.getClass().getSimpleName()));
    }
}
```

## 10.7 Test Reporting and Coverage

### Custom Test Results Analysis

```java
@TestExecutionListener
public class TestMetricsCollector implements TestExecutionListener {
    
    private static final Map<String, TestMetrics> testMetrics = new ConcurrentHashMap<>();
    
    @Override
    public void beforeTestMethod(TestContext testContext) throws Exception {
        String testName = getTestName(testContext);
        TestMetrics metrics = new TestMetrics();
        metrics.setStartTime(System.currentTimeMillis());
        testMetrics.put(testName, metrics);
    }
    
    @Override
    public void afterTestMethod(TestContext testContext) throws Exception {
        String testName = getTestName(testContext);
        TestMetrics metrics = testMetrics.get(testName);
        
        if (metrics != null) {
            metrics.setEndTime(System.currentTimeMillis());
            metrics.setDuration(metrics.getEndTime() - metrics.getStartTime());
            metrics.setSuccess(!testContext.getTestException().isPresent());
            
            // Collect additional metrics if available
            ApplicationContext context = testContext.getApplicationContext();
            if (context.containsBean("sessionFactory")) {
                SessionFactory sessionFactory = context.getBean(SessionFactory.class);
                Statistics stats = sessionFactory.getStatistics();
                metrics.setQueryCount(stats.getQueryExecutionCount());
                metrics.setEntityLoadCount(stats.getEntityLoadCount());
            }
        }
    }
    
    @Override
    public void afterTestClass(TestContext testContext) throws Exception {
        generateTestReport();
    }
    
    private String getTestName(TestContext testContext) {
        return testContext.getTestClass().getSimpleName() + "." + 
               testContext.getTestMethod().getName();
    }
    
    private void generateTestReport() {
        System.out.println("\n=== Test Metrics Report ===");
        testMetrics.entrySet().stream()
            .sorted(Map.Entry.<String, TestMetrics>comparingByValue(
                (a, b) -> Long.compare(b.getDuration(), a.getDuration())))
            .forEach(entry -> {
                TestMetrics metrics = entry.getValue();
                System.out.printf("%-50s | %6dms | %3d queries | %s%n",
                    entry.getKey(),
                    metrics.getDuration(),
                    metrics.getQueryCount(),
                    metrics.isSuccess() ? "PASS" : "FAIL");
            });
        System.out.println("===========================\n");
    }
    
    public static class TestMetrics {
        private long startTime;
        private long endTime;
        private long duration;
        private boolean success;
        private long queryCount;
        private long entityLoadCount;
        
        // Getters and setters...
        public long getStartTime() { return startTime; }
        public void setStartTime(long startTime) { this.startTime = startTime; }
        
        public long getEndTime() { return endTime; }
        public void setEndTime(long endTime) { this.endTime = endTime; }
        
        public long getDuration() { return duration; }
        public void setDuration(long duration) { this.duration = duration; }
        
        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
        
        public long getQueryCount() { return queryCount; }
        public void setQueryCount(long queryCount) { this.queryCount = queryCount; }
        
        public long getEntityLoadCount() { return entityLoadCount; }
        public void setEntityLoadCount(long entityLoadCount) { this.entityLoadCount = entityLoadCount; }
    }
}
```

### Database Test Coverage Analysis

```java
@Component
@Profile("test")
public class DatabaseCoverageAnalyzer {
    
    @Autowired
    private JdbcTemplate jdbcTemplate;
    
    private Set<String> accessedTables = ConcurrentHashMap.newKeySet();
    private Set<String> accessedColumns = ConcurrentHashMap.newKeySet();
    
    @EventListener
    public void handleTestStart(TestStartEvent event) {
        accessedTables.clear();
        accessedColumns.clear();
    }
    
    @EventListener
    public void handleTestEnd(TestEndEvent event) {
        generateCoverageReport();
    }
    
    public void recordTableAccess(String tableName) {
        accessedTables.add(tableName);
    }
    
    public void recordColumnAccess(String tableName, String columnName) {
        accessedColumns.add(tableName + "." + columnName);
    }
    
    private void generateCoverageReport() {
        List<String> allTables = getAllTables();
        Map<String, List<String>> allColumns = getAllColumns();
        
        double tableCoverage = (double) accessedTables.size() / allTables.size() * 100;
        
        int totalColumns = allColumns.values().stream()
            .mapToInt(List::size)
            .sum();
        double columnCoverage = (double) accessedColumns.size() / totalColumns * 100;
        
        System.out.println("\n=== Database Coverage Report ===");
        System.out.printf("Table Coverage: %.1f%% (%d/%d)%n", 
            tableCoverage, accessedTables.size(), allTables.size());
        System.out.printf("Column Coverage: %.1f%% (%d/%d)%n", 
            columnCoverage, accessedColumns.size(), totalColumns);
        
        // Show uncovered tables
        List<String> uncoveredTables = allTables.stream()
            .filter(table -> !accessedTables.contains(table))
            .collect(Collectors.toList());
        
        if (!uncoveredTables.isEmpty()) {
            System.out.println("\nUncovered Tables:");
            uncoveredTables.forEach(table -> System.out.println("  - " + table));
        }
        
        System.out.println("================================\n");
    }
    
    private List<String> getAllTables() {
        return jdbcTemplate.queryForList(
            "SELECT table_name FROM information_schema.tables " +
            "WHERE table_schema = 'public' AND table_type = 'BASE TABLE'",
            String.class);
    }
    
    private Map<String, List<String>> getAllColumns() {
        List<Map<String, Object>> result = jdbcTemplate.queryForList(
            "SELECT table_name, column_name FROM information_schema.columns " +
            "WHERE table_schema = 'public'");
        
        return result.stream().collect(
            Collectors.groupingBy(
                row -> (String) row.get("table_name"),
                Collectors.mapping(
                    row -> (String) row.get("column_name"),
                    Collectors.toList())));
    }
}
```

## Key Testing Best Practices Summary

### 1. Test Strategy Pyramid
```
┌─────────────────────────┐
│    End-to-End Tests     │ ← Few, slow, expensive
├─────────────────────────┤
│   Integration Tests     │ ← Some, medium speed
├─────────────────────────┤
│     Unit Tests          │ ← Many, fast, cheap
└─────────────────────────┘
```

### 2. Testing Checklist for Spring Data JPA

```java
// Comprehensive test checklist
public class JPATestingChecklist {
    
    // ✅ Repository Tests
    // - Test basic CRUD operations
    // - Test custom query methods
    // - Test pagination and sorting
    // - Test specifications and criteria queries
    // - Test relationship mappings and cascading
    
    // ✅ Service Tests
    // - Test business logic with mocked repositories
    // - Test transaction boundaries
    // - Test error handling and rollback scenarios
    // - Test caching behavior
    
    // ✅ Integration Tests
    // - Test full application context
    // - Test with real database (Testcontainers)
    // - Test multi-database scenarios
    // - Test performance under load
    
    // ✅ Performance Tests
    // - Identify N+1 query problems
    // - Test batch operations
    // - Monitor connection pool usage
    // - Test pagination performance
    
    // ✅ Data Management
    // - Use test data builders
    // - Manage test database state
    // - Use @Sql for complex setup
    // - Clean up after tests
}
```

### 3. Common Testing Anti-patterns to Avoid

```java
// ❌ Don't do this - Testing implementation details
@Test
void testRepositoryInternals() {
    verify(entityManager).persist(any()); // Testing JPA internals
}

// ✅ Do this - Test behavior and outcomes
@Test
void testProductCreation() {
    Product product = productService.createProduct(request);
    assertThat(product.getId()).isNotNull();
    assertThat(productRepository.findById(product.getId())).isPresent();
}

// ❌ Don't do this - Fragile tests dependent on order
@Test
void testFindAll() {
    List<Product> products = repository.findAll();
    assertThat(products.get(0).getName()).isEqualTo("Expected First Product");
}

// ✅ Do this - Test invariants, not order
@Test
void testFindAll() {
    List<Product> products = repository.findAll();
    assertThat(products).hasSize(expectedCount);
    assertThat(products).extracting(Product::getName)
        .containsExactlyInAnyOrder("Product A", "Product B", "Product C");
}
```

This completes Phase 10: Testing. You now have comprehensive testing strategies covering unit tests, integration tests, performance testing, and enterprise patterns. The next phase would be Phase 11: Production Considerations, focusing on monitoring, database migrations, and security aspects for production deployments.
    # Phase 9: Enterprise Patterns - Spring Data JPA Mastery

## 9.1 Multi-tenancy

Multi-tenancy allows a single application instance to serve multiple tenants (customers/organizations) with data isolation. This is crucial for SaaS applications.

### Schema per Tenant Strategy

Each tenant gets their own database schema, providing complete data isolation.

#### Configuration for Schema per Tenant

```java
// TenantContext.java - Thread-local tenant storage
public class TenantContext {
    private static final ThreadLocal<String> CURRENT_TENANT = new ThreadLocal<>();
    
    public static void setCurrentTenant(String tenant) {
        CURRENT_TENANT.set(tenant);
    }
    
    public static String getCurrentTenant() {
        return CURRENT_TENANT.get();
    }
    
    public static void clear() {
        CURRENT_TENANT.remove();
    }
}

// TenantConnectionProvider.java
@Component
public class TenantConnectionProvider implements MultiTenantConnectionProvider {
    
    @Autowired
    private DataSource dataSource;
    
    @Override
    public Connection getAnyConnection() throws SQLException {
        return dataSource.getConnection();
    }
    
    @Override
    public Connection getConnection(String tenantIdentifier) throws SQLException {
        Connection connection = getAnyConnection();
        connection.setSchema(tenantIdentifier);
        return connection;
    }
    
    @Override
    public void releaseAnyConnection(Connection connection) throws SQLException {
        connection.close();
    }
    
    @Override
    public void releaseConnection(String tenantIdentifier, Connection connection) 
            throws SQLException {
        connection.setSchema("public"); // Reset to default
        releaseAnyConnection(connection);
    }
    
    @Override
    public boolean supportsAggressiveRelease() {
        return true;
    }
    
    @Override
    public boolean isUnwrappableAs(Class unwrapType) {
        return false;
    }
    
    @Override
    public <T> T unwrap(Class<T> unwrapType) {
        return null;
    }
}

// TenantIdentifierResolver.java
@Component
public class TenantIdentifierResolver implements CurrentTenantIdentifierResolver {
    
    @Override
    public String resolveCurrentTenantIdentifier() {
        String tenant = TenantContext.getCurrentTenant();
        return tenant != null ? tenant : "default";
    }
    
    @Override
    public boolean validateExistingCurrentSessions() {
        return true;
    }
}

// JPA Configuration for Multi-tenancy
@Configuration
@EnableJpaRepositories(basePackages = "com.example.repository")
public class MultiTenantJpaConfig {
    
    @Bean
    public LocalContainerEntityManagerFactoryBean entityManagerFactory(
            DataSource dataSource,
            MultiTenantConnectionProvider connectionProvider,
            CurrentTenantIdentifierResolver tenantResolver) {
        
        LocalContainerEntityManagerFactoryBean factory = 
            new LocalContainerEntityManagerFactoryBean();
        factory.setDataSource(dataSource);
        factory.setPackagesToScan("com.example.entity");
        
        HibernateJpaVendorAdapter vendorAdapter = new HibernateJpaVendorAdapter();
        factory.setJpaVendorAdapter(vendorAdapter);
        
        Map<String, Object> properties = new HashMap<>();
        properties.put(Environment.MULTI_TENANT, MultiTenancyStrategy.SCHEMA);
        properties.put(Environment.MULTI_TENANT_CONNECTION_PROVIDER, connectionProvider);
        properties.put(Environment.MULTI_TENANT_IDENTIFIER_RESOLVER, tenantResolver);
        properties.put(Environment.DIALECT, "org.hibernate.dialect.PostgreSQLDialect");
        properties.put(Environment.HBM2DDL_AUTO, "update");
        
        factory.setJpaPropertyMap(properties);
        return factory;
    }
}
```

#### Tenant Filter and Service

```java
// TenantFilter.java - Extract tenant from request
@Component
@Order(1)
public class TenantFilter implements Filter {
    
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, 
                        FilterChain chain) throws IOException, ServletException {
        
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        String tenantId = extractTenantId(httpRequest);
        
        try {
            TenantContext.setCurrentTenant(tenantId);
            chain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }
    
    private String extractTenantId(HttpServletRequest request) {
        // Option 1: From subdomain
        String serverName = request.getServerName();
        if (serverName.contains(".")) {
            String subdomain = serverName.substring(0, serverName.indexOf('.'));
            if (!subdomain.equals("www") && !subdomain.equals("api")) {
                return subdomain;
            }
        }
        
        // Option 2: From header
        String tenantHeader = request.getHeader("X-Tenant-ID");
        if (tenantHeader != null) {
            return tenantHeader;
        }
        
        // Option 3: From JWT token
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            // Extract tenant from JWT claims
            return extractTenantFromJWT(authHeader.substring(7));
        }
        
        return "default";
    }
    
    private String extractTenantFromJWT(String token) {
        // JWT parsing logic to extract tenant claim
        // This is a simplified example
        return "tenant1";
    }
}

// Multi-tenant Service Example
@Service
@Transactional
public class ProductService {
    
    @Autowired
    private ProductRepository productRepository;
    
    public Product createProduct(Product product) {
        // The repository automatically uses current tenant context
        return productRepository.save(product);
    }
    
    public List<Product> findAllProducts() {
        // Only returns products for current tenant
        return productRepository.findAll();
    }
    
    public Optional<Product> findById(Long id) {
        return productRepository.findById(id);
    }
}
```

### Shared Schema Multi-tenancy

All tenants share the same schema but data is separated by tenant ID column.

```java
// Base entity with tenant support
@MappedSuperclass
public abstract class TenantAwareEntity {
    
    @Column(name = "tenant_id", nullable = false)
    private String tenantId;
    
    @PrePersist
    @PreUpdate
    public void setTenantId() {
        this.tenantId = TenantContext.getCurrentTenant();
    }
    
    // Getters and setters
    public String getTenantId() {
        return tenantId;
    }
    
    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }
}

// Product entity with tenant awareness
@Entity
@Table(name = "products")
@FilterDef(name = "tenantFilter", parameters = @ParamDef(name = "tenantId", type = "string"))
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
public class Product extends TenantAwareEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false)
    private String name;
    
    @Column(precision = 10, scale = 2)
    private BigDecimal price;
    
    @Column
    private String description;
    
    // Constructors, getters, and setters
    public Product() {}
    
    public Product(String name, BigDecimal price, String description) {
        this.name = name;
        this.price = price;
        this.description = description;
    }
    
    // Getters and setters...
}

// Custom repository with tenant filtering
public interface ProductRepository extends JpaRepository<Product, Long>, 
                                         JpaSpecificationExecutor<Product> {
    
    @Query("SELECT p FROM Product p WHERE p.tenantId = :#{T(com.example.context.TenantContext).getCurrentTenant()}")
    List<Product> findAllForCurrentTenant();
    
    @Query("SELECT p FROM Product p WHERE p.name LIKE %:name% AND p.tenantId = :#{T(com.example.context.TenantContext).getCurrentTenant()}")
    List<Product> findByNameContaining(@Param("name") String name);
}

// Tenant-aware entity listener
@Component
public class TenantEntityListener {
    
    @PrePersist
    @PreUpdate
    public void setTenantId(Object entity) {
        if (entity instanceof TenantAwareEntity) {
            TenantAwareEntity tenantEntity = (TenantAwareEntity) entity;
            String currentTenant = TenantContext.getCurrentTenant();
            if (currentTenant != null) {
                tenantEntity.setTenantId(currentTenant);
            }
        }
    }
}
```

### Database per Tenant Strategy

Each tenant has completely separate database.

```java
// Tenant-specific DataSource Configuration
@Configuration
public class TenantDataSourceConfig {
    
    @Bean
    @ConfigurationProperties("spring.datasource.tenant1")
    public DataSource tenant1DataSource() {
        return DataSourceBuilder.create().build();
    }
    
    @Bean
    @ConfigurationProperties("spring.datasource.tenant2")
    public DataSource tenant2DataSource() {
        return DataSourceBuilder.create().build();
    }
    
    @Bean
    public Map<String, DataSource> tenantDataSources() {
        Map<String, DataSource> dataSources = new HashMap<>();
        dataSources.put("tenant1", tenant1DataSource());
        dataSources.put("tenant2", tenant2DataSource());
        return dataSources;
    }
}

// Dynamic DataSource Router
public class TenantRoutingDataSource extends AbstractRoutingDataSource {
    
    @Override
    protected Object determineCurrentLookupKey() {
        return TenantContext.getCurrentTenant();
    }
}

// Configuration for routing datasource
@Configuration
@EnableJpaRepositories(basePackages = "com.example.repository")
public class TenantRoutingConfig {
    
    @Autowired
    private Map<String, DataSource> tenantDataSources;
    
    @Bean
    @Primary
    public DataSource routingDataSource() {
        TenantRoutingDataSource routingDataSource = new TenantRoutingDataSource();
        routingDataSource.setTargetDataSources(new HashMap<>(tenantDataSources));
        routingDataSource.setDefaultTargetDataSource(tenantDataSources.get("default"));
        return routingDataSource;
    }
    
    @Bean
    public LocalContainerEntityManagerFactoryBean entityManagerFactory() {
        LocalContainerEntityManagerFactoryBean factory = 
            new LocalContainerEntityManagerFactoryBean();
        factory.setDataSource(routingDataSource());
        factory.setPackagesToScan("com.example.entity");
        factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        
        Properties properties = new Properties();
        properties.setProperty("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect");
        properties.setProperty("hibernate.hbm2ddl.auto", "update");
        factory.setJpaProperties(properties);
        
        return factory;
    }
}
```

## 9.2 Multiple Databases

Configure and manage multiple databases in a single Spring Boot application.

### Primary and Secondary Database Configuration

```java
// Primary Database Configuration
@Configuration
@EnableJpaRepositories(
    basePackages = "com.example.primary.repository",
    entityManagerFactoryRef = "primaryEntityManagerFactory",
    transactionManagerRef = "primaryTransactionManager"
)
public class PrimaryDatabaseConfig {
    
    @Bean
    @Primary
    @ConfigurationProperties("spring.datasource.primary")
    public DataSource primaryDataSource() {
        return DataSourceBuilder.create().build();
    }
    
    @Bean
    @Primary
    public LocalContainerEntityManagerFactoryBean primaryEntityManagerFactory(
            @Qualifier("primaryDataSource") DataSource dataSource) {
        
        LocalContainerEntityManagerFactoryBean factory = 
            new LocalContainerEntityManagerFactoryBean();
        factory.setDataSource(dataSource);
        factory.setPackagesToScan("com.example.primary.entity");
        factory.setPersistenceUnitName("primary");
        
        HibernateJpaVendorAdapter vendorAdapter = new HibernateJpaVendorAdapter();
        factory.setJpaVendorAdapter(vendorAdapter);
        
        Properties properties = new Properties();
        properties.setProperty("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect");
        properties.setProperty("hibernate.hbm2ddl.auto", "update");
        properties.setProperty("hibernate.physical_naming_strategy", 
                             "org.springframework.boot.orm.jpa.hibernate.SpringPhysicalNamingStrategy");
        factory.setJpaProperties(properties);
        
        return factory;
    }
    
    @Bean
    @Primary
    public PlatformTransactionManager primaryTransactionManager(
            @Qualifier("primaryEntityManagerFactory") EntityManagerFactory factory) {
        return new JpaTransactionManager(factory);
    }
}

// Secondary Database Configuration
@Configuration
@EnableJpaRepositories(
    basePackages = "com.example.secondary.repository",
    entityManagerFactoryRef = "secondaryEntityManagerFactory",
    transactionManagerRef = "secondaryTransactionManager"
)
public class SecondaryDatabaseConfig {
    
    @Bean
    @ConfigurationProperties("spring.datasource.secondary")
    public DataSource secondaryDataSource() {
        return DataSourceBuilder.create().build();
    }
    
    @Bean
    public LocalContainerEntityManagerFactoryBean secondaryEntityManagerFactory(
            @Qualifier("secondaryDataSource") DataSource dataSource) {
        
        LocalContainerEntityManagerFactoryBean factory = 
            new LocalContainerEntityManagerFactoryBean();
        factory.setDataSource(dataSource);
        factory.setPackagesToScan("com.example.secondary.entity");
        factory.setPersistenceUnitName("secondary");
        
        HibernateJpaVendorAdapter vendorAdapter = new HibernateJpaVendorAdapter();
        factory.setJpaVendorAdapter(vendorAdapter);
        
        Properties properties = new Properties();
        properties.setProperty("hibernate.dialect", "org.hibernate.dialect.MySQLDialect");
        properties.setProperty("hibernate.hbm2ddl.auto", "update");
        factory.setJpaProperties(properties);
        
        return factory;
    }
    
    @Bean
    public PlatformTransactionManager secondaryTransactionManager(
            @Qualifier("secondaryEntityManagerFactory") EntityManagerFactory factory) {
        return new JpaTransactionManager(factory);
    }
}
```

### Entities for Different Databases

```java
// Primary Database Entity (User Management)
package com.example.primary.entity;

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
    
    @Column(nullable = false)
    private String password;
    
    @Enumerated(EnumType.STRING)
    private Role role;
    
    @CreationTimestamp
    private LocalDateTime createdAt;
    
    // Constructors, getters, and setters
}

// Secondary Database Entity (Analytics/Reporting)
package com.example.secondary.entity;

@Entity
@Table(name = "user_activities")
public class UserActivity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "user_id", nullable = false)
    private Long userId;
    
    @Column(nullable = false)
    private String activity;
    
    @Column(name = "activity_data")
    private String activityData;
    
    @Column(nullable = false)
    private LocalDateTime timestamp;
    
    @Column
    private String ipAddress;
    
    @Column
    private String userAgent;
    
    // Constructors, getters, and setters
}
```

### Repositories for Multiple Databases

```java
// Primary Database Repository
package com.example.primary.repository;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    
    Optional<User> findByUsername(String username);
    
    Optional<User> findByEmail(String email);
    
    List<User> findByRole(Role role);
    
    @Query("SELECT u FROM User u WHERE u.createdAt > :date")
    List<User> findUsersCreatedAfter(@Param("date") LocalDateTime date);
}

// Secondary Database Repository
package com.example.secondary.repository;

@Repository
public interface UserActivityRepository extends JpaRepository<UserActivity, Long> {
    
    List<UserActivity> findByUserId(Long userId);
    
    List<UserActivity> findByActivity(String activity);
    
    @Query("SELECT ua FROM UserActivity ua WHERE ua.timestamp BETWEEN :start AND :end")
    List<UserActivity> findActivitiesBetween(
        @Param("start") LocalDateTime start, 
        @Param("end") LocalDateTime end);
    
    @Query(value = "SELECT activity, COUNT(*) as count FROM user_activities " +
                   "WHERE timestamp >= :since GROUP BY activity", 
           nativeQuery = true)
    List<Object[]> getActivityCountsSince(@Param("since") LocalDateTime since);
}
```

### Service Layer with Multiple Databases

```java
@Service
public class UserManagementService {
    
    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private UserActivityRepository activityRepository;
    
    @Transactional("primaryTransactionManager")
    public User createUser(User user) {
        User savedUser = userRepository.save(user);
        
        // Log user creation activity in secondary database
        logActivity(savedUser.getId(), "USER_CREATED", 
                   "User created with username: " + user.getUsername());
        
        return savedUser;
    }
    
    @Transactional("primaryTransactionManager")
    public User updateUser(Long userId, User updates) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new EntityNotFoundException("User not found"));
        
        // Update user fields
        user.setEmail(updates.getEmail());
        user.setRole(updates.getRole());
        
        User updatedUser = userRepository.save(user);
        
        // Log update activity
        logActivity(userId, "USER_UPDATED", 
                   "User updated: " + updates.toString());
        
        return updatedUser;
    }
    
    @Transactional("secondaryTransactionManager")
    public void logActivity(Long userId, String activity, String data) {
        UserActivity userActivity = new UserActivity();
        userActivity.setUserId(userId);
        userActivity.setActivity(activity);
        userActivity.setActivityData(data);
        userActivity.setTimestamp(LocalDateTime.now());
        
        activityRepository.save(userActivity);
    }
    
    @Transactional(value = "primaryTransactionManager", readOnly = true)
    public Optional<User> findUser(Long userId) {
        return userRepository.findById(userId);
    }
    
    @Transactional(value = "secondaryTransactionManager", readOnly = true)
    public List<UserActivity> getUserActivities(Long userId) {
        return activityRepository.findByUserId(userId);
    }
}
```

## 9.3 Read/Write Splitting

Separate read and write operations to different database instances for better performance.

### Read/Write DataSource Configuration

```java
@Configuration
public class ReadWriteSplitConfig {
    
    @Bean
    @ConfigurationProperties("spring.datasource.write")
    public DataSource writeDataSource() {
        HikariConfig config = new HikariConfig();
        // Write datasource typically has smaller connection pool
        config.setMaximumPoolSize(10);
        config.setMinimumIdle(2);
        return new HikariDataSource(config);
    }
    
    @Bean
    @ConfigurationProperties("spring.datasource.read")
    public DataSource readDataSource() {
        HikariConfig config = new HikariConfig();
        // Read datasource can have larger connection pool
        config.setMaximumPoolSize(20);
        config.setMinimumIdle(5);
        return new HikariDataSource(config);
    }
    
    @Bean
    @Primary
    public DataSource routingDataSource() {
        ReadWriteRoutingDataSource routingDataSource = new ReadWriteRoutingDataSource();
        
        Map<Object, Object> targetDataSources = new HashMap<>();
        targetDataSources.put(DataSourceType.WRITE, writeDataSource());
        targetDataSources.put(DataSourceType.READ, readDataSource());
        
        routingDataSource.setTargetDataSources(targetDataSources);
        routingDataSource.setDefaultTargetDataSource(writeDataSource());
        
        return routingDataSource;
    }
}

// DataSource routing logic
public class ReadWriteRoutingDataSource extends AbstractRoutingDataSource {
    
    @Override
    protected Object determineCurrentLookupKey() {
        return DataSourceContextHolder.getDataSourceType();
    }
}

// Context holder for datasource type
public class DataSourceContextHolder {
    
    private static final ThreadLocal<DataSourceType> CONTEXT_HOLDER = new ThreadLocal<>();
    
    public static void setDataSourceType(DataSourceType dataSourceType) {
        CONTEXT_HOLDER.set(dataSourceType);
    }
    
    public static DataSourceType getDataSourceType() {
        return CONTEXT_HOLDER.get();
    }
    
    public static void clearDataSourceType() {
        CONTEXT_HOLDER.remove();
    }
}

enum DataSourceType {
    READ, WRITE
}
```

### Aspect for Automatic Read/Write Routing

```java
@Aspect
@Component
@Order(1)
public class DataSourceRoutingAspect {
    
    @Around("@annotation(transactional)")
    public Object routeDataSource(ProceedingJoinPoint pjp, Transactional transactional) 
            throws Throwable {
        
        try {
            if (transactional.readOnly()) {
                DataSourceContextHolder.setDataSourceType(DataSourceType.READ);
            } else {
                DataSourceContextHolder.setDataSourceType(DataSourceType.WRITE);
            }
            
            return pjp.proceed();
        } finally {
            DataSourceContextHolder.clearDataSourceType();
        }
    }
    
    // Also route based on method names
    @Around("execution(* com.example.repository.*Repository.find*(..)) || " +
            "execution(* com.example.repository.*Repository.count*(..)) || " +
            "execution(* com.example.repository.*Repository.exists*(..))")
    public Object routeReadOperations(ProceedingJoinPoint pjp) throws Throwable {
        try {
            DataSourceContextHolder.setDataSourceType(DataSourceType.READ);
            return pjp.proceed();
        } finally {
            DataSourceContextHolder.clearDataSourceType();
        }
    }
    
    @Around("execution(* com.example.repository.*Repository.save*(..)) || " +
            "execution(* com.example.repository.*Repository.delete*(..)) || " +
            "execution(* com.example.repository.*Repository.update*(..))")
    public Object routeWriteOperations(ProceedingJoinPoint pjp) throws Throwable {
        try {
            DataSourceContextHolder.setDataSourceType(DataSourceType.WRITE);
            return pjp.proceed();
        } finally {
            DataSourceContextHolder.clearDataSourceType();
        }
    }
}
```

### Service Implementation with Read/Write Split

```java
@Service
public class ProductService {
    
    @Autowired
    private ProductRepository productRepository;
    
    // Write operations - automatically routes to write database
    @Transactional
    public Product createProduct(CreateProductRequest request) {
        Product product = new Product();
        product.setName(request.getName());
        product.setPrice(request.getPrice());
        product.setDescription(request.getDescription());
        
        return productRepository.save(product);
    }
    
    @Transactional
    public Product updateProduct(Long id, UpdateProductRequest request) {
        Product product = productRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Product not found"));
        
        product.setName(request.getName());
        product.setPrice(request.getPrice());
        product.setDescription(request.getDescription());
        
        return productRepository.save(product);
    }
    
    @Transactional
    public void deleteProduct(Long id) {
        productRepository.deleteById(id);
    }
    
    // Read operations - automatically routes to read database
    @Transactional(readOnly = true)
    public Optional<Product> findById(Long id) {
        return productRepository.findById(id);
    }
    
    @Transactional(readOnly = true)
    public Page<Product> findAll(Pageable pageable) {
        return productRepository.findAll(pageable);
    }
    
    @Transactional(readOnly = true)
    public List<Product> findByPriceRange(BigDecimal minPrice, BigDecimal maxPrice) {
        return productRepository.findByPriceBetween(minPrice, maxPrice);
    }
    
    @Transactional(readOnly = true)
    public List<Product> searchByName(String name) {
        return productRepository.findByNameContainingIgnoreCase(name);
    }
    
    // Complex reporting query - definitely should use read replica
    @Transactional(readOnly = true)
    public ProductAnalyticsReport generateAnalyticsReport(LocalDateTime from, LocalDateTime to) {
        List<Product> products = productRepository.findProductsCreatedBetween(from, to);
        
        // Complex calculations that don't affect write database
        BigDecimal avgPrice = products.stream()
            .map(Product::getPrice)
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .divide(BigDecimal.valueOf(products.size()), RoundingMode.HALF_UP);
        
        return new ProductAnalyticsReport(products.size(), avgPrice, from, to);
    }
}
```

### Configuration Properties

```yaml
# application.yml
spring:
  datasource:
    write:
      url: jdbc:postgresql://write-db-host:5432/myapp
      username: ${DB_WRITE_USERNAME}
      password: ${DB_WRITE_PASSWORD}
      driver-class-name: org.postgresql.Driver
      hikari:
        maximum-pool-size: 10
        minimum-idle: 2
        connection-timeout: 30000
        idle-timeout: 600000
        max-lifetime: 1800000
    
    read:
      url: jdbc:postgresql://read-db-host:5432/myapp
      username: ${DB_READ_USERNAME}
      password: ${DB_READ_PASSWORD}
      driver-class-name: org.postgresql.Driver
      hikari:
        maximum-pool-size: 20
        minimum-idle: 5
        connection-timeout: 30000
        idle-timeout: 600000
        max-lifetime: 1800000

  # Multi-tenant configuration (for schema-per-tenant)
  datasource:
    tenant1:
      url: jdbc:postgresql://localhost:5432/tenant1_db
      username: tenant1_user
      password: tenant1_pass
    
    tenant2:
      url: jdbc:postgresql://localhost:5432/tenant2_db
      username: tenant2_user
      password: tenant2_pass

  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: false
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect
        format_sql: true
        # Multi-tenancy specific properties
        multiTenancy: SCHEMA
        # Enable second-level cache for read operations
        cache:
          use_second_level_cache: true
          use_query_cache: true
          region:
            factory_class: org.hibernate.cache.ehcache.EhCacheRegionFactory
```

## Real-World Use Cases and Best Practices

### 1. SaaS E-commerce Platform (Multi-tenant)

```java
// Tenant-aware Product Service
@Service
@Transactional
public class TenantAwareProductService {
    
    @Autowired
    private ProductRepository productRepository;
    
    @Autowired
    private TenantConfigurationService tenantConfigService;
    
    public Product createProduct(CreateProductRequest request) {
        // Validate tenant-specific business rules
        TenantConfiguration config = tenantConfigService.getCurrentTenantConfig();
        
        if (config.getMaxProductsPerTenant() != null) {
            long currentCount = productRepository.count();
            if (currentCount >= config.getMaxProductsPerTenant()) {
                throw new BusinessException("Product limit exceeded for tenant");
            }
        }
        
        Product product = new Product();
        product.setName(request.getName());
        product.setPrice(request.getPrice());
        
        // Apply tenant-specific pricing rules
        if (config.hasPricingModifier()) {
            product.setPrice(product.getPrice().multiply(config.getPricingModifier()));
        }
        
        return productRepository.save(product);
    }
}
```

### 2. Microservices with Event Sourcing (Multiple Databases)

```java
@Service
public class OrderService {
    
    @Autowired
    private OrderRepository orderRepository; // Primary DB
    
    @Autowired
    private EventRepository eventRepository; // Event Store DB
    
    @Autowired
    private ApplicationEventPublisher eventPublisher;
    
    @Transactional("primaryTransactionManager")
    public Order createOrder(CreateOrderRequest request) {
        Order order = new Order();
        order.setCustomerId(request.getCustomerId());
        order.setStatus(OrderStatus.PENDING);
        
        Order savedOrder = orderRepository.save(order);
        
        // Store event in separate event store
        storeEvent(new OrderCreatedEvent(savedOrder.getId(), request));
        
        // Publish domain event
        eventPublisher.publishEvent(new OrderCreatedDomainEvent(savedOrder));
        
        return savedOrder;
    }
    
    @Transactional("eventTransactionManager")
    private void storeEvent(DomainEvent event) {
        EventEntity eventEntity = new EventEntity();
        eventEntity.setAggregateId(event.getAggregateId());
        eventEntity.setEventType(event.getClass().getSimpleName());
        eventEntity.setEventData(JsonUtils.toJson(event));
        eventEntity.setTimestamp(event.getTimestamp());
        
        eventRepository.save(eventEntity);
    }
}
```

### 3. High-Performance Analytics Platform (Read/Write Splitting)

```java
@Service
public class AnalyticsService {
    
    @Autowired
    private UserEventRepository eventRepository;
    
    @Autowired
    private AnalyticsReportRepository reportRepository;
    
    // Write operations - high-frequency event ingestion
    @Transactional
    @Async("eventIngestionExecutor")
    public CompletableFuture<Void> ingestUserEvent(UserEventDto eventDto) {
        UserEvent event = new UserEvent();
        event.setUserId(eventDto.getUserId());
        event.setEventType(eventDto.getEventType());
        event.setEventData(eventDto.getEventData());
        event.setTimestamp(LocalDateTime.now());
        
        eventRepository.save(event);
        return CompletableFuture.completedFuture(null);
    }
    
    // Read operations - complex analytical queries on read replicas
    @Transactional(readOnly = true)
    public DailyAnalyticsReport generateDailyReport(LocalDate date) {
        LocalDateTime startOfDay = date.atStartOfDay();
        LocalDateTime endOfDay = date.atTime(23, 59, 59);
        
        // Complex aggregation queries run on read replica
        List<Object[]> eventCounts = eventRepository.getEventCountsByType(startOfDay, endOfDay);
        List<Object[]> userEngagement = eventRepository.getUserEngagementMetrics(startOfDay, endOfDay);
        List<Object[]> hourlyDistribution = eventRepository.getHourlyEventDistribution(startOfDay, endOfDay);
        
        return DailyAnalyticsReport.builder()
            .date(date)
            .eventCounts(mapEventCounts(eventCounts))
            .userEngagement(mapUserEngagement(userEngagement))
            .hourlyDistribution(mapHourlyDistribution(hourlyDistribution))
            .build();
    }
    
    @Transactional(readOnly = true)
    public UserBehaviorAnalysis analyzeUserBehavior(Long userId, LocalDateTime from, LocalDateTime to) {
        // Heavy analytical query that should not impact write performance
        List<UserEvent> events = eventRepository.findByUserIdAndTimestampBetweenOrderByTimestamp(
            userId, from, to);
        
        // Complex analysis logic
        Map<String, Long> eventTypeFrequency = events.stream()
            .collect(Collectors.groupingBy(UserEvent::getEventType, Collectors.counting()));
        
        List<UserSession> sessions = extractUserSessions(events);
        Double averageSessionDuration = calculateAverageSessionDuration(sessions);
        
        return UserBehaviorAnalysis.builder()
            .userId(userId)
            .period(new DateRange(from, to))
            .eventTypeFrequency(eventTypeFrequency)
            .sessions(sessions)
            .averageSessionDuration(averageSessionDuration)
            .build();
    }
}

// Repository with optimized read queries
@Repository
public interface UserEventRepository extends JpaRepository<UserEvent, Long> {
    
    @Query(value = "SELECT event_type, COUNT(*) as count " +
                   "FROM user_events " +
                   "WHERE timestamp BETWEEN ?1 AND ?2 " +
                   "GROUP BY event_type " +
                   "ORDER BY count DESC", 
           nativeQuery = true)
    List<Object[]> getEventCountsByType(LocalDateTime from, LocalDateTime to);
    
    @Query(value = "SELECT user_id, COUNT(DISTINCT DATE(timestamp)) as active_days, " +
                   "COUNT(*) as total_events " +
                   "FROM user_events " +
                   "WHERE timestamp BETWEEN ?1 AND ?2 " +
                   "GROUP BY user_id " +
                   "HAVING total_events > 10 " +
                   "ORDER BY total_events DESC " +
                   "LIMIT 100", 
           nativeQuery = true)
    List<Object[]> getUserEngagementMetrics(LocalDateTime from, LocalDateTime to);
    
    @Query(value = "SELECT HOUR(timestamp) as hour, COUNT(*) as count " +
                   "FROM user_events " +
                   "WHERE timestamp BETWEEN ?1 AND ?2 " +
                   "GROUP BY HOUR(timestamp) " +
                   "ORDER BY hour", 
           nativeQuery = true)
    List<Object[]> getHourlyEventDistribution(LocalDateTime from, LocalDateTime to);
    
    List<UserEvent> findByUserIdAndTimestampBetweenOrderByTimestamp(
        Long userId, LocalDateTime from, LocalDateTime to);
}
```

### 4. Enterprise Multi-Database Transaction Management

```java
// XA Transaction Configuration for true distributed transactions
@Configuration
@EnableTransactionManagement
public class XATransactionConfig {
    
    @Bean
    public PlatformTransactionManager transactionManager() {
        JtaTransactionManager jtaTransactionManager = new JtaTransactionManager();
        jtaTransactionManager.setTransactionManager(atomikosTransactionManager());
        jtaTransactionManager.setUserTransaction(atomikosUserTransaction());
        return jtaTransactionManager;
    }
    
    @Bean
    public TransactionManager atomikosTransactionManager() {
        UserTransactionManager userTransactionManager = new UserTransactionManager();
        userTransactionManager.setForceShutdown(false);
        return userTransactionManager;
    }
    
    @Bean
    public UserTransaction atomikosUserTransaction() throws SystemException {
        UserTransactionImp userTransactionImp = new UserTransactionImp();
        userTransactionImp.setTransactionTimeout(300);
        return userTransactionImp;
    }
    
    @Bean
    @Primary
    public DataSource primaryXADataSource() {
        AtomikosDataSourceBean xaDataSource = new AtomikosDataSourceBean();
        xaDataSource.setUniqueResourceName("primaryDS");
        xaDataSource.setXaDataSourceClassName("org.postgresql.xa.PGXADataSource");
        
        Properties xaProperties = new Properties();
        xaProperties.setProperty("serverName", "localhost");
        xaProperties.setProperty("portNumber", "5432");
        xaProperties.setProperty("databaseName", "primary_db");
        xaProperties.setProperty("user", "primary_user");
        xaProperties.setProperty("password", "primary_pass");
        
        xaDataSource.setXaProperties(xaProperties);
        xaDataSource.setPoolSize(10);
        return xaDataSource;
    }
    
    @Bean
    public DataSource secondaryXADataSource() {
        AtomikosDataSourceBean xaDataSource = new AtomikosDataSourceBean();
        xaDataSource.setUniqueResourceName("secondaryDS");
        xaDataSource.setXaDataSourceClassName("com.mysql.cj.jdbc.MysqlXADataSource");
        
        Properties xaProperties = new Properties();
        xaProperties.setProperty("URL", "jdbc:mysql://localhost:3306/secondary_db");
        xaProperties.setProperty("user", "secondary_user");
        xaProperties.setProperty("password", "secondary_pass");
        
        xaDataSource.setXaProperties(xaProperties);
        xaDataSource.setPoolSize(5);
        return xaDataSource;
    }
}

// Service with distributed transaction
@Service
public class DistributedTransactionService {
    
    @Autowired
    private OrderRepository orderRepository;
    
    @Autowired
    private PaymentRepository paymentRepository;
    
    @Autowired
    private InventoryRepository inventoryRepository;
    
    // This transaction spans multiple databases
    @Transactional
    public OrderResult processOrder(OrderRequest request) {
        try {
            // Step 1: Create order in primary database
            Order order = new Order();
            order.setCustomerId(request.getCustomerId());
            order.setItems(request.getItems());
            order.setTotalAmount(request.getTotalAmount());
            order.setStatus(OrderStatus.PROCESSING);
            
            Order savedOrder = orderRepository.save(order);
            
            // Step 2: Process payment in secondary database
            Payment payment = new Payment();
            payment.setOrderId(savedOrder.getId());
            payment.setAmount(request.getTotalAmount());
            payment.setPaymentMethod(request.getPaymentMethod());
            payment.setStatus(PaymentStatus.PROCESSING);
            
            Payment processedPayment = paymentRepository.save(payment);
            
            // Step 3: Update inventory in another database
            for (OrderItem item : request.getItems()) {
                Inventory inventory = inventoryRepository.findByProductId(item.getProductId())
                    .orElseThrow(() -> new ProductNotFoundException("Product not found: " + item.getProductId()));
                
                if (inventory.getQuantity() < item.getQuantity()) {
                    throw new InsufficientInventoryException("Not enough inventory for product: " + item.getProductId());
                }
                
                inventory.setQuantity(inventory.getQuantity() - item.getQuantity());
                inventoryRepository.save(inventory);
            }
            
            // If we reach here, all operations succeeded
            savedOrder.setStatus(OrderStatus.CONFIRMED);
            processedPayment.setStatus(PaymentStatus.COMPLETED);
            
            orderRepository.save(savedOrder);
            paymentRepository.save(processedPayment);
            
            return OrderResult.success(savedOrder, processedPayment);
            
        } catch (Exception e) {
            // XA transaction will automatically rollback all operations
            log.error("Order processing failed: {}", e.getMessage(), e);
            throw new OrderProcessingException("Failed to process order", e);
        }
    }
}
```

### Advanced Multi-Tenancy Patterns

```java
// Hierarchical Multi-tenancy (Organizations -> Tenants -> Users)
@Entity
@Table(name = "organizations")
public class Organization {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(unique = true)
    private String organizationCode;
    
    @OneToMany(mappedBy = "organization", cascade = CascadeType.ALL)
    private List<Tenant> tenants = new ArrayList<>();
    
    // Additional organization-level configurations
    @Embedded
    private OrganizationSettings settings;
}

@Entity
@Table(name = "tenants")
public class Tenant {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(unique = true)
    private String tenantCode;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id")
    private Organization organization;
    
    @Embedded
    private TenantSettings settings;
}

// Enhanced Tenant Context for hierarchical tenancy
public class HierarchicalTenantContext {
    private static final ThreadLocal<TenantInfo> CURRENT_TENANT = new ThreadLocal<>();
    
    public static void setCurrentTenant(String organizationCode, String tenantCode) {
        TenantInfo info = new TenantInfo(organizationCode, tenantCode);
        CURRENT_TENANT.set(info);
    }
    
    public static TenantInfo getCurrentTenant() {
        return CURRENT_TENANT.get();
    }
    
    public static String getCurrentTenantCode() {
        TenantInfo info = getCurrentTenant();
        return info != null ? info.getTenantCode() : null;
    }
    
    public static String getCurrentOrganizationCode() {
        TenantInfo info = getCurrentTenant();
        return info != null ? info.getOrganizationCode() : null;
    }
    
    public static void clear() {
        CURRENT_TENANT.remove();
    }
    
    public static class TenantInfo {
        private final String organizationCode;
        private final String tenantCode;
        
        public TenantInfo(String organizationCode, String tenantCode) {
            this.organizationCode = organizationCode;
            this.tenantCode = tenantCode;
        }
        
        // Getters...
    }
}

// Multi-level tenant filtering
@MappedSuperclass
public abstract class HierarchicalTenantAwareEntity {
    
    @Column(name = "organization_code", nullable = false)
    private String organizationCode;
    
    @Column(name = "tenant_code", nullable = false)
    private String tenantCode;
    
    @PrePersist
    @PreUpdate
    public void setTenantInfo() {
        TenantInfo tenantInfo = HierarchicalTenantContext.getCurrentTenant();
        if (tenantInfo != null) {
            this.organizationCode = tenantInfo.getOrganizationCode();
            this.tenantCode = tenantInfo.getTenantCode();
        }
    }
    
    // Getters and setters...
}
```

### Performance Monitoring and Health Checks

```java
// Database Health Indicator for multiple datasources
@Component
public class MultiDataSourceHealthIndicator implements HealthIndicator {
    
    @Autowired
    @Qualifier("primaryDataSource")
    private DataSource primaryDataSource;
    
    @Autowired
    @Qualifier("secondaryDataSource")
    private DataSource secondaryDataSource;
    
    @Override
    public Health health() {
        Health.Builder builder = new Health.Builder();
        
        try {
            checkDataSourceHealth(primaryDataSource, "primary", builder);
            checkDataSourceHealth(secondaryDataSource, "secondary", builder);
            
            return builder.up().build();
        } catch (Exception e) {
            return builder.down(e).build();
        }
    }
    
    private void checkDataSourceHealth(DataSource dataSource, String name, Health.Builder builder) {
        try (Connection connection = dataSource.getConnection()) {
            if (connection.isValid(1)) {
                builder.withDetail(name + "Database", "Available");
                
                // Add connection pool metrics if using HikariCP
                if (dataSource instanceof HikariDataSource) {
                    HikariDataSource hikari = (HikariDataSource) dataSource;
                    HikariPoolMXBean pool = hikari.getHikariPoolMXBean();
                    
                    builder.withDetail(name + "ActiveConnections", pool.getActiveConnections())
                           .withDetail(name + "IdleConnections", pool.getIdleConnections())
                           .withDetail(name + "TotalConnections", pool.getTotalConnections())
                           .withDetail(name + "ThreadsAwaitingConnection", pool.getThreadsAwaitingConnection());
                }
            } else {
                builder.withDetail(name + "Database", "Connection invalid");
            }
        } catch (SQLException e) {
            builder.withDetail(name + "Database", "Connection failed: " + e.getMessage());
            throw new RuntimeException("Health check failed for " + name + " database", e);
        }
    }
}

// Custom metrics for tenant operations
@Component
public class TenantMetrics {
    
    private final MeterRegistry meterRegistry;
    private final Counter tenantSwitchCounter;
    private final Timer tenantOperationTimer;
    
    public TenantMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        this.tenantSwitchCounter = Counter.builder("tenant.switches")
            .description("Number of tenant context switches")
            .register(meterRegistry);
        this.tenantOperationTimer = Timer.builder("tenant.operations")
            .description("Time spent in tenant operations")
            .register(meterRegistry);
    }
    
    public void recordTenantSwitch(String fromTenant, String toTenant) {
        tenantSwitchCounter.increment(
            Tags.of(
                Tag.of("from_tenant", fromTenant != null ? fromTenant : "none"),
                Tag.of("to_tenant", toTenant != null ? toTenant : "none")
            )
        );
    }
    
    public Timer.Sample startTenantOperation(String tenantId, String operation) {
        return Timer.start(meterRegistry)
            .tags("tenant", tenantId, "operation", operation);
    }
}
```

### Testing Strategies for Enterprise Patterns

```java
// Integration test for multi-tenant functionality
@SpringBootTest
@TestPropertySource(properties = {
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE"
})
class MultiTenantIntegrationTest {
    
    @Autowired
    private ProductService productService;
    
    @Autowired
    private ProductRepository productRepository;
    
    @Test
    void testTenantIsolation() {
        // Test tenant1 operations
        TenantContext.setCurrentTenant("tenant1");
        
        Product product1 = new Product("Product 1", new BigDecimal("10.00"));
        Product savedProduct1 = productService.createProduct(product1);
        
        List<Product> tenant1Products = productRepository.findAll();
        assertThat(tenant1Products).hasSize(1);
        assertThat(tenant1Products.get(0).getTenantId()).isEqualTo("tenant1");
        
        // Switch to tenant2
        TenantContext.setCurrentTenant("tenant2");
        
        Product product2 = new Product("Product 2", new BigDecimal("20.00"));
        Product savedProduct2 = productService.createProduct(product2);
        
        List<Product> tenant2Products = productRepository.findAll();
        assertThat(tenant2Products).hasSize(1);
        assertThat(tenant2Products.get(0).getTenantId()).isEqualTo("tenant2");
        
        // Verify tenant1 still has only its data
        TenantContext.setCurrentTenant("tenant1");
        List<Product> tenant1ProductsAfterSwitch = productRepository.findAll();
        assertThat(tenant1ProductsAfterSwitch).hasSize(1);
        assertThat(tenant1ProductsAfterSwitch.get(0).getId()).isEqualTo(savedProduct1.getId());
        
        TenantContext.clear();
    }
}

// Test for read/write splitting
@SpringBootTest
@Testcontainers
class ReadWriteSplitIntegrationTest {
    
    @Container
    static PostgreSQLContainer<?> writeDb = new PostgreSQLContainer<>("postgres:13")
        .withDatabaseName("writedb")
        .withUsername("test")
        .withPassword("test");
    
    @Container
    static PostgreSQLContainer<?> readDb = new PostgreSQLContainer<>("postgres:13")
        .withDatabaseName("readdb")
        .withUsername("test")
        .withPassword("test");
    
    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.write.url", writeDb::getJdbcUrl);
        registry.add("spring.datasource.read.url", readDb::getJdbcUrl);
    }
    
    @Autowired
    private ProductService productService;
    
    @Test
    void testReadWriteSplitting() {
        // Write operation should go to write database
        Product product = productService.createProduct(
            new CreateProductRequest("Test Product", new BigDecimal("10.00"))
        );
        
        assertThat(product.getId()).isNotNull();
        
        // Read operation should go to read database
        // Note: In real scenarios, you'd need replication setup
        Optional<Product> foundProduct = productService.findById(product.getId());
        assertThat(foundProduct).isPresent();
    }
}
```

## Best Practices and Common Pitfalls

### 1. Multi-tenancy Best Practices

```java
// Always validate tenant access
@Service
public class SecureProductService {
    
    @Autowired
    private ProductRepository productRepository;
    
    @Autowired
    private TenantSecurityService tenantSecurityService;
    
    public Product findById(Long id) {
        Optional<Product> product = productRepository.findById(id);
        
        if (product.isPresent()) {
            // Verify the product belongs to current tenant
            if (!tenantSecurityService.canAccessResource(product.get().getTenantId())) {
                throw new SecurityException("Access denied to resource");
            }
        }
        
        return product.orElseThrow(() -> new EntityNotFoundException("Product not found"));
    }
}

// Tenant-aware caching
@Service
public class TenantAwareCacheService {
    
    @Cacheable(value = "products", key = "#root.methodName + '_' + T(com.example.context.TenantContext).getCurrentTenant() + '_' + #id")
    public Product findProduct(Long id) {
        // Implementation
    }
    
    @CacheEvict(value = "products", key = "#root.methodName + '_' + T(com.example.context.TenantContext).getCurrentTenant() + '_' + #product.id")
    public Product updateProduct(Product product) {
        // Implementation
    }
}
```

### 2. Connection Pool Optimization

```java
@Configuration
public class OptimizedDataSourceConfig {
    
    @Bean
    @ConfigurationProperties("app.datasource.write")
    public HikariConfig writeHikariConfig() {
        HikariConfig config = new HikariConfig();
        
        // Write pool - smaller, focused on quick transactions
        config.setMaximumPoolSize(10);
        config.setMinimumIdle(2);
        config.setConnectionTimeout(5000); // 5 seconds
        config.setIdleTimeout(300000); // 5 minutes
        config.setMaxLifetime(1200000); // 20 minutes
        config.setLeakDetectionThreshold(60000); // 1 minute
        
        // Write optimization
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("useServerPrepStmts", "true");
        
        return config;
    }
    
    @Bean
    @ConfigurationProperties("app.datasource.read")
    public HikariConfig readHikariConfig() {
        HikariConfig config = new HikariConfig();
        
        // Read pool - larger, optimized for longer-running queries
        config.setMaximumPoolSize(20);
        config.setMinimumIdle(5);
        config.setConnectionTimeout(10000); // 10 seconds
        config.setIdleTimeout(600000); // 10 minutes
        config.setMaxLifetime(1800000); // 30 minutes
        
        // Read optimization - allow longer queries
        config.addDataSourceProperty("socketTimeout", "300000"); // 5 minutes
        config.addDataSourceProperty("tcpKeepAlive", "true");
        
        return config;
    }
}
```

### Key Takeaways for Phase 9

1. **Multi-tenancy Strategy Selection**: Choose based on isolation needs, complexity, and scalability requirements:
   - **Schema-per-tenant**: Best isolation, moderate complexity
   - **Shared schema**: Good performance, requires careful security
   - **Database-per-tenant**: Maximum isolation, highest operational overhead

2. **Multiple Database Management**: 
   - Use `@Primary` and `@Qualifier` for clear DataSource identification
   - Separate transaction managers for each database
   - Consider XA transactions for distributed consistency

3. **Read/Write Splitting Benefits**:
   - Improved read performance through dedicated read replicas
   - Reduced load on primary write database
   - Better resource utilization and scalability

4. **Performance Considerations**:
   - Monitor connection pool metrics
   - Use appropriate pool sizes for read vs write operations
   - Implement proper caching strategies for multi-tenant scenarios

5. **Security and Validation**:
   - Always validate tenant access at the service layer
   - Implement row-level security where possible
   - Use tenant-aware caching keys

6. **Testing Strategies**:
   - Use Testcontainers for integration testing with multiple databases
   - Test tenant isolation thoroughly
   - Verify transaction boundaries across databases

7. **Monitoring and Observability**:
   - Track tenant-specific metrics
   - Monitor database health across all instances
   - Implement custom health indicators for enterprise patterns