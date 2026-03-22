# Phase 8: Specialized SQL & Modern Features
*Master cutting-edge SQL capabilities and specializations*

## 1. Modern SQL Features

### 1.1 Advanced Analytics Functions

#### PIVOT and UNPIVOT Operations
Transform rows to columns and vice versa for better data presentation.

**PIVOT Example: Sales by Quarter**
```sql
-- Original data: monthly sales
SELECT * FROM sales_data;
-- ProductID | Month | Sales
-- 1         | Jan   | 1000
-- 1         | Feb   | 1200
-- 1         | Mar   | 900

-- PIVOT to show quarters as columns
SELECT ProductID,
  [Q1] = [Jan] + [Feb] + [Mar],
  [Q2] = [Apr] + [May] + [Jun],
  [Q3] = [Jul] + [Aug] + [Sep],
  [Q4] = [Oct] + [Nov] + [Dec]
FROM (
  SELECT ProductID, Month, Sales
  FROM sales_data
) AS SourceTable
PIVOT (
  SUM(Sales) FOR Month IN ([Jan],[Feb],[Mar],[Apr],[May],[Jun],
                          [Jul],[Aug],[Sep],[Oct],[Nov],[Dec])
) AS PivotTable;
```

**UNPIVOT Example: Normalize Quarterly Data**
```sql
-- Convert columns back to rows
SELECT ProductID, Quarter, Sales
FROM (
  SELECT ProductID, Q1, Q2, Q3, Q4
  FROM quarterly_sales
) AS SourceTable
UNPIVOT (
  Sales FOR Quarter IN (Q1, Q2, Q3, Q4)
) AS UnpivotTable;
```

#### GROUPING SETS, CUBE, ROLLUP
Advanced grouping for comprehensive reporting.

**GROUPING SETS Example: Multi-dimensional Analysis**
```sql
-- Get sales totals by different grouping combinations
SELECT 
  Region,
  Product,
  SalesRep,
  SUM(SalesAmount) AS TotalSales,
  GROUPING(Region) AS RegionGrouping,
  GROUPING(Product) AS ProductGrouping,
  GROUPING(SalesRep) AS SalesRepGrouping
FROM sales_fact
GROUP BY GROUPING SETS (
  (Region, Product, SalesRep),    -- Detailed level
  (Region, Product),              -- By region and product
  (Region),                       -- By region only
  (Product),                      -- By product only
  ()                              -- Grand total
)
ORDER BY RegionGrouping, ProductGrouping, SalesRepGrouping;
```

**CUBE Example: All Possible Combinations**
```sql
-- Generate all possible grouping combinations
SELECT 
  CASE WHEN GROUPING(Region) = 1 THEN 'All Regions' ELSE Region END AS Region,
  CASE WHEN GROUPING(Product) = 1 THEN 'All Products' ELSE Product END AS Product,
  CASE WHEN GROUPING(Year) = 1 THEN 'All Years' ELSE CAST(Year AS VARCHAR) END AS Year,
  SUM(SalesAmount) AS TotalSales,
  COUNT(*) AS TransactionCount
FROM sales_fact
GROUP BY CUBE(Region, Product, Year)
ORDER BY GROUPING(Region), GROUPING(Product), GROUPING(Year);
```

**ROLLUP Example: Hierarchical Totals**
```sql
-- Create hierarchical subtotals and grand total
SELECT 
  Region,
  State,
  City,
  SUM(Population) AS TotalPopulation
FROM demographics
GROUP BY ROLLUP(Region, State, City)
ORDER BY Region, State, City;
```

#### Advanced Statistical Functions

**Percentile and Distribution Functions**
```sql
-- Calculate various percentiles and statistical measures
SELECT 
  product_category,
  price,
  -- Percentile calculations
  PERCENTILE_CONT(0.25) WITHIN GROUP (ORDER BY price) OVER (PARTITION BY product_category) AS Q1,
  PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY price) OVER (PARTITION BY product_category) AS Median,
  PERCENTILE_CONT(0.75) WITHIN GROUP (ORDER BY price) OVER (PARTITION BY product_category) AS Q3,
  PERCENTILE_CONT(0.9) WITHIN GROUP (ORDER BY price) OVER (PARTITION BY product_category) AS P90,
  
  -- Percentile rank of each product
  PERCENT_RANK() OVER (PARTITION BY product_category ORDER BY price) AS PricePercentileRank,
  
  -- Distribution into quartiles
  NTILE(4) OVER (PARTITION BY product_category ORDER BY price) AS PriceQuartile
FROM products;
```

**Time-Series Specific Functions**
```sql
-- Advanced time-series analysis
WITH monthly_sales AS (
  SELECT 
    DATE_TRUNC('month', order_date) AS month,
    SUM(total_amount) AS monthly_revenue
  FROM orders
  GROUP BY DATE_TRUNC('month', order_date)
)
SELECT 
  month,
  monthly_revenue,
  
  -- Moving averages
  AVG(monthly_revenue) OVER (
    ORDER BY month 
    ROWS BETWEEN 2 PRECEDING AND CURRENT ROW
  ) AS moving_avg_3m,
  
  -- Year-over-year growth
  LAG(monthly_revenue, 12) OVER (ORDER BY month) AS same_month_prev_year,
  
  CASE 
    WHEN LAG(monthly_revenue, 12) OVER (ORDER BY month) IS NOT NULL
    THEN ((monthly_revenue - LAG(monthly_revenue, 12) OVER (ORDER BY month)) * 100.0 / 
          LAG(monthly_revenue, 12) OVER (ORDER BY month))
  END AS yoy_growth_percent,
  
  -- Seasonal decomposition (basic)
  monthly_revenue - AVG(monthly_revenue) OVER (
    ORDER BY month 
    ROWS BETWEEN 5 PRECEDING AND 6 FOLLOWING
  ) AS seasonal_component
FROM monthly_sales
ORDER BY month;
```

### 1.2 Graph Database Queries

#### Recursive Queries for Hierarchies

**Employee Hierarchy Example**
```sql
-- Find all subordinates of a manager (recursive CTE)
WITH RECURSIVE employee_hierarchy AS (
  -- Base case: start with a specific manager
  SELECT 
    employee_id,
    name,
    manager_id,
    position,
    1 as level,
    CAST(name AS VARCHAR(1000)) AS hierarchy_path
  FROM employees 
  WHERE employee_id = 100  -- Starting manager
  
  UNION ALL
  
  -- Recursive case: find direct reports
  SELECT 
    e.employee_id,
    e.name,
    e.manager_id,
    e.position,
    eh.level + 1,
    eh.hierarchy_path || ' -> ' || e.name
  FROM employees e
  INNER JOIN employee_hierarchy eh ON e.manager_id = eh.employee_id
  WHERE eh.level < 10  -- Prevent infinite recursion
)
SELECT 
  employee_id,
  REPEAT('  ', level - 1) || name AS indented_name,
  position,
  level,
  hierarchy_path
FROM employee_hierarchy
ORDER BY level, name;
```

