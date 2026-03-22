# SQL Phase 3: Multi-Table Operations - Complete Guide

## Overview
Phase 3 is where SQL becomes truly powerful. You'll learn to work with multiple tables simultaneously, understanding relationships between data, and performing complex operations that mirror real-world business scenarios.

---

## 1. Table Relationships Fundamentals

### Understanding Database Relationships

In the real world, data is interconnected. A customer places orders, orders contain products, employees belong to departments - these connections are the foundation of relational databases.

#### Primary Keys
A primary key uniquely identifies each record in a table.

```sql
-- Example: Customers table
CREATE TABLE customers (
    customer_id INT PRIMARY KEY,
    first_name VARCHAR(50) NOT NULL,
    last_name VARCHAR(50) NOT NULL,
    email VARCHAR(100) UNIQUE,
    registration_date DATE
);
```

#### Foreign Keys
A foreign key creates a link between two tables, referencing the primary key of another table.

```sql
-- Example: Orders table referencing customers
CREATE TABLE orders (
    order_id INT PRIMARY KEY,
    customer_id INT,
    order_date DATE,
    total_amount DECIMAL(10,2),
    FOREIGN KEY (customer_id) REFERENCES customers(customer_id)
);
```

### Types of Relationships

#### 1. One-to-One (1:1)
Each record in Table A relates to exactly one record in Table B.

```sql
-- Example: Employee and employee_details
CREATE TABLE employees (
    employee_id INT PRIMARY KEY,
    first_name VARCHAR(50),
    last_name VARCHAR(50)
);

CREATE TABLE employee_details (
    employee_id INT PRIMARY KEY,
    social_security VARCHAR(11),
    emergency_contact VARCHAR(100),
    FOREIGN KEY (employee_id) REFERENCES employees(employee_id)
);
```

#### 2. One-to-Many (1:M) - Most Common
One record in Table A can relate to multiple records in Table B.

```sql
-- Example: One customer can have many orders
-- customers table (already defined above)
-- orders table (already defined above)

-- Sample data
INSERT INTO customers VALUES 
(1, 'John', 'Doe', 'john@email.com', '2023-01-15'),
(2, 'Jane', 'Smith', 'jane@email.com', '2023-02-20');

INSERT INTO orders VALUES 
(101, 1, '2023-03-01', 150.00),
(102, 1, '2023-03-15', 200.00),
(103, 2, '2023-03-10', 75.00);
```

#### 3. Many-to-Many (M:N)
Multiple records in Table A can relate to multiple records in Table B. Requires a junction table.

```sql
-- Example: Students and Courses
CREATE TABLE students (
    student_id INT PRIMARY KEY,
    student_name VARCHAR(100)
);

CREATE TABLE courses (
    course_id INT PRIMARY KEY,
    course_name VARCHAR(100),
    credits INT
);

-- Junction table for many-to-many relationship
CREATE TABLE student_courses (
    student_id INT,
    course_id INT,
    enrollment_date DATE,
    grade VARCHAR(2),
    PRIMARY KEY (student_id, course_id),
    FOREIGN KEY (student_id) REFERENCES students(student_id),
    FOREIGN KEY (course_id) REFERENCES courses(course_id)
);
```

### Referential Integrity
Ensures that foreign key values always refer to existing primary key values.

```sql
-- This will work (customer_id 1 exists)
INSERT INTO orders VALUES (104, 1, '2023-04-01', 300.00);

-- This will fail (customer_id 999 doesn't exist)
-- INSERT INTO orders VALUES (105, 999, '2023-04-01', 100.00);
-- Error: Cannot add or update a child row: foreign key constraint fails
```

---

## 2. JOIN Operations - The Heart of Multi-Table Queries

JOINs combine data from multiple tables based on related columns. Think of them as ways to "connect the dots" between related information.

### INNER JOIN - The Foundation

Returns only records that have matching values in both tables.

#### Basic Syntax
```sql
SELECT columns
FROM table1
INNER JOIN table2 ON table1.column = table2.column;
```

