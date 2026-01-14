# Phase 7: Transactions & Concurrency - Complete Mastery Guide

## 7.1 Transaction Management

### Understanding Transactions in Spring Data JPA

A transaction is a unit of work that either completely succeeds or completely fails. In Spring Data JPA, transactions are managed through the `@Transactional` annotation and Spring's transaction management infrastructure.

### @Transactional Annotation Deep Dive

#### Basic Usage

```java
@Service
public class BankService {
    
    @Autowired
    private AccountRepository accountRepository;
    
    @Transactional
    public void transferMoney(Long fromAccountId, Long toAccountId, BigDecimal amount) {
        Account fromAccount = accountRepository.findById(fromAccountId)
            .orElseThrow(() -> new AccountNotFoundException("From account not found"));
        Account toAccount = accountRepository.findById(toAccountId)
            .orElseThrow(() -> new AccountNotFoundException("To account not found"));
        
        if (fromAccount.getBalance().compareTo(amount) < 0) {
            throw new InsufficientFundsException("Insufficient funds");
        }
        
        fromAccount.setBalance(fromAccount.getBalance().subtract(amount));
        toAccount.setBalance(toAccount.getBalance().add(amount));
        
        accountRepository.save(fromAccount);
        accountRepository.save(toAccount);
        
        // If any exception occurs here, entire transaction rolls back
        auditService.logTransfer(fromAccountId, toAccountId, amount);
    }
}
```

#### Class-Level vs Method-Level

```java
@Service
@Transactional(readOnly = true) // Default for all methods
public class OrderService {
    
    // This method inherits readOnly = true
    public List<Order> findAllOrders() {
        return orderRepository.findAll();
    }
    
    // This overrides the class-level setting
    @Transactional(readOnly = false)
    public Order createOrder(OrderDTO orderDTO) {
        Order order = new Order();
        // ... order creation logic
        return orderRepository.save(order);
    }
    
    // Explicitly no transaction
    @Transactional(propagation = Propagation.NEVER)
    public void sendEmailNotification(String email, String message) {
        // Email service - should not be in a database transaction
        emailService.send(email, message);
    }
}
```

### Transaction Propagation

Propagation defines how transactions relate to each other when one transactional method calls another.

```java
@Service
public class OrderService {
    
    @Autowired
    private InventoryService inventoryService;
    
    @Autowired
    private PaymentService paymentService;
    
    @Transactional(propagation = Propagation.REQUIRED)
    public Order processOrder(OrderRequest request) {
        Order order = createOrder(request);
        
        // This will join the existing transaction
        inventoryService.reserveItems(request.getItems());
        
        // This will create a new transaction
        paymentService.processPayment(request.getPaymentInfo());
        
        return order;
    }
}

@Service
public class InventoryService {
    
    // REQUIRED (default) - joins existing transaction or creates new one
    @Transactional(propagation = Propagation.REQUIRED)
    public void reserveItems(List<OrderItem> items) {
        for (OrderItem item : items) {
            Product product = productRepository.findById(item.getProductId())
                .orElseThrow(() -> new ProductNotFoundException());
                
            if (product.getQuantity() < item.getQuantity()) {
                throw new InsufficientStockException();
            }
            
            product.setQuantity(product.getQuantity() - item.getQuantity());
            productRepository.save(product);
        }
    }
}

@Service
public class PaymentService {
    
    // REQUIRES_NEW - always creates a new transaction
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PaymentResult processPayment(PaymentInfo paymentInfo) {
        // This runs in a separate transaction
        // If payment fails, it won't rollback the order creation
        Payment payment = new Payment();
        payment.setAmount(paymentInfo.getAmount());
        payment.setStatus(PaymentStatus.PROCESSING);
        
        try {
            // Call external payment gateway
            ExternalPaymentResult result = paymentGateway.charge(paymentInfo);
            payment.setStatus(PaymentStatus.COMPLETED);
            payment.setTransactionId(result.getTransactionId());
        } catch (PaymentException e) {
            payment.setStatus(PaymentStatus.FAILED);
            throw e;
        } finally {
            paymentRepository.save(payment);
        }
        
        return PaymentResult.from(payment);
    }
}
```

#### Complete Propagation Types with Examples

```java
@Service
public class PropagationExampleService {
    
    @Autowired
    private OrderRepository orderRepository;
    
    // REQUIRED (Default) - Join existing or create new
    @Transactional(propagation = Propagation.REQUIRED)
    public void requiredExample() {
        // Implementation
    }
    
    // REQUIRES_NEW - Always create new transaction
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void requiresNewExample() {
        // Runs in completely separate transaction
        // Current transaction is suspended
    }
    
    // SUPPORTS - Join if exists, non-transactional if not
    @Transactional(propagation = Propagation.SUPPORTS)
    public void supportsExample() {
        // Flexible - can run with or without transaction
    }
    
    // NOT_SUPPORTED - Always non-transactional
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void notSupportedExample() {
        // Current transaction is suspended
        // Runs without transaction
    }
    
    // MANDATORY - Must have existing transaction
    @Transactional(propagation = Propagation.MANDATORY)
    public void mandatoryExample() {
        // Throws exception if no transaction exists
    }
    
    // NEVER - Must not have transaction
    @Transactional(propagation = Propagation.NEVER)
    public void neverExample() {
        // Throws exception if transaction exists
    }
    
    // NESTED - Creates savepoint if supported
    @Transactional(propagation = Propagation.NESTED)
    public void nestedExample() {
        // Creates nested transaction with savepoint
        // Rollback to savepoint possible
    }
}
```

### Transaction Isolation Levels

Isolation levels control what data modifications are visible between concurrent transactions.

