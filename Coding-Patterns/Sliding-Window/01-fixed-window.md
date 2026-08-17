# Sliding Window — Pattern 1: Fixed Window

## Pattern Overview

A **Fixed Window** is used when every candidate window has exactly `K` elements.

```text
Fixed Window
│
├── Sum
├── Average
├── Count
├── Frequency
└── Max / Min
```

The defining property is:

```text
window size = K
```

As the right pointer moves forward, the left pointer also moves forward so that the window always contains exactly `K` elements after it becomes full.

### Core Template

```java
int left = 0;

for (int right = 0; right < nums.length; right++) {

    // Add nums[right]

    if (right - left + 1 == k) {

        // Process current window

        // Remove nums[left]
        left++;
    }
}
```

### Master Invariant

When:

```java
right - left + 1 == k
```

the window `[left, right]` contains exactly `K` elements.

---

# 1. Fixed Window — Sum

## 1. Problem Statement

Given an integer array and an integer `K`, find the **maximum sum of any contiguous subarray of exactly `K` elements**.

### Example

```text
Input:
nums = [2, 1, 5, 1, 3, 2]
k = 3

Windows:

[2, 1, 5] = 8
[1, 5, 1] = 7
[5, 1, 3] = 9
[1, 3, 2] = 6

Output:
9
```

---

## 2. Brute-Force Solution

Generate every window of size `K` and calculate its sum from scratch.

```java
static int maxSumBruteForce(int[] nums, int k) {
    int maxSum = Integer.MIN_VALUE;

    for (int i = 0; i <= nums.length - k; i++) {
        int sum = 0;

        for (int j = i; j < i + k; j++) {
            sum += nums[j];
        }

        maxSum = Math.max(maxSum, sum);
    }

    return maxSum;
}
```

---

## 3. Why Brute Force Is Slow

Suppose:

```text
n = 1,000,000
k = 500,000
```

There can be roughly `n` candidate windows, and each window scans `K` elements.

Complexity:

```text
O(n * k)
```

The problem is that adjacent windows overlap heavily.

For example:

```text
[2, 1, 5]
   ↓
[1, 5, 1]
```

Only one element was removed and one element was added.

There is no reason to recalculate everything.

---

## 4. Pattern Identification

Look for phrases such as:

- maximum sum of `K` consecutive elements
- minimum sum of `K` consecutive elements
- sum of every window of size `K`
- exactly `K` consecutive elements
- fixed-size subarray

The critical clue is:

> **The window size is fixed.**

---

## 5. Sliding Window Intuition

Maintain the sum of the current window.

When moving:

```text
[2, 1, 5]
```

to:

```text
[1, 5, 1]
```

we do:

```text
old sum = 8

remove outgoing:
8 - 2 = 6

add incoming:
6 + 1 = 7
```

Therefore:

```text
newSum = oldSum - outgoing + incoming
```

---

## 6. Window Invariant

After the window becomes full:

```text
right - left + 1 == K
```

and:

```text
sum = sum of all elements in [left, right]
```

The invariant must remain true after every slide.

---

## 7. Pointer Movement

`right` always moves forward.

`left` moves forward only after a full window has been processed.

```text
right →
left  →
```

Example:

```text
[2, 1, 5]
 ↑     ↑
left  right

slide:

[1, 5, 1]
    ↑     ↑
   left  right
```

---

## 8. Data Structure Used

No extra data structure is required.

Maintain:

```java
long sum;
int left;
int right;
```

Use `long` when input values can produce a sum larger than `int`.

---

## 9. Java Implementation

```java
static long maxSum(int[] nums, int k) {
    if (nums == null || k <= 0 || k > nums.length) {
        throw new IllegalArgumentException("Invalid window size");
    }

    int left = 0;
    long sum = 0;
    long maxSum = Long.MIN_VALUE;

    for (int right = 0; right < nums.length; right++) {
        sum += nums[right];

        if (right - left + 1 == k) {
            maxSum = Math.max(maxSum, sum);

            sum -= nums[left];
            left++;
        }
    }

    return maxSum;
}
```

