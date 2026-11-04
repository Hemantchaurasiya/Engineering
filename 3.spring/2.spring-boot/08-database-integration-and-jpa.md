# 08 - Database Integration & Spring Data JPA

---

## Plain Spring — manual DataSource + JdbcTemplate

Your notes (kept, corrected):

```java
@Configuration
public class JavaConfig {

    @Bean
    public DriverManagerDataSource dataSource() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setUrl("jdbc:mysql://localhost:3306/orders_db");
        dataSource.setDriverClassName("com.mysql.cj.jdbc.Driver");
        dataSource.setUsername("root");
        dataSource.setPassword("secret");
        return dataSource;
    }

    @Bean
    public JdbcTemplate jdbcTemplate(DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }
}

@Repository
public class ProductDao {

    @Autowired
    private JdbcTemplate jdbcTemplate;
}
```

Every bit of this is manual — you write the `DataSource` bean, wire it into `JdbcTemplate` yourself.

---

## Spring Boot — auto-configured DataSource

`application.properties`:
```properties
spring.datasource.url=jdbc:mysql://localhost:3306/orders_db
spring.datasource.username=root
spring.datasource.password=secret
spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver
```

- Add `spring-boot-starter-jdbc` (or `spring-boot-starter-data-jpa`, which brings it transitively) to `pom.xml`.
- Boot's `DataSourceAutoConfiguration` reads `spring.datasource.*`, and if present, automatically creates the `DataSource` **and** `JdbcTemplate` beans for you — no manual `@Bean` needed.
- If `spring.datasource.*` isn't configured at all and there's no embedded DB (H2/HSQLDB) on the classpath either, the app fails to start with a clear "Failed to configure a DataSource" error — this matches your note about it erroring if not configured.
- By default, Boot only auto-configures **one** `DataSource`. Multiple data sources require manual configuration (below) — Boot deliberately doesn't guess how you'd want to split them.

