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

from PIL import Image, ImageDraw

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import dalail_page as page
import pdf_out

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "dalail")
COVER = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..",
                     "dalail-app", "design", "cover-plate.jpg")

#: The photographs, one to a leaf, at the book's end.
PLATES = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..",
                      "dalail-app", "design", "plates")

#: What the plates are gathered under. A plain word: the author has not said
#: who stands in each photograph, so nothing is claimed here that he has not.
PLATES_TITLE = "صُوَرٌ"

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


#: How much of the leaf a photograph may take, inside the ruled border.
PLATE_PAD = 26


def plates(folio):
    """
    The photographs, one to a leaf, each in a ruled frame on the parchment.

    They stand at the end, after the book is closed with تم بحمد الله, so that
    nothing comes between the reader and the صلوات. Each is fitted whole
    inside its frame rather than cropped to fill it — a face is not a
    background — and the parchment shows around whichever side is short.
    """
    files = sorted(glob.glob(os.path.join(PLATES, "*.jpg")))
    if not files:
        return []

    lay = page.Layout()
    out = []
    for i, path in enumerate(files):
        im = lay.leaf()
        first = i == 0
        if first:
            lay.head(im, PLATES_TITLE)
        else:
            lay.slim(im, PLATES_TITLE, i + 1, len(files))
        top = lay.body_top(first) + PLATE_PAD
        room_w = lay.column() - 2 * PLATE_PAD
        room_h = lay.bottom - top - PLATE_PAD

        art = Image.open(path).convert("RGB")
        scale = min(room_w / art.width, room_h / art.height)
        art = art.resize((round(art.width * scale), round(art.height * scale)),
                         Image.LANCZOS)
        x = (page.PAGE_W - art.width) // 2
        y = top + (room_h - art.height) // 2
        im.paste(art, (x, y))
        ImageDraw.Draw(im).rectangle(
            [x - 3, y - 3, x + art.width + 2, y + art.height + 2],
            outline=page.RULE, width=2)

        lay.folio(im, folio + i)
        out.append(im)
    return out


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
        where, leaves = page.locate(text, closing)
        passages = [ln for ln in text.splitlines() if page.strip_marker(ln).strip()]
        for n, passage in enumerate(passages):
            hit = SALAT.match(passage.strip())
            if hit:
                suras = "، ".join(sura_names(passages[n + 1])) if n + 1 < len(passages) else ""
                label = f"{hit.group(1)}  ·  {suras}" if suras else hit.group(1)
                out.append(f"{page_no + where[n]}|1|{label}")
        page_no += leaves
    if glob.glob(os.path.join(PLATES, "*.jpg")):
        out.append(f"{page_no}|0|{bare(PLATES_TITLE)}")
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
        made = page.compose(text, title, folio=folio, closing=closing)
        print(f"{len(made):3} ورقة   {title}")
        leaves += made
        folio += len(made)

    made = plates(folio)
    if made:
        print(f"{len(made):3} ورقة   {PLATES_TITLE}")
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


if __name__ == "__main__":
    build(sys.argv[1] if len(sys.argv) > 1 else "dalail-al-rahamat.pdf")
