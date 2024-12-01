## Odd even linked list

```java
public ListNode oddEvenList(ListNode head) {
    if (head == null || head.next == null){
        return head;
    }
    ListNode odd = head;
    ListNode even = head.next;
    ListNode temp = head.next;
    while (even != null && even.next != null){
        odd.next = odd.next.next;
        even.next = even.next.next;
        odd = odd.next;
        even = even.next;
    }
    odd.next = temp;
    if (even!=null) {
        even.next = null;
    }
    return head;
}
```