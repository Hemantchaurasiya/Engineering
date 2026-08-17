# Sliding Window — Pattern 8: Advanced Hybrid

Advanced Sliding Window problems often combine the basic window technique with another algorithmic tool.

```text
8. Advanced Hybrid
│
├── Sliding Window + Prefix Sum
├── Sliding Window + HashMap
├── Sliding Window + Deque
└── Two Pointer Hybrid
```

The key interview skill is not memorizing a template.

It is recognizing:

```text
What information does the window need?
```

Then choose the supporting data structure or technique:

```text
Need cumulative sums?
    -> Prefix Sum

Need frequencies/counts?
    -> HashMap

Need max/min?
    -> Monotonic Deque

Need two independent constraints?
    -> Two Pointer Hybrid
```

---

# 1. Sliding Window + Prefix Sum

## 1. Problem Statement

Consider a positive-integer array.

Find the minimum length contiguous subarray whose sum is at least `target`.

Example:

```text
nums = [2,3,1,2,4,3]
target = 7
```

Answer:

```text
2
```

because:

```text
[4,3]
```

has sum:

```text
7
```

This is a classic problem where a Sliding Window can be used directly.

Prefix Sum is also useful for understanding and extending the problem.

---

## 2. Brute-Force Solution

Generate every subarray and calculate its sum.

A naive implementation:

```java
static int minSubArrayLenBruteForce(
        int target,
        int[] nums) {

    int answer = Integer.MAX_VALUE;

    for (int i = 0;
         i < nums.length;
         i++) {

        int sum = 0;

        for (int j = i;
             j < nums.length;
             j++) {

            sum += nums[j];

            if (sum >= target) {

                answer = Math.min(
                        answer,
                        j - i + 1
                );

                break;
            }
        }
    }

    return answer == Integer.MAX_VALUE
            ? 0
            : answer;
}
```

Complexity:

```text
O(N²)
```

---

## 3. Why Brute Force Is Slow

There can be:

```text
O(N²)
```

candidate subarrays.

For large arrays this becomes expensive.

Because all numbers are positive, we have a crucial property:

```text
expanding right -> sum never decreases
shrinking left  -> sum never increases
```

This monotonic behavior enables Sliding Window.

---

## 4. Pattern Identification

Look for:

```text
positive numbers
contiguous subarray
sum >= target
longest/shortest valid subarray
```

The most important condition is:

```text
ALL VALUES ARE POSITIVE
```

Without that property, the standard Sliding Window sum approach may fail.

---

## 5. Sliding Window Intuition

Maintain:

```text
[left ... right]
```

and:

```text
sum
```

Expand `right`.

When:

```text
sum >= target
```

the window is valid.

Now try to make it smaller:

```text
while sum >= target:
    record answer
    remove nums[left]
    left++
```

This produces the shortest valid window ending at the current `right`.

---

## 6. Window Invariant

After the shrinking phase finishes:

```text
sum < target
```

Before shrinking:

```text
sum >= target
```

The algorithm repeatedly converts:

```text
valid -> shrink -> smallest locally valid window
```

---

## 7. Pointer Movement

```text
right -> expands the window

left -> shrinks the window while valid
```

Template:

```java
for (int right = 0; right < nums.length; right++) {

    sum += nums[right];

    while (sum >= target) {

        answer = Math.min(
                answer,
                right - left + 1
        );

        sum -= nums[left];
        left++;
    }
}
```

---

## 8. Data Structure Used

Only:

```java
long sum;
int left;
int answer;
```

No additional data structure is required.

---

## 9. Java Implementation

```java
static int minSubArrayLen(
        int target,
        int[] nums) {

    if (nums == null
            || nums.length == 0
            || target <= 0) {
        return 0;
    }

    int left = 0;
    int answer = Integer.MAX_VALUE;

    long sum = 0;

    for (int right = 0;
         right < nums.length;
         right++) {

        sum += nums[right];

        while (sum >= target) {

            answer = Math.min(
                    answer,
                    right - left + 1
            );

            sum -= nums[left];
            left++;
        }
    }

    return answer == Integer.MAX_VALUE
            ? 0
            : answer;
}
```

---

## 10. Dry Run

```text
nums = [2,3,1,2,4,3]
target = 7
```

Start:

```text
left = 0
sum = 0
```

Add `2`:

```text
sum = 2
```

Add `3`:

```text
sum = 5
```

Add `1`:

```text
sum = 6
```

