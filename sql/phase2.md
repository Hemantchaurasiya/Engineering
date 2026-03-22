# SQL Phase 2: Data Manipulation & Aggregation - Complete Guide

## Overview
Phase 2 focuses on modifying data (INSERT, UPDATE, DELETE) and summarizing data using aggregation functions. These skills are critical for maintaining databases and generating analytical reports.

---

## 1. Data Modification

### INSERT Statements

#### Single Row Insertion
```sql
-- Basic syntax
INSERT INTO table_name (column1, column2, column3)
VALUES (value1, value2, value3);

-- Example: Adding a new customer
INSERT INTO customers (customer_id, first_name, last_name, email, registration_date)
VALUES (101, 'Alice', 'Martinez', 'alice.m@email.com', '2024-01-15');

-- Insert without specifying columns (must match all columns in order)
INSERT INTO customers
VALUES (102, 'Bob', 'Taylor', 'bob.taylor@email.com', '555-0201', 'Boston', 'MA', 'North', '2024-01-16', NULL, 0.00);

-- Insert with some NULL values explicitly
INSERT INTO customers (customer_id, first_name, last_name, email, phone, city)
VALUES (103, 'Carol', 'Davis', 'carol.d@email.com', NULL, 'Seattle');
```

#### Multiple Row Insertion
```sql
-- Insert multiple rows in one statement (more efficient)
INSERT INTO products (product_id, product_name, category, price, stock_quantity)
VALUES 
    (501, 'Wireless Mouse', 'Electronics', 29.99, 150),
    (502, 'USB Keyboard', 'Electronics', 45.50, 200),
    (503, 'Monitor Stand', 'Accessories', 35.00, 75),
    (504, 'HDMI Cable', 'Electronics', 12.99, 500);

-- Real-world example: Bulk product import
INSERT INTO products (product_id, product_name, category, price, stock_quantity, supplier_id)
VALUES
    (601, 'Office Chair', 'Furniture', 199.99, 50, 10),
    (602, 'Desk Lamp', 'Furniture', 45.99, 120, 10),
    (603, 'Filing Cabinet', 'Furniture', 149.99, 30, 11),
    (604, 'Whiteboard', 'Office Supplies', 89.99, 45, 12);
```

#### INSERT INTO SELECT
```sql
-- Copy data from one table to another
INSERT INTO archived_orders (order_id, customer_id, order_date, total_amount)
SELECT order_id, customer_id, order_date, total_amount
FROM orders
WHERE order_date < '2023-01-01';

-- Create a customer backup
INSERT INTO customers_backup
SELECT * FROM customers;

-- Insert aggregated data into a summary table
INSERT INTO monthly_sales_summary (month, year, total_sales, order_count)
SELECT 
    EXTRACT(MONTH FROM order_date) AS month,
    EXTRACT(YEAR FROM order_date) AS year,
    SUM(total_amount) AS total_sales,
    COUNT(*) AS order_count
FROM orders
WHERE EXTRACT(YEAR FROM order_date) = 2023
GROUP BY EXTRACT(MONTH FROM order_date), EXTRACT(YEAR FROM order_date);

-- Practical example: Identify and store VIP customers
CREATE TABLE vip_customers AS
SELECT customer_id, first_name, last_name, email, total_purchases
FROM customers
WHERE total_purchases > 5000;
```

### UPDATE Statements

#### Basic UPDATE Syntax
```sql
-- Update a single column for a specific row
UPDATE customers
SET email = 'newemail@email.com'
WHERE customer_id = 101;

-- Update multiple columns
UPDATE products
SET price = 39.99, stock_quantity = 200
WHERE product_id = 501;

-- DANGER: Update without WHERE updates ALL rows!
-- UPDATE customers SET city = 'Unknown';  -- Don't do this!
```

