#!/usr/bin/env python3
"""Compute a SemVer release and notes from changelog fragments."""
import re
import sys

LEVELS = {"patch": 0, "minor": 1, "major": 2}
BUMP_RE = re.compile(r"^bump:\s*(major|minor|patch)\s*$", re.M)


def main(argv):
    last, paths = argv[1], argv[2:]
    fragments = [open(path, encoding="utf-8").read() for path in paths]
    if not fragments:
        return
    levels = []
    for fragment in fragments:
        match = BUMP_RE.search(fragment)
        if not match:
            raise ValueError("fragment has no valid 'bump:' line")
        levels.append(match.group(1))
    major, minor, patch = (int(value) for value in last.split("."))
    level = max(levels, key=LEVELS.get)
    version = f"{major + 1}.0.0" if level == "major" else (
        f"{major}.{minor + 1}.0" if level == "minor" else f"{major}.{minor}.{patch + 1}")
    print(version)
    print("\n\n".join(BUMP_RE.sub("", fragment, count=1).strip() for fragment in fragments))


if __name__ == "__main__":
    main(sys.argv)