Add `2`:

```text
sum = 8
```

Valid.

Window:

```text
[2,3,1,2]
```

Length:

```text
4
```

Remove `2`:

```text
sum = 6
```

Invalid.

Continue.

Eventually:

```text
[4,3]
```

sum:

```text
7
```

length:

```text
2
```

Answer:

```text
2
```

---

## 11. Edge Cases

- empty array;
- target larger than total sum;
- one element;
- target exactly equal to one element;
- all values positive;
- very large sums;
- target <= 0;
- target equals total sum.

---

## 12. Complexity Analysis

```text
Time:  O(N)
Space: O(1)
```

Every element enters once and leaves once.

---

## 13. Common Mistakes

- Applying this algorithm when negative numbers exist;
- using `if` instead of `while`;
- forgetting to subtract `nums[left]`;
- updating the answer after shrinking too far;
- using `int` for potentially large sums.

---

## 14. Interview Follow-Up Questions

1. Why must numbers be positive?
2. What breaks if negative numbers are allowed?
3. Can prefix sums solve it?
4. What is the complexity of prefix-sum + binary search?
5. Can you return the actual subarray?
6. What if we need the longest sum >= target?
7. What if the condition is sum <= target?
8. What if values can be zero?

---

## 15. Variations

- Minimum Size Subarray Sum;
- longest positive-sum window;
- maximum window under sum constraint;
- shortest window with sum > target;
- exactly target for positive arrays.

---

# Prefix Sum Connection

Prefix sum:

```text
prefix[i] =
nums[0] + nums[1] + ... + nums[i-1]
```

Subarray sum:

```text
sum(i, j)
=
prefix[j + 1] - prefix[i]
```

Example:

```text
nums = [2,3,1,2]
```

Prefix:

```text
[0,2,5,6,8]
```

Sum from index `1` to `3`:

```text
prefix[4] - prefix[1]
= 8 - 2
= 6
```

Prefix Sum is especially useful when:

```text
negative values exist
```

because ordinary positive-number Sliding Window monotonicity may disappear.

---

# Important Distinction

Do not automatically combine Prefix Sum with Sliding Window.

Ask:

```text
Are all values positive?
```

If yes:

```text
Sliding Window
```

is usually simpler.

If negative values exist:

```text
Prefix Sum + HashMap
```

or another technique may be required.

---

# 2. Sliding Window + HashMap

## 1. Problem Statement

Find the longest substring containing at most `K` distinct characters.

Example:

```text
s = "eceba"
K = 2
```

Answer:

```text
3
```

because:

```text
"ece"
```

contains:

```text
e
c
```

only two distinct characters.

---

## 2. Brute-Force Solution

Generate every substring and use a Set/Map to count distinct characters.

```java
static int longestAtMostKDistinctBruteForce(
        String s,
        int k) {

    int answer = 0;

    for (int i = 0;
         i < s.length();
         i++) {

        Set<Character> set =
                new HashSet<>();

        for (int j = i;
             j < s.length();
             j++) {

            set.add(s.charAt(j));

            if (set.size() > k) {
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

Worst-case:

```text
O(N²)
```

---

## 3. Why Brute Force Is Slow

Every starting position may examine many ending positions.

The same characters are repeatedly processed.

Sliding Window lets us maintain the frequency state incrementally.

---

## 4. Pattern Identification

Strong signals:

```text
substring
subarray
at most K distinct
frequency
characters
longest valid
```

Think:

```text
Variable Sliding Window
+
HashMap
```

---

## 5. Sliding Window Intuition

Maintain:

```text
[left ... right]
```

and a frequency map:

```text
character -> frequency
```

Expand `right`.

If distinct count becomes:

```text
> K
```

shrink from the left.

When removing a character causes its frequency to become zero:

```text
remove it from map
```

Then:

```text
map.size() <= K
```

is restored.

---

## 6. Window Invariant

```text
number of distinct characters <= K
```

Formally:

```java
frequencyMap.size() <= k
```

---

## 7. Pointer Movement

Expand:

```text
right++
```

When invalid:

```text
while map.size() > K:
    remove s[left]
    left++
