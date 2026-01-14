# Phase 11: Production Considerations - Complete Guide

## 11.1 Monitoring and Metrics

### JPA Metrics: Hibernate Statistics

Hibernate provides comprehensive statistics about your JPA operations. Here's how to enable and use them:

#### Configuration
```yaml
# application.yml
spring:
  jpa:
    properties:
      hibernate:
        generate_statistics: true
        session:
          events:
            log:
              LOG_QUERIES_SLOWER_THAN_MS: 1000

management:
  endpoints:
    web:
      exposure:
        include: metrics, health, info, hibernate
  metrics:
    export:
      prometheus:
        enabled: true
```

#### Custom Metrics Configuration
```java
@Configuration
@EnableConfigurationProperties
public class HibernateMetricsConfig {

    @Bean
    public HibernateMetrics hibernateMetrics(EntityManagerFactory entityManagerFactory) {
        return new HibernateMetrics(entityManagerFactory, "hibernate", null);
    }

    @Component
    public static class DatabaseMetricsCollector {
        
        private final MeterRegistry meterRegistry;
        private final EntityManagerFactory entityManagerFactory;
        
        public DatabaseMetricsCollector(MeterRegistry meterRegistry, 
                                       EntityManagerFactory entityManagerFactory) {
            this.meterRegistry = meterRegistry;
            this.entityManagerFactory = entityManagerFactory;
            
            // Register custom metrics
            registerCustomMetrics();
        }
        
        private void registerCustomMetrics() {
            Gauge.builder("hibernate.sessions.open")
                .description("Number of currently open Hibernate sessions")
                .register(meterRegistry, this, 
                    metrics -> getHibernateStatistics().getSessionOpenCount());
                    
            Gauge.builder("hibernate.transactions.count")
                .description("Total number of transactions")
                .register(meterRegistry, this,
                    metrics -> getHibernateStatistics().getTransactionCount());
        }
        
        private Statistics getHibernateStatistics() {
            return entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        }
    }
}
```

#### Metrics Service for Custom Monitoring
```java
@Service
@Slf4j
public class DatabaseMetricsService {
    
    private final EntityManagerFactory entityManagerFactory;
    private final MeterRegistry meterRegistry;
    private final Timer.Sample queryTimer;
    
    public DatabaseMetricsService(EntityManagerFactory entityManagerFactory,
                                 MeterRegistry meterRegistry) {
        this.entityManagerFactory = entityManagerFactory;
        this.meterRegistry = meterRegistry;
    }
    
    public Statistics getHibernateStatistics() {
        return entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
    }
    
    public void logDatabaseMetrics() {
        Statistics stats = getHibernateStatistics();
        
        log.info("=== Database Metrics ===");
        log.info("Entity Load Count: {}", stats.getEntityLoadCount());
        log.info("Entity Insert Count: {}", stats.getEntityInsertCount());
        log.info("Entity Update Count: {}", stats.getEntityUpdateCount());
        log.info("Entity Delete Count: {}", stats.getEntityDeleteCount());
        log.info("Query Execution Count: {}", stats.getQueryExecutionCount());
        log.info("Query Cache Hit Count: {}", stats.getQueryCacheHitCount());
        log.info("Query Cache Miss Count: {}", stats.getQueryCacheMissCount());
        log.info("Second Level Cache Hit Count: {}", stats.getSecondLevelCacheHitCount());
        log.info("Second Level Cache Miss Count: {}", stats.getSecondLevelCacheMissCount());
        log.info("Session Open Count: {}", stats.getSessionOpenCount());
        log.info("Session Close Count: {}", stats.getSessionCloseCount());
        log.info("Transaction Count: {}", stats.getTransactionCount());
        log.info("Connection Count: {}", stats.getConnectCount());
    }
    
    @EventListener
    public void handleQueryExecution(QueryExecutionEvent event) {
        Timer.Sample sample = Timer.start(meterRegistry);
        sample.stop(Timer.builder("database.query.execution.time")
            .description("Database query execution time")
            .tag("query_type", event.getQueryType())
            .register(meterRegistry));
    }
}

// Custom event for query execution
public class QueryExecutionEvent {
    private final String queryType;
    private final long executionTime;
    
    public QueryExecutionEvent(String queryType, long executionTime) {
        this.queryType = queryType;
        this.executionTime = executionTime;
    }
    
    // getters...
}
```

### Spring Boot Actuator: Database Health Checks

#### Custom Health Indicator
```java
@Component
public class DatabaseHealthIndicator implements HealthIndicator {
    
    private final DataSource dataSource;
    private final EntityManagerFactory entityManagerFactory;
    
    public DatabaseHealthIndicator(DataSource dataSource, 
                                  EntityManagerFactory entityManagerFactory) {
        this.dataSource = dataSource;
        this.entityManagerFactory = entityManagerFactory;
    }
    
    @Override
    public Health health() {
        try {
            Health.Builder builder = checkDatabaseConnectivity();
            builder = checkHibernateStatistics(builder);
            builder = checkConnectionPool(builder);
            
            return builder.build();
        } catch (Exception e) {
            return Health.down()
                .withDetail("error", e.getMessage())
                .build();
        }
    }
    
    private Health.Builder checkDatabaseConnectivity() throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            if (connection.isValid(1)) {
                return Health.up()
                    .withDetail("database", "Available")
                    .withDetail("validationQuery", "Connection is valid");
            } else {
                return Health.down().withDetail("database", "Connection is not valid");
            }
        }
    }
    
    private Health.Builder checkHibernateStatistics(Health.Builder builder) {
        try {
            Statistics stats = entityManagerFactory.unwrap(SessionFactory.class)
                                                  .getStatistics();
            
            return builder
                .withDetail("hibernate.sessionOpenCount", stats.getSessionOpenCount())
                .withDetail("hibernate.queryExecutionCount", stats.getQueryExecutionCount())
                .withDetail("hibernate.queryExecutionMaxTime", stats.getQueryExecutionMaxTime())
                .withDetail("hibernate.entityLoadCount", stats.getEntityLoadCount());
                
        } catch (Exception e) {
            return builder.withDetail("hibernate.error", e.getMessage());
        }
    }
    
    private Health.Builder checkConnectionPool(Health.Builder builder) {
        if (dataSource instanceof HikariDataSource) {
            HikariDataSource hikari = (HikariDataSource) dataSource;
            HikariPoolMXBean poolMXBean = hikari.getHikariPoolMXBean();
            
            return builder
                .withDetail("pool.totalConnections", poolMXBean.getTotalConnections())
                .withDetail("pool.activeConnections", poolMXBean.getActiveConnections())
                .withDetail("pool.idleConnections", poolMXBean.getIdleConnections())
                .withDetail("pool.threadsAwaitingConnection", 
                           poolMXBean.getThreadsAwaitingConnection())
                .withDetail("pool.maxPoolSize", hikari.getMaximumPoolSize())
                .withDetail("pool.minPoolSize", hikari.getMinimumIdle());
        }
        
        return builder;
    }
}
```

### Query Logging: SQL Statement Logging

#### Comprehensive Logging Configuration
```yaml
# application.yml
spring:
  jpa:
    show-sql: false  # Don't use this in production
    properties:
      hibernate:
        format_sql: true
        use_sql_comments: true
        type: trace
        stat: debug

logging:
  level:
    org.hibernate.SQL: DEBUG
    org.hibernate.type.descriptor.sql.BasicBinder: TRACE
    org.springframework.jdbc.core.JdbcTemplate: DEBUG
    org.springframework.jdbc.core.StatementCreatorUtils: TRACE
    com.zaxxer.hikari.HikariConfig: DEBUG
    com.zaxxer.hikari: TRACE
    
    # Custom package logging
    com.yourcompany.repository: DEBUG
    
  pattern:
    console: "%d{yyyy-MM-dd HH:mm:ss} - %msg%n"
    file: "%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{36} - %msg%n"
```

#### Custom Query Logger
```java
@Component
@Slf4j
public class QueryLogger {
    
    private final MeterRegistry meterRegistry;
    
    public QueryLogger(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }
    
    @EventListener
    public void handleQueryExecution(QueryExecutionEvent event) {
        long executionTime = event.getExecutionTime();
        String queryType = event.getQueryType();
        
        // Log slow queries
        if (executionTime > 1000) { // 1 second threshold
            log.warn("Slow query detected: Type={}, ExecutionTime={}ms, Query={}", 
                    queryType, executionTime, event.getQuery());
        } else {
            log.debug("Query executed: Type={}, ExecutionTime={}ms", 
                     queryType, executionTime);
        }
        
        // Record metrics
        Timer.builder("database.query.execution")
            .description("Database query execution time")
            .tag("type", queryType)
            .register(meterRegistry)
            .record(executionTime, TimeUnit.MILLISECONDS);
    }
}

// Aspect for automatic query logging
@Aspect
@Component
@Slf4j
public class RepositoryLoggingAspect {
    
    private final ApplicationEventPublisher eventPublisher;
    
    public RepositoryLoggingAspect(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }
    
    @Around("execution(* com.yourcompany.repository.*.*(..))")
    public Object logRepositoryMethods(ProceedingJoinPoint joinPoint) throws Throwable {
        long startTime = System.currentTimeMillis();
        String methodName = joinPoint.getSignature().getName();
        String className = joinPoint.getTarget().getClass().getSimpleName();
        
        try {
            Object result = joinPoint.proceed();
            
            long executionTime = System.currentTimeMillis() - startTime;
            
            log.debug("Repository method executed: {}.{} in {}ms", 
                     className, methodName, executionTime);
            
            // Publish event for metrics collection
            eventPublisher.publishEvent(
                new QueryExecutionEvent("repository_method", executionTime));
            
            return result;
            
        } catch (Exception e) {
            long executionTime = System.currentTimeMillis() - startTime;
            log.error("Repository method failed: {}.{} in {}ms, Error: {}", 
                     className, methodName, executionTime, e.getMessage());
            throw e;
        }
    }
}
```

### Performance Monitoring: Slow Query Detection

#### Advanced Performance Monitor
```java
@Component
@Slf4j
public class DatabasePerformanceMonitor {
    
    private final MeterRegistry meterRegistry;
    private final Map<String, LongAdder> queryCounters = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> slowQueryCounters = new ConcurrentHashMap<>();
    
    // Configurable thresholds
    @Value("${app.monitoring.slow-query-threshold:1000}")
    private long slowQueryThreshold;
    
    @Value("${app.monitoring.very-slow-query-threshold:5000}")
    private long verySlowQueryThreshold;
    
    public DatabasePerformanceMonitor(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        setupMetrics();
    }
    
    private void setupMetrics() {
        Gauge.builder("database.slow_queries.count")
            .description("Number of slow queries")
            .register(meterRegistry, this, 
                monitor -> slowQueryCounters.values()
                    .stream()
                    .mapToLong(AtomicLong::get)
                    .sum());
    }
    
    public void recordQueryExecution(String query, long executionTimeMs, String operation) {
        // Count all queries
        queryCounters.computeIfAbsent(operation, k -> new LongAdder()).increment();
        
        // Record execution time
        Timer.builder("database.query.duration")
            .description("Database query execution time")
            .tag("operation", operation)
            .tag("performance", getPerformanceCategory(executionTimeMs))
            .register(meterRegistry)
            .record(executionTimeMs, TimeUnit.MILLISECONDS);
        
        // Handle slow queries
        if (executionTimeMs > slowQueryThreshold) {
            handleSlowQuery(query, executionTimeMs, operation);
        }
        
        // Generate alerts for very slow queries
        if (executionTimeMs > verySlowQueryThreshold) {
            generateSlowQueryAlert(query, executionTimeMs, operation);
        }
    }
    
    private String getPerformanceCategory(long executionTimeMs) {
        if (executionTimeMs < 100) return "fast";
        if (executionTimeMs < 1000) return "normal";
        if (executionTimeMs < 5000) return "slow";
        return "very_slow";
    }
    
    private void handleSlowQuery(String query, long executionTime, String operation) {
        slowQueryCounters.computeIfAbsent(operation, k -> new AtomicLong()).incrementAndGet();
        
        log.warn("Slow query detected - Operation: {}, Duration: {}ms, Query: {}", 
                operation, executionTime, sanitizeQuery(query));
        
        // Store slow query information for analysis
        storeSlowQueryInfo(query, executionTime, operation);
    }
    
    private void generateSlowQueryAlert(String query, long executionTime, String operation) {
        log.error("ALERT: Very slow query - Operation: {}, Duration: {}ms, Query: {}", 
                 operation, executionTime, sanitizeQuery(query));
        
        // You can integrate with alerting systems here
        // e.g., send to Slack, email, or monitoring system
    }
    
    private String sanitizeQuery(String query) {
        // Remove or mask sensitive information from queries for logging
        return query.length() > 200 ? query.substring(0, 200) + "..." : query;
    }
    
    private void storeSlowQueryInfo(String query, long executionTime, String operation) {
        // Store in a separate table or send to monitoring system for analysis
        SlowQueryInfo info = SlowQueryInfo.builder()
            .query(query)
            .executionTime(executionTime)
            .operation(operation)
            .timestamp(LocalDateTime.now())
            .build();
        
        // Async storage to avoid impacting performance
        CompletableFuture.runAsync(() -> {
            // Save to database or send to monitoring system
        });
    }
    
    @Scheduled(fixedRate = 60000) // Every minute
    public void reportPerformanceMetrics() {
        long totalQueries = queryCounters.values().stream()
            .mapToLong(LongAdder::sum)
            .sum();
        
        long totalSlowQueries = slowQueryCounters.values().stream()
            .mapToLong(AtomicLong::get)
            .sum();
        
        if (totalQueries > 0) {
            double slowQueryPercentage = (double) totalSlowQueries / totalQueries * 100;
            
            log.info("Performance Summary - Total Queries: {}, Slow Queries: {} ({:.2f}%)", 
                    totalQueries, totalSlowQueries, slowQueryPercentage);
            
            // Alert if slow query percentage is too high
            if (slowQueryPercentage > 10) { // 10% threshold
                log.warn("High slow query percentage detected: {:.2f}%", slowQueryPercentage);
            }
        }
    }
}

@Data
@Builder
public class SlowQueryInfo {
    private String query;
    private long executionTime;
    private String operation;
    private LocalDateTime timestamp;
}
```

### Connection Pool Monitoring: HikariCP Metrics

