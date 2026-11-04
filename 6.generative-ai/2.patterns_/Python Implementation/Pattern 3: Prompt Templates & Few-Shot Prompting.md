# Pattern 3: Prompt Templates & Few-Shot Prompting — Python Version

Hardcoded prompt strings don't scale — you need reusable templates with variable substitution, and for many tasks you need to *show* the model examples of correct input→output behavior rather than just describe it. Few-shot prompting consistently improves accuracy on classification, formatting, and style-matching tasks where instructions alone are ambiguous.

This is a Python/FastAPI translation of the original Java/Spring AI pattern, preserving the same structure and behavior.

---

## What's happening

In Python, **Jinja2** plays the role of Spring AI's `PromptTemplate` (which itself uses StringTemplate syntax under the hood) — it separates the prompt's *structure* (reusable, often loaded from a `.txt`/`.j2` file) from its *data* (the variables filled in per request).

Few-shot prompting layers on top of either approach: you prepend a handful of `user`/`assistant` message pairs demonstrating the exact input→output behavior you want, *before* the real user message — the model pattern-matches against them rather than relying purely on your written instructions.

### The core concept — why templates exist at all

If every prompt is an inline f-string scattered across your codebase, you get the same problems you'd get from hardcoding SQL strings all over a Java app: no reuse, no single place to tune wording, and non-engineers (product managers, prompt engineers, support leads) can't touch the copy without a code change and a deploy.

**Real-world analogy:** think of a hospital's discharge-instructions form. The *form* is fixed and reusable — "Patient Name: ___, Medication: ___, Follow-up Date: ___" — but the *data* filled into the blanks changes every time. Nobody redesigns the form for each patient; they just fill in the blanks. A `PromptTemplate` (or a Jinja2 template) is exactly that fixed form for a prompt: the wording, structure, and constraints stay constant and get reviewed once, while `{text}`, `{tone}`, `{ticket}` are the blanks filled in per request.

### The core concept — why few-shot examples beat pure instructions

Telling the model a *rule* ("classify sentiment") is a one-dimensional signal. Showing the model 2-3 *worked examples*, especially ambiguous edge cases, anchors exactly how conflicting signals should be resolved — something natural language instructions struggle to fully specify.

**Real-world analogy:** think of training a new employee to grade essays. You could hand them a rubric ("deduct points for weak arguments"), but a rubric alone leaves a lot of judgment calls ambiguous. What actually works is sitting them down with 3 real graded essays — one that got an A despite a typo, one that got a C despite a good idea, one that got a B in between — and saying "here's how those were graded, now do the next one." The worked examples calibrate their judgment far better than the written rule alone. That's exactly what few-shot examples do for the model.

---

## Setup

### `requirements.txt`

```
fastapi==0.115.0
uvicorn==0.32.0
openai==1.54.0
jinja2==3.1.4
python-dotenv==1.0.1
```

Jinja2 is the direct equivalent of Spring's StringTemplate engine — it supports `{{ variable }}` substitution, plus conditionals and loops for more complex templates.

---

## Code

### 1. Template with inline variables (simplest case)

```python
# main.py
import os
from dotenv import load_dotenv
from fastapi import FastAPI, Query
from openai import OpenAI
from jinja2 import Template

load_dotenv()

app = FastAPI()
client = OpenAI(api_key=os.getenv("OPENAI_API_KEY"))

SUMMARIZE_TEMPLATE = Template("""\
Summarize the following text in a {{ tone }} tone.
Keep it to 2 sentences maximum.

Text: {{ text }}
""")


@app.get("/api/template/summarize")
def summarize(text: str = Query(...), tone: str = Query(...)):
    rendered_prompt = SUMMARIZE_TEMPLATE.render(text=text, tone=tone)

    response = client.chat.completions.create(
        model="gpt-4o-mini",
        messages=[{"role": "user", "content": rendered_prompt}],
    )
    return {"summary": response.choices[0].message.content}
```

`Template(...).render(**vars)` is the direct equivalent of `PromptTemplate.render(Map.of(...))` — structure defined once, data substituted per call.

### 2. Template loaded from an external resource file

(Keeps prompts out of Python source, lets non-devs edit copy — same motivation as the `.st` classpath resource in the Java version.)

**`prompts/classify_ticket.j2`**

```text
You are a support ticket triager.
Classify the ticket into exactly one category: BILLING, BUG, FEATURE_REQUEST, OTHER.
Respond with only the category name.

Ticket: {{ ticket }}
```

```python
from jinja2 import Environment, FileSystemLoader

# Equivalent of Spring's @Value("classpath:/prompts/...") Resource injection —
# loaded once at startup, reused across requests.
jinja_env = Environment(loader=FileSystemLoader("prompts"))
classify_template = jinja_env.get_template("classify_ticket.j2")


@app.get("/api/template/classify")
def classify(ticket: str = Query(...)):
    prompt = classify_template.render(ticket=ticket)
    response = client.chat.completions.create(
        model="gpt-4o-mini",
        messages=[{"role": "user", "content": prompt}],
    )
    return {"category": response.choices[0].message.content}
```

`Environment(loader=FileSystemLoader("prompts"))` + `get_template(...)` is the Python equivalent of Spring auto-loading a classpath resource: the template file lives outside your `.py` source, and Jinja2 caches/parses it once, not on every request.

### 3. Few-shot prompting

