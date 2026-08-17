# Sliding Window — Pattern 5: Counting Patterns

Counting Sliding Window problems are different from ordinary "find the longest/shortest window" problems.

Instead of asking:

```text
What is the best window?
```

we ask:

```text
How many valid subarrays/substrings exist?
```

The key idea is:

> For every `right`, count how many valid windows end at `right`.

```text
Counting Patterns
│
├── Count At Most K
├── Count Exactly K
└── Subarrays Ending at Right
```

---

# 1. Count At Most K

## 1. Problem Statement

Given an array, count the number of subarrays containing **at most K distinct elements**.

Example:

```text
nums = [1, 2, 1, 2, 3]
K = 2
```

We need to count every contiguous subarray whose number of distinct values is at most `2`.

---

## 2. Brute-Force Solution

Generate every subarray and maintain its distinct elements.

```java
static long countAtMostKBruteForce(
        int[] nums,
        int k) {

    long count = 0;

    for (int i = 0; i < nums.length; i++) {

        Set<Integer> set = new HashSet<>();

        for (int j = i; j < nums.length; j++) {

            set.add(nums[j]);

            if (set.size() <= k) {
                count++;
            } else {
                break;
            }
        }
    }

    return count;
}
```

---

## 3. Why Brute Force Is Slow

There can be:

```text
N * (N + 1) / 2
```

subarrays.

Therefore:

```text
Time = O(N²)
```

Sliding Window can count all valid subarrays in:

```text
O(N)
```

---

## 4. Pattern Identification

Look for:

```text
count subarrays
number of subarrays
at most K
no more than K
maximum K distinct
```

The most important signal is:

```text
COUNT + AT MOST K
```

---

## 5. Sliding Window Intuition

Maintain a window:

```text
[left ... right]
```

such that:

```text
distinctCount <= K
```

For each `right`, once the window becomes valid:

```text
[left ... right]
```

is valid.

But importantly, so are:

```text
[left+1 ... right]
[left+2 ... right]
...
[right ... right]
```

Therefore the number of valid subarrays ending at `right` is:

```text
right - left + 1
```

This is the central counting trick.

---

## 6. Window Invariant

```text
number of distinct elements in window <= K
```

The window must be the **smallest left boundary that still makes the current right boundary valid**.

In other words:

```text
[left ... right] is valid
```

and every window beginning before `left` is invalid.

---

## 7. Pointer Movement

For each `right`:

1. Add `nums[right]`.
2. If distinct count exceeds `K`, move `left`.
3. Continue until the window is valid.
4. Add:

```text
right - left + 1
```

to the answer.

---

## 8. Data Structure Used

Use:

```java
HashMap<Integer, Integer>
```

The map stores:

```text
element -> frequency inside current window
```

Why frequency?

Because when `left` moves, we need to know whether an element completely leaves the window.

---

## 9. Java Implementation

```java
static long countAtMostK(
        int[] nums,
        int k) {

    if (nums == null || nums.length == 0 || k <= 0) {
        return 0;
    }

    Map<Integer, Integer> frequency =
            new HashMap<>();

    int left = 0;
    long count = 0;

    for (int right = 0;
         right < nums.length;
         right++) {

        int value = nums[right];

        frequency.put(
                value,
                frequency.getOrDefault(value, 0) + 1
        );

        while (frequency.size() > k) {

            int leftValue = nums[left];

            int newFrequency =
                    frequency.get(leftValue) - 1;

            if (newFrequency == 0) {
                frequency.remove(leftValue);
            } else {
                frequency.put(
                        leftValue,
                        newFrequency
                );
            }

            left++;
        }

        count += right - left + 1L;
    }

    return count;
}
```

---

## 10. Dry Run

```text
nums = [1, 2, 1]
K = 2
```

### right = 0

Window:

```text
[1]
```

Distinct:

```text
1
```

Valid.

New subarrays ending at `0`:

```text
[1]
```

Count:

```text
1
```

---

### right = 1

Window:

```text
[1,2]
```

Distinct:

```text
2
```

Valid.

New subarrays ending at `1`:

