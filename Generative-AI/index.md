# Master Prompt: Generative AI Patterns — Complete Production-Grade Mastery

Act as my **Senior Generative AI Architect, AI Systems Engineer, Python mentor, and technical interviewer**.

I want to **master Generative AI design and implementation patterns from beginner to expert level** so that I can design, build, debug, optimize, and productionize enterprise-grade GenAI applications.

My goal is NOT simply to learn how to call an LLM API.

My goal is to understand **the theory, internal working, architectural decisions, trade-offs, implementation techniques, failure modes, scalability concerns, and production best practices behind every important Generative AI pattern.**

Use **Python as the primary programming language**.

---

# 1. Teaching Philosophy

Teach me as an engineer who wants to become a:

- Senior GenAI Engineer
- GenAI Application Architect
- AI Systems Designer
- Production LLM Engineer
- GenAI Solutions Architect

Start from fundamentals, but eventually reach **expert/architect level**.

Do NOT skip patterns.

Do NOT assume that knowing LangChain/LlamaIndex means I understand the underlying concepts.

Whenever possible:

> First explain the underlying concept → then implement it manually → then show how frameworks simplify it.

Focus on **why a pattern exists**, not just how to implement it.

---

# 2. Mandatory Pattern Coverage

Create a comprehensive taxonomy of Generative AI patterns.

Cover ALL important patterns, including but not limited to the following categories.

## A. LLM Interaction Patterns

Teach:

1. Basic LLM Invocation
2. Prompt Template Pattern
3. Structured Prompt Pattern
4. Few-Shot Prompting
5. Zero-Shot Prompting
6. Chain-of-Thought considerations
7. Role Prompting
8. Context Injection
9. Instruction Hierarchy
10. Output Formatting
11. Structured Output / JSON Output
12. Function Calling
13. Tool Calling
14. Model Selection
15. Model Routing
16. Fallback Models
17. Multi-Model Architecture
18. LLM Gateway Pattern

Explain when each should and should not be used.

---

# 3. Prompt Engineering Patterns

Cover:

1. Zero-Shot Prompting
2. Few-Shot Prompting
3. Role-Based Prompting
4. Instruction Prompting
5. Delimiter Pattern
6. Template Pattern
7. Dynamic Prompt Construction
8. Contextual Prompting
9. Prompt Chaining
10. Prompt Decomposition
11. Self-Consistency
12. Critique / Revision
13. Generate → Critique → Refine
14. Prompt Routing
15. Prompt Optimization
16. Prompt Versioning
17. Prompt Registry
18. Prompt Evaluation

Explain prompt injection and defensive prompt design.

---

# 4. RAG Patterns

Teach RAG deeply from first principles.

Cover:

1. Basic RAG
2. Naive RAG
3. Advanced RAG
4. Modular RAG
5. Query Transformation
6. Query Expansion
7. Query Rewriting
8. Multi-Query RAG
9. HyDE
10. Query Decomposition
11. Recursive Retrieval
12. Parent-Child Retrieval
13. Sentence Window Retrieval
14. Metadata Filtering
15. Hybrid Search
16. Dense Retrieval
17. Sparse Retrieval
18. BM25
19. Semantic Search
20. Re-Ranking
21. Context Compression
22. Contextual Retrieval
23. Multi-Hop RAG
24. Graph RAG
25. Agentic RAG
26. Self-RAG
27. Corrective RAG
28. Adaptive RAG
29. Conversational RAG
30. Multimodal RAG
31. SQL RAG
32. API/Data RAG
33. Enterprise RAG
34. Real-Time RAG

For every RAG pattern explain:

- Problem
- Why basic RAG fails
- Architecture
- Data flow
- Retrieval strategy
- Advantages
- Disadvantages
- Trade-offs
- Failure modes
- Production use cases
- Python implementation
- Evaluation strategy

---

# 5. Agent Patterns

Teach AI Agents from first principles.

Cover:

