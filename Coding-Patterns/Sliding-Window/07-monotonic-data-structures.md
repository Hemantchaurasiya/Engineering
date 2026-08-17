# Sliding Window — Pattern 7: Monotonic Data Structures

Monotonic Data Structures become necessary when a Sliding Window problem asks for information such as:

```text
maximum value inside current window
minimum value inside current window
maximum - minimum
```

A normal `HashMap` or running counter cannot efficiently tell us the maximum/minimum after the left side of the window moves.

The key tool is a:

```text
Deque
```

A carefully maintained deque can keep candidates in sorted order so that:

```text
maximum -> front of decreasing deque
minimum -> front of increasing deque
```

This allows us to maintain window extremes in:

```text
O(1) amortized time per element
```

and solve many problems in:

```text
O(N)
```

---

# 1. Deque

## 1. Problem Statement

Understand how a deque can maintain a monotonic sequence of candidates inside a Sliding Window.

A deque supports operations from both ends:

```text
addFirst()
addLast()
removeFirst()
removeLast()
peekFirst()
peekLast()
```

Java provides:

```java
Deque<Integer> deque = new ArrayDeque<>();
```

For Sliding Window problems, the deque usually stores:

```text
indices
```

rather than values.

---

## 2. Brute-Force Solution

Suppose we need the maximum of every window of size `K`.

For every window, scan all `K` elements.

```java
static int[] slidingMaximumBruteForce(
        int[] nums,
        int k) {

    int n = nums.length;

    if (n == 0 || k <= 0 || k > n) {
        return new int[0];
    }

    int[] result = new int[n - k + 1];

    for (int i = 0; i <= n - k; i++) {

        int max = nums[i];

        for (int j = i; j < i + k; j++) {
            max = Math.max(max, nums[j]);
        }

        result[i] = max;
    }

    return result;
}
```

---

## 3. Why Brute Force Is Slow

There are:

```text
N - K + 1
```

windows.

Each window takes:

```text
O(K)
```

to scan.

Therefore:

```text
O((N-K+1) * K)
```

which is:

```text
O(NK)
```

in the worst case.

If:

```text
N = 1,000,000
K = 500,000
```

this is far too expensive.

---

## 4. Pattern Identification

Look for:

```text
maximum in every window
minimum in every window
max - min <= K
max - min constraint
window extreme
```

The strongest signal is:

```text
Need MAX/MIN of a moving window
```

---

## 5. Sliding Window Intuition

Suppose:

```text
nums = [1,3,-1,-3,5,3,6,7]
K = 3
```

For:

```text
[1,3,-1]
```

maximum is:

```text
3
```

When the window moves:

```text
[3,-1,-3]
```

the maximum is still:

```text
3
```

When `3` eventually leaves the window, we need the next best candidate immediately.

The deque maintains exactly those candidates.

---

## 6. Window Invariant

For a maximum deque:

```text
values are decreasing from front to back
```

Example:

```text
Deque values:

8
5
3
1
```

The front is always the maximum.

For a minimum deque:

```text
values are increasing from front to back
```

Example:

```text
1
3
5
8
```

The front is always the minimum.

---

## 7. Pointer Movement

There are three important operations.

### Step 1 — Remove expired indexes

If:

```text
deque.front < left
```

remove it.

---

### Step 2 — Remove dominated candidates

For maximum:

```text
while nums[deque.back] <= nums[right]:
    remove back
```

The new value is better than those candidates.

For minimum:

```text
while nums[deque.back] >= nums[right]:
    remove back
```

---

### Step 3 — Add current index

```java
deque.addLast(right);
```

---

## 8. Data Structure Used

Java:

```java
Deque<Integer>
```

Implementation:

```java
ArrayDeque<Integer>
```

Important:

> Store indexes, not values.

Indexes allow us to determine whether an element has expired from the window.

---

## 9. Java Implementation

Maximum deque template:

