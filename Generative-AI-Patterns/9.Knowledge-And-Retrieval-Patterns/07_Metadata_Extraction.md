# Pattern 7: Metadata Extraction

## 1. Introduce the Pattern

**Metadata extraction** is the process of pulling structured, filterable facts out of a document — separate from its embeddable text — so that retrieval can later be narrowed by those facts *before or alongside* semantic similarity search. Things like: which team owns this document, when was it last updated, what access level does it require, what product/system does it mention, what date range does it cover.

```
Document text (goes to embedding/chunking):
  "To reset your password, go to Settings > Security..."

Extracted metadata (goes to the vector DB's metadata fields, NOT embedded):
  {
    "doc_id": "it-faq-003",
    "team": "IT",
    "access_level": "all_employees",
    "last_updated": "2026-06-12",
    "doc_type": "faq",
    "entities": ["password reset", "account settings"]
  }
```

The embedding captures *what the text means*. The metadata captures *facts about the document* that meaning alone can't express — and that a user might want to filter or restrict by, regardless of semantic relevance.

## 2. The Problem It Solves

Pure semantic search (Pattern 1) answers "what's the most similar content to this query?" — but production systems constantly need answers to questions that similarity search alone can't express:

- **Access control**: "Only show me documents I'm allowed to see." A junior employee's query shouldn't retrieve an executive compensation policy just because it's semantically similar — access level has to be enforced regardless of similarity score.
- **Recency**: "What's our *current* remote work policy?" If the corpus contains three versions of the policy from three different years, semantic similarity alone can't tell recent from outdated — all three "look" equally relevant to the embedding model. You need a `last_updated` field to filter to (or prioritize) the current one.
- **Scoping by team/product/type**: "Show me only Platform team runbooks," or "only search HR documents," or "exclude anything marked deprecated." These are exact, structured constraints — the wrong tool for embeddings, the right tool for metadata filters.
- **Entity-based lookups**: "Every document that mentions the `payments-api` service." An embedding of a document that briefly mentions `payments-api` in passing might not rank it highly for a query specifically about that service — but an extracted entity list makes exact lookup trivial and reliable.

Without metadata extraction, a retrieval system is forced to solve all of these problems through embedding similarity alone, which is the wrong tool for exact, structured constraints — semantic search finds "similar," not "matches this exact rule." Pattern 13 (Retrieval Filters) is where these extracted fields actually get *applied* at query time; this pattern is about generating them reliably in the first place.

## 3. Realistic Enterprise Scenario

DocuMind's corpus mixes documents with wildly different access requirements and lifecycles:

- HR policies with an `access_level` of `all_employees` vs. `managers_only` (e.g. performance review guidelines)
- Runbooks with a `team` owner and a `last_reviewed` date — some haven't been reviewed in over a year and should be flagged as possibly stale
- Product documentation that mentions specific internal service names (`payments-api`, `auth-service`) that should be searchable as exact entities, not just embedded text

When an employee asks a question, DocuMind needs to combine semantic relevance (Pattern 1) with **hard constraints**: never return a `managers_only` document to a non-manager, prefer the most recently updated version when duplicates exist, and let users explicitly scope a search ("just search Platform team docs").

This file builds the extraction step that produces the metadata those constraints run against.

## 4. Architecture / Flow Diagram

```
┌─────────────────────────┐
│  ParsedDocument            │   (from Pattern 6)
│  { text, headings, tables }│
└─────────────┬─────────────┘
              │
              ▼
   ┌────────────────────────────────────────┐
   │           Metadata Extractors             │
   │                                            │
   │  ┌──────────────┐  ┌──────────────────┐  │
   │  │ Rule-based     │  │ LLM-based          │  │
   │  │ extractors     │  │ extractors         │  │
   │  │ (fast, exact)  │  │ (flexible, fuzzy)  │  │
   │  │                │  │                    │  │
   │  │ - filename/path│  │ - entities         │  │
   │  │   → team/type  │  │   mentioned         │  │
   │  │ - regex dates  │  │ - document summary  │  │
   │  │ - front-matter │  │ - inferred doc_type │  │
   │  │   (YAML header)│  │   when rules fail   │  │
   │  └──────┬─────────┘  └────────┬───────────┘  │
   └─────────┼───────────────────┼──────────────┘
             │                   │
             ▼                   ▼
   ┌────────────────────────────────────────┐
   │        Merged, validated metadata          │
   │  { doc_id, team, doc_type, access_level,   │
   │    last_updated, entities[], summary }      │
   └─────────────┬─────────────────────────┘
                 │
                 ▼
     attached to every chunk from Pattern 3/4/5
                 │
                 ▼
     → Pattern 9 (Vector DB storage, as metadata fields)
     → Pattern 13 (Retrieval Filters, applied at query time)
```

