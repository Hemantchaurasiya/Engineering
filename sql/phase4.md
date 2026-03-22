# Phase 4: Advanced Querying - Complete Mastery Guide
*Goal: Master complex query patterns and subqueries*

## 1. Subqueries: The Foundation of Advanced SQL

### 1.1 Scalar Subqueries

Scalar subqueries return a single value and can be used anywhere a single value is expected.

#### Single-Value Subqueries in SELECT

```sql
-- Find each product's price compared to average price
SELECT 
    product_name,
    price,
    (SELECT AVG(price) FROM products) as avg_price,
    price - (SELECT AVG(price) FROM products) as price_difference
FROM products;

-- Real-world example: Employee salaries vs department average
SELECT 
    employee_name,
    salary,
    department_id,
    (SELECT AVG(salary) 
     FROM employees e2 
     WHERE e2.department_id = e1.department_id) as dept_avg_salary
FROM employees e1;
```

#### Subqueries in WHERE Clause

```sql
-- Find products priced above average
SELECT product_name, price
FROM products
WHERE price > (SELECT AVG(price) FROM products);

-- Find customers who made their last order after company average
SELECT customer_id, customer_name
FROM customers
WHERE last_order_date > (
    SELECT AVG(last_order_date) 
    FROM customers
);
```

#### Correlated vs Non-Correlated Subqueries

**Non-Correlated (Independent):**
```sql
-- Subquery runs once, independent of outer query
SELECT employee_name, salary
FROM employees
WHERE salary > (SELECT AVG(salary) FROM employees);
```

**Correlated (Dependent):**
```sql
-- Subquery runs for each row of outer query
SELECT e1.employee_name, e1.salary, e1.department_id
FROM employees e1
WHERE e1.salary > (
    SELECT AVG(e2.salary)
    FROM employees e2
    WHERE e2.department_id = e1.department_id  -- Correlation here
);
```

### 1.2 Multiple-Row Subqueries

These return multiple rows and require special operators.

#### EXISTS and NOT EXISTS

```sql
-- Find customers who have placed orders
SELECT customer_id, customer_name
FROM customers c
WHERE EXISTS (
    SELECT 1 
    FROM orders o 
    WHERE o.customer_id = c.customer_id
);

-- Find products that have never been ordered
SELECT product_id, product_name
FROM products p
WHERE NOT EXISTS (
    SELECT 1 
    FROM order_items oi 
    WHERE oi.product_id = p.product_id
);

-- Real-world: Find employees who manage others
SELECT employee_id, employee_name
FROM employees e1
WHERE EXISTS (
    SELECT 1 
    FROM employees e2 
    WHERE e2.manager_id = e1.employee_id
);
```

#### IN with Subqueries

```sql
-- Find orders from customers in California
SELECT order_id, order_date, total_amount
FROM orders
WHERE customer_id IN (
    SELECT customer_id 
    FROM customers 
    WHERE state = 'California'
);

-- Find products in categories with average price > $100
SELECT product_name, price, category_id
FROM products
WHERE category_id IN (
    SELECT category_id
    FROM products
    GROUP BY category_id
    HAVING AVG(price) > 100
);
```

#### ANY, ALL Operators

```sql
-- ANY: True if condition is true for any row
-- Find products more expensive than ANY product in category 1
SELECT product_name, price
FROM products
WHERE price > ANY (
    SELECT price 
    FROM products 
    WHERE category_id = 1
);

-- ALL: True if condition is true for all rows
-- Find products more expensive than ALL products in category 1
SELECT product_name, price
FROM products
WHERE price > ALL (
    SELECT price 
    FROM products 
    WHERE category_id = 1
);

-- Real-world example: Find salespeople who sold more than ANY salesperson's average
SELECT salesperson_id, total_sales
FROM (
    SELECT salesperson_id, SUM(amount) as total_sales
    FROM sales
    GROUP BY salesperson_id
) s1
WHERE total_sales > ANY (
    SELECT AVG(amount)
    FROM sales
    GROUP BY salesperson_id
);
```

### 1.3 Subqueries in FROM Clause

#### Derived Tables

```sql
-- Calculate percentage of total sales for each product
SELECT 
    product_name,
    product_sales,
    (product_sales / total_sales) * 100 as sales_percentage
FROM (
    SELECT 
        p.product_name,
        SUM(oi.quantity * oi.price) as product_sales
    FROM products p
    JOIN order_items oi ON p.product_id = oi.product_id
    GROUP BY p.product_id, p.product_name
) product_totals
CROSS JOIN (
    SELECT SUM(oi.quantity * oi.price) as total_sales
    FROM order_items oi
) company_total;
```

