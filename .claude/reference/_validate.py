#!/usr/bin/env python3
"""Validate reference-KB notes against the spec in README.md.

    python3 .claude/reference/_validate.py [--warnings] [--stale]

Exit 0 = clean, 1 = violations found, 2 = the spec itself could not be parsed.

DESIGN NOTE, worth reading before extending this.

This script deliberately holds NO independent opinion about the format. Where a
rule can be derived from a spec file, it is:

  * observation categories, inline tags and relation types are parsed out of
    README.md's tables
  * the required key set for domains/ notes is read from _template.md's frontmatter

Three trees, three rulesets: domains/ is strict (required == allowed, read from
the template), entities/ is strict with a small optional set, projects/ is loose.
The entities/ and projects/ key sets are constants below rather than parsed,
because a two-tier set cannot be read off a single frontmatter block.

So adding a category means editing README.md, and adding a frontmatter key means
editing _template.md. The validator then enforces the new rule everywhere on the
next run, which is the point: the spec stays the single source of truth and the
sweep obligation in README.md becomes mechanically enforced rather than
remembered.

The unknown-key check is the load-bearing one. It exists to make a spec addition
FAIL LOUDLY in every note that lacks it, so that inventing a convention in one
note is more expensive than settling it in the spec. Do not soften it.

DIVERGENCE FROM BASIC MEMORY, deliberate. Upstream treats observation categories
and relation types as arbitrary free text. We enforce a closed vocabulary from
README.md, because a controlled vocabulary is what makes cross-note queries work
and an uncontrolled one degrades into synonyms. Adding to the vocabulary is a
one-line spec edit; that is the intended cost.
"""

from __future__ import annotations

import argparse
import datetime as dt
import re
import subprocess
import sys
from pathlib import Path

import yaml

ROOT = Path(__file__).resolve().parent
README = ROOT / "README.md"
TEMPLATE = ROOT / "_template.md"


def _repo_root() -> Path:
    """The repo `sources:` paths are relative to.

    Asks git rather than assuming a depth, so the KB still validates if it is
    installed somewhere other than `<repo>/.claude/reference/`. Falls back to
    two levels up, which is that conventional location.
    """
    try:
        result = subprocess.run(["git", "rev-parse", "--show-toplevel"],
                                cwd=ROOT, capture_output=True, text=True,
                                timeout=30, check=False)
        if result.returncode == 0 and result.stdout.strip():
            top = Path(result.stdout.strip()).resolve()
            if top in ROOT.parents:
                return top
    except (OSError, subprocess.SubprocessError):
        pass
    return ROOT.parents[1] if len(ROOT.parents) > 1 else ROOT


REPO = _repo_root()

# The blockquote after the H1 is the note's own statement of what it does not
# cover. The floor rules out a stub, not a terse note: real scope paragraphs run
# 173 chars and up. The template's own scope text is long enough to pass, so
# PLACEHOLDERS is what catches an unedited copy.
SCOPE_OPENER = "**Scope.**"
MIN_SCOPE_CHARS = 100
# Retired shared boilerplate. Any note still carrying it was missed by the sweep
# that replaced it with SCOPE_OPENER.
RETIRED_BANNER_OPENER = "**Incomplete and permanently WIP.**"

PLACEHOLDERS = (
    "YYYY-MM-DD",
    "REPLACE THIS",
    "the-branch-you-read-the-code-on",
    "area/slug",
    "Human Readable Name",
    "Parent Hub Note",
    "Some Other Note",
    "path/to/",
)

# projects/ notes are scoped by their subject, not by how much code was read.
PROJECT_REQUIRED = {"title", "type", "permalink", "tags"}
PROJECT_OPTIONAL = {"verified", "branch", "status", "release", "opened",
                    "call_date", "sources"}

