### Hashing
- Hashing
    - [Ransom Note](https://github.com/Hemantchaurasiya/Engineering/blob/Coding_Patterns/Data%20Structures/Src/01%20Array/01%20Hashmaps/Programs/Ransom%20Note.md)
    - [Isomorphic Strings](https://github.com/Hemantchaurasiya/Engineering/blob/Coding_Patterns/Data%20Structures/Src/01%20Array/01%20Hashmaps/Programs/Isomorphic%20Strings.md)
    - [Word Pattern](https://github.com/Hemantchaurasiya/Engineering/blob/Coding_Patterns/Data%20Structures/Src/01%20Array/01%20Hashmaps/Programs/Word%20Pattern.md)
    - [Valid Anagram](https://github.com/Hemantchaurasiya/Engineering/blob/Coding_Patterns/Data%20Structures/Src/01%20Array/01%20Hashmaps/Programs/Valid%20Anagram.md)
    - [Group Anagrams](https://github.com/Hemantchaurasiya/Engineering/blob/Coding_Patterns/Data%20Structures/Src/01%20Array/01%20Hashmaps/Programs/Group%20Anagrams.md)
    - [Two Sum](https://github.com/Hemantchaurasiya/Engineering/blob/Coding_Patterns/Data%20Structures/Src/01%20Array/01%20Hashmaps/Programs/Two%20Sum.md)
    - [Happy Number](https://github.com/Hemantchaurasiya/Engineering/blob/Coding_Patterns/Data%20Structures/Src/01%20Array/01%20Hashmaps/Programs/Happy%20Number.md)
    - [Contains Duplicate II](https://github.com/Hemantchaurasiya/Engineering/blob/Coding_Patterns/Data%20Structures/Src/01%20Array/01%20Hashmaps/Programs/Contains%20Duplicate%20II.md)
    - [Longest Consecutive Sequence](https://github.com/Hemantchaurasiya/Engineering/blob/Coding_Patterns/Data%20Structures/Src/01%20Array/01%20Hashmaps/Programs/Longest%20Consecutive%20Sequence.md)
    - [Majority Element](https://github.com/Hemantchaurasiya/Engineering/blob/Coding_Patterns/Data%20Structures/Src/01%20Array/01%20Hashmaps/Programs/Majority%20Element.md)
    - [Majority Element II](https://github.com/Hemantchaurasiya/Engineering/blob/Coding_Patterns/Data%20Structures/Src/01%20Array/01%20Hashmaps/Programs/Majority%20Element%20II.md)
    - [First Non-repeating Character](https://github.com/Hemantchaurasiya/Engineering/blob/Coding_Patterns/Data%20Structures/Src/01%20Array/01%20Hashmaps/Programs/First%20Non-repeating%20Character.md)
    - [Largest Unique Number](https://github.com/Hemantchaurasiya/Engineering/blob/Coding_Patterns/Data%20Structures/Src/01%20Array/01%20Hashmaps/Programs/Largest%20Unique%20Number.md)
    - [Longest Palindrome](https://github.com/Hemantchaurasiya/Engineering/blob/Coding_Patterns/Data%20Structures/Src/01%20Array/01%20Hashmaps/Programs/Longest%20Palindrome.md)
    - [Maximum Number of Balloons](https://github.com/Hemantchaurasiya/Engineering/blob/Coding_Patterns/Data%20Structures/Src/01%20Array/01%20Hashmaps/Programs/Maximum%20Number%20of%20Balloons.md)

- Hashing With Prefix Sum
    - [Subarray Sum Equals K](https://github.com/Hemantchaurasiya/Engineering/blob/Coding_Patterns/Data%20Structures/Src/01%20Array/01%20Hashmaps/Programs/Subarray%20Sum%20Equals%20K.md)
    - [Subarray Sum Divisible by K](https://github.com/Hemantchaurasiya/Engineering/blob/Coding_Patterns/Data%20Structures/Src/01%20Array/01%20Hashmaps/Programs/Subarray%20Sum%20Divisible%20by%20K.md)
    - [Count Number of Nice Subarrays](https://github.com/Hemantchaurasiya/Engineering/blob/Coding_Patterns/Data%20Structures/Src/01%20Array/01%20Hashmaps/Programs/Count%20Number%20of%20Nice%20Subarrays.md)
    - [Number of Submatrices That Sum to Target](https://github.com/Hemantchaurasiya/Engineering/blob/Coding_Patterns/Data%20Structures/Src/01%20Array/01%20Hashmaps/Programs/Number%20of%20Submatrices%20That%20Sum%20to%20Target.md)


# Hashing and HashMap: Comprehensive Notes

## 1. What is Hashing or HashMap?
- **Hashing** is a technique to convert a range of key values into a range of indexes of an array. It allows for fast data retrieval by calculating a hash code using a hash function.
- A **HashMap** is a data structure that implements the Map interface in Java. It stores key-value pairs, where each key is unique, and provides constant-time complexity for basic operations like `get()` and `put()`.

### Key Characteristics of HashMap:
- Keys must be unique.
- Values can be duplicated.
- Allows null values and a single null key.
- Does not maintain insertion order.

---

## 2. How to Identify Hashing or HashMap in Problems
You might need hashing or HashMap if:
- The problem involves frequent lookups, inserts, or deletions.
- You need to store key-value pairs.
- You need to find duplicates or check membership efficiently.
- The problem mentions counting frequencies of elements.
- You need to group or categorize data based on certain criteria (e.g., anagrams).

---

## 3. Where to Use Hashing or HashMap
- **Counting Frequencies:** Counting occurrences of elements in a list.
- **Caching:** Implementing LRU Cache or other caching mechanisms.
- **Groupings:** Grouping elements like anagrams or similar strings.
- **Index Mapping:** Storing indices of elements for quick lookup.
- **Duplicate Detection:** Finding duplicates in a list or stream.
- **Dynamic Programming Optimization:** Avoiding recomputation by storing intermediate results (memoization).

---

## 4. Types of Hashing or HashMap
### Based on Implementation:
1. **Separate Chaining:** Uses linked lists to handle collisions.
2. **Open Addressing:** Probes for the next available slot in the case of collisions.
   - Linear Probing
   - Quadratic Probing
   - Double Hashing

### Based on Functionality:
1. **HashMap:** Basic key-value mapping.
2. **HashSet:** Stores unique elements.
3. **LinkedHashMap:** Maintains insertion order.
4. **TreeMap:** Stores keys in sorted order.

---

## 5. Algorithm of Hashing or HashMap
1. **Initialize:** Create a HashMap with a certain capacity and load factor.
2. **Insert:**
   - Calculate hash code using the key.
   - Map the hash code to an index in the underlying array.
   - If a collision occurs, resolve it using a collision resolution strategy (e.g., chaining or probing).
3. **Search:**
   - Calculate hash code for the key.
   - Locate the corresponding index and traverse the linked list (if chaining is used).
4. **Delete:**
   - Calculate hash code for the key.
   - Locate the index and remove the key-value pair.

---

## 6. Sample Standard Problems
### Problem 1: Two Sum
**Problem:** Given an array of integers `nums` and an integer `target`, return indices of the two numbers such that they add up to the target.

**Approach:**
- Use a HashMap to store the difference between the target and each number.
- For each element, check if it exists in the HashMap.

**Code in Java:**
```java
import java.util.HashMap;

public class TwoSum {
    public int[] twoSum(int[] nums, int target) {
        HashMap<Integer, Integer> map = new HashMap<>();
        for (int i = 0; i < nums.length; i++) {
            int complement = target - nums[i];
            if (map.containsKey(complement)) {
                return new int[] { map.get(complement), i };
            }
            map.put(nums[i], i);
        }
        throw new IllegalArgumentException("No solution found");
    }
}
```

### Problem 2: Group Anagrams
**Problem:** Group strings that are anagrams of each other.

**Approach:**
- Use a HashMap with the sorted version of the string as the key.
- Group all strings with the same sorted value.

**Code in Java:**
```java
import java.util.*;

public class GroupAnagrams {
    public List<List<String>> groupAnagrams(String[] strs) {
        HashMap<String, List<String>> map = new HashMap<>();
        for (String str : strs) {
            char[] chars = str.toCharArray();
            Arrays.sort(chars);
            String key = new String(chars);
            map.computeIfAbsent(key, k -> new ArrayList<>()).add(str);
        }
        return new ArrayList<>(map.values());
    }
}
```

---

## 7. Revision Points
- Understand the difference between hash codes and indices.
- Know collision resolution techniques (chaining, probing).
- Remember the average time complexity of HashMap operations is O(1), but can degrade to O(n) in the worst-case scenario.
- Practice problems involving HashMap for counting, grouping, and mapping data.
- Be familiar with Java-specific HashMap methods like `put()`, `get()`, `containsKey()`, and `computeIfAbsent()`.

---