```text
[1,2]
[2]
```

Count:

```text
2
```

Total:

```text
3
```

---

### right = 2

Window:

```text
[1,2,1]
```

Distinct:

```text
2
```

Valid.

New subarrays ending at `2`:

```text
[1,2,1]
[2,1]
[1]
```

Count:

```text
3
```

Final:

```text
6
```

All subarrays are valid because the array contains only two distinct values.

---

## 11. Edge Cases

- `K <= 0`;
- empty array;
- `K >= number of distinct elements`;
- all elements identical;
- all elements different;
- large answer requiring `long`.

Important:

The number of subarrays can be:

```text
N * (N + 1) / 2
```

For large `N`, this may exceed `int`.

Use:

```java
long
```

for the answer.

---

## 12. Complexity Analysis

Each element:

- enters the window once;
- leaves the window once.

Therefore:

```text
Time:  O(N)
Space: O(K)
```

More precisely, the map stores at most `K + 1` elements temporarily.

---

## 13. Common Mistakes

### Mistake 1

Adding:

```text
1
```

for every valid window.

Wrong.

You must add:

```text
right - left + 1
```

---

### Mistake 2

Using a `Set` instead of a frequency map.

A set cannot correctly handle removal when duplicates exist.

---

### Mistake 3

Using `int` for the result.

Use:

```java
long
```

---

### Mistake 4

Shrinking only once.

Use:

```java
while
```

because multiple elements may need to be removed.

---

### Mistake 5

Confusing:

```text
at most K
```

with:

```text
exactly K
```

---

## 14. Interview Follow-Up Questions

1. Why is the contribution `right - left + 1`?
2. Why does every subarray ending at `right` count?
3. Why do we need frequencies?
4. Can we use an array instead of HashMap?
5. Why is the answer a `long`?
6. What changes for strings?
7. What changes for binary arrays?
8. How do you count exactly K distinct elements?
9. Can this work with negative values?
10. Can you return the actual subarrays?

---

## 15. Variations

- Count subarrays with at most K distinct integers.
- Count substrings with at most K distinct characters.
- Count binary subarrays with at most K ones.
- Count subarrays with at most K odd numbers.
- Count subarrays satisfying a bounded frequency constraint.

---

# 2. Count Exactly K

## 1. Problem Statement

Count the number of subarrays containing **exactly K distinct elements**.

Example:

```text
nums = [1, 2, 1, 2, 3]
K = 2
```

Expected answer:

```text
7
```

---

## 2. Brute-Force Solution

Generate all subarrays and count their distinct values.

```java
static long countExactlyKBruteForce(
        int[] nums,
        int k) {

    long count = 0;

    for (int i = 0; i < nums.length; i++) {

        Set<Integer> set = new HashSet<>();

        for (int j = i; j < nums.length; j++) {

            set.add(nums[j]);

            if (set.size() == k) {
                count++;
            } else if (set.size() > k) {
                break;
            }
        }
    }

    return count;
}
```

---

## 3. Why Brute Force Is Slow

There are:

```text
O(N²)
```

possible subarrays.

Sliding Window provides an elegant `O(N)` solution.

---

## 4. Pattern Identification

The most important recognition rule is:

```text
EXACTLY K
```

For many Sliding Window problems:

```text
Exactly(K)
=
AtMost(K)
-
AtMost(K - 1)
```

This is one of the most important interview transformations.

---

## 5. Sliding Window Intuition

Directly maintaining:

```text
distinctCount == K
```

is awkward for counting because there can be multiple valid left boundaries.

Instead calculate:

```text
number with at most K
```

minus:

```text
number with at most K - 1
```

Therefore:

```text
exactlyK = atMostK - atMostKMinusOne
```

---

## 6. Window Invariant

For each `atMost(K)` calculation:

```text
distinctCount <= K
```

Then:

```text
exactly K
=
at most K
-
at most K-1
```

---

## 7. Pointer Movement

The `atMostK` helper follows:

```java
add right

while distinctCount > K:
    remove left
    left++

count += right - left + 1
```

Run it twice:

```text
atMost(K)
atMost(K - 1)
```

