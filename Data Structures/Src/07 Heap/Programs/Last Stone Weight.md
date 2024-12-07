
# Last Stone Weight Problem

## Problem Description
You are given an array of integers, `stones`, where each element represents the weight of a stone. The task is to smash the stones as follows:
1. Select the two heaviest stones (`x` and `y`), where \( y \geq x \).
2. If \( x 
eq y \), the remaining stone has weight \( y - x \). If \( x = y \), both stones are destroyed.
3. Repeat this process until there is at most one stone left.

Return the weight of the last remaining stone, or `0` if no stones remain.

---

## Approach 1: Sorting-Based Solution

### Steps:
1. Sort the array in ascending order to find the two heaviest stones at the end.
2. Smash the two heaviest stones:
   - If the stones are unequal (`x != y`), replace the second heaviest stone with the difference (`y - x`).
   - If the stones are equal (`x == y`), both are destroyed.
3. Reduce the size of the active array accordingly and repeat until one or zero stones remain.
4. Return the weight of the last stone or `0`.

### Code:
```java
import java.util.Arrays;

public class Solution {
    public int lastStoneWeight(int[] stones) {
        int n = stones.length;

        while (n > 1) {
            // Sort the array to find the two heaviest stones
            Arrays.sort(stones);

            int y = stones[n - 1]; // Largest stone
            int x = stones[n - 2]; // Second largest stone

            if (x != y) {
                stones[n - 2] = y - x; // Replace with the difference
                n--; // Reduce the active size
            } else {
                n -= 2; // Both stones are destroyed
            }
        }

        return n == 1 ? stones[0] : 0;
    }

    public static void main(String[] args) {
        Solution solution = new Solution();
        int[] stones = {2, 7, 4, 1, 8, 1};
        System.out.println("Last stone weight: " + solution.lastStoneWeight(stones)); // Output: 1
    }
}
```

### Time Complexity:
- Sorting each time takes \(O(n \log n)\), repeated for up to \(n\) iterations.
- **Overall:** \(O(n^2 \log n)\).

### Space Complexity:
- \(O(1)\), as the array is sorted in-place.

---

## Approach 2: Max-Heap (Priority Queue) Solution

### Steps:
1. Use a max-heap (priority queue) to efficiently fetch the two largest stones.
2. Insert all stones into the max-heap.
3. Repeatedly:
   - Remove the two largest stones (`y` and `x`) from the heap.
   - If \(x 
eq y\), insert the difference (`y - x`) back into the heap.
4. Stop when the heap has one or zero stones remaining.
5. Return the weight of the last stone, or `0` if the heap is empty.

### Code:
```java
import java.util.PriorityQueue;
import java.util.Collections;

public class Solution {
    public int lastStoneWeight(int[] stones) {
        // Max-heap to store stones
        PriorityQueue<Integer> maxHeap = new PriorityQueue<>(Collections.reverseOrder());

        // Add all stones to the heap
        for (int stone : stones) {
            maxHeap.offer(stone);
        }

        // Process the stones
        while (maxHeap.size() > 1) {
            int y = maxHeap.poll(); // Largest stone
            int x = maxHeap.poll(); // Second largest stone

            if (x != y) {
                maxHeap.offer(y - x); // Add the difference back
            }
        }

        // Return the last stone weight, or 0 if none are left
        return maxHeap.isEmpty() ? 0 : maxHeap.poll();
    }

    public static void main(String[] args) {
        Solution solution = new Solution();
        int[] stones = {2, 7, 4, 1, 8, 1};
        System.out.println("Last stone weight: " + solution.lastStoneWeight(stones)); // Output: 1
    }
}
```

### Time Complexity:
- Insertion and extraction in a max-heap take \(O(\log n)\).
- Repeated for up to \(n\) stones.
- **Overall:** \(O(n \log n)\).

### Space Complexity:
- \(O(n)\), for storing all elements in the heap.

---

## Comparison

| **Aspect**         | **Sorting Approach**         | **Max-Heap Approach**           |
|---------------------|------------------------------|----------------------------------|
| **Time Complexity** | \(O(n^2 \log n)\)            | \(O(n \log n)\)                 |
| **Space Complexity**| \(O(1)\)                     | \(O(n)\)                        |
| **Performance**     | Slower for large input sizes | Faster and scalable             |
| **Ease of Implementation** | Easier (uses sorting) | Requires understanding heaps    |

---

## Choosing the Right Approach
- **Sorting-Based Approach**: Use this if simplicity and small inputs are sufficient.
- **Max-Heap Approach**: Preferred for larger inputs due to better performance.
