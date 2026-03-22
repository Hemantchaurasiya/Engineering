## **Java Collections – Mock Interview: Round 2**

### **Interviewer:** We’ll go a little deeper into edge cases, performance trade-offs, and practical application.

---

### **Q1. [Custom Comparator + TreeMap]**

**Scenario**: You’re creating a leaderboard system for a game. Players are ranked by score, and if scores are tied, by registration time.

**Question**:

How would you implement this ranking system using Collections? Which collection would you use to sort and retrieve top N players efficiently?

---

### **Q2. [HashMap Collisions]**

**Scenario**: You notice your application is experiencing performance drops when using `HashMap`. After profiling, you suspect poor hash distribution in custom keys.

**Question**:

What might be the cause? How do you ensure a well-distributed custom hash function for keys?

---

### **Q3. [Multimap Simulation]**

**Scenario**: You are tagging photos in a photo management app. One photo can have multiple tags, and one tag can be on multiple photos.

**Question**:

How would you simulate a **Multimap** in Java? What collection(s) would you use?

---

### **Q4. [Concurrent Access + BlockingQueue]**

**Scenario**: You’re developing a real-time log processor where multiple producers push logs and a single consumer writes them to disk.

**Question**:

Which Java collection or utility class would you choose? How does it handle thread safety?

---

### **Q5. [Finding First Unique Element]**

**Scenario**: In a stream of user IDs, you want to **return the first unique user ID** at any time (real-time processing).

**Question**:

Which collections would you use to maintain insertion order and uniqueness simultaneously?

---

### **Q6. [Bidirectional Mapping]**

**Scenario**: You are building a login system where a username maps to a session ID, and you also want to look up usernames by session ID.

**Question**:

Which collection(s) would you use to achieve constant time lookup in both directions?

---

### **Q7. [Versioned Data Store]**

**Scenario**: Implement a key-value store where the same key can have **multiple versions** (timestamped values).

**Question**:

How would you design this structure using collections? How would you retrieve the latest or a specific version?

---

### **Q8. [Streaming Duplicate Detection]**

**Scenario**: You’re analyzing a real-time feed of events and want to alert when a duplicate event is seen **within a 5-minute window**.

**Question**:

How would you do this using Java Collections? Consider memory efficiency.

---

### **Q9. [Trie Alternative with Collections]**

**Scenario**: You need to implement prefix-based search, but cannot use a Trie class.

**Question**:

How would you replicate basic Trie behavior using Maps and Collections?

---

### **Q10. [Fail-Fast Behavior]**

**Scenario**: In one part of your code, an `ArrayList` is being modified from one thread and read from another, causing `ConcurrentModificationException`.

**Question**:

What’s the root cause? What are two ways to safely resolve this?
