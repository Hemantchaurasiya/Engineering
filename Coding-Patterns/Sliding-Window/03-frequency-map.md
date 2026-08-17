# Sliding Window — Pattern 3: Frequency Map

Frequency Map Sliding Window is used when the validity of a window depends on **how many times values or characters occur inside the current window**.

```text
Frequency Map
│
├── At Most K
├── Exactly K
└── Required Frequency
```

This category is extremely important for Java interviews because it combines:

```text
Sliding Window
+
HashMap / Frequency Array
+
Window Invariant
+
At-Most / Exactly transformations
```

---

# 0. Core Concept

A frequency map stores:

```text
element -> frequency inside current window
```

For example:

```text
window = "abac"

frequency:

a -> 2
b -> 1
c -> 1
```

When an element enters:

```java
freq.put(x, freq.getOrDefault(x, 0) + 1);
```

When an element leaves:

```java
freq.put(x, freq.get(x) - 1);
```

If the frequency becomes zero:

```java
freq.remove(x);
```

The most important idea is:

> The frequency map must describe exactly the current window.

---

# 1. Frequency Map — At Most K

## 1. Problem Statement

Find the length of the **longest substring containing at most K distinct characters**.

### Example

```text
s = "eceba"
k = 2
```

Possible windows:

```text
"e"
"ec"
"ece"
"ba"
```

The longest valid window is:

```text
"ece"
```

Length:

```text
3
```

Because it contains:

```text
e -> 2
c -> 1
```

Only 2 distinct characters.

---

## 2. Brute-Force Solution

Generate every substring and count distinct characters.

```java
static int longestAtMostKBruteForce(String s, int k) {
    int maxLength = 0;

    for (int i = 0; i < s.length(); i++) {
        Set<Character> set = new HashSet<>();

        for (int j = i; j < s.length(); j++) {
            set.add(s.charAt(j));

            if (set.size() <= k) {
                maxLength = Math.max(
                        maxLength,
                        j - i + 1
                );
            }
        }
    }

    return maxLength;
}
```

---

## 3. Why Brute Force Is Slow

There are:

```text
O(N²)
```

possible substrings.

Even if the set is updated incrementally, we still consider every starting and ending position.

The Sliding Window approach maintains the distinct count incrementally and moves both pointers forward only.

---

## 4. Pattern Identification

Look for:

- at most K distinct characters;
- at most K different numbers;
- at most K unique values;
- longest substring with at most K types;
- longest subarray with at most K distinct elements.

The phrase:

```text
AT MOST K DISTINCT
```

is a major Sliding Window signal.

---

## 5. Sliding Window Intuition

Maintain:

```text
left
right
frequency map
distinct count
```

Expand `right`.

If a new character appears:

```text
distinct++
```

If:

```text
distinct > K
```

the window is invalid.

Shrink from the left until:

```text
distinct <= K
```

Then calculate the length.

---

## 6. Window Invariant

After shrinking:

```text
number of distinct elements <= K
```

This is the invariant.

The frequency map must accurately represent the current window.

Example:

```text
s = "eceba"
k = 2
```

When the window becomes:

```text
"eceb"
```

frequencies:

```text
e -> 2
c -> 1
b -> 1
```

Distinct count:

```text
3
```

Invalid.

Remove from left:

```text
remove e

"ceb"
```

Still 3 distinct.

Remove c:

```text
"eb"
```

Now:

```text
e -> 1
b -> 1
```

Distinct:

```text
2
```

Valid again.

---

## 7. Pointer Movement

```text
right -> always expands

left -> moves while distinct > K
```

Template:

```java
for (int right = 0; right < n; right++) {

    add(s.charAt(right));

    while (distinct > k) {
        remove(s.charAt(left));
        left++;
    }

    update answer;
}
```

---

## 8. Data Structure Used

### General solution

```java
HashMap<Character, Integer>
```

### Lowercase English letters

```java
int[26]
```

### Integer array

```java
HashMap<Integer, Integer>
```

The map is usually the most general interview solution.

---

## 9. Java Implementation

