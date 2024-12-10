
# Find Minimum in Rotated Sorted Array

This document provides detailed explanations and Java implementations of various approaches to solve the problem **Find Minimum in Rotated Sorted Array**.

---

## Problem Statement

You are given a rotated sorted array, where the array was originally sorted in ascending order but is rotated at some pivot.  
For example:
- Original: `[1, 2, 3, 4, 5]`
- Rotated: `[4, 5, 1, 2, 3]`

Your task is to find the minimum element in this array in **O(log n)** time.

---

## Constraints
1. The array does not contain duplicates.
2. The array has at least one element.

---

## Approach 1: Binary Search (Optimized Solution)

This approach utilizes **binary search** to achieve a time complexity of `O(log n)`.

### Algorithm

1. **Initialize Pointers**:
   - Use two pointers: `left` at the start of the array and `right` at the end.
   
2. **Check for Already Sorted**:
   - If `nums[left] < nums[right]`, the array is already sorted, so the first element is the minimum.

3. **Binary Search**:
   - While `left < right`:
     - Calculate the middle index: `mid = left + (right - left) / 2`.
     - Compare `nums[mid]` with `nums[right]`:
       - If `nums[mid] > nums[right]`, the minimum lies in the right half; move `left = mid + 1`.
       - Otherwise, the minimum lies in the left half; move `right = mid`.

4. **Return Result**:
   - After the loop, `nums[left]` will hold the minimum element.

### Java Code

```java
public class FindMinimumInRotatedSortedArray {

    public static int findMin(int[] nums) {
        // Initialize pointers
        int left = 0, right = nums.length - 1;

        // If the array is already sorted
        if (nums[left] < nums[right]) {
            return nums[left];
        }

        // Perform binary search
        while (left < right) {
            // Calculate middle index
            int mid = left + (right - left) / 2;

            // If middle element is greater than the rightmost element,
            // the minimum is in the right half
            if (nums[mid] > nums[right]) {
                left = mid + 1;
            } 
            // Otherwise, the minimum is in the left half (including mid)
            else {
                right = mid;
            }
        }

        // Left pointer now points to the minimum element
        return nums[left];
    }

    public static void main(String[] args) {
        // Example usage
        int[] nums = {4, 5, 6, 7, 0, 1, 2};
        System.out.println("The minimum element is: " + findMin(nums));
    }
}
```

### Complexity Analysis

- **Time Complexity**: `O(log n)`  
- **Space Complexity**: `O(1)`

---

## Approach 2: Linear Search

This is the simplest approach, where we traverse the entire array to find the minimum element.

### Algorithm

1. Initialize `minElement` with the first element.
2. Traverse the array from left to right:
   - Compare each element with `minElement`. Update if smaller.
3. Return `minElement`.

### Java Code

```java
public class FindMinimumLinearSearch {

    public static int findMin(int[] nums) {
        // Initialize the minimum element to the first element
        int minElement = nums[0];
        
        // Traverse the array to find the minimum
        for (int i = 1; i < nums.length; i++) {
            if (nums[i] < minElement) {
                minElement = nums[i];
            }
        }
        
        return minElement;
    }

    public static void main(String[] args) {
        // Example usage
        int[] nums = {4, 5, 6, 7, 0, 1, 2};
        System.out.println("The minimum element is: " + findMin(nums));
    }
}
```

### Complexity Analysis

- **Time Complexity**: `O(n)`  
- **Space Complexity**: `O(1)`

---

## Approach 3: Modified Binary Search Using Pivot Detection

This approach uses binary search to directly detect the pivot point.

### Algorithm

1. **Initialize Pointers**:  
   - Start with `left` at the beginning and `right` at the end.

2. **Pivot Detection**:
   - While `left <= right`, calculate `mid = left + (right - left) / 2`.
   - Check for pivot:
     - If `nums[mid] > nums[mid + 1]`, return `nums[mid + 1]`.
     - If `nums[mid - 1] > nums[mid]`, return `nums[mid]`.
   - Adjust pointers based on comparison with `nums[right]`.

3. Return the result.

### Java Code

```java
public class FindMinimumUsingPivotDetection {

    public static int findMin(int[] nums) {
        // Initialize pointers
        int left = 0, right = nums.length - 1;

        // Handle edge cases: single element or already sorted array
        if (nums[left] < nums[right]) {
            return nums[left];
        }

        while (left <= right) {
            // Calculate the mid index
            int mid = left + (right - left) / 2;

            // Check for the pivot point
            if (mid < nums.length - 1 && nums[mid] > nums[mid + 1]) {
                return nums[mid + 1]; // Pivot found, return the next element
            }
            if (mid > 0 && nums[mid - 1] > nums[mid]) {
                return nums[mid]; // Pivot found, return the current element
            }

            // Adjust pointers based on comparison with the rightmost element
            if (nums[mid] > nums[right]) {
                left = mid + 1; // Search in the right half
            } else {
                right = mid - 1; // Search in the left half
            }
        }

        return -1; // This line should never be reached
    }

    public static void main(String[] args) {
        // Example usage
        int[] nums = {4, 5, 6, 7, 0, 1, 2};
        System.out.println("The minimum element is: " + findMin(nums));
    }
}
```

### Complexity Analysis

- **Time Complexity**: `O(log n)`  
- **Space Complexity**: `O(1)`

---

## Comparison of Approaches

| Approach                       | Time Complexity | Space Complexity | Use Case                                                                 |
|--------------------------------|-----------------|------------------|--------------------------------------------------------------------------|
| **Binary Search (Optimized)**  | `O(log n)`      | `O(1)`           | Best for large arrays with sorted rotation.                             |
| **Linear Search**              | `O(n)`          | `O(1)`           | Use for small arrays or as a fallback when binary search is unnecessary. |
| **Pivot Detection (Binary)**   | `O(log n)`      | `O(1)`           | Alternate to optimized binary search; focuses directly on pivot points. |

---

## Notes on Alternative Approaches

1. **Linear Search**: Simple but inefficient for large arrays.
2. **Pivot Detection**: More intuitive for understanding pivot-based logic.

For most cases, the **optimized binary search** is the go-to solution due to its simplicity and efficiency.