1. Basic Agent
2. Tool-Using Agent
3. ReAct
4. Planning Agent
5. Reflection Agent
6. Self-Critique Agent
7. Self-Improving Agent
8. Router Agent
9. Supervisor Agent
10. Worker Agent
11. Hierarchical Agent
12. Multi-Agent System
13. Debate Pattern
14. Sequential Agent
15. Parallel Agent
16. Human-in-the-Loop Agent
17. Agent Handoff
18. Agent Memory
19. Agent Planning
20. Agentic RAG
21. Agentic Workflow
22. Autonomous Agent
23. Event-Driven Agent
24. Long-Running Agent
25. Stateful Agent
26. Stateless Agent

Explain the difference between:

- LLM
- Workflow
- Chain
- Agent
- Agentic Workflow
- Multi-Agent System

---

# 6. Workflow Patterns

Teach:

1. Sequential Workflow
2. Parallel Workflow
3. Conditional Workflow
4. Branching
5. Routing
6. Map-Reduce
7. Fan-Out / Fan-In
8. Iterative Workflow
9. Loop Workflow
10. Retry Pattern
11. Fallback Pattern
12. Human Approval Workflow
13. Event-Driven Workflow
14. Async Workflow
15. Long-Running Workflow
16. Stateful Workflow

Show how workflows differ from autonomous agents.

---

# 7. Memory Patterns

Teach GenAI memory deeply.

Cover:

1. Conversation Memory
2. Short-Term Memory
3. Long-Term Memory
4. Semantic Memory
5. Episodic Memory
6. Procedural Memory
7. Entity Memory
8. Summary Memory
9. Vector-Based Memory
10. External Memory
11. Persistent Memory
12. Working Memory
13. Memory Retrieval
14. Memory Consolidation
15. Memory Compression
16. Memory Forgetting
17. User Profile Memory

Explain when memory should be stored in:

- Database
- Redis
- Vector Database
- Object Storage
- Graph Database

---

# 8. Tool-Use Patterns

Cover:

1. Function Calling
2. Tool Calling
3. API Calling
4. Database Tool
5. Search Tool
6. Calculator Tool
7. Code Execution Tool
8. Browser Tool
9. Retrieval Tool
10. MCP-Based Tools
11. Tool Selection
12. Tool Routing
13. Tool Validation
14. Tool Authorization
15. Tool Retry
16. Tool Timeout
17. Tool Fallback
18. Tool Result Validation

Explain how to safely expose tools to an LLM.

---

# 9. MCP Patterns

Teach Model Context Protocol deeply.

Cover:

1. MCP fundamentals
2. MCP Client
3. MCP Server
4. MCP Resources
5. MCP Tools
6. MCP Prompts
7. MCP Architecture
8. Tool Discovery
9. Dynamic Tool Discovery
10. MCP Security
11. MCP Authorization
12. MCP Tool Routing
13. MCP with Agents
14. MCP with RAG
15. MCP with Enterprise Systems
16. Multi-MCP Architecture

Build practical MCP servers in Python.

---

# 10. Knowledge & Retrieval Patterns

Cover:

- Vector embeddings
- Embedding generation
- Chunking
- Semantic chunking
- Recursive chunking
- Document parsing
- Metadata extraction
- Knowledge graphs
- Vector databases
- Hybrid databases
- Graph + Vector retrieval
- Re-ranking
- Retrieval filters
- Similarity search
- Approximate nearest neighbor search

Explain:

> How raw documents become searchable knowledge for an LLM.

---

# 11. Context Engineering Patterns

Teach context engineering separately from prompt engineering.

Cover:

1. Context Selection
2. Context Filtering
3. Context Compression
4. Context Ranking
5. Context Prioritization
6. Context Window Management
7. Context Caching
8. Context Isolation
9. Context Routing
10. Context Summarization
11. Context Deduplication
12. Contextual Retrieval

Explain how to solve:

- Context overflow
- Irrelevant context
- Lost-in-the-middle problem
- Duplicate context
- Poor retrieval
- Conflicting information

---

# 12. Guardrails & Security Patterns

Cover:

1. Input Validation
2. Output Validation
3. Schema Validation
4. Prompt Injection Defense
5. Jailbreak Defense
6. Data Leakage Prevention
7. PII Detection
8. Sensitive Data Filtering
9. Toxicity Filtering
10. Content Moderation
11. Tool Authorization
12. Permission-Based Agents
13. Sandboxing
14. Secure Tool Execution
15. Rate Limiting
16. Budget Limiting
17. Model Access Control
18. Human Approval
19. Zero-Trust AI Architecture

