### Reverse Linked List

```java
public ListNode reverseList(ListNode head) {
        if (head == null){
            return null;
        }
        ListNode prevNode = null;
        ListNode currNode = head;
        ListNode nextNode = null;

        while (currNode != null) {
            nextNode = currNode.next;
            currNode.next = prevNode;
            prevNode = currNode;
            currNode = nextNode;
        }
        return prevNode;
    }
```

### Using Recursion
```java
public ListNode reverseList(ListNode head) {
        if (head == null) {
            return null;
        }
        return reverse(null, head);
}

public ListNode reverse(ListNode prevNode, ListNode currNode){
        if (currNode == null){
            return prevNode;
        }
        ListNode nextNode = currNode.next;
        currNode.next = prevNode;
        return reverse(currNode, nextNode);
}
```