---

## 8. Data Structure Used

```java
HashMap<Integer, Integer>
```

---

## 9. Java Implementation

```java
static long countExactlyK(
        int[] nums,
        int k) {

    if (nums == null || nums.length == 0 || k <= 0) {
        return 0;
    }

    return countAtMostK(nums, k)
            - countAtMostK(nums, k - 1);
}

static long countAtMostK(
        int[] nums,
        int k) {

    if (k <= 0) {
        return 0;
    }

    Map<Integer, Integer> frequency =
            new HashMap<>();

    int left = 0;
    long count = 0;

    for (int right = 0;
         right < nums.length;
         right++) {

        int value = nums[right];

        frequency.put(
                value,
                frequency.getOrDefault(value, 0) + 1
        );

        while (frequency.size() > k) {

            int leftValue = nums[left];

            int frequencyAfterRemoval =
                    frequency.get(leftValue) - 1;

            if (frequencyAfterRemoval == 0) {
                frequency.remove(leftValue);
            } else {
                frequency.put(
                        leftValue,
                        frequencyAfterRemoval
                );
            }

            left++;
        }

        count += right - left + 1L;
    }

    return count;
}
```

---

## 10. Dry Run

```text
nums = [1,2,1]
K = 2
```

Calculate:

```text
AtMost(2)
```

All subarrays are valid:

```text
6
```

Calculate:

```text
AtMost(1)
```

Valid subarrays are:

```text
[1]
[2]
[1]
```

Count:

```text
3
```

Therefore:

```text
Exactly(2)
=
6 - 3
=
3
```

Those are:

```text
[1,2]
[2,1]
[1,2,1]
```

---

## 11. Edge Cases

- `K <= 0`;
- `K > number of distinct elements`;
- empty array;
- all values identical;
- all values distinct;
- large result.

---

## 12. Complexity Analysis

Each `atMostK` calculation:

```text
Time:  O(N)
Space: O(K)
```

We execute it twice:

```text
Time:  O(N)
Space: O(K)
```

---

## 13. Common Mistakes

### Mistake 1

Trying to directly count:

```text
distinctCount == K
```

without understanding left-boundary multiplicity.

---

### Mistake 2

Forgetting:

```text
atMost(K - 1)
```

---

### Mistake 3

Writing:

```text
atMost(K) + atMost(K - 1)
```

Wrong.

Correct:

```text
atMost(K) - atMost(K - 1)
```

---

### Mistake 4

Not handling:

```text
K <= 0
```

---

## 14. Interview Follow-Up Questions

1. Prove `Exactly(K) = AtMost(K) - AtMost(K-1)`.
2. Why is direct counting harder?
3. Can you solve without calling the helper twice?
4. What is the maximum possible answer?
5. Can this technique count substrings?
6. What other problems use this identity?
7. Can it be applied to sum constraints?

---

## 15. Variations

- Exactly K distinct integers;
- Exactly K distinct characters;
- Exactly K odd numbers;
- Exactly K zeros;
- Exactly K elements satisfying a property.

---

# 3. Subarrays Ending at Right

## 1. Problem Statement

Understand the general counting technique:

> For a fixed `right`, how many valid subarrays end at `right`?

This is the core mathematical idea behind many counting Sliding Window problems.

Suppose:

```text
nums = [1, 2, 3, 4]
```

and the current valid window is:

```text
[left ... right]
```

Then every start position from:

```text
left
```

through:

```text
right
```

creates a valid subarray ending at `right`.

Therefore:

```text
number of valid subarrays ending at right
=
right - left + 1
```

---

## 2. Brute-Force Solution

Enumerate every possible start position for every `right`.

```java
static long countUsingBruteForce(
        int[] nums) {

    long count = 0;

    for (int right = 0;
         right < nums.length;
         right++) {

        for (int left = 0;
             left <= right;
             left++) {

            if (isValid(nums, left, right)) {
                count++;
            }
        }
    }

    return count;
}
```

---

## 3. Why Brute Force Is Slow

There are:

```text
N(N+1)/2
```

subarrays.

Therefore:

```text
O(N²)
```

candidate ranges.

If `isValid()` itself requires scanning the window, complexity can become:

```text
O(N³)
```

for some implementations.

---

## 4. Pattern Identification

Look for:

```text
count subarrays
count substrings
number of valid ranges
how many subarrays end at...
```

The key question should become:

> For the current `right`, how far left can I go while the window remains valid?

If the answer is `left`, then:

```text
right - left + 1
```

valid subarrays end at `right`.

---

## 5. Sliding Window Intuition

Suppose:

```text
left = 2
right = 5
```

The valid windows ending at `5` are:

```text
[2..5]
[3..5]
[4..5]
[5..5]
```

Number:

```text
4
```

Formula:

```text
5 - 2 + 1 = 4
```

This eliminates the need to enumerate those four subarrays individually.

---

## 6. Window Invariant

The window:

```text
[left ... right]
```

must satisfy the required condition.

More specifically:

```text
[left ... right] is valid
```

and `left` should be the smallest valid start under the chosen monotonic constraint.

Then every start:

```text
left, left+1, ..., right
```

is also valid.

This last property is essential.

---

## 7. Pointer Movement

General structure:

```java
for (int right = 0; right < n; right++) {

    add(nums[right]);

    while (windowIsInvalid()) {
        remove(nums[left]);
        left++;
    }

    answer += right - left + 1L;
}
```

---

## 8. Data Structure Used

Depends on the validity condition.

Examples:

```text
HashMap
HashSet
frequency array
running sum
Deque
```

For:

```text
at most K distinct
```

use:

```java
HashMap
```

For:

```text
binary array with at most K ones
```

a simple integer count may be enough.

---

## 9. Java Implementation

Example:

> Count subarrays containing at most `K` distinct integers.

```java
static long countEndingAtRight(
        int[] nums,
        int k) {

    if (nums == null || nums.length == 0 || k <= 0) {
        return 0;
    }

    Map<Integer, Integer> frequency =
            new HashMap<>();

    int left = 0;
    long answer = 0;

    for (int right = 0;
         right < nums.length;
         right++) {

        int value = nums[right];

        frequency.put(
                value,
                frequency.getOrDefault(value, 0) + 1
        );

        while (frequency.size() > k) {

            int leftValue = nums[left];

            int newFrequency =
                    frequency.get(leftValue) - 1;

            if (newFrequency == 0) {
                frequency.remove(leftValue);
            } else {
                frequency.put(
                        leftValue,
                        newFrequency
                );
            }

            left++;
        }

        // Every start from left to right is valid.
        answer += right - left + 1L;
    }

    return answer;
}
```

---

## 10. Dry Run

Consider:

```text
nums = [1,2,1]
K = 2
```

### right = 0

```text
window = [1]
left = 0
```

Valid starts:

```text
0
```

Contribution:

```text
0 - 0 + 1 = 1
```

---

### right = 1

```text
window = [1,2]
left = 0
```

Valid starts:

```text
0, 1
```

Contribution:

```text
1 - 0 + 1 = 2
```

---

### right = 2

```text
window = [1,2,1]
left = 0
```

Valid starts:

```text
0, 1, 2
```

Contribution:

```text
2 - 0 + 1 = 3
```

Total:

```text
1 + 2 + 3 = 6
```

---

# The Most Important Counting Insight

For a normal longest-window problem:

```text
answer = max(answer, windowLength)
```

For a counting problem:

```text
answer += windowLength
```

where:

```text
windowLength = right - left + 1
```

This is a major interview pattern.

---

# Why This Works

Suppose:

```text
left = 3
right = 7
```

Current valid window:

```text
3 4 5 6 7
```

Every suffix of this window ending at `7` is also valid:

```text
[3..7]
[4..7]
[5..7]
[6..7]
[7..7]
```

Number:

```text
5
```

Formula:

```text
7 - 3 + 1 = 5
```

Instead of processing five windows, we count them with one arithmetic operation.

---

# When This Counting Trick Is Valid

This technique requires a monotonic property.

If:

```text
[left ... right]
```

is valid, then all suffixes:

```text
[left+1 ... right]
[left+2 ... right]
...
[right ... right]
```

must also be valid.

Examples where this commonly works:

```text
At most K distinct
At most K zeros
At most K odd numbers
Sum <= K
Maximum-minimum <= K
```

when the underlying condition supports the necessary monotonicity.

---

# When It Does NOT Automatically Work

Be careful with conditions such as:

```text
sum == K
exactly K distinct
exactly K occurrences
```

The direct:

```text
right - left + 1
```

formula may not directly count the desired exact condition.

For example:

```text
exactly K distinct
```

is usually handled by:

```text
atMost(K) - atMost(K - 1)
```

---

# Counting At Most vs Counting Exactly

## At Most K

Direct Sliding Window:

```java
answer += right - left + 1;
```

because all suffixes remain within the "at most K" constraint.

---

## Exactly K

Use:

```text
Exactly(K)
=
AtMost(K)
-
AtMost(K - 1)
```

This converts a difficult exact condition into two monotonic at-most conditions.

---

# A General Counting Template

```java
static long countValid(int[] nums) {

    int left = 0;
    long answer = 0;

    for (int right = 0;
         right < nums.length;
         right++) {

        // 1. Add right element
        add(nums[right]);

        // 2. Restore validity
        while (invalid()) {
            remove(nums[left]);
            left++;
        }

        // 3. Count all valid suffixes
        answer += right - left + 1L;
    }

    return answer;
}
```

---

# Pattern Recognition

When an interviewer says:

```text
How many subarrays...
```

immediately ask:

```text
Is the validity condition monotonic?
```

If yes, investigate:

```text
Sliding Window
```

Then ask:

```text
For a fixed right,
what is the smallest valid left?
```

If it is `left`:

```text
number of valid subarrays ending at right
=
right - left + 1
```

---

# Important Mathematical Formula

Number of all subarrays of an array of length `N`:

```text
N(N + 1) / 2
```

Example:

```text
N = 4

4 * 5 / 2
= 10
```

They are:

```text
[0]
[1]
[2]
[3]

[0..1]
[1..2]
[2..3]

[0..2]
[1..3]

[0..3]
```

This is why the result can be much larger than `N`.

Use:

```java
long
```

---

# Comparison of Counting Patterns

| Pattern | Main Idea | Formula |
|---|---|---|
| Count At Most K | Maintain valid window | `ans += right-left+1` |
| Count Exactly K | Difference of two at-most counts | `atMost(K)-atMost(K-1)` |
| Ending at Right | Count valid starts | `right-left+1` |

---

# Counting Pattern Decision Tree

```text
                    COUNT SUBARRAYS?
                          |
                         YES
                          |
                Is condition monotonic?
                     /           \
                   YES            NO
                    |              |
                    v              v
             Sliding Window   Consider Prefix Sum,
                    |          HashMap, DP, etc.
                    |
          +---------+---------+
          |                   |
       AT MOST K          EXACTLY K
          |                   |
          v                   v
   Direct Window       AtMost(K)
          |                -
          v             AtMost(K-1)
 right-left+1
```

---

# Common Mistakes

## Mistake 1 — Counting only the current window

Wrong:

```java
answer++;
```

Correct:

```java
answer += right - left + 1L;
```

---

## Mistake 2 — Using `int`

Wrong:

```java
int answer;
```

Prefer:

```java
long answer;
```

---

## Mistake 3 — Not shrinking enough

Wrong:

```java
if (invalid) {
    left++;
}
```

Usually correct:

```java
while (invalid) {
    remove(nums[left]);
    left++;
}
```

---

## Mistake 4 — Applying the formula to non-monotonic exact conditions

Do not blindly use:

```text
right-left+1
```

for every counting problem.

Verify that all suffixes are valid.

---

## Mistake 5 — Forgetting duplicate frequencies

For distinct-count constraints:

```text
distinct count
```

is not the same as:

```text
total elements
```

---

# Interview Follow-Up Questions

