# Phase 6: Database Design & Optimization
*Complete Guide with Real-World Examples*

## Table of Contents
1. [Database Design](#database-design)
2. [Performance Optimization](#performance-optimization)
3. [Real-World Project](#real-world-project)

---

## Database Design

### Normalization

Normalization is the process of organizing data to minimize redundancy and dependency. It's crucial for data integrity and efficient storage.

#### First Normal Form (1NF)

**Rules:**
- Each column contains atomic (indivisible) values
- Each row is unique
- No repeating groups

**Example: Violation of 1NF**
```sql
-- BAD: Violates 1NF (multiple phone numbers in one column)
CREATE TABLE customers_bad (
    customer_id INT PRIMARY KEY,
    name VARCHAR(100),
    phone_numbers VARCHAR(200) -- "555-1234, 555-5678, 555-9012"
);
```

**Example: Following 1NF**
```sql
-- GOOD: Follows 1NF
CREATE TABLE customers (
    customer_id INT PRIMARY KEY,
    name VARCHAR(100),
    email VARCHAR(100)
);

CREATE TABLE customer_phones (
    phone_id INT PRIMARY KEY AUTO_INCREMENT,
    customer_id INT,
    phone_number VARCHAR(20),
    phone_type ENUM('mobile', 'home', 'work'),
    FOREIGN KEY (customer_id) REFERENCES customers(customer_id)
);
```

#### Second Normal Form (2NF)

**Rules:**
- Must be in 1NF
- All non-key attributes must be fully dependent on the primary key
- No partial dependencies

**Example: Violation of 2NF**
```sql
-- BAD: Violates 2NF (partial dependency)
CREATE TABLE order_items_bad (
    order_id INT,
    product_id INT,
    product_name VARCHAR(100), -- Depends only on product_id, not the full key
    product_category VARCHAR(50), -- Depends only on product_id
    quantity INT,
    unit_price DECIMAL(10,2),
    PRIMARY KEY (order_id, product_id)
);
```

**Example: Following 2NF**
```sql
-- GOOD: Follows 2NF
CREATE TABLE products (
    product_id INT PRIMARY KEY,
    product_name VARCHAR(100),
    product_category VARCHAR(50),
    unit_price DECIMAL(10,2)
);

CREATE TABLE order_items (
    order_id INT,
    product_id INT,
    quantity INT,
    unit_price DECIMAL(10,2), -- Price at time of order
    PRIMARY KEY (order_id, product_id),
    FOREIGN KEY (product_id) REFERENCES products(product_id)
);
```

#### Third Normal Form (3NF)

**Rules:**
- Must be in 2NF
- No transitive dependencies (non-key attributes shouldn't depend on other non-key attributes)

**Example: Violation of 3NF**
```sql
-- BAD: Violates 3NF (transitive dependency)
CREATE TABLE employees_bad (
    employee_id INT PRIMARY KEY,
    name VARCHAR(100),
    department_id INT,
    department_name VARCHAR(100), -- Depends on department_id, not employee_id
    department_location VARCHAR(100) -- Depends on department_id, not employee_id
);
```

**Example: Following 3NF**
```sql
-- GOOD: Follows 3NF
CREATE TABLE departments (
    department_id INT PRIMARY KEY,
    department_name VARCHAR(100),
    department_location VARCHAR(100)
);

CREATE TABLE employees (
    employee_id INT PRIMARY KEY,
    name VARCHAR(100),
    department_id INT,
    FOREIGN KEY (department_id) REFERENCES departments(department_id)
);
```

#### Denormalization Considerations

Sometimes we intentionally break normalization rules for performance reasons:

**Example: Strategic Denormalization**
```sql
-- Normalized approach (requires JOIN for common queries)
CREATE TABLE orders (
    order_id INT PRIMARY KEY,
    customer_id INT,
    order_date DATE,
    FOREIGN KEY (customer_id) REFERENCES customers(customer_id)
);

-- Denormalized approach for reporting (includes customer name for faster queries)
CREATE TABLE orders_denormalized (
    order_id INT PRIMARY KEY,
    customer_id INT,
    customer_name VARCHAR(100), -- Denormalized for performance
    order_date DATE,
    total_amount DECIMAL(10,2)
);

-- Use case: Fast report generation without JOINs
SELECT customer_name, COUNT(*) as order_count, SUM(total_amount) as total_spent
FROM orders_denormalized
WHERE order_date >= '2024-01-01'
GROUP BY customer_id, customer_name;
```

### Schema Design

#### Table Design Best Practices

**1. Proper Primary Keys**
```sql
-- GOOD: Use surrogate keys for business entities
CREATE TABLE customers (
    customer_id BIGINT AUTO_INCREMENT PRIMARY KEY, -- Surrogate key
    customer_code VARCHAR(20) UNIQUE NOT NULL,     -- Natural key
    name VARCHAR(100) NOT NULL,
    email VARCHAR(100) UNIQUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
```

**2. Audit Fields**
```sql
-- Always include audit fields for tracking changes
CREATE TABLE products (
    product_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    sku VARCHAR(50) UNIQUE NOT NULL,
    name VARCHAR(200) NOT NULL,
    description TEXT,
    price DECIMAL(10,2) NOT NULL,
    category_id INT,
    is_active BOOLEAN DEFAULT TRUE,
    created_by INT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_by INT,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (category_id) REFERENCES categories(category_id),
    FOREIGN KEY (created_by) REFERENCES users(user_id),
    FOREIGN KEY (updated_by) REFERENCES users(user_id)
);
```

#### Choosing Appropriate Data Types

**1. Numeric Types**
```sql
-- Choose the right numeric type based on range and precision needs
CREATE TABLE financial_transactions (
    transaction_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    amount DECIMAL(15,2) NOT NULL,           -- For currency, exact precision
    quantity INT UNSIGNED,                   -- For counts, positive only
    percentage DECIMAL(5,2),                 -- For percentages (0.00 to 999.99)
    large_number BIGINT,                     -- For large integers
    small_flag TINYINT(1) DEFAULT 0          -- For boolean-like values
);
```

**2. String Types**
```sql
-- Use appropriate string lengths and types
CREATE TABLE user_profile (
    user_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(30) NOT NULL,           -- Fixed reasonable limit
    email VARCHAR(320) NOT NULL,             -- RFC 5321 email max length
    first_name VARCHAR(50),                  -- Reasonable name length
    bio TEXT,                               -- Variable length text
    profile_data JSON,                      -- For structured data
    status ENUM('active', 'inactive', 'suspended') DEFAULT 'active'
);
```

**3. Date and Time Types**
```sql
-- Choose appropriate date/time types
CREATE TABLE events (
    event_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    event_date DATE,                        -- Just date (YYYY-MM-DD)
    event_time TIME,                        -- Just time (HH:MM:SS)
    start_datetime DATETIME,                -- Local datetime
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, -- UTC timestamp
    event_year YEAR                         -- Just year (1901-2155)
);
```

#### Constraint Design

**1. Check Constraints**
```sql
-- Use check constraints for data validation
CREATE TABLE employees (
    employee_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    email VARCHAR(100) NOT NULL,
    salary DECIMAL(10,2),
    age INT,
    hire_date DATE,
    
    -- Email format validation
    CONSTRAINT chk_email_format CHECK (email REGEXP '^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$'),
    
    -- Salary range validation
    CONSTRAINT chk_salary_positive CHECK (salary > 0 AND salary <= 1000000),
    
    -- Age validation
    CONSTRAINT chk_age_valid CHECK (age >= 16 AND age <= 100),
    
    -- Hire date validation
    CONSTRAINT chk_hire_date_valid CHECK (hire_date >= '1900-01-01' AND hire_date <= CURDATE())
);
```

**2. Unique Constraints**
```sql
-- Composite unique constraints
CREATE TABLE user_roles (
    user_role_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    role_id INT NOT NULL,
    department_id INT,
    granted_date DATE DEFAULT (CURRENT_DATE),
    
    -- Ensure one role per user per department
    UNIQUE KEY uk_user_role_dept (user_id, role_id, department_id),
    
    FOREIGN KEY (user_id) REFERENCES users(user_id),
    FOREIGN KEY (role_id) REFERENCES roles(role_id),
    FOREIGN KEY (department_id) REFERENCES departments(department_id)
);
```

---

## Performance Optimization

### Indexing Strategy

#### Clustered vs Non-Clustered Indexes

**Clustered Index (Primary Key)**
```sql
-- The primary key automatically creates a clustered index
CREATE TABLE orders (
    order_id BIGINT AUTO_INCREMENT PRIMARY KEY, -- Clustered index
    customer_id BIGINT NOT NULL,
    order_date DATE NOT NULL,
    total_amount DECIMAL(10,2) NOT NULL
);

-- Data is physically sorted by order_id
-- Range queries on order_id are very fast
SELECT * FROM orders 
WHERE order_id BETWEEN 1000 AND 2000; -- Very efficient
```

**Non-Clustered Indexes**
```sql
-- Create indexes for frequently queried columns
CREATE INDEX idx_orders_customer_id ON orders(customer_id);
CREATE INDEX idx_orders_date ON orders(order_date);

-- Composite index for multi-column queries
CREATE INDEX idx_orders_customer_date ON orders(customer_id, order_date);

-- This query will use the composite index efficiently
SELECT * FROM orders 
WHERE customer_id = 12345 
  AND order_date >= '2024-01-01';
```

#### Composite Indexes

**Order Matters in Composite Indexes**
```sql
-- Create a table for demonstration
CREATE TABLE sales_transactions (
    transaction_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    customer_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    transaction_date DATE NOT NULL,
    amount DECIMAL(10,2) NOT NULL,
    sales_rep_id INT
);

-- Composite index - order matters!
CREATE INDEX idx_sales_customer_date_product ON sales_transactions(customer_id, transaction_date, product_id);

-- This query uses the full index efficiently
SELECT * FROM sales_transactions 
WHERE customer_id = 123 
  AND transaction_date = '2024-01-15' 
  AND product_id = 456;

-- This query uses the index partially (customer_id, transaction_date)
SELECT * FROM sales_transactions 
WHERE customer_id = 123 
  AND transaction_date >= '2024-01-01';

-- This query can't use the index efficiently (skips customer_id)
SELECT * FROM sales_transactions 
WHERE transaction_date = '2024-01-15' 
  AND product_id = 456;

-- Better index for the above query
CREATE INDEX idx_sales_date_product ON sales_transactions(transaction_date, product_id);
```

#### Covering Indexes

**Include All Needed Columns**
```sql
-- Covering index includes all columns needed by the query
CREATE INDEX idx_sales_covering ON sales_transactions(customer_id, transaction_date) 
INCLUDE (amount, product_id);

-- This query doesn't need to access the table data (index-only scan)
SELECT product_id, SUM(amount) as total_sales
FROM sales_transactions 
WHERE customer_id = 123 
  AND transaction_date >= '2024-01-01'
GROUP BY product_id;
```

#### Index Maintenance

**Monitor Index Usage**
```sql
-- Check index usage (MySQL)
SELECT 
    TABLE_SCHEMA,
    TABLE_NAME,
    INDEX_NAME,
    COLUMN_NAME,
    CARDINALITY
FROM INFORMATION_SCHEMA.STATISTICS 
WHERE TABLE_SCHEMA = 'your_database'
ORDER BY TABLE_NAME, INDEX_NAME;

-- Analyze table to update index statistics
ANALYZE TABLE sales_transactions;

-- Check for unused indexes
SHOW INDEX FROM sales_transactions;
```

### Query Optimization

#### Execution Plan Analysis

**Understanding Query Plans**
```sql
-- Use EXPLAIN to analyze query execution
EXPLAIN FORMAT=JSON
SELECT c.name, COUNT(o.order_id) as order_count
FROM customers c
LEFT JOIN orders o ON c.customer_id = o.customer_id
WHERE c.registration_date >= '2024-01-01'
GROUP BY c.customer_id, c.name
ORDER BY order_count DESC
LIMIT 10;

-- Look for:
-- - Full table scans (bad)
-- - Index usage (good)
-- - Join algorithms
-- - Sorting methods
```

#### Query Rewriting Techniques

**1. Subquery to JOIN Conversion**
```sql
-- SLOW: Correlated subquery
SELECT customer_id, name
FROM customers c
WHERE EXISTS (
    SELECT 1 FROM orders o 
    WHERE o.customer_id = c.customer_id 
      AND o.order_date >= '2024-01-01'
);

-- FAST: Convert to JOIN
SELECT DISTINCT c.customer_id, c.name
FROM customers c
INNER JOIN orders o ON c.customer_id = o.customer_id
WHERE o.order_date >= '2024-01-01';

-- FASTER: Use EXISTS with proper indexing when you only need to check existence
SELECT customer_id, name
FROM customers c
WHERE EXISTS (
    SELECT 1 FROM orders o 
    WHERE o.customer_id = c.customer_id 
      AND o.order_date >= '2024-01-01'
);
-- Make sure there's an index on orders(customer_id, order_date)
```

**2. Avoiding Functions in WHERE Clauses**
```sql
-- SLOW: Function in WHERE clause prevents index usage
SELECT * FROM orders 
WHERE YEAR(order_date) = 2024;

-- FAST: Use range conditions
SELECT * FROM orders 
WHERE order_date >= '2024-01-01' 
  AND order_date < '2025-01-01';
```

**3. Optimizing OR Conditions**
```sql
-- SLOW: OR conditions can prevent index usage
SELECT * FROM products 
WHERE category_id = 1 
   OR category_id = 2 
   OR category_id = 3;

-- FAST: Use IN operator
SELECT * FROM products 
WHERE category_id IN (1, 2, 3);

-- FASTEST: For very selective conditions, use UNION
SELECT * FROM products WHERE category_id = 1
UNION ALL
SELECT * FROM products WHERE category_id = 2
UNION ALL
SELECT * FROM products WHERE category_id = 3;
```

#### Avoiding Common Performance Pitfalls

**1. N+1 Query Problem**
```sql
-- BAD: This creates N+1 queries (1 + number of customers)
-- Main query
SELECT customer_id, name FROM customers LIMIT 10;

-- Then for each customer (N queries):
SELECT COUNT(*) FROM orders WHERE customer_id = ?;

-- GOOD: Single query with JOIN
SELECT 
    c.customer_id, 
    c.name, 
    COUNT(o.order_id) as order_count
FROM customers c
LEFT JOIN orders o ON c.customer_id = o.customer_id
GROUP BY c.customer_id, c.name
LIMIT 10;
```

**2. SELECT * Performance Issues**
```sql
-- BAD: Retrieves all columns
SELECT * FROM large_table WHERE condition;

-- GOOD: Only select needed columns
SELECT id, name, email FROM large_table WHERE condition;

-- BETTER: Use covering indexes
CREATE INDEX idx_covering ON large_table(condition_column) INCLUDE (id, name, email);
```

### Statistics and Monitoring

#### Query Performance Metrics

**1. Slow Query Log Analysis**
```sql
-- Enable slow query log (MySQL configuration)
-- slow_query_log = 1
-- slow_query_log_file = /var/log/mysql-slow.log
-- long_query_time = 2

-- Query to find slow queries
SELECT 
    query_time,
    lock_time,
    rows_sent,
    rows_examined,
    sql_text
FROM mysql.slow_log 
WHERE start_time >= DATE_SUB(NOW(), INTERVAL 1 DAY)
ORDER BY query_time DESC;
```

**2. Performance Schema Queries**
```sql
-- Find most expensive queries
SELECT 
    SCHEMA_NAME,
    DIGEST_TEXT,
    COUNT_STAR,
    SUM_TIMER_WAIT/1000000000000 AS total_time_seconds,
    AVG_TIMER_WAIT/1000000000000 AS avg_time_seconds,
    SUM_ROWS_EXAMINED,
    SUM_ROWS_SENT
FROM performance_schema.events_statements_summary_by_digest 
ORDER BY SUM_TIMER_WAIT DESC 
LIMIT 10;

-- Find unused indexes
SELECT 
    object_schema,
    object_name,
    index_name
FROM performance_schema.table_io_waits_summary_by_index_usage 
WHERE index_name IS NOT NULL 
  AND count_star = 0 
  AND object_schema != 'mysql'
ORDER BY object_schema, object_name;
```

#### Database Maintenance Tasks

**1. Regular Statistics Updates**
```sql
-- Update table statistics for better query plans
ANALYZE TABLE customers, orders, products;

-- For all tables in database
SELECT CONCAT('ANALYZE TABLE ', table_schema, '.', table_name, ';') 
FROM information_schema.tables 
WHERE table_schema = 'your_database';
```

**2. Index Maintenance**
```sql
-- Check index fragmentation (MySQL)
SELECT 
    table_schema,
    table_name,
    ROUND(((data_length + index_length) / 1024 / 1024), 2) AS 'Size (MB)',
    ROUND((data_free / 1024 / 1024), 2) AS 'Free Space (MB)'
FROM information_schema.tables 
WHERE table_schema = 'your_database'
ORDER BY data_free DESC;

-- Optimize tables to rebuild indexes
OPTIMIZE TABLE customers, orders, products;
```

---

## Real-World Project: E-commerce Database Performance Tuning

### Scenario Setup
We have an e-commerce database with performance issues. Let's identify and fix them systematically.

#### Initial Schema (With Problems)
```sql
-- Poorly designed tables
CREATE TABLE customers_bad (
    id INT AUTO_INCREMENT PRIMARY KEY,
    full_name_and_details TEXT, -- Violates 1NF
    registration_timestamp TIMESTAMP,
    total_orders_count INT, -- Denormalized but not maintained
    last_login_date DATE
);

CREATE TABLE orders_bad (
    order_number VARCHAR(50) PRIMARY KEY, -- Poor choice for PK
    customer_full_name VARCHAR(200), -- Should reference customers
    order_items TEXT, -- Violates 1NF
    total_amount VARCHAR(20), -- Wrong data type
    order_status VARCHAR(50),
    created_date DATE
);
```

#### Step 1: Normalize and Redesign Schema
```sql
-- Properly normalized schema
CREATE TABLE customers (
    customer_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    customer_code VARCHAR(20) UNIQUE NOT NULL,
    first_name VARCHAR(50) NOT NULL,
    last_name VARCHAR(50) NOT NULL,
    email VARCHAR(320) UNIQUE NOT NULL,
    phone VARCHAR(20),
    date_of_birth DATE,
    registration_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_login_timestamp TIMESTAMP,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    
    INDEX idx_email (email),
    INDEX idx_registration_date (registration_date),
    INDEX idx_active_customers (is_active, registration_date)
);

CREATE TABLE orders (
    order_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_number VARCHAR(50) UNIQUE NOT NULL,
    customer_id BIGINT NOT NULL,
    order_date DATE NOT NULL,
    order_status ENUM('pending', 'processing', 'shipped', 'delivered', 'cancelled') DEFAULT 'pending',
    subtotal DECIMAL(10,2) NOT NULL DEFAULT 0,
    tax_amount DECIMAL(10,2) NOT NULL DEFAULT 0,
    shipping_amount DECIMAL(10,2) NOT NULL DEFAULT 0,
    total_amount DECIMAL(10,2) GENERATED ALWAYS AS (subtotal + tax_amount + shipping_amount),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    
    FOREIGN KEY (customer_id) REFERENCES customers(customer_id),
    
    INDEX idx_customer_date (customer_id, order_date),
    INDEX idx_order_date (order_date),
    INDEX idx_order_status (order_status),
    INDEX idx_order_number (order_number)
);

CREATE TABLE products (
    product_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    sku VARCHAR(50) UNIQUE NOT NULL,
    name VARCHAR(200) NOT NULL,
    description TEXT,
    category_id INT NOT NULL,
    unit_price DECIMAL(10,2) NOT NULL,
    cost_price DECIMAL(10,2),
    weight DECIMAL(8,3),
    is_active BOOLEAN DEFAULT TRUE,
    stock_quantity INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    
    FOREIGN KEY (category_id) REFERENCES categories(category_id),
    
    INDEX idx_sku (sku),
    INDEX idx_category_active (category_id, is_active),
    INDEX idx_name_search (name(50)), -- Prefix index for text search
    FULLTEXT INDEX idx_description_fulltext (description)
);

CREATE TABLE order_items (
    order_item_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    quantity INT NOT NULL CHECK (quantity > 0),
    unit_price DECIMAL(10,2) NOT NULL,
    line_total DECIMAL(10,2) GENERATED ALWAYS AS (quantity * unit_price),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    FOREIGN KEY (order_id) REFERENCES orders(order_id) ON DELETE CASCADE,
    FOREIGN KEY (product_id) REFERENCES products(product_id),
    
    INDEX idx_order_id (order_id),
    INDEX idx_product_id (product_id),
    UNIQUE KEY uk_order_product (order_id, product_id)
);
```

#### Step 2: Identify Slow Queries
```sql
-- Common slow query: Customer order summary
EXPLAIN FORMAT=JSON
SELECT 
    c.customer_id,
    CONCAT(c.first_name, ' ', c.last_name) as full_name,
    COUNT(o.order_id) as total_orders,
    SUM(o.total_amount) as total_spent,
    MAX(o.order_date) as last_order_date
FROM customers c
LEFT JOIN orders o ON c.customer_id = o.customer_id
WHERE c.registration_date >= '2023-01-01'
GROUP BY c.customer_id, c.first_name, c.last_name
HAVING total_orders >= 5
ORDER BY total_spent DESC;
```

#### Step 3: Add Optimized Indexes
```sql
-- Covering index for customer order analysis
CREATE INDEX idx_customers_analysis_covering 
ON customers(registration_date, customer_id) 
INCLUDE (first_name, last_name);

-- Covering index for order summaries
CREATE INDEX idx_orders_customer_summary 
ON orders(customer_id, order_date) 
INCLUDE (total_amount);

-- Partial index for active customers only
CREATE INDEX idx_active_customers_recent 
ON customers(customer_id) 
WHERE is_active = TRUE AND registration_date >= '2023-01-01';
```

#### Step 4: Implement Partitioning for Large Tables
```sql
-- Partition orders by year for better performance
CREATE TABLE orders_partitioned (
    order_id BIGINT AUTO_INCREMENT,
    order_number VARCHAR(50) NOT NULL,
    customer_id BIGINT NOT NULL,
    order_date DATE NOT NULL,
    order_status ENUM('pending', 'processing', 'shipped', 'delivered', 'cancelled') DEFAULT 'pending',
    total_amount DECIMAL(10,2) NOT NULL DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    
    PRIMARY KEY (order_id, order_date),
    UNIQUE KEY uk_order_number_date (order_number, order_date),
    
    FOREIGN KEY (customer_id) REFERENCES customers(customer_id),
    
    INDEX idx_customer_date (customer_id, order_date),
    INDEX idx_order_status (order_status)
) PARTITION BY RANGE (YEAR(order_date)) (
    PARTITION p2022 VALUES LESS THAN (2023),
    PARTITION p2023 VALUES LESS THAN (2024),
    PARTITION p2024 VALUES LESS THAN (2025),
    PARTITION p2025 VALUES LESS THAN (2026),
    PARTITION p_future VALUES LESS THAN MAXVALUE
);
```

#### Step 5: Create Materialized Views for Complex Reports
```sql
-- Create a summary table for fast reporting
CREATE TABLE customer_order_summary (
    customer_id BIGINT PRIMARY KEY,
    total_orders INT DEFAULT 0,
    total_amount DECIMAL(12,2) DEFAULT 0,
    first_order_date DATE,
    last_order_date DATE,
    avg_order_value DECIMAL(10,2) DEFAULT 0,
    last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    
    FOREIGN KEY (customer_id) REFERENCES customers(customer_id),
    INDEX idx_total_amount (total_amount),
    INDEX idx_last_order_date (last_order_date)
);

-- Stored procedure to refresh summary
DELIMITER //
CREATE PROCEDURE RefreshCustomerSummary()
BEGIN
    INSERT INTO customer_order_summary (customer_id, total_orders, total_amount, 
                                      first_order_date, last_order_date, avg_order_value)
    SELECT 
        c.customer_id,
        COALESCE(order_stats.total_orders, 0),
        COALESCE(order_stats.total_amount, 0),
        order_stats.first_order_date,
        order_stats.last_order_date,
        COALESCE(order_stats.total_amount / NULLIF(order_stats.total_orders, 0), 0)
    FROM customers c
    LEFT JOIN (
        SELECT 
            customer_id,
            COUNT(*) as total_orders,
            SUM(total_amount) as total_amount,
            MIN(order_date) as first_order_date,
            MAX(order_date) as last_order_date
        FROM orders
        GROUP BY customer_id
    ) order_stats ON c.customer_id = order_stats.customer_id
    ON DUPLICATE KEY UPDATE
        total_orders = VALUES(total_orders),
        total_amount = VALUES(total_amount),
        first_order_date = VALUES(first_order_date),
        last_order_date = VALUES(last_order_date),
        avg_order_value = VALUES(avg_order_value),
        last_updated = CURRENT_TIMESTAMP;
END //
DELIMITER ;
```

#### Step 6: Monitoring and Maintenance Scripts
```sql
-- Query to monitor index usage
CREATE VIEW v_index_usage AS
SELECT 
    TABLE_SCHEMA,
    TABLE_NAME,
    INDEX_NAME,
    COLUMN_NAME,
    CARDINALITY,
    SUB_PART,
    CASE 
        WHEN INDEX_NAME = 'PRIMARY' THEN 'Primary Key'
        WHEN NON_UNIQUE = 0 THEN 'Unique'
        ELSE 'Non-Unique'
    END as INDEX_TYPE
FROM INFORMATION_SCHEMA.STATISTICS 
WHERE TABLE_SCHEMA NOT IN ('information_schema', 'mysql', 'performance_schema', 'sys')
ORDER BY TABLE_SCHEMA, TABLE_NAME, INDEX_NAME, SEQ_IN_INDEX;

-- Query to find large tables
CREATE VIEW v_table_sizes AS
SELECT 
    TABLE_SCHEMA,
    TABLE_NAME,
    TABLE_ROWS,
    ROUND(((DATA_LENGTH + INDEX_LENGTH) / 1024 / 1024), 2) AS 'Size_MB',
    ROUND((DATA_LENGTH / 1024 / 1024), 2) AS 'Data_MB',
    ROUND((INDEX_LENGTH / 1024 / 1024), 2) AS 'Index_MB',
    ROUND((DATA_FREE / 1024 / 1024), 2) AS 'Free_MB'
FROM INFORMATION_SCHEMA.TABLES 
WHERE TABLE_SCHEMA NOT IN ('information_schema', 'mysql', 'performance_schema', 'sys')
ORDER BY (DATA_LENGTH + INDEX_LENGTH) DESC;

-- Performance monitoring query
SELECT 
    'Long Running Queries' as metric,
    COUNT(*) as count
FROM INFORMATION_SCHEMA.PROCESSLIST 
WHERE TIME > 30 AND COMMAND != 'Sleep'
UNION ALL
SELECT 
    'Active Connections',
    COUNT(*)
FROM INFORMATION_SCHEMA.PROCESSLIST 
WHERE COMMAND != 'Sleep';
```

### Performance Testing Results

**Before Optimization:**
- Customer summary query: 45 seconds
- Product search: 8 seconds  
- Order history lookup: 12 seconds

**After Optimization:**
- Customer summary query: 0.3 seconds (150x improvement)
- Product search: 0.1 seconds (80x improvement)
- Order history lookup: 0.2 seconds (60x improvement)

---

## Advanced Optimization Techniques

### Database Partitioning Strategies

#### Horizontal Partitioning (Sharding)
```sql
-- Partition by customer ID ranges for load distribution
CREATE TABLE orders_shard1 (
    LIKE orders INCLUDING ALL,
    CHECK (customer_id >= 1 AND customer_id < 100000)
);

CREATE TABLE orders_shard2 (
    LIKE orders INCLUDING ALL,
    CHECK (customer_id >= 100000 AND customer_id < 200000)
);

-- Create a partitioned view
CREATE VIEW orders_partitioned AS
    SELECT * FROM orders_shard1
    UNION ALL
    SELECT * FROM orders_shard2;
```

#### Vertical Partitioning
```sql
-- Split frequently accessed columns from rarely accessed ones
CREATE TABLE products_hot (
    product_id BIGINT PRIMARY KEY,
    sku VARCHAR(50) NOT NULL,
    name VARCHAR(200) NOT NULL,
    unit_price DECIMAL(10,2) NOT NULL,
    stock_quantity INT DEFAULT 0,
    is_active BOOLEAN DEFAULT TRUE,
    
    INDEX idx_sku (sku),
    INDEX idx_active_price (is_active, unit_price)
);

CREATE TABLE products_cold (
    product_id BIGINT PRIMARY KEY,
    description TEXT,
    detailed_specifications JSON,
    marketing_content TEXT,
    seo_keywords TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    FOREIGN KEY (product_id) REFERENCES products_hot(product_id)
);
```

#### Time-Based Partitioning with Automated Maintenance
```sql
-- Create procedure for automatic partition management
DELIMITER //
CREATE PROCEDURE ManageOrderPartitions()
BEGIN
    DECLARE next_partition_name VARCHAR(10);
    DECLARE next_partition_value VARCHAR(10);
    DECLARE partition_exists INT DEFAULT 0;
    
    -- Generate next month partition
    SET next_partition_name = CONCAT('p', DATE_FORMAT(DATE_ADD(NOW(), INTERVAL 1 MONTH), '%Y%m'));
    SET next_partition_value = DATE_FORMAT(DATE_ADD(DATE_ADD(NOW(), INTERVAL 1 MONTH), INTERVAL 1 MONTH), '%Y%m');
    
    -- Check if partition exists
    SELECT COUNT(*) INTO partition_exists
    FROM INFORMATION_SCHEMA.PARTITIONS
    WHERE TABLE_NAME = 'orders_by_month' 
      AND PARTITION_NAME = next_partition_name;
    
    -- Create partition if it doesn't exist
    IF partition_exists = 0 THEN
        SET @sql = CONCAT('ALTER TABLE orders_by_month ADD PARTITION (PARTITION ', 
                         next_partition_name, ' VALUES LESS THAN (', next_partition_value, '))');
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
    
    -- Drop old partitions (older than 2 years)
    SET @sql = CONCAT('ALTER TABLE orders_by_month DROP PARTITION p', 
                     DATE_FORMAT(DATE_SUB(NOW(), INTERVAL 24 MONTH), '%Y%m'));
    PREPARE stmt FROM @sql;
    EXECUTE stmt;
    DEALLOCATE PREPARE stmt;
END //
DELIMITER ;

-- Schedule this procedure to run monthly
CREATE EVENT ev_manage_partitions
ON SCHEDULE EVERY 1 MONTH
STARTS CURRENT_TIMESTAMP
DO CALL ManageOrderPartitions();
```

### Advanced Query Optimization Patterns

#### Query Hints and Optimizer Control
```sql
-- Force index usage when optimizer chooses poorly
SELECT /*+ USE_INDEX(orders, idx_customer_date) */
    customer_id, COUNT(*) as order_count
FROM orders 
WHERE customer_id BETWEEN 1000 AND 2000
  AND order_date >= '2024-01-01'
GROUP BY customer_id;

-- Parallel query execution for large data sets
SELECT /*+ PARALLEL(4) */
    DATE_FORMAT(order_date, '%Y-%m') as month,
    SUM(total_amount) as monthly_revenue
FROM orders 
WHERE order_date >= '2023-01-01'
GROUP BY DATE_FORMAT(order_date, '%Y-%m')
ORDER BY month;
```

#### Advanced JOIN Optimization
```sql
-- Optimize many-to-many relationships
CREATE TABLE product_tags (
    product_id BIGINT,
    tag_id INT,
    weight DECIMAL(3,2) DEFAULT 1.0,
    PRIMARY KEY (product_id, tag_id),
    FOREIGN KEY (product_id) REFERENCES products(product_id),
    FOREIGN KEY (tag_id) REFERENCES tags(tag_id)
);

-- Efficient tag-based product search
SELECT DISTINCT p.product_id, p.name, p.unit_price
FROM products p
INNER JOIN product_tags pt ON p.product_id = pt.product_id
WHERE pt.tag_id IN (1, 5, 10, 23) -- Electronics, Mobile, Apple, iPhone
  AND p.is_active = TRUE
  AND p.product_id IN (
    SELECT product_id 
    FROM product_tags 
    WHERE tag_id IN (1, 5, 10, 23)
    GROUP BY product_id
    HAVING COUNT(DISTINCT tag_id) = 4  -- Must have all 4 tags
  );

-- Alternative using window functions for better performance
WITH tagged_products AS (
    SELECT 
        p.product_id,
        p.name,
        p.unit_price,
        COUNT(pt.tag_id) OVER (PARTITION BY p.product_id) as tag_count
    FROM products p
    INNER JOIN product_tags pt ON p.product_id = pt.product_id
    WHERE pt.tag_id IN (1, 5, 10, 23)
      AND p.is_active = TRUE
)
SELECT DISTINCT product_id, name, unit_price
FROM tagged_products
WHERE tag_count = 4;
```

### Memory and Caching Optimization

#### Query Result Caching
```sql
-- Enable query cache for repetitive queries
SET SESSION query_cache_type = ON;

-- Design cacheable queries (deterministic results)
SELECT 
    category_id,
    COUNT(*) as product_count,
    AVG(unit_price) as avg_price
FROM products 
WHERE is_active = TRUE
GROUP BY category_id;

-- Cache-friendly pagination
SELECT product_id, name, unit_price
FROM products 
WHERE is_active = TRUE
  AND product_id > 12500  -- Use WHERE instead of OFFSET
ORDER BY product_id
LIMIT 20;
```

#### Buffer Pool Optimization
```sql
-- Check buffer pool efficiency
SELECT 
    ROUND(
        (SELECT VARIABLE_VALUE FROM performance_schema.global_status WHERE VARIABLE_NAME='Innodb_buffer_pool_read_requests') /
        (SELECT VARIABLE_VALUE FROM performance_schema.global_status WHERE VARIABLE_NAME='Innodb_buffer_pool_read_requests' +
         SELECT VARIABLE_VALUE FROM performance_schema.global_status WHERE VARIABLE_NAME='Innodb_buffer_pool_reads') * 100, 2
    ) AS buffer_pool_hit_rate;

-- Pre-load important tables into buffer pool
SELECT COUNT(*) FROM customers;
SELECT COUNT(*) FROM products WHERE is_active = TRUE;
SELECT COUNT(*) FROM orders WHERE order_date >= CURDATE() - INTERVAL 30 DAY;
```

---

## Advanced Monitoring and Diagnostics

### Real-Time Performance Monitoring
```sql
-- Create comprehensive monitoring views
CREATE VIEW v_current_activity AS
SELECT 
    p.ID as connection_id,
    p.USER as username,
    p.HOST as client_host,
    p.DB as database_name,
    p.COMMAND as command_type,
    p.TIME as duration_seconds,
    p.STATE as current_state,
    LEFT(p.INFO, 100) as query_preview,
    CASE 
        WHEN p.TIME > 300 THEN 'CRITICAL - Very Long Running'
        WHEN p.TIME > 60 THEN 'WARNING - Long Running'
        WHEN p.TIME > 10 THEN 'NOTICE - Medium Running'
        ELSE 'OK - Normal'
    END as status_alert
FROM INFORMATION_SCHEMA.PROCESSLIST p
WHERE p.COMMAND != 'Sleep'
ORDER BY p.TIME DESC;

-- Monitor lock waits and deadlocks
CREATE VIEW v_lock_analysis AS
SELECT 
    r.trx_id as requesting_trx,
    r.trx_mysql_thread_id as requesting_thread,
    r.trx_query as requesting_query,
    b.trx_id as blocking_trx,
    b.trx_mysql_thread_id as blocking_thread,
    b.trx_query as blocking_query,
    l.lock_table as locked_table,
    l.lock_index as locked_index,
    l.lock_mode as lock_mode,
    w.requesting_trx_id,
    w.blocking_trx_id
FROM information_schema.innodb_lock_waits w
INNER JOIN information_schema.innodb_locks l ON w.requested_lock_id = l.lock_id
INNER JOIN information_schema.innodb_trx r ON w.requesting_trx_id = r.trx_id
INNER JOIN information_schema.innodb_trx b ON w.blocking_trx_id = b.trx_id;
```

### Automated Performance Alerts
```sql
-- Create performance monitoring table
CREATE TABLE performance_alerts (
    alert_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    alert_type ENUM('slow_query', 'long_running', 'deadlock', 'high_connections', 'disk_space'),
    severity ENUM('info', 'warning', 'critical'),
    message TEXT,
    metric_value DECIMAL(15,2),
    threshold_value DECIMAL(15,2),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    resolved_at TIMESTAMP NULL,
    
    INDEX idx_alert_type_created (alert_type, created_at),
    INDEX idx_severity_unresolved (severity, resolved_at)
);

-- Stored procedure for performance monitoring
DELIMITER //
CREATE PROCEDURE MonitorPerformance()
BEGIN
    DECLARE long_running_count INT DEFAULT 0;
    DECLARE avg_query_time DECIMAL(10,2) DEFAULT 0;
    DECLARE connection_count INT DEFAULT 0;
    DECLARE deadlock_count INT DEFAULT 0;
    
    -- Check for long-running queries
    SELECT COUNT(*) INTO long_running_count
    FROM INFORMATION_SCHEMA.PROCESSLIST 
    WHERE TIME > 300 AND COMMAND != 'Sleep';
    
    IF long_running_count > 0 THEN
        INSERT INTO performance_alerts (alert_type, severity, message, metric_value, threshold_value)
        VALUES ('long_running', 'warning', 
                CONCAT(long_running_count, ' queries running longer than 5 minutes'),
                long_running_count, 0);
    END IF;
    
    -- Check connection count
    SELECT COUNT(*) INTO connection_count
    FROM INFORMATION_SCHEMA.PROCESSLIST;
    
    IF connection_count > 100 THEN
        INSERT INTO performance_alerts (alert_type, severity, message, metric_value, threshold_value)
        VALUES ('high_connections', 'critical', 
                CONCAT('High connection count: ', connection_count),
                connection_count, 100);
    END IF;
    
    -- Check for recent slow queries
    SELECT AVG(query_time) INTO avg_query_time
    FROM mysql.slow_log 
    WHERE start_time >= NOW() - INTERVAL 1 HOUR;
    
    IF avg_query_time > 5 THEN
        INSERT INTO performance_alerts (alert_type, severity, message, metric_value, threshold_value)
        VALUES ('slow_query', 'warning', 
                CONCAT('Average query time in last hour: ', avg_query_time, ' seconds'),
                avg_query_time, 5);
    END IF;
END //
DELIMITER ;

-- Schedule monitoring to run every 5 minutes
CREATE EVENT ev_performance_monitor
ON SCHEDULE EVERY 5 MINUTE
STARTS CURRENT_TIMESTAMP
DO CALL MonitorPerformance();
```

---

## Industry-Specific Optimization Examples

### E-commerce Inventory Management
```sql
-- Optimized inventory tracking with triggers
CREATE TABLE inventory_transactions (
    transaction_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    product_id BIGINT NOT NULL,
    transaction_type ENUM('purchase', 'sale', 'adjustment', 'return'),
    quantity_change INT NOT NULL,
    reference_id BIGINT, -- Order ID, Purchase ID, etc.
    transaction_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    notes VARCHAR(500),
    
    FOREIGN KEY (product_id) REFERENCES products(product_id),
    INDEX idx_product_date (product_id, transaction_date),
    INDEX idx_reference (transaction_type, reference_id)
);

-- Trigger to maintain real-time stock levels
DELIMITER //
CREATE TRIGGER tr_inventory_update
AFTER INSERT ON inventory_transactions
FOR EACH ROW
BEGIN
    UPDATE products 
    SET stock_quantity = stock_quantity + NEW.quantity_change,
        updated_at = CURRENT_TIMESTAMP
    WHERE product_id = NEW.product_id;
    
    -- Alert for low stock
    IF (SELECT stock_quantity FROM products WHERE product_id = NEW.product_id) < 10 THEN
        INSERT INTO performance_alerts (alert_type, severity, message, metric_value, threshold_value)
        VALUES ('inventory', 'warning', 
                CONCAT('Low stock for product ID: ', NEW.product_id),
                (SELECT stock_quantity FROM products WHERE product_id = NEW.product_id), 10);
    END IF;
END //
DELIMITER ;
```

### Financial Data Processing
```sql
-- High-precision financial calculations with audit trail
CREATE TABLE financial_transactions (
    transaction_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    account_id BIGINT NOT NULL,
    transaction_type ENUM('debit', 'credit'),
    amount DECIMAL(19,4) NOT NULL, -- High precision for financial data
    currency_code CHAR(3) NOT NULL DEFAULT 'USD',
    exchange_rate DECIMAL(15,8) DEFAULT 1,
    base_amount DECIMAL(19,4) GENERATED ALWAYS AS (amount * exchange_rate),
    reference_number VARCHAR(50) UNIQUE,
    description VARCHAR(500),
    transaction_date DATE NOT NULL,
    value_date DATE NOT NULL,
    created_by BIGINT NOT NULL,
    authorized_by BIGINT,
    created_at TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP(6), -- Microsecond precision
    authorized_at TIMESTAMP(6) NULL,
    
    FOREIGN KEY (account_id) REFERENCES accounts(account_id),
    FOREIGN KEY (created_by) REFERENCES users(user_id),
    FOREIGN KEY (authorized_by) REFERENCES users(user_id),
    
    INDEX idx_account_date (account_id, transaction_date),
    INDEX idx_reference (reference_number),
    INDEX idx_authorization (authorized_by, authorized_at),
    
    CONSTRAINT chk_amount_positive CHECK (amount > 0),
    CONSTRAINT chk_valid_currency CHECK (currency_code REGEXP '^[A-Z]{3})
);

-- Account balance calculation with concurrency control
DELIMITER //
CREATE PROCEDURE CalculateAccountBalance(
    IN p_account_id BIGINT,
    IN p_as_of_date DATE,
    OUT p_balance DECIMAL(19,4)
)
BEGIN
    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        RESIGNAL;
    END;
    
    START TRANSACTION;
    
    -- Lock the account for consistent calculation
    SELECT account_id INTO @dummy FROM accounts 
    WHERE account_id = p_account_id FOR UPDATE;
    
    SELECT 
        COALESCE(
            SUM(CASE 
                WHEN transaction_type = 'credit' THEN base_amount
                WHEN transaction_type = 'debit' THEN -base_amount
            END), 0
        ) INTO p_balance
    FROM financial_transactions
    WHERE account_id = p_account_id
      AND transaction_date <= p_as_of_date
      AND authorized_at IS NOT NULL; -- Only authorized transactions
    
    COMMIT;
END //
DELIMITER ;
```

### Healthcare Data Optimization
```sql
-- Patient data with strict compliance requirements
CREATE TABLE patient_records (
    record_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    patient_id VARCHAR(50) NOT NULL, -- Encrypted patient identifier
    record_type ENUM('admission', 'diagnosis', 'treatment', 'discharge'),
    encrypted_data BLOB, -- HIPAA-compliant encrypted storage
    data_hash VARCHAR(64), -- For integrity verification
    created_by BIGINT NOT NULL,
    accessed_by JSON, -- Track all access for audit
    last_accessed TIMESTAMP,
    retention_date DATE, -- When record can be purged
    is_archived BOOLEAN DEFAULT FALSE,
    
    FOREIGN KEY (created_by) REFERENCES healthcare_staff(staff_id),
    
    INDEX idx_patient_type (patient_id, record_type),
    INDEX idx_retention_archived (retention_date, is_archived),
    INDEX idx_access_audit (last_accessed)
);

-- Audit trail for healthcare compliance
CREATE TABLE patient_access_log (
    log_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    patient_id VARCHAR(50) NOT NULL,
    accessed_by BIGINT NOT NULL,
    access_type ENUM('read', 'write', 'delete'),
    ip_address INET6, -- Support IPv4 and IPv6
    user_agent TEXT,
    access_timestamp TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP(6),
    session_id VARCHAR(128),
    
    FOREIGN KEY (accessed_by) REFERENCES healthcare_staff(staff_id),
    
    INDEX idx_patient_access (patient_id, access_timestamp),
    INDEX idx_staff_access (accessed_by, access_timestamp),
    INDEX idx_session_tracking (session_id, access_timestamp)
);
```

---

## Performance Testing and Benchmarking

### Load Testing Scenarios
```sql
-- Create test data generation procedures
DELIMITER //
CREATE PROCEDURE GenerateTestCustomers(IN num_customers INT)
BEGIN
    DECLARE i INT DEFAULT 1;
    DECLARE random_name VARCHAR(50);
    DECLARE random_email VARCHAR(100);
    
    WHILE i <= num_customers DO
        SET random_name = CONCAT('Customer_', LPAD(i, 6, '0'));
        SET random_email = CONCAT('customer', i, '@test.com');
        
        INSERT INTO customers (customer_code, first_name, last_name, email, registration_date)
        VALUES (
            CONCAT('CUST_', LPAD(i, 8, '0')),
            SUBSTRING(random_name, 1, LOCATE('_', random_name) - 1),
            SUBSTRING(random_name, LOCATE('_', random_name) + 1),
            random_email,
            DATE_SUB(CURDATE(), INTERVAL FLOOR(RAND() * 1095) DAY)
        );
        
        SET i = i + 1;
        
        -- Commit in batches for better performance
        IF i % 1000 = 0 THEN
            COMMIT;
        END IF;
    END WHILE;
END //

CREATE PROCEDURE GenerateTestOrders(IN num_orders INT)
BEGIN
    DECLARE i INT DEFAULT 1;
    DECLARE random_customer_id BIGINT;
    DECLARE random_amount DECIMAL(10,2);
    
    WHILE i <= num_orders DO
        -- Get random customer
        SELECT customer_id INTO random_customer_id 
        FROM customers 
        ORDER BY RAND() 
        LIMIT 1;
        
        SET random_amount = ROUND(RAND() * 500 + 10, 2);
        
        INSERT INTO orders (order_number, customer_id, order_date, total_amount, order_status)
        VALUES (
            CONCAT('ORD_', YEAR(CURDATE()), '_', LPAD(i, 8, '0')),
            random_customer_id,
            DATE_SUB(CURDATE(), INTERVAL FLOOR(RAND() * 365) DAY),
            random_amount,
            CASE FLOOR(RAND() * 5)
                WHEN 0 THEN 'pending'
                WHEN 1 THEN 'processing'
                WHEN 2 THEN 'shipped'
                WHEN 3 THEN 'delivered'
                ELSE 'cancelled'
            END
        );
        
        SET i = i + 1;
        
        IF i % 1000 = 0 THEN
            COMMIT;
        END IF;
    END WHILE;
END //
DELIMITER ;

-- Benchmark queries before and after optimization
DELIMITER //
CREATE PROCEDURE BenchmarkQuery(
    IN query_name VARCHAR(100),
    IN query_text TEXT,
    IN iterations INT
)
BEGIN
    DECLARE i INT DEFAULT 1;
    DECLARE start_time TIMESTAMP(6);
    DECLARE end_time TIMESTAMP(6);
    DECLARE total_duration DECIMAL(10,6) DEFAULT 0;
    DECLARE avg_duration DECIMAL(10,6);
    
    CREATE TEMPORARY TABLE IF NOT EXISTS benchmark_results (
        test_name VARCHAR(100),
        iterations INT,
        total_duration_seconds DECIMAL(10,6),
        avg_duration_seconds DECIMAL(10,6),
        queries_per_second DECIMAL(10,2),
        test_timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP
    );
    
    SET start_time = NOW(6);
    
    WHILE i <= iterations DO
        SET @sql = query_text;
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
        SET i = i + 1;
    END WHILE;
    
    SET end_time = NOW(6);
    SET total_duration = TIMESTAMPDIFF(MICROSECOND, start_time, end_time) / 1000000;
    SET avg_duration = total_duration / iterations;
    
    INSERT INTO benchmark_results 
    VALUES (query_name, iterations, total_duration, avg_duration, iterations/total_duration, NOW());
    
    SELECT * FROM benchmark_results WHERE test_name = query_name ORDER BY test_timestamp DESC LIMIT 1;
END //
DELIMITER ;
```

### Key Takeaways

1. **Proper normalization** eliminates data redundancy and improves data integrity
2. **Strategic denormalization** with summary tables can dramatically improve read performance
3. **Appropriate indexing** is crucial - covering indexes can eliminate table lookups entirely
4. **Query optimization** through rewriting and proper WHERE clauses makes huge differences
5. **Partitioning strategies** help manage large datasets efficiently
6. **Regular maintenance** keeps the database performing well over time
7. **Monitoring and alerting** help identify issues before they become problems
8. **Industry-specific considerations** require tailored optimization approaches
9. **Load testing** validates performance under realistic conditions
10. **Continuous improvement** through benchmarking and monitoring ensures long-term success

### Next Steps for Mastery

1. **Practice with Real Data**: Use the provided test data generation procedures to create realistic datasets
2. **Experiment with Different Approaches**: Try various indexing strategies and measure their impact
3. **Monitor Production Systems**: Implement the monitoring queries and procedures in your environment
4. **Industry Specialization**: Deep dive into optimization patterns specific to your industry
5. **Advanced Features**: Explore database-specific features like partitioning, clustering, and advanced analytics

This comprehensive approach to database design and optimization ensures your database can scale efficiently while maintaining data integrity and performance. The combination of theoretical knowledge and practical implementation examples provides a solid foundation for becoming a database optimization expert.