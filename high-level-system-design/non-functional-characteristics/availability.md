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


# ---

# 🚀 2. Availability (Ultra Deep Dive)

## 🔹 1. Definition (Simple + Intuitive)

### ✅ Simple Definition

**Availability** = How often your system is up and usable when users try to access it.

### 🧠 Real-World Analogy (Deep)

Think of an ATM machine:

- You go to withdraw money  
- If ATM is working → ✅ Available  
- If it’s down → ❌ Not available

Now imagine:  
- ATM works 99% of time → good  
- ATM works 99.999% → excellent

👉 That “how often it works” = availability

### 💡 Key Insight

Availability is about **uptime from user’s perspective**, not internal system health.

Even if system is running internally, but:  
- API not responding  
- UI not loading  

👉 It is **DOWN for users**

---

## 🔹 2. Why It Matters

### 📉 If Availability is Poor:
- Users cannot access your service  
- Immediate frustration  
- Loss of trust

### 💸 Business Impact
- Lost revenue (e.g., checkout failure on Amazon)  
- Brand damage  
- SLA violations

### 🔥 Real Example Thinking
- Streaming stops on Netflix → users leave instantly  
- Search fails on Google → unacceptable

👉 High-availability systems are non-negotiable in modern apps.

---

## 🔹 3. Key Metrics / How to Measure

### 📊 1. Uptime Percentage
Availability = (Uptime / Total Time) × 100


### 📊 2. “Nines” of Availability

| Level               | Availability | Downtime per Year |
|---------------------|--------------|--------------------|
| 99%                 | 99%          | ~3.65 days         |
| 99.9%               | 99.9%        | ~8.7 hours         |
| 99.99%              | 99.99%       | ~52 minutes        |
| 99.999% (5 nines)   | 99.999%      | ~5 minutes         |

### 📊 3. MTBF & MTTR

- **MTBF** (Mean Time Between Failures) → How often system fails  
- **MTTR** (Mean Time To Repair) → How quickly system recovers

### 🧠 Key Insight

Availability improves when:  
- Failures are rare (high MTBF)  
- Recovery is fast (low MTTR)

---

## 🔹 4. How to Achieve Availability (Deep + Practical)

### 🧩 1. Redundancy (MOST IMPORTANT)

👉 Never rely on one component

**❌ Bad Design (Single Point of Failure)**  

User → Server → DB

If server dies → system down ❌

**✅ Good Design (Redundant)**  
User → Load Balancer → Server1, Server2
↓
DB Primary + Replica
👉 If one fails → others take over

### 🧩 2. Load Balancing
- Distributes traffic  
- Removes unhealthy servers automatically

### 🧩 3. Failover Mechanism

When primary fails → switch to backup.

Types:  
- Active-Passive  
- Active-Active

### 🧩 4. Replication

Duplicate data across machines.

Types:  
- Synchronous → safer, slower  
- Asynchronous → faster, risk of data loss

### 🧩 5. Health Checks
- Detect failures automatically  
- Remove bad nodes from pool

### 🧩 6. Auto Scaling
Handle sudden traffic spikes

### 🧩 7. Multi-Region Deployment (Advanced)
India Region → US Region → EU Region

👉 If one region fails → traffic shifts

Used by: Google, Netflix

---

## 🔹 5. Trade-offs (CRITICAL SECTION)

### ⚖️ 1. Availability vs Consistency (VERY IMPORTANT)

This leads to **CAP theorem**.

👉 In distributed systems:  
You cannot have both perfect consistency and availability during failures.

Example:  
- **Bank system:** Strong consistency → accurate balance, but system may reject requests if DB unavailable.  
- **Social media:** Slight inconsistency OK, always available preferred.

### ⚖️ 2. Availability vs Latency
Multi-region systems increase latency.

### ⚖️ 3. Availability vs Cost
More replicas = more infrastructure cost.

### ⚖️ 4. Availability vs Complexity
- Failover logic is complex  
- Hard to debug distributed failures

---

## 🔹 6. Real-World Examples

### 🎬 Netflix
Prioritizes availability over consistency.  
Even if some data is slightly stale → service must not stop.

### 🛒 Amazon
Checkout must be highly available.  
Uses redundancy + failover.

### 🔍 Google
Multi-region infrastructure.  
Extremely high uptime (near 5 nines).

---

## 🔹 7. Impact on System Design Decisions

### 🧠 Architecture

| Decision                 | Impact on Availability |
|--------------------------|------------------------|
| Single server            | Low availability ❌     |
| Distributed system       | High availability ✅     |

### 🧠 Database
- Replication required  
- Multi-region DB

### 🧠 API Design
- Retry mechanisms  
- Idempotent APIs

### 🧠 Infra
- Load balancers  
- Health checks  
- Auto failover

---

## 🔹 8. Common Mistakes / Pitfalls

- ❌ **1. Single Point of Failure** – One DB / one server  
- ❌ **2. No Failover Strategy** – Backup exists but not used automatically  
- ❌ **3. Ignoring Partial Failures** – Distributed systems fail partially, not fully  
- ❌ **4. No Monitoring** – Failures go undetected  
- ❌ **5. Overestimating availability** – Claiming “5 nines” without proper infra

---

## 🧠 Mini Quiz

1. Why does adding replicas improve availability?  
2. What is the difference between failover and replication?  
3. Why can’t we have both strong consistency and high availability always?

---

## 🎯 Final Mental Model

> **Availability** = Designing systems that keep working even when parts fail.