#### Real-World Example: Customer Orders
```sql
-- Find all orders with customer information
SELECT 
    c.first_name,
    c.last_name,
    c.email,
    o.order_id,
    o.order_date,
    o.total_amount
FROM customers c
INNER JOIN orders o ON c.customer_id = o.customer_id;

-- Result:
-- John    | Doe   | john@email.com | 101 | 2023-03-01 | 150.00
-- John    | Doe   | john@email.com | 102 | 2023-03-15 | 200.00
-- Jane    | Smith | jane@email.com | 103 | 2023-03-10 | 75.00
```

#### Multiple Table INNER JOINs
```sql
-- Create products and order_items tables
CREATE TABLE products (
    product_id INT PRIMARY KEY,
    product_name VARCHAR(100),
    price DECIMAL(10,2),
    category VARCHAR(50)
);

CREATE TABLE order_items (
    order_item_id INT PRIMARY KEY,
    order_id INT,
    product_id INT,
    quantity INT,
    unit_price DECIMAL(10,2),
    FOREIGN KEY (order_id) REFERENCES orders(order_id),
    FOREIGN KEY (product_id) REFERENCES products(product_id)
);

-- Sample data
INSERT INTO products VALUES 
(1, 'Laptop Computer', 999.99, 'Electronics'),
(2, 'Wireless Mouse', 29.99, 'Electronics'),
(3, 'Office Chair', 199.99, 'Furniture');

INSERT INTO order_items VALUES 
(1, 101, 1, 1, 999.99),
(2, 101, 2, 2, 29.99),
(3, 102, 3, 1, 199.99);

-- Three-table join: customers, orders, and products
SELECT 
    c.first_name + ' ' + c.last_name AS customer_name,
    o.order_date,
    p.product_name,
    oi.quantity,
    oi.unit_price,
    (oi.quantity * oi.unit_price) AS line_total
FROM customers c
INNER JOIN orders o ON c.customer_id = o.customer_id
INNER JOIN order_items oi ON o.order_id = oi.order_id
INNER JOIN products p ON oi.product_id = p.product_id
ORDER BY o.order_date, c.last_name;
```

### LEFT JOIN (LEFT OUTER JOIN) - Including the "Missing"

Returns all records from the left table, and matched records from the right table. NULL values for non-matching right table records.

#### When to Use LEFT JOIN
- Show all customers, even those who haven't placed orders
- Display all products, including those never sold
- List all employees, including those not assigned to projects

#### Example: All Customers and Their Orders
```sql
-- Show ALL customers, even those without orders
SELECT 
    c.customer_id,
    c.first_name,
    c.last_name,
    o.order_id,
    o.order_date,
    o.total_amount
FROM customers c
LEFT JOIN orders o ON c.customer_id = o.customer_id
ORDER BY c.customer_id;

-- If we add a customer with no orders:
INSERT INTO customers VALUES (3, 'Bob', 'Johnson', 'bob@email.com', '2023-04-01');

-- The query will show:
-- 1 | John | Doe     | 101 | 2023-03-01 | 150.00
-- 1 | John | Doe     | 102 | 2023-03-15 | 200.00
-- 2 | Jane | Smith   | 103 | 2023-03-10 | 75.00
-- 3 | Bob  | Johnson | NULL| NULL       | NULL
```

#### Practical Use Case: Finding Inactive Customers
```sql
-- Find customers who haven't placed any orders
SELECT 
    c.customer_id,
    c.first_name,
    c.last_name,
    c.email
FROM customers c
LEFT JOIN orders o ON c.customer_id = o.customer_id
WHERE o.customer_id IS NULL;
```

#### LEFT JOIN with Aggregation
```sql
-- Customer order summary (including customers with 0 orders)
SELECT 
    c.first_name,
    c.last_name,
    COUNT(o.order_id) AS total_orders,
    COALESCE(SUM(o.total_amount), 0) AS total_spent
FROM customers c
LEFT JOIN orders o ON c.customer_id = o.customer_id
GROUP BY c.customer_id, c.first_name, c.last_name
ORDER BY total_spent DESC;
```

### RIGHT JOIN (RIGHT OUTER JOIN) - Less Common but Useful

Returns all records from the right table, and matched records from the left table.

