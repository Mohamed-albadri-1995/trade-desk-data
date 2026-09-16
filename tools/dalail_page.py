#!/usr/bin/env python3
"""
Lays دلائل الرحمات out page by page, in the book's own hand.

The page is a leaf of parchment inside a thin ruled border, with an ornamental
band across its head carrying the section's name — the band, its cartouche and
its rosettes all cut from the book's own printed page, so nothing here is
borrowed from anywhere else. A section's first page wears the band; the pages
that carry it on wear a slim rule naming the section and the leaf you are on.

The lines stand the same distance apart on every page, wide enough that the
harakat of one never meet the letters of the line above, and every line but the
last of a passage is justified to the full column, so the pages come out even.
"""
import sys
from PIL import Image, ImageDraw, ImageFont

BAND_IMG = "dalail-app/design/header-band.png"
ORNAMENT_IMG = "dalail-app/design/ornament.png"
FONT = "ratib-app/app/src/main/res/font/amiri.ttf"
#: A second face, for the repetition counts. The book says how often a
#: passage is read with a figure; spelt out in words it would read as part
#: of the prayer, so it is set in a slanted naskh instead — plainly an
#: instruction to the reader rather than something to be recited.
ASIDE_FONT = "ratib-app/app/src/main/res/font/amiri_italic.ttf"

PAGE_W, PAGE_H = 1000, 1720
MARGIN = 16          # from the leaf's edge to the ruled border
PAD_X = 34           # from the border to the column
#: Lines on an ordinary leaf. A leaf that opens a section gives the ornamental
#: band its room and so holds fewer; the pitch is the same on both.
LINES_PER_PAGE = 14

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
#: How far apart the lines stand, as a multiple of the type size. Arabic set
#: with full harakat needs the room: a fatha or a damma rides well above the
#: letter it belongs to, and a kasra hangs below, so at a tighter leading than
#: this the marks of one line come up against the letters of the line over it.
LINE_RATIO = 1.90
ORNAMENT_RATIO = 0.74
#: Lines of the column the closing line takes up, so it is never crowded.
CLOSING_LINES = 3
#: How far from the nominal the type may be nudged to fill a section's last
#: leaf, in dots. Kept tight — a stouter squeeze would show when the reader
#: turns from one حزب to the next.
FIT_RANGE = (-4, 3)
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

    def pitch(self):
        """
        How far apart the lines stand, the same on every leaf.

        Set by the ordinary leaf, since that is nearly every leaf in the book
        and the one the reader has in front of them; the leaf that opens a
        section pays for its band by holding fewer lines at the same pitch,
        rather than by setting them closer together.
        """
        return (self.bottom - self.slim_top) // LINES_PER_PAGE

    def capacity(self, first):
        """How many lines a leaf holds — fewer where the band stands."""
        return (self.bottom - self.body_top(first)) // self.pitch()

    def border(self, im):
        """The leaf's ruled border: a stout rule with a fine one inside it."""
        d = ImageDraw.Draw(im)
        d.rectangle([MARGIN - 8, MARGIN - 8, PAGE_W - MARGIN + 7,
                     PAGE_H - MARGIN + 7], outline=RULE, width=3)
        d.rectangle([MARGIN - 3, MARGIN - 3, PAGE_W - MARGIN + 2,
                     PAGE_H - MARGIN + 2], outline=RULE, width=1)
        return im

    def leaf(self):
        """Blank parchment inside its ruled border."""
        return self.border(Image.new("RGB", (PAGE_W, PAGE_H), PAPER))

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

    def folio(self, im, number):
        """The leaf's number, small and centred at its foot."""
        d = ImageDraw.Draw(im)
        font = ImageFont.truetype(FONT, 26)
        text = arabic(number)
        w = d.textlength(text, font=font, direction="rtl")
        d.text((PAGE_W / 2 - w / 2, self.bottom + 5), text,
               font=font, fill=FAINT, direction="rtl")

    def closing(self, im, text):
        """
        The book's last line, standing alone at the foot of its last leaf.

        Set large and in the book's turquoise, the way the printed book ends.
        """
        d = ImageDraw.Draw(im)
        size = 64
        while size > 12:
            font = ImageFont.truetype(FONT, size)
            w = d.textlength(text, font=font, direction="rtl")
            if w <= self.column():
                break
            size -= 2
        d.text((PAGE_W / 2 - w / 2, self.bottom - size * 2.0), text,
               font=font, fill=TEAL, direction="rtl")