**Bill of Materials (BOM) Example**
```sql
-- Explode a product's bill of materials
WITH RECURSIVE bom_explosion AS (
  -- Base case: top-level product
  SELECT 
    product_id,
    component_id,
    quantity,
    1 as level,
    quantity as total_quantity,
    CAST(product_id AS VARCHAR(1000)) AS bom_path
  FROM bill_of_materials 
  WHERE product_id = 'LAPTOP-001'  -- Top level product
  
  UNION ALL
  
  -- Recursive case: components of components
  SELECT 
    bom.product_id,
    bom.component_id,
    bom.quantity,
    be.level + 1,
    be.total_quantity * bom.quantity,  -- Multiply quantities down the chain
    be.bom_path || ' -> ' || bom.component_id
  FROM bill_of_materials bom
  INNER JOIN bom_explosion be ON bom.product_id = be.component_id
  WHERE be.level < 20  -- Reasonable depth limit
)
SELECT 
  component_id,
  SUM(total_quantity) as total_required,
  MIN(level) as min_level,
  STRING_AGG(DISTINCT bom_path, '; ') as usage_paths
FROM bom_explosion
WHERE component_id NOT IN (
  SELECT DISTINCT product_id FROM bill_of_materials
)  -- Only leaf components (actual parts)
GROUP BY component_id
ORDER BY total_required DESC;
```

#### Path Finding Algorithms

**Shortest Path in Network**
```sql
-- Find shortest path between two locations
WITH RECURSIVE shortest_path AS (
  -- Base case: start location
  SELECT 
    from_location,
    to_location,
    distance,
    1 as hop_count,
    distance as total_distance,
    CAST(from_location AS VARCHAR(1000)) AS path
  FROM network_connections 
  WHERE from_location = 'NYC'  -- Starting point
  
  UNION ALL
  
  -- Recursive case: extend path
  SELECT 
    nc.from_location,
    nc.to_location,
    nc.distance,
    sp.hop_count + 1,
    sp.total_distance + nc.distance,
    sp.path || ' -> ' || nc.to_location
  FROM network_connections nc
  INNER JOIN shortest_path sp ON nc.from_location = sp.to_location
  WHERE sp.hop_count < 10  -- Limit path length
    AND sp.path NOT LIKE '%' || nc.to_location || '%'  -- Prevent cycles
)
SELECT 
  to_location as destination,
  MIN(total_distance) as shortest_distance,
  hop_count,
  path
FROM shortest_path
WHERE to_location = 'LAX'  -- Destination
GROUP BY to_location, hop_count, path
HAVING total_distance = MIN(total_distance) OVER (PARTITION BY to_location)
ORDER BY total_distance
LIMIT 1;
```

## 2. Database-Specific Advanced Features

### 2.1 PostgreSQL Advanced Features

#### Custom Functions and Extensions
```sql
-- Create custom aggregate function
CREATE OR REPLACE FUNCTION median(numeric[])
RETURNS numeric AS $$
  SELECT PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY unnest($1));
$$ LANGUAGE SQL;

-- Create custom aggregate
CREATE AGGREGATE median_agg (numeric) (
  SFUNC = array_append,
  STYPE = numeric[],
  FINALFUNC = median,
  INITCOND = '{}'
);

-- Usage example
SELECT 
  department,
  median_agg(salary) AS median_salary
FROM employees
GROUP BY department;
```

#### Full-Text Search
```sql
-- Create full-text search index
ALTER TABLE products 
ADD COLUMN search_vector tsvector;

UPDATE products 
SET search_vector = to_tsvector('english', 
  COALESCE(product_name, '') || ' ' || 
  COALESCE(description, '') || ' ' ||
  COALESCE(category, '')
);

CREATE INDEX idx_products_search ON products USING GIN(search_vector);

-- Advanced search with ranking
SELECT 
  product_id,
  product_name,
  ts_rank(search_vector, query) as relevance_score,
  ts_headline('english', description, query) as highlighted_description
FROM products, 
     plainto_tsquery('english', 'wireless bluetooth headphones') query
WHERE search_vector @@ query
ORDER BY relevance_score DESC;
```

#### Geometric Data Types
```sql
-- Store and query geographic data
CREATE TABLE stores (
  store_id SERIAL PRIMARY KEY,
  name VARCHAR(100),
  location POINT,
  service_area POLYGON
);

INSERT INTO stores (name, location, service_area) VALUES 
('Downtown Store', POINT(40.7128, -74.0060), 
 POLYGON(POINT(40.710, -74.010), POINT(40.715, -74.010), 
         POINT(40.715, -74.000), POINT(40.710, -74.000)));

-- Find stores within radius
SELECT 
  name,
  location,
  location <-> POINT(40.7589, -73.9851) as distance_miles
FROM stores
WHERE location <@ CIRCLE(POINT(40.7589, -73.9851), 0.1)  -- Within 0.1 unit radius
ORDER BY distance_miles;
```

#### Advanced Array Operations
```sql
-- Complex array manipulations
WITH product_tags AS (
  SELECT 
    product_id,
    ARRAY['electronics', 'portable', 'audio'] AS tags,
    ARRAY[4.5, 3.8, 4.2, 5.0, 4.1] AS ratings
  FROM products
  WHERE product_id = 1
)
SELECT 
  product_id,
  tags,
  -- Array aggregations
  array_length(ratings, 1) as rating_count,
  (SELECT AVG(rating) FROM unnest(ratings) as rating) as avg_rating,
  
  -- Array operations
  tags || ARRAY['bestseller'] as updated_tags,
  tags[1:2] as first_two_tags,
  'audio' = ANY(tags) as has_audio_tag,
  
  -- Find common elements with another array
  tags && ARRAY['electronics', 'mobile'] as has_common_tags
FROM product_tags;
```

### 2.2 SQL Server Advanced Features

#### T-SQL Advanced Programming
```sql
-- Advanced stored procedure with error handling
CREATE PROCEDURE ProcessLargeOrder
  @CustomerID INT,
  @OrderItems NVARCHAR(MAX),  -- JSON string
  @ProcessingMode VARCHAR(20) = 'STANDARD'
AS
BEGIN
  SET NOCOUNT ON;
  
  -- Declare variables
  DECLARE @OrderID INT;
  DECLARE @TotalAmount DECIMAL(18,2) = 0;
  DECLARE @ErrorMessage NVARCHAR(4000);
  
  -- Start transaction
  BEGIN TRANSACTION OrderProcessing;
  
  BEGIN TRY
    -- Create main order
    INSERT INTO Orders (CustomerID, OrderDate, Status)
    VALUES (@CustomerID, GETDATE(), 'PROCESSING');
    
    SET @OrderID = SCOPE_IDENTITY();
    
    -- Process order items from JSON
    WITH OrderItemsJSON AS (
      SELECT 
        JSON_VALUE(value, '$.ProductID') AS ProductID,
        CAST(JSON_VALUE(value, '$.Quantity') AS INT) AS Quantity,
        CAST(JSON_VALUE(value, '$.UnitPrice') AS DECIMAL(18,2)) AS UnitPrice
      FROM OPENJSON(@OrderItems)
    )
    INSERT INTO OrderDetails (OrderID, ProductID, Quantity, UnitPrice, LineTotal)
    SELECT 
      @OrderID,
      ProductID,
      Quantity,
      UnitPrice,
      Quantity * UnitPrice
    FROM OrderItemsJSON;
    
    -- Calculate total
    SELECT @TotalAmount = SUM(LineTotal)
    FROM OrderDetails
    WHERE OrderID = @OrderID;
    
    -- Apply business rules based on processing mode
    IF @ProcessingMode = 'EXPRESS' AND @TotalAmount > 1000
    BEGIN
      -- Apply express processing fee
      SET @TotalAmount = @TotalAmount * 1.15;
    END
    
    -- Update order total
    UPDATE Orders 
    SET TotalAmount = @TotalAmount,
        Status = 'CONFIRMED'
    WHERE OrderID = @OrderID;
    
    -- Commit transaction
    COMMIT TRANSACTION OrderProcessing;
    
    -- Return success
    SELECT 
      @OrderID AS OrderID,
      @TotalAmount AS TotalAmount,
      'SUCCESS' AS Status;
      
  END TRY
  BEGIN CATCH
    -- Rollback on error
    IF @@TRANCOUNT > 0
      ROLLBACK TRANSACTION OrderProcessing;
    
    -- Get error information
    SELECT 
      ERROR_NUMBER() AS ErrorNumber,
      ERROR_MESSAGE() AS ErrorMessage,
      ERROR_LINE() AS ErrorLine,
      'FAILED' AS Status;
      
  END CATCH
END;
```

