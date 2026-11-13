#!/usr/bin/env python3
"""Decide whether this run should create or skip the official GitHub Release."""

from __future__ import annotations

import json
import os
import pathlib
import subprocess
import sys
import tempfile

sys.path.insert(0, os.path.dirname(__file__))
import official_release as helper


def output(**values: str) -> None:
    with open(os.environ["GITHUB_OUTPUT"], "a", encoding="utf-8") as handle:
        for key, value in values.items():
            handle.write(f"{key}={value}\n")


def release_command(*args: str) -> list[str]:
    return ["gh", "release", *args, "--repo", os.environ["GITHUB_REPOSITORY"]]


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


def view_release(tag: str) -> tuple[dict | None, bool]:
    """Return (release, absent). Unexpected gh failures abort after printing stderr."""
    view = subprocess.run(
        release_command("view", tag, "--json", "assets,isPrerelease,tagName"),
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        text=True,
    )
    if view.returncode == 0:
        return json.loads(view.stdout), False
    print(helper.format_command_output(f"gh release view {tag}", view.stderr, view.stdout))
    if helper.release_view_is_absent(view.stderr, view.stdout):
        print(f"No Release for {tag} yet.")
        return None, True
    sys.exit("could not read the existing release")


def released_codes(except_tag: str) -> int:
    listed = json.loads(
        subprocess.check_output(
            release_command("list", "--limit", "100", "--json", "tagName"),
            text=True,
        )
    )
    highest = 0
    for item in listed:
        other = item["tagName"]
        if other == except_tag:
            continue
        with tempfile.TemporaryDirectory() as tmp:
            downloaded = subprocess.run(
                release_command("download", other, "--pattern", "update.json", "--dir", tmp),
                stdout=subprocess.DEVNULL,
                stderr=subprocess.DEVNULL,
            )
            path = pathlib.Path(tmp, "update.json")
            if downloaded.returncode != 0 or not path.is_file():
                continue
            code = int(json.loads(path.read_text(encoding="utf-8"))["versionCode"])
            highest = max(highest, code)
    return highest


def build_commit(tag: str, existing: bool, planned_commit: str) -> str:
    remote = remote_commit(tag)
    if existing:
        if not remote or remote != planned_commit:
            sys.exit("refusing to move or recreate an existing tag")
        return remote
    if remote and remote != planned_commit:
        sys.exit("refusing to move an existing tag")
    if not remote and planned_commit != os.environ["GITHUB_SHA"]:
        sys.exit("refusing to create a tag from a different commit")
    chosen = remote or planned_commit
    if helper.COMMIT.fullmatch(chosen) is None:
        sys.exit("refusing to publish without a commit")
    return chosen


def main() -> int:
    ref = os.environ["GITHUB_REF"]
    ref_name = os.environ["GITHUB_REF_NAME"]
    run_number = os.environ["RUN_NUMBER"]
    planned = os.environ.get("PLANNED_TAG", "").strip()
    planned_commit = os.environ.get("PLANNED_COMMIT", "").strip()
    event = os.environ["GITHUB_EVENT_NAME"]
    cuts_next_release = event == "workflow_dispatch" or (event == "push" and ref == "refs/heads/master")

    if cuts_next_release:
        if not helper.is_official_tag(planned):
            sys.exit("official publish is missing its version tag")
        tag = planned
        name = planned[1:]
        existing = os.environ.get("PLANNED_EXISTING", "").strip() == "true"
        commit = build_commit(tag, existing, planned_commit)
    elif event == "push" and ref.startswith("refs/tags/"):
        tag = ref_name
        name = ref_name[1:] if ref_name.startswith("v") else ref_name
        if not helper.is_official_tag(tag) or name != tag[1:]:
            sys.exit("refusing an unofficial version; existing tags were left unchanged")
        remote = remote_commit(tag)
        if remote != os.environ["GITHUB_SHA"]:
            sys.exit("refusing to publish a tag that does not point at this commit")
        existing = True
        commit = remote
    else:
        sys.exit("refusing to publish without an official version tag")
    if not helper.is_official_tag(tag) or name != tag[1:]:
        sys.exit("refusing an unofficial version; existing tags were left unchanged")
    if not run_number.isdigit() or int(run_number) <= 0:
        sys.exit("refusing an unusable version code")

    release, _absent = view_release(tag)
    if release is not None and release.get("isPrerelease"):
        sys.exit("refusing to replace an existing non-final release")

    names = []
    if release is not None:
        names = [asset.get("name", "") for asset in release.get("assets", [])]
    has_apk = "MobWriter.apk" in names
    has_json = "update.json" in names
    if has_apk and has_json:
        output(
            skip="true",
            tag=tag,
            commit=commit,
            existing="true" if existing else "false",
            code="0",
            name=name,
            create="false",
            upload_apk="false",
            upload_json="false",
        )
        print(f"Release {tag} already has both packages; not publishing again.")
        return 0
    if has_apk and not has_json:
        sys.exit("release has an apk and no manifest; leaving both unchanged")

    if has_json:
        with tempfile.TemporaryDirectory() as tmp:
            subprocess.check_call(
                release_command("download", tag, "--pattern", "update.json", "--dir", tmp)
            )
            manifest = json.loads(pathlib.Path(tmp, "update.json").read_text(encoding="utf-8"))
        code = int(manifest["versionCode"])
        name = str(manifest["versionName"])
        if code <= 0 or name != tag[1:]:
            sys.exit("refusing the manifest already attached to this release")
        create = False
        upload_apk = True
        upload_json = False
    else:
        code = 0
        create = release is None
        upload_apk = not has_apk
        upload_json = not has_json
    previous = released_codes(tag)
    if has_json:
        if code <= previous:
            sys.exit(f"versionCode {code} is not greater than the previous release versionCode {previous}")
    else:
        try:
            code = helper.require_newer_version_code(run_number, previous)
        except ValueError as exc:
            sys.exit(str(exc))
    output(
        skip="false",
        tag=tag,
        commit=commit,
        existing="true" if existing else "false",
        code=str(code),
        name=name,
        create="true" if create else "false",
        upload_apk="true" if upload_apk else "false",
        upload_json="true" if upload_json else "false",
    )
    print(
        f"Publishing {tag} at {commit} with versionCode {code}, which is greater than {previous}."
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