```java
Deque<Integer> deque =
        new ArrayDeque<>();

for (int right = 0;
     right < nums.length;
     right++) {

    while (!deque.isEmpty()
            && nums[deque.peekLast()] <= nums[right]) {

        deque.pollLast();
    }

    deque.offerLast(right);
}
```

Minimum deque:

```java
Deque<Integer> deque =
        new ArrayDeque<>();

for (int right = 0;
     right < nums.length;
     right++) {

    while (!deque.isEmpty()
            && nums[deque.peekLast()] >= nums[right]) {

        deque.pollLast();
    }

    deque.offerLast(right);
}
```

---

## 10. Dry Run

For:

```text
nums = [1,3,-1]
```

Maximum deque:

### Add 1

```text
[1]
```

### Add 3

`3` is greater than `1`.

Remove `1`.

```text
[3]
```

### Add -1

```text
[3,-1]
```

Front:

```text
3
```

Therefore:

```text
maximum = 3
```

The deque stores only useful candidates.

---

## 11. Edge Cases

- empty array;
- `K = 1`;
- `K = N`;
- `K > N`;
- duplicate values;
- strictly increasing array;
- strictly decreasing array;
- all values identical;
- negative numbers.

---

## 12. Complexity Analysis

Each index:

- enters deque once;
- leaves deque at most once.

Therefore:

```text
Time:  O(N)
Space: O(K)
```

This is amortized `O(1)` per element.

---

## 13. Common Mistakes

### Mistake 1

Storing values instead of indexes.

Wrong:

```java
Deque<Integer> deque; // storing values
```

Better:

```java
Deque<Integer> deque; // storing indexes
```

---

### Mistake 2

Forgetting expired indexes.

---

### Mistake 3

Using the wrong monotonic direction.

Maximum:

```text
decreasing
```

Minimum:

```text
increasing
```

---

### Mistake 4

Using `<` instead of `<=` without understanding duplicate handling.

Both strategies can be valid depending on the invariant, but the chosen rule must be consistent.

---

## 14. Interview Follow-Up Questions

1. Why store indexes?
2. Why remove dominated elements?
3. Why is the deque monotonic?
4. Why is each index removed at most once?
5. Why is complexity O(N), not O(NK)?
6. What changes for minimum?
7. What happens with duplicate values?
8. Can you use `LinkedList`?
9. Why is `ArrayDeque` generally preferred?
10. Can this solve max-min constraints?

---

## 15. Variations

- Sliding maximum;
- Sliding minimum;
- maximum minus minimum;
- shortest window satisfying max-min constraint;
- constrained subarray problems;
- monotonic queue optimization in DP.

---

# 2. Sliding Maximum

## 1. Problem Statement

Given an integer array and window size `K`, return the maximum element in every contiguous window.

Example:

```text
nums = [1,3,-1,-3,5,3,6,7]
K = 3
```

Expected result:

```text
[3,3,5,5,6,7]
```

---

## 2. Brute-Force Solution

Scan every window.

```java
static int[] maxSlidingWindowBruteForce(
        int[] nums,
        int k) {

    if (nums == null
            || nums.length == 0
            || k <= 0
            || k > nums.length) {
        return new int[0];
    }

    int[] result =
            new int[nums.length - k + 1];

    for (int left = 0;
         left <= nums.length - k;
         left++) {

        int max = nums[left];

        for (int i = left;
             i < left + k;
             i++) {

            max = Math.max(
                    max,
                    nums[i]
            );
        }

        result[left] = max;
    }

    return result;
}
```

---

## 3. Why Brute Force Is Slow

Complexity:

```text
O(NK)
```

because every window scans `K` elements.

---

## 4. Pattern Identification

Classic signal:

```text
maximum of every window of size K
```

This is one of the canonical monotonic deque problems.

---

## 5. Sliding Window Intuition

Maintain a deque of indexes such that:

```text
values decrease from front to back
```

Therefore:

```text
deque.front
```

is always the maximum.

Before adding `nums[right]`, remove all smaller/equal values from the back.

They can never become the maximum while the new value remains in the window.

---

## 6. Window Invariant

