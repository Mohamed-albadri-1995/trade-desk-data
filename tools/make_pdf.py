#!/usr/bin/env python3
"""
Typesets ratib.txt into a print-ready PDF.

Arabic needs two passes that are easy to get backwards: letters must be reshaped
into their contextual forms, and the bidi algorithm must run *per rendered line*,
after the line breaks are decided. Reshaping the whole paragraph and letting the
PDF library wrap it puts the lines in the wrong order, so the wrapping is done
here and each finished line is reordered on its own.
"""
import re
import sys

import arabic_reshaper
from bidi.algorithm import get_display
from reportlab.lib.colors import Color
from reportlab.lib.pagesizes import A5
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from reportlab.pdfgen import canvas

SRC = "ratib-app/app/src/main/assets/ratib.txt"
FONT = "ratib-app/app/src/main/res/font/amiri.ttf"
OUT = "أوراد-الطريقة-السمانية.pdf"

PAGE_W, PAGE_H = A5
MARGIN_X = 42
MARGIN_TOP = 54
MARGIN_BOT = 62

BODY, LEAD = 13.0, 23.0
HEAD, HEAD_LEAD = 17.0, 30.0
SUB, SUB_LEAD = 14.5, 26.0
FOOT, FOOT_LEAD = 9.5, 15.0

INK = Color(0.09, 0.13, 0.11)
GREEN = Color(0.11, 0.37, 0.20)
GOLD = Color(0.60, 0.44, 0.10)
GREY = Color(0.42, 0.46, 0.44)

COL_W = PAGE_W - 2 * MARGIN_X
AR_DIGITS = "٠١٢٣٤٥٦٧٨٩"


def arabic_number(n):
    return "".join(AR_DIGITS[int(d)] for d in str(n))


#: The library strips harakat unless told otherwise, which would silently
#: unvocalise the قصيدة — it is fully pointed in the source and must stay so.
_reshaper = arabic_reshaper.ArabicReshaper({"delete_harakat": False})

# Checked by behaviour, not by reading the flag back: the configuration is held
# as strings, so a truthiness test on it passes either way.
assert any(m in _reshaper.reshape("الظَّاهِرَ") for m in "ًٌٍَُِّْ"), (
    "the reshaper is dropping harakat; the قصيدة would lose its vocalisation"
)


def shape(text):
    """Contextual letter forms, still in logical order. Keeps the harakat."""
    return _reshaper.reshape(text)


def visual(text):
    """Reorder one finished line for display. Never call on unwrapped text."""
    return get_display(text)