```java
@Service
public class IsolationExampleService {
    
    @Autowired
    private AccountRepository accountRepository;
    
    // READ_UNCOMMITTED - Lowest isolation, allows dirty reads
    @Transactional(isolation = Isolation.READ_UNCOMMITTED)
    public BigDecimal getAccountBalanceUncommitted(Long accountId) {
        // Can read uncommitted changes from other transactions
        // Risk: Dirty reads
        return accountRepository.findById(accountId)
            .map(Account::getBalance)
            .orElse(BigDecimal.ZERO);
    }
    
    // READ_COMMITTED - Prevents dirty reads
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public BigDecimal getAccountBalanceCommitted(Long accountId) {
        // Only reads committed data
        // Risk: Non-repeatable reads, phantom reads
        return accountRepository.findById(accountId)
            .map(Account::getBalance)
            .orElse(BigDecimal.ZERO);
    }
    
    // REPEATABLE_READ - Prevents dirty and non-repeatable reads
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public AccountSummary getAccountSummaryRepeatable(Long accountId) {
        Account account1 = accountRepository.findById(accountId).orElse(null);
        
        // Some other processing...
        processBusinessLogic();
        
        // This will return the same data as the first read
        Account account2 = accountRepository.findById(accountId).orElse(null);
        
        // Risk: Phantom reads (new records matching criteria)
        return AccountSummary.from(account2);
    }
    
    // SERIALIZABLE - Highest isolation level
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public List<Account> getAccountsSerializable(BigDecimal minBalance) {
        // Complete isolation, prevents all phenomena
        // Risk: Performance impact, potential deadlocks
        return accountRepository.findByBalanceGreaterThan(minBalance);
    }
}
```

### Rollback Rules

Control when transactions should rollback based on exceptions.

```java
@Service
public class RollbackRulesService {
    
    @Autowired
    private OrderRepository orderRepository;
    
    @Autowired
    private NotificationService notificationService;
    
    // Default behavior - rollback only on RuntimeException and Error
    @Transactional
    public void defaultRollbackBehavior() throws IOException {
        Order order = new Order();
        orderRepository.save(order);
        
        // This will cause rollback
        throw new RuntimeException("This causes rollback");
        
        // This will NOT cause rollback (checked exception)
        // throw new IOException("This does NOT cause rollback");
    }
    
    // Custom rollback rules
    @Transactional(rollbackFor = {Exception.class}, noRollbackFor = {BusinessException.class})
    public void customRollbackRules() throws Exception {
        Order order = new Order();
        orderRepository.save(order);
        
        // This will cause rollback (included in rollbackFor)
        // throw new IOException("This now causes rollback");
        
        // This will NOT cause rollback (excluded in noRollbackFor)
        // throw new BusinessException("This does NOT cause rollback");
    }
    
    // Practical example with business logic
    @Transactional(rollbackFor = {PaymentFailedException.class}, 
                   noRollbackFor = {NotificationException.class})
    public Order processOrderWithNotification(OrderRequest request) {
        try {
            Order order = new Order();
            // ... populate order
            order = orderRepository.save(order);
            
            // This failure should rollback the order
            processPayment(order);
            
            // This failure should NOT rollback the order
            notificationService.sendOrderConfirmation(order);
            
            return order;
        } catch (NotificationException e) {
            // Order is still saved, just notification failed
            log.warn("Failed to send notification for order: " + order.getId(), e);
            return order;
        }
    }
}

// Custom exceptions for business logic
public class BusinessException extends Exception {
    public BusinessException(String message) {
        super(message);
    }
}

public class PaymentFailedException extends Exception {
    public PaymentFailedException(String message) {
        super(message);
    }
}

public class NotificationException extends Exception {
    public NotificationException(String message) {
        super(message);
    }
}
```

### Programmatic Transaction Management

Sometimes you need more fine-grained control over transactions.

```java
@Service
public class ProgrammaticTransactionService {
    
    @Autowired
    private TransactionTemplate transactionTemplate;
    
    @Autowired
    private PlatformTransactionManager transactionManager;
    
    @Autowired
    private OrderRepository orderRepository;
    
    // Using TransactionTemplate
    public Order createOrderWithTemplate(OrderRequest request) {
        return transactionTemplate.execute(status -> {
            try {
                Order order = new Order();
                // ... populate order
                order = orderRepository.save(order);
                
                // Some complex business logic
                if (!validateOrder(order)) {
                    status.setRollbackOnly();
                    return null;
                }
                
                return order;
            } catch (Exception e) {
                status.setRollbackOnly();
                throw new RuntimeException("Order creation failed", e);
            }
        });
    }
    
    // Using PlatformTransactionManager directly
    public List<Order> batchCreateOrders(List<OrderRequest> requests) {
        List<Order> createdOrders = new ArrayList<>();
        
        for (OrderRequest request : requests) {
            TransactionDefinition def = new DefaultTransactionDefinition();
            TransactionStatus status = transactionManager.getTransaction(def);
            
            try {
                Order order = new Order();
                // ... populate order
                order = orderRepository.save(order);
                
                transactionManager.commit(status);
                createdOrders.add(order);
                
            } catch (Exception e) {
                transactionManager.rollback(status);
                log.error("Failed to create order: " + request, e);
                // Continue with next order
            }
        }
        
        return createdOrders;
    }
    
    // Complex transaction with multiple savepoints
    public void complexTransactionWithSavepoints() {
        TransactionDefinition def = new DefaultTransactionDefinition();
        TransactionStatus status = transactionManager.getTransaction(def);
        
        try {
            // First operation
            Order order = createBaseOrder();
            
            // Create savepoint
            Object savepoint = status.createSavepoint();
            
            try {
                // Risky operation
                addOrderItems(order);
                
            } catch (Exception e) {
                // Rollback to savepoint, keeping base order
                status.rollbackToSavepoint(savepoint);
                log.warn("Failed to add items, keeping base order", e);
            } finally {
                status.releaseSavepoint(savepoint);
            }
            
            // Final operation
            finalizeOrder(order);
            
            transactionManager.commit(status);
            
        } catch (Exception e) {
            transactionManager.rollback(status);
            throw new RuntimeException("Transaction failed", e);
        }
    }
}
```

