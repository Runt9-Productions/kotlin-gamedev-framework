# Development profile

What the `develop` skill needs to know about kotlin-gamedev-framework (kgdfw). Where `CLAUDE.md` or a knowledge base
note already answers a question, this file points there. The repository is public, so this file holds no machine
paths and names no consuming project.

## 1. Ticket tracker

- None. GitHub issues are disabled on `Runt9-Productions/kotlin-gamedev-framework`, and the repository has no other
  tracker.
- Work arrives as a change a consuming project needs, and is tracked on that project's ticket under that project's
  own profile. Here the ticket is a kebab-case slug naming the change, which names the branch (section 2) and the spec
  directory (section 4).
- No kgdfw text (pull request, commit message, knowledge base note) cites a consumer's ticket, as text or as a tag:
  the consumer's tracker may be private, and its ids mean nothing here (`.claude/reference/README.md`, "Local
  conventions").
- Step 3's setup and step 4's "work started" update: not applicable here. Any tracker update belongs on the
  consumer's ticket.
- Automation: none.
- Connection failure: `gh` answers 401 until `GH_TOKEN` is set, and how it is minted is machine-local. File an owner
  task and carry on.

## 2. Branches

- Base: `master`, both as the pull request target and for diffs.
- **A merge into `master` is a release.** A pull request that changes published code bumps `version` in
  `gradle.properties`, and the `version` job in `.github/workflows/pr.yml` fails one that does not
  (`domains/build-and-release/overview.md`).
- Naming: `claude/<slug>`. The "Bot branch confinement" ruleset blocks creating, updating or deleting any branch
  outside `claude/**`.
- Lag: the "CI must pass" ruleset requires `gate` on `master` with the strict policy, so a branch behind `master`
  cannot merge. Merge `master` in before the last push. A merge that brings in a change to `.github/workflows/`
  cannot be pushed by the bot (section 10).
- Trap: `origin/master` and `bot/master` track the same branch through two remotes and go stale separately, and the
  main checkout's local `master` is not kept current. Fetch `bot` and branch from `bot/master`.

## 3. Worktree and working directory

- Never work in the main checkout. A consuming project's local composite build (`-PuseLocalKgdfw=true`, `CLAUDE.md`,
  "Working here") compiles kgdfw from it, so a branch switch or an edit there changes what that consumer builds. Each
  change gets a worktree beside it:
  `git worktree add ../kotlin-gamedev-framework-wt/<slug> -b claude/<slug> bot/master`, then
  `git branch --unset-upstream` in it, since branching from `bot/master` makes `master` the upstream.
- The repository has no `.worktreeinclude` and no `worktree.*` settings, so Claude Code's defaults apply to built-in
  worktrees. A new worktree needs no untracked file copied in.
- Renaming the branch: what it changes about cleaning a worktree up has not been measured here.
- Shared with the main checkout: the git config (the `claude.*` keys and the credential helper) and `.git/hooks/`, so
  the `pre-commit` and `pre-push` hooks (section 10) run in every worktree. Writes through a worktree work.
- Working directory: `.claude/output/<slug>/` in the worktree, ignored by the tracked `.gitignore`. It is scratch
  and goes when the worktree does, so nothing that must last is kept there, and no comment or doc points at it.
- Fresh session: a session's language server stays rooted where the session started, so a build session is started
  in the worktree. Unconfirmed here; measured in the harness's own repository.

## 4. Spec location

- The knowledge base: `.claude/reference/projects/<slug>/`, in the reference-kb plugin's format, with `overview.md`,
  `decisions.md` and `checklist.md` as the format sets out. The knowledge base has no `projects/` tree yet; the first
  project creates it and adds a line to the entry points in `.claude/reference/README.md`.
- Notes are public and follow the README's local conventions: no consuming project named, and no link out to a
  consumer's knowledge base.
- Validator, from the worktree's root:
  `python3 <reference-kb plugin>/skills/reference-kb/scripts/validate.py --root .claude/reference --stale`, and the
  same with `--stale-in bot/master..HEAD`. Read the warnings.

## 5. Agents

- Implementer, verifier and research agents: **none adopted.** The repository has no `.claude/agents/`, so `develop`
  step 4 stops and points at the `delegation` plugin's `templates/engineer.md` until one is copied in and filled.
- Doc writer: `reference-kb:kb-writer` for knowledge base notes.
- Comment sweep: `develop:comment-sweeper`.
- Ramp-up: not measured.
- Every brief that writes code adds:
  - read the knowledge base note covering the area first (`CLAUDE.md`, "The knowledge base"), and the
    `kotlin-style` skill;
  - a change to published code bumps `version` in `gradle.properties` (section 2);
  - nothing names a consuming project, in code, comments, tests, notes or commit messages.

## 6. Build, test and pre-push

- Build and test: `./gradlew build --console=plain`, which is what CI runs, or `./gradlew test --console=plain` for
  the tests alone. Read each module's `TEST-SUMMARY <task> <result> total= passed= failed= skipped=` line
  (`:core:test`, `:api:test`), printed by the listener in `buildSrc/src/main/kotlin/kgdfw-common-plugin.gradle.kts`.
  `--rerun` forces only the last task named past UP-TO-DATE, so put it on each task.