#### Conditional Updates with WHERE
```sql
-- Apply a discount to specific category
UPDATE products
SET price = price * 0.9
WHERE category = 'Electronics' AND stock_quantity > 100;

-- Update based on date condition
UPDATE customers
SET customer_status = 'Inactive'
WHERE last_purchase_date < '2023-01-01';

-- Complex condition update
UPDATE employees
SET salary = salary * 1.10
WHERE department = 'Sales' 
    AND performance_rating >= 4
    AND hire_date < '2022-01-01';

-- Real-world: Mark orders as shipped
UPDATE orders
SET 
    status = 'Shipped',
    ship_date = CURRENT_DATE,
    tracking_number = CONCAT('TRK-', order_id)
WHERE status = 'Processing' 
    AND order_date < CURRENT_DATE - INTERVAL '2 days';
```

#### Updating Multiple Columns
```sql
-- Update customer profile completely
UPDATE customers
SET 
    first_name = 'Robert',
    last_name = 'Johnson',
    email = 'robert.johnson@newemail.com',
    phone = '555-9999',
    city = 'San Francisco',
    state = 'CA',
    last_updated = CURRENT_TIMESTAMP
WHERE customer_id = 105;

-- Calculated updates
UPDATE products
SET 
    sale_price = price * 0.85,
    discount_percentage = 15,
    sale_start_date = '2024-01-20',
    sale_end_date = '2024-01-27'
WHERE category IN ('Electronics', 'Clothing');

-- Update with subquery
UPDATE products
SET average_rating = (
    SELECT AVG(rating)
    FROM product_reviews
    WHERE product_reviews.product_id = products.product_id
)
WHERE product_id IN (SELECT DISTINCT product_id FROM product_reviews);
```

### DELETE Statements

#### DELETE with Conditions
```sql
-- Delete specific records
DELETE FROM customers
WHERE customer_id = 999;

-- Delete based on condition
DELETE FROM orders
WHERE order_date < '2020-01-01' AND status = 'Cancelled';

-- Delete with multiple conditions
DELETE FROM products
WHERE stock_quantity = 0 
    AND last_order_date < '2022-01-01'
    AND status = 'Discontinued';

-- DANGER: DELETE without WHERE deletes ALL rows!
-- DELETE FROM customers;  -- This removes everything!
```

#### TRUNCATE vs DELETE
```sql
-- DELETE: Removes rows one by one, can use WHERE, slower, can be rolled back
DELETE FROM temp_data
WHERE processing_date < '2024-01-01';

-- TRUNCATE: Removes all rows at once, faster, resets auto-increment, cannot be rolled back in some databases
TRUNCATE TABLE temp_data;

-- Comparison:
-- DELETE FROM logs WHERE log_date < '2023-01-01';  -- Selective, logged, slower
-- TRUNCATE TABLE logs;  -- All rows, fast, minimal logging

-- When to use each:
-- DELETE: When you need WHERE clause, when you need transaction rollback
-- TRUNCATE: When clearing entire table, when speed matters, for temp/staging tables
```

#### Safe Deletion Practices
```sql
-- BEST PRACTICE 1: Always test with SELECT first
-- First, check what will be deleted:
SELECT * 
FROM orders
WHERE order_date < '2022-01-01' AND status = 'Cancelled';

-- Then delete:
DELETE FROM orders
WHERE order_date < '2022-01-01' AND status = 'Cancelled';

-- BEST PRACTICE 2: Use transactions for critical deletes
BEGIN TRANSACTION;

DELETE FROM customer_addresses
WHERE customer_id = 555;

-- Check if correct number deleted
-- If wrong, ROLLBACK; if correct, COMMIT;
COMMIT;

-- BEST PRACTICE 3: Soft deletes (recommended for business data)
-- Instead of DELETE, mark as deleted:
UPDATE customers
SET 
    is_deleted = TRUE,
    deleted_date = CURRENT_TIMESTAMP
WHERE customer_id = 123;

-- BEST PRACTICE 4: Archive before delete
-- Move to archive table first
INSERT INTO orders_archive
SELECT * FROM orders
WHERE order_date < '2022-01-01';

-- Then delete
DELETE FROM orders
WHERE order_date < '2022-01-01';
```

---

## 2. Aggregation Functions

### Basic Aggregates

