# Complete SQL Mastery Roadmap: From Beginner to Expert

## Phase 1: SQL Foundations (Weeks 1-3)

### Core Concepts to Master

#### 1.1 Database Fundamentals
- **What is a Database?** A structured collection of data stored electronically
- **RDBMS Concepts:** Tables, rows, columns, primary keys, foreign keys
- **SQL Dialects:** MySQL, PostgreSQL, SQL Server, Oracle, SQLite

#### 1.2 Basic SQL Syntax
```sql
-- Basic SELECT statement
SELECT column1, column2 
FROM table_name;

-- WHERE clause for filtering
SELECT customer_name, email 
FROM customers 
WHERE country = 'USA';

-- ORDER BY for sorting
SELECT product_name, price 
FROM products 
ORDER BY price DESC;
```

#### 1.3 Data Types and Constraints
```sql
-- Common data types
CREATE TABLE employees (
    employee_id INT PRIMARY KEY,
    first_name VARCHAR(50) NOT NULL,
    last_name VARCHAR(50) NOT NULL,
    email VARCHAR(100) UNIQUE,
    hire_date DATE,
    salary DECIMAL(10,2),
    is_active BOOLEAN DEFAULT TRUE
);
```

#### 1.4 CRUD Operations
```sql
-- CREATE (INSERT)
INSERT INTO customers (name, email, city) 
VALUES ('John Doe', 'john@email.com', 'New York');

-- READ (SELECT) - covered above

-- UPDATE
UPDATE customers 
SET city = 'Boston' 
WHERE customer_id = 1;

-- DELETE
DELETE FROM customers 
WHERE customer_id = 1;
```

### Real-World Example: E-commerce Store
Imagine you're building a simple online bookstore. You need to track:
- Customers (ID, name, email, address)
- Books (ID, title, author, price, stock)
- Orders (ID, customer_id, order_date, total)

### Optimization Tips for Phase 1
- **Use LIMIT:** Always limit results when testing: `SELECT * FROM large_table LIMIT 10;`
- **Index Primary Keys:** Always define primary keys for faster lookups
- **Use specific columns:** Avoid `SELECT *` in production code

### Phase 1 Project: Library Management System
**Goal:** Build a basic library database with books, members, and borrowing records.

**Tables to Create:**
```sql
-- Books table
CREATE TABLE books (
    book_id INT PRIMARY KEY,
    title VARCHAR(200) NOT NULL,
    author VARCHAR(100) NOT NULL,
    isbn VARCHAR(13) UNIQUE,
    publication_year INT,
    available_copies INT DEFAULT 0
);

-- Members table
CREATE TABLE members (
    member_id INT PRIMARY KEY,
    first_name VARCHAR(50) NOT NULL,
    last_name VARCHAR(50) NOT NULL,
    email VARCHAR(100) UNIQUE,
    phone VARCHAR(15),
    membership_date DATE DEFAULT CURRENT_DATE
);

-- Borrowing records
CREATE TABLE borrowings (
    borrowing_id INT PRIMARY KEY,
    book_id INT,
    member_id INT,
    borrow_date DATE DEFAULT CURRENT_DATE,
    due_date DATE,
    return_date DATE,
    FOREIGN KEY (book_id) REFERENCES books(book_id),
    FOREIGN KEY (member_id) REFERENCES members(member_id)
);
```

**Practice Queries:**
1. Find all available books
2. List members who joined this year
3. Show overdue books
4. Update book availability after borrowing

---

## Phase 2: Intermediate Querying (Weeks 4-6)

### Core Concepts to Master

#### 2.1 Advanced Filtering and Conditions
```sql
-- Multiple conditions
SELECT * FROM products 
WHERE price BETWEEN 10 AND 100 
AND category IN ('Electronics', 'Books')
AND stock_quantity > 0;

-- Pattern matching
SELECT * FROM customers 
WHERE email LIKE '%@gmail.com'
OR phone LIKE '+1%';

-- NULL handling
SELECT * FROM employees 
WHERE commission IS NOT NULL
AND department IS NOT NULL;
```

#### 2.2 Aggregate Functions and GROUP BY
```sql
-- Basic aggregates
SELECT 
    COUNT(*) as total_orders,
    AVG(order_total) as average_order,
    MAX(order_total) as highest_order,
    MIN(order_date) as first_order
FROM orders;

-- GROUP BY with aggregates
SELECT 
    category,
    COUNT(*) as product_count,
    AVG(price) as avg_price,
    SUM(stock_quantity) as total_stock
FROM products 
GROUP BY category
HAVING COUNT(*) > 5;
```