#### HikariCP Advanced Configuration and Monitoring
```java
@Configuration
public class HikariCPConfiguration {
    
    @Bean
    @ConfigurationProperties("spring.datasource.hikari")
    public HikariConfig hikariConfig() {
        HikariConfig config = new HikariConfig();
        
        // Connection pool settings
        config.setMaximumPoolSize(20);
        config.setMinimumIdle(5);
        config.setIdleTimeout(300000); // 5 minutes
        config.setConnectionTimeout(30000); // 30 seconds
        config.setMaxLifetime(1800000); // 30 minutes
        config.setLeakDetectionThreshold(60000); // 1 minute
        
        // Performance settings
        config.setConnectionTestQuery("SELECT 1");
        config.setValidationTimeout(5000);
        config.setInitializationFailTimeout(1);
        
        // Monitoring settings
        config.setRegisterMbeans(true);
        config.setMetricRegistry(new MetricRegistry());
        
        return config;
    }
    
    @Bean
    @Primary
    public DataSource dataSource(HikariConfig hikariConfig) {
        return new HikariDataSource(hikariConfig);
    }
    
    @Bean
    public HikariPoolMonitor hikariPoolMonitor(DataSource dataSource, 
                                               MeterRegistry meterRegistry) {
        return new HikariPoolMonitor((HikariDataSource) dataSource, meterRegistry);
    }
}

@Component
@Slf4j
public class HikariPoolMonitor {
    
    private final HikariDataSource dataSource;
    private final MeterRegistry meterRegistry;
    private final HikariPoolMXBean poolMXBean;
    
    public HikariPoolMonitor(HikariDataSource dataSource, MeterRegistry meterRegistry) {
        this.dataSource = dataSource;
        this.meterRegistry = meterRegistry;
        this.poolMXBean = dataSource.getHikariPoolMXBean();
        
        registerMetrics();
    }
    
    private void registerMetrics() {
        // Active connections
        Gauge.builder("hikaricp.connections.active")
            .description("Active connections in the pool")
            .register(meterRegistry, poolMXBean, HikariPoolMXBean::getActiveConnections);
        
        // Idle connections
        Gauge.builder("hikaricp.connections.idle")
            .description("Idle connections in the pool")
            .register(meterRegistry, poolMXBean, HikariPoolMXBean::getIdleConnections);
        
        // Total connections
        Gauge.builder("hikaricp.connections.total")
            .description("Total connections in the pool")
            .register(meterRegistry, poolMXBean, HikariPoolMXBean::getTotalConnections);
        
        // Threads awaiting connection
        Gauge.builder("hikaricp.connections.pending")
            .description("Threads awaiting connections")
            .register(meterRegistry, poolMXBean, HikariPoolMXBean::getThreadsAwaitingConnection);
        
        // Maximum pool size
        Gauge.builder("hikaricp.connections.max")
            .description("Maximum pool size")
            .register(meterRegistry, this, monitor -> dataSource.getMaximumPoolSize());
        
        // Minimum idle
        Gauge.builder("hikaricp.connections.min")
            .description("Minimum idle connections")
            .register(meterRegistry, this, monitor -> dataSource.getMinimumIdle());
    }
    
    @Scheduled(fixedRate = 30000) // Every 30 seconds
    public void logPoolStatistics() {
        int activeConnections = poolMXBean.getActiveConnections();
        int idleConnections = poolMXBean.getIdleConnections();
        int totalConnections = poolMXBean.getTotalConnections();
        int threadsAwaiting = poolMXBean.getThreadsAwaitingConnection();
        
        log.info("HikariCP Pool Stats - Active: {}, Idle: {}, Total: {}, Pending: {}", 
                activeConnections, idleConnections, totalConnections, threadsAwaiting);
        
        // Alert on potential issues
        if (threadsAwaiting > 0) {
            log.warn("Connection pool bottleneck detected - {} threads waiting for connections", 
                    threadsAwaiting);
        }
        
        double utilizationPercent = (double) activeConnections / dataSource.getMaximumPoolSize() * 100;
        if (utilizationPercent > 80) {
            log.warn("High connection pool utilization: {:.1f}%", utilizationPercent);
        }
    }
    
    @EventListener(ApplicationReadyEvent.class)
    public void validatePoolConfiguration() {
        log.info("=== HikariCP Configuration ===");
        log.info("Maximum Pool Size: {}", dataSource.getMaximumPoolSize());
        log.info("Minimum Idle: {}", dataSource.getMinimumIdle());
        log.info("Connection Timeout: {}ms", dataSource.getConnectionTimeout());
        log.info("Idle Timeout: {}ms", dataSource.getIdleTimeout());
        log.info("Max Lifetime: {}ms", dataSource.getMaxLifetime());
        log.info("Leak Detection Threshold: {}ms", dataSource.getLeakDetectionThreshold());
        
        // Validate configuration
        if (dataSource.getMaximumPoolSize() < 10) {
            log.warn("Connection pool size might be too small for production: {}", 
                    dataSource.getMaximumPoolSize());
        }
        
        if (dataSource.getLeakDetectionThreshold() == 0) {
            log.warn("Connection leak detection is disabled - consider enabling for production");
        }
    }
}
```

## 11.2 Database Migration

### Flyway Integration: Version-controlled migrations

#### Flyway Configuration
```yaml
# application.yml
spring:
  flyway:
    enabled: true
    baseline-on-migrate: true
    locations: classpath:db/migration
    schemas: public
    table: flyway_schema_history
    sql-migration-prefix: V
    sql-migration-separator: __
    sql-migration-suffixes: .sql
    validate-on-migrate: true
    clean-disabled: true  # Important for production
    placeholder-replacement: true
    placeholders:
      database.name: ${spring.datasource.database-name:myapp}
    
  jpa:
    hibernate:
      ddl-auto: validate  # Important: Don't let Hibernate manage schema
```

#### Advanced Flyway Configuration
```java
@Configuration
@ConditionalOnProperty(name = "spring.flyway.enabled", havingValue = "true", matchIfMissing = true)
public class FlywayConfiguration {
    
    @Bean
    public FlywayConfigurationCustomizer flywayConfigurationCustomizer() {
        return configuration -> {
            // Custom migration locations based on profile
            configuration.locations("classpath:db/migration/common", 
                                  "classpath:db/migration/" + getActiveProfile());
            
            // Custom callbacks for migration events
            configuration.callbacks(new CustomFlywayCallback());
            
            // Migration validation
            configuration.validateOnMigrate(true);
            configuration.ignoreMissingMigrations(false);
            configuration.ignoreIgnoredMigrations(false);
            configuration.ignoreFutureMigrations(false);
        };
    }
    
    @Bean
    public FlywayMigrationStrategy flywayMigrationStrategy() {
        return new FlywayMigrationStrategy() {
            @Override
            public void migrate(Flyway flyway) {
                try {
                    // Validate current state
                    flyway.validate();
                    
                    // Perform migration
                    MigrateResult result = flyway.migrate();
                    
                    log.info("Flyway migration completed successfully. " +
                            "Migrations executed: {}, Target version: {}", 
                            result.migrationsExecuted, result.targetSchemaVersion);
                            
                } catch (FlywayException e) {
                    log.error("Flyway migration failed", e);
                    
                    // In production, you might want to handle this differently
                    // e.g., send alerts, perform rollback, etc.
                    handleMigrationFailure(e, flyway);
                    
                    throw e;
                }
            }
        };
    }
    
    private void handleMigrationFailure(FlywayException e, Flyway flyway) {
        // Log detailed information about the failure
        log.error("Migration failure details:");
        log.error("Error message: {}", e.getMessage());
        
        // Get migration info
        MigrationInfoService infoService = flyway.info();
        for (MigrationInfo info : infoService.all()) {
            if (info.getState() == MigrationState.FAILED) {
                log.error("Failed migration: {} - {}", 
                         info.getVersion(), info.getDescription());
            }
        }
        
        // Send alert (implement based on your alerting system)
        sendMigrationFailureAlert(e);
    }
    
    private void sendMigrationFailureAlert(FlywayException e) {
        // Implementation depends on your alerting system
        // Could be email, Slack, monitoring system, etc.
    }
    
    private String getActiveProfile() {
        // Return active profile for environment-specific migrations
        return System.getProperty("spring.profiles.active", "dev");
    }
}

// Custom Flyway callback for migration events
@Slf4j
public class CustomFlywayCallback implements Callback {
    
    @Override
    public boolean supports(Event event, Context context) {
        return true; // Support all events
    }
    
    @Override
    public boolean canHandleInTransaction(Event event, Context context) {
        return true;
    }
    
    @Override
    public void handle(Event event, Context context) {
        switch (event) {
            case BEFORE_MIGRATE:
                log.info("Starting database migration...");
                recordMigrationStart();
                break;
                
            case AFTER_MIGRATE:
                log.info("Database migration completed successfully");
                recordMigrationEnd(true);
                break;
                
            case AFTER_MIGRATE_ERROR:
                log.error("Database migration failed");
                recordMigrationEnd(false);
                break;
                
            case BEFORE_EACH_MIGRATE:
                MigrationInfo migrationInfo = context.getMigrationInfo();
                log.info("Executing migration: {} - {}", 
                        migrationInfo.getVersion(), 
                        migrationInfo.getDescription());
                break;
                
            case AFTER_EACH_MIGRATE:
                MigrationInfo completedMigration = context.getMigrationInfo();
                log.info("Completed migration: {} in {}ms", 
                        completedMigration.getVersion(),
                        completedMigration.getExecutionTime());
                break;
                
            case AFTER_EACH_MIGRATE_ERROR:
                log.error("Migration failed: {}", context.getMigrationInfo().getVersion());
                break;
        }
    }
    
    private void recordMigrationStart() {
        // Record migration start time and details
        // Could be stored in database, sent to monitoring system, etc.
    }
    
    private void recordMigrationEnd(boolean success) {
        // Record migration completion and success status
        // Calculate total migration time, etc.
    }
}
```

#### Migration File Examples

```sql
-- V1__Create_user_table.sql
CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    email VARCHAR(100) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    first_name VARCHAR(50),
    last_name VARCHAR(50),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    version INTEGER DEFAULT 0
);

CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_username ON users(username);

-- V2__Create_product_table.sql
CREATE TABLE products (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    price DECIMAL(10,2) NOT NULL,
    category_id BIGINT,
    created_by BIGINT REFERENCES users(id),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    version INTEGER DEFAULT 0
);

CREATE INDEX idx_products_category ON products(category_id);
CREATE INDEX idx_products_price ON products(price);

-- V3__Add_user_profile_fields.sql
ALTER TABLE users 
ADD COLUMN phone VARCHAR(20),
ADD COLUMN date_of_birth DATE,
ADD COLUMN status VARCHAR(20) DEFAULT 'ACTIVE';

-- Create enum type for status
CREATE TYPE user_status AS ENUM ('ACTIVE', 'INACTIVE', 'SUSPENDED');
ALTER TABLE users ALTER COLUMN status TYPE user_status USING status::user_status;
```

#### Zero-Downtime Migration Strategies

```java
@Service
@Slf4j
public class ZeroDowntimeMigrationService {
    
    private final Flyway flyway;
    private final DataSource dataSource;
    private final ApplicationEventPublisher eventPublisher;
}

### Row-Level Security: Database-level security

#### Entity-Level Security Configuration
```java
// User entity with row-level security
@Entity
@Table(name = "users")
@SQLRestriction("deleted = false AND (tenant_id = current_setting('app.current_tenant_id')::bigint OR current_setting('app.current_user_role') = 'ADMIN')")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "username", unique = true, nullable = false)
    private String username;
    
    @Column(name = "email", unique = true, nullable = false)
    private String email;
    
    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;
    
    @Column(name = "deleted", nullable = false)
    private Boolean deleted = false;
    
    @Column(name = "created_by")
    private Long createdBy;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "role")
    private UserRole role;
    
    // getters and setters...
}

// Multi-tenant entity with automatic filtering
@Entity
@Table(name = "documents")
@FilterDef(name = "tenantFilter", parameters = @ParamDef(name = "tenantId", type = "long"))
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
@FilterDef(name = "ownerFilter", parameters = @ParamDef(name = "userId", type = "long"))
@Filter(name = "ownerFilter", condition = "owner_id = :userId OR visibility = 'PUBLIC'")
public class Document {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "title", nullable = false)
    private String title;
    
    @Column(name = "content", columnDefinition = "TEXT")
    private String content;
    
    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;
    
    @Column(name = "owner_id", nullable = false)
    private Long ownerId;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "visibility")
    private DocumentVisibility visibility = DocumentVisibility.PRIVATE;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "classification")
    private SecurityClassification classification = SecurityClassification.INTERNAL;
    
    // getters and setters...
}

public enum DocumentVisibility {
    PRIVATE, TEAM, ORGANIZATION, PUBLIC
}

public enum SecurityClassification {
    PUBLIC, INTERNAL, CONFIDENTIAL, RESTRICTED
}
```

#### Row-Level Security Service
```java
@Service
@Slf4j
public class RowLevelSecurityService {
    
    @PersistenceContext
    private EntityManager entityManager;
    
    private final SecurityContextService securityContextService;
    
    public RowLevelSecurityService(SecurityContextService securityContextService) {
        this.securityContextService = securityContextService;
    }
    
    /**
     * Enables tenant-based filtering for the current session
     */
    @PostConstruct
    public void enableTenantFiltering() {
        Session session = entityManager.unwrap(Session.class);
        
        // Enable tenant filter
        Filter tenantFilter = session.enableFilter("tenantFilter");
        tenantFilter.setParameter("tenantId", securityContextService.getCurrentTenantId());
        
        // Enable owner-based filter if needed
        if (securityContextService.getCurrentUserRole() != UserRole.ADMIN) {
            Filter ownerFilter = session.enableFilter("ownerFilter");
            ownerFilter.setParameter("userId", securityContextService.getCurrentUserId());
        }
        
        log.debug("Row-level security filters enabled for tenant: {} and user: {}", 
                 securityContextService.getCurrentTenantId(),
                 securityContextService.getCurrentUserId());
    }
    