#### Common Table Expressions (CTE)

```sql
-- Same query using CTE (cleaner syntax)
WITH product_sales AS (
    SELECT 
        p.product_name,
        SUM(oi.quantity * oi.price) as sales_amount
    FROM products p
    JOIN order_items oi ON p.product_id = oi.product_id
    GROUP BY p.product_id, p.product_name
),
total_sales AS (
    SELECT SUM(sales_amount) as company_total
    FROM product_sales
)
SELECT 
    product_name,
    sales_amount,
    (sales_amount / company_total) * 100 as percentage
FROM product_sales
CROSS JOIN total_sales
ORDER BY percentage DESC;

-- Multiple CTEs for complex analysis
WITH monthly_sales AS (
    SELECT 
        DATE_TRUNC('month', order_date) as month,
        SUM(total_amount) as monthly_total
    FROM orders
    WHERE order_date >= '2024-01-01'
    GROUP BY DATE_TRUNC('month', order_date)
),
sales_with_growth AS (
    SELECT 
        month,
        monthly_total,
        LAG(monthly_total) OVER (ORDER BY month) as prev_month_total
    FROM monthly_sales
)
SELECT 
    month,
    monthly_total,
    prev_month_total,
    CASE 
        WHEN prev_month_total IS NOT NULL 
        THEN ((monthly_total - prev_month_total) / prev_month_total) * 100
        ELSE NULL
    END as growth_rate
FROM sales_with_growth;
```

#### Recursive CTEs

```sql
-- Organizational hierarchy traversal
WITH RECURSIVE employee_hierarchy AS (
    -- Base case: top-level managers
    SELECT 
        employee_id,
        employee_name,
        manager_id,
        0 as level,
        CAST(employee_name AS VARCHAR(1000)) as hierarchy_path
    FROM employees
    WHERE manager_id IS NULL
    
    UNION ALL
    
    -- Recursive case: employees with managers
    SELECT 
        e.employee_id,
        e.employee_name,
        e.manager_id,
        eh.level + 1,
        eh.hierarchy_path || ' -> ' || e.employee_name
    FROM employees e
    JOIN employee_hierarchy eh ON e.manager_id = eh.employee_id
)
SELECT 
    employee_id,
    REPEAT('  ', level) || employee_name as indented_name,
    level,
    hierarchy_path
FROM employee_hierarchy
ORDER BY hierarchy_path;

-- Category hierarchy with product counts
WITH RECURSIVE category_tree AS (
    SELECT 
        category_id,
        category_name,
        parent_category_id,
        0 as depth,
        category_name as full_path
    FROM categories
    WHERE parent_category_id IS NULL
    
    UNION ALL
    
    SELECT 
        c.category_id,
        c.category_name,
        c.parent_category_id,
        ct.depth + 1,
        ct.full_path || ' > ' || c.category_name
    FROM categories c
    JOIN category_tree ct ON c.parent_category_id = ct.category_id
)
SELECT 
    ct.category_id,
    ct.full_path,
    ct.depth,
    COUNT(p.product_id) as product_count
FROM category_tree ct
LEFT JOIN products p ON ct.category_id = p.category_id
GROUP BY ct.category_id, ct.full_path, ct.depth
ORDER BY ct.full_path;
```

## 2. Advanced Functions

### 2.1 String Functions

#### Basic String Manipulation

```sql
-- SUBSTRING, LEFT, RIGHT
SELECT 
    product_name,
    SUBSTRING(product_name, 1, 10) as short_name,
    LEFT(product_name, 5) as prefix,
    RIGHT(product_name, 3) as suffix,
    LENGTH(product_name) as name_length
FROM products;

-- Real-world: Extract area code from phone numbers
SELECT 
    customer_name,
    phone_number,
    SUBSTRING(phone_number, 2, 3) as area_code,
    SUBSTRING(phone_number, 6, 3) as exchange,
    SUBSTRING(phone_number, 10, 4) as number
FROM customers
WHERE phone_number LIKE '(%'  -- Format: (123) 456-7890
```

#### Case Conversion and Trimming

