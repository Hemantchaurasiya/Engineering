# Ransom Note Problem

## Problem Statement
Given two strings `ransomNote` and `magazine`, return `true` if `ransomNote` can be constructed by using the letters from `magazine` and `false` otherwise.

Each letter in `magazine` can only be used once in `ransomNote`.

### Example 1:
**Input:**
```
ransomNote = "a"
magazine = "b"
```
**Output:**
```
false
```

### Example 2:
**Input:**
```
ransomNote = "aa"
magazine = "ab"
```
**Output:**
```
false
```

### Example 3:
**Input:**
```
ransomNote = "aa"
magazine = "aab"
```
**Output:**
```
true
```

## Approach 1: HashMap

### Explanation
To solve this problem, we can count the frequency of each character in the `magazine` and ensure that the `ransomNote` can be formed using the available characters in the `magazine`:

1. Use a `HashMap` to store the frequency of each character in the `magazine`.
2. Iterate through each character in the `ransomNote`:
   - If the character exists in the map and its count is greater than 0, decrement its count.
   - If the character does not exist or its count is 0, return `false`.
3. If all characters in the `ransomNote` are processed successfully, return `true`.

### Algorithm
1. Create a `HashMap` to count the frequency of each character in the `magazine`.
2. Iterate through the `ransomNote`:
   - Check if the character exists and has a non-zero frequency in the map.
   - If true, decrement the count.
   - If false, return `false`.
3. Return `true` if all characters match the above conditions.

### Code (Java)
```java
import java.util.HashMap;

public class RansomNote {
    public boolean canConstruct(String ransomNote, String magazine) {
        // Step 1: Create a HashMap to store character frequencies of the magazine
        HashMap<Character, Integer> charCount = new HashMap<>();

        // Step 2: Count characters in the magazine
        for (char c : magazine.toCharArray()) {
            charCount.put(c, charCount.getOrDefault(c, 0) + 1);
        }

        // Step 3: Check if ransomNote can be constructed
        for (char c : ransomNote.toCharArray()) {
            if (!charCount.containsKey(c) || charCount.get(c) == 0) {
                return false; // Character not found or no more available
            }
            charCount.put(c, charCount.get(c) - 1); // Use the character
        }

        return true; // Successfully constructed the ransom note
    }

    public static void main(String[] args) {
        RansomNote rn = new RansomNote();
        System.out.println(rn.canConstruct("aa", "aab")); // Output: true
        System.out.println(rn.canConstruct("aa", "ab"));  // Output: false
        System.out.println(rn.canConstruct("a", "b"));    // Output: false
    }
}
```

### Comments in Code
- **Step 1:** Use `HashMap` to store character counts from the `magazine`.
- **Step 2:** Loop through the `ransomNote` and check if the character exists and is available.
- **Step 3:** Update the count in the `HashMap` if the character is used.

---

## Approach 2: Array (Optimized for Lowercase Letters)

### Explanation
Since the problem specifies lowercase English letters, we can use an integer array of size 26 instead of a `HashMap`. Each index in the array corresponds to a character ('a' to 'z').

1. Create an array `charCount` of size 26 to store the frequency of characters in `magazine`.
2. Iterate through the `ransomNote`:
   - Check if the required character is available in the `charCount` array.
   - If available, decrement its count.
   - If not available, return `false`.
3. Return `true` if all characters match the above conditions.

### Algorithm
1. Initialize an array `charCount` of size 26 to 0.
2. Count the frequency of each character in the `magazine`.
3. For each character in the `ransomNote`, check availability in `charCount` and decrement if possible.
4. If all characters are processed successfully, return `true`.

### Code (Java)
```java
public class RansomNoteOptimized {
    public boolean canConstruct(String ransomNote, String magazine) {
        // Step 1: Create an array to store character counts
        int[] charCount = new int[26];

        // Step 2: Count characters in the magazine
        for (char c : magazine.toCharArray()) {
            charCount[c - 'a']++;
        }

        // Step 3: Check if ransomNote can be constructed
        for (char c : ransomNote.toCharArray()) {
            if (charCount[c - 'a'] == 0) {
                return false; // Character not available
            }
            charCount[c - 'a']--; // Use the character
        }

        return true; // Successfully constructed the ransom note
    }

    public static void main(String[] args) {
        RansomNoteOptimized rn = new RansomNoteOptimized();
        System.out.println(rn.canConstruct("aa", "aab")); // Output: true
        System.out.println(rn.canConstruct("aa", "ab"));  // Output: false
        System.out.println(rn.canConstruct("a", "b"));    // Output: false
    }
}
```

### Comments in Code
- **Step 1:** Initialize an array of size 26 for lowercase English letters.
- **Step 2:** Populate the array using characters from the `magazine`.
- **Step 3:** Check and decrement counts for characters in the `ransomNote`.

---

## Complexity Analysis

| Approach   | Time Complexity | Space Complexity |
|------------|-----------------|------------------|
| HashMap    | O(n + m)        | O(m)             |
| Array      | O(n + m)        | O(1)             |

Where `n` is the length of the `ransomNote` and `m` is the length of the `magazine`.

---

## Summary
- **HashMap Approach:** General solution for all character sets.
- **Array Approach:** Optimized for lowercase English letters with constant space usage.
