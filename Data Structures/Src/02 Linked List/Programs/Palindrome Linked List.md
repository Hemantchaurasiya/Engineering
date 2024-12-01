## Palindrom link list

```java
public boolean isPalindrome(ListNode head) {
    ListNode sp = head, fp = head;
    if (head.next == null) return true;
    while (fp != null && fp.next != null){
        sp = sp.next;
        fp = fp.next.next;
    }
    ListNode head2 = reverse(sp);
    while(head2 != null){
        if (head.val != head2.val){
            return false;
        }
        head = head.next;
        head2 = head2.next;
    }
    return true;
}

public ListNode reverse(ListNode head){
    ListNode prev = null;
    ListNode curr = head;
    while (curr != null){
        ListNode temp = curr.next;
        curr.next = prev;
        prev = curr;
        curr = temp;
    }
    return prev;
}
```