# kotlin-gamedev-framework knowledge base

Notes an agent reads before the framework code they cover. The format is the reference-kb plugin's,
`skills/reference-kb/references/format.md` inside that plugin, and its `reference-kb` skill says how to read, write and
validate notes. This knowledge base adds no vocabulary, so it has no `kb.toml`.

Validate from the repository root before finishing any knowledge base work, and read the warnings, since they never
change the exit status:

```
python3 <reference-kb plugin>/skills/reference-kb/scripts/validate.py --root .claude/reference --stale
```

## Entry points

| Note | Read it before |
|---|---|
| [Interception](domains/interception/overview.md) | registering an interceptor or writing a hook context: exact-class matching, holders |
| [Events, Async and State](domains/event-and-state/overview.md) | sending or handling an event, using a named single-thread context, or relying on `GameStateService`'s load, save and update contract |
| [View Binding](domains/view-binding/overview.md) | using `ViewModel.Binding`, `Updatable` or the binding helpers |
| [Seeded Randomizer](domains/seeded-randomizer/overview.md) | asserting on `rngCounter`, or changing how the counter persists: it must count generator steps, and an intact round-trip proves nothing |
| [Logging](domains/logging/overview.md) | adding a log call in a hot path, swapping the sink, or capturing output in a test |
| [Development API Harness](domains/api-harness/overview.md) | adding an endpoint to the `api` module: the render-thread hop, screen resolution, the response envelope and the reified-type constraint |
| [Running Without a Display](domains/headless/overview.md) | assuming a consumer can run display-less: no LibGDX `Application` is not the same as headless |
| [Build and Release](domains/build-and-release/overview.md) | opening a PR that changes published code, changing `publishing { }` or a workflow, or chasing a version a consumer cannot resolve |

## Local conventions

- **The repository is public, and it is a framework consumed by other repositories.** A note says what the framework
  does and how to use it correctly, and stays true for any consumer. No note names a consuming project, in prose, in
  an example or as evidence for a claim.
- **kgdfw's notes own the mechanism; a consumer's notes own its usage.** What `canIntercept` compares, what
  `Binding.set` short-circuits on and which thread `updateAsync` dispatches to belong here. Which of a game's effects
  register on which hook, and which of its bugs came from the exact-class match, belong in that game's knowledge base,
  which links in to the note here rather than restating it.
- **Usage advice is welcome, phrased about a consumer rather than the consumer.** "Prefer X to Y here" and "a
  consumer's test harness can generally drop Z" are worth writing. A claim resting on something observed downstream
  says what was observed without saying where.
- **No link points out to a consuming project's knowledge base**, neither a `[[wikilink]]`, which can never resolve in
  this corpus, nor a path, which names a project this repository does not know about. A fact that can only be
  justified by pointing at one downstream repository is usage, and belongs there.
- **Write the trap down.** kgdfw has no published documentation and little training data, so an agent in a consuming
  project guesses at undocumented behaviour, confidently. An unrecorded trap costs more here than in a library with a
  manual.
- **kgdfw has no issue tracker.** Work is usually driven from a consuming project's issues, which mean nothing in this
  repository, so a note describes the work in prose and never cites a downstream ticket, as text or as a tag.
- **`[decision]` lines are the owner's rulings only**, attributed as `[owner, <where>, <date>]`.