```sql
-- UPPER, LOWER, TRIM
SELECT 
    UPPER(customer_name) as name_caps,
    LOWER(email) as email_lower,
    TRIM(address) as clean_address,
    TRIM(BOTH '$' FROM price_text) as numeric_price
FROM customer_data;

-- Clean and standardize data
UPDATE customers 
SET 
    email = LOWER(TRIM(email)),
    customer_name = TRIM(customer_name),
    state = UPPER(TRIM(state))
WHERE updated_date < '2024-01-01';
```

#### String Concatenation and Replacement

```sql
-- CONCAT, REPLACE
SELECT 
    CONCAT(first_name, ' ', last_name) as full_name,
    CONCAT(city, ', ', state, ' ', zip_code) as full_address,
    REPLACE(phone_number, '-', '.') as phone_formatted,
    REPLACE(REPLACE(product_name, '_', ' '), '-', ' ') as clean_name
FROM customer_products;

-- Build dynamic email addresses
SELECT 
    employee_id,
    CONCAT(
        LOWER(REPLACE(first_name, ' ', '')), 
        '.', 
        LOWER(REPLACE(last_name, ' ', '')), 
        '@company.com'
    ) as email_address
FROM employees;
```

#### Pattern Matching

```sql
-- Advanced LIKE patterns
SELECT product_name
FROM products
WHERE 
    product_name LIKE '%phone%'           -- Contains 'phone'
    AND product_name LIKE 'Smart%'        -- Starts with 'Smart'
    AND product_name NOT LIKE '%case%';   -- Doesn't contain 'case'

-- Using wildcards effectively
SELECT customer_name, email
FROM customers
WHERE 
    email LIKE '%gmail.com'               -- Gmail users
    OR email LIKE '%yahoo.%'              -- Yahoo users
    OR phone_number LIKE '(555)%';        -- Specific area code
```

### 2.2 Date/Time Functions

#### Date Manipulation

```sql
-- DATE, TIME, DATETIME manipulation
SELECT 
    order_date,
    DATE(order_date) as date_only,
    TIME(order_date) as time_only,
    YEAR(order_date) as order_year,
    MONTH(order_date) as order_month,
    DAY(order_date) as order_day,
    DAYNAME(order_date) as day_name
FROM orders;

-- Real-world: Business hours analysis
SELECT 
    order_id,
    order_date,
    CASE 
        WHEN TIME(order_date) BETWEEN '09:00:00' AND '17:00:00' 
             AND DAYOFWEEK(order_date) BETWEEN 2 AND 6
        THEN 'Business Hours'
        ELSE 'After Hours'
    END as order_timing
FROM orders;
```

#### Date Arithmetic

```sql
-- DATEADD, DATEDIFF (SQL Server syntax)
SELECT 
    customer_id,
    registration_date,
    DATEADD(DAY, 30, registration_date) as trial_end_date,
    DATEDIFF(DAY, registration_date, GETDATE()) as days_since_registration,
    DATEADD(MONTH, -1, GETDATE()) as one_month_ago
FROM customers;

-- PostgreSQL syntax
SELECT 
    customer_id,
    registration_date,
    registration_date + INTERVAL '30 days' as trial_end_date,
    CURRENT_DATE - registration_date as days_since_registration,
    CURRENT_DATE - INTERVAL '1 month' as one_month_ago
FROM customers;

-- Business logic: Find customers approaching renewal
SELECT 
    customer_id,
    subscription_start_date,
    subscription_start_date + INTERVAL '1 year' as renewal_date,
    (subscription_start_date + INTERVAL '1 year') - CURRENT_DATE as days_until_renewal
FROM subscriptions
WHERE (subscription_start_date + INTERVAL '1 year') - CURRENT_DATE <= 30
ORDER BY days_until_renewal;
```

#### Date Extraction and Formatting

```sql
-- EXTRACT/DATE_PART
SELECT 
    order_date,
    EXTRACT(YEAR FROM order_date) as year,
    EXTRACT(QUARTER FROM order_date) as quarter,
    EXTRACT(MONTH FROM order_date) as month,
    EXTRACT(WEEK FROM order_date) as week_number,
    EXTRACT(DOW FROM order_date) as day_of_week  -- 0=Sunday
FROM orders;

-- Seasonal analysis
SELECT 
    CASE EXTRACT(MONTH FROM order_date)
        WHEN 12, 1, 2 THEN 'Winter'
        WHEN 3, 4, 5 THEN 'Spring'
        WHEN 6, 7, 8 THEN 'Summer'
        WHEN 9, 10, 11 THEN 'Fall'
    END as season,
    COUNT(*) as order_count,
    AVG(total_amount) as avg_order_value
FROM orders
GROUP BY 
    CASE EXTRACT(MONTH FROM order_date)
        WHEN 12, 1, 2 THEN 'Winter'
        WHEN 3, 4, 5 THEN 'Spring'
        WHEN 6, 7, 8 THEN 'Summer'
        WHEN 9, 10, 11 THEN 'Fall'
    END;
```