#### Advanced JSON Processing
```sql
-- Complex JSON manipulation
DECLARE @ProductCatalog NVARCHAR(MAX) = N'{
  "categories": [
    {
      "name": "Electronics",
      "products": [
        {"id": 1, "name": "Laptop", "price": 999.99, "specs": {"ram": "16GB", "storage": "512GB"}},
        {"id": 2, "name": "Mouse", "price": 29.99, "specs": {"type": "wireless", "dpi": 1200}}
      ]
    },
    {
      "name": "Books",
      "products": [
        {"id": 3, "name": "SQL Guide", "price": 39.99, "specs": {"pages": 450, "format": "paperback"}}
      ]
    }
  ]
}';

-- Extract and transform JSON data
WITH ProductsFromJSON AS (
  SELECT 
    c.value AS Category,
    p.value AS Product
  FROM OPENJSON(@ProductCatalog, '$.categories') c
  CROSS APPLY OPENJSON(c.value, '$.products') p
)
SELECT 
  JSON_VALUE(Category, '$.name') AS CategoryName,
  JSON_VALUE(Product, '$.id') AS ProductID,
  JSON_VALUE(Product, '$.name') AS ProductName,
  CAST(JSON_VALUE(Product, '$.price') AS DECIMAL(10,2)) AS Price,
  
  -- Extract nested JSON objects
  JSON_QUERY(Product, '$.specs') AS Specifications,
  
  -- Conditional extraction based on category
  CASE 
    WHEN JSON_VALUE(Category, '$.name') = 'Electronics'
    THEN JSON_VALUE(Product, '$.specs.ram')
    ELSE NULL
  END AS RAM,
  
  CASE 
    WHEN JSON_VALUE(Category, '$.name') = 'Books'
    THEN JSON_VALUE(Product, '$.specs.pages')
    ELSE NULL
  END AS Pages
FROM ProductsFromJSON;
```

### 2.3 Oracle Advanced Features

#### PL/SQL Advanced Programming
```sql
-- Package for order management
CREATE OR REPLACE PACKAGE OrderManagement AS
  -- Public types
  TYPE OrderItemType IS RECORD (
    product_id NUMBER,
    quantity NUMBER,
    unit_price NUMBER(10,2)
  );
  
  TYPE OrderItemsTableType IS TABLE OF OrderItemType INDEX BY PLS_INTEGER;
  
  -- Public procedures and functions
  FUNCTION ProcessOrder(
    p_customer_id IN NUMBER,
    p_order_items IN OrderItemsTableType,
    p_discount_pct IN NUMBER DEFAULT 0
  ) RETURN NUMBER;
  
  PROCEDURE GetOrderSummary(
    p_order_id IN NUMBER,
    p_order_info OUT SYS_REFCURSOR
  );
  
END OrderManagement;
/

CREATE OR REPLACE PACKAGE BODY OrderManagement AS
  
  -- Private exception
  insufficient_inventory EXCEPTION;
  PRAGMA EXCEPTION_INIT(insufficient_inventory, -20001);
  
  FUNCTION ProcessOrder(
    p_customer_id IN NUMBER,
    p_order_items IN OrderItemsTableType,
    p_discount_pct IN NUMBER DEFAULT 0
  ) RETURN NUMBER IS
    
    v_order_id NUMBER;
    v_total_amount NUMBER(12,2) := 0;
    v_available_qty NUMBER;
    
  BEGIN
    -- Create order header
    INSERT INTO orders (customer_id, order_date, status)
    VALUES (p_customer_id, SYSDATE, 'PROCESSING')
    RETURNING order_id INTO v_order_id;
    
    -- Process each order item
    FOR i IN p_order_items.FIRST .. p_order_items.LAST LOOP
      -- Check inventory
      SELECT quantity_available
      INTO v_available_qty
      FROM inventory
      WHERE product_id = p_order_items(i).product_id;
      
      IF v_available_qty < p_order_items(i).quantity THEN
        RAISE_APPLICATION_ERROR(-20001, 
          'Insufficient inventory for product ID: ' || p_order_items(i).product_id);
      END IF;
      
      -- Insert order detail
      INSERT INTO order_details (
        order_id, product_id, quantity, unit_price, line_total
      ) VALUES (
        v_order_id,
        p_order_items(i).product_id,
        p_order_items(i).quantity,
        p_order_items(i).unit_price,
        p_order_items(i).quantity * p_order_items(i).unit_price
      );
      
      -- Update running total
      v_total_amount := v_total_amount + (p_order_items(i).quantity * p_order_items(i).unit_price);
      
      -- Update inventory
      UPDATE inventory
      SET quantity_available = quantity_available - p_order_items(i).quantity
      WHERE product_id = p_order_items(i).product_id;
    END LOOP;
    
    -- Apply discount
    IF p_discount_pct > 0 THEN
      v_total_amount := v_total_amount * (1 - p_discount_pct / 100);
    END IF;
    
    -- Update order total
    UPDATE orders
    SET total_amount = v_total_amount,
        status = 'CONFIRMED'
    WHERE order_id = v_order_id;
    
    COMMIT;
    RETURN v_order_id;
    
  EXCEPTION
    WHEN insufficient_inventory THEN
      ROLLBACK;
      RAISE;
    WHEN OTHERS THEN
      ROLLBACK;
      RAISE_APPLICATION_ERROR(-20999, 'Order processing failed: ' || SQLERRM);
  END ProcessOrder;
  
  PROCEDURE GetOrderSummary(
    p_order_id IN NUMBER,
    p_order_info OUT SYS_REFCURSOR
  ) IS
  BEGIN
    OPEN p_order_info FOR
      SELECT 
        o.order_id,
        o.customer_id,
        c.customer_name,
        o.order_date,
        o.status,
        o.total_amount,
        COUNT(od.product_id) as item_count
      FROM orders o
      JOIN customers c ON o.customer_id = c.customer_id
      LEFT JOIN order_details od ON o.order_id = od.order_id
      WHERE o.order_id = p_order_id
      GROUP BY o.order_id, o.customer_id, c.customer_name, 
               o.order_date, o.status, o.total_amount;
  END GetOrderSummary;
  
END OrderManagement;
/
```

