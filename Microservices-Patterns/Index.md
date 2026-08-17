Microservices Pattern Curriculum

## Category 1 — Microservices Fundamentals & Decomposition
1. Microservices Architecture
2. Monolith vs Modular Monolith vs Microservices
3. Strangler Fig Pattern
4. Smart Endpoints, Dumb Pipes
5. Stateless Services
6. Database per Service
7. Polyglot Persistence

## Category 2 — API & Client Communication
1. API Gateway Pattern
2. Backend for Frontend (BFF)
3. Synchronous REST Communication
4. gRPC Communication
5. Async Messaging
6. Event-Driven Architecture

## Category 3 — Service Discovery & Infrastructure
1. Service Registry / Service Discovery
2. Client-Side Service Discovery
3. Server-Side Service Discovery
4. Sidecar Pattern
5. Service Mesh Pattern
6. Externalized Configuration

## Category 4 — Resilience & Fault Tolerance
1. Circuit Breaker
2. Retry
3. Timeout
4. Fallback
5. Bulkhead
6. Rate Limiting
7. Load Balancing

## Category 5 — Distributed Transactions & Data Consistency
1. Saga Pattern
2. Choreography-based Saga
3. Orchestration-based Saga
4. Transactional Outbox Pattern
5. Idempotency Pattern
6. Distributed Transaction Problems
7. Eventual Consistency

Explain:

ACID vs BASE
Local transaction vs distributed transaction
Why 2PC is problematic in many microservice architectures
Compensation transactions
Duplicate messages
Message ordering
Exactly-once vs at-least-once processing
Category 6 — Event-Driven Architecture
Event-Driven Architecture
Event Notification
Event-Carried State Transfer
Async Messaging
Message Broker
Consumer Groups
Dead Letter Queue
Retry Queue
Poison Message Handling
Event Ordering
Event Deduplication
Idempotent Consumers

Use Apache Kafka for the primary implementation unless another technology is more appropriate.

## Category 7 — CQRS & Event Sourcing
1. CQRS
2. Event Sourcing
3. CQRS + Event Sourcing
4. Projection
5. Read Model
6. Event Replay
7. Snapshotting
8. Schema Evolution

Clearly explain:

Traditional CRUD

Command → Database
Query   → Database

versus:

CQRS

Command
   ↓
Command Model
   ↓
Events
   ↓
Event Store
   ↓
Projection
   ↓
Read Model
   ↓
Query

Explain when CQRS/Event Sourcing is justified and when it is unnecessary complexity.

## Category 8 — Scalability & Data Architecture
1. Data Sharding
2. Horizontal Partitioning
3. Read Replicas
4. Caching
5. Distributed Cache
6. Polyglot Persistence
7. Database per Service

Explain:

Sharding strategies
Partition keys
Hot partitions
Rebalancing
Data locality
Cache invalidation
Cache-aside
Write-through
Write-behind
Distributed consistency problems

## Category 9 — Deployment & Delivery Patterns
1. Shadow Deployment
2. Blue-Green Deployment
3. Canary Deployment
4. Rolling Deployment
5. Feature Flags

Explain how these patterns reduce deployment risk.

## Category 10 — Contract & Integration Patterns
1. Consumer-Driven Contracts
2. API Versioning
3. Backward Compatibility
4. Schema Evolution
5. Compatibility Testing

Use Pact or an equivalent production-grade contract testing solution.

=============================================================
1. Strangler fig Pattern
2. API gateway pattern
3. Backend for frontend pattern
4. Service discovery pattern
5. Circuite breaker pattern
6. Bulkhead pattern
7. Retry pattern
8. Sidecar pattern
9. Service mesh pattern
10. Database per service pattern
11. Saga pattern
12. Transactional outbox pattern
13. Event driven architecture pattern
14. CQRS pattern
15. Event sourcing pattern
16. Configuration externalization pattern

=================
1. Service Registry
2. API Gateway
3. Circuit Breaker
4. Bulkhead
5. Saga Pattern
6. Event Sourcing
7. Command Query Responsibility Segregation (CQRS)
8. Data Sharding
9. Polyglot Persistence
10. Retry
12. Sidecar
13. Backends for Frontends (BFF)
14. Shadow Deployment
15. Consumer-Driven Contracts
16. Smart Endpoints, Dumb Pipes
17. Database per Service
18. Async Messaging
19. Stateless Service
===================================