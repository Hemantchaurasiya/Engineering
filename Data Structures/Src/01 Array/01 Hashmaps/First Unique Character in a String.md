# First Unique Character in a String

## Problem Statement
Given a string `s`, find the first non-repeating character in it and return its index. If it does not exist, return `-1`.

## Key Approaches

### 1. **Brute Force Approach**
#### Explanation:
- Iterate through each character in the string.
- For each character, iterate through the string again to count its occurrences.
- If a character is found with a count of `1`, return its index.

#### Complexity:
- **Time Complexity:** O(n^2) because of the nested iteration.
- **Space Complexity:** O(1), as no extra data structures are used.

#### Code:
```java
public int firstUniqChar(String s) {
    for (int i = 0; i < s.length(); i++) {
        char c = s.charAt(i);
        int count = 0;
        for (int j = 0; j < s.length(); j++) {
            if (s.charAt(j) == c) {
                count++;
            }
        }
        if (count == 1) {
            return i;
        }
    }
    return -1;
}
```

---

### 2. **Using a Frequency Map**
#### Explanation:
- Use a `HashMap` or `int[]` to store the frequency of each character.
- Iterate through the string a second time to find the first character with a frequency of `1`.

#### Complexity:
- **Time Complexity:** O(n), as we traverse the string twice.
- **Space Complexity:** O(1) (constant size of the alphabet).

#### Code:
```java
public int firstUniqChar(String s) {
    int[] freq = new int[26];
    for (char c : s.toCharArray()) {
        freq[c - 'a']++;
    }
    for (int i = 0; i < s.length(); i++) {
        if (freq[s.charAt(i) - 'a'] == 1) {
            return i;
        }
    }
    return -1;
}
```

---

### 3. **Using a LinkedHashMap**
#### Explanation:
- Use a `LinkedHashMap` to store characters and their indices.
- This structure maintains the insertion order.
- Iterate through the string to populate the map with character counts.
- Traverse the map to find the first character with a count of `1`.

#### Complexity:
- **Time Complexity:** O(n), due to a single traversal.
- **Space Complexity:** O(n), because of the map.

#### Code:
```java
import java.util.*;

public int firstUniqChar(String s) {
    Map<Character, Integer> map = new LinkedHashMap<>();
    for (int i = 0; i < s.length(); i++) {
        char c = s.charAt(i);
        map.put(c, map.getOrDefault(c, 0) + 1);
    }
    for (int i = 0; i < s.length(); i++) {
        if (map.get(s.charAt(i)) == 1) {
            return i;
        }
    }
    return -1;
}
```

---

### 4. **Using Queue**
#### Explanation:
- Use a `Queue` to track unique characters.
- Maintain an `int[]` for character frequency.
- Iterate through the string:
  - Add characters to the queue.
  - Remove characters from the queue if their frequency exceeds `1`.
- The front of the queue is the first unique character.

#### Complexity:
- **Time Complexity:** O(n), as characters are added and removed from the queue.
- **Space Complexity:** O(n), due to the queue.

#### Code:
```java
import java.util.*;

public int firstUniqChar(String s) {
    int[] freq = new int[26];
    Queue<Integer> queue = new LinkedList<>();

    for (int i = 0; i < s.length(); i++) {
        char c = s.charAt(i);
        freq[c - 'a']++;
        queue.offer(i);

        while (!queue.isEmpty() && freq[s.charAt(queue.peek()) - 'a'] > 1) {
            queue.poll();
        }
    }

    return queue.isEmpty() ? -1 : queue.peek();
}
```

---

### 5. **Optimized Single Pass Approach**
#### Explanation:
- Use an `int[]` to store indices of characters.
- If a character appears more than once, store a special value (e.g., `Integer.MAX_VALUE`).
- Traverse the array to find the smallest index.

#### Complexity:
- **Time Complexity:** O(n).
- **Space Complexity:** O(1).

#### Code:
```java
public int firstUniqChar(String s) {
    int[] indices = new int[26];
    Arrays.fill(indices, -1);

    for (int i = 0; i < s.length(); i++) {
        char c = s.charAt(i);
        if (indices[c - 'a'] == -1) {
            indices[c - 'a'] = i;
        } else {
            indices[c - 'a'] = Integer.MAX_VALUE;
        }
    }

    int minIndex = Integer.MAX_VALUE;
    for (int index : indices) {
        if (index != -1 && index != Integer.MAX_VALUE) {
            minIndex = Math.min(minIndex, index);
        }
    }

    return minIndex == Integer.MAX_VALUE ? -1 : minIndex;
}