### @TransactionalEventListener

Handle events after transaction commit/rollback.

```java
@Entity
public class Order {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    private String customerEmail;
    private BigDecimal total;
    private OrderStatus status;
    
    // ... getters and setters
}

@Service
public class OrderService {
    
    @Autowired
    private OrderRepository orderRepository;
    
    @Autowired
    private ApplicationEventPublisher eventPublisher;
    
    @Transactional
    public Order createOrder(OrderRequest request) {
        Order order = new Order();
        // ... populate order
        order = orderRepository.save(order);
        
        // Publish event - listeners will be called after transaction commits
        eventPublisher.publishEvent(new OrderCreatedEvent(order));
        
        return order;
    }
}

// Event class
public class OrderCreatedEvent {
    private final Order order;
    
    public OrderCreatedEvent(Order order) {
        this.order = order;
    }
    
    public Order getOrder() {
        return order;
    }
}

// Event listeners
@Component
public class OrderEventListener {
    
    @Autowired
    private EmailService emailService;
    
    @Autowired
    private InventoryService inventoryService;
    
    // Called after successful transaction commit
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleOrderCreated(OrderCreatedEvent event) {
        Order order = event.getOrder();
        
        // Send confirmation email (non-transactional)
        emailService.sendOrderConfirmation(order.getCustomerEmail(), order);
        
        // Update inventory (could be in separate transaction)
        inventoryService.reserveItems(order.getItems());
    }
    
    // Called after transaction rollback
    @TransactionalEventListener(phase = TransactionPhase.AFTER_ROLLBACK)
    public void handleOrderCreationFailed(OrderCreatedEvent event) {
        log.warn("Order creation failed and rolled back: " + event.getOrder().getId());
        
        // Cleanup operations, notifications, etc.
    }
    
    // Called before transaction commit
    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void validateOrderBeforeCommit(OrderCreatedEvent event) {
        // Final validations before commit
        Order order = event.getOrder();
        if (!isValidForCommit(order)) {
            throw new IllegalStateException("Order validation failed before commit");
        }
    }
    
    // Called after transaction completion (commit or rollback)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMPLETION)
    public void cleanupAfterOrderProcessing(OrderCreatedEvent event) {
        // Cleanup temporary resources
        cleanupTemporaryFiles(event.getOrder().getId());
    }
}
```

## 7.2 Concurrency Control

### Optimistic Locking with @Version

Optimistic locking assumes that conflicts are rare and detects them when they occur.

```java
@Entity
@Table(name = "accounts")
public class Account {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    private String accountNumber;
    private BigDecimal balance;
    
    @Version
    private Long version;
    
    // Constructors, getters, setters
    
    public void withdraw(BigDecimal amount) {
        if (balance.compareTo(amount) < 0) {
            throw new InsufficientFundsException("Insufficient funds");
        }
        this.balance = this.balance.subtract(amount);
    }
    
    public void deposit(BigDecimal amount) {
        this.balance = this.balance.add(amount);
    }
}

@Service
public class AccountService {
    
    @Autowired
    private AccountRepository accountRepository;
    
    @Transactional
    public void transferMoney(Long fromAccountId, Long toAccountId, BigDecimal amount) {
        try {
            Account fromAccount = accountRepository.findById(fromAccountId)
                .orElseThrow(() -> new AccountNotFoundException("From account not found"));
            Account toAccount = accountRepository.findById(toAccountId)
                .orElseThrow(() -> new AccountNotFoundException("To account not found"));
            
            // These operations will increment the version automatically
            fromAccount.withdraw(amount);
            toAccount.deposit(amount);
            
            // If another transaction modified these entities, OptimisticLockException will be thrown
            accountRepository.save(fromAccount);
            accountRepository.save(toAccount);
            
        } catch (OptimisticLockException e) {
            throw new ConcurrentModificationException("Account was modified by another transaction", e);
        }
    }
    
    // Retry mechanism for optimistic locking conflicts
    @Retryable(value = {OptimisticLockException.class}, maxAttempts = 3, backoff = @Backoff(delay = 100))
    @Transactional
    public void transferMoneyWithRetry(Long fromAccountId, Long toAccountId, BigDecimal amount) {
        transferMoney(fromAccountId, toAccountId, amount);
    }
}

// Custom exception handling
@ControllerAdvice
public class ConcurrencyExceptionHandler {
    
    @ExceptionHandler(OptimisticLockException.class)
    public ResponseEntity<ErrorResponse> handleOptimisticLock(OptimisticLockException e) {
        ErrorResponse error = new ErrorResponse(
            "CONCURRENT_MODIFICATION",
            "The record was modified by another user. Please refresh and try again."
        );
        return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
    }
}
```

### Advanced Version Strategies

