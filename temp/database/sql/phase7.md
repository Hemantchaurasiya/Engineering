# Phase 7: Advanced Database Features - Complete Guide

## Overview
Phase 7 focuses on enterprise-level SQL features that enable complex business logic, data integrity, and advanced data management. These features transform databases from simple storage systems into intelligent, self-managing platforms.

---

## 1. Advanced Data Types

### 1.1 JSON/XML Handling

#### JSON in Modern Databases

JSON (JavaScript Object Notation) has become essential for storing semi-structured data, API responses, and configuration data.

**PostgreSQL JSON Examples:**

```sql
-- Creating a table with JSON column
CREATE TABLE products (
    id SERIAL PRIMARY KEY,
    name VARCHAR(100),
    specifications JSONB,  -- JSONB is binary JSON (faster)
    created_at TIMESTAMP DEFAULT NOW()
);

-- Inserting JSON data
INSERT INTO products (name, specifications) VALUES 
('Laptop Pro', '{
    "brand": "TechCorp",
    "specs": {
        "cpu": "Intel i7",
        "ram": "16GB",
        "storage": "512GB SSD"
    },
    "features": ["backlit keyboard", "touchscreen", "fingerprint reader"],
    "price": 1299.99,
    "availability": {
        "in_stock": true,
        "quantity": 45
    }
}'),
('Gaming Mouse', '{
    "brand": "GameGear",
    "specs": {
        "dpi": 12000,
        "buttons": 8,
        "wireless": true
    },
    "features": ["RGB lighting", "programmable buttons"],
    "price": 79.99,
    "availability": {
        "in_stock": false,
        "quantity": 0
    }
}');

-- Querying JSON data
-- Extract simple values
SELECT name, specifications->>'brand' as brand,
       (specifications->>'price')::NUMERIC as price
FROM products;

-- Query nested JSON
SELECT name, 
       specifications->'specs'->>'cpu' as cpu,
       specifications->'specs'->>'ram' as ram
FROM products
WHERE specifications->'specs'->>'cpu' LIKE '%i7%';

-- Query JSON arrays
SELECT name, feature
FROM products,
     jsonb_array_elements_text(specifications->'features') as feature
WHERE feature = 'touchscreen';

-- Complex JSON queries with conditions
SELECT name, 
       specifications->>'brand' as brand,
       (specifications->>'price')::NUMERIC as price
FROM products
WHERE (specifications->'availability'->>'in_stock')::BOOLEAN = true
  AND (specifications->>'price')::NUMERIC < 1000;

-- Update JSON data
UPDATE products 
SET specifications = jsonb_set(
    specifications, 
    '{availability,quantity}', 
    '25'::jsonb
)
WHERE name = 'Gaming Mouse';

-- Add new JSON properties
UPDATE products 
SET specifications = specifications || '{"warranty": "2 years"}'::jsonb
WHERE name = 'Laptop Pro';
```

**SQL Server JSON Examples:**

```sql
-- SQL Server JSON handling
CREATE TABLE customer_data (
    id INT IDENTITY PRIMARY KEY,
    customer_info NVARCHAR(MAX) CHECK (ISJSON(customer_info) > 0),
    created_date DATETIME2 DEFAULT GETDATE()
);

INSERT INTO customer_data (customer_info) VALUES 
(N'{
    "name": "John Doe",
    "contact": {
        "email": "john@email.com",
        "phone": "+1-555-0123"
    },
    "orders": [
        {"id": 1001, "amount": 250.00, "date": "2024-01-15"},
        {"id": 1002, "amount": 180.50, "date": "2024-02-20"}
    ],
    "preferences": {
        "newsletter": true,
        "marketing": false
    }
}');

-- Query JSON in SQL Server
SELECT 
    JSON_VALUE(customer_info, '$.name') as customer_name,
    JSON_VALUE(customer_info, '$.contact.email') as email,
    JSON_VALUE(customer_info, '$.preferences.newsletter') as newsletter_opt_in
FROM customer_data;

-- Extract JSON arrays
SELECT 
    c.id,
    JSON_VALUE(c.customer_info, '$.name') as customer_name,
    JSON_VALUE(order_data.value, '$.id') as order_id,
    CAST(JSON_VALUE(order_data.value, '$.amount') AS DECIMAL(10,2)) as amount
FROM customer_data c
CROSS APPLY OPENJSON(customer_info, '$.orders') order_data;

-- Modify JSON data
UPDATE customer_data 
SET customer_info = JSON_MODIFY(
    customer_info, 
    '$.preferences.marketing', 
    'true'
)
WHERE JSON_VALUE(customer_info, '$.name') = 'John Doe';
```

#### XML Handling

```sql
-- PostgreSQL XML example
CREATE TABLE documents (
    id SERIAL PRIMARY KEY,
    content XML,
    title VARCHAR(200)
);

INSERT INTO documents (title, content) VALUES 
('Product Catalog', 
'<catalog>
    <product id="1">
        <name>Wireless Headphones</name>
        <category>Electronics</category>
        <price currency="USD">199.99</price>
        <features>
            <feature>Noise Cancelling</feature>
            <feature>Bluetooth 5.0</feature>
        </features>
    </product>
    <product id="2">
        <name>Smart Watch</name>
        <category>Wearables</category>
        <price currency="USD">299.99</price>
        <features>
            <feature>Heart Rate Monitor</feature>
            <feature>GPS</feature>
        </features>
    </product>
</catalog>');

-- XPath queries
SELECT title,
       xpath('//product/name/text()', content) as product_names,
       xpath('//product[@id="1"]/price/text()', content) as first_product_price
FROM documents;

-- SQL Server XML
DECLARE @xml XML = '
<employees>
    <employee id="1">
        <name>Alice Johnson</name>
        <department>Engineering</department>
        <salary>95000</salary>
    </employee>
    <employee id="2">
        <name>Bob Smith</name>
        <department>Marketing</department>
        <salary>75000</salary>
    </employee>
</employees>';

-- Query XML with XQuery
SELECT 
    emp.value('@id', 'INT') as employee_id,
    emp.value('name[1]', 'VARCHAR(50)') as name,
    emp.value('department[1]', 'VARCHAR(50)') as department,
    emp.value('salary[1]', 'INT') as salary
FROM @xml.nodes('/employees/employee') as T(emp);
```

### 1.2 Arrays and Complex Types

**PostgreSQL Array Operations:**

```sql
-- Create table with array columns
CREATE TABLE survey_responses (
    id SERIAL PRIMARY KEY,
    respondent_name VARCHAR(100),
    favorite_colors TEXT[],
    ratings INTEGER[],
    tags TEXT[]
);

-- Insert array data
INSERT INTO survey_responses (respondent_name, favorite_colors, ratings, tags) VALUES 
('Alice', ARRAY['blue', 'green', 'purple'], ARRAY[8, 9, 7, 6], ARRAY['tech', 'music', 'travel']),
('Bob', ARRAY['red', 'black'], ARRAY[7, 8, 9], ARRAY['sports', 'food']),
('Carol', ARRAY['yellow', 'orange', 'pink'], ARRAY[6, 7, 8, 9, 10], ARRAY['art', 'books', 'music', 'travel']);

-- Array queries
-- Find people who like 'blue'
SELECT respondent_name, favorite_colors
FROM survey_responses
WHERE 'blue' = ANY(favorite_colors);

-- Get array length
SELECT respondent_name, 
       array_length(favorite_colors, 1) as color_count,
       array_length(ratings, 1) as rating_count
FROM survey_responses;

-- Unnest arrays (convert to rows)
SELECT respondent_name, unnest(favorite_colors) as color
FROM survey_responses;

-- Array aggregation
SELECT array_agg(DISTINCT color ORDER BY color) as all_colors
FROM (
    SELECT unnest(favorite_colors) as color
    FROM survey_responses
) t;

-- Complex array operations
SELECT respondent_name,
       favorite_colors[1] as first_color,  -- Array indexing (1-based)
       ratings[1:3] as first_three_ratings,  -- Array slicing
       array_cat(favorite_colors, ARRAY['white']) as colors_with_white
FROM survey_responses;
```

---

## 2. Procedural SQL

### 2.1 Stored Procedures

Stored procedures encapsulate business logic within the database, improving performance and maintaining consistency.

**SQL Server Stored Procedure Examples:**

