
# Count Negative Numbers in a Sorted Matrix

## Problem Statement

Given an `m x n` matrix `grid` sorted in non-increasing order both row-wise and column-wise, count the number of negative numbers in it.

### Example:
```java
Input:
grid = [
 [4, 3, 2, 1],
 [3, 2, 1, -1],
 [1, 1, -1, -2],
 [-1, -1, -2, -3]
]

Output: 8
```

### Constraints:
- `m == grid.length`
- `n == grid[i].length`
- `1 <= m, n <= 1000`
- `-100 <= grid[i][j] <= 100`

---

## Approaches

### Approach 1: Brute Force (O(m * n))

1. **Explanation**:
   - Loop through each element in the matrix and check if it is negative.
   - Count the negative elements.

2. **Algorithm**:
   - Loop through each row in the matrix.
   - For each row, loop through each element.
   - If the element is negative, increment the count.

3. **Code**:

```java
public class NegativeNumbers {
    public static int countNegatives(int[][] grid) {
        int count = 0;
        // Loop through each row of the matrix
        for (int[] row : grid) {
            // Loop through each element in the row
            for (int num : row) {
                if (num < 0) {
                    count++; // If the number is negative, increment the count
                }
            }
        }
        return count;
    }

    public static void main(String[] args) {
        int[][] grid = {
            {4, 3, 2, 1},
            {3, 2, 1, -1},
            {1, 1, -1, -2},
            {-1, -1, -2, -3}
        };
        System.out.println("Count of negative numbers: " + countNegatives(grid));
    }
}
```

4. **Time Complexity**: O(m * n), where m is the number of rows and n is the number of columns. We visit each element once.
5. **Space Complexity**: O(1), as we are only using a constant amount of extra space.

---

### Approach 2: Optimized Approach Using Binary Search (O(m * log(n)))

1. **Explanation**:
   - Since the matrix is sorted, we can use binary search to find the first negative element in each row.
   - For each row, we find the index of the first negative element and count how many elements from that point onward are negative.

2. **Algorithm**:
   - Iterate over each row.
   - Use binary search to find the first negative number in that row.
   - Count how many numbers from that index are negative.

3. **Code**:

```java
public class NegativeNumbers {
    // Helper function to perform binary search in each row
    private static int findFirstNegative(int[] row) {
        int left = 0, right = row.length - 1;
        while (left <= right) {
            int mid = left + (right - left) / 2;
            if (row[mid] < 0) {
                right = mid - 1;  // Negative found, look for earlier negatives
            } else {
                left = mid + 1;  // Positive number, ignore it
            }
        }
        return left; // Left will point to the first negative element
    }

    public static int countNegatives(int[][] grid) {
        int count = 0;
        // Loop through each row and find the first negative element using binary search
        for (int[] row : grid) {
            int firstNegativeIndex = findFirstNegative(row);
            // All elements from firstNegativeIndex to the end of the row are negative
            count += row.length - firstNegativeIndex;
        }
        return count;
    }

    public static void main(String[] args) {
        int[][] grid = {
            {4, 3, 2, 1},
            {3, 2, 1, -1},
            {1, 1, -1, -2},
            {-1, -1, -2, -3}
        };
        System.out.println("Count of negative numbers: " + countNegatives(grid));
    }
}
```

4. **Time Complexity**: O(m * log(n)), where m is the number of rows and n is the number of columns. Binary search in each row takes O(log n).
5. **Space Complexity**: O(1), as we are only using a constant amount of extra space.

---

### Approach 3: Optimized Approach Using Row and Column Traversal (O(m + n))

1. **Explanation**:
   - Start from the top-right corner of the matrix.
   - If the current element is negative, move left to find more negative numbers in the same row.
   - If the current element is positive, move down to the next row.

2. **Algorithm**:
   - Initialize row index to 0 and column index to n - 1 (top-right corner).
   - If the element is negative, increment the count and move left.
   - If the element is positive, move down to the next row.
   - Stop when you exceed the bounds of the matrix.

3. **Code**:

```java
public class NegativeNumbers {
    public static int countNegatives(int[][] grid) {
        int count = 0;
        int row = 0;
        int col = grid[0].length - 1;
        
        // Traverse the matrix from top-right corner
        while (row < grid.length && col >= 0) {
            if (grid[row][col] < 0) {
                count += grid.length - row; // All elements from row to the end are negative
                col--; // Move left to find more negative numbers
            } else {
                row++; // Move down if the element is non-negative
            }
        }
        return count;
    }

    public static void main(String[] args) {
        int[][] grid = {
            {4, 3, 2, 1},
            {3, 2, 1, -1},
            {1, 1, -1, -2},
            {-1, -1, -2, -3}
        };
        System.out.println("Count of negative numbers: " + countNegatives(grid));
    }
}
```

4. **Time Complexity**: O(m + n), where m is the number of rows and n is the number of columns. We traverse each row and column only once.
5. **Space Complexity**: O(1), as we only use a constant amount of extra space.

---

## Conclusion

- The brute-force approach is easy to implement but inefficient for larger matrices.
- The binary search approach optimizes the search for negative numbers in each row, improving time complexity.
- The traversal approach, starting from the top-right corner, is the most efficient, achieving a time complexity of O(m + n).