## 3. Data Integration & ETL

### 3.1 ETL Processes

#### Extract, Transform, Load Patterns
```sql
-- Comprehensive ETL process for customer data integration
-- Step 1: Extract from multiple sources
CREATE TABLE staging_customers_crm AS
SELECT 
  customer_id,
  first_name,
  last_name,
  email,
  phone,
  'CRM' as source_system,
  CURRENT_TIMESTAMP as extracted_at
FROM crm_system.customers
WHERE modified_date >= CURRENT_DATE - INTERVAL '1 day';

CREATE TABLE staging_customers_ecommerce AS
SELECT 
  user_id as customer_id,
  fname as first_name,
  lname as last_name,
  email_address as email,
  mobile_phone as phone,
  'ECOMMERCE' as source_system,
  CURRENT_TIMESTAMP as extracted_at
FROM ecommerce_db.users
WHERE last_updated >= CURRENT_DATE - INTERVAL '1 day';

-- Step 2: Transform and clean data
WITH transformed_customers AS (
  SELECT 
    customer_id,
    TRIM(UPPER(COALESCE(first_name, ''))) as first_name,
    TRIM(UPPER(COALESCE(last_name, ''))) as last_name,
    LOWER(TRIM(email)) as email,
    REGEXP_REPLACE(phone, '[^0-9]', '') as phone_clean,
    source_system,
    extracted_at,
    
    -- Data quality flags
    CASE WHEN email IS NULL OR email NOT LIKE '%@%.%' 
         THEN 0 ELSE 1 END as email_valid,
    CASE WHEN LENGTH(REGEXP_REPLACE(phone, '[^0-9]', '')) >= 10 
         THEN 1 ELSE 0 END as phone_valid,
    
    -- Deduplication key
    MD5(LOWER(TRIM(email))) as dedup_key
    
  FROM (
    SELECT * FROM staging_customers_crm
    UNION ALL
    SELECT * FROM staging_customers_ecommerce
  ) all_customers
),

-- Step 3: Apply business rules and deduplication
deduplicated_customers AS (
  SELECT 
    *,
    ROW_NUMBER() OVER (
      PARTITION BY dedup_key 
      ORDER BY email_valid DESC, phone_valid DESC, extracted_at DESC
    ) as row_rank
  FROM transformed_customers
  WHERE email IS NOT NULL
)

-- Step 4: Load into target table
INSERT INTO master_customers (
  customer_id, first_name, last_name, email, phone, 
  source_system, data_quality_score, created_at
)
SELECT 
  customer_id,
  first_name,
  last_name,
  email,
  phone_clean,
  source_system,
  (email_valid + phone_valid) * 50 as data_quality_score,
  CURRENT_TIMESTAMP
FROM deduplicated_customers
WHERE row_rank = 1;
```

#### Data Validation and Quality Checks
```sql
-- Comprehensive data quality monitoring
CREATE OR REPLACE VIEW data_quality_report AS
WITH quality_metrics AS (
  SELECT 
    'customers' AS table_name,
    COUNT(*) AS total_records,
    COUNT(CASE WHEN email IS NULL THEN 1 END) AS missing_email,
    COUNT(CASE WHEN email NOT LIKE '%@%.%' THEN 1 END) AS invalid_email,
    COUNT(CASE WHEN phone IS NULL THEN 1 END) AS missing_phone,
    COUNT(CASE WHEN LENGTH(phone) < 10 THEN 1 END) AS invalid_phone,
    COUNT(DISTINCT email) AS unique_emails,
    COUNT(*) - COUNT(DISTINCT email) AS duplicate_emails,
    CURRENT_TIMESTAMP AS check_date
  FROM customers
  
  UNION ALL
  
  SELECT 
    'orders' AS table_name,
    COUNT(*) AS total_records,
    COUNT(CASE WHEN customer_id IS NULL THEN 1 END) AS missing_customer_id,
    COUNT(CASE WHEN total_amount <= 0 THEN 1 END) AS invalid_amount,
    COUNT(CASE WHEN order_date > CURRENT_DATE THEN 1 END) AS future_dates,
    COUNT(CASE WHEN order_date < DATE('2020-01-01') THEN 1 END) AS old_dates,
    0 AS unique_count,
    0 AS duplicate_count,
    CURRENT_TIMESTAMP AS check_date
  FROM orders
)
SELECT 
  table_name,
  total_records,
  ROUND((missing_email::DECIMAL / total_records) * 100, 2) AS missing_email_pct,
  ROUND((invalid_email::DECIMAL / total_records) * 100, 2) AS invalid_email_pct,
  ROUND((duplicate_emails::DECIMAL / total_records) * 100, 2) AS duplicate_pct,
  
  -- Overall quality score
  GREATEST(0, 
    100 - 
    ((missing_email + invalid_email + duplicate_emails)::DECIMAL / total_records * 100)
  ) AS quality_score,
  
  check_date
FROM quality_metrics;
```

### 3.2 Cross-Database Queries

#### Federated Queries
```sql
-- Query across multiple database systems
-- Using database links (Oracle) or linked servers (SQL Server)

-- Create database link (Oracle example)
CREATE DATABASE LINK sales_db
CONNECT TO sales_user IDENTIFIED BY password
USING 'sales_database_connection_string';

-- Cross-database analytical query
WITH sales_summary AS (
  -- Data from remote sales database
  SELECT 
    customer_id,
    SUM(order_amount) as total_sales,
    COUNT(*) as order_count,
    MAX(order_date) as last_order_date
  FROM orders@sales_db
  WHERE order_date >= DATE('2024-01-01')
  GROUP BY customer_id
),
customer_info AS (
  -- Data from local customer database
  SELECT 
    customer_id,
    customer_name,
    customer_segment,
    registration_date,
    preferred_contact
  FROM customers
),
marketing_campaigns AS (
  -- Data from marketing database
  SELECT 
    customer_id,
    campaign_name,
    response_rate,
    last_contact_date
  FROM campaign_responses@marketing_db
  WHERE campaign_date >= DATE('2024-01-01')
)
SELECT 
  ci.customer_name,
  ci.customer_segment,
  ss.total_sales,
  ss.order_count,
  ss.last_order_date,
  mc.campaign_name,
  mc.response_rate,
  
  -- Calculate customer lifetime value
  CASE 
    WHEN ss.order_count > 0 
    THEN ss.total_sales * 
         (1 + COALESCE(mc.response_rate, 0)) * 
         EXTRACT(DAYS FROM (CURRENT_DATE - ci.registration_date)) / 365.0
    ELSE 0
  END AS estimated_clv
  
FROM customer_info ci
LEFT JOIN sales_summary ss ON ci.customer_id = ss.customer_id
LEFT JOIN marketing_campaigns mc ON ci.customer_id = mc.customer_id
ORDER BY estimated_clv DESC;
```