```sql
-- Basic stored procedure for customer management
CREATE PROCEDURE sp_GetCustomerOrders
    @CustomerID INT,
    @StartDate DATE = NULL,
    @EndDate DATE = NULL
AS
BEGIN
    SET NOCOUNT ON;
    
    -- Input validation
    IF @CustomerID IS NULL OR @CustomerID <= 0
    BEGIN
        RAISERROR('Invalid Customer ID provided', 16, 1);
        RETURN;
    END
    
    -- Set default dates if not provided
    IF @StartDate IS NULL SET @StartDate = DATEADD(YEAR, -1, GETDATE());
    IF @EndDate IS NULL SET @EndDate = GETDATE();
    
    -- Main query
    SELECT 
        o.order_id,
        o.order_date,
        o.total_amount,
        c.customer_name,
        c.email,
        COUNT(oi.item_id) as item_count
    FROM orders o
    INNER JOIN customers c ON o.customer_id = c.customer_id
    LEFT JOIN order_items oi ON o.order_id = oi.order_id
    WHERE o.customer_id = @CustomerID
      AND o.order_date BETWEEN @StartDate AND @EndDate
    GROUP BY o.order_id, o.order_date, o.total_amount, c.customer_name, c.email
    ORDER BY o.order_date DESC;
END;

-- Execute the procedure
EXEC sp_GetCustomerOrders @CustomerID = 123, @StartDate = '2024-01-01';
```

**Complex Stored Procedure with Error Handling:**

```sql
CREATE PROCEDURE sp_ProcessOrder
    @CustomerID INT,
    @ProductID INT,
    @Quantity INT,
    @OrderID INT OUTPUT
AS
BEGIN
    SET NOCOUNT ON;
    BEGIN TRY
        BEGIN TRANSACTION;
        
        DECLARE @StockLevel INT, @Price DECIMAL(10,2), @TotalAmount DECIMAL(10,2);
        
        -- Check product availability
        SELECT @StockLevel = stock_quantity, @Price = unit_price
        FROM products 
        WHERE product_id = @ProductID;
        
        IF @StockLevel IS NULL
        BEGIN
            RAISERROR('Product not found', 16, 1);
            RETURN;
        END
        
        IF @StockLevel < @Quantity
        BEGIN
            RAISERROR('Insufficient stock. Available: %d, Requested: %d', 16, 1, @StockLevel, @Quantity);
            RETURN;
        END
        
        -- Calculate total
        SET @TotalAmount = @Price * @Quantity;
        
        -- Create order
        INSERT INTO orders (customer_id, order_date, total_amount, status)
        VALUES (@CustomerID, GETDATE(), @TotalAmount, 'Pending');
        
        SET @OrderID = SCOPE_IDENTITY();
    
    SAVE TRANSACTION CreateOrder;
    
    -- Step 3: Add order items with stock validation
    DECLARE @ProductID INT = 101;
    DECLARE @Quantity INT = 5;
    DECLARE @UnitPrice DECIMAL(10,2);
    DECLARE @StockLevel INT;
    
    -- Check stock
    SELECT @UnitPrice = unit_price, @StockLevel = stock_quantity
    FROM products WHERE product_id = @ProductID;
    
    IF @StockLevel < @Quantity
    BEGIN
        ROLLBACK TRANSACTION CreateOrder;
        RAISERROR('Insufficient stock for product %d. Available: %d, Requested: %d', 16, 1, @ProductID, @StockLevel, @Quantity);
        RETURN;
    END
    
    -- Add order item
    INSERT INTO order_items (order_id, product_id, quantity, unit_price, subtotal)
    VALUES (@OrderID, @ProductID, @Quantity, @UnitPrice, @UnitPrice * @Quantity);
    
    -- Update order total
    UPDATE orders 
    SET total_amount = @UnitPrice * @Quantity
    WHERE order_id = @OrderID;
    
    -- Update stock
    UPDATE products 
    SET stock_quantity = stock_quantity - @Quantity
    WHERE product_id = @ProductID;
    
    SAVE TRANSACTION AddItems;
    
    -- Step 4: Apply customer discount if applicable
    DECLARE @DiscountPercent DECIMAL(5,2) = 0;
    
    -- Check if customer qualifies for new customer discount
    IF NOT EXISTS (SELECT 1 FROM orders WHERE customer_id = @CustomerID AND order_id != @OrderID)
    BEGIN
        SET @DiscountPercent = 10.0; -- 10% new customer discount
        
        UPDATE orders 
        SET discount_percent = @DiscountPercent,
            total_amount = total_amount * (1 - @DiscountPercent / 100)
        WHERE order_id = @OrderID;
        
        -- Log discount application
        INSERT INTO discount_log (order_id, discount_type, discount_percent, applied_date)
        VALUES (@OrderID, 'NEW_CUSTOMER', @DiscountPercent, GETDATE());
    END
    
    -- Step 5: Create loyalty points
    DECLARE @PointsEarned INT;
    SELECT @PointsEarned = CAST(total_amount AS INT) -- 1 point per dollar
    FROM orders WHERE order_id = @OrderID;
    
    INSERT INTO loyalty_points (customer_id, points_earned, points_type, order_id, earned_date)
    VALUES (@CustomerID, @PointsEarned, 'PURCHASE', @OrderID, GETDATE());
    
    -- Final validation
    IF @@TRANCOUNT > 0
    BEGIN
        COMMIT TRANSACTION;
        SELECT 'SUCCESS' as Status, @OrderID as OrderID, @CustomerID as CustomerID;
    END
    
END TRY
BEGIN CATCH
    IF @@TRANCOUNT > 0
        ROLLBACK TRANSACTION;
        
    DECLARE @ErrorMessage NVARCHAR(4000) = ERROR_MESSAGE();
    DECLARE @ErrorSeverity INT = ERROR_SEVERITY();
    DECLARE @ErrorState INT = ERROR_STATE();
    
    RAISERROR(@ErrorMessage, @ErrorSeverity, @ErrorState);
END CATCH;
```

**Isolation Levels and Locking:**

```sql
-- Demonstrate different isolation levels
-- READ UNCOMMITTED - Allows dirty reads
SET TRANSACTION ISOLATION LEVEL READ UNCOMMITTED;
BEGIN TRANSACTION;
    SELECT customer_id, customer_name, account_balance 
    FROM customers 
    WHERE customer_id = 123;
    -- This might read uncommitted changes from other transactions
COMMIT;

-- READ COMMITTED - Default level, prevents dirty reads
SET TRANSACTION ISOLATION LEVEL READ COMMITTED;
BEGIN TRANSACTION;
    SELECT customer_id, customer_name, account_balance 
    FROM customers 
    WHERE customer_id = 123;
    -- Will only read committed data, but might see different values in subsequent reads
COMMIT;

-- REPEATABLE READ - Prevents dirty and non-repeatable reads
SET TRANSACTION ISOLATION LEVEL REPEATABLE READ;
BEGIN TRANSACTION;
    SELECT customer_id, customer_name, account_balance 
    FROM customers 
    WHERE customer_id = 123;
    
    -- Some other operations...
    WAITFOR DELAY '00:00:05';
    
    SELECT customer_id, customer_name, account_balance 
    FROM customers 
    WHERE customer_id = 123;
    -- Second read will return same values as first read
COMMIT;

-- SERIALIZABLE - Highest isolation level
SET TRANSACTION ISOLATION LEVEL SERIALIZABLE;
BEGIN TRANSACTION;
    SELECT COUNT(*) as customer_count
    FROM customers 
    WHERE registration_date >= '2024-01-01';
    
    -- Phantom reads are prevented - count will be same even if 
    -- other transactions try to insert new customers
    
    WAITFOR DELAY '00:00:03';
    
    SELECT COUNT(*) as customer_count
    FROM customers 
    WHERE registration_date >= '2024-01-01';
COMMIT;

-- Explicit locking hints
-- Shared lock (prevents modifications)
SELECT customer_id, customer_name, account_balance
FROM customers WITH (HOLDLOCK, ROWLOCK)
WHERE customer_id = 123;

-- Exclusive lock (prevents reads and modifications)
SELECT customer_id, customer_name, account_balance
FROM customers WITH (XLOCK, ROWLOCK)
WHERE customer_id = 123;

-- No lock (dirty read)
SELECT customer_id, customer_name, account_balance
FROM customers WITH (NOLOCK)
WHERE account_balance > 1000;
```

**Deadlock Prevention and Handling:**

