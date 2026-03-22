# Aggregate Window Functions - Running Totals & Moving Averages
```sql
-- Financial Data for Aggregate Window Functions Demo

CREATE TABLE daily_revenue (
    revenue_date DATE,
    business_unit VARCHAR(30),
    daily_revenue DECIMAL(12,2),
    transactions_count INT,
    avg_transaction_value DECIMAL(8,2)
);

INSERT INTO daily_revenue VALUES
('2024-01-01', 'Retail', 15000.00, 150, 100.00),
('2024-01-02', 'Retail', 18500.00, 185, 100.00),
('2024-01-03', 'Retail', 22000.00, 200, 110.00),
('2024-01-04', 'Retail', 19500.00, 175, 111.43),
('2024-01-05', 'Retail', 21000.00, 190, 110.53),
('2024-01-01', 'Online', 25000.00, 200, 125.00),
('2024-01-02', 'Online', 28000.00, 220, 127.27),
('2024-01-03', 'Online', 31000.00, 240, 129.17),
('2024-01-04', 'Online', 29500.00, 230, 128.26),
('2024-01-05', 'Online', 33000.00, 250, 132.00),
('2024-01-01', 'Wholesale', 45000.00, 50, 900.00),
('2024-01-02', 'Wholesale', 52000.00, 55, 945.45),
('2024-01-03', 'Wholesale', 48000.00, 52, 923.08),
('2024-01-04', 'Wholesale', 51000.00, 54, 944.44),
('2024-01-05', 'Wholesale', 49500.00, 53, 934.91);

-- 1. RUNNING TOTALS with SUM() OVER
-- Essential for financial reports, cumulative metrics
SELECT 
    business_unit,
    revenue_date,
    daily_revenue,
    
    -- Overall running total (across all business units)
    SUM(daily_revenue) OVER (
        ORDER BY revenue_date, business_unit
        ROWS UNBOUNDED PRECEDING
    ) as company_running_total,
    
    -- Business unit specific running total
    SUM(daily_revenue) OVER (
        PARTITION BY business_unit 
        ORDER BY revenue_date
        ROWS UNBOUNDED PRECEDING
    ) as unit_running_total,
    
    -- Percentage of total daily revenue
    ROUND(
        100.0 * daily_revenue / SUM(daily_revenue) OVER (
            PARTITION BY revenue_date
        ), 2
    ) as daily_revenue_percentage

FROM daily_revenue
ORDER BY revenue_date, business_unit;

-- 2. MOVING AVERAGES - Critical for trend analysis
SELECT 
    business_unit,
    revenue_date,
    daily_revenue,
    transactions_count,
    
    -- 3-day moving average (current + 2 previous days)
    AVG(daily_revenue) OVER (
        PARTITION BY business_unit 
        ORDER BY revenue_date
        ROWS 2 PRECEDING
    ) as three_day_moving_avg,
    
    -- 3-day moving average of transaction count
    AVG(transactions_count) OVER (
        PARTITION BY business_unit 
        ORDER BY revenue_date
        ROWS 2 PRECEDING
    ) as three_day_avg_transactions,
    
    -- Centered moving average (1 before + current + 1 after)
    AVG(daily_revenue) OVER (
        PARTITION BY business_unit 
        ORDER BY revenue_date
        ROWS BETWEEN 1 PRECEDING AND 1 FOLLOWING
    ) as centered_moving_avg,
    
    -- Compare current day to moving average (performance indicator)
    ROUND(
        100.0 * (daily_revenue - AVG(daily_revenue) OVER (
            PARTITION BY business_unit 
            ORDER BY revenue_date
            ROWS 2 PRECEDING
        )) / AVG(daily_revenue) OVER (
            PARTITION BY business_unit 
            ORDER BY revenue_date
            ROWS 2 PRECEDING
        ), 2
    ) as performance_vs_avg_pct

FROM daily_revenue
ORDER BY business_unit, revenue_date;

-- 3. ADVANCED WINDOW AGGREGATES - Real-world Stock Analysis Example
CREATE TABLE stock_prices (
    trading_date DATE,
    stock_symbol VARCHAR(10),
    opening_price DECIMAL(8,2),
    closing_price DECIMAL(8,2),
    volume INT,
    market_cap DECIMAL(15,2)
);

INSERT INTO stock_prices VALUES
('2024-01-01', 'TECH', 150.00, 155.50, 1000000, 15500000000),
('2024-01-02', 'TECH', 155.00, 157.25, 1200000, 15725000000),
('2024-01-03', 'TECH', 157.50, 154.75, 1100000, 15475000000),
('2024-01-04', 'TECH', 154.50, 159.00, 1300000, 15900000000),
('2024-01-05', 'TECH', 158.75, 162.25, 1150000, 16225000000),
('2024-01-01', 'FINANCE', 75.25, 76.50, 800000, 7650000000),
('2024-01-02', 'FINANCE', 76.25, 78.00, 850000, 7800000000),
('2024-01-03', 'FINANCE', 78.25, 77.50, 750000, 7750000000),
('2024-01-04', 'FINANCE', 77.75, 79.25, 900000, 7925000000),
('2024-01-05', 'FINANCE', 79.00, 81.00, 950000, 8100000000);

-- Comprehensive Stock Analysis with Window Functions
SELECT 
    stock_symbol,
    trading_date,
    opening_price,
    closing_price,
    volume,
    
    -- Price change from previous day
    closing_price - LAG(closing_price) OVER (
        PARTITION BY stock_symbol 
        ORDER BY trading_date
    ) as daily_change,
    
    -- 5-day moving average price
    AVG(closing_price) OVER (
        PARTITION BY stock_symbol 
        ORDER BY trading_date
        ROWS 4 PRECEDING
    ) as five_day_ma,
    
    -- Volume moving average
    AVG(volume) OVER (
        PARTITION BY stock_symbol 
        ORDER BY trading_date
        ROWS 2 PRECEDING
    ) as three_day_avg_volume,
    
    -- Cumulative volume
    SUM(volume) OVER (
        PARTITION BY stock_symbol 
        ORDER BY trading_date
        ROWS UNBOUNDED PRECEDING
    ) as cumulative_volume,
    
    -- Price volatility (standard deviation over 3 days)
    STDDEV(closing_price) OVER (
        PARTITION BY stock_symbol 
        ORDER BY trading_date
        ROWS 2 PRECEDING
    ) as price_volatility,
    
    -- Relative performance (current price vs period average)
    ROUND(
        100.0 * (closing_price - AVG(closing_price) OVER (
            PARTITION BY stock_symbol
        )) / AVG(closing_price) OVER (
            PARTITION BY stock_symbol
        ), 2
    ) as relative_performance_pct

FROM stock_prices
ORDER BY stock_symbol, trading_date;

-- 4. BUSINESS INTELLIGENCE: Customer Cohort Analysis
CREATE TABLE customer_monthly_metrics (
    customer_id INT,
    signup_month DATE,
    activity_month DATE,
    monthly_revenue DECIMAL(10,2),
    monthly_orders INT,
    customer_segment VARCHAR(20)
);

INSERT INTO customer_monthly_metrics VALUES
(1001, '2024-01-01', '2024-01-01', 500.00, 3, 'Premium'),
(1001, '2024-01-01', '2024-02-01', 650.00, 4, 'Premium'),
(1001, '2024-01-01', '2024-03-01', 720.00, 5, 'Premium'),
(1002, '2024-01-01', '2024-01-01', 200.00, 2, 'Standard'),
(1002, '2024-01-01', '2024-02-01', 180.00, 1, 'Standard'),
(1003, '2024-02-01', '2024-02-01', 800.00, 6, 'Premium'),
(1003, '2024-02-01', '2024-03-01', 950.00, 7, 'Premium'),
(1004, '2024-02-01', '2024-02-01', 300.00, 2, 'Standard'),
(1005, '2024-03-01', '2024-03-01', 450.00, 3, 'Standard');

-- Advanced Customer Lifetime Value Analysis
SELECT 
    customer_id,
    customer_segment,
    signup_month,
    activity_month,
    monthly_revenue,
    monthly_orders,
    
    -- Customer lifetime revenue (running total)
    SUM(monthly_revenue) OVER (
        PARTITION BY customer_id 
        ORDER BY activity_month
        ROWS UNBOUNDED PRECEDING
    ) as lifetime_revenue,
    
    -- Average monthly revenue for this customer
    AVG(monthly_revenue) OVER (
        PARTITION BY customer_id
    ) as avg_monthly_revenue,
    
    -- Segment performance comparison
    AVG(monthly_revenue) OVER (
        PARTITION BY customer_segment, activity_month
    ) as segment_monthly_avg,
    
    -- Cohort analysis: customers from same signup month
    AVG(monthly_revenue) OVER (
        PARTITION BY signup_month, activity_month
    ) as cohort_monthly_avg,
    
    -- Customer growth rate (month-over-month)
    ROUND(
        100.0 * (monthly_revenue - LAG(monthly_revenue) OVER (
            PARTITION BY customer_id 
            ORDER BY activity_month
        )) / NULLIF(LAG(monthly_revenue) OVER (
            PARTITION BY customer_id 
            ORDER BY activity_month
        ), 0), 2
    ) as monthly_growth_rate_pct

FROM customer_monthly_metrics
ORDER BY customer_id, activity_month;

```