
# Find First and Last Position of Element in Sorted Array

## Problem Statement

Given a sorted array of integers and a target element, your task is to find the first and last position of the target element in the array. If the target element is not found, return `[-1, -1]`.

### Example:

**Input:**

`nums = [5, 7, 7, 8, 8, 10]`, `target = 8`

**Output:**

`[3, 4]`

### Explanation:

- The target `8` appears first at index `3` and last at index `4` in the sorted array.

---

## Approach

This problem can be solved efficiently using binary search because the array is sorted. We can utilize the binary search algorithm to find both the **first** and **last** positions of the target.

### Key Points:
1. The array is sorted, so binary search is appropriate here. 
2. We can perform two separate binary searches:
   - **One to find the first occurrence of the target.**
   - **One to find the last occurrence of the target.**

### Steps:
1. **Binary Search for First Position**:
   - Perform a binary search for the target.
   - If the element at the middle index is equal to the target, we check if it is the first occurrence by ensuring it's either the first element or the previous element is not the target.
   
2. **Binary Search for Last Position**:
   - Perform a binary search for the target.
   - If the element at the middle index is equal to the target, we check if it is the last occurrence by ensuring it's either the last element or the next element is not the target.

3. **Return**:
   - If both positions are found, return the indices as `[firstPosition, lastPosition]`.
   - If the target does not exist in the array, return `[-1, -1]`.

### Time Complexity:
- **O(log n)** for both the first and last binary searches.

---

## Algorithm

1. **Find First Position**:
   - Initialize `low = 0` and `high = nums.length - 1`.
   - Perform binary search:
     - Calculate the middle index: `mid = (low + high) / 2`.
     - If `nums[mid] == target`, check if it's the first position.
     - If `nums[mid] < target`, move the left pointer `low = mid + 1`.
     - If `nums[mid] > target`, move the right pointer `high = mid - 1`.

2. **Find Last Position**:
   - Initialize `low = 0` and `high = nums.length - 1`.
   - Perform binary search:
     - Calculate the middle index: `mid = (low + high) / 2`.
     - If `nums[mid] == target`, check if it's the last position.
     - If `nums[mid] < target`, move the left pointer `low = mid + 1`.
     - If `nums[mid] > target`, move the right pointer `high = mid - 1`.

3. **Return**:
   - If both positions are found, return the indices as `[firstPosition, lastPosition]`.
   - If the target does not exist in the array, return `[-1, -1]`.

---

## Java Code

```java
public class Solution {

    // Function to find the first position of the target
    public int findFirstPosition(int[] nums, int target) {
        int low = 0, high = nums.length - 1, result = -1;
        
        while (low <= high) {
            int mid = low + (high - low) / 2;
            
            if (nums[mid] == target) {
                result = mid; // Target found, continue searching on the left side
                high = mid - 1;
            } else if (nums[mid] < target) {
                low = mid + 1;
            } else {
                high = mid - 1;
            }
        }
        
        return result;
    }

    // Function to find the last position of the target
    public int findLastPosition(int[] nums, int target) {
        int low = 0, high = nums.length - 1, result = -1;
        
        while (low <= high) {
            int mid = low + (high - low) / 2;
            
            if (nums[mid] == target) {
                result = mid; // Target found, continue searching on the right side
                low = mid + 1;
            } else if (nums[mid] < target) {
                low = mid + 1;
            } else {
                high = mid - 1;
            }
        }
        
        return result;
    }

    // Main function to find the first and last position of the target
    public int[] searchRange(int[] nums, int target) {
        int[] result = new int[2];
        
        result[0] = findFirstPosition(nums, target);
        result[1] = findLastPosition(nums, target);
        
        return result;
    }

    public static void main(String[] args) {
        Solution solution = new Solution();
        int[] nums = {5, 7, 7, 8, 8, 10};
        int target = 8;
        
        int[] result = solution.searchRange(nums, target);
        System.out.println("First Position: " + result[0] + ", Last Position: " + result[1]);
    }
}
```

---

## Explanation of Code

- **findFirstPosition**: This function performs a binary search to find the first occurrence of the target. If the middle element is equal to the target, it updates the `result` and keeps searching in the left half of the array (by updating `high`).
  
- **findLastPosition**: This function performs a binary search to find the last occurrence of the target. If the middle element is equal to the target, it updates the `result` and keeps searching in the right half of the array (by updating `low`).

- **searchRange**: This is the main function which calls both `findFirstPosition` and `findLastPosition` and returns the results.

---

## Output

For the given input:

```java
int[] nums = {5, 7, 7, 8, 8, 10};
int target = 8;
```

The output will be:

```
First Position: 3, Last Position: 4
```

If the target is not found:

```java
int[] nums = {1, 2, 3, 4, 5};
int target = 6;
```

The output will be:

```
First Position: -1, Last Position: -1
```

---

## Conclusion

This approach efficiently finds the first and last positions of a target in a sorted array using binary search. With a time complexity of **O(log n)**, this solution is optimal for large datasets.
