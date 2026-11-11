#!/usr/bin/env python3
"""Check the Git index without printing private values or file contents."""

import ipaddress
from pathlib import Path
import re
import subprocess
import sys


def git(*args):
    return subprocess.check_output(["git", *args])


root = Path(git("rev-parse", "--show-toplevel").decode().strip())
private_file = root / "config/private.local.properties"
private_values = []
private_keys = {
    "blog.owner", "blog.repo", "test.postsDirectory", "legacy.applicationId",
    "deploy.host", "deploy.remote", "deploy.authorEmail",
    "privacy.privateEmail", "privacy.userName", "privacy.domain",
}
if private_file.exists():
    for line in private_file.read_text().splitlines():
        if "=" in line and not line.lstrip().startswith("#"):
            key, value = line.split("=", 1)
            if key.strip() in private_keys and len(value.strip()) >= 3:
                private_values.append(value.strip().encode())

patterns = {
    "personal home-directory path": re.compile(rb"/(?:home|Users)/[A-Za-z0-9_.-]+/"),
    "GitHub credential": re.compile(rb"(?:gh[pousr]_[A-Za-z0-9]{20,}|github_pat_[A-Za-z0-9_]{20,})"),
    "private key": re.compile(rb"-----BEGIN (?:RSA |OPENSSH |EC |DSA )?PRIVATE KEY-----"),
}
ipv4 = re.compile(rb"(?<![\w.])(?:\d{1,3}\.){3}\d{1,3}(?![\w.])")
failures = []
files = git("ls-files", "-z").split(b"\0")
checked = 0
for raw_name in filter(None, files):
    name = raw_name.decode()
    checked += 1
    path = Path(name)
    # A force-added ignored file must also be rejected.
    if subprocess.run(
        ["git", "check-ignore", "--no-index", "-q", "--", name],
        stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
    ).returncode == 0:
        failures.append((name, "ignored/private/generated file is staged"))
    data = git("show", ":" + name)
    if any(value in data or value in raw_name for value in private_values):
        failures.append((name, "matches a private local value"))
    if b"\0" in data[:8192]:
        continue
    for label, pattern in patterns.items():
        if pattern.search(data):
            failures.append((name, label))
    for match in ipv4.finditer(data):
        try:
            address = ipaddress.ip_address(match.group().decode())
        except ValueError:
            continue
        if not address.is_loopback:
            failures.append((name, "literal IPv4 address"))
            break

if failures:
    for name, reason in sorted(set(failures)):
        # Redact private substrings even if one was accidentally used in a path.
        for value in private_values:
            name = name.replace(value.decode(), "[private]")
        print(f"BLOCKED: {name}: {reason}", file=sys.stderr)
    sys.exit(1)

print(f"Privacy check passed for {checked} staged files; no private values printed.")