    /**
     * Sets database session variables for PostgreSQL RLS
     */
    public void setDatabaseSecurityContext() {
        try {
            Long tenantId = securityContextService.getCurrentTenantId();
            String userRole = securityContextService.getCurrentUserRole().name();
            Long userId = securityContextService.getCurrentUserId();
            
            // Set PostgreSQL session variables for RLS
            entityManager.createNativeQuery("SELECT set_config('app.current_tenant_id', ?1, true)")
                        .setParameter(1, tenantId.toString())
                        .executeUpdate();
            
            entityManager.createNativeQuery("SELECT set_config('app.current_user_role', ?1, true)")
                        .setParameter(1, userRole)
                        .executeUpdate();
            
            entityManager.createNativeQuery("SELECT set_config('app.current_user_id', ?1, true)")
                        .setParameter(1, userId.toString())
                        .executeUpdate();
            
            log.debug("Database security context set - Tenant: {}, Role: {}, User: {}", 
                     tenantId, userRole, userId);
                     
        } catch (Exception e) {
            log.error("Failed to set database security context", e);
            throw new SecurityException("Could not establish security context", e);
        }
    }
    
    /**
     * Validates user access to specific entity
     */
    public <T> boolean hasAccessToEntity(T entity, AccessType accessType) {
        if (entity == null) return false;
        
        // Admin users have access to everything
        if (securityContextService.getCurrentUserRole() == UserRole.ADMIN) {
            return true;
        }
        
        // Check tenant-based access
        if (entity instanceof TenantAware) {
            TenantAware tenantEntity = (TenantAware) entity;
            if (!Objects.equals(tenantEntity.getTenantId(), securityContextService.getCurrentTenantId())) {
                log.warn("Access denied - Entity tenant {} does not match user tenant {}", 
                        tenantEntity.getTenantId(), securityContextService.getCurrentTenantId());
                return false;
            }
        }
        
        // Check ownership-based access
        if (entity instanceof OwnedEntity) {
            OwnedEntity ownedEntity = (OwnedEntity) entity;
            return hasOwnershipAccess(ownedEntity, accessType);
        }
        
        // Check role-based access
        return hasRoleBasedAccess(entity, accessType);
    }
    
    private boolean hasOwnershipAccess(OwnedEntity entity, AccessType accessType) {
        Long currentUserId = securityContextService.getCurrentUserId();
        
        // Owner has full access
        if (Objects.equals(entity.getOwnerId(), currentUserId)) {
            return true;
        }
        
        // Check if entity allows team or organization access
        if (entity instanceof Document) {
            Document doc = (Document) entity;
            switch (doc.getVisibility()) {
                case PUBLIC:
                    return true;
                case ORGANIZATION:
                    return accessType == AccessType.READ;
                case TEAM:
                    return isTeamMember(entity.getOwnerId(), currentUserId) && 
                           (accessType == AccessType.READ || accessType == AccessType.UPDATE);
                case PRIVATE:
                default:
                    return false;
            }
        }
        
        return false;
    }
    
    private boolean hasRoleBasedAccess(Object entity, AccessType accessType) {
        UserRole currentRole = securityContextService.getCurrentUserRole();
        
        // Check security classification for documents
        if (entity instanceof Document) {
            Document doc = (Document) entity;
            return hasClassificationAccess(doc.getClassification(), currentRole, accessType);
        }
        
        // Default role-based access
        switch (currentRole) {
            case ADMIN:
                return true;
            case MANAGER:
                return accessType != AccessType.DELETE;
            case USER:
                return accessType == AccessType.READ;
            default:
                return false;
        }
    }
    
    private boolean hasClassificationAccess(SecurityClassification classification, 
                                          UserRole userRole, AccessType accessType) {
        switch (classification) {
            case PUBLIC:
                return true;
            case INTERNAL:
                return userRole != UserRole.GUEST;
            case CONFIDENTIAL:
                return userRole == UserRole.ADMIN || userRole == UserRole.MANAGER;
            case RESTRICTED:
                return userRole == UserRole.ADMIN && accessType == AccessType.READ;
            default:
                return false;
        }
    }
    
    private boolean isTeamMember(Long ownerId, Long userId) {
        // Implementation to check if users are in the same team
        // This could query team membership tables
        return false; // Simplified for example
    }
}

public enum AccessType {
    READ, CREATE, UPDATE, DELETE
}

// Interfaces for marking entities
public interface TenantAware {
    Long getTenantId();
}

public interface OwnedEntity extends TenantAware {
    Long getOwnerId();
}
```

#### Security Context Service
```java
@Service
@Slf4j
public class SecurityContextService {
    
    private static final String TENANT_ID_HEADER = "X-Tenant-ID";
    private static final String USER_ID_HEADER = "X-User-ID";
    
    /**
     * Gets current tenant ID from security context
     */
    public Long getCurrentTenantId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserPrincipal) {
            UserPrincipal principal = (UserPrincipal) auth.getPrincipal();
            return principal.getTenantId();
        }
        
        // Fallback to request header
        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        if (attrs instanceof ServletRequestAttributes) {
            HttpServletRequest request = ((ServletRequestAttributes) attrs).getRequest();
            String tenantId = request.getHeader(TENANT_ID_HEADER);
            if (tenantId != null) {
                try {
                    return Long.parseLong(tenantId);
                } catch (NumberFormatException e) {
                    log.warn("Invalid tenant ID in header: {}", tenantId);
                }
            }
        }
        
        throw new SecurityException("No tenant context available");
    }
    
    /**
     * Gets current user ID from security context
     */
    public Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserPrincipal) {
            UserPrincipal principal = (UserPrincipal) auth.getPrincipal();
            return principal.getUserId();
        }
        
        throw new SecurityException("No user context available");
    }
    
    /**
     * Gets current user role from security context
     */
    public UserRole getCurrentUserRole() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserPrincipal) {
            UserPrincipal principal = (UserPrincipal) auth.getPrincipal();
            return principal.getRole();
        }
        
        return UserRole.GUEST;
    }
    
    /**
     * Checks if current user has specific authority
     */
    public boolean hasAuthority(String authority) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null) {
            return auth.getAuthorities().stream()
                      .anyMatch(a -> a.getAuthority().equals(authority));
        }
        return false;
    }
    
    /**
     * Executes code in a specific security context
     */
    public <T> T executeInContext(Long tenantId, Long userId, UserRole role, Supplier<T> action) {
        SecurityContext originalContext = SecurityContextHolder.getContext();
        
        try {
            // Create temporary context
            SecurityContext tempContext = SecurityContextHolder.createEmptyContext();
            UserPrincipal tempPrincipal = new UserPrincipal(userId, "temp", role, tenantId);
            Authentication tempAuth = new UsernamePasswordAuthenticationToken(
                tempPrincipal, null, tempPrincipal.getAuthorities());
            tempContext.setAuthentication(tempAuth);
            
            SecurityContextHolder.setContext(tempContext);
            
            return action.get();
            
        } finally {
            SecurityContextHolder.setContext(originalContext);
        }
    }
}

// Custom UserPrincipal class
public class UserPrincipal implements UserDetails {
    private final Long userId;
    private final String username;
    private final UserRole role;
    private final Long tenantId;
    private final List<GrantedAuthority> authorities;
    
    public UserPrincipal(Long userId, String username, UserRole role, Long tenantId) {
        this.userId = userId;
        this.username = username;
        this.role = role;
        this.tenantId = tenantId;
        this.authorities = createAuthorities(role);
    }
    
    private List<GrantedAuthority> createAuthorities(UserRole role) {
        List<GrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_" + role.name()));
        
        // Add specific permissions based on role
        switch (role) {
            case ADMIN:
                authorities.add(new SimpleGrantedAuthority("PERMISSION_ALL"));
                break;
            case MANAGER:
                authorities.add(new SimpleGrantedAuthority("PERMISSION_READ"));
                authorities.add(new SimpleGrantedAuthority("PERMISSION_WRITE"));
                break;
            case USER:
                authorities.add(new SimpleGrantedAuthority("PERMISSION_READ"));
                break;
        }
        
        return authorities;
    }
    
    // UserDetails implementation
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }
    
    @Override
    public String getPassword() {
        return null; // Not used in this context
    }
    
    @Override
    public String getUsername() {
        return username;
    }
    
    @Override
    public boolean isAccountNonExpired() {
        return true;
    }
    
    @Override
    public boolean isAccountNonLocked() {
        return true;
    }
    
    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }
    
    @Override
    public boolean isEnabled() {
        return true;
    }
    
    // Custom getters
    public Long getUserId() { return userId; }
    public UserRole getRole() { return role; }
    public Long getTenantId() { return tenantId; }
}

public enum UserRole {
    GUEST, USER, MANAGER, ADMIN
}
```

### Audit Logging: Security event tracking

#### Comprehensive Audit System
```java
@Entity
@Table(name = "audit_events")
public class AuditEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "event_type", nullable = false)
    @Enumerated(EnumType.STRING)
    private AuditEventType eventType;
    
    @Column(name = "entity_type", nullable = false)
    private String entityType;
    
    @Column(name = "entity_id")
    private String entityId;
    
    @Column(name = "user_id")
    private Long userId;
    
    @Column(name = "tenant_id")
    private Long tenantId;
    
    @Column(name = "ip_address")
    private String ipAddress;
    
    @Column(name = "user_agent")
    private String userAgent;
    
    @Column(name = "session_id")
    private String sessionId;
    
    @Column(name = "timestamp", nullable = false)
    private LocalDateTime timestamp;
    
    @Column(name = "details", columnDefinition = "JSONB")
    private String details;
    
    @Column(name = "old_values", columnDefinition = "JSONB")
    private String oldValues;
    
    @Column(name = "new_values", columnDefinition = "JSONB")
    private String newValues;
    
    @Column(name = "success", nullable = false)
    private Boolean success;
    
    @Column(name = "error_message")
    private String errorMessage;
    
    @Column(name = "risk_score")
    private Integer riskScore;
    
    // getters and setters...
}

public enum AuditEventType {
    // Authentication events
    LOGIN_SUCCESS, LOGIN_FAILURE, LOGOUT, PASSWORD_CHANGE,
    
    // Authorization events  
    ACCESS_GRANTED, ACCESS_DENIED, PRIVILEGE_ESCALATION,
    
    // Data events
    CREATE, READ, UPDATE, DELETE, BULK_OPERATION,
    
    // Security events
    SECURITY_VIOLATION, SUSPICIOUS_ACTIVITY, DATA_EXPORT,
    
    // System events
    SYSTEM_START, SYSTEM_STOP, CONFIGURATION_CHANGE
}
```

#### Audit Service Implementation
```java
@Service
@Async
@Slf4j
public class AuditService {
    
    private final AuditEventRepository auditEventRepository;
    private final SecurityContextService securityContextService;
    private final ObjectMapper objectMapper;
    private final RiskAssessmentService riskAssessmentService;
    
    public AuditService(AuditEventRepository auditEventRepository,
                       SecurityContextService securityContextService,
                       ObjectMapper objectMapper,
                       RiskAssessmentService riskAssessmentService) {
        this.auditEventRepository = auditEventRepository;
        this.securityContextService = securityContextService;
        this.objectMapper = objectMapper;
        this.riskAssessmentService = riskAssessmentService;
    }
    
    /**
     * Records an audit event
     */
    public void recordEvent(AuditEventType eventType, String entityType, 
                           String entityId, Object details) {
        recordEvent(eventType, entityType, entityId, details, null, null, true, null);
    }
    
    /**
     * Records an audit event with old and new values
     */
    public void recordDataChange(AuditEventType eventType, String entityType, 
                                String entityId, Object oldValues, Object newValues) {
        recordEvent(eventType, entityType, entityId, null, oldValues, newValues, true, null);
    }
    
    /**
     * Records a failed audit event
     */
    public void recordFailure(AuditEventType eventType, String entityType, 
                             String entityId, String errorMessage) {
        recordEvent(eventType, entityType, entityId, null, null, null, false, errorMessage);
    }
    
    private void recordEvent(AuditEventType eventType, String entityType, String entityId,
                           Object details, Object oldValues, Object newValues,
                           boolean success, String errorMessage) {
        try {
            AuditEvent event = new AuditEvent();
            event.setEventType(eventType);
            event.setEntityType(entityType);
            event.setEntityId(entityId);
            event.setTimestamp(LocalDateTime.now());
            event.setSuccess(success);
            event.setErrorMessage(errorMessage);
            
            // Set security context information
            setSecurityContext(event);
            
            // Set request context information
            setRequestContext(event);
            
            // Serialize details and values
            if (details != null) {
                event.setDetails(objectMapper.writeValueAsString(details));
            }
            if (oldValues != null) {
                event.setOldValues(objectMapper.writeValueAsString(oldValues));
            }
            if (newValues != null) {
                event.setNewValues(objectMapper.writeValueAsString(newValues));
            }
            
            // Calculate risk score
            event.setRiskScore(riskAssessmentService.calculateRiskScore(event));
            
            // Save audit event
            auditEventRepository.save(event);
            
            // Check for high-risk events
            if (event.getRiskScore() != null && event.getRiskScore() > 80) {
                handleHighRiskEvent(event);
            }
            
            log.debug("Audit event recorded: {} for {}/{}", eventType, entityType, entityId);
            
        } catch (Exception e) {
            log.error("Failed to record audit event", e);
            // Don't let audit failures break the main operation
        }
    }
    
    private void setSecurityContext(AuditEvent event) {
        try {
            event.setUserId(securityContextService.getCurrentUserId());
            event.setTenantId(securityContextService.getCurrentTenantId());
        } catch (Exception e) {
            log.debug("No security context available for audit event");
        }
    }
    
    private void setRequestContext(AuditEvent event) {
        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        if (attrs instanceof ServletRequestAttributes) {
            HttpServletRequest request = ((ServletRequestAttributes) attrs).getRequest();
            
            event.setIpAddress(getClientIpAddress(request));
            event.setUserAgent(request.getHeader("User-Agent"));
            event.setSessionId(request.getRequestedSessionId());
        }
    }
    
    private String getClientIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }
        
        return request.getRemoteAddr();
    }
    
    private void handleHighRiskEvent(AuditEvent event) {
        log.warn("High-risk audit event detected: {} - Risk Score: {}", 
                event.getEventType(), event.getRiskScore());
        
        // Send alert to security team
        sendSecurityAlert(event);
        
        // Consider additional actions like temporarily blocking user, etc.
        if (event.getRiskScore() > 95) {
            handleCriticalRiskEvent(event);
        }
    }
    
    private void sendSecurityAlert(AuditEvent event) {
        // Implementation depends on your alerting system
        // Could send email, Slack message, or trigger monitoring alert
    }
    
    private void handleCriticalRiskEvent(AuditEvent event) {
        // Implementation for critical risk events
        // Might include automatic user suspension, IP blocking, etc.
    }
}
```

#### Risk Assessment Service
```java
@Service
@Slf4j
public class RiskAssessmentService {
    
