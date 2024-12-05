# Longest Palindrome - Approaches and Explanations

The **Longest Palindrome** problem focuses on finding the longest substring or subsequence within a given string that forms a palindrome. Below, we outline multiple approaches to solve this problem, along with explanations for each.

---

## 1. Brute Force Approach (Substring)

### Explanation
The brute force approach involves checking all possible substrings of the given string and verifying if each substring is a palindrome. We then keep track of the longest palindrome encountered.

### Steps
1. Generate all possible substrings of the string.
2. Check if each substring is a palindrome.
3. Track the longest palindrome found.

### Complexity
- **Time Complexity**: \(O(n^3)\) (\(O(n^2)\) for generating substrings and \(O(n)\) for checking if a substring is a palindrome)
- **Space Complexity**: \(O(1)\)

---

## 2. Dynamic Programming Approach (Substring)

### Explanation
This approach uses a 2D table to store whether a substring starting at index `i` and ending at index `j` is a palindrome. The table is filled using previously computed results.

### Steps
1. Create a 2D boolean table `dp` where `dp[i][j]` is `true` if the substring `s[i:j+1]` is a palindrome.
2. Initialize:
   - Single character substrings (\(dp[i][i] = true\)).
   - Two-character substrings if both characters are equal.
3. Fill the table for substrings longer than two characters using the relation:
   \[
dp[i][j] = (s[i] == s[j]) \land dp[i+1][j-1]
   \]
4. Track the longest palindrome.

### Complexity
- **Time Complexity**: \(O(n^2)\)
- **Space Complexity**: \(O(n^2)\)

---

## 3. Expand Around Center Approach (Substring)

### Explanation
A palindrome mirrors around its center. This approach considers each character (and the gap between every two characters) as a potential center and expands outward to find palindromes.

### Steps
1. Iterate through each character in the string, considering it as a center.
2. Expand outward while the characters on both sides match.
3. Track the longest palindrome found during the expansion.

### Complexity
- **Time Complexity**: \(O(n^2)\)
- **Space Complexity**: \(O(1)\)

---

## 4. Manacher's Algorithm (Substring)

### Explanation
Manacher's Algorithm finds the longest palindromic substring in linear time by transforming the string to handle even-length palindromes uniformly.

### Steps
1. Preprocess the string to insert special characters (e.g., `#`) between every character and at the ends.
2. Use a helper array to store the radius of the palindrome centered at each character.
3. Use a center-expansion technique while maintaining a right boundary to optimize the search.
4. Extract the longest palindrome from the helper array.

### Complexity
- **Time Complexity**: \(O(n)\)
- **Space Complexity**: \(O(n)\)

---

## 5. Dynamic Programming Approach (Subsequence)

### Explanation
For finding the longest palindromic subsequence, a 2D table is used where `dp[i][j]` represents the length of the longest palindromic subsequence in `s[i:j+1]`.

### Steps
1. Initialize single-character substrings as palindromes of length 1 (\(dp[i][i] = 1\)).
2. Use the recurrence relation:
   - If `s[i] == s[j]`:
     \[
dp[i][j] = dp[i+1][j-1] + 2
     \]
   - Otherwise:
     \[
dp[i][j] = \max(dp[i+1][j], dp[i][j-1])
     \]
3. Fill the table diagonally.
4. The value at `dp[0][n-1]` gives the length of the longest palindromic subsequence.

### Complexity
- **Time Complexity**: \(O(n^2)\)
- **Space Complexity**: \(O(n^2)\)

---

## Comparison of Approaches

| Approach                     | Type            | Time Complexity | Space Complexity |
|------------------------------|-----------------|-----------------|------------------|
| Brute Force                  | Substring       | \(O(n^3)\)      | \(O(1)\)         |
| Dynamic Programming          | Substring       | \(O(n^2)\)      | \(O(n^2)\)       |
| Expand Around Center         | Substring       | \(O(n^2)\)      | \(O(1)\)         |
| Manacher's Algorithm         | Substring       | \(O(n)\)        | \(O(n)\)         |
| Dynamic Programming (Subseq) | Subsequence     | \(O(n^2)\)      | \(O(n^2)\)       |

---

## Notes
- The choice of approach depends on the specific requirements (substring vs. subsequence) and constraints of the problem.
- Manacher's Algorithm is optimal for substrings but is complex to implement.
- The Dynamic Programming approach is versatile and works for both substrings and subsequences, making it a reliable choice for most scenarios.

