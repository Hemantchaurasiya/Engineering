## Remove duplicate from sorted list

```java
public ListNode deleteDuplicates(ListNode head) {
    if (head == null || head.next == null) return head;
    ListNode prev = head;
    ListNode curr = head.next;
    while(curr != null){
        if (prev.val != curr.val){
            prev.next = curr;
            prev = curr;
        }
        curr = curr.next;
    }
    prev.next = null;
    return head;
}
```