# Sliding Window — Pattern 6: Binary Array

Binary-array Sliding Window problems are an excellent bridge from basic two-pointer problems to more advanced counting and constraint-based problems.

A binary array contains only:

```text
0
1
```

This makes many constraints easy to maintain with a simple counter.

```text
Binary Array
│
├── At Most K Zeros
├── K Flips
└── Consecutive Ones
```

The most important recognition rule is:

```text
Binary array
+
at most K zeros
        ↓
Sliding Window
```

---

# 1. At Most K Zeros

## 1. Problem Statement

Given a binary array, find the length of the longest subarray containing **at most K zeros**.

Example:

```text
nums = [1,1,1,0,0,0,1,1,1,1,0]
K = 2
```

We want the longest contiguous subarray containing no more than two zeros.

---

## 2. Brute-Force Solution

Generate every subarray and count zeros.

```java
static int longestAtMostKZerosBruteForce(
        int[] nums,
        int k) {

    int maxLength = 0;

    for (int i = 0; i < nums.length; i++) {

        int zeroCount = 0;

        for (int j = i; j < nums.length; j++) {

            if (nums[j] == 0) {
                zeroCount++;
            }

            if (zeroCount > k) {
                break;
            }

            maxLength = Math.max(
                    maxLength,
                    j - i + 1
            );
        }
    }

    return maxLength;
}
```

---

## 3. Why Brute Force Is Slow

There can be:

```text
O(N²)
```

subarrays.

Even though zero counting is incremental, the algorithm still examines quadratic candidate windows.

Sliding Window processes every element at most twice.

---

## 4. Pattern Identification

Look for:

```text
binary array
longest subarray
at most K zeros
at most K bad elements
```

A powerful generalization is:

> Find the longest window containing at most K invalid elements.

Here:

```text
invalid element = 0
```

---

## 5. Sliding Window Intuition

Maintain:

```text
[left ... right]
```

and track:

```text
zeroCount
```

Expand `right`.

When:

```text
zeroCount > K
```

the window becomes invalid.

Move `left` until:

```text
zeroCount <= K
```

Then calculate:

```text
right - left + 1
```

---

## 6. Window Invariant

The current window must satisfy:

```text
zeroCount <= K
```

This is the complete invariant.

---

## 7. Pointer Movement

```java
for (int right = 0; right < nums.length; right++) {

    if (nums[right] == 0) {
        zeroCount++;
    }

    while (zeroCount > k) {

        if (nums[left] == 0) {
            zeroCount--;
        }

        left++;
    }

    answer = Math.max(
            answer,
            right - left + 1
    );
}
```

---

## 8. Data Structure Used

No HashMap is necessary.

Only:

```java
int zeroCount
int left
int right
```

This is one reason binary-array Sliding Window is efficient.

---

## 9. Java Implementation

```java
static int longestAtMostKZeros(
        int[] nums,
        int k) {

    if (nums == null
            || nums.length == 0
            || k < 0) {
        return 0;
    }

    int left = 0;
    int zeroCount = 0;
    int maxLength = 0;

    for (int right = 0;
         right < nums.length;
         right++) {

        if (nums[right] == 0) {
            zeroCount++;
        }

        while (zeroCount > k) {

            if (nums[left] == 0) {
                zeroCount--;
            }

            left++;
        }

        maxLength = Math.max(
                maxLength,
                right - left + 1
        );
    }

    return maxLength;
}
```

---

## 10. Dry Run

```text
nums = [1,1,1,0,0,1,1]
K = 2
```

Start:

```text
left = 0
zeroCount = 0
```

Expand:

```text
[1]
[1,1]
[1,1,1]
[1,1,1,0]
[1,1,1,0,0]
```

At this point:

```text
zeroCount = 2
```

Valid.

Add another `1`:

```text
[1,1,1,0,0,1]
```

Still valid.

If another zero appears:

```text
zeroCount = 3
```

Invalid.

Move `left` until one zero leaves the window.

Then continue.

---

## 11. Edge Cases

- empty array;
- `K = 0`;
- `K < 0`;
- all ones;
- all zeros;
- `K >= number of zeros`;
- one-element array.

---

## 12. Complexity Analysis

Each element enters once and leaves once.

```text
Time:  O(N)
Space: O(1)
```

---

## 13. Common Mistakes