# entities/ notes are one-per-member-of-an-enumerable-set (an item, a recording),
# so unlike a topic note they have a uniform natural schema. They are durable and
# trusted like domains/ notes, so they keep `coverage` and the scope blockquote — "we
# have only seen this item in one recording" is the single most important caveat
# such a note carries.
#
# Two keys are optional rather than required, and the reasons differ:
#   sources — an entity's evidence is not always a repo file. An item note cites
#             the definition that implements it; a recording note's evidence is a
#             video that is deliberately disposable, so requiring it would make
#             the KB error permanently once frames are cleaned up.
#   branch  — meaningless for a note describing a recording rather than code.
#   id      — a stable external identifier. Recording ids in particular are
#             immutable: they are the citation token in ~180 inline provenance
#             markers across the corpus, so renumbering is not a rename.
#
# Held here rather than parsed from _template-entity.md because a two-tier set
# cannot be read off a single frontmatter block — the same reason PROJECT_* are
# constants. Adding a key still fails every note until it is listed here, which
# is the property that matters.
ENTITY_REQUIRED = {"title", "type", "permalink", "tags", "verified", "coverage"}
ENTITY_OPTIONAL = {"branch", "sources", "id"}

# Keys whose value must be a non-empty list of strings. Flow (`[a, b]`) and block
# (`- a`) spellings are the same YAML and both pass; Basic Memory writes block.
LIST_KEYS = {"sources", "tags"}

# Ticket cross-references are an open but well-formed namespace, so they are
# accepted by pattern instead of being listed in README.md's tag table. The
# pattern is issue-tracker shaped (PROJ-123) rather than tied to one prefix, so
# this file needs no edit when it moves to a project with a different key.
TICKET_TAG = re.compile(r"[A-Z][A-Z0-9]*-\d+")


class Problem:
    def __init__(self, path: Path, line: int, msg: str, warning: bool = False):
        self.path, self.line, self.msg, self.warning = path, line, msg, warning

    def __str__(self) -> str:
        try:
            where = self.path.relative_to(REPO)
        except ValueError:
            where = self.path
        return f"{'warn ' if self.warning else 'ERROR'} {where}:{self.line}: {self.msg}"


def parse_frontmatter(lines: list[str]) -> tuple[dict[str, object], int]:
    """Return (mapping, index of the closing ---), or ({}, -1) if there is no
    delimited block. Raises yaml.YAMLError if the block is not a YAML mapping.

    Values arrive YAML-typed: an unquoted date is a `datetime.date`, a quoted one
    a `str`. Checks must be semantic, never about layout, because Basic Memory
    re-serializes frontmatter with `yaml.dump` and the two must agree.
    """
    if not lines or lines[0].strip() != "---":
        return {}, -1
    for i, raw in enumerate(lines[1:], start=1):
        if raw.strip() == "---":
            data = yaml.safe_load("\n".join(lines[1:i]))
            if data is None:
                return {}, i
            if not isinstance(data, dict):
                raise yaml.YAMLError(f"frontmatter is a {type(data).__name__}, not a mapping")
            return {str(k): v for k, v in data.items()}, i
    return {}, -1


def as_date(value: object) -> dt.date | None:
    """`value` as a date if it is an ISO YYYY-MM-DD date, bare or quoted."""
    if isinstance(value, dt.datetime):
        return None
    if isinstance(value, dt.date):
        return value
    if isinstance(value, str) and re.fullmatch(r"\d{4}-\d{2}-\d{2}", value):
        try:
            return dt.date.fromisoformat(value)
        except ValueError:
            return None
    return None


def spec_categories() -> set[str]:
    """Observation categories, parsed from README.md's table."""
    text = README.read_text(encoding="utf-8")
    return set(re.findall(r"^\|\s*`\[(\w+)\]`\s*\|", text, re.MULTILINE))


def spec_tags() -> set[str]:
    """Inline observation tags, parsed from README.md's table."""
    text = README.read_text(encoding="utf-8")
    found = set(re.findall(r"^\|\s*`#([\w-]+)`\s*\|", text, re.MULTILINE))
    # The ticket row documents the pattern, it is not a literal tag.
    return {t for t in found if "nnnn" not in t}