#### Date Formatting

```sql
-- Format dates for display
SELECT 
    order_id,
    order_date,
    TO_CHAR(order_date, 'YYYY-MM-DD') as iso_date,
    TO_CHAR(order_date, 'Month DD, YYYY') as formatted_date,
    TO_CHAR(order_date, 'Day') as full_day_name,
    TO_CHAR(order_date, 'HH24:MI:SS') as time_24hr
FROM orders;

-- Create date ranges for reporting
WITH date_ranges AS (
    SELECT 
        DATE_TRUNC('month', order_date) as month_start,
        DATE_TRUNC('month', order_date) + INTERVAL '1 month' - INTERVAL '1 day' as month_end
    FROM orders
    GROUP BY DATE_TRUNC('month', order_date)
)
SELECT 
    TO_CHAR(month_start, 'Month YYYY') as month_label,
    month_start,
    month_end
FROM date_ranges
ORDER BY month_start;
```

### 2.3 Mathematical Functions

#### Rounding and Precision

```sql
-- ROUND, CEIL, FLOOR
SELECT 
    product_name,
    price,
    ROUND(price, 2) as rounded_price,
    CEIL(price) as price_ceiling,
    FLOOR(price) as price_floor,
    ROUND(price * 0.85, 2) as discounted_price  -- 15% discount
FROM products;

-- Financial calculations with proper rounding
SELECT 
    order_id,
    subtotal,
    tax_rate,
    ROUND(subtotal * tax_rate, 2) as tax_amount,
    ROUND(subtotal * (1 + tax_rate), 2) as total_with_tax
FROM orders;
```

#### Advanced Mathematical Operations

```sql
-- ABS, POWER, SQRT, MOD
SELECT 
    product_id,
    actual_sales,
    projected_sales,
    ABS(actual_sales - projected_sales) as variance,
    POWER(actual_sales - projected_sales, 2) as variance_squared,
    SQRT(POWER(actual_sales - projected_sales, 2)) as absolute_variance,
    MOD(product_id, 10) as hash_bucket  -- For data partitioning
FROM sales_projections;

-- Statistical calculations
WITH sales_stats AS (
    SELECT 
        AVG(total_amount) as mean_sales,
        STDDEV(total_amount) as std_dev_sales
    FROM orders
)
SELECT 
    order_id,
    total_amount,
    mean_sales,
    (total_amount - mean_sales) / std_dev_sales as z_score,
    CASE 
        WHEN ABS((total_amount - mean_sales) / std_dev_sales) > 2 
        THEN 'Outlier'
        ELSE 'Normal'
    END as outlier_status
FROM orders
CROSS JOIN sales_stats
WHERE total_amount IS NOT NULL;
```

## 3. Real-World Project 4: Financial Reporting System

Let's build a comprehensive financial analysis system that demonstrates all these concepts:

### Setup: Sample Schema

```sql
-- Create sample tables for the financial reporting system
CREATE TABLE accounts (
    account_id INT PRIMARY KEY,
    account_name VARCHAR(100),
    account_type VARCHAR(50), -- 'Revenue', 'Expense', 'Asset', 'Liability'
    parent_account_id INT,
    is_active BOOLEAN DEFAULT TRUE
);

CREATE TABLE transactions (
    transaction_id INT PRIMARY KEY,
    transaction_date DATE,
    account_id INT,
    amount DECIMAL(12,2),
    description TEXT,
    reference_number VARCHAR(50),
    created_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE customers (
    customer_id INT PRIMARY KEY,
    customer_name VARCHAR(100),
    registration_date DATE,
    customer_segment VARCHAR(50),
    credit_limit DECIMAL(10,2)
);

CREATE TABLE sales (
    sale_id INT PRIMARY KEY,
    customer_id INT,
    sale_date DATE,
    amount DECIMAL(10,2),
    product_category VARCHAR(50),
    salesperson_id INT
);
```

### Query 1: Running Totals and Moving Averages