- Counting ones instead of zeros;
- using `if` instead of `while`;
- forgetting to decrement `zeroCount`;
- moving `left` without checking whether it is zero;
- confusing "at most K zeros" with "exactly K zeros".

---

## 14. Interview Follow-Up Questions

1. What changes when `K = 0`?
2. Can you solve it with prefix sums?
3. Can you return the actual subarray?
4. Can you return its indexes?
5. What if the array contains arbitrary integers and some values are considered bad?
6. What if we want the shortest valid window?
7. How would you count all valid subarrays instead of finding the longest?

---

## 15. Variations

- Longest subarray with at most K zeros;
- Longest subarray with at most K ones;
- Longest subarray with at most K bad values;
- Longest substring with at most K mismatches;
- Count binary subarrays with at most K zeros.

---

# 2. K Flips

## 1. Problem Statement

Given a binary array, you may flip at most `K` zeros into ones.

Find the maximum number of consecutive ones after performing at most `K` flips.

Example:

```text
nums = [1,1,1,0,0,0,1,1,1,1,0]
K = 2
```

The problem is equivalent to:

> Find the longest window containing at most K zeros.

---

## 2. Brute-Force Solution

Generate every subarray.

Count zeros.

If:

```text
zeroCount <= K
```

the entire window can be converted into ones.

```java
static int maxConsecutiveOnesBruteForce(
        int[] nums,
        int k) {

    int answer = 0;

    for (int i = 0; i < nums.length; i++) {

        int zeros = 0;

        for (int j = i; j < nums.length; j++) {

            if (nums[j] == 0) {
                zeros++;
            }

            if (zeros > k) {
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

There are:

```text
O(N²)
```

possible windows.

Sliding Window reduces this to:

```text
O(N)
```

---

## 4. Pattern Identification

Look for:

```text
flip at most K zeros
convert zeros to ones
maximum consecutive ones
longest ones after K changes
```

The critical transformation is:

```text
K flips
    ↓
