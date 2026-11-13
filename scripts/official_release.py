#!/usr/bin/env python3
"""Decide which official tag a MobWriter release should use.

Official tags are exactly ``vMAJOR.MINOR.PATCH``. A missing GitHub Release is
not a fatal error: ``gh release view`` may say ``release not found``,
``could not find release`` (which does not contain the words ``not found``),
HTTP 404, or the equivalent. Other failures, including being unable to see
the repository, stay fatal.
"""

from __future__ import annotations

import re
import sys
from typing import NamedTuple

OFFICIAL = re.compile(r"v(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)\Z")
COMMIT = re.compile(r"[0-9a-fA-F]{40}|[0-9a-fA-F]{64}")
BLOCKED = ("-pre", "-rc", "-alpha", "-beta")

# Matched case-insensitively against gh's combined stdout and stderr.
# "could not find release" does not contain "not found".
_ABSENT_PHRASES = (
    "not found",
    "could not find release",
    "couldn't find release",
    "cannot find release",
    "can't find release",
    "unable to find release",
    "no release",
    "release does not exist",
    "release doesn't exist",
    "could not resolve to a release",
)
_HTTP_404 = re.compile(r"(?<!\d)404(?!\d)")


class PublishTarget(NamedTuple):
    tag: str
    commit: str
    existing: bool


def is_official_tag(tag: str) -> bool:
    if OFFICIAL.fullmatch(tag) is None:
        return False
    lowered = tag.lower()
    if lowered == "latest" or lowered.startswith("build-"):
        return False
    return not any(piece in lowered for piece in BLOCKED)


def version_key(tag: str) -> tuple[int, int, int]:
    match = OFFICIAL.fullmatch(tag)
    if match is None:
        raise ValueError(f"unofficial tag {tag}")
    return tuple(int(part) for part in match.groups())


def release_view_is_absent(stderr: str, stdout: str = "") -> bool:
    """True only when gh failed because that tag has no GitHub Release."""
    detail = f"{stderr or ''}\n{stdout or ''}".lower()
    if any(phrase in detail for phrase in _ABSENT_PHRASES):
        return True
    return _HTTP_404.search(detail) is not None


def format_command_output(label: str, stderr: str, stdout: str = "") -> str:
    parts = [f"{label} stderr:", stderr if stderr else "(empty)"]
    if (stdout or "").strip():
        parts.append(f"{label} stdout:")
        parts.append(stdout.rstrip("\n"))
    return "\n".join(parts)


def require_newer_version_code(run_number: str, previous: int) -> int:
    """versionCode is the Actions run number and must exceed published codes."""
    if not str(run_number).isdigit() or int(run_number) <= 0:
        raise ValueError("refusing an unusable version code")
    code = int(run_number)
    if code <= previous:
        raise ValueError(
            f"versionCode {code} is not greater than the previous release versionCode {previous}"
        )
    return code


def parse_official_commits(ls_remote_text: str) -> dict[str, str]:
    """Map each official tag to the commit it points at.

    A peeled ``refs/tags/<tag>^{}`` line replaces the tag-object id.
    """
    commits: dict[str, str] = {}
    peeled: set[str] = set()
    for line in ls_remote_text.splitlines():
        if not line.strip():
            continue
        sha, ref = line.split()
        is_peeled = ref.endswith("^{}")
        if is_peeled:
            ref = ref[:-3]
        name = ref.removeprefix("refs/tags/")
        if not is_official_tag(name):
            continue
        if is_peeled or name not in peeled:
            commits[name] = sha
            if is_peeled:
                peeled.add(name)
    return commits


def oldest_tag_without_release(
    commits: dict[str, str],
    without_release: set[str],
) -> tuple[str, str] | None:
    """Oldest official tag that has no Release, and the commit it already points at."""
    found: list[tuple[tuple[int, int, int], str, str]] = []
    for tag in without_release:
        commit = commits.get(tag, "")
        if not is_official_tag(tag) or not COMMIT.fullmatch(commit):
            continue
        found.append((version_key(tag), tag, commit))
    if not found:
        return None
    found.sort()
    _, tag, commit = found[0]
    return tag, commit


def decide_publish_target(
    requested: str,
    commits: dict[str, str],
    without_release: set[str],
    head: str,
    next_tag: str,
) -> PublishTarget:
    """Pick the tag to publish without moving an existing one.

    An explicit tag must already exist. Otherwise reuse the oldest official
    tag that has no Release, at the commit it already points at. Allocate
    ``next_tag`` on ``head`` only when every official tag already has a Release.
    """
    if requested:
        if not is_official_tag(requested):
            raise ValueError("refusing an unofficial version; existing tags were left unchanged")
        commit = commits.get(requested, "")
        if not COMMIT.fullmatch(commit):
            raise ValueError("refusing to create a tag; the requested official tag does not exist")
        return PublishTarget(requested, commit, True)

    reuse = oldest_tag_without_release(commits, without_release)
    if reuse is not None:
        tag, commit = reuse
        return PublishTarget(tag, commit, True)

    if not is_official_tag(next_tag):
        raise ValueError("refusing unofficial tag")
    if not COMMIT.fullmatch(head):
        raise ValueError("refusing to create a tag from a different commit")
    return PublishTarget(next_tag, head, False)


