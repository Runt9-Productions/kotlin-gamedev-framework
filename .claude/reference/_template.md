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

> **Scope.** REPLACE THIS SENTENCE with what *this* note covers and, specifically, what it does not: the class, module, vendor or path that was not read, and anything carried forward unverified from an earlier pass.

One paragraph on what this note is for and when to read it. Say what the reader is about to get wrong if they skip it.

## Observations

- [fact] A single indexable fact, citing `symbolName` plus `TheFile.ext:123`.
- [trap] A fact that belongs to a cross-cutting class, tagged so it is findable from
  outside this note #silent-failure (and a trailing parenthetical for the qualifier).
- [fact] A fact tied to a ticket #ABC-1234
- [trap] True and counter-intuitive — the thing that costs an hour if unknown.
- [invariant] Holds today, and code elsewhere depends on it holding.
- [risk] A hazard believed but not demonstrated. Say what would confirm it.
- [question] Genuinely open. Say what would answer it.
- [decision] A choice made deliberately, with the reasoning.
- [history] Why it is this way — ticket, commit, date.

## Relations

- part_of [[Parent Hub Note]]
- see_also [[Some Other Note]]

<!--
CHECKLIST — delete this block before saving.

  [ ] permalink = path under domains/ or projects/, minus .md, with NO "domains/" prefix
      domains/<area>/overview.md            -> <area>/overview
      domains/<area>/<subarea>/<topic>.md   -> <area>/<subarea>/<topic>
  [ ] verified = the date you actually read the code; branch = the branch you read it on.
      Do not carry either forward from another note, and do not bump them without re-reading.
  [ ] sources = the files this note's claims DEPEND ON, repo-relative. Writing fresh, that
      is what you opened. The test is: if this file changed, would something in the note
      become wrong? If no, it is not a source. This list is what `--stale` diffs against
      git history, so padding it produces false staleness and trimming it produces false
      confidence. Reconstructing it for an older note: resolve the symbols the note already
      cites, leave `verified` alone, and say in the Scope line that the list is a floor.
  [ ] coverage: partial, and the Scope line names something specific. It holds scope only: no
      boilerplate (README.md "What absence means" states the principle once), and no fact
      about the subject, which belongs in the body.
  [ ] Every [fact] cites a symbol you opened AND READ IN FULL, this run. Not a search result, not
      a grep hit, not CLAUDE.md, not another note, not memory. See "Read the damn code" in
      README.md — a note built from three lines of a search result is worse than no note, because
      a reader trusts whatever IS written. Symbol names are durable; line numbers
      are hints.
  [ ] Nothing is marked [fact] that is actually [risk] or [question]. Second-hand claims are worth
      keeping — mark them [risk]/[question] and say they are unverified, never [fact].
  [ ] Counted things were actually counted. "Seven properties" above a list of six has happened.
  [ ] One fact per observation line. If it needs "and", split it.
  [ ] Inline #tags come from README.md's closed vocabulary (plus the #ABC-nnnn ticket
      pattern). Tag the observations someone would want to find from OUTSIDE this note,
      not all of them — a tag on every line carries no information.
  [ ] Durable knowledge -> domains/. Time-bound work (ticket, escalation) -> projects/.
  [ ] Title matches the [[wikilinks]] other notes will use to reach it.
  [ ] Added to README.md "Entry points" if this is a hub.
  [ ] If you invented a new convention while writing this, see "Spec changes carry a sweep
      obligation" in README.md: apply it to every existing note and write it back into
      README.md in the SAME session, or do not introduce it.
-->
