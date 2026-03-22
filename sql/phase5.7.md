# Executive Dashboard - Complete Business Intelligence Solution
```sql
-- EXECUTIVE DASHBOARD: COMPLETE BUSINESS INTELLIGENCE SOLUTION
-- This project integrates all window function concepts for a real-world scenario

-- 1. Create comprehensive business dataset
CREATE TABLE business_metrics (
    metric_date DATE,
    business_unit VARCHAR(30),
    region VARCHAR(20),
    revenue DECIMAL(12,2),
    costs DECIMAL(12,2),
    customers_acquired INT,
    customers_lost INT,
    active_customers INT,
    marketing_spend DECIMAL(10,2),
    sales_team_size INT,
    customer_satisfaction_score DECIMAL(3,2)
);

INSERT INTO business_metrics VALUES
-- January 2024 data
('2024-01-01', 'E-commerce', 'North America', 2500000, 1800000, 150, 45, 12500, 200000, 25, 4.2),
('2024-01-01', 'E-commerce', 'Europe', 1800000, 1300000, 120, 35, 8900, 150000, 18, 4.1),
('2024-01-01', 'E-commerce', 'Asia Pacific', 2200000, 1600000, 180, 40, 11200, 180000, 22, 4.3),
('2024-01-01', 'Retail', 'North America', 3200000, 2400000, 80, 25, 15600, 120000, 35, 4.0),
('2024-01-01', 'Retail', 'Europe', 2100000, 1600000, 60, 20, 9800, 90000, 28, 3.9),
('2024-01-01', 'B2B Services', 'North America', 1900000, 1100000, 25, 8, 2800, 80000, 15, 4.4),
-- February 2024 data
('2024-02-01', 'E-commerce', 'North America', 2650000, 1850000, 165, 50, 12615, 210000, 26, 4.3),
('2024-02-01', 'E-commerce', 'Europe', 1950000, 1350000, 135, 38, 8997, 160000, 19, 4.2),
('2024-02-01', 'E-commerce', 'Asia Pacific', 2380000, 1680000, 195, 42, 11353, 190000, 23, 4.4),
('2024-02-01', 'Retail', 'North America', 3100000, 2350000, 75, 30, 15645, 115000, 35, 4.1),
('2024-02-01', 'Retail', 'Europe', 2250000, 1650000, 68, 22, 9846, 95000, 29, 4.0),
('2024-02-01', 'B2B Services', 'North America', 2100000, 1150000, 30, 10, 2820, 85000, 16, 4.5),
-- March 2024 data
('2024-03-01', 'E-commerce', 'North America', 2780000, 1900000, 180, 48, 12747, 220000, 27, 4.4),
('2024-03-01', 'E-commerce', 'Europe', 2100000, 1400000, 145, 35, 9107, 170000, 20, 4.3),
('2024-03-01', 'E-commerce', 'Asia Pacific', 2520000, 1720000, 210, 45, 11518, 200000, 24, 4.5),
('2024-03-01', 'Retail', 'North America', 3350000, 2450000, 85, 28, 15702, 125000, 36, 4.2),
('2024-03-01', 'Retail', 'Europe', 2400000, 1700000, 72, 25, 9893, 100000, 30, 4.1),
('2024-03-01', 'B2B Services', 'North America', 2250000, 1200000, 35, 12, 2843, 90000, 17, 4.6);

-- 2. EXECUTIVE SUMMARY DASHBOARD
-- Key metrics with trend analysis, rankings, and performance indicators

WITH monthly_kpis AS (
    SELECT 
        metric_date,
        business_unit,
        region,
        revenue,
        costs,
        revenue - costs as profit,
        customers_acquired,
        customers_lost,
        active_customers,
        marketing_spend,
        customer_satisfaction_score,
        
        -- Calculate derived KPIs
        ROUND(100.0 * (revenue - costs) / revenue, 2) as profit_margin_pct,
        ROUND(revenue / NULLIF(marketing_spend, 0), 2) as marketing_roi,
        ROUND(revenue / NULLIF(active_customers, 0), 2) as revenue_per_customer,
        ROUND(marketing_spend / NULLIF(customers_acquired, 0), 2) as customer_acquisition_cost,
        ROUND(100.0 * customers_lost / NULLIF(active_customers, 0), 2) as churn_rate_pct
        
    FROM business_metrics
),
executive_analytics AS (
    SELECT 
        metric_date,
        business_unit,
        region,
        revenue,
        profit,
        profit_margin_pct,
        marketing_roi,
        revenue_per_customer,
        customer_acquisition_cost,
        churn_rate_pct,
        customer_satisfaction_score,
        active_customers,
        
        -- GROWTH ANALYSIS
        LAG(revenue) OVER (
            PARTITION BY business_unit, region 
            ORDER BY metric_date
        ) as prev_month_revenue,
        
        ROUND(
            100.0 * (revenue - LAG(revenue) OVER (
                PARTITION BY business_unit, region 
                ORDER BY metric_date
            )) / NULLIF(LAG(revenue) OVER (
                PARTITION BY business_unit, region 
                ORDER BY metric_date
            ), 0), 2
        ) as revenue_growth_mom_pct,
        
        -- TREND ANALYSIS (3-month moving averages)
        AVG(revenue) OVER (
            PARTITION BY business_unit, region 
            ORDER BY metric_date
            ROWS 2 PRECEDING
        ) as revenue_3m_avg,
        
        AVG(profit_margin_pct) OVER (
            PARTITION BY business_unit, region 
            ORDER BY metric_date
            ROWS 2 PRECEDING
        ) as profit_margin_3m_avg,
        
        -- COMPETITIVE POSITIONING
        RANK() OVER (
            PARTITION BY metric_date 
            ORDER BY revenue DESC
        ) as revenue_rank_overall,
        
        RANK() OVER (
            PARTITION BY metric_date, business_unit 
            ORDER BY revenue DESC
        ) as revenue_rank_within_unit,
        
        -- PERFORMANCE PERCENTILES
        PERCENT_RANK() OVER (
            PARTITION BY metric_date 
            ORDER BY profit_margin_pct
        ) as profit_margin_percentile,
        
        PERCENT_RANK() OVER (
            PARTITION BY metric_date 
            ORDER BY customer_satisfaction_score
        ) as satisfaction_percentile,
        
        -- PORTFOLIO ANALYSIS
        SUM(revenue) OVER (
            PARTITION BY metric_date
        ) as total_company_revenue,
        
        ROUND(
            100.0 * revenue / SUM(revenue) OVER (
                PARTITION BY metric_date
            ), 2
        ) as revenue_contribution_pct,
        
        -- YEAR-TO-DATE PERFORMANCE
        SUM(revenue) OVER (
            PARTITION BY business_unit, region 
            ORDER BY metric_date
            ROWS UNBOUNDED PRECEDING
        ) as ytd_revenue,
        
        SUM(profit) OVER (
            PARTITION BY business_unit, region 
            ORDER BY metric_date
            ROWS UNBOUNDED PRECEDING
        ) as ytd_profit,
        
        -- CUSTOMER METRICS TRENDS
        LAG(active_customers) OVER (
            PARTITION BY business_unit, region 
            ORDER BY metric_date
        ) as prev_active_customers,
        
        active_customers - LAG(active_customers) OVER (
            PARTITION BY business_unit, region 
            ORDER BY metric_date
        ) as net_customer_change
        
    FROM monthly_kpis
)
SELECT 
    metric_date,
    business_unit,
    region,
    
    -- FINANCIAL PERFORMANCE
    FORMAT(revenue, 'C') as revenue_formatted,
    FORMAT(profit, 'C') as profit_formatted,
    profit_margin_pct,
    revenue_growth_mom_pct,
    revenue_contribution_pct,
    
    -- RANKINGS & BENCHMARKS  
    revenue_rank_overall,
    revenue_rank_within_unit,
    ROUND(profit_margin_percentile * 100, 0) as profit_margin_percentile_rank,
    ROUND(satisfaction_percentile * 100, 0) as satisfaction_percentile_rank,
    
    -- CUSTOMER INSIGHTS
    active_customers,
    net_customer_change,
    revenue_per_customer,
    customer_acquisition_cost,
    churn_rate_pct,
    customer_satisfaction_score,
    
    -- TREND INDICATORS
    CASE 
        WHEN revenue > revenue_3m_avg * 1.05 THEN '📈 Strong Growth'
        WHEN revenue < revenue_3m_avg * 0.95 THEN '📉 Declining'
        ELSE '➡️ Stable'
    END as revenue_trend,
    
    CASE 
        WHEN profit_margin_pct > profit_margin_3m_avg + 2 THEN '💰 Improving'
        WHEN profit_margin_pct < profit_margin_3m_avg - 2 THEN '⚠️ Declining'
        ELSE '➡️ Stable'
    END as profitability_trend,
    
    -- PERFORMANCE ALERTS
    CASE 
        WHEN revenue_growth_mom_pct < -10 THEN '🚨 Revenue Alert'
        WHEN profit_margin_pct < 15 THEN '⚠️ Margin Alert'
        WHEN churn_rate_pct > 5 THEN '🚨 Churn Alert'
        WHEN customer_satisfaction_score < 4.0 THEN '⚠️ Satisfaction Alert'
        ELSE '✅ Healthy'
    END as performance_status,
    
    -- YTD PERFORMANCE
    FORMAT(ytd_revenue, 'C') as ytd_revenue_formatted,
    FORMAT(ytd_profit, 'C') as ytd_profit_formatted,
    
    -- EFFICIENCY METRICS
    marketing_roi,
    CASE 
        WHEN marketing_roi > 10 THEN 'Excellent'
        WHEN marketing_roi > 5 THEN 'Good'
        WHEN marketing_roi > 2 THEN 'Fair'
        ELSE 'Poor'
    END as marketing_efficiency_rating

FROM executive_analytics
ORDER BY metric_date DESC, revenue DESC;

-- 3. DETAILED BUSINESS UNIT PERFORMANCE COMPARISON

WITH unit_comparison AS (
    SELECT 
        metric_date,
        business_unit,
        SUM(revenue) as total_revenue,
        SUM(revenue - costs) as total_profit,
        SUM(active_customers) as total_customers,
        SUM(marketing_spend) as total_marketing_spend,
        AVG(customer_satisfaction_score) as avg_satisfaction,
        
        -- Growth calculations
        LAG(SUM(revenue)) OVER (
            PARTITION BY business_unit 
            ORDER BY metric_date
        ) as prev_month_unit_revenue,
        
        -- Market share within company
        ROUND(
            100.0 * SUM(revenue) / SUM(SUM(revenue)) OVER (
                PARTITION BY metric_date
            ), 2
        ) as company_revenue_share_pct
        
    FROM business_metrics
    GROUP BY metric_date, business_unit
)
SELECT 
    metric_date,
    business_unit,
    FORMAT(total_revenue, 'C') as revenue,
    FORMAT(total_profit, 'C') as profit,
    ROUND(100.0 * total_profit / total_revenue, 2) as profit_margin_pct,
    company_revenue_share_pct,
    
    -- Growth analysis
    ROUND(
        100.0 * (total_revenue - prev_month_unit_revenue) / 
        NULLIF(prev_month_unit_revenue, 0), 2
    ) as revenue_growth_pct,
    
    -- Efficiency metrics
    total_customers,
    ROUND(total_revenue / total_customers, 2) as revenue_per_customer,
    ROUND(total_revenue / total_marketing_spend, 2) as marketing_roi,
    avg_satisfaction,
    
    -- Competitive position
    RANK() OVER (
        PARTITION BY metric_date 
        ORDER BY total_revenue DESC
    ) as revenue_rank,
    
    RANK() OVER (
        PARTITION BY metric_date 
        ORDER BY (100.0 * total_profit / total_revenue) DESC
    ) as profitability_rank,
    
    -- Performance categorization
    NTILE(3) OVER (
        PARTITION BY metric_date 
        ORDER BY total_revenue
    ) as performance_tier,
    
    CASE 
        WHEN NTILE(3) OVER (
            PARTITION BY metric_date 
            ORDER BY total_revenue
        ) = 3 THEN 'Top Performer'
        WHEN NTILE(3) OVER (
            PARTITION BY metric_date 
            ORDER BY total_revenue
        ) = 2 THEN 'Mid Performer'
        ELSE 'Needs Focus'
    END as performance_category

FROM unit_comparison
ORDER BY metric_date DESC, total_revenue DESC;

-- 4. REGIONAL PERFORMANCE HEATMAP DATA

SELECT 
    region,
    business_unit,
    metric_date,
    revenue,
    
    -- Regional rankings
    DENSE_RANK() OVER (
        PARTITION BY metric_date, business_unit 
        ORDER BY revenue DESC
    ) as regional_rank,
    
    -- Performance vs regional average
    ROUND(
        100.0 * (revenue - AVG(revenue) OVER (
            PARTITION BY metric_date, region
        )) / AVG(revenue) OVER (
            PARTITION BY metric_date, region
        ), 2
    ) as vs_regional_avg_pct,
    
    -- Performance vs business unit average
    ROUND(
        100.0 * (revenue - AVG(revenue) OVER (
            PARTITION BY metric_date, business_unit
        )) / AVG(revenue) OVER (
            PARTITION BY metric_date, business_unit
        ), 2
    ) as vs_unit_avg_pct,
    
    -- Growth momentum
    CASE 
        WHEN revenue > LAG(revenue, 1) OVER (
            PARTITION BY business_unit, region 
            ORDER BY metric_date
        ) AND LAG(revenue, 1) OVER (
            PARTITION BY business_unit, region 
            ORDER BY metric_date
        ) > LAG(revenue, 2) OVER (
            PARTITION BY business_unit, region 
            ORDER BY metric_date
        ) THEN 'Accelerating'
        WHEN revenue > LAG(revenue, 1) OVER (
            PARTITION BY business_unit, region 
            ORDER BY metric_date
        ) THEN 'Growing'
        WHEN revenue < LAG(revenue, 1) OVER (
            PARTITION BY business_unit, region 
            ORDER BY metric_date
        ) THEN 'Declining'
        ELSE 'Stable'
    END

```