## 4. Real-World Project: Complete Data Warehouse & Analytics Platform

### 4.1 Star Schema Design
```sql
-- Dimension Tables
CREATE TABLE dim_customer (
  customer_key SERIAL PRIMARY KEY,
  customer_id VARCHAR(50) UNIQUE,
  customer_name VARCHAR(200),
  customer_segment VARCHAR(50),
  geographic_region VARCHAR(100),
  registration_date DATE,
  is_active BOOLEAN,
  -- SCD Type 2 fields
  effective_start_date DATE,
  effective_end_date DATE,
  is_current BOOLEAN DEFAULT TRUE
);

CREATE TABLE dim_product (
  product_key SERIAL PRIMARY KEY,
  product_id VARCHAR(50) UNIQUE,
  product_name VARCHAR(200),
  category_level_1 VARCHAR(100),
  category_level_2 VARCHAR(100),
  category_level_3 VARCHAR(100),
  brand VARCHAR(100),
  unit_cost DECIMAL(10,2),
  list_price DECIMAL(10,2),
  is_active BOOLEAN
);

CREATE TABLE dim_date (
  date_key INTEGER PRIMARY KEY,
  full_date DATE UNIQUE,
  day_of_week INTEGER,
  day_name VARCHAR(10),
  day_of_month INTEGER,
  day_of_year INTEGER,
  week_of_year INTEGER,
  month_number INTEGER,
  month_name VARCHAR(10),
  quarter_number INTEGER,
  quarter_name VARCHAR(10),
  year_number INTEGER,
  is_weekend BOOLEAN,
  is_holiday BOOLEAN,
  fiscal_year INTEGER,
  fiscal_quarter INTEGER
);

CREATE TABLE dim_location (
  location_key SERIAL PRIMARY KEY,
  store_id VARCHAR(50),
  store_name VARCHAR(200),
  address VARCHAR(500),
  city VARCHAR(100),
  state_province VARCHAR(100),
  country VARCHAR(100),
  postal_code VARCHAR(20),
  latitude DECIMAL(10,8),
  longitude DECIMAL(11,8),
  store_type VARCHAR(50),
  opening_date DATE
);

-- Fact Table
CREATE TABLE fact_sales (
  sales_key BIGSERIAL PRIMARY KEY,
  customer_key INTEGER REFERENCES dim_customer(customer_key),
  product_key INTEGER REFERENCES dim_product(product_key),
  date_key INTEGER REFERENCES dim_date(date_key),
  location_key INTEGER REFERENCES dim_location(location_key),
  
  -- Measures
  quantity_sold INTEGER,
  unit_price DECIMAL(10,2),
  unit_cost DECIMAL(10,2),
  discount_amount DECIMAL(10,2),
  tax_amount DECIMAL(10,2),
  gross_sales_amount DECIMAL(12,2),
  net_sales_amount DECIMAL(12,2),
  profit_amount DECIMAL(12,2),
  
  -- Transaction identifiers
  transaction_id VARCHAR(100),
  order_line_number INTEGER,
  
  -- Audit fields
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Create partitioning for performance (PostgreSQL example)
CREATE TABLE fact_sales_2024 PARTITION OF fact_sales
FOR VALUES FROM ('2024-01-01') TO ('2025-01-01');

CREATE TABLE fact_sales_2025 PARTITION OF fact_sales
FOR VALUES FROM ('2025-01-01') TO ('2026-01-01');

-- Indexes for optimal query performance
CREATE INDEX idx_fact_sales_customer ON fact_sales(customer_key);
CREATE INDEX idx_fact_sales_product ON fact_sales(product_key);
CREATE INDEX idx_fact_sales_date ON fact_sales(date_key);
CREATE INDEX idx_fact_sales_location ON fact_sales(location_key);
CREATE INDEX idx_fact_sales_composite ON fact_sales(date_key, customer_key, product_key);
```

### 4.2 Real-Time Data Processing Pipeline

#### Streaming ETL with Change Data Capture
```sql
-- Create staging tables for real-time ingestion
CREATE TABLE staging_sales_stream (
  transaction_id VARCHAR(100),
  customer_id VARCHAR(50),
  product_id VARCHAR(50),
  store_id VARCHAR(50),
  transaction_timestamp TIMESTAMP,
  quantity INTEGER,
  unit_price DECIMAL(10,2),
  discount_pct DECIMAL(5,2),
  payment_method VARCHAR(50),
  operation_type VARCHAR(10), -- INSERT, UPDATE, DELETE
  sequence_number BIGINT,
  processed BOOLEAN DEFAULT FALSE
);

-- Real-time processing procedure
CREATE OR REPLACE FUNCTION process_sales_stream()
RETURNS TRIGGER AS $
DECLARE
  v_customer_key INTEGER;
  v_product_key INTEGER;
  v_date_key INTEGER;
  v_location_key INTEGER;
  v_discount_amount DECIMAL(10,2);
  v_tax_amount DECIMAL(10,2);
  v_gross_amount DECIMAL(12,2);
  v_net_amount DECIMAL(12,2);
  v_profit_amount DECIMAL(12,2);
BEGIN
  -- Only process unprocessed records
  IF NEW.processed = FALSE THEN
    
    -- Lookup dimension keys
    SELECT customer_key INTO v_customer_key
    FROM dim_customer 
    WHERE customer_id = NEW.customer_id AND is_current = TRUE;
    
    SELECT product_key INTO v_product_key
    FROM dim_product 
    WHERE product_id = NEW.product_id;
    
    SELECT date_key INTO v_date_key
    FROM dim_date 
    WHERE full_date = DATE(NEW.transaction_timestamp);
    
    SELECT location_key INTO v_location_key
    FROM dim_location 
    WHERE store_id = NEW.store_id;
    
    -- Calculate derived measures
    v_discount_amount := NEW.unit_price * NEW.quantity * (NEW.discount_pct / 100);
    v_gross_amount := NEW.unit_price * NEW.quantity;
    v_net_amount := v_gross_amount - v_discount_amount;
    v_tax_amount := v_net_amount * 0.08; -- Assume 8% tax
    
    -- Calculate profit (need product cost from dimension)
    SELECT v_net_amount - (unit_cost * NEW.quantity) INTO v_profit_amount
    FROM dim_product 
    WHERE product_key = v_product_key;
    
    -- Insert into fact table based on operation type
    CASE NEW.operation_type
      WHEN 'INSERT' THEN
        INSERT INTO fact_sales (
          customer_key, product_key, date_key, location_key,
          quantity_sold, unit_price, discount_amount, tax_amount,
          gross_sales_amount, net_sales_amount, profit_amount,
          transaction_id, order_line_number
        ) VALUES (
          v_customer_key, v_product_key, v_date_key, v_location_key,
          NEW.quantity, NEW.unit_price, v_discount_amount, v_tax_amount,
          v_gross_amount, v_net_amount, v_profit_amount,
          NEW.transaction_id, 1
        );
        
      WHEN 'UPDATE' THEN
        UPDATE fact_sales 
        SET quantity_sold = NEW.quantity,
            unit_price = NEW.unit_price,
            discount_amount = v_discount_amount,
            tax_amount = v_tax_amount,
            gross_sales_amount = v_gross_amount,
            net_sales_amount = v_net_amount,
            profit_amount = v_profit_amount,
            updated_at = CURRENT_TIMESTAMP
        WHERE transaction_id = NEW.transaction_id;
        
      WHEN 'DELETE' THEN
        DELETE FROM fact_sales 
        WHERE transaction_id = NEW.transaction_id;
    END CASE;
    
    -- Mark as processed
    UPDATE staging_sales_stream 
    SET processed = TRUE 
    WHERE sequence_number = NEW.sequence_number;
    
  END IF;
  
  RETURN NEW;
END;
$ LANGUAGE plpgsql;

-- Create trigger for real-time processing
CREATE TRIGGER trigger_process_sales_stream
  AFTER INSERT ON staging_sales_stream
  FOR EACH ROW
  EXECUTE FUNCTION process_sales_stream();
```

