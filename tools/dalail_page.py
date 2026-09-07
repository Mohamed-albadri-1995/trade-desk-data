#!/usr/bin/env python3
"""
Lays دلائل الرحمات out page by page, in the book's own hand.

The page is a leaf of parchment inside a thin ruled border, with an ornamental
band across its head carrying the section's name — the band, its cartouche and
its rosettes all cut from the book's own printed page, so nothing here is
borrowed from anywhere else. A section's first page wears the band; the pages
that carry it on wear a slim rule naming the section and the leaf you are on.

Every page holds the same number of lines, and every line but the last of a
passage is justified to the full column, so the pages come out even.
"""
import sys
from PIL import Image, ImageDraw, ImageFont

BAND_IMG = "android-app/design/header-band.png"
ORNAMENT_IMG = "android-app/design/ornament.png"
FONT = "ratib-app/app/src/main/res/font/amiri.ttf"

PAGE_W, PAGE_H = 1000, 1720
MARGIN = 16          # from the leaf's edge to the ruled border
PAD_X = 34           # from the border to the column
LINES_PER_PAGE = 15

PAPER = (252, 247, 231)
INK = (36, 26, 11)
RULE = (150, 118, 46)
FAINT = (120, 96, 44)

#: Where the cartouche sits inside the band, in the band image's own pixels.
BAND_PANEL = (302, 70, 518, 148)

ORNAMENTS = ("۞", "♡", "♥", "❤")
LINE_RATIO = 1.62
ORNAMENT_RATIO = 0.74
ARABIC_DIGITS = "٠١٢٣٤٥٦٧٨٩"


def arabic(n):
    return "".join(ARABIC_DIGITS[int(d)] for d in str(n))


def load_ornament():
    """The rosette, keyed off its paper so it sits on any tone."""
    im = Image.open(ORNAMENT_IMG).convert("RGB")
    out = Image.new("RGBA", im.size)
    src, dst = im.load(), out.load()
    for y in range(im.height):
        for x in range(im.width):
            r, g, b = src[x, y]
            a = max(0, min(255, int((238 - (r + g + b) / 3) * 3.2)))
            dst[x, y] = (r, g, b, a)
    return out


class Layout:
    """The leaf's furniture, and where the column of text may go."""

    def __init__(self):
        band = Image.open(BAND_IMG).convert("RGB")
        self.band_w = PAGE_W - 2 * MARGIN
        self.scale = self.band_w / band.width
        self.band = band.resize(
            (self.band_w, round(band.height * self.scale)), Image.LANCZOS)
        self.x0 = MARGIN + PAD_X
        self.x1 = PAGE_W - MARGIN - PAD_X
        self.head_top = MARGIN + self.band.height + 34
        self.slim_top = MARGIN + 96
        self.bottom = PAGE_H - MARGIN - 40

    def column(self):
        return self.x1 - self.x0

    def body_top(self, first):
        return self.head_top if first else self.slim_top

    def pitch(self, first):
        return (self.bottom - self.body_top(first)) // LINES_PER_PAGE

    def leaf(self):
        """Blank parchment inside its ruled border."""
        im = Image.new("RGB", (PAGE_W, PAGE_H), PAPER)
        d = ImageDraw.Draw(im)
        d.rectangle([MARGIN - 8, MARGIN - 8, PAGE_W - MARGIN + 7,
                     PAGE_H - MARGIN + 7], outline=RULE, width=3)
        d.rectangle([MARGIN - 3, MARGIN - 3, PAGE_W - MARGIN + 2,
                     PAGE_H - MARGIN + 2], outline=RULE, width=1)
        return im

    def head(self, im, title):
        """The ornamental band, with the section's name in its cartouche."""
        im.paste(self.band, (MARGIN, MARGIN))
        x0, y0, x1, y1 = [round(v * self.scale) for v in BAND_PANEL]
        x0 += MARGIN; x1 += MARGIN; y0 += MARGIN; y1 += MARGIN
        d = ImageDraw.Draw(im)
        d.rectangle([x0, y0, x1, y1], fill=(253, 249, 235))
        size = y1 - y0 - 24
        while size > 8:
            font = ImageFont.truetype(FONT, size)
            w = d.textlength(title, font=font, direction="rtl")
            if w <= (x1 - x0) - 18:
                break
            size -= 1
        d.text(((x0 + x1) / 2 - w / 2, y0 + (y1 - y0 - size * 1.45) / 2),
               title, font=font, fill=INK, direction="rtl")

    def slim(self, im, title, leaf, leaves):
        """The running head: the section, and which of its leaves this is."""
        d = ImageDraw.Draw(im)
        text = f"{title}  |  {arabic(leaf)}  |  {arabic(leaves)}"
        font = ImageFont.truetype(FONT, 30)
        w = d.textlength(text, font=font, direction="rtl")
        y = MARGIN + 14
        d.rectangle([self.x1 - w - 20, y - 6, self.x1 + 6, y + 46],
                    outline=RULE, width=2)
        d.text((self.x1 - w - 7, y), text, font=font, fill=FAINT, direction="rtl")


