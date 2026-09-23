#!/usr/bin/env python3
import os
import re
import subprocess
import sys


def get_previous_tag():
    """Finds the most recent git tag prior to HEAD."""
    try:
        tag = (
            subprocess.check_output(
                ["git", "describe", "--tags", "--abbrev=0", "HEAD^"],
                stderr=subprocess.DEVNULL,
            )
            .decode("utf-8")
            .strip()
        )
        return tag
    except Exception:
        pass

    try:
        tags = (
            subprocess.check_output(
                ["git", "tag", "--sort=-creatordate"], stderr=subprocess.DEVNULL
            )
            .decode("utf-8")
            .splitlines()
        )
        for t in tags:
            t = t.strip()
            if t:
                return t
    except Exception:
        pass

    return None


def get_commits_since(tag):
    """Fetches non-merge commit messages since the given tag, or latest 10 commits if no tag."""
    if tag:
        cmd = ["git", "log", f"{tag}..HEAD", "--pretty=format:%s", "--no-merges"]
    else:
        cmd = ["git", "log", "-n", "10", "--pretty=format:%s", "--no-merges"]

    try:
        output = subprocess.check_output(cmd, stderr=subprocess.DEVNULL).decode("utf-8")
        return [line.strip() for line in output.splitlines() if line.strip()]
    except Exception:
        return []


def clean_commit_message(msg):
    """Strips conventional commit prefixes and capitalizes the first word."""
    # Pattern to match feat(scope):, fix:, chore(deps):, etc.
    cleaned = re.sub(
        r"^(feat|fix|perf|refactor|style|docs|chore|test)(\([^\)]+\))?:\s*",
        "",
        msg,
        flags=re.IGNORECASE,
    ).strip()

    if not cleaned:
        return ""

    # Capitalize the first letter
    return cleaned[0].upper() + cleaned[1:]


def main():
    # If a custom RELEASE_NOTES.md exists in repo root, prioritize it
    release_notes_file = "RELEASE_NOTES.md"
    if os.path.isfile(release_notes_file):
        try:
            with open(release_notes_file, "r", encoding="utf-8") as f:
                content = f.read().strip()
                if content:
                    print(content)
                    return
        except Exception:
            pass

    prev_tag = get_previous_tag()
    commits = get_commits_since(prev_tag)

    ignore_patterns = [
        r"^merge\b",
        r"^bump\b",
        r"^release\b",
        r"^commit(\s+and\s+push)?$",
        r"^wip\b",
        r"^update$",
        r"^temp\b",
    ]

    cleaned_items = []
    seen = set()

    for commit in commits:
        if any(re.search(pat, commit, re.IGNORECASE) for pat in ignore_patterns):
            continue

        cleaned = clean_commit_message(commit)
        if cleaned and cleaned not in seen:
            seen.add(cleaned)
            cleaned_items.append(f"• {cleaned}")

    if not cleaned_items:
        cleaned_items.append("• Performance improvements and bug fixes.")

    print("### What's Changed")
    for item in cleaned_items:
        print(item)


if __name__ == "__main__":
    main()
