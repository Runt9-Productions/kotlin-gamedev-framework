---
title: Human Readable Name
type: note
permalink: entities/area/slug
tags: [kind, rarity, what-it-is-about]
verified: YYYY-MM-DD
coverage: partial
branch: the-branch-you-read-the-code-on
sources:
  - path/to/the-definition-that-implements-this.ext
id: stable-external-identifier
---

# Human Readable Name

> **Scope.** REPLACE THIS SENTENCE with what *this* note covers and, specifically, what it does not. For a play note that means the evidence that is missing (which characters, difficulties, rounds), not the code that was not read.

One paragraph on what this entity is and why it has a note. An entity note earns its
place by holding something a catalog does not: how the thing is actually used, judged,
or reasoned about — not a restatement of what it mechanically does.

## Observations

- [fact] A single indexable fact, citing `symbolName` plus `TheFile.ext:123`.
- [decision] A choice made deliberately about this entity, with the reasoning.
- [question] Genuinely open. Say what would answer it.

## Relations

- part_of [[The Index Note For This Kind]]
- see_also [[Another Entity]]

<!--
CHECKLIST — delete this block before saving.

  [ ] Is this actually an entity? One note per member of an ENUMERABLE set (an
      item, a recording, a character). If the set is "however many investigations
      we do", it is a topic and belongs in domains/ instead.
  [ ] permalink = the path under .claude/reference/, minus .md. Unlike domains/,
      the leading directory is KEPT: entities/<kind>/<slug>.
  [ ] REQUIRED: title, type, permalink, tags, verified, coverage, and the Scope line.
      OPTIONAL: branch, sources, id — delete the ones that do not apply rather
      than leaving them blank.
  [ ] tags carry what would otherwise be structured metadata: the kind (sticker,
      trinket, recording) and any classification (rarity, character). This is
      deliberate — a per-kind frontmatter key would be forced onto every note in
      the tree, including the ones it makes no sense for.
  [ ] sources = files whose change would make something here WRONG. For an item,
      the definition that implements it. Omit for a recording: its evidence is a
      video that is deliberately disposable, and requiring it would break the KB
      once frames are cleaned up.
  [ ] id is for a stable external identifier. Recording ids (v1, v2, …) are
      IMMUTABLE — they are the citation token in ~180 inline provenance markers,
      so renumbering is a corpus-wide rewrite, not a rename.
  [ ] Relations declare part_of the index note for this kind. The index lives in
      domains/, and _validate.py enforces the backlink, so an entity note that is
      not indexed will warn.
  [ ] No note names a consuming project — see "kgdfw-specific conventions" in
      README.md. Suggest usage generically; never cite a downstream repo as
      evidence, and never link out to one.
  [ ] Title matches the [[wikilinks]] other notes will use to reach it, and is
      unique across the whole KB.
-->
