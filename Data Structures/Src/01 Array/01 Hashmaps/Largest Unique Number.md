# Largest Unique Number

The **Largest Unique Number** problem involves finding the largest number in an array that appears exactly once.

## Problem Statement
Given an integer array `nums`, return the largest integer that only occurs once. If no integer occurs exactly once, return `-1`.

### Example
#### Input:
```plaintext
nums = [5, 7, 3, 9, 4, 9, 8, 3, 1]
```
#### Output:
```plaintext
8
```
Explanation: The unique numbers are `[5, 7, 4, 8, 1]`, and the largest among them is `8`.

## Approaches
### 1. Using a HashMap
#### Steps:
1. Traverse the array and count the frequency of each number using a HashMap.
2. Traverse the keys of the HashMap to find the largest number with a frequency of `1`.
3. If no number with frequency `1` is found, return `-1`.

#### Code:
```java
import java.util.HashMap;

public class LargestUniqueNumber {
    public static int largestUniqueNumber(int[] nums) {
        HashMap<Integer, Integer> frequencyMap = new HashMap<>();
        
        for (int num : nums) {
            frequencyMap.put(num, frequencyMap.getOrDefault(num, 0) + 1);
        }
        
        int largest = -1;
        for (int num : frequencyMap.keySet()) {
            if (frequencyMap.get(num) == 1) {
                largest = Math.max(largest, num);
            }
        }
        
        return largest;
    }
}
```
#### Complexity:
- **Time Complexity:** O(n), where `n` is the size of the array.
- **Space Complexity:** O(n), for the HashMap.

---

### 2. Using Sorting
#### Steps:
1. Sort the array in descending order.
2. Traverse the sorted array, and check if the current number is unique by comparing it to its neighbors.
3. If a unique number is found, return it.
4. If no unique number is found, return `-1`.

#### Code:
```java
import java.util.Arrays;

public class LargestUniqueNumber {
    public static int largestUniqueNumber(int[] nums) {
        Arrays.sort(nums);
        
        for (int i = nums.length - 1; i >= 0; i--) {
            if ((i == 0 || nums[i] != nums[i - 1]) && (i == nums.length - 1 || nums[i] != nums[i + 1])) {
                return nums[i];
            }
        }
        
        return -1;
    }
}
```
#### Complexity:
- **Time Complexity:** O(n log n), for sorting the array.
- **Space Complexity:** O(1), as no extra data structures are used.

---

### 3. Using Frequency Array (Optimized for Small Range)
#### Assumptions:
- The numbers are within a small, fixed range (e.g., `0 <= nums[i] <= 1000`).

#### Steps:
1. Create a frequency array of size `1001` to store the count of each number.
2. Traverse the input array and populate the frequency array.
3. Traverse the frequency array in reverse to find the largest number with a frequency of `1`.

#### Code:
```java
public class LargestUniqueNumber {
    public static int largestUniqueNumber(int[] nums) {
        int[] frequency = new int[1001];
        
        for (int num : nums) {
            frequency[num]++;
        }
        
        for (int i = 1000; i >= 0; i--) {
            if (frequency[i] == 1) {
                return i;
            }
        }
        
        return -1;
    }
}
```
#### Complexity:
- **Time Complexity:** O(n), where `n` is the size of the array.
- **Space Complexity:** O(1), for the frequency array of fixed size.

---

## Comparison of Approaches
| Approach               | Time Complexity | Space Complexity | Notes                                |
|------------------------|-----------------|------------------|--------------------------------------|
| HashMap               | O(n)            | O(n)             | Handles large ranges of numbers.     |
| Sorting               | O(n log n)      | O(1)             | Simple but slower due to sorting.    |
| Frequency Array       | O(n)            | O(1)             | Optimal for small, fixed ranges.     |

---

## Conclusion
Choose the **HashMap** approach for general cases, the **Sorting** approach for simplicity, and the **Frequency Array** approach for small, fixed ranges of numbers.

