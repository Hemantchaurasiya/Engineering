# Phase 8: Advanced Spring Data JPA Features

## 8.1 Custom Repository Implementation

### Overview
While Spring Data JPA provides excellent auto-generated repository methods, sometimes you need custom logic that can't be expressed through method naming conventions or simple `@Query` annotations. Custom repository implementations allow you to write complex business logic while still leveraging Spring Data's infrastructure.

### 8.1.1 Custom Repository Interfaces

First, define a custom interface for your additional methods:

```java
public interface UserRepositoryCustom {
    List<User> findUsersWithComplexCriteria(String name, Integer minAge, String department);
    void bulkUpdateUserStatus(List<Long> userIds, UserStatus status);
    List<User> findUsersWithDynamicQuery(Map<String, Object> filters);
    Page<UserSummaryDto> findUserSummariesWithStats(Pageable pageable);
}
```

### 8.1.2 Repository Implementation

Create the implementation class (must end with "Impl"):

```java
@Repository
@Transactional
public class UserRepositoryImpl implements UserRepositoryCustom {
    
    @PersistenceContext
    private EntityManager entityManager;
    
    @Override
    public List<User> findUsersWithComplexCriteria(String name, Integer minAge, String department) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<User> query = cb.createQuery(User.class);
        Root<User> root = query.from(User.class);
        
        List<Predicate> predicates = new ArrayList<>();
        
        if (StringUtils.hasText(name)) {
            predicates.add(cb.like(cb.lower(root.get("name")), "%" + name.toLowerCase() + "%"));
        }
        
        if (minAge != null) {
            predicates.add(cb.greaterThanOrEqualTo(root.get("age"), minAge));
        }
        
        if (StringUtils.hasText(department)) {
            predicates.add(cb.equal(root.get("department"), department));
        }
        
        query.where(predicates.toArray(new Predicate[0]));
        query.orderBy(cb.asc(root.get("name")));
        
        return entityManager.createQuery(query).getResultList();
    }
    
    @Override
    @Modifying
    public void bulkUpdateUserStatus(List<Long> userIds, UserStatus status) {
        if (userIds.isEmpty()) return;
        
        String jpql = "UPDATE User u SET u.status = :status, u.lastModifiedDate = :now " +
                     "WHERE u.id IN :userIds";
        
        entityManager.createQuery(jpql)
                .setParameter("status", status)
                .setParameter("now", LocalDateTime.now())
                .setParameter("userIds", userIds)
                .executeUpdate();
    }
    
    @Override
    public List<User> findUsersWithDynamicQuery(Map<String, Object> filters) {
        StringBuilder jpql = new StringBuilder("SELECT u FROM User u WHERE 1=1");
        Map<String, Object> parameters = new HashMap<>();
        
        filters.forEach((key, value) -> {
            if (value != null) {
                switch (key) {
                    case "name":
                        jpql.append(" AND LOWER(u.name) LIKE :name");
                        parameters.put("name", "%" + value.toString().toLowerCase() + "%");
                        break;
                    case "department":
                        jpql.append(" AND u.department = :department");
                        parameters.put("department", value);
                        break;
                    case "minAge":
                        jpql.append(" AND u.age >= :minAge");
                        parameters.put("minAge", value);
                        break;
                    case "status":
                        jpql.append(" AND u.status = :status");
                        parameters.put("status", value);
                        break;
                }
            }
        });
        
        Query query = entityManager.createQuery(jpql.toString());
        parameters.forEach(query::setParameter);
        
        return query.getResultList();
    }
    
    @Override
    public Page<UserSummaryDto> findUserSummariesWithStats(Pageable pageable) {
        // Complex query with joins and aggregations
        String jpql = """
            SELECT new com.example.dto.UserSummaryDto(
                u.id, u.name, u.email, u.department,
                COUNT(p.id), SUM(p.budget)
            )
            FROM User u
            LEFT JOIN u.projects p
            GROUP BY u.id, u.name, u.email, u.department
            """;
        
        TypedQuery<UserSummaryDto> query = entityManager.createQuery(jpql, UserSummaryDto.class);
        
        // Apply pagination
        query.setFirstResult((int) pageable.getOffset());
        query.setMaxResults(pageable.getPageSize());
        
        List<UserSummaryDto> content = query.getResultList();
        
        // Get total count for pagination
        String countJpql = "SELECT COUNT(DISTINCT u.id) FROM User u";
        Long total = entityManager.createQuery(countJpql, Long.class).getSingleResult();
        
        return new PageImpl<>(content, pageable, total);
    }
}
```

### 8.1.3 Repository Fragment Pattern

For better organization, you can create repository fragments:

```java
// Base custom interface
public interface CustomUserOperations {
    List<User> searchUsers(UserSearchCriteria criteria);
}

// Analytics fragment
public interface UserAnalyticsOperations {
    UserAnalytics getUserAnalytics(Long userId);
    List<DepartmentStats> getDepartmentStatistics();
}

// Combined repository interface
public interface UserRepository extends JpaRepository<User, Long>, 
                                       CustomUserOperations, 
                                       UserAnalyticsOperations {
    // Standard Spring Data methods
    List<User> findByDepartment(String department);
}

// Implementation classes
@Component
public class CustomUserOperationsImpl implements CustomUserOperations {
    @PersistenceContext
    private EntityManager entityManager;
    
    @Override
    public List<User> searchUsers(UserSearchCriteria criteria) {
        // Implementation using Criteria API or custom JPQL
    }
}

@Component
public class UserAnalyticsOperationsImpl implements UserAnalyticsOperations {
    @PersistenceContext
    private EntityManager entityManager;
    
    @Override
    public UserAnalytics getUserAnalytics(Long userId) {
        // Complex analytics query
    }
}
```

