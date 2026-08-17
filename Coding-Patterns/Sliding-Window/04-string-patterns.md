# Sliding Window — Pattern 4: String Patterns

String Sliding Window problems are among the most common questions in Java interviews.

```text
String Patterns
│
├── Unique Characters
├── Anagrams
├── Permutations
├── Character Replacement
└── Minimum Window
```

The main difference between these problems is not the two-pointer mechanics.

The important part is identifying **what the current window must satisfy**.

```text
Unique Characters
    -> no duplicate characters

Anagrams
    -> same character frequencies

Permutations
    -> fixed-size frequency match

Character Replacement
    -> windowLength - maxFrequency <= K

Minimum Window
    -> all required frequencies satisfied
```

---

# 1. Unique Characters

## 1. Problem Statement

Find the length of the **longest substring without repeating characters**.

### Example

```text
s = "abcabcbb"
```

Valid substrings include:

```text
"abc"
"bca"
"cab"
```

The longest length is:

```text
3
```

---

## 2. Brute-Force Solution

Generate every substring and check whether it contains duplicate characters.

```java
static int longestUniqueBruteForce(String s) {

    int maxLength = 0;

    for (int i = 0; i < s.length(); i++) {

        Set<Character> set = new HashSet<>();

        for (int j = i; j < s.length(); j++) {

            char c = s.charAt(j);

            if (set.contains(c)) {
                break;
            }

            set.add(c);

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

substrings.

Although the duplicate check is incremental, the algorithm still explores many possible windows.

Sliding Window allows the left boundary to move forward instead of restarting from every position.

---

## 4. Pattern Identification

Look for:

- longest substring without repeating characters;
- longest unique substring;
- no duplicate characters;
- distinct characters only;
- longest window where every character appears once.

The strongest signal is:

```text
LONGEST + NO DUPLICATES
```

---

## 5. Sliding Window Intuition

Maintain a window:

```text
[left ... right]
```

where every character is unique.

Expand `right`.

If the new character already exists:

```text
duplicate -> window invalid
```

Move `left` until the duplicate is removed.

Then continue.

---

## 6. Window Invariant

The invariant is:

```text
Every character occurs at most once in the current window.
```

Equivalent:

```text
frequency[c] <= 1
```

for every character.

---

## 7. Pointer Movement

```java
for (int right = 0; right < s.length(); right++) {

    add(s.charAt(right));

    while (duplicateExists()) {
        remove(s.charAt(left));
        left++;
    }

    updateAnswer();
}
```

---

## 8. Data Structure Used

Possible approaches:

```text
HashSet<Character>
HashMap<Character, Integer>
int[128]
lastSeen[128]
```

For interviews, a `HashSet` is easiest to explain.

For optimized implementations, storing the last index can avoid repeated left-pointer removals.

---

## 9. Java Implementation

### HashSet version

```java
static int longestUniqueSubstring(String s) {

    if (s == null || s.isEmpty()) {
        return 0;
    }

    Set<Character> set = new HashSet<>();

    int left = 0;
    int maxLength = 0;

    for (int right = 0; right < s.length(); right++) {

        char c = s.charAt(right);

        while (set.contains(c)) {
            set.remove(s.charAt(left));
            left++;
        }

        set.add(c);

        maxLength = Math.max(
                maxLength,
                right - left + 1
        );
    }

    return maxLength;
}
```

### Last-seen optimization

```java
static int longestUniqueSubstringOptimized(String s) {

    int[] lastSeen = new int[128];
    Arrays.fill(lastSeen, -1);

    int left = 0;
    int maxLength = 0;

    for (int right = 0; right < s.length(); right++) {

        char c = s.charAt(right);

        if (lastSeen[c] >= left) {
            left = lastSeen[c] + 1;
        }

        lastSeen[c] = right;

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
s = "abcabcbb"
```

```text
right=0 -> a -> "a"   -> 1
right=1 -> b -> "ab"  -> 2
right=2 -> c -> "abc" -> 3
right=3 -> a -> duplicate
```

Move `left` until previous `a` is removed:

```text
"bca"
```

Continue:

```text
"cab" -> 3
```

Final answer:

```text
3
```

---

## 11. Edge Cases

- empty string;
- one character;
- all characters identical;
- all characters unique;
- duplicate at the beginning;
- duplicate immediately after another duplicate;
- Unicode characters.

---

## 12. Complexity Analysis

HashSet version:

```text
Time:  O(N)
Space: O(min(N, alphabet size))
```

Last-seen version:

```text
Time:  O(N)
Space: O(alphabet size)
```

---

## 13. Common Mistakes

- Resetting the whole set when a duplicate appears;
- moving `left` backward;
- forgetting `left = max(left, lastSeen[c] + 1)`;
- using character count instead of uniqueness;
- assuming ASCII when Unicode is required.

---

## 14. Interview Follow-Up Questions

1. Return the actual substring.
2. Return its start/end indexes.
3. Solve using `HashSet`.
4. Solve using `HashMap`.
5. Optimize using last-seen indexes.
6. What changes for Unicode?
7. What if you need at most K duplicates?

---

## 15. Variations

- Longest substring without repeating characters;
- longest substring with at most K duplicate occurrences;
- longest substring with unique characters;
- longest subarray with distinct integers.

---

# 2. Anagrams

## 1. Problem Statement

Determine whether two strings are anagrams.

Or, in Sliding Window form:

> Find all substrings of `s` that are anagrams of pattern `p`.

Example:

```text
s = "cbaebabacd"
p = "abc"
```

Anagrams occur at:

```text
"cba" -> index 0
"bac" -> index 6
```

Answer:

```text
[0, 6]
```

---

## 2. Brute-Force Solution

Generate every substring of length `p.length()` and compare character frequencies.

```java
static List<Integer> findAnagramsBruteForce(
        String s,
        String p) {

    List<Integer> result = new ArrayList<>();

    if (s.length() < p.length()) {
        return result;
    }

    int windowSize = p.length();

    for (int i = 0;
         i <= s.length() - windowSize;
         i++) {

        String window =
                s.substring(i, i + windowSize);

        char[] a = window.toCharArray();
        char[] b = p.toCharArray();

        Arrays.sort(a);
        Arrays.sort(b);

        if (Arrays.equals(a, b)) {
            result.add(i);
        }
    }

    return result;
}
```

---

## 3. Why Brute Force Is Slow

If we sort every window:

```text
O(N * K log K)
```

where:

```text
K = pattern length
```

We can do much better because consecutive windows overlap heavily.

---

## 4. Pattern Identification

Look for:

- find all anagrams;
- find permutations of a pattern;
- substring has same character frequencies;
- all occurrences of a rearranged pattern;
- permutation in string.

The key signal is:

```text
FIXED WINDOW SIZE + FREQUENCY MATCH
```

---

## 5. Sliding Window Intuition

An anagram must have:

```text
same length
+
same character frequencies
```

Therefore the window size is fixed:

```text
windowSize = p.length()
```

Maintain the frequency of the current window.

When:

```text
window frequency == pattern frequency
```

we found an anagram.

---

## 6. Window Invariant

The current window always has:

```text
length == pattern.length()
```

The target condition is:

```text
windowFrequency == patternFrequency
```

---

## 7. Pointer Movement

Expand `right`.

When window size becomes larger than pattern length:

```java
remove(s.charAt(left));
left++;
```

Then compare frequencies.

---

## 8. Data Structure Used

For lowercase English letters:

```java
int[26]
```

This is preferred because the alphabet is known and small.

---

## 9. Java Implementation

```java
static List<Integer> findAnagrams(
        String s,
        String p) {

    List<Integer> result = new ArrayList<>();

    if (s == null || p == null
            || p.length() > s.length()) {
        return result;
    }

    int[] need = new int[26];
    int[] window = new int[26];

    for (char c : p.toCharArray()) {
        need[c - 'a']++;
    }

    int left = 0;

    for (int right = 0;
         right < s.length();
         right++) {

        window[s.charAt(right) - 'a']++;

        if (right - left + 1 > p.length()) {
            window[s.charAt(left) - 'a']--;
            left++;
        }

        if (right - left + 1 == p.length()
                && Arrays.equals(need, window)) {

            result.add(left);
        }
    }

    return result;
}
```

---

## 10. Dry Run

```text
s = "cbaebabacd"
p = "abc"
```

Pattern:

```text
a=1
b=1
c=1
```

First window:

```text
"cba"
```

Frequencies match.

Add `e`:

```text
"cbae"
```

Window too large.

Remove `c`:

```text
"bae"
```

Continue.

Eventually:

```text
"bac"
```

matches.

Indexes:

```text
0, 6
```

---

## 11. Edge Cases

- pattern longer than source;
- empty pattern;
- repeated characters in pattern;
- all characters identical;
- no anagrams;
- entire source is one window.

---

## 12. Complexity Analysis

With `int[26]` and fixed alphabet comparison:

```text
Time:  O(N)
Space: O(1)
```

If the alphabet is large and a HashMap is used:

```text
Time:  O(N)
Space: O(K)
```

---

## 13. Common Mistakes

- Using variable window size;
- forgetting the outgoing character;
- comparing only distinct counts;
- ignoring duplicate frequencies;
- sorting every window.

---

## 14. Interview Follow-Up Questions

1. Find all anagram starting indexes.
2. Determine whether any permutation exists.
3. Count anagram occurrences.
4. Solve with HashMap.
5. Solve with frequency arrays.
6. How do duplicates affect the algorithm?
7. Can you avoid `Arrays.equals()`?

---

## 15. Variations

- Find All Anagrams in a String;
- Permutation in String;
- Count anagram occurrences;
- Find anagram groups;
- Minimum window that is an anagram.

---

# 3. Permutations

## 1. Problem Statement

Determine whether `s2` contains a permutation of `s1`.

Example:

```text
s1 = "ab"
s2 = "eidbaooo"
```

The substring:

```text
"ba"
```

is a permutation of `"ab"`.

Answer:

```text
true
```

---

## 2. Brute-Force Solution

Generate every substring of length `s1.length()` and compare frequencies.

This is essentially the same underlying brute-force idea as anagram search.

---

## 3. Why Brute Force Is Slow

There can be:

```text
O(N)
```

candidate windows.

Checking or sorting each window can make the solution:

```text
O(N * K)
```

or:

```text
O(N * K log K)
```

depending on implementation.

---

## 4. Pattern Identification

Look for:

```text
contains permutation
contains rearrangement
permutation of pattern
same characters with same frequencies
```

The key insight:

> A permutation must have exactly the same length and frequency distribution as the pattern.

---

## 5. Sliding Window Intuition

If:

```text
s1.length() = K
```

then every candidate in `s2` must have length:

```text
K
```

Therefore use a **fixed-size Sliding Window**.

Maintain:

```text
need frequencies
window frequencies
```

When they match:

```text
permutation exists
```

---

## 6. Window Invariant

```text
window length == s1.length()
```

Candidate validity:

```text
window frequency == need frequency
```

---

## 7. Pointer Movement

```java
for (int right = 0; right < s2.length(); right++) {

    add(s2.charAt(right));

    if (windowSize > k) {
        remove(s2.charAt(left));
        left++;
    }

    if (windowSize == k && frequenciesMatch()) {
        return true;
    }
}
```

---

## 8. Data Structure Used

For lowercase English:

```java
int[26]
```

---

## 9. Java Implementation

```java
static boolean checkInclusion(
        String s1,
        String s2) {

    if (s1 == null || s2 == null
            || s1.length() > s2.length()) {
        return false;
    }

    int[] need = new int[26];
    int[] window = new int[26];

    for (char c : s1.toCharArray()) {
        need[c - 'a']++;
    }

    int left = 0;

    for (int right = 0;
         right < s2.length();
         right++) {

        window[s2.charAt(right) - 'a']++;

        if (right - left + 1 > s1.length()) {
            window[s2.charAt(left) - 'a']--;
            left++;
        }

        if (right - left + 1 == s1.length()
                && Arrays.equals(need, window)) {
            return true;
        }
    }

    return false;
}
```

---

## 10. Dry Run

```text
s1 = "ab"
s2 = "eidbaooo"
```

Windows of length 2:

```text
ei -> no
id -> no
db -> no
ba -> yes
```

Therefore:

```text
true
```

---

## 11. Edge Cases

- pattern longer than source;
- same strings;
- repeated characters;
- empty pattern;
- no permutation;
- one-character pattern.

---

## 12. Complexity Analysis

For a fixed 26-character alphabet:

```text
Time:  O(N)
Space: O(1)
```

---

## 13. Common Mistakes

- Using variable window;
- forgetting fixed size;
- comparing only set membership;
- ignoring duplicate frequency;
- sorting every candidate.

---

## 14. Interview Follow-Up Questions

1. Return the matching permutation instead of boolean.
2. Return its starting index.
3. Find all permutation positions.
4. Count permutations.
5. Support Unicode.
6. Avoid comparing all 26 positions every time.

---

## 15. Variations

- Permutation in String;
- Find all anagrams;
- Count permutation matches;
- Return first permutation index;
- Longest permutation-like window under constraints.

---

# 4. Character Replacement

## 1. Problem Statement

Given a string `s` and integer `k`, replace at most `k` characters so that the resulting substring contains only one repeated character.

Find the maximum possible length.

Example:

```text
s = "AABABBA"
k = 1
```

Answer:

```text
4
```

For example:

```text
"AABA"
```

Replace `B` with `A`:

```text
"AAAA"
```

---

## 2. Brute-Force Solution

Generate every substring.

For each substring:

1. count character frequencies;
2. identify the most frequent character;
3. calculate replacements required.

A window is valid when:

```text
windowLength - maxFrequency <= k
```

---

## 3. Why Brute Force Is Slow

There are:

```text
O(N²)
```

substrings.

Counting frequencies for every substring can result in:

```text
O(N² * alphabet)
```

or worse depending on implementation.

Sliding Window maintains frequencies incrementally.

---

## 4. Pattern Identification

The key phrase is:

```text
replace at most K characters
```

The critical formula is:

```text
replacementsRequired
=
windowLength - maximumFrequency
```

Why?

Suppose:

```text
window = "AABAB"
```

Frequencies:

```text
A -> 3
B -> 2
```

If we want all `A`:

```text
windowLength = 5
maxFrequency = 3

replacements = 5 - 3
             = 2
```

---

## 5. Sliding Window Intuition

Maintain:

```text
frequency of each character
maxFrequency
```

Expand the window.

Calculate:

```text
windowLength - maxFrequency
```

If it is greater than `k`, the window is invalid.

Shrink from the left.

---

## 6. Window Invariant

The desired invariant is:

```text
windowLength - maxFrequency <= k
```

This means:

> We can make the entire window consist of one character using at most K replacements.

---

## 7. Pointer Movement

```java
for (int right = 0; right < n; right++) {

    add character;

    maxFrequency = Math.max(
        maxFrequency,
        frequency[current]
    );

    while (windowLength - maxFrequency > k) {
        remove left character;
        left++;
    }

    update maximum length;
}
```

---

## 8. Data Structure Used

For uppercase English letters:

```java
int[26]
```

---

## 9. Java Implementation

```java
static int characterReplacement(
        String s,
        int k) {

    if (s == null || s.isEmpty()) {
        return 0;
    }

    int[] freq = new int[26];

    int left = 0;
    int maxFrequency = 0;
    int maxLength = 0;

    for (int right = 0;
         right < s.length();
         right++) {

        int index =
                s.charAt(right) - 'A';

        freq[index]++;

        maxFrequency = Math.max(
                maxFrequency,
                freq[index]
        );

        while ((right - left + 1)
                - maxFrequency > k) {

            freq[s.charAt(left) - 'A']--;
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
s = "AABABBA"
k = 1
```

Window:

```text
"A"
```

```text
length = 1
maxFrequency = 1
replacements = 0
```

Window:

```text
"AAB"
```

```text
length = 3
maxFrequency = 2
replacements = 1
```

Valid.

Window:

```text
"AABA"
```

```text
length = 4
maxFrequency = 3
replacements = 1
```

Valid.

Add another `B`:

```text
"AABAB"
```

```text
length = 5
maxFrequency = 3
replacements = 2
```

Invalid because:

```text
2 > 1
```

Shrink from left.

Continue.

Final answer:

```text
4
```

---

## 11. Edge Cases

- `k = 0`;
- `k >= string length`;
- all characters identical;
- all characters different;
- empty string;
- lowercase input if implementation assumes uppercase;
- Unicode input.

---

## 12. Complexity Analysis

With a fixed alphabet:

```text
Time:  O(N)
Space: O(1)
```

Each character enters and leaves the window at most once.

---

## 13. Common Mistakes

### Mistake 1

Using:

```text
number of distinct characters
```

instead of:

```text
maximum frequency
```

### Mistake 2

Using:

```text
windowLength - distinctCount
```

Wrong.

Correct:

```text
windowLength - maxFrequency
```

### Mistake 3

Recomputing `maxFrequency` from scratch after every removal.

For this standard problem, keeping a historical maximum is sufficient for determining the maximum answer.

### Mistake 4

Forgetting the replacement interpretation.

---

## 14. Interview Follow-Up Questions

1. Why is `windowLength - maxFrequency` the replacement count?
2. Why can `maxFrequency` remain stale after shrinking?
3. What changes for lowercase characters?
4. What changes for Unicode?
5. Return the actual substring.
6. What if each character has a different replacement cost?
7. What if we need at least K equal characters?
8. What if replacements can only be made in one direction?

---

## 15. Variations

- Longest Repeating Character Replacement;
- Longest substring with at most K changes;
- Longest substring convertible to one character;
- Longest binary substring after K flips;
- Weighted replacement variants.

---

# 5. Minimum Window

## 1. Problem Statement

Find the shortest substring of `s` that contains all characters of `t` with at least the required frequencies.

Example:

```text
s = "ADOBECODEBANC"
t = "ABC"
```

Answer:

```text
"BANC"
```

This is one of the most important Sliding Window interview problems.

---

## 2. Brute-Force Solution

Generate all substrings and test whether each contains the required frequency map.

```java
static String minimumWindowBruteForce(
        String s,
        String t) {

    String best = "";

    for (int i = 0; i < s.length(); i++) {

        for (int j = i; j < s.length(); j++) {

            String current =
                    s.substring(i, j + 1);

            if (containsAllRequired(current, t)) {

                if (best.isEmpty()
                        || current.length()
                        < best.length()) {
                    best = current;
                }

                break;
            }
        }
    }

    return best;
}
```

The requirement check itself needs frequency tracking.

---

## 3. Why Brute Force Is Slow

There can be:

```text
O(N²)
```

substrings.

Checking each substring against required frequencies adds additional work.

The straightforward brute-force solution can therefore approach:

```text
O(N² * M)
```

where `M` is the number of distinct required characters.

---

## 4. Pattern Identification

Look for:

```text
minimum window
smallest substring
shortest substring containing
contains all characters
required frequency
minimum covering window
```

The pattern is:

```text
Expand until valid
        ↓
Shrink while valid
        ↓
Record minimum
```

---

## 5. Sliding Window Intuition

Maintain:

```text
need
window
formed
required
```

Example:

```text
t = "AABC"

need:
A -> 2
B -> 1
C -> 1
```

As `right` expands, we satisfy requirements.

Once:

```text
formed == required
```

the window is valid.

Now aggressively move `left`.

Every time the window is still valid:

```text
record its length
remove left
```

Eventually removing from the left makes it invalid.

Then continue expanding `right`.

---

## 6. Window Invariant

When:

```text
formed == required
```

the current window contains all required frequencies.

The shrinking phase maintains:

```text
window is valid
```

until removing one character breaks the requirement.

---

## 7. Pointer Movement

### Right pointer

Expands:

```text
right++
```

### Left pointer

Shrinks while valid:

```java
while (formed == required) {

    update best;

    remove s[left];

    if requirement is broken:
        formed--;

    left++;
}
```

---

## 8. Data Structure Used

```java
HashMap<Character, Integer>
```

Two maps:

```text
need
window
```

And two counters:

```text
required
formed
```

---

## 9. Java Implementation

```java
static String minWindow(
        String s,
        String t) {

    if (s == null || t == null
            || s.isEmpty()
            || t.isEmpty()
            || t.length() > s.length()) {
        return "";
    }

    Map<Character, Integer> need =
            new HashMap<>();

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

    for (int right = 0;
         right < s.length();
         right++) {

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

            int currentLength =
                    right - left + 1;

            if (currentLength < bestLength) {
                bestLength = currentLength;
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

    if (bestLength == Integer.MAX_VALUE) {
        return "";
    }

    return s.substring(
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

Expand:

```text
A D O B E C
```

At `C`:

```text
formed = 3
```

Window:

```text
"ADOBEC"
```

Valid.

Shrink:

```text
remove A
```

Now invalid.

Continue.

Later the window becomes:

```text
"CODEBANC"
```

Valid.

Shrink:

```text
"ODEBANC"
"DEBANC"
"BANC"
```

`"BANC"` remains valid.

Remove `B`:

```text
"ANC"
```

Invalid.

Best answer:

```text
"BANC"
```

---

## 11. Edge Cases

- `t` longer than `s`;
- no valid window;
- empty strings;
- duplicate required characters;
- all characters already present in first window;
- one-character pattern;
- case sensitivity;
- Unicode characters.

---

## 12. Complexity Analysis

Let:

```text
N = s.length()
M = number of distinct characters in t
```

Each pointer moves forward only.

Average HashMap operations are `O(1)`.

Therefore:

```text
Time:  O(N + M)
Space: O(M)
```

---

## 13. Common Mistakes

- Counting only distinct characters;
- ignoring duplicate requirements;
- incrementing `formed` for every required character occurrence;
- decrementing `formed` at the wrong time;
- using `if` instead of `while` for shrinking;
- updating the answer after the window becomes invalid;
- forgetting to store the best start index.

---

## 14. Interview Follow-Up Questions

1. Explain `formed` vs `required`.
2. How do duplicate characters work?
3. Can this use `int[128]`?
4. Can this support Unicode?
5. Return indexes instead of substring.
6. Find the longest valid covering window.
7. What if each required character has a weight?
8. What if requirements change dynamically?
9. Can you optimize the map operations?
10. Compare Minimum Window with Permutation in String.

---

## 15. Variations

- Minimum Window Substring;
- Minimum window with duplicate requirements;
- Minimum window containing a set of characters;
- Minimum window containing required numbers;
- Minimum covering range;
- Weighted minimum covering window.

---

# String Patterns — Critical Comparison

| Pattern | Window Size | Main State | Validity |
|---|---|---|---|
| Unique Characters | Variable | Set / frequency | No duplicate |
| Anagrams | Fixed | Frequency | Exact frequency match |
| Permutations | Fixed | Frequency | Exact frequency match |
| Character Replacement | Variable | Frequency + max frequency | `len - maxFreq <= K` |
| Minimum Window | Variable | Need + window + formed | All requirements satisfied |

---

# Unique vs Anagram vs Permutation

These are commonly confused.

## Unique Characters

```text
"abc"
```

Question:

```text
Does every character occur once?
```

Condition:

```text
frequency[c] <= 1
```

---

## Anagram

```text
"abc"
"bca"
```

Question:

```text
Do both strings have exactly the same frequencies?
```

Condition:

```text
freqA == freqB
```

---

## Permutation

Question:

```text
Does s2 contain ANY substring
whose frequency equals s1?
```

Therefore:

```text
fixed window size = s1.length()
```

---

# Character Replacement Formula

Memorize the reasoning, not just the formula.

For a window:

```text
AAAA B C
```

Suppose:

```text
A = 4
B = 1
C = 1
```

Window size:

```text
6
```

To make the whole window `A`:

```text
6 - 4 = 2
```

replacements.

Therefore:

```text
replacementsNeeded
=
windowLength - maxFrequency
```

Valid when:

```text
windowLength - maxFrequency <= K
```

---

# Minimum Window vs Character Replacement

These look similar because both are variable windows.

But their invariants are completely different.

## Character Replacement

```text
windowLength - maxFrequency <= K
```

## Minimum Window

```text
formed == required
```

Character Replacement asks:

> Can I convert this window into one repeated character with at most K changes?

Minimum Window asks:

> Does this window contain every required character with the required frequency?

---

# String Sliding Window Recognition Guide

When you see a string problem, ask:

```text
1. Is the answer a contiguous substring?
        |
       YES
        |
        v
2. Is the window size fixed?
        |
   +----+----+
   |         |
  YES       NO
   |         |
   v         v
Frequency   What is
matching?   validity?
   |         |
   v         v
Anagram    Unique?
Permutation |
            At most K?
            |
            Required frequency?
            |
            Replacement?
```

---

# Master Templates

## Template 1 — Unique Characters

```java
Set<Character> set = new HashSet<>();

int left = 0;

for (int right = 0; right < s.length(); right++) {

    while (set.contains(s.charAt(right))) {
        set.remove(s.charAt(left));
        left++;
    }

    set.add(s.charAt(right));

    answer = Math.max(
            answer,
            right - left + 1
    );
}
```

---

## Template 2 — Fixed Frequency Match

```java
for (int right = 0; right < n; right++) {

    add(right);

    if (windowSize > patternLength) {
        remove(left);
        left++;
    }

    if (windowSize == patternLength
            && frequencyMatches()) {

        // found match
    }
}
```

---

## Template 3 — Character Replacement

```java
for (int right = 0; right < n; right++) {

    add(right);

    while (windowLength - maxFrequency > k) {
        remove(left);
        left++;
    }

    answer = Math.max(
            answer,
            windowLength
    );
}
```

---

## Template 4 — Minimum Required Window

```java
for (int right = 0; right < n; right++) {

    add(right);

    while (formed == required) {

        updateMinimum();

        remove(left);
        left++;
    }
}
```

---

# Common Mistakes Across String Patterns

```text
[ ] Confusing fixed and variable window
[ ] Forgetting outgoing character
[ ] Using distinct count when frequency is required
[ ] Using Set when duplicate frequency matters
[ ] Not removing zero-frequency keys
[ ] Using if instead of while
[ ] Incorrect left-pointer movement
[ ] Forgetting duplicate requirements
[ ] Confusing maxFrequency with distinctCount
[ ] Assuming ASCII/26 letters without checking input
[ ] Recomputing the entire window unnecessarily
```

---

# Interview Follow-Up Master Questions

Be prepared to answer:

1. Why does Sliding Window work here?
2. What is your window invariant?
3. Why can `left` only move forward?
4. Why is the complexity O(N)?
5. What state must be maintained?
6. Why use HashMap vs array?
7. What changes for Unicode?
8. What happens with duplicate characters?
9. What happens when K is zero?
10. Why is the anagram window fixed?
11. Why is Minimum Window variable?
12. Why is `windowLength - maxFrequency` correct?
13. Why is `formed` required?
14. Can you return the actual substring?
15. Can you return indexes?
16. Can the problem be solved with two pointers without a map?
17. What is the brute-force complexity?
18. What input assumptions does the solution depend on?

---

# Practice Roadmap

## Level 1 — Beginner

1. Longest substring without repeating characters
2. Find all anagrams in a string
3. Permutation in String
4. Longest substring with at most K distinct characters

## Level 2 — Intermediate

5. Longest Repeating Character Replacement
6. Count anagram occurrences
7. Count substrings satisfying frequency constraints
8. Minimum Window Substring

## Level 3 — Advanced

9. Minimum Window with duplicate requirements
10. Unicode-aware frequency windows
11. Frequency Map + multiple constraints
12. Frequency Map + Deque
13. Weighted character replacement
14. Minimum covering window with complex requirements

---

# Final Mental Model

```text
STRING SLIDING WINDOW
          |
          +-----------------------------+
          |                             |
       FIXED                         VARIABLE
          |                             |
          v                             v
   Frequency Match              Validity Condition
          |                             |
     +----+----+                  +-----+------+
     |         |                  |            |
  Anagram  Permutation         Unique       Minimum
                                  |          Window
                                  |
                            Character
                            Replacement
```

The most important recognition rules are:

```text
ANAGRAM / PERMUTATION
    -> fixed-size window
    -> frequency equality

UNIQUE CHARACTERS
    -> variable window
    -> remove duplicates

CHARACTER REPLACEMENT
    -> variable window
    -> length - maxFrequency <= K

MINIMUM WINDOW
    -> variable window
    -> required frequencies satisfied
```

---

# Final Interview Checklist

Before writing Java code:

```text
[ ] Is the problem about a contiguous substring?
[ ] Is the window fixed or variable?
[ ] Is the requirement about uniqueness?
[ ] Is the requirement about exact frequency?
[ ] Is there a required pattern?
[ ] Is there a replacement budget K?
[ ] What is the window invariant?
[ ] What state enters when right moves?
[ ] What state leaves when left moves?
[ ] When should left move?
[ ] Is the answer longest or shortest?
[ ] Do duplicate frequencies matter?
[ ] HashMap or frequency array?
[ ] ASCII, lowercase alphabet, or Unicode?
[ ] Can the answer fit in int?
[ ] Can each pointer move only forward?
[ ] Can I explain the O(N) amortized complexity?
```

# Master Rule

Do not memorize five unrelated algorithms.

Recognize the invariant:

```text
UNIQUE
    -> no duplicate

ANAGRAM / PERMUTATION
    -> exact frequency equality

CHARACTER REPLACEMENT
    -> windowLength - maxFrequency <= K

MINIMUM WINDOW
    -> all required frequencies satisfied
```

Once you can identify the invariant, the Sliding Window implementation becomes a mechanical application of:

```text
EXPAND RIGHT
     ↓
UPDATE STATE
     ↓
CHECK INVARIANT
     ↓
SHRINK LEFT WHEN NECESSARY
     ↓
UPDATE ANSWER
```
