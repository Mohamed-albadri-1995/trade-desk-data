#!/usr/bin/env python3
"""
Typesets ratib.txt into a PDF that carries the app's design: the photograph
behind every page under the same veil, the same palette, the same ornaments, and
the repetition counts picked out in gold.

Arabic needs two things that are easy to get backwards. Letters must be reshaped
into their contextual forms, and the bidi reordering must happen per rendered
line, after the line breaks are chosen — reshaping a whole paragraph and letting
a layout engine wrap it puts the lines in the wrong order. Here every line is
laid out word by word, right to left, each word reordered on its own, which also
allows a word to be coloured differently from its neighbours.
"""
import re
import sys

import arabic_reshaper
from bidi.algorithm import get_display
from PIL import Image
from reportlab.lib.colors import HexColor
from reportlab.lib.pagesizes import A5
from reportlab.lib.utils import ImageReader
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from reportlab.pdfgen import canvas

SRC = "ratib-app/app/src/main/assets/ratib.txt"
FONT = "ratib-app/app/src/main/res/font/amiri.ttf"
PHOTO = "ratib-app/app/src/main/assets/background.jpg"
OUT = "أوراد-الطريقة-السمانية.pdf"
#: Backgrounds are flattened here rather than drawn as translucent rectangles
#: over the photo: banding shows through a stack of alpha bands, and one opaque
#: image per page keeps the file small.
PAGE_BG = "/tmp/awrad-page-bg.jpg"
COVER_BG = "/tmp/awrad-cover-bg.jpg"
BG_DPI = 200

# The app's own palette, values taken from res/values/colors.xml.
INK = HexColor("#1A1A1A")          # reading_text
GREEN = HexColor("#1B5E20")        # heading_text
BROWN = HexColor("#8A6D3B")        # subheading_text
GOLD_CUE = HexColor("#A9791A")     # marker_color
GREY = HexColor("#6E6E6E")         # footnote_text
GOLD = HexColor("#C9A227")         # gold
SLATE = HexColor("#2F4858")        # refrain_text
VEIL = HexColor("#FBFBF6")         # reading_scrim, drawn at its DC alpha
VEIL_ALPHA = 0xDC / 255.0

PAGE_W, PAGE_H = A5
MARGIN_X = 40
MARGIN_TOP = 50
MARGIN_BOT = 58
COL_W = PAGE_W - 2 * MARGIN_X

BODY, LEAD = 13.0, 22.0
HEAD, HEAD_LEAD = 17.0, 29.0
SUB, SUB_LEAD = 14.5, 25.0
FOOT, FOOT_LEAD = 9.5, 15.0

#: Same rule the app uses to pick out repetition counts: a parenthesised aside,
#: or a bare number in either set of digits.
CUE = re.compile(r"\([^)]*\)|[0-9٠-٩]+")
AR_DIGITS = "٠١٢٣٤٥٦٧٨٩"

#: The library strips harakat unless told otherwise, which would silently
#: unvocalise the قصيدة — it is fully pointed in the source and must stay so.
_reshaper = arabic_reshaper.ArabicReshaper({"delete_harakat": False})
# Checked by behaviour, not by reading the flag back: the configuration is held
# as strings, so a truthiness test on it passes either way.
assert any(m in _reshaper.reshape("الظَّاهِرَ") for m in "ًٌٍَُِّْ"), (
    "the reshaper is dropping harakat; the قصيدة would lose its vocalisation"
)


def arabic_number(n):
    return "".join(AR_DIGITS[int(d)] for d in str(n))


def render(word):
    """
    One token, reshaped and reordered, ready to draw.

    A bracketed count is handled whole. The reordering library does not mirror
    brackets, and this layout places each token right to left, so a lone «(»
    would land on the far side of its own phrase and read as «) ثلاثًا (». Keeping
    the brackets outside the reversal puts them where the app shows them.
    """
    if len(word) > 2 and word.startswith("(") and word.endswith(")"):
        # The inner spacing is kept, so «( ثلاثًا )» reads as it does in the app.
        return "(" + get_display(_reshaper.reshape(word[1:-1])) + ")"
    return get_display(_reshaper.reshape(word))


def tag_words(text):
    """
    Split into drawable tokens, marking the repetition counts.

    A count is one token even when it contains spaces, so «( ثلاثًا بالمد )» is
    never split across two lines and its brackets stay together.
    """
    out, i = [], 0
    for m in CUE.finditer(text):
        for w in text[i:m.start()].split(" "):
            if w:
                out.append((w, False))
        out.append((m.group().strip(), True))
        i = m.end()
    for w in text[i:].split(" "):
        if w:
            out.append((w, False))
    return out


