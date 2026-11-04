#!/usr/bin/env python3
"""
Binds دلائل الرحمات into one PDF.

The book runs cover, مقدمة, أسماء الله الحسنى and its دعوة, دعاء النية, then
the seven أحزاب — one for each day of the week, ninety صلوات between them,
one for every sura — and closes with دعوة الصلوات. Every section opens on a
leaf wearing the ornamental band with its name in the cartouche; the leaves
that carry it on wear the slim running head. The leaves are numbered through
the book, the cover apart.

    python3 tools/dalail_book.py دلائل-الرحمات.pdf
"""
import glob
import re
import os
import sys

from PIL import Image, ImageDraw, ImageFont

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import dalail_page as page
import pdf_out

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "dalail")
COVER = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..",
                     "dalail-app", "design", "cover-plate.jpg")

#: The leaf that names the order: whose ratib this is, and where it is kept.
#: The first line stands in the band's cartouche, as a section's name does, and
#: the rest are set beneath it. That first line is also the leaf's name where
#: sections() puts it in order, the way QASIDA_TITLE names the verse.
COLOPHON = (
    "الطَّرِيقَةُ القَادِرِيَّةُ العَرَكِيَّةُ",
    "سَجَّادَةُ أَبُونَا وَشَيْخِنَا",
    "الشَّيْخُ الفَضْلُ الشَّيْخُ مُحَمَّدُ بْنُ يُونُسَ",
    "الفَاوُ – القَرْيَةُ (٥)",
)

#: The three lines below the band: their size, and the colour they wear.
COLOPHON_LINES = ((44, "TEAL"), (58, "INK"), (40, "FAINT"))

#: How wide the printed leaf is meant to be, in dots to the inch. The design is
#: 1000 × 1720 dots, so at this resolution the leaf comes out 127 × 218 mm —
#: the slim, tall shape the book is printed in.
DPI = 200

#: A rosette between two passages that the printed book parts with a rule.
DIVIDER = "۞ ۞ ۞"

DAYS = ["الأَثْنَيْنِ", "الثُّلَاثَاءِ", "الأَربِعَاءِ", "الخَمِيسِ",
        "الجُمْعَةِ", "السَّبْتِ", "الأَحَدِ"]
ORDINALS = ["الأَوَّلُ", "الثَّانِي", "الثَّالِثُ", "الرَّابِعُ",
            "الخَامِسُ", "السَّادِسُ", "السَّابِعُ"]
ARABIC_DIGITS = "٠١٢٣٤٥٦٧٨٩"


def arabic(n):
    return "".join(ARABIC_DIGITS[int(d)] for d in str(n))


def read(*names):
    """The named files, each a passage of its own, in the order given."""
    out = []
    for name in names:
        with open(os.path.join(ROOT, name), encoding="utf-8") as f:
            out.append(f.read().strip())
    return "\n".join(out)


def hizb(n):
    """
    A حزب's صلوات, each headed by its number in the aside face.

    The number is an instruction to the reader, not part of the prayer, so it
    is set in the same slanted face the repetition counts wear.
    """
    out = []
    folder = os.path.join(ROOT, f"hizb{n}")
    for path in sorted(glob.glob(os.path.join(folder, "*.txt"))):
        number = int(os.path.basename(path)[:3])
        body = " ".join(open(path, encoding="utf-8").read().split())
        out.append(f"⟨{arabic(number)}⟩")
        out.append(body)
    return "\n".join(out)


def sections():
    """The book in order: each section's name, its text, and its last line."""
    out = [
        ("((مُقَدِّمَة))", read("p03.txt", "p04.txt"), None),
        # Verse in praise of the book and its reading. Each hemistich is a
        # passage of its own, which is what sets it centred rather than
        # stretched across the column as prose is.
        (QASIDA_TITLE, read("qasida.txt"), None),
        ("أَسْمَاءُ اللهِ الحُسْنَى", read("p05.txt"), None),
        # The two short صلوات that follow the دعوة carry no name of their own in
        # the printed book — a rule parts them, and a rosette does so here.
        ("دَعْوَةُ أَسْمَاءِ اللهِ الحُسْنَى",
         read("p06.txt") + f"\n{DIVIDER}\n" + read("p07a.txt")
         + f"\n{DIVIDER}\n" + read("p07b.txt"), None),
        ("دُعَاءَ النِّيَّةِ", read("p07c.txt"), None),
    ]
    for i in range(7):
        out.append((f"الحِزْبُ {ORDINALS[i]} * يَوْمَ {DAYS[i]}",
                    hizb(i + 1), None))

    # دعوة الصلوات keeps its name on its own first line and the book's closing
    # words on its last, marked as the reader reads them.
    lines = read("dawat_salawat.txt").splitlines()
    title = page.strip_marker(lines[0])
    closing = page.strip_marker(lines[-1])
    out.append((title, "\n".join(lines[1:-1]), closing))

    # The book closed, the leaf that names the order and the shaykh whose
    # ratib it is — and the shaykh's lineage after it, by the author's order.
    out.append((COLOPHON[0], None, None))
    out.append(("نَسَبُهُ الشَّرِيفُ", read("nasab.txt"), None))
    return out


