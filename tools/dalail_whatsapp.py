#!/usr/bin/env python3
"""
Holds the text to the author's own messages, not to my record of them.

tools/dalail_check.py tests the text against dalail/CORRECTIONS.md — which is
my writing-down of what he ordered, and so can carry my mistakes. This tests it
against what he actually wrote: the exported WhatsApp conversation.

His messages run to a pattern. A number on the first line names the صلاة; the
line under it is the reading as it should be; and the last line, «الكلمه الغلط
X», names the word that is wrong. So the reading he quotes is the ruling, and
it is that which this compares.

A plain substring search is useless here — he types from the printed book with
his own habits, writing اللّٰه for الله and leaving a vowel off a word he is not
talking about. So this works in two steps. It finds his phrase in the text by
the letters alone, ignoring every mark; then, having found it, it sets his
words against mine one by one with the marks, reporting each word that differs.
Marks are put in a fixed order first, since a shadda written before a fatha and
one written after are the same word and different bytes.

Pass it the export, and the file of his correction messages pulled out of it:

    python3 tools/dalail_whatsapp.py corrections.txt

What it reports is not all error. Most of what remains is his own typing — the
superscript alif of اللّٰه, a vowel left off — and the eye still has to part that
from a real difference. What it does is make the list short enough to read.
"""

import re, glob, os, sys

ROOT = "dalail"
MARKS = set("ًٌٍَُِّْٰ")
H = re.compile(r"[ً-ْٰـ]")
PUNCT = "♡۞*()[]{}،؛,.!؟?⟨⟩﴿﴾–—‎‏"

def canon(w):
    """One spelling for one word: marks in a fixed order on each letter."""
    out, cur = [], []
    for c in w.strip(PUNCT):
        if c in MARKS:
            cur.append(c)
        else:
            out.append("".join(sorted(cur))); cur = []
            out.append(c)
    out.append("".join(sorted(cur)))
    return "".join(out)

def skel(w):
    w = H.sub("", w)
    for a, b in (("أ","ا"),("إ","ا"),("آ","ا"),("ٱ","ا"),("ؤ","و"),("ئ","ي"),
                 ("ى","ي"),("ة","ه")):
        w = w.replace(a, b)
    return w.strip(PUNCT)

def words(t):
    return [w for w in re.sub(r"[♡۞]", " ", t).split() if w.strip(PUNCT)]

files = {os.path.relpath(p, ROOT): words(open(p, encoding="utf-8").read())
         for p in glob.glob(os.path.join(ROOT, "**", "*.txt"), recursive=True)}
order = ["p03.txt","p04.txt","p05.txt","p06.txt","p07a.txt","p07b.txt",
         "p07c.txt","dawat_salawat.txt"]
allw = []
for k in order + sorted(k for k in files if k not in order):
    allw += [(k, i, w) for i, w in enumerate(files[k])]

DIG = {d: str(n) for n, d in enumerate("٠١٢٣٤٥٦٧٨٩")}
def number(line):
    line = line.strip()
    if re.fullmatch(r"[٠-٩]+", line): return int("".join(DIG[c] for c in line))
    if re.fullmatch(r"\d+", line): return int(line)
    return None

def find(need, hay):
    ns = [skel(w) for w in need]; hs = [skel(w) for w in hay]
    return [i for i in range(len(hs)-len(ns)+1) if hs[i:i+len(ns)] == ns]

blocks = open(sys.argv[1], encoding='utf-8').read().split("### ")
clean = nolocate = 0
for b in blocks:
    if not b.strip(): continue
    lines = [l for l in b.strip().splitlines() if l.strip()]
    head, body = lines[0], lines[1:]
    if "no one" in head: continue
    wrong = [re.sub(r"^(الكلم[ةه] الغلط|الغلط|وكلمت)\s*", "", l).strip()
             for l in body if re.match(r"^(الكلم[ةه] الغلط|الغلط|وكلمت)", l.strip())]
    right = [l for l in body
             if not re.match(r"^(الكلم[ةه] الغلط|الغلط|وكلمت|الهمزه)", l.strip())]
    place = None
    if right and number(right[0]) is not None:
        place, right = number(right[0]), right[1:]
    elif right and re.match(r"^(المقدمه|دعاء|دعوة)", right[0].strip()):
        right = right[1:]
    quote = words(" ".join(right))
    if not quote:
        print(f"— {head}   الغلط: {' / '.join(wrong)}   (لا عبارةَ فيها)"); continue
    scope = allw
    if place and 1 <= place <= 90:
        named = [t for t in allw if t[0].endswith(f"{place:03d}.txt")]
        if named: scope = named
    hay = [w for _,_,w in scope]
    hits = find(quote, hay)
    if not hits:
        best, bestrun = 0, None
        for n in range(len(quote), 0, -1):
            for s in range(len(quote)-n+1):
                if find(quote[s:s+n], hay):
                    best, bestrun = n, quote[s:s+n]; break
            if best: break
        nolocate += 1
        print(f"? {head}   الغلط: {' / '.join(wrong)}")
        print(f"     لم أجد عبارتَه كاملة ({best} من {len(quote)})؛ عبارتُه: {' '.join(quote)}")
        continue
    i = hits[0]; where = scope[i][0]
    bad = [(q, hay[i+j]) for j, q in enumerate(quote) if canon(q) != canon(hay[i+j])]
    if not bad:
        clean += 1; continue
    print(f"✗ {head}   [{where}]   الغلط: {' / '.join(wrong)}")
    for q, got in bad:
        print(f"     هو: {q}     أنا: {got}")
print(f"\n═══ مطابقٌ تمامًا: {clean} — لم تُوجد عبارتُه: {nolocate} ═══")
