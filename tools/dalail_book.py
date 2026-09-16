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
import os
import sys

from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import dalail_page as page
import pdf_out

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "dalail")
COVER = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..",
                     "dalail-app", "design", "cover.jpg")

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


#: The front board's corners in the cover artwork, which shows the whole bound
#: book — spine and all — and so has to be cut down to its front.
FRONT_BOARD = (148, 8, 946, 1272)


def cover():
    """
    The front board, filling the page inside the leaves' own gold border.

    The artwork is a photograph of the bound book, so the spine is cut away and
    what is left stands on a ground of the boards' own dark brown, sampled from
    the artwork rather than guessed at.
    """
    art = Image.open(COVER).convert("RGB").crop(FRONT_BOARD)
    inner = page.PAGE_W - 2 * (page.MARGIN + 8)
    art = art.resize((inner, round(art.height * inner / art.width)),
                     Image.LANCZOS)

    ground = art.crop((0, 0, art.width, 4)).resize((1, 1), Image.BOX).getpixel((0, 0))
    im = Image.new("RGB", (page.PAGE_W, page.PAGE_H), ground)
    im.paste(art, ((page.PAGE_W - art.width) // 2,
                   (page.PAGE_H - art.height) // 2))
    page.Layout().border(im)
    return im


def build(out_path):
    leaves = [cover()]
    folio = 1
    for title, text, closing in sections():
        made = page.compose(text, title, folio=folio, closing=closing)
        print(f"{len(made):3} ورقة   {title}")
        leaves += made
        folio += len(made)

    pdf_out.save(leaves, out_path, dpi=DPI)
    mb = os.path.getsize(out_path) / 1e6
    print(f"\n{len(leaves)} صفحة (الغلاف منها) — {mb:.1f} م.ب — {out_path}")


if __name__ == "__main__":
    build(sys.argv[1] if len(sys.argv) > 1 else "dalail-al-rahamat.pdf")