Build an explicit `messages` list with example pairs before the real question — this is *identical* in shape to the Java version, because both ultimately just construct an ordered list of role-tagged turns.

```python
@app.get("/api/fewshot/extract-sentiment")
def extract_sentiment(review: str = Query(...)):
    messages = [
        {"role": "system", "content": "Extract sentiment as exactly one word: POSITIVE, NEGATIVE, or NEUTRAL."},

        # --- few-shot examples ---
        {"role": "user", "content": "The delivery was late but the product quality is excellent."},
        {"role": "assistant", "content": "POSITIVE"},

        {"role": "user", "content": "Worst purchase I've made all year, broke in two days."},
        {"role": "assistant", "content": "NEGATIVE"},

        {"role": "user", "content": "It does what it says on the box, nothing more nothing less."},
        {"role": "assistant", "content": "NEUTRAL"},
        # --- end examples ---

        {"role": "user", "content": review},
    ]

    response = client.chat.completions.create(
        model="gpt-4o-mini",
        messages=messages,
    )
    return {"sentiment": response.choices[0].message.content}
```

Run it:

```bash
uvicorn main:app --reload --port 8080
```

---

## Try it

```bash
curl "http://localhost:8080/api/fewshot/extract-sentiment?review=Customer%20service%20never%20replied%20to%20my%20emails"
```

```json
{"sentiment": "NEGATIVE"}
```

---

## Mapping Java/Spring AI concepts to Python

| Spring AI / Java | Python | What it's really doing |
|---|---|---|
| `PromptTemplate` (StringTemplate `{var}` syntax) | `jinja2.Template` (`{{ var }}` syntax) | Both separate reusable prompt structure from per-request data |
| `template.render(Map.of("text", text, "tone", tone))` | `template.render(text=text, tone=tone)` | Both substitute variables into the template string |
| `@Value("classpath:/prompts/classify-ticket.st")` + `Resource` | `Environment(loader=FileSystemLoader("prompts"))` + `get_template(...)` | Both load a template file from outside the source code, once, and reuse it |
| `List.of(SystemMessage, UserMessage, AssistantMessage, ...)` | `[{"role": "system"/"user"/"assistant", "content": ...}, ...]` | Both build an ordered list of role-tagged conversation turns to demonstrate examples before the real question |
| `new Prompt(messages)` | `messages=[...]` argument | Both wrap the message list as the actual request payload |
| `.st` StringTemplate conditionals/loops | Jinja2 `{% if %}` / `{% for %}` | Both support more complex templating beyond flat substitution |

**Key insight:** few-shot prompting isn't a separate mechanism from a "normal" prompt — it's just *more entries* in the same `messages` list you've been using since Pattern 1. There's no special API for "example mode"; you're simply pre-populating the conversation history with turns that never really happened, so the model conditions on them as if they did. This is the same trick used for conversation memory in later patterns — the model only ever sees "a list of turns," and whether those turns are real history, injected examples, or retrieved context is entirely up to what your application code puts in the list.

---

## Why few-shot beats pure instructions here

"Extract sentiment" sounds unambiguous, but real reviews are mixed — the example above ("late delivery but excellent product") has both a complaint and praise in the same sentence. Telling the model the rule isn't as reliable as showing it 2-3 edge cases of how to resolve ambiguity — the examples anchor exactly which signal should dominate the classification, something natural-language instructions struggle to fully specify.

**Real-world example:** a moderation team labeling customer reviews as spam/not-spam could write a 500-word policy document, and reviewers would still disagree on edge cases (a genuine review that happens to include a promo code; a short review that's just "wow"). What actually converges reviewer judgment fast is a short calibration set: "here are 5 borderline cases and how they were labeled." Few-shot prompting gives the model that same calibration set instead of relying on the model's own interpretation of an abstract policy.

---

## Production tips

- Keep few-shot examples diverse and representative of edge cases, not just the easy cases — 3-5 examples is usually the sweet spot; more adds latency and cost (every example is tokens billed on *every* request) without much accuracy gain.
- Jinja2 templates support `{% if %}` and `{% for %}` just like StringTemplate, so more complex prompts (e.g. optionally including a "few-shot" block only when examples are configured) can stay data-driven rather than hardcoded as Python string concatenation.
- For templates reused across many endpoints, load the `Environment` and templates once at module/startup level (as shown above) rather than re-parsing the template file on every request — this mirrors defining a single `@Bean PromptTemplate` and injecting it in the Java version.
- Consider storing few-shot examples themselves in a config file (JSON/YAML) rather than inline in code, so non-engineers can add/edit examples the same way they'd edit template copy.

---

## Project structure

```
pattern3-templates-fewshot/
├── main.py
├── prompts/
│   └── classify_ticket.j2
├── requirements.txt
├── .env
└── README.md
```

---

## Pattern series progress

| # | Pattern | Adds |
|---|---|---|
| 1 | Direct Prompting | ✅ Single stateless call |
| 2 | Structured Output | ✅ Typed, validated responses via Pydantic |
| 3 | Prompt Templates & Few-Shot | ✅ Reusable templates + example-driven prompting (this doc) |
| 4 | Chaining / Routing | Output of one call feeds or routes to the next |
| 5 | RAG | Retrieved context injected into the prompt |
| 6 | Tool Calling | Model requests function execution, app runs it |
| 7 | Agents | Looped call/decide/act cycle with self-correction |

*(To be filled in as each pattern's source doc is provided.)*