For every index in deque:

```text
indices are increasing
```

and:

```text
nums[deque[0]]
>=
nums[deque[1]]
>=
nums[deque[2]]
...
```

Also:

```text
all deque indexes belong to current window
```

---

## 7. Pointer Movement

For each `right`:

### Remove expired

```java
while (!deque.isEmpty()
        && deque.peekFirst() < right - k + 1) {

    deque.pollFirst();
}
```

### Remove dominated candidates

```java
while (!deque.isEmpty()
        && nums[deque.peekLast()] <= nums[right]) {

    deque.pollLast();
}
```

### Add current

```java
deque.offerLast(right);
```

### Produce result

Once:

```text
right >= k - 1
```

the window is complete.

Maximum:

```java
nums[deque.peekFirst()]
```

---

## 8. Data Structure Used

```java
Deque<Integer>
```

Use:

```java
ArrayDeque<Integer>
```

---

## 9. Java Implementation

```java
static int[] maxSlidingWindow(
        int[] nums,
        int k) {

    if (nums == null
            || nums.length == 0
            || k <= 0
            || k > nums.length) {
        return new int[0];
    }

    int[] result =
            new int[nums.length - k + 1];

    Deque<Integer> deque =
            new ArrayDeque<>();

    for (int right = 0;
         right < nums.length;
         right++) {

        // Remove indexes outside the window.
        int windowLeft = right - k + 1;

        while (!deque.isEmpty()
                && deque.peekFirst() < windowLeft) {

            deque.pollFirst();
        }

        // Remove values that can never be maximum.
        while (!deque.isEmpty()
                && nums[deque.peekLast()]
                   <= nums[right]) {

            deque.pollLast();
        }

        deque.offerLast(right);

        // Window has reached size K.
        if (right >= k - 1) {

            result[right - k + 1] =
                    nums[deque.peekFirst()];
        }
    }

    return result;
}
```

---

## 10. Dry Run

```text
nums = [1,3,-1,-3,5]
K = 3
```

### right = 0

Deque:

```text
[1]
```

---

### right = 1

`3 > 1`.

Remove index of `1`.

Deque:

```text
[3]
```

---

### right = 2

Add `-1`.

Deque:

```text
[3,-1]
```

Window:

```text
[1,3,-1]
```

Maximum:

```text
3
```

---

### right = 3

Add `-3`.

Deque:

```text
[3,-1,-3]
```

Window:

```text
[3,-1,-3]
```

Maximum:

```text
3
```

---

### right = 4

Add `5`.

`5` dominates:

```text
-3
-1
3
```

All are removed.

Deque:

```text
[5]
```

Maximum:

```text
5
```

---

## 11. Edge Cases

- `K = 1`;
- `K = N`;
- `K > N`;
- duplicate values;
- all increasing;
- all decreasing;
- negative values;
- integer extremes.

---

## 12. Complexity Analysis

```text
Time:  O(N)
Space: O(K)
```

---

## 13. Common Mistakes

- forgetting expired indexes;
- storing values rather than indexes;
- removing from the wrong end;
- using increasing order instead of decreasing order;
- producing output before the window reaches size `K`.

---

## 14. Interview Follow-Up Questions

1. Why does the deque front contain the maximum?
2. Why can smaller elements be removed?
3. Why does the new larger element dominate them?
4. Why is each index processed O(1) amortized?
5. How would you find minimum instead?
6. Can a heap solve it?
7. Compare heap vs deque.
8. What is the space complexity?
9. How do duplicates affect the deque?
10. Can you solve it in a streaming system?

---

## 15. Variations

- Sliding minimum;
- top-K per window;
- maximum of each window;
- maximum minus minimum;
- constrained windows.

---

# 3. Sliding Minimum

## 1. Problem Statement

Given an integer array and window size `K`, return the minimum element from every window.

Example:

```text
nums = [1,3,-1,-3,5,3,6,7]
K = 3
```

Expected:

```text
[-1,-3,-3,3,3,3]
```

---

## 2. Brute-Force Solution