```java
static int longestSubstringAtMostKDistinct(
        String s,
        int k) {

    if (s == null || s.isEmpty() || k <= 0) {
        return 0;
    }

    Map<Character, Integer> freq = new HashMap<>();

    int left = 0;
    int maxLength = 0;

    for (int right = 0; right < s.length(); right++) {

        char c = s.charAt(right);

        freq.put(
                c,
                freq.getOrDefault(c, 0) + 1
        );

        while (freq.size() > k) {

            char leftChar = s.charAt(left);

            int count = freq.get(leftChar);

            if (count == 1) {
                freq.remove(leftChar);
            } else {
                freq.put(leftChar, count - 1);
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
s = "eceba"
k = 2
```

| right | char | window | distinct | action |
|---:|---|---|---:|---|
| 0 | e | e | 1 | valid |
| 1 | c | ec | 2 | valid |
| 2 | e | ece | 2 | valid |
| 3 | b | eceb | 3 | shrink |
| 3 | b | ceb | 3 | shrink |
| 3 | b | eb | 2 | valid |
| 4 | a | eba | 3 | shrink |
| 4 | a | ba | 2 | valid |

Maximum:

```text
3
```

---

## 11. Edge Cases

- `k = 0`
- `k = 1`
- `k >= number of distinct characters`
- empty string
- all characters identical
- all characters different
- Unicode characters

---

## 12. Complexity Analysis

Average-case HashMap operations are:

```text
O(1)
```

Each character enters and leaves the window at most once.

Therefore:

```text
Time:  O(N)
Space: O(K)
```

More precisely, the map can contain at most `K` distinct elements.

---

## 13. Common Mistakes

### Mistake 1

Tracking window size instead of distinct count.

```text
window size != number of distinct values
```

### Mistake 2

Not removing keys when frequency becomes zero.

### Mistake 3

Using:

```java
if (freq.size() > k)
```

instead of:

```java
while (freq.size() > k)
```

The window may remain invalid after one removal.

### Mistake 4

Confusing:

```text
at most K distinct
```

with:

```text
exactly K distinct
```

---

## 14. Interview Follow-Up Questions

1. Find the longest substring with at most K distinct characters.
2. Find the longest subarray with at most K distinct integers.
3. What if K is larger than the number of distinct values?
4. Can you solve it using `int[26]`?
5. What changes for Unicode?
6. Find the shortest window with at most K distinct values.
7. Count the number of subarrays with at most K distinct values.
8. How do you derive exactly K from at most K?

---

## 15. Variations

- Longest substring with at most K distinct characters
- Longest subarray with at most K distinct integers
- Count subarrays with at most K distinct values
- Longest substring with K character types
- At most K odd values
- At most K zeros

---

# 2. Frequency Map — Exactly K

## 1. Problem Statement

Find the number of subarrays containing **exactly K distinct elements**.

Example:

```text
nums = [1,2,1,2,3]
k = 2
```

The valid subarrays include:

```text
[1,2]
[2,1]
[1,2]
[1,2,1]
[2,1,2]
[1,2,1,2]
[2,1,2,3] -> 3 distinct, invalid
```

The answer is:

```text
7
```

This is a classic interview problem.

---

## 2. Brute-Force Solution

Generate every subarray and maintain its distinct count.

```java
static int subarraysWithExactlyKBruteForce(
        int[] nums,
        int k) {

    int count = 0;

    for (int i = 0; i < nums.length; i++) {

        Set<Integer> set = new HashSet<>();

        for (int j = i; j < nums.length; j++) {

            set.add(nums[j]);

            if (set.size() == k) {
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
N * (N + 1) / 2
```

possible subarrays.

Therefore:

```text
O(N²)
```

time.

For large arrays, this is too slow.

---

## 4. Pattern Identification

The critical phrase is:

```text
EXACTLY K DISTINCT
```

This is usually solved using:

```text
exactly(K)
=
atMost(K)
-
atMost(K - 1)
```

This transformation is one of the most important Sliding Window counting techniques.

---

## 5. Sliding Window Intuition

Why does subtraction work?

Consider:

```text
atMost(K)
```

This counts:

```text
0, 1, 2, ..., K distinct
```

And:

```text
atMost(K - 1)
```

counts:

```text
0, 1, 2, ..., K-1 distinct
```

Subtract:

```text
atMost(K) - atMost(K-1)
```

Everything below K cancels.

What remains:

```text
exactly K
```

Therefore:

```text
Exactly K = At Most K - At Most K-1
```

