"""Lints what a pull request adds to the files that instruct agents or run in CI.

    python3 .github/scripts/guard.py <base-sha> <head-sha>

Covers `.claude/**`, every `CLAUDE.md`, hook scripts and `.github/**`. Fails on invisible or bidirectional
Unicode, HTML comments and base64-looking runs added to Markdown, and network commands added outside
Markdown. Lists network commands added to Markdown and added URLs whose host is not on ALLOWED_URL_HOSTS,
without failing on either. The full diff
of those files goes into the job summary either way, so the owner can read exactly what changed.

Only added lines are checked. A file's existing content was checked when it was added, or predates this
script.
"""

import re
import sys
from dataclasses import dataclass
from urllib.parse import urlsplit

from pr_diff import FileDiff, annotate, changed_paths, fence, file_diffs, unified_diff, write_summary

# Ranges are inclusive. A byte-order mark is legitimate only as a file's first character, which
# check_unicode allows for.
INVISIBLE_RANGES = (
    (0x00AD, 0x00AD, "soft hyphen"),
    (0x061C, 0x061C, "Arabic letter mark"),
    (0x180E, 0x180E, "Mongolian vowel separator"),
    (0x200B, 0x200F, "zero-width or directional mark"),
    (0x202A, 0x202E, "bidirectional embedding or override"),
    (0x2060, 0x2064, "word joiner or invisible operator"),
    (0x2066, 0x2069, "bidirectional isolate"),
    (0xFEFF, 0xFEFF, "byte-order mark / zero-width no-break space"),
    (0xE0000, 0xE007F, "tag character"),
)
BOM = 0xFEFF

# Spelled with character classes so this file does not match itself.
NETWORK_COMMAND = re.compile(r"\bcur[l]\b|\bwge[t]\b|\bnc\s|/dev/tc[p]")
URL = re.compile(r"https?://[^\s<>()\[\]\"'`]+")
HTML_COMMENT = "<!--"

# Exact hostnames, so a subdomain is not allowed by its parent. Any port.
ALLOWED_URL_HOSTS = frozenset({
    "github.com",
    "maven.pkg.github.com",
})

# A run of base64 alphabet with a slash-free stretch at least this long. Slash-free because file paths are
# long runs of the same alphabet broken by slashes; real base64 has a slash only once in 64 characters.
BASE64_RUN = re.compile(r"[A-Za-z0-9+/]{40,}={0,2}")
BASE64_MIN_SEGMENT = 40
HEX_ONLY = re.compile(r"[0-9a-fA-F]+")

# Network commands in Markdown are prose far more often than instructions. Setting this to True makes them
# fail like they do everywhere else.
NETWORK_IN_MARKDOWN_BLOCKS = False

# GITHUB_STEP_SUMMARY rejects a step's summary past 1 MiB, which would lose the findings above the diff.
MAX_SUMMARY_DIFF_CHARS = 900_000


@dataclass(frozen=True)
class Finding:
    path: str
    line: int
    message: str
    blocking: bool


def is_guarded(path: str) -> bool:
    if path == "CLAUDE.md" or path.endswith("/CLAUDE.md"):
        return True
    if path.startswith((".claude/", ".github/", ".githooks/")):
        return True
    return "/hooks/" in f"/{path}"


def is_markdown(path: str) -> bool:
    return path.lower().endswith(".md")


def invisible_name(char: str) -> str | None:
    code = ord(char)
    for low, high, name in INVISIBLE_RANGES:
        if low <= code <= high:
            return name
    return None


def check_unicode(path: str, line_number: int, text: str) -> list[Finding]:
    findings = []
    for column, char in enumerate(text):
        if ord(char) == BOM and line_number == 1 and column == 0:
            continue
        name = invisible_name(char)
        if name is None:
            continue
        findings.append(Finding(path, line_number, f"U+{ord(char):04X} ({name}) at column {column + 1}", True))
    return findings