```java
static int[] minSlidingWindowBruteForce(
        int[] nums,
        int k) {

    if (nums == null
            || nums.length == 0
            || k <= 0
            || k > nums.length) {
        return new int[0];
    }

    int[] result =
            new int[nums.length - k + 1];

    for (int left = 0;
         left <= nums.length - k;
         left++) {

        int min = nums[left];

        for (int i = left;
             i < left + k;
             i++) {

            min = Math.min(
                    min,
                    nums[i]
            );
        }

        result[left] = min;
    }

    return result;
}
```

---

## 3. Why Brute Force Is Slow

```text
O(NK)
```

in the worst case.

---

## 4. Pattern Identification

Look for:

```text
minimum of every window
minimum in sliding window
smallest value in each K-sized range
```

---

## 5. Sliding Window Intuition

For minimum, reverse the monotonic direction.

Maintain:

```text
increasing deque
```

Example:

```text
1
3
5
8
```

The smallest value is always at the front.

When a new smaller value arrives, larger values at the back can never become minimum before the new value leaves.

---

## 6. Window Invariant

```text
nums[deque[0]]
<= nums[deque[1]]
<= nums[deque[2]]
...
```

and all indexes belong to the current window.

---

## 7. Pointer Movement

For each `right`:

1. Remove expired indexes from front.
2. Remove larger/equal values from back.
3. Add current index.
4. Read minimum from front.

---

## 8. Data Structure Used

```java
Deque<Integer>
```

---

## 9. Java Implementation

```java
static int[] minSlidingWindow(
        int[] nums,
        int k) {

    if (nums == null
            || nums.length == 0
            || k <= 0
            || k > nums.length) {
        return new int[0];
    }

    int[] result =
            new int[nums.length - k + 1];

    Deque<Integer> deque =
            new ArrayDeque<>();

    for (int right = 0;
         right < nums.length;
         right++) {

        int windowLeft = right - k + 1;

        while (!deque.isEmpty()
                && deque.peekFirst() < windowLeft) {

            deque.pollFirst();
        }

        while (!deque.isEmpty()
                && nums[deque.peekLast()]
                   >= nums[right]) {

            deque.pollLast();
        }

        deque.offerLast(right);

        if (right >= k - 1) {

            result[right - k + 1] =
                    nums[deque.peekFirst()];
        }
    }

    return result;
}
```

---

## 10. Dry Run

```text
nums = [3,1,2]
K = 2
```

### right = 0

Deque:

```text
[3]
```

### right = 1

`1 < 3`.

Remove `3`.

Deque:

```text
[1]
```

Window:

```text
[3,1]
```

Minimum:

```text
1
```

### right = 2

Add `2`.

Deque:

```text
[1,2]
```

Window:

```text
[1,2]
```

Minimum:

```text
1
```

---

## 11. Edge Cases

- `K = 1`;
- `K = N`;
- all equal values;
- increasing values;
- decreasing values;
- negative values;
- empty array.

---

## 12. Complexity Analysis

```text
Time:  O(N)
Space: O(K)
```

---

## 13. Common Mistakes

- using maximum-deque logic;
- removing smaller values instead of larger values;
- forgetting expired indexes;
- storing values rather than indexes;
- reading the result before the window is complete.

---

## 14. Interview Follow-Up Questions

1. Why is the deque increasing?
2. Why can larger elements be removed?
3. How would you modify maximum code?
4. Why is complexity O(N)?
5. Can you use a heap?
6. Which solution is easier to implement?
7. What happens with duplicates?

---

## 15. Variations

- sliding minimum;
- maximum-minimum range;
- shortest valid window;
- minimum constrained range;
- monotonic queue optimization.

---

# 4. Max-Min Constraint

## 1. Problem Statement

Find the length of the longest contiguous subarray such that:

```text
max(window) - min(window) <= limit
```

Example:

```text
nums = [10,1,2,4,7,2]
limit = 5
```

Answer:

```text
4
```

One valid longest window is:

```text
[2,4,7,2]
```

because:

```text
max = 7
min = 2

7 - 2 = 5
```

---

## 2. Brute-Force Solution

Generate all subarrays and maintain max/min.

```java
static int longestSubarrayBruteForce(
        int[] nums,
        int limit) {

    int answer = 0;

    for (int i = 0;
         i < nums.length;
         i++) {

        int min = nums[i];
        int max = nums[i];

        for (int j = i;
             j < nums.length;
             j++) {

            min = Math.min(
                    min,
                    nums[j]
            );

            max = Math.max(
                    max,
                    nums[j]
            );

            if ((long) max - min > limit) {
                break;
            }

            answer = Math.max(
                    answer,
                    j - i + 1
            );
        }
    }

    return answer;
}
```

---

## 3. Why Brute Force Is Slow

Although max and min are maintained incrementally, there can still be:

```text
O(N²)
```

candidate windows.

Sliding Window with two monotonic deques solves it in:

```text
O(N)
```

---

## 4. Pattern Identification

Look for:

```text
longest subarray
max - min <= limit
range <= K
difference between maximum and minimum
bounded range
```

This is a strong signal for:

```text
Sliding Window + two monotonic deques
```

---

## 5. Sliding Window Intuition

Maintain two structures:

```text
maxDeque
minDeque
```

The front of:

```text
maxDeque
```

is the maximum.

The front of:

```text
minDeque
```

is the minimum.

Therefore:

```text
currentMax = nums[maxDeque.peekFirst()]
currentMin = nums[minDeque.peekFirst()]
```

The window is valid when:

```text
currentMax - currentMin <= limit
```

If it becomes invalid:

```text
currentMax - currentMin > limit
```

move `left`.

---

## 6. Window Invariant

The current window satisfies:

```text
max(window) - min(window) <= limit
```

Additionally:

```text
maxDeque = decreasing
minDeque = increasing
```

and all indexes are inside the current window.

---

## 7. Pointer Movement

For each `right`:

### Add to max deque

Remove smaller/equal values:

```java
while (!maxDeque.isEmpty()
        && nums[maxDeque.peekLast()]
           <= nums[right]) {

    maxDeque.pollLast();
}
```

### Add to min deque

Remove larger/equal values:

```java
while (!minDeque.isEmpty()
        && nums[minDeque.peekLast()]
           >= nums[right]) {

    minDeque.pollLast();
}
```

### Restore validity

```java
while ((long) nums[maxDeque.peekFirst()]
       - nums[minDeque.peekFirst()]
       > limit) {

    if (maxDeque.peekFirst() == left) {
        maxDeque.pollFirst();
    }

    if (minDeque.peekFirst() == left) {
        minDeque.pollFirst();
    }

    left++;
}
```

### Update answer

```java
answer = Math.max(
        answer,
        right - left + 1
);
```

---

## 8. Data Structure Used

Two deques:

```java
Deque<Integer> maxDeque =
        new ArrayDeque<>();

Deque<Integer> minDeque =
        new ArrayDeque<>();
```

Why two?

Because one tracks:

```text
maximum
```

and the other:

```text
minimum
```

---

## 9. Java Implementation

```java
static int longestSubarray(
        int[] nums,
        int limit) {

    if (nums == null || nums.length == 0) {
        return 0;
    }

    Deque<Integer> maxDeque =
            new ArrayDeque<>();

    Deque<Integer> minDeque =
            new ArrayDeque<>();

    int left = 0;
    int answer = 0;

    for (int right = 0;
         right < nums.length;
         right++) {

        // Maintain decreasing deque for maximum.
        while (!maxDeque.isEmpty()
                && nums[maxDeque.peekLast()]
                   <= nums[right]) {

            maxDeque.pollLast();
        }

        maxDeque.offerLast(right);

        // Maintain increasing deque for minimum.
        while (!minDeque.isEmpty()
                && nums[minDeque.peekLast()]
                   >= nums[right]) {

            minDeque.pollLast();
        }

        minDeque.offerLast(right);

        // Restore max-min constraint.
        while ((long) nums[maxDeque.peekFirst()]
                - nums[minDeque.peekFirst()]
                > limit) {

            if (maxDeque.peekFirst() == left) {
                maxDeque.pollFirst();
            }

            if (minDeque.peekFirst() == left) {
                minDeque.pollFirst();
            }

            left++;
        }

        answer = Math.max(
                answer,
                right - left + 1
        );
    }

    return answer;
}
```

