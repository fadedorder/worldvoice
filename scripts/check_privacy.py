"""Check staged changes or reachable Git history without printing sensitive values."""
import argparse
import re
import subprocess
import sys


def git(*args):
    return subprocess.check_output(["git", *args])


def private_email(value):
    value = value.lower()
    local, _, domain = value.rpartition("@")
    return not (domain == "users.noreply.github.com" or local in ("noreply", "no-reply")
                or domain in ("example.com", "example.org", "example.net")
                or domain.endswith((".example", ".invalid")))


EMAIL = re.compile(r"[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}")
HOME = re.compile(r"(?i)(?:[A-Z]:[\\/]+Users[\\/]+|/(?:home|Users)/)[^\s/\\]+")


def forbidden(path):
    parts = path.split("/")
    name = parts[-1].lower()
    return (path.startswith(".idea/") or path == ".claude/settings.local.json"
            or name.endswith(".log") or name == ".env"
            or (name.startswith(".env.") and name not in (".env.example", ".env.sample"))
            or name in ("id_rsa", "id_ed25519", "credentials.json"))


def inspect_text(raw):
    if raw.startswith((b"\xff\xfe", b"\xfe\xff")):
        text = raw.decode("utf-16", errors="replace")
    elif b"\0" in raw:
        return []
    else:
        text = raw.decode("utf-8", errors="replace")
    issues = []
    for number, line in enumerate(text.splitlines(), 1):
        if HOME.search(line):
            issues.append((number, "machine-specific home path"))
        if any(private_email(x) for x in EMAIL.findall(line)):
            issues.append((number, "non-noreply email"))
    return issues


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--staged", action="store_true")
    args = parser.parse_args()
    problems = []
    blobs = {}
    if args.staged:
        for role in ("AUTHOR", "COMMITTER"):
            identity = git("var", "GIT_" + role + "_IDENT").decode()
            match = re.search(r"<([^>]+)>", identity)
            if not match or private_email(match.group(1)):
                problems.append("Current " + role.lower() + " email must use a noreply address.")
        for record in git("ls-files", "--stage", "-z").split(b"\0"):
            if not record:
                continue
            metadata, path = record.split(b"\t", 1)
            mode, oid, stage = metadata.split()
            path = path.decode("utf-8", errors="replace")
            if stage != b"0":
                problems.append("Resolve index conflicts before committing.")
            if forbidden(path):
                problems.append(path + ": personal/runtime file must not be tracked.")
            if mode != b"160000":
                blobs.setdefault(oid.decode(), path)
    else:
        commits = git("rev-list", "--all").decode().splitlines()
        for sha in commits:
            emails = git("show", "-s", "--format=%ae%n%ce", sha).decode().splitlines()
            if any(private_email(x) for x in emails):
                problems.append(sha[:12] + ": commit identity contains a non-noreply email.")
            for record in git("ls-tree", "-r", "-z", sha).split(b"\0"):
                if not record:
                    continue
                metadata, path = record.split(b"\t", 1)
                _, kind, oid = metadata.split()
                path = path.decode("utf-8", errors="replace")
                if forbidden(path):
                    problems.append(sha[:12] + " " + path + ": personal/runtime file.")
                if kind == b"blob":
                    blobs.setdefault(oid.decode(), path)
            for line, reason in inspect_text(git("show", "-s", "--format=%B", sha)):
                problems.append(sha[:12] + ": commit message " + reason)
    for oid, path in blobs.items():
        for line, reason in inspect_text(git("cat-file", "blob", oid)):
            problems.append(f"{path}:{line}: {reason} (value redacted).")
    if problems:
        print("Privacy check blocked the operation:")
        for issue in sorted(set(problems)):
            print("- " + issue)
        return 1
    print(f"Privacy check passed: {len(blobs)} unique file versions checked.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
