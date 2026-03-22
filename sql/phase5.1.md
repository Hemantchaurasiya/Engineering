# Basic Window Functions - Ranking Examples
```sql
-- Sample Employee Salary Table
CREATE TABLE employees (
    employee_id INT PRIMARY KEY,
    employee_name VARCHAR(50),
    department VARCHAR(30),
    salary DECIMAL(10,2),
    hire_date DATE
);

INSERT INTO employees VALUES
(1, 'Alice Johnson', 'Sales', 75000.00, '2020-01-15'),
(2, 'Bob Smith', 'Sales', 82000.00, '2019-03-22'),
(3, 'Carol Davis', 'Marketing', 78000.00, '2021-06-10'),
(4, 'David Wilson', 'Sales', 82000.00, '2020-08-05'),
(5, 'Eva Brown', 'Marketing', 85000.00, '2019-11-18'),
(6, 'Frank Miller', 'IT', 95000.00, '2018-12-03'),
(7, 'Grace Lee', 'IT', 88000.00, '2021-02-14'),
(8, 'Henry Taylor', 'Sales', 79000.00, '2020-04-30'),
(9, 'Ivy Chen', 'Marketing', 72000.00, '2021-09-12'),
(10, 'Jack Williams', 'IT', 92000.00, '2019-07-08');

-- 1. ROW_NUMBER(): Assigns unique sequential integers
-- Use Case: Creating unique identifiers, pagination
SELECT 
    employee_name,
    department,
    salary,
    ROW_NUMBER() OVER (ORDER BY salary DESC) as overall_rank,
    ROW_NUMBER() OVER (PARTITION BY department ORDER BY salary DESC) as dept_rank
FROM employees;

/*
Result shows:
- Overall ranking by salary (unique numbers even for ties)
- Department-specific ranking
- Each employee gets a unique row number
*/

-- 2. RANK(): Assigns same rank to ties, skips subsequent ranks
-- Use Case: Competition rankings, performance tiers
SELECT 
    employee_name,
    department,
    salary,
    RANK() OVER (ORDER BY salary DESC) as salary_rank,
    RANK() OVER (PARTITION BY department ORDER BY salary DESC) as dept_salary_rank
FROM employees;

/*
Result shows:
- Employees with same salary get same rank
- Next rank is skipped (e.g., if two people tie for 2nd, next person is 4th)
*/

-- 3. DENSE_RANK(): Assigns same rank to ties, no gaps in ranking
-- Use Case: Grade levels, continuous ranking systems
SELECT 
    employee_name,
    department,
    salary,
    DENSE_RANK() OVER (ORDER BY salary DESC) as dense_salary_rank,
    DENSE_RANK() OVER (PARTITION BY department ORDER BY salary DESC) as dense_dept_rank
FROM employees;

/*
Result shows:
- Employees with same salary get same rank
- No gaps in ranking sequence (e.g., if two people tie for 2nd, next person is 3rd)
*/

-- REAL-WORLD EXAMPLE: Sales Performance Dashboard
-- Find top 3 performers in each region with their rankings

CREATE TABLE sales_performance (
    salesperson_id INT,
    salesperson_name VARCHAR(50),
    region VARCHAR(30),
    monthly_sales DECIMAL(12,2),
    sales_month DATE
);

INSERT INTO sales_performance VALUES
(101, 'John Adams', 'North', 125000.00, '2024-01-01'),
(102, 'Sarah Connor', 'North', 135000.00, '2024-01-01'),
(103, 'Mike Ross', 'South', 142000.00, '2024-01-01'),
(104, 'Rachel Green', 'South', 138000.00, '2024-01-01'),
(105, 'Ross Geller', 'East', 131000.00, '2024-01-01'),
(106, 'Monica Bing', 'East', 127000.00, '2024-01-01'),
(107, 'Chandler Bing', 'West', 145000.00, '2024-01-01'),
(108, 'Joey Tribbiani', 'West', 118000.00, '2024-01-01'),
(109, 'Phoebe Buffay', 'North', 135000.00, '2024-01-01'); -- Tie with Sarah

-- Top 3 performers by region with different ranking methods
SELECT 
    salesperson_name,
    region,
    monthly_sales,
    ROW_NUMBER() OVER (PARTITION BY region ORDER BY monthly_sales DESC) as row_rank,
    RANK() OVER (PARTITION BY region ORDER BY monthly_sales DESC) as standard_rank,
    DENSE_RANK() OVER (PARTITION BY region ORDER BY monthly_sales DESC) as dense_rank
FROM sales_performance
WHERE ROW_NUMBER() OVER (PARTITION BY region ORDER BY monthly_sales DESC) <= 3
ORDER BY region, monthly_sales DESC;

-- Note: The WHERE clause with window function requires a subquery in most databases
-- Here's the correct approach:

WITH ranked_sales AS (
    SELECT 
        salesperson_name,
        region,
        monthly_sales,
        ROW_NUMBER() OVER (PARTITION BY region ORDER BY monthly_sales DESC) as row_rank,
        RANK() OVER (PARTITION BY region ORDER BY monthly_sales DESC) as standard_rank,
        DENSE_RANK() OVER (PARTITION BY region ORDER BY monthly_sales DESC) as dense_rank
    FROM sales_performance
)
SELECT * FROM ranked_sales 
WHERE row_rank <= 3
ORDER BY region, monthly_sales DESC;

```