    private final Map<AuditEventType, Integer> baseRiskScores = Map.of(
        AuditEventType.LOGIN_FAILURE, 20,
        AuditEventType.ACCESS_DENIED, 30,
        AuditEventType.PRIVILEGE_ESCALATION, 80,
        AuditEventType.SECURITY_VIOLATION, 90,
        AuditEventType.DATA_EXPORT, 40,
        AuditEventType.BULK_OPERATION, 30,
        AuditEventType.DELETE, 50
    );
    
    public Integer calculateRiskScore(AuditEvent event) {
        int riskScore = baseRiskScores.getOrDefault(event.getEventType(), 10);
        
        // Adjust based on context
        riskScore += assessTimeContext(event);
        riskScore += assessLocationContext(event);
        riskScore += assessVolumeContext(event);
        riskScore += assessUserBehavior(event);
        
        // Cap at 100
        return Math.min(riskScore, 100);
    }
    
    private int assessTimeContext(AuditEvent event) {
        LocalTime time = event.getTimestamp().toLocalTime();
        
        // Higher risk for events outside business hours
        if (time.isBefore(LocalTime.of(6, 0)) || time.isAfter(LocalTime.of(22, 0))) {
            return 15;
        }
        
        // Weekend events are slightly higher risk
        DayOfWeek dayOfWeek = event.getTimestamp().getDayOfWeek();
        if (dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY) {
            return 10;
        }
        
        return 0;
    }
    
    private int assessLocationContext(AuditEvent event) {
        // Check if IP is from known location
        // This is simplified - in reality you'd use IP geolocation services
        String ipAddress = event.getIpAddress();
        
        if (ipAddress != null) {
            // Check against whitelist of known good IPs
            if (isKnownGoodIp(ipAddress)) {
                return -5; // Reduce risk
            }
            
            // Check if IP is from suspicious location/range
            if (isSuspiciousIp(ipAddress)) {
                return 25;
            }
        }
        
        return 0;
    }
    
    private int assessVolumeContext(AuditEvent event) {
        // Check for high volume of similar events from same user
        Long userId = event.getUserId();
        if (userId != null) {
            long recentSimilarEvents = countRecentSimilarEvents(event, Duration.ofMinutes(10));
            
            if (recentSimilarEvents > 10) {
                return 20;
            } else if (recentSimilarEvents > 5) {
                return 10;
            }
        }
        
        return 0;
    }
    
    private int assessUserBehavior(AuditEvent event) {
        // Assess based on user's typical behavior patterns
        Long userId = event.getUserId();
        if (userId != null) {
            // Check if this is unusual behavior for this user
            if (isUnusualBehavior(event)) {
                return 15;
            }
            
            // Check user's recent security incidents
            if (hasRecentSecurityIncidents(userId)) {
                return 10;
            }
        }
        
        return 0;
    }
    
    // Helper methods (simplified implementations)
    private boolean isKnownGoodIp(String ipAddress) {
        // Check against whitelist
        return false;
    }
    
    private boolean isSuspiciousIp(String ipAddress) {
        // Check against blacklist or suspicious patterns
        return false;
    }
    
    private long countRecentSimilarEvents(AuditEvent event, Duration timeWindow) {
        // Count similar events in time window
        return 0;
    }
    
    private boolean isUnusualBehavior(AuditEvent event) {
        // Compare with user's typical patterns
        return false;
    }
    
    private boolean hasRecentSecurityIncidents(Long userId) {
        // Check for recent security events for user
        return false;
    }
}
```

### Encryption: Database field encryption

#### Field-Level Encryption Configuration
```java
// Custom converter for encrypted fields
@Converter
public class EncryptedStringConverter implements AttributeConverter<String, String> {
    
    private final EncryptionService encryptionService;
    
    public EncryptedStringConverter() {
        this.encryptionService = ApplicationContextProvider.getBean(EncryptionService.class);
    }
    
    @Override
    public String convertToDatabaseColumn(String attribute) {
        if (attribute == null || attribute.isEmpty()) {
            return attribute;
        }
        
        try {
            return encryptionService.encrypt(attribute);
        } catch (Exception e) {
            throw new RuntimeException("Failed to encrypt attribute", e);
        }
    }
    
    @Override
    public String convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isEmpty()) {
            return dbData;
        }
        
        try {
            return encryptionService.decrypt(dbData);
        } catch (Exception e) {
            throw new RuntimeException("Failed to decrypt attribute", e);
        }
    }
}

// Entity with encrypted fields
@Entity
@Table(name = "sensitive_data")
public class SensitiveData {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "name")
    private String name;
    
    @Column(name = "ssn", length = 500) // Encrypted data might be longer
    @Convert(converter = EncryptedStringConverter.class)
    private String socialSecurityNumber;
    
    @Column(name = "credit_card", length = 500)
    @Convert(converter = EncryptedStringConverter.class)
    private String creditCardNumber;
    
    @Column(name = "email", length = 500)
    @Convert(converter = EncryptedStringConverter.class)
    private String email;
    
    @Column(name = "phone", length = 500)
    @Convert(converter = EncryptedStringConverter.class)
    private String phoneNumber;
    
    // Non-encrypted metadata
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    // getters and setters...
}
```

#### Advanced Encryption Service
```java
@Service
@Slf4j
public class EncryptionService {
    
    private final String algorithm = "AES/GCM/NoPadding";
    private final int gcmIvLength = 12;
    private final int gcmTagLength = 16;
    
    @Value("${app.encryption.master-key}")
    private String masterKeyBase64;
    
    private SecretKey masterKey;
    
    @PostConstruct
    public void init() {
        if (masterKeyBase64 == null || masterKeyBase64.isEmpty()) {
            throw new IllegalStateException("Master encryption key must be configured");
        }
        
        try {
            byte[] decodedKey = Base64.getDecoder().decode(masterKeyBase64);
            this.masterKey = new SecretKeySpec(decodedKey, "AES");
            
            log.info("Encryption service initialized successfully");
        } catch (Exception e) {
            throw new IllegalStateException("Failed to initialize encryption service", e);
        }
    }
    
    /**
     * Encrypts a string value
     */
    public String encrypt(String plaintext) throws Exception {
        if (plaintext == null || plaintext.isEmpty()) {
            return plaintext;
        }
        
        // Generate random IV
        byte[] iv = generateRandomIV();
        
        // Initialize cipher
        Cipher cipher = Cipher.getInstance(algorithm);
        GCMParameterSpec gcmSpec = new GCMParameterSpec(gcmTagLength * 8, iv);
        cipher.init(Cipher.DECRYPT_MODE, masterKey, gcmSpec);
        
        // Decrypt
        byte[] decryptedData = cipher.doFinal(encryptedData);
        
        return new String(decryptedData, StandardCharsets.UTF_8);
    }
    
    /**
     * Encrypts sensitive data for search purposes (deterministic encryption)
     */
    public String encryptForSearch(String plaintext) throws Exception {
        if (plaintext == null || plaintext.isEmpty()) {
            return plaintext;
        }
        
        // Use HMAC for deterministic "encryption" suitable for equality searches
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(masterKey);
        byte[] hash = mac.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
        
        return Base64.getEncoder().encodeToString(hash);
    }
    
    /**
     * Generates a random IV for encryption
     */
    private byte[] generateRandomIV() {
        byte[] iv = new byte[gcmIvLength];
        SecureRandom.getInstanceStrong().nextBytes(iv);
        return iv;
    }
    
    /**
     * Validates encryption configuration
     */
    @EventListener(ApplicationReadyEvent.class)
    public void validateConfiguration() {
        try {
            // Test encryption/decryption
            String testData = "test-encryption-data";
            String encrypted = encrypt(testData);
            String decrypted = decrypt(encrypted);
            
            if (!testData.equals(decrypted)) {
                throw new IllegalStateException("Encryption validation failed");
            }
            
            log.info("Encryption configuration validated successfully");
            
        } catch (Exception e) {
            log.error("Encryption validation failed", e);
            throw new IllegalStateException("Invalid encryption configuration", e);
        }
    }
}
```

#### Searchable Encrypted Fields
```java
@Entity
@Table(name = "users_encrypted")
public class EncryptedUser {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "username")
    private String username;
    
    // Encrypted email with search capability
    @Column(name = "email_encrypted", length = 500)
    @Convert(converter = EncryptedStringConverter.class)
    private String email;
    
    // Hash for searching encrypted email
    @Column(name = "email_hash")
    @Convert(converter = SearchHashConverter.class)
    private String emailHash;
    
    // Phone with encryption
    @Column(name = "phone_encrypted", length = 500)
    @Convert(converter = EncryptedStringConverter.class)
    private String phoneNumber;
    
    @Column(name = "phone_hash")
    @Convert(converter = SearchHashConverter.class)
    private String phoneHash;
    
    // Custom setters to automatically set hash values
    public void setEmail(String email) {
        this.email = email;
        this.emailHash = email; // Converter will handle hashing
    }
    
    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
        this.phoneHash = phoneNumber; // Converter will handle hashing
    }
    
    // getters and setters...
}

@Converter
public class SearchHashConverter implements AttributeConverter<String, String> {
    
    private final EncryptionService encryptionService;
    
    public SearchHashConverter() {
        this.encryptionService = ApplicationContextProvider.getBean(EncryptionService.class);
    }
    
    @Override
    public String convertToDatabaseColumn(String attribute) {
        if (attribute == null || attribute.isEmpty()) {
            return attribute;
        }
        
        try {
            return encryptionService.encryptForSearch(attribute);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create search hash", e);
        }
    }
    
    @Override
    public String convertToEntityAttribute(String dbData) {
        // Hash values are not meant to be converted back
        return dbData;
    }
}
```

#### Repository with Encrypted Field Search
```java
@Repository
public class EncryptedUserRepository extends JpaRepository<EncryptedUser, Long> {
    
    @PersistenceContext
    private EntityManager entityManager;
    
    private final EncryptionService encryptionService;
    
    public EncryptedUserRepository(EncryptionService encryptionService) {
        this.encryptionService = encryptionService;
    }
    
    /**
     * Finds user by encrypted email using hash comparison
     */
    public Optional<EncryptedUser> findByEmail(String email) {
        try {
            String emailHash = encryptionService.encryptForSearch(email);
            
            return entityManager.createQuery(
                "SELECT u FROM EncryptedUser u WHERE u.emailHash = :emailHash", 
                EncryptedUser.class)
                .setParameter("emailHash", emailHash)
                .getResultList()
                .stream()
                .findFirst();
                
        } catch (Exception e) {
            log.error("Error searching by encrypted email", e);
            return Optional.empty();
        }
    }
    
    /**
     * Finds users by encrypted phone using hash comparison
     */
    public List<EncryptedUser> findByPhoneNumber(String phoneNumber) {
        try {
            String phoneHash = encryptionService.encryptForSearch(phoneNumber);
            
            return entityManager.createQuery(
                "SELECT u FROM EncryptedUser u WHERE u.phoneHash = :phoneHash", 
                EncryptedUser.class)
                .setParameter("phoneHash", phoneHash)
                .getResultList();
                
        } catch (Exception e) {
            log.error("Error searching by encrypted phone", e);
            return Collections.emptyList();
        }
    }
    
    /**
     * Finds users with partial email match (requires decryption)
     * Note: This is expensive and should be used sparingly
     */
    public List<EncryptedUser> findByEmailContaining(String emailPart) {
        // Get all users and filter in memory (not efficient for large datasets)
        return findAll().stream()
            .filter(user -> {
                try {
                    String decryptedEmail = user.getEmail();
                    return decryptedEmail != null && 
                           decryptedEmail.toLowerCase().contains(emailPart.toLowerCase());
                } catch (Exception e) {
                    log.warn("Failed to decrypt email for user {}", user.getId());
                    return false;
                }
            })
            .collect(Collectors.toList());
    }
}
```

#### Complete Production Example: Secure Document Management
```java
@Entity
@Table(name = "secure_documents")
@EntityListeners({AuditingEntityListener.class, SecurityAuditListener.class})
public class SecureDocument implements TenantAware, OwnedEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "title")
    private String title;
    
    // Encrypted content
    @Column(name = "content_encrypted", columnDefinition = "TEXT")
    @Convert(converter = EncryptedStringConverter.class)
    private String content;
    
    // Encrypted metadata
    @Column(name = "keywords_encrypted", length = 1000)
    @Convert(converter = EncryptedStringConverter.class)
    private String keywords;
    
    // Search hashes for keywords
    @Column(name = "keywords_hash")
    @Convert(converter = SearchHashConverter.class)
    private String keywordsHash;
    
    // Security classification
    @Enumerated(EnumType.STRING)
    @Column(name = "classification")
    private SecurityClassification classification;
    
    // Tenant and owner information
    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;
    
    @Column(name = "owner_id", nullable = false)
    private Long ownerId;
    
    // Audit fields
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @CreatedBy
    @Column(name = "created_by", updatable = false)
    private Long createdBy;
    
    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    @LastModifiedBy
    @Column(name = "updated_by")
    private Long updatedBy;
    
    @Version
    private Integer version;
    
    // Custom setters for encrypted searchable fields
    public void setKeywords(String keywords) {
        this.keywords = keywords;
        this.keywordsHash = keywords; // Converter handles hashing
    }
    
    // getters and setters...
    
    @Override
    public Long getTenantId() {
        return tenantId;
    }
    
    @Override
    public Long getOwnerId() {
        return ownerId;
    }
}

// Security audit listener
@Component
public class SecurityAuditListener {
    
    private final AuditService auditService;
    
    public SecurityAuditListener(AuditService auditService) {
        this.auditService = auditService;
    }
    
    @PrePersist
    public void prePersist(Object entity) {
        if (entity instanceof SecureDocument) {
            SecureDocument doc = (SecureDocument) entity;
            auditService.recordEvent(AuditEventType.CREATE, "SecureDocument", 
                                   doc.getId() != null ? doc.getId().toString() : "new",
                                   Map.of("classification", doc.getClassification()));
        }
    }
    
    @PreUpdate
    public void preUpdate(Object entity) {
        if (entity instanceof SecureDocument) {
            SecureDocument doc = (SecureDocument) entity;
            auditService.recordEvent(AuditEventType.UPDATE, "SecureDocument", 
                                   doc.getId().toString(),
                                   Map.of("classification", doc.getClassification()));
        }
    }
    