Modern connection pool default: **HikariCP** (fast, lightweight — has been the Boot default pool since Boot 2.x, replacing Tomcat's own pool).

```properties
spring.datasource.hikari.maximum-pool-size=10
spring.datasource.hikari.connection-timeout=3000
```

---

## Multiple DataSources (corrected — your original code had structural errors)

`application.properties`:
```properties
app.datasource.first.driver-class-name=oracle.jdbc.OracleDriver
app.datasource.first.url=jdbc:oracle:thin:@localhost:1521:orcl
app.datasource.first.username=first_user
app.datasource.first.password=first_pass

app.datasource.second.driver-class-name=com.mysql.cj.jdbc.Driver
app.datasource.second.url=jdbc:mysql://localhost:3306/second_db
app.datasource.second.username=second_user
app.datasource.second.password=second_pass
```

```java
@Configuration
public class MultipleDataSourceConfig {

    // ---- first datasource ----
    @Bean
    @Primary
    @ConfigurationProperties("app.datasource.first")
    public DataSourceProperties firstDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean
    @Primary
    public DataSource firstDataSource(
            @Qualifier("firstDataSourceProperties") DataSourceProperties properties) {
        return properties.initializeDataSourceBuilder().build();
    }

    @Bean
    public JdbcTemplate firstJdbcTemplate(@Qualifier("firstDataSource") DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    // ---- second datasource ----
    @Bean
    @ConfigurationProperties("app.datasource.second")
    public DataSourceProperties secondDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean
    public DataSource secondDataSource(
            @Qualifier("secondDataSourceProperties") DataSourceProperties properties) {
        return properties.initializeDataSourceBuilder().build();
    }

    @Bean
    public JdbcTemplate secondJdbcTemplate(@Qualifier("secondDataSource") DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }
}
```

Key points that are easy to get wrong (and get asked about):
- Exactly one `DataSource` should be marked `@Primary` — otherwise autowiring `DataSource` anywhere without a `@Qualifier` is ambiguous and fails at startup.
- `@ConfigurationProperties("app.datasource.first")` on a `DataSourceProperties` bean, then `properties.initializeDataSourceBuilder().build()`, is the standard, correct pattern — this is what your original notes were reaching for with `@ConditionalOnProperties`/`inHtialize`.
- For JPA with multiple datasources you additionally need two separate `EntityManagerFactory` + `TransactionManager` beans, each pointed at its own `DataSource` and its own base package — noticeably more setup than plain JDBC, and a common source of "why is my `@Transactional` not committing to the right DB" bugs.

---

## Spring Data JPA

Your notes (kept):

- The DAO layer usually contains a lot of boilerplate code (open connection, build query, map ResultSet, close connection) that should be simplified to reduce lines of code and increase reusability.
- The main goal of Spring Data JPA is to eliminate boilerplate code in the DAO layer entirely.
- Spring Data JPA provides 4 core interfaces that handle DB connectivity and CRUD operations for you — **you write zero DAO implementation code** for standard operations.

### The interface hierarchy (your diagram, kept)

```
Repository<T, ID>
       ▲
       │ extends
CrudRepository<T, ID>
       ▲
       │ extends
PagingAndSortingRepository<T, ID>
       ▲
       │ extends
JpaRepository<T, ID>
```

- `Repository<T, ID>` — marker interface, no methods, just identifies something as a Spring Data repository
- `CrudRepository<T, ID>` — adds `save()`, `findById()`, `findAll()`, `deleteById()`, `count()`, etc.
- `PagingAndSortingRepository<T, ID>` — adds `findAll(Pageable)`, `findAll(Sort)`
- `JpaRepository<T, ID>` — JPA-specific extras: batch operations, `flush()`, `saveAndFlush()` — this is the one you extend in practice almost every time

### Entity annotations (your list, kept)

```java
@Entity
@Table(name = "products")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_name", nullable = false)
    private String name;

    @Column(precision = 10, scale = 2)
    private BigDecimal price;
}
```

- `@Entity` — marks the class as a JPA-managed entity
- `@Table` — maps it to a specific table name (optional if class name matches)
- `@Id` — marks the primary key field
- `@Column` — customizes column mapping (name, nullable, length, precision)
- `@GeneratedValue` — how the ID is generated (`IDENTITY` = DB auto-increment, `SEQUENCE` = DB sequence object, `AUTO` = let the JPA provider pick)

To wire this up:
- Add `spring-boot-starter-data-jpa` to `pom.xml`
- Add the same `spring.datasource.*` properties as before — Spring Data JPA reuses the same auto-configured `DataSource`

---

## Real-world example — the three cases your notes flagged, filled in

**Case 1 — zero DAO code, use JPA's predefined methods:**
```java
public interface ProductRepository extends JpaRepository<Product, Long> {
    // save(), findById(), findAll(), deleteById() all inherited - no code needed
}

@Service
@RequiredArgsConstructor
public class ProductService {
    private final ProductRepository repository;

    public Product create(Product product) {
        return repository.save(product);
    }
}
```

**Case 2 — your own method, via Spring Data's query derivation from the method name:**
```java
public interface ProductRepository extends JpaRepository<Product, Long> {

    // Spring Data parses the method name and builds the query automatically -
    // no implementation needed, just the method signature.
    List<Product> findByNameContainingIgnoreCase(String name);

    List<Product> findByPriceLessThanAndCategory(BigDecimal price, String category);

    Optional<Product> findByNameAndCategory(String name, String category);
}
```

**Case 3 — explicit/generated queries with `@Query`, for anything the naming convention can't express cleanly:**
```java
public interface ProductRepository extends JpaRepository<Product, Long> {

    @Query("SELECT p FROM Product p WHERE p.price > :minPrice ORDER BY p.price DESC")
    List<Product> findExpensiveProducts(@Param("minPrice") BigDecimal minPrice);

    @Query(value = "SELECT * FROM products WHERE stock_quantity < 10", nativeQuery = true)
    List<Product> findLowStockProducts();

    @Modifying
    @Transactional
    @Query("UPDATE Product p SET p.price = p.price * 1.1 WHERE p.category = :category")
    int applyPriceIncrease(@Param("category") String category);
}
```

---

## Tricky interview questions — Section 8

**Q1. `findByNameAndCategory` works with zero implementation — how does Spring Data actually generate the query at runtime?**
At application startup, Spring Data JPA proxies the repository interface and parses each method name into query fragments using a fixed grammar (`findBy`, `And`, `Or`, `LessThan`, `ContainingIgnoreCase`, etc.), mapping each fragment to the entity's field names via reflection. It builds and validates the corresponding JPQL query once, at startup — not per call — so a typo in a field name (e.g. `findByNam` instead of `findByName`) fails immediately at application startup with a clear error, not silently at runtime.

**Q2. Difference between `CrudRepository.save()` behavior for a new entity vs an existing one — how does JPA know which to do?**
`save()` internally checks whether the entity's `@Id` field is `null` (or, for non-primitive wrapper IDs, unset) — if null, it performs an `INSERT`; if set, it performs a `SELECT`-then-`UPDATE` (technically a merge). This is exactly why using a manually-assigned (non-generated) ID and then calling `save()` on what you intend as a "new" row can accidentally trigger an update-merge instead of an insert if the ID happens to collide — a subtle real bug.

**Q3. Why must `@Modifying` queries also be `@Transactional`, and what happens if you forget?**
JPA's persistence context and query execution model expects DML (UPDATE/DELETE) to run inside an active transaction so it can manage flushing and first-level cache consistency correctly. Without `@Transactional`, you'll get a `TransactionRequiredException` at runtime the moment the modifying query executes — a very common gotcha the first time someone writes a bulk update repository method.

**Q4. `spring-boot-starter-data-jpa` vs `spring-boot-starter-jdbc` — when would you deliberately choose plain JDBC/`JdbcTemplate` over JPA in a real project?**
JPA/Hibernate adds object-relational mapping convenience but also overhead (persistence context tracking, potential N+1 query issues, less predictable generated SQL) that can hurt performance-critical, high-throughput, or bulk-operation code paths. Plain `JdbcTemplate` gives full control over the exact SQL executed with minimal overhead — commonly chosen for reporting/analytics queries, bulk batch jobs, or anywhere query performance needs to be precisely tunable rather than ORM-generated.

**Q5. What's the N+1 query problem in Spring Data JPA, and how do you fix it?**
Fetching a list of parent entities (e.g. `Order`s) with a `LAZY` child collection (e.g. `orderItems`), then iterating and accessing `.getOrderItems()` on each, triggers **one query per parent** to lazily load its children — 1 query for the parents + N queries for children = N+1 total, instead of one efficient join. Fix: use `@EntityGraph` or a `JOIN FETCH` in a custom `@Query` to eagerly fetch the association in the *same* query when you know you'll need it:
```java
@Query("SELECT o FROM Order o JOIN FETCH o.orderItems WHERE o.status = :status")
List<Order> findByStatusWithItems(@Param("status") String status);
```

**Q6. What does `@Transactional`'s default propagation and isolation actually mean, and why does calling a `@Transactional` method from within the same class not create a new transaction?**
Default propagation is `REQUIRED` (join an existing transaction if one is active, otherwise start a new one) and default isolation defers to the DB's default (usually `READ_COMMITTED`). Self-invocation (calling a `@Transactional` method on `this` from another method in the same class) bypasses Spring's transaction advice entirely, because `@Transactional` is implemented via a **proxy** wrapping the bean — an internal `this.method()` call never goes through that proxy, so no transaction boundary is applied. Fix: move the call through another Spring-managed bean, or self-inject a proxy of the bean if you truly must call it internally.