#### 2.3 JOINS - The Heart of SQL
```sql
-- INNER JOIN (most common)
SELECT 
    c.customer_name,
    o.order_date,
    o.total_amount
FROM customers c
INNER JOIN orders o ON c.customer_id = o.customer_id;

-- LEFT JOIN (include all from left table)
SELECT 
    c.customer_name,
    COUNT(o.order_id) as order_count
FROM customers c
LEFT JOIN orders o ON c.customer_id = o.customer_id
GROUP BY c.customer_id, c.customer_name;

-- SELF JOIN (join table to itself)
SELECT 
    e1.employee_name as employee,
    e2.employee_name as manager
FROM employees e1
LEFT JOIN employees e2 ON e1.manager_id = e2.employee_id;
```

### Real-World Example: Sales Analytics
A retail company wants to analyze:
- Which products sell best by category?
- What's the average order value per customer?
- Which customers haven't ordered recently?

```sql
-- Top selling products by category
SELECT 
    p.category,
    p.product_name,
    SUM(oi.quantity) as total_sold,
    SUM(oi.quantity * oi.price) as revenue
FROM products p
INNER JOIN order_items oi ON p.product_id = oi.product_id
INNER JOIN orders o ON oi.order_id = o.order_id
WHERE o.order_date >= DATE_SUB(CURRENT_DATE, INTERVAL 30 DAY)
GROUP BY p.category, p.product_id, p.product_name
ORDER BY revenue DESC;
```

### Optimization Tips for Phase 2
- **Index JOIN columns:** Create indexes on foreign keys
- **Use appropriate JOIN types:** Don't use INNER JOIN when you need LEFT JOIN
- **Filter early:** Apply WHERE conditions before JOINs when possible

### Phase 2 Project: E-commerce Analytics Dashboard
**Goal:** Build a comprehensive e-commerce database with advanced querying capabilities.

**Extended Schema:**
```sql
-- Add to previous tables
CREATE TABLE categories (
    category_id INT PRIMARY KEY,
    category_name VARCHAR(50) NOT NULL,
    parent_category_id INT,
    FOREIGN KEY (parent_category_id) REFERENCES categories(category_id)
);

CREATE TABLE order_items (
    order_item_id INT PRIMARY KEY,
    order_id INT,
    product_id INT,
    quantity INT NOT NULL,
    unit_price DECIMAL(10,2) NOT NULL,
    FOREIGN KEY (order_id) REFERENCES orders(order_id),
    FOREIGN KEY (product_id) REFERENCES products(product_id)
);

CREATE TABLE reviews (
    review_id INT PRIMARY KEY,
    product_id INT,
    customer_id INT,
    rating INT CHECK (rating BETWEEN 1 AND 5),
    review_text TEXT,
    review_date DATE DEFAULT CURRENT_DATE,
    FOREIGN KEY (product_id) REFERENCES products(product_id),
    FOREIGN KEY (customer_id) REFERENCES customers(customer_id)
);
```

**Key Queries to Master:**
1. Customer lifetime value calculation
2. Product performance by category
3. Monthly sales trends
4. Customer segmentation analysis

---

## Phase 3: Advanced SQL Techniques (Weeks 7-10)

### Core Concepts to Master

#### 3.1 Subqueries and Common Table Expressions (CTEs)
```sql
-- Subquery example: Find customers with above-average order values
SELECT customer_name, email
FROM customers 
WHERE customer_id IN (
    SELECT customer_id 
    FROM orders 
    WHERE total_amount > (
        SELECT AVG(total_amount) FROM orders
    )
);

-- CTE example: Recursive hierarchy
WITH RECURSIVE employee_hierarchy AS (
    -- Base case: top-level managers
    SELECT employee_id, name, manager_id, 1 as level
    FROM employees 
    WHERE manager_id IS NULL
    
    UNION ALL
    
    -- Recursive case: employees with managers
    SELECT e.employee_id, e.name, e.manager_id, eh.level + 1
    FROM employees e
    INNER JOIN employee_hierarchy eh ON e.manager_id = eh.employee_id
)
SELECT * FROM employee_hierarchy ORDER BY level, name;
```

