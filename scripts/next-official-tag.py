#!/usr/bin/env python3
"""Choose the next official MobWriter version tag.

Reads ``git ls-remote --tags`` output on stdin and prints the next
``vMAJOR.MINOR.PATCH`` tag. Tags that are not exactly that shape are ignored,
so an older suffixed tag cannot change the next official number and cannot be
reused. When no official tag exists yet, the first one is ``v0.1.0``.
"""

from __future__ import annotations

import re
import sys

OFFICIAL = re.compile(r"v(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)\Z")


def next_official_tag(ls_remote_text: str) -> str:
    found: set[tuple[int, int, int]] = set()
    for line in ls_remote_text.splitlines():
        if not line.strip():
            continue
        ref = line.split()[-1]
        if ref.endswith("^{}"):
            ref = ref[:-3]
        name = ref.removeprefix("refs/tags/")
        match = OFFICIAL.fullmatch(name)
        if match is None:
            continue
        found.add(tuple(int(part) for part in match.groups()))
    if not found:
        version = (0, 1, 0)
    else:
        major, minor, patch = max(found)
        version = (major, minor, patch + 1)
    tag = "v%d.%d.%d" % version
    if OFFICIAL.fullmatch(tag) is None:
        raise RuntimeError("next tag is not an official version")
    return tag


def _self_test() -> None:
    def lines(*names: str) -> str:
        return "".join(f"{index:040x}\trefs/tags/{name}\n" for index, name in enumerate(names, start=1))

    assert next_official_tag("") == "v0.1.0"
    assert next_official_tag(lines("v0.1.0", "v0.1.1")) == "v0.1.2"
    assert next_official_tag(lines("v0.1.0", "v0.1.0-pre.1", "v0.1.0-pre.2")) == "v0.1.1"
    assert next_official_tag(lines("v0.1.0-rc.1", "v0.1.0-alpha", "v0.1.0-beta.2")) == "v0.1.0"
    assert next_official_tag(lines("v0.1.9", "v0.1.10")) == "v0.1.11"
    assert next_official_tag(lines("v0.2.0", "v0.1.15")) == "v0.2.1"
    assert next_official_tag(lines("v1.2.3")) == "v1.2.4"
    peeled = lines("v0.1.1") + "bbbb\trefs/tags/v0.1.1^{}\n"
    assert next_official_tag(peeled) == "v0.1.2"
    assert next_official_tag(lines("v0.1", "build-6", "v0.1.01", "latest")) == "v0.1.0"
    print("next-official-tag self-test passed")


def main(argv: list[str]) -> int:
    if argv == ["--self-test"]:
        _self_test()
        return 0
    if argv:
        print("usage: next-official-tag.py [--self-test] < ls-remote-output", file=sys.stderr)
        return 2
    sys.stdout.write(next_official_tag(sys.stdin.read()) + "\n")
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