#### COUNT()
```sql
-- Count all rows
SELECT COUNT(*) AS total_customers
FROM customers;

-- Count non-NULL values in a column
SELECT COUNT(email) AS customers_with_email
FROM customers;

-- Count with condition
SELECT COUNT(*) AS high_value_customers
FROM customers
WHERE total_purchases > 1000;

-- Multiple counts
SELECT 
    COUNT(*) AS total_customers,
    COUNT(email) AS has_email,
    COUNT(phone) AS has_phone,
    COUNT(*) - COUNT(email) AS missing_email
FROM customers;
```

#### SUM()
```sql
-- Total of all values
SELECT SUM(total_amount) AS total_revenue
FROM orders;

-- Sum with condition
SELECT SUM(total_amount) AS revenue_2023
FROM orders
WHERE EXTRACT(YEAR FROM order_date) = 2023;

-- Multiple sums
SELECT 
    SUM(total_amount) AS total_revenue,
    SUM(shipping_cost) AS total_shipping,
    SUM(total_amount - shipping_cost) AS product_revenue
FROM orders;

-- Conditional sum
SELECT 
    SUM(CASE WHEN status = 'Completed' THEN total_amount ELSE 0 END) AS completed_revenue,
    SUM(CASE WHEN status = 'Pending' THEN total_amount ELSE 0 END) AS pending_revenue
FROM orders;
```

#### AVG()
```sql
-- Average value
SELECT AVG(price) AS average_price
FROM products;

-- Average with condition
SELECT AVG(total_amount) AS avg_order_value
FROM orders
WHERE order_date >= '2024-01-01';

-- Rounded average
SELECT ROUND(AVG(salary), 2) AS average_salary
FROM employees;

-- Multiple averages
SELECT 
    category,
    AVG(price) AS avg_price,
    AVG(stock_quantity) AS avg_stock
FROM products
GROUP BY category;
```

#### MIN() and MAX()
```sql
-- Find minimum and maximum
SELECT 
    MIN(price) AS cheapest_product,
    MAX(price) AS most_expensive_product
FROM products;

-- Date ranges
SELECT 
    MIN(order_date) AS first_order,
    MAX(order_date) AS latest_order
FROM orders;

-- Combined analysis
SELECT 
    MIN(salary) AS lowest_salary,
    MAX(salary) AS highest_salary,
    AVG(salary) AS average_salary,
    MAX(salary) - MIN(salary) AS salary_range
FROM employees
WHERE department = 'Engineering';
```

#### COUNT(DISTINCT)
```sql
-- Count unique values
SELECT COUNT(DISTINCT customer_id) AS unique_customers
FROM orders;

-- Multiple distinct counts
SELECT 
    COUNT(DISTINCT customer_id) AS unique_customers,
    COUNT(DISTINCT product_id) AS unique_products,
    COUNT(*) AS total_orders
FROM order_items;

-- Distinct with condition
SELECT COUNT(DISTINCT customer_id) AS active_customers
FROM orders
WHERE order_date >= CURRENT_DATE - INTERVAL '30 days';
```

### GROUP BY Operations

#### Grouping Data
```sql
-- Basic grouping
SELECT 
    category,
    COUNT(*) AS product_count
FROM products
GROUP BY category;

-- Grouping with multiple aggregates
SELECT 
    category,
    COUNT(*) AS product_count,
    AVG(price) AS avg_price,
    MIN(price) AS min_price,
    MAX(price) AS max_price,
    SUM(stock_quantity) AS total_stock
FROM products
GROUP BY category
ORDER BY product_count DESC;

-- Real-world: Sales by month
SELECT 
    EXTRACT(YEAR FROM order_date) AS year,
    EXTRACT(MONTH FROM order_date) AS month,
    COUNT(*) AS order_count,
    SUM(total_amount) AS monthly_revenue,
    AVG(total_amount) AS avg_order_value
FROM orders
GROUP BY EXTRACT(YEAR FROM order_date), EXTRACT(MONTH FROM order_date)
ORDER BY year, month;
```

