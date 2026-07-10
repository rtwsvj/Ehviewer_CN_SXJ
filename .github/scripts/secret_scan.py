#!/usr/bin/env python3
"""Small fail-closed worktree secret scan used by Android CI.

The scanner reports only rule id and file position. It never prints the matched
line or value, so a CI failure does not copy a credential into job logs.
"""

from __future__ import annotations

import argparse
import os
import re
import sys
import tempfile
from dataclasses import dataclass
from pathlib import Path


EXCLUDED_DIRECTORIES = {
    ".git",
    ".gradle",
    ".cxx",
    ".idea",
    "build",
    "coverage",
    "node_modules",
}

BINARY_SUFFIXES = {
    ".7z", ".apk", ".aab", ".bin", ".class", ".db", ".dex", ".gif",
    ".ico", ".jar", ".jpeg", ".jpg", ".jks", ".keystore", ".pdf",
    ".png", ".so", ".sqlite", ".webp", ".zip",
}

SENSITIVE_FILE_SUFFIXES = {".jks", ".key", ".keystore", ".p12", ".pfx"}
SENSITIVE_FILE_NAMES = {"key.properties", "keystore.properties", "signing.properties"}


@dataclass(frozen=True)
class Rule:
    name: str
    pattern: re.Pattern[str]
    value_group: str | None = None


@dataclass(frozen=True, order=True)
class Finding:
    path: str
    line: int
    rule: str

    def safe_message(self) -> str:
        return f"{self.path}:{self.line} [{self.rule}] possible secret"


PRIVATE_KEY_HEADER = (
    "-" * 5
    + "BEGIN "
    + r"(?:(?:RSA |EC |DSA |OPENSSH |ENCRYPTED )?PRIVATE KEY|PGP PRIVATE KEY BLOCK)"
    + "-" * 5
)
RULES = (
    Rule("private-key", re.compile(PRIVATE_KEY_HEADER)),
    Rule("aws-access-key", re.compile(r"\bAKIA[0-9A-Z]{16}\b")),
    Rule("github-token", re.compile(r"\bgh[pousr]_[A-Za-z0-9]{20,}\b")),
    Rule("google-api-key", re.compile(r"\bAIza[0-9A-Za-z_-]{30,}\b")),
    Rule("slack-token", re.compile(r"\bxox[baprs]-[0-9A-Za-z-]{20,}\b")),
    Rule("stripe-live-key", re.compile(r"\b(?:sk|rk)_live_[0-9A-Za-z]{16,}\b")),
    Rule(
        "eh-api-key",
        re.compile(
            r"(?i)(?<![A-Za-z0-9_])[\"']?apikey[\"']?\s*[:=]\s*"
            r"[\"'](?P<value>[a-f0-9]{16,})[\"']"
        ),
        "value",
    ),
    Rule(
        "assigned-secret",
        re.compile(
            r"(?i)(?<![A-Za-z0-9_])[\"']?(?:api[_-]?key|client[_-]?secret|"
            r"access[_-]?token|auth[_-]?token|password)[\"']?\s*[:=]\s*[\"']"
            r"(?P<value>[^\"'\r\n]{12,})[\"']"
        ),
        "value",
    ),
    Rule(
        "identity-cookie",
        re.compile(
            r"(?i)(?<![A-Za-z0-9_])[\"']?(?:ipb_pass_hash|igneous)[\"']?"
            r"\s*[:=]\s*[\"'](?P<value>[^\"'\r\n]{16,})[\"']"
        ),
        "value",
    ),
)


def is_placeholder(value: str) -> bool:
    normalized = value.strip().lower()
    if not normalized:
        return True
    markers = (
        "changeme", "dummy", "example", "fake", "not-a-real", "placeholder",
        "redacted", "replace-me", "sample", "synthetic", "test-only",
    )
    if any(marker in normalized for marker in markers):
        return True
    compact = re.sub(r"[^a-z0-9]", "", normalized)
    return bool(compact) and len(set(compact)) <= 1


def should_skip(path: Path, root: Path) -> bool:
    try:
        relative = path.relative_to(root)
    except ValueError:
        return True
    return (
        path.is_symlink()
        or any(part in EXCLUDED_DIRECTORIES for part in relative.parts[:-1])
        or path.suffix.lower() in BINARY_SUFFIXES
    )


