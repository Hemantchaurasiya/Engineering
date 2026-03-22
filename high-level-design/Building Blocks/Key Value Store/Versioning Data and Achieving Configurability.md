## Versioning Data and Achieving Configurability
Learn how to resolve conflicts via versioning and how to make the key-value storage into a configurable service.

### Data versioning
When network partitions and node failures occur during an update, an object’s version history might become fragmented. As a result, it requires a reconciliation effort on the part of the system. It’s necessary to build a way that explicitly accepts the potential of several copies of the same data so that we can avoid the loss of any updates. It’s critical to realize that some failure scenarios can lead to multiple copies of the same data in the system. So, these copies might be the same or divergent. Resolving the conflicts among these divergent histories is essential and critical for consistency purposes.

To handle inconsistency, we need to maintain causality between the events. We can do this using the timestamps and update all conflicting values with the value of the latest request. But time isn’t reliable in a distributed system, so we can’t use it as a deciding factor.

Another approach to maintaining causality effectively is by using vector clocks. A vector clock is a list of (node, counter) pairs. There’s a single vector clock for every version of an object. If two objects have different vector clocks, we’re able to tell whether they’re causally related or not (more on this in a bit). Unless one of the two changes is reconciled, the two are deemed at odds.

### Modify the API design
We talked about how we can decide if two events are causally related or not using a vector clock value. For this, we need information about which node performed the operation before and what its vector clock value was. This is the context of an operation. So, we’ll modify our API design as follows.

The API call to get a value should look like this:
```java
get(key)
```


Parameter: key
Description: This is the key against which we want to get value.

We return an object or a collection of conflicting objects along with a context. The context holds encoded metadata about the object, including details such as the object’s version.

The API call to put the value into the system should look like this:
```java
put(key, context, value)
```

Parameter: key
Description: This is the key against which we have to store value.

Parameter: context
Description: This holds the metadata for each object.

Parameter: value
Description: 
This is the object that needs to be stored against the key.

The function finds the node where the value should be placed on the basis of the key and stores the value associated with it. The context is returned by the system after the get operation. If we have a list of objects in context that raises a conflict, we’ll ask the client to resolve it.

To update an object in the key-value store, the client must give the context. We determine version information using a vector clock by supplying the context from a previous read operation. If the key-value store has access to several branches, it provides all objects at the leaf nodes, together with their respective version information in context, when processing a read request. Reconciling disparate versions and merging them into a single new version is considered an update.

Note: This process of resolving conflicts is comparable to how it’s done in Git. If Git is able to merge multiple versions into one, merging is performed automatically. It’s up to the client (the developer) to resolve conflicts manually if automatic conflict resolution is not possible. Along the same lines, our system can try automatic conflict resolution and, if not possible, ask the application to provide a final resolved value.

Vector clock usage example#
Let’s consider an example. Say we have a write operation request. Node A handles the first version of the write request, E1. The corresponding vector clock has node information and its counter—that is, 
[
A
,
1
]
[A,1]
. Node 
A
A
 handles another write for the same object on which the previous write was performed. So, for 
E
2
E2
, we have 
[
A
,
2
]
[A,2]
. 
E
1
E1
 is no longer required because 
E
2
E2
 was updated on the same node. 
E
2
E2
 reads the changes made by 
E
1
E1
, and then new changes are made. Suppose a network partition happens. Now, the request is handled by two different nodes, 
B
B
 and 
C
C
. The context with updated versions, which are 
E
3
E3
, 
E
4
E4
, and their related clocks, which are 
(
[
A
,
2
]
,
[
B
,
1
]
)
([A,2],[B,1])
 and 
(
[
A
,
2
]
,
[
C
,
1
]
)
([A,2],[C,1])
, are now in the system.

Suppose the network partition is repaired, and the client requests a write again, but now we have conflicts. The context 
(
[
A
,
3
]
,
[
B
,
1
]
,
[
C
,
1
]
)
([A,3],[B,1],[C,1])
 of the conflicts are returned to the client. After the client does reconciliation and 
A
A
 coordinates the write, we have 
E
5
E5
 with the clock 
(
[
A
,
4
]
)
([A,4])
.

### Compromise with vector clocks limitations

The size of vector clocks may increase if multiple servers write to the same object simultaneously. It’s unlikely to happen in practice because writes are typically handled by one of the top 
n
n
 nodes in a preference list.

For example, if there are network partitions or multiple server failures, write requests may be processed by nodes not in the top 
n
n
 nodes in the preference list. As a result we can have a long version like this: 
(
[
A
,
10
]
,
[
B
,
4
]
,
[
C
,
1
]
,
[
D
,
2
]
,
[
E
,
1
]
,
[
F
,
3
]
,
[
G
,
5
]
,
[
H
,
7
]
,
[
I
,
2
]
,
[
J
,
2
]
,
[
K
,
1
]
,
[
L
,
1
]
)
([A,10],[B,4],[C,1],[D,2],[E,1],[F,3],[G,5],[H,7],[I,2],[J,2],[K,1],[L,1])
. It’s a hassle to store and maintain such a long version history.