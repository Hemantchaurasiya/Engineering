# Constructing Ransom Note from Magazine

This document covers different approaches to solve the problem of determining if a `ransomNote` can be constructed using the letters from `magazine`. Each approach includes an explanation of the algorithm, code implementation in Java, and an analysis of time and space complexity.

## Problem Description
Given two strings `ransomNote` and `magazine`, return `true` if `ransomNote` can be constructed by using the letters from `magazine` and `false` otherwise.

### Constraints
1. Each letter in `magazine` can only be used once in `ransomNote`.
2. Strings consist of lowercase English letters.

---

## Approach 1: Using HashMap

### Algorithm
1. Create a `HashMap` to store the frequency of each character in `magazine`.
2. Iterate through `magazine` and populate the `HashMap` with the character frequencies.
3. Iterate through `ransomNote`:
    - If the character is not in the `HashMap` or its count is zero, return `false`.
    - Otherwise, decrement the count of the character in the `HashMap`.
4. If all characters in `ransomNote` are accounted for, return `true`.

### Java Code
```java
import java.util.HashMap;

public class RansomNote {
    public boolean canConstruct(String ransomNote, String magazine) {
        HashMap<Character, Integer> charCount = new HashMap<>();

        for (char c : magazine.toCharArray()) {
            charCount.put(c, charCount.getOrDefault(c, 0) + 1);
        }

        for (char c : ransomNote.toCharArray()) {
            if (!charCount.containsKey(c) || charCount.get(c) == 0) {
                return false;
            }
            charCount.put(c, charCount.get(c) - 1);
        }

        return true;
    }
}
```

### Complexity
- **Time Complexity:** O(m + n), where `m` is the length of `magazine` and `n` is the length of `ransomNote`.
- **Space Complexity:** O(1), as the `HashMap` stores at most 26 characters (constant space).

---

## Approach 2: Using Array as Frequency Counter

### Algorithm
1. Create an integer array `count` of size 26 to store the frequency of each character in `magazine`.
2. Iterate through `magazine` and increment the count for each character.
3. Iterate through `ransomNote`:
    - If the count of a character is zero, return `false`.
    - Otherwise, decrement the count for the character.
4. If all characters in `ransomNote` are accounted for, return `true`.

### Java Code
```java
public class RansomNote {
    public boolean canConstruct(String ransomNote, String magazine) {
        int[] charCount = new int[26];

        for (char c : magazine.toCharArray()) {
            charCount[c - 'a']++;
        }

        for (char c : ransomNote.toCharArray()) {
            if (charCount[c - 'a'] == 0) {
                return false;
            }
            charCount[c - 'a']--;
        }

        return true;
    }
}
```

### Complexity
- **Time Complexity:** O(m + n), where `m` is the length of `magazine` and `n` is the length of `ransomNote`.
- **Space Complexity:** O(1), as the array size is constant.

---

## Approach 3: Sorting and Two Pointers

### Algorithm
1. Sort both `ransomNote` and `magazine`.
2. Use two pointers:
    - One pointer for `ransomNote` and one for `magazine`.
    - Iterate through both strings.
    - If the characters match, move both pointers forward.
    - If the character in `magazine` does not match the current character in `ransomNote`, move the `magazine` pointer forward.
3. If the end of `ransomNote` is reached, return `true`. Otherwise, return `false`.

### Java Code
```java
import java.util.Arrays;

public class RansomNote {
    public boolean canConstruct(String ransomNote, String magazine) {
        char[] ransomArr = ransomNote.toCharArray();
        char[] magazineArr = magazine.toCharArray();

        Arrays.sort(ransomArr);
        Arrays.sort(magazineArr);

        int i = 0, j = 0;

        while (i < ransomArr.length && j < magazineArr.length) {
            if (ransomArr[i] == magazineArr[j]) {
                i++;
            }
            j++;
        }

        return i == ransomArr.length;
    }
}
```

### Complexity
- **Time Complexity:** O(m log m + n log n), where `m` is the length of `magazine` and `n` is the length of `ransomNote`.
- **Space Complexity:** O(m + n), due to the sorted arrays.

---

## Conclusion
| Approach                    | Time Complexity      | Space Complexity  |
|-----------------------------|----------------------|-------------------|
| HashMap                    | O(m + n)            | O(1)              |
| Array as Frequency Counter | O(m + n)            | O(1)              |
| Sorting and Two Pointers   | O(m log m + n log n) | O(m + n)          |

The optimal approach depends on the problem constraints, but the **array-based frequency counter** is often the most efficient and straightforward method for this problem.