```sql
CREATE PROCEDURE sp_SafeTransferFunds
    @FromCustomerID INT,
    @ToCustomerID INT,
    @Amount DECIMAL(10,2)
AS
BEGIN
    SET NOCOUNT ON;
    
    -- Always acquire locks in same order to prevent deadlocks
    DECLARE @FirstCustomer INT = CASE WHEN @FromCustomerID < @ToCustomerID THEN @FromCustomerID ELSE @ToCustomerID END;
    DECLARE @SecondCustomer INT = CASE WHEN @FromCustomerID < @ToCustomerID THEN @ToCustomerID ELSE @FromCustomerID END;
    
    DECLARE @RetryCount INT = 0;
    DECLARE @MaxRetries INT = 3;
    
    WHILE @RetryCount < @MaxRetries
    BEGIN
        BEGIN TRY
            BEGIN TRANSACTION;
            
            -- Lock accounts in consistent order
            UPDATE customers 
            SET account_balance = account_balance 
            WHERE customer_id = @FirstCustomer;
            
            UPDATE customers 
            SET account_balance = account_balance 
            WHERE customer_id = @SecondCustomer;
            
            -- Validate sufficient funds
            DECLARE @FromBalance DECIMAL(10,2);
            SELECT @FromBalance = account_balance 
            FROM customers 
            WHERE customer_id = @FromCustomerID;
            
            IF @FromBalance < @Amount
            BEGIN
                ROLLBACK TRANSACTION;
                RAISERROR('Insufficient funds', 16, 1);
                RETURN;
            END
            
            -- Perform transfer
            UPDATE customers 
            SET account_balance = account_balance - @Amount,
                last_updated = GETDATE()
            WHERE customer_id = @FromCustomerID;
            
            UPDATE customers 
            SET account_balance = account_balance + @Amount,
                last_updated = GETDATE()
            WHERE customer_id = @ToCustomerID;
            
            -- Log transaction
            INSERT INTO transfer_log (from_customer_id, to_customer_id, amount, transfer_date)
            VALUES (@FromCustomerID, @ToCustomerID, @Amount, GETDATE());
            
            COMMIT TRANSACTION;
            
            SELECT 'SUCCESS' as Status, @Amount as AmountTransferred;
            RETURN;
            
        END TRY
        BEGIN CATCH
            IF @@TRANCOUNT > 0
                ROLLBACK TRANSACTION;
            
            -- Handle deadlock specifically
            IF ERROR_NUMBER() = 1205 -- Deadlock
            BEGIN
                SET @RetryCount = @RetryCount + 1;
                WAITFOR DELAY '00:00:01'; -- Wait before retry
                CONTINUE;
            END
            ELSE
            BEGIN
                -- Re-throw other errors
                DECLARE @ErrorMessage NVARCHAR(4000) = ERROR_MESSAGE();
                RAISERROR(@ErrorMessage, ERROR_SEVERITY(), ERROR_STATE());
                RETURN;
            END
        END CATCH
    END
    
    -- Max retries exceeded
    RAISERROR('Transaction failed after %d attempts due to deadlocks', 16, 1, @MaxRetries);
END;
```

---

## Real-World Project 7: Enterprise HR Application Backend

Let's build a comprehensive HR system that demonstrates all Phase 7 concepts:

### Database Schema Setup

```sql
-- Core HR tables with advanced features
CREATE TABLE employees (
    employee_id INT IDENTITY PRIMARY KEY,
    employee_number VARCHAR(20) UNIQUE NOT NULL,
    first_name VARCHAR(50) NOT NULL,
    last_name VARCHAR(50) NOT NULL,
    email VARCHAR(100) UNIQUE NOT NULL,
    phone VARCHAR(20),
    hire_date DATE NOT NULL,
    department_id INT,
    position_id INT,
    manager_id INT,
    salary DECIMAL(10,2),
    status VARCHAR(20) DEFAULT 'Active',
    employee_data NVARCHAR(MAX), -- JSON for flexible attributes
    created_date DATETIME2 DEFAULT GETDATE(),
    updated_date DATETIME2 DEFAULT GETDATE(),
    
    CONSTRAINT fk_emp_department FOREIGN KEY (department_id) REFERENCES departments(department_id),
    CONSTRAINT fk_emp_position FOREIGN KEY (position_id) REFERENCES positions(position_id),
    CONSTRAINT fk_emp_manager FOREIGN KEY (manager_id) REFERENCES employees(employee_id),
    CONSTRAINT chk_emp_data_json CHECK (ISJSON(employee_data) = 1)
);

CREATE TABLE departments (
    department_id INT IDENTITY PRIMARY KEY,
    department_name VARCHAR(100) NOT NULL,
    department_head_id INT,
    budget DECIMAL(12,2),
    location VARCHAR(100),
    created_date DATETIME2 DEFAULT GETDATE()
);

CREATE TABLE positions (
    position_id INT IDENTITY PRIMARY KEY,
    position_title VARCHAR(100) NOT NULL,
    department_id INT,
    min_salary DECIMAL(10,2),
    max_salary DECIMAL(10,2),
    job_description TEXT,
    requirements NVARCHAR(MAX) -- JSON for flexible requirements
);

CREATE TABLE performance_reviews (
    review_id INT IDENTITY PRIMARY KEY,
    employee_id INT NOT NULL,
    reviewer_id INT NOT NULL,
    review_period_start DATE,
    review_period_end DATE,
    overall_rating DECIMAL(3,2),
    goals_met DECIMAL(3,2),
    strengths TEXT,
    areas_for_improvement TEXT,
    review_data NVARCHAR(MAX), -- JSON for detailed metrics
    status VARCHAR(20) DEFAULT 'Draft',
    created_date DATETIME2 DEFAULT GETDATE(),
    completed_date DATETIME2,
    
    CONSTRAINT fk_pr_employee FOREIGN KEY (employee_id) REFERENCES employees(employee_id),
    CONSTRAINT fk_pr_reviewer FOREIGN KEY (reviewer_id) REFERENCES employees(employee_id)
);

CREATE TABLE audit_log (
    audit_id BIGINT IDENTITY PRIMARY KEY,
    table_name VARCHAR(100),
    operation CHAR(1), -- I, U, D
    record_id INT,
    old_values NVARCHAR(MAX),
    new_values NVARCHAR(MAX),
    changed_by VARCHAR(100),
    changed_date DATETIME2 DEFAULT GETDATE(),
    ip_address VARCHAR(45)
);
```

### Advanced Stored Procedures

