#!/usr/bin/env python3
"""
Writes a list of images out as a PDF, losslessly.

Pillow's own PDF writer re-encodes every page as a JPEG, which leaves a haze
around Arabic letters and their diacritics — the very thing a book of prayers
can least afford. The leaves here are flat parchment with two or three inks on
it, so an indexed palette and plain deflate hold them exactly as drawn, and in
less room than the JPEG took.

Nothing outside the standard library and Pillow is needed.
"""
import zlib

from PIL import Image

#: Points to the inch, the unit PDF measures a page in.
POINTS_PER_INCH = 72


def _palette(im):
    """The image's colours as a PDF palette, and its indexed pixels."""
    pal = im.getpalette() or []
    used = max(im.getdata()) + 1
    pal = pal[: used * 3]
    pal += [0] * (used * 3 - len(pal))
    return bytes(pal), im.tobytes()


def save(images, path, dpi=200):
    """Writes the images, one to a page, at the given dots to the inch."""
    objects = []          # object number 1 is /Pages, filled in at the end

    def add(body):
        objects.append(body)
        return len(objects) + 1

    pages = []
    for im in images:
        if im.mode != "P":
            im = im.convert("P", palette=Image.ADAPTIVE, colors=256)
        pal, pixels = _palette(im)
        data = zlib.compress(pixels, 9)
        image = add(
            b"<< /Type /XObject /Subtype /Image /Width %d /Height %d "
            b"/ColorSpace [/Indexed /DeviceRGB %d <%s>] /BitsPerComponent 8 "
            b"/Filter /FlateDecode /Length %d >>\nstream\n%s\nendstream"
            % (im.width, im.height, len(pal) // 3 - 1, pal.hex().encode(),
               len(data), data))

        w = im.width * POINTS_PER_INCH / dpi
        h = im.height * POINTS_PER_INCH / dpi
        draw = b"q %.2f 0 0 %.2f 0 0 cm /Im Do Q" % (w, h)
        content = add(b"<< /Length %d >>\nstream\n%s\nendstream"
                      % (len(draw), draw))
        pages.append(add(
            b"<< /Type /Page /Parent 1 0 R /MediaBox [0 0 %.2f %.2f] "
            b"/Resources << /XObject << /Im %d 0 R >> >> /Contents %d 0 R >>"
            % (w, h, image, content)))

    kids = b" ".join(b"%d 0 R" % n for n in pages)
    tree = (b"<< /Type /Pages /Count %d /Kids [%s] >>" % (len(pages), kids))
    catalog = add(b"<< /Type /Catalog /Pages 1 0 R >>")

    # Object 1 is the page tree, which only now knows all its children, so the
    # bodies are laid out with it first and the rest shifted up by one.
    bodies = [tree] + objects

    out = bytearray(b"%PDF-1.4\n%\xe2\xe3\xcf\xd3\n")
    offsets = []
    for n, body in enumerate(bodies, start=1):
        offsets.append(len(out))
        out += b"%d 0 obj\n" % n + body + b"\nendobj\n"

    start = len(out)
    out += b"xref\n0 %d\n0000000000 65535 f \n" % (len(bodies) + 1)
    for off in offsets:
        out += b"%010d 00000 n \n" % off
    out += (b"trailer\n<< /Size %d /Root %d 0 R >>\nstartxref\n%d\n%%%%EOF\n"
            % (len(bodies) + 1, catalog, start))

    with open(path, "wb") as f:
        f.write(out)
    return path
