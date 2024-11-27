## Reverse a link list

Explanation
Initialization:

prev is set to null (the end of the reversed list).
curr is set to head (the current node being processed).
Iteration:

Save the next node of curr in temp.
Reverse the link: point curr.next to prev.
Move prev and curr one step forward.
Return:

After the loop completes, prev will point to the new head of the reversed list.

```java
public ListNode reverseList(ListNode head) {
    ListNode prev = null;
    ListNode curr = head;
    while(curr != null){
        ListNode temp = curr.next;
        curr.next = prev;
        prev = curr;
        curr = temp;
    }
    return prev;
}
```