#: How a word is set: the book's own ink, the aside face for an instruction to
#: the reader, or the turquoise it gives revelation.
PLAIN, ASIDE, QURAN = 0, 1, 2


def tokenise(passage):
    """
    The passage's words, each marked for how it is set.

    A run between ⟨ and ⟩ is an instruction to the reader; a run between ﴿ and ﴾
    is Qur'an or a sura's name. Neither pair of markers is printed.

    A mark is looked for inside the word's punctuation, not only at its ends.
    The book closes صلاة ٢٣ with «… وَ زَانَتِ الدُّنَا (ثلاثًا)», bracket and
    all, so the token reads ⟨ثلاثًا⟩) — and while the closing mark was only
    ever sought at the very end of a word, the gold of the count ran on to
    everything after it, which is exactly what it must not do: the colour marks
    the count, never the صلاة.
    """
    out, mode = [], PLAIN
    for w in passage.split():
        bare = w.strip("()[]{}،؛,.!؟?")
        opened = mode
        if bare.startswith(ASIDE_OPEN):
            opened = ASIDE
        elif bare.startswith(QURAN_OPEN):
            opened = QURAN
        closes = bare.endswith(ASIDE_CLOSE) or bare.endswith(QURAN_CLOSE)
        out.append((w.replace(ASIDE_OPEN, "").replace(ASIDE_CLOSE, "")
                     .replace(QURAN_OPEN, "").replace(QURAN_CLOSE, ""), opened))
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


def break_lines(text, size, lay, probe):
    """
    The section's lines at the given type size, each marked for justification.

    Each line of the source is a passage of its own, and begins a line of its
    own. A passage's lines are justified to the column — all but its last,
    which is centred. So a passage that is one line long, البسملة among them,
    stands centred and alone, as the book sets it.

    Also hands back where each passage begins, so an index can say which leaf
    a صلاة is on.
    """
    fonts = {False: ImageFont.truetype(FONT, size),
             True: ImageFont.truetype(ASIDE_FONT, size)}
    orn_px = int(size * ORNAMENT_RATIO)
    lines, starts = [], []
    for passage in text.splitlines():
        passage = strip_marker(passage)
        if not passage.strip():
            continue
        starts.append(len(lines))
        got = wrap(tokenise(passage), fonts, probe, lay.column(), orn_px)
        for i, ln in enumerate(got):
            lines.append((ln, i < len(got) - 1))
    return lines, fonts, orn_px, starts


def deal(lines, lay, pitch, closing):
    """
    The lines dealt out into leaves.

    The first leaf gives room to the ornamental band, so it holds fewer lines
    than those after it, and the closing line — where there is one — keeps a
    few lines' room of its own at the foot of the last leaf.
    """
    first_cap = max(1, lay.capacity(first=True))
    rest_cap = max(1, lay.capacity(first=False))
    leaves, i = [], 0
    while i < len(lines):
        cap = first_cap if not leaves else rest_cap
        leaves.append(lines[i:i + cap])
        i += cap
    if not leaves:
        leaves = [[]]

    if closing:
        room = max(1, (rest_cap if len(leaves) > 1 else first_cap)
                   - CLOSING_LINES)
        if len(leaves[-1]) > room:
            leaves.append(leaves[-1][room:])
            leaves[-2] = leaves[-2][:room]
    return leaves, first_cap, rest_cap


