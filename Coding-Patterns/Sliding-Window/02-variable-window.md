# Sliding Window — Pattern 2: Variable Window

Variable Window is used when the window size is **not fixed**.

```text
Variable Window
│
├── Longest Valid
└── Shortest Valid
```

Unlike Fixed Window, we do not maintain:

```text
window size == K
```

Instead, we maintain a **condition/invariant**.

The general structure is:

```java
int left = 0;

for (int right = 0; right < n; right++) {

    // Add nums[right]

    while (window is invalid) {
        // Remove nums[left]
        left++;
    }

    // Process valid window
}
```

The two major forms are:

```text
Longest Valid Window
    -> Expand as much as possible
    -> Shrink only when invalid

Shortest Valid Window
    -> Expand until valid
    -> Shrink as much as possible while valid
```

---

# 1. Variable Window — Longest Valid

## 1. Problem Statement

Find the **longest contiguous subarray/substring that satisfies a given condition**.

A canonical problem is:

> Given an array of positive integers and a target `S`, find the length of the longest contiguous subarray whose sum is at most `S`.

### Example

```text
nums = [2, 1, 1, 1, 3, 1]
S = 5
```

Valid windows include:

```text
[2,1,1,1] = 5
[1,1,1] = 3
[1,3,1] = 5
```

The answer is:

```text
4
```

---

## 2. Brute-Force Solution

Generate every possible subarray and calculate its sum.

```java
static int longestValidBruteForce(int[] nums, int s) {
    int maxLength = 0;

    for (int i = 0; i < nums.length; i++) {
        long sum = 0;

        for (int j = i; j < nums.length; j++) {
            sum += nums[j];

            if (sum <= s) {
                maxLength = Math.max(maxLength, j - i + 1);
            }
        }
    }

    return maxLength;
}
```

This already avoids a third loop by maintaining the sum incrementally.

---

## 3. Why Brute Force Is Slow

There are:

```text
O(N²)
```

possible contiguous subarrays.

Even though each sum is maintained incrementally, we still inspect every `(i, j)` pair.

For:

```text
N = 100,000
```

the number of candidate windows can become enormous.

We need to exploit the fact that when the array contains **positive numbers**, increasing the window can only increase the sum.

That monotonic property enables Sliding Window.

---

## 4. Pattern Identification

Look for phrases such as:

- longest subarray satisfying a condition;
- longest substring satisfying a condition;
- longest contiguous sequence;
- maximum length while condition remains valid;
- longest window with at most K;
- longest window with sum at most S;
- longest substring with at most K distinct characters.

The key question is:

> Can I expand the window and detect when it becomes invalid, then move `left` until it becomes valid again?

If yes, Variable Sliding Window may apply.

### Important prerequisite

The shrinking strategy must be logically valid.

For sum constraints such as:

```text
sum <= S
```

the standard positive-number version works because adding a positive number never decreases the sum.

This is **not generally true with negative numbers**.

---

## 5. Sliding Window Intuition

Start with an empty window:

```text
left = 0
right = 0
```

Expand `right`.

Suppose:

```text
[2,1,1,1]
sum = 5
```

This is valid.

Add `3`:

```text
[2,1,1,1,3]
sum = 8
```

Invalid.

Now shrink from the left:

```text
remove 2

[1,1,1,3]
sum = 6
```

Still invalid.

Remove `1`:

```text
[1,1,3]
sum = 5
```

Valid again.

Now calculate the length.

The key idea:

> `right` explores new possibilities; `left` removes the minimum amount necessary to restore the invariant.

---

## 6. Window Invariant

For this problem:

```text
sum <= S
```

must be true whenever we evaluate the answer.

Therefore:

```text
while (sum > S) {
    sum -= nums[left];
    left++;
}
```

After the loop:

```text
sum <= S
```

is guaranteed.

Then:

```java
maxLength = Math.max(maxLength, right - left + 1);
```

The invariant is:

> The current window is valid and contains the longest valid window ending at `right`.

---

## 7. Pointer Movement

### `right`

Always moves forward:

```text
right++
```

### `left`

Moves forward only when the window is invalid:

```java
while (sum > s) {
    sum -= nums[left];
    left++;
}
```