```sql
-- Complex employee onboarding procedure
CREATE PROCEDURE sp_OnboardEmployee
    @FirstName VARCHAR(50),
    @LastName VARCHAR(50),
    @Email VARCHAR(100),
    @DepartmentID INT,
    @PositionID INT,
    @ManagerID INT = NULL,
    @StartDate DATE = NULL,
    @AdditionalData NVARCHAR(MAX) = NULL,
    @EmployeeID INT OUTPUT
AS
BEGIN
    SET NOCOUNT ON;
    
    DECLARE @ErrorMessage NVARCHAR(4000);
    DECLARE @EmployeeNumber VARCHAR(20);
    DECLARE @Salary DECIMAL(10,2);
    
    BEGIN TRY
        BEGIN TRANSACTION;
        
        -- Validate inputs
        IF @StartDate IS NULL SET @StartDate = GETDATE();
        
        -- Check if email already exists
        IF EXISTS(SELECT 1 FROM employees WHERE email = @Email AND status = 'Active')
        BEGIN
            RAISERROR('Employee with email %s already exists', 16, 1, @Email);
            RETURN;
        END
        
        -- Validate department and position
        IF NOT EXISTS(SELECT 1 FROM departments WHERE department_id = @DepartmentID)
        BEGIN
            RAISERROR('Invalid department ID: %d', 16, 1, @DepartmentID);
            RETURN;
        END
        
        IF NOT EXISTS(SELECT 1 FROM positions WHERE position_id = @PositionID)
        BEGIN
            RAISERROR('Invalid position ID: %d', 16, 1, @PositionID);
            RETURN;
        END
        
        -- Generate employee number
        DECLARE @DeptCode VARCHAR(3);
        SELECT @DeptCode = LEFT(UPPER(department_name), 3)
        FROM departments WHERE department_id = @DepartmentID;
        
        DECLARE @NextNumber INT;
        SELECT @NextNumber = COALESCE(MAX(CAST(RIGHT(employee_number, 4) AS INT)), 0) + 1
        FROM employees 
        WHERE employee_number LIKE @DeptCode + '%';
        
        SET @EmployeeNumber = @DeptCode + RIGHT('0000' + CAST(@NextNumber AS VARCHAR(4)), 4);
        
        -- Get default salary for position
        SELECT @Salary = min_salary FROM positions WHERE position_id = @PositionID;
        
        -- Create employee record with JSON data
        DECLARE @DefaultEmployeeData NVARCHAR(MAX) = JSON_MODIFY(
            COALESCE(@AdditionalData, '{}'),
            '$.onboarding_status', 'In Progress'
        );
        
        SET @DefaultEmployeeData = JSON_MODIFY(@DefaultEmployeeData, '$.onboarding_date', FORMAT(@StartDate, 'yyyy-MM-dd'));
        
        INSERT INTO employees (
            employee_number, first_name, last_name, email, 
            hire_date, department_id, position_id, manager_id, 
            salary, employee_data, status
        )
        VALUES (
            @EmployeeNumber, @FirstName, @LastName, @Email,
            @StartDate, @DepartmentID, @PositionID, @ManagerID,
            @Salary, @DefaultEmployeeData, 'Active'
        );
        
        SET @EmployeeID = SCOPE_IDENTITY();
        
        -- Create onboarding tasks
        INSERT INTO onboarding_tasks (employee_id, task_name, task_description, due_date, status)
        VALUES 
            (@EmployeeID, 'Complete I-9 Form', 'Verify work eligibility', DATEADD(DAY, 3, @StartDate), 'Pending'),
            (@EmployeeID, 'Benefits Enrollment', 'Choose health and retirement benefits', DATEADD(DAY, 14, @StartDate), 'Pending'),
            (@EmployeeID, 'IT Setup', 'Setup computer and accounts', @StartDate, 'Pending'),
            (@EmployeeID, 'Safety Training', 'Complete mandatory safety training', DATEADD(DAY, 7, @StartDate), 'Pending');
        
        -- Notify manager if assigned
        IF @ManagerID IS NOT NULL
        BEGIN
            INSERT INTO notifications (recipient_id, message, notification_type, created_date)
            SELECT @ManagerID, 
                   CONCAT('New employee ', @FirstName, ' ', @LastName, ' has been assigned to your team'),
                   'NEW_TEAM_MEMBER',
                   GETDATE();
        END
        
        -- Create initial performance review schedule
        INSERT INTO performance_reviews (
            employee_id, reviewer_id, review_period_start, review_period_end, 
            status, review_data
        )
        VALUES (
            @EmployeeID, 
            COALESCE(@ManagerID, (SELECT department_head_id FROM departments WHERE department_id = @DepartmentID)),
            @StartDate,
            DATEADD(DAY, 90, @StartDate), -- 90-day review
            'Scheduled',
            '{"review_type": "90_day_new_hire", "goals": []}'
        );
        
        COMMIT TRANSACTION;
        
        -- Return success information
        SELECT 
            'SUCCESS' as Status,
            @EmployeeID as EmployeeID,
            @EmployeeNumber as EmployeeNumber,
            'Employee successfully onboarded' as Message;
            
    END TRY
    BEGIN CATCH
        IF @@TRANCOUNT > 0
            ROLLBACK TRANSACTION;
            
        SET @ErrorMessage = ERROR_MESSAGE();
        
        -- Log the error
        INSERT INTO error_log (procedure_name, error_message, parameters, timestamp)
        VALUES ('sp_OnboardEmployee', @ErrorMessage, 
                CONCAT('Email:', @Email, ', Dept:', @DepartmentID), GETDATE());
        
        RAISERROR(@ErrorMessage, ERROR_SEVERITY(), ERROR_STATE());
    END CATCH
END;
```

### Advanced Functions and Views

```sql
-- Function to calculate comprehensive employee metrics
CREATE FUNCTION dbo.fn_CalculateEmployeeMetrics(@EmployeeID INT)
RETURNS TABLE
AS
RETURN
(
    WITH employee_data AS (
        SELECT 
            e.employee_id,
            e.hire_date,
            e.salary,
            DATEDIFF(DAY, e.hire_date, GETDATE()) as days_employed,
            DATEDIFF(MONTH, e.hire_date, GETDATE()) as months_employed,
            JSON_VALUE(e.employee_data, '$.onboarding_status') as onboarding_status
        FROM employees e
        WHERE e.employee_id = @EmployeeID
    ),
    performance_data AS (
        SELECT 
            employee_id,
            COUNT(*) as total_reviews,
            AVG(overall_rating) as avg_rating,
            MAX(completed_date) as last_review_date
        FROM performance_reviews
        WHERE employee_id = @EmployeeID AND status = 'Completed'
        GROUP BY employee_id
    ),
    training_data AS (
        SELECT 
            employee_id,
            COUNT(*) as courses_completed,
            SUM(CASE WHEN completion_date >= DATEADD(YEAR, -1, GETDATE()) THEN 1 ELSE 0 END) as recent_training
        FROM training_records
        WHERE employee_id = @EmployeeID AND status = 'Completed'
        GROUP BY employee_id
    )
    SELECT 
        ed.employee_id,
        ed.days_employed,
        ed.months_employed,
        ed.salary,
        ed.onboarding_status,
        COALESCE(pd.total_reviews, 0) as total_reviews,
        COALESCE(pd.avg_rating, 0) as average_performance_rating,
        pd.last_review_date,
        COALESCE(td.courses_completed, 0) as total_training_completed,
        COALESCE(td.recent_training, 0) as recent_training_count,
        
        -- Calculated metrics
        CASE 
            WHEN ed.months_employed <= 3 THEN 'New Hire'
            WHEN ed.months_employed <= 12 THEN 'Junior'
            WHEN ed.months_employed <= 36 THEN 'Experienced'
            ELSE 'Senior'
        END as tenure_category,
        
        CASE 
            WHEN COALESCE(pd.avg_rating, 0) >= 4.5 THEN 'Exceptional'
            WHEN COALESCE(pd.avg_rating, 0) >= 3.5 THEN 'Meets Expectations'
            WHEN COALESCE(pd.avg_rating, 0) >= 2.5 THEN 'Below Expectations'
            ELSE 'Needs Improvement'
        END as performance_category
        
    FROM employee_data ed
    LEFT JOIN performance_data pd ON ed.employee_id = pd.employee_id
    LEFT JOIN training_data td ON ed.employee_id = td.employee_id
);

-- Usage
SELECT * FROM dbo.fn_CalculateEmployeeMetrics(123);
```

### Comprehensive Audit Triggers