```sql
-- Show all orders, even if customer data is missing (unusual scenario)
SELECT 
    c.first_name,
    c.last_name,
    o.order_id,
    o.order_date,
    o.total_amount
FROM customers c
RIGHT JOIN orders o ON c.customer_id = o.customer_id;
```

*Note: RIGHT JOIN is less commonly used. Most developers prefer LEFT JOIN for readability.*

### FULL OUTER JOIN - Everything Included

Returns all records when there's a match in either table.

```sql
-- Show all customers AND all orders (including orphaned records)
SELECT 
    c.customer_id,
    c.first_name,
    c.last_name,
    o.order_id,
    o.order_date
FROM customers c
FULL OUTER JOIN orders o ON c.customer_id = o.customer_id;
```

### Advanced JOIN Concepts

#### CROSS JOIN - Cartesian Product
Creates every possible combination of rows from both tables.

```sql
-- Example: All possible product-category combinations
CREATE TABLE sizes (size_name VARCHAR(10));
INSERT INTO sizes VALUES ('Small'), ('Medium'), ('Large');

CREATE TABLE colors (color_name VARCHAR(20));
INSERT INTO colors VALUES ('Red'), ('Blue'), ('Green');

-- Generate all size-color combinations
SELECT 
    s.size_name,
    c.color_name
FROM sizes s
CROSS JOIN colors c;

-- Results in 9 rows (3 × 3):
-- Small  | Red
-- Small  | Blue
-- Small  | Green
-- Medium | Red
-- ... etc
```

#### SELF JOIN - Table Joining Itself
Useful for hierarchical data or comparing records within the same table.

```sql
-- Employee hierarchy example
CREATE TABLE employees_hierarchy (
    employee_id INT PRIMARY KEY,
    employee_name VARCHAR(100),
    manager_id INT,
    department VARCHAR(50)
);

INSERT INTO employees_hierarchy VALUES 
(1, 'Alice Johnson', NULL, 'Executive'),
(2, 'Bob Smith', 1, 'Engineering'),
(3, 'Carol Davis', 1, 'Marketing'),
(4, 'David Wilson', 2, 'Engineering'),
(5, 'Eve Brown', 2, 'Engineering');

-- Find employees and their managers
SELECT 
    e.employee_name AS employee,
    m.employee_name AS manager,
    e.department
FROM employees_hierarchy e
LEFT JOIN employees_hierarchy m ON e.manager_id = m.employee_id
ORDER BY e.department, e.employee_name;

-- Results:
-- Alice Johnson  | NULL           | Executive
-- Bob Smith      | Alice Johnson  | Engineering
-- David Wilson   | Bob Smith      | Engineering
-- Eve Brown      | Bob Smith      | Engineering
-- Carol Davis    | Alice Johnson  | Marketing
```

#### Non-Equi JOINs
Joins using operators other than equals.

```sql
-- Example: Price ranges
CREATE TABLE price_tiers (
    tier_name VARCHAR(20),
    min_price DECIMAL(10,2),
    max_price DECIMAL(10,2)
);

INSERT INTO price_tiers VALUES 
('Budget', 0, 100),
('Mid-range', 100.01, 500),
('Premium', 500.01, 9999.99);

-- Classify products by price tier
SELECT 
    p.product_name,
    p.price,
    pt.tier_name
FROM products p
JOIN price_tiers pt ON p.price >= pt.min_price AND p.price <= pt.max_price;
```

#### Multiple JOIN Conditions
```sql
-- Join with multiple conditions for more precise matching
SELECT 
    c.first_name,
    c.last_name,
    o.order_id,
    o.total_amount
FROM customers c
INNER JOIN orders o ON c.customer_id = o.customer_id 
                   AND o.order_date >= '2023-03-01'
                   AND o.total_amount > 100;
```

---

## 3. Set Operations - Combining Query Results

Set operations combine the results of two or more SELECT statements.

### UNION and UNION ALL

#### UNION - Removes Duplicates
```sql
-- Combine customer emails from two sources
SELECT email, 'Customer' AS source 
FROM customers
UNION
SELECT contact_email, 'Supplier' AS source 
FROM suppliers;
```

