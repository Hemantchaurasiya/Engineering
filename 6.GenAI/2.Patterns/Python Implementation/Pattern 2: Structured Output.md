# Pattern 2: Structured Output — Python Version

Raw text is useless to a program — you need typed Python objects you can store, validate, and pass downstream. In Python, **Pydantic models + the OpenAI SDK's `.parse()` method** solve this the same way Spring AI's `.entity()` does: it auto-generates a schema instruction, sends it to the model, then deserializes the JSON response straight into your Python type.

This is a Python/FastAPI translation of the original Java/Spring AI pattern, preserving the same structure and behavior.

---

## What's happening

Instead of `ChatClient`'s `.entity()` terminal method, the Python OpenAI SDK gives you `client.chat.completions.parse()`, which does the same three things automatically:

1. Inspects your target Python type — a **Pydantic model** (the Python equivalent of a Java record).
2. Generates a JSON-schema instruction from that model and sends it to the provider as a `response_format` constraint (not just appended text — OpenAI enforces it at decode time).
3. Parses the model's JSON response into that type via Pydantic, so you get a validated, type-safe object — no manual parsing, no regex on the response.

### The core concept — why this pattern exists at all

Pattern 1 gave you `response.choices[0].message.content` — a plain string. Strings are fine for a chatbot UI, but useless the moment you want to:

- Save the result to a database column (`prep_time_minutes: int`, not `"20"` embedded somewhere in a paragraph)
- Pass a field into another function (`if recipe.prep_time_minutes < 30: ...`)
- Guarantee the model didn't forget a field, misspell a key, or wrap the JSON in explanatory prose

**Real-world analogy:** think of the difference between a waiter who *tells you* your order verbally versus a waiter who fills out a printed order slip with labeled boxes: Dish / Quantity / Allergies / Table Number. The kitchen doesn't want a sentence like "the customer at table 4 would like a pizza without cheese" — it wants a form it can process mechanically, every time, in the same shape. That printed slip is what a Pydantic model gives your code: a guaranteed shape the rest of your program can rely on without ever having to "read" and interpret free text.

This is exactly why the original doc calls this pattern "the dependency every later pattern leans on" — a router deciding which specialist to call, an agent scoring its own output, a planner breaking a task into steps: all of those need a *reliable, typed* answer from the model, not prose you have to parse by hand.

---

## Setup

### `requirements.txt`

```
fastapi==0.115.0
uvicorn==0.32.0
openai==1.54.0
pydantic==2.9.2
python-dotenv==1.0.1
```

Pydantic is the direct equivalent of Jackson + Java records here: it defines the shape, validates the data, and (critically) can *generate* the JSON schema the model needs to see.

---

## Code

### 1. Define your target type as a Pydantic model

This plays the same role as the Java `record Recipe(...)`.

```python
# models.py
from pydantic import BaseModel, Field
from typing import List


class Recipe(BaseModel):
    title: str
    ingredients: List[str]
    steps: List[str]
    prep_time_minutes: int = Field(
        description="Total preparation time in minutes"
    )
```

`Field(description=...)` is the Python equivalent of Java's `@JsonPropertyDescription("...")` — it gets embedded in the schema sent to the model and meaningfully improves accuracy on ambiguous fields (more on this below).

### 2. Use `.parse()` to get it back typed

```python
# main.py
import os
from dotenv import load_dotenv
from fastapi import FastAPI, Query
from openai import OpenAI
from typing import List

from models import Recipe

load_dotenv()

app = FastAPI()
client = OpenAI(api_key=os.getenv("OPENAI_API_KEY"))


@app.get("/api/structured/recipe", response_model=Recipe)
def get_recipe(dish: str = Query(...)):
    completion = client.chat.completions.parse(
        model="gpt-4o-mini",
        messages=[
            {"role": "user", "content": f"Generate a simple recipe for {dish}."}
        ],
        response_format=Recipe,   # <-- typed deserialization happens here
    )
    return completion.choices[0].message.parsed
```

- `response_format=Recipe` is the direct equivalent of `.entity(Recipe.class)`. The SDK converts the Pydantic model into a JSON Schema, sends it to OpenAI's structured-output mode, and OpenAI **constrains its own token generation** to only produce JSON matching that schema — this is stronger than Spring AI's prompt-instruction fallback, because the provider enforces it at decode time, not just via a "please output JSON like this" instruction.
- `completion.choices[0].message.parsed` is already a fully-constructed, validated `Recipe` instance — not a dict, not a string.

### 3. For collections, wrap in a container model

Python's generics erase at runtime too (there's no `ParameterizedTypeReference` needed, but the *reason* for wrapping is the same idea: give the schema generator one concrete top-level type to work with).

```python
class RecipeList(BaseModel):
    recipes: List[Recipe]


@app.get("/api/structured/recipes", response_model=RecipeList)
def get_recipes(cuisine: str = Query(...)):
    completion = client.chat.completions.parse(
        model="gpt-4o-mini",
        messages=[
            {"role": "user", "content": f"Give me 3 distinct recipes for {cuisine} cuisine."}
        ],
        response_format=RecipeList,
    )
    return completion.choices[0].message.parsed
```