```sql
-- Advanced audit trigger for employees table
CREATE TRIGGER tr_employees_comprehensive_audit
ON employees
FOR INSERT, UPDATE, DELETE
AS
BEGIN
    SET NOCOUNT ON;
    
    DECLARE @Operation CHAR(1);
    DECLARE @UserName VARCHAR(100) = SYSTEM_USER;
    DECLARE @IPAddress VARCHAR(45) = CONVERT(VARCHAR(45), CONNECTIONPROPERTY('client_net_address'));
    
    -- Determine operation type
    IF EXISTS(SELECT * FROM inserted) AND EXISTS(SELECT * FROM deleted)
        SET @Operation = 'U'; -- UPDATE
    ELSE IF EXISTS(SELECT * FROM inserted)
        SET @Operation = 'I'; -- INSERT
    ELSE
        SET @Operation = 'D'; -- DELETE
    
    -- Handle INSERT operations
    IF @Operation = 'I'
    BEGIN
        INSERT INTO audit_log (table_name, operation, record_id, new_values, changed_by, ip_address)
        SELECT 
            'employees',
            'I',
            i.employee_id,
            (SELECT i.* FOR JSON PATH, WITHOUT_ARRAY_WRAPPER),
            @UserName,
            @IPAddress
        FROM inserted i;
        
        -- Send notification for new hires
        INSERT INTO notifications (recipient_id, message, notification_type)
        SELECT 
            d.department_head_id,
            CONCAT('New employee hired: ', i.first_name, ' ', i.last_name, ' in ', d.department_name),
            'NEW_HIRE'
        FROM inserted i
        JOIN departments d ON i.department_id = d.department_id
        WHERE d.department_head_id IS NOT NULL;
    END
    
    -- Handle UPDATE operations
    IF @Operation = 'U'
    BEGIN
        INSERT INTO audit_log (table_name, operation, record_id, old_values, new_values, changed_by, ip_address)
        SELECT 
            'employees',
            'U',
            i.employee_id,
            (SELECT d.* FOR JSON PATH, WITHOUT_ARRAY_WRAPPER),
            (SELECT i.* FOR JSON PATH, WITHOUT_ARRAY_WRAPPER),
            @UserName,
            @IPAddress
        FROM inserted i
        JOIN deleted d ON i.employee_id = d.employee_id
        WHERE EXISTS (
            -- Only audit if there are actual changes
            SELECT d.first_name, d.last_name, d.email, d.department_id, d.position_id, 
                   d.manager_id, d.salary, d.status
            EXCEPT 
            SELECT i.first_name, i.last_name, i.email, i.department_id, i.position_id, 
                   i.manager_id, i.salary, i.status
        );
        
        -- Special handling for sensitive changes
        -- Salary changes
        IF EXISTS (
            SELECT * FROM inserted i 
            JOIN deleted d ON i.employee_id = d.employee_id 
            WHERE i.salary != d.salary
        )
        BEGIN
            INSERT INTO sensitive_changes_log (table_name, record_id, field_changed, old_value, new_value, changed_by, change_date)
            SELECT 
                'employees',
                i.employee_id,
                'salary',
                CAST(d.salary AS VARCHAR(20)),
                CAST(i.salary AS VARCHAR(20)),
                @UserName,
                GETDATE()
            FROM inserted i
            JOIN deleted d ON i.employee_id = d.employee_id
            WHERE i.salary != d.salary;
        END
        
        -- Manager changes
        IF EXISTS (
            SELECT * FROM inserted i 
            JOIN deleted d ON i.employee_id = d.employee_id 
            WHERE ISNULL(i.manager_id, 0) != ISNULL(d.manager_id, 0)
        )
        BEGIN
            -- Notify old manager
            INSERT INTO notifications (recipient_id, message, notification_type)
            SELECT 
                d.manager_id,
                CONCAT(d.first_name, ' ', d.last_name, ' is no longer reporting to you'),
                'TEAM_CHANGE'
            FROM deleted d
            JOIN inserted i ON d.employee_id = i.employee_id
            WHERE d.manager_id IS NOT NULL 
              AND ISNULL(i.manager_id, 0) != ISNULL(d.manager_id, 0);
            
            -- Notify new manager
            INSERT INTO notifications (recipient_id, message, notification_type)
            SELECT 
                i.manager_id,
                CONCAT(i.first_name, ' ', i.last_name, ' now reports to you'),
                'TEAM_CHANGE'
            FROM inserted i
            JOIN deleted d ON i.employee_id = d.employee_id
            WHERE i.manager_id IS NOT NULL 
              AND ISNULL(i.manager_id, 0) != ISNULL(d.manager_id, 0);
        END
    END
    
    -- Handle DELETE operations
    IF @Operation = 'D'
    BEGIN
        INSERT INTO audit_log (table_name, operation, record_id, old_values, changed_by, ip_address)
        SELECT 
            'employees',
            'D',
            d.employee_id,
            (SELECT d.* FOR JSON PATH, WITHOUT_ARRAY_WRAPPER),
            @UserName,
            @IPAddress
        FROM deleted d;
        
        -- Archive employee data instead of hard delete (if this is a soft delete trigger)
        INSERT INTO employees_archive (
            original_employee_id, employee_number, first_name, last_name, email,
            hire_date, termination_date, department_id, position_id, manager_id,
            salary, status, employee_data, archived_date, archived_by
        )
        SELECT 
            d.employee_id, d.employee_number, d.first_name, d.last_name, d.email,
            d.hire_date, GETDATE(), d.department_id, d.position_id, d.manager_id,
            d.salary, 'Terminated', d.employee_data, GETDATE(), @UserName
        FROM deleted d;
    END
END;
```

### Advanced Analytics Views

```sql
-- Comprehensive HR dashboard view
CREATE VIEW vw_hr_analytics_dashboard AS
WITH department_metrics AS (
    SELECT 
        d.department_id,
        d.department_name,
        COUNT(e.employee_id) as current_headcount,
        AVG(e.salary) as avg_salary,
        MIN(e.hire_date) as oldest_hire_date,
        MAX(e.hire_date) as newest_hire_date,
        AVG(DATEDIFF(DAY, e.hire_date, GETDATE())) as avg_tenure_days,
        
        -- Performance metrics
        AVG(pr.overall_rating) as avg_performance_rating,
        
        -- Turnover in last 12 months
        (SELECT COUNT(*) 
         FROM employees_archive ea 
         WHERE ea.department_id = d.department_id 
           AND ea.termination_date >= DATEADD(YEAR, -1, GETDATE())) as turnover_12_months,
        
        -- JSON aggregation of salary ranges
        (SELECT 
            JSON_OBJECT(
                'min_salary', MIN(e2.salary),
                'max_salary', MAX(e2.salary),
                'median_salary', PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY e2.salary) OVER()
            )
         FROM employees e2 
         WHERE e2.department_id = d.department_id 
           AND e2.status = 'Active') as salary_stats
           
    FROM departments d
    LEFT JOIN employees e ON d.department_id = e.department_id AND e.status = 'Active'
    LEFT JOIN performance_reviews pr ON e.employee_id = pr.employee_id 
                                     AND pr.status = 'Completed' 
                                     AND pr.review_period_end >= DATEADD(YEAR, -1, GETDATE())
    GROUP BY d.department_id, d.department_name
),
hiring_trends AS (
    SELECT 
        d.department_id,
        COUNT(CASE WHEN e.hire_date >= DATEADD(QUARTER, -1, GETDATE()) THEN 1 END) as hires_last_quarter,
        COUNT(CASE WHEN e.hire_date >= DATEADD(YEAR, -1, GETDATE()) THEN 1 END) as hires_last_year
    FROM departments d
    LEFT JOIN employees e ON d.department_id = e.department_id
    GROUP BY d.department_id
)
SELECT 
    dm.department_id,
    dm.department_name,
    dm.current_headcount,
    dm.avg_salary,
    dm.avg_tenure_days,
    dm.avg_performance_rating,
    
    -- Calculate turnover rate
    CASE 
        WHEN dm.current_headcount > 0 THEN 
            CAST(dm.turnover_12_months AS DECIMAL(5,2)) / dm.current_headcount * 100
        ELSE 0 
    END as turnover_rate_percent,
    
    ht.hires_last_quarter,
    ht.hires_last_year,
    
    -- Growth rate
    CASE 
        WHEN dm.current_headcount > ht.hires_last_year THEN
            CAST(ht.hires_last_year AS DECIMAL(5,2)) / (dm.current_headcount - ht.hires_last_year) * 100
        ELSE 0
    END as growth_rate_percent,
    
    dm.salary_stats,
    
    -- Risk indicators
    CASE 
        WHEN CAST(dm.turnover_12_months AS DECIMAL(5,2)) / NULLIF(dm.current_headcount, 0) > 0.2 THEN 'High Turnover Risk'
        WHEN dm.avg_performance_rating < 3.0 THEN 'Low Performance Risk'
        WHEN dm.avg_tenure_days < 365 THEN 'New Team Risk'
        ELSE 'Stable'
    END as risk_indicator
    
FROM department_metrics dm
JOIN hiring_trends ht ON dm.department_id = ht.department_id;

-- Usage examples
SELECT * FROM vw_hr_analytics_dashboard 
WHERE risk_indicator != 'Stable' 
ORDER BY turnover_rate_percent DESC;

SELECT 
    department_name,
    current_headcount,
    FORMAT(avg_salary, 'C') as formatted_avg_salary,
    CAST(avg_performance_rating AS DECIMAL(3,2)) as performance_rating,
    risk_indicator
FROM vw_hr_analytics_dashboard
ORDER BY current_headcount DESC;
```

## Summary and Key Takeaways

Phase 7 introduces you to enterprise-level database features that transform databases from simple storage systems into intelligent, self-managing platforms. Here's what you've mastered:

### 1. Advanced Data Types Mastery
- **JSON/XML Handling**: Store and query semi-structured data efficiently
- **Arrays and Complex Types**: Handle complex data structures within database columns
- **Real-world Applications**: Product catalogs, user preferences, configuration data

### 2. Procedural SQL Excellence
- **Stored Procedures**: Encapsulate business logic with proper error handling and transactions
- **Functions**: Create reusable code components for calculations and data transformations  
- **Triggers**: Implement automatic data validation, auditing, and business rules
- **Control Flow**: Use conditional logic and loops for complex processing

### 3. Advanced Database Features
- **Views and Materialized Views**: Create virtual tables and pre-computed result sets
- **Transaction Management**: Handle complex multi-step operations with ACID compliance
- **Concurrency Control**: Manage isolation levels and prevent deadlocks
- **Security**: Implement proper access control and audit trails