### Real-World Use Case: E-commerce Product Search

```java
public interface ProductRepositoryCustom {
    Page<Product> searchProducts(ProductSearchCriteria criteria, Pageable pageable);
}

@Repository
public class ProductRepositoryImpl implements ProductRepositoryCustom {
    
    @PersistenceContext
    private EntityManager entityManager;
    
    @Override
    public Page<Product> searchProducts(ProductSearchCriteria criteria, Pageable pageable) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Product> query = cb.createQuery(Product.class);
        Root<Product> product = query.from(Product.class);
        
        List<Predicate> predicates = new ArrayList<>();
        
        // Text search in name and description
        if (StringUtils.hasText(criteria.getSearchText())) {
            String searchPattern = "%" + criteria.getSearchText().toLowerCase() + "%";
            Predicate nameMatch = cb.like(cb.lower(product.get("name")), searchPattern);
            Predicate descMatch = cb.like(cb.lower(product.get("description")), searchPattern);
            predicates.add(cb.or(nameMatch, descMatch));
        }
        
        // Price range
        if (criteria.getMinPrice() != null) {
            predicates.add(cb.greaterThanOrEqualTo(product.get("price"), criteria.getMinPrice()));
        }
        if (criteria.getMaxPrice() != null) {
            predicates.add(cb.lessThanOrEqualTo(product.get("price"), criteria.getMaxPrice()));
        }
        
        // Category filter
        if (criteria.getCategoryId() != null) {
            predicates.add(cb.equal(product.get("category").get("id"), criteria.getCategoryId()));
        }
        
        // Brand filter
        if (criteria.getBrandIds() != null && !criteria.getBrandIds().isEmpty()) {
            predicates.add(product.get("brand").get("id").in(criteria.getBrandIds()));
        }
        
        // Rating filter
        if (criteria.getMinRating() != null) {
            predicates.add(cb.greaterThanOrEqualTo(product.get("avgRating"), criteria.getMinRating()));
        }
        
        // Availability
        if (criteria.getInStockOnly()) {
            predicates.add(cb.greaterThan(product.get("stockQuantity"), 0));
        }
        
        query.where(predicates.toArray(new Predicate[0]));
        
        // Sorting
        if (pageable.getSort().isSorted()) {
            List<Order> orders = new ArrayList<>();
            for (Sort.Order sortOrder : pageable.getSort()) {
                Expression<?> expression = product.get(sortOrder.getProperty());
                orders.add(sortOrder.isAscending() ? cb.asc(expression) : cb.desc(expression));
            }
            query.orderBy(orders);
        }
        
        TypedQuery<Product> typedQuery = entityManager.createQuery(query);
        typedQuery.setFirstResult((int) pageable.getOffset());
        typedQuery.setMaxResults(pageable.getPageSize());
        
        List<Product> products = typedQuery.getResultList();
        
        // Count query for pagination
        CriteriaQuery<Long> countQuery = cb.createQuery(Long.class);
        Root<Product> countRoot = countQuery.from(Product.class);
        countQuery.select(cb.count(countRoot));
        countQuery.where(predicates.toArray(new Predicate[0]));
        
        Long total = entityManager.createQuery(countQuery).getSingleResult();
        
        return new PageImpl<>(products, pageable, total);
    }
}
```

## 8.2 Auditing

Auditing automatically tracks when entities are created, modified, and by whom. This is crucial for compliance, debugging, and maintaining data history.

### 8.2.1 Basic Temporal Auditing

Enable auditing in your main application class:

```java
@SpringBootApplication
@EnableJpaAuditing
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
```

Create an abstract auditable entity:

```java
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class AuditableEntity {
    
    @CreatedDate
    @Column(name = "created_date", nullable = false, updatable = false)
    private LocalDateTime createdDate;
    
    @LastModifiedDate
    @Column(name = "last_modified_date")
    private LocalDateTime lastModifiedDate;
    
    @CreatedBy
    @Column(name = "created_by", updatable = false)
    private String createdBy;
    
    @LastModifiedBy
    @Column(name = "last_modified_by")
    private String lastModifiedBy;
    
    // Getters and setters
    public LocalDateTime getCreatedDate() { return createdDate; }
    public void setCreatedDate(LocalDateTime createdDate) { this.createdDate = createdDate; }
    
    public LocalDateTime getLastModifiedDate() { return lastModifiedDate; }
    public void setLastModifiedDate(LocalDateTime lastModifiedDate) { this.lastModifiedDate = lastModifiedDate; }
    
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    
    public String getLastModifiedBy() { return lastModifiedBy; }
    public void setLastModifiedBy(String lastModifiedBy) { this.lastModifiedBy = lastModifiedBy; }
}
```

Use the auditable entity:

```java
@Entity
@Table(name = "users")
public class User extends AuditableEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    private String name;
    private String email;
    
    // Other fields and methods
}
```

### 8.2.2 User Auditing with AuditorAware

Implement AuditorAware to capture the current user:

```java
@Component
public class SpringSecurityAuditorAware implements AuditorAware<String> {
    
    @Override
    public Optional<String> getCurrentAuditor() {
        return Optional.ofNullable(SecurityContextHolder.getContext())
                .map(SecurityContext::getAuthentication)
                .filter(Authentication::isAuthenticated)
                .map(Authentication::getName);
    }
}
```