def looks_binary(path: Path) -> bool:
    try:
        with path.open("rb") as stream:
            return b"\0" in stream.read(4096)
    except OSError:
        return True


def scan(root: Path) -> list[Finding]:
    root = root.resolve()
    findings: list[Finding] = []
    seen_positions: set[tuple[str, int]] = set()
    for directory, directory_names, file_names in os.walk(root, topdown=True,
            followlinks=False):
        current = Path(directory)
        directory_names[:] = sorted(
            name for name in directory_names
            if name not in EXCLUDED_DIRECTORIES and not (current / name).is_symlink()
        )
        for file_name in sorted(file_names):
            path = current / file_name
            relative = path.relative_to(root).as_posix()
            if (path.suffix.lower() in SENSITIVE_FILE_SUFFIXES
                    or path.name.lower() in SENSITIVE_FILE_NAMES):
                findings.append(Finding(relative, 0, "sensitive-file"))
                continue
            if should_skip(path, root) or looks_binary(path):
                continue
            try:
                with path.open("r", encoding="utf-8", errors="replace") as stream:
                    for line_number, line in enumerate(stream, 1):
                        for rule in RULES:
                            match = rule.pattern.search(line)
                            if match is None:
                                continue
                            if rule.value_group is not None and is_placeholder(
                                    match.group(rule.value_group)):
                                continue
                            position = (relative, line_number)
                            if position not in seen_positions:
                                findings.append(Finding(relative, line_number, rule.name))
                                seen_positions.add(position)
                            break
            except OSError:
                # An unreadable file is itself a scan failure, without revealing content.
                findings.append(Finding(relative, 0, "unreadable-file"))
    return sorted(findings)


def self_test() -> None:
    with tempfile.TemporaryDirectory() as temporary:
        root = Path(temporary)
        (root / "src").mkdir()
        (root / "build").mkdir()
        token = "gh" + "p_" + "A1b2C3d4E5f6G7h8I9j0K1l2M3n4O5p6Q7r8"
        eh_key = "0123456789abcdef" * 2
        identity = "1a2b3c4d5e6f7a8b" * 2
        (root / "src" / "safe.txt").write_text(
            'var apikey = "00000000000000000000";\n', encoding="utf-8")
        (root / "src" / "leak.txt").write_text(token + "\n", encoding="utf-8")
        (root / "src" / "leak.json").write_text(
            '{"apikey":"' + eh_key + '"}\n', encoding="utf-8")
        (root / "src" / "identity.json").write_text(
            '{"ipb_pass_hash":"' + identity + '"}\n', encoding="utf-8")
        (root / "src" / "release.keystore").write_bytes(b"binary-placeholder")
        (root / "src" / "encrypted.pem").write_text(
            "-----BEGIN ENCRYPTED PRIVATE KEY-----\nsynthetic\n", encoding="utf-8")
        (root / "src" / "signing.properties").write_text(
            "storeFile=outside-repository\n", encoding="utf-8")
        (root / "build" / "ignored.txt").write_text(token + "\n", encoding="utf-8")

        findings = scan(root)
        if len(findings) != 6 or {item.path for item in findings} != {
                "src/encrypted.pem", "src/identity.json", "src/leak.json",
                "src/leak.txt", "src/release.keystore", "src/signing.properties"}:
            raise AssertionError(f"unexpected scanner self-test result count={len(findings)}")
        messages = "\n".join(finding.safe_message() for finding in findings)
        if token in messages or eh_key in messages or identity in messages:
            raise AssertionError("scanner output exposed the matched secret")
    print("Secret scanner self-test passed")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("root", nargs="?", default=".")
    parser.add_argument("--self-test", action="store_true")
    args = parser.parse_args()

    if args.self_test:
        self_test()
        return 0

    root = Path(args.root)
    if not root.is_dir():
        print("Secret scan root is not a directory", file=sys.stderr)
        return 2
    findings = scan(root)
    if findings:
        print(f"Secret scan failed with {len(findings)} finding(s):", file=sys.stderr)
        for finding in findings:
            print(finding.safe_message(), file=sys.stderr)
        return 1
    print("Secret scan passed: no matching credentials in current worktree")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