---

## 10. Dry Run

```text
nums = [10,1,2,4,7,2]
limit = 5
```

### right = 0

Window:

```text
[10]
```

```text
max = 10
min = 10
range = 0
```

Valid.

---

### right = 1

Window:

```text
[10,1]
```

```text
max = 10
min = 1
range = 9
```

Invalid.

Move `left`.

Remove `10`.

Now:

```text
[1]
```

Valid.

---

### right = 2

Window:

```text
[1,2]
```

```text
max = 2
min = 1
range = 1
```

Valid.

Length:

```text
2
```

---

### right = 3

Window:

```text
[1,2,4]
```

```text
max = 4
min = 1
range = 3
```

Length:

```text
3
```

---

### right = 4

Window:

```text
[1,2,4,7]
```

```text
max = 7
min = 1
range = 6
```

Invalid.

Move `left` from `1`.

Window:

```text
[2,4,7]
```

Now:

```text
max = 7
min = 2
range = 5
```

Valid.

Length:

```text
3
```

---

### right = 5

Window:

```text
[2,4,7,2]
```

```text
max = 7
min = 2
range = 5
```

Valid.

Length:

```text
4
```

Final answer:

```text
4
```

---

## 11. Edge Cases

- empty array;
- one element;
- `limit = 0`;
- all elements equal;
- strictly increasing array;
- strictly decreasing array;
- negative numbers;
- very large positive/negative values;
- `limit` larger than the entire value range.

Important:

Use `long` when calculating:

```java
max - min
```

if integer extremes are possible.

---

## 12. Complexity Analysis

Each index enters and leaves each deque at most once.

Therefore:

```text
Time:  O(N)
Space: O(N)
```

More tightly, each deque contains at most the current window's useful candidates, so:

```text
Space: O(K)
```

where `K` is the maximum window size.

Worst case:

```text
O(N)
```

---

## 13. Common Mistakes

### Mistake 1

Using only one deque.

You need:

```text
maxDeque
minDeque
```

---

### Mistake 2

Maintaining the wrong monotonic direction.

Maximum:

```text
decreasing
```

Minimum:

```text
increasing
```

---

### Mistake 3

Forgetting to remove expired indexes from both deques.

---

### Mistake 4

Computing:

```java
max - min
```

with `int` when overflow is possible.

Safer:

```java
(long) max - min
```

---

### Mistake 5

Shrinking only once.

Use:

```java
while (max - min > limit)
```

---

### Mistake 6

Removing the left index from a deque only by value.

Use its index:

```java
if (deque.peekFirst() == left)
```

---

## 14. Interview Follow-Up Questions

1. Why do we need two deques?
2. Why does the max deque decrease?
3. Why does the min deque increase?
4. Why can dominated values be removed?
5. Why is the algorithm O(N)?
6. Why do we store indexes?
7. Could a TreeMap solve it?
8. Could a PriorityQueue solve it?
9. Compare Deque vs TreeMap vs Heap.
10. Why is `long` safer for max-min?
11. Can you find the actual longest subarray?
12. Can you find the shortest valid subarray?
13. What if the constraint is `max-min < limit`?
14. What if there are duplicate values?
15. Can this work in a streaming system?

---

## 15. Variations

- Longest subarray where max-min <= K;
- Longest substring where max character - min character <= K;
- Shortest subarray satisfying a range constraint;
- Count subarrays where max-min <= K;
- Maximum length with bounded range;
- Minimum length with range >= K.

---

# Why Monotonic Deques Work

The key concept is **domination**.

