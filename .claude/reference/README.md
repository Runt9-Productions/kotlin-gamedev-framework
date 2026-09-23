---
title: Reference Knowledge Base
type: note
permalink: readme
tags: [ meta, conventions ]
---

# Reference Knowledge Base

Agent-first documents that are also human-readable and hand-editable. The goal is that an agent reads the relevant note **before** reading code, and arrives already knowing the traps.

Not a replacement for reading code. A map that says which code to read and what is surprising about it.

## Installing this into a project

Copy `README.md`, `_template.md`, `_template-entity.md` and `_validate.py` to `<repo>/.claude/reference/`, then create `domains/`, `entities/` and `projects/` beneath them. In some projects `.claude/reference/` is gitignored; **in kgdfw it is tracked**, so KB changes show up in review alongside the code they describe.

Three things are worth doing on arrival, because they are the parts that cannot be generic:

1. **Fill in the entry-point list at the bottom.** An empty KB with no hub note is not discoverable.
2. **Decide the ticket-tag prefix.** `_validate.py` accepts any `#ABC-1234` shape by pattern. **kgdfw has no issue tracker** — work here is usually driven from a consuming project's issues, which have no meaning in this repository. Describe what the work was in prose rather than citing a downstream ticket, and never as an inline tag.
3. **Add a pointer from the project's `CLAUDE.md`** saying the KB exists and is relevance-triggered. A KB nothing points at does not get read.

## Read the damn code — the failure that makes a note worse than nothing

**A note written from a search result, a grep hit, a design doc, another note, or your own memory of how code like this usually works is not incomplete. It is false, and it is worse than having no note at all**, because a reader trusts what *is* written. A gap makes someone go and read the source. A fabricated fact stops them from reading it.

This section exists because of a real incident, quoted verbatim so the shape is unmistakable. An agent wrote a note, listed its dependencies, and then admitted:

> "I listed `FlipContext.kt` and `FullClearContext.kt` in `sources` having seen only three lines of each through a search result."

Three lines. Out of a 43-line file and a 29-line file that would each have taken one `read_file` call. The note that resulted was confident, well-formatted, internally consistent, passed validation, and stated things that were not true. In the same pass that agent also wrote "Seven `Card` properties are `@Transient`" directly above a list of six, and claimed each carried an explanatory comment when one of them did not.

None of that is exotic. It is what happens by default when writing feels like the task and reading feels like preparation for the task.

The rules that follow from it, all of which are already implied elsewhere in this file and are restated here because implication was not enough:

- **Every file in `sources` is one you opened and read.** Not searched. Not grepped. Not seen three lines of in a tool result. If you cannot honestly say you read it, either read it now or remove the claim that depends on it.
- **A `[fact]` is something you read in the code, this run.** Not something CLAUDE.md says, not something another note says, not something a design document asserts, not something you remember about this codebase from earlier in the session. Those are all fine inputs — they are how you decide *what to go read* — but they do not survive into a `[fact]` without the read.
- **Second-hand claims are still worth recording. Mark them.** `[risk]` for a believed-but-unproven hazard, `[question]` for genuinely open. A note saying "CLAUDE.md claims X; not verified against source" is honest and useful. The same sentence as `[fact]` is a lie with a citation on it.
- **Cite what you actually opened, and count what you actually list.** If the claim is about a symbol, you opened that symbol. If the claim is a number, you counted it.
- **Being fast here is worthless.** The whole value of this KB is that a reader can skip reading the code. Buying speed by not reading it yourself just moves the cost onto every future reader, with interest, and without their knowledge.

If you are about to write a note and notice you have not opened the files, the correct move is to stop and open them. There is no version of this where writing it anyway is the better call.

## What absence means — read this before trusting a gap

**Every note here is incomplete and permanently work-in-progress.** These notes record what has been *investigated*, not what *exists*. They were written from live investigations, in the order those investigations happened, which is close to random with respect to how the system is actually laid out.

**If something is not in the KB, the overwhelmingly likely reason is that nobody has needed it yet — not that it does not exist, is not a concern, or works the obvious way.**

Practical consequences:

- A missing subsystem, class, flag or failure mode is **no evidence at all** about the code. Go read the code.
- A note covering an area does **not** mean that area is covered *completely*. Most notes document a handful of traps found while chasing something else, and say nothing about the surrounding 90%.
- **Never answer "is there anything that does X?" from the KB alone.** The KB can confirm a thing exists; it can never establish that one does not.
- An absent caveat is not a guarantee of correctness. Code that no note warns about has usually just never been read closely.