at most K zeros inside the window
```

---

## 5. Sliding Window Intuition

Suppose:

```text
window = 1 1 0 1 0 1
```

There are:

```text
2 zeros
```

If:

```text
K = 2
```

we can flip both:

```text
1 1 1 1 1 1
```

Therefore the window is valid.

If there are `K + 1` zeros:

```text
window invalid
```

Shrink from the left.

---

## 6. Window Invariant

```text
zeroCount <= K
```

This means:

```text
the window can be converted entirely into ones
using at most K flips.
```

---

## 7. Pointer Movement

```text
right -> expand
left  -> shrink when zeroCount > K
```

---

## 8. Data Structure Used

Only:

```java
int zeroCount
```

No map or set is required.

---

## 9. Java Implementation

```java
static int longestOnesAfterKFlips(
        int[] nums,
        int k) {

    if (nums == null
            || nums.length == 0
            || k < 0) {
        return 0;
    }

    int left = 0;
    int zeroCount = 0;
    int answer = 0;

    for (int right = 0;
         right < nums.length;
         right++) {

        if (nums[right] == 0) {
            zeroCount++;
        }

        while (zeroCount > k) {

            if (nums[left] == 0) {
                zeroCount--;
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
nums = [1,0,1,0,1,0,1]
K = 2
```

Window progression:

```text
[1]             zeros=0
[1,0]           zeros=1
[1,0,1]         zeros=1
[1,0,1,0]       zeros=2
[1,0,1,0,1]     zeros=2
```

Length:

```text
5
```

Add another zero:

```text
[1,0,1,0,1,0]
```

Now:

```text
zeros = 3
```

Invalid.

Move `left` until one zero leaves.

The window becomes valid again.

---

## 11. Edge Cases

- `K = 0`;
- `K >= number of zeros`;
- all zeros;
- all ones;
- empty array;
- negative K.

---

## 12. Complexity Analysis

```text
Time:  O(N)
Space: O(1)
```

---

## 13. Common Mistakes

- Thinking the problem requires physically modifying the array;
- actually flipping elements;
- counting ones instead of zeros;
- forgetting that "at most K flips" means zeros <= K;
- using nested loops unnecessarily.

---

## 14. Interview Follow-Up Questions

1. Why don't we actually perform the flips?
2. Why is counting zeros sufficient?
3. How would you return the flipped subarray?
4. How would you return the indexes of the K flips?
5. What if flipping a zero has a cost?
6. What if there are multiple types of changes?
7. Can this be generalized to non-binary arrays?

---

## 15. Variations

- Max Consecutive Ones III;
- Longest binary subarray after K flips;
- K replacements;
- K changes;
- Longest sequence after replacing K bad elements.

---

# 3. Consecutive Ones

## 1. Problem Statement

Find the maximum number of consecutive `1`s in a binary array.

Example:

```text
nums = [1,1,0,1,1,1]
```

Answer:

```text
3
```

This is the simplest member of the binary Sliding Window family.

---

## 2. Brute-Force Solution

For every starting position, continue while values are `1`.

```java
static int maxConsecutiveOnesBruteForce(
        int[] nums) {

    int answer = 0;

    for (int i = 0; i < nums.length; i++) {

        int length = 0;

        for (int j = i; j < nums.length; j++) {

            if (nums[j] == 0) {
                break;
            }

            length++;
        }

        answer = Math.max(
                answer,
                length
        );
    }

    return answer;
}
```

---

## 3. Why Brute Force Is Slow

The same stretches of ones may be scanned repeatedly.

Worst-case:

```text
[1,1,1,1,1,1,1,...]
```

The nested loops can result in:

```text
O(N²)
```

---

## 4. Pattern Identification

Look for:

```text
maximum consecutive ones
longest run of ones
longest sequence of 1s
```

There is an even simpler observation:

```text
If current value is 1:
    currentLength++

If current value is 0:
    currentLength = 0
```

This problem does not actually require a full Sliding Window.

That is an important interview lesson:

> Not every problem that looks like Sliding Window needs two pointers.

---

## 5. Sliding Window Intuition

If we still think in Sliding Window terms:

```text
window contains only ones
```

When we encounter zero:

```text
window becomes invalid
```

Move the left boundary past the zero.

But because the constraint is extremely simple, a running counter is cleaner.

---

## 6. Window Invariant

The current valid window contains:

```text
only 1s
```

Equivalent:

```text
zeroCount == 0
```

---

## 7. Pointer Movement

Simplest solution:

```java
if (nums[i] == 1) {
    current++;
} else {
    current = 0;
}
```

A two-pointer version is possible but unnecessary.

---

## 8. Data Structure Used

No data structure is required.

Only:

```java
int currentLength
int maxLength
```

---

## 9. Java Implementation

```java
static int maxConsecutiveOnes(
        int[] nums) {

    if (nums == null || nums.length == 0) {
        return 0;
    }

    int currentLength = 0;
    int maxLength = 0;

    for (int value : nums) {

        if (value == 1) {
            currentLength++;

            maxLength = Math.max(
                    maxLength,
                    currentLength
            );

        } else {
            currentLength = 0;
        }
    }

    return maxLength;
}
```

---

## 10. Dry Run

```text
nums = [1,1,0,1,1,1,0,1]
```

Process:

```text
1 -> current=1 -> max=1
1 -> current=2 -> max=2
0 -> current=0
1 -> current=1 -> max=2
1 -> current=2 -> max=2
1 -> current=3 -> max=3
0 -> current=0
1 -> current=1
```

Final:

```text
3
```

---

## 11. Edge Cases

- empty array;
- all ones;
- all zeros;
- one element;
- starts with zero;
- ends with zero;
- alternating `1,0,1,0`.

---

## 12. Complexity Analysis

```text
Time:  O(N)
Space: O(1)
```

---

## 13. Common Mistakes

- Using unnecessary HashMap;
- using nested loops;
- forgetting to reset the current count;
- confusing this problem with K-flips;
- using Sliding Window when a simple scan is enough.

---

## 14. Interview Follow-Up Questions

1. Why is a simple scan better than Sliding Window?
2. How would you solve it with K flips?
3. How would you return the longest sequence indexes?
4. What if one zero can be deleted?
5. What if K zeros can be deleted?
6. What if the array is streamed continuously?
7. What if you need the longest circular run?

---

## 15. Variations

- Maximum consecutive ones;
- Maximum consecutive ones after K flips;
- Maximum consecutive ones after deleting one element;
- Longest run of zeros;
- Longest binary run with K changes;
- Circular binary array.

---

# Binary Array Pattern Comparison

| Problem | Window Type | Invariant | State |
|---|---|---|---|
| At Most K Zeros | Variable | `zeros <= K` | `zeroCount` |
| K Flips | Variable | `zeros <= K` | `zeroCount` |
| Consecutive Ones | Simple scan | `zeros == 0` | `currentLength` |

---

# At Most K Zeros vs K Flips

These are mathematically the same pattern.

## At Most K Zeros

Question:

> Find the longest window containing at most K zeros.

Invariant:

```text
zeros <= K
```

## K Flips

Question:

> Find the longest window that can become all ones using at most K flips.

A zero requires one flip.

Therefore:

```text
flipsNeeded = zeroCount
```

So:

```text
zeroCount <= K
```

The algorithms are identical.

---

# Generalization — K Bad Elements

The binary-array pattern is actually a much broader pattern.

Imagine:

```text
nums = [good, bad, good, bad, good]
K = 2
```

If we define:

```text
bad element = 0
```

then:

```text
longest window with <= K bad elements
```

uses exactly the same Sliding Window.

General template:

```java
static int longestWithAtMostKBadElements(
        int[] nums,
        int k) {

    int left = 0;
    int badCount = 0;
    int answer = 0;

    for (int right = 0;
         right < nums.length;
         right++) {

        if (isBad(nums[right])) {
            badCount++;
        }

        while (badCount > k) {

            if (isBad(nums[left])) {
                badCount--;
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

This abstraction is extremely useful in interviews.

---

# Counting Binary Subarrays

The same pattern can be changed from:

```text
LONGEST
```

to:

```text
COUNT
```

For example:

> Count binary subarrays containing at most K zeros.

After restoring validity:

```java
answer += right - left + 1L;
```

So:

```text
Longest
    -> max(windowLength)

Count
    -> sum(windowLength)
```

This is a very important connection with the previous Counting Patterns file.

---

# Binary Array Recognition Guide

When you see:

```text
binary array
```

ask:

```text
What does zero represent?
```

Usually:

```text
zero = bad element
one  = good element
```

Then ask:

```text
How many bad elements are allowed?
```

If:

```text
at most K
```

use:

```text
zeroCount <= K
```

If:

```text
exactly K
```

consider:

```text
AtMost(K) - AtMost(K-1)
```

If:

```text
no zero
```

you may only need a simple scan.

---

# Master Java Templates

## Template 1 — Longest with At Most K Zeros

```java
int left = 0;
int zeros = 0;
int answer = 0;

for (int right = 0;
     right < nums.length;
     right++) {

    if (nums[right] == 0) {
        zeros++;
    }

    while (zeros > k) {

        if (nums[left] == 0) {
            zeros--;
        }

        left++;
    }

    answer = Math.max(
            answer,
            right - left + 1
    );
}
```

---

## Template 2 — Count with At Most K Zeros

```java
int left = 0;
int zeros = 0;
long answer = 0;

for (int right = 0;
     right < nums.length;
     right++) {

    if (nums[right] == 0) {
        zeros++;
    }

    while (zeros > k) {

        if (nums[left] == 0) {
            zeros--;
        }

        left++;
    }

    answer += right - left + 1L;
}
```

---

## Template 3 — Consecutive Ones

```java
int current = 0;
int answer = 0;

for (int value : nums) {

    if (value == 1) {
        current++;
        answer = Math.max(answer, current);
    } else {
        current = 0;
    }
}
```

---

# Common Mistakes Across Binary Patterns

```text
[ ] Confusing zeros with ones
[ ] Forgetting that one zero = one flip
[ ] Using HashMap unnecessarily
[ ] Using nested loops
[ ] Using if instead of while for invalid windows
[ ] Forgetting to decrement zeroCount
[ ] Confusing at most K with exactly K
[ ] Returning int when counting may require long
[ ] Missing the simpler one-pass solution
[ ] Physically flipping array values unnecessarily
```

---

# Interview Follow-Up Master Questions

1. Why does one zero correspond to one flip?
2. Why is `zeroCount <= K` the invariant?
3. Why do we not actually modify the array?
4. What is the complexity?
5. Why can the left pointer only move forward?
6. What if K is zero?
7. What if K is larger than the number of zeros?
8. What if the array is all zeros?
9. What if the array is all ones?
10. How do you count instead of maximize?
11. How do you solve exactly K?
12. Can this be generalized to K bad elements?
13. Can this work for strings?
14. When is Sliding Window unnecessary?
15. How would you solve the problem on a stream?

---

# Advanced Variations

## 1. Longest Ones After Deleting One Element

Classic variation:

```text
You may delete exactly one element.
Find longest consecutive ones.
```

A useful interpretation is:

```text
allow at most one zero
```

but the final window length must account for the deletion.

---

## 2. Longest Binary Subarray with K Changes

Generalize:

```text
zeroCount <= K
```

---

## 3. At Most K Ones

Symmetric version:

```text
oneCount <= K
```

---

## 4. At Most K Zeros and K Ones

Now the window has two constraints:

```text
zeroCount <= K
oneCount <= K
```

Since:

```text
windowLength = zeroCount + oneCount
```

this can often be simplified, but the exact problem determines the best formulation.

---

# When Binary Sliding Window Works

Binary Sliding Window works especially well when the condition is:

```text
number of zeros <= K
```

or:

```text
number of bad elements <= K
```

because as the window expands:

```text
badCount
```

only increases.

As the window shrinks:

```text
badCount
```

only decreases.

This monotonicity makes the two-pointer approach possible.

---

# When It Does Not Automatically Work

Be careful with:

```text
sum == K
```

especially if negative values are allowed.

Also be careful with conditions that are not monotonic.

For example:

```text
sum exactly K
```

does not generally behave like:

```text
sum <= K
```

for arbitrary integer arrays.

In such cases, consider:

```text
Prefix Sum
HashMap
Deque
Binary Search
```

depending on the problem.

---

# Binary Array Decision Tree

```text
                 BINARY ARRAY
                      |
                      v
             What is being asked?
                      |
          +-----------+-----------+
          |           |           |
       LONGEST      COUNT       EXACTLY
          |           |           |
          v           v           v
     At Most K     At Most K    AtMost(K)
       Zeros         Zeros          -
          |           |          AtMost(K-1)
          v           v
     max(length)   sum(length)
```

---

# Complexity Master Table

| Problem | Time | Space |
|---|---:|---:|
| Longest Consecutive Ones | O(N) | O(1) |
| Longest At Most K Zeros | O(N) | O(1) |
| K Flips | O(N) | O(1) |
| Count At Most K Zeros | O(N) | O(1) |
| Exactly K Zeros | O(N) | O(1) |

---

# Final Mental Model

```text
BINARY ARRAY
     |
     v
ZERO = BAD
     |
     v
How many bad elements are allowed?
     |
     +----------------------+
     |                      |
   AT MOST K              EXACTLY K
     |                      |
     v                      v
zeroCount <= K       AtMost(K) -
     |                AtMost(K-1)
     |
     +--------------------+
     |                    |
   LONGEST              COUNT
     |                    |
     v                    v
max(window)        sum(window)
```

The most important transformation is:

```text
K FLIPS
   ↓
K BAD ELEMENTS ALLOWED
   ↓
zeroCount <= K
   ↓
Sliding Window
```

And the most important optimization is recognizing when the full Sliding Window is unnecessary:

```text
Pure consecutive ones
    ↓
Simple running counter
```

---

# Master Interview Checklist

Before coding:

```text
[ ] Is the array binary?
[ ] Can I treat 0 as the bad element?
[ ] Is the problem about at most K bad elements?
[ ] Is K a flip/replacement budget?
[ ] What is the window invariant?
[ ] What happens when right sees zero?
[ ] What happens when left removes zero?
[ ] Should I maximize window length?
[ ] Should I count windows?
[ ] If counting, can I use right-left+1?
[ ] If exactly K, can I use AtMost(K)-AtMost(K-1)?
[ ] Is a HashMap actually necessary?
[ ] Is a simple scan sufficient?
[ ] Does the result require long?
```

---

# Final Takeaway

Master these three transformations:

```text
1. Consecutive Ones

zeroCount == 0
        ↓
simple running counter


2. K Flips

flips <= K
        ↓
zeroCount <= K
        ↓
variable Sliding Window


3. Counting

valid window
        ↓
right - left + 1
        ↓
count every valid suffix
```

Once these become automatic, binary-array Sliding Window questions become a recognition exercise rather than a memorization exercise.