#### 3.2 Window Functions
```sql
-- Running totals
SELECT 
    order_date,
    daily_sales,
    SUM(daily_sales) OVER (ORDER BY order_date) as running_total
FROM (
    SELECT 
        order_date,
        SUM(total_amount) as daily_sales
    FROM orders 
    GROUP BY order_date
) daily_summary;

-- Ranking functions
SELECT 
    product_name,
    category,
    price,
    ROW_NUMBER() OVER (PARTITION BY category ORDER BY price DESC) as price_rank,
    DENSE_RANK() OVER (ORDER BY price DESC) as overall_rank
FROM products;

-- Lead/Lag for comparisons
SELECT 
    month,
    revenue,
    LAG(revenue) OVER (ORDER BY month) as prev_month_revenue,
    revenue - LAG(revenue) OVER (ORDER BY month) as month_over_month_growth
FROM monthly_sales;
```

#### 3.3 Advanced JOIN Techniques
```sql
-- Cross JOIN for generating combinations
SELECT 
    p.product_name,
    s.size_name,
    c.color_name
FROM products p
CROSS JOIN sizes s
CROSS JOIN colors c
WHERE p.category = 'Clothing';

-- FULL OUTER JOIN (MySQL doesn't support, use UNION)
SELECT customer_id, 'Has Orders' as status FROM orders
UNION
SELECT customer_id, 'No Orders' as status FROM customers
WHERE customer_id NOT IN (SELECT DISTINCT customer_id FROM orders);
```

### Real-World Example: Business Intelligence
A SaaS company wants to analyze user engagement:

```sql
-- User engagement analysis with window functions
WITH user_activity AS (
    SELECT 
        user_id,
        login_date,
        COUNT(*) as daily_actions,
        LAG(login_date) OVER (PARTITION BY user_id ORDER BY login_date) as prev_login
    FROM user_logs 
    GROUP BY user_id, login_date
),
engagement_metrics AS (
    SELECT 
        user_id,
        login_date,
        daily_actions,
        DATEDIFF(login_date, prev_login) as days_since_last_login,
        AVG(daily_actions) OVER (
            PARTITION BY user_id 
            ORDER BY login_date 
            ROWS BETWEEN 6 PRECEDING AND CURRENT ROW
        ) as avg_weekly_actions
    FROM user_activity
)
SELECT 
    user_id,
    COUNT(*) as total_active_days,
    AVG(daily_actions) as avg_daily_actions,
    MAX(days_since_last_login) as longest_gap
FROM engagement_metrics
GROUP BY user_id
HAVING COUNT(*) >= 10; -- Users active at least 10 days
```

### Optimization Tips for Phase 3
- **CTE vs Subquery:** CTEs are more readable and can be referenced multiple times
- **Window Function Performance:** Use appropriate PARTITION BY to limit calculation scope
- **Subquery Correlation:** Avoid correlated subqueries when possible; use JOINs instead

### Phase 3 Project: Advanced Analytics Platform
**Goal:** Build a comprehensive analytics system for a subscription business.

**Schema Extensions:**
```sql
CREATE TABLE subscriptions (
    subscription_id INT PRIMARY KEY,
    customer_id INT,
    plan_id INT,
    start_date DATE,
    end_date DATE,
    monthly_price DECIMAL(8,2),
    status ENUM('active', 'cancelled', 'paused'),
    FOREIGN KEY (customer_id) REFERENCES customers(customer_id)
);

CREATE TABLE usage_logs (
    log_id INT PRIMARY KEY,
    customer_id INT,
    feature_used VARCHAR(50),
    usage_date DATE,
    usage_count INT DEFAULT 1,
    FOREIGN KEY (customer_id) REFERENCES customers(customer_id)
);
```

**Advanced Queries to Implement:**
1. Customer churn prediction using activity patterns
2. Monthly recurring revenue (MRR) calculations
3. Cohort analysis for user retention
4. Feature adoption rates over time

---

## Phase 4: Database Design and Performance (Weeks 11-14)

### Core Concepts to Master

#### 4.1 Database Normalization
```sql
-- First Normal Form (1NF): Atomic values
-- Bad: phone_numbers = "123-456-7890, 987-654-3210"
-- Good: Separate phone_numbers table

-- Second Normal Form (2NF): No partial dependencies
-- Bad: order_items(order_id, product_id, product_name, quantity)
-- Good: Separate products table for product_name

-- Third Normal Form (3NF): No transitive dependencies
-- Bad: employees(id, department_id, department_name, department_location)
-- Good: Separate departments table
```

