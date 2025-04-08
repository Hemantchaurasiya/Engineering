## **Java Collections – Mock Interview: Round 3**

### **Interviewer:** Let’s dive deeper into performance tuning, real-time systems, and creative use of Java Collections.

---

### **Q1. [Top-K Frequent Elements]**

**Scenario**: You’re building a search analytics dashboard that shows the **top 10 most searched keywords** in real-time.

**Question**:

Which collections would you use to track frequencies and efficiently extract the top 10 keywords?

---

### **Q2. [Immutable Collections Use Case]**

**Scenario**: You provide a configuration service that returns a list of allowed file extensions. It should not be modifiable by any client.

**Question**:

How would you protect this list from modification? What options does Java Collections offer?

---

### **Q3. [Memory-Efficient Access Log]**

**Scenario**: You're storing **access logs** for every user session, but the logs grow rapidly. You want to keep only the **last 1000 logs per user**.

**Question**:

Which collection(s) would you use to keep insertion order and limit size efficiently?

---

### **Q4. [Inventory System with Restock]**

**Scenario**: You’re managing a warehouse system where each product has a quantity, and you restock when quantity drops below a threshold.

**Question**:

How would you track and monitor low-stock products efficiently using collections?

---

### **Q5. [Undo Operation History]**

**Scenario**: You’re implementing an editor with **undo and redo** functionality.

**Question**:

Which collections are ideal for tracking operations in both directions? Why?

---

### **Q6. [Group Anagrams]**

**Scenario**: You have a list of words and want to group them by anagram sets. For example, “eat”, “tea”, and “ate” should be grouped together.

**Question**:

Which collection would you use to implement this grouping? How would you generate the key?

---

### **Q7. [Fixed Time Sliding Window]**

**Scenario**: You need to keep track of all login attempts in the last **60 seconds** for rate-limiting purposes.

**Question**:

How would you implement this with Java Collections? Which structure would be best suited?

---

### **Q8. [Map Performance Tradeoff]**

**Scenario**: You’re using a `TreeMap` for a leaderboard, but you notice slower performance with frequent inserts and lookups.

**Question**:

Would switching to `HashMap` help? What trade-off would you be making?

---

### **Q9. [Circular Buffer for Metrics]**

**Scenario**: You’re collecting CPU usage metrics every second and want to store the **last 60 readings**.

**Question**:

Which collection(s) would you use to implement this circular buffer?

---

### **Q10. [Sorting + Stability]**

**Scenario**: You’re sorting a list of transactions by date, but when dates are the same, you want to preserve the original insertion order.

**Question**:

How can you achieve this using Collections? Which sort is stable in Java?