> **Why wrap in `RecipeList` instead of passing `List[Recipe]` directly?** OpenAI's structured-output mode requires the top-level `response_format` to be a single JSON *object* schema, not a bare array. Wrapping in a small container model (`{"recipes": [...]}`) is the idiomatic Python fix — conceptually identical to why Spring AI needs `ParameterizedTypeReference` to preserve `List<Recipe>`'s generic type at runtime instead of erasing it to raw `List`.

Run it:

```bash
uvicorn main:app --reload --port 8080
```

---

## Try it

```bash
curl "http://localhost:8080/api/structured/recipe?dish=margherita%20pizza"
```

Returns:

```json
{
  "title": "Margherita Pizza",
  "ingredients": [
    "Pizza dough",
    "Tomato sauce",
    "Fresh mozzarella",
    "Basil",
    "Olive oil"
  ],
  "steps": [
    "Preheat oven to 250°C",
    "Spread sauce on dough",
    "Add mozzarella",
    "Bake 8-10 min",
    "Top with basil"
  ],
  "prep_time_minutes": 20
}
```

---

## Mapping Java/Spring AI concepts to Python

| Spring AI / Java | Python | What it's really doing |
|---|---|---|
| Java `record Recipe(...)` | Pydantic `class Recipe(BaseModel)` | Defines the target shape; both are declarative, immutable-by-default data containers |
| `.call().entity(Recipe.class)` | `.parse(response_format=Recipe)` → `.choices[0].message.parsed` | Sends schema, gets back a validated typed object instead of a string |
| `ParameterizedTypeReference<List<Recipe>>` | A wrapper `RecipeList(BaseModel)` containing `recipes: List[Recipe]` | Both work around the runtime limitation that a raw generic/array can't be the top-level schema target |
| `@JsonPropertyDescription("...")` | `Field(description="...")` | Both inject a human-readable hint into the generated JSON schema, improving model accuracy on ambiguous fields |
| Jackson (deserialization + validation) | Pydantic (deserialization + validation) | Both convert JSON → typed object and raise on type mismatches (missing field, wrong type, etc.) |

---

## Why "type-safe, validated output" matters

Without this pattern, every response is a raw string, and *your code* has to guess where the data is inside it. Consider the failure modes this pattern eliminates:

- **Missing fields** — the model forgets `prep_time_minutes`. Pydantic raises a validation error immediately, at the boundary, instead of your code crashing three functions later with a confusing `KeyError`.
- **Wrong types** — the model returns `"prep_time_minutes": "twenty minutes"` instead of an integer. Pydantic either coerces it correctly or fails loudly and immediately — you find out *before* that bad value corrupts a database row or a downstream calculation.
- **Extra prose around the JSON** — without structured-output mode, models sometimes wrap JSON in explanations ("Sure! Here's the recipe: ```json ... ```"). OpenAI's schema-constrained decoding (triggered by `response_format=<PydanticModel>`) prevents this at the source, rather than you writing regex to strip markdown fences.

**Real-world example:** imagine an insurance company using an LLM to read incoming claim emails and extract `claimant_name`, `policy_number`, `incident_date`, and `damage_amount`. Without structured output, you'd get back paragraphs of varying format that a human (or fragile regex) has to re-read every time. With structured output, every single response — regardless of how the claim was worded — comes back as the exact same four fields, ready to insert directly into the claims database. The LLM becomes a **data entry clerk with a fixed form**, not a conversational partner you have to interpret.

This is also why the doc calls this "the dependency every later pattern leans on": a **router** (Pattern 3+) needs a typed decision like `{"route": "billing_agent"}`, not a sentence to parse; an **evaluator** step needs a typed `{"score": 8, "feedback": "..."}`, not prose; a **planner** needs a typed list of steps it can iterate over in code. Every one of those is this exact pattern — a Pydantic model plus `.parse()` — just with a different schema.

---

## Notes from production use

- Give ambiguous fields explicit `Field(description=...)` hints — this is the single highest-leverage lever for structured-output accuracy, exactly as `@JsonPropertyDescription` is in the Java version.
- `response_format=<PydanticModel>` uses OpenAI's native structured-output enforcement (JSON schema-constrained decoding) when the model supports it (e.g. `gpt-4o`, `gpt-4o-mini`). For providers or models without native support, you'd fall back to prompting for JSON and parsing manually with `Recipe.model_validate_json(text)` inside a `try/except`, which is the direct equivalent of Spring AI's prompt-based fallback path.
- Keep Pydantic models flat and well-typed where possible (`int`, `str`, `List[str]`) — deeply nested or ambiguous unions increase the chance of malformed output even with schema enforcement, in both ecosystems.

---

## Project structure

```
pattern2-structured-output/
├── main.py
├── models.py
├── requirements.txt
├── .env
└── README.md
```

---

## Pattern series progress

| # | Pattern | Adds |
|---|---|---|
| 1 | Direct Prompting | ✅ Single stateless call |
| 2 | Structured Output | ✅ Typed, validated responses via Pydantic (this doc) |
| 3 | Chaining / Routing | Output of one call feeds or routes to the next |
| 4 | RAG | Retrieved context injected into the prompt |
| 5 | Tool Calling | Model requests function execution, app runs it |
| 6 | Agents | Looped call/decide/act cycle with self-correction |

*(To be filled in as each pattern's source doc is provided.)*