Or for a simpler approach without Spring Security:

```java
@Component
public class SimpleAuditorAware implements AuditorAware<String> {
    
    @Override
    public Optional<String> getCurrentAuditor() {
        // You could get this from a ThreadLocal, HTTP session, etc.
        String currentUser = getCurrentUserFromContext();
        return Optional.ofNullable(currentUser);
    }
    
    private String getCurrentUserFromContext() {
        // Implementation depends on your authentication mechanism
        return "system"; // Fallback
    }
}
```

### 8.2.3 Advanced Auditing with Custom Fields

```java
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class AdvancedAuditableEntity {
    
    @CreatedDate
    @Column(name = "created_date", nullable = false, updatable = false)
    private LocalDateTime createdDate;
    
    @LastModifiedDate
    @Column(name = "last_modified_date")
    private LocalDateTime lastModifiedDate;
    
    @CreatedBy
    @Column(name = "created_by", updatable = false)
    private String createdBy;
    
    @LastModifiedBy
    @Column(name = "last_modified_by")
    private String lastModifiedBy;
    
    // Custom audit fields
    @Column(name = "ip_address", updatable = false)
    private String ipAddress;
    
    @Column(name = "user_agent", updatable = false)
    private String userAgent;
    
    @Column(name = "version")
    @Version
    private Long version;
    
    // Getters and setters
}

@Component
public class CustomAuditingEntityListener {
    
    @PrePersist
    public void setCreationAuditFields(AdvancedAuditableEntity entity) {
        HttpServletRequest request = getCurrentRequest();
        if (request != null) {
            entity.setIpAddress(getClientIpAddress(request));
            entity.setUserAgent(request.getHeader("User-Agent"));
        }
    }
    
    private HttpServletRequest getCurrentRequest() {
        try {
            return ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes())
                    .getRequest();
        } catch (IllegalStateException e) {
            return null; // Not in web context
        }
    }
    
    private String getClientIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
```

### Real-World Use Case: Document Management System

```java
@Entity
@Table(name = "documents")
@EntityListeners({AuditingEntityListener.class, DocumentAuditListener.class})
public class Document extends AuditableEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    private String title;
    private String content;
    private DocumentStatus status;
    
    // Audit trail for document-specific actions
    @OneToMany(cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id")
    private List<DocumentAuditEntry> auditTrail = new ArrayList<>();
    
    // Getters and setters
}

@Entity
@Table(name = "document_audit_entries")
public class DocumentAuditEntry {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "document_id")
    private Long documentId;
    
    @Enumerated(EnumType.STRING)
    private AuditAction action;
    
    private String details;
    private String performedBy;
    private LocalDateTime performedAt;
    
    // Getters and setters
}

@Component
public class DocumentAuditListener {
    
    @PostPersist
    public void onDocumentCreated(Document document) {
        addAuditEntry(document, AuditAction.CREATED, "Document created");
    }
    
    @PostUpdate
    public void onDocumentUpdated(Document document) {
        addAuditEntry(document, AuditAction.UPDATED, "Document updated");
    }
    
    private void addAuditEntry(Document document, AuditAction action, String details) {
        DocumentAuditEntry entry = new DocumentAuditEntry();
        entry.setDocumentId(document.getId());
        entry.setAction(action);
        entry.setDetails(details);
        entry.setPerformedBy(getCurrentUser());
        entry.setPerformedAt(LocalDateTime.now());
        
        document.getAuditTrail().add(entry);
    }
    
    private String getCurrentUser() {
        // Get current user from security context
        return "current-user";
    }
}
```

## 8.3 Events and Callbacks

JPA provides lifecycle callbacks that allow you to execute code at specific points in an entity's lifecycle.

### 8.3.1 JPA Callbacks

```java
@Entity
@Table(name = "orders")
public class Order {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    private String orderNumber;
    private BigDecimal totalAmount;
    private OrderStatus status;
    
    @PrePersist
    protected void onCreate() {
        if (this.orderNumber == null) {
            this.orderNumber = generateOrderNumber();
        }
        this.status = OrderStatus.PENDING;
        System.out.println("Order is about to be persisted: " + this.orderNumber);
    }
    
    @PostPersist
    protected void onPersist() {
        System.out.println("Order has been persisted: " + this.orderNumber);
        // Send notification, log event, etc.
    }
    
    @PreUpdate
    protected void onUpdate() {
        System.out.println("Order is about to be updated: " + this.orderNumber);
    }
    
    @PostUpdate
    protected void onUpdated() {
        System.out.println("Order has been updated: " + this.orderNumber);
        // Log changes, send notifications
    }
    
    @PreRemove
    protected void onRemove() {
        System.out.println("Order is about to be removed: " + this.orderNumber);
    }
    
    @PostRemove
    protected void onRemoved() {
        System.out.println("Order has been removed: " + this.orderNumber);
        // Cleanup related data
    }
    
    @PostLoad
    protected void onLoad() {
        System.out.println("Order loaded from database: " + this.orderNumber);
        // Initialize transient fields, decrypt data, etc.
    }
    
    private String generateOrderNumber() {
        return "ORD-" + System.currentTimeMillis();
    }
    
    // Getters and setters
}
```

### 8.3.2 EntityListeners

Create separate listener classes for better organization:

```java
@Component
public class OrderEntityListener {
    
    @Autowired
    private OrderNumberGenerator orderNumberGenerator;
    
    @Autowired
    private NotificationService notificationService;
    
    @Autowired
    private OrderHistoryService orderHistoryService;
    
    @PrePersist
    public void beforePersist(Order order) {
        if (order.getOrderNumber() == null) {
            order.setOrderNumber(orderNumberGenerator.generate());
        }
        order.setStatus(OrderStatus.PENDING);
        order.setCreatedDate(LocalDateTime.now());
    }
    
    @PostPersist
    public void afterPersist(Order order) {
        // Send order confirmation
        notificationService.sendOrderConfirmation(order);
        
        // Log order creation
        orderHistoryService.logOrderEvent(order.getId(), "ORDER_CREATED", 
                                         "Order created with number: " + order.getOrderNumber());
    }
    
    @PreUpdate
    public void beforeUpdate(Order order) {
        order.setLastModifiedDate(LocalDateTime.now());
        
        // Capture old state for comparison
        Order oldOrder = orderHistoryService.getOrderSnapshot(order.getId());
        compareAndLogChanges(oldOrder, order);
    }
    
    @PostUpdate
    public void afterUpdate(Order order) {
        // Send status update notification
        if (order.getStatus() == OrderStatus.SHIPPED) {
            notificationService.sendShippingNotification(order);
        }
    }
    
    private void compareAndLogChanges(Order oldOrder, Order newOrder) {
        if (!Objects.equals(oldOrder.getStatus(), newOrder.getStatus())) {
            orderHistoryService.logOrderEvent(newOrder.getId(), "STATUS_CHANGED",
                String.format("Status changed from %s to %s", 
                            oldOrder.getStatus(), newOrder.getStatus()));
        }
    }
}

// Apply the listener to the entity
@Entity
@Table(name = "orders")
@EntityListeners(OrderEntityListener.class)
public class Order {
    // Entity fields and methods
}
```

### 8.3.3 Spring Data Events

Spring Data publishes events during repository operations:

```java
@Component
public class OrderEventListener {
    
    @Autowired
    private EmailService emailService;
    
    @Autowired
    private InventoryService inventoryService;
    
    @EventListener
    public void handleOrderCreated(BeforeSaveEvent<Order> event) {
        Order order = event.getEntity();
        System.out.println("Before saving order: " + order.getOrderNumber());
        
        // Validate inventory before saving
        validateInventory(order);
    }
    
    @EventListener
    public void handleOrderSaved(AfterSaveEvent<Order> event) {
        Order order = event.getEntity();
        System.out.println("After saving order: " + order.getOrderNumber());
        
        // Reserve inventory after successful save
        if (order.getStatus() == OrderStatus.CONFIRMED) {
            inventoryService.reserveItems(order.getOrderItems());
        }
    }
    
    @EventListener
    public void handleOrderDeleted(AfterDeleteEvent<Order> event) {
        Order order = event.getEntity();
        System.out.println("After deleting order: " + order.getOrderNumber());
        
        // Release reserved inventory
        inventoryService.releaseReservation(order.getOrderItems());
    }
    
    private void validateInventory(Order order) {
        for (OrderItem item : order.getOrderItems()) {
            if (!inventoryService.isAvailable(item.getProductId(), item.getQuantity())) {
                throw new InsufficientInventoryException(
                    "Insufficient inventory for product: " + item.getProductId());
            }
        }
    }
}
```

### 8.3.4 Custom Application Events

Create and publish custom events:

```java
// Custom event
public class OrderStatusChangedEvent {
    private final Long orderId;
    private final OrderStatus oldStatus;
    private final OrderStatus newStatus;
    private final String changedBy;
    
    public OrderStatusChangedEvent(Long orderId, OrderStatus oldStatus, 
                                  OrderStatus newStatus, String changedBy) {
        this.orderId = orderId;
        this.oldStatus = oldStatus;
        this.newStatus = newStatus;
        this.changedBy = changedBy;
    }
    
    // Getters
    public Long getOrderId() { return orderId; }
    public OrderStatus getOldStatus() { return oldStatus; }
    public OrderStatus getNewStatus() { return newStatus; }
    public String getChangedBy() { return changedBy; }
}

// Service that publishes events
@Service
public class OrderService {
    
    @Autowired
    private OrderRepository orderRepository;
    
    @Autowired
    private ApplicationEventPublisher eventPublisher;
    
    @Transactional
    public void updateOrderStatus(Long orderId, OrderStatus newStatus) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException("Order not found: " + orderId));
        
        OrderStatus oldStatus = order.getStatus();
        order.setStatus(newStatus);
        
        Order savedOrder = orderRepository.save(order);
        
        // Publish event
        eventPublisher.publishEvent(new OrderStatusChangedEvent(
                orderId, oldStatus, newStatus, getCurrentUser()));
    }
    
    private String getCurrentUser() {
        // Get current user from security context
        return "current-user";
    }
}

// Event listener
@Component
public class OrderStatusEventListener {
    
    @Autowired
    private NotificationService notificationService;
    
    @Autowired
    private WorkflowService workflowService;
    
    @EventListener
    @Async  // Handle asynchronously
    public void handleOrderStatusChanged(OrderStatusChangedEvent event) {
        System.out.println("Order status changed: " + event.getOrderId() + 
                          " from " + event.getOldStatus() + " to " + event.getNewStatus());
        
        // Send notifications based on status change
        switch (event.getNewStatus()) {
            case CONFIRMED:
                notificationService.sendOrderConfirmation(event.getOrderId());
                workflowService.startFulfillmentProcess(event.getOrderId());
                break;
            case SHIPPED:
                notificationService.sendShippingNotification(event.getOrderId());
                break;
            case DELIVERED:
                notificationService.sendDeliveryConfirmation(event.getOrderId());
                workflowService.startReturnWindow(event.getOrderId());
                break;
            case CANCELLED:
                workflowService.processRefund(event.getOrderId());
                break;
        }
    }
}
```

