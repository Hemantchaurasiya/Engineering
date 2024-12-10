# Arranging Coins Problem

The **Arranging Coins** problem asks us to determine the total number of complete rows of coins that can be formed in a staircase arrangement, given `n` coins. Each row requires one more coin than the previous row (i.e., the first row requires 1 coin, the second row requires 2 coins, and so on).

## Problem Statement

You have `n` coins, and you want to build a staircase with them. The staircase consists of rows where the `i-th` row has exactly `i` coins. The last row of the staircase may be incomplete.

**Input:**
- `n` (integer): the total number of coins.

**Output:**
- `k` (integer): the total number of complete rows that can be formed.

---

## Example

### Example 1
**Input:** `n = 5`  
**Output:** `2`  
**Explanation:** The arrangement is:
```
*
**
***
```
Row 1 and Row 2 are complete. Row 3 is incomplete.

### Example 2
**Input:** `n = 8`  
**Output:** `3`  
**Explanation:** The arrangement is:
```
*
**
***
****
```
Row 1, Row 2, and Row 3 are complete. Row 4 is incomplete.

---

## Approaches

### 1. **Brute Force Approach**
In this approach, we iterate through the rows, subtracting the required coins for each row from `n` until we can no longer form a complete row.

#### Algorithm:
1. Initialize a variable `row = 0`.
2. While `n` is greater than or equal to `row + 1`:
    - Increment the `row`.
    - Subtract `row` from `n`.
3. Return the value of `row`.

#### Code:
```java
public int arrangeCoinsBruteForce(int n) {
    int row = 0;
    while (n >= row + 1) {
        row++;
        n -= row;
    }
    return row;
}
```
#### Time Complexity:
- **O(k)**, where `k` is the number of complete rows.

#### Space Complexity:
- **O(1)**

---

### 2. **Mathematical Approach**
We can use the mathematical formula for the sum of the first `k` natural numbers:  
\[ S = \frac{k \times (k + 1)}{2} \]  
We need the largest `k` such that \( S \leq n \).

Rearranging the equation:
\[ k^2 + k - 2n = 0 \]
This is a quadratic equation. Solving for `k`:
\[ k = \frac{-1 + \sqrt{1 + 8n}}{2} \]
Take the floor of this value to get the largest integer `k`.

#### Algorithm:
1. Compute \( k = \frac{-1 + \sqrt{1 + 8n}}{2} \).
2. Return the integer part of `k`.

#### Code:
```java
public int arrangeCoinsMath(int n) {
    return (int)(Math.sqrt(2L * n + 0.25) - 0.5);
}
```
#### Time Complexity:
- **O(1)**

#### Space Complexity:
- **O(1)**

---

### 3. **Binary Search Approach**
We use binary search to find the largest `k` such that \( \frac{k \times (k + 1)}{2} \leq n \).

#### Algorithm:
1. Initialize `low = 0` and `high = n`.
2. While `low <= high`:
    - Compute `mid = low + (high - low) / 2`.
    - Calculate the total coins required for `mid` rows: \( \text{currentSum} = \frac{mid \times (mid + 1)}{2} \).
    - If `currentSum == n`, return `mid`.
    - If `currentSum < n`, set `low = mid + 1`.
    - Otherwise, set `high = mid - 1`.
3. Return `high`.

#### Code:
```java
public int arrangeCoinsBinarySearch(int n) {
    long low = 0, high = n;
    while (low <= high) {
        long mid = low + (high - low) / 2;
        long currentSum = mid * (mid + 1) / 2;
        
        if (currentSum == n) {
            return (int) mid;
        } else if (currentSum < n) {
            low = mid + 1;
        } else {
            high = mid - 1;
        }
    }
    return (int) high;
}
```

#### Time Complexity:
- **O(log n)**

#### Space Complexity:
- **O(1)**

---

## Comparison of Approaches
| Approach            | Time Complexity | Space Complexity | Description                              |
|---------------------|-----------------|------------------|------------------------------------------|
| Brute Force         | O(k)           | O(1)             | Iteratively subtract coins row by row.  |
| Mathematical        | O(1)           | O(1)             | Uses quadratic formula to compute `k`.  |
| Binary Search       | O(log n)       | O(1)             | Efficiently finds `k` using binary search. |

---

## Conclusion
- The **Brute Force** approach is simple but can be slow for large `n`.
- The **Mathematical** approach is the most efficient in terms of time complexity.
- The **Binary Search** approach provides a balance between intuition and efficiency.