class Book:
    def __init__(self, c):
        self.c = c
        self.photo = ImageReader(PAGE_BG)
        self.y = PAGE_H - MARGIN_TOP
        self.page = 1
        self.footnote = None
        self.total = 0
        self.background()

    # ---- the page itself -------------------------------------------------
    def background(self):
        """The veiled photograph, already flattened, filling the page."""
        self.c.drawImage(self.photo, 0, 0, width=PAGE_W, height=PAGE_H)

    def width(self, word, size):
        return self.c.stringWidth(render(word), "Amiri", size)

    def space(self, size):
        return self.c.stringWidth(" ", "Amiri", size)

    # ---- lines -----------------------------------------------------------
    def wrap(self, text, size):
        """Break tagged words into lines that fit the column."""
        lines, cur, cur_w = [], [], 0.0
        sp = self.space(size)
        for word, cue in tag_words(text):
            ww = self.width(word, size)
            trial = cur_w + ww + (sp if cur else 0)
            if trial <= COL_W or not cur:
                cur.append((word, cue))
                cur_w = trial
            else:
                lines.append(cur)
                cur, cur_w = [(word, cue)], ww
        if cur:
            lines.append(cur)
        return lines

    def draw_line(self, words, size, colour, align, cue_colour=None):
        """
        Lays a line out right to left: the first word of the sentence sits at
        the right edge. Each word is reordered on its own, so one can be
        coloured without disturbing the rest.
        """
        self.c.setFont("Amiri", size)
        widths = [self.width(w, size) for w, _ in words]
        sp = self.space(size)
        n = len(words)

        if align == "justify" and n > 1:
            gap = (COL_W - sum(widths)) / (n - 1)
            x = PAGE_W - MARGIN_X
        elif align == "center":
            gap = sp
            total = sum(widths) + gap * (n - 1)
            x = (PAGE_W + total) / 2
        else:  # right
            gap = sp
            x = PAGE_W - MARGIN_X

        for (word, cue), ww in zip(words, widths):
            self.c.setFillColor(cue_colour if (cue and cue_colour) else colour)
            self.c.drawString(x - ww, self.y - size, render(word))
            x -= ww + gap
        self.y -= LEAD if size == BODY else (HEAD_LEAD if size == HEAD else SUB_LEAD)

    # ---- page furniture --------------------------------------------------
    def footnote_height(self):
        if not self.footnote:
            return 0
        return len(self.wrap(self.footnote, FOOT)) * FOOT_LEAD + 16

    def room(self):
        return self.y - MARGIN_BOT - self.footnote_height()

    def need(self, h):
        if self.room() < h:
            self.new_page()

    def new_page(self):
        self.draw_footnote()
        self.draw_folio()
        self.c.showPage()
        self.page += 1
        self.y = PAGE_H - MARGIN_TOP
        self.footnote = None
        self.background()

    def draw_footnote(self):
        if not self.footnote:
            return
        lines = self.wrap(self.footnote, FOOT)
        base = MARGIN_BOT + len(lines) * FOOT_LEAD
        self.c.setStrokeColor(GREY)
        self.c.setLineWidth(0.4)
        self.c.line(PAGE_W / 2 - 52, base + 10, PAGE_W / 2 + 52, base + 10)
        saved, self.y = self.y, base + FOOT
        for ln in lines:
            self.c.setFont("Amiri", FOOT)
            widths = [self.width(w, FOOT) for w, _ in ln]
            sp = self.space(FOOT)
            total = sum(widths) + sp * (len(ln) - 1)
            x = (PAGE_W + total) / 2
            for (word, _), ww in zip(ln, widths):
                self.c.setFillColor(GREY)
                self.c.drawString(x - ww, self.y - FOOT, render(word))
                x -= ww + sp
            self.y -= FOOT_LEAD
        self.y = saved

    def draw_folio(self):
        """Page number, in the app's own «current / total» form."""
        text = f"{arabic_number(self.page)} / {arabic_number(self.total or self.page)}"
        self.c.setFont("Amiri", 10)
        self.c.setFillColor(GREEN)
        w = self.c.stringWidth(text, "Amiri", 10)
        self.c.drawString((PAGE_W - w) / 2, MARGIN_BOT - 24, text)

    # ---- blocks ----------------------------------------------------------
    def heading(self, text):
        lines = self.wrap(f"۞  {text}  ۞", HEAD)
        self.need(len(lines) * HEAD_LEAD + 2 * LEAD)
        self.y -= 6
        for ln in lines:
            self.draw_line(ln, HEAD, GREEN, "center")
        self.y -= 4

    def subheading(self, text):
        lines = self.wrap(f"﴿ {text} ﴾", SUB)
        self.need(len(lines) * SUB_LEAD + 2 * LEAD)
        self.y -= 4
        for ln in lines:
            self.draw_line(ln, SUB, BROWN, "center")
        self.y -= 2

    def body(self, text, ink=INK):
        # The app's own test: verse, or a line too short to wrap, is centred;
        # only a passage of running prose is justified.
        if "\n" in text or len(text) <= 55:
            # Verse: a hemistich to a line, centred, couplets kept whole.
            parts = text.split("\n")
            self.need(min(len(parts), 2) * LEAD)
            for part in parts:
                for ln in self.wrap(part, BODY):
                    self.need(LEAD)
                    self.draw_line(ln, BODY, ink, "center", GOLD_CUE)
            self.y -= 5
        else:
            lines = self.wrap(text, BODY)
            for i, ln in enumerate(lines):
                self.need(LEAD)
                last = i == len(lines) - 1
                self.draw_line(ln, BODY, ink, "right" if last else "justify", GOLD_CUE)
            self.y -= 6


