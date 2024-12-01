
# Kth Largest Element in an Array

This document explains two common approaches to find the Kth largest element in an array with step-by-step explanations.

---

## Problem Statement

Given an integer array `nums` and an integer `k`, return the **kth largest** element in the array.

**Example:**
```java
Input: nums = [3,2,1,5,6,4], k = 2  
Output: 5
```

---

## Approach 1: **Using Sorting**

### Explanation
1. Sort the array in descending order.
2. The kth largest element is located at index `k - 1` in the sorted array.
3. Return the element at index `k - 1`.

### Code
```java
import java.util.Arrays;

public class KthLargestElement {
    public int findKthLargest(int[] nums, int k) {
        // Step 1: Sort the array in descending order
        Arrays.sort(nums);
        // Step 2: Return the element at index (nums.length - k)
        return nums[nums.length - k];
    }
}
```

### Complexity Analysis
- **Time Complexity:** \(O(n \log n)\), where \(n\) is the size of the array, due to sorting.
- **Space Complexity:** \(O(1)\), no additional space used.

---

## Approach 2: **Using a Min-Heap**

### Explanation
1. Use a **Min-Heap** (Priority Queue) to keep track of the top `k` elements.
2. For each element in the array:
   - Add the element to the heap.
   - If the heap size exceeds `k`, remove the smallest element (root of the heap).
3. At the end, the root of the heap contains the kth largest element.

### Code
```java
import java.util.PriorityQueue;

public class KthLargestElement {
    public int findKthLargest(int[] nums, int k) {
        // Step 1: Create a Min-Heap
        PriorityQueue<Integer> minHeap = new PriorityQueue<>();
        
        // Step 2: Add elements to the heap
        for (int num : nums) {
            minHeap.add(num);
            // Step 3: If heap size exceeds k, remove the smallest element
            if (minHeap.size() > k) {
                minHeap.poll();
            }
        }
        
        // Step 4: Return the root of the heap
        return minHeap.peek();
    }
}
```

### Complexity Analysis
- **Time Complexity:** \(O(n \log k)\), where \(n\) is the size of the array, and the heap operations take \(O(\log k)\).
- **Space Complexity:** \(O(k)\), due to the heap storing at most \(k\) elements.

---

## Comparison of Approaches

| Approach        | Time Complexity | Space Complexity | Notes                                      |
|------------------|-----------------|------------------|--------------------------------------------|
| Sorting          | \(O(n \log n)\) | \(O(1)\)         | Simple and efficient for small arrays.     |
| Min-Heap         | \(O(n \log k)\) | \(O(k)\)         | Suitable for large arrays with small `k`. |

---

## Summary
Both approaches are effective depending on the input size and requirements:
- Use **sorting** for small arrays or when simplicity is preferred.
- Use a **min-heap** for large arrays with a small `k`.

---
