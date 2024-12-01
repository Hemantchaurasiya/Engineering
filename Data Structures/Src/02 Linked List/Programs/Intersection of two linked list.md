## Intersection of two linked list

```java
public ListNode getIntersectionNode(ListNode headA, ListNode headB) {
    ListNode temp = headB;
    while (headA != null){
        while (temp != null){
            if (temp == headA) return temp;
            temp = temp.next;
        }
        temp = headB;
        headA = headA.next;
    }
    return null;
}
```

```java
public ListNode getIntersectionNode(ListNode headA, ListNode headB) {
    Set<ListNode> set = new HashSet<>();
    while (headA != null){
        set.add(headA);
        headA = headA.next;
    }
    while (headB != null){
        if (set.contains(headB)){
            return headB;
        }
        headB = headB.next;
    }
    return null;
}
```

```java
public ListNode getIntersectionNode(ListNode headA, ListNode headB) {
    int length1 = getLength(headA);
    int length2 = getLength(headB);
    int res = Math.abs(length1 - length2);
    if (length1 < length2){
        while(res > 0){
            res--;
            headB = headB.next;
        }
    } else {
        while(res > 0){
            res--;
            headA = headA.next;
        }
    }
    while(headA != null){
        if (headA == headB) return headA;
        headA = headA.next;
        headB = headB.next;
    }
    return null;
}

public int getLength(ListNode head){
    int length = 0;
    while (head != null){
        length++;
        head = head.next;
    }
    return length;
}
```