---

## 10. Dry Run

```text
nums = [2, 1, 5, 1, 3, 2]
k = 3
```

| right | incoming | window | sum | action |
|---:|---:|---|---:|---|
| 0 | 2 | `[2]` | 2 | expand |
| 1 | 1 | `[2,1]` | 3 | expand |
| 2 | 5 | `[2,1,5]` | 8 | answer=8 |
| 3 | 1 | `[1,5,1]` | 7 | answer=8 |
| 4 | 3 | `[5,1,3]` | 9 | answer=9 |
| 5 | 2 | `[1,3,2]` | 6 | answer=9 |

Final answer:

```text
9
```

---

## 11. Edge Cases

### `K = 1`

Every individual element is a window.

### `K = N`

Only one window exists.

### All negative numbers

Do not initialize the answer to `0`.

```java
long maxSum = Long.MIN_VALUE;
```

### Empty array

Handle explicitly.

### `K > N`

Invalid window.

### Integer overflow

Prefer `long` for sums when constraints require it.

---

## 12. Complexity Analysis

```text
Time:  O(N)
Space: O(1)
```

Each element is:

- added once;
- removed once.

---

## 13. Common Mistakes

### Mistake 1

Using:

```java
right - left == k
```

instead of:

```java
right - left + 1 == k
```

### Mistake 2

Forgetting to remove the outgoing element.

### Mistake 3

Using `0` as the initial maximum when values can be negative.

### Mistake 4

Using `int` when the sum can overflow.

---

## 14. Interview Follow-Up Questions

1. Find the minimum sum window of size `K`.
2. Return the starting index of the maximum-sum window.
3. Return the actual subarray.
4. What if `K = 1`?
5. What if all numbers are negative?
6. What if values can exceed `Integer.MAX_VALUE`?
7. Can you solve it in `O(N)` time and `O(1)` space?
8. What changes if the window size is variable?

---

## 15. Variations

- Maximum sum subarray of size K
- Minimum sum subarray of size K
- Maximum average subarray of size K
- Sum of every K-sized window
- Maximum product of K elements
- Maximum weighted window

---

# 2. Fixed Window — Average

## 1. Problem Statement

Given an array and window size `K`, find the average of every contiguous subarray of size `K`.

### Example

```text
nums = [1, 3, 2, 6, -1, 4, 1, 8, 2]
k = 5
```

For each window:

```text
[1,3,2,6,-1] = 11 / 5 = 2.2
[3,2,6,-1,4] = 14 / 5 = 2.8
...
```

---

## 2. Brute-Force Solution

For every window, calculate its sum and divide by `K`.

```java
static double[] averagesBruteForce(int[] nums, int k) {
    double[] result = new double[nums.length - k + 1];

    for (int i = 0; i <= nums.length - k; i++) {
        long sum = 0;

        for (int j = i; j < i + k; j++) {
            sum += nums[j];
        }

        result[i] = (double) sum / k;
    }

    return result;
}
```

---

## 3. Why Brute Force Is Slow

Every window recalculates the same overlapping elements.

Complexity:

```text
O(N * K)
```

---

## 4. Pattern Identification

Look for:

- average of every K consecutive elements;
- moving average;
- rolling average;
- average over fixed-size windows.

Average is simply:

```text
average = sum / K
```

Therefore, solve the sum problem efficiently.

---

## 5. Sliding Window Intuition

Maintain the rolling sum.

```text
newSum = oldSum - outgoing + incoming
```

Then:

```text
average = newSum / K
```

---

## 6. Window Invariant

For every processed full window:

```text
window size = K
sum = sum of exactly those K elements
```

---

## 7. Pointer Movement

Expand `right`.

When the window reaches `K`:

1. calculate average;
2. remove `nums[left]`;
3. increment `left`.

---

## 8. Data Structure Used

Only a running `long` sum.

Output requires:

```java
double[]
```

---

## 9. Java Implementation