### 4. Enterprise Patterns Implemented
- **Comprehensive Auditing**: Track all changes with detailed logging
- **Error Handling**: Robust exception management with retry logic
- **Performance Optimization**: Efficient queries and proper indexing strategies
- **Business Logic Enforcement**: Rules implemented at the database level

---

## Practice Exercises

### Exercise 1: E-commerce Order Processing
Create a comprehensive order processing system with:
```sql
-- Challenge: Build a complete order processing pipeline
-- Requirements:
-- 1. Validate inventory before order creation
-- 2. Apply discounts based on customer tier (stored in JSON)
-- 3. Handle payment processing with proper transaction management
-- 4. Send notifications using triggers
-- 5. Maintain audit trail for all operations
-- 6. Implement retry logic for concurrency issues

-- Your implementation here...
CREATE PROCEDURE sp_ProcessCompleteOrder
    @CustomerID INT,
    @OrderItems NVARCHAR(MAX), -- JSON array of items
    @PaymentMethod VARCHAR(50),
    @OrderID INT OUTPUT
AS
BEGIN
    -- Your comprehensive solution here
    -- Include: validation, inventory checks, discounts, payments, notifications
END;
```

### Exercise 2: Financial Reconciliation System
Build a financial system that handles:
```sql
-- Challenge: Create a bank reconciliation system
-- Requirements:
-- 1. Process transaction batches with atomic operations
-- 2. Handle currency conversions (store rates in JSON)
-- 3. Implement automatic reconciliation rules
-- 4. Create materialized views for reporting
-- 5. Audit all financial transactions
-- 6. Handle regulatory compliance requirements

-- Create the necessary tables, procedures, and triggers
-- Your implementation...
```

### Exercise 3: Healthcare Patient Management
Design a patient management system:
```sql
-- Challenge: Build HIPAA-compliant patient system
-- Requirements:
-- 1. Store patient data with encryption considerations
-- 2. Implement role-based access through views
-- 3. Track all access to sensitive data
-- 4. Handle medical history in JSON format
-- 5. Automatic alerts for critical values
-- 6. Compliance reporting capabilities

-- Your comprehensive solution...
```

---

## Next Steps: Advanced Topics to Explore

### 1. Database Security Deep Dive
```sql
-- Row-level security implementation
CREATE SECURITY POLICY patient_security_policy
    ADD FILTER PREDICATE dbo.fn_securitypredicate(patient_id) ON patients,
    ADD BLOCK PREDICATE dbo.fn_securitypredicate(patient_id) ON patients;

-- Dynamic data masking
ALTER TABLE patients
ALTER COLUMN ssn ADD MASKED WITH (FUNCTION = 'partial(1,"XXX-XX-",4)');
```

### 2. Advanced Performance Tuning
```sql
-- Partitioning strategies
CREATE PARTITION FUNCTION pf_monthly_sales(DATE)
AS RANGE RIGHT FOR VALUES 
('2024-01-01', '2024-02-01', '2024-03-01', ...);

-- Columnstore indexes for analytics
CREATE CLUSTERED COLUMNSTORE INDEX cci_sales_fact ON sales_fact;
```

### 3. Integration Patterns
```sql
-- Service Broker for async messaging
-- CLR integration for complex calculations  
-- Linked servers for cross-database operations
```

---

## Real-World Implementation Checklist

### Before Production Deployment:
- [ ] **Security Review**: Audit all permissions and access patterns
- [ ] **Performance Testing**: Load test all procedures under realistic conditions
- [ ] **Backup Strategy**: Implement and test recovery procedures
- [ ] **Monitoring Setup**: Configure alerts for performance and errors
- [ ] **Documentation**: Complete technical and user documentation
- [ ] **Code Review**: Peer review of all database objects
- [ ] **Compliance Check**: Ensure regulatory requirements are met

### Ongoing Maintenance:
- [ ] **Regular Statistics Updates**: Maintain query plan efficiency
- [ ] **Index Maintenance**: Rebuild/reorganize as needed
- [ ] **Archive Old Data**: Implement data lifecycle management
- [ ] **Monitor Growth**: Track database size and performance trends
- [ ] **Security Updates**: Regular review of access and permissions
- [ ] **Performance Tuning**: Continuous optimization based on usage patterns

---

## Industry Best Practices Learned

### 1. Code Quality Standards
- Always use parameterized queries to prevent SQL injection
- Implement comprehensive error handling with proper logging
- Use consistent naming conventions across all database objects
- Document complex business logic within stored procedures

### 2. Performance Optimization
- Design indexes based on query patterns, not table structure
- Use appropriate isolation levels for different transaction types
- Implement proper connection pooling and resource management
- Monitor and tune regularly based on actual usage patterns

### 3. Security and Compliance  
- Implement principle of least privilege for all database access
- Maintain comprehensive audit trails for sensitive operations
- Use encryption for data at rest and in transit
- Regular security assessments and penetration testing

### 4. Operational Excellence
- Automate routine maintenance tasks
- Implement proper backup and disaster recovery procedures
- Monitor system health and performance continuously
- Plan for scalability from the beginning

---

## Congratulations! 🎉

You've successfully completed Phase 7 and now possess enterprise-level SQL skills including:

✅ **Advanced Data Types**: JSON, XML, Arrays, and Complex Types  
✅ **Procedural Programming**: Stored Procedures, Functions, and Triggers  
✅ **Transaction Management**: ACID compliance and concurrency control  
✅ **Enterprise Features**: Views, security, and audit systems  
✅ **Real-World Application**: Complete HR system implementation  

### Your SQL Journey Status:
- **Beginner Level**: ✅ Completed
- **Intermediate Level**: ✅ Completed  
- **Advanced Level**: ✅ Completed
- **Enterprise Level**: ✅ **Current - Phase 7 Complete!**

### Ready for Phase 8: Specialized SQL & Modern Features
You're now prepared to tackle cutting-edge SQL capabilities including:
- Advanced Analytics and Statistical Functions
- Graph Database Queries and Recursive Operations
- Database-Specific Advanced Features
- Data Integration and ETL Processes
- Machine Learning Integration with SQL

**Continue to Phase 8 when you're ready to explore the most advanced SQL capabilities and become a true SQL expert!** 🚀
        
        -- Add order items
        INSERT INTO order_items (order_id, product_id, quantity, unit_price, subtotal)
        VALUES (@OrderID, @ProductID, @Quantity, @Price, @TotalAmount);
        
        -- Update stock
        UPDATE products 
        SET stock_quantity = stock_quantity - @Quantity,
            last_updated = GETDATE()
        WHERE product_id = @ProductID;
        
        -- Log the transaction
        INSERT INTO audit_log (table_name, operation, record_id, timestamp, details)
        VALUES ('orders', 'INSERT', @OrderID, GETDATE(), 
                CONCAT('Order created for customer ', @CustomerID, ', amount: ', @TotalAmount));
        
        COMMIT TRANSACTION;
        
        SELECT 'Success' as Status, @OrderID as OrderID, @TotalAmount as TotalAmount;
        
    END TRY
    BEGIN CATCH
        ROLLBACK TRANSACTION;
        
        DECLARE @ErrorMessage NVARCHAR(4000) = ERROR_MESSAGE();
        DECLARE @ErrorSeverity INT = ERROR_SEVERITY();
        DECLARE @ErrorState INT = ERROR_STATE();
        
        -- Log error
        INSERT INTO error_log (error_message, severity, state, procedure_name, timestamp)
        VALUES (@ErrorMessage, @ErrorSeverity, @ErrorState, 'sp_ProcessOrder', GETDATE());
        
        RAISERROR(@ErrorMessage, @ErrorSeverity, @ErrorState);
    END CATCH
END;

-- Execute with output parameter
DECLARE @NewOrderID INT;
EXEC sp_ProcessOrder 
    @CustomerID = 123, 
    @ProductID = 456, 
    @Quantity = 2, 
    @OrderID = @NewOrderID OUTPUT;
    
