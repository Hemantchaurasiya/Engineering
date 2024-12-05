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

