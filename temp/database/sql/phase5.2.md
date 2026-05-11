ARTITION BY Clause - Advanced Examples
```sql
-- E-commerce Order Analysis with PARTITION BY

CREATE TABLE orders (
    order_id INT PRIMARY KEY,
    customer_id INT,
    product_category VARCHAR(30),
    order_amount DECIMAL(10,2),
    order_date DATE,
    customer_segment VARCHAR(20)
);

INSERT INTO orders VALUES
(1001, 201, 'Electronics', 1200.00, '2024-01-15', 'Premium'),
(1002, 201, 'Books', 45.00, '2024-01-20', 'Premium'),
(1003, 202, 'Electronics', 800.00, '2024-01-18', 'Standard'),
(1004, 203, 'Clothing', 150.00, '2024-01-22', 'Standard'),
(1005, 201, 'Electronics', 600.00, '2024-02-10', 'Premium'),
(1006, 204, 'Books', 75.00, '2024-02-12', 'Premium'),
(1007, 202, 'Clothing', 200.00, '2024-02-15', 'Standard'),
(1008, 205, 'Electronics', 950.00, '2024-02-18', 'Standard'),
(1009, 203, 'Books', 30.00, '2024-02-20', 'Standard'),
(1010, 206, 'Electronics', 1100.00, '2024-03-01', 'Premium');

-- 1. Customer Purchase Ranking by Category
SELECT 
    customer_id,
    product_category,
    order_amount,
    order_date,
    -- Rank customers within each product category
    RANK() OVER (
        PARTITION BY product_category 
        ORDER BY order_amount DESC
    ) as category_purchase_rank,
    -- Rank purchases for each customer
    ROW_NUMBER() OVER (
        PARTITION BY customer_id 
        ORDER BY order_date
    ) as customer_purchase_sequence
FROM orders
ORDER BY product_category, order_amount DESC;

-- 2. Multiple Partitioning: Category + Customer Segment
SELECT 
    customer_id,
    product_category,
    customer_segment,
    order_amount,
    -- Ranking within category and segment combination
    DENSE_RANK() OVER (
        PARTITION BY product_category, customer_segment 
        ORDER BY order_amount DESC
    ) as segment_category_rank,
    -- Count of orders in same category and segment
    COUNT(*) OVER (
        PARTITION BY product_category, customer_segment
    ) as segment_category_order_count
FROM orders
ORDER BY product_category, customer_segment, order_amount DESC;

-- 3. REAL-WORLD SCENARIO: Monthly Sales Performance Analysis
CREATE TABLE monthly_sales (
    sales_rep_id INT,
    sales_rep_name VARCHAR(50),
    territory VARCHAR(30),
    sales_month DATE,
    monthly_revenue DECIMAL(12,2),
    deals_closed INT
);

INSERT INTO monthly_sales VALUES
(301, 'Alex Thompson', 'California', '2024-01-01', 95000, 12),
(301, 'Alex Thompson', 'California', '2024-02-01', 87000, 10),
(301, 'Alex Thompson', 'California', '2024-03-01', 103000, 15),
(302, 'Maria Garcia', 'California', '2024-01-01', 78000, 8),
(302, 'Maria Garcia', 'California', '2024-02-01', 92000, 11),
(302, 'Maria Garcia', 'California', '2024-03-01', 85000, 9),
(303, 'James Wilson', 'Texas', '2024-01-01', 112000, 14),
(303, 'James Wilson', 'Texas', '2024-02-01', 98000, 12),
(303, 'James Wilson', 'Texas', '2024-03-01', 108000, 13),
(304, 'Linda Davis', 'Texas', '2024-01-01', 89000, 10),
(304, 'Linda Davis', 'Texas', '2024-02-01', 76000, 8),
(304, 'Linda Davis', 'Texas', '2024-03-01', 94000, 11);

-- Comprehensive Sales Performance Analysis
SELECT 
    sales_rep_name,
    territory,
    sales_month,
    monthly_revenue,
    deals_closed,
    
    -- Territory-based rankings
    RANK() OVER (
        PARTITION BY territory, sales_month 
        ORDER BY monthly_revenue DESC
    ) as territory_monthly_rank,
    
    -- Individual performance tracking
    ROW_NUMBER() OVER (
        PARTITION BY sales_rep_id 
        ORDER BY sales_month
    ) as performance_period,
    
    -- Performance percentiles within territory
    NTILE(4) OVER (
        PARTITION BY territory 
        ORDER BY monthly_revenue
    ) as revenue_quartile,
    
    -- Territory statistics
    AVG(monthly_revenue) OVER (
        PARTITION BY territory
    ) as territory_avg_revenue,
    
    MAX(monthly_revenue) OVER (
        PARTITION BY territory
    ) as territory_max_revenue
    
FROM monthly_sales
ORDER BY territory, sales_month, monthly_revenue DESC;

-- 4. Complex Business Scenario: Customer Lifetime Value Analysis
CREATE TABLE customer_transactions (
    transaction_id INT PRIMARY KEY,
    customer_id INT,
    customer_acquisition_channel VARCHAR(30),
    transaction_date DATE,
    transaction_amount DECIMAL(10,2),
    customer_age_group VARCHAR(20)
);

INSERT INTO customer_transactions VALUES
(5001, 401, 'Online Marketing', '2024-01-10', 250.00, '25-35'),
(5002, 401, 'Online Marketing', '2024-02-15', 180.00, '25-35'),
(5003, 401, 'Online Marketing', '2024-03-20', 300.00, '25-35'),
(5004, 402, 'Referral', '2024-01-12', 450.00, '35-45'),
(5005, 402, 'Referral', '2024-01-25', 220.00, '35-45'),
(5006, 403, 'Social Media', '2024-02-01', 175.00, '18-25'),
(5007, 403, 'Social Media', '2024-02-28', 280.00, '18-25'),
(5008, 404, 'Online Marketing', '2024-01-08', 520.00, '45-55'),
(5009, 404, 'Online Marketing', '2024-03-15', 340.00, '45-55'),
(5010, 405, 'Referral', '2024-02-10', 190.00, '25-35');

-- Advanced Customer Analysis with Multiple Partitions
SELECT 
    customer_id,
    customer_acquisition_channel,
    customer_age_group,
    transaction_date,
    transaction_amount,
    
    -- Customer journey tracking
    ROW_NUMBER() OVER (
        PARTITION BY customer_id 
        ORDER BY transaction_date
    ) as transaction_sequence,
    
    -- Channel performance comparison
    RANK() OVER (
        PARTITION BY customer_acquisition_channel 
        ORDER BY transaction_amount DESC
    ) as channel_transaction_rank,
    
    -- Age group spending patterns
    DENSE_RANK() OVER (
        PARTITION BY customer_age_group 
        ORDER BY transaction_amount DESC
    ) as age_group_spending_rank,
    
    -- Multi-dimensional analysis: Channel + Age Group
    PERCENT_RANK() OVER (
        PARTITION BY customer_acquisition_channel, customer_age_group
        ORDER BY transaction_amount
    ) as segment_percentile,
    
    -- Customer lifetime value components
    SUM(transaction_amount) OVER (
        PARTITION BY customer_id 
        ORDER BY transaction_date 
        ROWS UNBOUNDED PRECEDING
    ) as cumulative_customer_value
    
FROM customer_transactions
ORDER BY customer_id, transaction_date;
```