## 8.4 Projections

Projections allow you to retrieve only the data you need, improving performance and reducing memory usage.

### 8.4.1 Interface Projections (Closed Projections)

```java
// Closed projection - only declared methods are included
public interface UserSummary {
    String getName();
    String getEmail();
    String getDepartment();
    
    // Nested projection
    DepartmentInfo getDepartmentInfo();
    
    interface DepartmentInfo {
        String getName();
        String getManager();
    }
}

// Repository method
public interface UserRepository extends JpaRepository<User, Long> {
    List<UserSummary> findByDepartment(String department);
    
    @Query("SELECT u.name as name, u.email as email, u.department as department " +
           "FROM User u WHERE u.active = true")
    List<UserSummary> findActiveUserSummaries();
}

// Usage in service
@Service
public class UserService {
    
    @Autowired
    private UserRepository userRepository;
    
    public List<UserSummary> getActiveUsers() {
        return userRepository.findActiveUserSummaries();
    }
}
```

### 8.4.2 Open Projections with SpEL

```java
public interface UserProjection {
    String getName();
    String getEmail();
    
    // SpEL expression
    @Value("#{target.firstName + ' ' + target.lastName}")
    String getFullName();
    
    // Complex SpEL with method call
    @Value("#{@userService.calculateUserScore(target.id)}")
    Double getUserScore();
    
    // Conditional expression
    @Value("#{target.age >= 18 ? 'Adult' : 'Minor'}")
    String getAgeCategory();
    
    // Collection size
    @Value("#{target.projects.size()}")
    Integer getProjectCount();
}
```

### 8.4.3 Class-based Projections (DTOs)

```java
public class UserDto {
    private final String name;
    private final String email;
    private final String department;
    private final long projectCount;
    
    public UserDto(String name, String email, String department, long projectCount) {
        this.name = name;
        this.email = email;
        this.department = department;
        this.projectCount = projectCount;
    }
    
    // Getters
    public String getName() { return name; }
    public String getEmail() { return email; }
    public String getDepartment() { return department; }
    public long getProjectCount() { return projectCount; }
}

// Repository with constructor expression
public interface UserRepository extends JpaRepository<User, Long> {
    @Query("SELECT new com.example.dto.UserDto(u.name, u.email, u.department, " +
           "SIZE(u.projects)) FROM User u WHERE u.active = true")
    List<UserDto> findActiveUserDtos();
}
```

### 8.4.4 Dynamic Projections

```java
public interface UserRepository extends JpaRepository<User, Long> {
    // Dynamic projection - type determined at runtime
    <T> List<T> findByDepartment(String department, Class<T> type);
    
    <T> Page<T> findByActive(boolean active, Pageable pageable, Class<T> type);
}

// Usage
@Service
public class UserService {
    
    @Autowired
    private UserRepository userRepository;
    
    public List<UserSummary> getUserSummaries(String department) {
        return userRepository.findByDepartment(department, UserSummary.class);
    }
    
    public List<UserDto> getUserDtos(String department) {
        return userRepository.findByDepartment(department, UserDto.class);
    }
    
    public List<User> getFullUsers(String department) {
        return userRepository.findByDepartment(department, User.class);
    }
}
```

### Real-World Use Case: E-commerce Product Catalog

