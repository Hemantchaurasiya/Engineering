
# Find K Closest Elements - Problem Notes

## Problem Statement
Given a **sorted array** `arr`, two integers `k` and `x`, return the `k` closest integers to `x` in the array. The result should also be sorted in ascending order. If there is a tie, the smaller number is preferred.

---

## Table of Contents
1. **Approach 1: Sorting with Custom Comparator**
2. **Approach 2: Two Pointers Technique**
3. **Approach 3: Binary Search with Sliding Window**
4. **Approach 4: Min-Heap Priority Queue**

---

## Approach 1: Sorting with Custom Comparator

### Explanation
- Calculate the absolute difference between each element and `x`.
- Sort the array based on the absolute difference (in ascending order). If two numbers have the same difference, the smaller number is preferred.
- Pick the first `k` elements and sort them.

### Algorithm
1. Sort the array with a custom comparator:
   - Sort primarily by the absolute difference from `x`.
   - If two numbers have the same difference, sort by their value.
2. Take the first `k` elements.
3. Sort the result in ascending order.

### Code (Java)
```java
import java.util.*;

public class FindKClosestElements {
    public static List<Integer> findClosestElementsSort(int[] arr, int k, int x) {
        List<Integer> result = new ArrayList<>();
        for (int num : arr) {
            result.add(num);
        }

        // Sort with custom comparator
        result.sort((a, b) -> {
            int diff1 = Math.abs(a - x);
            int diff2 = Math.abs(b - x);
            if (diff1 == diff2) return a - b; // Prefer smaller value
            return diff1 - diff2;
        });

        // Take the first k elements and sort them
        result = result.subList(0, k);
        Collections.sort(result); // Ensure the result is sorted
        return result;
    }

    public static void main(String[] args) {
        int[] arr = {1, 2, 3, 4, 5};
        int k = 4, x = 3;
        System.out.println(findClosestElementsSort(arr, k, x)); // Output: [1, 2, 3, 4]
    }
}
```

---

## Approach 2: Two Pointers Technique

### Explanation
- Use two pointers: one starting from the beginning of the array and one from the end.
- Gradually move the pointers closer together until only `k` elements remain.
- Compare the absolute difference of the elements pointed by the two pointers with `x` to decide which pointer to move.

### Algorithm
1. Initialize two pointers: `left` at `0` and `right` at `n-1`.
2. While the range between `left` and `right` is greater than `k`:
   - If the element at `left` is farther from `x` than the element at `right`, move `left` forward.
   - Otherwise, move `right` backward.
3. The elements between `left` and `right` (inclusive) are the result.

### Code (Java)
```java
import java.util.*;

public class FindKClosestElements {
    public static List<Integer> findClosestElementsTwoPointers(int[] arr, int k, int x) {
        int left = 0, right = arr.length - 1;

        // Narrow the range to exactly k elements
        while (right - left >= k) {
            if (Math.abs(arr[left] - x) > Math.abs(arr[right] - x)) {
                left++;
            } else {
                right--;
            }
        }

        // Collect the result
        List<Integer> result = new ArrayList<>();
        for (int i = left; i <= right; i++) {
            result.add(arr[i]);
        }
        return result;
    }

    public static void main(String[] args) {
        int[] arr = {1, 2, 3, 4, 5};
        int k = 4, x = 3;
        System.out.println(findClosestElementsTwoPointers(arr, k, x)); // Output: [1, 2, 3, 4]
    }
}
```

---

## Approach 3: Binary Search with Sliding Window

### Explanation
- Use binary search to find the potential position of `x` in the array.
- Expand a sliding window of size `k` around this position by comparing elements on both sides of the window.

### Algorithm
1. Use `Arrays.binarySearch()` or a custom binary search to find the position `idx` where `x` could be inserted.
2. Initialize the window boundaries: `left = idx - k` and `right = idx + k`.
3. Ensure the window boundaries are valid within the array.
4. Shrink the window to size `k` by comparing elements at `left` and `right`.

### Code (Java)
```java
import java.util.*;

public class FindKClosestElements {
    public static List<Integer> findClosestElementsBinarySearch(int[] arr, int k, int x) {
        int left = 0, right = arr.length - k;

        // Perform binary search to find the starting index of the closest window
        while (left < right) {
            int mid = left + (right - left) / 2;
            if (x - arr[mid] > arr[mid + k] - x) {
                left = mid + 1; // Shift the window right
            } else {
                right = mid; // Shift the window left
            }
        }

        // Collect the result
        List<Integer> result = new ArrayList<>();
        for (int i = left; i < left + k; i++) {
            result.add(arr[i]);
        }
        return result;
    }

    public static void main(String[] args) {
        int[] arr = {1, 2, 3, 4, 5};
        int k = 4, x = 3;
        System.out.println(findClosestElementsBinarySearch(arr, k, x)); // Output: [1, 2, 3, 4]
    }
}
```

---

## Approach 4: Min-Heap Priority Queue

### Explanation
- Push all elements into a Min-Heap with their absolute difference from `x` as the priority.
- Pop the top `k` elements from the heap.
- Sort the result.

### Algorithm
1. Initialize a priority queue with a custom comparator for absolute difference.
2. Push all elements with their absolute difference into the heap.
3. Poll the first `k` elements from the heap.
4. Sort the result.

### Code (Java)
```java
import java.util.*;

public class FindKClosestElements {
    public static List<Integer> findClosestElementsHeap(int[] arr, int k, int x) {
        PriorityQueue<int[]> heap = new PriorityQueue<>((a, b) -> {
            if (a[1] == b[1]) return a[0] - b[0]; // Compare by value for ties
            return a[1] - b[1]; // Compare by absolute difference
        });

        // Add elements to the heap
        for (int num : arr) {
            heap.offer(new int[]{num, Math.abs(num - x)});
        }

        // Extract k closest elements
        List<Integer> result = new ArrayList<>();
        while (k-- > 0) {
            result.add(heap.poll()[0]);
        }

        // Sort the result
        Collections.sort(result);
        return result;
    }

    public static void main(String[] args) {
        int[] arr = {1, 2, 3, 4, 5};
        int k = 4, x = 3;
        System.out.println(findClosestElementsHeap(arr, k, x)); // Output: [1, 2, 3, 4]
    }
}
```

---

## Time Complexity
| Approach                   | Time Complexity  | Space Complexity |
|----------------------------|------------------|------------------|
| Sorting (Custom Comparator)| O(n log n)       | O(n)             |
| Two Pointers               | O(n)             | O(1)             |
| Binary Search + Sliding    | O(log n + k)     | O(k)             |
| Min-Heap                   | O(n log n + k)   | O(n)             |

---

## Summary
The most efficient approach for large arrays is **Binary Search with Sliding Window**, as it has a logarithmic search time and minimal space usage.