```java
@Entity
public class Document {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    private String title;
    private String content;
    
    // Timestamp-based versioning
    @Version
    @Column(name = "last_modified")
    private Timestamp lastModified;
    
    // Custom version field
    @Version
    @Column(name = "revision")
    private Integer revision = 1;
}

@Entity
public class Product {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    private String name;
    private BigDecimal price;
    private Integer quantity;
    
    @Version
    private Long version;
    
    // Method to safely update quantity
    public void updateQuantity(Integer newQuantity) {
        if (newQuantity < 0) {
            throw new IllegalArgumentException("Quantity cannot be negative");
        }
        this.quantity = newQuantity;
    }
}

@Service
public class ProductService {
    
    @Autowired
    private ProductRepository productRepository;
    
    // Batch update with version checking
    @Transactional
    public List<Product> batchUpdateProducts(List<ProductUpdateRequest> updates) {
        List<Product> updatedProducts = new ArrayList<>();
        
        for (ProductUpdateRequest request : updates) {
            try {
                Product product = productRepository.findById(request.getId())
                    .orElseThrow(() -> new ProductNotFoundException("Product not found: " + request.getId()));
                
                // Version check - will throw exception if versions don't match
                if (!product.getVersion().equals(request.getVersion())) {
                    throw new OptimisticLockException("Product version mismatch");
                }
                
                product.setName(request.getName());
                product.setPrice(request.getPrice());
                product.updateQuantity(request.getQuantity());
                
                product = productRepository.save(product);
                updatedProducts.add(product);
                
            } catch (OptimisticLockException e) {
                log.warn("Optimistic lock exception for product: " + request.getId());
                // Could implement retry logic or collect failed updates
            }
        }
        
        return updatedProducts;
    }
}
```

### Pessimistic Locking

Pessimistic locking prevents conflicts by locking records when they are accessed.

```java
@Repository
public interface AccountRepository extends JpaRepository<Account, Long> {
    
    // Pessimistic read lock - other transactions can read but not write
    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("SELECT a FROM Account a WHERE a.id = :id")
    Optional<Account> findByIdWithReadLock(@Param("id") Long id);
    
    // Pessimistic write lock - exclusive access
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Account a WHERE a.id = :id")
    Optional<Account> findByIdWithWriteLock(@Param("id") Long id);
    
    // Pessimistic write lock with timeout
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints({@QueryHint(name = "javax.persistence.lock.timeout", value = "5000")})
    @Query("SELECT a FROM Account a WHERE a.id = :id")
    Optional<Account> findByIdWithWriteLockAndTimeout(@Param("id") Long id);
    
    // Lock multiple records
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Account a WHERE a.id IN :ids ORDER BY a.id")
    List<Account> findByIdsWithWriteLock(@Param("ids") List<Long> ids);
}

@Service
public class PessimisticLockingService {
    
    @Autowired
    private AccountRepository accountRepository;
    
    @Transactional
    public void transferMoneyWithPessimisticLocking(Long fromAccountId, Long toAccountId, BigDecimal amount) {
        // Lock accounts in consistent order to prevent deadlocks
        Long firstId = Math.min(fromAccountId, toAccountId);
        Long secondId = Math.max(fromAccountId, toAccountId);
        
        Account firstAccount = accountRepository.findByIdWithWriteLock(firstId)
            .orElseThrow(() -> new AccountNotFoundException("Account not found: " + firstId));
        Account secondAccount = accountRepository.findByIdWithWriteLock(secondId)
            .orElseThrow(() -> new AccountNotFoundException("Account not found: " + secondId));
        
        // Determine which is from and which is to
        Account fromAccount = firstAccount.getId().equals(fromAccountId) ? firstAccount : secondAccount;
        Account toAccount = firstAccount.getId().equals(toAccountId) ? firstAccount : secondAccount;
        
        if (fromAccount.getBalance().compareTo(amount) < 0) {
            throw new InsufficientFundsException("Insufficient funds");
        }
        
        fromAccount.withdraw(amount);
        toAccount.deposit(amount);
        
        // Locks are held until transaction commits
        accountRepository.save(fromAccount);
        accountRepository.save(toAccount);
    }
    
    // Using EntityManager for more control
    @PersistenceContext
    private EntityManager entityManager;
    
    @Transactional
    public void transferWithEntityManagerLocking(Long fromAccountId, Long toAccountId, BigDecimal amount) {
        try {
            // Lock with timeout
            Map<String, Object> properties = new HashMap<>();
            properties.put("javax.persistence.lock.timeout", 5000);
            
            Account fromAccount = entityManager.find(Account.class, fromAccountId, 
                LockModeType.PESSIMISTIC_WRITE, properties);
            Account toAccount = entityManager.find(Account.class, toAccountId, 
                LockModeType.PESSIMISTIC_WRITE, properties);
            
            if (fromAccount == null || toAccount == null) {
                throw new AccountNotFoundException("One or both accounts not found");
            }
            
            fromAccount.withdraw(amount);
            toAccount.deposit(amount);
            
        } catch (LockTimeoutException e) {
            throw new ConcurrencyException("Unable to acquire lock within timeout period", e);
        } catch (PessimisticLockException e) {
            throw new ConcurrencyException("Failed to acquire pessimistic lock", e);
        }
    }
}
```

### Lock Timeouts and Deadlock Handling

