#!/usr/bin/env python3
"""
Hunts the transcription for letters read on the wrong side of a dot.

Every misreading the book's author has caught in my copying has had one
shape: two letters drawn identically, parted only by their dots — الشهاد
for السهاد, تعازك for تعارك, ظهر for طهر, التائبين for التائهين — and in
most of them the word I had written was not an Arabic word at all, while
the word one dot away was.

So that is what this tests, against a real lexicon rather than against the
book's own vocabulary: a word the dictionary does not know, which the
dictionary would know if a single dot moved. Those two facts together are
a far narrower net than either alone — the book is full of rare forms the
dictionary lacks, and full of ordinary words that have dot-twins.

    python3 tools/dalail_dots.py
"""
import glob
import itertools
import re
import sqlite3
import sys

DATA = "/usr/local/lib/python3.11/dist-packages/arramooz/data"

HARAKAT = re.compile(r"[ً-ْٰـ]")

#: Letters of one shape when the letter is not the last of its word, and when
#: it is. Arabic's teeth — ب ت ث ن ي — are a single shape unless final; a final
#: nūn and yāʾ share their bowl, a final bāʾ, tāʾ and thāʾ their tail.
MEDIAL = ["بتثنيه", "جحخ", "دذ", "رز", "سش", "صض", "طظ", "عغ", "فق"]
FINAL = ["بتث", "ني", "هة", "جحخ", "دذ", "رز", "سش", "صض", "طظ", "عغ", "فق"]

#: What may hang off the front and back of a lemma. The dictionary holds
#: lemmas, so a word has to be stripped back before it can be looked up.
PREFIXES = ["", "ال", "وال", "فال", "بال", "كال", "لل", "و", "ف", "ب", "ل",
            "ك", "س", "ولل", "وب", "ول", "فب", "أ", "ي", "ت", "ن", "م"]
SUFFIXES = ["", "ه", "ها", "هم", "هن", "هما", "ك", "كم", "كن", "كما", "ي",
            "نا", "ات", "ة", "ان", "ين", "ون", "وا", "تم", "تن", "ت", "تك",
            "نه", "نك", "هما", "ا", "ى", "وه", "كه"]


def normalise(w):
    """The word as the dictionary spells it: bare of harakat and of hamza."""
    w = HARAKAT.sub("", w)
    for a, b in (("أ", "ا"), ("إ", "ا"), ("آ", "ا"), ("ٱ", "ا"),
                 ("ؤ", "و"), ("ئ", "ي"), ("ى", "ي"), ("ة", "ه")):
        w = w.replace(a, b)
    return w


def lexicon():
    """Every form the lexicon knows, normalised the same way."""
    words = set()
    for db, queries in (
        ("arabicdictionary.sqlite",
         ["SELECT unvocalized FROM nouns", "SELECT vocalized FROM nouns",
          "SELECT unvocalized FROM verbs", "SELECT vocalized FROM verbs",
          "SELECT root FROM nouns", "SELECT root FROM verbs"]),
        ("stopwords.sqlite", ["SELECT UNVOCALIZED FROM STOPWORDS",
                              "SELECT VOCALIZED FROM STOPWORDS"]),
        ("wordfreq.sqlite", ["SELECT unvocalized FROM wordfreq",
                             "SELECT vocalized FROM wordfreq"]),
    ):
        con = sqlite3.connect(f"{DATA}/{db}")
        for q in queries:
            for (v,) in con.execute(q):
                if v:
                    words.add(normalise(v))
        con.close()
    return words


def known(word, lex):
    """Whether the word, stripped of its clitics, is in the lexicon."""
    w = normalise(word)
    if w in lex:
        return True
    for p in PREFIXES:
        if not w.startswith(p):
            continue
        rest = w[len(p):]
        for s in SUFFIXES:
            if s and not rest.endswith(s):
                continue
            stem = rest[: len(rest) - len(s)] if s else rest
            if len(stem) >= 2 and stem in lex:
                return True
    return False


def dot_variants(word):
    """Every word one dot-move away, position by position."""
    med = {c: g for g in MEDIAL for c in g}
    fin = {c: g for g in FINAL for c in g}
    out = set()
    for i, c in enumerate(word):
        group = (fin if i == len(word) - 1 else med).get(c)
        if not group:
            continue
        for other in group:
            if other != c:
                out.add(word[:i] + other + word[i + 1:])
    return out


def main():
    lex = lexicon()
    print(f"المعجم: {len(lex)} صيغة\n", file=sys.stderr)

    seen = {}
    for path in sorted(glob.glob("dalail/**/*.txt", recursive=True)):
        if path.endswith(".md"):
            continue
        for w in open(path, encoding="utf-8").read().split():
            b = HARAKAT.sub("", w).strip("()[]{}،؛,.!؟?⟨⟩﴿﴾♡۞*")
            if len(b) >= 4:
                seen.setdefault(b, path.split("/", 1)[1])

    suspect = []
    for w, place in seen.items():
        if known(w, lex):
            continue
        fixes = [v for v in dot_variants(normalise(w)) if v in lex]
        if fixes:
            suspect.append((w, sorted(fixes), place))

    print("كلمةٌ لا يعرفها المعجم، ويعرفها لو تحرّكت نقطةٌ واحدة:\n")
    for w, fixes, place in sorted(suspect, key=lambda s: s[2]):
        print(f"  ⟪{w}⟫  ←  {'، '.join(fixes[:4])}    ({place})")
    print(f"\n{len(suspect)} موضعًا من {len(seen)} كلمةً مختلفة")


if __name__ == "__main__":
    main()