Show realistic attacks and defensive implementations.

---

# 13. Reliability Patterns

Teach:

1. Retry
2. Exponential Backoff
3. Timeout
4. Circuit Breaker
5. Bulkhead
6. Fallback
7. Model Fallback
8. Tool Fallback
9. Validation
10. Idempotency
11. Deduplication
12. Error Recovery
13. State Recovery
14. Checkpointing
15. Human Escalation

Explain how GenAI systems fail differently from traditional applications.

---

# 14. LLM Routing Patterns

Cover:

1. Cost-Based Routing
2. Quality-Based Routing
3. Latency-Based Routing
4. Capability-Based Routing
5. Task-Based Routing
6. Model Cascading
7. Small → Large Model Routing
8. Local → Cloud Model Fallback
9. Multi-Provider Routing
10. Intelligent Model Selection

Example:

Simple query → cheap model

Complex reasoning → powerful model

Sensitive data → local model

---

# 15. Caching Patterns

Teach:

1. Response Cache
2. Semantic Cache
3. Prompt Cache
4. Embedding Cache
5. Retrieval Cache
6. Tool Result Cache
7. Context Cache

Explain cache invalidation and correctness problems.

---

# 16. Evaluation Patterns

Teach GenAI evaluation deeply.

Cover:

1. Offline Evaluation
2. Online Evaluation
3. Golden Dataset
4. LLM-as-a-Judge
5. Human Evaluation
6. Regression Testing
7. Prompt Evaluation
8. RAG Evaluation
9. Agent Evaluation
10. Tool Evaluation
11. Retrieval Evaluation
12. Faithfulness
13. Relevance
14. Groundedness
15. Context Precision
16. Context Recall
17. Answer Correctness
18. Hallucination Detection

Show how to create an evaluation pipeline in Python.

---

# 17. Observability Patterns

Teach:

1. LLM Tracing
2. Prompt Logging
3. Token Tracking
4. Cost Tracking
5. Latency Tracking
6. Tool Tracing
7. Agent Tracing
8. Retrieval Tracing
9. Error Tracking
10. Evaluation Tracking
11. Distributed Tracing

Explain how to debug:

User Request
→ Agent
→ LLM
→ Tool
→ Database
→ Retrieval
→ Final Response

---

# 18. Cost Optimization Patterns

Cover:

1. Model Routing
2. Prompt Compression
3. Context Compression
4. Semantic Caching
5. Token Optimization
6. Batch Processing
7. Streaming
8. Smaller Models
9. Embedding Optimization
10. Retrieval Optimization
11. Fine-Tuning vs Prompting
12. Local Models

For every optimization explain:

Cost → Latency → Quality trade-off.

---

# 19. Multimodal AI Patterns

Cover:

1. Text → Text
2. Text → Image
3. Image → Text
4. Image → Image
5. Audio → Text
6. Text → Audio
7. Video → Text
8. Multimodal RAG
9. Multimodal Agents
10. Document Intelligence
11. Vision-Language Models

Build practical Python examples.

---

# 20. Structured Generation Patterns

Teach:

1. JSON Generation
2. Pydantic Output
3. Schema-Constrained Generation
4. Function Calling
5. Typed Responses
6. Validation + Retry
7. Partial Structured Output
8. Streaming Structured Output

Show how to prevent malformed LLM responses.

---

# 21. Streaming Patterns

Cover:

1. Token Streaming
2. Server-Sent Events
3. WebSocket Streaming
4. Streaming Tool Calls
5. Streaming Agent Responses
6. Partial Results
7. Backpressure
8. Async Streaming

Implement examples using Python/FastAPI.

---

# 22. Data & Database Patterns

Teach:

1. SQL + LLM
2. Text-to-SQL
3. SQL Agent
4. Database RAG
5. Vector Database
6. Graph Database
7. Graph RAG
8. Redis + LLM
9. Document Database + LLM
10. Multi-Database AI Architecture

Include security concerns around Text-to-SQL.

---

# 23. Fine-Tuning Patterns

Teach:

1. Prompting vs Fine-Tuning
2. Supervised Fine-Tuning
3. LoRA
4. QLoRA
5. PEFT
6. Instruction Tuning
7. Domain Adaptation
8. Fine-Tuning Dataset Design
9. Evaluation
10. Fine-Tuning vs RAG
11. Fine-Tuning vs Prompt Engineering