SELECT @NewOrderID as CreatedOrderID;
```

**PostgreSQL Stored Functions:**

```sql
-- PostgreSQL function for complex calculations
CREATE OR REPLACE FUNCTION calculate_customer_metrics(
    p_customer_id INTEGER,
    p_date_from DATE DEFAULT NULL,
    p_date_to DATE DEFAULT NULL
)
RETURNS TABLE(
    customer_id INTEGER,
    total_orders BIGINT,
    total_spent NUMERIC,
    average_order_value NUMERIC,
    days_since_last_order INTEGER,
    customer_tier TEXT
) 
LANGUAGE plpgsql
AS $$
DECLARE
    v_date_from DATE := COALESCE(p_date_from, CURRENT_DATE - INTERVAL '1 year');
    v_date_to DATE := COALESCE(p_date_to, CURRENT_DATE);
BEGIN
    RETURN QUERY
    WITH customer_stats AS (
        SELECT 
            o.customer_id,
            COUNT(*)::BIGINT as order_count,
            SUM(o.total_amount) as total_amount,
            AVG(o.total_amount) as avg_amount,
            MAX(o.order_date) as last_order_date
        FROM orders o
        WHERE o.customer_id = p_customer_id
          AND o.order_date BETWEEN v_date_from AND v_date_to
        GROUP BY o.customer_id
    )
    SELECT 
        cs.customer_id,
        cs.order_count,
        cs.total_amount,
        cs.avg_amount,
        (CURRENT_DATE - cs.last_order_date)::INTEGER as days_since_last,
        CASE 
            WHEN cs.total_amount > 10000 THEN 'Platinum'
            WHEN cs.total_amount > 5000 THEN 'Gold'
            WHEN cs.total_amount > 1000 THEN 'Silver'
            ELSE 'Bronze'
        END as tier
    FROM customer_stats cs;
END;
$$;

-- Usage
SELECT * FROM calculate_customer_metrics(123, '2024-01-01'::DATE);
```

### 2.2 Functions and Triggers

**User-Defined Functions:**

```sql
-- Scalar function (SQL Server)
CREATE FUNCTION dbo.CalculateAge(@BirthDate DATE)
RETURNS INT
AS
BEGIN
    RETURN DATEDIFF(YEAR, @BirthDate, GETDATE()) - 
           CASE WHEN DATEADD(YEAR, DATEDIFF(YEAR, @BirthDate, GETDATE()), @BirthDate) > GETDATE() 
                THEN 1 ELSE 0 END;
END;

-- Table-valued function
CREATE FUNCTION dbo.GetTopCustomers(@Year INT, @TopCount INT = 10)
RETURNS TABLE
AS
RETURN
(
    SELECT TOP (@TopCount)
        c.customer_id,
        c.customer_name,
        c.email,
        SUM(o.total_amount) as yearly_total,
        COUNT(o.order_id) as order_count
    FROM customers c
    INNER JOIN orders o ON c.customer_id = o.customer_id
    WHERE YEAR(o.order_date) = @Year
    GROUP BY c.customer_id, c.customer_name, c.email
    ORDER BY SUM(o.total_amount) DESC
);

-- Usage
SELECT * FROM dbo.GetTopCustomers(2024, 5);
```

**Triggers for Audit and Business Logic:**

```sql
-- Audit trigger (SQL Server)
CREATE TABLE audit_customers (
    audit_id INT IDENTITY PRIMARY KEY,
    customer_id INT,
    operation CHAR(1), -- I, U, D
    old_values NVARCHAR(MAX),
    new_values NVARCHAR(MAX),
    changed_by NVARCHAR(100),
    changed_date DATETIME2 DEFAULT GETDATE()
);

CREATE TRIGGER tr_customers_audit
ON customers
AFTER INSERT, UPDATE, DELETE
AS
BEGIN
    SET NOCOUNT ON;
    
    -- Handle INSERT
    IF EXISTS(SELECT * FROM inserted) AND NOT EXISTS(SELECT * FROM deleted)
    BEGIN
        INSERT INTO audit_customers (customer_id, operation, new_values, changed_by)
        SELECT 
            customer_id, 
            'I',
            CONCAT('name:', customer_name, ', email:', email, ', phone:', phone),
            SYSTEM_USER
        FROM inserted;
    END
    
    -- Handle UPDATE
    IF EXISTS(SELECT * FROM inserted) AND EXISTS(SELECT * FROM deleted)
    BEGIN
        INSERT INTO audit_customers (customer_id, operation, old_values, new_values, changed_by)
        SELECT 
            i.customer_id,
            'U',
            CONCAT('name:', d.customer_name, ', email:', d.email, ', phone:', d.phone),
            CONCAT('name:', i.customer_name, ', email:', i.email, ', phone:', i.phone),
            SYSTEM_USER
        FROM inserted i
        INNER JOIN deleted d ON i.customer_id = d.customer_id;
    END
    
    -- Handle DELETE
    IF NOT EXISTS(SELECT * FROM inserted) AND EXISTS(SELECT * FROM deleted)
    BEGIN
        INSERT INTO audit_customers (customer_id, operation, old_values, changed_by)
        SELECT 
            customer_id,
            'D',
            CONCAT('name:', customer_name, ', email:', email, ', phone:', phone),
            SYSTEM_USER
        FROM deleted;
    END
END;
```

**Business Logic Trigger:**

```sql
-- Inventory management trigger
CREATE TRIGGER tr_update_inventory
ON order_items
AFTER INSERT
AS
BEGIN
    SET NOCOUNT ON;
    
    -- Update product stock levels
    UPDATE p
    SET stock_quantity = p.stock_quantity - i.quantity,
        last_updated = GETDATE()
    FROM products p
    INNER JOIN inserted i ON p.product_id = i.product_id;
    
    -- Check for low stock and create alerts
    INSERT INTO inventory_alerts (product_id, alert_type, message, created_date)
    SELECT 
        p.product_id,
        'LOW_STOCK',
        CONCAT('Product ', p.product_name, ' is low in stock. Current level: ', p.stock_quantity),
        GETDATE()
    FROM products p
    INNER JOIN inserted i ON p.product_id = i.product_id
    WHERE p.stock_quantity <= p.reorder_level
      AND p.stock_quantity > 0;
    
    -- Out of stock alerts
    INSERT INTO inventory_alerts (product_id, alert_type, message, created_date)
    SELECT 
        p.product_id,
        'OUT_OF_STOCK',
        CONCAT('Product ', p.product_name, ' is out of stock!'),
        GETDATE()
    FROM products p
    INNER JOIN inserted i ON p.product_id = i.product_id
    WHERE p.stock_quantity <= 0;
END;
```

### 2.3 Control Flow

**Conditional Logic and Loops:**

```sql
-- SQL Server control flow example
CREATE PROCEDURE sp_ProcessMonthlyReports
AS
BEGIN
    DECLARE @CurrentMonth INT = MONTH(GETDATE());
    DECLARE @CurrentYear INT = YEAR(GETDATE());
    DECLARE @ReportType VARCHAR(20);
    DECLARE @CustomerID INT;
    DECLARE @Counter INT = 1;
    
    -- Determine report type based on month
    IF @CurrentMonth IN (3, 6, 9, 12)
        SET @ReportType = 'Quarterly';
    ELSE IF @CurrentMonth = 12
        SET @ReportType = 'Annual';
    ELSE
        SET @ReportType = 'Monthly';
    
    PRINT CONCAT('Processing ', @ReportType, ' reports for ', @CurrentMonth, '/', @CurrentYear);
    
    -- Process reports for each customer
    DECLARE customer_cursor CURSOR FOR
        SELECT customer_id FROM customers WHERE status = 'Active';
    
    OPEN customer_cursor;
    FETCH NEXT FROM customer_cursor INTO @CustomerID;
    
    WHILE @@FETCH_STATUS = 0
    BEGIN
        BEGIN TRY
            -- Process customer report
            EXEC sp_GenerateCustomerReport @CustomerID, @ReportType;
            PRINT CONCAT('Processed report for customer ', @CustomerID);
            
        END TRY
        BEGIN CATCH
            PRINT CONCAT('Error processing customer ', @CustomerID, ': ', ERROR_MESSAGE());
            
            -- Log error
            INSERT INTO error_log (customer_id, error_message, timestamp)
            VALUES (@CustomerID, ERROR_MESSAGE(), GETDATE());
        END CATCH
        
        FETCH NEXT FROM customer_cursor INTO @CustomerID;
        SET @Counter = @Counter + 1;
        
        -- Process in batches to avoid long locks
        IF @Counter % 100 = 0
        BEGIN
            PRINT CONCAT('Processed ', @Counter, ' customers so far...');
            WAITFOR DELAY '00:00:01'; -- Brief pause
        END
    END
    
    CLOSE customer_cursor;
    DEALLOCATE customer_cursor;
    
    PRINT CONCAT('Completed processing ', @Counter - 1, ' customer reports');