```sql
-- Calculate running totals and 3-month moving averages
WITH monthly_revenue AS (
    SELECT 
        DATE_TRUNC('month', t.transaction_date) as month,
        SUM(CASE WHEN a.account_type = 'Revenue' THEN t.amount ELSE 0 END) as revenue,
        SUM(CASE WHEN a.account_type = 'Expense' THEN t.amount ELSE 0 END) as expenses
    FROM transactions t
    JOIN accounts a ON t.account_id = a.account_id
    WHERE t.transaction_date >= '2023-01-01'
    GROUP BY DATE_TRUNC('month', t.transaction_date)
),
revenue_with_analytics AS (
    SELECT 
        month,
        revenue,
        expenses,
        revenue - expenses as net_income,
        SUM(revenue) OVER (ORDER BY month) as running_revenue,
        AVG(revenue) OVER (
            ORDER BY month 
            ROWS BETWEEN 2 PRECEDING AND CURRENT ROW
        ) as three_month_avg_revenue
    FROM monthly_revenue
)
SELECT 
    TO_CHAR(month, 'YYYY-MM') as month_label,
    ROUND(revenue, 2) as monthly_revenue,
    ROUND(expenses, 2) as monthly_expenses,
    ROUND(net_income, 2) as net_income,
    ROUND(running_revenue, 2) as cumulative_revenue,
    ROUND(three_month_avg_revenue, 2) as three_month_avg
FROM revenue_with_analytics
ORDER BY month;
```

### Query 2: Above-Average Purchase Analysis

```sql
-- Find customers with above-average purchase amounts using correlated subqueries
SELECT 
    c.customer_id,
    c.customer_name,
    c.customer_segment,
    customer_stats.total_purchases,
    customer_stats.avg_purchase,
    segment_avg.segment_average,
    ROUND(
        (customer_stats.avg_purchase - segment_avg.segment_average) / segment_avg.segment_average * 100, 
        2
    ) as performance_vs_segment
FROM customers c
JOIN (
    SELECT 
        customer_id,
        COUNT(*) as total_purchases,
        AVG(amount) as avg_purchase
    FROM sales
    GROUP BY customer_id
) customer_stats ON c.customer_id = customer_stats.customer_id
JOIN (
    SELECT 
        c.customer_segment,
        AVG(s.amount) as segment_average
    FROM customers c
    JOIN sales s ON c.customer_id = s.customer_id
    GROUP BY c.customer_segment
) segment_avg ON c.customer_segment = segment_avg.customer_segment
WHERE customer_stats.avg_purchase > (
    SELECT AVG(amount) 
    FROM sales s2 
    JOIN customers c2 ON s2.customer_id = c2.customer_id
    WHERE c2.customer_segment = c.customer_segment
)
ORDER BY performance_vs_segment DESC;
```

### Query 3: Month-over-Month Growth Reports

```sql
-- Create comprehensive month-over-month growth analysis
WITH monthly_metrics AS (
    SELECT 
        DATE_TRUNC('month', sale_date) as month,
        COUNT(*) as transaction_count,
        SUM(amount) as total_sales,
        AVG(amount) as avg_transaction_size,
        COUNT(DISTINCT customer_id) as unique_customers
    FROM sales
    WHERE sale_date >= '2023-01-01'
    GROUP BY DATE_TRUNC('month', sale_date)
),
growth_analysis AS (
    SELECT 
        month,
        transaction_count,
        total_sales,
        avg_transaction_size,
        unique_customers,
        LAG(total_sales) OVER (ORDER BY month) as prev_month_sales,
        LAG(transaction_count) OVER (ORDER BY month) as prev_month_transactions,
        LAG(unique_customers) OVER (ORDER BY month) as prev_month_customers
    FROM monthly_metrics
)
SELECT 
    TO_CHAR(month, 'YYYY-MM') as month_label,
    ROUND(total_sales, 2) as current_sales,
    ROUND(prev_month_sales, 2) as previous_sales,
    CASE 
        WHEN prev_month_sales IS NOT NULL AND prev_month_sales > 0
        THEN ROUND(((total_sales - prev_month_sales) / prev_month_sales) * 100, 2)
        ELSE NULL
    END as sales_growth_pct,
    CASE 
        WHEN prev_month_transactions IS NOT NULL AND prev_month_transactions > 0
        THEN ROUND(((transaction_count - prev_month_transactions) / CAST(prev_month_transactions AS DECIMAL)) * 100, 2)
        ELSE NULL
    END as transaction_growth_pct,
    ROUND(avg_transaction_size, 2) as avg_order_value
FROM growth_analysis
ORDER BY month;
```