```java
static double[] averages(int[] nums, int k) {
    if (nums == null || k <= 0 || k > nums.length) {
        throw new IllegalArgumentException("Invalid input");
    }

    double[] result = new double[nums.length - k + 1];

    int left = 0;
    long sum = 0;
    int index = 0;

    for (int right = 0; right < nums.length; right++) {
        sum += nums[right];

        if (right - left + 1 == k) {
            result[index++] = (double) sum / k;

            sum -= nums[left++];
        }
    }

    return result;
}
```

---

## 10. Dry Run

```text
nums = [1, 3, 2, 6]
k = 2
```

Windows:

```text
[1,3] -> 4 / 2 = 2.0
[3,2] -> 5 / 2 = 2.5
[2,6] -> 8 / 2 = 4.0
```

Output:

```text
[2.0, 2.5, 4.0]
```

---

## 11. Edge Cases

- `K = 1`
- `K = N`
- negative values
- fractional averages
- large sums
- invalid K

---

## 12. Complexity Analysis

```text
Time:  O(N)
Space: O(N)
```

`O(N)` output space is required for returning all averages.

The algorithm itself uses `O(1)` auxiliary space.

---

## 13. Common Mistakes

- Integer division:

```java
sum / k
```

instead of:

```java
(double) sum / k
```

- Recomputing every sum.
- Incorrect window length.
- Overflow before converting to double.

---

## 14. Interview Follow-Up Questions

1. Find the maximum average instead of all averages.
2. Return only the maximum average.
3. What if K is 1?
4. What if values are floating point?
5. How would you calculate a weighted moving average?
6. What is the difference between rolling average and cumulative average?

---

## 15. Variations

- Moving average
- Maximum average subarray of size K
- Minimum average window
- Rolling statistics
- Weighted moving average

---

# 3. Fixed Window — Count

## 1. Problem Statement

Count how many elements in every fixed-size window satisfy a condition.

Example:

> Given an array and `K`, count how many odd numbers are present in every window of size K.

```text
nums = [1, 2, 3, 4, 5]
k = 3

[1,2,3] -> 2 odd
[2,3,4] -> 1 odd
[3,4,5] -> 2 odd
```

---

## 2. Brute-Force Solution

For every window, scan all K elements and count matching elements.

```java
static int[] countOddsBruteForce(int[] nums, int k) {
    int[] result = new int[nums.length - k + 1];

    for (int i = 0; i <= nums.length - k; i++) {
        int count = 0;

        for (int j = i; j < i + k; j++) {
            if ((nums[j] & 1) == 1) {
                count++;
            }
        }

        result[i] = count;
    }

    return result;
}
```

---

## 3. Why Brute Force Is Slow

Each window scans K elements.

```text
O(N * K)
```

---

## 4. Pattern Identification

Use this pattern when the question asks for:

- count positives in every K-window;
- count negatives;
- count even/odd values;
- count values greater than X;
- count occurrences satisfying a predicate.

The important clue is:

> The condition can be represented by an increment/decrement when elements enter and leave.

---

## 5. Sliding Window Intuition

Suppose:

```text
[1,2,3]
```

contains two odd values.

Move to:

```text
[2,3,4]
```

The outgoing `1` was odd:

```text
count--
```

The incoming `4` is even:

```text
count unchanged
```

Therefore we update only two elements instead of scanning K elements.

---

## 6. Window Invariant

`count` always represents the number of qualifying elements inside `[left, right]`.

When the window is full:

```text
right - left + 1 == K
```

---

## 7. Pointer Movement

For every new `right`:

1. add the incoming element's contribution;
2. when size reaches K, record count;
3. remove the outgoing element's contribution;
4. move `left`.

---

## 8. Data Structure Used

Usually one primitive counter.

No HashMap or Set is required when the condition can be evaluated directly.

---

## 9. Java Implementation

