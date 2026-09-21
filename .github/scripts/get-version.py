"""Read the Gradle version from this checkout or the nearest matching tag."""

import argparse
import os
from pathlib import Path
import subprocess
import sys
import tempfile


def git(*args):
    return subprocess.run(
        ["git", *args], check=True, capture_output=True, text=True
    ).stdout.strip()


def gradle_version(checkout):
    wrapper = checkout / ("gradlew.bat" if os.name == "nt" else "gradlew")
    return subprocess.run(
        [str(wrapper), "-q", "echoVersion"],
        cwd=checkout, check=True, stdout=subprocess.PIPE, text=True
    ).stdout.strip()


def nearest_tag(pattern):
    if not git("tag", "--merged", "HEAD", "--list", pattern):
        return None
    return git("describe", "--tags", "--abbrev=0", "--match", pattern, "HEAD")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("tag_pattern", nargs="?", help="Read from the nearest reachable matching tag")
    args = parser.parse_args()
    checkout = Path(git("rev-parse", "--show-toplevel"))

    if args.tag_pattern:
        tag = nearest_tag(args.tag_pattern)
        if tag is None:
            version = ""
        else:
            with tempfile.TemporaryDirectory(prefix="underilla-version-") as root:
                worktree = Path(root) / "checkout"
                subprocess.run(
                    ["git", "worktree", "add", "--detach", str(worktree), tag],
                    check=True, stdout=subprocess.DEVNULL,
                )
                try:
                    version = gradle_version(worktree)
                finally:
                    subprocess.run(["git", "worktree", "remove", "--force", str(worktree)], check=True)
    else:
        version = gradle_version(checkout)

    if not version and not args.tag_pattern:
        sys.exit("Gradle returned an empty version")

    print(version)


if __name__ == "__main__":
    main()