### Query 4: Seasonal Patterns with Date Functions

```sql
-- Identify seasonal sales patterns using advanced date functions
WITH seasonal_analysis AS (
    SELECT 
        EXTRACT(YEAR FROM sale_date) as year,
        EXTRACT(QUARTER FROM sale_date) as quarter,
        EXTRACT(MONTH FROM sale_date) as month,
        CASE 
            WHEN EXTRACT(MONTH FROM sale_date) IN (12, 1, 2) THEN 'Winter'
            WHEN EXTRACT(MONTH FROM sale_date) IN (3, 4, 5) THEN 'Spring'
            WHEN EXTRACT(MONTH FROM sale_date) IN (6, 7, 8) THEN 'Summer'
            WHEN EXTRACT(MONTH FROM sale_date) IN (9, 10, 11) THEN 'Fall'
        END as season,
        TO_CHAR(sale_date, 'Day') as day_of_week,
        amount,
        product_category
    FROM sales
    WHERE sale_date >= '2022-01-01'
),
seasonal_summary AS (
    SELECT 
        season,
        product_category,
        COUNT(*) as transaction_count,
        SUM(amount) as total_sales,
        AVG(amount) as avg_sale_amount,
        MIN(amount) as min_sale,
        MAX(amount) as max_sale
    FROM seasonal_analysis
    GROUP BY season, product_category
)
SELECT 
    season,
    product_category,
    transaction_count,
    ROUND(total_sales, 2) as total_sales,
    ROUND(avg_sale_amount, 2) as avg_sale,
    ROUND(total_sales / SUM(total_sales) OVER (PARTITION BY season) * 100, 2) as category_share_pct,
    RANK() OVER (PARTITION BY season ORDER BY total_sales DESC) as category_rank_in_season
FROM seasonal_summary
ORDER BY season, total_sales DESC;
```

### Query 5: Hierarchical Category Reports using CTEs

```sql
-- Build hierarchical account reports with recursive CTEs
WITH RECURSIVE account_hierarchy AS (
    -- Base case: top-level accounts
    SELECT 
        account_id,
        account_name,
        account_type,
        parent_account_id,
        0 as level,
        account_name as full_path,
        CAST(account_id AS VARCHAR(1000)) as path_ids
    FROM accounts
    WHERE parent_account_id IS NULL
    
    UNION ALL
    
    -- Recursive case: child accounts
    SELECT 
        a.account_id,
        a.account_name,
        a.account_type,
        a.parent_account_id,
        ah.level + 1,
        ah.full_path || ' > ' || a.account_name,
        ah.path_ids || ',' || a.account_id
    FROM accounts a
    JOIN account_hierarchy ah ON a.parent_account_id = ah.account_id
),
account_balances AS (
    SELECT 
        t.account_id,
        SUM(t.amount) as balance,
        COUNT(t.transaction_id) as transaction_count,
        MIN(t.transaction_date) as first_transaction,
        MAX(t.transaction_date) as last_transaction
    FROM transactions t
    WHERE t.transaction_date >= '2024-01-01'
    GROUP BY t.account_id
)
SELECT 
    ah.account_id,
    REPEAT('  ', ah.level) || ah.account_name as indented_name,
    ah.account_type,
    ah.level,
    ah.full_path,
    COALESCE(ab.balance, 0) as account_balance,
    COALESCE(ab.transaction_count, 0) as transaction_count,
    ab.first_transaction,
    ab.last_transaction
FROM account_hierarchy ah
LEFT JOIN account_balances ab ON ah.account_id = ab.account_id
ORDER BY ah.path_ids;
```

## Key Takeaways for Phase 4

1. **Subquery Mastery**: Understanding when to use scalar vs multiple-row subqueries, and recognizing correlated patterns
2. **CTE Power**: CTEs make complex queries readable and maintainable, especially for recursive operations
3. **Function Proficiency**: String, date, and math functions are essential for real-world data manipulation
4. **Performance Awareness**: Correlated subqueries can be expensive; consider JOINs or window functions as alternatives
5. **Business Logic**: Complex business requirements often require combining multiple advanced SQL concepts

Practice these patterns with your own datasets, and you'll be ready for Phase 5's window functions and advanced analytics!