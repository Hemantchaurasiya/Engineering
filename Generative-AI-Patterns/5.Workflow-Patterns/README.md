# AI Workflow Patterns — Mastering Them One by One

This series walks through the 16 most common workflow patterns used when
building production AI systems with **LangChain** and **LangGraph**. Each
pattern now has **its own file**, with a simple explanation, a real
enterprise example, a diagram, and complete, runnable Python code.

### Environment used across this whole series

```bash
python --version        # Python 3.12 (any 3.11–3.13 works)

pip install "langgraph==1.2.10" "langchain==1.3.14" "langchain-ollama==1.1.0" "pydantic==2.9.2"

# Ollama itself must be installed and running locally: https://ollama.com
ollama pull llama3.1:8b
```

We use `langchain-ollama` (running models locally through Ollama) as the
model provider from Pattern 2 onward. Swap the model name in
`ChatOllama(model=...)` for any model you have pulled locally (`qwen2.5`,
`mistral`, `gpt-oss`, etc.) — the workflow code itself doesn't change.

> Note: Pattern 1 (Sequential Workflow) uses `langchain-anthropic`, since it
> was written before the Ollama preference was given.

### Patterns

| # | Pattern | File | Status |
|---|---------|------|--------|
| 1 | Sequential Workflow | [01-sequential-workflow.md](01-sequential-workflow.md) | ✅ |
| 2 | Parallel Workflow | [02-parallel-workflow.md](02-parallel-workflow.md) | ✅ |
| 3 | Conditional Workflow | [03-conditional-workflow.md](03-conditional-workflow.md) | ✅ |
| 4 | Branching | [04-branching.md](04-branching.md) | ✅ |
| 5 | Routing | [05-routing.md](05-routing.md) | ✅ |
| 6 | Map-Reduce | [06-map-reduce.md](06-map-reduce.md) | ✅ |
| 7 | Fan-Out / Fan-In | [07-fan-out-fan-in.md](07-fan-out-fan-in.md) | ✅ |
| 8 | Iterative Workflow | [08-iterative-workflow.md](08-iterative-workflow.md) | ✅ |
| 9 | Loop Workflow | [09-loop-workflow.md](09-loop-workflow.md) | ✅ |
| 10 | Retry Pattern | [10-retry-pattern.md](10-retry-pattern.md) | ✅ |
| 11 | Fallback Pattern | [11-fallback-pattern.md](11-fallback-pattern.md) | ✅ |
| 12 | Human Approval Workflow | [12-human-approval-workflow.md](12-human-approval-workflow.md) | ✅ |
| 13 | Event-Driven Workflow | [13-event-driven-workflow.md](13-event-driven-workflow.md) | ✅ |
| 14 | Async Workflow | [14-async-workflow.md](14-async-workflow.md) | ✅ |
| 15 | Long-Running Workflow | [15-long-running-workflow.md](15-long-running-workflow.md) | ✅ |
| 16 | Stateful Workflow | [16-stateful-workflow.md](16-stateful-workflow.md) | ✅ |

## 🎉 All 16 patterns complete

From a straight-line Sequential pipeline to a Stateful Workflow whose memory
outlives the process that started it — each file above is self-contained,
with a full explanation, a real enterprise example, diagrams, and
production-quality LangGraph code you can run locally against Ollama.
