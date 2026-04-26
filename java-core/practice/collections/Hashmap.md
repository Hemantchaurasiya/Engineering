### **1. Designing a Caching System**

**Scenario:**

You are asked to design a simple caching system where frequent lookups should be fast (O(1)) and duplicates must be avoided.

**Question:**

How would you use a `HashMap` to design a cache that allows fast insert, lookup, and prevents duplicate entries? What would be your approach if the cache also needs to expire entries after a certain time?

---

### **2. Word Frequency Counter**

**Scenario:**

You are building a text analyzer tool. One feature is to count how many times each word appears in a large document.

**Question:**

Which Java collection would you use to implement this? How would you handle the case sensitivity and punctuation?

---

### **3. Detecting Duplicates in an Array**

**Scenario:**

You are given an array of integers. Your task is to find if there are any duplicates.

**Question:**

How can a `HashMap` help you solve this problem in linear time? How would your code look?

---

### **4. LRU Cache Implementation**

**Scenario:**

You're asked to implement an **LRU Cache** from scratch.

**Question:**

How will you use a `HashMap` and a `Doubly Linked List` together to achieve O(1) for both `get()` and `put()` operations?

---

### **5. Grouping Anagrams**

**Scenario:**

Given a list of strings, you need to group all the anagrams together.

**Question:**

How can a `HashMap` help you group anagrams efficiently? What would you use as the key in the map?

---

### **6. Custom Object as Key**

**Scenario:**

You have a `Student` class (with fields like `id`, `name`) and want to use it as a key in a `HashMap`.

**Question:**

What steps must you take to ensure correct behavior of the `HashMap` when using `Student` as a key?

---

### **7. Storing Employee Departments**

**Scenario:**

You're managing a company system where each department has a list of employees.

**Question:**

How can you use a `HashMap<String, List<Employee>>` to represent this structure? How would you add an employee to a department?

---

### **8. Finding First Non-Repeating Character**

**Scenario:**

Given a string, return the first character that does not repeat.

**Question:**

How can a `HashMap` help you solve this in one pass?

---

### **9. Implementing a Phone Directory**

**Scenario:**

You are building a contact directory where names map to phone numbers. Duplicate names are allowed, but each name can have multiple numbers.

**Question:**

How would you design this using a `HashMap`? What would be the key and value types?

---

### **10. Frequency Sort**

**Scenario:**

Given a list of integers, you need to return the numbers sorted by their frequency (highest frequency first).

**Question:**

How would you use a `HashMap` to track frequencies and then sort based on them?