#### 4.2 Indexing Strategies
```sql
-- Single column index
CREATE INDEX idx_customer_email ON customers(email);

-- Composite index (order matters!)
CREATE INDEX idx_order_customer_date ON orders(customer_id, order_date);

-- Covering index (includes non-key columns)
CREATE INDEX idx_product_category_covering 
ON products(category_id) 
INCLUDE (product_name, price);

-- Partial index (PostgreSQL)
CREATE INDEX idx_active_customers 
ON customers(email) 
WHERE status = 'active';
```

#### 4.3 Query Execution Plans
```sql
-- Analyze query performance
EXPLAIN ANALYZE 
SELECT c.customer_name, COUNT(o.order_id)
FROM customers c
LEFT JOIN orders o ON c.customer_id = o.customer_id
WHERE c.registration_date >= '2024-01-01'
GROUP BY c.customer_id, c.customer_name;

-- Look for:
-- - Sequential scans (bad for large tables)
-- - Index scans (good)
-- - Nested loops vs Hash joins
-- - Actual vs estimated rows
```

#### 4.4 Advanced Data Manipulation
```sql
-- UPSERT operations (INSERT ... ON DUPLICATE KEY UPDATE)
INSERT INTO product_inventory (product_id, quantity)
VALUES (1, 100)
ON DUPLICATE KEY UPDATE 
quantity = quantity + VALUES(quantity);

-- MERGE statement (SQL Server/Oracle)
MERGE target_table AS target
USING source_table AS source
ON target.id = source.id
WHEN MATCHED THEN 
    UPDATE SET target.value = source.value
WHEN NOT MATCHED THEN
    INSERT (id, value) VALUES (source.id, source.value);
```

### Real-World Example: Inventory Management
```sql
-- Complex inventory tracking with multiple warehouses
WITH inventory_summary AS (
    SELECT 
        p.product_id,
        p.product_name,
        w.warehouse_location,
        i.quantity_on_hand,
        i.reorder_level,
        COALESCE(pending.pending_orders, 0) as pending_shipments,
        CASE 
            WHEN i.quantity_on_hand <= i.reorder_level THEN 'LOW_STOCK'
            WHEN i.quantity_on_hand = 0 THEN 'OUT_OF_STOCK'
            ELSE 'IN_STOCK'
        END as stock_status
    FROM products p
    INNER JOIN inventory i ON p.product_id = i.product_id
    INNER JOIN warehouses w ON i.warehouse_id = w.warehouse_id
    LEFT JOIN (
        SELECT 
            product_id,
            warehouse_id,
            SUM(quantity) as pending_orders
        FROM pending_shipments 
        GROUP BY product_id, warehouse_id
    ) pending ON i.product_id = pending.product_id 
                AND i.warehouse_id = pending.warehouse_id
)
SELECT 
    product_name,
    warehouse_location,
    quantity_on_hand,
    pending_shipments,
    stock_status,
    CASE 
        WHEN stock_status = 'LOW_STOCK' 
        THEN reorder_level * 2 - quantity_on_hand
        ELSE 0 
    END as suggested_reorder_quantity
FROM inventory_summary
WHERE stock_status IN ('LOW_STOCK', 'OUT_OF_STOCK')
ORDER BY warehouse_location, stock_status;
```

### Optimization Tips for Phase 4
- **Index Strategy:** Create indexes based on WHERE, JOIN, and ORDER BY clauses
- **Query Rewriting:** Sometimes EXISTS performs better than IN
- **Partitioning:** Consider table partitioning for very large datasets
- **Statistics:** Keep table statistics updated for optimal query plans

### Phase 4 Project: Performance-Optimized Data Warehouse
**Goal:** Build a scalable data warehouse for a multi-location retail chain.

**Key Features:**
- Fact and dimension tables (star schema)
- Efficient indexing strategy
- Query performance monitoring
- Data archiving strategy

```sql
-- Dimension table: Time
CREATE TABLE dim_time (
    date_key INT PRIMARY KEY,
    full_date DATE NOT NULL,
    year INT,
    quarter INT,
    month INT,
    week INT,
    day_of_week INT,
    is_weekend BOOLEAN,
    is_holiday BOOLEAN
);

-- Fact table: Sales
CREATE TABLE fact_sales (
    sale_id BIGINT PRIMARY KEY,
    date_key INT,
    store_id INT,
    product_id INT,
    customer_id INT,
    quantity INT,
    unit_price DECIMAL(10,2),
    total_amount DECIMAL(12,2),
    discount_amount DECIMAL(10,2),
    INDEX idx_sales_date (date_key),
    INDEX idx_sales_store (store_id),
    INDEX idx_sales_product (product_id),
    INDEX idx_sales_composite (date_key, store_id, product_id)
);
```

