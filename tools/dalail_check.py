#!/usr/bin/env python3
"""
Holds the text to every correction its author has ever ordered.

The book's author put it plainly: «كل مرة اعطيك تصحيح تصحح الجديد وتخرب القديم»
— each time you are given a correction you fix the new and spoil the old. He was
right, and the cause was not carelessness over any one word but the absence of
this: corrections were applied from a message and never checked against the text
afterwards, so a word could be put back the way it came and nothing would say so.

It had already happened. In the ninth batch «بِذَلِكَ رَدًّا» was corrected to
«بِذَلِكَ رَدًا» by his order. In the tenth his line was read inside out — his
second line is the right reading and his third names the fault, and the two were
swapped — so the shadda went back on, undoing his own correction, and a sweep
then carried the same inverted rule to «مُرًا» as well. Nothing in the работа
complained, because nothing was looking.

So this looks. dalail/CORRECTIONS.md is the standing record of every correction;
this reads it and, for each one, asks the text two questions: is the reading he
ordered there, and is the reading he rejected gone? Run it after every batch,
before the book is built.

    python3 tools/dalail_check.py          # exit 1 if anything is adrift

A row is skipped where it cannot be tested: where the record says the word is
موقوف — standing until he rules — or where the cell holds a note rather than a
reading. And a row is skipped where a later row supersedes it: the fourth batch
set «سَطَّاءُ» and a later one changed its last vowel to «سَطَّاءْ», so the
earlier reading is rightly no longer in the text.
"""
import glob
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.join(HERE, "..", "dalail")
RECORD = os.path.join(ROOT, "CORRECTIONS.md")

#: A cell that holds a note, not a reading. Anything with a full stop, a
#: parenthesis of explanation or one of these words is read by the eye, not here.
NOT_A_READING = re.compile(r"موقوف|لعلّ|لعل|الصواب|صوابه|لماذا|القافية|المصوَّرة"
                           r"|الكتاب|ليست كلمة|مرّة|مرات|بلا شكل")
#: The marks the record wears for emphasis, and the note it hangs off a reading.
BOLD = re.compile(r"\*\*")
NOTE = re.compile(r"\s+[—–]\s+.*$")
ARABIC = re.compile(r"[؀-ۿ]")
#: How a row names its place, where it names one: a صلاة by its number.
PLACE = re.compile(r"[٠-٩]+")
DIGITS = {d: str(n) for n, d in enumerate("٠١٢٣٤٥٦٧٨٩")}


def cell(text):
    """A cell reduced to the reading it holds, or None if it holds none."""
    text = NOTE.sub("", BOLD.sub("", text)).strip().strip("«»").strip()
    if not text or NOT_A_READING.search(text) or not ARABIC.search(text):
        return None
    # A heading, or a sentence rather than a reading.
    if len(text.split()) > 6 or text.endswith("."):
        return None
    return text


#: A table is a table of corrections only where its own head says so: the
#: second column names what the reading was and the third what it became. The
#: record holds other tables too — places left standing, readings put to the
#: author, a batch's own state — and reading those as corrections had this
#: report half noise.
WAS_HEAD = re.compile(r"^(كان|كانت|في الأصل|قرأتُها|ما كان|ما كان عندي)$")
NOW_HEAD = re.compile(r"^(صار|صارت|الصواب|والصواب|ما كان ساقطًا)$")


def rows():
    """Every testable correction in the record, in the order it was written."""
    out, reading = [], False
    for n, line in enumerate(open(RECORD, encoding="utf-8"), 1):
        line = line.strip()
        if not line.startswith("|"):
            if line:
                reading = False      # prose between tables closes the last one
            continue
        if set(line) <= set("|- "):
            continue
        cells = [c.strip() for c in line.strip("|").split("|")]
        if len(cells) < 3:
            reading = False
            continue
        if WAS_HEAD.match(cells[1]) and NOW_HEAD.match(cells[2]):
            reading = True
            continue
        if not reading:
            continue
        was, now = cell(cells[1]), cell(cells[2])
        if was and now and was != now:
            out.append({"line": n, "place": cells[0], "was": was, "now": now})
    return out


#: Files that sit beside the book without being the book: the author's own
#: messages, and the Qur'anic verses held apart as a guard. Read as text they
#: would double every verse and report the book against itself.
NOT_THE_BOOK = ("MESSAGES.txt", "AYAT.txt")


def texts():
    """The book's text: the whole of it, and each file on its own."""
    files = {}
    for path in sorted(glob.glob(os.path.join(ROOT, "**", "*.txt"),
                                 recursive=True)):
        name = os.path.relpath(path, ROOT)
        if name in NOT_THE_BOOK:
            continue
        files[name] = open(path, encoding="utf-8").read()
    return files


