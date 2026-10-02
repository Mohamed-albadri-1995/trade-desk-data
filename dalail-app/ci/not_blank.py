#!/usr/bin/env python3
"""
Says whether a screenshot has anything on it.

This is the check that «it opens on a blank screen» needed. Every other check
passed while that was happening: the package was sound, the app installed, it
started, it was the activity on screen — and nothing was drawn. A leaf of the
book has thousands of distinct tones; a blank screen has one or two.

Exits 0 if the screen has something on it, 1 if it does not.

    python3 ci/not_blank.py shot/1-opened.png [least-tones]
"""
import sys

from PIL import Image

#: Below this many distinct tones in a small sample, there is nothing to read.
#: A leaf gives some thousands; an empty pager gives the one or two of its own
#: background, and a solid colour gives one.
LEAST = 200


def tones(path):
    im = Image.open(path).convert("RGB").resize((160, 280), Image.BILINEAR)
    return len(set(im.getdata()))


def main():
    path = sys.argv[1]
    least = int(sys.argv[2]) if len(sys.argv) > 2 else LEAST
    found = tones(path)
    print(f"   tones on screen: {found} (needs {least})")
    sys.exit(0 if found >= least else 1)


if __name__ == "__main__":
    main()
