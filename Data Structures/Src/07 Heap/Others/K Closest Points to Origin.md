
# K Closest Points to Origin

## Problem Statement
Given an array of points in a 2D plane, find the **K closest points** to the origin `(0, 0)` based on Euclidean distance. 

The Euclidean distance of a point `(x, y)` from the origin `(0, 0)` is calculated as:
\[
\text{distance} = \sqrt{x^2 + y^2}
\]

For simplicity, the distance comparison can ignore the square root since \(\sqrt{a}\) and \(a\) have the same relative order.

---

## Examples

### Input
```plaintext
points = [[1, 3], [-2, 2]], K = 1
```

### Output
```plaintext
[[-2, 2]]
```

### Explanation
The distance of `[1, 3]` is \(\sqrt{1^2 + 3^2} = \sqrt{10}\), and the distance of `[-2, 2]` is \(\sqrt{(-2)^2 + 2^2} = \sqrt{8}\). Since `[-2, 2]` is closer to the origin, it is the answer when \(K = 1\).

---

## Approaches

### 1. Sorting-Based Approach

#### Steps:
1. Calculate the Euclidean distance for all points (without square root for efficiency).
2. Sort the points by their distance.
3. Return the first \(K\) points from the sorted list.

#### Code:
```java
import java.util.Arrays;

public class KClosestPoints {
    public int[][] kClosest(int[][] points, int K) {
        Arrays.sort(points, (a, b) -> {
            int distA = a[0] * a[0] + a[1] * a[1];
            int distB = b[0] * b[0] + b[1] * b[1];
            return Integer.compare(distA, distB);
        });
        return Arrays.copyOfRange(points, 0, K);
    }
}
```

#### Complexity:
- **Time Complexity**: \(O(n \log n)\), where \(n\) is the number of points (due to sorting).
- **Space Complexity**: \(O(1)\), since the sorting happens in place.

#### Pros and Cons:
- **Pros**: Simple to implement.
- **Cons**: Inefficient for large inputs since it processes all \(n\) points.

---

### 2. Heap-Based Approach (Optimal for Large Inputs)

#### Steps:
1. Use a **max-heap** (priority queue) of size \(K\) to store the closest points.
2. Iterate over each point:
   - Compute its distance.
   - If the heap size is less than \(K\), add the point.
   - Otherwise, if the current point is closer than the farthest point in the heap, replace the farthest point.
3. Extract all points from the heap as the result.

#### Code:
```java
import java.util.PriorityQueue;

public class KClosestPoints {
    public int[][] kClosest(int[][] points, int K) {
        PriorityQueue<int[]> maxHeap = new PriorityQueue<>(
            (a, b) -> Integer.compare(b[0] * b[0] + b[1] * b[1], a[0] * a[0] + a[1] * a[1])
        );

        for (int[] point : points) {
            int dist = point[0] * point[0] + point[1] * point[1];
            if (maxHeap.size() < K) {
                maxHeap.add(point);
            } else if (dist < maxHeap.peek()[0] * maxHeap.peek()[0] + maxHeap.peek()[1] * maxHeap.peek()[1]) {
                maxHeap.poll();
                maxHeap.add(point);
            }
        }

        int[][] result = new int[K][2];
        for (int i = 0; i < K; i++) {
            result[i] = maxHeap.poll();
        }
        return result;
    }
}
```

#### Complexity:
- **Time Complexity**: \(O(n \log K)\), where \(n\) is the number of points (heap operations for \(K\) points).
- **Space Complexity**: \(O(K)\), for the heap.

#### Pros and Cons:
- **Pros**: Efficient for large inputs where \(K \ll n\).
- **Cons**: Slightly more complex implementation.

---

## Choosing the Right Approach

| Criteria                  | Sorting-Based             | Heap-Based                  |
|---------------------------|---------------------------|-----------------------------|
| **Small Inputs**          | Preferred for simplicity  | Overkill                    |
| **Large Inputs (\(n \gg K\))** | Inefficient due to sorting | Optimal for performance      |

---

## Key Insights
- Use the **sorting-based approach** for small datasets.
- Use the **heap-based approach** when \(n\) is large and \(K\) is small.

---