def looks_like_base64(run: str) -> bool:
    for segment in run.rstrip("=").split("/"):
        if len(segment) < BASE64_MIN_SEGMENT:
            continue
        if HEX_ONLY.fullmatch(segment):
            continue
        has_digit = any(c.isdigit() for c in segment)
        has_upper = any(c.isupper() for c in segment)
        has_lower = any(c.islower() for c in segment)
        if has_digit and has_upper and has_lower:
            return True
    return False


def check_line(path: str, line_number: int, text: str) -> list[Finding]:
    markdown = is_markdown(path)
    findings = check_unicode(path, line_number, text)

    if markdown and HTML_COMMENT in text:
        findings.append(Finding(path, line_number, "HTML comment added to Markdown (hidden when rendered)", True))

    if markdown:
        for run in BASE64_RUN.findall(text):
            if looks_like_base64(run):
                findings.append(Finding(path, line_number, f"base64-looking run of {len(run)} characters", True))

    command = NETWORK_COMMAND.search(text)
    if command:
        blocking = NETWORK_IN_MARKDOWN_BLOCKS or not markdown
        findings.append(Finding(path, line_number, f"network command `{command.group().strip()}` added", blocking))
    return findings


def unlisted_urls(diffs: list[FileDiff]) -> list[tuple[str, int, str]]:
    urls = []
    for diff in diffs:
        for line_number, text in diff.added:
            for match in URL.findall(text):
                url = match.rstrip(".,;:")
                if urlsplit(url).hostname in ALLOWED_URL_HOSTS:
                    continue
                urls.append((diff.path, line_number, url))
    return urls


def findings_table(findings: list[Finding]) -> str:
    rows = ["| | File | Line | Finding |", "|---|---|---:|---|"]
    for finding in findings:
        marker = "fail" if finding.blocking else "note"
        rows.append(f"| {marker} | `{finding.path}` | {finding.line} | {finding.message} |")
    return "\n".join(rows)


def summary(guarded: list[str], findings: list[Finding], urls: list[tuple[str, int, str]], diff: str) -> str:
    parts = ["### Guard", ""]
    if not guarded:
        parts.append("No agent-instruction, hook or CI files changed.")
        return "\n".join(parts)

    blocking = [f for f in findings if f.blocking]
    parts.append(f"{len(guarded)} guarded file(s) changed, {len(blocking)} blocking finding(s).")
    parts.append("")
    if findings:
        parts += [findings_table(findings), ""]

    if urls:
        parts += ["#### URLs added outside the allowlist", ""]
        # URL stops at a backtick, so a URL can never close its own code span.
        parts += [f"- `{path}:{line}` `{url}`" for path, line, url in urls]
        parts.append("")

    if len(diff) > MAX_SUMMARY_DIFF_CHARS:
        diff = diff[:MAX_SUMMARY_DIFF_CHARS]
        parts.append(f"Diff truncated to {MAX_SUMMARY_DIFF_CHARS} characters; the job log has it in full.")
    parts += ["#### Diff of guarded files", "", fence(diff, "diff")]
    return "\n".join(parts)


def main() -> int:
    if len(sys.argv) != 3:
        sys.exit("usage: guard.py <base-sha> <head-sha>")
    base, head = sys.argv[1], sys.argv[2]

    guarded = [path for path in changed_paths(base, head) if is_guarded(path)]
    diffs = file_diffs(base, head, guarded)

    findings = []
    for diff in diffs:
        for line_number, text in diff.added:
            findings.extend(check_line(diff.path, line_number, text))

    for finding in findings:
        annotate("error" if finding.blocking else "notice", finding.message, finding.path, finding.line)

    full_diff = unified_diff(base, head, guarded)
    write_summary(summary(guarded, findings, unlisted_urls(diffs), full_diff))
    if len(full_diff) > MAX_SUMMARY_DIFF_CHARS:
        print(full_diff)

    return 1 if any(f.blocking for f in findings) else 0


if __name__ == "__main__":
    sys.exit(main())