Explain when NOT to fine-tune.

---

# 24. Production Architecture Patterns

Teach how to build production-grade GenAI systems using:

- FastAPI
- Python
- PostgreSQL
- Redis
- Vector Database
- Kafka
- Docker
- Kubernetes
- Observability
- LLM providers
- Local models
- Cloud infrastructure

Cover:

1. LLM Gateway
2. AI Gateway
3. Model Router
4. Retrieval Service
5. Agent Service
6. Tool Service
7. Memory Service
8. Evaluation Service
9. Guardrail Service
10. Prompt Management Service

Show both:

### Monolithic AI Application

and

### Enterprise AI Microservices Architecture

---

# 25. Real-World Projects

After teaching the patterns, progressively build these projects.

## Project 1 — Enterprise RAG

Build a production-grade document Q&A system.

Requirements:

- PDF ingestion
- Chunking
- Embeddings
- Vector DB
- Hybrid search
- Re-ranking
- RAG
- Citations
- Evaluation
- Guardrails
- FastAPI
- Redis
- PostgreSQL

---

## Project 2 — AI Customer Support Agent

Features:

- Conversation memory
- RAG
- Tool calling
- Order lookup
- Refund API
- Human escalation
- Guardrails
- Observability
- Cost optimization

---

## Project 3 — AI Software Engineer Agent

Build an agent that can:

- Understand requirements
- Search code
- Read files
- Generate code
- Run tests
- Analyze errors
- Fix code
- Review code

Use:

- Planning
- Tool calling
- Reflection
- Memory
- Human approval
- Sandboxing

---

## Project 4 — Multi-Agent Enterprise System

Build:

User
↓
Supervisor Agent
↓
Research Agent
↓
Database Agent
↓
RAG Agent
↓
Analysis Agent
↓
Writer Agent
↓
Critic Agent
↓
Final Response

Explain why multi-agent architecture is necessary and when it is NOT necessary.

---

## Project 5 — Enterprise GenAI Platform

Design a platform supporting:

- Multiple LLM providers
- Model routing
- Prompt management
- RAG
- Agents
- Tools
- MCP
- Memory
- Guardrails
- Evaluation
- Observability
- Cost tracking
- Rate limiting
- Multi-tenancy

Treat this as a **real enterprise architecture interview problem**.

---

# 26. Mandatory Explanation Format

For EVERY pattern, use this structure:

## Pattern Name

### 1. Problem

What real-world problem does this solve?

### 2. Motivation

Why was this pattern created?

### 3. Core Idea

Explain the concept from first principles.

### 4. Architecture

Show an ASCII architecture diagram.

Example:

```text
User
  ↓
API
  ↓
Router
  ↓
LLM
  ↓
Tool
  ↓
Database
```

### 5. Internal Flow

Explain the complete request lifecycle step by step.

### 6. Simple Example

Use a very small Python example.

### 7. Production Example

Show a realistic enterprise implementation.

### 8. Python Implementation

Provide clean, runnable Python code.

### 9. Framework Implementation

If applicable, show how to implement it using:

- LangChain
- LangGraph
- LlamaIndex
- Pydantic
- FastAPI
- relevant vector databases

But NEVER hide the underlying implementation behind a framework.

### 10. Real-World Use Cases

Give at least 3 practical examples.

### 11. Advantages

Explain benefits.

### 12. Disadvantages

Explain limitations.

### 13. Trade-offs

Explain architectural trade-offs.

### 14. Failure Modes

Explain how the pattern can fail.

### 15. Security

Explain security concerns.

### 16. Scalability

Explain how it behaves at scale.

### 17. Cost

Explain token/API/infrastructure cost implications.

### 18. Observability

Explain what should be monitored.

### 19. Testing

Explain how to test it.

### 20. Evaluation

Explain how to measure quality.

### 21. When NOT to Use It

This section is mandatory.

### 22. Interview Questions

Give senior-level interview questions.

### 23. Design Exercise

Give me a practical architecture/design problem.

### 24. Coding Exercise

Give me a hands-on Python exercise.

---

# 27. Pattern Comparison

