# Advanced Analytical Queries - Business Intelligence Scenarios

```sql
-- COMPREHENSIVE BUSINESS INTELLIGENCE SCENARIOS

-- 1. CUSTOMER COHORT RETENTION ANALYSIS
-- Understanding how customer behavior changes over time

CREATE TABLE customer_activity_log (
    customer_id INT,
    signup_date DATE,
    activity_date DATE,
    activity_type VARCHAR(30),
    revenue_generated DECIMAL(10,2),
    customer_segment VARCHAR(20)
);

INSERT INTO customer_activity_log VALUES
(1001, '2024-01-01', '2024-01-15', 'Purchase', 250.00, 'Premium'),
(1001, '2024-01-01', '2024-02-10', 'Purchase', 180.00, 'Premium'),
(1001, '2024-01-01', '2024-03-05', 'Purchase', 320.00, 'Premium'),
(1002, '2024-01-01', '2024-01-20', 'Purchase', 150.00, 'Standard'),
(1002, '2024-01-01', '2024-02-15', 'Purchase', 90.00, 'Standard'),
(1003, '2024-01-15', '2024-01-25', 'Purchase', 400.00, 'Premium'),
(1003, '2024-01-15', '2024-02-20', 'Purchase', 280.00, 'Premium'),
(1004, '2024-02-01', '2024-02-10', 'Purchase', 200.00, 'Standard'),
(1005, '2024-02-01', '2024-02-12', 'Purchase', 350.00, 'Premium'),
(1005, '2024-02-01', '2024-03-08', 'Purchase', 420.00, 'Premium');

-- Advanced Cohort Analysis Query
WITH customer_periods AS (
    SELECT 
        customer_id,
        customer_segment,
        signup_date,
        activity_date,
        revenue_generated,
        -- Calculate period number (0 = signup period, 1 = first period after signup, etc.)
        EXTRACT(EPOCH FROM (activity_date - signup_date)) / 86400 / 30 as period_number,
        -- Month of signup for cohort grouping
        DATE_TRUNC('month', signup_date) as cohort_month
    FROM customer_activity_log
),
cohort_metrics AS (
    SELECT 
        customer_id,
        customer_segment,
        cohort_month,
        period_number,
        activity_date,
        revenue_generated,
        
        -- Cumulative revenue by customer
        SUM(revenue_generated) OVER (
            PARTITION BY customer_id 
            ORDER BY activity_date
            ROWS UNBOUNDED PRECEDING
        ) as cumulative_customer_revenue,
        
        -- Customer's rank within their cohort by total revenue
        DENSE_RANK() OVER (
            PARTITION BY cohort_month, customer_segment
            ORDER BY SUM(revenue_generated) OVER (
                PARTITION BY customer_id
            ) DESC
        ) as cohort_revenue_rank,
        
        -- Average revenue per customer in same cohort and period
        AVG(revenue_generated) OVER (
            PARTITION BY cohort_month, FLOOR(period_number)
        ) as cohort_period_avg_revenue,
        
        -- Customer retention indicator
        COUNT(DISTINCT customer_id) OVER (
            PARTITION BY cohort_month, FLOOR(period_number)
        ) as active_customers_in_period,
        
        -- First activity revenue for comparison
        FIRST_VALUE(revenue_generated) OVER (
            PARTITION BY customer_id 
            ORDER BY activity_date
            ROWS UNBOUNDED PRECEDING
        ) as first_purchase_amount
        
    FROM customer_periods
)
SELECT 
    cohort_month,
    customer_segment,
    FLOOR(period_number) as period,
    COUNT(DISTINCT customer_id) as active_customers,
    AVG(revenue_generated) as avg_period_revenue,
    SUM(revenue_generated) as total_period_revenue,
    AVG(cumulative_customer_revenue) as avg_cumulative_revenue,
    -- Retention rate compared to first period
    ROUND(
        100.0 * COUNT(DISTINCT customer_id) / 
        FIRST_VALUE(COUNT(DISTINCT customer_id)) OVER (
            PARTITION BY cohort_month, customer_segment 
            ORDER BY FLOOR(period_number)
            ROWS UNBOUNDED PRECEDING
        ), 2
    ) as retention_rate_pct
FROM cohort_metrics
GROUP BY cohort_month, customer_segment, FLOOR(period_number)
ORDER BY cohort_month, customer_segment, period;

-- 2. SALES PERFORMANCE & QUOTA ANALYSIS
-- Tracking sales rep performance against targets with trend analysis

CREATE TABLE sales_rep_performance (
    rep_id INT,
    rep_name VARCHAR(50),
    territory VARCHAR(30),
    month_date DATE,
    monthly_sales DECIMAL(12,2),
    monthly_quota DECIMAL(12,2),
    deals_closed INT,
    new_customers_acquired INT
);

INSERT INTO sales_rep_performance VALUES
(201, 'John Smith', 'West Coast', '2024-01-01', 125000, 100000, 12, 8),
(201, 'John Smith', 'West Coast', '2024-02-01', 135000, 110000, 15, 10),
(201, 'John Smith', 'West Coast', '2024-03-01', 118000, 115000, 11, 7),
(202, 'Sarah Johnson', 'East Coast', '2024-01-01', 145000, 120000, 18, 12),
(202, 'Sarah Johnson', 'East Coast', '2024-02-01', 152000, 125000, 20, 14),
(202, 'Sarah Johnson', 'East Coast', '2024-03-01', 139000, 130000, 16, 11),
(203, 'Mike Davis', 'Central', '2024-01-01', 98000, 95000, 9, 6),
(203, 'Mike Davis', 'Central', '2024-02-01', 105000, 100000, 11, 8),
(203, 'Mike Davis', 'Central', '2024-03-01', 112000, 105000, 13, 9);

-- Comprehensive Sales Performance Analysis
SELECT 
    rep_name,
    territory,
    month_date,
    monthly_sales,
    monthly_quota,
    deals_closed,
    new_customers_acquired,
    
    -- Quota attainment percentage
    ROUND(100.0 * monthly_sales / monthly_quota, 2) as quota_attainment_pct,
    
    -- Cumulative sales performance
    SUM(monthly_sales) OVER (
        PARTITION BY rep_id 
        ORDER BY month_date
        ROWS UNBOUNDED PRECEDING
    ) as cumulative_sales,
    
    -- Cumulative quota
    SUM(monthly_quota) OVER (
        PARTITION BY rep_id 
        ORDER BY month_date
        ROWS UNBOUNDED PRECEDING
    ) as cumulative_quota,
    
    -- Month-over-month growth
    ROUND(
        100.0 * (monthly_sales - LAG(monthly_sales) OVER (
            PARTITION BY rep_id ORDER BY month_date
        )) / NULLIF(LAG(monthly_sales) OVER (
            PARTITION BY rep_id ORDER BY month_date
        ), 0), 2
    ) as mom_growth_pct,
    
    -- 3-month moving average
    AVG(monthly_sales) OVER (
        PARTITION BY rep_id 
        ORDER BY month_date
        ROWS 2 PRECEDING
    ) as three_month_avg_sales,
    
    -- Territory ranking by monthly sales
    RANK() OVER (
        PARTITION BY territory, month_date 
        ORDER BY monthly_sales DESC
    ) as territory_monthly_rank,
    
    -- Overall company ranking
    RANK() OVER (
        PARTITION BY month_date 
        ORDER BY monthly_sales DESC
    ) as company_monthly_rank,
    
    -- Performance consistency (standard deviation)
    STDDEV(monthly_sales) OVER (
        PARTITION BY rep_id
    ) as sales_consistency_stddev,
    
    -- Customer acquisition efficiency
    ROUND(monthly_sales / NULLIF(new_customers_acquired, 0), 2) as revenue_per_new_customer,
    
    -- Deal size analysis
    ROUND(monthly_sales / NULLIF(deals_closed, 0), 2) as avg_deal_size,
    
    -- Trend analysis: Is performance improving?
    CASE 
        WHEN monthly_sales > LAG(monthly_sales, 1) OVER (
            PARTITION BY rep_id ORDER BY month_date
        ) AND LAG(monthly_sales, 1) OVER (
            PARTITION BY rep_id ORDER BY month_date
        ) > LAG(monthly_sales, 2) OVER (
            PARTITION BY rep_id ORDER BY month_date
        ) THEN 'Improving Trend'
        WHEN monthly_sales < LAG(monthly_sales, 1) OVER (
            PARTITION BY rep_id ORDER BY month_date
        ) AND LAG(monthly_sales, 1) OVER (
            PARTITION BY rep_id ORDER BY month_date
        ) < LAG(monthly_sales, 2) OVER (
            PARTITION BY rep_id ORDER BY month_date
        ) THEN 'Declining Trend'
        ELSE 'Stable/Mixed'
    END as performance_trend

FROM sales_rep_performance
ORDER BY rep_name, month_date;

-- 3. ADVANCED PRODUCT PERFORMANCE ANALYSIS
-- Multi-dimensional analysis of product performance across different segments

CREATE TABLE product_sales_analytics (
    product_id VARCHAR(20),
    product_category VARCHAR(30),
    sales_channel VARCHAR(20),
    customer_segment VARCHAR(20),
    sale_date DATE,
    units_sold INT,
    unit_price DECIMAL(8,2),
    cost_per_unit DECIMAL(8,2),
    discount_applied DECIMAL(5,4)
);

INSERT INTO product_sales_analytics VALUES
('LAPTOP-001', 'Electronics', 'Online', 'Consumer', '2024-01-15', 25, 1200.00, 800.00, 0.0500),
('LAPTOP-001', 'Electronics', 'Retail', 'Consumer', '2024-01-16', 18, 1200.00, 800.00, 0.0300),
('LAPTOP-001', 'Electronics', 'B2B', 'Enterprise', '2024-01-17', 50, 1150.00, 800.00, 0.0800),
('TABLET-001', 'Electronics', 'Online', 'Consumer', '2024-01-15', 40, 600.00, 350.00, 0.0400),
('TABLET-001', 'Electronics', 'Retail', 'Consumer', '2024-01-16', 30, 600.00, 350.00, 0.0200),
('DESK-001', 'Furniture', 'Online', 'Consumer', '2024-01-15', 15, 800.00, 500.00, 0.0600),
('DESK-001', 'Furniture', 'B2B', 'Enterprise', '2024-01-16', 25, 750.00, 500.00, 0.0900),
('CHAIR-001', 'Furniture', 'Retail', 'Consumer', '2024-01-17', 35, 400.00, 220.00, 0.0300);

-- Multi-Dimensional Product Performance Analysis
WITH product_metrics AS (
    SELECT 
        product_id,
        product_category,
        sales_channel,
        customer_segment,
        sale_date,
        units_sold,
        unit_price,
        cost_per_unit,
        discount_applied,
        
        -- Calculate derived metrics
        units_sold * unit_price * (1 - discount_applied) as net_revenue,
        units_sold * (unit_price * (1 - discount_applied) - cost_per_unit) as gross_profit,
        unit_price * (1 - discount_applied) - cost_per_unit as unit_gross_profit
    FROM product_sales_analytics
)
SELECT 
    product_id,
    product_category,
    sales_channel,
    customer_segment,
    sale_date,
    units_sold,
    net_revenue,
    gross_profit,
    
    -- Product performance ranking within category
    RANK() OVER (
        PARTITION BY product_category, sale_date 
        ORDER BY net_revenue DESC
    ) as category_revenue_rank,
    
    -- Channel performance for this product
    RANK() OVER (
        PARTITION BY product_id, sale_date 
        ORDER BY net_revenue DESC
    ) as product_channel_rank,
    
    -- Segment analysis
    AVG(net_revenue) OVER (
        PARTITION BY customer_segment, product_category
    ) as segment_category_avg_revenue,
    
    -- Channel efficiency comparison
    AVG(unit_gross_profit) OVER (
        PARTITION BY sales_channel
    ) as channel_avg_unit_profit,
    
    -- Product profitability percentile
    PERCENT_RANK() OVER (
        ORDER BY unit_gross_profit
    ) as profitability_percentile,
    
    -- Market share within category (by units)
    ROUND(
        100.0 * units_sold / SUM(units_sold) OVER (
            PARTITION BY product_category, sale_date
        ), 2
    ) as category_market_share_pct,
    
    -- Revenue share within category
    ROUND(
        100.0 * net_revenue / SUM(net_revenue) OVER (
            PARTITION BY product_category, sale_date
        ), 2
    ) as category_revenue_share_pct,
    
    -- Cross-channel performance comparison
    MAX(net_revenue) OVER (
        PARTITION BY product_id
    ) as best_channel_revenue,
    
    MIN(net_revenue) OVER (
        PARTITION BY product_id
    ) as worst_channel_revenue,
    
    -- Performance vs category average
    ROUND(
        100.0 * (unit_gross_profit - AVG(unit_gross_profit) OVER (
            PARTITION BY product_category
        )) / NULLIF(AVG(unit_gross_profit) OVER (
            PARTITION BY product_category
        ), 0), 2
    ) as vs_category_avg_profit_pct

FROM product_metrics
ORDER BY product_category, net_revenue DESC;

-- 4. TIME-BASED TREND ANALYSIS - Website Traffic & Conversion
-- Analyzing patterns, seasonality, and performance trends

CREATE TABLE website_conversion_funnel (
    analysis_date DATE,
    traffic_source VARCHAR(30),
    device_type VARCHAR(20),
    visitors INT,
    page_views INT,
    cart_additions INT,
    checkouts_initiated INT,
    purchases_completed INT,
    total_revenue DECIMAL(12,2)
);

INSERT INTO website_conversion_funnel VALUES
('2024-01-01', 'Organic Search', 'Desktop', 1500, 4200, 180, 90, 75, 8500.00),
('2024-01-01', 'Organic Search', 'Mobile', 2200, 3800, 165, 70, 55, 6200.00),
('2024-01-01', 'Paid Search', 'Desktop', 800, 2100, 220, 140, 120, 15600.00),
('2024-01-01', 'Social Media', 'Mobile', 1200, 2000, 95, 40, 28, 3200.00),
('2024-01-02', 'Organic Search', 'Desktop', 1600, 4500, 195, 98, 82, 9100.00),
('2024-01-02', 'Organic Search', 'Mobile', 2400, 4100, 180, 78, 62, 6800.00),
('2024-01-02', 'Paid Search', 'Desktop', 750, 1950, 200, 125, 105, 13800.00),
('2024-01-02', 'Social Media', 'Mobile', 1100, 1850, 85, 35, 25, 2900.00);

-- Advanced Conversion Funnel Analysis
SELECT 
    analysis_date,
    traffic_source,
    device_type,
    visitors,
    page_views,
    cart_additions,
    checkouts_initiated,
    purchases_completed,
    total_revenue,
    
    -- Conversion rates at each stage
    ROUND(100.0 * cart_additions / NULLIF(visitors, 0), 2) as visitor_to_cart_rate,
    ROUND(100.0 * checkouts_initiated / NULLIF(cart_additions, 0), 2) as cart_to_checkout_rate,
    ROUND(100.0 * purchases_completed / NULLIF(checkouts_initiated, 0), 2) as checkout_to_purchase_rate,
    ROUND(100.0 * purchases_completed / NULLIF(visitors, 0), 2) as overall_conversion_rate,
    
    -- Revenue metrics
    ROUND(total_revenue / NULLIF(purchases_completed, 0), 2) as avg_order_value,
    ROUND(total_revenue / NULLIF(visitors, 0), 2) as revenue_per_visitor,
    
    -- Day-over-day comparisons
    LAG(visitors) OVER (
        PARTITION BY traffic_source, device_type 
        ORDER BY analysis_date
    ) as prev_day_visitors,
    
    ROUND(
        100.0 * (visitors - LAG(visitors) OVER (
            PARTITION BY traffic_source, device_type 
            ORDER BY analysis_date
        )) / NULLIF(LAG(visitors) OVER (
            PARTITION BY traffic_source, device_type 
            ORDER BY analysis_date
        ), 0), 2
    ) as visitor_growth_pct,
    
    -- Performance ranking by source and device
    RANK() OVER (
        PARTITION BY analysis_date 
        ORDER BY total_revenue DESC
    ) as daily_revenue_rank,
    
    -- Conversion rate ranking
    RANK() OVER (
        PARTITION BY analysis_date 
        ORDER BY (100.0 * purchases_completed / NULLIF(visitors, 0)) DESC
    ) as daily_conversion_rank,
    
    -- Moving averages for trend analysis
    AVG(total_revenue) OVER (
        PARTITION BY traffic_source, device_type 
        ORDER BY analysis_date
        ROWS 1 PRECEDING
    ) as two_day_avg_revenue,
    
    -- Market share by traffic source
    ROUND(
        100.0 * visitors / SUM(visitors) OVER (
            PARTITION BY analysis_date
        ), 2
    ) as daily_visitor_share_pct,
    
    ROUND(
        100.0 * total_revenue / SUM(total_revenue) OVER (
            PARTITION BY analysis_date
        ), 2
    ) as daily_revenue_share_pct

FROM website_conversion_funnel
ORDER BY analysis_date, total_revenue DESC;

```