```java
// Product entity (simplified)
@Entity
public class Product {
    @Id
    private Long id;
    private String name;
    private String description;
    private BigDecimal price;
    private Integer stockQuantity;
    
    @ManyToOne
    private Category category;
    
    @OneToMany(mappedBy = "product")
    private List<Review> reviews;
    
    @ManyToOne
    private Brand brand;
    
    // Getters and setters
}

// Various projections for different use cases
public interface ProductSummary {
    Long getId();
    String getName();
    BigDecimal getPrice();
    Integer getStockQuantity();
    
    // Nested projection for category
    CategoryInfo getCategory();
    
    interface CategoryInfo {
        String getName();
        String getCode();
    }
}

public interface ProductListView {
    Long getId();
    String getName();
    BigDecimal getPrice();
    
    // SpEL expressions
    @Value("#{target.stockQuantity > 0}")
    Boolean getInStock();
    
    @Value("#{target.reviews.size()}")
    Integer getReviewCount();
    
    @Value("#{target.reviews.?[rating != null].![rating].size() > 0 ? " +
           "target.reviews.?[rating != null].![rating].sum() / " +
           "target.reviews.?[rating != null].![rating].size() : 0}")
    Double getAverageRating();
}

public interface ProductDetailView {
    Long getId();
    String getName();
    String getDescription();
    BigDecimal getPrice();
    Integer getStockQuantity();
    
    CategoryInfo getCategory();
    BrandInfo getBrand();
    List<ReviewSummary> getReviews();
    
    interface CategoryInfo {
        String getName();
        String getDescription();
    }
    
    interface BrandInfo {
        String getName();
        String getWebsite();
    }
    
    interface ReviewSummary {
        Integer getRating();
        String getComment();
        String getReviewerName();
    }
}

// DTO for complex aggregations
public class ProductAnalyticsDto {
    private final Long productId;
    private final String productName;
    private final BigDecimal totalRevenue;
    private final Long totalSales;
    private final Double averageRating;
    private final Integer totalReviews;
    private final String topSellingMonth;
    
    public ProductAnalyticsDto(Long productId, String productName, 
                              BigDecimal totalRevenue, Long totalSales,
                              Double averageRating, Integer totalReviews,
                              String topSellingMonth) {
        this.productId = productId;
        this.productName = productName;
        this.totalRevenue = totalRevenue;
        this.totalSales = totalSales;
        this.averageRating = averageRating;
        this.totalReviews = totalReviews;
        this.topSellingMonth = topSellingMonth;
    }
    
    // Getters
    public Long getProductId() { return productId; }
    public String getProductName() { return productName; }
    public BigDecimal getTotalRevenue() { return totalRevenue; }
    public Long getTotalSales() { return totalSales; }
    public Double getAverageRating() { return averageRating; }
    public Integer getTotalReviews() { return totalReviews; }
    public String getTopSellingMonth() { return topSellingMonth; }
}

// Repository with various projection methods
public interface ProductRepository extends JpaRepository<Product, Long> {
    
    // Interface projections
    List<ProductSummary> findByCategoryName(String categoryName);
    
    Page<ProductListView> findByStockQuantityGreaterThan(Integer stock, Pageable pageable);
    
    ProductDetailView findProductDetailById(Long id);
    
    // Dynamic projections
    <T> List<T> findByBrandName(String brandName, Class<T> type);
    
    <T> Page<T> findByPriceBetween(BigDecimal minPrice, BigDecimal maxPrice, 
                                   Pageable pageable, Class<T> type);
    
    // Complex DTO projection
    @Query("""
        SELECT new com.example.dto.ProductAnalyticsDto(
            p.id, p.name,
            COALESCE(SUM(oi.price * oi.quantity), 0),
            COALESCE(SUM(oi.quantity), 0),
            AVG(r.rating),
            COUNT(r.id),
            'N/A'
        )
        FROM Product p
        LEFT JOIN OrderItem oi ON oi.product.id = p.id
        LEFT JOIN p.reviews r
        WHERE p.id = :productId
        GROUP BY p.id, p.name
        """)
    ProductAnalyticsDto getProductAnalytics(@Param("productId") Long productId);
    
    // Native query projection
    @Query(value = """
        SELECT 
            p.id as productId,
            p.name as productName,
            COALESCE(monthly_sales.revenue, 0) as monthlyRevenue,
            COALESCE(monthly_sales.quantity, 0) as monthlyQuantity
        FROM products p
        LEFT JOIN (
            SELECT 
                oi.product_id,
                SUM(oi.price * oi.quantity) as revenue,
                SUM(oi.quantity) as quantity
            FROM order_items oi
            JOIN orders o ON o.id = oi.order_id
            WHERE o.created_date >= :startDate 
            AND o.created_date < :endDate
            GROUP BY oi.product_id
        ) monthly_sales ON monthly_sales.product_id = p.id
        WHERE p.category_id = :categoryId
        """, nativeQuery = true)
    List<ProductMonthlySales> findMonthlySalesByCategory(
            @Param("categoryId") Long categoryId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);
}

// Interface for native query projection
public interface ProductMonthlySales {
    Long getProductId();
    String getProductName();
    BigDecimal getMonthlyRevenue();
    Integer getMonthlyQuantity();
}

// Service using different projections
@Service
public class ProductService {
    
    @Autowired
    private ProductRepository productRepository;
    
    // Use summary projection for listing pages
    public List<ProductSummary> getProductsByCategory(String categoryName) {
        return productRepository.findByCategoryName(categoryName);
    }
    
    // Use list view for search results
    public Page<ProductListView> searchProducts(String brandName, Pageable pageable) {
        return productRepository.findByBrandName(brandName, pageable, ProductListView.class);
    }
    
    // Use detail view for product details page
    public ProductDetailView getProductDetails(Long productId) {
        return productRepository.findProductDetailById(productId);
    }
    
    // Use DTO for analytics dashboard
    public ProductAnalyticsDto getProductAnalytics(Long productId) {
        return productRepository.getProductAnalytics(productId);
    }
    
    // Dynamic projection based on user preference
    public <T> List<T> getProductsByBrand(String brandName, Class<T> projectionType) {
        return productRepository.findByBrandName(brandName, projectionType);
    }
}
```

### 8.4.5 Advanced Projection Techniques