---

## Phase 5: Advanced SQL and Analytics (Weeks 15-18)

### Core Concepts to Master

#### 5.1 Advanced Window Functions
```sql
-- Percentiles and ntiles
SELECT 
    customer_id,
    total_spent,
    NTILE(4) OVER (ORDER BY total_spent) as spending_quartile,
    PERCENT_RANK() OVER (ORDER BY total_spent) as spending_percentile
FROM customer_totals;

-- Moving averages
SELECT 
    date,
    daily_revenue,
    AVG(daily_revenue) OVER (
        ORDER BY date 
        ROWS BETWEEN 6 PRECEDING AND CURRENT ROW
    ) as seven_day_avg
FROM daily_sales;

-- First/Last value in groups
SELECT 
    customer_id,
    order_date,
    order_amount,
    FIRST_VALUE(order_amount) OVER (
        PARTITION BY customer_id 
        ORDER BY order_date
    ) as first_order_amount,
    LAST_VALUE(order_amount) OVER (
        PARTITION BY customer_id 
        ORDER BY order_date
        ROWS BETWEEN UNBOUNDED PRECEDING AND UNBOUNDED FOLLOWING
    ) as most_recent_order
FROM orders;
```

#### 5.2 Advanced Functions and Expressions
```sql
-- JSON operations (MySQL 5.7+, PostgreSQL)
SELECT 
    product_name,
    JSON_EXTRACT(product_attributes, '$.color') as color,
    JSON_EXTRACT(product_attributes, '$.size') as size
FROM products 
WHERE JSON_EXTRACT(product_attributes, '$.category') = 'clothing';

-- String manipulation
SELECT 
    CONCAT(first_name, ' ', last_name) as full_name,
    UPPER(email) as email_upper,
    SUBSTRING(phone, 1, 3) as area_code,
    REPLACE(address, 'Street', 'St.') as formatted_address
FROM customers;

-- Date calculations
SELECT 
    order_date,
    EXTRACT(YEAR FROM order_date) as order_year,
    EXTRACT(MONTH FROM order_date) as order_month,
    DATEDIFF(CURRENT_DATE, order_date) as days_ago,
    DATE_FORMAT(order_date, '%Y-%m') as year_month
FROM orders;
```

#### 5.3 Stored Procedures and Functions
```sql
-- Stored procedure for complex business logic
DELIMITER //
CREATE PROCEDURE CalculateCustomerTier(IN customer_id INT)
BEGIN
    DECLARE total_spent DECIMAL(12,2);
    DECLARE order_count INT;
    DECLARE customer_tier VARCHAR(20);
    
    -- Calculate metrics
    SELECT 
        COALESCE(SUM(total_amount), 0),
        COUNT(*)
    INTO total_spent, order_count
    FROM orders 
    WHERE customer_id = customer_id;
    
    -- Determine tier
    IF total_spent >= 10000 THEN
        SET customer_tier = 'PLATINUM';
    ELSEIF total_spent >= 5000 THEN
        SET customer_tier = 'GOLD';
    ELSEIF total_spent >= 1000 THEN
        SET customer_tier = 'SILVER';
    ELSE
        SET customer_tier = 'BRONZE';
    END IF;
    
    -- Update customer record
    UPDATE customers 
    SET tier = customer_tier, 
        last_tier_update = CURRENT_DATE
    WHERE customer_id = customer_id;
END //
DELIMITER ;
```

#### 5.4 Advanced Analytics Queries
```sql
-- Cohort analysis for user retention
WITH first_orders AS (
    SELECT 
        customer_id,
        MIN(order_date) as first_order_date,
        DATE_FORMAT(MIN(order_date), '%Y-%m') as cohort_month
    FROM orders 
    GROUP BY customer_id
),
customer_activities AS (
    SELECT 
        fo.customer_id,
        fo.cohort_month,
        o.order_date,
        TIMESTAMPDIFF(MONTH, fo.first_order_date, o.order_date) as period_number
    FROM first_orders fo
    INNER JOIN orders o ON fo.customer_id = o.customer_id
)
SELECT 
    cohort_month,
    period_number,
    COUNT(DISTINCT customer_id) as customers,
    ROUND(
        100.0 * COUNT(DISTINCT customer_id) / 
        FIRST_VALUE(COUNT(DISTINCT customer_id)) OVER (
            PARTITION BY cohort_month 
            ORDER BY period_number
        ), 2
    ) as retention_rate
FROM customer_activities
GROUP BY cohort_month, period_number
ORDER BY cohort_month, period_number;
```

