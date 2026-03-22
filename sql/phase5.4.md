# Advanced Window Functions - LAG, LEAD, FIRST_VALUE, LAST_VALUE
```sql
-- Website Analytics Data for Advanced Window Functions

CREATE TABLE website_analytics (
    analytics_date DATE,
    page_category VARCHAR(30),
    daily_visitors INT,
    bounce_rate DECIMAL(5,2),
    avg_session_duration DECIMAL(6,2),
    conversion_rate DECIMAL(5,4)
);

INSERT INTO website_analytics VALUES
('2024-01-01', 'Home', 5000, 35.50, 180.25, 0.0350),
('2024-01-02', 'Home', 5200, 34.80, 185.50, 0.0365),
('2024-01-03', 'Home', 4800, 36.20, 175.80, 0.0340),
('2024-01-04', 'Home', 5500, 33.90, 190.25, 0.0380),
('2024-01-05', 'Home', 5300, 35.10, 188.75, 0.0375),
('2024-01-01', 'Products', 3200, 42.30, 240.50, 0.0580),
('2024-01-02', 'Products', 3400, 41.80, 245.25, 0.0590),
('2024-01-03', 'Products', 3100, 43.50, 235.80, 0.0560),
('2024-01-04', 'Products', 3600, 40.90, 250.30, 0.0610),
('2024-01-05', 'Products', 3500, 41.20, 248.60, 0.0600),
('2024-01-01', 'Blog', 1800, 28.90, 320.80, 0.0120),
('2024-01-02', 'Blog', 1950, 27.50, 325.60, 0.0130),
('2024-01-03', 'Blog', 1700, 30.20, 315.40, 0.0115),
('2024-01-04', 'Blog', 2100, 26.80, 335.20, 0.0140),
('2024-01-05', 'Blog', 2000, 28.10, 330.50, 0.0135);

-- 1. LAG() and LEAD() Functions - Time Series Comparisons
SELECT 
    page_category,
    analytics_date,
    daily_visitors,
    bounce_rate,
    conversion_rate,
    
    -- Compare with previous day (LAG)
    LAG(daily_visitors, 1) OVER (
        PARTITION BY page_category 
        ORDER BY analytics_date
    ) as previous_day_visitors,
    
    -- Calculate day-over-day change
    daily_visitors - LAG(daily_visitors, 1) OVER (
        PARTITION BY page_category 
        ORDER BY analytics_date
    ) as visitor_change,
    
    -- Percentage change from previous day
    ROUND(
        100.0 * (daily_visitors - LAG(daily_visitors, 1) OVER (
            PARTITION BY page_category 
            ORDER BY analytics_date
        )) / NULLIF(LAG(daily_visitors, 1) OVER (
            PARTITION BY page_category 
            ORDER BY analytics_date
        ), 0), 2
    ) as visitor_change_pct,
    
    -- Look ahead to next day (LEAD)
    LEAD(bounce_rate, 1) OVER (
        PARTITION BY page_category 
        ORDER BY analytics_date
    ) as next_day_bounce_rate,
    
    -- Compare current bounce rate with next day
    bounce_rate - LEAD(bounce_rate, 1) OVER (
        PARTITION BY page_category 
        ORDER BY analytics_date
    ) as bounce_rate_improvement,
    
    -- Week-over-week comparison (if we had weekly data)
    LAG(conversion_rate, 7, 0) OVER (
        PARTITION BY page_category 
        ORDER BY analytics_date
    ) as week_ago_conversion_rate

FROM website_analytics
ORDER BY page_category, analytics_date;

-- 2. FIRST_VALUE() and LAST_VALUE() - Period Comparisons
SELECT 
    page_category,
    analytics_date,
    daily_visitors,
    avg_session_duration,
    
    -- First day of the period
    FIRST_VALUE(daily_visitors) OVER (
        PARTITION BY page_category 
        ORDER BY analytics_date
        ROWS UNBOUNDED PRECEDING
    ) as period_start_visitors,
    
    -- Last day of the period (need to specify frame correctly)
    LAST_VALUE(daily_visitors) OVER (
        PARTITION BY page_category 
        ORDER BY analytics_date
        ROWS BETWEEN UNBOUNDED PRECEDING AND UNBOUNDED FOLLOWING
    ) as period_end_visitors,
    
    -- Growth from start to current day
    ROUND(
        100.0 * (daily_visitors - FIRST_VALUE(daily_visitors) OVER (
            PARTITION BY page_category 
            ORDER BY analytics_date
            ROWS UNBOUNDED PRECEDING
        )) / NULLIF(FIRST_VALUE(daily_visitors) OVER (
            PARTITION BY page_category 
            ORDER BY analytics_date
            ROWS UNBOUNDED PRECEDING
        ), 0), 2
    ) as growth_from_start_pct,
    
    -- Best performance in the period
    MAX(conversion_rate) OVER (
        PARTITION BY page_category
    ) as best_conversion_rate,
    
    -- Worst performance in the period
    MIN(bounce_rate) OVER (
        PARTITION BY page_category
    ) as best_bounce_rate

FROM website_analytics
ORDER BY page_category, analytics_date;

-- 3. NTILE() for Percentile Analysis
SELECT 
    page_category,
    analytics_date,
    daily_visitors,
    conversion_rate,
    
    -- Divide into quartiles based on visitor count
    NTILE(4) OVER (
        PARTITION BY page_category 
        ORDER BY daily_visitors
    ) as visitor_quartile,
    
    -- Divide into thirds based on conversion rate
    NTILE(3) OVER (
        PARTITION BY page_category 
        ORDER BY conversion_rate
    ) as conversion_tertile,
    
    -- Overall performance ranking (all categories combined)
    NTILE(10) OVER (
        ORDER BY conversion_rate DESC
    ) as overall_performance_decile

FROM website_analytics
ORDER BY page_category, daily_visitors DESC;

-- 4. REAL-WORLD EXAMPLE: E-commerce Inventory Management
CREATE TABLE inventory_movements (
    movement_date DATE,
    product_id VARCHAR(20),
    product_category VARCHAR(30),
    movement_type VARCHAR(10), -- 'IN' or 'OUT'
    quantity INT,
    unit_cost DECIMAL(8,2),
    supplier_id INT
);

INSERT INTO inventory_movements VALUES
('2024-01-01', 'LAPTOP001', 'Electronics', 'IN', 50, 800.00, 101),
('2024-01-02', 'LAPTOP001', 'Electronics', 'OUT', -15, 800.00, NULL),
('2024-01-03', 'LAPTOP001', 'Electronics', 'OUT', -12, 800.00, NULL),
('2024-01-04', 'LAPTOP001', 'Electronics', 'IN', 30, 820.00, 101),
('2024-01-05', 'LAPTOP001', 'Electronics', 'OUT', -18, 820.00, NULL),
('2024-01-01', 'DESK001', 'Furniture', 'IN', 25, 350.00, 201),
('2024-01-02', 'DESK001', 'Furniture', 'OUT', -8, 350.00, NULL),
('2024-01-03', 'DESK001', 'Furniture', 'OUT', -5, 350.00, NULL),
('2024-01-04', 'DESK001', 'Furniture', 'IN', 15, 360.00, 201),
('2024-01-05', 'DESK001', 'Furniture', 'OUT', -7, 360.00, NULL);

-- Advanced Inventory Analysis with Window Functions
SELECT 
    product_id,
    product_category,
    movement_date,
    movement_type,
    quantity,
    unit_cost,
    
    -- Running inventory balance
    SUM(quantity) OVER (
        PARTITION BY product_id 
        ORDER BY movement_date, movement_type
        ROWS UNBOUNDED PRECEDING
    ) as current_stock_level,
    
    -- Previous stock level before this transaction
    SUM(quantity) OVER (
        PARTITION BY product_id 
        ORDER BY movement_date, movement_type
        ROWS UNBOUNDED PRECEDING
    ) - quantity as previous_stock_level,
    
    -- Days since last restock
    CASE 
        WHEN movement_type = 'IN' AND quantity > 0 THEN 0
        ELSE analytics_date - LAG(
            CASE WHEN movement_type = 'IN' AND quantity > 0 
                 THEN movement_date END, 1
        ) OVER (
            PARTITION BY product_id 
            ORDER BY movement_date, movement_type
        )
    END as days_since_restock,
    
    -- Average daily consumption (negative quantities)
    AVG(CASE WHEN quantity < 0 THEN ABS(quantity) END) OVER (
        PARTITION BY product_id 
        ORDER BY movement_date
        ROWS 6 PRECEDING
    ) as avg_daily_consumption,
    
    -- Cost trend analysis
    LAG(unit_cost, 1) OVER (
        PARTITION BY product_id, movement_type 
        ORDER BY movement_date
    ) as previous_unit_cost,
    
    -- Price change percentage
    ROUND(
        100.0 * (unit_cost - LAG(unit_cost, 1) OVER (
            PARTITION BY product_id, movement_type 
            ORDER BY movement_date
        )) / NULLIF(LAG(unit_cost, 1) OVER (
            PARTITION BY product_id, movement_type 
            ORDER BY movement_date
        ), 0), 2
    ) as cost_change_pct,
    
    -- Stock-out risk indicator (when current stock < 3x average consumption)
    CASE 
        WHEN SUM(quantity) OVER (
            PARTITION BY product_id 
            ORDER BY movement_date, movement_type
            ROWS UNBOUNDED PRECEDING
        ) < 3 * COALESCE(AVG(CASE WHEN quantity < 0 THEN ABS(quantity) END) OVER (
            PARTITION BY product_id 
            ORDER BY movement_date
            ROWS 6 PRECEDING
        ), 0) THEN 'HIGH RISK'
        WHEN SUM(quantity) OVER (
            PARTITION BY product_id 
            ORDER BY movement_date, movement_type
            ROWS UNBOUNDED PRECEDING
        ) < 5 * COALESCE(AVG(CASE WHEN quantity < 0 THEN ABS(quantity) END) OVER (
            PARTITION BY product_id 
            ORDER BY movement_date
            ROWS 6 PRECEDING
        ), 0) THEN 'MEDIUM RISK'
        ELSE 'LOW RISK'
    END as stockout_risk

FROM inventory_movements
ORDER BY product_id, movement_date, movement_type;

-- 5. ADVANCED FRAME SPECIFICATIONS - Sales Trend Analysis
CREATE TABLE daily_sales_detailed (
    sale_date DATE,
    store_location VARCHAR(30),
    product_line VARCHAR(20),
    daily_sales_amount DECIMAL(10,2),
    weather_condition VARCHAR(20),
    promotional_activity BOOLEAN
);

INSERT INTO daily_sales_detailed VALUES
('2024-01-01', 'Downtown', 'Beverages', 2500.00, 'Sunny', FALSE),
('2024-01-02', 'Downtown', 'Beverages', 2800.00, 'Rainy', TRUE),
('2024-01-03', 'Downtown', 'Beverages', 2300.00, 'Cloudy', FALSE),
('2024-01-04', 'Downtown', 'Beverages', 3200.00, 'Sunny', TRUE),
('2024-01-05', 'Downtown', 'Beverages', 2900.00, 'Sunny', FALSE),
('2024-01-06', 'Downtown', 'Beverages', 3100.00, 'Rainy', FALSE),
('2024-01-07', 'Downtown', 'Beverages', 2700.00, 'Cloudy', FALSE);

-- Complex Window Frames for Trend Analysis
SELECT 
    store_location,
    product_line,
    sale_date,
    daily_sales_amount,
    weather_condition,
    promotional_activity,
    
    -- 3-day centered moving average
    AVG(daily_sales_amount) OVER (
        PARTITION BY store_location, product_line
        ORDER BY sale_date
        ROWS BETWEEN 1 PRECEDING AND 1 FOLLOWING
    ) as centered_3day_avg,
    
    -- Exponential-weighted moving average approximation
    -- (More weight to recent values)
    (0.5 * daily_sales_amount + 
     0.3 * LAG(daily_sales_amount, 1, daily_sales_amount) OVER (
         PARTITION BY store_location, product_line ORDER BY sale_date
     ) +
     0.2 * LAG(daily_sales_amount, 2, daily_sales_amount) OVER (
         PARTITION BY store_location, product_line ORDER BY sale_date
     )) as weighted_moving_avg,
    
    -- Range-based window (all rows with sales within $500 of current)
    COUNT(*) OVER (
        PARTITION BY store_location, product_line
        ORDER BY daily_sales_amount
        RANGE BETWEEN 500 PRECEDING AND 500 FOLLOWING
    ) as similar_sales_days_count,
    
    -- Performance vs. promotional days
    AVG(CASE WHEN promotional_activity THEN daily_sales_amount END) OVER (
        PARTITION BY store_location, product_line
    ) as avg_promotional_sales,
    
    -- Performance vs. non-promotional days
    AVG(CASE WHEN NOT promotional_activity THEN daily_sales_amount END) OVER (
        PARTITION BY store_location, product_line
    ) as avg_regular_sales,
    
    -- Weather impact analysis
    AVG(daily_sales_amount) OVER (
        PARTITION BY store_location, product_line, weather_condition
    ) as avg_sales_by_weather

FROM daily_sales_detailed
ORDER BY store_location, product_line, sale_date;

```