Important:

`left` never moves backward.

Therefore both pointers move at most `N` times.

---

## 8. Data Structure Used

For the positive-number sum example:

```text
long sum
int left
int right
```

No HashMap or Set is required.

For other longest-valid problems, the required state may change.

Examples:

| Problem | State |
|---|---|
| At most K distinct | HashMap |
| At most K zeros | Counter |
| Unique characters | Set / frequency |
| Character replacement | Frequency array/map |
| Sum constraint | Running sum |

---

## 9. Java Implementation

### Longest subarray with sum <= S

```java
static int longestSubarrayWithSumAtMost(int[] nums, long s) {
    if (nums == null || nums.length == 0) {
        return 0;
    }

    int left = 0;
    int maxLength = 0;
    long sum = 0;

    for (int right = 0; right < nums.length; right++) {
        sum += nums[right];

        while (sum > s && left <= right) {
            sum -= nums[left];
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

### Important assumption

This implementation assumes:

```text
nums[i] > 0
```

or at minimum non-negative values for the monotonic sum behavior.

With arbitrary negative values, this standard Sliding Window approach is not generally valid.

---

## 10. Dry Run

```text
nums = [2,1,1,1,3,1]
S = 5
```

### Step 1

```text
right = 0
window = [2]
sum = 2
length = 1
max = 1
```

### Step 2

```text
right = 1
window = [2,1]
sum = 3
length = 2
max = 2
```

### Step 3

```text
right = 2
window = [2,1,1]
sum = 4
length = 3
max = 3
```

### Step 4

```text
right = 3
window = [2,1,1,1]
sum = 5
length = 4
max = 4
```

### Step 5

Add `3`:

```text
[2,1,1,1,3]
sum = 8
```

Invalid.

Shrink:

```text
remove 2

[1,1,1,3]
sum = 6
```

Still invalid.

Shrink:

```text
remove 1

[1,1,3]
sum = 5
```

Valid.

Length:

```text
3
```

### Step 6

Add `1`:

```text
[1,1,3,1]
sum = 6
```

Shrink:

```text
remove 1

[1,3,1]
sum = 5
```

Length:

```text
3
```

Final answer:

```text
4
```

---

## 11. Edge Cases

### Empty array

Return:

```text
0
```

### `S` is very large

The complete array may be valid.

### `S = 0`

If values are positive, no non-empty window is valid.

### Single element

Handle normally.

### Values contain zero

The approach still works.

### Negative values

Do not blindly use this template.

Negative numbers can make the sum decrease when the window expands, breaking the monotonic assumption.

---

## 12. Complexity Analysis

Each element is:

- added once by `right`;
- removed at most once by `left`.

Therefore:

```text
Time:  O(N)
Space: O(1)
```

This is one of the most important Sliding Window amortized-analysis patterns.

Although there is a nested `while` loop, the algorithm is still `O(N)`.

---

## 13. Common Mistakes

### Mistake 1 — Using `if` instead of `while`

Wrong:

```java
if (sum > s) {
    sum -= nums[left++];
}
```

The window may still be invalid.

Correct:

```java
while (sum > s) {
    sum -= nums[left++];
}
```

### Mistake 2 — Updating the answer before restoring validity

Wrong:

```java
sum += nums[right];

maxLength = Math.max(...);

