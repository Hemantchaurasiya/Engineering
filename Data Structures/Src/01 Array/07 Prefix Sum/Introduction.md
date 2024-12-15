### Prifix Sum
- Prifix Sum
    - [Range Sum Query - Immutable](https://github.com/Hemantchaurasiya/Engineering/blob/Coding_Patterns/Data%20Structures/Src/01%20Array/07%20Prefix%20Sum/Programs/Range%20Sum%20Query%20-%20Immutable.md)
    - [Range Sum Query 2D - Immutable](https://github.com/Hemantchaurasiya/Engineering/blob/Coding_Patterns/Data%20Structures/Src/01%20Array/07%20Prefix%20Sum/Programs/Range%20Sum%20Query%202D%20-%20Immutable.md)
    - [Product of Array Except Self](https://github.com/Hemantchaurasiya/Engineering/blob/Coding_Patterns/Data%20Structures/Src/01%20Array/07%20Prefix%20Sum/Programs/Product%20of%20Array%20Except%20Self.md)
    - [Product of the Last K Numbers](https://github.com/Hemantchaurasiya/Engineering/blob/Coding_Patterns/Data%20Structures/Src/01%20Array/07%20Prefix%20Sum/Programs/Product%20of%20the%20Last%20K%20Numbers.md)
    - [Increment Submatrices by One](https://github.com/Hemantchaurasiya/Engineering/blob/Coding_Patterns/Data%20Structures/Src/01%20Array/07%20Prefix%20Sum/Programs/Increment%20Submatrices%20by%20One.md)

- Line Sweep
    - [Points That Intersect With Cards](https://github.com/Hemantchaurasiya/Engineering/blob/Coding_Patterns/Data%20Structures/Src/01%20Array/07%20Prefix%20Sum/Programs/Points%20That%20Intersect%20With%20Cars.md)
    - [Car Pooling](https://github.com/Hemantchaurasiya/Engineering/blob/Coding_Patterns/Data%20Structures/Src/01%20Array/07%20Prefix%20Sum/Programs/Car%20Pooling.md)
    - [My Calendar II](https://github.com/Hemantchaurasiya/Engineering/blob/Coding_Patterns/Data%20Structures/Src/01%20Array/07%20Prefix%20Sum/Programs/My%20Calendar%20II.md)
    - [Number of Flowers in Full Bloom](https://github.com/Hemantchaurasiya/Engineering/blob/Coding_Patterns/Data%20Structures/Src/01%20Array/07%20Prefix%20Sum/Programs/Number%20of%20Flowers%20in%20Full%20Bloom.md)



# Prefix Sum Notes

## 1. What is Prefix Sum?
Prefix Sum is a pre-computation technique often used in array and range-based problems. It involves creating an auxiliary array where each element at index `i` stores the sum of the array elements from the start up to `i`. This helps in reducing the complexity of range sum queries from O(n) to O(1) after the pre-computation step.

**Example:**
For an array `arr = [1, 2, 3, 4]`, the prefix sum array would be `prefixSum = [1, 3, 6, 10]`, where:
- `prefixSum[0] = arr[0]`
- `prefixSum[1] = arr[0] + arr[1]`
- `prefixSum[2] = arr[0] + arr[1] + arr[2]`
- `prefixSum[3] = arr[0] + arr[1] + arr[2] + arr[3]`

## 2. How to Identify Prefix Sum Problems?
Look for problems where:
- You need to calculate the sum of elements over multiple subarrays or ranges.
- The problem involves optimization in querying or modifying range sums.
- Queries like "sum of elements from index `i` to `j`" are frequent.
- Problems mention cumulative sums, partial sums, or efficient range queries.

## 3. Where to Use Prefix Sum?
Prefix Sum is useful in:
- Calculating range sums in constant time.
- Detecting subarrays with specific properties (e.g., subarray with zero sum, subarray with a given sum).
- Solving 2D matrix problems efficiently (e.g., sum of elements in a sub-matrix).
- Sliding window or optimization problems involving cumulative sums.

## 4. Types of Prefix Sum
- **1D Prefix Sum:** Used for one-dimensional arrays.
- **2D Prefix Sum:** Used for 2D matrices to calculate sum of elements in a sub-matrix.
- **Modified Prefix Sum:** Includes additional constraints like weights, conditions, or modular arithmetic.

## 5. Algorithm of Prefix Sum
### For 1D Array:
1. Initialize an array `prefixSum` of the same size as the input array.
2. Set `prefixSum[0] = arr[0]`.
3. Iterate through the input array from index 1 to n-1 and compute:
   ```java
   prefixSum[i] = prefixSum[i-1] + arr[i];
   ```
4. Use the formula to compute range sum between indices `i` and `j`:
   ```java
   rangeSum = prefixSum[j] - (i > 0 ? prefixSum[i-1] : 0);
   ```

### For 2D Array:
1. Initialize a 2D array `prefixSum` of the same size as the input matrix.
2. Compute prefix sum for each cell using:
   ```java
   prefixSum[i][j] = matrix[i][j] + (i > 0 ? prefixSum[i-1][j] : 0)
                    + (j > 0 ? prefixSum[i][j-1] : 0)
                    - (i > 0 && j > 0 ? prefixSum[i-1][j-1] : 0);
   ```
3. Use the prefix sum to calculate sub-matrix sums efficiently.

## 6. Sample Standard Problems

### Problem 1: Range Sum Query
**Problem:** Given an array, compute the sum of elements between indices `i` and `j` multiple times.

**Approach:**
1. Precompute the prefix sum array.
2. Use the formula:
   ```java
   rangeSum = prefixSum[j] - (i > 0 ? prefixSum[i-1] : 0);
   ```

**Code:**
```java
public class RangeSum {
    public static void main(String[] args) {
        int[] arr = {1, 2, 3, 4, 5};
        int[] prefixSum = new int[arr.length];

        // Compute prefix sum
        prefixSum[0] = arr[0];
        for (int i = 1; i < arr.length; i++) {
            prefixSum[i] = prefixSum[i - 1] + arr[i];
        }

        // Range sum query
        int i = 1, j = 3; // Query from index 1 to 3
        int rangeSum = prefixSum[j] - (i > 0 ? prefixSum[i - 1] : 0);
        System.out.println("Range Sum: " + rangeSum); // Output: 9
    }
}
```

### Problem 2: Subarray Sum Equals K
**Problem:** Count the number of subarrays that sum to a given value `k`.

**Approach:** Use prefix sums and a HashMap to track frequency of prefix sums.

**Code:**
```java
import java.util.HashMap;

public class SubarraySumEqualsK {
    public static void main(String[] args) {
        int[] arr = {1, 2, 3, -2, 1};
        int k = 3;
        System.out.println(countSubarraysWithSumK(arr, k)); // Output: 3
    }

    public static int countSubarraysWithSumK(int[] arr, int k) {
        HashMap<Integer, Integer> prefixSumCount = new HashMap<>();
        prefixSumCount.put(0, 1);

        int prefixSum = 0;
        int count = 0;

        for (int num : arr) {
            prefixSum += num;
            if (prefixSumCount.containsKey(prefixSum - k)) {
                count += prefixSumCount.get(prefixSum - k);
            }
            prefixSumCount.put(prefixSum, prefixSumCount.getOrDefault(prefixSum, 0) + 1);
        }
        return count;
    }
}
```

## 7. Revision Points of Prefix Sum
- Understand the logic of prefix sum and its applications.
- Practice both 1D and 2D prefix sum problems.
- Focus on edge cases, especially for indices starting from 0.
- Explore variations like modular prefix sums or weighted prefix sums.
- Familiarize yourself with common problems like range sum queries, subarray problems, and matrix sub-region sums.

