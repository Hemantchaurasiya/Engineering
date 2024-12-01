## Delete node in a link list

```java
public void deleteNode(ListNode node) {
    while(node.next != null){
        node.val = node.next.val;
        if (node.next.next == null){
            node.next = null;
        } else {
            node = node.next;
        }
    }
}
```

```java
public void deleteNode(ListNode node) {
    while(node.next.next != null){
        node.val = node.next.val;
        node = node.next;
    }
    node.val = node.next.val;
    node.next = null;
}
```