def token_width(w, font, draw, orn_px):
    if w in ORNAMENTS:
        return float(orn_px)
    return draw.textlength(w, font=font, direction="rtl")


def wrap(words, font, draw, width, orn_px):
    space = draw.textlength(" ", font=font)
    lines, cur, cur_w = [], [], 0.0
    for w in words:
        ww = token_width(w, font, draw, orn_px)
        trial = cur_w + ww + (space if cur else 0)
        if trial <= width or not cur:
            cur.append(w)
            cur_w = trial
        else:
            lines.append(cur)
            cur, cur_w = [w], ww
    if cur:
        lines.append(cur)
    return lines


def render(text, out_prefix, title="", start_page=1):
    lay = Layout()
    rosette = load_ornament()
    probe = ImageDraw.Draw(Image.new("RGB", (8, 8)))
    words = text.split()

    # The number of lines to a page is fixed, so the type size follows from it
    # rather than the other way about.
    pitch = lay.pitch(first=True)
    size = max(8, int(pitch / LINE_RATIO))
    font = ImageFont.truetype(FONT, size)
    orn_px = int(size * ORNAMENT_RATIO)
    rose = rosette.resize((orn_px, orn_px), Image.LANCZOS)
    lines = wrap(words, font, probe, lay.column(), orn_px)

    # The first leaf gives room to the band, so it holds fewer lines than the
    # rest; count the leaves before drawing any, so the running head can say
    # how many there are.
    first_capacity = LINES_PER_PAGE
    rest_capacity = max(1, (lay.bottom - lay.slim_top) // pitch)
    leaves, i = [], 0
    while i < len(lines):
        cap = first_capacity if not leaves else rest_capacity
        leaves.append(lines[i:i + cap])
        i += cap

    made = []
    for n, chunk in enumerate(leaves):
        first = n == 0
        im = lay.leaf()
        if first:
            lay.head(im, title)
        else:
            lay.slim(im, title, n + 1, len(leaves))
        d = ImageDraw.Draw(im)
        top = lay.body_top(first)
        for i, ln in enumerate(chunk):
            y = top + i * pitch
            widths = [token_width(w, font, d, orn_px) for w in ln]
            space = d.textlength(" ", font=font)
            k = len(ln)
            last = (i == len(chunk) - 1 and n == len(leaves) - 1)
            if k > 1 and not last:
                gap = (lay.column() - sum(widths)) / (k - 1)
                x = lay.x1
            else:
                gap = space
                x = lay.x1 - (lay.column() - (sum(widths) + space * (k - 1))) / 2
            for w, ww in zip(ln, widths):
                if w in ORNAMENTS:
                    im.paste(rose, (int(x - ww), y + int(size * 0.30)), rose)
                else:
                    d.text((x - ww, y), w, font=font, fill=INK, direction="rtl")
                x -= ww + gap
        path = f"{out_prefix}_{start_page + n}.png"
        im.save(path)
        made.append(path)
    print(f"{size}px type, {len(lines)} lines over {len(made)} leaves")
    return made


if __name__ == "__main__":
    body = open(sys.argv[1], encoding="utf-8").read()
    render(body, sys.argv[2], sys.argv[3] if len(sys.argv) > 3 else "")