while (sum > s) {
    ...
}
```

The answer might represent an invalid window.

### Mistake 3 — Assuming Sliding Window works with arbitrary negative numbers

This is a major interview trap.

### Mistake 4 — Moving `left` backward

`left` must only move forward.

---

## 14. Interview Follow-Up Questions

1. Why is the nested `while` still `O(N)`?
2. Why does this fail with negative numbers?
3. How would you solve the negative-number version?
4. Find the longest window with sum `< S`.
5. Find the longest window with sum `>= S`.
6. Find the actual longest subarray, not just its length.
7. Find the longest substring with at most K distinct characters.
8. Find the longest substring with at most K replacements.
9. What is the difference between `if` and `while` when shrinking?
10. What makes a Sliding Window condition monotonic?

---

## 15. Variations

- Longest subarray with sum at most S
- Longest substring with at most K distinct characters
- Longest substring with at most K replacements
- Longest binary subarray with at most K zeros
- Longest substring without repeating characters
- Longest window satisfying `max - min <= K`

---

# 2. Variable Window — Shortest Valid

## 1. Problem Statement

Find the **shortest contiguous subarray/substring satisfying a required condition**.

A canonical problem is:

> Given an array of positive integers and target `S`, find the minimum length of a contiguous subarray whose sum is at least `S`.

### Example

```text
nums = [2,3,1,2,4,3]
S = 7
```

Valid windows include:

```text
[2,3,1,2] = 8
[3,1,2,4] = 10
[4,3] = 7
```

The shortest is:

```text
[4,3]
```

Length:

```text
2
```

---

## 2. Brute-Force Solution

Check every possible subarray.

```java
static int minSubArrayLenBruteForce(int[] nums, int target) {
    int minLength = Integer.MAX_VALUE;

    for (int i = 0; i < nums.length; i++) {
        long sum = 0;

        for (int j = i; j < nums.length; j++) {
            sum += nums[j];

            if (sum >= target) {
                minLength = Math.min(
                        minLength,
                        j - i + 1
                );
            }
        }
    }

    return minLength == Integer.MAX_VALUE
            ? 0
            : minLength;
}
```

---

## 3. Why Brute Force Is Slow

There are:

```text
O(N²)
```

candidate subarrays.

For every starting point, we may inspect many ending points.

The Sliding Window approach exploits the fact that for positive numbers:

```text
adding elements increases the sum
removing elements decreases the sum
```

This creates a useful monotonic property.

---

## 4. Pattern Identification

Look for:

- minimum length subarray;
- shortest substring;
- smallest window;
- shortest contiguous sequence satisfying a condition;
- minimum number of consecutive elements whose sum is at least target;
- smallest window containing required elements.

The strongest clue is:

> **Find the shortest valid window.**

Typical strategy:

```text
Expand until valid
        ↓
Shrink while still valid
        ↓
Record the shortest window
```

---

## 5. Sliding Window Intuition

Start expanding:

```text
[2]
sum = 2
```

Not enough.

```text
[2,3]
sum = 5
```

Still not enough.

```text
[2,3,1]
sum = 6
```

Still not enough.

```text
[2,3,1,2]
sum = 8
```

Now valid.

But we want the **shortest** valid window.

Try removing from the left:

```text
[3,1,2]
sum = 6
```

Invalid.

Therefore:

```text
[2,3,1,2]
```

is the best valid window ending at this `right`.

Continue expanding.

Eventually:

```text
[4,3]
sum = 7
```

Length `2`, which becomes the answer.

---

## 6. Window Invariant

For shortest-valid problems, the key invariant is slightly different.

During the shrinking phase:

```text
sum >= target
```

must remain true.

Therefore:

```java
while (sum >= target) {
    minLength = Math.min(
        minLength,
        right - left + 1
    );

    sum -= nums[left];
    left++;
}
```

The critical idea is:

> While the window is valid, keep shrinking it because a shorter valid window may exist.

---

## 7. Pointer Movement

### `right`

Expands the window.

```text
right++
```

### `left`

Moves aggressively whenever the window is valid.

```java
while (sum >= target) {
    update answer;
    remove nums[left];
    left++;
}
```

This is the opposite mindset from many longest-window problems.

### Longest valid

```text
invalid → shrink
valid → record
```

### Shortest valid

```text
valid → record + shrink
invalid → expand
```

---

## 8. Data Structure Used

For the positive-number sum problem:

```text
long sum
int left
int right
int minLength
```

No additional data structure is required.

Other shortest-window problems may need:

```text
HashMap
frequency array
counter
deque
```

depending on the validity condition.

---

## 9. Java Implementation

```java
static int minSubArrayLen(int target, int[] nums) {
    if (nums == null || nums.length == 0) {
        return 0;
    }

    int left = 0;
    int minLength = Integer.MAX_VALUE;
    long sum = 0;

    for (int right = 0; right < nums.length; right++) {
        sum += nums[right];

        while (sum >= target) {
            minLength = Math.min(
                    minLength,
                    right - left + 1
            );

            sum -= nums[left];
            left++;
        }
    }

    return minLength == Integer.MAX_VALUE
            ? 0
            : minLength;
}
```

### Important assumption

This standard solution requires positive/non-negative values for the sum monotonicity needed by the window.

For arbitrary negative numbers, use a different algorithmic technique.

---

## 10. Dry Run

```text
nums = [2,3,1,2,4,3]
target = 7
```

### right = 0

```text
window = [2]
sum = 2
```

Invalid.

### right = 1

```text
window = [2,3]
sum = 5
```

Invalid.

### right = 2

```text
window = [2,3,1]
sum = 6
```

Invalid.

### right = 3

```text
window = [2,3,1,2]
sum = 8
```

Valid.

Record:

```text
length = 4
```

Shrink:

```text
remove 2