#### HAVING Clause vs WHERE
```sql
-- WHERE filters BEFORE grouping
-- HAVING filters AFTER grouping

-- WRONG: Can't use aggregate in WHERE
-- SELECT category, AVG(price)
-- FROM products
-- WHERE AVG(price) > 100  -- ERROR!
-- GROUP BY category;

-- CORRECT: Use HAVING for aggregate conditions
SELECT 
    category,
    AVG(price) AS avg_price,
    COUNT(*) AS product_count
FROM products
GROUP BY category
HAVING AVG(price) > 100;

-- Combining WHERE and HAVING
SELECT 
    category,
    COUNT(*) AS product_count,
    AVG(price) AS avg_price
FROM products
WHERE stock_quantity > 0  -- Filter individual rows first
GROUP BY category
HAVING COUNT(*) >= 5;      -- Then filter groups

-- Real-world example: Find departments with high average salaries
SELECT 
    department,
    COUNT(*) AS employee_count,
    AVG(salary) AS avg_salary,
    SUM(salary) AS total_payroll
FROM employees
WHERE employment_status = 'Active'
GROUP BY department
HAVING AVG(salary) > 50000 AND COUNT(*) >= 10
ORDER BY avg_salary DESC;
```

#### Multiple Column Grouping
```sql
-- Group by multiple columns
SELECT 
    category,
    brand,
    COUNT(*) AS product_count,
    AVG(price) AS avg_price
FROM products
GROUP BY category, brand
ORDER BY category, brand;

-- Real-world: Sales analysis by region and product category
SELECT 
    c.region,
    p.category,
    COUNT(DISTINCT o.order_id) AS order_count,
    COUNT(DISTINCT o.customer_id) AS customer_count,
    SUM(oi.quantity * oi.unit_price) AS total_revenue
FROM orders o
JOIN customers c ON o.customer_id = c.customer_id
JOIN order_items oi ON o.order_id = oi.order_id
JOIN products p ON oi.product_id = p.product_id
GROUP BY c.region, p.category
ORDER BY total_revenue DESC;

-- Year-over-year comparison
SELECT 
    EXTRACT(YEAR FROM order_date) AS year,
    EXTRACT(QUARTER FROM order_date) AS quarter,
    COUNT(*) AS orders,
    SUM(total_amount) AS revenue
FROM orders
GROUP BY EXTRACT(YEAR FROM order_date), EXTRACT(QUARTER FROM order_date)
ORDER BY year, quarter;
```

#### Common Grouping Patterns
```sql
-- Pattern 1: Top N per category
SELECT 
    category,
    COUNT(*) AS products,
    SUM(stock_quantity) AS total_inventory_units
FROM products
GROUP BY category
HAVING COUNT(*) >= 10
ORDER BY total_inventory_units DESC
LIMIT 5;

-- Pattern 2: Percentage calculations
SELECT 
    category,
    COUNT(*) AS product_count,
    ROUND(COUNT(*) * 100.0 / (SELECT COUNT(*) FROM products), 2) AS percentage_of_total
FROM products
GROUP BY category
ORDER BY product_count DESC;

-- Pattern 3: Customer segmentation
SELECT 
    CASE 
        WHEN total_purchases < 1000 THEN 'Bronze'
        WHEN total_purchases < 5000 THEN 'Silver'
        WHEN total_purchases < 10000 THEN 'Gold'
        ELSE 'Platinum'
    END AS customer_tier,
    COUNT(*) AS customer_count,
    AVG(total_purchases) AS avg_purchases,
    SUM(total_purchases) AS tier_revenue
FROM customers
GROUP BY 
    CASE 
        WHEN total_purchases < 1000 THEN 'Bronze'
        WHEN total_purchases < 5000 THEN 'Silver'
        WHEN total_purchases < 10000 THEN 'Gold'
        ELSE 'Platinum'
    END
ORDER BY tier_revenue DESC;
```

---

## Real-World Project 2: Sales Analytics System

Let's build a comprehensive sales reporting system:

```sql
-- Setup: Create complete database structure
CREATE TABLE products (
    product_id INT PRIMARY KEY,
    product_name VARCHAR(100),
    category VARCHAR(50),
    cost DECIMAL(10,2),
    price DECIMAL(10,2),
    stock_quantity INT
);

CREATE TABLE customers (
    customer_id INT PRIMARY KEY,
    first_name VARCHAR(50),
    last_name VARCHAR(50),
    email VARCHAR(100),
    customer_segment VARCHAR(20),
    registration_date DATE
);

CREATE TABLE sales_reps (
    rep_id INT PRIMARY KEY,
    rep_name VARCHAR(100),
    department VARCHAR(50),
    hire_date DATE
);

CREATE TABLE orders (
    order_id INT PRIMARY KEY,
    customer_id INT,
    rep_id INT,
    order_date DATE,
    total_amount DECIMAL(10,2),
    FOREIGN KEY (customer_id) REFERENCES customers(customer_id),
    FOREIGN KEY (rep_id) REFERENCES sales_reps(rep_id)
);

CREATE TABLE order_items (
    order_id INT,
    product_id INT,
    quantity INT,
    unit_price DECIMAL(10,2),
    PRIMARY KEY (order_id, product_id),
    FOREIGN KEY (order_id) REFERENCES orders(order_id),
    FOREIGN KEY (product_id) REFERENCES products(product_id)
);

-- Insert sample data
INSERT INTO products VALUES
(1, 'Laptop Pro', 'Electronics', 800, 1200, 50),
(2, 'Wireless Mouse', 'Electronics', 15, 29.99, 200),
(3, 'Office Chair', 'Furniture', 120, 199.99, 75),
(4, 'Desk Lamp', 'Furniture', 20, 45.99, 150),
(5, 'USB Hub', 'Electronics', 10, 24.99, 300);

INSERT INTO customers VALUES
(1, 'John', 'Doe', 'john@email.com', 'Premium', '2023-01-15'),
(2, 'Jane', 'Smith', 'jane@email.com', 'Standard', '2023-02-20'),
(3, 'Bob', 'Johnson', 'bob@email.com', 'Premium', '2023-03-10');

INSERT INTO sales_reps VALUES
(1, 'Alice Williams', 'Sales', '2022-01-10'),
(2, 'Tom Brown', 'Sales', '2022-03-15'),
(3, 'Sarah Davis', 'Sales', '2023-01-05');

INSERT INTO orders VALUES
(1, 1, 1, '2024-01-15', 1229.99),
(2, 2, 2, '2024-01-16', 245.98),
(3, 1, 1, '2024-01-20', 1200.00),
(4, 3, 3, '2024-01-22', 469.97);

INSERT INTO order_items VALUES
(1, 1, 1, 1200.00),
(1, 2, 1, 29.99),
(2, 3, 1, 199.99),
(2, 4, 1, 45.99),
(3, 1, 1, 1200.00),
(4, 3, 2, 199.99),
(4, 4, 1, 45.99),
(4, 2, 1, 24.00);

-- PROJECT TASKS:

-- Task 1: Calculate total sales by product category
SELECT 
    p.category,
    COUNT(DISTINCT oi.order_id) AS orders,
    SUM(oi.quantity) AS units_sold,
    SUM(oi.quantity * oi.unit_price) AS total_revenue,
    AVG(oi.quantity * oi.unit_price) AS avg_order_value
FROM order_items oi
JOIN products p ON oi.product_id = p.product_id
GROUP BY p.category
ORDER BY total_revenue DESC;

-- Task 2: Find average order values by customer segment
SELECT 
    c.customer_segment,
    COUNT(DISTINCT o.order_id) AS order_count,
    SUM(o.total_amount) AS total_revenue,
    AVG(o.total_amount) AS avg_order_value,
    MIN(o.total_amount) AS min_order,
    MAX(o.total_amount) AS max_order
FROM orders o
JOIN customers c ON o.customer_id = c.customer_id
GROUP BY c.customer_segment
ORDER BY total_revenue DESC;

-- Task 3: Identify top-performing sales representatives
SELECT 
    sr.rep_name,
    sr.department,
    COUNT(o.order_id) AS orders_closed,
    SUM(o.total_amount) AS total_sales,
    AVG(o.total_amount) AS avg_deal_size,
    ROUND(SUM(o.total_amount) / COUNT(o.order_id), 2) AS avg_order_value
FROM sales_reps sr
LEFT JOIN orders o ON sr.rep_id = o.rep_id
GROUP BY sr.rep_id, sr.rep_name, sr.department
HAVING COUNT(o.order_id) > 0
ORDER BY total_sales DESC;

-- Task 4: Update product prices based on category rules
-- 10% increase for Electronics, 5% for Furniture
UPDATE products
SET price = CASE 
    WHEN category = 'Electronics' THEN price * 1.10
    WHEN category = 'Furniture' THEN price * 1.05
    ELSE price
END;

-- Verify the update
SELECT product_name, category, price
FROM products
ORDER BY category, price DESC;

-- Task 5: Clean up duplicate customer records (simulation)
-- First, identify potential duplicates
SELECT 
    email,
    COUNT(*) AS duplicate_count,
    STRING_AGG(customer_id::TEXT, ', ') AS customer_ids
FROM customers
GROUP BY email
HAVING COUNT(*) > 1;

-- Keep the oldest customer_id, mark others for deletion
-- (In real scenario, you'd merge data first)
DELETE FROM customers
WHERE customer_id IN (
    SELECT customer_id
    FROM (
        SELECT 
            customer_id,
            email,
            ROW_NUMBER() OVER (PARTITION BY email ORDER BY customer_id) AS row_num
        FROM customers
    ) ranked
    WHERE row_num > 1
);

-- Advanced Task 6: Product profitability analysis
SELECT 
    p.product_name,
    p.category,
    SUM(oi.quantity) AS units_sold,
    SUM(oi.quantity * p.cost) AS total_cost,
    SUM(oi.quantity * oi.unit_price) AS total_revenue,
    SUM(oi.quantity * oi.unit_price) - SUM(oi.quantity * p.cost) AS profit,
    ROUND(
        ((SUM(oi.quantity * oi.unit_price) - SUM(oi.quantity * p.cost)) / 
         SUM(oi.quantity * p.cost)) * 100, 
        2
    ) AS profit_margin_percentage
FROM products p
JOIN order_items oi ON p.product_id = oi.product_id
GROUP BY p.product_id, p.product_name, p.category
HAVING SUM(oi.quantity) > 0
ORDER BY profit DESC;

-- Advanced Task 7: Customer purchase frequency
SELECT 
    c.customer_id,
    CONCAT(c.first_name, ' ', c.last_name) AS customer_name,
    c.customer_segment,
    COUNT(o.order_id) AS total_orders,
    SUM(o.total_amount) AS lifetime_value,
    AVG(o.total_amount) AS avg_order_value,
    MIN(o.order_date) AS first_purchase,
    MAX(o.order_date) AS last_purchase
FROM customers c
JOIN orders o ON c.customer_id = o.customer_id
GROUP BY c.customer_id, c.first_name, c.last_name, c.customer_segment
ORDER BY lifetime_value DESC;
```