```java
static int[] countOddsInWindows(int[] nums, int k) {
    if (nums == null || k <= 0 || k > nums.length) {
        throw new IllegalArgumentException("Invalid input");
    }

    int[] result = new int[nums.length - k + 1];

    int left = 0;
    int count = 0;
    int index = 0;

    for (int right = 0; right < nums.length; right++) {

        if ((nums[right] & 1) == 1) {
            count++;
        }

        if (right - left + 1 == k) {
            result[index++] = count;

            if ((nums[left] & 1) == 1) {
                count--;
            }

            left++;
        }
    }

    return result;
}
```

---

## 10. Dry Run

```text
nums = [1,2,3,4,5]
k = 3
```

| Window | Incoming | Outgoing | Count |
|---|---:|---:|---:|
| `[1,2,3]` | 3 odd | - | 2 |
| `[2,3,4]` | 4 even | 1 odd | 1 |
| `[3,4,5]` | 5 odd | 2 even | 2 |

Output:

```text
[2, 1, 2]
```

---

## 11. Edge Cases

- `K = 1`
- all elements satisfy condition
- no elements satisfy condition
- negative values
- empty input
- invalid K

---

## 12. Complexity Analysis

```text
Time:  O(N)
Space: O(N)
```

`O(N)` is for the output array.

Auxiliary algorithmic space:

```text
O(1)
```

---

## 13. Common Mistakes

- Forgetting to remove the outgoing element's contribution.
- Removing the wrong element.
- Updating count after moving `left`.
- Recalculating count for every window.

---

## 14. Interview Follow-Up Questions

1. Count even values instead.
2. Count values greater than X.
3. Count negative numbers.
4. Return the window having the maximum count.
5. Find the first window containing at least K qualifying values.
6. Count distinct values instead — what data structure changes?

---

## 15. Variations

- Count odd numbers in every K-window
- Count negative numbers
- Count values greater than X
- Count zeros
- Count elements satisfying a predicate
- Maximum count in a K-sized window

---

# 4. Fixed Window — Frequency

## 1. Problem Statement

Maintain the frequency of values or characters inside every fixed-size window.

Example:

> Find the frequency of each character in every substring of length K.

A common interview variant is:

> Determine whether a permutation/anagram of a pattern occurs in a string.

---

## 2. Brute-Force Solution

For each K-sized window, construct a new frequency map.

```java
static Map<Character, Integer> frequency(String window) {
    Map<Character, Integer> map = new HashMap<>();

    for (char c : window.toCharArray()) {
        map.merge(c, 1, Integer::sum);
    }

    return map;
}
```

Doing this independently for every window costs approximately:

```text
O(N * K)
```

---

## 3. Why Brute Force Is Slow

Adjacent windows share `K-1` elements.

Example:

```text
[ a b c d ]
  ↓
[ b c d e ]
```

Only:

```text
remove a
add e
```

The frequencies of `b`, `c`, and `d` do not need to be rebuilt.

---

## 4. Pattern Identification

Look for:

- frequency of every K-sized window;
- anagram;
- permutation;
- same character counts;
- fixed-size frequency matching;
- count occurrences within a moving window.

The critical clues are:

```text
contiguous
+
fixed K
+
frequency/count condition
```

---

## 5. Sliding Window Intuition

Maintain a frequency map for the current window.

When a character enters:

```java
freq.merge(c, 1, Integer::sum);
```

When it leaves:

```java
freq.merge(c, -1, Integer::sum);
```

If the count reaches zero, remove the key.

---

## 6. Window Invariant

The map must represent **exactly the frequencies of elements currently inside the K-sized window**.

For:

```text
s = "abca"
k = 3
```

windows are:

```text
"abc"
"bca"
```

Their maps are maintained incrementally.

---

## 7. Pointer Movement

1. Add `s[right]`.
2. If window becomes larger than K, remove `s[left]`.
3. Increment `left`.
4. When size equals K, inspect the frequency state.

---

## 8. Data Structure Used

Depending on the input:

### General characters

```java
HashMap<Character, Integer>
```

### Lowercase English letters

```java
int[26]
```

### ASCII

```java
int[128]
```

