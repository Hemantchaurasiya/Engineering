### Merge Two Sorted Lists

```java
public ListNode mergeTwoLists(ListNode list1, ListNode list2) {
        if (list1 == null) {
            return list2;
        }
        if (list2 == null) {
            return list1;
        }
        ListNode head = new ListNode(0);
        ListNode resultNode = head;
        while (list1 != null && list2 != null) {
            if (list1.val <= list2.val){
                head.next = list1;
                head = head.next;
                list1 = list1.next;
            } else {
                head.next = list2;
                head = head.next;
                list2 = list2.next;
            }
        }
        while (list1 != null) {
            head.next = list1;
            head = head.next;
            list1 = list1.next;
        }
        while (list2 != null) {
            head.next = list2;
            head = head.next;
            list2 = list2.next;
        }
        return resultNode.next;
    }
```
