#!/usr/bin/env python3
"""
Hunts the transcription for words that are not words.

Every error the book's author has caught in my copying has had one of three
shapes, and all three are mechanical:

  * a letter read on the wrong side of its dot — الشهاد for السهاد, تعازك for
    تعارك, ظهر for طهر, العثرة for العترة, يزدف for يردف, الازتجال for
    الارتجال;
  * a letter dropped — اسكنا for اسكرنا;
  * a letter added, or two changing places.

So what this tests is: a word the lexicon does not know, which it would know
if one dot moved, or one letter were added or taken away, or two letters
swapped. Both halves matter — the book is full of rare forms the lexicon
lacks, and full of ordinary words with dot-twins — and it is their meeting
that is narrow enough to be worth reading.

This replaces tools/dalail_dots.py, which tested only dots and which had a
fault that let two of the errors above through: it stripped ال and its
fellows off a word before asking the lexicon whether the word was known, but
not off the correction it proposed. So الارتجال, which the lexicon holds as
ارتجال, was judged no better than الازتجال, and the pair was never reported.

    python3 tools/dalail_spell.py
"""
import glob
import re
import sqlite3
import sys

DATA = "/usr/local/lib/python3.11/dist-packages/arramooz/data"

HARAKAT = re.compile(r"[ً-ْٰـ]")
LETTERS = "ابتثجحخدذرزسشصضطظعغفقكلمنهوي"

#: Letters of one shape when the letter is not the last of its word, and when
#: it is. Arabic's teeth — ب ت ث ن ي — are a single shape unless final; a final
#: nūn and yāʾ share their bowl, a final bāʾ, tāʾ and thāʾ their tail.
MEDIAL = ["بتثنيه", "جحخ", "دذ", "رز", "سش", "صض", "طظ", "عغ", "فق"]
FINAL = ["بتث", "ني", "هة", "جحخ", "دذ", "رز", "سش", "صض", "طظ", "عغ", "فق"]

#: What may hang off the front and back of a lemma. The dictionary holds
#: lemmas, so a word has to be stripped back before it can be looked up.
PREFIXES = ["", "ال", "وال", "فال", "بال", "كال", "لل", "و", "ف", "ب", "ل",
            "ك", "س", "ولل", "وب", "ول", "فب", "أ", "ي", "ت", "ن", "م",
            "وي", "ول", "فت", "سي", "لي", "لت", "لن", "وت", "ون", "بت"]
SUFFIXES = ["", "ه", "ها", "هم", "هن", "هما", "ك", "كم", "كن", "كما", "ي",
            "نا", "ات", "ة", "ان", "ين", "ون", "وا", "تم", "تن", "ت", "تك",
            "نه", "نك", "ا", "ى", "وه", "كه", "نهم", "هن", "ني", "نى"]


def normalise(w):
    """The word as the dictionary spells it: bare of harakat and of hamza."""
    w = HARAKAT.sub("", w)
    for a, b in (("أ", "ا"), ("إ", "ا"), ("آ", "ا"), ("ٱ", "ا"),
                 ("ؤ", "و"), ("ئ", "ي"), ("ى", "ي"), ("ة", "ه")):
        w = w.replace(a, b)
    return w


#: The clitics as the lexicon spells them. normalise() turns every hamza into
#: a bare alif, so a prefix written «أ» would never meet a normalised word —
#: which is why ارحم, اغفر and their like were being called unknown, and a
#: nonsense dot-twin proposed for each. They are put through the same sieve.
PREFIXES = sorted({normalise(p) for p in PREFIXES}, key=len, reverse=True)
SUFFIXES = sorted({normalise(s) for s in SUFFIXES}, key=len, reverse=True)


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


def dot_moves(word):
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


def one_letter(word):
    """Every word one letter away: one dropped, one added, or two swapped."""
    out = set()
    for i in range(len(word)):
        out.add(word[:i] + word[i + 1:])                      # a letter gone
    for i in range(len(word) - 1):                            # two swapped
        out.add(word[:i] + word[i + 1] + word[i] + word[i + 2:])
    for i in range(len(word) + 1):                            # one put in
        for c in LETTERS:
            out.add(word[:i] + c + word[i:])
    out.discard(word)
    return out


def book_words():
    """Every distinct word in the book, and where it was first seen."""
    seen = {}
    for path in sorted(glob.glob("dalail/**/*.txt", recursive=True)):
        for w in open(path, encoding="utf-8").read().split():
            bare = HARAKAT.sub("", w).strip("()[]{}،؛,.!؟?⟨⟩﴿﴾♡۞*-–")
            if len(bare) >= 4 and all(c in LETTERS + "أإآئؤةى" for c in bare):
                seen.setdefault(bare, path.split("/", 1)[1])
    return seen


def main():
    lex = lexicon()
    seen = book_words()
    print(f"المعجم: {len(lex)} صيغة — والكتاب: {len(seen)} كلمةً مختلفة\n",
          file=sys.stderr)

    dots, letters, lonely = [], [], []
    for w, place in sorted(seen.items(), key=lambda kv: kv[1]):
        if known(w, lex):
            continue
        n = normalise(w)
        fix = sorted({v for v in dot_moves(n) if known(v, lex)})
        if fix:
            dots.append((w, fix, place))
            continue
        fix = sorted({v for v in one_letter(n) if known(v, lex)})
        if fix:
            letters.append((w, fix, place))
        else:
            lonely.append((w, place))

    def show(title, rows):
        print(f"\n═══ {title} — {len(rows)} ═══")
        for w, fix, place in rows:
            print(f"  ⟪{w}⟫  ←  {'، '.join(fix[:5])}    ({place})")

    show("نقطةٌ واحدة تجعلها كلمةً معروفة", dots)
    show("حرفٌ واحد يُزاد أو يُحذف أو يُقلب", letters)
    print(f"\n═══ لا جارَ لها في المعجم — {len(lonely)} ═══")
    print("   (أكثرها أعلامٌ ونوادر، لكنها تُقرأ بالعين)")
    for w, place in lonely:
        print(f"  ⟪{w}⟫    ({place})")


if __name__ == "__main__":
    main()