Suppose we are maintaining maximum.

Current deque:

```text
10
7
5
```

New value:

```text
12
```

For maximum queries:

```text
12 > 10
12 > 7
12 > 5
```

Therefore those values are dominated.

As long as `12` remains inside the window, none of them can become the maximum.

So we safely remove them.

The same reasoning applies in reverse for minimum.

---

# Maximum vs Minimum Deque

| Query | Deque Order | Remove From Back When |
|---|---|---|
| Maximum | Decreasing | `old <= current` |
| Minimum | Increasing | `old >= current` |

Memorize this table.

---

# Deque Invariant

## Maximum

```text
front -> largest
         ↓
         smaller
         ↓
         smaller
         ↓
back -> smallest
```

Therefore:

```java
nums[maxDeque.peekFirst()]
```

is maximum.

---

## Minimum

```text
front -> smallest
         ↓
         larger
         ↓
         larger
         ↓
back -> largest
```

Therefore:

```java
nums[minDeque.peekFirst()]
```

is minimum.

---

# Why Store Indexes?

Suppose:

```text
nums = [4, 2, 7]
```

and current window starts at index:

```text
left = 1
```

The value:

```text
4
```

must expire.

If the deque only stores values, knowing whether a value belongs to the current window becomes difficult, especially with duplicates.

Indexes solve this:

```text
index < left
```

means:

```text
expired
```

Therefore:

```java
while (!deque.isEmpty()
        && deque.peekFirst() < left) {

    deque.pollFirst();
}
```

---

# Why O(N) Instead of O(NK)?

At first glance, the algorithm contains:

```java
while (...)
```

inside:

```java
for (...)
```

which may look like:

```text
O(N²)
```

But this is an **amortized analysis** problem.

Every index:

```text
enters deque once
```

and:

```text
leaves deque once
```

Therefore the total number of deque operations is bounded by:

```text
O(N)
```

So:

```text
Time = O(N)
```

---

# Deque vs PriorityQueue vs TreeMap

There are multiple ways to maintain window maximum/minimum.

| Data Structure | Typical Time | Supports Expiration | Main Advantage |
|---|---:|---|---|
| Monotonic Deque | O(N) total | Yes | Optimal for fixed sliding order |
| PriorityQueue | O(N log K) | Lazy removal | Easy to understand |
| TreeMap | O(N log K) | Yes | Maintains all frequencies |
| Brute Force | O(NK) | N/A | Simplest |

For a pure Sliding Window maximum/minimum problem:

```text
Monotonic Deque
```

is usually the optimal solution.

---

# PriorityQueue Alternative

A max heap can solve Sliding Maximum:

```java
PriorityQueue<int[]> pq =
        new PriorityQueue<>(
                (a, b) -> Integer.compare(
                        b[0],
                        a[0]
                )
        );
```

Store:

```text
[value, index]
```

But complexity becomes:

```text
O(N log K)
```

The deque improves it to:

```text
O(N)
```

---

# Max-Min Constraint Decision Tree

```text
        LONGEST SUBARRAY
               |
               v
      max(window)-min(window)
               |
               v
        <= LIMIT ?
          /       \
        YES        NO
         |          |
         v          v
   valid window   shrink left
         |
         v
   maxDeque + minDeque
         |
         v
   O(N) Sliding Window
```

---

# Counting Max-Min Windows

The same max/min deques can be used for counting.

If:

```text
max(window) - min(window) <= limit
```

then after shrinking to the smallest valid `left`:

```java
answer += right - left + 1L;
```

This connects:

```text
Monotonic Deque
+
Counting Sliding Window
```

and is an important advanced interview pattern.

---

# Advanced Hybrid Pattern

A particularly powerful combination is:

```text
Sliding Window
       +
Monotonic Deque
       +
Counting
```

Example:

> Count subarrays where max-min <= K.

Algorithm:

```text
1. Expand right.
2. Update max deque.
3. Update min deque.
4. While max-min > K:
       move left.
5. answer += right-left+1.
```

Complexity:

```text
Time: O(N)
Space: O(N)
```

---

# Common Recognition Signals

When you see:

```text
window maximum
window minimum
range
max - min
bounded difference
```

think:

```text
MONOTONIC DEQUE
```

When you see:

```text
count windows
```

combine it with:

```text
right - left + 1
```

When you see:

```text
exactly K
```

consider:

```text
AtMost(K) - AtMost(K-1)
```

---

# Master Templates

## Template A — Sliding Maximum

```java
Deque<Integer> maxDeque =
        new ArrayDeque<>();

for (int right = 0;
     right < nums.length;
     right++) {

    while (!maxDeque.isEmpty()
            && maxDeque.peekFirst() < left) {

        maxDeque.pollFirst();
    }

    while (!maxDeque.isEmpty()
            && nums[maxDeque.peekLast()]
               <= nums[right]) {

        maxDeque.pollLast();
    }

    maxDeque.offerLast(right);

    // nums[maxDeque.peekFirst()] = max
}
```

---

## Template B — Sliding Minimum

```java
Deque<Integer> minDeque =
        new ArrayDeque<>();

for (int right = 0;
     right < nums.length;
     right++) {

    while (!minDeque.isEmpty()
            && minDeque.peekFirst() < left) {

        minDeque.pollFirst();
    }

    while (!minDeque.isEmpty()
            && nums[minDeque.peekLast()]
               >= nums[right]) {

        minDeque.pollLast();
    }

    minDeque.offerLast(right);

    // nums[minDeque.peekFirst()] = min
}
```

---

## Template C — Max-Min Constraint

```java
Deque<Integer> maxDeque =
        new ArrayDeque<>();

Deque<Integer> minDeque =
        new ArrayDeque<>();

int left = 0;

for (int right = 0;
     right < nums.length;
     right++) {

    while (!maxDeque.isEmpty()
            && nums[maxDeque.peekLast()]
               <= nums[right]) {

        maxDeque.pollLast();
    }

    maxDeque.offerLast(right);

    while (!minDeque.isEmpty()
            && nums[minDeque.peekLast()]
               >= nums[right]) {

        minDeque.pollLast();
    }

    minDeque.offerLast(right);

    while ((long) nums[maxDeque.peekFirst()]
            - nums[minDeque.peekFirst()]
            > limit) {

        if (maxDeque.peekFirst() == left) {
            maxDeque.pollFirst();
        }

        if (minDeque.peekFirst() == left) {
            minDeque.pollFirst();
        }

        left++;
    }

    // Window is valid.
}
```

---

# Interview Checklist

Before coding:

```text
[ ] Does the problem need window maximum?
[ ] Does it need window minimum?
[ ] Does it ask for max-min?
[ ] Is the window moving?
[ ] Can dominated values be discarded?
[ ] For maximum, is the deque decreasing?
[ ] For minimum, is the deque increasing?
[ ] Am I storing indexes?
[ ] Am I removing expired indexes?
[ ] Am I removing dominated candidates?
[ ] Is the answer longest, shortest, or count?
[ ] If count, can I use right-left+1?
[ ] Could TreeMap/Heap work?
[ ] Is the deque O(N) solution expected?
```

---

# Final Mental Model

```text
                MOVING WINDOW
                      |
              Need MAX / MIN?
                      |
                     YES
                      |
                      v
              MONOTONIC DEQUE
                      |
          +-----------+-----------+
          |                       |
       MAXIMUM                  MINIMUM
          |                       |
          v                       v
      DECREASING               INCREASING
       DEQUE                    DEQUE
          |                       |
          v                       v
      front=max                front=min
```

For a range constraint:

```text
max - min <= K
       |
       v
two monotonic deques
       |
       v
restore validity
       |
       v
longest -> max(length)
count   -> sum(length)
```

The core insight is:

> A monotonic deque does not store every element in the window. It stores only the elements that can still become the future maximum/minimum.

That is why it achieves the optimal `O(N)` behavior for classic Sliding Window extreme-value problems.