---

## Phase 2 Summary & Key Takeaways

### What You've Mastered:

**Data Modification:**
- ✅ INSERT: Adding single and multiple records efficiently
- ✅ UPDATE: Modifying data with conditions and calculations
- ✅ DELETE: Safely removing data with best practices
- ✅ Understanding TRUNCATE vs DELETE
- ✅ INSERT INTO SELECT for data copying

**Aggregation:**
- ✅ COUNT, SUM, AVG, MIN, MAX functions
- ✅ COUNT(DISTINCT) for unique values
- ✅ GROUP BY for data summarization
- ✅ HAVING clause for filtering aggregated results
- ✅ Multi-column grouping for complex analysis

### Critical Concepts:
1. **Always test SELECT before DELETE/UPDATE**
2. **WHERE filters rows, HAVING filters groups**
3. **GROUP BY requires all non-aggregated columns**
4. **Use transactions for critical data modifications**
5. **Soft deletes are safer for business data**

### Real-World Skills Gained:
- Sales analytics and reporting
- Customer segmentation
- Product performance analysis
- Data cleaning and maintenance
- Revenue calculations and profitability metrics

### Practice Recommendations:
1. Create your own sales database with realistic data
2. Practice writing reports using GROUP BY and aggregates
3. Experiment with UPDATE statements (on test data!)
4. Build dashboards using aggregation queries

**You're now ready for Phase 3: Multi-Table Operations (JOINs)**! This is where SQL gets really powerful, allowing you to combine data from multiple tables to answer complex business questions.

Ready to continue?