    @PreRemove
    public void preRemove(Object entity) {
        if (entity instanceof SecureDocument) {
            SecureDocument doc = (SecureDocument) entity;
            auditService.recordEvent(AuditEventType.DELETE, "SecureDocument", 
                                   doc.getId().toString(),
                                   Map.of("classification", doc.getClassification()));
        }
    }
}

// Secure document service
@Service
@Transactional
@Slf4j
public class SecureDocumentService {
    
    private final SecureDocumentRepository repository;
    private final RowLevelSecurityService securityService;
    private final AuditService auditService;
    
    public SecureDocumentService(SecureDocumentRepository repository,
                                RowLevelSecurityService securityService,
                                AuditService auditService) {
        this.repository = repository;
        this.securityService = securityService;
        this.auditService = auditService;
    }
    
    @PreAuthorize("hasPermission(#document, 'CREATE')")
    public SecureDocument createDocument(SecureDocument document) {
        // Validate security classification access
        if (!securityService.hasAccessToEntity(document, AccessType.CREATE)) {
            auditService.recordFailure(AuditEventType.ACCESS_DENIED, "SecureDocument", 
                                      "new", "Insufficient privileges for classification: " + 
                                      document.getClassification());
            throw new AccessDeniedException("Insufficient privileges to create document with classification: " + 
                                          document.getClassification());
        }
        
        SecureDocument saved = repository.save(document);
        
        auditService.recordEvent(AuditEventType.CREATE, "SecureDocument", 
                               saved.getId().toString(),
                               Map.of("title", document.getTitle(),
                                     "classification", document.getClassification()));
        
        return saved;
    }
    
    @PreAuthorize("hasPermission(#id, 'SecureDocument', 'READ')")
    public Optional<SecureDocument> getDocument(Long id) {
        Optional<SecureDocument> document = repository.findById(id);
        
        if (document.isPresent()) {
            if (!securityService.hasAccessToEntity(document.get(), AccessType.READ)) {
                auditService.recordFailure(AuditEventType.ACCESS_DENIED, "SecureDocument", 
                                          id.toString(), "Insufficient read privileges");
                return Optional.empty();
            }
            
            auditService.recordEvent(AuditEventType.READ, "SecureDocument", 
                                   id.toString(), null);
        }
        
        return document;
    }
    
    @PreAuthorize("hasPermission(#document, 'UPDATE')")
    public SecureDocument updateDocument(SecureDocument document) {
        Optional<SecureDocument> existing = repository.findById(document.getId());
        
        if (existing.isEmpty()) {
            throw new EntityNotFoundException("Document not found: " + document.getId());
        }
        
        if (!securityService.hasAccessToEntity(document, AccessType.UPDATE)) {
            auditService.recordFailure(AuditEventType.ACCESS_DENIED, "SecureDocument", 
                                      document.getId().toString(), "Insufficient update privileges");
            throw new AccessDeniedException("Insufficient privileges to update document");
        }
        
        SecureDocument oldDoc = existing.get();
        SecureDocument updated = repository.save(document);
        
        auditService.recordDataChange(AuditEventType.UPDATE, "SecureDocument", 
                                    updated.getId().toString(), oldDoc, updated);
        
        return updated;
    }
    
    @PreAuthorize("hasPermission(#id, 'SecureDocument', 'DELETE')")
    public void deleteDocument(Long id) {
        Optional<SecureDocument> document = repository.findById(id);
        
        if (document.isEmpty()) {
            throw new EntityNotFoundException("Document not found: " + id);
        }
        
        if (!securityService.hasAccessToEntity(document.get(), AccessType.DELETE)) {
            auditService.recordFailure(AuditEventType.ACCESS_DENIED, "SecureDocument", 
                                      id.toString(), "Insufficient delete privileges");
            throw new AccessDeniedException("Insufficient privileges to delete document");
        }
        
        repository.deleteById(id);
        
        auditService.recordEvent(AuditEventType.DELETE, "SecureDocument", 
                               id.toString(), document.get());
    }
    
    public List<SecureDocument> searchByKeywords(String keywords) {
        // Use hash-based search for encrypted keywords
        return repository.findByKeywordsHash(keywords);
    }
}
```

## Summary

Phase 11 provides comprehensive production considerations covering:

### 11.1 Monitoring and Metrics
- **Hibernate Statistics**: Detailed JPA operation tracking
- **Spring Boot Actuator**: Health checks and database monitoring
- **Query Logging**: SQL statement tracking with performance analysis
- **Custom Metrics**: Connection pool monitoring and performance alerts

### 11.2 Database Migration
- **Flyway Integration**: Version-controlled SQL migrations with callbacks
- **Liquibase Integration**: XML/YAML migrations with advanced features
- **Zero-Downtime Strategies**: Blue-green deployments and shadow tables
- **Schema Validation**: Automated validation and rollback capabilities

### 11.3 Security Considerations
- **SQL Injection Prevention**: Parameterized queries and input validation
- **Row-Level Security**: Multi-tenant filtering and access control
- **Audit Logging**: Comprehensive security event tracking with risk assessment
- **Field Encryption**: Transparent encryption with searchable capabilities
- **Secure Configuration**: Credential management and SSL enforcement

Each component includes:
- ✅ Production-ready implementations
- ✅ Error handling and logging
- ✅ Performance optimizations
- ✅ Security best practices
- ✅ Monitoring and alerting
- ✅ Real-world examples with complete code

This completes your comprehensive Spring Data JPA mastery journey! You now have enterprise-grade knowledge covering all aspects from basic CRUD operations to advanced production security and monitoring. gcmSpec = new GCMParameterSpec(gcmTagLength * 8, iv);
        cipher.init(Cipher.ENCRYPT_MODE, masterKey, gcmSpec);
        
        // Encrypt
        byte[] encryptedData = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
        
        // Combine IV and encrypted data
        byte[] combined = new byte[iv.length + encryptedData.length];
        System.arraycopy(iv, 0, combined, 0, iv.length);
        System.arraycopy(encryptedData, 0, combined, iv.length, encryptedData.length);
        
        return Base64.getEncoder().encodeToString(combined);
    }
    
    /**
     * Decrypts a string value
     */
    public String decrypt(String encryptedText) throws Exception {
        if (encryptedText == null || encryptedText.isEmpty()) {
            return encryptedText;
        }
        
        // Decode from Base64
        byte[] combined = Base64.getDecoder().decode(encryptedText);
        
        // Extract IV and encrypted data
        byte[] iv = new byte[gcmIvLength];
        byte[] encryptedData = new byte[combined.length - gcmIvLength];
        System.arraycopy(combined, 0, iv, 0, gcmIvLength);
        System.arraycopy(combined, gcmIvLength, encryptedData, 0, encryptedData.length);
        
        // Initialize cipher
        Cipher cipher = Cipher.getInstance(algorithm);
        GCMParameterSpec# Phase 11: Production Considerations - Complete Guide

## 11.1 Monitoring and Metrics

### JPA Metrics: Hibernate Statistics

Hibernate provides comprehensive statistics about your JPA operations. Here's how to enable and use them:

#### Configuration
```yaml
# application.yml
spring:
  jpa:
    properties:
      hibernate:
        generate_statistics: true
        session:
          events:
            log:
              LOG_QUERIES_SLOWER_THAN_MS: 1000

management:
  endpoints:
    web:
      exposure:
        include: metrics, health, info, hibernate
  metrics:
    export:
      prometheus:
        enabled: true
```

#### Custom Metrics Configuration
```java
@Configuration
@EnableConfigurationProperties
public class HibernateMetricsConfig {

    @Bean
    public HibernateMetrics hibernateMetrics(EntityManagerFactory entityManagerFactory) {
        return new HibernateMetrics(entityManagerFactory, "hibernate", null);
    }

    @Component
    public static class DatabaseMetricsCollector {
        
        private final MeterRegistry meterRegistry;
        private final EntityManagerFactory entityManagerFactory;
        
        public DatabaseMetricsCollector(MeterRegistry meterRegistry, 
                                       EntityManagerFactory entityManagerFactory) {
            this.meterRegistry = meterRegistry;
            this.entityManagerFactory = entityManagerFactory;
            
            // Register custom metrics
            registerCustomMetrics();
        }
        
        private void registerCustomMetrics() {
            Gauge.builder("hibernate.sessions.open")
                .description("Number of currently open Hibernate sessions")
                .register(meterRegistry, this, 
                    metrics -> getHibernateStatistics().getSessionOpenCount());
                    
            Gauge.builder("hibernate.transactions.count")
                .description("Total number of transactions")
                .register(meterRegistry, this,
                    metrics -> getHibernateStatistics().getTransactionCount());
        }
        
        private Statistics getHibernateStatistics() {
            return entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        }
    }
}
```

#### Metrics Service for Custom Monitoring
```java
@Service
@Slf4j
public class DatabaseMetricsService {
    
    private final EntityManagerFactory entityManagerFactory;
    private final MeterRegistry meterRegistry;
    private final Timer.Sample queryTimer;
    
    public DatabaseMetricsService(EntityManagerFactory entityManagerFactory,
                                 MeterRegistry meterRegistry) {
        this.entityManagerFactory = entityManagerFactory;
        this.meterRegistry = meterRegistry;
    }
    
    public Statistics getHibernateStatistics() {
        return entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
    }
    
    public void logDatabaseMetrics() {
        Statistics stats = getHibernateStatistics();
        
        log.info("=== Database Metrics ===");
        log.info("Entity Load Count: {}", stats.getEntityLoadCount());
        log.info("Entity Insert Count: {}", stats.getEntityInsertCount());
        log.info("Entity Update Count: {}", stats.getEntityUpdateCount());
        log.info("Entity Delete Count: {}", stats.getEntityDeleteCount());
        log.info("Query Execution Count: {}", stats.getQueryExecutionCount());
        log.info("Query Cache Hit Count: {}", stats.getQueryCacheHitCount());
        log.info("Query Cache Miss Count: {}", stats.getQueryCacheMissCount());
        log.info("Second Level Cache Hit Count: {}", stats.getSecondLevelCacheHitCount());
        log.info("Second Level Cache Miss Count: {}", stats.getSecondLevelCacheMissCount());
        log.info("Session Open Count: {}", stats.getSessionOpenCount());
        log.info("Session Close Count: {}", stats.getSessionCloseCount());
        log.info("Transaction Count: {}", stats.getTransactionCount());
        log.info("Connection Count: {}", stats.getConnectCount());
    }
    
    @EventListener
    public void handleQueryExecution(QueryExecutionEvent event) {
        Timer.Sample sample = Timer.start(meterRegistry);
        sample.stop(Timer.builder("database.query.execution.time")
            .description("Database query execution time")
            .tag("query_type", event.getQueryType())
            .register(meterRegistry));
    }
}

// Custom event for query execution
public class QueryExecutionEvent {
    private final String queryType;
    private final long executionTime;
    
    public QueryExecutionEvent(String queryType, long executionTime) {
        this.queryType = queryType;
        this.executionTime = executionTime;
    }
    
    // getters...
}
```

### Spring Boot Actuator: Database Health Checks

#### Custom Health Indicator
```java
@Component
public class DatabaseHealthIndicator implements HealthIndicator {
    
    private final DataSource dataSource;
    private final EntityManagerFactory entityManagerFactory;
    
    public DatabaseHealthIndicator(DataSource dataSource, 
                                  EntityManagerFactory entityManagerFactory) {
        this.dataSource = dataSource;
        this.entityManagerFactory = entityManagerFactory;
    }
    
    @Override
    public Health health() {
        try {
            Health.Builder builder = checkDatabaseConnectivity();
            builder = checkHibernateStatistics(builder);
            builder = checkConnectionPool(builder);
            
            return builder.build();
        } catch (Exception e) {
            return Health.down()
                .withDetail("error", e.getMessage())
                .build();
        }
    }
    
    private Health.Builder checkDatabaseConnectivity() throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            if (connection.isValid(1)) {
                return Health.up()
                    .withDetail("database", "Available")
                    .withDetail("validationQuery", "Connection is valid");
            } else {
                return Health.down().withDetail("database", "Connection is not valid");
            }
        }
    }
    
    private Health.Builder checkHibernateStatistics(Health.Builder builder) {
        try {
            Statistics stats = entityManagerFactory.unwrap(SessionFactory.class)
                                                  .getStatistics();
            
            return builder
                .withDetail("hibernate.sessionOpenCount", stats.getSessionOpenCount())
                .withDetail("hibernate.queryExecutionCount", stats.getQueryExecutionCount())
                .withDetail("hibernate.queryExecutionMaxTime", stats.getQueryExecutionMaxTime())
                .withDetail("hibernate.entityLoadCount", stats.getEntityLoadCount());
                
        } catch (Exception e) {
            return builder.withDetail("hibernate.error", e.getMessage());
        }
    }
    
    private Health.Builder checkConnectionPool(Health.Builder builder) {
        if (dataSource instanceof HikariDataSource) {
            HikariDataSource hikari = (HikariDataSource) dataSource;
            HikariPoolMXBean poolMXBean = hikari.getHikariPoolMXBean();
            
            return builder
                .withDetail("pool.totalConnections", poolMXBean.getTotalConnections())
                .withDetail("pool.activeConnections", poolMXBean.getActiveConnections())
                .withDetail("pool.idleConnections", poolMXBean.getIdleConnections())
                .withDetail("pool.threadsAwaitingConnection", 
                           poolMXBean.getThreadsAwaitingConnection())
                .withDetail("pool.maxPoolSize", hikari.getMaximumPoolSize())
                .withDetail("pool.minPoolSize", hikari.getMinimumIdle());
        }
        
        return builder;
    }
}
```

### Query Logging: SQL Statement Logging

#### Comprehensive Logging Configuration
```yaml
# application.yml
spring:
  jpa:
    show-sql: false  # Don't use this in production
    properties:
      hibernate:
        format_sql: true
        use_sql_comments: true
        type: trace
        stat: debug

logging:
  level:
    org.hibernate.SQL: DEBUG
    org.hibernate.type.descriptor.sql.BasicBinder: TRACE
    org.springframework.jdbc.core.JdbcTemplate: DEBUG
    org.springframework.jdbc.core.StatementCreatorUtils: TRACE
    com.zaxxer.hikari.HikariConfig: DEBUG
    com.zaxxer.hikari: TRACE
    
    # Custom package logging
    com.yourcompany.repository: DEBUG
    
  pattern:
    console: "%d{yyyy-MM-dd HH:mm:ss} - %msg%n"
    file: "%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{36} - %msg%n"