```java
@Service
public class DeadlockHandlingService {
    
    @Autowired
    private AccountRepository accountRepository;
    
    @Retryable(
        value = {CannotAcquireLockException.class, PessimisticLockException.class},
        maxAttempts = 3,
        backoff = @Backoff(delay = 100, multiplier = 2)
    )
    @Transactional(timeout = 30) // 30 second timeout
    public void transferWithDeadlockHandling(Long fromAccountId, Long toAccountId, BigDecimal amount) {
        try {
            // Always lock accounts in same order to prevent deadlocks
            List<Long> accountIds = Arrays.asList(fromAccountId, toAccountId);
            Collections.sort(accountIds);
            
            List<Account> accounts = accountRepository.findByIdsWithWriteLock(accountIds);
            
            Account fromAccount = accounts.stream()
                .filter(acc -> acc.getId().equals(fromAccountId))
                .findFirst()
                .orElseThrow(() -> new AccountNotFoundException("From account not found"));
                
            Account toAccount = accounts.stream()
                .filter(acc -> acc.getId().equals(toAccountId))
                .findFirst()
                .orElseThrow(() -> new AccountNotFoundException("To account not found"));
            
            fromAccount.withdraw(amount);
            toAccount.deposit(amount);
            
            accountRepository.saveAll(Arrays.asList(fromAccount, toAccount));
            
        } catch (CannotAcquireLockException e) {
            log.warn("Could not acquire lock, retrying...", e);
            throw e; // Will be retried by @Retryable
        }
    }
    
    // Manual deadlock detection and handling
    @Transactional
    public void transferWithManualDeadlockHandling(Long fromAccountId, Long toAccountId, BigDecimal amount) {
        int maxRetries = 3;
        int attempt = 0;
        
        while (attempt < maxRetries) {
            try {
                attempt++;
                performTransfer(fromAccountId, toAccountId, amount);
                return; // Success
                
            } catch (CannotAcquireLockException | PessimisticLockException e) {
                if (attempt >= maxRetries) {
                    throw new ConcurrencyException("Failed to complete transfer after " + maxRetries + " attempts", e);
                }
                
                // Exponential backoff
                try {
                    Thread.sleep(100 * attempt);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Interrupted during retry", ie);
                }
            }
        }
    }
}

// Global exception handler for concurrency issues
@ControllerAdvice
public class ConcurrencyExceptionHandler {
    
    @ExceptionHandler({CannotAcquireLockException.class, PessimisticLockException.class})
    public ResponseEntity<ErrorResponse> handleLockException(Exception e) {
        ErrorResponse error = new ErrorResponse(
            "LOCK_TIMEOUT",
            "System is busy. Please try again in a few moments."
        );
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(error);
    }
    
    @ExceptionHandler(OptimisticLockException.class)
    public ResponseEntity<ErrorResponse> handleOptimisticLockException(OptimisticLockException e) {
        ErrorResponse error = new ErrorResponse(
            "CONCURRENT_UPDATE",
            "Data was updated by another user. Please refresh and try again."
        );
        return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
    }
}
```

## 7.3 Batch Processing

### Batch Configuration

```java
// Application properties
spring.jpa.properties.hibernate.jdbc.batch_size=25
spring.jpa.properties.hibernate.order_inserts=true
spring.jpa.properties.hibernate.order_updates=true
spring.jpa.properties.hibernate.batch_versioned_data=true

// For batch processing
@Configuration
public class BatchConfiguration {
    
    @Bean
    @ConfigurationProperties(prefix = "spring.datasource.batch")
    public DataSource batchDataSource() {
        return DataSourceBuilder.create().build();
    }
    
    @Bean
    public JdbcTemplate batchJdbcTemplate(@Qualifier("batchDataSource") DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }
}
```

### Batch Inserts with JPA

```java
@Service
public class BatchInsertService {
    
    @PersistenceContext
    private EntityManager entityManager;
    
    @Autowired
    private ProductRepository productRepository;
    
    @Transactional
    public void batchInsertProducts(List<Product> products) {
        int batchSize = 25;
        
        for (int i = 0; i < products.size(); i++) {
            entityManager.persist(products.get(i));
            
            if (i > 0 && i % batchSize == 0) {
                // Flush and clear every batch_size
                entityManager.flush();
                entityManager.clear();
            }
        }
        
        // Flush remaining
        entityManager.flush();
        entityManager.clear();
    }
    
    // Using Spring Data JPA saveAll
    @Transactional
    public List<Product> batchSaveProducts(List<Product> products) {
        // Spring Data JPA will handle batching automatically if configured
        return productRepository.saveAll(products);
    }
    
    // Manual batching with saveAll
    @Transactional
    public List<Product> batchSaveProductsManual(List<Product> products) {
        List<Product> savedProducts = new ArrayList<>();
        int batchSize = 25;
        
        for (int i = 0; i < products.size(); i += batchSize) {
            int endIndex = Math.min(i + batchSize, products.size());
            List<Product> batch = products.subList(i, endIndex);
            
            savedProducts.addAll(productRepository.saveAll(batch));
            productRepository.flush(); // Force immediate execution
        }
        
        return savedProducts;
    }
    
    // Batch insert with custom SQL
    @Autowired
    private JdbcTemplate jdbcTemplate;
    
    @Transactional
    public void batchInsertWithJdbc(List<Product> products) {
        String sql = "INSERT INTO products (name, price, quantity, category_id) VALUES (?, ?, ?, ?)";
        
        List<Object[]> batchArgs = products.stream()
            .map(product -> new Object[]{
                product.getName(),
                product.getPrice(),
                product.getQuantity(),
                product.getCategory().getId()
            })
            .collect(Collectors.toList());
        
        jdbcTemplate.batchUpdate(sql, batchArgs);
    }
}

// Optimized batch repository
@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {
    
    // Custom bulk insert method
    @Modifying
    @Query(value = "INSERT INTO products (name, price, quantity) VALUES ?1", nativeQuery = true)
    void bulkInsert(@Param("products") List<Object[]> products);
    
    // Bulk update
    @Modifying
    @Query("UPDATE Product p SET p.price = p.price * :multiplier WHERE p.category.id = :categoryId")
    int bulkUpdatePricesByCategory(@Param("categoryId") Long categoryId, @Param("multiplier") BigDecimal multiplier);
}
```

### Batch Updates and Deletes