#### UNION ALL - Keeps Duplicates (Faster)
```sql
-- All transactions (orders and returns)
SELECT order_id AS transaction_id, order_date AS transaction_date, total_amount
FROM orders
UNION ALL
SELECT return_id AS transaction_id, return_date AS transaction_date, -refund_amount
FROM returns;
```

### INTERSECT - Common Records
```sql
-- Find customers who are also employees
SELECT customer_id
FROM customers
INTERSECT
SELECT employee_id
FROM employees;
```

### EXCEPT/MINUS - Records in First Set Only
```sql
-- Customers who haven't placed orders this year
SELECT customer_id
FROM customers
EXCEPT
SELECT DISTINCT customer_id
FROM orders
WHERE order_date >= '2023-01-01';
```

---

## 4. Real-World Project: E-commerce Order Management System

Let's build a comprehensive e-commerce system to practice all concepts.

### Database Schema Setup
```sql
-- Complete e-commerce schema
CREATE TABLE customers (
    customer_id INT PRIMARY KEY,
    first_name VARCHAR(50),
    last_name VARCHAR(50),
    email VARCHAR(100) UNIQUE,
    phone VARCHAR(20),
    registration_date DATE,
    loyalty_tier VARCHAR(20) DEFAULT 'Bronze'
);

CREATE TABLE categories (
    category_id INT PRIMARY KEY,
    category_name VARCHAR(100),
    parent_category_id INT
);

CREATE TABLE products (
    product_id INT PRIMARY KEY,
    product_name VARCHAR(200),
    category_id INT,
    price DECIMAL(10,2),
    stock_quantity INT,
    supplier_id INT,
    created_date DATE,
    FOREIGN KEY (category_id) REFERENCES categories(category_id)
);

CREATE TABLE orders (
    order_id INT PRIMARY KEY,
    customer_id INT,
    order_date DATE,
    ship_date DATE,
    order_status VARCHAR(20),
    total_amount DECIMAL(10,2),
    discount_amount DECIMAL(10,2) DEFAULT 0,
    FOREIGN KEY (customer_id) REFERENCES customers(customer_id)
);

CREATE TABLE order_items (
    order_item_id INT PRIMARY KEY,
    order_id INT,
    product_id INT,
    quantity INT,
    unit_price DECIMAL(10,2),
    discount_percent DECIMAL(5,2) DEFAULT 0,
    FOREIGN KEY (order_id) REFERENCES orders(order_id),
    FOREIGN KEY (product_id) REFERENCES products(product_id)
);

CREATE TABLE suppliers (
    supplier_id INT PRIMARY KEY,
    supplier_name VARCHAR(100),
    contact_person VARCHAR(100),
    email VARCHAR(100),
    country VARCHAR(50)
);

-- Sample data insertion
INSERT INTO categories VALUES 
(1, 'Electronics', NULL),
(2, 'Computers', 1),
(3, 'Smartphones', 1),
(4, 'Furniture', NULL),
(5, 'Office Furniture', 4);

INSERT INTO suppliers VALUES 
(1, 'TechCorp Inc', 'John Manager', 'john@techcorp.com', 'USA'),
(2, 'Global Electronics', 'Sarah Director', 'sarah@global.com', 'China'),
(3, 'Furniture World', 'Mike Sales', 'mike@furniture.com', 'Canada');

INSERT INTO customers VALUES 
(1, 'Alice', 'Johnson', 'alice@email.com', '555-0101', '2022-01-15', 'Gold'),
(2, 'Bob', 'Smith', 'bob@email.com', '555-0102', '2022-03-20', 'Silver'),
(3, 'Carol', 'Davis', 'carol@email.com', '555-0103', '2023-01-10', 'Bronze'),
(4, 'David', 'Wilson', 'david@email.com', '555-0104', '2023-02-14', 'Bronze');

INSERT INTO products VALUES 
(1, 'Gaming Laptop Pro', 2, 1299.99, 50, 1, '2023-01-01'),
(2, 'Wireless Mouse Ultra', 2, 79.99, 200, 1, '2023-01-01'),
(3, 'Smartphone X12', 3, 899.99, 100, 2, '2023-01-15'),
(4, 'Office Chair Deluxe', 5, 299.99, 75, 3, '2023-01-01'),
(5, 'Standing Desk', 5, 499.99, 30, 3, '2023-01-20');

INSERT INTO orders VALUES 
(1001, 1, '2023-03-01', '2023-03-03', 'Delivered', 1379.98, 0),
(1002, 2, '2023-03-05', '2023-03-07', 'Delivered', 979.98, 50),
(1003, 1, '2023-03-10', '2023-03-12', 'Shipped', 799.98, 0),
(1004, 3, '2023-03-15', NULL, 'Processing', 299.99, 0),
(1005, 4, '2023-03-20', NULL, 'Processing', 1399.98, 100);

INSERT INTO order_items VALUES 
(1, 1001, 1, 1, 1299.99, 0),
(2, 1001, 2, 1, 79.99, 0),
(3, 1002, 3, 1, 899.99, 0),
(4, 1002, 2, 1, 79.99, 0),
(5, 1003, 4, 1, 299.99, 0),
(6, 1003, 5, 1, 499.99, 0),
(7, 1004, 4, 1, 299.99, 0),
(8, 1005, 1, 1, 1299.99, 0),
(9, 1005, 2, 1, 79.99, 0);
```