END;
```

**PostgreSQL Control Flow:**

```sql
CREATE OR REPLACE FUNCTION process_customer_tiers()
RETURNS TEXT
LANGUAGE plpgsql
AS $$
DECLARE
    rec RECORD;
    total_processed INTEGER := 0;
    total_upgraded INTEGER := 0;
    result_message TEXT;
BEGIN
    -- Loop through all customers
    FOR rec IN 
        SELECT customer_id, customer_name, 
               SUM(total_amount) as yearly_total
        FROM customers c
        JOIN orders o ON c.customer_id = o.customer_id
        WHERE o.order_date >= CURRENT_DATE - INTERVAL '1 year'
        GROUP BY c.customer_id, c.customer_name
    LOOP
        total_processed := total_processed + 1;
        
        -- Determine new tier
        CASE 
            WHEN rec.yearly_total >= 10000 THEN
                UPDATE customers SET tier = 'Platinum' WHERE customer_id = rec.customer_id;
                IF FOUND THEN total_upgraded := total_upgraded + 1; END IF;
                
            WHEN rec.yearly_total >= 5000 THEN
                UPDATE customers SET tier = 'Gold' WHERE customer_id = rec.customer_id;
                IF FOUND THEN total_upgraded := total_upgraded + 1; END IF;
                
            WHEN rec.yearly_total >= 1000 THEN
                UPDATE customers SET tier = 'Silver' WHERE customer_id = rec.customer_id;
                IF FOUND THEN total_upgraded := total_upgraded + 1; END IF;
                
            ELSE
                UPDATE customers SET tier = 'Bronze' WHERE customer_id = rec.customer_id;
        END CASE;
        
        -- Log progress every 100 customers
        IF total_processed % 100 = 0 THEN
            RAISE NOTICE 'Processed % customers', total_processed;
        END IF;
    END LOOP;
    
    result_message := format('Processed %s customers, upgraded %s tiers', 
                           total_processed, total_upgraded);
    
    -- Insert summary log
    INSERT INTO process_log (process_name, message, timestamp)
    VALUES ('customer_tier_update', result_message, NOW());
    
    RETURN result_message;
END;
$$;

-- Execute the function
SELECT process_customer_tiers();
```

---

## 3. Advanced Features

### 3.1 Views and Materialized Views

**Complex Views:**

```sql
-- Create a comprehensive sales dashboard view
CREATE VIEW vw_sales_dashboard AS
SELECT 
    c.customer_id,
    c.customer_name,
    c.email,
    c.registration_date,
    c.tier,
    COUNT(DISTINCT o.order_id) as total_orders,
    COALESCE(SUM(o.total_amount), 0) as lifetime_value,
    COALESCE(AVG(o.total_amount), 0) as average_order_value,
    MAX(o.order_date) as last_order_date,
    DATEDIFF(DAY, MAX(o.order_date), GETDATE()) as days_since_last_order,
    COUNT(DISTINCT YEAR(o.order_date)) as active_years,
    
    -- Current year metrics
    COUNT(CASE WHEN YEAR(o.order_date) = YEAR(GETDATE()) THEN o.order_id END) as orders_this_year,
    COALESCE(SUM(CASE WHEN YEAR(o.order_date) = YEAR(GETDATE()) THEN o.total_amount END), 0) as revenue_this_year,
    
    -- Quarterly metrics
    COUNT(CASE WHEN o.order_date >= DATEADD(QUARTER, -1, GETDATE()) THEN o.order_id END) as orders_last_quarter,
    COALESCE(SUM(CASE WHEN o.order_date >= DATEADD(QUARTER, -1, GETDATE()) THEN o.total_amount END), 0) as revenue_last_quarter,
    
    -- Product diversity
    COUNT(DISTINCT oi.product_id) as unique_products_purchased,
    COUNT(DISTINCT p.category_id) as categories_purchased,
    
    -- Behavioral indicators
    CASE 
        WHEN MAX(o.order_date) >= DATEADD(DAY, -30, GETDATE()) THEN 'Active'
        WHEN MAX(o.order_date) >= DATEADD(DAY, -90, GETDATE()) THEN 'At Risk'
        ELSE 'Churned'
    END as customer_status
    
FROM customers c
LEFT JOIN orders o ON c.customer_id = o.customer_id
LEFT JOIN order_items oi ON o.order_id = oi.order_id
LEFT JOIN products p ON oi.product_id = p.product_id
GROUP BY c.customer_id, c.customer_name, c.email, c.registration_date, c.tier;

-- Query the view
SELECT 
    customer_name,
    tier,
    lifetime_value,
    customer_status,
    days_since_last_order
FROM vw_sales_dashboard
WHERE customer_status = 'At Risk'
  AND lifetime_value > 1000
ORDER BY lifetime_value DESC;
```

**Materialized Views (PostgreSQL):**

```sql
-- Create materialized view for heavy analytics
CREATE MATERIALIZED VIEW mv_monthly_sales_summary AS
SELECT 
    DATE_TRUNC('month', o.order_date) as month_year,
    c.tier,
    p.category_name,
    COUNT(DISTINCT o.order_id) as total_orders,
    COUNT(DISTINCT o.customer_id) as unique_customers,
    SUM(oi.quantity) as total_items_sold,
    SUM(o.total_amount) as total_revenue,
    AVG(o.total_amount) as average_order_value,
    
    -- Growth metrics (compare to previous month)
    LAG(SUM(o.total_amount)) OVER (
        PARTITION BY c.tier, p.category_name 
        ORDER BY DATE_TRUNC('month', o.order_date)
    ) as previous_month_revenue,
    
    -- Calculate growth rate
    CASE 
        WHEN LAG(SUM(o.total_amount)) OVER (
            PARTITION BY c.tier, p.category_name 
            ORDER BY DATE_TRUNC('month', o.order_date)
        ) > 0 THEN
            ((SUM(o.total_amount) - LAG(SUM(o.total_amount)) OVER (
                PARTITION BY c.tier, p.category_name 
                ORDER BY DATE_TRUNC('month', o.order_date)
            )) / LAG(SUM(o.total_amount)) OVER (
                PARTITION BY c.tier, p.category_name 
                ORDER BY DATE_TRUNC('month', o.order_date)
            ) * 100)
        ELSE NULL
    END as growth_rate_percent
    
FROM orders o
JOIN customers c ON o.customer_id = c.customer_id
JOIN order_items oi ON o.order_id = oi.order_id
JOIN products p ON oi.product_id = p.product_id
JOIN categories cat ON p.category_id = cat.category_id
WHERE o.order_date >= DATE_TRUNC('month', CURRENT_DATE - INTERVAL '24 months')
GROUP BY DATE_TRUNC('month', o.order_date), c.tier, p.category_name, cat.category_name;

-- Create indexes on materialized view
CREATE INDEX idx_mv_monthly_sales_month ON mv_monthly_sales_summary(month_year);
CREATE INDEX idx_mv_monthly_sales_tier ON mv_monthly_sales_summary(tier);
CREATE INDEX idx_mv_monthly_sales_category ON mv_monthly_sales_summary(category_name);

-- Refresh materialized view (run regularly via scheduled job)
REFRESH MATERIALIZED VIEW mv_monthly_sales_summary;

-- Query the materialized view
SELECT 
    month_year,
    category_name,
    total_revenue,
    growth_rate_percent
FROM mv_monthly_sales_summary
WHERE tier = 'Gold'
  AND month_year >= DATE_TRUNC('month', CURRENT_DATE - INTERVAL '6 months')
ORDER BY month_year DESC, total_revenue DESC;
```

### 3.2 Transactions and Concurrency

**Advanced Transaction Management:**

```sql
-- Complex transaction with savepoints
BEGIN TRANSACTION;

BEGIN TRY
    -- Step 1: Create customer
    DECLARE @CustomerID INT;
    INSERT INTO customers (customer_name, email, phone, registration_date)
    VALUES ('New Customer', 'new@email.com', '555-0123', GETDATE());
    SET @CustomerID = SCOPE_IDENTITY();
    
    SAVE TRANSACTION CreateCustomer;
    
    -- Step 2: Create initial order
    DECLARE @OrderID INT;
    INSERT INTO orders (customer_id, order_date, total_amount, status)
    VALUES (@CustomerID, GETDATE(), 0, 'Pending');
    SET @OrderID = SCOPE_IDENTITY();