def spec_relations() -> set[str]:
    """Relation types, parsed from the backticked list under the README heading."""
    text = README.read_text(encoding="utf-8")
    m = re.search(r"## Relation types in use\s*\n+(.+?)\n", text, re.DOTALL)
    return set(re.findall(r"`(\w+)`", m.group(1))) if m else set()


def spec_domain_keys() -> list[str]:
    """Required frontmatter keys for domains/ notes, read from _template.md."""
    fm, _ = parse_frontmatter(TEMPLATE.read_text(encoding="utf-8").splitlines())
    return list(fm.keys())


def expected_permalink(path: Path) -> str:
    """Path under reference/, minus .md, with a leading domains/ dropped."""
    rel = path.relative_to(ROOT).with_suffix("")
    parts = rel.parts[1:] if rel.parts[0] == "domains" else rel.parts
    return "/".join(parts)


def link_targets(text: str) -> set[str]:
    """Wikilink targets in `text`, with any `|alias` display text stripped.

    `[[Increments|increment]]` targets `Increments`; the alias is how the link
    reads in the sentence. Matching on the raw capture would resolve nothing and
    report every aliased link as dangling, which reads as "write this note" for a
    note that already exists.
    """
    return {m.split("|", 1)[0].strip()
            for m in re.findall(r"\[\[([^\]]+)\]\]", text)}


class Note:
    def __init__(self, path: Path):
        self.path = path
        self.lines = path.read_text(encoding="utf-8").splitlines()
        self.fm_error: yaml.YAMLError | None = None
        try:
            self.fm, self.fm_end = parse_frontmatter(self.lines)
        except yaml.YAMLError as exc:
            self.fm, self.fm_end, self.fm_error = {}, -1, exc
        self.tree = path.relative_to(ROOT).parts[0]
        self.is_domain = self.tree == "domains"
        self.is_entity = self.tree == "entities"
        title = self.fm.get("title")
        self.title: str | None = None if title is None else str(title)
        self.links: set[str] = set()
        self.relations: set[str] = set()   # wikilink targets in ## Relations only

    def sources(self) -> list[str]:
        value = self.fm.get("sources")
        return [s for s in value if isinstance(s, str)] if isinstance(value, list) else []