### Real-World Example: Financial Analysis
A fintech company analyzing transaction patterns:

```sql
-- Fraud detection using statistical analysis
WITH transaction_stats AS (
    SELECT 
        customer_id,
        AVG(amount) as avg_transaction,
        STDDEV(amount) as stddev_transaction,
        COUNT(*) as transaction_count
    FROM transactions 
    WHERE transaction_date >= DATE_SUB(CURRENT_DATE, INTERVAL 30 DAY)
    GROUP BY customer_id
),
flagged_transactions AS (
    SELECT 
        t.transaction_id,
        t.customer_id,
        t.amount,
        t.transaction_date,
        ts.avg_transaction,
        ts.stddev_transaction,
        CASE 
            WHEN t.amount > (ts.avg_transaction + 3 * ts.stddev_transaction) 
            THEN 'HIGH_AMOUNT_ANOMALY'
            WHEN HOUR(t.transaction_date) BETWEEN 2 AND 5 
            AND t.amount > 1000 
            THEN 'SUSPICIOUS_TIME'
            ELSE 'NORMAL'
        END as risk_flag
    FROM transactions t
    INNER JOIN transaction_stats ts ON t.customer_id = ts.customer_id
    WHERE t.transaction_date >= CURRENT_DATE
)
SELECT * FROM flagged_transactions 
WHERE risk_flag != 'NORMAL'
ORDER BY transaction_date DESC;
```

### Phase 5 Project: Real-Time Analytics Engine
**Goal:** Build a sophisticated analytics engine for a streaming service.

**Key Components:**
- User viewing behavior analysis
- Content recommendation engine
- Performance metrics dashboard
- A/B testing framework

---

## Phase 6: Expert-Level SQL and Optimization (Weeks 19-24)

### Core Concepts to Master

#### 6.1 Query Optimization Mastery
```sql
-- Query rewriting for performance
-- Instead of this (slow):
SELECT * FROM orders o
WHERE EXISTS (
    SELECT 1 FROM customers c 
    WHERE c.customer_id = o.customer_id 
    AND c.country = 'USA'
);

-- Use this (faster):
SELECT o.* FROM orders o
INNER JOIN customers c ON o.customer_id = c.customer_id
WHERE c.country = 'USA';

-- Optimize with indexes and query hints
SELECT /*+ USE_INDEX(orders, idx_order_date) */
    customer_id, 
    SUM(total_amount)
FROM orders 
WHERE order_date BETWEEN '2024-01-01' AND '2024-12-31'
GROUP BY customer_id;
```

#### 6.2 Advanced Indexing and Partitioning
```sql
-- Table partitioning by date (MySQL)
CREATE TABLE sales_2024 (
    sale_id BIGINT,
    sale_date DATE,
    amount DECIMAL(10,2),
    customer_id INT
) PARTITION BY RANGE (YEAR(sale_date)) (
    PARTITION p2024 VALUES LESS THAN (2025),
    PARTITION p2025 VALUES LESS THAN (2026),
    PARTITION p_future VALUES LESS THAN MAXVALUE
);

-- Functional index (PostgreSQL)
CREATE INDEX idx_customer_email_lower 
ON customers (LOWER(email));
```

#### 6.3 Complex Business Logic
```sql
-- Revenue recognition with complex rules
WITH subscription_revenue AS (
    SELECT 
        s.subscription_id,
        s.customer_id,
        s.monthly_amount,
        generate_series(
            s.start_date::date,
            LEAST(s.end_date, CURRENT_DATE)::date,
            '1 month'::interval
        )::date as revenue_month
    FROM subscriptions s
    WHERE s.status = 'active'
),
monthly_recognition AS (
    SELECT 
        DATE_TRUNC('month', revenue_month) as month,
        customer_id,
        subscription_id,
        monthly_amount,
        CASE 
            WHEN revenue_month = DATE_TRUNC('month', revenue_month) 
            THEN monthly_amount
            ELSE monthly_amount * 
                 EXTRACT(days FROM revenue_month - DATE_TRUNC('month', revenue_month) + 1) /
                 EXTRACT(days FROM DATE_TRUNC('month', revenue_month) + INTERVAL '1 month' - INTERVAL '1 day')
        END as recognized_revenue
    FROM subscription_revenue
)
SELECT 
    month,
    SUM(recognized_revenue) as total_revenue,
    COUNT(DISTINCT customer_id) as active_customers,
    AVG(recognized_revenue) as arpu
FROM monthly_recognition
GROUP BY month
ORDER BY month;
```