```java
// Projection with computed fields using @Formula
@Entity
public class Order {
    @Id
    private Long id;
    
    @OneToMany(mappedBy = "order")
    private List<OrderItem> items;
    
    // Computed field using database function
    @Formula("(SELECT SUM(oi.quantity * oi.price) FROM order_items oi WHERE oi.order_id = id)")
    private BigDecimal totalAmount;
    
    @Formula("(SELECT COUNT(oi.id) FROM order_items oi WHERE oi.order_id = id)")
    private Integer itemCount;
    
    // Getters and setters
}

// Projection interface using @Formula fields
public interface OrderSummaryProjection {
    Long getId();
    String getOrderNumber();
    BigDecimal getTotalAmount();  // Will use @Formula field
    Integer getItemCount();       // Will use @Formula field
    LocalDateTime getCreatedDate();
}

// Complex projection with multiple joins and aggregations
public class OrderAnalyticsDto {
    private final Long orderId;
    private final String customerName;
    private final BigDecimal orderTotal;
    private final Integer itemCount;
    private final String orderStatus;
    private final Integer daysSinceOrder;
    private final BigDecimal avgItemPrice;
    
    public OrderAnalyticsDto(Long orderId, String customerName, 
                           BigDecimal orderTotal, Long itemCount,
                           String orderStatus, Long daysSinceOrder,
                           BigDecimal avgItemPrice) {
        this.orderId = orderId;
        this.customerName = customerName;
        this.orderTotal = orderTotal;
        this.itemCount = itemCount.intValue();
        this.orderStatus = orderStatus;
        this.daysSinceOrder = daysSinceOrder.intValue();
        this.avgItemPrice = avgItemPrice;
    }
    
    // Getters...
}

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {
    
    @Query("""
        SELECT new com.example.dto.OrderAnalyticsDto(
            o.id,
            c.name,
            SUM(oi.quantity * oi.price),
            COUNT(oi.id),
            CAST(o.status AS string),
            DATEDIFF(CURRENT_DATE, o.createdDate),
            AVG(oi.price)
        )
        FROM Order o
        JOIN o.customer c
        JOIN o.items oi
        WHERE o.createdDate >= :startDate
        GROUP BY o.id, c.name, o.status, o.createdDate
        HAVING SUM(oi.quantity * oi.price) > :minAmount
        ORDER BY SUM(oi.quantity * oi.price) DESC
        """)
    List<OrderAnalyticsDto> findOrderAnalytics(
            @Param("startDate") LocalDateTime startDate,
            @Param("minAmount") BigDecimal minAmount);
}
```

## Real-World Integration Examples

### Example 1: Complete Audit Trail System

```java
@Service
@Transactional
public class DocumentManagementService {
    
    @Autowired
    private DocumentRepository documentRepository;
    
    @Autowired
    private ApplicationEventPublisher eventPublisher;
    
    public Document createDocument(DocumentCreateRequest request) {
        Document document = new Document();
        document.setTitle(request.getTitle());
        document.setContent(request.getContent());
        document.setStatus(DocumentStatus.DRAFT);
        
        Document savedDocument = documentRepository.save(document);
        
        // Publish custom event
        eventPublisher.publishEvent(new DocumentCreatedEvent(
                savedDocument.getId(), getCurrentUser()));
        
        return savedDocument;
    }
    
    public void publishDocument(Long documentId) {
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new DocumentNotFoundException("Document not found"));
        
        DocumentStatus oldStatus = document.getStatus();
        document.setStatus(DocumentStatus.PUBLISHED);
        
        documentRepository.save(document);
        
        // Publish status change event
        eventPublisher.publishEvent(new DocumentStatusChangedEvent(
                documentId, oldStatus, DocumentStatus.PUBLISHED, getCurrentUser()));
    }
    
    private String getCurrentUser() {
        return SecurityContextHolder.getContext().getAuthentication().getName();
    }
}

@Component
public class DocumentEventListener {
    
    @Autowired
    private NotificationService notificationService;
    
    @Autowired
    private SearchIndexService searchIndexService;
    
    @EventListener
    @Async
    public void handleDocumentCreated(DocumentCreatedEvent event) {
        // Send notification to subscribers
        notificationService.notifyDocumentCreated(event.getDocumentId());
        
        // Index document for search
        searchIndexService.indexDocument(event.getDocumentId());
    }
    
    @EventListener
    @Async
    public void handleDocumentStatusChanged(DocumentStatusChangedEvent event) {
        if (event.getNewStatus() == DocumentStatus.PUBLISHED) {
            // Notify all users about new published document
            notificationService.notifyDocumentPublished(event.getDocumentId());
            
            // Update search index
            searchIndexService.updateDocumentIndex(event.getDocumentId());
        }
    }
}
```

### Example 2: Performance-Optimized Product Catalog

```java
@Service
public class ProductCatalogService {
    
    @Autowired
    private ProductRepository productRepository;
    
    // Use lightweight projection for list views
    public Page<ProductListView> getProductListing(ProductFilter filter, Pageable pageable) {
        return productRepository.findProductsWithFilter(filter, pageable, ProductListView.class);
    }
    
    // Use detailed projection for product details
    public ProductDetailView getProductDetail(Long productId) {
        return productRepository.findProductDetailById(productId);
    }
    
    // Use DTO for complex analytics
    public List<ProductAnalyticsDto> getTopSellingProducts(int limit) {
        return productRepository.findTopSellingProducts(PageRequest.of(0, limit));
    }
    
    // Custom repository method with complex logic
    public List<Product> findRecommendedProducts(Long userId, int limit) {
        return productRepository.findRecommendedProductsForUser(userId, limit);
    }
}

@Repository
public class ProductRepositoryImpl implements ProductRepositoryCustom {
    
    @PersistenceContext
    private EntityManager entityManager;
    
    @Override
    public List<Product> findRecommendedProductsForUser(Long userId, int limit) {
        // Complex recommendation algorithm using Criteria API
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Product> query = cb.createQuery(Product.class);
        Root<Product> product = query.from(Product.class);
        
        // Join with user's purchase history
        Subquery<Long> purchaseHistory = query.subquery(Long.class);
        Root<Order> orderRoot = purchaseHistory.from(Order.class);
        Join<Order, OrderItem> itemJoin = orderRoot.join("items");
        
        purchaseHistory.select(itemJoin.get("product").get("id"))
                      .where(cb.equal(orderRoot.get("customer").get("id"), userId));
        
        // Find products in similar categories
        Subquery<String> userCategories = query.subquery(String.class);
        Root<Order> categoryOrderRoot = userCategories.from(Order.class);
        Join<Order, OrderItem> categoryItemJoin = categoryOrderRoot.join("items");
        Join<OrderItem, Product> categoryProductJoin = categoryItemJoin.join("product");
        
        userCategories.select(categoryProductJoin.get("category").get("name"))
                     .where(cb.equal(categoryOrderRoot.get("customer").get("id"), userId));
        
        // Build final query
        query.select(product)
             .where(
                 cb.and(
                     cb.not(product.get("id").in(purchaseHistory)),
                     product.get("category").get("name").in(userCategories),
                     cb.greaterThan(product.get("stockQuantity"), 0)
                 )
             )
             .orderBy(cb.desc(product.get("averageRating")));
        
        return entityManager.createQuery(query)
                          .setMaxResults(limit)
                          .getResultList();
    }
}
```