[3,1,2]
sum = 6
```

Stop shrinking.

Current minimum:

```text
4
```

### right = 4

Add `4`:

```text
[3,1,2,4]
sum = 10
```

Valid.

Record:

```text
length = 4
```

Remove `3`:

```text
[1,2,4]
sum = 7
```

Still valid.

Record:

```text
length = 3
```

Remove `1`:

```text
[2,4]
sum = 6
```

Invalid.

### right = 5

Add `3`:

```text
[2,4,3]
sum = 9
```

Record:

```text
length = 3
```

Remove `2`:

```text
[4,3]
sum = 7
```

Still valid.

Record:

```text
length = 2
```

Remove `4`:

```text
[3]
sum = 3
```

Invalid.

Final answer:

```text
2
```

---

## 11. Edge Cases

### No valid window

Return:

```text
0
```

### Target is 0

For positive numbers, the answer can immediately be `1`.

### Single element meets target

Answer:

```text
1
```

### Entire array is required

Return:

```text
N
```

### Negative numbers

Do not blindly apply this template.

### Large sums

Use:

```java
long sum
```

when integer overflow is possible.

---

## 12. Complexity Analysis

Each element enters the window once and leaves at most once.

Therefore:

```text
Time:  O(N)
Space: O(1)
```

Again, despite the nested `while`, total pointer movement is linear.

---

## 13. Common Mistakes

### Mistake 1 — Updating answer after shrinking

You need to record the current valid window **before** removing the left element.

Correct:

```java
while (sum >= target) {
    minLength = Math.min(
        minLength,
        right - left + 1
    );

    sum -= nums[left++];
}
```

### Mistake 2 — Using `if`

Wrong:

```java
if (sum >= target) {
    ...
}
```

You may have multiple unnecessary elements that can be removed.

Use:

```java
while (sum >= target)
```

### Mistake 3 — Returning `Integer.MAX_VALUE`

Return `0` when no valid window exists.

### Mistake 4 — Forgetting the positive-number assumption

This is a common interview trap.

---

## 14. Interview Follow-Up Questions

1. Why do we shrink while valid?
2. Why do we record the answer before removing `left`?
3. Why is the algorithm `O(N)`?
4. What changes with negative numbers?
5. Return the actual shortest subarray.
6. Find the shortest substring containing all characters of another string.
7. Find the minimum window with at least K distinct characters.
8. Find the shortest binary subarray with sum at least K.
9. What if the target can be negative?
10. Can prefix sums solve the negative-number version?

---

## 15. Variations

- Minimum Size Subarray Sum
- Minimum Window Substring
- Shortest subarray satisfying a frequency requirement
- Shortest binary subarray with required sum
- Shortest window containing all required characters
- Minimum window with at least K distinct values

---

# Longest Valid vs Shortest Valid

This distinction is one of the most important Sliding Window interview concepts.

| Concept | Longest Valid | Shortest Valid |
|---|---|---|
| Goal | Maximize length | Minimize length |
| Expand | Usually always | Until valid |
| Shrink | When invalid | While valid |
| Main condition | Restore validity | Exploit validity |
| Update answer | After valid | During valid shrinking |
| Typical loop | `while (invalid)` | `while (valid)` |

### Longest

```java
for (int right = 0; right < n; right++) {

    add(nums[right]);

    while (invalid()) {
        remove(nums[left]);
        left++;
    }

    answer = Math.max(
        answer,
        right - left + 1
    );
}
```

### Shortest

```java
for (int right = 0; right < n; right++) {

    add(nums[right]);

    while (valid()) {

        answer = Math.min(
            answer,
            right - left + 1
        );

        remove(nums[left]);
        left++;
    }
}
```

---

# Variable Window Master Recognition Guide

Ask these questions in order:

```text
1. Is the problem about a contiguous subarray/substring?
                |
               YES
                ↓