class Book:
    def __init__(self, c):
        self.c = c
        self.y = PAGE_H - MARGIN_TOP
        self.page = 1
        self.footnote = None

    # ---- measuring -------------------------------------------------------
    def width(self, shaped, size):
        return self.c.stringWidth(shaped, "Amiri", size)

    def wrap(self, text, size):
        """Break shaped text into lines that fit the column."""
        shaped = shape(text)
        words = shaped.split(" ")
        lines, cur = [], ""
        for w in words:
            trial = w if not cur else cur + " " + w
            if self.width(trial, size) <= COL_W or not cur:
                cur = trial
            else:
                lines.append(cur)
                cur = w
        if cur:
            lines.append(cur)
        return lines

    # ---- drawing ---------------------------------------------------------
    def footnote_height(self):
        if not self.footnote:
            return 0
        return len(self.wrap(self.footnote, FOOT)) * FOOT_LEAD + 16

    def room(self):
        return self.y - MARGIN_BOT - self.footnote_height()

    def new_page(self):
        self.draw_footnote()
        self.draw_folio()
        self.c.showPage()
        self.page += 1
        self.y = PAGE_H - MARGIN_TOP
        self.footnote = None

    def need(self, h):
        if self.room() < h:
            self.new_page()

    def line_centred(self, shaped, size, colour, lead):
        self.c.setFont("Amiri", size)
        self.c.setFillColor(colour)
        v = visual(shaped)
        w = self.width(v, size)
        self.c.drawString((PAGE_W - w) / 2, self.y - size, v)
        self.y -= lead

    def line_justified(self, shaped, size, colour, lead, last):
        """Right-aligned; interior spaces stretched unless it ends a paragraph."""
        self.c.setFont("Amiri", size)
        self.c.setFillColor(colour)
        v = visual(shaped)
        if last:
            self.c.drawRightString(PAGE_W - MARGIN_X, self.y - size, v)
        else:
            words = v.split(" ")
            if len(words) < 2:
                self.c.drawRightString(PAGE_W - MARGIN_X, self.y - size, v)
            else:
                text_w = sum(self.width(w, size) for w in words)
                gap = (COL_W - text_w) / (len(words) - 1)
                # The bidi pass already put these words in display order, so they
                # are laid left to right. Walking rightwards from the right edge
                # instead would reverse the line a second time.
                x = MARGIN_X
                for w in words:
                    self.c.drawString(x, self.y - size, w)
                    x += self.width(w, size) + gap
        self.y -= lead

    def draw_footnote(self):
        if not self.footnote:
            return
        lines = self.wrap(self.footnote, FOOT)
        h = len(lines) * FOOT_LEAD
        base = MARGIN_BOT + h
        self.c.setStrokeColor(GREY)
        self.c.setLineWidth(0.4)
        self.c.line(PAGE_W / 2 - 55, base + 10, PAGE_W / 2 + 55, base + 10)
        self.c.setFont("Amiri", FOOT)
        self.c.setFillColor(GREY)
        y = base
        for ln in lines:
            v = visual(ln)
            self.c.drawString((PAGE_W - self.width(v, FOOT)) / 2, y, v)
            y -= FOOT_LEAD

    def draw_folio(self):
        self.c.setFont("Amiri", 10)
        self.c.setFillColor(GREY)
        n = arabic_number(self.page)
        self.c.drawString((PAGE_W - self.width(shape(n), 10)) / 2, MARGIN_BOT - 26, n)

    # ---- blocks ----------------------------------------------------------
    def heading(self, text):
        lines = self.wrap(f"۞  {text}  ۞", HEAD)
        # A heading with two lines of its text beneath it, or a fresh page.
        self.need(len(lines) * HEAD_LEAD + 2 * LEAD)
        self.y -= 6
        for ln in lines:
            self.line_centred(ln, HEAD, GREEN, HEAD_LEAD)
        self.y -= 4

    def subheading(self, text):
        lines = self.wrap(f"﴿ {text} ﴾", SUB)
        self.need(len(lines) * SUB_LEAD + 2 * LEAD)
        self.y -= 4
        for ln in lines:
            self.line_centred(ln, SUB, GOLD, SUB_LEAD)
        self.y -= 2

    def body(self, text):
        verse = "\n" in text
        if verse:
            # Verse: each hemistich on its own centred line, couplets kept whole.
            src = text.split("\n")
            self.need(min(len(src), 2) * LEAD)
            for part in src:
                for ln in self.wrap(part, BODY):
                    self.need(LEAD)
                    self.line_centred(ln, BODY, INK, LEAD)
            self.y -= 5
        else:
            lines = self.wrap(text, BODY)
            for i, ln in enumerate(lines):
                self.need(LEAD)
                self.line_justified(ln, BODY, INK, LEAD, last=(i == len(lines) - 1))
            self.y -= 6


def parse(path):
    blocks, buf = [], []

    def flush():
        if buf:
            blocks.append(("body", "\n".join(buf)))
            buf.clear()

    for raw in open(path, encoding="utf-8"):
        line = raw.strip()
        if not line:
            flush()
        elif line.startswith("## "):
            flush()
            blocks.append(("sub", line[3:].strip()))
        elif line.startswith("# "):
            flush()
            blocks.append(("head", line[2:].strip()))
        elif line.startswith("> "):
            flush()
            blocks.append(("note", line[2:].strip()))
        else:
            buf.append(line)
    flush()
    return blocks


def title_page(c):
    def centred(text, size, colour, y):
        c.setFont("Amiri", size)
        c.setFillColor(colour)
        v = visual(shape(text))
        c.drawString((PAGE_W - c.stringWidth(v, "Amiri", size)) / 2, y, v)

    centred("۞", 26, GOLD, PAGE_H - 190)
    centred("أوراد الطريقة السمانية", 27, GREEN, PAGE_H - 250)
    c.setStrokeColor(GOLD)
    c.setLineWidth(1)
    c.line(PAGE_W / 2 - 78, PAGE_H - 272, PAGE_W / 2 + 78, PAGE_H - 272)
    centred("السجادة السليمانية", 16, GOLD, PAGE_H - 300)
    centred("راتب السعادة والأوراد المربوطة", 13, INK, PAGE_H - 350)
    centred("۞", 16, GOLD, MARGIN_BOT + 40)
    c.showPage()


def main():
    pdfmetrics.registerFont(TTFont("Amiri", FONT))
    c = canvas.Canvas(OUT, pagesize=A5)
    c.setTitle("أوراد الطريقة السمانية")
    c.setAuthor("الطريقة السمانية · السجادة السليمانية")

    title_page(c)
    book = Book(c)
    for kind, text in parse(SRC):
        if kind == "head":
            book.heading(text)
        elif kind == "sub":
            book.subheading(text)
        elif kind == "note":
            book.footnote = text
        else:
            book.body(text)
    book.draw_footnote()
    book.draw_folio()
    c.showPage()
    c.save()
    print(f"{OUT} — {book.page + 1} pages")


if __name__ == "__main__":
    sys.exit(main())
