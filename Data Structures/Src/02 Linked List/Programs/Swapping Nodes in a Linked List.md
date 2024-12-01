## Swapping Nodes in a Linked List

```java
class Solution {
    public int getLength(ListNode head){
        int length = 0;
        while(head != null){
            length++;
            head = head.next;
        }
        return length;
    }
    public ListNode[] findKthAndLastKthNode(ListNode head,int k, int lastK){
        ListNode firstKthNode = null,lastKthNode = null;
        int n = Math.max(k,lastK);
        for (int i=1;i<=n;i++){
            if (i == k) firstKthNode = head;
            else if (i == lastK) lastKthNode = head;
            head = head.next;
        }
        return new ListNode[] { firstKthNode,lastKthNode };
    }
    public ListNode swapNodes(ListNode head, int k) {
        int length = getLength(head);
        int lastK = (length-k) + 1;
        ListNode[] res = findKthAndLastKthNode(head,k,lastK);
        if (res[0] != null && res[1] != null){
            int temp = res[0].val;
            res[0].val = res[1].val;
            res[1].val = temp;
        }
        return head;
    }
}
```

```java
class Solution {
    public ListNode swapNodes(ListNode head, int k) {
        if (head == null) {
            return null;
        }

        ListNode P1 = null; // Pointer for the k-th node from the start
        ListNode P2 = null; // Pointer for the k-th node from the end
        ListNode temp = head;

        while (temp != null) {
            if (P2 != null) {
                P2 = P2.next; // Advance P2 after the first k nodes
            }

            k--;
            if (k == 0) {
                P1 = temp; // Assign P1 to the k-th node from the start
                P2 = head; // Start P2 to find the k-th node from the end
            }

            temp = temp.next;
        }

        // Swap the values of the two nodes
        int tempVal = P1.val;
        P1.val = P2.val;
        P2.val = tempVal;

        return head;
    }
}

```