---

## 6. Window Invariant

For the helper:

```java
countAtMostK(nums, k)
```

the window must satisfy:

```text
distinct <= k
```

For every right endpoint, all valid starting positions form a continuous range.

If the left boundary after shrinking is `left`, then:

```text
left
left + 1
...
right
```

are all valid starting positions.

Number of valid subarrays ending at `right`:

```text
right - left + 1
```

This is the key counting insight.

---

## 7. Pointer Movement

For `atMost(K)`:

```java
for each right:

    add nums[right]

    while distinct > K:
        remove nums[left]
        left++

    count += right - left + 1
```

Why:

```text
right - left + 1
```

?

Because every start index from `left` through `right` produces a valid subarray ending at `right`.

---

## 8. Data Structure Used

```java
HashMap<Integer, Integer>
```

The map stores:

```text
number -> frequency in current window
```

---

## 9. Java Implementation

```java
static long subarraysWithExactlyKDistinct(
        int[] nums,
        int k) {

    if (nums == null || k <= 0) {
        return 0;
    }

    return countAtMostK(nums, k)
            - countAtMostK(nums, k - 1);
}

static long countAtMostK(int[] nums, int k) {

    if (k < 0) {
        return 0;
    }

    Map<Integer, Integer> freq = new HashMap<>();

    int left = 0;
    long count = 0;

    for (int right = 0; right < nums.length; right++) {

        freq.put(
                nums[right],
                freq.getOrDefault(nums[right], 0) + 1
        );

        while (freq.size() > k) {

            int value = nums[left];

            int frequency = freq.get(value);

            if (frequency == 1) {
                freq.remove(value);
            } else {
                freq.put(value, frequency - 1);
            }

            left++;
        }

        count += right - left + 1;
    }

    return count;
}
```

Use `long` for the answer because the number of subarrays can be approximately:

```text
N * (N + 1) / 2
```

which can exceed `int`.

---

## 10. Dry Run

```text
nums = [1,2,1,2,3]
k = 2
```

We calculate:

```text
exactly(2)
=
atMost(2)
-
atMost(1)
```

### `atMost(2)`

For each `right`, count:

```text
right - left + 1
```

The total is:

```text
12
```

### `atMost(1)`

The valid subarrays are only runs of the same value.

Total:

```text
5
```

Therefore:

```text
12 - 5 = 7
```

Answer:

```text
7
```

---

## 11. Edge Cases

### `K <= 0`

Return:

```text
0
```

### `K > number of distinct values`

Answer:

```text
0
```

### All values equal

Exactly one distinct value can exist.

### All values different

Exactly K means the window must contain K unique values.

### Large N

Use:

```java
long count
```

---

## 12. Complexity Analysis

Each `atMostK` execution is:

```text
O(N)
```

We execute it twice:

```text
O(N) + O(N) = O(N)
```

Therefore:

```text
Time:  O(N)
Space: O(K)
```

---

## 13. Common Mistakes

### Mistake 1

Trying to directly count exactly K with a single ordinary window.

Counting exactly K is more subtle than finding longest/shortest.

### Mistake 2

Forgetting:

```text
exactly K =
atMost K - atMost K-1
```

### Mistake 3

Using:

```text
count++
```

instead of:

```java
count += right - left + 1;
```

### Mistake 4

Returning `int`.

Use `long` for large input.

---

## 14. Interview Follow-Up Questions

1. Why does exactly K equal two at-most calculations?
2. Why is the contribution `right - left + 1`?
3. Count subarrays with exactly K odd numbers.
4. Count subarrays with exactly K zeros.
5. Count substrings with exactly K distinct characters.
6. Can you do this with two HashMaps?
7. Why is the subtraction approach easier to reason about?
8. What happens when K is zero?

---

## 15. Variations

- Exactly K distinct integers
- Exactly K distinct characters
- Exactly K odd numbers
- Exactly K zeros
- Exactly K different values
- Exactly K elements satisfying a predicate

---

# 3. Frequency Map — Required Frequency

## 1. Problem Statement

Find the shortest substring/window that contains all required characters with at least the required frequencies.

The canonical problem is:

> Given strings `s` and `t`, find the minimum window in `s` that contains every character of `t` with the required frequency.

### Example

