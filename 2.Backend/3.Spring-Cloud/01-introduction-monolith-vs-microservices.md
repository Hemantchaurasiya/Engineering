# 01. Introduction — Cloud, Scaling, Monolith vs Microservices

[← Back to README](README.md) | Next → [02. Spring Cloud Evolution](02-spring-cloud-evolution-netflix-stack.md)

---

## 1. Your Original Notes (cleaned up)

### Why microservices — the necessity

10 years back, applications had very limited traffic:
- amazon.com's daily requests were in the range of ~1000/day
- today amazon's daily requests are in the range of ~10,00,000+/day (millions)

Old requirements ≠ today's requirements. The usage of an application today is huge,
unpredictable, and expected to be:
- Always up & running (no downtime observed by users)
- Always fast (no perceptible performance issue)

**Paytm example (from notes):** Until demonetization, Paytm had very little traffic.
After demonetization the traffic became ~100x. An architecture that can't absorb a
100x traffic spike in days, not months, will fail the business.

**Develop → Deploy → Infrastructure problem:** If a product suddenly becomes a
success (e.g. within a month), and you need to scale:
1. Buy a new physical server
2. Set up infrastructure
3. Deploy the application

This takes time. Meanwhile, users leave because the app is slow/down.

If instead you buy a big server upfront (assuming success), and the product fails,
you've wasted the initial cost. **This is exactly the vertical scaling trap.**

### Vertical Scaling

Increasing the capacity of a **single** physical machine (1 CPU/1GB → 2 CPU/2GB → 4
CPU/4GB RAM) to handle more requests.

**Drawbacks:**
1. You can't predict the "correct" resource size — you'll either over-provision
   (waste money) or under-provision (traffic problems).
