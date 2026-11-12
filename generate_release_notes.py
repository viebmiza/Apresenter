#!/usr/bin/env python3
"""
Generates categorized release notes for everything that landed since the last GitHub release.

One bullet per pull request, titled the way the PR was titled, plus anything pushed straight to the
branch. Not one bullet per commit: a release is read by people deciding whether to upgrade, and the
steps a branch took to get where it got are not that.

Usage: python3 generate_release_notes.py [from_tag [version [windows macos_arm64 macos_x64 linux [end_ref]]]]
Platform args are 'true'/'false' strings; omit to include all platforms. `end_ref` names where the
notes stop; omit it and they stop at the newest tag reachable from HEAD, or at HEAD when that tag is
`from_tag`. The nightly passes HEAD explicitly: its base is the last *full* release, so a pre-release
tagged since then must not cut the notes short.
"""

import subprocess
import sys
import re

# Ensure UTF-8 output on Windows
if sys.stdout.encoding != "utf-8":
    sys.stdout.reconfigure(encoding="utf-8")

CATEGORIES = [
    ("Músicas",              r"song|favorite|column|composer|writer|songbook|m[uú]sica"),
    ("Recentes",             r"recent"),
    ("P&R / QR Code",        r"\bqa\b|q&a|p&r|qr|qr code|cooldown"),
    ("Mídia",                r"media|video|audio|m[ií]dia|\bplay\b|\bstop\b"),
    ("Bíblia",               r"bible|verse|scripture|b[ií]blia"),
    ("Roteiro e Calendário", r"schedule|playlist|calendar|calend[aá]rio|roteiro"),
    ("Imagens",              r"picture|image|photo|imagem|imagens"),
    ("Apresentações",        r"presentation|slide|powerpoint|pdf|apresenta"),
    ("Telas e Saídas",       r"display|screen|lock|output|tela"),
    ("Assistente Timóteo",   r"helper|wick|tim[oó]teo"),
    ("Transcrição / STT",    r"\bstt\b|whisper|caption"),
    ("Tradução e Idiomas",   r"language|translation|locali|i18n|string|pt-br|portugu[eê]s"),
    ("Configurações",        r"setting|config|preference|theme"),
    ("Build e Automação",    r"submodule|ci|build|gradle|workflow"),
    ("Geral / Interface",    r""),
]

NOISE = re.compile(
    r"^(bug fix|moved? button pos|made button clickable|wip|temp|fix|update|fixes|cleanup)$",
    re.IGNORECASE,
)

def run(cmd):
    return subprocess.check_output(cmd, text=True).strip()

def last_release_tag():
    if len(sys.argv) > 1:
        return sys.argv[1]
    # The rolling `nightly` pre-release is re-created every night, so it is always the newest entry
    # and never the release to write notes from.
    return run(["gh", "release", "list", "--limit", "3", "--json", "tagName", "-q",
                '[.[] | select(.tagName != "nightly")][1].tagName'])

def categorize(msg):
    lower = msg.lower()
    for name, pattern in CATEGORIES:
        if not pattern or re.search(pattern, lower):
            return name
    return "Geral / Interface"

# GitHub's merge commits carry the PR title as the first line of the commit body, so the whole
# changelog is computable from git alone — no API calls, nothing to rate-limit in CI.
PR_MERGE = re.compile(r"^Merge pull request #(\d+) from (\S+)")

# Bodies are multi-line, so records and fields need separators a commit message cannot contain.
FIELD, RECORD = "\x1f", "\x1e"

def landed_entries(from_tag, end_ref):
    """
    What actually landed on the release branch, as (title, pr_number) pairs.

    Walks --first-parent, which is the whole point: it never descends into a merged branch, so a
    pull request contributes its own title once instead of every internal step it took to get
    there. Without it, twelve PRs whose commits were each called "Added unit tests" printed
    twenty-one identical bullets.
    """
    fmt = f"--pretty=format:%P{FIELD}%s{FIELD}%b{RECORD}"
    raw = run(["git", "log", f"{from_tag}..{end_ref}", "--first-parent", fmt])
    for record in raw.split(RECORD):
        if not record.strip():
            continue
        parents, subject, body = (record.strip("\n").split(FIELD) + ["", ""])[:3]
        merged = PR_MERGE.match(subject.strip())
        if merged:
            number, branch = merged.group(1), merged.group(2)
            title = next((line.strip() for line in body.splitlines() if line.strip()), "")
            # An empty body would otherwise yield a blank bullet; the branch name still says
            # something about the work.
            yield (title or branch.rsplit("/", 1)[-1], number)
        elif len(parents.split()) > 1:
            # A sync merge ("Merge branch 'main' into ...") — carries no content of its own.
            continue
        else:
            # Pushed straight to the branch, never went through a PR. Keep it, or this is where
            # work silently disappears from the notes.
            yield (subject.strip(), None)

def combine_duplicates(entries):
    """
    One bullet per distinct title, carrying every PR that used it.

    Separate pull requests share a title often enough to matter ("Added unit tests" was twelve of
    them), and listing it twelve times is what made these notes unreadable. Collapsing to one
    bullet while keeping all the numbers means nothing becomes unfindable.
    """
    order, numbers = [], {}
    for title, number in entries:
        key = title.lower()
        if key not in numbers:
            order.append((key, title))
            numbers[key] = []
        if number and number not in numbers[key]:
            numbers[key].append(number)
    for key, title in order:
        refs = numbers[key]
        yield f"{title} ({', '.join('#' + n for n in refs)})" if refs else title

def main():
    from_tag = last_release_tag()
    latest_tag = run(["git", "describe", "--tags", "--abbrev=0"])
    # If no new tag exists yet, show commits from last release to HEAD
    end_ref = sys.argv[7] if len(sys.argv) > 7 else ("HEAD" if latest_tag == from_tag else latest_tag)
    # Allow version override via second argument (used by CI before the tag is pushed)
    if len(sys.argv) > 2:
        version = sys.argv[2].lstrip("v")
    else:
        version = latest_tag.lstrip("v") if end_ref != "HEAD" else "unreleased"

    entries = [(t, n) for t, n in landed_entries(from_tag, end_ref) if t and not NOISE.match(t)]
    commits = list(combine_duplicates(entries))

    buckets = {name: [] for name, _ in CATEGORIES}
    for msg in commits:
        buckets[categorize(msg)].append(msg)

    for name, _ in CATEGORIES:
        items = buckets[name]
        if items:
            print(f"**{name}**")
            for item in items:
                print(f"- {item}")
            print()

    def flag(i): return len(sys.argv) <= i or sys.argv[i].lower() != 'false'
    build_windows    = flag(3)
    build_macos_arm  = flag(4)
    build_macos_x64  = flag(5)
    build_linux      = flag(6)

    print("---")
    print("Baixe abaixo o instalador do **Apresenter** para o seu sistema.\n")
    if build_windows:
        print(f"- **Windows (recomendado):** `Apresenter-{version}-WINDOWS-x64.msi` (ou `.exe`)")
    if build_macos_arm:
        print(f"- **macOS (Apple Silicon / M-series):** `Apresenter-{version}-MACOS-arm64.dmg`")
    if build_macos_x64:
        print(f"- **macOS (Intel):** `Apresenter-{version}-MACOS-x64.dmg`")
    if build_linux:
        print(f"- **Linux (Ubuntu 22.04, Debian 12, Linux Mint 21 ou superior):** `apresenter_{version}_amd64-DEBIAN-x64.deb`")

if __name__ == "__main__":
    main()