For interview performance and simplicity, use an array when the alphabet is known and bounded.

---

## 9. Java Implementation

### Example: Find all anagram starting indices

```java
static List<Integer> findAnagrams(String text, String pattern) {
    List<Integer> result = new ArrayList<>();

    if (text == null || pattern == null ||
        pattern.length() > text.length()) {
        return result;
    }

    int[] need = new int[26];
    int[] window = new int[26];

    for (char c : pattern.toCharArray()) {
        need[c - 'a']++;
    }

    int k = pattern.length();

    for (int right = 0; right < text.length(); right++) {
        window[text.charAt(right) - 'a']++;

        if (right >= k) {
            window[text.charAt(right - k) - 'a']--;
        }

        if (right >= k - 1 && Arrays.equals(need, window)) {
            result.add(right - k + 1);
        }
    }

    return result;
}
```

---

## 10. Dry Run

```text
text    = "cbaebabacd"
pattern = "abc"
k       = 3
```

Target:

```text
a=1
b=1
c=1
```

Windows:

```text
"cba" -> matches -> index 0
"bae" -> no
"aeb" -> no
"eba" -> no
"bab" -> no
"aba" -> no
"bac" -> no
"acd" -> no
```

For the actual sequence, the second matching window is:

```text
"bac" at index 6
```

Result:

```text
[0, 6]
```

---

## 11. Edge Cases

- pattern longer than text
- empty pattern
- repeated characters
- duplicate frequency requirements
- case sensitivity
- uppercase/lowercase
- Unicode
- large alphabet

---

## 12. Complexity Analysis

With a fixed alphabet of size `A`:

```text
Time:  O(N * A)
Space: O(A)
```

For English lowercase letters, `A = 26`, so this is effectively:

```text
O(N)
```

A more optimized implementation can maintain a `matched` count to avoid comparing all 26 positions for every window.

---

## 13. Common Mistakes

### Mistake 1

Comparing only the number of distinct characters.

These are different:

```text
a=2,b=1
```

and:

```text
a=1,b=2
```

They have the same number of distinct characters but different frequencies.

### Mistake 2

Forgetting to remove the outgoing character.

### Mistake 3

Removing the outgoing element before the window actually exceeds K.

### Mistake 4

Using a HashMap when a fixed alphabet array is simpler.

---

## 14. Interview Follow-Up Questions

1. Find all anagrams.
2. Determine whether a permutation exists.
3. Count anagram windows.
4. Optimize `Arrays.equals`.
5. How would you support Unicode?
6. How would you handle case-insensitive matching?
7. What changes if the window size is not fixed?
8. Can you solve using a `matched` counter?

---

## 15. Variations

- Find All Anagrams in a String
- Permutation in String
- Count anagram occurrences
- Fixed-window character frequency
- Fixed-window integer frequency
- Frequency comparison between two windows

---

# 5. Fixed Window — Max / Min

## 1. Problem Statement

Find the maximum or minimum value in every contiguous window of size `K`.

Example:

```text
nums = [1,3,-1,-3,5,3,6,7]
k = 3
```

Maximums:

```text
[3,3,5,5,6,7]
```

Minimums:

```text
[-1,-3,-3,-3,3,3]
```

---

## 2. Brute-Force Solution

For every window, scan all K elements.