### 4.3 Advanced Analytics & OLAP Cubes

#### Multi-Dimensional Analysis Queries
```sql
-- Create materialized views for OLAP cube simulation
CREATE MATERIALIZED VIEW sales_cube_summary AS
SELECT 
  -- Dimensions
  dd.year_number,
  dd.quarter_number,
  dd.month_number,
  dc.customer_segment,
  dc.geographic_region,
  dp.category_level_1,
  dp.category_level_2,
  dl.country,
  dl.store_type,
  
  -- Measures
  COUNT(*) as transaction_count,
  SUM(fs.quantity_sold) as total_quantity,
  SUM(fs.gross_sales_amount) as total_gross_sales,
  SUM(fs.net_sales_amount) as total_net_sales,
  SUM(fs.profit_amount) as total_profit,
  AVG(fs.unit_price) as avg_unit_price,
  
  -- Advanced measures
  SUM(fs.net_sales_amount) / NULLIF(SUM(fs.quantity_sold), 0) as avg_selling_price,
  SUM(fs.profit_amount) / NULLIF(SUM(fs.net_sales_amount), 0) as profit_margin,
  
  COUNT(DISTINCT fs.customer_key) as unique_customers,
  SUM(fs.net_sales_amount) / NULLIF(COUNT(DISTINCT fs.customer_key), 0) as revenue_per_customer

FROM fact_sales fs
JOIN dim_date dd ON fs.date_key = dd.date_key
JOIN dim_customer dc ON fs.customer_key = dc.customer_key
JOIN dim_product dp ON fs.product_key = dp.product_key
JOIN dim_location dl ON fs.location_key = dl.location_key
GROUP BY GROUPING SETS (
  -- Various levels of detail
  (dd.year_number, dd.quarter_number, dd.month_number, dc.customer_segment, dp.category_level_1, dl.country),
  (dd.year_number, dd.quarter_number, dc.customer_segment, dp.category_level_1),
  (dd.year_number, dc.customer_segment, dp.category_level_1),
  (dd.year_number, dp.category_level_1),
  (dc.customer_segment, dp.category_level_1),
  (dp.category_level_1),
  (dc.customer_segment),
  (dd.year_number),
  ()  -- Grand total
);

-- Create indexes for fast OLAP queries
CREATE INDEX idx_sales_cube_year_segment_category 
ON sales_cube_summary(year_number, customer_segment, category_level_1);

CREATE INDEX idx_sales_cube_measures 
ON sales_cube_summary(total_net_sales, total_profit, unique_customers);
```

#### Advanced Business Intelligence Queries
```sql
-- Customer Cohort Analysis
WITH customer_cohorts AS (
  SELECT 
    dc.customer_key,
    dc.customer_segment,
    DATE_TRUNC('month', MIN(dd.full_date)) as cohort_month,
    DATE_TRUNC('month', dd.full_date) as purchase_month,
    SUM(fs.net_sales_amount) as monthly_revenue
  FROM fact_sales fs
  JOIN dim_date dd ON fs.date_key = dd.date_key
  JOIN dim_customer dc ON fs.customer_key = dc.customer_key
  WHERE dd.full_date >= '2023-01-01'
  GROUP BY dc.customer_key, dc.customer_segment, 
           DATE_TRUNC('month', dd.full_date),
           DATE_TRUNC('month', MIN(dd.full_date) OVER (PARTITION BY dc.customer_key))
),
cohort_analysis AS (
  SELECT 
    cohort_month,
    customer_segment,
    purchase_month,
    EXTRACT(MONTH FROM AGE(purchase_month, cohort_month)) as months_since_first_purchase,
    COUNT(DISTINCT customer_key) as active_customers,
    SUM(monthly_revenue) as cohort_revenue,
    
    -- Retention rate calculation
    COUNT(DISTINCT customer_key) * 100.0 / 
      FIRST_VALUE(COUNT(DISTINCT customer_key)) OVER (
        PARTITION BY cohort_month, customer_segment 
        ORDER BY purchase_month 
        ROWS UNBOUNDED PRECEDING
      ) as retention_rate
      
  FROM customer_cohorts
  GROUP BY cohort_month, customer_segment, purchase_month
)
SELECT 
  cohort_month,
  customer_segment,
  months_since_first_purchase,
  active_customers,
  ROUND(cohort_revenue, 2) as revenue,
  ROUND(retention_rate, 2) as retention_rate_pct,
  
  -- Customer lifetime value projection
  ROUND(cohort_revenue / NULLIF(active_customers, 0) * 
        (1 / (1 - retention_rate/100)), 2) as projected_clv
        
FROM cohort_analysis
ORDER BY cohort_month, customer_segment, months_since_first_purchase;

-- Advanced Market Basket Analysis
WITH transaction_items AS (
  SELECT 
    fs.transaction_id,
    dp.product_name,
    dp.category_level_2,
    fs.quantity_sold,
    fs.net_sales_amount
  FROM fact_sales fs
  JOIN dim_product dp ON fs.product_key = dp.product_key
  JOIN dim_date dd ON fs.date_key = dd.date_key
  WHERE dd.full_date >= CURRENT_DATE - INTERVAL '90 days'
),
product_pairs AS (
  SELECT 
    t1.product_name as product_a,
    t2.product_name as product_b,
    t1.category_level_2 as category_a,
    t2.category_level_2 as category_b,
    COUNT(*) as co_occurrence_count,
    AVG(t1.net_sales_amount + t2.net_sales_amount) as avg_basket_value
  FROM transaction_items t1
  JOIN transaction_items t2 ON t1.transaction_id = t2.transaction_id
  WHERE t1.product_name < t2.product_name  -- Avoid duplicate pairs
  GROUP BY t1.product_name, t2.product_name, t1.category_level_2, t2.category_level_2
  HAVING COUNT(*) >= 10  -- Minimum support threshold
),
association_rules AS (
  SELECT 
    product_a,
    product_b,
    category_a,
    category_b,
    co_occurrence_count,
    avg_basket_value,
    
    -- Support: frequency of the itemset
    co_occurrence_count * 100.0 / (SELECT COUNT(DISTINCT transaction_id) FROM transaction_items) as support_pct,
    
    -- Confidence: P(B|A)
    co_occurrence_count * 100.0 / 
      (SELECT COUNT(DISTINCT transaction_id) 
       FROM transaction_items 
       WHERE product_name = pp.product_a) as confidence_a_to_b,
       
    -- Confidence: P(A|B)  
    co_occurrence_count * 100.0 / 
      (SELECT COUNT(DISTINCT transaction_id) 
       FROM transaction_items 
       WHERE product_name = pp.product_b) as confidence_b_to_a
       
  FROM product_pairs pp
)
SELECT 
  product_a,
  product_b,
  ROUND(support_pct, 2) as support_percent,
  ROUND(confidence_a_to_b, 2) as confidence_a_to_b_percent,
  ROUND(confidence_b_to_a, 2) as confidence_b_to_a_percent,
  ROUND(avg_basket_value, 2) as avg_combined_value,
  
  -- Lift: measures how much more likely B is purchased when A is purchased
  ROUND(confidence_a_to_b / 
    (SELECT COUNT(DISTINCT transaction_id) * 100.0 / 
     (SELECT COUNT(DISTINCT transaction_id) FROM transaction_items)
     FROM transaction_items WHERE product_name = product_b), 2) as lift
     
FROM association_rules
WHERE support_pct >= 0.5  -- Minimum support of 0.5%
  AND confidence_a_to_b >= 10  -- Minimum confidence of 10%
ORDER BY confidence_a_to_b DESC, support_pct DESC
LIMIT 50;
```