def cover():
    """
    The cover, filling the leaf inside the gold border.

    The artwork is a finished design — title, author and all — rather than a
    photograph to be cut about, so it is scaled to cover the whole of the
    inside of the border and what overhangs is trimmed from the sides, where
    the design carries only its background. Fitting it by width instead would
    leave bands of bare colour above and below a design that was not made to
    have any.
    """
    art = Image.open(COVER).convert("RGB")
    inner_w = page.PAGE_W - 2 * (page.MARGIN + 8)
    inner_h = page.PAGE_H - 2 * (page.MARGIN + 8)
    scale = max(inner_w / art.width, inner_h / art.height)
    art = art.resize((round(art.width * scale), round(art.height * scale)),
                     Image.LANCZOS)
    left = (art.width - inner_w) // 2
    top = (art.height - inner_h) // 2
    art = art.crop((left, top, left + inner_w, top + inner_h))

    im = Image.new("RGB", (page.PAGE_W, page.PAGE_H), page.PAPER)
    im.paste(art, (page.MARGIN + 8, page.MARGIN + 8))
    page.Layout().border(im)
    return im


#: The verse in praise of the book, set whole on one leaf: fourteen hemistichs
#: in seven couplets, a hair's rule between each pair as the copy he sent has.
QASIDA_TITLE = "قَصَائِدُ"

#: From the gilt frame's rule in to the type. The verse is set wider than the
#: book's prose column because the frame does the work the margin does.
QASIDA_PAD = 24
#: Clear air between a couplet's ink and the rule that closes its panel, so
#: neither the harakat above a line nor the kasra below one meets the rule.
QASIDA_CLEAR = 15
#: The red of the medallions on the border, for the lozenge that sits on each
#: rule between one couplet and the next.
LOZENGE = (152, 38, 32)