```java
@Service
public class BatchUpdateService {
    
    @Autowired
    private ProductRepository productRepository;
    
    @PersistenceContext
    private EntityManager entityManager;
    
    // Batch update with JPA
    @Transactional
    public void batchUpdateProductPrices(List<ProductPriceUpdate> updates) {
        int batchSize = 25;
        
        for (int i = 0; i < updates.size(); i++) {
            ProductPriceUpdate update = updates.get(i);
            
            Product product = entityManager.find(Product.class, update.getProductId());
            if (product != null) {
                product.setPrice(update.getNewPrice());
                entityManager.merge(product);
            }
            
            if (i > 0 && i % batchSize == 0) {
                entityManager.flush();
                entityManager.clear();
            }
        }
        
        entityManager.flush();
        entityManager.clear();
    }
    
    // Bulk update with JPQL
    @Transactional
    public int bulkUpdateProductStatus(ProductStatus oldStatus, ProductStatus newStatus) {
        return entityManager.createQuery(
                "UPDATE Product p SET p.status = :newStatus WHERE p.status = :oldStatus")
            .setParameter("newStatus", newStatus)
            .setParameter("oldStatus", oldStatus)
            .executeUpdate();
    }
    
    // Bulk delete
    @Transactional
    public int bulkDeleteInactiveProducts(LocalDateTime cutoffDate) {
        return entityManager.createQuery(
                "DELETE FROM Product p WHERE p.lastAccessDate < :cutoffDate AND p.status = :status")
            .setParameter("cutoffDate", cutoffDate)
            .setParameter("status", ProductStatus.INACTIVE)
            .executeUpdate();
    }
    
    // Batch processing with pagination to avoid memory issues
    @Transactional
    public void batchProcessLargeDataset(ProcessingFunction<Product> processor) {
        int pageSize = 100;
        int pageNumber = 0;
        
        Page<Product> page;
        do {
            Pageable pageable = PageRequest.of(pageNumber, pageSize);
            page = productRepository.findAll(pageable);
            
            for (Product product : page.getContent()) {
                processor.process(product);
            }
            
            // Clear persistence context to avoid memory issues
            entityManager.flush();
            entityManager.clear();
            
            pageNumber++;
        } while (page.hasNext());
    }
}

@FunctionalInterface
public interface ProcessingFunction<T> {
    void process(T item);
}
```

### StatelessSession for High-Volume Processing

```java
@Service
public class StatelessSessionService {
    
    @Autowired
    private SessionFactory sessionFactory;
    
    public void massDataImport(List<Product> products) {
        StatelessSession statelessSession = sessionFactory.openStatelessSession();
        Transaction transaction = statelessSession.beginTransaction();
        
        try {
            for (int i = 0; i < products.size(); i++) {
                statelessSession.insert(products.get(i));
                
                // Batch processing
                if (i % 100 == 0) {
                    statelessSession.getTransaction().commit();
                    statelessSession.getTransaction().begin();
                }
            }
            
            transaction.commit();
        } catch (Exception e) {
            transaction.rollback();
            throw new RuntimeException("Mass import failed", e);
        } finally {
            statelessSession.close();
        }
    }
    
    // Mass update with StatelessSession
    public void massUpdateProductPrices(BigDecimal multiplier) {
        StatelessSession statelessSession = sessionFactory.openStatelessSession();
        Transaction transaction = statelessSession.beginTransaction();
        
        try {
            // Use ScrollableResults for memory-efficient processing
            ScrollableResults results = statelessSession.createQuery(
                    "FROM Product p WHERE p.status = :status")
                .setParameter("status", ProductStatus.ACTIVE)
                .scroll(ScrollMode.FORWARD_ONLY);
            
            int count = 0;
            while (results.next()) {
                Product product = (Product) results.get(0);
                product.setPrice(product.getPrice().multiply(multiplier));
                
                statelessSession.update(product);
                
                if (++count % 100 == 0) {
                    statelessSession.getTransaction().commit();
                    statelessSession.getTransaction().begin();
                }
            }
            
            transaction.commit();
            results.close();
            
        } catch (Exception e) {
            transaction.rollback();
            throw new RuntimeException("Mass update failed", e);
        } finally {
            statelessSession.close();
        }
    }
}
```

## Real-World Use Cases and Best Practices

### Use Case 1: E-commerce Order Processing

```java
@Service
public class EcommerceOrderService {
    
    @Autowired
    private OrderRepository orderRepository;
    
    @Autowired
    private InventoryService inventoryService;
    
    @Autowired
    private PaymentService paymentService;
    
    @Autowired
    private ApplicationEventPublisher eventPublisher;
    
    /**
     * Complete order processing with proper transaction management
     * - Create order (REQUIRED transaction)
     * - Reserve inventory (joins transaction - rollback if fails)
     * - Process payment (separate transaction - don't rollback order if fails)
     * - Send notifications (after commit)
     */
    @Transactional(rollbackFor = {InventoryException.class}, 
                   noRollbackFor = {PaymentException.class, NotificationException.class})
    public Order processOrder(OrderRequest request) {
        try {
            // 1. Create order
            Order order = createOrder(request);
            
            // 2. Reserve inventory - should rollback if fails
            inventoryService.reserveItems(order.getItems());
            
            // 3. Process payment - should NOT rollback order if fails
            try {
                PaymentResult paymentResult = paymentService.processPayment(order.getTotal());
                order.setPaymentStatus(PaymentStatus.COMPLETED);
                order.setPaymentId(paymentResult.getTransactionId());
            } catch (PaymentException e) {
                order.setPaymentStatus(PaymentStatus.FAILED);
                // Order is still saved, payment can be retried later
            }
            
            order.setStatus(OrderStatus.CONFIRMED);
            order = orderRepository.save(order);
            
            // 4. Publish event for async processing (notifications, etc.)
            eventPublisher.publishEvent(new OrderProcessedEvent(order));
            
            return order;
            
        } catch (InventoryException e) {
            // Inventory reservation failed - rollback everything
            throw e;
        }
    }
    
    private Order createOrder(OrderRequest request) {
        Order order = new Order();
        order.setCustomerId(request.getCustomerId());
        order.setItems(request.getItems());
        order.setTotal(calculateTotal(request.getItems()));
        order.setStatus(OrderStatus.PENDING);
        order.setPaymentStatus(PaymentStatus.PENDING);
        
        return orderRepository.save(order);
    }
}

@Component
public class OrderEventHandler {
    
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleOrderProcessed(OrderProcessedEvent event) {
        Order order = event.getOrder();
        
        // Send confirmation email
        emailService.sendOrderConfirmation(order);
        
        // Update analytics
        analyticsService.recordOrderPlacement(order);
        
        // Trigger fulfillment process
        fulfillmentService.scheduleOrderFulfillment(order);
    }
    
    @TransactionalEventListener(phase = TransactionPhase.AFTER_ROLLBACK)
    public void handleOrderProcessingFailed(OrderProcessedEvent event) {
        // Cleanup any external resources
        // Log for monitoring/alerting
        log.error("Order processing failed and was rolled back: " + event.getOrder().getId());
    }
}
```