def fit(text, lay, pitch, closing, probe):
    """
    The type size that leaves the section's last leaf as full as it can be.

    A section always opens on a leaf of its own, so whatever its last leaf
    cannot fill is left blank — and at one size or another that came out as a
    single line stranded on an empty page. The size is therefore chosen for the
    section rather than fixed for the book: within a hair's breadth of the
    nominal, the one that fills the last leaf best wins, and the nominal wins
    any tie, so the type stays even to the eye throughout.
    """
    nominal = max(8, int(pitch / LINE_RATIO))
    best = None
    for size in range(nominal + FIT_RANGE[0], nominal + FIT_RANGE[1] + 1):
        if size < 8:
            continue
        lines, _, _, _ = break_lines(text, size, lay, probe)
        leaves, first_cap, rest_cap = deal(lines, lay, pitch, closing)
        cap = (rest_cap if len(leaves) > 1 else first_cap) \
            - (CLOSING_LINES if closing else 0)
        fill = 1.0 if len(leaves) == 1 else len(leaves[-1]) / max(1, cap)
        score = (round(fill, 3), -abs(size - nominal))
        if best is None or score > best[0]:
            best = (score, size)
    return best[1]


def locate(text, closing=None):
    """
    Which leaf of the section each of its passages begins on, from zero.

    The type size and the dealing out into leaves are chosen exactly as
    compose() chooses them, so an index built from this points at the leaf the
    reader will actually turn to.
    """
    lay = Layout()
    probe = ImageDraw.Draw(Image.new("RGB", (8, 8)))
    pitch = lay.pitch()
    size = fit(text, lay, pitch, closing, probe)
    lines, _, _, starts = break_lines(text, size, lay, probe)
    leaves, _, _ = deal(lines, lay, pitch, closing)

    first_line, at = [], 0
    for leaf in leaves:
        first_line.append(at)
        at += len(leaf)

    def leaf_of(line):
        found = 0
        for n, begins in enumerate(first_line):
            if line >= begins:
                found = n
        return found

    return [leaf_of(s) for s in starts], len(leaves)


def compose(text, title="", folio=None, closing=None):
    """
    The section's leaves, drawn and returned as images.

    `folio`, when given, is the number to print at the foot of the first leaf;
    the leaves after it count on from there. `closing` is a last line to stand
    alone at the foot of the final leaf — the book's own خاتمة.
    """
    lay = Layout()
    rosette = load_ornament()
    probe = ImageDraw.Draw(Image.new("RGB", (8, 8)))

    # The number of lines to a page is fixed, so the type size follows from it
    # rather than the other way about — and is then nudged, within a hair, to
    # fill the section's last leaf.
    pitch = lay.pitch()
    size = fit(text, lay, pitch, closing, probe)
    lines, fonts, orn_px, _ = break_lines(text, size, lay, probe)
    rose = rosette.resize((orn_px, orn_px), Image.LANCZOS)
    leaves, _, _ = deal(lines, lay, pitch, closing)

    made = []
    for n, chunk in enumerate(leaves):
        first = n == 0
        im = lay.leaf()
        if first:
            lay.head(im, title)
        else:
            lay.slim(im, title, n + 1, len(leaves))
        if folio is not None:
            lay.folio(im, folio + n)
        if closing and n == len(leaves) - 1:
            lay.closing(im, closing)
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
        made.append(im)
    return made


def render(text, out_prefix, title="", start_page=1):
    """Lays a section out and writes its leaves beside one another as PNGs."""
    made = []
    for n, im in enumerate(compose(text, title)):
        path = f"{out_prefix}_{start_page + n}.png"
        im.save(path)
        made.append(path)
    print(f"{len(made)} leaves")
    return made


if __name__ == "__main__":
    body = open(sys.argv[1], encoding="utf-8").read()
    render(body, sys.argv[2], sys.argv[3] if len(sys.argv) > 3 else "")