```

---

## 8. Data Structure Used

```java
Map<Character, Integer>
```

Implementation:

```java
HashMap<Character, Integer>
```

---

## 9. Java Implementation

```java
static int longestAtMostKDistinct(
        String s,
        int k) {

    if (s == null
            || s.isEmpty()
            || k <= 0) {
        return 0;
    }

    Map<Character, Integer> frequency =
            new HashMap<>();

    int left = 0;
    int answer = 0;

    for (int right = 0;
         right < s.length();
         right++) {

        char current =
                s.charAt(right);

        frequency.merge(
                current,
                1,
                Integer::sum
        );

        while (frequency.size() > k) {

            char leftChar =
                    s.charAt(left);

            int count =
                    frequency.get(leftChar);

            if (count == 1) {
                frequency.remove(leftChar);
            } else {
                frequency.put(
                        leftChar,
                        count - 1
                );
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
s = "eceba"
K = 2
```

Start:

```text
Window: e
Map: {e=1}
```

Add `c`:

```text
Window: ec
Map: {e=1,c=1}
```

Add `e`:

```text
Window: ece
Map: {e=2,c=1}
```

Distinct:

```text
2
```

Valid.

Add `b`:

```text
Map = {e=2,c=1,b=1}
```

Distinct:

```text
3
```

Invalid.

Remove from left:

```text
remove e
```

Now:

```text
Map = {e=1,c=1,b=1}
```

Still 3 distinct.

Remove `c`:

```text
Map = {e=1,b=1}
```

Valid.

Continue.

Answer:

```text
3
```

---

## 11. Edge Cases

- empty string;
- `K = 0`;
- `K = 1`;
- `K >= number of distinct characters`;
- all same characters;
- all unique characters;
- Unicode considerations.

---

## 12. Complexity Analysis

For a HashMap:

```text
Time:  O(N) average
Space: O(K)
```

More precisely, the map contains at most `K` distinct characters.

---

## 13. Common Mistakes

- tracking only distinct values without frequencies;
- forgetting to remove a key when frequency becomes zero;
- using `if` instead of `while`;
- confusing at most K distinct with exactly K;
- using a Set when deletion counts are required.

---

## 14. Interview Follow-Up Questions

1. Why do we need frequencies?
2. Why can't we use only a Set?
3. What happens when frequency becomes zero?
4. How do you solve exactly K distinct?
5. How do you count instead of find longest?
6. Can an array replace HashMap for lowercase English letters?
7. What about Unicode?
8. Can this pattern solve minimum window problems?

---

## 15. Variations

- At most K distinct;
- exactly K distinct;
- longest substring without repeating characters;
- fruit into baskets;
- minimum window containing required characters;
- count subarrays with K distinct values.

---

# 3. Sliding Window + Deque

## 1. Problem Statement

Find the longest subarray where:

```text
max(window) - min(window) <= limit
```

This is the classic hybrid:

```text
Sliding Window
+
Monotonic Deque
```

---

## 2. Brute-Force Solution

Generate subarrays and maintain max/min.

```java
static int longestRangeBruteForce(
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

            if ((long) max - min <= limit) {

                answer = Math.max(
                        answer,
                        j - i + 1
                );

            } else {
                break;
            }
        }
    }

    return answer;
}
```

---

## 3. Why Brute Force Is Slow

Worst case:

```text
O(N²)
```

because there can be quadratic candidate windows.

---

## 4. Pattern Identification

Look for:

```text
longest subarray
maximum
minimum
difference
range
constraint
```

This is a direct signal for:

```text
maxDeque + minDeque
```

---

## 5. Sliding Window Intuition

Maintain:

```text
maxDeque
minDeque
```

The window is valid when:

```text
max - min <= limit
```

If invalid:

```text
max - min > limit
```

move `left`.

---

## 6. Window Invariant

```text
max(window) - min(window) <= limit
```

and:

```text
maxDeque is decreasing
minDeque is increasing
```

---

## 7. Pointer Movement

```text
right -> expand
left  -> shrink while invalid
```

---

## 8. Data Structure Used

```java
Deque<Integer> maxDeque;
Deque<Integer> minDeque;
```

---

## 9. Java Implementation

```java
static int longestSubarrayWithRangeLimit(
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
nums = [8,2,4,7]
limit = 4
```

Start:

```text
[8]
range = 0
```

Add `2`:

```text
[8,2]
max = 8
min = 2
range = 6
```

Invalid.

Shrink:

```text
[2]
range = 0
```

Add `4`:

```text
[2,4]
range = 2
```

Add `7`:

```text
[2,4,7]
range = 5
```

Invalid.

Remove `2`:

```text
[4,7]
range = 3
```

Answer:

```text
2
```

---

## 11. Edge Cases

- empty array;
- one element;
- limit = 0;
- all equal;
- duplicates;
- increasing sequence;
- decreasing sequence;
- negative numbers;
- integer overflow in `max-min`.

---

## 12. Complexity Analysis

```text
Time:  O(N)
Space: O(N) worst case
```

---

## 13. Common Mistakes

- maintaining only maximum;
- maintaining only minimum;
- wrong deque ordering;
- not removing expired indexes;
- shrinking only once;
- integer overflow.

---

## 14. Interview Follow-Up Questions

1. Why two deques?
2. Why are they monotonic?
3. Why is the algorithm O(N)?
4. Can TreeMap solve it?
5. Can PriorityQueue solve it?
6. What is the difference between deque and heap solutions?
7. Can we count valid windows?
8. Can we return the actual window?

---

## 15. Variations

- Longest subarray with max-min <= K;
- count valid subarrays;
- shortest valid range;
- bounded difference substring;
- max-min constraints with additional conditions.

---

# 4. Two Pointer Hybrid

## 1. Problem Statement

Two Pointer Hybrid means combining Sliding Window with another pointer-based constraint.

A classic example is:

> Find the longest substring after replacing at most `K` characters so that all characters in the substring become identical.

Example:

```text
s = "AABABBA"
K = 1
```

Answer:

```text
4
```

because:

```text
"AABA"
```

can become:

```text
AAAA
```

with one replacement.

---

## 2. Brute-Force Solution

For every substring:

1. count character frequencies;
2. find the most frequent character;
3. calculate replacements needed.

```java
static int characterReplacementBruteForce(
        String s,
        int k) {

    int answer = 0;

    for (int i = 0;
         i < s.length();
         i++) {

        int[] frequency =
                new int[26];

        int maxFrequency = 0;

        for (int j = i;
             j < s.length();
             j++) {

            int index =
                    s.charAt(j) - 'A';

            frequency[index]++;

            maxFrequency = Math.max(
                    maxFrequency,
                    frequency[index]
            );

            int length =
                    j - i + 1;

            int replacements =
                    length - maxFrequency;

            if (replacements <= k) {

                answer = Math.max(
                        answer,
                        length
                );
            }
        }
    }

    return answer;
}
```

---

## 3. Why Brute Force Is Slow

For every possible starting point we inspect many endings.

Worst case:

```text
O(N²)
```

Sliding Window maintains frequency information incrementally.

---

## 4. Pattern Identification

Look for:

```text
longest substring
at most K replacements
at most K changes
make substring satisfy a condition
frequency of most common element
```

The key equation is:

```text
windowLength - maxFrequency <= K
```

Why?

If the most frequent character appears:

```text
maxFrequency
```

times, all remaining characters must be replaced.

---

## 5. Sliding Window Intuition

Maintain:

```text
[left ... right]
```

and:

```text
frequency[]
```

Also maintain:

```text
maxFrequency
```

The window is valid if:

```text
windowLength - maxFrequency <= K
```

If invalid:

```text
windowLength - maxFrequency > K
```

move `left`.

---

## 6. Window Invariant

Valid window:

```text
(right - left + 1) - maxFrequency <= K
```

Equivalent:

```text
number of replacements needed <= K
```

---

## 7. Pointer Movement

Expand:

```text
right++
```

Update frequency.

Update:

```text
maxFrequency
```

Then:

```text
while windowLength - maxFrequency > K:
    remove s[left]
    left++
```

---

## 8. Data Structure Used

For uppercase English letters:

```java
int[26]
```

This is faster and simpler than a HashMap.

---

## 9. Java Implementation

```java
static int characterReplacement(
        String s,
        int k) {

    if (s == null
            || s.isEmpty()
            || k < 0) {
        return 0;
    }

    int[] frequency =
            new int[26];

    int left = 0;
    int maxFrequency = 0;
    int answer = 0;

    for (int right = 0;
         right < s.length();
         right++) {

        int index =
                s.charAt(right) - 'A';

        frequency[index]++;

        maxFrequency = Math.max(
                maxFrequency,
                frequency[index]
        );

        int windowLength =
                right - left + 1;

        while (windowLength - maxFrequency > k) {

            int leftIndex =
                    s.charAt(left) - 'A';

            frequency[leftIndex]--;

            left++;

            windowLength =
                    right - left + 1;
        }

        answer = Math.max(
                answer,
                windowLength
        );
    }

    return answer;
}
```

---

## 10. Dry Run

```text
s = "AABABBA"
K = 1
```

Start:

```text
Window: A
maxFrequency = 1
length - maxFrequency = 0
```

Add A:

```text
AA
maxFrequency = 2
replacements = 0
```

Add B:

```text
AAB
maxFrequency = 2
length = 3
replacements = 1
```

Valid.

Add A:

```text
AABA
maxFrequency = 3
length = 4
replacements = 1
```

Valid.

Add B:

```text
AABAB
maxFrequency = 3
length = 5
replacements = 2
```

Invalid.

Shrink from left.

Eventually:

```text
ABAB
```

has:

```text
length = 4
maxFrequency = 2
replacements = 2
```

Still invalid for `K=1`, so continue shrinking.

The maximum valid length remains:

```text
4
```

---

## 11. Edge Cases

- empty string;
- `K = 0`;
- `K >= string length`;
- all characters identical;
- all characters different;
- lowercase input;
- Unicode input.

---

## 12. Complexity Analysis

For fixed uppercase alphabet:

```text
Time:  O(N)
Space: O(1)
```

If arbitrary Unicode characters are supported:

```text
Space: O(U)
```

where `U` is the number of distinct characters.

---

## 13. Common Mistakes

### Mistake 1

Using:

```text
windowLength - frequency[right]
```

instead of:

```text
windowLength - maxFrequency
```

---

### Mistake 2

Recomputing the maximum frequency every time.

For fixed alphabet, maintain:

```text
maxFrequency
```

incrementally.

---

### Mistake 3

Confusing:

```text
K replacements
```

with:

```text
K distinct characters
```

They are different constraints.

---

### Mistake 4

Using a Set instead of frequency counts.

---

## 14. Interview Follow-Up Questions

1. Why is `length - maxFrequency` the number of replacements?
2. Why can `maxFrequency` be maintained incrementally?
3. Why don't we always decrease `maxFrequency` when moving left?
4. Is the algorithm still correct if `maxFrequency` becomes stale?
5. Can we use HashMap?
6. What changes for Unicode?
7. What if replacements have different costs?
8. What if we need the actual substring?

---

## 15. Variations

- Longest repeating character replacement;
- K substitutions;
- K deletions;
- longest substring with K changes;
- longest sequence with one dominant value;
- array version instead of string.

---

# Hybrid Pattern Recognition

The biggest goal of this section is to learn how to combine techniques.

## Decision Table

| Requirement | Supporting Technique |
|---|---|
| Sum with positive values | Running sum / Sliding Window |
| Prefix-based range sum | Prefix Sum |
| Character/value frequency | HashMap / Frequency Array |
| Maximum in window | Max Deque |
| Minimum in window | Min Deque |
| Max-min constraint | Two Deques |
| Replacement budget | Frequency + Two Pointers |
| Count valid windows | `right-left+1` |
| Exactly K | AtMost difference |

---

# Hybrid Selection Framework

When reading a new problem, ask:

```text
1. Is it contiguous?
        |
       YES
        |
2. Can I expand right and shrink left?
        |
       YES
        |
3. What state must the window maintain?
        |
        +-------------------------------+
        |               |               |
       SUM          FREQUENCY          MAX/MIN
        |               |               |
        v               v               v
     Running         HashMap          Deque
      Sum           / Array
        |
        v
   Positive values?
        |
     YES -> Sliding Window
     NO  -> Prefix Sum / HashMap
```

---

# Advanced Connection 1 — Sliding Window + Prefix Sum

The important lesson is:

```text
Prefix Sum does not automatically mean Sliding Window.
```

Use ordinary Sliding Window when the condition is monotonic.

For positive numbers:

```text
sum increases when right expands
sum decreases when left moves
```

For negative numbers:

```text
sum may increase OR decrease
```

Therefore the monotonicity disappears.

---

# Advanced Connection 2 — Sliding Window + HashMap

Frequency-based problems usually have this shape:

```text
window
  |
  v
frequency state
  |
  v
constraint violated?
  |
  +---- NO ---> expand
  |
 YES
  |
  v
shrink
```

Examples:

```text
At most K distinct
Exactly K distinct
Anagrams
Permutation
Minimum Window
Character replacement
```

---

# Advanced Connection 3 — Sliding Window + Deque

Use this when the window needs:

```text
max
min
max-min
```

The deque maintains only candidates.

```text
Window
  |
  +---- Max Deque
  |
  +---- Min Deque
```

---

# Advanced Connection 4 — Two Pointer Hybrid

Two pointers become especially powerful when:

```text
right expands
left restores validity
```

and another state is maintained:

```text
frequency
sum
number of zeros
number of changes
max frequency
max/min
```

This is the core architecture behind many hard Sliding Window interview questions.

---

# Master Java Templates

## Template 1 — Positive Sum

```java
int left = 0;
long sum = 0;

for (int right = 0;
     right < nums.length;
     right++) {

    sum += nums[right];

    while (/* valid */) {

        // update answer

        sum -= nums[left];
        left++;
    }
}
```

---

## Template 2 — Frequency Map

```java
Map<Character, Integer> map =
        new HashMap<>();

int left = 0;

for (int right = 0;
     right < s.length();
     right++) {

    char c = s.charAt(right);

    map.merge(
            c,
            1,
            Integer::sum
    );

    while (/* invalid */) {

        char leftChar =
                s.charAt(left++);

        int count =
                map.get(leftChar);

        if (count == 1) {
            map.remove(leftChar);
        } else {
            map.put(
                    leftChar,
                    count - 1
            );
        }
    }
}
```

---

## Template 3 — Maximum Deque

```java
Deque<Integer> deque =
        new ArrayDeque<>();

while (!deque.isEmpty()
        && nums[deque.peekLast()]
           <= nums[right]) {

    deque.pollLast();
}

deque.offerLast(right);
```

---

## Template 4 — Minimum Deque

```java
Deque<Integer> deque =
        new ArrayDeque<>();

while (!deque.isEmpty()
        && nums[deque.peekLast()]
           >= nums[right]) {

    deque.pollLast();
}

deque.offerLast(right);
```

---

## Template 5 — Count Valid Windows

```java
while (invalid) {
    // remove left
    left++;
}

answer += right - left + 1L;
```

---

# Common Advanced Mistakes

```text
[ ] Choosing Sliding Window without checking monotonicity
[ ] Using HashMap when a frequency array is enough
[ ] Using a Set when frequencies are required
[ ] Using one deque for both max and min
[ ] Storing values instead of indexes in monotonic deque
[ ] Forgetting expired deque indexes
[ ] Recomputing max/min unnecessarily
[ ] Applying positive-sum logic to negative arrays
[ ] Using if instead of while for invalid windows
[ ] Confusing longest with counting
[ ] Forgetting exactly-K = AtMost(K) - AtMost(K-1)
[ ] Ignoring integer overflow
```

---

# Interview Master Checklist

Before writing code:

```text
[ ] Is the answer about a contiguous subarray/substring?
[ ] Is there a left/right boundary?
[ ] What makes the window valid?
[ ] What makes it invalid?
[ ] Is validity monotonic as the window grows/shrinks?
[ ] What state must be maintained?
[ ] Sum?
[ ] Frequency?
[ ] Distinct count?
[ ] Max?
[ ] Min?
[ ] Replacement count?
[ ] Zero count?
[ ] Do I need Prefix Sum?
[ ] Do I need HashMap?
[ ] Do I need a Deque?
[ ] Do I need two Deques?
[ ] Is the answer longest?
[ ] Is the answer shortest?
[ ] Is the answer a count?
[ ] Can I use right-left+1?
[ ] Do negative values break monotonicity?
[ ] Can the answer overflow int?
```

---

# Final Mental Model

```text
                ADVANCED SLIDING WINDOW
                         |
          +--------------+--------------+
          |              |              |
         SUM         FREQUENCY       MAX/MIN
          |              |              |
          v              v              v
    Positive?          HashMap        Deque
          |              |              |
       YES|              |              |
          v              v              v
     Sliding       Variable Window   Monotonic
      Window             |            Window
          |              |              |
          +--------------+--------------+
                         |
                         v
                  TWO POINTERS
                         |
                         v
              Longest / Shortest / Count
```

The central question is:

> **What information must I maintain while the window moves?**

Then choose the supporting technique:

```text
SUM        -> Running Sum / Prefix Sum
FREQUENCY  -> HashMap / Frequency Array
MAX/MIN    -> Monotonic Deque
COUNT      -> right-left+1
EXACTLY K  -> AtMost(K)-AtMost(K-1)
```

Once this becomes automatic, advanced Sliding Window problems become combinations of a small number of reusable ideas rather than isolated problems.