1. Why does `right - left + 1` count all valid subarrays?
2. Prove the counting formula.
3. Why is the condition required to be monotonic?
4. Why does `Exactly(K)` use two `AtMost` calculations?
5. Why must the answer use `long`?
6. Can you count substrings instead of integer subarrays?
7. Can you solve it using prefix sums?
8. What if negative numbers are allowed?
9. What happens if the constraint is `sum == K`?
10. What happens if the constraint is `sum <= K`?
11. Can you return the actual valid ranges?
12. Can you count only windows of length at least L?
13. Can you count only windows of length exactly L?
14. What if the validity condition is `max - min <= K`?
15. Can a Deque replace the HashMap?

---

# Variations

## Variation 1 — At Most K Distinct

```text
Count subarrays with <= K distinct values
```

Use:

```text
HashMap
```

---

## Variation 2 — At Most K Odd Numbers

Convert the problem into:

```text
count of odd numbers <= K
```

Maintain:

```java
int oddCount;
```

---

## Variation 3 — At Most K Zeros

Maintain:

```java
int zeroCount;
```

---

## Variation 4 — At Most K Different Characters

Same pattern as distinct integers:

```text
HashMap<Character, Integer>
```

---

## Variation 5 — Exactly K Distinct

Use:

```text
atMost(K) - atMost(K-1)
```

---

# Production-Quality Java Template

```java
public static long countAtMostKDistinct(
        int[] nums,
        int k) {

    if (nums == null || nums.length == 0 || k <= 0) {
        return 0L;
    }

    Map<Integer, Integer> frequency =
            new HashMap<>();

    int left = 0;
    long result = 0L;

    for (int right = 0;
         right < nums.length;
         right++) {

        int current = nums[right];

        frequency.merge(
                current,
                1,
                Integer::sum
        );

        while (frequency.size() > k) {

            int outgoing = nums[left];

            frequency.computeIfPresent(
                    outgoing,
                    (key, value) ->
                            value == 1 ? null : value - 1
            );

            left++;
        }

        result += (long) right - left + 1;
    }

    return result;
}
```

---

# Interview Optimization

If the value range is small, replace:

```java
HashMap<Integer, Integer>
```

with:

```java
int[]
```

For example, if:

```text
0 <= nums[i] <= 100000
```

you can use an array.

This can improve constant factors.

---

# Advanced Insight — Contribution Thinking

A powerful way to understand counting Sliding Window is:

Instead of asking:

```text
How many windows are there?
```

ask:

```text
How many valid windows does this RIGHT endpoint contribute?
```

For each `right`:

```text
contribution(right)
=
right - left + 1
```

Then:

```text
total
=
Σ contribution(right)
```

This transforms an `O(N²)` enumeration problem into an `O(N)` contribution problem.

---

# Sliding Window Counting Master Formula

For monotonic validity:

```text
Expand right
      ↓
Restore validity
      ↓
Find smallest valid left
      ↓
Count all valid starts
      ↓
answer += right - left + 1
```

Memorize the **reasoning**, not just the formula.

---

# Master Checklist

Before coding:

```text
[ ] Is this asking for COUNT?
[ ] Is the object a contiguous subarray/substring?
[ ] Is validity monotonic?
[ ] Can I maintain validity incrementally?
[ ] What state does the window need?
[ ] What makes the window invalid?
[ ] How far should left move?
[ ] For this right, is every suffix from left valid?
[ ] If yes, use right-left+1
[ ] Does the answer require long?
[ ] Is this AT MOST or EXACTLY?
[ ] If EXACTLY, can I use AtMost(K)-AtMost(K-1)?
```

---

# Final Mental Model

```text
COUNTING SLIDING WINDOW
          |
          v
      Fix RIGHT
          |
          v
   Maintain validity
          |
          v
     Find LEFT
          |
          v
 Count valid suffixes
          |
          v
 right - left + 1
```

The three concepts to master are:

```text
1. At Most K
       ↓
   direct counting

2. Exactly K
       ↓
   AtMost(K) - AtMost(K-1)

3. Ending at Right
       ↓
   right - left + 1
```

If these three ideas become automatic, a large class of "count subarrays" interview problems becomes much easier to recognize.