def _self_test() -> None:
    absent_samples = (
        "release not found",
        "Not Found",
        "could not find release v0.1.2",
        "Could not resolve to a Release with the tagName of 'v0.1.2'.",
        "HTTP 404 (https://api.github.com/repos/example/app/releases/tags/v0.1.2)",
        "HTTP 404: Not Found (https://api.github.com/repos/example/app/releases/tags/v0.1.2)",
        "no release found for tag v0.1.2",
        "release does not exist",
    )
    for sample in absent_samples:
        assert release_view_is_absent(sample), sample
    assert release_view_is_absent("", "could not find release")
    fatal_samples = (
        "",
        "HTTP 401: Bad credentials",
        "HTTP 403: Resource not accessible by integration",
        "HTTP 500: Internal Server Error",
        "could not find the current repository",
        "rate limit exceeded",
        "dial tcp: connection refused",
        "failed to run git: fatal: not a git repository (or any of the parent directories): .git",
    )
    for sample in fatal_samples:
        assert not release_view_is_absent(sample), sample
    logged = format_command_output("gh release view v0.1.2", "could not find release\n")
    assert "could not find release" in logged

    assert require_newer_version_code("8", 6) == 8
    assert require_newer_version_code("9", 6) == 9
    try:
        require_newer_version_code("6", 6)
    except ValueError as exc:
        assert "versionCode 6 is not greater than the previous release versionCode 6" == str(exc)
    else:
        raise AssertionError("versionCode 6 must not replace a published 6")
    try:
        require_newer_version_code("0", 6)
    except ValueError as exc:
        assert "unusable" in str(exc)
    else:
        raise AssertionError("versionCode 0 must be refused")

    v012 = "da816a72e3b1b47cb5fef85b48e756108f81902f"
    head = "c" * 40
    remote = "\n".join(
        (
            "2ba3958f73674700d25d789c391dfa6c35cd0872\trefs/tags/v0.1.0",
            "248c3e0a189a9f4cb53d462089e601cc23a5ba1f\trefs/tags/v0.1.1",
            f"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\trefs/tags/v0.1.2",
            f"{v012}\trefs/tags/v0.1.2^{{}}",
            "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb\trefs/tags/v0.1.2-rc.1",
            "cccccccccccccccccccccccccccccccccccccccc\trefs/tags/build-8",
        )
    )
    commits = parse_official_commits(remote)
    assert commits == {
        "v0.1.0": "2ba3958f73674700d25d789c391dfa6c35cd0872",
        "v0.1.1": "248c3e0a189a9f4cb53d462089e601cc23a5ba1f",
        "v0.1.2": v012,
    }
    # v0.1.0 and v0.1.1 already have releases. The next number would be v0.1.3,
    # but the unpublished tag stays on the commit it already points at.
    reused = decide_publish_target("", commits, {"v0.1.2"}, head, "v0.1.3")
    assert reused == PublishTarget("v0.1.2", v012, True)
    assert reused.commit != head
    fresh = decide_publish_target("", commits, set(), head, "v0.1.3")
    assert fresh == PublishTarget("v0.1.3", head, False)
    pinned = decide_publish_target("v0.1.2", commits, set(), head, "v0.1.3")
    assert pinned == PublishTarget("v0.1.2", v012, True)
    oldest = decide_publish_target("", commits, {"v0.1.0", "v0.1.2"}, head, "v0.1.3")
    assert oldest.tag == "v0.1.0"
    assert oldest.existing
    try:
        decide_publish_target("v0.1.3", commits, set(), head, "v0.1.4")
    except ValueError as exc:
        assert "does not exist" in str(exc)
    else:
        raise AssertionError("a missing tag must not be created by the explicit path")
    try:
        decide_publish_target("v0.1.2-rc.1", commits, set(), head, "v0.1.3")
    except ValueError as exc:
        assert "unofficial" in str(exc)
    else:
        raise AssertionError("an unofficial tag must be refused")
    try:
        decide_publish_target("build-8", commits, {"build-8"}, head, "v0.1.3")
    except ValueError as exc:
        assert "unofficial" in str(exc)
    else:
        raise AssertionError("a build tag must be refused")
    print("official-release self-test passed")


def main(argv: list[str]) -> int:
    if argv == ["--self-test"]:
        _self_test()
        return 0
    if argv:
        print("usage: official_release.py [--self-test]", file=sys.stderr)
        return 2
    print("usage: official_release.py [--self-test]", file=sys.stderr)
    return 2


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