```text
s = "ADOBECODEBANC"
t = "ABC"
```

Answer:

```text
"BANC"
```

Why?

```text
B -> 1
A -> 1
N -> extra
C -> 1
```

It contains every required character.

---

## 2. Brute-Force Solution

Generate every substring and check whether it contains all required frequencies.

```java
static String minWindowBruteForce(
        String s,
        String t) {

    String answer = "";

    for (int i = 0; i < s.length(); i++) {

        Map<Character, Integer> freq =
                new HashMap<>();

        for (int j = i; j < s.length(); j++) {

            char c = s.charAt(j);

            freq.put(
                    c,
                    freq.getOrDefault(c, 0) + 1
            );

            if (containsRequired(freq, t)) {

                String current =
                        s.substring(i, j + 1);

                if (answer.isEmpty()
                        || current.length()
                        < answer.length()) {

                    answer = current;
                }

                break;
            }
        }
    }

    return answer;
}
```

A production implementation of the brute force would optimize the requirement check, but the fundamental approach remains quadratic or worse depending on how the check is implemented.

---

## 3. Why Brute Force Is Slow

We may inspect:

```text
O(N²)
```

substrings.

If each substring requires scanning the requirement map, complexity can approach:

```text
O(N² * M)
```

where:

```text
N = length of s
M = number of required characters
```

The Sliding Window solution maintains the requirement state incrementally.

---

## 4. Pattern Identification

Look for:

- minimum window containing another string;
- shortest substring containing all required characters;
- contains all characters of pattern;
- minimum window with required frequencies;
- at least the required count of each element.

The key phrase is:

```text
REQUIRED FREQUENCY
```

Unlike "at most K distinct", this problem does not care only about the number of distinct values.

It cares about exact requirements.

Example:

```text
t = "AABC"
```

requires:

```text
A -> 2
B -> 1
C -> 1
```

A window containing only one `A` is not valid.

---

## 5. Sliding Window Intuition

Maintain two frequency maps:

```text
need   -> required frequencies
window -> current frequencies
```

Example:

```text
t = "AABC"

need:
A -> 2
B -> 1
C -> 1
```

As characters enter the window, update `window`.

We also track:

```text
formed
```

which represents how many required character types currently satisfy their required frequencies.

When:

```text
formed == required
```

the window is valid.

Now shrink from the left to find the shortest valid window.

---

## 6. Window Invariant

The central invariant is:

```text
formed == required
```

means:

> Every required character has reached its required frequency.

For:

```text
t = "AABC"
```

we have:

```text
required = 3
```

because there are 3 required character types:

```text
A
B
C
```

When:

```text
window[A] >= need[A]
window[B] >= need[B]
window[C] >= need[C]
```

then:

```text
formed = 3
```

and the window is valid.

---

## 7. Pointer Movement

### Expand

Move `right` forward and add:

```java
s.charAt(right)
```

If the character is required and its frequency reaches exactly the required count:

```java
formed++;
```

### Shrink

While:

```text
formed == required
```

the window is valid.

Record the current answer.

Then remove `s[left]`.

If removing it causes a required frequency to fall below the required count:

```java
formed--;
```

Then move:

```java
left++;
```

---

## 8. Data Structure Used

General solution:

```java
Map<Character, Integer> need;
Map<Character, Integer> window;
```

For lowercase English letters:

```java
int[26]
```

For ASCII:

```java
int[128]
```

The two-map approach is easiest to understand in interviews.

---

## 9. Java Implementation

```java
static String minWindow(String s, String t) {

    if (s == null || t == null
            || s.isEmpty()
            || t.isEmpty()
            || t.length() > s.length()) {
        return "";
    }

    Map<Character, Integer> need = new HashMap<>();

    for (char c : t.toCharArray()) {
        need.put(
                c,
                need.getOrDefault(c, 0) + 1
        );
    }

    Map<Character, Integer> window =
            new HashMap<>();

    int required = need.size();
    int formed = 0;

    int left = 0;

    int bestStart = 0;
    int bestLength = Integer.MAX_VALUE;

    for (int right = 0; right < s.length(); right++) {

        char c = s.charAt(right);

        if (need.containsKey(c)) {

            window.put(
                    c,
                    window.getOrDefault(c, 0) + 1
            );

            if (window.get(c).intValue()
                    == need.get(c).intValue()) {
                formed++;
            }
        }

        while (formed == required) {

            int windowLength =
                    right - left + 1;

            if (windowLength < bestLength) {
                bestLength = windowLength;
                bestStart = left;
            }

            char leftChar =
                    s.charAt(left);

            if (need.containsKey(leftChar)) {

                window.put(
                        leftChar,
                        window.get(leftChar) - 1
                );

                if (window.get(leftChar)
                        < need.get(leftChar)) {
                    formed--;
                }
            }

            left++;
        }
    }

    return bestLength == Integer.MAX_VALUE
            ? ""
            : s.substring(
                    bestStart,
                    bestStart + bestLength
            );
}
```