## Best Practices and Performance Tips

### 1. Projection Selection Strategy

```java
@Service
public class OptimizedUserService {
    
    @Autowired
    private UserRepository userRepository;
    
    // Use minimal projections for lists
    public Page<UserSummary> getUserList(Pageable pageable) {
        // Only loads id, name, email, department
        return userRepository.findBy(pageable, UserSummary.class);
    }
    
    // Use full entities only when needed for updates
    public User updateUser(Long userId, UserUpdateRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found"));
        
        // Update fields
        user.setName(request.getName());
        user.setEmail(request.getEmail());
        
        return userRepository.save(user);
    }
    
    // Use DTOs for complex aggregations
    public List<DepartmentStatsDto> getDepartmentStatistics() {
        return userRepository.findDepartmentStatistics();
    }
}
```

### 2. Event Handling Best Practices

```java
@Component
public class OptimizedEventListener {
    
    // Use @Async for non-critical operations
    @EventListener
    @Async
    public void handleUserRegistered(UserRegisteredEvent event) {
        // Send welcome email asynchronously
        emailService.sendWelcomeEmail(event.getUserId());
    }
    
    // Use @TransactionalEventListener for operations that need transaction context
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleOrderCompleted(OrderCompletedEvent event) {
        // This runs after the order transaction is committed
        inventoryService.updateStockLevels(event.getOrderId());
    }
    
    // Handle exceptions gracefully
    @EventListener
    public void handleCriticalEvent(CriticalEvent event) {
        try {
            criticalOperationService.process(event);
        } catch (Exception e) {
            // Log error and handle gracefully
            log.error("Failed to process critical event: " + event.getId(), e);
            alertService.sendAlert("Critical event processing failed", e);
        }
    }
}
```

### 3. Custom Repository Performance

```java
@Repository
public class PerformantCustomRepository {
    
    @PersistenceContext
    private EntityManager entityManager;
    
    // Use batch processing for bulk operations
    @Transactional
    public void bulkUpdateUserStatus(List<Long> userIds, UserStatus status) {
        int batchSize = 1000;
        
        for (int i = 0; i < userIds.size(); i += batchSize) {
            List<Long> batch = userIds.subList(i, 
                    Math.min(i + batchSize, userIds.size()));
            
            entityManager.createQuery(
                    "UPDATE User u SET u.status = :status WHERE u.id IN :ids")
                    .setParameter("status", status)
                    .setParameter("ids", batch)
                    .executeUpdate();
            
            // Clear persistence context to avoid memory issues
            if (i % batchSize == 0) {
                entityManager.flush();
                entityManager.clear();
            }
        }
    }
    
    // Use native queries for complex operations
    public List<MonthlyRevenueDto> getMonthlyRevenue(int year) {
        String sql = """
            SELECT 
                EXTRACT(MONTH FROM o.created_date) as month,
                EXTRACT(YEAR FROM o.created_date) as year,
                SUM(oi.quantity * oi.price) as revenue,
                COUNT(DISTINCT o.id) as order_count
            FROM orders o
            JOIN order_items oi ON oi.order_id = o.id
            WHERE EXTRACT(YEAR FROM o.created_date) = ?1
            AND o.status = 'COMPLETED'
            GROUP BY EXTRACT(MONTH FROM o.created_date), EXTRACT(YEAR FROM o.created_date)
            ORDER BY month
            """;
        
        Query query = entityManager.createNativeQuery(sql);
        query.setParameter(1, year);
        
        @SuppressWarnings("unchecked")
        List<Object[]> results = query.getResultList();
        
        return results.stream()
                .map(row -> new MonthlyRevenueDto(
                        ((Number) row[0]).intValue(),  // month
                        ((Number) row[1]).intValue(),  // year
                        (BigDecimal) row[2],           // revenue
                        ((Number) row[3]).longValue()  // order_count
                ))
                .collect(Collectors.toList());
    }
}
```

This completes Phase 8 of the Spring Data JPA mastery roadmap. You now have a comprehensive understanding of:

1. **Custom Repository Implementation** - How to extend Spring Data repositories with custom business logic
2. **Auditing** - Automatic tracking of entity changes and user information
3. **Events and Callbacks** - Responding to entity lifecycle events and publishing custom events
4. **Projections** - Optimizing data retrieval with interface and class-based projections

Each concept includes real-world examples, performance considerations, and best practices. The code examples demonstrate practical implementations you can use in production applications.

Would you like me to continue with Phase 9 (Enterprise Patterns) next, or would you like to dive deeper into any specific aspect of Phase 8?