### Use Case 2: Banking System with Concurrency Control

```java
@Entity
public class BankAccount {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    private String accountNumber;
    private BigDecimal balance;
    private AccountStatus status;
    
    @Version
    private Long version;
    
    // Optimistic locking with business validation
    public void withdraw(BigDecimal amount) {
        if (status != AccountStatus.ACTIVE) {
            throw new AccountNotActiveException("Account is not active");
        }
        if (balance.compareTo(amount) < 0) {
            throw new InsufficientFundsException("Insufficient funds");
        }
        this.balance = this.balance.subtract(amount);
    }
    
    public void deposit(BigDecimal amount) {
        if (status != AccountStatus.ACTIVE) {
            throw new AccountNotActiveException("Account is not active");
        }
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Deposit amount must be positive");
        }
        this.balance = this.balance.add(amount);
    }
}

@Service
public class BankingService {
    
    @Autowired
    private BankAccountRepository accountRepository;
    
    @Autowired
    private TransactionLogRepository transactionLogRepository;
    
    /**
     * Money transfer with optimistic locking and retry mechanism
     */
    @Retryable(value = {OptimisticLockException.class}, maxAttempts = 5, backoff = @Backoff(delay = 50))
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public TransferResult transferMoney(TransferRequest request) {
        try {
            // Load accounts with optimistic locking
            BankAccount fromAccount = accountRepository.findById(request.getFromAccountId())
                .orElseThrow(() -> new AccountNotFoundException("From account not found"));
            BankAccount toAccount = accountRepository.findById(request.getToAccountId())
                .orElseThrow(() -> new AccountNotFoundException("To account not found"));
            
            // Perform transfer
            fromAccount.withdraw(request.getAmount());
            toAccount.deposit(request.getAmount());
            
            // Save both accounts (optimistic lock check happens here)
            accountRepository.save(fromAccount);
            accountRepository.save(toAccount);
            
            // Log transaction
            TransactionLog log = new TransactionLog();
            log.setFromAccountId(request.getFromAccountId());
            log.setToAccountId(request.getToAccountId());
            log.setAmount(request.getAmount());
            log.setType(TransactionType.TRANSFER);
            log.setStatus(TransactionStatus.COMPLETED);
            log.setTimestamp(LocalDateTime.now());
            
            transactionLogRepository.save(log);
            
            return TransferResult.success(log.getId());
            
        } catch (OptimisticLockException e) {
            // Log retry attempt
            log.info("Optimistic lock exception during transfer, retrying...");
            throw e; // Will be retried by @Retryable
        }
    }
    
    /**
     * High-frequency operations with pessimistic locking
     * Used for operations where conflicts are expected
     */
    @Transactional
    public void highFrequencyTransfer(Long fromAccountId, Long toAccountId, BigDecimal amount) {
        // Lock accounts in consistent order to prevent deadlocks
        Long firstId = Math.min(fromAccountId, toAccountId);
        Long secondId = Math.max(fromAccountId, toAccountId);
        
        BankAccount firstAccount = accountRepository.findByIdWithPessimisticLock(firstId)
            .orElseThrow(() -> new AccountNotFoundException("Account not found: " + firstId));
        BankAccount secondAccount = accountRepository.findByIdWithPessimisticLock(secondId)
            .orElseThrow(() -> new AccountNotFoundException("Account not found: " + secondId));
        
        // Determine which is from and which is to
        BankAccount fromAccount = firstAccount.getId().equals(fromAccountId) ? firstAccount : secondAccount;
        BankAccount toAccount = firstAccount.getId().equals(toAccountId) ? firstAccount : secondAccount;
        
        fromAccount.withdraw(amount);
        toAccount.deposit(amount);
        
        // Locks are held until transaction commits
        accountRepository.save(fromAccount);
        accountRepository.save(toAccount);
    }
}

@Repository
public interface BankAccountRepository extends JpaRepository<BankAccount, Long> {
    
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM BankAccount a WHERE a.id = :id")
    Optional<BankAccount> findByIdWithPessimisticLock(@Param("id") Long id);
    
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints({@QueryHint(name = "javax.persistence.lock.timeout", value = "2000")})
    @Query("SELECT a FROM BankAccount a WHERE a.id = :id")
    Optional<BankAccount> findByIdWithPessimisticLockAndTimeout(@Param("id") Long id);
}
```

### Use Case 3: Inventory Management with Batch Processing