2. Cost of vertical scaling is high, and non-linear (a 4x machine doesn't cost 4x).
3. There's a hard ceiling — you cannot get infinite CPU/RAM on one box.
4. Doesn't work anymore for internet-scale traffic patterns.

### Horizontal Scaling

Instead of running the application on one large server, run it across **multiple
smaller servers**, fronted by a load balancer:

```
        Server1
req1 →  Application  | private ip

        Server2
req2 →  BALANCER → Application  | private ip

        Server3
req3 →  Application  | private ip
```

When huge traffic comes in → add Server4 immediately.

Horizontal scaling is far more elastic than vertical scaling, but on **physical**
servers it still has problems:
1. How many servers do you keep in the cluster?
2. What's the resource spec of each server?
3. Physical servers still require a purchase/provisioning decision — same resource
   planning headache as vertical scaling, just distributed.

### Virtualization (VM)

Take one physical server and split it into multiple **virtual machines** using a
hypervisor:

```
| VM1       | VM2        | VM3      |
| Linux OS  | Windows OS | Linux OS |
|      Hypervisor (virtualization software)     |
|              OS (HOST)                        |
```

- Each VM behaves like an independent physical server: its own CPU allocation, RAM,
  OS.
- When more traffic is expected, spin up one more VM on the existing hardware —
  no need to buy a new physical machine.
- Company-owner-level concerns (not usually a developer's day-to-day thought):
  - On-prem servers need more people to manage setup, scale-out, scale-in.
  - Need to worry about data-loss prevention — if one data center goes down, data
    must be recoverable from another data center.
  - Data should be backed up across data centers.

### Characteristics demanded of applications today

1. **Performance** — respond in milliseconds regardless of load.
2. **High Availability** — 24x7 uptime.
3. **Auto Scaling** — automatically create/add a new VM to the load balancer based
   on CPU utilization.
   - CPU 60% used → add 1 VM
   - CPU 70% used → add 2 VMs
   - CPU 80% used → add more VMs
   - This is called **Scale out**. The reverse (removing VMs when load drops) is
     **Scale in**. Together = **Auto Scaling**.
4. **Dynamic Resource Provisioning** — autoscaling is impossible without the
   ability to request/release resources dynamically (this is what cloud providers
   give you and on-prem largely doesn't).
5. **Zero Downtime** — deployments/migrations must not take the app down.
6. **Distributed** — data distributed across geographical data centers.

To achieve all 6 of the above, **cloud** came into the picture — and you cannot
realistically get there with a **monolithic** application. You *can* use a monolith
on the cloud, but you'll hit a wall — which is exactly why **microservices**
architecture exists.

### Monolithic Application

All features of the application bundled into **one single deployable artifact**
(one WAR/JAR):

```
          E-Commerce Application
| Product Catalog | Shopping Cart |
| Order History   | Customer feature |
```

**Drawbacks:**
1. **Can't scale a single feature** — scaling means scaling the *entire* app, even
   if only one feature (e.g. Product Catalog) is under load. Resource utilization
   is poor.
2. **Tight coupling** — changing one feature risks breaking others (e.g. changing
   Product feature impacts Shopping Cart, Order History). Requires heavy
   regression + functional testing on unrelated modules. Dev/test time increases.
3. **Slow feedback loop** — e.g. large companies historically released major
   features only every ~6 months because the whole monolith had to be
   tested/released together.
4. **Difficult to enable DevOps** — one big build/deploy pipeline; a commit
   anywhere triggers a full CI/CD run, which takes longer.
5. **Single Point of Failure (SPOF)** — if one feature crashes the JVM/process,
   *every* feature goes down with it.

### Microservices

Microservices are **distributed, loosely coupled** software components that each
carry out a small, well-defined task/business capability.

- A **methodology/architectural style**, not a technology — a set of guidelines,
  design patterns and recommendations for building large, complex systems as a
  collection of small, independently deployable services.
- Each microservice is responsible for **one** kind of functionality/feature.

**Characteristics:**
1. **Loosely Coupled** — separate codebase, separate repository per service.
2. **Independently Deployable** — build & deploy each service separately.
3. **Highly Scalable** — scale only the feature that needs it, not the whole app.
4. **Resilient** — failure of one service doesn't cascade to bring down everything.
5. **Collaborative** — services work together over the network to fulfil a
   business use case.

### How do you split a monolith into microservices?

1. **SRP — Single Responsibility Principle**: a service should have one reason to
   change.
2. **Bounded Context** (from Domain-Driven Design): split along business
   capability/domain boundaries, not technical layers.

**Advantages of microservices (from notes, consolidated):**
- **Loose coupling + high cohesion** — a change in one service shouldn't require
  testing every other service; related functionality lives together in one
  service (high cohesion).
- **Database per service** — every microservice owns its own database; cross-service
  data access happens only *through the owning service's API*, never by directly
  querying another service's DB.
- **Backward-compatible breaking changes** — every service must maintain API
  versioning so that consumers aren't broken by a deploy.
- **One service, one repo** — simplifies build & deploy.
- **Parallel, independent development** — teams build against their own codebase
  without waiting on others; independent planning/release/scaling; failures are
  isolated because deployment is independent.
- Smaller codebases → developers understand them faster, IDEs are faster, local
  server startup is faster → faster dev/test loop.
- Independently scalable → real horizontal scaling per feature.
- Improved testability (smaller surface area per service).

### What building microservices actually requires

Not just APIs/a programming language — you also need tooling to **deploy,
discover, monitor, and manage** many small services:

1. **Eureka Server** — service discovery
2. **Feign Client** — declarative inter-service REST client
3. **Hystrix** — circuit breaker for resilience *(legacy — see file 06)*
4. **Ribbon** — client-side load balancer *(legacy — see file 05)*
5. **Zuul** — API Gateway *(legacy — see file 07)*

Netflix built and open-sourced all of the above; using them directly is powerful
but verbose/boilerplate-heavy. **Spring Cloud** wraps Netflix OSS (and later its own
tools) into Spring-idiomatic, auto-configured building blocks.

> `Microservice Development = Java API/libraries + cloud + third-party tools`
> `= (REST APIs, Spring Boot) + AWS/cloud + Netflix/Apache tooling`
>
> `Microservices Application = Development + (Deploy, Manage, Integrate, Discover) + Infrastructure (cloud)`
>
> `Spring Boot` → develops the microservice itself (auto-configuration, embedded
> server, actuator).
> `Spring Cloud` → provides the integration tooling around it (Config Server,
> Eureka, Gateway, LoadBalancer, Circuit Breaker...).

---

## 2. Deep Dive — What Your Notes Didn't Cover

### 2.1 The CAP theorem angle on scaling/discovery (interviewers love this)
Distributed systems must trade off **Consistency, Availability, Partition
tolerance** — you can only fully guarantee 2 of the 3 during a network partition.
- Eureka is **AP** (favors availability — will serve stale registry data rather than
  refuse to respond).
- Zookeeper/Consul (strict mode) lean **CP** (favor consistency — may refuse to
  serve during a partition).
This is a very common 5-8 YOE interview question: *"Why did Netflix pick AP for
service discovery?"* — because in a service mesh, a **stale list of instances is
recoverable** (a client just retries a dead instance and fails over), but a
**discovery server that's unavailable** takes down every service's ability to find
anything — a much worse outage.

### 2.2 Stateless vs Stateful services
Horizontal scaling only works cleanly if services are **stateless** — no
session/local in-memory state tied to a specific instance. Session state, if
needed, must be externalized (Redis, DB, JWT-encoded claims) so any instance can
serve any request. This is *the* precondition for load balancing and autoscaling
to work correctly, and your notes don't mention it — but interviewers always probe
it: *"How do you handle user sessions across autoscaled instances?"*

### 2.3 12-Factor App principles
Modern cloud-native apps (including every microservice you'll build) generally
follow the [12-Factor App](https://12factor.net) methodology:
config in environment, stateless processes, disposability (fast startup/graceful
shutdown), backing services as attached resources, dev/prod parity, logs as event
streams, etc. Config Server (file 03) is a direct implementation of Factor III
("Config").

### 2.4 Containers vs VMs
Your notes stop at VM-level virtualization. In practice today, microservices run
in **containers** (Docker) orchestrated by **Kubernetes**, not raw VMs:
- VM virtualizes hardware (each VM has its own OS kernel) → heavier, slower to
  start (minutes).
- Containers virtualize the OS (share the host kernel, isolate via namespaces/
  cgroups) → lightweight, start in seconds, much better density.
- Kubernetes provides auto-scaling (HPA), self-healing, service discovery, and
  config/secret management *natively* — which is why in cloud-native shops today,
  Kubernetes Service Discovery / ConfigMaps often **replace** Eureka / Config
  Server entirely. Interviewers at 8-10 YOE frequently ask: *"Why would you still
  use Eureka if you're on Kubernetes?"* Good answer: you often wouldn't — K8s
  Services + kube-dns give you discovery for free; Eureka is more relevant for
  non-K8s deployments (VMs, on-prem, PCF/Cloud Foundry).

### 2.5 Sync vs async communication (missing from notes entirely)
Notes only cover REST (sync, request/response). Real systems mix in:
- **Sync**: REST (RestTemplate/WebClient/Feign), gRPC
- **Async/event-driven**: Kafka, RabbitMQ — for decoupling, buffering load spikes,
  and avoiding cascading failures when a downstream service is slow. This is
  usually its own interview phase (Spring Cloud Stream, Kafka) — mentioned here so
  you know it's a gap, covered separately from these 9 files.

---

## 3. "Behind the Scenes"

**Why can't a monolith achieve Auto Scaling cleanly even if deployed on the
cloud?** Because the unit of deployment/scaling is the *entire* JAR/WAR. If
"Payments" gets 10x traffic and "Product Catalog" gets none, autoscaling the
monolith scales *both equally* — you pay for and provision capacity you don't
need for 3 of 4 features, defeating the "dynamic resource provisioning" goal.
This single fact is the crux of nearly every "why microservices" interview answer.

---

## 4. Interview Q&A

**Q1: What's the difference between scalability and elasticity?**
A: Scalability is the *capability* of a system to handle increased load (by adding
resources). Elasticity is scalability that happens **automatically and
dynamically** in response to real-time load (auto scale-out/scale-in). All elastic
systems are scalable; not all scalable systems are elastic (e.g. manually adding a
server is scalable but not elastic).

**Q2: Why is vertical scaling considered an anti-pattern for cloud-native apps?**
A: Hard resource ceiling, high non-linear cost, downtime typically required to
resize, and it doesn't solve the "correct sizing" problem — you're still guessing
capacity. Horizontal scaling instead adds/removes homogeneous small units
elastically, matching capacity to real-time demand.

**Q3 (tricky): If horizontal scaling is better, why do database servers still
often scale vertically?**
A: Because most relational databases are **stateful** and consistency-critical —
horizontally *sharding* a DB is far harder (data partitioning, cross-shard
transactions, rebalancing) than horizontally scaling stateless app servers behind
a load balancer. Many teams vertically scale the DB (or use managed
read-replicas/sharding solutions) while horizontally scaling the stateless
service layer in front of it.

**Q4: What's the difference between monolithic, SOA, and microservices
architecture?**
A: Monolith = single deployable, tightly coupled, usually one shared DB. SOA
(Service-Oriented Architecture) = services communicate typically via an
Enterprise Service Bus (ESB), often share a database, and services tend to be
coarser-grained. Microservices = fine-grained, independently deployable,
database-per-service, lightweight protocols (REST/gRPC/messaging) instead of a
heavyweight ESB, decentralized governance.

**Q5 (tricky — very commonly asked at senior level): Are microservices always the
right choice?**
A: No. Microservices trade **development simplicity for operational complexity**
— you now need service discovery, distributed tracing, distributed transactions
(sagas), network reliability handling, and much more DevOps maturity. For a small
team/small domain, a well-modularized "modular monolith" is often the pragmatic
choice; microservices earn their cost mainly at organizational scale (many teams
needing independent deploy cadence) or where independent scaling of hot paths
genuinely matters. A good senior answer *always* mentions this trade-off — never
say "microservices are always better."

**Q6: What does "Bounded Context" mean and why is it central to splitting a
monolith?**
A: A Bounded Context (from Domain-Driven Design) is a boundary within which a
particular domain model (its terms, rules, entities) is consistent and
unambiguous. E.g. "Customer" might mean something different in Billing vs
Support. Splitting services along bounded contexts (rather than technical layers
like "all controllers" / "all DB access") keeps each service cohesive and
minimizes cross-service chatter for a single business transaction.

---

## 5. Scenario Questions

**Scenario 1:** *"Your monolithic e-commerce app's Order Service gets 50x traffic
during a flash sale, while Product Catalog gets normal traffic. Your ops team
wants to scale the whole app using AWS Auto Scaling Groups. What do you tell
them, and what's your target architecture?"*

Model answer: Explain that scaling the monolith wastes resources scaling
unrelated features (Catalog, Cart) along with Orders, and doesn't isolate Order
Service failures from the rest of the app. Propose splitting Order Service into
its own microservice with its own database, deployed behind its own Auto Scaling
Group / HPA (if on Kubernetes), so only the hot path scales, and a failure/latency
spike in Order Service (under load) is contained by a circuit breaker (file 06)
rather than starving threads for Catalog/Cart requests too.

**Scenario 2:** *"You split a monolith into 12 microservices along technical
layers (a 'controllers service', a 'services-layer service', a 'DAO service')
instead of business capability. Six months later, every feature change requires
deploying all 12 services together. What went wrong, and how do you fix it?"*

Model answer: This is a classic "distributed monolith" anti-pattern — splitting
by technical layer instead of Bounded Context creates tight coupling *across*
service boundaries (worse than a monolith, because now it's tightly coupled *and*
has network latency/failure modes). Fix: re-slice by business capability
(Orders, Payments, Inventory, etc.), each owning its own persistence and exposing
a versioned API, so a change to one business capability doesn't ripple into
unrelated services.

---

[← Back to README](README.md) | Next → [02. Spring Cloud Evolution](02-spring-cloud-evolution-netflix-stack.md)
