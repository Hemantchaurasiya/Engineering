## Middle of the link list

```java
public ListNode middleNode(ListNode head) {
    ListNode sp = head, fp = head;
    while (fp != null && fp.next != null){
        sp = sp.next;
        fp = fp.next.next;
    }
    return sp;
}
```