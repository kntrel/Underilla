"""Print the body of a level-1 Markdown heading, up to the next level-1 heading."""

import argparse
from pathlib import Path
import re
import sys


HEADING = re.compile(r" {0,3}#(?!#)[ \t]*(.*)")
FENCE = re.compile(r" {0,3}(`{3,}|~{3,})(.*)")


def get_section(markdown, header):
    content = []
    found = False
    fence = None

    for line in markdown.splitlines(keepends=True):
        marker = FENCE.match(line)
        if fence:
            if (marker and marker[1][0] == fence[0]
                    and len(marker[1]) >= len(fence) and not marker[2].strip()):
                fence = None
        elif marker:
            fence = marker[1]
        else:
            heading = HEADING.match(line)
            if heading:
                if found:
                    return "".join(content)
                found = heading[1].strip() == header.strip()
                continue

        if found:
            content.append(line)

    if not found:
        raise ValueError(f"Level-1 heading not found: {header}")
    return "".join(content)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("file", type=Path, help="Markdown file to read")
    parser.add_argument("header", help="Heading text without the # prefix")
    args = parser.parse_args()

    try:
        section = get_section(args.file.read_text(encoding="utf-8-sig"), args.header)
    except (OSError, ValueError) as error:
        parser.error(str(error))
    sys.stdout.write(section)


if __name__ == "__main__":
    main()
