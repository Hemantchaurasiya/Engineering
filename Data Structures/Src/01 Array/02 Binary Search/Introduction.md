# Binary Search Notes

## 1. What is Binary Search?
Binary Search is an efficient algorithm used to find the position of a target element within a sorted array. The algorithm works by repeatedly dividing the search interval in half and comparing the target value to the middle element of the interval. If the target matches the middle element, its position is returned. If the target is smaller, the search continues in the left half; otherwise, it continues in the right half.

**Key Characteristics:**
- Requires a sorted array.
- Time complexity: **O(log n)**.
- Space complexity: **O(1)** for iterative implementation, **O(log n)** for recursive.

---

## 2. How to Identify Binary Search in Problems?
Binary Search can be applied if:
1. **Sorted Input:** The problem involves a sorted array or data structure.
2. **Search Condition:** The problem asks to find an element, its position, or check if it exists.
3. **Decision-Based Problems:** The problem involves minimizing or maximizing a function, often indicated by words like "smallest," "largest," "minimum," or "maximum."
4. **Monotonic Properties:** A condition where the function increases or decreases as you move through the input.

---

## 3. Where to Use Binary Search?
Binary Search is used in:
1. **Search Problems:** Finding an element or its index in a sorted array.
2. **Optimization Problems:** Solving problems that involve finding the optimal point in a range.
3. **Decision Problems:** Validating whether a condition is satisfied, such as allocating resources or scheduling.
4. **Infinite Search Space:** Problems where the range isn't explicitly defined but can be narrowed iteratively.

---

## 4. Types of Binary Search
1. **Standard Binary Search:** Search for an element in a sorted array.
2. **Lower Bound Binary Search:** Finds the first position where a condition is satisfied.
3. **Upper Bound Binary Search:** Finds the last position where a condition is satisfied.
4. **Binary Search on Answer:** Used to solve optimization problems by searching over a range of possible values.
5. **Infinite Binary Search:** Handles cases where the size of the array is unknown or infinite.

---

## 5. Algorithm of Binary Search
**Iterative Implementation:**
```java
public int binarySearch(int[] arr, int target) {
    int left = 0, right = arr.length - 1;
    while (left <= right) {
        int mid = left + (right - left) / 2;
        if (arr[mid] == target) {
            return mid;
        } else if (arr[mid] < target) {
            left = mid + 1;
        } else {
            right = mid - 1;
        }
    }
    return -1; // Element not found
}
```

**Recursive Implementation:**
```java
public int binarySearchRecursive(int[] arr, int target, int left, int right) {
    if (left > right) {
        return -1; // Element not found
    }
    int mid = left + (right - left) / 2;
    if (arr[mid] == target) {
        return mid;
    } else if (arr[mid] < target) {
        return binarySearchRecursive(arr, target, mid + 1, right);
    } else {
        return binarySearchRecursive(arr, target, left, mid - 1);
    }
}
```

---

## 6. Sample Standard Problems

### 6.1 Problem: Search Insert Position
**Description:** Find the index where a target should be inserted in a sorted array.

**Approach:**
Use binary search to locate the position where the target fits.

**Code:**
```java
public int searchInsert(int[] nums, int target) {
    int left = 0, right = nums.length - 1;
    while (left <= right) {
        int mid = left + (right - left) / 2;
        if (nums[mid] == target) {
            return mid;
        } else if (nums[mid] < target) {
            left = mid + 1;
        } else {
            right = mid - 1;
        }
    }
    return left;
}
```

### 6.2 Problem: Find Minimum in Rotated Sorted Array
**Description:** Given a rotated sorted array, find the minimum element.

**Approach:**
Use binary search to identify the rotation point.

**Code:**
```java
public int findMin(int[] nums) {
    int left = 0, right = nums.length - 1;
    while (left < right) {
        int mid = left + (right - left) / 2;
        if (nums[mid] > nums[right]) {
            left = mid + 1;
        } else {
            right = mid;
        }
    }
    return nums[left];
}
```

### 6.3 Problem: Median of Two Sorted Arrays
**Description:** Find the median of two sorted arrays.

**Approach:**
Use binary search on one of the arrays to partition both arrays into two halves.

**Code:**
```java
public double findMedianSortedArrays(int[] nums1, int[] nums2) {
    if (nums1.length > nums2.length) {
        return findMedianSortedArrays(nums2, nums1);
    }
    int x = nums1.length, y = nums2.length;
    int low = 0, high = x;

    while (low <= high) {
        int partitionX = (low + high) / 2;
        int partitionY = (x + y + 1) / 2 - partitionX;

        int maxX = (partitionX == 0) ? Integer.MIN_VALUE : nums1[partitionX - 1];
        int minX = (partitionX == x) ? Integer.MAX_VALUE : nums1[partitionX];

        int maxY = (partitionY == 0) ? Integer.MIN_VALUE : nums2[partitionY - 1];
        int minY = (partitionY == y) ? Integer.MAX_VALUE : nums2[partitionY];

        if (maxX <= minY && maxY <= minX) {
            if ((x + y) % 2 == 0) {
                return (Math.max(maxX, maxY) + Math.min(minX, minY)) / 2.0;
            } else {
                return Math.max(maxX, maxY);
            }
        } else if (maxX > minY) {
            high = partitionX - 1;
        } else {
            low = partitionX + 1;
        }
    }
    throw new IllegalArgumentException("Input arrays are not sorted.");
}
```

---

## 7. Revision Points of Binary Search
1. Always ensure the input is sorted before applying binary search.
2. Avoid integer overflow by calculating `mid` as `left + (right - left) / 2`.
3. Clearly define the termination condition, e.g., `left <= right` or `left < right`.
4. Check edge cases like empty arrays or single-element arrays.
5. Understand the difference between `lower_bound` and `upper_bound` searches.
6. Practice common variations like searching for the first/last occurrence of a target.
7. Recognize when to apply binary search on the search space or answer range.