```java
@Service
public class InventoryManagementService {
    
    @Autowired
    private ProductRepository productRepository;
    
    @Autowired
    private InventoryMovementRepository movementRepository;
    
    /**
     * Bulk inventory update with batch processing
     */
    @Transactional
    public void bulkInventoryUpdate(List<InventoryUpdateRequest> updates) {
        int batchSize = 50;
        
        for (int i = 0; i < updates.size(); i += batchSize) {
            int endIndex = Math.min(i + batchSize, updates.size());
            List<InventoryUpdateRequest> batch = updates.subList(i, endIndex);
            
            processBatchUpdate(batch);
            
            // Clear persistence context to manage memory
            entityManager.flush();
            entityManager.clear();
        }
    }
    
    @PersistenceContext
    private EntityManager entityManager;
    
    private void processBatchUpdate(List<InventoryUpdateRequest> batch) {
        // Get all product IDs
        List<Long> productIds = batch.stream()
            .map(InventoryUpdateRequest::getProductId)
            .collect(Collectors.toList());
        
        // Load all products at once
        List<Product> products = productRepository.findAllById(productIds);
        Map<Long, Product> productMap = products.stream()
            .collect(Collectors.toMap(Product::getId, Function.identity()));
        
        // Process updates
        List<InventoryMovement> movements = new ArrayList<>();
        
        for (InventoryUpdateRequest update : batch) {
            Product product = productMap.get(update.getProductId());
            if (product != null) {
                int oldQuantity = product.getQuantity();
                product.setQuantity(update.getNewQuantity());
                
                // Create movement record
                InventoryMovement movement = new InventoryMovement();
                movement.setProductId(product.getId());
                movement.setOldQuantity(oldQuantity);
                movement.setNewQuantity(update.getNewQuantity());
                movement.setMovementType(MovementType.ADJUSTMENT);
                movement.setReason(update.getReason());
                movement.setTimestamp(LocalDateTime.now());
                
                movements.add(movement);
            }
        }
        
        // Save all at once
        productRepository.saveAll(products);
        movementRepository.saveAll(movements);
    }
    
    /**
     * Periodic inventory reconciliation with large dataset processing
     */
    @Transactional(timeout = 300) // 5 minute timeout for large operations
    public void reconcileInventory() {
        int pageSize = 1000;
        int pageNumber = 0;
        
        Page<Product> page;
        do {
            Pageable pageable = PageRequest.of(pageNumber, pageSize);
            page = productRepository.findAll(pageable);
            
            List<Product> productsToUpdate = new ArrayList<>();
            
            for (Product product : page.getContent()) {
                int actualQuantity = calculateActualQuantity(product.getId());
                if (product.getQuantity() != actualQuantity) {
                    product.setQuantity(actualQuantity);
                    productsToUpdate.add(product);
                }
            }
            
            if (!productsToUpdate.isEmpty()) {
                productRepository.saveAll(productsToUpdate);
            }
            
            // Clear persistence context to avoid memory issues
            entityManager.flush();
            entityManager.clear();
            
            pageNumber++;
        } while (page.hasNext());
    }
    
    private int calculateActualQuantity(Long productId) {
        // Complex calculation logic here
        return 0; // Placeholder
    }
}
```

## Performance Best Practices

### Transaction Configuration Best Practices

```java
// Configuration for optimal transaction performance
@Configuration
@EnableTransactionManagement
public class TransactionConfig {
    
    @Bean
    public PlatformTransactionManager transactionManager(EntityManagerFactory emf) {
        JpaTransactionManager transactionManager = new JpaTransactionManager();
        transactionManager.setEntityManagerFactory(emf);
        
        // Set default timeout (in seconds)
        transactionManager.setDefaultTimeout(30);
        
        // Enable nested transactions if supported
        transactionManager.setNestedTransactionAllowed(true);
        
        return transactionManager;
    }
    
    // Custom transaction template for programmatic transactions
    @Bean
    public TransactionTemplate transactionTemplate(PlatformTransactionManager transactionManager) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
        template.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        template.setTimeout(30);
        return template;
    }
}

// Monitoring and metrics
@Component
public class TransactionMonitor {
    
    private final MeterRegistry meterRegistry;
    
    public TransactionMonitor(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }
    
    @EventListener
    public void handleTransactionCommit(TransactionCommitEvent event) {
        Timer.Sample sample = Timer.start(meterRegistry);
        sample.stop(Timer.builder("transaction.commit.time")
            .description("Transaction commit time")
            .register(meterRegistry));
    }
    
    @EventListener
    public void handleTransactionRollback(TransactionRollbackEvent event) {
        meterRegistry.counter("transaction.rollback.count",
            "cause", event.getCause().getClass().getSimpleName())
            .increment();
    }
}
```

## Key Takeaways and Next Steps

### Critical Points to Remember:

1. **Transaction Boundaries**: Keep transactions as short as possible while maintaining data consistency
2. **Optimistic vs Pessimistic**: Use optimistic locking for low-conflict scenarios, pessimistic for high-conflict
3. **Batch Processing**: Essential for handling large datasets efficiently
4. **Deadlock Prevention**: Always lock resources in consistent order
5. **Event Handling**: Use `@TransactionalEventListener` for post-commit operations

### Common Pitfalls to Avoid:

1. **Long-running transactions** - Can cause performance issues and deadlocks
2. **Improper rollback rules** - Not configuring rollback for business exceptions
3. **N+1 problems in transactions** - Loading entities individually instead of batch loading
4. **Ignoring version conflicts** - Not handling OptimisticLockException properly
5. **Mixing business logic with transaction logic** - Keep transaction boundaries clean

### Performance Optimization Tips:

1. Configure proper batch sizes for bulk operations
2. Use read-only transactions for queries
3. Implement proper retry mechanisms for lock conflicts
4. Monitor transaction metrics and slow queries
5. Use appropriate isolation levels for your use case

Ready to move to **Phase 8: Advanced Features**? This will cover custom repository implementations, auditing, events, and projections!