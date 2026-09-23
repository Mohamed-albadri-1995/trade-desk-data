"""
يتحقّق من السور التي تُختم بها الصلوات.

كل صلاةٍ تُختم بسورةٍ أو سورتين، والكتاب يمضي على ترتيب المصحف من الفاتحة إلى
الناس. وهذا بابٌ يُقطع فيه بيقين لا بظنّ: فتُستخرج أسماء السور من الصلوات
التسعين، وتُقابَل بالمصحف اسمًا اسمًا، فيُعلم أنّ المائة والأربع عشرة كلَّها
مذكورة، وأن لا اسم تكرّر، وأين خرج الترتيب عن ترتيب المصحف إن خرج.

والأسماء تُقرَّب قبل المقابلة، لأن الكتاب يكتب بعضها على وجهٍ غير وجه المصحف:
«النسا» و«الممتحنه» و«المآئدة» و«سبا»، ويسمّي المُلك «تبارك» والقلم «ن».

    python3 tools/dalail_suwar.py
"""

import sys, re
sys.path.insert(0, "tools")
import dalail_book as bk

QURAN = """الفاتحة البقرة آل_عمران النساء المائدة الأنعام الأعراف الأنفال التوبة يونس
هود يوسف الرعد إبراهيم الحجر النحل الإسراء الكهف مريم طه الأنبياء الحج المؤمنون
النور الفرقان الشعراء النمل القصص العنكبوت الروم لقمان السجدة الأحزاب سبأ فاطر يس
الصافات ص الزمر غافر فصلت الشورى الزخرف الدخان الجاثية الأحقاف محمد الفتح الحجرات
ق الذاريات الطور النجم القمر الرحمن الواقعة الحديد المجادلة الحشر الممتحنة الصف
الجمعة المنافقون التغابن الطلاق التحريم الملك القلم الحاقة المعارج نوح الجن المزمل
المدثر القيامة الإنسان المرسلات النبأ النازعات عبس التكوير الانفطار المطففين
الانشقاق البروج الطارق الأعلى الغاشية الفجر البلد الشمس الليل الضحى الشرح التين
العلق القدر البينة الزلزلة العاديات القارعة التكاثر العصر الهمزة الفيل قريش الماعون
الكوثر الكافرون النصر المسد الإخلاص الفلق الناس""".split()
assert len(QURAN) == 114, len(QURAN)

HAR = re.compile(r"[ً-ْٰـ]")
def key(s):
    s = HAR.sub("", s).strip().replace("_", " ")
    s = s.replace("أ", "ا").replace("إ", "ا").replace("آ", "ا").replace("ٱ", "ا")
    s = s.replace("ة", "ه").replace("ى", "ي").replace("ئ", "ي").replace("ؤ", "و")
    s = re.sub(r"ء$", "", s)          # النساء as the book spells it: النسا
    return re.sub(r"\s+", " ", s)

NUM = {key(n): i + 1 for i, n in enumerate(QURAN)}
# What the book calls a sura by another of its names.
NUM[key("تبارك")] = 67      # الملك
NUM[key("ن")] = 68          # القلم

seq, unknown = [], []
for title, text, closing in bk.sections():
    passages = [ln for ln in text.splitlines() if bk.page.strip_marker(ln).strip()]
    for i, p in enumerate(passages):
        m = bk.SALAT.match(p.strip())
        if m and i + 1 < len(passages):
            salat = HAR.sub("", m.group(1))
            for name in bk.sura_names(passages[i + 1]):
                k = key(name)
                if k in NUM:
                    seq.append((NUM[k], salat, HAR.sub("", name)))
                else:
                    unknown.append((salat, name))

print(f"عدد السور المذكورة: {len(seq)}")
if unknown:
    print("\n✗ أسماء لم أعرفها:", unknown)

nums = [n for n, _, _ in seq]
missing = [f"{i+1} {QURAN[i]}" for i in range(114) if (i + 1) not in nums]
dupes = sorted({n for n in nums if nums.count(n) > 1})
print("ناقصة:", missing or "لا شيء")
print("مكرّرة:", [f"{n} {QURAN[n-1]}" for n in dupes] or "لا شيء")

print("\n═══ مواضع خروجٍ عن ترتيب المصحف ═══")
bad = False
for a, b in zip(seq, seq[1:]):
    if b[0] < a[0]:
        bad = True
        print(f"  الصلاة {a[1]}: {a[2]} ({a[0]})   ثم   الصلاة {b[1]}: {b[2]} ({b[0]})")
if not bad:
    print("  لا شيء — الترتيب على المصحف من الفاتحة إلى الناس")