### Business Intelligence Queries

#### 1. Customer Lifetime Value Analysis
```sql
-- Calculate comprehensive customer metrics
SELECT 
    c.customer_id,
    c.first_name + ' ' + c.last_name AS customer_name,
    c.loyalty_tier,
    c.registration_date,
    COUNT(o.order_id) AS total_orders,
    COALESCE(SUM(o.total_amount), 0) AS lifetime_value,
    COALESCE(AVG(o.total_amount), 0) AS average_order_value,
    MAX(o.order_date) AS last_order_date,
    DATEDIFF(day, MAX(o.order_date), GETDATE()) AS days_since_last_order
FROM customers c
LEFT JOIN orders o ON c.customer_id = o.customer_id
GROUP BY c.customer_id, c.first_name, c.last_name, c.loyalty_tier, c.registration_date
ORDER BY lifetime_value DESC;
```

#### 2. Product Performance Analysis
```sql
-- Detailed product sales analysis with category information
SELECT 
    cat.category_name,
    p.product_name,
    p.price AS unit_price,
    p.stock_quantity AS current_stock,
    s.supplier_name,
    COUNT(oi.order_item_id) AS times_ordered,
    COALESCE(SUM(oi.quantity), 0) AS total_units_sold,
    COALESCE(SUM(oi.quantity * oi.unit_price), 0) AS total_revenue,
    CASE 
        WHEN COUNT(oi.order_item_id) = 0 THEN 'Never Ordered'
        WHEN COUNT(oi.order_item_id) <= 2 THEN 'Low Demand'
        WHEN COUNT(oi.order_item_id) <= 5 THEN 'Medium Demand'
        ELSE 'High Demand'
    END AS demand_category
FROM products p
LEFT JOIN categories cat ON p.category_id = cat.category_id
LEFT JOIN suppliers s ON p.supplier_id = s.supplier_id
LEFT JOIN order_items oi ON p.product_id = oi.product_id
GROUP BY cat.category_name, p.product_name, p.price, p.stock_quantity, s.supplier_name
ORDER BY total_revenue DESC;
```

#### 3. Order Analysis with Customer Segmentation
```sql
-- Complex order analysis with multiple joins
SELECT 
    o.order_id,
    c.first_name + ' ' + c.last_name AS customer_name,
    c.loyalty_tier,
    o.order_date,
    o.order_status,
    COUNT(oi.order_item_id) AS items_count,
    SUM(oi.quantity) AS total_quantity,
    o.total_amount,
    o.discount_amount,
    CASE 
        WHEN o.total_amount >= 1000 THEN 'High Value'
        WHEN o.total_amount >= 500 THEN 'Medium Value'
        ELSE 'Low Value'
    END AS order_value_category,
    STRING_AGG(p.product_name, ', ') AS products_ordered
FROM orders o
INNER JOIN customers c ON o.customer_id = c.customer_id
INNER JOIN order_items oi ON o.order_id = oi.order_id
INNER JOIN products p ON oi.product_id = p.product_id
GROUP BY o.order_id, c.first_name, c.last_name, c.loyalty_tier, 
         o.order_date, o.order_status, o.total_amount, o.discount_amount
ORDER BY o.order_date DESC;
```

