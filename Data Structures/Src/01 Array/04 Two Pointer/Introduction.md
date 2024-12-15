### Two Pointer
- Two Pointer on Arrays
    - [Valid Palindrome](https://github.com/Hemantchaurasiya/Engineering/blob/Coding_Patterns/Data%20Structures/Src/01%20Array/04%20Two%20Pointer/Programs/Valid%20Palindrome.md)
    - [Valid Palindrome II](https://github.com/Hemantchaurasiya/Engineering/blob/Coding_Patterns/Data%20Structures/Src/01%20Array/04%20Two%20Pointer/Programs/Valid%20Palindrome%20II.md)
    - Is Subsequence
    - [Two Sum II - Input Array Is Sorted](https://github.com/Hemantchaurasiya/Engineering/blob/Coding_Patterns/Data%20Structures/Src/01%20Array/04%20Two%20Pointer/Programs/Two%20Sum%20II%20Input%20Array%20Is%20Sorted.md)
    - [3Sum](https://github.com/Hemantchaurasiya/Engineering/blob/Coding_Patterns/Data%20Structures/Src/01%20Array/04%20Two%20Pointer/Programs/3Sum.md)
    - [Move Zeroes](https://github.com/Hemantchaurasiya/Engineering/blob/Coding_Patterns/Data%20Structures/Src/01%20Array/04%20Two%20Pointer/Programs/Move%20Zeroes.md)
    - [Trapping Rain Water](https://github.com/Hemantchaurasiya/Engineering/blob/Coding_Patterns/Data%20Structures/Src/01%20Array/04%20Two%20Pointer/Programs/Trapping%20Rain%20Water.md)
    - [Container With Most Water](https://github.com/Hemantchaurasiya/Engineering/blob/Coding_Patterns/Data%20Structures/Src/01%20Array/04%20Two%20Pointer/Programs/Container%20With%20Most%20Water.md)

    - Two Sum
    - [Remove Duplicates](https://github.com/Hemantchaurasiya/Engineering/blob/Coding_Patterns/Data%20Structures/Src/01%20Array/04%20Two%20Pointer/Programs/Remove%20Duplicates.md)
    - Squaring a Sorted Array
    - [Triplet Sum Close to Target](https://github.com/Hemantchaurasiya/Engineering/blob/Coding_Patterns/Data%20Structures/Src/01%20Array/04%20Two%20Pointer/Programs/Triplet%20Sum%20Close%20to%20Target%20.md)
    - [Triplets with Smaller Sum](https://github.com/Hemantchaurasiya/Engineering/blob/Coding_Patterns/Data%20Structures/Src/01%20Array/04%20Two%20Pointer/Programs/Triplets%20with%20Smaller%20Sum.md)
    - Subarrays with Product Less than a Target
    - Dutch National Flag Problem
    - Quadruple Sum to Target
    - [Comparing Strings containing Backspaces](https://github.com/Hemantchaurasiya/Engineering/blob/Coding_Patterns/Data%20Structures/Src/01%20Array/04%20Two%20Pointer/Programs/Comparing%20Strings%20containing%20Backspaces.md)
    - [Minimum Window Sort](https://github.com/Hemantchaurasiya/Engineering/blob/Coding_Patterns/Data%20Structures/Src/01%20Array/04%20Two%20Pointer/Programs/Minimum%20Window%20Sort.md)

    - [Sort Array by Parity](https://github.com/Hemantchaurasiya/Engineering/blob/Coding_Patterns/Data%20Structures/Src/01%20Array/04%20Two%20Pointer/Programs/Sort%20Array%20by%20Parity.md)
    - Sort Array by Parity II
    - [Rotate Array](https://github.com/Hemantchaurasiya/Engineering/blob/Coding_Patterns/Data%20Structures/Src/01%20Array/04%20Two%20Pointer/Programs/Rotate%20Array.md)
    - Partition Array According to Given Pivot
    - Sort Colors
    - Next Permutation
    

- Two Pointer on Strings
    - DI String Match
    - Reverse Words in a String

# Two Pointer Technique

## 1. What is the Two Pointer Technique?
The two-pointer technique is an algorithmic approach commonly used to solve problems on arrays and strings. It involves using two pointers to traverse the data structure in a specific manner, optimizing the computation for problems that would otherwise require nested loops.

The two pointers can:
- Start at opposite ends and move toward each other.
- Start at the same position and move in the same or opposite directions.
- Be positioned dynamically based on problem requirements.

---

## 2. How to Identify Problems Suitable for Two Pointer Technique
You can consider using the two-pointer technique if:
- The problem involves a sorted array or can be sorted.
- You need to find pairs, triplets, or subsequences that satisfy a condition (e.g., sum equals a target).
- You need to count or find unique subsequences, duplicate elements, or palindromes.
- Sliding windows or overlapping intervals need to be processed efficiently.

Examples:
- Finding a pair with a specific sum in a sorted array.
- Removing duplicates in-place from a sorted array.
- Merging two sorted arrays.

---

## 3. Where to Use Two Pointer Technique
The technique is widely used in:
- Searching problems: E.g., finding pairs with a specific sum.
- Sorting-related optimizations: E.g., merging sorted arrays.
- String problems: E.g., checking if a string is a palindrome.
- Interval-based problems: E.g., overlapping intervals.
- Sliding window problems: E.g., finding the smallest or largest subarray satisfying a condition.

---

## 4. Types of Two Pointer Techniques
1. **Opposite Ends Approach**
   - Pointers start at the beginning and end of an array and move toward each other.
   - Example: Checking if a string is a palindrome.

2. **Same Direction Approach**
   - Both pointers start at the same position and move forward.
   - Example: Removing duplicates in a sorted array.

3. **Sliding Window Technique**
   - One pointer defines the start and the other defines the end of a dynamic window.
   - Example: Finding the maximum sum subarray of a fixed size.

4. **Dynamic Pointers**
   - Pointers adjust their positions dynamically based on problem requirements.
   - Example: Partitioning an array around a pivot.

---

## 5. Algorithm of Two Pointer Technique
1. **Initialization:** Set the two pointers (e.g., `left` and `right`) to appropriate starting positions.
2. **Traversal:** Use a loop to move the pointers toward the target condition (e.g., meet in the middle or traverse the entire array).
3. **Condition Checking:** Check if the current pointers satisfy the condition.
4. **Update:** Adjust the pointers based on the condition.
5. **Termination:** Stop the traversal when the pointers meet or cover the entire data structure.

---

## 6. Sample Standard Problems

### Problem 1: Find a Pair with Target Sum
**Problem:** Given a sorted array and a target sum, find a pair whose sum equals the target.

**Approach:**
- Initialize two pointers: `left` at the start and `right` at the end.
- If `arr[left] + arr[right]` equals the target, return the pair.
- If the sum is less than the target, increment `left`.
- If the sum is greater, decrement `right`.

**Code Solution:**
```java
public static int[] findPairWithTargetSum(int[] arr, int target) {
    int left = 0, right = arr.length - 1;
    while (left < right) {
        int sum = arr[left] + arr[right];
        if (sum == target) {
            return new int[]{arr[left], arr[right]};
        } else if (sum < target) {
            left++;
        } else {
            right--;
        }
    }
    return new int[]{}; // No pair found
}
```

### Problem 2: Remove Duplicates from Sorted Array
**Problem:** Remove duplicates in-place from a sorted array and return the new length.

**Approach:**
- Use two pointers: `i` for the unique element position and `j` to iterate through the array.
- Copy non-duplicate elements to position `i`.

**Code Solution:**
```java
public static int removeDuplicates(int[] nums) {
    if (nums.length == 0) return 0;
    int i = 0;
    for (int j = 1; j < nums.length; j++) {
        if (nums[j] != nums[i]) {
            i++;
            nums[i] = nums[j];
        }
    }
    return i + 1;
}
```

---

## 7. Revision Points of Two Pointer Technique
- Understand the types of problems that suit the technique.
- Practice with sorted and unsorted data structures.
- Learn the different variations: opposite ends, same direction, sliding windows, and dynamic adjustments.
- Focus on edge cases like empty arrays, duplicates, and out-of-bound conditions.
- Optimize time and space complexity through efficient pointer movement.

------------------------------------------------------------------------------------------------------------------------
# Two Pointer Techniques (Types of two pointer)

Two-pointer techniques are efficient methods for solving array and string problems. This document provides an overview of the types, their use cases, and example problems with Java implementations.

---

## **1. Opposite Ends Approach**

### **Description**
- Two pointers start at the opposite ends of an array or string and move toward each other.

### **When to Use**
- Symmetrical problems, such as comparing elements at both ends of the array.
- Suitable for palindrome checks or two-sum problems in sorted arrays.

### **Example Problem**: Checking if a string is a palindrome
```java
public class PalindromeCheck {
    public static boolean isPalindrome(String s) {
        int left = 0, right = s.length() - 1;
        while (left < right) {
            if (s.charAt(left) != s.charAt(right)) {
                return false;
            }
            left++;
            right--;
        }
        return true;
    }

    public static void main(String[] args) {
        System.out.println(isPalindrome("madam")); // true
        System.out.println(isPalindrome("hello")); // false
    }
}
```

### **Other Problems**
- Two Sum in a sorted array.
- Container with Most Water.

---

## **2. Same Direction Approach**

### **Description**
- Both pointers start at the same position and move in the same direction.

### **When to Use**
- Problems requiring linear scans with pointer updates.
- Useful for processing unique or sequential data.

### **Example Problem**: Removing duplicates from a sorted array
```java
public class RemoveDuplicates {
    public static int removeDuplicates(int[] nums) {
        int uniqueIndex = 1; // Pointer for the position to replace.
        for (int i = 1; i < nums.length; i++) {
            if (nums[i] != nums[i - 1]) {
                nums[uniqueIndex] = nums[i];
                uniqueIndex++;
            }
        }
        return uniqueIndex;
    }

    public static void main(String[] args) {
        int[] nums = {1, 1, 2, 3, 3};
        int length = removeDuplicates(nums);
        for (int i = 0; i < length; i++) {
            System.out.print(nums[i] + " ");
        }
    }
}
```

### **Other Problems**
- Merging two sorted arrays.
- Partitioning arrays by condition.

---

## **3. Sliding Window Technique**

### **Description**
- Two pointers define the start and end of a window that slides over the array.

### **When to Use**
- Problems involving subarrays, substrings, or ranges.
- Ideal for problems with fixed or variable-sized windows.

### **Example Problem**: Maximum sum subarray of a fixed size
```java
public class MaxSumSubarray {
    public static int maxSumSubarray(int[] nums, int k) {
        int maxSum = 0, windowSum = 0;
        for (int i = 0; i < nums.length; i++) {
            windowSum += nums[i];
            if (i >= k - 1) {
                maxSum = Math.max(maxSum, windowSum);
                windowSum -= nums[i - (k - 1)];
            }
        }
        return maxSum;
    }

    public static void main(String[] args) {
        int[] nums = {2, 1, 5, 1, 3, 2};
        int k = 3;
        System.out.println(maxSumSubarray(nums, k)); // 9
    }
}
```

### **Other Problems**
- Longest substring without repeating characters.
- Smallest subarray with a given sum.

---

## **4. Dynamic Pointers**

### **Description**
- Pointers adjust dynamically based on conditions or constraints.

### **When to Use**
- Partitioning problems or dynamic pointer movements.
- Suitable for array rearrangements or partition-based algorithms.

### **Example Problem**: Partitioning an array around a pivot
```java
import java.util.Arrays;

public class PartitionArray {
    public static void partition(int[] nums, int pivot) {
        int left = 0, right = nums.length - 1;
        while (left <= right) {
            if (nums[left] < pivot) {
                left++;
            } else if (nums[right] >= pivot) {
                right--;
            } else {
                // Swap elements
                int temp = nums[left];
                nums[left] = nums[right];
                nums[right] = temp;
                left++;
                right--;
            }
        }
    }

    public static void main(String[] args) {
        int[] nums = {5, 2, 9, 1, 5, 6};
        partition(nums, 5);
        System.out.println(Arrays.toString(nums)); // [2, 1, 5, 5, 9, 6]
    }
}
```

### **Other Problems**
- Dutch National Flag problem.
- Quicksort partitioning.

---

## **Summary**
| Technique              | When to Use                                        | Example Problems                                 |
|------------------------|----------------------------------------------------|------------------------------------------------|
| **Opposite Ends**      | Symmetry in the problem.                           | Palindrome check, Two Sum in sorted array.     |
| **Same Direction**     | Sequential processing in one pass.                 | Remove duplicates, Merge sorted arrays.        |
| **Sliding Window**     | Problems with subarray or substring properties.    | Max sum subarray, Longest substring problems.  |
| **Dynamic Pointers**   | Partitioning or conditionally adjusting pointers.  | Partition arrays, Dutch National Flag.         |

---
Mastering these patterns will make solving array- and string-based problems much easier!