Whenever two or more patterns solve similar problems, create a comparison:

| Pattern | Problem Solved | Complexity | Cost | Latency | Accuracy | Best Use Case |
|---|---|---|---|---|---|---|

Examples:

- RAG vs Fine-Tuning
- Agent vs Workflow
- Single Agent vs Multi-Agent
- Vector Search vs Hybrid Search
- RAG vs Graph RAG
- Prompting vs Fine-Tuning
- Redis Memory vs Vector Memory
- Small Model vs Large Model
- Function Calling vs MCP

---

# 28. Production Engineering Requirements

Every implementation should consider:

- Clean architecture
- SOLID principles
- Type hints
- Pydantic
- Async Python
- FastAPI
- Error handling
- Logging
- Configuration management
- Environment variables
- Secrets management
- Retry
- Timeout
- Circuit breaker
- Rate limiting
- Authentication
- Authorization
- Testing
- Docker
- CI/CD
- Monitoring
- Metrics
- Tracing
- Security
- Cost control

Do not write toy code when discussing production architecture.

---

# 29. Python Standards

Use modern Python.

Prefer:

- Python 3.12+
- asyncio
- typing
- Pydantic
- FastAPI
- pytest
- httpx
- SQLAlchemy
- Redis
- PostgreSQL
- appropriate vector databases

Use clean project structures.

Example:

```text
genai-app/
├── app/
│   ├── api/
│   ├── agents/
│   ├── chains/
│   ├── prompts/
│   ├── retrieval/
│   ├── tools/
│   ├── memory/
│   ├── models/
│   ├── services/
│   ├── evaluation/
│   ├── guardrails/
│   └── config/
├── tests/
├── Dockerfile
├── requirements.txt
└── README.md
```

---

# 30. Learning Progression

Teach me in phases.

## Phase 1
GenAI Fundamentals

## Phase 2
LLM & Prompt Patterns

## Phase 3
RAG Patterns

## Phase 4
Context Engineering

## Phase 5
Tool Calling

## Phase 6
Agents

## Phase 7
Agentic Workflows

## Phase 8
Memory

## Phase 9
MCP

## Phase 10
Guardrails & Security

## Phase 11
Evaluation

## Phase 12
Observability

## Phase 13
Cost & Performance Optimization

## Phase 14
Multimodal AI

## Phase 15
Fine-Tuning

## Phase 16
Production Architecture

## Phase 17
Enterprise GenAI Systems

---

# 31. Do Not Skip Patterns

Maintain a **Pattern Mastery Checklist**.

Example:

```text
[ ] Prompt Template
[ ] Few Shot
[ ] Structured Output
[ ] Function Calling
[ ] Tool Calling
[ ] RAG
[ ] Hybrid Search
[ ] Re-ranking
[ ] Query Transformation
[ ] Agent
[ ] ReAct
[ ] Reflection
[ ] Planning
[ ] Multi-Agent
[ ] Memory
[ ] MCP
[ ] Guardrails
[ ] Evaluation
[ ] Observability
...
```

Before moving to the next major category:

1. Verify that all patterns in the category were covered.
2. Tell me which patterns are completed.
3. Identify missing patterns.
4. Do not silently skip any pattern.

If a pattern is obsolete, niche, experimental, or rarely used, still explain it briefly and clearly label its maturity.

---

# 32. Practical Learning Method

Do NOT teach everything in one giant response.

Teach **one pattern at a time**.

For each pattern:

1. Explain theory.
2. Explain architecture.
3. Explain internal mechanics.
4. Show simple Python implementation.
5. Show production implementation.
6. Explain failure modes.
7. Give real-world use cases.
8. Compare it with related patterns.
9. Give interview questions.
10. Give coding exercise.
11. Give architecture exercise.
12. Ask me to solve the exercises.
13. Review my answer.
14. Then move to the next pattern.

Use a difficulty progression:

### Level 1 — Beginner
Understand the concept.

### Level 2 — Intermediate
Implement the pattern.

### Level 3 — Advanced
Combine multiple patterns.

### Level 4 — Production
Handle reliability, security, scale, cost and observability.

### Level 5 — Architect
Design an enterprise system using the pattern.

---

# 33. Real-World Engineering Problems

Do not only provide ideal scenarios.

Include problems such as:

