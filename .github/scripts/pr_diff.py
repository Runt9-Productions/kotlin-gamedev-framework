"""The pull request's diff, parsed into added and removed lines per file, plus the job-summary helpers the PR
checks share.

Every diff here is three-dot (`base...head`), the same comparison GitHub's "Files changed" tab shows, so a
line reported by a check is a line the reviewer can find on the PR.
"""

import os
import re
import subprocess
from dataclasses import dataclass, field
from pathlib import Path

HUNK_HEADER = re.compile(r"^@@ -(\d+)(?:,\d+)? \+(\d+)(?:,\d+)? @@")


@dataclass
class FileDiff:
    path: str
    added: list[tuple[int, str]] = field(default_factory=list)
    removed: list[tuple[int, str]] = field(default_factory=list)
    deleted: bool = False


def git(*args: str) -> str:
    result = subprocess.run(["git", *args], capture_output=True, check=True)
    return result.stdout.decode("utf-8", errors="replace")


def changed_paths(base: str, head: str) -> list[str]:
    # core.quotePath=false keeps non-ASCII paths readable rather than octal-escaped, so they match the
    # path predicates the checks apply.
    out = git("-c", "core.quotePath=false", "diff", "--name-only", "--no-renames", f"{base}...{head}")
    return [line for line in out.splitlines() if line]


def unified_diff(base: str, head: str, paths: list[str], context: int = 3) -> str:
    if not paths:
        return ""
    return git("-c", "core.quotePath=false", "diff", "--no-color", "--no-ext-diff", "--no-renames",
               f"-U{context}", f"{base}...{head}", "--", *paths)


def file_diffs(base: str, head: str, paths: list[str]) -> list[FileDiff]:
    """Line numbers are the new file's for added lines and the old file's for removed ones. Binary files
    come back with no lines at all."""
    files: list[FileDiff] = []
    current: FileDiff | None = None
    old_path = ""
    old_line = new_line = 0
    # `---`/`+++` are headers only before a file's first hunk. After it, a removed line reading "-- x" is
    # also "--- x", and parsing it as a header would start a phantom file.
    in_header = False

    for line in unified_diff(base, head, paths, context=0).splitlines():
        if line.startswith("diff --git "):
            current = None
            in_header = True
            continue

        hunk = HUNK_HEADER.match(line)
        if hunk:
            in_header = False
            old_line, new_line = int(hunk.group(1)), int(hunk.group(2))
            continue

        if in_header:
            if line.startswith("--- "):
                old_path = line[4:]
            elif line.startswith("+++ "):
                new_path = line[4:]
                deleted = new_path == "/dev/null"
                path = old_path[2:] if deleted else new_path[2:]
                current = FileDiff(path=path, deleted=deleted)
                files.append(current)
            continue
        if current is None:
            continue

        if line.startswith("+"):
            current.added.append((new_line, line[1:]))
            new_line += 1
        elif line.startswith("-"):
            current.removed.append((old_line, line[1:]))
            old_line += 1
    return files


def fence(text: str, info: str = "") -> str:
    """`text` in a Markdown code fence that its own content cannot close."""
    longest = max((len(run) for run in re.findall(r"`+", text)), default=0)
    ticks = "`" * max(3, longest + 1)
    return f"{ticks}{info}\n{text.rstrip()}\n{ticks}"


def annotate(level: str, message: str, path: str | None = None, line: int | None = None) -> None:
    """A workflow command (`::error file=...::...`), escaped so a path or message cannot end it early."""
    def escape_data(value: str) -> str:
        return value.replace("%", "%25").replace("\r", "%0D").replace("\n", "%0A")

    def escape_property(value: str) -> str:
        return escape_data(value).replace(":", "%3A").replace(",", "%2C")

    properties = []
    if path is not None:
        properties.append(f"file={escape_property(path)}")
    if line is not None:
        properties.append(f"line={line}")
    joined = " " + ",".join(properties) if properties else ""
    print(f"::{level}{joined}::{escape_data(message)}")


def write_summary(markdown: str) -> None:
    """Appends to the job summary and prints the same text, since the REST API exposes logs but not
    summaries."""
    print(markdown)
    summary = os.environ.get("GITHUB_STEP_SUMMARY")
    if summary:
        with Path(summary).open("a", encoding="utf-8") as out:
            out.write(markdown + "\n")