#### 6.4 Advanced Data Modeling Patterns
```sql
-- Slowly Changing Dimensions (SCD Type 2)
CREATE TABLE dim_customer_scd (
    customer_key INT PRIMARY KEY AUTO_INCREMENT,
    customer_id INT NOT NULL,
    customer_name VARCHAR(100),
    email VARCHAR(100),
    tier VARCHAR(20),
    effective_date DATE NOT NULL,
    expiry_date DATE,
    is_current BOOLEAN DEFAULT TRUE,
    INDEX idx_customer_current (customer_id, is_current)
);

-- Event sourcing pattern
CREATE TABLE customer_events (
    event_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    customer_id INT NOT NULL,
    event_type VARCHAR(50) NOT NULL,
    event_data JSON,
    event_timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_customer_events (customer_id, event_timestamp)
);
```

### Real-World Example: Advanced E-commerce Analytics
```sql
-- Market basket analysis using association rules
WITH item_pairs AS (
    SELECT 
        oi1.product_id as product_a,
        oi2.product_id as product_b,
        COUNT(DISTINCT oi1.order_id) as co_occurrence_count
    FROM order_items oi1
    INNER JOIN order_items oi2 ON oi1.order_id = oi2.order_id
    WHERE oi1.product_id < oi2.product_id  -- Avoid duplicates
    GROUP BY oi1.product_id, oi2.product_id
    HAVING COUNT(DISTINCT oi1.order_id) >= 10  -- Minimum support
),
product_totals AS (
    SELECT 
        product_id,
        COUNT(DISTINCT order_id) as total_orders
    FROM order_items 
    GROUP BY product_id
)
SELECT 
    pa.product_name as product_a,
    pb.product_name as product_b,
    ip.co_occurrence_count,
    ROUND(
        ip.co_occurrence_count * 100.0 / pta.total_orders, 2
    ) as confidence_a_to_b,
    ROUND(
        ip.co_occurrence_count * 100.0 / ptb.total_orders, 2
    ) as confidence_b_to_a
FROM item_pairs ip
INNER JOIN products pa ON ip.product_a = pa.product_id
INNER JOIN products pb ON ip.product_b = pb.product_id
INNER JOIN product_totals pta ON ip.product_a = pta.product_id
INNER JOIN product_totals ptb ON ip.product_b = ptb.product_id
WHERE ip.co_occurrence_count >= 20
ORDER BY ip.co_occurrence_count DESC;
```

### Optimization Tips for Phase 6
- **Materialized Views:** Pre-calculate complex aggregations
- **Query Caching:** Implement intelligent caching strategies
- **Parallel Processing:** Use parallel query execution for large datasets
- **Memory Management:** Optimize sort and hash operations

### Phase 6 Project: Enterprise Data Platform
**Goal:** Build a complete enterprise-grade data platform with real-time and batch processing.

**Components:**
- OLTP system for transactional data
- OLAP system for analytics
- ETL pipelines for data movement
- Real-time streaming analytics
- Data quality monitoring

---

## Phase 7: Specialized SQL Domains (Weeks 25-30)

### Core Concepts to Master

#### 7.1 Spatial and Geographic Data (PostGIS)
```sql
-- Geographic queries
SELECT 
    store_name,
    ST_Distance(
        store_location, 
        ST_GeomFromText('POINT(-74.006 40.7128)')  -- NYC coordinates
    ) as distance_from_nyc
FROM stores
WHERE ST_DWithin(
    store_location, 
    ST_GeomFromText('POINT(-74.006 40.7128)'), 
    50000  -- 50km radius
)
ORDER BY distance_from_nyc;
```

#### 7.2 Time Series Analysis
```sql
-- Time series forecasting preparation
WITH daily_metrics AS (
    SELECT