```java
static int[] maxBruteForce(int[] nums, int k) {
    int[] result = new int[nums.length - k + 1];

    for (int i = 0; i <= nums.length - k; i++) {
        int max = Integer.MIN_VALUE;

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

There are `O(N)` windows and each scans `K` elements:

```text
O(N * K)
```

For large K, this becomes expensive.

---

## 4. Pattern Identification

Look for:

- maximum of every K elements;
- minimum of every K elements;
- largest value in every moving window;
- smallest value in every moving window.

When `K` is large, a simple running max is not enough because the maximum might leave the window.

Example:

```text
[9, 1, 2]
```

If `9` leaves:

```text
[1,2]
```

we need to know the next maximum.

This is where a **monotonic deque** becomes important.

---

## 5. Sliding Window Intuition

A deque stores only candidates that can still become the maximum.

For maximum:

```text
deque values are decreasing
```

Example:

```text
[1, 3, -1]
```

After adding `3`, `1` can be removed because:

- `3` is larger;
- `3` enters later;
- `3` will leave no earlier than `1`.

Therefore `1` can never become the maximum while `3` is still present.

---

## 6. Window Invariant

For maximum:

1. Deque contains only indices inside the current window.
2. Indices are ordered from front to back.
3. Their corresponding values are monotonically decreasing.
4. The front index represents the maximum.

For minimum:

```text
values are monotonically increasing
```

and the front represents the minimum.

---

## 7. Pointer Movement

For each `right`:

### Step 1 — Remove expired indices

An index is outside the window when:

```java
index <= right - k
```

### Step 2 — Remove dominated candidates

For maximum:

```java
while nums[last] <= nums[right]
    remove last
```

### Step 3 — Add right

```java
deque.offerLast(right);
```

### Step 4 — Record answer

Once:

```text
right >= k - 1
```

the front is the answer.

---

## 8. Data Structure Used

Use:

```java
ArrayDeque<Integer>
```

Store **indices**, not values.

Why indices?

Because we need to know when an element leaves the window.

---

## 9. Java Implementation

### Sliding Window Maximum

```java
static int[] maxSlidingWindow(int[] nums, int k) {
    if (nums == null || k <= 0 || k > nums.length) {
        throw new IllegalArgumentException("Invalid input");
    }

    int[] result = new int[nums.length - k + 1];

    Deque<Integer> deque = new ArrayDeque<>();
    int resultIndex = 0;

    for (int right = 0; right < nums.length; right++) {

        // Remove indices outside the current window.
        while (!deque.isEmpty()
                && deque.peekFirst() <= right - k) {
            deque.pollFirst();
        }

        // Remove values that can never become maximum.
        while (!deque.isEmpty()
                && nums[deque.peekLast()] <= nums[right]) {
            deque.pollLast();
        }

        deque.offerLast(right);

        // Window is full.
        if (right >= k - 1) {
            result[resultIndex++] = nums[deque.peekFirst()];
        }
    }

    return result;
}
```

### Sliding Window Minimum

Change the domination condition:

```java
while (!deque.isEmpty()
        && nums[deque.peekLast()] >= nums[right]) {
    deque.pollLast();
}
```

The deque becomes monotonically increasing.

---

## 10. Dry Run

```text
nums = [1,3,-1,-3,5]
k = 3
```

### right = 0

```text
value = 1

