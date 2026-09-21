"""Print -1, 0, or 1 for the release version comparison."""

import argparse
import re


SEMVER = re.compile(
    r"(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)"
    r"(?:-([0-9A-Za-z-]+(?:\.[0-9A-Za-z-]+)*))?"
    r"(?:\+[0-9A-Za-z-]+(?:\.[0-9A-Za-z-]+)*)?"
)


def precedence(version):
    match = SEMVER.fullmatch(version)
    if match is None:
        raise ValueError(f"Invalid semantic version: {version}")

    major, minor, patch, prerelease = match.groups()
    if prerelease is None:
        return int(major), int(minor), int(patch), 1, ()

    identifiers = prerelease.split(".")
    if any(part.isdigit() and len(part) > 1 and part[0] == "0" for part in identifiers):
        raise ValueError(f"Invalid semantic version: {version}")
    suffix = tuple((0, int(part)) if part.isdigit() else (1, part) for part in identifiers)
    return int(major), int(minor), int(patch), 0, suffix


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("left", help="Current stable MAJOR.MINOR.PATCH version")
    parser.add_argument("right", help="Previous SemVer version, or empty if none")
    args = parser.parse_args()

    if not re.fullmatch(r"(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)", args.left):
        parser.error(f"Release version must be stable MAJOR.MINOR.PATCH: {args.left}")

    try:
        left = precedence(args.left)
        right = precedence(args.right) if args.right else None
    except ValueError as error:
        parser.error(str(error))
    print(1 if right is None else (left > right) - (left < right))


if __name__ == "__main__":
    main()