### 4.4 Data Governance & Quality Monitoring

#### Comprehensive Data Quality Framework
```sql
-- Data Quality Rules Engine
CREATE TABLE data_quality_rules (
  rule_id SERIAL PRIMARY KEY,
  rule_name VARCHAR(200),
  table_name VARCHAR(100),
  column_name VARCHAR(100),
  rule_type VARCHAR(50), -- NULL_CHECK, FORMAT_CHECK, RANGE_CHECK, REFERENCE_CHECK
  rule_definition TEXT,
  expected_value TEXT,
  severity_level VARCHAR(20), -- CRITICAL, HIGH, MEDIUM, LOW
  is_active BOOLEAN DEFAULT TRUE,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Sample data quality rules
INSERT INTO data_quality_rules (rule_name, table_name, column_name, rule_type, rule_definition, severity_level) VALUES
('Customer Email Format', 'dim_customer', 'email', 'FORMAT_CHECK', 'email LIKE ''%@%.%''', 'HIGH'),
('Sales Amount Range', 'fact_sales', 'net_sales_amount', 'RANGE_CHECK', 'net_sales_amount BETWEEN 0 AND 100000', 'CRITICAL'),
('Future Date Check', 'fact_sales', 'date_key', 'RANGE_CHECK', 'date_key <= EXTRACT(EPOCH FROM CURRENT_DATE)', 'CRITICAL'),
('Customer Reference', 'fact_sales', 'customer_key', 'REFERENCE_CHECK', 'EXISTS (SELECT 1 FROM dim_customer WHERE customer_key = fact_sales.customer_key)', 'CRITICAL');

-- Data quality monitoring function
CREATE OR REPLACE FUNCTION run_data_quality_checks()
RETURNS TABLE (
  rule_name VARCHAR(200),
  table_name VARCHAR(100),
  total_records BIGINT,
  failed_records BIGINT,
  failure_rate NUMERIC(5,2),
  severity_level VARCHAR(20),
  check_timestamp TIMESTAMP
) AS $
DECLARE
  rule_record RECORD;
  sql_query TEXT;
  total_count BIGINT;
  failed_count BIGINT;
BEGIN
  FOR rule_record IN 
    SELECT * FROM data_quality_rules WHERE is_active = TRUE
  LOOP
    -- Count total records
    sql_query := FORMAT('SELECT COUNT(*) FROM %I', rule_record.table_name);
    EXECUTE sql_query INTO total_count;
    
    -- Count failed records based on rule type
    CASE rule_record.rule_type
      WHEN 'NULL_CHECK' THEN
        sql_query := FORMAT('SELECT COUNT(*) FROM %I WHERE %I IS NULL', 
                           rule_record.table_name, rule_record.column_name);
      WHEN 'FORMAT_CHECK', 'RANGE_CHECK' THEN
        sql_query := FORMAT('SELECT COUNT(*) FROM %I WHERE NOT (%s)', 
                           rule_record.table_name, rule_record.rule_definition);
      WHEN 'REFERENCE_CHECK' THEN
        sql_query := FORMAT('SELECT COUNT(*) FROM %I WHERE NOT %s', 
                           rule_record.table_name, rule_record.rule_definition);
    END CASE;
    
    EXECUTE sql_query INTO failed_count;
    
    -- Return results
    RETURN QUERY SELECT 
      rule_record.rule_name,
      rule_record.table_name,
      total_count,
      failed_count,
      ROUND((failed_count * 100.0 / NULLIF(total_count, 0)), 2),
      rule_record.severity_level,
      CURRENT_TIMESTAMP;
      
  END LOOP;
  
  RETURN;
END;
$ LANGUAGE plpgsql;

-- Data lineage tracking
CREATE TABLE data_lineage (
  lineage_id SERIAL PRIMARY KEY,
  source_system VARCHAR(100),
  source_table VARCHAR(100),
  target_system VARCHAR(100),
  target_table VARCHAR(100),
  transformation_logic TEXT,
  last_processed TIMESTAMP,
  record_count BIGINT,
  processing_status VARCHAR(20)
);

-- Audit logging for sensitive operations
CREATE TABLE audit_log (
  audit_id SERIAL PRIMARY KEY,
  table_name VARCHAR(100),
  operation_type VARCHAR(10), -- INSERT, UPDATE, DELETE
  user_name VARCHAR(100),
  timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  old_values JSONB,
  new_values JSONB,
  affected_columns TEXT[]
);

-- Generic audit trigger function
CREATE OR REPLACE FUNCTION audit_trigger_function()
RETURNS TRIGGER AS $
BEGIN
  IF TG_OP = 'DELETE' THEN
    INSERT INTO audit_log (table_name, operation_type, user_name, old_values)
    VALUES (TG_TABLE_NAME, TG_OP, USER, row_to_json(OLD));
    RETURN OLD;
  ELSIF TG_OP = 'UPDATE' THEN
    INSERT INTO audit_log (table_name, operation_type, user_name, old_values, new_values)
    VALUES (TG_TABLE_NAME, TG_OP, USER, row_to_json(OLD), row_to_json(NEW));
    RETURN NEW;
  ELSIF TG_OP = 'INSERT' THEN
    INSERT INTO audit_log (table_name, operation_type, user_name, new_values)
    VALUES (TG_TABLE_NAME, TG_OP, USER, row_to_json(NEW));
    RETURN NEW;
  END IF;
  RETURN NULL;
END;
$ LANGUAGE plpgsql;

-- Apply audit triggers to sensitive tables
CREATE TRIGGER audit_dim_customer
  AFTER INSERT OR UPDATE OR DELETE ON dim_customer
  FOR EACH ROW EXECUTE FUNCTION audit_trigger_function();

CREATE TRIGGER audit_fact_sales
  AFTER INSERT OR UPDATE OR DELETE ON fact_sales
  FOR EACH ROW EXECUTE FUNCTION audit_trigger_function();
```