Two extraction strategies run **side by side**: cheap, deterministic rule-based extraction for anything that has a reliable structural signal (a filename pattern, a YAML front-matter block, a regex-matchable date), and LLM-based extraction for anything that requires actual understanding (which named services does this runbook discuss, what's a one-line summary).

## 5. Request-to-Response Walkthrough

1. A `ParsedDocument` (from Pattern 6) arrives, along with basic file info (path, source system).
2. **Rule-based extractors run first**, because they're fast, free, and deterministic: checking the file path for team folder conventions (`/runbooks/platform/...` → `team: Platform`), parsing a YAML front-matter block if present, regex-matching common date formats for `last_updated`.
3. For anything rule-based extraction couldn't confidently determine (e.g. no front-matter, ambiguous path), an **LLM-based extractor** runs a structured-output prompt against `ChatOllama`, asking it to infer `doc_type`, list mentioned service/system entities, and produce a one-line summary — all constrained to a JSON schema.
4. The two extraction results are **merged**, with rule-based values taking priority when both exist (since they're more trustworthy) and LLM-based values filling gaps.
5. A **validation pass** checks the merged metadata against expected value sets (e.g. `access_level` must be one of a known enum) and flags anything that doesn't validate, rather than silently accepting garbage.
6. The final metadata dictionary is attached to the parsed document and, after chunking, propagated onto every chunk derived from it — so every chunk in the vector database carries the same filterable facts as its parent document.

## 6. Why This Pattern Is Appropriate

| Approach | Strength | Weakness |
|---|---|---|
| No metadata — embeddings only | Simple | Cannot express access control, recency, or exact scoping at all |
| Manual tagging by content authors | Accurate when done | Doesn't scale, inconsistent across authors, frequently forgotten/stale |
| Rule-based extraction only | Fast, free, deterministic | Fails on anything without a clean structural signal (no front-matter, ambiguous filenames) |
| LLM-based extraction only | Flexible, handles unstructured cases | Slower, costs inference, occasionally hallucinates a value with no signal to catch it |
| **This pattern**: rule-based first, LLM-based as fallback, validated merge | Cheap and reliable where possible, flexible where necessary, with a validation safety net | More implementation complexity than either approach alone |

The hybrid approach mirrors a broader theme across this series (see also Pattern 3 vs. Pattern 4 for chunking): **prefer cheap, deterministic methods wherever a reliable signal exists, and reserve the more expensive, flexible LLM-based approach for genuinely ambiguous cases** — not as the default for everything.

## 7. Production-Quality Python Implementation

```python
"""
metadata_extraction_pipeline.py

Production-quality hybrid metadata extractor for DocuMind: rule-based
extraction first (filenames, YAML front-matter, regex dates), with an
LLM-based fallback (via ChatOllama) for entities/summary/doc_type when
rules can't confidently determine them. Includes a validation pass.
"""

from __future__ import annotations

import json
import re
from dataclasses import dataclass, field
from datetime import datetime
from pathlib import Path
from typing import Optional

from langchain_ollama import ChatOllama

CHAT_MODEL = "llama3.1"

VALID_ACCESS_LEVELS = {"all_employees", "managers_only", "team_only", "confidential"}
VALID_DOC_TYPES = {"runbook", "policy", "faq", "postmortem", "wiki_page", "unknown"}

# Team folder conventions used by DocuMind's internal wiki export.
TEAM_PATH_MAP = {
    "platform": "Platform",
    "hr": "HR",
    "it": "IT",
    "security": "Security",
}

DATE_PATTERN = re.compile(r"\b(\d{4})[-/](\d{2})[-/](\d{2})\b")
FRONT_MATTER_PATTERN = re.compile(r"^---\n(.*?)\n---\n", re.DOTALL)


@dataclass
class DocumentMetadata:
    doc_id: str
    team: Optional[str] = None
    doc_type: str = "unknown"
    access_level: str = "all_employees"
    last_updated: Optional[str] = None
    entities: list = field(default_factory=list)
    summary: Optional[str] = None
    extraction_warnings: list = field(default_factory=list)


# --------------------------------------------------------------------------
# Rule-based extraction
# --------------------------------------------------------------------------

def extract_rule_based(doc_id: str, file_path: str, text: str) -> DocumentMetadata:
    meta = DocumentMetadata(doc_id=doc_id)

    # 1. Team from path convention, e.g. "runbooks/platform/db-failover.md"
    path_parts = [p.lower() for p in Path(file_path).parts]
    for part in path_parts:
        if part in TEAM_PATH_MAP:
            meta.team = TEAM_PATH_MAP[part]
            break

    # 2. YAML-style front-matter, e.g.:
    #    ---
    #    access_level: managers_only
    #    last_updated: 2026-05-01
    #    ---
    front_matter_match = FRONT_MATTER_PATTERN.match(text)
    if front_matter_match:
        for line in front_matter_match.group(1).split("\n"):
            if ":" not in line:
                continue
            key, _, value = line.partition(":")
            key, value = key.strip(), value.strip()
            if key == "access_level":
                meta.access_level = value
            elif key == "last_updated":
                meta.last_updated = value
            elif key == "team" and not meta.team:
                meta.team = value
            elif key == "doc_type":
                meta.doc_type = value

    # 3. Fallback date detection: most recent date mentioned anywhere in text
    if not meta.last_updated:
        dates_found = DATE_PATTERN.findall(text)
        if dates_found:
            parsed_dates = []
            for y, m, d in dates_found:
                try:
                    parsed_dates.append(datetime(int(y), int(m), int(d)))
                except ValueError:
                    continue
            if parsed_dates:
                meta.last_updated = max(parsed_dates).date().isoformat()

    return meta


# --------------------------------------------------------------------------
# LLM-based extraction (fallback for entities / doc_type / summary)
# --------------------------------------------------------------------------

ENTITY_EXTRACTION_PROMPT = """You are a metadata extraction assistant for an internal \
knowledge base. Given a document, extract structured metadata as JSON only — no \
preamble, no markdown fences, just the raw JSON object.

Return exactly this shape:
{{
  "doc_type": one of ["runbook", "policy", "faq", "postmortem", "wiki_page", "unknown"],
  "entities": [list of specific system/service/product names mentioned, max 8],
  "summary": "one sentence, under 25 words, describing what this document is about"
}}

Document text:
\"\"\"
{text}
\"\"\"

JSON:"""


class LLMMetadataExtractor:
    def __init__(self, model: str = CHAT_MODEL):
        self._llm = ChatOllama(model=model, temperature=0)

    def extract(self, text: str) -> dict:
        truncated = text[:3000]  # keep the prompt bounded for long documents
        prompt = ENTITY_EXTRACTION_PROMPT.format(text=truncated)

        response = self._llm.invoke(prompt)
        raw = response.content.strip()
        raw = re.sub(r"^```json\s*|\s*```$", "", raw.strip())

        try:
            parsed = json.loads(raw)
        except json.JSONDecodeError:
            return {"doc_type": "unknown", "entities": [], "summary": None}

        return {
            "doc_type": parsed.get("doc_type", "unknown"),
            "entities": parsed.get("entities", []) or [],
            "summary": parsed.get("summary"),
        }


# --------------------------------------------------------------------------
# Merge + validate
# --------------------------------------------------------------------------

class MetadataExtractionPipeline:
    def __init__(self):
        self._llm_extractor = LLMMetadataExtractor()

    def extract(self, doc_id: str, file_path: str, text: str) -> DocumentMetadata:
        meta = extract_rule_based(doc_id, file_path, text)

        # Only call the LLM for what rules couldn't determine — avoids
        # unnecessary inference cost when the front-matter already had
        # a doc_type, and keeps entities/summary (which rules can't
        # produce at all) always LLM-derived.
        needs_llm = meta.doc_type == "unknown"
        llm_result = self._llm_extractor.extract(text)

        if needs_llm:
            meta.doc_type = llm_result["doc_type"]
        meta.entities = llm_result["entities"]
        meta.summary = llm_result["summary"]

        self._validate(meta)
        return meta

    @staticmethod
    def _validate(meta: DocumentMetadata) -> None:
        if meta.access_level not in VALID_ACCESS_LEVELS:
            meta.extraction_warnings.append(
                f"Unrecognized access_level '{meta.access_level}', defaulting to 'all_employees'.")
            meta.access_level = "all_employees"

        if meta.doc_type not in VALID_DOC_TYPES:
            meta.extraction_warnings.append(
                f"Unrecognized doc_type '{meta.doc_type}', defaulting to 'unknown'.")
            meta.doc_type = "unknown"

        if meta.last_updated is None:
            meta.extraction_warnings.append("No last_updated date found in document.")


# --------------------------------------------------------------------------
# Demo
# --------------------------------------------------------------------------

SAMPLE_DOC = """---
access_level: team_only
last_updated: 2026-06-12
---

# Database Failover Runbook

When the primary payments-api database becomes unresponsive, promote the
standby replica and update the connection string in the config service.
This runbook is owned by the Platform team and was last reviewed after
the March 2026 payments outage.
"""


def run_demo() -> None:
    pipeline = MetadataExtractionPipeline()
    meta = pipeline.extract(
        doc_id="runbook-db-failover",
        file_path="runbooks/platform/db-failover.md",
        text=SAMPLE_DOC,
    )

    print(f"doc_id:        {meta.doc_id}")
    print(f"team:          {meta.team}")
    print(f"doc_type:      {meta.doc_type}")
    print(f"access_level:  {meta.access_level}")
    print(f"last_updated:  {meta.last_updated}")
    print(f"entities:      {meta.entities}")
    print(f"summary:       {meta.summary}")
    print(f"warnings:      {meta.extraction_warnings}")


if __name__ == "__main__":
    run_demo()
```

### Expected output (shape — LLM fields vary slightly by model run)

```
doc_id:        runbook-db-failover
team:          Platform
doc_type:      runbook
access_level:  team_only
last_updated:  2026-06-12
entities:      ['payments-api', 'config service', 'standby replica']
summary:       Runbook for failing over the payments-api database to its standby replica.
warnings:      []
```

`team` and `access_level` came entirely from the fast, free, deterministic path (path convention + YAML front-matter) — the LLM was never asked about them. `entities` and `summary` came from the LLM, since no rule-based method could reliably produce those. `doc_type` happened to be inferrable from front-matter in this example (skipping the LLM call for it), but the pipeline would fall back to the LLM if front-matter had omitted it.

### Key production notes

- **Rule-based extraction should win when both sources produce a value** — it's cheaper and more trustworthy for anything with a real structural signal. Only call the LLM for fields rules genuinely can't produce (entities) or couldn't determine (missing doc_type).
- **Always validate against a known enum** — an LLM asked for `access_level` might return `"internal"` instead of `"all_employees"` due to phrasing drift; silently accepting that breaks every downstream access-control filter (Pattern 13) that expects one of the known values. Defaulting to the most restrictive-safe option (or explicitly flagging for review) is safer than guessing.
- **Truncate long documents before sending to the LLM** — sending an entire 40-page document for a one-sentence summary wastes context and money; the first ~3000 characters (or a smarter extractive summary of key sections) is usually enough signal for `doc_type`/`entities`/`summary` extraction.
- **Track extraction warnings, don't just silently default** — a document missing `last_updated` should be flagged for a human to fix, not silently treated as equally fresh as a document that actually has a real date; Pattern 13's recency filtering depends on this field being trustworthy.
- **Metadata must propagate from document → every derived chunk** — after chunking (Patterns 3–5), each chunk needs a *copy* of the parent document's metadata (plus its own `chunk_index`), not a reference back to a separate document record, since the vector database (Pattern 9) stores and filters at the chunk level.

## 8. Pinned Dependency Versions

```txt
langchain-ollama==0.2.3
langchain-core==0.3.29
python>=3.11,<3.13
```

```bash
ollama pull llama3.1
```

---

**Next:** say `next` and I'll build `08_Knowledge_Graphs.md` — representing entities and their relationships explicitly, to answer multi-hop questions that plain vector similarity can't.