```

#### Custom Query Logger
```java
@Component
@Slf4j
public class QueryLogger {
    
    private final MeterRegistry meterRegistry;
    
    public QueryLogger(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }
    
    @EventListener
    public void handleQueryExecution(QueryExecutionEvent event) {
        long executionTime = event.getExecutionTime();
        String queryType = event.getQueryType();
        
        // Log slow queries
        if (executionTime > 1000) { // 1 second threshold
            log.warn("Slow query detected: Type={}, ExecutionTime={}ms, Query={}", 
                    queryType, executionTime, event.getQuery());
        } else {
            log.debug("Query executed: Type={}, ExecutionTime={}ms", 
                     queryType, executionTime);
        }
        
        // Record metrics
        Timer.builder("database.query.execution")
            .description("Database query execution time")
            .tag("type", queryType)
            .register(meterRegistry)
            .record(executionTime, TimeUnit.MILLISECONDS);
    }
}

// Aspect for automatic query logging
@Aspect
@Component
@Slf4j
public class RepositoryLoggingAspect {
    
    private final ApplicationEventPublisher eventPublisher;
    
    public ZeroDowntimeMigrationService(Flyway flyway, DataSource dataSource,
                                       ApplicationEventPublisher eventPublisher) {
        this.flyway = flyway;
        this.dataSource = dataSource;
        this.eventPublisher = eventPublisher;
    }
    
    /**
     * Performs zero-downtime migration using blue-green deployment strategy
     */
    public void performZeroDowntimeMigration() {
        try {
            // 1. Create shadow tables for major schema changes
            createShadowTables();
            
            // 2. Start dual-write to both old and new tables
            enableDualWrite();
            
            // 3. Migrate existing data to new tables
            migrateExistingData();
            
            // 4. Switch reads to new tables
            switchReadsToNewTables();
            
            // 5. Stop writes to old tables
            stopWritesToOldTables();
            
            // 6. Drop old tables
            dropOldTables();
            
            eventPublisher.publishEvent(new MigrationCompletedEvent("zero-downtime-migration"));
            
        } catch (Exception e) {
            log.error("Zero-downtime migration failed", e);
            rollbackMigration();
            throw new MigrationException("Failed to perform zero-downtime migration", e);
        }
    }
    
    /**
     * Validates migration before execution
     */
    public MigrationValidationResult validateMigration() {
        MigrationValidationResult result = new MigrationValidationResult();
        
        try {
            // Check for breaking changes
            result.setHasBreakingChanges(hasBreakingChanges());
            
            // Estimate migration time
            result.setEstimatedTime(estimateMigrationTime());
            
            // Check data volume
            result.setDataVolume(calculateDataVolume());
            
            // Validate dependencies
            result.setDependenciesValid(validateDependencies());
            
            // Check rollback possibility
            result.setRollbackPossible(isRollbackPossible());
            
        } catch (Exception e) {
            log.error("Migration validation failed", e);
            result.setValid(false);
            result.setErrorMessage(e.getMessage());
        }
        
        return result;
    }
    
    private void createShadowTables() {
        // Implementation for creating shadow tables
        log.info("Creating shadow tables for zero-downtime migration");
    }
    
    private void enableDualWrite() {
        // Enable writing to both old and new tables
        log.info("Enabling dual-write mode");
    }
    
    private void migrateExistingData() {
        // Batch migrate existing data
        log.info("Migrating existing data to new schema");
    }
    
    private void switchReadsToNewTables() {
        // Switch application reads to new tables
        log.info("Switching reads to new tables");
    }
    
    private void stopWritesToOldTables() {
        // Stop writing to old tables
        log.info("Stopping writes to old tables");
    }
    
    private void dropOldTables() {
        // Clean up old tables
        log.info("Dropping old tables");
    }
    
    private void rollbackMigration() {
        // Implement rollback logic
        log.warn("Rolling back migration");
    }
    
    // Helper methods for validation
    private boolean hasBreakingChanges() { return false; }
    private Duration estimateMigrationTime() { return Duration.ofMinutes(5); }
    private long calculateDataVolume() { return 0L; }
    private boolean validateDependencies() { return true; }
    private boolean isRollbackPossible() { return true; }
}

@Data
public class MigrationValidationResult {
    private boolean valid = true;
    private boolean hasBreakingChanges;
    private Duration estimatedTime;
    private long dataVolume;
    private boolean dependenciesValid;
    private boolean rollbackPossible;
    private String errorMessage;
}

public class MigrationCompletedEvent {
    private final String migrationType;
    private final LocalDateTime completedAt;
    
    public MigrationCompletedEvent(String migrationType) {
        this.migrationType = migrationType;
        this.completedAt = LocalDateTime.now();
    }
    
    // getters...
}
```

### Liquibase Integration: XML/YAML migrations

#### Liquibase Configuration
```yaml
# application.yml
spring:
  liquibase:
    enabled: true
    change-log: classpath:db/changelog/db.changelog-master.xml
    contexts: ${spring.profiles.active}
    default-schema: public
    drop-first: false
    database-change-log-table: databasechangelog
    database-change-log-lock-table: databasechangeloglock
    liquibase-schema: public
```

#### Master Changelog Configuration
```xml
<!-- db.changelog-master.xml -->
<?xml version="1.0" encoding="UTF-8"?>
<databaseChangeLog
    xmlns="http://www.liquibase.org/xml/ns/dbchangelog"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xsi:schemaLocation="http://www.liquibase.org/xml/ns/dbchangelog
        http://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-3.8.xsd">

    <!-- Include environment-specific changesets -->
    <includeAll path="db/changelog/changes/" relativeToChangelogFile="true"/>
    
    <!-- Include data migration scripts -->
    <includeAll path="db/changelog/data/" relativeToChangelogFile="true"/>
    
    <!-- Include indexes and constraints -->
    <includeAll path="db/changelog/indexes/" relativeToChangelogFile="true"/>
    
</databaseChangeLog>
```

#### Complex Migration Examples
```xml
<!-- 001-create-user-table.xml -->
<?xml version="1.0" encoding="UTF-8"?>
<databaseChangeLog xmlns="http://www.liquibase.org/xml/ns/dbchangelog"
                   xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                   xsi:schemaLocation="http://www.liquibase.org/xml/ns/dbchangelog
                   http://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-3.8.xsd">

    <changeSet id="001-create-user-table" author="developer">
        <preConditions onFail="MARK_RAN">
            <not>
                <tableExists tableName="users"/>
            </not>
        </preConditions>
        
        <createTable tableName="users">
            <column name="id" type="BIGSERIAL">
                <constraints primaryKey="true" nullable="false"/>
            </column>
            <column name="username" type="VARCHAR(50)">
                <constraints nullable="false" unique="true"/>
            </column>
            <column name="email" type="VARCHAR(100)">
                <constraints nullable="false" unique="true"/>
            </column>
            <column name="password_hash" type="VARCHAR(255)">
                <constraints nullable="false"/>
            </column>
            <column name="first_name" type="VARCHAR(50)"/>
            <column name="last_name" type="VARCHAR(50)"/>
            <column name="created_at" type="TIMESTAMP" defaultValueComputed="CURRENT_TIMESTAMP">
                <constraints nullable="false"/>
            </column>
            <column name="updated_at" type="TIMESTAMP" defaultValueComputed="CURRENT_TIMESTAMP">
                <constraints nullable="false"/>
            </column>
            <column name="version" type="INTEGER" defaultValue="0">
                <constraints nullable="false"/>
            </column>
        </createTable>
        
        <createIndex tableName="users" indexName="idx_users_email">
            <column name="email"/>
        </createIndex>
        
        <createIndex tableName="users" indexName="idx_users_username">
            <column name="username"/>
        </createIndex>
        
        <rollback>
            <dropTable tableName="users"/>
        </rollback>
    </changeSet>
    
    <!-- Complex migration with data transformation -->
    <changeSet id="002-split-full-name" author="developer">
        <preConditions onFail="MARK_RAN">
            <columnExists tableName="users" columnName="full_name"/>
        </preConditions>
        
        <!-- Add new columns -->
        <addColumn tableName="users">
            <column name="first_name" type="VARCHAR(50)"/>
            <column name="last_name" type="VARCHAR(50)"/>
        </addColumn>
        
        <!-- Custom SQL to split full name -->
        <sql>
            UPDATE users 
            SET first_name = SPLIT_PART(full_name, ' ', 1),
                last_name = CASE 
                    WHEN ARRAY_LENGTH(STRING_TO_ARRAY(full_name, ' '), 1) > 1 
                    THEN SUBSTRING(full_name FROM POSITION(' ' IN full_name) + 1)
                    ELSE ''
                END
            WHERE full_name IS NOT NULL;
        </sql>
        
        <!-- Drop old column -->
        <dropColumn tableName="users" columnName="full_name"/>
        
        <rollback>
            <addColumn tableName="users">
                <column name="full_name" type="VARCHAR(100)"/>
            </addColumn>
            <sql>
                UPDATE users 
                SET full_name = CONCAT(COALESCE(first_name, ''), ' ', COALESCE(last_name, ''))
                WHERE first_name IS NOT NULL OR last_name IS NOT NULL;
            </sql>
            <dropColumn tableName="users" columnName="first_name"/>
            <dropColumn tableName="users" columnName="last_name"/>
        </rollback>
    </changeSet>
</databaseChangeLog>
```

#### YAML Format Migration Example
```yaml
# 003-create-product-table.yaml
databaseChangeLog:
  - changeSet:
      id: 003-create-product-table
      author: developer
      preConditions:
        onFail: MARK_RAN
        not:
          tableExists:
            tableName: products
      changes:
        - createTable:
            tableName: products
            columns:
              - column:
                  name: id
                  type: BIGSERIAL
                  constraints:
                    primaryKey: true
                    nullable: false
              - column:
                  name: name
                  type: VARCHAR(255)
                  constraints:
                    nullable: false
              - column:
                  name: description
                  type: TEXT
              - column:
                  name: price
                  type: DECIMAL(10,2)
                  constraints:
                    nullable: false
              - column:
                  name: category_id
                  type: BIGINT
              - column:
                  name: created_by
                  type: BIGINT
              - column:
                  name: created_at
                  type: TIMESTAMP
                  defaultValueComputed: CURRENT_TIMESTAMP
                  constraints:
                    nullable: false
              - column:
                  name: updated_at
                  type: TIMESTAMP
                  defaultValueComputed: CURRENT_TIMESTAMP
                  constraints:
                    nullable: false
              - column:
                  name: version
                  type: INTEGER
                  defaultValue: 0
                  constraints:
                    nullable: false
        
        - addForeignKeyConstraint:
            baseTableName: products
            baseColumnNames: created_by
            referencedTableName: users
            referencedColumnNames: id
            constraintName: fk_products_created_by
            
        - createIndex:
            tableName: products
            indexName: idx_products_category
            columns:
              - column:
                  name: category_id
                  
        - createIndex:
            tableName: products
            indexName: idx_products_price
            columns:
              - column:
                  name: price
      rollback:
        - dropTable:
            tableName: products
```

### JPA DDL Generation: hibernate.hbm2ddl.auto

#### DDL Configuration for Different Environments
```java
@Configuration
public class JpaDdlConfiguration {
    
    @Value("${spring.profiles.active:dev}")
    private String activeProfile;
    
    @Bean
    @ConditionalOnProperty(name = "spring.jpa.hibernate.ddl-auto", havingValue = "none", matchIfMissing = false)
    public JpaVendorAdapter jpaVendorAdapter() {
        HibernateJpaVendorAdapter adapter = new HibernateJpaVendorAdapter();
        
        // Environment-specific DDL settings
        switch (activeProfile) {
            case "dev":
                adapter.setShowSql(true);
                adapter.setGenerateDdl(true);
                break;
            case "test":
                adapter.setShowSql(false);
                adapter.setGenerateDdl(true);
                break;
            case "prod":
                adapter.setShowSql(false);
                adapter.setGenerateDdl(false); // Never generate DDL in production
                break;
        }
        
        return adapter;
    }
    
    @Bean
    public LocalContainerEntityManagerFactoryBean entityManagerFactory(
            DataSource dataSource, JpaVendorAdapter jpaVendorAdapter) {
        
        LocalContainerEntityManagerFactoryBean factory = new LocalContainerEntityManagerFactoryBean();
        factory.setDataSource(dataSource);
        factory.setJpaVendorAdapter(jpaVendorAdapter);
        factory.setPackagesToScan("com.yourcompany.entity");
        
        // DDL properties
        Properties jpaProperties = new Properties();
        
        if ("dev".equals(activeProfile)) {
            jpaProperties.put("hibernate.hbm2ddl.auto", "update");
            jpaProperties.put("hibernate.hbm2ddl.import_files", "data-dev.sql");
        } else if ("test".equals(activeProfile)) {
            jpaProperties.put("hibernate.hbm2ddl.auto", "create-drop");
            jpaProperties.put("hibernate.hbm2ddl.import_files", "data-test.sql");
        } else {
            // Production: Never let Hibernate manage schema
            jpaProperties.put("hibernate.hbm2ddl.auto", "validate");
        }
        
        // Schema validation and export
        jpaProperties.put("hibernate.hbm2ddl.halt_on_error", "true");
        jpaProperties.put("javax.persistence.schema-generation.scripts.action", "create");
        jpaProperties.put("javax.persistence.schema-generation.scripts.create-target", 
                         "target/generated-schema.sql");
        
        factory.setJpaProperties(jpaProperties);
        
        return factory;
    }
}
```

#### Schema Generation and Validation Service
```java
@Service
@Slf4j
public class SchemaManagementService {
    
    private final EntityManagerFactory entityManagerFactory;
    private final DataSource dataSource;
    
    public SchemaManagementService(EntityManagerFactory entityManagerFactory, 
                                  DataSource dataSource) {
        this.entityManagerFactory = entityManagerFactory;
        this.dataSource = dataSource;
    }
    
    /**
     * Generates DDL scripts for the current entity model
     */
    public void generateDDLScripts() {
        try {
            // Get Hibernate metadata
            MetadataImplementor metadata = (MetadataImplementor) 
                ((SessionFactoryImplementor) entityManagerFactory.unwrap(SessionFactory.class))
                .getMetamodel();
            
            // Generate create script
            SchemaExport schemaExport = new SchemaExport();
            schemaExport.setOutputFile("target/create-schema.sql");
            schemaExport.create(EnumSet.of(TargetType.SCRIPT), metadata);
            
            // Generate drop script
            schemaExport.setOutputFile("target/drop-schema.sql");
            schemaExport.drop(EnumSet.of(TargetType.SCRIPT), metadata);
            
            log.info("DDL scripts generated successfully");
            
        } catch (Exception e) {
            log.error("Failed to generate DDL scripts", e);
            throw new SchemaGenerationException("DDL generation failed", e);
        }
    }
    