def check(note: Note, categories: set[str], relations: set[str],
          domain_keys: list[str], tags_vocab: set[str]) -> list[Problem]:
    problems: list[Problem] = []
    path, lines, fm = note.path, note.lines, note.fm

    def err(line: int, msg: str, warning: bool = False) -> None:
        problems.append(Problem(path, line, msg, warning))

    if note.fm_error is not None:
        mark = getattr(note.fm_error, "problem_mark", None)
        err(mark.line + 2 if mark else 1,
            f"frontmatter is not valid YAML: {str(note.fm_error).splitlines()[0]}")
        return problems
    if note.fm_end == -1:
        err(1, "no closing --- on the frontmatter block (or no frontmatter at all)")
        return problems

    if note.is_domain:
        required = allowed = set(domain_keys)
    elif note.is_entity:
        required, allowed = ENTITY_REQUIRED, ENTITY_REQUIRED | ENTITY_OPTIONAL
    else:
        required, allowed = PROJECT_REQUIRED, PROJECT_REQUIRED | PROJECT_OPTIONAL

    for key in sorted(required - set(fm)):
        err(1, f"frontmatter missing required key '{key}'")
    for key in sorted(set(fm) - allowed):
        err(1, f"frontmatter has unknown key '{key}' — add it to the spec "
               f"(_template.md for domains/, README.md for projects/) and sweep "
               f"every existing note, or remove it")

    for key in sorted(set(fm) & LIST_KEYS):
        value = fm[key]
        if value is None or value == []:
            err(1, f"'{key}' is present but empty")
        elif not isinstance(value, list):
            err(1, f"'{key}' must be a list, got {value!r}")
        elif not all(isinstance(v, str) and v.strip() for v in value):
            err(1, f"'{key}' must hold only non-empty strings, got {value!r}")

    if fm.get("type") not in (None, "note"):
        err(1, f"type is '{fm['type']}', expected 'note'")

    if "permalink" in fm:
        want = expected_permalink(path)
        if fm["permalink"] != want:
            err(1, f"permalink is '{fm['permalink']}', expected '{want}'")

    if "verified" in fm:
        verified = as_date(fm["verified"])
        if verified is None:
            err(1, f"verified '{fm['verified']}' is not an ISO YYYY-MM-DD date")
        elif verified > dt.date.today():
            err(1, f"verified '{verified}' is in the future")

    if "coverage" in fm and fm["coverage"] not in ("partial", "complete"):
        err(1, f"coverage is '{fm['coverage']}', expected 'partial' or 'complete'")

    # Every listed source must exist. A rename or delete breaks the note, and
    # that is exactly the signal `sources` exists to give.
    for src in note.sources():
        if not (REPO / src).exists():
            err(1, f"sources lists '{src}', which does not exist in the repo — "
                   f"the file moved or was deleted, so this note needs re-verifying")

    for key, value in fm.items():
        for text in (value if isinstance(value, list) else [value]):
            for ph in PLACEHOLDERS:
                if isinstance(text, str) and ph in text:
                    err(1, f"leftover template placeholder {ph!r} in "
                           f"frontmatter key '{key}'")

    body = lines[note.fm_end + 1:]
    offset = note.fm_end + 2  # 1-based line numbers for messages

    h1 = next((i for i, l in enumerate(body) if l.startswith("# ")), None)
    if h1 is None:
        err(offset, "no H1 heading")
    elif note.is_domain or note.is_entity:
        after = [i for i, l in enumerate(body[h1 + 1:], start=h1 + 1) if l.strip()]
        if not after or not body[after[0]].lstrip().startswith(">"):
            err(offset + h1, f"{note.tree}/ note has no scope blockquote immediately after the H1")
        else:
            # Only the first paragraph is the scope statement; a later `>` paragraph
            # is a separate callout and must not pad a placeholder past the floor.
            para: list[str] = []
            for l in body[after[0]:]:
                quoted = l.strip()
                if not quoted.startswith(">") or not quoted[1:].strip():
                    break
                para.append(quoted[1:].strip())
            scope = " ".join(para)
            if not scope.startswith(SCOPE_OPENER):
                err(offset + after[0], f"scope blockquote does not open with {SCOPE_OPENER!r}")
            elif len(scope) - len(SCOPE_OPENER) < MIN_SCOPE_CHARS:
                err(offset + after[0],
                    f"scope is {len(scope) - len(SCOPE_OPENER)} chars after {SCOPE_OPENER!r} — "
                    f"looks like a placeholder, not what this note does not cover "
                    f"(want >= {MIN_SCOPE_CHARS})")

    in_relations = False
    for i, line in enumerate(body):
        n = offset + i
        stripped = line.strip()

        for ph in PLACEHOLDERS:
            if ph in line:
                err(n, f"leftover template placeholder {ph!r}")

        if RETIRED_BANNER_OPENER in line:
            err(n, f"retired boilerplate {RETIRED_BANNER_OPENER!r} — the blockquote after "
                   f"the H1 now holds only this note's scope, opening with {SCOPE_OPENER!r}")

        if stripped.startswith("## "):
            in_relations = stripped == "## Relations"
            continue

        # `- [x]` / `- [ ]` are GFM task-list checkboxes, not observation
        # categories. projects/ checklists are full of them.
        m = re.match(r"-\s*\[(\w+)\]", stripped)
        if m and m.group(1).lower() == "x":
            m = None
        if m and m.group(1) not in categories:
            err(n, f"unknown observation category '[{m.group(1)}]' — add it to "
                   f"README.md's category table or use one of: "
                   f"{', '.join(sorted(categories))}")

        # Inline tags are checked on observation lines only — that is where Basic
        # Memory indexes them, and restricting to those lines keeps the `#` scan
        # from colliding with markdown headings. Space-preceded so that URL
        # fragments and `foo#bar` do not read as tags.
        if m:
            for tag in re.findall(r"(?:^|\s)#([\w-]+)", stripped):
                if TICKET_TAG.fullmatch(tag) or tag in tags_vocab:
                    continue
                err(n, f"unknown inline tag '#{tag}' — add a row to README.md's "
                       f"inline-tag table and sweep, or use one of: "
                       f"{', '.join('#' + t for t in sorted(tags_vocab))}, "
                       f"plus the #PROJ-123 ticket pattern")

        if in_relations and stripped.startswith("- "):
            rel = stripped[2:].split(" ", 1)[0]
            if rel and rel not in relations:
                err(n, f"unknown relation type '{rel}' — add it to README.md's "
                       f"relation list or use one of: {', '.join(sorted(relations))}")
            note.relations.update(link_targets(stripped))

    note.links = link_targets("\n".join(lines))
    return problems


