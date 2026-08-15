# Pattern 6: Document Parsing

## 1. Introduce the Pattern

**Document parsing** is the step that comes *before* chunking (Patterns 3–5): taking a real-world source file — PDF, DOCX, HTML, Markdown, CSV — and extracting clean, structured plain text from it, while preserving useful structural signals (headings, tables, lists) that later patterns depend on.

Every pattern so far in this series assumed you already had a nice plain-text string to work with. Document parsing is where that string actually comes from — and it's rarely as simple as "read the file."

```
Raw file on disk                          Parsed output
┌─────────────────────┐                   ┌───────────────────────────────┐
│ Incident_Runbook.pdf │   ── parse ──►    │ text: "Incident Response...    │
│ (binary, multi-column │                   │        3. Database Incidents  │
│  layout, embedded      │                   │        Step 1. Confirm..."    │
│  table, header/footer) │                   │ structure: [headings, tables] │
└─────────────────────┘                   │ page_map: {para_1: page_3, ...} │
                                           └───────────────────────────────┘
```

## 2. The Problem It Solves

Real documents are messy in ways that break naive text extraction:

- **PDFs are a visual format, not a text format.** A PDF stores where characters are drawn on a page, not "this is a paragraph" or "this is a table cell." Naive extraction can scramble multi-column layouts (reading left-to-right across both columns instead of down one column then the other), merge table cells into unreadable runs of text, or include repeated headers/footers on every single page as noise.
- **HTML is full of non-content.** Navigation menus, ads, cookie banners, and script tags are technically "text on the page" but are pure noise for a knowledge base — including them pollutes chunks and wastes embedding budget.
- **DOCX/Markdown carry structure that's valuable to preserve.** Headings, bullet lists, and tables carry real semantic signal (Pattern 3's section-title detection depends on headings surviving parsing intact). A parser that flattens everything to one undifferentiated text blob throws that signal away before chunking even gets a chance to use it.
- **Scanned/image-based PDFs have no extractable text at all** — they need OCR (optical character recognition) as a distinct sub-step, or the "parsed" output is simply empty.
- **Tables need special handling.** A table read as a flat character stream loses the row/column relationships entirely — "Staging: pool_size=10" and "Production: pool_size=20" turn into ambiguous soup like "Staging Production pool_size 10 20" with no way to tell which number belongs to which environment.

Getting parsing wrong silently degrades every downstream pattern: bad text in means bad chunks, bad embeddings, and bad retrieval — no amount of tuning chunk size or re-ranking can recover information that parsing already destroyed.

## 3. Realistic Enterprise Scenario

DocuMind's content sources are genuinely mixed:

- **PDF runbooks** exported from a design tool, two-column layout, with a repeated "Confidential — Internal Use Only" footer on every page
- **HTML pages** scraped from the internal Confluence wiki, full of navigation chrome and embedded comment threads
- **A DOCX HR policy document** with a real table of contents, numbered headings, and a benefits table
- **CSV exports** of an on-call rotation schedule

Each format needs a different extraction approach, but the *output* needs to be uniform: plain text with preserved heading structure, tables converted to a readable format (not garbled), and known noise (footers, nav menus) stripped out — ready to hand to the chunking patterns.

## 4. Architecture / Flow Diagram

```
┌───────────────┐  ┌───────────────┐  ┌───────────────┐  ┌───────────────┐
│   PDF file     │  │   HTML page    │  │   DOCX file    │  │   CSV file     │
└───────┬───────┘  └───────┬───────┘  └───────┬───────┘  └───────┬───────┘
        │                  │                  │                  │
        ▼                  ▼                  ▼                  ▼
┌───────────────┐  ┌───────────────┐  ┌───────────────┐  ┌───────────────┐
│ PDF loader      │  │ HTML loader    │  │ DOCX loader    │  │ CSV loader     │
│ (pdfplumber /   │  │ (BeautifulSoup │  │ (python-docx)  │  │ (pandas /      │
│  pypdf)         │  │  strip nav/ads)│  │                │  │  csv module)   │
└───────┬───────┘  └───────┬───────┘  └───────┬───────┘  └───────┬───────┘
        │                  │                  │                  │
        ▼                  ▼                  ▼                  ▼
┌────────────────────────────────────────────────────────────────────┐
│                    Format-specific normalization                    │
│  - strip repeated headers/footers (PDF)                             │
│  - remove nav/script/style tags (HTML)                              │
│  - preserve heading levels as markdown-style "## Heading" (DOCX)    │
│  - convert tables to markdown-table text (all formats)              │
└──────────────────────────────┬───────────────────────────────────┘
                                │
                                ▼
                  ┌───────────────────────────┐
                  │  Unified ParsedDocument      │
                  │  { text, headings[],         │
                  │    tables[], source_format,  │
                  │    page_map (PDF only) }      │
                  └──────────────┬─────────────┘
                                 │
                                 ▼
                  → Pattern 3/4/5 (Chunking)
                  → Pattern 7 (Metadata Extraction)
```

