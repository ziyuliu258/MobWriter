#!/usr/bin/env python3
"""Choose the official tag for this run, creating one only when every older tag has a Release."""

from __future__ import annotations

import os
import subprocess
import sys

sys.path.insert(0, os.path.dirname(__file__))
import official_release as helper


def remote_tags(pattern: str = "refs/tags/v*") -> str:
    return subprocess.check_output(
        ["git", "ls-remote", "--tags", "origin", pattern],
        text=True,
    )


def remote_commit(tag: str) -> str:
    raw = subprocess.check_output(
        [
            "git",
            "ls-remote",
            "--tags",
            "origin",
            f"refs/tags/{tag}",
            f"refs/tags/{tag}^{{}}",
        ],
        text=True,
    )
    return helper.parse_official_commits(raw).get(tag, "")


def next_tag() -> str:
    tag = subprocess.check_output(
        ["python3", "scripts/next-official-tag.py"],
        input=remote_tags(),
        text=True,
    ).strip()
    if not helper.is_official_tag(tag):
        sys.exit("refusing unofficial tag")
    return tag


def release_view(tag: str) -> subprocess.CompletedProcess[str]:
    view = subprocess.run(
        [
            "gh",
            "release",
            "view",
            tag,
            "--repo",
            os.environ["GITHUB_REPOSITORY"],
            "--json",
            "tagName",
        ],
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        text=True,
    )
    if view.returncode != 0:
        print(helper.format_command_output(f"gh release view {tag}", view.stderr, view.stdout))
    return view


def write_output(tag: str, commit: str, existing: bool) -> None:
    if not helper.is_official_tag(tag):
        sys.exit("refusing unofficial tag")
    if helper.COMMIT.fullmatch(commit) is None:
        sys.exit("refusing a tag that does not point at a commit")
    with open(os.environ["GITHUB_OUTPUT"], "a", encoding="utf-8") as handle:
        handle.write(f"tag={tag}\n")
        handle.write(f"commit={commit}\n")
        handle.write(f"existing={'true' if existing else 'false'}\n")


def reuse(target: helper.PublishTarget) -> None:
    current = remote_commit(target.tag)
    if current != target.commit:
        sys.exit("refusing to move an existing tag")
    print(
        f"Official tag {target.tag} already points at {target.commit}. "
        "Not allocating a newer tag."
    )
    write_output(target.tag, target.commit, True)


def allocate(head: str) -> None:
    chosen = ""
    for _ in range(8):
        tag = next_tag()
        current = remote_commit(tag)
        if current == head:
            chosen = tag
            break
        if current:
            # This tag belongs to another commit. Never move it.
            continue
        # Drop a tag that exists only in this checkout. Never delete a remote tag.
        subprocess.run(
            ["git", "tag", "-d", tag],
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL,
        )
        subprocess.check_call(["git", "tag", tag, head])
        pushed = subprocess.run(
            ["git", "push", "origin", f"refs/tags/{tag}:refs/tags/{tag}"]
        )
        landed = remote_commit(tag)
        if landed == head:
            chosen = tag
            break
        subprocess.run(
            ["git", "tag", "-d", tag],
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL,
        )
        if pushed.returncode != 0 and not landed:
            sys.exit("failed to push the new tag; existing tags were left untouched")
    if not chosen:
        sys.exit("could not allocate an official tag")
    if remote_commit(chosen) != head:
        sys.exit("refusing to publish because the tag does not point at this commit")
    write_output(chosen, head, False)
    print(f"Official tag {chosen} points at this commit.")


def main() -> int:
    head = os.environ["TARGET_SHA"]
    requested = os.environ.get("REQUESTED_TAG", "").strip()
    if requested.lower() in {"false", "null", "none"}:
        requested = ""
    commits = helper.parse_official_commits(remote_tags())
    without_release: set[str] = set()
    if not requested:
        for tag in sorted(commits, key=helper.version_key):
            view = release_view(tag)
            if view.returncode == 0:
                continue
            if helper.release_view_is_absent(view.stderr, view.stdout):
                without_release.add(tag)
                break
            sys.exit("could not read the existing release")
    try:
        target = helper.decide_publish_target(
            requested,
            commits,
            without_release,
            head,
            "" if requested or without_release else next_tag(),
        )
    except ValueError as exc:
        sys.exit(str(exc))
    if target.existing:
        reuse(target)
        return 0
    allocate(head)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