def scope(place, files):
    """
    The files a row's place names, or all of them.

    A row that names a صلاة is tested in that صلاة alone. Without this, a
    reading the author rejected in one place is reported as still standing
    because the same word is right somewhere else — «ظَهَرَ» was corrected in
    صلاة ٤٥ and is sound in صلاة ٤٣.
    """
    hit = PLACE.search(place)
    if hit:
        n = int("".join(DIGITS[d] for d in hit.group()))
        if 1 <= n <= 90:
            want = f"{n:03d}.txt"
            named = {k: v for k, v in files.items() if k.endswith(want)}
            if named:
                return named
    return files


#: The verses of the Qur'an in the book, kept apart from it.
AYAT = os.path.join(ROOT, "AYAT.txt")
VERSE = re.compile(r"﴿[^﴾]*﴾")


def ayat(files):
    """
    Whether the Qur'anic verses still read as they are printed.

    They are set in the Qur'an's own orthography, which is not the book's: the
    verse of al-Qaṣaṣ has «أَنزَلْتَ» with no sukūn on the nūn where the book
    writes «أَنْزَلْتَ», and the last verse keeps its superscript alifs and its
    ٱ. A sweep over the book's spelling will walk into them and quietly
    regularise the Qur'an, which is what happened when the sukūn was unified —
    that verse came out of it changed, and nothing but an afterthought caught
    it. So the verses are held in dalail/AYAT.txt and compared byte for byte.

    A sura's name is the book's own writing and is not held here.
    """
    if not os.path.exists(AYAT):
        return ["dalail/AYAT.txt غير موجود — لا حارسَ على الآيات"]
    want = []
    for line in open(AYAT, encoding="utf-8"):
        if line.startswith("#") or "\t" not in line:
            continue
        where, verse = line.rstrip("\n").split("\t", 1)
        want.append((where, verse))
    have = []
    for name in sorted(files):
        for v in VERSE.findall(files[name]):
            if "سُورَة" not in v:
                have.append((name, v))
    trouble = []
    if len(have) != len(want):
        trouble.append(f"عددُ الآيات {len(have)} والمحفوظُ {len(want)}")
    for (wn, wv), (hn, hv) in zip(sorted(want), sorted(have)):
        if wv != hv:
            trouble.append(f"آيةٌ تغيّرت في {hn}:\n      المحفوظ: {wv}"
                           f"\n      في الكتاب: {hv}")
    return trouble


def main():
    files = texts()
    whole = "\n".join(files.values())
    found = rows()

    verses = ayat(files)
    if verses:
        print("✗ الآيات:")
        for t in verses:
            print(f"    {t}")
    else:
        print("✓ آياتُ التنزيل على رسمها")

    # A row whose reading a later row replaces is no longer the standing order.
    # Matched on containment, not equality: a later batch names its place with
    # the words about it — «قَهْرِكَ سَطَّاءُ» — and the earlier reading it
    # replaces sits inside that.
    for row in found:
        row["superseded"] = any(
            other["line"] > row["line"] and row["now"] in other["was"]
            for other in found)

    adrift = []
    for row in found:
        if row["superseded"]:
            continue
        here = row["now"] in whole
        gone = not any(row["was"] in t for t in scope(row["place"], files))
        if not (here and gone):
            row["here"], row["gone"] = here, gone
            adrift.append(row)

    tested = sum(1 for r in found if not r["superseded"])
    if verses:
        adrift.append(None)      # the verses alone are enough to stop a build
    print(f"السجلّ: {len(found)} تصحيحًا، منها {tested} تُختبر "
          f"و{len(found) - tested} نسختها دفعةٌ بعدها")
    print(f"سلِمت: {tested - len(adrift)}")
    if not adrift:
        print("\n✓ كلُّ تصحيحٍ أمر به صاحب الكتاب قائمٌ في النصّ.")
        return 0

    print(f"\n✗ أدرجت عن أمره: {len(adrift)}\n")
    for row in adrift:
        if row is None:
            continue
        why = []
        if not row["here"]:
            why.append("القراءةُ التي أمر بها غائبة")
        if not row["gone"]:
            why.append("القراءةُ التي رفضها باقية")
        print(f"  [{row['place']}]  «{row['was']}» ← «{row['now']}»")
        print(f"      {' و'.join(why)}   (سطر {row['line']} من السجلّ)")
    return 1


if __name__ == "__main__":
    sys.exit(main())