- LLM hallucination
- Prompt injection
- Retrieval failure
- Poor chunking
- Context overflow
- Token explosion
- High latency
- High API cost
- Model outage
- Tool failure
- Database failure
- Vector DB failure
- Agent infinite loops
- Incorrect tool selection
- Bad structured output
- Memory corruption
- Stale knowledge
- Conflicting sources
- Data leakage
- Multi-tenant isolation
- Race conditions
- Duplicate requests
- Long-running agents

For each problem teach the appropriate design patterns to solve it.

---

# 34. Architecture-First Thinking

For every major system, teach me to reason about:

```text
Requirements
    ↓
Constraints
    ↓
Quality Attributes
    ↓
Pattern Selection
    ↓
Architecture
    ↓
Technology Selection
    ↓
Implementation
    ↓
Testing
    ↓
Evaluation
    ↓
Observability
    ↓
Optimization
```

Do not blindly recommend technologies.

Explain WHY a technology or pattern is appropriate.

---

# 35. Senior-Level Design Questions

Throughout the course, challenge me with questions such as:

- How would you design a RAG system for 100M documents?
- How would you reduce LLM cost by 50%?
- How would you prevent prompt injection?
- How would you design an agent that cannot perform unauthorized actions?
- How would you handle an LLM provider outage?
- How would you evaluate hallucination?
- How would you debug a bad RAG answer?
- When should you use Graph RAG?
- When should you use an Agent instead of a workflow?
- When should you use multiple agents?
- How would you implement model routing?
- How would you build an enterprise AI gateway?
- How would you support multiple tenants?
- How would you guarantee data isolation?
- How would you monitor an AI agent in production?

---

# 36. Final Capstone

At the end, design and implement a complete:

# Enterprise GenAI Platform

Requirements:

```text
                     ┌───────────────┐
                     │     Users     │
                     └───────┬───────┘
                             ↓
                     ┌───────────────┐
                     │  AI Gateway   │
                     └───────┬───────┘
                             ↓
                 ┌───────────────────────┐
                 │    Model Router       │
                 └───────────┬───────────┘
                             ↓
          ┌──────────────────┼──────────────────┐
          ↓                  ↓                  ↓
       RAG Engine         Agent Engine      Workflows
          ↓                  ↓                  ↓
     Vector DB            Tools/MCP          Services
          ↓                  ↓                  ↓
          └──────────────────┼──────────────────┘
                             ↓
                       Guardrails
                             ↓
                         Evaluation
                             ↓
                       Observability
                             ↓
                       Final Response
```

The final system should include:

- LLM Gateway
- Model Router
- Prompt Management
- RAG
- Hybrid Search
- Re-ranking
- Agents
- Workflows
- Tool Calling
- MCP
- Memory
- Guardrails
- Evaluation
- Observability
- Caching
- Rate Limiting
- Cost Tracking
- Multi-Tenancy
- Security
- Human-in-the-loop
- Failure Recovery

Provide:

1. HLD
2. LLD
3. Architecture diagrams
4. Database design
5. API design
6. Python implementation
7. Project structure
8. Docker setup
9. Testing strategy
10. Evaluation framework
11. Monitoring strategy
12. Security architecture
13. Scaling strategy
14. Cost optimization
15. Deployment architecture
16. Production failure scenarios
17. Interview discussion

---

# 37. Teaching Rules

Follow these rules strictly:

1. Never skip an important GenAI pattern.
2. Never assume framework knowledge.
3. Explain fundamentals before frameworks.
4. Prefer Python.
5. Use real-world examples.
6. Include architecture diagrams.
7. Explain trade-offs.
8. Explain failure modes.
9. Include production considerations.
10. Include security.
11. Include scalability.
12. Include cost.
13. Include evaluation.
14. Include observability.
15. Include interview questions.
16. Include coding exercises.
17. Include system-design exercises.
18. Compare similar patterns.
19. Clearly identify experimental/advanced patterns.
20. Maintain a completion checklist.
21. Do not move forward until I understand the current pattern.
22. If I make a mistake, explain WHY it is wrong rather than simply giving the correct answer.
23. Continuously connect new patterns to previously learned patterns.
24. Use progressively more complex examples.
25. Prefer production-quality code over toy examples.

---