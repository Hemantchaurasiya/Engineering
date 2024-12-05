# Add Two Numbers (LeetCode Problem)

### Problem Description

You are given two non-empty linked lists representing two non-negative integers. The digits are stored in **reverse order**, and each of their nodes contains a single digit. Add the two numbers and return the sum as a linked list.

You may assume the two numbers do not contain any leading zero, except for the number 0 itself.

---

### Example

#### Input:

```text
l1 = [2, 4, 3]
l2 = [5, 6, 4]
```

#### Output:

```text
[7, 0, 8]
```

#### Explanation:

- `342 + 465 = 807`
- The sum is represented as `[7, 0, 8]` in reverse order.

---

## Key Concepts

1. **Linked List Representation**:
   Each digit is stored in a node, with the least significant digit (units place) at the head.

2. **Carry Handling**:
   When summing the digits at corresponding positions, a carry may propagate to the next position if the sum exceeds 9.

3. **Edge Cases**:

   - Different lengths of the two linked lists.
   - Carry at the final node, requiring an additional node.

---

## Approach 1: Iterative Solution

### Algorithm:

1. Create a dummy node to simplify list manipulation.
2. Use two pointers, `p1` for `l1` and `p2` for `l2`, to traverse the lists.
3. Maintain a variable `carry` initialized to 0.
4. At each step:
   - Sum the values of the current nodes of `l1` and `l2` (if they exist) and the `carry`.
   - Compute the new digit (`sum % 10`) and update `carry` (`sum // 10`).
   - Append the new digit to the result list.
5. If `carry` is non-zero after traversing both lists, add a final node with the value of `carry`.

### Code:

```java
public ListNode addTwoNumbers(ListNode l1, ListNode l2) {
    ListNode dummy = new ListNode(0); // Dummy node to simplify handling
    ListNode current = dummy;
    int carry = 0;

    while (l1 != null || l2 != null || carry != 0) {
        int val1 = (l1 != null) ? l1.val : 0;
        int val2 = (l2 != null) ? l2.val : 0;

        int sum = val1 + val2 + carry;
        carry = sum / 10;
        current.next = new ListNode(sum % 10);

        current = current.next;
        if (l1 != null) l1 = l1.next;
        if (l2 != null) l2 = l2.next;
    }

    return dummy.next;
}
```

### Complexity:

- **Time Complexity**: , where  and  are the lengths of `l1` and `l2`.
- **Space Complexity**: , for the output linked list.

---

## Approach 2: Recursive Solution

### Algorithm:

1. Use recursion to traverse the linked lists.
2. Add corresponding digits and propagate the carry to the next recursive call.
3. Base case:
   - If both lists are null and carry is 0, return null.
4. Recursive step:
   - Compute the sum of the current nodes and the carry.
   - Create a new node for the current digit (`sum % 10`).
   - Recurse with the next nodes and updated carry.

### Code:

```java
public ListNode addTwoNumbersRecursive(ListNode l1, ListNode l2, int carry) {
    if (l1 == null && l2 == null && carry == 0) return null;

    int val1 = (l1 != null) ? l1.val : 0;
    int val2 = (l2 != null) ? l2.val : 0;
    int sum = val1 + val2 + carry;

    ListNode current = new ListNode(sum % 10);
    current.next = addTwoNumbersRecursive(
        (l1 != null) ? l1.next : null,
        (l2 != null) ? l2.next : null,
        sum / 10
    );

    return current;
}

public ListNode addTwoNumbers(ListNode l1, ListNode l2) {
    return addTwoNumbersRecursive(l1, l2, 0);
}
```

### Complexity:

- **Time Complexity**: , due to the recursive calls.
- **Space Complexity**: , for the recursion stack.

---

## Approach 3: Using Stacks (Handles Forward Order)

If the input lists are in **forward order** (e.g., `[3, 4, 2]` and `[4, 6, 5]`):

1. Push all nodes of `l1` and `l2` onto two stacks.
2. Pop elements from both stacks, compute the sum, and handle the carry.
3. Use a dummy node and build the result list in reverse.

### Code:

```java
public ListNode addTwoNumbersForward(ListNode l1, ListNode l2) {
    Stack<Integer> stack1 = new Stack<>();
    Stack<Integer> stack2 = new Stack<>();

    while (l1 != null) {
        stack1.push(l1.val);
        l1 = l1.next;
    }
    while (l2 != null) {
        stack2.push(l2.val);
        l2 = l2.next;
    }

    int carry = 0;
    ListNode head = null;

    while (!stack1.isEmpty() || !stack2.isEmpty() || carry != 0) {
        int val1 = (!stack1.isEmpty()) ? stack1.pop() : 0;
        int val2 = (!stack2.isEmpty()) ? stack2.pop() : 0;
        int sum = val1 + val2 + carry;
        carry = sum / 10;

        ListNode newNode = new ListNode(sum % 10);
        newNode.next = head;
        head = newNode;
    }

    return head;
}
```

### Complexity:

- **Time Complexity**: , where  and  are the lengths of `l1` and `l2`.
- **Space Complexity**: , for the stacks.

---

## Summary Table of Approaches

| Approach     | Time Complexity | Space Complexity | Notes                                        |
| ------------ | --------------- | ---------------- | -------------------------------------------- |
| Iterative    |                 |                  | Most common and efficient for reverse order. |
| Recursive    |                 |                  | Elegant but uses extra stack space.          |
| Using Stacks |                 |                  | Ideal for forward-order input.               |

---

### Notes:

- Handle edge cases like empty lists or a result requiring an extra carry.
- Pay attention to memory usage in recursive approaches, especially with deep recursion.

can you please create a downloadable readme file for same?

