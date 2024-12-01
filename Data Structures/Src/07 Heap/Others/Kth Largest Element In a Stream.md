
# Kth Largest Element in a Stream

This problem involves designing a data structure that efficiently supports adding integers to a stream and retrieving the **k-th largest element**.

## Problem Description

Given an integer `k` and a stream of integers, design a class to implement:

1. **`add(val)`**: Add an integer `val` to the stream.
2. **`getKthLargest()`**: Return the k-th largest element in the stream.

### Example
```plaintext
Input:
k = 3, stream = [4, 5, 8, 2]
add(3) → Returns 4
add(5) → Returns 5
add(10) → Returns 5
add(9) → Returns 8
add(4) → Returns 8
```

---

## Approaches

### **Approach 1: Naive Approach**

#### Explanation

1. Maintain a list to store all elements added to the stream.
2. After adding a new element:
   - Sort the list in descending order.
   - Retrieve the `k-th largest` element by indexing.
3. This approach works but is not efficient due to the repeated sorting of the list.

#### Complexity
- **Time Complexity**:
  - Adding an element: \(O(n \log n)\), where \(n\) is the size of the list (due to sorting).
  - Retrieving the k-th largest: \(O(1)\).
- **Space Complexity**: \(O(n)\), to store all elements in the list.

#### Implementation (Java)
```java
import java.util.ArrayList;
import java.util.Collections;

public class KthLargestNaive {
    private int k;
    private ArrayList<Integer> stream;

    public KthLargestNaive(int k, int[] nums) {
        this.k = k;
        this.stream = new ArrayList<>();
        for (int num : nums) {
            stream.add(num);
        }
    }

    public int add(int val) {
        stream.add(val);
        Collections.sort(stream, Collections.reverseOrder());
        return stream.get(k - 1);
    }
}
```

---

### **Approach 2: Optimized Approach Using Min-Heap**

#### Explanation

1. Use a **Min-Heap** (priority queue) to maintain the top `k` largest elements in the stream.
2. Process:
   - Add the new element to the heap.
   - If the heap size exceeds `k`, remove the smallest element (top of the heap).
   - The smallest element in the heap is the `k-th largest`.
3. The heap automatically ensures that insertion and deletion operations are efficient.

#### Complexity
- **Time Complexity**:
  - Adding an element: \(O(\log k)\), where \(k\) is the size of the heap.
  - Retrieving the k-th largest: \(O(1)\).
- **Space Complexity**: \(O(k)\), for the heap.

#### Implementation (Java)
```java
import java.util.PriorityQueue;

public class KthLargestOptimized {
    private int k;
    private PriorityQueue<Integer> minHeap;

    public KthLargestOptimized(int k, int[] nums) {
        this.k = k;
        this.minHeap = new PriorityQueue<>();
        for (int num : nums) {
            add(num);
        }
    }

    public int add(int val) {
        minHeap.offer(val);
        if (minHeap.size() > k) {
            minHeap.poll();
        }
        return minHeap.peek();
    }
}
```

---

## Comparison of Approaches

| **Metric**           | **Naive Approach**       | **Optimized Approach**       |
|-----------------------|--------------------------|------------------------------|
| **Time Complexity**   | \(O(n \log n)\) per add  | \(O(\log k)\) per add        |
| **Space Complexity**  | \(O(n)\)                | \(O(k)\)                    |
| **Ease of Implementation** | Simple but inefficient | Efficient but requires heap understanding |

---

## Usage Example

```java
public static void main(String[] args) {
    // Naive Approach
    KthLargestNaive naive = new KthLargestNaive(3, new int[]{4, 5, 8, 2});
    System.out.println(naive.add(3)); // Output: 4
    System.out.println(naive.add(5)); // Output: 5

    // Optimized Approach
    KthLargestOptimized optimized = new KthLargestOptimized(3, new int[]{4, 5, 8, 2});
    System.out.println(optimized.add(3)); // Output: 4
    System.out.println(optimized.add(5)); // Output: 5
}
```

---

## Key Takeaways

- The **naive approach** is simple but inefficient for large streams due to repeated sorting.
- The **optimized approach** is efficient and scales well with large input sizes, leveraging a min-heap to manage the top `k` largest elements.

Choose the approach based on your performance requirements and constraints.
