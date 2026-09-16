#!/usr/bin/env python3
"""
Writes the reader's copy of دلائل الرحمات from the transcription.

The app reads one file. This gathers the transcription into it in the book's
order and marks it up the way the reader expects:

    = …    the book's name, once, above everything
    # …    a section — مقدمة, a حزب, دعوة الصلوات
    ## n   a صلاة's number, as the book prints it; after a tab, the suras it
           is built on, which the app shows in its index but not on the page
    ~ …    the book's closing line, set at the foot of its last page
    ⟨…⟩    an instruction to the reader — how often a passage is said
    ﴿…﴾    Qur'an, or a sura's name
    ♡      the book's own separator between one invocation and the next

Run it again whenever the transcription changes:

    python3 tools/dalail_asset.py
"""
import glob
import os
import re
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import dalail_book as book

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "dalail-app",
                   "app", "src", "main", "assets", "dalail.txt")

TITLE = "دَلَائِلُ الرَّحَمَاتِ"

#: A sura's name as the book gives it at the end of a صلاة, so the index can
#: say which suras that صلاة is built on.
SURA = re.compile(r"﴿\s*سُورَةِ?َ?\s+([^﴾]+?)\s*﴾")
#: Harakat, stripped from a sura's name so the index reads as a plain list.
HARAKAT = re.compile(r"[ً-ْٰـ]")


def suras(text):
    """The suras a صلاة names, bare, in the order it names them."""
    seen, out = set(), []
    for name in SURA.findall(text):
        bare = HARAKAT.sub("", name).strip()
        if bare and bare not in seen:
            seen.add(bare)
            out.append(bare)
    return out


def section(title, text):
    return [f"# {title}", ""] + [ln for ln in text.splitlines() if ln.strip()] + [""]


def hizb(n, title):
    """A حزب, each of its صلوات headed by its number and its suras."""
    out = [f"# {title}", ""]
    folder = os.path.join(book.ROOT, f"hizb{n}")
    for path in sorted(glob.glob(os.path.join(folder, "*.txt"))):
        number = book.arabic(int(os.path.basename(path)[:3]))
        body = " ".join(open(path, encoding="utf-8").read().split())
        named = "، ".join(suras(body))
        out += [f"## {number}\t{named}", "", body, ""]
    return out


def build():
    lines = [f"= {TITLE}", ""]
    lines += section("((مُقَدِّمَة))", book.read("p03.txt", "p04.txt"))
    lines += section("أَسْمَاءُ اللهِ الحُسْنَى", book.read("p05.txt"))
    lines += section(
        "دَعْوَةُ أَسْمَاءِ اللهِ الحُسْنَى",
        book.read("p06.txt") + f"\n{book.DIVIDER}\n" + book.read("p07a.txt")
        + f"\n{book.DIVIDER}\n" + book.read("p07b.txt"))
    lines += section("دُعَاءَ النِّيَّةِ", book.read("p07c.txt"))
    for i in range(7):
        lines += hizb(i + 1,
                      f"الحِزْبُ {book.ORDINALS[i]} * يَوْمَ {book.DAYS[i]}")

    # دعوة الصلوات already carries its own name and the book's closing line,
    # marked as the reader reads them.
    lines += [ln for ln in book.read("dawat_salawat.txt").splitlines() if ln.strip()]

    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with open(OUT, "w", encoding="utf-8") as f:
        f.write("\n".join(lines).rstrip() + "\n")

    body = [ln for ln in lines if ln and not ln[0] in "=#~"]
    print(f"{os.path.relpath(OUT)}: "
          f"{sum(1 for ln in lines if ln.startswith('# '))} قسم، "
          f"{sum(1 for ln in lines if ln.startswith('## '))} صلاة، "
          f"{sum(len(ln.split()) for ln in body)} كلمة")


if __name__ == "__main__":
    build()