    /**
     * Validates current database schema against entity model
     */
    public SchemaValidationResult validateSchema() {
        SchemaValidationResult result = new SchemaValidationResult();
        
        try {
            SchemaValidator validator = new SchemaValidator();
            MetadataImplementor metadata = getHibernateMetadata();
            
            // Perform validation
            validator.validate(metadata, (DatabaseMetadata) null);
            
            result.setValid(true);
            result.setMessage("Schema validation successful");
            
        } catch (SchemaManagementException e) {
            result.setValid(false);
            result.setMessage(e.getMessage());
            result.setErrors(extractValidationErrors(e));
            
            log.error("Schema validation failed", e);
        }
        
        return result;
    }
    
    /**
     * Updates database schema to match entity model
     */
    public void updateSchema() {
        if (!"dev".equals(getCurrentProfile())) {
            throw new UnsupportedOperationException("Schema updates not allowed in " + getCurrentProfile());
        }
        
        try {
            SchemaUpdate schemaUpdate = new SchemaUpdate();
            MetadataImplementor metadata = getHibernateMetadata();
            
            schemaUpdate.execute(EnumSet.of(TargetType.DATABASE), metadata);
            
            log.info("Schema updated successfully");
            
        } catch (Exception e) {
            log.error("Schema update failed", e);
            throw new SchemaUpdateException("Schema update failed", e);
        }
    }
    
    private MetadataImplementor getHibernateMetadata() {
        return (MetadataImplementor) 
            ((SessionFactoryImplementor) entityManagerFactory.unwrap(SessionFactory.class))
            .getMetamodel();
    }
    
    private List<String> extractValidationErrors(SchemaManagementException e) {
        // Extract specific validation errors from exception
        return Arrays.asList(e.getMessage().split("\n"));
    }
    
    private String getCurrentProfile() {
        return System.getProperty("spring.profiles.active", "dev");
    }
}

@Data
public class SchemaValidationResult {
    private boolean valid;
    private String message;
    private List<String> errors = new ArrayList<>();
}
```

## 11.3 Security Considerations

### SQL Injection Prevention: Parameterized queries

#### Repository Security Best Practices
```java
@Repository
public class SecureUserRepository {
    
    @PersistenceContext
    private EntityManager entityManager;
    
    /**
     * SECURE: Using parameterized queries
     */
    public List<User> findUsersByEmail(String email) {
        // Good: Using named parameters
        TypedQuery<User> query = entityManager.createQuery(
            "SELECT u FROM User u WHERE u.email = :email", User.class);
        query.setParameter("email", email);
        return query.getResultList();
    }
    
    /**
     * SECURE: Using Criteria API (type-safe)
     */
    public List<User> findUsersByCriteria(String username, String status) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<User> query = cb.createQuery(User.class);
        Root<User> root = query.from(User.class);
        
        List<Predicate> predicates = new ArrayList<>();
        
        if (username != null && !username.trim().isEmpty()) {
            predicates.add(cb.like(cb.lower(root.get("username")), 
                                 "%" + username.toLowerCase() + "%"));
        }
        
        if (status != null && !status.trim().isEmpty()) {
            predicates.add(cb.equal(root.get("status"), UserStatus.valueOf(status)));
        }
        
        query.where(predicates.toArray(new Predicate[0]));
        
        return entityManager.createQuery(query).getResultList();
    }
    
    /**
     * SECURE: Native query with parameters
     */
    @Query(value = "SELECT * FROM users u WHERE u.created_at BETWEEN ?1 AND ?2", 
           nativeQuery = true)
    List<User> findUsersByDateRange(LocalDateTime startDate, LocalDateTime endDate);
    
    /**
     * DANGEROUS: This method shows what NOT to do
     * Never concatenate user input directly into queries
     */
    // DON'T DO THIS:
    // public List<User> findUsersByEmailUnsafe(String email) {
    //     String jpql = "SELECT u FROM User u WHERE u.email = '" + email + "'";
    //     return entityManager.createQuery(jpql, User.class).getResultList();
    // }
}
```

#### SQL Injection Prevention Interceptor
```java
@Component
@Slf4j
public class SqlInjectionPreventionInterceptor implements Interceptor {
    
    private static final Pattern DANGEROUS_PATTERNS = Pattern.compile(
        ".*([';]|(--)|(/\\*)|(\\*/)|(@)|char|nchar|varchar|nvarchar|alter|begin|cast|create|cursor|declare|delete|drop|exec|execute|fetch|insert|kill|select|sys|sysobjects|syscolumns|table|update).*",
        Pattern.CASE_INSENSITIVE
    );
    
    @Override
    public boolean onLoad(Object entity, Serializable id, Object[] state, String[] propertyNames, Type[] types) {
        // Validate data being loaded
        validateEntityData(state, propertyNames);
        return false;
    }
    
    @Override
    public boolean onSave(Object entity, Serializable id, Object[] state, String[] propertyNames, Type[] types) {
        // Validate data being saved
        validateEntityData(state, propertyNames);
        return false;
    }
    
    @Override
    public void onDelete(Object entity, Serializable id, Object[] state, String[] propertyNames, Type[] types) {
        // Additional validation if needed during delete
    }
    
    private void validateEntityData(Object[] state, String[] propertyNames) {
        if (state == null || propertyNames == null) return;
        
        for (int i = 0; i < state.length; i++) {
            if (state[i] instanceof String) {
                String value = (String) state[i];
                if (containsSqlInjection(value)) {
                    log.warn("Potential SQL injection detected in property '{}': {}", 
                            propertyNames[i], sanitizeForLogging(value));
                    
                    throw new SecurityException(
                        "Potentially dangerous SQL content detected in: " + propertyNames[i]);
                }
            }
        }
    }
    
    private boolean containsSqlInjection(String input) {
        if (input == null || input.trim().isEmpty()) {
            return false;
        }
        
        return DANGEROUS_PATTERNS.matcher(input.trim()).matches();
    }
    
    private String sanitizeForLogging(String input) {
        // Remove or mask potentially sensitive information for logging
        return input.length() > 50 ? input.substring(0, 50) + "..." : input;
    }
}

// Register the interceptor
@Configuration
public class HibernateInterceptorConfig {
    
    @Bean
    public LocalContainerEntityManagerFactoryBean entityManagerFactory(
            DataSource dataSource, 
            SqlInjectionPreventionInterceptor interceptor) {
        
        LocalContainerEntityManagerFactoryBean factory = new LocalContainerEntityManagerFactoryBean();
        factory.setDataSource(dataSource);
        
        Properties jpaProperties = new Properties();
        // Register the interceptor
        jpaProperties.put("hibernate.session_factory.interceptor", interceptor);
        
        factory.setJpaProperties(jpaProperties);
        
        return factory;
    }
}
```

### Database Credentials: Secure configuration management

#### Secure Configuration Setup
```yaml
# application.yml - Base configuration
spring:
  datasource:
    driver-class-name: org.postgresql.Driver
    # Don't put credentials in plain text files
    # Use environment variables or external config
    
  jpa:
    hibernate:
      ddl-auto: validate
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect
        
# Use profiles for different environments
---
spring:
  config:
    activate:
      on-profile: dev
  datasource:
    url: jdbc:postgresql://localhost:5432/myapp_dev
    username: ${DB_USERNAME:dev_user}
    password: ${DB_PASSWORD:dev_password}

---
spring:
  config:
    activate:
      on-profile: prod
  datasource:
    url: ${DATABASE_URL}
    username: ${DATABASE_USERNAME}
    password: ${DATABASE_PASSWORD}
    
    # Production-specific security settings
    hikari:
      connection-test-query: SELECT 1
      leak-detection-threshold: 60000
      maximum-pool-size: 20
      minimum-idle: 5
```

#### Secure Configuration Management Service
```java
@Configuration
@ConfigurationProperties(prefix = "app.security.database")
@Data
public class DatabaseSecurityProperties {
    private boolean encryptConnection = true;
    private String encryptionAlgorithm = "AES";
    private int connectionTimeout = 30000;
    private boolean requireSsl = true;
    private String trustStore;
    private String trustStorePassword;
}

@Service
@Slf4j
public class SecureConfigurationService {
    
    private final DatabaseSecurityProperties securityProperties;
    private final Environment environment;
    
    public SecureConfigurationService(DatabaseSecurityProperties securityProperties,
                                    Environment environment) {
        this.securityProperties = securityProperties;
        this.environment = environment;
    }
    
    @PostConstruct
    public void validateConfiguration() {
        validateDatabaseCredentials();
        validateSslConfiguration();
        validateEncryption();
    }
    
    private void validateDatabaseCredentials() {
        String[] sensitiveProperties = {
            "spring.datasource.username",
            "spring.datasource.password",
            "DATABASE_USERNAME",
            "DATABASE_PASSWORD"
        };
        
        for (String property : sensitiveProperties) {
            String value = environment.getProperty(property);
            if (value != null && isWeakCredential(value)) {
                log.warn("Weak database credential detected for property: {}", property);
            }
        }
    }
    
    private void validateSslConfiguration() {
        if (securityProperties.isRequireSsl()) {
            String jdbcUrl = environment.getProperty("spring.datasource.url");
            if (jdbcUrl != null && !jdbcUrl.contains("ssl=true")) {
                log.warn("SSL is required but not configured in JDBC URL");
            }
        }
    }
    
    private void validateEncryption() {
        if (securityProperties.isEncryptConnection()) {
            // Validate encryption settings
            log.info("Database connection encryption is enabled with algorithm: {}", 
                    securityProperties.getEncryptionAlgorithm());
        }
    }
    
    private boolean isWeakCredential(String credential) {
        // Check for common weak patterns
        String[] weakPasswords = {"password", "123456", "admin", "root", "test"};
        String lowerCredential = credential.toLowerCase();
        
        return Arrays.stream(weakPasswords)
                    .anyMatch(lowerCredential::contains) ||
               credential.length() < 8;
    }
    
    /**
     * Encrypts sensitive configuration values
     */
    public String encryptValue(String plainText) {
        try {
            Cipher cipher = Cipher.getInstance(securityProperties.getEncryptionAlgorithm());
            cipher.init(Cipher.ENCRYPT_MODE, getEncryptionKey());
            
            byte[] encrypted = cipher.doFinal(plainText.getBytes());
            return Base64.getEncoder().encodeToString(encrypted);
            
        } catch (Exception e) {
            log.error("Failed to encrypt value", e);
            throw new SecurityException("Encryption failed", e);
        }
    }
    
    /**
     * Decrypts sensitive configuration values
     */
    public String decryptValue(String encryptedText) {
        try {
            Cipher cipher = Cipher.getInstance(securityProperties.getEncryptionAlgorithm());
            cipher.init(Cipher.DECRYPT_MODE, getEncryptionKey());
            
            byte[] decrypted = cipher.doFinal(Base64.getDecoder().decode(encryptedText));
            return new String(decrypted);
            
        } catch (Exception e) {
            log.error("Failed to decrypt value", e);
            throw new SecurityException("Decryption failed", e);
        }
    }
    
    private SecretKeySpec getEncryptionKey() {
        // Get encryption key from secure location (environment variable, key management service, etc.)
        String keyString = environment.getProperty("ENCRYPTION_KEY");
        if (keyString == null) {
            throw new SecurityException("Encryption key not configured");
        }
        
        return new SecretKeySpec(keyString.getBytes(), securityProperties.getEncryptionAlgorithm());
    }
}
```

This completes Phase 11 with comprehensive coverage of:

1. **Monitoring and Metrics**: Hibernate statistics, health checks, query logging, and connection pool monitoring
2. **Database Migration**: Flyway and Liquibase integration with zero-downtime strategies
3. **Security Considerations**: SQL injection prevention and secure configuration management

Each section includes production-ready code examples with proper error handling, logging, and security measures. The examples demonstrate real-world scenarios you'll encounter when deploying Spring Data JPA applications to production environments.public RepositoryLoggingAspect(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }
    
    @Around("execution(* com.yourcompany.repository.*.*(..))")
    public Object logRepositoryMethods(ProceedingJoinPoint joinPoint) throws Throwable {
        long startTime = System.currentTimeMillis();
        String methodName = joinPoint.getSignature().getName();
        String className = joinPoint.getTarget().getClass().getSimpleName();
        
        try {
            Object result = joinPoint.proceed();
            
            long executionTime = System.currentTimeMillis() - startTime;
            
            log.debug("Repository method executed: {}.{} in {}ms", 
                     className, methodName, executionTime);
            
            // Publish event for metrics collection
            eventPublisher.publishEvent(
                new QueryExecutionEvent("repository_method", executionTime));
            
            return result;
            
        } catch (Exception e) {
            long executionTime = System.currentTimeMillis() - startTime;
            log.error("Repository method failed: {}.{} in {}ms, Error: {}", 
                     className, methodName, executionTime, e.getMessage());
            throw e;
        }
    }
}
```

### Performance Monitoring: Slow Query Detection