### 4.5 Performance Optimization & Monitoring

#### Advanced Performance Tuning
```sql
-- Performance monitoring views
CREATE VIEW query_performance_summary AS
SELECT 
  schemaname,
  tablename,
  attname as column_name,
  n_distinct,
  correlation,
  most_common_vals[1:5] as top_5_values,
  most_common_freqs[1:5] as top_5_frequencies
FROM pg_stats
WHERE schemaname = 'public'
ORDER BY schemaname, tablename, attname;

-- Index usage analysis
CREATE VIEW index_usage_analysis AS
SELECT 
  schemaname,
  tablename,
  indexname,
  idx_tup_read,
  idx_tup_fetch,
  idx_scan,
  
  -- Calculate index efficiency
  CASE 
    WHEN idx_scan = 0 THEN 'UNUSED'
    WHEN idx_tup_read = 0 THEN 'SCAN_ONLY'
    ELSE ROUND((idx_tup_fetch * 100.0 / idx_tup_read), 2)::TEXT || '%'
  END as efficiency,
  
  pg_size_pretty(pg_relation_size(indexrelid)) as index_size
FROM pg_stat_user_indexes
ORDER BY idx_scan DESC;

-- Query optimization recommendations
CREATE OR REPLACE FUNCTION analyze_query_performance(query_text TEXT)
RETURNS TABLE (
  recommendation_type VARCHAR(50),
  description TEXT,
  estimated_improvement VARCHAR(20)
) AS $
BEGIN
  -- Analyze EXPLAIN plan and provide recommendations
  -- This is a simplified example - real implementations would be more complex
  
  IF query_text ILIKE '%SELECT *%' THEN
    RETURN QUERY SELECT 
      'COLUMN_SELECTION'::VARCHAR(50),
      'Avoid SELECT * - specify only needed columns'::TEXT,
      'HIGH'::VARCHAR(20);
  END IF;
  
  IF query_text ILIKE '%WHERE%' AND query_text NOT ILIKE '%INDEX%' THEN
    RETURN QUERY SELECT 
      'MISSING_INDEX'::VARCHAR(50),
      'Consider adding indexes on WHERE clause columns'::TEXT,
      'HIGH'::VARCHAR(20);
  END IF;
  
  IF query_text ILIKE '%ORDER BY%' AND query_text NOT ILIKE '%LIMIT%' THEN
    RETURN QUERY SELECT 
      'UNLIMITED_SORT'::VARCHAR(50),
      'Large sorts without LIMIT can be expensive'::TEXT,
      'MEDIUM'::VARCHAR(20);
  END IF;
  
  RETURN;
END;
$ LANGUAGE plpgsql;
```

## 5. Security & Compliance

### 5.1 Advanced Security Patterns
```sql
-- Row-level security (PostgreSQL)
-- Enable RLS on sensitive tables
ALTER TABLE fact_sales ENABLE ROW LEVEL SECURITY;

-- Create security policies
CREATE POLICY sales_regional_access ON fact_sales
  FOR ALL TO sales_users
  USING (
    location_key IN (
      SELECT location_key FROM dim_location dl
      JOIN user_permissions up ON dl.country = up.allowed_region
      WHERE up.username = current_user
    )
  );

-- Dynamic data masking for sensitive information
CREATE OR REPLACE FUNCTION mask_sensitive_data(
  input_value TEXT,
  mask_type VARCHAR(20) DEFAULT 'PARTIAL'
)
RETURNS TEXT AS $
BEGIN
  CASE mask_type
    WHEN 'EMAIL' THEN
      RETURN REGEXP_REPLACE(input_value, '(.{2}).*(@.*)', '\1***\2');
    WHEN 'PHONE' THEN
      RETURN REGEXP_REPLACE(input_value, '(\d{3})\d{3}(\d{4})', '\1-XXX-\2');
    WHEN 'CREDIT_CARD' THEN
      RETURN REGEXP_REPLACE(input_value, '(\d{4})\d{8}(\d{4})', '\1-XXXX-XXXX-\2');
    WHEN 'PARTIAL' THEN
      RETURN LEFT(input_value, 2) || REPEAT('*', LENGTH(input_value) - 4) || RIGHT(input_value, 2);
    ELSE
      RETURN REPEAT('*', LENGTH(input_value));
  END CASE;
END;
$ LANGUAGE plpgsql;

-- Create masked views for different user roles
CREATE VIEW customer_data_masked AS
SELECT 
  customer_key,
  mask_sensitive_data(customer_name, 'PARTIAL') as customer_name,
  mask_sensitive_data(email, 'EMAIL') as email,
  customer_segment,
  geographic_region,
  registration_date
FROM dim_customer
WHERE has_table_privilege(current_user, 'dim_customer', 'SELECT');

-- Encryption for sensitive data
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- Encrypted storage example
ALTER TABLE dim_customer ADD COLUMN encrypted_ssn BYTEA;

-- Function to encrypt/decrypt sensitive data
CREATE OR REPLACE FUNCTION encrypt_sensitive_data(plaintext TEXT, key_id TEXT)
RETURNS BYTEA AS $
BEGIN
  RETURN pgp_sym_encrypt(plaintext, key_id);
END;
$ LANGUAGE plpgsql SECURITY DEFINER;

CREATE OR REPLACE FUNCTION decrypt_sensitive_data(ciphertext BYTEA, key_id TEXT)
RETURNS TEXT AS $
BEGIN
  RETURN pgp_sym_decrypt(ciphertext, key_id);
END;
$ LANGUAGE plpgsql SECURITY DEFINER;
```

This completes Phase 8 of your SQL mastery roadmap. You've now covered the most advanced SQL concepts including:

**Key Achievements in Phase 8:**

1. **Modern SQL Analytics** - PIVOT/UNPIVOT, GROUPING SETS, advanced statistical functions
2. **Graph Database Queries** - Recursive CTEs for hierarchies and path finding
3. **Database-Specific Features** - PostgreSQL, SQL Server, Oracle advanced capabilities
4. **Complete Data Warehouse** - Star schema design, real-time ETL, OLAP cubes
5. **Advanced Business Intelligence** - Cohort analysis, market basket analysis
6. **Data Governance** - Quality monitoring, audit logging, lineage tracking
7. **Performance Optimization** - Advanced tuning and monitoring techniques
8. **Enterprise Security** - Row-level security, data masking, encryption

**Next Steps for Continuous Mastery:**

1. **Practice Real Scenarios** - Apply these concepts to actual business problems
2. **Cloud Platforms** - Learn cloud-specific SQL features (BigQuery, Snowflake, Redshift)
3. **Modern Analytics** - Integrate with machine learning and AI platforms
4. **Industry Specialization** - Focus on domain-specific requirements (finance, healthcare, retail)

You've now completed the most comprehensive SQL learning journey! These advanced techniques will enable you to handle enterprise-level database challenges and build sophisticated analytics platforms. Would you like me to elaborate on any specific concept or move to practical exercises with real datasets?