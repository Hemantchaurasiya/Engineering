# Window Frame Specifications & Performance Optimization
```sql
-- WINDOW FRAME SPECIFICATIONS - ROWS vs RANGE

-- Understanding different frame types is crucial for accurate analytics
-- ROWS: Physical number of rows
-- RANGE: Logical range based on values

CREATE TABLE stock_price_movements (
    trading_date DATE,
    stock_symbol VARCHAR(10),
    closing_price DECIMAL(8,2),
    trading_volume BIGINT,
    market_cap DECIMAL(15,2)
);

INSERT INTO stock_price_movements VALUES
('2024-01-01', 'TECH', 100.00, 1000000, 10000000000),
('2024-01-02', 'TECH', 102.50, 1200000, 10250000000),
('2024-01-03', 'TECH', 105.00, 1100000, 10500000000),
('2024-01-04', 'TECH', 103.75, 1300000, 10375000000),
('2024-01-05', 'TECH', 107.25, 1150000, 10725000000),
('2024-01-06', 'TECH', 106.00, 1050000, 10600000000),
('2024-01-07', 'TECH', 109.50, 1400000, 10950000000),
('2024-01-01', 'FINANCE', 50.00, 800000, 5000000000),
('2024-01-02', 'FINANCE', 51.25, 850000, 5125000000),
('2024-01-03', 'FINANCE', 52.75, 900000, 5275000000),
('2024-01-04', 'FINANCE', 51.50, 750000, 5150000000),
('2024-01-05', 'FINANCE', 53.00, 920000, 5300000000),
('2024-01-06', 'FINANCE', 54.25, 880000, 5425000000),
('2024-01-07', 'FINANCE', 55.75, 950000, 5575000000);

-- 1. ROWS vs RANGE Frame Specifications
SELECT 
    stock_symbol,
    trading_date,
    closing_price,
    trading_volume,
    
    -- ROWS frame: Exactly 3 physical rows (current + 2 preceding)
    AVG(closing_price) OVER (
        PARTITION BY stock_symbol 
        ORDER BY trading_date
        ROWS 2 PRECEDING
    ) as rows_3day_avg,
    
    -- RANGE frame: All rows within 2 days of current date
    AVG(closing_price) OVER (
        PARTITION BY stock_symbol 
        ORDER BY trading_date
        RANGE BETWEEN INTERVAL '2 days' PRECEDING AND CURRENT ROW
    ) as range_3day_avg,
    
    -- ROWS with specific boundaries
    SUM(trading_volume) OVER (
        PARTITION BY stock_symbol 
        ORDER BY trading_date
        ROWS BETWEEN 1 PRECEDING AND 1 FOLLOWING
    ) as rows_centered_volume_sum,
    
    -- RANGE with value boundaries (prices within $2 of current price)
    COUNT(*) OVER (
        PARTITION BY stock_symbol 
        ORDER BY closing_price
        RANGE BETWEEN 2 PRECEDING AND 2 FOLLOWING
    ) as similar_price_days_count,
    
    -- Complex frame: Unbounded preceding to current row
    MAX(closing_price) OVER (
        PARTITION BY stock_symbol 
        ORDER BY trading_date
        ROWS UNBOUNDED PRECEDING
    ) as period_high_price,
    
    -- Frame with following rows (requires UNBOUNDED FOLLOWING for LAST_VALUE)
    LAST_VALUE(closing_price) OVER (
        PARTITION BY stock_symbol 
        ORDER BY trading_date
        ROWS BETWEEN CURRENT ROW AND UNBOUNDED FOLLOWING
    ) as period_final_price

FROM stock_price_movements
ORDER BY stock_symbol, trading_date;

-- 2. PERFORMANCE OPTIMIZATION TECHNIQUES

-- Create indexes for optimal window function performance
-- Note: These are examples - actual syntax varies by database system

/*
-- Recommended indexes for window function queries:
CREATE INDEX idx_stock_symbol_date ON stock_price_movements (stock_symbol, trading_date);
CREATE INDEX idx_date_symbol ON stock_price_movements (trading_date, stock_symbol);
CREATE INDEX idx_symbol_price ON stock_price_movements (stock_symbol, closing_price);
*/

-- Demonstrate efficient vs inefficient window function usage
-- EFFICIENT: Reuse window specifications
SELECT 
    stock_symbol,
    trading_date,
    closing_price,
    trading_volume,
    
    -- Define window specification once, reuse multiple times
    AVG(closing_price) OVER w as avg_price,
    MIN(closing_price) OVER w as min_price,
    MAX(closing_price) OVER w as max_price,
    COUNT(*) OVER w as day_count,
    SUM(trading_volume) OVER w as total_volume
    
FROM stock_price_movements
WINDOW w AS (
    PARTITION BY stock_symbol 
    ORDER BY trading_date 
    ROWS 2 PRECEDING
)
ORDER BY stock_symbol, trading_date;

-- INEFFICIENT (avoid this pattern):
-- Repeating the same window specification multiple times
/*
SELECT 
    stock_symbol,
    trading_date,
    AVG(closing_price) OVER (PARTITION BY stock_symbol ORDER BY trading_date ROWS 2 PRECEDING),
    MIN(closing_price) OVER (PARTITION BY stock_symbol ORDER BY trading_date ROWS 2 PRECEDING),
    MAX(closing_price) OVER (PARTITION BY stock_symbol ORDER BY trading_date ROWS 2 PRECEDING)
FROM stock_price_movements;
*/

-- 3. COMPLEX FRAME SPECIFICATIONS for Financial Analysis

CREATE TABLE option_trading_data (
    trading_date DATE,
    option_symbol VARCHAR(20),
    strike_price DECIMAL(8,2),
    option_type VARCHAR(4), -- CALL or PUT
    premium DECIMAL(6,2),
    volume INT,
    open_interest INT,
    underlying_price DECIMAL(8,2)
);

INSERT INTO option_trading_data VALUES
('2024-01-01', 'TECH240315C110', 110.00, 'CALL', 2.50, 500, 1200, 108.00),
('2024-01-01', 'TECH240315C115', 115.00, 'CALL', 1.75, 300, 800, 108.00),
('2024-01-01', 'TECH240315C120', 120.00, 'CALL', 1.25, 200, 600, 108.00),
('2024-01-01', 'TECH240315P105', 105.00, 'PUT', 1.80, 400, 900, 108.00),
('2024-01-01', 'TECH240315P100', 100.00, 'PUT', 1.20, 350, 750, 108.00),
('2024-01-02', 'TECH240315C110', 110.00, 'CALL', 3.25, 600, 1250, 111.50),
('2024-01-02', 'TECH240315C115', 115.00, 'CALL', 2.40, 450, 820, 111.50),
('2024-01-02', 'TECH240315C120', 120.00, 'CALL', 1.80, 280, 610, 111.50),
('2024-01-02', 'TECH240315P105', 105.00, 'PUT', 1.20, 300, 880, 111.50),
('2024-01-02', 'TECH240315P100', 100.00, 'PUT', 0.85, 250, 720, 111.50);

-- Advanced Options Chain Analysis with Complex Frames
SELECT 
    trading_date,
    option_symbol,
    strike_price,
    option_type,
    premium,
    volume,
    open_interest,
    underlying_price,
    
    -- Strike price ladder analysis (options within $5 strike range)
    AVG(premium) OVER (
        PARTITION BY trading_date, option_type
        ORDER BY strike_price
        RANGE BETWEEN 5 PRECEDING AND 5 FOLLOWING
    ) as strike_range_avg_premium,
    
    -- Volume-weighted average premium
    SUM(premium * volume) OVER (
        PARTITION BY trading_date, option_type
        ORDER BY strike_price
        RANGE BETWEEN 2.5 PRECEDING AND 2.5 FOLLOWING
    ) / NULLIF(SUM(volume) OVER (
        PARTITION BY trading_date, option_type
        ORDER BY strike_price  
        RANGE BETWEEN 2.5 PRECEDING AND 2.5 FOLLOWING
    ), 0) as vwap_premium,
    
    -- Moneyness analysis (distance from underlying price)
    ABS(strike_price - underlying_price) as moneyness,
    
    -- At-the-money options (closest to underlying price)
    RANK() OVER (
        PARTITION BY trading_date, option_type
        ORDER BY ABS(strike_price - underlying_price)
    ) as atm_rank,
    
    -- Liquidity analysis across strike ladder
    SUM(volume) OVER (
        PARTITION BY trading_date, option_type
        ORDER BY strike_price
        ROWS BETWEEN 1 PRECEDING AND 1 FOLLOWING
    ) as strike_ladder_liquidity,
    
    -- Premium decay analysis (day-over-day change)
    premium - LAG(premium) OVER (
        PARTITION BY option_symbol 
        ORDER BY trading_date
    ) as daily_premium_change,
    
    -- Relative volume (current vs average)
    ROUND(
        100.0 * volume / AVG(volume) OVER (
            PARTITION BY option_symbol
        ), 2
    ) as relative_volume_pct

FROM option_trading_data
ORDER BY trading_date, option_type, strike_price;

-- 4. MEMORY-EFFICIENT WINDOW FUNCTIONS
-- Techniques for handling large datasets

-- Use CTEs to break down complex calculations
WITH daily_calculations AS (
    SELECT 
        stock_symbol,
        trading_date,
        closing_price,
        trading_volume,
        -- Calculate basic metrics first
        ROW_NUMBER() OVER (PARTITION BY stock_symbol ORDER BY trading_date) as day_number
    FROM stock_price_movements
),
trend_analysis AS (
    SELECT 
        *,
        -- Add trend calculations
        AVG(closing_price) OVER (
            PARTITION BY stock_symbol 
            ORDER BY day_number 
            ROWS 2 PRECEDING
        ) as moving_avg_3day,
        LAG(closing_price, 1) OVER (
            PARTITION BY stock_symbol 
            ORDER BY day_number
        ) as prev_day_price
    FROM daily_calculations
)
SELECT 
    stock_symbol,
    trading_date,
    closing_price,
    trading_volume,
    day_number,
    moving_avg_3day,
    prev_day_price,
    -- Final calculations
    CASE 
        WHEN closing_price > moving_avg_3day THEN 'Above Trend'
        WHEN closing_price < moving_avg_3day THEN 'Below Trend'
        ELSE 'On Trend'
    END as trend_position,
    ROUND(
        100.0 * (closing_price - prev_day_price) / NULLIF(prev_day_price, 0), 2
    ) as daily_return_pct
FROM trend_analysis
ORDER BY stock_symbol, trading_date;

-- 5. ERROR HANDLING AND EDGE CASES

CREATE TABLE sparse_sales_data (
    sale_date DATE,
    product_id VARCHAR(20),
    daily_sales DECIMAL(10,2),
    units_sold INT
);

-- Insert data with gaps (missing dates)
INSERT INTO sparse_sales_data VALUES
('2024-01-01', 'PROD001', 1000.00, 10),
('2024-01-03', 'PROD001', 1200.00, 12),  -- Missing 01-02
('2024-01-05', 'PROD001', 800.00, 8),    -- Missing 01-04
('2024-01-06', 'PROD001', 1100.00, 11),
('2024-01-01', 'PROD002', 500.00, 5),
('2024-01-02', 'PROD002', 600.00, 6),
('2024-01-04', 'PROD002', 700.00, 7);    -- Missing 01-03

-- Handling gaps and NULL values in window functions
SELECT 
    product_id,
    sale_date,
    daily_sales,
    units_sold,
    
    -- Standard moving average (ignores missing dates)
    AVG(daily_sales) OVER (
        PARTITION BY product_id 
        ORDER BY sale_date
        ROWS 2 PRECEDING
    ) as simple_moving_avg,
    
    -- Handle NULLs with COALESCE
    COALESCE(
        LAG(daily_sales, 1) OVER (
            PARTITION BY product_id 
            ORDER BY sale_date
        ), 0
    ) as prev_day_sales_safe,
    
    -- Calculate days between sales (to identify gaps)
    sale_date - LAG(sale_date) OVER (
        PARTITION BY product_id 
        ORDER BY sale_date
    ) as days_since_last_sale,
    
    -- Running total with NULL handling
    SUM(COALESCE(daily_sales, 0)) OVER (
        PARTITION BY product_id 
        ORDER BY sale_date
        ROWS UNBOUNDED PRECEDING
    ) as running_total_safe,
    
    -- Percentage change with division by zero protection
    ROUND(
        100.0 * (daily_sales - LAG(daily_sales) OVER (
            PARTITION BY product_id 
            ORDER BY sale_date
        )) / NULLIF(LAG(daily_sales) OVER (
            PARTITION BY product_id 
            ORDER BY sale_date
        ), 0), 2
    ) as safe_pct_change,
    
    -- First non-null value in the window
    FIRST_VALUE(daily_sales) OVER (
        PARTITION BY product_id 
        ORDER BY sale_date
        ROWS UNBOUNDED PRECEDING
    ) as first_recorded_sales

FROM sparse_sales_data
ORDER BY product_id, sale_date;

-- Performance Tips Summary:
/*
1. Use proper indexing on PARTITION BY and ORDER BY columns
2. Reuse window specifications with WINDOW clause
3. Avoid complex calculations within window functions
4. Use CTEs to break down complex window function queries
5. Be careful with RANGE vs ROWS - ROWS is usually more efficient
6. Consider the size of your partitions
7. Use LIMIT when testing on large datasets
8. Handle NULLs and edge cases explicitly
9. Monitor query execution plans
10. Consider materialized views for frequently-used window calculations
*/

```