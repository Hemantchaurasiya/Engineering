# Maximum Number of Balloons

## Problem Statement
You are given a string `text` where each character in the string represents a letter. Your task is to find the maximum number of instances of the word **"balloon"** that can be formed using the characters in the string.

Each character in `text` can be used only once per word. The word "balloon" consists of the characters `b`, `a`, `l`, `o`, `n` with `l` and `o` appearing twice.

## Constraints
- `1 <= text.length <= 10^4`
- `text` consists of lower-case English letters.

## Approaches

### Approach 1: Frequency Count Using a Dictionary/Map

#### Explanation
1. **Count the Frequency**: Count the occurrences of each character in the string using a frequency dictionary or hash map.
2. **Check Required Counts**: For the word "balloon":
   - `b` and `a` need 1 occurrence each.
   - `l` and `o` need 2 occurrences each.
   - `n` needs 1 occurrence.
3. **Calculate Maximum Instances**:
   - Divide the frequency of `l` and `o` by 2 since they are needed twice.
   - Find the minimum count among the required characters to determine the maximum number of "balloon" instances.

#### Algorithm
1. Create a frequency map for characters in `text`.
2. Extract the counts of the required characters: `b`, `a`, `l`, `o`, and `n`.
3. Calculate the maximum number of "balloon" instances using the formula:
   ```
   min(freq['b'], freq['a'], freq['l'] // 2, freq['o'] // 2, freq['n'])
   ```

#### Complexity
- **Time Complexity**: `O(n)` where `n` is the length of the string, since we iterate through the string to build the frequency map.
- **Space Complexity**: `O(1)` as the size of the map is constant (only 26 lowercase letters).

#### Code Example (Python)
```python
from collections import Counter

def maxNumberOfBalloons(text):
    freq = Counter(text)
    return min(freq['b'], freq['a'], freq['l'] // 2, freq['o'] // 2, freq['n'])

# Example usage
text = "loonbalxballpoon"
print(maxNumberOfBalloons(text))  # Output: 2
```

---

### Approach 2: Array-Based Frequency Count

#### Explanation
1. **Use a Fixed Array for Frequencies**: Since there are only 26 lowercase letters, use an array of size 26 to count character frequencies.
2. **Index Mapping**: Map characters to indices using `ord(char) - ord('a')`.
3. **Check Required Counts**: Use the same logic as Approach 1 but access frequencies using array indices.

#### Algorithm
1. Create an array `freq` of size 26 initialized to 0.
2. Traverse the string `text` and update frequencies in the array.
3. Extract required character frequencies (`b`, `a`, `l`, `o`, `n`) using their respective indices.
4. Compute the maximum number of "balloon" instances.

#### Complexity
- **Time Complexity**: `O(n)`
- **Space Complexity**: `O(1)`

#### Code Example (Python)
```python
def maxNumberOfBalloons(text):
    freq = [0] * 26
    for char in text:
        freq[ord(char) - ord('a')] += 1

    return min(freq[ord('b') - ord('a')],
               freq[ord('a') - ord('a')],
               freq[ord('l') - ord('a')] // 2,
               freq[ord('o') - ord('a')] // 2,
               freq[ord('n') - ord('a')])

# Example usage
text = "loonbalxballpoon"
print(maxNumberOfBalloons(text))  # Output: 2
```

---

### Approach 3: Optimized Single Pass with Early Termination

#### Explanation
1. **Single Pass Counting**: Instead of counting all characters in `text`, maintain counts only for the required characters `b`, `a`, `l`, `o`, and `n`.
2. **Early Termination**: Stop processing the string early if any character count becomes insufficient to form another "balloon".

#### Algorithm
1. Initialize a frequency dictionary or array for `b`, `a`, `l`, `o`, and `n`.
2. Traverse `text`, updating counts for these characters only.
3. Calculate maximum instances of "balloon" as in Approach 1.
4. Stop the traversal early if forming another "balloon" becomes impossible.

#### Complexity
- **Time Complexity**: `O(n)`
- **Space Complexity**: `O(1)`

#### Code Example (Python)
```python
def maxNumberOfBalloons(text):
    freq = {'b': 0, 'a': 0, 'l': 0, 'o': 0, 'n': 0}

    for char in text:
        if char in freq:
            freq[char] += 1

    return min(freq['b'], freq['a'], freq['l'] // 2, freq['o'] // 2, freq['n'])

# Example usage
text = "loonbalxballpoon"
print(maxNumberOfBalloons(text))  # Output: 2
```

---

## Conclusion
Each of the above approaches achieves the same result but varies in implementation style and optimization:
- **Approach 1** is simple and uses a dictionary for clarity.
- **Approach 2** uses an array for space efficiency.
- **Approach 3** minimizes unnecessary computation with early termination.

Choose the approach based on the constraints and requirements of your specific use case.