---

## 10. Dry Run

```text
s = "ADOBECODEBANC"
t = "ABC"
```

Required:

```text
A -> 1
B -> 1
C -> 1
```

Initially:

```text
formed = 0
required = 3
```

Expand until:

```text
"ADOBEC"
```

contains:

```text
A
B
C
```

Therefore:

```text
formed = 3
```

Now shrink:

```text
"ADOBEC"
```

Remove `A`:

```text
"DOBEC"
```

No longer valid.

Continue expanding.

Eventually:

```text
"CODEBANC"
```

becomes valid.

Shrink:

```text
"ODEBANC"
```

still valid.

```text
"DEBANC"
```

still valid.

```text
"BANC"
```

still valid.

Remove `B`:

```text
"ANC"
```

invalid.

The shortest valid window found is:

```text
"BANC"
```

---

## 11. Edge Cases

### `t` is longer than `s`

Return:

```text
""
```

### No valid window

Return:

```text
""
```

### Duplicate required characters

Example:

```text
t = "AABC"
```

must require:

```text
A -> 2
B -> 1
C -> 1
```

### Characters not present in `t`

Ignore them for `formed`.

### Empty strings

Return empty result.

### Case sensitivity

By default:

```text
A != a
```

---

## 12. Complexity Analysis

Let:

```text
N = s.length()
M = number of distinct characters in t
```

Every character is processed a constant number of times.

Average HashMap operations are `O(1)`.

Therefore:

```text
Time:  O(N + M)
Space: O(M)
```

The window map can technically contain characters from `s` if implemented broadly, but the shown implementation only stores required characters.

---

## 13. Common Mistakes

### Mistake 1 — Tracking only distinct characters

For:

```text
t = "AABC"
```

you cannot simply check:

```text
A,B,C present
```

You need:

```text
A frequency >= 2
```

### Mistake 2 — Incrementing `formed` for every occurrence

Wrong:

```java
if (need.containsKey(c)) {
    formed++;
}
```

Correct:

```java
if (window.get(c).equals(need.get(c))) {
    formed++;
}
```

Only the transition to the required frequency should increase `formed`.

### Mistake 3 — Decrementing `formed` too early

When removing:

```text
window[c]--
```

decrement `formed` only if the frequency becomes less than the required frequency.

### Mistake 4 — Forgetting to shrink while valid

Use:

```java
while (formed == required)
```

not merely `if`.

### Mistake 5 — Confusing required types with required total characters

For:

```text
t = "AABC"
```

we have:

```text
required = 3
```

not `4`.

`required` means number of distinct required character types.

---

## 14. Interview Follow-Up Questions

1. Explain the `formed` variable.
2. Why is `required = need.size()`?
3. How do duplicate characters in `t` change the algorithm?
4. Can you implement it using an `int[128]`?
5. Can you optimize the HashMap version?
6. Find the longest instead of shortest required window.
7. Return the start/end indexes instead of the substring.
8. What if the pattern contains Unicode?
9. What if requirements can change dynamically?
10. Can you solve the permutation version using a fixed window?

---

## 15. Variations

- Minimum Window Substring
- Minimum window containing all characters
- Minimum window containing required frequencies
- Longest window containing all required characters
- Minimum window containing all required numbers
- Smallest range satisfying frequency constraints

---

# Frequency Map — Master Concepts

## At Most K

The invariant is:

```text
distinct <= K
```

Template:

```java
for (int right = 0; right < n; right++) {

    add(right);

    while (distinct > k) {
        remove(left);
        left++;
    }

    // valid
}
```

---