deque:
[1]
```

### right = 1

`3` is greater than `1`.

Remove `1`:

```text
deque:
[3]
```

### right = 2

Add `-1`:

```text
deque:
[3,-1]
```

Front:

```text
3
```

First answer = `3`.

### right = 3

Add `-3`:

```text
deque:
[3,-1,-3]
```

Index `0` is now outside the window.

Remove it:

```text
[-1,-3]
```

Answer:

```text
-1
```

### right = 4

Add `5`.

`5` dominates:

```text
[5]
```

Answer:

```text
5
```

---

## 11. Edge Cases

- `K = 1`
- `K = N`
- all increasing values
- all decreasing values
- duplicate values
- negative values
- invalid K
- empty array

---

## 12. Complexity Analysis

Each index is:

- inserted once;
- removed from the front at most once;
- removed from the back at most once.

Therefore:

```text
Time:  O(N) amortized
Space: O(K)
```

---

## 13. Common Mistakes

### Mistake 1 — Storing values instead of indices

You cannot efficiently determine whether a value has expired.

Store:

```java
Deque<Integer>
```

not:

```java
Deque<Integer>
```

with values.

The deque stores indices.

### Mistake 2 — Wrong monotonic direction

For maximum:

```text
decreasing
```

For minimum:

```text
increasing
```

### Mistake 3 — Forgetting expired indices

Always remove indices outside the current window.

### Mistake 4 — Removing from the wrong end

Dominated candidates are removed from the **back**.

Expired candidates are removed from the **front**.

---

## 14. Interview Follow-Up Questions

1. Find the minimum instead of maximum.
2. Find both maximum and minimum.
3. Find `max - min` for every window.
4. Find whether `max - min <= K`.
5. Why does the deque give `O(N)`?
6. Why store indices instead of values?
7. Explain amortized complexity.
8. Can a priority queue solve this?
9. Compare Heap vs Monotonic Deque.
10. What happens when duplicate values occur?

---

## 15. Variations

- Sliding Window Maximum
- Sliding Window Minimum
- Maximum-minimum range
- Maximum of every K-sized window
- Minimum of every K-sized window
- Longest window where `max - min <= K`

---

# Fixed Window — Master Recognition Guide

## How to Recognize This Pattern

When an interviewer says:

> "For every K consecutive elements..."

immediately think:

```text
FIXED SLIDING WINDOW
```

Then classify the operation.

| Requirement | Typical State |
|---|---|
| Sum | running sum |
| Average | running sum |
| Count condition | counter |
| Frequency | HashMap / array |
| Maximum | monotonic deque |
| Minimum | monotonic deque |
| Anagram | frequency array/map |
| Distinct count | HashMap / Set |

---

# Fixed Window Master Template

```java
static void fixedWindow(int[] nums, int k) {

    int left = 0;

    for (int right = 0; right < nums.length; right++) {

        // 1. Add incoming element.
        // update window state

        // 2. Check whether window reached K.
        if (right - left + 1 == k) {

            // 3. Process window.

            // 4. Remove outgoing element.
            // update window state

            // 5. Move left.
            left++;
        }
    }
}
```

---

# Fixed Window Decision Tree

```text
Is the window exactly K?
        |
       YES
        |
        v
What do I need?
        |
        +-------------------+
        |                   |
       SUM                COUNT
        |                   |
   running sum          counter
        |
        +-------------------+
        |
     AVERAGE
        |
   running sum / K


Need frequency?
        |
       YES
        |
        v
HashMap / frequency array


Need max/min?
        |
       YES
        |
        v
Monotonic Deque
```

---

# Critical Interview Insight

Not every fixed-window maximum/minimum problem needs a deque.

For example:

```text
maximum sum of K elements
```

uses:

```text
running sum
```

But:

```text
maximum element of every K-sized window
```

needs a data structure capable of handling an element leaving the window.

That is why:

```text
Fixed Window
    |
    +-- Aggregate that is reversible
    |       └── Sum / Count / Frequency
    |
    └-- Order statistic
            └── Max / Min
                    └── Monotonic Deque
```

This distinction is extremely important for interviews.

---

# Fixed Window Checklist

Before coding, ask:

```text
[ ] Is the input contiguous?
[ ] Is window size exactly K?
[ ] What state describes the window?
[ ] Can I update state when one element enters?
[ ] Can I update state when one element leaves?
[ ] When should I process the window?
[ ] When should I remove the outgoing element?
[ ] Do I need a counter?
[ ] Do I need a frequency map?
[ ] Do I need a monotonic deque?
[ ] Can integer overflow occur?
[ ] What happens when K = 1?
[ ] What happens when K = N?
[ ] What happens with empty input?
```

---

# Problems You Should Master

## Beginner

1. Maximum sum subarray of size K
2. Minimum sum subarray of size K
3. Average of every subarray of size K
4. Count negative numbers in every K-sized window
5. Count odd numbers in every K-sized window

## Intermediate

6. Find all anagrams in a string
7. Permutation in String
8. Count distinct elements in every K-sized window
9. Maximum of every K-sized window
10. Minimum of every K-sized window

## Advanced

11. Sliding Window Maximum using Monotonic Deque
12. Sliding Window Minimum using Monotonic Deque
13. Maximum minus minimum constraint
14. Frequency matching with optimized `matched` count
15. Fixed window + multiple simultaneous constraints
