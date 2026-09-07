#!/usr/bin/env python3
"""
Lays a passage of دلائل الرحمات into the book's own page design.

Everything ornamental is the book's own, lifted from a scanned page and reused
unchanged: the frame, the title cartouche, the paper tone, and the gold rosette
that separates the phrases. Only the body is cleared and reset. The type is
sized so the passage fills its pages exactly, and every line is justified to the
full column, so the lines come out even rather than ragged.
"""
import sys
from PIL import Image, ImageDraw, ImageFont

TEMPLATE = "android-app/design/page-design.jpg"
ORNAMENT_IMG = "android-app/design/ornament.png"
FONT = "ratib-app/app/src/main/res/font/amiri.ttf"

# Measured off the template: the frame's inner edge, and the cartouche that sits
# inside its top. The body owns everything below the cartouche.
X0, X1 = 78, 776
BODY_TOP, BODY_BOT = 192, 1212
CLEAR = (72, 186, 782, 1218)   # wiped back to blank paper
INK = (36, 26, 11)
ORNAMENT = "۞"
LINE_RATIO = 1.62      # line pitch as a multiple of the type size
ORNAMENT_RATIO = 0.74  # rosette size as a multiple of the type size


def paper_tone(im):
    """
    The paper's colour row by row.

    Wiping the body with one flat cream leaves a visible panel, because the
    scan's tone drifts down the page. Taking each row's own paper colour — the
    median of its unwritten pixels — puts the drift back.
    """
    px = im.load()
    tones, last = {}, (252, 247, 231)
    for y in range(CLEAR[1], CLEAR[3]):
        bright = [px[x, y] for x in range(X0, X1, 3)
                  if sum(px[x, y]) / 3 > 205]
        if len(bright) > 20:
            bright.sort(key=lambda p: sum(p))
            last = bright[len(bright) // 2]
        tones[y] = last
    return tones


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


def blank_page(tones, template):
    """The page with its frame and title, and nothing in the body."""
    im = template.copy()
    d = ImageDraw.Draw(im)
    for y in range(CLEAR[1], CLEAR[3]):
        d.line([(CLEAR[0], y), (CLEAR[2], y)], fill=tones[y])
    return im


def token_width(w, font, draw, orn_px):
    if w == ORNAMENT:
        return float(orn_px)
    return draw.textlength(w, font=font, direction="rtl")


def wrap(words, font, draw, width, orn_px):
    """Break into lines, each as wide as the column allows."""
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


def render(text, pages, out_prefix):
    words = text.split()
    template = Image.open(TEMPLATE).convert("RGB")
    tones = paper_tone(template)
    rosette = load_ornament()
    probe = ImageDraw.Draw(Image.new("RGB", (8, 8)))
    column = X1 - X0

    # Largest type that still fits the passage into the pages asked for, so the
    # pages come out full rather than with a hole at the foot of the last one.
    best = None
    for size in range(20, 121):
        font = ImageFont.truetype(FONT, size)
        orn = int(size * ORNAMENT_RATIO)
        lines = wrap(words, font, probe, column, orn)
        pitch = int(size * LINE_RATIO)
        per_page = max(1, (BODY_BOT - BODY_TOP) // pitch)
        if len(lines) <= per_page * pages:
            best = (size, font, lines, pitch, per_page, orn)
        else:
            break
    if best is None:
        raise SystemExit("even the smallest type will not fit that many pages")
    size, font, lines, pitch, per_page, orn_px = best
    rose = rosette.resize((orn_px, orn_px), Image.LANCZOS)

    made = []
    for p in range(pages):
        chunk = lines[p * per_page:(p + 1) * per_page]
        if not chunk:
            break
        im = blank_page(tones, template)
        d = ImageDraw.Draw(im)
        # Spread the page's lines over the whole column, so the last one sits on
        # the last line of the page instead of leaving the foot empty.
        spare = (BODY_BOT - BODY_TOP) - len(chunk) * pitch
        step = pitch + (spare / (len(chunk) - 1) if len(chunk) > 1 else 0)
        for i, ln in enumerate(chunk):
            y = BODY_TOP + int(i * step)
            widths = [token_width(w, font, d, orn_px) for w in ln]
            space = d.textlength(" ", font=font)
            n = len(ln)
            last_line = (i == len(chunk) - 1 and p == pages - 1)
            if n > 1 and not last_line:
                gap = (column - sum(widths)) / (n - 1)
                x = X1
            else:
                gap = space
                x = X1 - (column - (sum(widths) + space * (n - 1))) / 2
            for w, ww in zip(ln, widths):
                if w == ORNAMENT:
                    im.paste(rose, (int(x - ww), y + int(size * 0.30)), rose)
                else:
                    d.text((x - ww, y), w, font=font, fill=INK, direction="rtl")
                x -= ww + gap
        path = f"{out_prefix}_{p + 1}.png"
        im.save(path)
        made.append(path)
    print(f"type size {size}px, {len(lines)} lines over {len(made)} pages")
    return made


if __name__ == "__main__":
    body = open(sys.argv[1], encoding="utf-8").read()
    render(body, int(sys.argv[2]), sys.argv[3])