## 5. Request-to-Response Walkthrough

1. A source file arrives for ingestion; its format is detected (by extension and/or content sniffing).
2. The matching format-specific loader extracts raw content: text blocks with position info (PDF), the DOM tree (HTML), the paragraph/style tree (DOCX), or rows/columns (CSV).
3. **Format-specific cleanup** runs: PDF text gets repeated-line detection to strip running headers/footers; HTML gets navigation/script/style tags removed before text extraction; DOCX heading styles get converted into consistent `## Heading` markdown-style markers so Pattern 3's section-title detection can find them regardless of source format.
4. **Tables are detected and converted to a markdown table string** (`| Setting | Staging | Production |` style) rather than left as flattened text — this preserves row/column relationships in a format an LLM can still read naturally.
5. The cleaned text, extracted heading list, and any extracted tables are assembled into a single `ParsedDocument` object with a uniform shape regardless of the original file format.
6. This `ParsedDocument` is what gets handed to the chunking patterns (3–5) — from this point on, the pipeline no longer needs to know or care whether the original file was a PDF or a DOCX.

## 6. Why This Pattern Is Appropriate

| Approach | Problem |
|---|---|
| `open(file).read()` / naive text dump | Works only for already-plain-text files; produces garbage for PDF/DOCX binary formats |
| Extract text but skip cleanup (no header/footer stripping, no nav removal) | Every single page's "Confidential — Internal Use Only" footer becomes a chunk of pure noise repeated dozens of times in the vector store, actively harming retrieval (a query might match the noisy footer chunk instead of real content) |
| Extract text but flatten all structure | Downstream section-title detection (Pattern 3) and hierarchical chunking (Pattern 5's Markdown hierarchy) have nothing to work with — all documents degrade to the "plain text" case even when better structure was available |
| Extract text but leave tables as raw character soup | Numeric/tabular facts (pool sizes, thresholds, dates) become unrecoverable — a common and costly failure mode for compliance and technical documentation |
| **This pattern**: format-specific loaders + unified cleanup + table-to-markdown conversion + uniform output shape | Downstream patterns get consistently clean, structure-preserving input regardless of source format |

## 7. Production-Quality Python Implementation

```python
"""
document_parsing_pipeline.py

Production-quality, format-aware document parser for DocuMind.

Supports: PDF (via pdfplumber), HTML (via BeautifulSoup), DOCX (via
python-docx), and plain text/Markdown (passthrough with light cleanup).

Produces a uniform ParsedDocument regardless of source format, so
downstream chunking patterns (3-5) and metadata extraction (Pattern 7)
never need to know what the original file type was.
"""

from __future__ import annotations

import re
from collections import Counter
from dataclasses import dataclass, field
from pathlib import Path
from typing import List, Optional


@dataclass
class ParsedTable:
    markdown: str
    page: Optional[int] = None


@dataclass
class ParsedDocument:
    text: str
    headings: List[str] = field(default_factory=list)
    tables: List[ParsedTable] = field(default_factory=list)
    source_format: str = "unknown"
    source_path: str = ""
    warnings: List[str] = field(default_factory=list)


# --------------------------------------------------------------------------
# PDF parsing
# --------------------------------------------------------------------------

def parse_pdf(path: str) -> ParsedDocument:
    """Extracts text + tables from a PDF, stripping repeated running
    headers/footers (a very common PDF noise source)."""
    import pdfplumber

    page_texts: List[str] = []
    tables: List[ParsedTable] = []
    warnings: List[str] = []

    with pdfplumber.open(path) as pdf:
        for page_num, page in enumerate(pdf.pages, start=1):
            text = page.extract_text() or ""
            if not text.strip():
                warnings.append(f"Page {page_num} had no extractable text "
                                 f"(possibly a scanned image — OCR needed).")
            page_texts.append(text)

            for table in page.extract_tables():
                tables.append(ParsedTable(markdown=_table_to_markdown(table), page=page_num))

    cleaned_pages = _strip_repeated_headers_footers(page_texts)
    full_text = "\n\n".join(cleaned_pages)
    headings = _detect_headings_by_heuristic(full_text)

    return ParsedDocument(
        text=full_text, headings=headings, tables=tables,
        source_format="pdf", source_path=path, warnings=warnings,
    )


def _strip_repeated_headers_footers(pages: List[str], min_repeats_ratio: float = 0.6) -> List[str]:
    """A line that appears near-identically on most pages (e.g. a footer
    like 'Confidential - Internal Use Only') is almost certainly noise,
    not content — detect and strip it."""
    if len(pages) < 3:
        return pages  # too few pages to reliably detect a repeating pattern

    first_lines = Counter()
    last_lines = Counter()
    for page in pages:
        lines = [l.strip() for l in page.strip().split("\n") if l.strip()]
        if lines:
            first_lines[lines[0]] += 1
            last_lines[lines[-1]] += 1

    threshold = len(pages) * min_repeats_ratio
    noise_lines = {line for line, count in first_lines.items() if count >= threshold}
    noise_lines |= {line for line, count in last_lines.items() if count >= threshold}

    cleaned = []
    for page in pages:
        lines = [l for l in page.split("\n") if l.strip() not in noise_lines]
        cleaned.append("\n".join(lines))
    return cleaned


def _table_to_markdown(table: List[List[Optional[str]]]) -> str:
    if not table:
        return ""
    rows = [[cell or "" for cell in row] for row in table]
    header, *body = rows
    lines = ["| " + " | ".join(header) + " |",
              "|" + "|".join(["---"] * len(header)) + "|"]
    for row in body:
        lines.append("| " + " | ".join(row) + " |")
    return "\n".join(lines)


def _detect_headings_by_heuristic(text: str) -> List[str]:
    """Without style info (unlike DOCX), PDFs need a text-shape heuristic:
    short lines, title-cased or numbered, standing alone on their line."""
    heading_pattern = re.compile(r"^(?:\d+(?:\.\d+)*\.?\s+)?[A-Z][A-Za-z0-9 ,&/\-]{2,60}$")
    headings = []
    for line in text.split("\n"):
        stripped = line.strip()
        if stripped and len(stripped) < 70 and heading_pattern.match(stripped):
            headings.append(stripped)
    return headings


# --------------------------------------------------------------------------
# HTML parsing
# --------------------------------------------------------------------------

def parse_html(html: str, source_path: str = "") -> ParsedDocument:
    from bs4 import BeautifulSoup

    soup = BeautifulSoup(html, "html.parser")

    # Strip non-content elements before extracting text.
    for tag in soup(["script", "style", "nav", "header", "footer", "aside", "form"]):
        tag.decompose()
    for element in soup.select(".comments, .comment-thread, .cookie-banner, .advertisement"):
        element.decompose()

    headings = [h.get_text(strip=True) for h in soup.find_all(re.compile(r"^h[1-4]$"))]

    tables = []
    for table_tag in soup.find_all("table"):
        rows = []
        for tr in table_tag.find_all("tr"):
            cells = [td.get_text(strip=True) for td in tr.find_all(["td", "th"])]
            if cells:
                rows.append(cells)
        if rows:
            tables.append(ParsedTable(markdown=_table_to_markdown(rows)))
        table_tag.decompose()  # avoid double-counting table text in the plain-text body

    body_text = soup.get_text(separator="\n")
    body_text = re.sub(r"\n{3,}", "\n\n", body_text).strip()

    return ParsedDocument(
        text=body_text, headings=headings, tables=tables,
        source_format="html", source_path=source_path,
    )


# --------------------------------------------------------------------------
# DOCX parsing
# --------------------------------------------------------------------------

def parse_docx(path: str) -> ParsedDocument:
    from docx import Document as DocxDocument

    doc = DocxDocument(path)
    lines: List[str] = []
    headings: List[str] = []

    for para in doc.paragraphs:
        text = para.text.strip()
        if not text:
            continue
        style = (para.style.name or "").lower()
        if style.startswith("heading"):
            level = re.search(r"\d+", style)
            marker = "#" * int(level.group()) if level else "##"
            lines.append(f"{marker} {text}")
            headings.append(text)
        else:
            lines.append(text)

    tables = []
    for table in doc.tables:
        rows = [[cell.text.strip() for cell in row.cells] for row in table.rows]
        if rows:
            tables.append(ParsedTable(markdown=_table_to_markdown(rows)))

    return ParsedDocument(
        text="\n\n".join(lines), headings=headings, tables=tables,
        source_format="docx", source_path=path,
    )


# --------------------------------------------------------------------------
# Unified entry point
# --------------------------------------------------------------------------

def parse_document(path: str) -> ParsedDocument:
    ext = Path(path).suffix.lower()
    if ext == ".pdf":
        return parse_pdf(path)
    elif ext in (".html", ".htm"):
        return parse_html(Path(path).read_text(encoding="utf-8"), source_path=path)
    elif ext == ".docx":
        return parse_docx(path)
    elif ext in (".md", ".txt"):
        text = Path(path).read_text(encoding="utf-8")
        headings = re.findall(r"^#{1,4}\s+(.+)$", text, re.MULTILINE)
        return ParsedDocument(text=text, headings=headings, source_format=ext.lstrip("."), source_path=path)
    else:
        raise ValueError(f"Unsupported document format: {ext}")


# --------------------------------------------------------------------------
# Demo
# --------------------------------------------------------------------------

def run_demo() -> None:
    sample_html = """
    <html><head><script>trackUser();</script></head>
    <body>
      <nav>Home | Docs | Contact</nav>
      <header>DocuMind Internal Wiki</header>
      <h1>Database Failover Procedure</h1>
      <p>When the primary database becomes unresponsive, promote the
      standby replica and update the connection string.</p>
      <table><tr><th>Environment</th><th>Timeout</th></tr>
             <tr><td>Staging</td><td>15s</td></tr>
             <tr><td>Production</td><td>30s</td></tr></table>
      <div class="comments">User123: great doc!</div>
      <footer>Confidential - Internal Use Only</footer>
    </body></html>
    """

    parsed = parse_html(sample_html, source_path="wiki/db-failover.html")

    print(f"Format: {parsed.source_format}")
    print(f"Headings found: {parsed.headings}")
    print(f"Tables found: {len(parsed.tables)}")
    if parsed.tables:
        print(f"Table 0 markdown:\n{parsed.tables[0].markdown}\n")
    print("Cleaned body text:")
    print(parsed.text)


if __name__ == "__main__":
    run_demo()
```

### Expected output (shape)

```
Format: html
Headings found: ['Database Failover Procedure']
Tables found: 1
Table 0 markdown:
| Environment | Timeout |
|---|---|
| Staging | 15s |
| Production | 30s |

Cleaned body text:
Database Failover Procedure
When the primary database becomes unresponsive, promote the
standby replica and update the connection string.
```

Notice the nav menu, the `<script>` tracking call, the comment thread, and the footer are all gone from the cleaned output — only the actual content and its heading survive, and the table is preserved as a readable structure rather than jumbled into the body text.

### Key production notes

- **Repeated-header/footer stripping needs at least a few pages to detect a pattern reliably** — the implementation above requires 3+ pages before attempting it, to avoid false positives on short documents.
- **Scanned PDFs need OCR as a distinct fallback path** — the demo flags pages with no extractable text as a warning; a full production pipeline would route such pages through an OCR step (e.g. `pytesseract`) rather than silently producing empty output.
- **Tables should never be left as flattened text** — converting to markdown table syntax is a small amount of code that prevents a large class of "the numbers got scrambled" retrieval failures, especially important for compliance/policy documents with numeric thresholds.
- **Heading detection strategy differs by format on purpose**: DOCX has real style metadata (reliable), HTML has real `<h1>`–`<h4>` tags (reliable), but PDF has neither — it needs a text-shape heuristic (short, capitalized, standalone lines), which is inherently less reliable and should be treated as best-effort.
- **This pattern's output feeds directly into Pattern 3/4/5** — the `headings` list produced here is exactly what Pattern 3's `_section_title_for_offset` heuristic was approximating from raw text; when parsing has already extracted real headings, chunking can use them directly instead of re-guessing.

## 8. Pinned Dependency Versions

```txt
pdfplumber==0.11.4
beautifulsoup4==4.12.3
python-docx==1.1.2
python>=3.11,<3.13
```

---

**Next:** say `next` and I'll build `07_Metadata_Extraction.md` — pulling structured, filterable metadata (dates, authors, access levels, entities) out of parsed documents to power precise retrieval filters later on.