#### 4. Advanced Business Questions

**Find customers who bought products but never bought from a specific category:**
```sql
-- Customers who bought electronics but never bought furniture
SELECT DISTINCT
    c.customer_id,
    c.first_name + ' ' + c.last_name AS customer_name
FROM customers c
INNER JOIN orders o ON c.customer_id = o.customer_id
INNER JOIN order_items oi ON o.order_id = oi.order_id
INNER JOIN products p ON oi.product_id = p.product_id
INNER JOIN categories cat ON p.category_id = cat.category_id
WHERE cat.category_name = 'Electronics'
  AND c.customer_id NOT IN (
    SELECT DISTINCT c2.customer_id
    FROM customers c2
    INNER JOIN orders o2 ON c2.customer_id = o2.customer_id
    INNER JOIN order_items oi2 ON o2.order_id = oi2.order_id
    INNER JOIN products p2 ON oi2.product_id = p2.product_id
    INNER JOIN categories cat2 ON p2.category_id = cat2.category_id
    WHERE cat2.category_name = 'Furniture'
  );
```

**Cross-selling opportunities:**
```sql
-- Find frequently bought together products
SELECT 
    p1.product_name AS product_1,
    p2.product_name AS product_2,
    COUNT(*) AS times_bought_together
FROM order_items oi1
INNER JOIN order_items oi2 ON oi1.order_id = oi2.order_id 
                           AND oi1.product_id < oi2.product_id
INNER JOIN products p1 ON oi1.product_id = p1.product_id
INNER JOIN products p2 ON oi2.product_id = p2.product_id
GROUP BY p1.product_name, p2.product_name
HAVING COUNT(*) > 1
ORDER BY times_bought_together DESC;
```

### Performance Optimization Tips

#### 1. Use appropriate indexes
```sql
-- Indexes for frequently joined columns
CREATE INDEX idx_orders_customer_id ON orders(customer_id);
CREATE INDEX idx_order_items_order_id ON order_items(order_id);
CREATE INDEX idx_order_items_product_id ON order_items(product_id);
CREATE INDEX idx_products_category_id ON products(category_id);
```

#### 2. Filter early in joins
```sql
-- Good: Filter before joining
SELECT c.first_name, c.last_name, o.order_date
FROM customers c
INNER JOIN (
    SELECT customer_id, order_date 
    FROM orders 
    WHERE order_date >= '2023-01-01'
) o ON c.customer_id = o.customer_id;

-- Less efficient: Filter after joining
SELECT c.first_name, c.last_name, o.order_date
FROM customers c
INNER JOIN orders o ON c.customer_id = o.customer_id
WHERE o.order_date >= '2023-01-01';
```

---

## Practice Exercises

### Exercise 1: Customer Order Summary
Write a query that shows each customer's total orders, total spent, and average order value. Include customers who haven't ordered anything.

### Exercise 2: Product Inventory Alert
Create a query that identifies products with low stock (< 50 units) and shows their supplier contact information.

### Exercise 3: Sales by Category
Build a report showing total sales by category, including subcategory details.

### Exercise 4: Customer Retention Analysis
Find customers who placed their first order more than 6 months ago but haven't ordered in the last 3 months.

---

## Key Takeaways

1. **Master the relationship types**: Understand one-to-one, one-to-many, and many-to-many relationships
2. **JOIN selection matters**: Use INNER JOIN for matching records, LEFT JOIN to include all from left table
3. **Think in business terms**: Every join should solve a real business question
4. **Performance considerations**: Index foreign keys, filter early, avoid unnecessary JOINs
5. **Practice complex scenarios**: Real-world queries often involve 3+ tables

The ability to work with multiple tables is what separates basic SQL users from advanced practitioners. These concepts form the foundation for complex business intelligence, reporting, and analytics that drive real business decisions.

Next up: Phase 4 will introduce subqueries and advanced functions to make your queries even more powerful!