def lozenge(d, cx, cy, half):
    """A small diamond, gold-edged over red, as an illuminated rule carries."""
    d.polygon([(cx, cy - half), (cx + half, cy), (cx, cy + half),
               (cx - half, cy)], fill=LOZENGE, outline=page.RULE)
    d.polygon([(cx, cy - half // 2), (cx + half // 2, cy),
               (cx, cy + half // 2), (cx - half // 2, cy)], outline=page.RULE)


def poems():
    """
    The verse, poem by poem.

    A blank line parts one poem from the next, and a line opening with «# » is
    that poem's own name, set in the first panel of its leaf as the copies the
    author sent have it. The first poem carries no name of its own: the band
    over it names the section.
    """
    out, cur = [], {"title": None, "lines": []}
    for raw in open(os.path.join(ROOT, "qasida.txt"), encoding="utf-8"):
        line = raw.strip()
        if not line:
            if cur["lines"]:
                out.append(cur)
            cur = {"title": None, "lines": []}
        elif line.startswith("# "):
            cur["title"] = line[2:].strip()
        else:
            cur["lines"].append(line)
    if cur["lines"]:
        out.append(cur)
    return out


def qasida(folio):
    """
    The verse: a leaf to each poem, every couplet in a panel of its own.

    Set here rather than through compose for two reasons. Fourteen hemistichs
    at the book's own pitch run two lines past a leaf that carries the band,
    and two lines alone on a leaf of their own is the thing he objected to in
    the first place; and verse in this book is to be ruled the way the copy he
    sent is ruled — a gilt frame about the whole, every couplet in a panel
    under it, and a rule between panels with a lozenge set in the middle of it.

    The panels divide the frame evenly, so the last one closes on the frame's
    own foot rather than leaving it short, and the type comes down until both
    the widest hemistich fits a panel's width and a couplet fits its height.

    A poem's own name, where it has one, takes the first panel by itself, as
    the copies the author sent have it.
    """
    all_poems = poems()
    made = []
    for n, poem in enumerate(all_poems):
        lines = poem["lines"]
        # A panel holds one line where it holds a name, two where a couplet.
        panels = ([[poem["title"]]] if poem["title"] else []) \
            + [lines[i:i + 2] for i in range(0, len(lines), 2)]

        lay = page.Layout()
        im = lay.leaf()
        if n:
            lay.slim(im, QASIDA_TITLE, n + 1, len(all_poems))
        else:
            lay.head(im, QASIDA_TITLE)
        d = ImageDraw.Draw(im)

        x0, x1 = lay.x0 - 12, lay.x1 + 12
        y0, y1 = lay.body_top(not n), lay.bottom
        cell = (y1 - y0) / len(panels)
        room = (x1 - x0) - 2 * QASIDA_PAD

        # Fitted on the ink itself, not on the font's nominal height. A
        # hemistich here carries harakat above and a kasra below, so what a
        # line occupies is half again what the type size says; measured by the
        # size alone, the marks of the lower line came up against the rule over
        # it and against the lozenge set on it.
        size = 56
        while size > 12:
            font = ImageFont.truetype(page.FONT, size)
            boxes = [[d.textbbox((0, 0), s, font=font, direction="rtl")
                      for s in panel] for panel in panels]
            widest = max(b[2] - b[0] for panel in boxes for b in panel)
            step = size * 1.74
            # From the top of a panel's first line to the foot of its last,
            # each line being centred on its own place.
            tall = max(step * (len(p) - 1) + (p[0][3] - p[0][1]) / 2
                       + (p[-1][3] - p[-1][1]) / 2 for p in boxes)
            if widest <= room and tall + 2 * QASIDA_CLEAR <= cell:
                break
            size -= 1

        # The frame: a stout rule with a fine one inside it, as the leaf's
        # border is drawn, so the verse belongs to the same hand as the page.
        d.rectangle([x0, y0, x1, y1], outline=page.RULE, width=3)
        d.rectangle([x0 + 6, y0 + 6, x1 - 6, y1 - 6], outline=page.RULE, width=1)

        for k, panel in enumerate(panels):
            middle = y0 + cell * (k + 0.5)
            for j, hemistich in enumerate(panel):
                box = d.textbbox((0, 0), hemistich, font=font, direction="rtl")
                y = middle + (j - (len(panel) - 1) / 2) * step
                d.text((page.PAGE_W / 2 - (box[2] - box[0]) / 2 - box[0],
                        y - (box[3] - box[1]) / 2 - box[1]),
                       hemistich, font=font, fill=page.INK, direction="rtl")
            if k:
                rule = y0 + cell * k
                d.line([x0 + 7, rule, x1 - 7, rule], fill=page.RULE, width=1)
                lozenge(d, page.PAGE_W / 2, rule, 11)

        lay.folio(im, folio + n)
        made.append(im)
    return made


def colophon(folio):
    """
    The leaf that closes the book, naming the order and the shaykh.

    It stands last, after تم بحمد الله, and wears the book's own furniture: the
    band with the order's name in its cartouche, and the rest set in the middle
    of the leaf beneath it, each line brought down until it fits the column.
    """
    lay = page.Layout()
    im = lay.leaf()
    lay.head(im, COLOPHON[0])
    d = ImageDraw.Draw(im)

    drawn = []
    for text, (size, tone) in zip(COLOPHON[1:], COLOPHON_LINES):
        while size > 10:
            font = ImageFont.truetype(page.FONT, size)
            box = d.textbbox((0, 0), text, font=font, direction="rtl")
            if box[2] - box[0] <= lay.column():
                break
            size -= 1
        drawn.append((text, font, box, getattr(page, tone)))

    gap = 46
    tall = sum(b[3] - b[1] for _, _, b, _ in drawn) + gap * (len(drawn) - 1)
    top = lay.body_top(True)
    y = top + (lay.bottom - top - tall) / 2
    for text, font, box, tone in drawn:
        d.text((page.PAGE_W / 2 - (box[2] - box[0]) / 2 - box[0], y - box[1]),
               text, font=font, fill=tone, direction="rtl")
        y += (box[3] - box[1]) + gap

    lay.folio(im, folio)
    return [im]


#: Where the app keeps its copy of the book, and the index into it.
APP = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "dalail-app",
                   "app", "src", "main", "assets")

#: A صلاة's number, as hizb() writes it into the text — its own passage, in the
#: face the instructions to the reader wear.
SALAT = re.compile(r"^⟨([٠-٩]+)⟩$")


def index():
    """
    Every section and every صلاة, with the PDF page it begins on.

    One line per entry: `page|depth|label`, the page counting the cover as 1,
    the depth 0 for a section and 1 for a صلاة under it.
    """
    out = []
    page_no = 2          # the cover is page 1
    for title, text, closing in sections():
        out.append(f"{page_no}|0|{bare(title)}")
        if title in (QASIDA_TITLE, COLOPHON[0]):
            # Drawn whole by a renderer of its own and holding no صلاة to index
            # under it: the colophon is one leaf, the verse a leaf to a poem.
            page_no += len(poems()) if title == QASIDA_TITLE else 1
            continue
        where, leaves = page.locate(text, closing)
        passages = [ln for ln in text.splitlines() if page.strip_marker(ln).strip()]
        for n, passage in enumerate(passages):
            hit = SALAT.match(passage.strip())
            if hit:
                suras = "، ".join(sura_names(passages[n + 1])) if n + 1 < len(passages) else ""
                label = f"{hit.group(1)}  ·  {suras}" if suras else hit.group(1)
                out.append(f"{page_no + where[n]}|1|{label}")
        page_no += leaves
    return out


#: Harakat, stripped so an index entry reads as plainly as a table of contents.
HARAKAT = re.compile(r"[ً-ْٰـ]")
SURA = re.compile(r"﴿\s*سُورَةِ?َ?\s+([^﴾]+?)\s*﴾")


def bare(text):
    return HARAKAT.sub("", text).strip()


def sura_names(body):
    """The suras a صلاة names, bare, in the order it names them."""
    seen, out = set(), []
    for name in SURA.findall(body):
        one = bare(name)
        if one and one not in seen:
            seen.add(one)
            out.append(one)
    return out


#: How the app stores a leaf. The leaf is drawn a thousand dots wide, which is
#: about what a phone shows it at, and WebP holds the parchment and the gold at
#: this quality without a mark on the type.
LEAF_QUALITY = 85


def build(out_path):
    leaves = [cover()]
    folio = 1
    for title, text, closing in sections():
        if title == QASIDA_TITLE:
            made = qasida(folio)
        elif title == COLOPHON[0]:
            made = colophon(folio)
        else:
            made = page.compose(text, title, folio=folio, closing=closing)
        print(f"{len(made):3} ورقة   {title}")
        leaves += made
        folio += len(made)

    pdf_out.save(leaves, out_path, dpi=DPI)
    mb = os.path.getsize(out_path) / 1e6
    print(f"\n{len(leaves)} صفحة (الغلاف منها) — {mb:.1f} م.ب — {out_path}")

    # The app reads the very leaves the PDF is made of, one image each, rather
    # than the PDF itself: it can then turn them right to left the way the book
    # is turned, and it carries no PDF engine to do it.
    pages = os.path.join(APP, "pages")
    if os.path.isdir(pages):
        for old in glob.glob(os.path.join(pages, "*")):
            os.remove(old)
    os.makedirs(pages, exist_ok=True)
    total = 0
    for n, im in enumerate(leaves, start=1):
        path = os.path.join(pages, f"p{n:03d}.webp")
        im.save(path, "WEBP", quality=LEAF_QUALITY, method=6)
        total += os.path.getsize(path)
    print(f"{len(leaves)} ورقة صورةً — {total/1e6:.1f} م.ب — {os.path.relpath(pages)}")

    entries = index()
    with open(os.path.join(APP, "index.txt"), "w", encoding="utf-8") as f:
        f.write("\n".join(entries) + "\n")
    print(f"{len(entries)} مدخلًا في الفهرس — {os.path.relpath(APP)}")

    # How many leaves there are, written down rather than counted at runtime.
    # The app used to learn it by asking the system to list the assets folder,
    # and when that came back empty — which it may, on some phones — the book
    # had no leaves and opened on a blank screen.
    with open(os.path.join(APP, "pages.txt"), "w", encoding="utf-8") as f:
        f.write(f"{len(leaves)}\n")


if __name__ == "__main__":
    # Nothing is drawn from a text that has not been held to his corrections
    # and to the Qur'an's own spelling. The checks were being run by hand and
    # by memory, which is how an order of his was once overturned and how a
    # verse was once regularised; they are the first thing now, and a leaf
    # cannot be made without them. Pass --anyway to look at a draft in spite
    # of them.
    import subprocess
    if "--anyway" not in sys.argv:
        check = os.path.join(os.path.dirname(os.path.abspath(__file__)),
                             "dalail_check.py")
        if subprocess.run([sys.executable, check]).returncode:
            print("\n✗ لم يُبنَ الكتاب: أصلِحْ ما سبق، أو مرِّرْ --anyway لمسوّدة.",
                  file=sys.stderr)
            sys.exit(1)
        print()
    build(next((a for a in sys.argv[1:] if not a.startswith("-")),
               "dalail-al-rahamat.pdf"))
