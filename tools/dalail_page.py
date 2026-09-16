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
#: A second face, for the repetition counts. The book says how often a
#: passage is read with a figure; spelt out in words it would read as part
#: of the prayer, so it is set in a slanted naskh instead — plainly an
#: instruction to the reader rather than something to be recited.
ASIDE_FONT = "ratib-app/app/src/main/res/font/amiri_italic.ttf"

PAGE_W, PAGE_H = 1000, 1720
MARGIN = 16          # from the leaf's edge to the ruled border
PAD_X = 34           # from the border to the column
LINES_PER_PAGE = 15

PAPER = (252, 247, 231)
INK = (36, 26, 11)
RULE = (150, 118, 46)
#: The book sets the Qur'anic verses and the sura names in a turquoise of
#: its own. Its exact value, sampled off the scan, is (45, 230, 205) — true
#: on the white it was printed on, but too pale to read on parchment, so it
#: is deepened while keeping the hue.
TEAL = (17, 138, 124)
FAINT = (120, 96, 44)

#: Where the cartouche sits inside the band, in the band image's own pixels.
BAND_PANEL = (302, 70, 518, 148)

ORNAMENTS = ("۞", "♡", "♥", "❤")
#: Marks a run set in the aside face. Stripped before drawing.
ASIDE_OPEN, ASIDE_CLOSE = "⟨", "⟩"
#: Marks a Qur'anic verse or a sura's name. Stripped before drawing — the
#: book sets these off by colour, not by brackets.
QURAN_OPEN, QURAN_CLOSE = "﴿", "﴾"
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


#: How a word is set: the book's own ink, the aside face for an instruction to
#: the reader, or the turquoise it gives revelation.
PLAIN, ASIDE, QURAN = 0, 1, 2


def tokenise(passage):
    """
    The passage's words, each marked for how it is set.

    A run between ⟨ and ⟩ is an instruction to the reader; a run between ﴿ and ﴾
    is Qur'an or a sura's name. Neither pair of markers is printed.
    """
    out, mode = [], PLAIN
    for w in passage.split():
        opened = mode
        if w.startswith(ASIDE_OPEN):
            opened = ASIDE
        elif w.startswith(QURAN_OPEN):
            opened = QURAN
        closes = w.endswith(ASIDE_CLOSE) or w.endswith(QURAN_CLOSE)
        w = w.strip(ASIDE_OPEN + ASIDE_CLOSE + QURAN_OPEN + QURAN_CLOSE)
        out.append((w, opened))
        mode = PLAIN if closes else opened
    return out


#: The transcription marks a heading with «# » or «## » and the book's closing
#: line with «~ », the way the reader itself reads them. Both stand alone on
#: their line, so here the marker is dropped and the line comes out centred of
#: its own accord.
MARKERS = ("## ", "# ", "~ ", "* ")


def strip_marker(passage):
    for m in MARKERS:
        if passage.startswith(m):
            return passage[len(m):]
    return passage


def face(fonts, mode):
    return fonts[True] if mode == ASIDE else fonts[False]


def colour(mode):
    return {ASIDE: RULE, QURAN: TEAL}.get(mode, INK)


def token_width(w, mode, fonts, draw, orn_px):
    if w in ORNAMENTS:
        return float(orn_px)
    return draw.textlength(w, font=face(fonts, mode), direction="rtl")


def wrap(words, fonts, draw, width, orn_px):
    space = draw.textlength(" ", font=fonts[False])
    lines, cur, cur_w = [], [], 0.0
    for w, mode in words:
        ww = token_width(w, mode, fonts, draw, orn_px)
        trial = cur_w + ww + (space if cur else 0)
        if trial <= width or not cur:
            cur.append((w, mode))
            cur_w = trial
        else:
            lines.append(cur)
            cur, cur_w = [(w, mode)], ww
    if cur:
        lines.append(cur)
    return lines


def render(text, out_prefix, title="", start_page=1):
    lay = Layout()
    rosette = load_ornament()
    probe = ImageDraw.Draw(Image.new("RGB", (8, 8)))

    # The number of lines to a page is fixed, so the type size follows from it
    # rather than the other way about.
    pitch = lay.pitch(first=True)
    size = max(8, int(pitch / LINE_RATIO))
    fonts = {False: ImageFont.truetype(FONT, size),
             True: ImageFont.truetype(ASIDE_FONT, size)}
    orn_px = int(size * ORNAMENT_RATIO)
    rose = rosette.resize((orn_px, orn_px), Image.LANCZOS)

    # Each line of the source is a passage of its own, and begins a line of its
    # own. A passage's lines are justified to the column — all but its last,
    # which is centred. So a passage that is one line long, البسملة among them,
    # stands centred and alone, as the book sets it.
    lines = []
    for passage in text.splitlines():
        passage = strip_marker(passage)
        if not passage.strip():
            continue
        got = wrap(tokenise(passage), fonts, probe, lay.column(), orn_px)
        for i, ln in enumerate(got):
            lines.append((ln, i < len(got) - 1))

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
            ln, justify = ln
            widths = [token_width(w, m, fonts, d, orn_px) for w, m in ln]
            space = d.textlength(" ", font=fonts[False])
            k = len(ln)
            if k > 1 and justify:
                gap = (lay.column() - sum(widths)) / (k - 1)
                x = lay.x1
            else:
                gap = space
                x = lay.x1 - (lay.column() - (sum(widths) + space * (k - 1))) / 2
            for (w, mode), ww in zip(ln, widths):
                if w in ORNAMENTS:
                    im.paste(rose, (int(x - ww), y + int(size * 0.30)), rose)
                else:
                    d.text((x - ww, y), w, font=face(fonts, mode),
                           fill=colour(mode), direction="rtl")
                x -= ww + gap
        path = f"{out_prefix}_{start_page + n}.png"
        im.save(path)
        made.append(path)
    print(f"{size}px type, {len(lines)} lines over {len(made)} leaves")
    return made


if __name__ == "__main__":
    body = open(sys.argv[1], encoding="utf-8").read()
    render(body, sys.argv[2], sys.argv[3] if len(sys.argv) > 3 else "")