#### Advanced Performance Monitor
```java
@Component
@Slf4j
public class DatabasePerformanceMonitor {
    
    private final MeterRegistry meterRegistry;
    private final Map<String, LongAdder> queryCounters = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> slowQueryCounters = new ConcurrentHashMap<>();
    
    // Configurable thresholds
    @Value("${app.monitoring.slow-query-threshold:1000}")
    private long slowQueryThreshold;
    
    @Value("${app.monitoring.very-slow-query-threshold:5000}")
    private long verySlowQueryThreshold;
    
    public DatabasePerformanceMonitor(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        setupMetrics();
    }
    
    private void setupMetrics() {
        Gauge.builder("database.slow_queries.count")
            .description("Number of slow queries")
            .register(meterRegistry, this, 
                monitor -> slowQueryCounters.values()
                    .stream()
                    .mapToLong(AtomicLong::get)
                    .sum());
    }
    
    public void recordQueryExecution(String query, long executionTimeMs, String operation) {
        // Count all queries
        queryCounters.computeIfAbsent(operation, k -> new LongAdder()).increment();
        
        // Record execution time
        Timer.builder("database.query.duration")
            .description("Database query execution time")
            .tag("operation", operation)
            .tag("performance", getPerformanceCategory(executionTimeMs))
            .register(meterRegistry)
            .record(executionTimeMs, TimeUnit.MILLISECONDS);
        
        // Handle slow queries
        if (executionTimeMs > slowQueryThreshold) {
            handleSlowQuery(query, executionTimeMs, operation);
        }
        
        // Generate alerts for very slow queries
        if (executionTimeMs > verySlowQueryThreshold) {
            generateSlowQueryAlert(query, executionTimeMs, operation);
        }
    }
    
    private String getPerformanceCategory(long executionTimeMs) {
        if (executionTimeMs < 100) return "fast";
        if (executionTimeMs < 1000) return "normal";
        if (executionTimeMs < 5000) return "slow";
        return "very_slow";
    }
    
    private void handleSlowQuery(String query, long executionTime, String operation) {
        slowQueryCounters.computeIfAbsent(operation, k -> new AtomicLong()).incrementAndGet();
        
        log.warn("Slow query detected - Operation: {}, Duration: {}ms, Query: {}", 
                operation, executionTime, sanitizeQuery(query));
        
        // Store slow query information for analysis
        storeSlowQueryInfo(query, executionTime, operation);
    }
    
    private void generateSlowQueryAlert(String query, long executionTime, String operation) {
        log.error("ALERT: Very slow query - Operation: {}, Duration: {}ms, Query: {}", 
                 operation, executionTime, sanitizeQuery(query));
        
        // You can integrate with alerting systems here
        // e.g., send to Slack, email, or monitoring system
    }
    
    private String sanitizeQuery(String query) {
        // Remove or mask sensitive information from queries for logging
        return query.length() > 200 ? query.substring(0, 200) + "..." : query;
    }
    
    private void storeSlowQueryInfo(String query, long executionTime, String operation) {
        // Store in a separate table or send to monitoring system for analysis
        SlowQueryInfo info = SlowQueryInfo.builder()
            .query(query)
            .executionTime(executionTime)
            .operation(operation)
            .timestamp(LocalDateTime.now())
            .build();
        
        // Async storage to avoid impacting performance
        CompletableFuture.runAsync(() -> {
            // Save to database or send to monitoring system
        });
    }
    
    @Scheduled(fixedRate = 60000) // Every minute
    public void reportPerformanceMetrics() {
        long totalQueries = queryCounters.values().stream()
            .mapToLong(LongAdder::sum)
            .sum();
        
        long totalSlowQueries = slowQueryCounters.values().stream()
            .mapToLong(AtomicLong::get)
            .sum();
        
        if (totalQueries > 0) {
            double slowQueryPercentage = (double) totalSlowQueries / totalQueries * 100;
            
            log.info("Performance Summary - Total Queries: {}, Slow Queries: {} ({:.2f}%)", 
                    totalQueries, totalSlowQueries, slowQueryPercentage);
            
            // Alert if slow query percentage is too high
            if (slowQueryPercentage > 10) { // 10% threshold
                log.warn("High slow query percentage detected: {:.2f}%", slowQueryPercentage);
            }
        }
    }
}

@Data
@Builder
public class SlowQueryInfo {
    private String query;
    private long executionTime;
    private String operation;
    private LocalDateTime timestamp;
}
```

### Connection Pool Monitoring: HikariCP Metrics

#### HikariCP Advanced Configuration and Monitoring
```java
@Configuration
public class HikariCPConfiguration {
    
    @Bean
    @ConfigurationProperties("spring.datasource.hikari")
    public HikariConfig hikariConfig() {
        HikariConfig config = new HikariConfig();
        
        // Connection pool settings
        config.setMaximumPoolSize(20);
        config.setMinimumIdle(5);
        config.setIdleTimeout(300000); // 5 minutes
        config.setConnectionTimeout(30000); // 30 seconds
        config.setMaxLifetime(1800000); // 30 minutes
        config.setLeakDetectionThreshold(60000); // 1 minute
        
        // Performance settings
        config.setConnectionTestQuery("SELECT 1");
        config.setValidationTimeout(5000);
        config.setInitializationFailTimeout(1);
        
        // Monitoring settings
        config.setRegisterMbeans(true);
        config.setMetricRegistry(new MetricRegistry());
        
        return config;
    }
    
    @Bean
    @Primary
    public DataSource dataSource(HikariConfig hikariConfig) {
        return new HikariDataSource(hikariConfig);
    }
    
    @Bean
    public HikariPoolMonitor hikariPoolMonitor(DataSource dataSource, 
                                               MeterRegistry meterRegistry) {
        return new HikariPoolMonitor((HikariDataSource) dataSource, meterRegistry);
    }
}

@Component
@Slf4j
public class HikariPoolMonitor {
    
    private final HikariDataSource dataSource;
    private final MeterRegistry meterRegistry;
    private final HikariPoolMXBean poolMXBean;
    
    public HikariPoolMonitor(HikariDataSource dataSource, MeterRegistry meterRegistry) {
        this.dataSource = dataSource;
        this.meterRegistry = meterRegistry;
        this.poolMXBean = dataSource.getHikariPoolMXBean();
        
        registerMetrics();
    }
    
    private void registerMetrics() {
        // Active connections
        Gauge.builder("hikaricp.connections.active")
            .description("Active connections in the pool")
            .register(meterRegistry, poolMXBean, HikariPoolMXBean::getActiveConnections);
        
        // Idle connections
        Gauge.builder("hikaricp.connections.idle")
            .description("Idle connections in the pool")
            .register(meterRegistry, poolMXBean, HikariPoolMXBean::getIdleConnections);
        
        // Total connections
        Gauge.builder("hikaricp.connections.total")
            .description("Total connections in the pool")
            .register(meterRegistry, poolMXBean, HikariPoolMXBean::getTotalConnections);
        
        // Threads awaiting connection
        Gauge.builder("hikaricp.connections.pending")
            .description("Threads awaiting connections")
            .register(meterRegistry, poolMXBean, HikariPoolMXBean::getThreadsAwaitingConnection);
        
        // Maximum pool size
        Gauge.builder("hikaricp.connections.max")
            .description("Maximum pool size")
            .register(meterRegistry, this, monitor -> dataSource.getMaximumPoolSize());
        
        // Minimum idle
        Gauge.builder("hikaricp.connections.min")
            .description("Minimum idle connections")
            .register(meterRegistry, this, monitor -> dataSource.getMinimumIdle());
    }
    
    @Scheduled(fixedRate = 30000) // Every 30 seconds
    public void logPoolStatistics() {
        int activeConnections = poolMXBean.getActiveConnections();
        int idleConnections = poolMXBean.getIdleConnections();
        int totalConnections = poolMXBean.getTotalConnections();
        int threadsAwaiting = poolMXBean.getThreadsAwaitingConnection();
        
        log.info("HikariCP Pool Stats - Active: {}, Idle: {}, Total: {}, Pending: {}", 
                activeConnections, idleConnections, totalConnections, threadsAwaiting);
        
        // Alert on potential issues
        if (threadsAwaiting > 0) {
            log.warn("Connection pool bottleneck detected - {} threads waiting for connections", 
                    threadsAwaiting);
        }
        
        double utilizationPercent = (double) activeConnections / dataSource.getMaximumPoolSize() * 100;
        if (utilizationPercent > 80) {
            log.warn("High connection pool utilization: {:.1f}%", utilizationPercent);
        }
    }
    
    @EventListener(ApplicationReadyEvent.class)
    public void validatePoolConfiguration() {
        log.info("=== HikariCP Configuration ===");
        log.info("Maximum Pool Size: {}", dataSource.getMaximumPoolSize());
        log.info("Minimum Idle: {}", dataSource.getMinimumIdle());
        log.info("Connection Timeout: {}ms", dataSource.getConnectionTimeout());
        log.info("Idle Timeout: {}ms", dataSource.getIdleTimeout());
        log.info("Max Lifetime: {}ms", dataSource.getMaxLifetime());
        log.info("Leak Detection Threshold: {}ms", dataSource.getLeakDetectionThreshold());
        
        // Validate configuration
        if (dataSource.getMaximumPoolSize() < 10) {
            log.warn("Connection pool size might be too small for production: {}", 
                    dataSource.getMaximumPoolSize());
        }
        
        if (dataSource.getLeakDetectionThreshold() == 0) {
            log.warn("Connection leak detection is disabled - consider enabling for production");
        }
    }
}
```

## 11.2 Database Migration

### Flyway Integration: Version-controlled migrations

#### Flyway Configuration
```yaml
# application.yml
spring:
  flyway:
    enabled: true
    baseline-on-migrate: true
    locations: classpath:db/migration
    schemas: public
    table: flyway_schema_history
    sql-migration-prefix: V
    sql-migration-separator: __
    sql-migration-suffixes: .sql
    validate-on-migrate: true
    clean-disabled: true  # Important for production
    placeholder-replacement: true
    placeholders:
      database.name: ${spring.datasource.database-name:myapp}
    
  jpa:
    hibernate:
      ddl-auto: validate  # Important: Don't let Hibernate manage schema
```

#### Advanced Flyway Configuration
```java
@Configuration
@ConditionalOnProperty(name = "spring.flyway.enabled", havingValue = "true", matchIfMissing = true)
public class FlywayConfiguration {
    
    @Bean
    public FlywayConfigurationCustomizer flywayConfigurationCustomizer() {
        return configuration -> {
            // Custom migration locations based on profile
            configuration.locations("classpath:db/migration/common", 
                                  "classpath:db/migration/" + getActiveProfile());
            
            // Custom callbacks for migration events
            configuration.callbacks(new CustomFlywayCallback());
            
            // Migration validation
            configuration.validateOnMigrate(true);
            configuration.ignoreMissingMigrations(false);
            configuration.ignoreIgnoredMigrations(false);
            configuration.ignoreFutureMigrations(false);
        };
    }
    
    @Bean
    public FlywayMigrationStrategy flywayMigrationStrategy() {
        return new FlywayMigrationStrategy() {
            @Override
            public void migrate(Flyway flyway) {
                try {
                    // Validate current state
                    flyway.validate();
                    
                    // Perform migration
                    MigrateResult result = flyway.migrate();
                    
                    log.info("Flyway migration completed successfully. " +
                            "Migrations executed: {}, Target version: {}", 
                            result.migrationsExecuted, result.targetSchemaVersion);
                            
                } catch (FlywayException e) {
                    log.error("Flyway migration failed", e);
                    
                    // In production, you might want to handle this differently
                    // e.g., send alerts, perform rollback, etc.
                    handleMigrationFailure(e, flyway);
                    
                    throw e;
                }
            }
        };
    }
    
    private void handleMigrationFailure(FlywayException e, Flyway flyway) {
        // Log detailed information about the failure
        log.error("Migration failure details:");
        log.error("Error message: {}", e.getMessage());
        
        // Get migration info
        MigrationInfoService infoService = flyway.info();
        for (MigrationInfo info : infoService.all()) {
            if (info.getState() == MigrationState.FAILED) {
                log.error("Failed migration: {} - {}", 
                         info.getVersion(), info.getDescription());
            }
        }
        
        // Send alert (implement based on your alerting system)
        sendMigrationFailureAlert(e);
    }
    
    private void sendMigrationFailureAlert(FlywayException e) {
        // Implementation depends on your alerting system
        // Could be email, Slack, monitoring system, etc.
    }
    
    private String getActiveProfile() {
        // Return active profile for environment-specific migrations
        return System.getProperty("spring.profiles.active", "dev");
    }
}

// Custom Flyway callback for migration events
@Slf4j
public class CustomFlywayCallback implements Callback {
    
    @Override
    public boolean supports(Event event, Context context) {
        return true; // Support all events
    }
    
    @Override
    public boolean canHandleInTransaction(Event event, Context context) {
        return true;
    }
    
    @Override
    public void handle(Event event, Context context) {
        switch (event) {
            case BEFORE_MIGRATE:
                log.info("Starting database migration...");
                recordMigrationStart();
                break;
                
            case AFTER_MIGRATE:
                log.info("Database migration completed successfully");
                recordMigrationEnd(true);
                break;
                
            case AFTER_MIGRATE_ERROR:
                log.error("Database migration failed");
                recordMigrationEnd(false);
                break;
                
            case BEFORE_EACH_MIGRATE:
                MigrationInfo migrationInfo = context.getMigrationInfo();
                log.info("Executing migration: {} - {}", 
                        migrationInfo.getVersion(), 
                        migrationInfo.getDescription());
                break;
                
            case AFTER_EACH_MIGRATE:
                MigrationInfo completedMigration = context.getMigrationInfo();
                log.info("Completed migration: {} in {}ms", 
                        completedMigration.getVersion(),
                        completedMigration.getExecutionTime());
                break;
                
            case AFTER_EACH_MIGRATE_ERROR:
                log.error("Migration failed: {}", context.getMigrationInfo().getVersion());
                break;
        }
    }
    
    private void recordMigrationStart() {
        // Record migration start time and details
        // Could be stored in database, sent to monitoring system, etc.
    }
    
    private void recordMigrationEnd(boolean success) {
        // Record migration completion and success status
        // Calculate total migration time, etc.
    }
}
```

#### Migration File Examples

```sql
-- V1__Create_user_table.sql
CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    email VARCHAR(100) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    first_name VARCHAR(50),
    last_name VARCHAR(50),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    version INTEGER DEFAULT 0
);

CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_username ON users(username);

-- V2__Create_product_table.sql
CREATE TABLE products (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    price DECIMAL(10,2) NOT NULL,
    category_id BIGINT,
    created_by BIGINT REFERENCES users(id),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    version INTEGER DEFAULT 0
);

CREATE INDEX idx_products_category ON products(category_id);
CREATE INDEX idx_products_price ON products(price);

-- V3__Add_user_profile_fields.sql
ALTER TABLE users 
ADD COLUMN phone VARCHAR(20),
ADD COLUMN date_of_birth DATE,
ADD COLUMN status VARCHAR(20) DEFAULT 'ACTIVE';

-- Create enum type for status
CREATE TYPE user_status AS ENUM ('ACTIVE', 'INACTIVE', 'SUSPENDED');
ALTER TABLE users ALTER COLUMN status TYPE user_status USING status::user_status;
```

#### Zero-Downtime Migration Strategies

```java
@Service
@Slf4j
public class ZeroDowntimeMigrationService {
    
    private final Flyway flyway;
    private final DataSource dataSource;
    private final ApplicationEventPublisher eventPublisher;
}