- No formatter or linter runs: `detekt.yml` sits at the root, but no build file applies detekt.
- Never run locally: none is barred. The tests need no display; CI runs them on `ubuntu-latest` with none.
- Pre-push: the `pre-push` hook in `.git/hooks/` refuses refs outside `claude/` and commits not authored by the bot.
  It runs no leak scan. What CI fails that is cheapest to catch first: a missing `version` bump, and an invisible
  character (zero-width, bidirectional, byte-order mark or Unicode tag) in a changed file under `.claude/` or in a
  `CLAUDE.md`, which the `guard` job checks.
- CI watch: CI runs only on pull requests (workflow `PR`, `.github/workflows/pr.yml`), so open the pull request
  before watching. With `GH_TOKEN` set (section 1) and `sha` the pushed head commit,

  ```
  while id=$(gh run list --commit "$sha" --workflow PR --json databaseId,createdAt \
      --jq 'max_by(.createdAt) | .databaseId // empty') && [ -z "$id" ]; do sleep 10; done
  gh run watch "$id" --exit-status
  ```

  It waits for the commit's `PR` run to exist, takes the newest, and follows it until every job, `gate` included, has
  finished. `publish.yml` and `dependency-submission.yml` run on pushes to `master` only, so the watch does not see
  them. Unconfirmed: adapted from the harness repository's watch and not yet run here.
- Unconfirmed: the bot cannot rerun a job, since its App lacks Actions write (true of the same App in the harness
  repository); the owner reruns, or the next push starts a new run.
- Duration: a `PR` run took about 1 to 3 minutes (the last five runs that started, 2026-10-24 to 2026-11-07). After a
  merge, the `publish` run must finish before a consumer can resolve the new version; the packages API showing it is
  not proof (`domains/build-and-release/overview.md`).

## 7. Verification

- Method: no application runs here; kgdfw is a library. A change is verified by its tests in `core` or `api`, and,
  where only a running game shows the behaviour, by building a consuming project against the worktree through that
  project's composite build. Unconfirmed: that a consumer's composite can be pointed at a worktree rather than the
  main checkout; how it finds kgdfw is the consumer's own configuration.
- Evidence: the commands run and the `TEST-SUMMARY` lines, in a pull request comment. It is public, so it holds no
  machine paths and names no consumer.
- What it cannot show: rendering, input and anything else that needs a window; and how a consumer behaves on the
  change until it has built against it.

## 8. Shared machine resources

- The main checkout, compiled by consumers' composite builds (section 3). Nothing is run or edited there.
- Gradle: the daemon and its caches are shared by every build on the machine, consumers' included. Unconfirmed
  whether two builds at once contend here; each worktree has its own build directories.

## 9. Review

- Code review: **no `.claude/code-review-profile.md` yet**, so the `lens-review` skill cannot run here. The harness
  migration's review phase adds the profile and kgdfw's own lenses.
- Test review: no `.claude/test-review-profile.md` yet; the same phase adds one. Until then `develop` step 7 skips
  the test review.
- External reviewer: none. The owner reviews.
- Posting: the bot, with `GH_TOKEN` (section 1). No tooling posts images; evidence is text.
- Public text: pull request bodies, reviewer guides and replies say what a change does and why, never where a rule
  came from, and name no consuming project. Unconfirmed: whether to run them through the installed harness's
  `leak-scan/review-text.sh` before posting, as the harness repository does; no hook or CI job here scans them.

## 10. Commit and push

- Identity: the GitHub App bot `runt9-claude-bot[bot]`, its name and email in `git config claude.botName` and
  `claude.botEmail`. Commit with
  `git -c user.name="$(git config claude.botName)" -c user.email="$(git config claude.botEmail)" commit`, and pass the
  same pair to every command that writes a commit (`merge`, `cherry-pick`, `revert`, `rebase`). The `pre-commit` hook
  refuses any other author or committer when run from an agent session.
- Messages and trailers: the trailer lines the session supplies. A message states the change and why, and names no
  consuming project or its tickets.
- Remote: `bot`, the HTTPS remote the bot pushes through. `origin` is SSH and the owner's. The bot's App has no
  `workflows` permission, so a push adding a commit that changes `.github/workflows/`, a merge of `master` that brings
  one in included, is the owner's to make (`domains/build-and-release/overview.md`).
- Guards: no guard hook stops destructive git commands such as a force push or `git reset --hard`; `pre-push` checks
  only the branch prefix and authorship. The remote rules hold: the bot pushes only `claude/**`; `master` takes
  changes only by pull request, with no force push or deletion, and only with `gate` passing on an up-to-date branch;
  the "Only Admin Tagging" ruleset restricts every tag. Unconfirmed: who bypasses these rulesets, since the bot's
  token cannot read their bypass lists.
- With only the remote half present there is no standing grant: commit, push and open the pull request when the owner
  says so, or for the whole run where the owner has granted that.
- Pull request: `gh pr create --draft --base master --body-file <file>`, two or three sentences on what changed and
  why. It leaves draft only on the owner's explicit approval. Merging is the owner's, and it releases.

## 11. Hand-off

- Nobody reviews after the owner.
- When the change is wanted by a consumer: once the merge's `publish` run has finished, the consumer bumps its kgdfw
  pin in its own change, under its own profile.
