
# Find Minimum in Rotated Sorted Array II

## Problem Statement
You are given a rotated sorted array, which may contain duplicates. Find the minimum element in the array.

### Example:
- Input: `nums = [2, 2, 2, 0, 1]`
- Output: `0`

---

## Key Observations
1. A rotated sorted array means the array was initially sorted and then rotated at some pivot.
2. The smallest element is the **only element** that is less than its previous element (in terms of value).
3. Duplicates complicate the process since the usual binary search comparisons (`nums[mid] < nums[right]`) might fail.

---

## Approach 1: Linear Search

### Explanation
When duplicates exist, a brute-force linear search will always work, as we simply iterate through the array to find the minimum.

### Algorithm
1. Initialize `min` to the first element.
2. Traverse the entire array and update `min` whenever a smaller element is found.
3. Return `min`.

### Code
```java
public int findMinLinear(int[] nums) {
    int min = nums[0]; // Initialize min to the first element
    for (int num : nums) {
        if (num < min) {
            min = num; // Update min when a smaller element is found
        }
    }
    return min;
}
```

---

## Approach 2: Binary Search (Optimized)

### Explanation
Binary search is efficient, with **O(log n)** complexity, but duplicates require careful handling:
1. Compare the middle element (`nums[mid]`) with the rightmost element (`nums[right]`).
2. **Cases:**
   - If `nums[mid] < nums[right]`, the minimum is in the **left half** (including `mid`).
   - If `nums[mid] > nums[right]`, the minimum is in the **right half**.
   - If `nums[mid] == nums[right]`, reduce the search space by moving `right` one step left (`right--`).

### Algorithm
1. Initialize two pointers: `left = 0` and `right = nums.length - 1`.
2. While `left < right`:
   - Calculate `mid = left + (right - left) / 2`.
   - Compare `nums[mid]` with `nums[right]` and adjust the search space.
3. Return `nums[left]`.

### Code
```java
public int findMinBinarySearch(int[] nums) {
    int left = 0, right = nums.length - 1;

    while (left < right) {
        int mid = left + (right - left) / 2;

        if (nums[mid] < nums[right]) {
            // Minimum must be in the left half (including mid)
            right = mid;
        } else if (nums[mid] > nums[right]) {
            // Minimum must be in the right half (excluding mid)
            left = mid + 1;
        } else {
            // nums[mid] == nums[right], reduce search space
            right--;
        }
    }
    return nums[left]; // At the end, left == right
}
```

---

## Comparison of Approaches

| Approach       | Time Complexity | Space Complexity | Use Case                                        |
|----------------|-----------------|------------------|------------------------------------------------|
| Linear Search  | O(n)           | O(1)             | Small arrays or when simplicity is preferred. |
| Binary Search  | O(log n)       | O(1)             | Large arrays with better performance needs.   |

---

## Example Walkthrough

### Input: `nums = [2, 2, 2, 0, 1]`

1. **Binary Search Execution Steps:**
   - **Step 1:** `left = 0`, `right = 4`, `mid = 2`  
     Compare `nums[mid] = 2` with `nums[right] = 1`. Since `2 > 1`, set `left = mid + 1 = 3`.
   - **Step 2:** `left = 3`, `right = 4`, `mid = 3`  
     Compare `nums[mid] = 0` with `nums[right] = 1`. Since `0 < 1`, set `right = mid = 3`.
   - **Step 3:** `left = 3`, `right = 3`  
     Return `nums[left] = 0`.

2. **Output:** `0`

---

## Edge Cases
1. **No Rotation:** `nums = [1, 2, 3, 4]`  
   - Both approaches return `1`.
2. **All Duplicates:** `nums = [2, 2, 2, 2]`  
   - Both approaches handle this case correctly.
3. **Single Element:** `nums = [5]`  
   - Both approaches return `5`.

---

## Conclusion
The binary search approach is optimal for solving the problem in most cases. The linear search is simpler and can be used as a fallback for small datasets. Both methods are robust and handle duplicates gracefully.