def parse(path):
    blocks, buf = [], []
    refrain = False

    def flush():
        nonlocal refrain
        if buf:
            blocks.append(("refrain" if refrain else "body", "\n".join(buf)))
            buf.clear()
        refrain = False

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
        elif line.startswith("* ") and not buf:
            # Marks the block as recited apart from its surroundings, and gives
            # the whole of it a colour of its own.
            refrain = True
            buf.append(line[2:].strip())
        else:
            buf.append(line)
    flush()
    return blocks


def fill_crop(size):
    """The photograph scaled to cover `size` and centre-cropped, as the app does."""
    img = Image.open(PHOTO).convert("RGB")
    tw, th = size
    scale = max(tw / img.width, th / img.height)
    img = img.resize((max(1, round(img.width * scale)), max(1, round(img.height * scale))),
                     Image.LANCZOS)
    left, top = (img.width - tw) // 2, (img.height - th) // 2
    return img.crop((left, top, left + tw, top + th))


def build_backgrounds():
    """Flattens the reading veil and the cover scrim into two images."""
    px = (round(PAGE_W / 72 * BG_DPI), round(PAGE_H / 72 * BG_DPI))

    # Reading pages: the cream veil at its own alpha, evenly over the photo.
    page = fill_crop(px)
    veil = Image.new("RGB", px, (0xFB, 0xFB, 0xF6))
    Image.blend(page, veil, VEIL_ALPHA).save(PAGE_BG, quality=88)

    # Cover: black, opaque at the foot and clearing upward, so the title reads
    # over the bright robe.
    cov = fill_crop(px).convert("RGBA")
    shade = Image.new("L", (1, px[1]))
    for y in range(px[1]):
        t = y / (px[1] - 1)          # 0 at the top, 1 at the foot
        shade.putpixel((0, y), int(255 * min(1.0, 0.06 + 0.92 * max(0.0, t - 0.30) / 0.70)))
    mask = shade.resize(px)
    cov = Image.composite(Image.new("RGBA", px, (0, 0, 0, 255)), cov, mask)
    cov.convert("RGB").save(COVER_BG, quality=88)


def cover(c):
    """The app's opening screen: the photograph, darkened, titled from below."""
    c.drawImage(ImageReader(COVER_BG), 0, 0, width=PAGE_W, height=PAGE_H)

    def centred(text, size, colour, y):
        c.setFont("Amiri", size)
        c.setFillColor(colour)
        v = get_display(_reshaper.reshape(text))
        c.drawString((PAGE_W - c.stringWidth(v, "Amiri", size)) / 2, y, v)

    centred("۞", 22, GOLD, 196)
    centred("أوراد الطريقة السمانية", 30, HexColor("#FFFFFF"), 140)
    c.setStrokeColor(GOLD)
    c.setLineWidth(1.2)
    c.line(PAGE_W / 2 - 62, 124, PAGE_W / 2 + 62, 124)
    centred("السجادة السليمانية", 14, HexColor("#F2F2F2"), 98)
    centred("راتب السعادة والأوراد المربوطة", 11, HexColor("#D8D8D8"), 74)
    c.showPage()


def build(total_hint=0):
    """Lays the book out. Run twice: the first pass counts the pages."""
    pdfmetrics.registerFont(TTFont("Amiri", FONT))
    build_backgrounds()
    c = canvas.Canvas(OUT, pagesize=A5)
    c.setTitle("أوراد الطريقة السمانية")
    c.setAuthor("الطريقة السمانية · السجادة السليمانية")
    cover(c)

    book = Book(c)
    book.total = total_hint
    for kind, text in parse(SRC):
        if kind == "head":
            book.heading(text)
        elif kind == "sub":
            book.subheading(text)
        elif kind == "note":
            book.footnote = text
        elif kind == "refrain":
            book.body(text, SLATE)
        else:
            book.body(text)
    book.draw_footnote()
    book.draw_folio()
    c.showPage()
    c.save()
    return book.page


def main():
    pages = build()          # counts them
    pages = build(pages)     # again, so «١ / ١٥» knows the total
    print(f"{OUT} — {pages + 1} pages including the cover")


if __name__ == "__main__":
    sys.exit(main())
