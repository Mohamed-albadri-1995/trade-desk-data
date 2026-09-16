#!/usr/bin/env python3
"""
Gathers a حزب's صلوات into one text and lays it out.

Each صلاة is preceded by its number, alone on its line and set in the aside
face — the book's own way of marking where one ends and the next begins.
"""
import glob, os, subprocess, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import dalail_page

ARABIC_DIGITS = "٠١٢٣٤٥٦٧٨٩"


def arabic(n):
    return "".join(ARABIC_DIGITS[int(d)] for d in str(n))


def gather(folder):
    out = []
    for path in sorted(glob.glob(os.path.join(folder, "*.txt"))):
        number = int(os.path.basename(path)[:3])
        body = " ".join(open(path, encoding="utf-8").read().split())
        out.append(f"⟨{arabic(number)}⟩")
        out.append(body)
    return "\n".join(out)


if __name__ == "__main__":
    folder, prefix, title = sys.argv[1], sys.argv[2], sys.argv[3]
    dalail_page.render(gather(folder), prefix, title)
