# 🟢 Availability in System Design

## 📌 What is Availability?

Availability measures **how often a system is operational and accessible** when users need it.

**Formula:**
Availability (%) = ((Total Time - Downtime) / Total Time) × 100

👉 **One-line summary:**
> Availability ensures your system remains up and serves users even during failures.

---

## 🧠 Core Mindset

🚨 **You don’t prevent failures → you design for failure**

Modern systems assume:
- Servers will crash  
- Networks will fail  
- Databases will go down  

👉 Your goal: **System should still work**

---

## 🎯 First Principles

### 1. No Single Point of Failure (SPOF)
- Nothing should depend on a single component  
- If one thing fails → system continues  

👉 Rule:
> **“2 is 1, 1 is none”**

---

## 📊 The Nines of Availability

| Availability | Downtime / Year |
|-------------|----------------|
| 90%         | 36.5 days      |
| 99%         | 3.65 days      |
| 99.9%       | 8.76 hours     |
| 99.99%      | 52.56 minutes  |
| 99.999%     | 5.26 minutes   |

👉 Interview Tip:
- Most real systems target **99.9% – 99.99%**
- 5 nines = **very expensive + complex**

---

## 🏗️ Building Blocks of High Availability

### 1. 🔁 Redundancy
Duplicate everything critical:
- Servers
- Databases
- Regions

---

### 2. ⚖️ Load Balancing
Distribute traffic across servers:
- Avoid overload
- Skip failed nodes automatically  

Types:
- L4 (TCP)
- L7 (HTTP – smart routing)

---

### 3. 🧩 Stateless Services
❌ Bad:
- Session stored in server memory  

✅ Good:
- Session stored in Redis / DB  

👉 Any server can handle any request

---

### 4. 💾 Data Replication
Multiple copies of data:

Models:
- Leader–Follower  
- Multi-Leader  
- Leaderless  

👉 Trade-off:
- Consistency vs Availability  

---

### 5. 🔄 Failover
Automatic switching:
- Primary → Secondary DB  
- Region A → Region B  

Must be:
- Fast  
- Automatic  
- Tested  

---

### 6. ❤️ Health Checks
- Continuously monitor services  
- Remove unhealthy nodes  
- Auto-replace them  

---

### 7. 📈 Auto Scaling
- Scale out → handle traffic spikes  
- Scale in → reduce cost  

---

### 8. ⚡ Caching
- CDN (edge caching)  
- Redis / Memcached  

👉 Even if backend fails → system still serves users  

---

### 9. 🪫 Graceful Degradation
System should **partially work instead of failing**

Examples:
- Show cached data  
- Disable recommendations  
- Retry payments later  

---

### 10. 🚫 Circuit Breaker
Prevents cascading failures  

Example:
- Payment service down → stop calling it  

---

## ⚖️ CAP Theorem (Critical Concept)

You cannot have all three:

- Consistency  
- Availability  
- Partition Tolerance  

👉 In real systems:
- **CP** → Banking systems  
- **AP** → Social media  

---

## 🌍 Advanced Real-World Techniques

### 🔥 Chaos Engineering
- Randomly break systems  
- Test real failure scenarios  

---

### 🌎 Multi-Region (Active-Active)
- All regions serve traffic  
- If one fails → others continue  

---

### 🔄 Blue-Green Deployment
- Two environments  
- Instant rollback  

---

### 🧪 Canary Releases
- Release to small % of users  
- Detect issues early  

---

## 🧩 Step-by-Step Design Approach

### Example: Food Delivery System

**1. Entry Layer**
- DNS + Load Balancer  
- Multiple instances  

**2. Application Layer**
- Stateless services  
- Auto-scaling  

**3. Data Layer**
- Primary DB + replicas  
- Read from replicas  

**4. Caching Layer**
- Redis (hot data)  
- CDN (static content)  

**5. Failure Handling**
- Retries with backoff  
- Circuit breakers  
- Timeouts  

**6. Multi-Region**
- Deploy across regions  
- Geo-routing  

**7. Monitoring**
- Metrics + alerts  
- Auto-healing  

---

## ❌ Common Mistakes (Interview Killers)

- Single database  
- Stateful servers  
- No failover  (Not properly handle failures)
- No monitoring  
- Ignoring network failures  

👉 These signal **weak system design understanding**

---

## 🎤 Interview Answer (Perfect Template)

> “To ensure high availability, I would eliminate single points of failure using redundancy, deploy services across multiple regions, enable automatic failover, and design stateless services with replicated data and caching.”

---

## 🧠 Final Mental Model

When designing any system, always ask:

❓ What happens if this fails?
- Server?  
- Database?  
- Region?  
- Network?  

👉 If answer = *system goes down* → ❌ Bad design  

---

## 🚀 Golden Summary

> High availability is not a feature — it’s a mindset applied at every layer of the system.


### Availability in system design means:
- How often your system is up, running, and accessible when users need it.
- Availability = Percentage of time a system is operational and usable.

🔹 One-Line Summary

Availability ensures your system is always up and ready to serve users, even in the presence of failures.

### To ensure high availability, I would eliminate single points of failure using redundancy, deploy services across multiple regions, enable automatic failover, and design stateless services with replicated data and caching.

### High availability is not a feature—it’s a mindset applied at every layer of the system.

# The Nines of Availability

## Availability Percentages vs Service Downtime

| Availability | Downtime per Year | Downtime per Month | Downtime per Week |
|-------------|------------------|-------------------|------------------|
| 90% (1 nine) | 36.5 days | 72 hours | 16.8 hours |
| 99% (2 nines) | 3.65 days | 7.20 hours | 1.68 hours |
| 99.5% (2 nines) | 1.83 days | 3.60 hours | 50.4 minutes |
| 99.9% (3 nines) | 8.76 hours | 43.8 minutes | 10.1 minutes |
| 99.99% (4 nines) | 52.56 minutes | 4.32 minutes | 1.01 minutes |
| 99.999% (5 nines) | 5.26 minutes | 25.9 seconds | 6.05 seconds |
| 99.9999% (6 nines) | 31.5 seconds | 2.59 seconds | 0.605 seconds |
| 99.99999% (7 nines) | 3.15 seconds | 0.259 seconds | 0.0605 seconds |