def tracked_sources(paths: list[str]) -> set[str] | None:
    """Which of `paths` git actually tracks. None if git could not be asked.

    Exists because `git log -- <untracked file>` prints nothing and exits 0, which
    is indistinguishable from "this file has not changed". A note sourcing a
    brand-new uncommitted file would therefore report clean forever, no matter how
    much that file moved -- the precise silent-clean this whole check exists to
    prevent, arriving through the tool rather than through the author.
    """
    try:
        result = subprocess.run(["git", "ls-files", "-z", "--", *paths],
                                cwd=REPO, capture_output=True, text=True,
                                timeout=60, check=False)
    except (OSError, subprocess.SubprocessError):
        return None
    if result.returncode != 0:
        return None
    return {p for p in result.stdout.split("\0") if p}


def stale_report(notes: list[Note]) -> list[Problem]:
    """Notes whose sources have commits newer than their `verified` date.

    This is the whole reason `sources` exists: `verified` alone tells a reader how
    old a note is, not whether the code moved underneath it.

    Three outcomes per note, and they are deliberately distinct: stale (commits
    landed), clean (measured, nothing landed), and UNKNOWN (could not measure).
    Collapsing the third into the second is what makes a staleness check lie.
    """
    out: list[Problem] = []
    for note in notes:
        verified, sources = as_date(note.fm.get("verified")), note.sources()
        if verified is None or not sources:
            continue
        existing = [s for s in sources if (REPO / s).exists()]
        if not existing:
            continue

        tracked = tracked_sources(existing)
        if tracked is None:
            out.append(Problem(note.path, 1,
                "could not ask git which sources are tracked, so staleness is "
                "UNKNOWN, not clean", True))
            continue
        untracked = [s for s in existing if s not in tracked]
        if untracked:
            out.append(Problem(note.path, 1,
                f"staleness UNKNOWN for {len(untracked)} untracked source(s) — git has "
                f"no history to compare against, so their being unchanged is unmeasured, "
                f"not confirmed: {', '.join(sorted(untracked))}", True))
        existing = [s for s in existing if s in tracked]
        if not existing:
            continue

        try:
            result = subprocess.run(
                ["git", "log", f"--since={verified}", "--format=%h", "--", *existing],
                cwd=REPO, capture_output=True, text=True, timeout=60, check=False)
        except (OSError, subprocess.SubprocessError) as exc:
            out.append(Problem(note.path, 1, f"could not run git log: {exc}", True))
            continue
        # A failing git call returns empty stdout, which would otherwise read as
        # "nothing changed" — the exact silent-clean this check exists to avoid.
        if result.returncode != 0:
            out.append(Problem(note.path, 1,
                f"git log failed, so staleness is UNKNOWN, not clean: "
                f"{result.stderr.strip().splitlines()[0] if result.stderr.strip() else 'no stderr'}",
                True))
            continue
        commits = [c for c in result.stdout.split() if c]
        if commits:
            out.append(Problem(note.path, 1,
                f"{len(commits)} commit(s) touched its sources since "
                f"verified={verified} — re-read before trusting its file:line cites",
                warning=True))
    return out


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--warnings", action="store_true",
                    help="also report non-blocking warnings")
    ap.add_argument("--stale", action="store_true",
                    help="check `sources` against git history since `verified` "
                         "(implies --warnings; needs a git repo)")
    args = ap.parse_args()
    show_warnings = args.warnings or args.stale

    categories, relations, tags_vocab = spec_categories(), spec_relations(), spec_tags()
    try:
        domain_keys = spec_domain_keys()
        readme_fm, _ = parse_frontmatter(README.read_text(encoding="utf-8").splitlines())
    except yaml.YAMLError as exc:
        print(f"README.md or _template.md frontmatter is not valid YAML: {exc}",
              file=sys.stderr)
        return 2
    if not categories or not relations or not domain_keys or not tags_vocab:
        print("could not parse the spec out of README.md / _template.md — "
              "has their structure changed?", file=sys.stderr)
        return 2

    notes = [Note(p) for p in sorted(ROOT.rglob("*.md"))
             if not p.name.startswith("_") and p.name != "README.md"]

    problems: list[Problem] = []

    # README.md is not validated as a note, but it carries a title and is a
    # legitimate wikilink target, so register it before resolving links.
    titles: dict[str, Path] = ({str(readme_fm["title"]): README}
                               if "title" in readme_fm else {})

    for note in notes:
        problems.extend(check(note, categories, relations, domain_keys, tags_vocab))
        if note.title:
            if note.title in titles:
                problems.append(Problem(note.path, 1,
                    f"title '{note.title}' already used by "
                    f"{titles[note.title].relative_to(ROOT)} — "
                    f"wikilinks would be ambiguous"))
            titles[note.title] = note.path

    by_title = {n.title: n for n in notes if n.title}

    for note in notes:
        for target in sorted(note.links):
            if target not in titles:
                problems.append(Problem(note.path, 1,
                    f"dangling wikilink [[{target}]] (allowed by spec — a note "
                    f"worth writing)", warning=True))

        # Relation symmetry: if A declares a relation to B, B should acknowledge A.
        # Deliberately loose — any relation back counts, because demanding exact
        # inverses would need an inverse table and would fire on legitimate
        # asymmetries like part_of/see_also.
        #
        # projects/ -> domains/ is exempt by design: a ticket note links out to the
        # durable note (README: "should link *out* to the durable note rather than
        # restating it"), and a domains/ note must not accumulate backlinks to every
        # escalation that ever referenced it. Those would go stale as tickets close.
        #
        # entities/ -> domains/ is deliberately NOT exempt. An entity note declares
        # part_of the index note for its kind, so requiring the backlink is what
        # keeps that index complete: adding an item note without indexing it warns.
        # Entity backlinks do not rot the way ticket backlinks do, because the set
        # they enumerate is the durable thing.
        for target in sorted(note.relations):
            other = by_title.get(target)
            if other is None or not note.title:
                continue
            if note.tree == "projects" and other.is_domain:
                continue
            if note.title not in other.relations:
                problems.append(Problem(note.path, 1,
                    f"relation to [[{target}]] is one-way — "
                    f"{other.path.relative_to(ROOT)} does not link back",
                    warning=True))

    if args.stale:
        problems.extend(stale_report(notes))

    errors = [p for p in problems if not p.warning]
    warnings = [p for p in problems if p.warning]

    for p in errors:
        print(p)
    if show_warnings:
        for p in warnings:
            print(p)

    print(f"\n{len(notes)} notes · {len(errors)} errors · {len(warnings)} warnings"
          + ("" if show_warnings else " (--warnings to list)"))
    return 1 if errors else 0


if __name__ == "__main__":
    sys.exit(main())
