# Simple Binary Search

This README explains the Binary Search algorithm in depth, covering the approach, explanation, algorithm, and implementation in Java with comments.

## **Approach**
Binary Search is an efficient algorithm for finding a target value within a sorted array. The algorithm works by repeatedly dividing the search interval in half:

1. Start with the entire array as the search interval.
2. Compare the target value with the middle element of the interval.
   - If the target matches the middle element, return its index.
   - If the target is less than the middle element, narrow the interval to the left half.
   - If the target is greater than the middle element, narrow the interval to the right half.
3. Repeat until the target is found or the search interval is empty.

### **Time Complexity**
- **Best Case**: \(O(1)\) (target is at the middle).
- **Worst Case**: \(O(\log n)\).
- **Space Complexity**: \(O(1)\) for the iterative version and \(O(\log n)\) for the recursive version (due to the call stack).

## **Algorithm**
1. Define the search boundaries (`low` and `high`).
2. While `low <= high`:
   - Calculate the middle index: `mid = low + (high - low) / 2` to avoid overflow.
   - Compare the middle element with the target:
     - If `array[mid] == target`, return `mid`.
     - If `array[mid] < target`, move the `low` boundary to `mid + 1`.
     - If `array[mid] > target`, move the `high` boundary to `mid - 1`.
3. If the target is not found, return -1.

## **Implementation**
Below is the implementation of Binary Search in Java:

```java
public class BinarySearch {

    /**
     * Perform binary search on a sorted array.
     * 
     * @param array The sorted array.
     * @param target The value to search for.
     * @return The index of the target if found; otherwise, -1.
     */
    public static int binarySearch(int[] array, int target) {
        int low = 0;              // Starting index of the search interval.
        int high = array.length - 1; // Ending index of the search interval.

        while (low <= high) {
            // Calculate the middle index to avoid integer overflow.
            int mid = low + (high - low) / 2;

            // Check if the middle element is the target.
            if (array[mid] == target) {
                return mid;
            }

            // If the target is greater, narrow the search to the right half.
            if (array[mid] < target) {
                low = mid + 1;
            } else {
                // If the target is smaller, narrow the search to the left half.
                high = mid - 1;
            }
        }

        // Target is not present in the array.
        return -1;
    }

    public static void main(String[] args) {
        int[] sortedArray = {1, 3, 5, 7, 9, 11, 13};
        int target = 7;

        int result = binarySearch(sortedArray, target);

        if (result != -1) {
            System.out.println("Target found at index: " + result);
        } else {
            System.out.println("Target not found.");
        }
    }
}
```

### **Explanation of Code**
1. **Initialization**: `low` starts at the beginning of the array, and `high` starts at the end.
2. **Loop Condition**: The loop continues as long as the search interval is valid (`low <= high`).
3. **Mid Calculation**: `mid` is calculated to avoid overflow.
4. **Target Check**: Each iteration checks whether the middle element matches the target. If not, the search interval is adjusted based on the target's relation to the middle element.
5. **Return Value**: The index of the target is returned if found, or `-1` if not found.

### **Example Walkthrough**
#### Input:
```plaintext
Array: [1, 3, 5, 7, 9, 11, 13]
Target: 7
```
#### Execution:
1. Initial `low = 0`, `high = 6`.
2. Calculate `mid = 3` (value = 7).
3. `array[mid] == target`.
4. Return `3` (index of the target).

### **Edge Cases**
1. **Empty Array**: Return `-1`.
2. **Target Not Present**: Return `-1`.
3. **Target at Boundaries**: Ensure correct handling of elements at the first or last index.
4. **Duplicate Elements**: The algorithm will return the index of one occurrence.

## **Conclusion**
Binary Search is a powerful and efficient algorithm for searching sorted arrays. Its logarithmic time complexity makes it ideal for scenarios where quick lookups are required.
