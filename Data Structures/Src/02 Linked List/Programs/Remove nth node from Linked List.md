

```java
public ListNode removeNthFromEnd(ListNode head, int n) {
    if (head == null) return head;
    if (head.next == null) return null;
    int length = length(head);
    n = length - n;
    ListNode prev = null;
    ListNode curr = head;
    if (n == 0){
        return head.next;
    }
    while(n > 0){
        n--;
        prev = curr;
        curr = curr.next;
    }
    prev.next = curr.next;
    return head;
}
public int length(ListNode head){
    int len = 0;
    while(head != null){
        len++;
        head = head.next;
    }
    return len;
}
```