# Exactly K

The key transformation is:

```text
EXACTLY(K)
=
AT_MOST(K)
-
AT_MOST(K - 1)
```

This is one of the highest-value Sliding Window transformations for interviews.

---

# Required Frequency

Maintain:

```text
need
window
formed
required
```

Core logic:

```text
expand
   ↓
become valid
   ↓
record answer
   ↓
shrink
   ↓
become invalid
   ↓
expand again
```

---

# Why `right - left + 1` Matters

For counting "at most K" subarrays:

```text
left ........ right
```

Once the current window `[left, right]` is valid, all of these are valid:

```text
[left, right]
[left+1, right]
[left+2, right]
...
[right, right]
```

Therefore:

```text
number of valid subarrays ending at right
=
right - left + 1
```

This single observation solves a large class of counting problems.

---

# Frequency Map Decision Tree

```text
Does the problem mention frequency/count?
              |
             YES
              |
              v
      Is the condition
      "at most K"?
              |
             YES
              |
              v
        Frequency Map
        + shrink when
        distinct > K


      Is it "exactly K"?
              |
             YES
              |
              v
    atMost(K) - atMost(K-1)


      Is there a required
      pattern/frequency?
              |
             YES
              |
              v
     need + window + formed
```

---

# HashMap vs Frequency Array

## HashMap

Use when:

```text
values are arbitrary
characters are Unicode
keys are large integers
alphabet is unknown
```

Example:

```java
Map<Integer, Integer> freq = new HashMap<>();
```

## Frequency Array

Use when:

```text
alphabet/range is small and known
```

Example:

```java
int[] freq = new int[26];
```

This is usually faster and has predictable memory usage.

---

# Common Frequency-Map Mistakes

```text
[ ] Confusing frequency with distinct count
[ ] Forgetting to remove zero-frequency keys
[ ] Using if instead of while
[ ] Forgetting the outgoing element
[ ] Treating exactly K as directly equivalent to at most K
[ ] Using int for potentially huge subarray counts
[ ] Mishandling duplicate requirements
[ ] Incorrectly updating formed
[ ] Comparing only map size for required-frequency problems
```

---

# Interview Master Checklist

Before coding, ask:

```text
[ ] Is the problem contiguous?
[ ] Is the window variable?
[ ] Does validity depend on frequency?
[ ] Am I tracking frequency or distinct count?
[ ] Is the condition AT MOST K?
[ ] Is it EXACTLY K?
[ ] Is there a REQUIRED frequency?
[ ] What happens when an element enters?
[ ] What happens when an element leaves?
[ ] When does a key enter the map?
[ ] When should a key be removed?
[ ] Do I need a HashMap or fixed array?
[ ] Can the answer exceed int?
[ ] Can I use the atMost(K) - atMost(K-1) transformation?
[ ] For required frequencies, what exactly does "formed" mean?
```

---

# Practice Progression

## Beginner

1. Longest substring with at most K distinct characters
2. Longest subarray with at most K distinct integers
3. Count subarrays with at most K distinct integers

## Intermediate

4. Subarrays with exactly K distinct integers
5. Substrings with exactly K distinct characters
6. Count subarrays with exactly K odd numbers
7. Find all anagrams of a pattern

## Advanced

8. Minimum Window Substring
9. Minimum window with duplicate requirements
10. Count exactly-K windows using atMost transformation
11. Frequency Map + Deque
12. Frequency Map + multiple constraints
13. Unicode-aware frequency windows
14. Optimize HashMap implementations using primitive arrays where possible

---

# Final Mental Model

```text
FREQUENCY MAP SLIDING WINDOW
             |
     +-------+-------+
     |       |       |
     v       v       v
 AT MOST  EXACTLY  REQUIRED
     |       |       |
     v       v       v
distinct   atMost   need
   <= K     (K)     window
             -       formed
          atMost     required
           (K-1)
```

Master these three transformations:

```text
1. At Most K
   -> shrink when constraint is violated

2. Exactly K
   -> atMost(K) - atMost(K-1)

3. Required Frequency
   -> need + window + formed
```

Once these become automatic, a large class of:

```text
Substring
Subarray
Frequency
Distinct
Anagram
Permutation
Counting
Minimum Window
```

interview problems becomes much easier to recognize.