2. Is the window size fixed?
                |
               NO
                ↓
3. Is there a validity condition?
                |
               YES
                ↓
4. Do I need the LONGEST valid window?
                |
               YES
                ↓
        Expand → invalid → shrink
```

Or:

```text
4. Do I need the SHORTEST valid window?
                |
               YES
                ↓
        Expand → valid → shrink aggressively
```

---

# The Most Important Rule

Do not memorize:

```text
Longest = while invalid
Shortest = while valid
```

without understanding **why**.

Instead ask:

> What happens to the condition when I add an element?

and:

> What happens when I remove an element?

Sliding Window works when this relationship allows us to move the left boundary monotonically without losing candidate answers.

---

# When Variable Sliding Window Usually Works

Typical examples:

```text
Positive numbers + sum constraint
At most K distinct characters
At most K zeros
At most K replacements
No duplicate characters
Maximum window under a constraint
Minimum window satisfying a monotonic requirement
```

---

# When It May NOT Work

Be careful when:

```text
Array contains arbitrary negative numbers
Condition is non-monotonic
Removing from the left can unpredictably improve/worsen validity
The problem requires arbitrary non-contiguous elements
The required state cannot be updated incrementally
```

For example:

```text
Find shortest subarray with sum >= K
```

with arbitrary negative values cannot generally be solved by the simple:

```java
while (sum >= K)
```

template.

More advanced techniques such as:

```text
Prefix Sum + Monotonic Deque
```

may be required.

---

# Variable Window Interview Checklist

Before coding:

```text
[ ] Is the data contiguous?
[ ] Is the window size variable?
[ ] What exactly makes a window valid?
[ ] Is validity monotonic?
[ ] What happens when right expands?
[ ] What happens when left shrinks?
[ ] Do I need LONGEST or SHORTEST?
[ ] For longest: when does the window become invalid?
[ ] For shortest: when does the window become valid?
[ ] What state must be maintained?
[ ] Can I update state incrementally?
[ ] Does the input contain negative numbers?
[ ] Does the invariant still hold with those values?
[ ] Does left ever move backward? It should not.
[ ] Can each element enter/leave only O(1) times?
```

---

# Practice Progression

## Beginner

1. Longest subarray with sum at most S
2. Minimum size subarray with sum at least S
3. Longest binary subarray with at most K zeros
4. Longest substring with at most K distinct characters

## Intermediate

5. Longest substring without repeating characters
6. Longest repeating-character replacement
7. Longest subarray with at most K distinct values
8. Minimum window containing required characters

## Advanced

9. Longest window where `max - min <= K`
10. Shortest window satisfying multiple frequency constraints
11. Sliding Window + HashMap
12. Sliding Window + Monotonic Deque
13. Variable Window + multiple constraints
14. Prefix Sum + Monotonic Deque when normal Sliding Window fails

---

# Final Mental Model

```text
VARIABLE WINDOW
       |
       +-------------------------+
       |                         |
       v                         v
LONGEST VALID              SHORTEST VALID
       |                         |
       v                         v
Expand                      Expand
       |                         |
       v                         v
Invalid?                    Valid?
       |                         |
      YES                       YES
       |                         |
       v                         v
Shrink                      Record answer
       |                         |
       v                         v
Restore validity            Shrink
       |                         |
       v                         v
Record maximum             Try shorter window
```

The most important skill is not memorizing code.

It is identifying:

```text
WINDOW
+
VALIDITY CONDITION
+
MONOTONICITY
+
LEFT/RIGHT MOVEMENT
+
WINDOW INVARIANT
```

Once those five pieces are clear, the implementation is usually straightforward.