This matters because a knowledge base is trusted, and a trusted source that is silent reads as a source that says "no". That failure is worse than having no KB at all, because it ends a search that should have continued. When the KB is quiet on your question, treat it as having said nothing.

**This section is the one place that says so.** Every note used to open with the same boilerplate sentence restating it. That was removed on 2026-09-22: semantic search over the KB indexes notes in passages, so the identical sentence was being matched as the relevant passage for unrelated queries, and it pushed the text that was specific to each note further from the heading. Each note now opens with a `> **Scope.**` line carrying only what is true of that note, written on the assumption that this section has already been read.

## Format

Shaped to match [Basic Memory](https://github.com/basicmachines-co/basic-memory), which indexes this directory for search over MCP (`search_notes`, `build_context`). **Agents edit an existing note with Basic Memory's `edit_note`, never by writing the file.** Its file watcher drops changes that arrive while it is still indexing, so a direct write can leave search answering from the old text with no error, and a hook denies it. Create a note with `write_note` too: put the full frontmatter from the template in `content`, and pass as `title` the words of the filename you want (`Overview` gives `overview.md`). The tool's `title` overwrites the frontmatter one, so when the two differ, follow with an `edit_note` `find_replace` on the `title:` line. The file keeps its name.

**Copy `_template.md` rather than writing this from memory.** It is the normative version of the block below and carries a pre-save checklist; this section is the explanation.

```markdown
---
title: Human Readable Name
type: note
permalink: area/slug
tags: [area, subsystem]
verified: YYYY-MM-DD
branch: the-branch-you-read-the-code-on
coverage: partial
sources:
  - path/to/the-file-this-note-is-about.ext
  - path/to/the-other-one-you-actually-read.ext
---

# Human Readable Name

> **Scope.** <what THIS note covers, and specifically what it does not.>

One paragraph on what this note is for.

## Observations

- [category] A single indexable fact #tag (optional context)

## Relations

- relation_type [[Other Note]]
```

Every field above is required, including `coverage`, `sources` and the Scope line. See the conventions below for what each is for.

### `sources` is what makes staleness detectable

`verified` tells a reader how *old* a note is. It cannot tell them whether the code moved underneath it, which is the way reference material actually goes wrong: a stale `[fact]` is worse than a missing one, because a reader trusts what is there.

`sources` closes that. List **the files this note's claims depend on**, repo-relative, as a YAML block sequence. In practice, when writing a fresh note, that is the set you opened — you open what you write about. The dependency framing is the definition, though, because it is the one that answers the question `--stale` asks: *has the code this note describes moved?* Not *did someone happen to open a file?*

Then:

```
python3 .claude/reference/_validate.py --stale
```

runs `git log --since=<verified> -- <sources>` per note and reports the ones whose subject has changed since anyone last read it. That converts `verified` from a disclaimer into a query, and it is the input an agent needs to decide "re-read this note's code before quoting it".

Two failure modes to avoid, both of which silently break the signal:

- **Padding the list.** A file the note makes no claim about produces false staleness, and a check that cries wolf gets ignored. If nothing in the note would become wrong when that file changes, it is not a source.
- **Trimming the list.** A note whose real dependencies are missing reports clean forever. This is the worse of the two.

**`--stale` reports three outcomes, not two, and the third is the one that matters.** A note is *stale* (commits landed on its sources), *clean* (measured, nothing landed), or **UNKNOWN** (could not be measured). Collapsing UNKNOWN into clean is what makes a staleness check lie, so the validator keeps them apart and reports two cases explicitly:

- **An untracked source.** `git log -- <untracked file>` prints nothing and exits 0, which is indistinguishable from "unchanged" — so a note sourcing a freshly-written, uncommitted file would otherwise report clean forever. `_validate.py` asks `git ls-files` first and names any untracked sources as unmeasured. Stage or commit them to get a real answer.
- **A failing git call**, for the same reason: empty output must not read as "nothing changed".

A *missing* source is a hard error rather than a warning, since a path that no longer exists means the note's subject moved.

Line numbers in the body will drift and that is fine — they are documented as hints. `sources` exists for the case that actually matters: the concept changed, not the line moved.

### Reconstructing `sources` for a note written before the field existed

Do not guess, and do not skip the note. Every claim in a well-written note here is anchored to a named symbol, so the dependency set is **derivable from the note's own text** — resolve each cited symbol to its file. That is legitimate, and it is not the same as guessing what someone opened months ago.

Two things must stay honest when you do it:

- **It does not re-verify anything.** `verified` still means the date someone last confirmed the facts. Leave it alone; do not bump it because you touched the frontmatter. Adding `sources` makes staleness *detectable*, which is precisely what tells you whether re-verification is needed.
- **A reconstructed list is a floor, not a complete set.** A note can depend on a file it never names — an unmentioned base class, a caller that establishes a precondition. Say so in the note's Scope line, which is already the slot for what the note does not cover. That needs no new frontmatter key.

Then run `--stale`. If it reports nothing, the note describes code that has not moved and no deep-dive is warranted — which is a measurement, not an assumption.

**`permalink` is the file path under `.claude/reference/`, minus the `.md`, with the `domains/` prefix dropped.** `domains/` is the default namespace and is elided; `projects/` is kept, so a permalink tells you which kind of note you are looking at.

| File                                        | Permalink                                |
|---------------------------------------------|------------------------------------------|
| `domains/<area>/overview.md`                | `<area>/overview`                        |
| `domains/<area>/<subarea>/<topic>.md`       | `<area>/<subarea>/<topic>`               |
| `entities/<kind>/<slug>.md`                 | `entities/<kind>/<slug>`                 |
| `projects/<ticket-or-incident>/overview.md` | `projects/<ticket-or-incident>/overview` |

A permalink must stay stable if the file moves, so prefer renaming the file to changing the permalink.

### Three rulesets: `domains/` is strict, `entities/` nearly so, `projects/` is not

- `domains/` notes carry exactly the seven keys in the template — no more, no less — plus the Scope line. They are durable, they get trusted, and a reader needs to know their staleness and scope.
- `entities/` notes require `title`, `type`, `permalink`, `tags`, `verified`, `coverage`, **plus the Scope line**, and may additionally carry `branch`, `sources` and `id`. They are durable and trusted like `domains/` notes, which is why they keep `coverage` — "this item has only been seen in one recording" is the most important thing such a note says. `sources` is optional because an entity's evidence is not always a repo file, and `branch` because it is meaningless for a note describing a recording rather than code.
- `projects/` notes require only `title`, `type`, `permalink`, `tags`. They may additionally carry `verified`, `branch`, `sources`, `status`, `release`, `opened`, `call_date`. **They are exempt from `coverage:` and the Scope line**, because "incomplete relative to the whole subsystem" is not the relevant caveat for a record of one ticket — such a note is scoped by its subject, not by how much code was read.
- Adding a key to the `domains/` set is a spec change and triggers the sweep obligation below. `_validate.py` fails on unknown keys specifically so that this cannot happen by accident in one note. The `entities/` and `projects/` key sets live as constants in `_validate.py` rather than in a template, because a two-tier set cannot be read off a single frontmatter block; adding a key there is the same spec change with the same obligation.

### What makes something an entity rather than a topic

**A `domains/` note is one per investigation. An `entities/` note is one per member of an enumerable set.** That is the whole test, and it is why the extra frontmatter exists: every item has a type and a rarity, every recording has an id, and a topic note has no such natural schema. Trying to put `rarity` in the shared template would force it onto a note about board layout.

Practically: if you could write down the complete list of things that need one of these notes, they are entities. If the list is "however many investigations we end up doing", they are topics.

An entity note's **index is a `domains/` note**, not a hub inside `entities/` — see `player-model/items`. That keeps each tree internally uniform, and the index is where backlinks concentrate. `_validate.py` deliberately does *not* exempt `entities/` → `domains/` from the two-way relation check, so adding an entity note without indexing it produces a warning.

`## Observations` and `## Relations` are conventional, not required, but keep them — Basic Memory indexes observations individually, which is what makes fact-level search work later.

## Spec changes carry a sweep obligation

**If you introduce or change a convention, you own applying it everywhere in the same session.** That means all three of (a) the new or changed note, (b) *every* existing note the convention now applies to, and (c) this file plus `_template.md`.

Doing only (a) is the failure this rule exists to prevent: a convention that lives only in the corpus is invisible, one that lives only in the spec is aspirational, one that lives in neither is a trap for the next reader. If applying a change everywhere is too expensive right now, do not make the change. An inconsistent corpus is worse than an unimproved one, because inconsistency is indistinguishable from a deliberate exception.

Worth echoing this into the project's `CLAUDE.md`, since that is what an agent reads before it reaches this file.

## Conventions we are adding on top

These are ours, not Basic Memory's.

- **One fact per observation line.** If a line needs "and", it is two observations.
- **Cite `symbol` plus `file:line`.** The symbol name is the durable part; the line number is a hint that drifts. Every note carries a `verified` date and branch in frontmatter so a reader knows how stale the numbers are.
- **Record the trap, not the happy path.** "This method exists and does X" is what code reading is for. "This method is not the one that runs at runtime" is what this KB is for.
- **Mark confidence.** `[fact]` is verified by reading the code or measuring. `[question]` is open. `[risk]` is a hazard we believe but have not proven. Never dress the second two as the first.
- **Say what a note does not cover.** **Every** note — hub and child alike — carries a `coverage:` frontmatter field and a blockquote immediately after the H1 opening with `> **Scope.**`, saying what the note covers and naming what is *outside* it, including which parts are carried forward unverified from an earlier pass. There is no shared boilerplate in it: the "incomplete by design" principle is stated once, under "What absence means" above, and a Scope line that only restates it is empty. Write the Scope line when you create the note and narrow it as coverage grows. It is the only thing preventing a reader from mistaking a partial note for a complete one, and a note without it reads as authoritative. A substantive fact about the subject belongs in the body, not in the Scope line, where a reader skimming for scope will not look for it.
- **Scope lines must be specific to be worth anything.** "Some things are not covered" is noise. Name the class, module, vendor or path that was not read: "measurements cover one vendor only", "the auth shape is characterised, the call path never traced", "everything downstream of the dedupe step is named but not investigated". A reader uses that line to decide whether their question is inside or outside the note.
- **`coverage:` values.** `partial` is the honest default. `complete` is a claim that the subsystem was read exhaustively; do not use it without saying in the note how that was established.
- **Link liberally, including to notes that do not exist yet.** A `[[dangling link]]` is a note worth writing, not an error.

## kgdfw-specific conventions

These exist because this KB documents a **framework consumed by other repositories**, which changes what a
note is for.

### Say what the framework guarantees, not what one consumer does with it

A note here answers "what does this code do, and how is it used correctly". It has to stay true for any
consumer. **How a specific game uses or misuses a mechanism belongs in that game's KB, not here.** A note
that only makes sense if you already know a particular consuming project exists is in the wrong repository,
and **no note here should name one** — not in prose, not in an example, not as evidence for a claim.

Suggesting usage is fine, and is often the most useful thing a note can do: "prefer X to Y here", "a consumer's
test harness can generally drop Z", "this pattern has burned people before". Keep it phrased about *a*
consumer rather than *the* consumer, and where a claim rests on something observed downstream, say what was
observed without attributing it — the fact has to stand for the next project too, and one that reads as a
report about somebody else's codebase does not.

### The split with consuming projects

- **kgdfw's KB owns the mechanism.** What `canIntercept` compares. What `Binding.set` short-circuits on.
  Which thread `updateAsync` dispatches to.
- **The consuming project's KB owns the usage.** Which of its effects register on which hook, which of its
  shipped bugs came from the exact-class match, what conventions it layers on top.
- A consuming note **links across rather than restating**. Restating drifts, and two corpora describing the
  same function differently is worse than one describing it once.

**Do not link out to a consuming project's KB at all.** Not as a `[[wikilink]]`, which can never resolve
inside this corpus and so reads as a note somebody still owes, and not as a relative path either, which names
a project this repository is not supposed to know about and rots the moment that project moves the file. The
edge belongs to the consumer: its note links *in* to this one. If a fact here can only be justified by
pointing at a specific downstream repository, it is usage rather than mechanism, and it belongs there.

### There is nothing to fall back on

kgdfw has no published documentation, no Stack Overflow answers, and effectively no training data. An agent
working in a consuming project cannot look anything up here; where behaviour is surprising and
undocumented, it will be guessed at instead, and the guess will arrive confident with nothing to contradict
it. **An undocumented trap in this repo is therefore more expensive than the same trap in a library with a
manual.** Write the trap down.

## Validating

```
python3 .claude/reference/_validate.py [--warnings] [--stale]
```

Exit 0 is clean *for errors*. Warnings — including everything `--stale` reports — do not affect the
exit code, so a clean exit is not evidence that nothing is stale. Read the `warn` lines. **Run it
before finishing any KB work** — it catches the mechanical mistakes that a reader would otherwise mistake for deliberate choices: wrong permalink shape, missing `coverage` or Scope line, a Scope line too short to be more than a placeholder, the retired shared banner sentence, unknown categories or relation types, leftover template placeholders, duplicate titles, bad dates.

It holds no independent opinion about the format. Categories, inline tags and relation types are parsed out of this file's tables; the required key set for `domains/` notes is read from `_template.md`'s frontmatter. So the spec files stay the single source of truth, and the validator's job is only to enforce them everywhere at once.

That is also why **unknown frontmatter keys are a hard error**. Adding a key to one note fails the run until it is added to the spec and swept across the corpus — the sweep obligation above, made mechanical rather than remembered. The friction is deliberate: it makes settling the spec cheaper than growing it. Do not soften that check.

## Observation categories in use

| Category      | Means                                                                                                                                       |
|---------------|---------------------------------------------------------------------------------------------------------------------------------------------|
| `[fact]`      | Verified by reading code, or measured. Safe to act on.                                                                                      |
| `[trap]`      | True and counter-intuitive. The thing that burns an hour if unknown.                                                                        |
| `[invariant]` | Holds today, and code depends on it holding. Breaking it breaks something distant.                                                          |
| `[risk]`      | Plausible hazard, not demonstrated.                                                                                                         |
| `[question]`  | Genuinely open.                                                                                                                             |
| `[decision]`  | A choice made deliberately, with the reasoning.                                                                                             |
| `[history]`   | Why it is this way, usually a ticket or commit.                                                                                             |
| `[status]`    | Where a piece of time-bound work currently stands. `projects/` notes only — a `domains/` note describing the code should not have a status. |

## Inline tags in use

The full observation grammar is `- [category] content #tag (optional context)`. The category says how much to trust the fact; **the tag says what class of problem it belongs to**, cutting *across* areas in a way note-level `tags:` structurally cannot. Trailing `(context)` is also available and is free text — use it for the qualifier that would otherwise bloat the sentence.

This is a starter vocabulary. It is deliberately small and language-agnostic; a project earns its own tags as described in the rules below.

| Tag                | Means                                                                            | Why it exists                                                                                                                                                                                                                                                |
|--------------------|----------------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `#silent-failure`  | Something is discarded, swallowed or short-circuited without an error surfacing. | Usually the most expensive class of behaviour in any codebase, and the one least likely to be found by reading a single file. A wrong answer arrives looking exactly like a finished one.                                                                    |
| `#irreversible`    | If this goes wrong, it cannot be fixed forward.                                  | Highest-stakes tag: an agent proposing a change should search it first. Data destroyed on upgrade, a merge that cannot be undone, a counter that cannot be recomputed.                                                                                       |
| `#order-dependent` | Sequence is load-bearing and reordering breaks something distant.                | The dependency is invisible at the call site, so it survives refactoring only if it is written down.                                                                                                                                                         |
| `#measured`        | Established by measuring real data, not by reading code.                         | `[fact]` deliberately covers both, and they decay differently: code-reading rots when the code changes, measurements rot when the data does. This tag splits them.                                                                                           |
| `#ABC-nnnn`        | Cross-reference to a ticket, e.g. `#ABC-1234`.                                   | An open but well-formed namespace, accepted by pattern rather than listed. Makes "what do we know about ABC-1234" a query instead of a grep. Replace `ABC` with this project's real prefix in prose; the validator accepts any `<UPPERCASE>-<digits>` shape. |

Rules, because an uncontrolled tag namespace is worse than none:

- **The vocabulary is closed.** Adding a tag means adding a row above, and then the sweep obligation applies — `_validate.py` fails on any tag not listed. `#silent-failure` / `#silentfailure` / `#silent_failure` degrading into three synonyms is the exact failure this prevents.
- **Tags belong on observation lines only.** That is where Basic Memory indexes them, and it keeps them from colliding with markdown headings.
- **A tag that would appear in only one note is a section heading, not a tag.** Earn it across at least three notes, or two areas, before adding it.
- **Do not tag everything.** A tag on every observation carries no information. Tag the observation someone would want to find *from outside this note*.

A project-specific tag is legitimate once it has earned its rows. Something like `#annotation-semantics` ("this annotation does not do what it looks like") is worth adding in a framework-heavy codebase where several unrelated notes each hold one piece of the same surprise, and worth nothing in a codebase without annotations. Write the "why it exists" column for the *local* reason, not the generic one, so the next reader can tell whether their case belongs to it.

## Relation types in use

`part_of`, `depends_on`, `implements`, `contradicts`, `supersedes`, `measured_in`, `causes`, `caused_by`, `see_also`

`causes` / `caused_by` are inverses and should be used as a pair from both ends: the note describing the cause and the note describing the symptom each declare their half, so the edge is visible from whichever side a reader arrives on.

**Relations should be two-way between notes of the same kind.** If a `domains/` note declares a relation to another `domains/` note, the target should link back — not necessarily with the exact inverse type, but it should acknowledge the connection, because a one-way edge is invisible from the side a reader arrives on. `_validate.py` warns on these.

**`projects/` → `domains/` is deliberately one-way.** A ticket note links *out* to the durable note; the durable note must not accumulate backlinks to every escalation that referenced it, because those rot as tickets close. The validator exempts that direction.

Note that upstream Basic Memory treats relation types as arbitrary free text. The closed vocabulary here is a local decision: it is what makes "find everything that `causes` a data-loss path" answerable, and an open namespace degrades into synonyms. Adding a type is a one-line edit to the list above.

## Layout

```
.claude/reference/
├── README.md                     ← you are here: the format spec
├── _template.md                  ← copy this to start a domains/ note
├── _template-entity.md           ← copy this to start an entities/ note
├── _validate.py                  ← run before finishing any KB work
├── domains/                      ← durable knowledge, one note per investigation
│   ├── <area>/
│   │   ├── overview.md           ← hub
│   │   └── <subarea>/            ← narrower specifics under that area
│   └── <other-area>/
├── entities/                     ← one note per member of an enumerable set
│   └── <kind>/<slug>.md          ← indexed by a domains/ note, not from in here
└── projects/                     ← time-bound work: escalations, tickets
    └── <ticket-or-incident>/overview.md
```

**`domains/` versus `projects/` is the important split.** A fact about how the system behaves belongs in `domains/` and stays true after the ticket closes. Anything scoped to one customer, one incident or one release belongs in `projects/` and should link *out* to the durable note rather than restating it.

`entities/` cuts across that on a different axis — it is durable like `domains/`, and split out because enumerable sets have a uniform schema that topic notes cannot share. See the entity-versus-topic test above.

Within `domains/`, general knowledge goes in `domains/<area>/` and narrower specifics one level down. If a fact is true of two of the narrower cases, it belongs one level up.

Use `_legacy/` or `_superseded/` for a document kept only until its content is extracted. Open it with a line saying what supersedes it, and delete it once there is nothing left worth mining.

## Entry points

- [[Interception]] — the interceptor framework: hooks, contexts, exact-class matching, holders.
- [[Events, Async and State]] — the EventBus, the named single-thread contexts, and `GameStateService`'s
  load/save/update contract.
- [[View Binding]] — `ViewModel.Binding`, `Updatable`, and the binding helpers.
- [[Seeded Randomizer]] — the reproducible stream that persists as a counter, why that counter must count
  generator steps, and why an intact counter round-trip proves nothing. Read before asserting on `rngCounter`.
- [[Logging]] — `kgdfLogger`, levels, the swappable sink, and how tests capture output. Read before adding a
  log call in a hot path.
- [[Development API Harness]] — the `api` module: the render-thread hop every endpoint must cross, screen
  resolution, the response envelope, and the reified-type constraint on responding.
- [[Running Without a Display]] — why "no LibGDX `Application`" is not the same as headless, and the static
  call that needs a windowing system anyway. Read before assuming a consumer can run display-less.
- [[Build and Release]] — publishing to GitHub Packages: the tag is the version, the credential shape, and
  why a version appearing in the API does not mean the publish finished.

## Relations

Hub notes this file links to, once they exist. `_validate.py` treats README as a legitimate wikilink target, so a hub may link back here.
