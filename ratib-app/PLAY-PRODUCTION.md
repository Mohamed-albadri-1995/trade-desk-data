# إجابات استبيان الإنتاج — Google Play
## أوراد الطريقة السمانية (com.sammaniyya.awrad)

الاستمارة تحدّ كل صندوق بـ **٣٠٠ حرف**. هذه هي الإجابات النهائية المختصرة،
وعدد حروف كلٍّ منها مقيس. النسخ الطويلة محفوظة بعدها للرجوع إليها.

> كل ما هنا مستخرَج من عمل حقيقي مؤرَّخ في هذا المستودع (٥٦ تعديلًا بين
> ١٢ أغسطس و١٥ سبتمبر ٢٠٢٦). لا رقم مُختلَق ولا ملاحظة مُخترعة.
> **اقرأها قبل الإرسال واحذف أي جملة لم تقع فعلًا.**

---

## ١ — About your closed test

**How did you recruit users for your closed test?** — ٢٧٧/٣٠٠

```
No paid provider and no tester-exchange group. I recruited them myself: friends, and people I knew already read this book, which the app is a reader for. I explained it was a closed test and sent each of them the opt-in link directly, then kept in touch by phone and in person.
```

**How easy was it to recruit testers?** → **Difficult**

**Describe the engagement you received from testers** — ٢٧٩/٣٠٠

```
They used it daily, as the book itself is read daily - real use, not a token install. Their reports show which features they reached: errors found in the text mean they read it closely, and reports that the alarm rang at the wrong time in Sudan mean they relied on the reminders.
```

**Summary of the feedback, and how you collected it** — ٢٩٥/٣٠٠

```
Collected by phone calls and in person; most are older readers who do not write reports, so I asked them directly. Feedback: errors in the text; text cut off at the screen bottom and bad page breaks on some phones; alarms at the wrong time in Sudan or not firing; updates that would not install.
```

---

## ٢ — About your app

**Who is the intended audience?** — ٢٦٧/٣٠٠

```
Arabic-speaking adult Muslims who read the daily awrad of the Sammaniyya Sufi order, mainly in Sudan and among Sudanese abroad. A religious reading app for people who already follow this practice. Not aimed at children: no games, no accounts, no user content, no ads.
```

**Describe how your app provides value to users** — ٢٨٢/٣٠٠

```
The book is hard to obtain in print. The app carries the full text, verified by readers who know it, typeset for the phone with full diacritics, and calls each reading at its time - a daily ward reminder, prayer times and the adhan. Entirely offline: no internet permission, no ads.
```

**How many installs do you expect in your first year?** → **0 – 10K**

---

## ٣ — Your production readiness

**What changes did you make based on what you learned?** — ٢٨٩/٣٠٠

```
Alarms: the ward reminder became a ringing alarm, not a silent notification; the pre-dawn alarm moved to the correct hour; prayer times now default to Sudan, not Mecca, and sound a real adhan. Layout: clipped text and broken page breaks fixed. Text: every correction readers found applied.
```

**How did you decide that your app is ready for production?** — ٢٨٧/٣٠٠

```
Not by my own opinion - by the test. Every problem testers raised was fixed, and each fix was confirmed by them on a new build. The last build ran the full period on their phones with no crash and no new report. It works offline and targets Android 16. The testers stay on after release.
```

> إن لم تعش النسخة الأخيرة المدّة كاملة، استبدل
> `ran the full period on their phones with no crash and no new report`
> بـ `ran on their phones with no crash reported`.

**لا تكتب هنا:** «كان مثاليًّا قبل الاختبار»، «وجدت شيئين أو ثلاثة»،
«لا سبب عندي لتأخيره». الثلاثة تُقرأ ضدّك.

---

## ٤ — Additional testing

**What did you do differently this time?** — ٢٨٩/٣٠٠

```
Last time I only asked testers whether the app worked, and got general answers. This time I gave each one a task: read a whole section and report any wrong word, and leave the alarms on overnight. I wrote down every report, fixed them, and published a new build during the test to confirm.
```

### ما يجب فعله فعلًا خلال الأربعة عشر يومًا حتى تصدق هذه الإجابة

1. أعطِ كل مختبِر **مهمّة محدّدة**: «اقرأ الحزب كاملًا وأخبرني بأي كلمة غلط»،
   «اترك المنبّه شغّالًا الليلة وأخبرني هل رنّ في وقته».
2. ركّز على **المنبّهات** — موضع أكثر الأعطال، وأوضح دليل على استعمال حقيقي.
3. **دوّن كل ملاحظة** بالاسم والتاريخ.
4. **انشر نسخة واحدة على الأقلّ** أثناء المدّة على مسار الاختبار المغلق،
   فيها إصلاح لملاحظة وصلتك — يترك أثرًا في الحساب تراه قوقل.
5. **تابع العدد يوميًّا**: ١٢ فأكثر إلى آخر يوم. انسحاب واحد يكسر الشرط،
   والمدّة تبدأ من **تاريخ المراجعة** لا من اليوم الذي بدأتَ فيه.

---
---

# النسخ الطويلة (للرجوع، لا للصق في الاستمارة)

## السؤال الأول — كيف جمعتَ المختبِرين وكيف أبقيتَهم مشاركين؟

### بالعربية

تطبيقي قارئ لكتاب أوراد الطريقة السمانية، وهو نصّ يُقرأ يوميًّا في أوقات
معلومة. لم أستعمل مجموعات تبادل الاختبار ولا أي خدمة مدفوعة. جمعتُ المختبِرين
بنفسي: تواصلتُ مع أصدقائي ومع أشخاص أعرف أنهم مهتمّون بهذا الكتاب تحديدًا،
وشرحتُ لهم أن التطبيق في مرحلة اختبار مغلق، وأرسلتُ لكل واحد منهم رابط
الانضمام مباشرة. جميعهم ممّن يقرأون هذا النصّ أصلًا، فكان لديهم سبب حقيقي
لفتح التطبيق كل يوم لا لمجرّد تثبيته.

وأمّا إبقاؤهم مشاركين طوال الأربعة عشر يومًا فكان بالتواصل المباشر: كنت
أتّصل بهم هاتفيًّا أثناء فترة الاختبار، وألتقي بعدد منهم وجهًا لوجه. وطبيعة
التطبيق نفسها ساعدت — فهو يُنبّه للورد يوميًّا ويؤذّن لأوقات الصلاة، فصار
جزءًا من يومهم لا مهمّة إضافية. ونشرتُ أثناء الاختبار عدّة نسخ محدَّثة، وكنت
أخبر كل مختبِر بما تغيّر وبما أريده أن ينظر فيه، فكان لديه في كل مرّة شيء
جديد يجرّبه.

### In English (for the form)

My app is a reader for a devotional book — أوراد الطريقة السمانية, the daily
office of the Sammaniyya order — which is read every day at fixed times. I did
not use a tester-exchange group, a testing service, or paid installs. I
recruited every tester personally: I approached friends and people I knew were
interested in this particular book, explained that the app was in closed
testing, and sent each of them the opt-in link directly. They are all people
who already read this text, so they had a real reason to open the app daily
rather than simply install it and leave it.

To keep them engaged across the 14 days I stayed in direct contact — I called
testers by phone during the test period and met a number of them face to face.
The nature of the app helped as well: it gives a daily reminder for the ward
and calls the adhan at prayer times, so it became part of their daily routine
rather than an extra chore. I released several updated builds during the
testing period and told each tester what had changed and what I wanted them to
look at, so there was something new for them to try each time.

---

## السؤال الثاني — ما الملاحظات التي وصلتك، وكيف جمعتَها؟

### بالعربية

كل الملاحظات جُمعت مباشرةً: بمكالمات هاتفية مع كل مختبِر على حدة، وبلقاءات
وجهًا لوجه. أكثر المختبِرين من كبار السنّ ممّن لا يكتبون تقارير أعطال، فكنت
أسألهم مباشرة — في الهاتف أو وأنا جالس معهم بينما يستعملون التطبيق — عن الخطأ
وعن الصعب عليهم، وأدوّن ذلك بنفسي.

وجاءت الملاحظات في خمسة أبواب:

1. **القراءة وتنسيق الصفحة.** أخبروني أن النصّ يُقطع من أسفل الشاشة في بعض
   الهواتف، وأن الصفحة تنتهي في منتصف الجملة، وأن العناوين تبقى وحدها في
   آخر الصفحة ويُترك تحتها فراغ كبير، وأن قائمة الخيارات غير ظاهرة في الوضع
   الفاتح. وواجه أحدهم انهيار التطبيق عند فتح الإعدادات.

2. **صحّة النصّ.** المختبِرون يحفظون هذا الكتاب، فوجدوا فيه أخطاء: شدّة ساقطة
   في اسم «محمد بشر»، وعدد تكرار خاطئ في أوراد السحر، وقسم أوراد السحر ناقصًا،
   وتنبيهًا في قسم الأوراد المربوطة لم يكن التطبيق يعرضه أصلًا، وأخطاء أخرى
   في التشكيل واللفظ.

3. **عدد التكرار وترتيب الأقسام.** قالوا إنهم لا يعرفون كم مرّة يُكرَّر كل
   بيت، وإن قسم «الأوراد المربوطة» كان مُقدَّمًا كأنه من الراتب وهو ليس منه.

4. **التنبيهات والمنبّهات** — وهذا أكثر ما جاءتني فيه ملاحظات. كانوا يفوّتون
   الورد لأن التنبيه كان إشعارًا صامتًا؛ ومنبّه أوراد السحر كان يرنّ في أول
   الثلث الأخير لا في وسطه؛ وأوقات الصلاة كانت ترنّ كمنبّه استيقاظ لا كأذان؛
   وحساب أوقات الصلاة كان مضبوطًا على مكّة فأعطى أوقاتًا خاطئة لمن في السودان.
   وأخبرني عدد منهم أن هواتفهم ذات توفير البطارية الشديد لا تُطلق المنبّه أصلًا.

5. **التثبيت.** لم يستطع بعضهم تثبيت التحديث فوق النسخة القديمة.

### In English (for the form)

All of my feedback was gathered in person: by phone calls with individual
testers, and by meeting them face to face. Most of my testers are older
readers who are not comfortable writing bug reports, so I asked them directly
— over the phone, or sitting with them while they used the app — what was
wrong and what was difficult, and I wrote the points down myself.

The feedback fell into five areas:

1. **Reading and page layout.** Testers reported that text was cut off at the
   bottom of the screen on some phones, that pages broke in the middle of a
   sentence, that section headings were left stranded alone at the foot of a
   page with a large blank space beneath them, and that the overflow menu was
   invisible in light mode. One tester had the app crash when opening settings.

2. **Correctness of the text.** My testers know this book by heart, and
   several found errors in it: a missing shadda in the name محمد بشر, a wrong
   repetition count in the أوراد السحر section, an incomplete أوراد السحر
   section, an explanatory note in the الأوراد المربوطة section that the app
   was not displaying at all, and further wording and diacritic errors.

3. **Repetition counts and structure.** Testers said they could not tell how
   many times each verse should be repeated, and that the الأوراد المربوطة
   section was presented as though it were part of the ratib when it is a
   separate text.

4. **Reminders and alarms** — this produced the most feedback. Testers were
   missing the ward because the reminder was a silent notification; the أوراد
   السحر alarm rang at the start of the last third of the night instead of its
   middle; prayer times sounded like an ordinary wake-up alarm rather than
   being called; and the prayer-time calculation defaulted to Mecca's
   settings, which gave the wrong times for testers in Sudan. Several testers
   on phones with aggressive battery saving reported that alarms did not fire
   at all.

5. **Installation.** Some testers could not install an update over their
   existing copy of the app.

---

## السؤال الثالث — كيف غيّرتَ التطبيق بناءً على هذه الملاحظات؟

### بالعربية

كل ملاحظة ممّا سبق أدّت إلى تغيير حقيقي، وكنت أنشر النسخة المعدَّلة لنفس
المختبِرين ليتأكّدوا من الإصلاح:

**تنسيق الصفحة:** أعدت كتابة مُقسِّم الصفحات بالكامل. صارت الصفحة تمتلئ بدل أن
تنقطع مبكّرًا، وصار القطع عند نهاية عبارة تامّة، ولا يبقى عنوان وحده في آخر
صفحة. وأصلحتُ القطع من الأسفل بقياس سطر حقيقي على الجهاز بدل حسابه نظريًّا.
وأصلحتُ انهيار الإعدادات، وأظهرتُ قائمة الخيارات في الوضع الفاتح.

**النصّ:** صحّحتُ كل خطأ وجدوه — الشدّة الساقطة، وعدد تكرار السحر، وأكملتُ قسم
أوراد السحر كاملًا، وأظهرتُ تنبيه الأوراد المربوطة الذي كان يسقط، مع جولات
تصحيح أخرى للتشكيل واللفظ.

**عدد التكرار:** صار العدد مكتوبًا مع كل بيت، مكتوبًا بالحروف وبخطّ مختلف حتى
يتميّز عن النصّ المقروء، وأُفرِدت الخاتمة «يا ملجأ القاصد» بلونها وبعلامة
(ثلاثًا). ورُفع قسم الأوراد المربوطة إلى رتبة عنوان الكتاب نفسه حتى يتبيّن
أنه ليس من الراتب.

**المنبّهات:** صار تنبيه الورد منبّهًا يرنّ لا إشعارًا صامتًا؛ وصار أوراد
السحر يرنّ في وسط الثلث الأخير؛ ومُيِّز وقت الصلاة عن منبّه الاستيقاظ وصار
يُؤذَّن له بأذان حقيقي مُضمَّن في التطبيق؛ ونُقلت إعدادات حساب المواقيت من
مكّة إلى السودان حيث يسكن مستخدموه؛ وصارت المنبّهات تُضبَط من لحظة التثبيت لا
عند فتح الإعدادات؛ وأضفتُ صفحة إرشاد تشرح كيف يُستثنى التطبيق من توفير
البطارية في الهواتف التي كانت تُوقف المنبّه.

**التثبيت:** صرتُ أوقّع كل نسخة بمفتاح ثابت واحد، فصار التحديث يُثبَّت فوق
السابق بدل أن يُرفَض.

وأضفتُ كذلك طلب إذن الموقع عند أول تشغيل مع شرح سببه، لأن مواقيت الصلاة لا
تُحسب بدونه، وحذفتُ أزرار حجم الخطّ التي رآها المختبِرون زائدة مع وجود التكبير
باللمس.

### In English (for the form)

Every point above led to a real change, and I released the updated build to
the same closed testers so they could confirm the fix.

**Page layout:** I rewrote the paginator completely. Pages now fill instead of
breaking early, page breaks fall at the end of a complete phrase, and headings
are never left alone at the foot of a page. The bottom clipping was fixed by
measuring a real line of text on the device rather than calculating one. The
settings crash was fixed, and the overflow menu was made visible in light mode.

**Text:** I corrected every error the testers found — the missing shadda, the
repetition count in أوراد السحر, the incomplete السحر section (which I added
in full), the الأوراد المربوطة note that was being dropped, and several
further rounds of wording and diacritic corrections.

**Repetition counts:** the count is now printed with each verse, spelled out
in words and set in a second typeface so it stands apart from the text being
read, and the closing invocation is set apart in its own colour and marked
(ثلاثًا). The الأوراد المربوطة section was raised to the same heading rank as
the book's own title, so it is clear it is not part of the ratib.

**Alarms:** the ward reminder became a real ringing alarm instead of a silent
notification; أوراد السحر now rings in the middle of the last third of the
night rather than at its start; prayer times are distinguished from wake-up
alarms and are announced with a real adhan bundled in the app; the
prayer-time calculation defaults were changed from Mecca to Sudan, where my
users are; alarms are armed from the moment the app is installed instead of
waiting for the settings screen to be opened; and I added a troubleshooting
page explaining how to exempt the app from battery optimisation on the phones
that were stopping the alarms.

**Installation:** I now sign every build with one fixed key, so an update
installs over the previous version instead of being refused.

I also added a location permission request at first launch with an
explanation of why it is needed, because prayer times cannot be calculated
without it, and I removed the font-size buttons that testers found redundant
alongside pinch-to-zoom.

---

## إن سُئلتَ: ماذا تعلّمت، وكيف ستستمرّ؟

### In English

The main thing I learned is that I cannot judge this app from my own phone.
Almost every serious problem — the clipped text, the alarms that never fired,
the update that would not install — appeared only on someone else's device,
with a different screen size or a different manufacturer's battery settings.
I also learned that my readers care most about two things: that the text is
exactly right, and that the alarm actually sounds at the right moment.

After release I intend to keep the same group of testers on the closed track
and continue to publish to them first, to keep collecting corrections to the
text from readers who know it, and to answer by the contact email on the
store listing. The book is read daily, so mistakes are found quickly and I
will keep issuing corrections as they come.

### بالعربية

أهمّ ما تعلّمته أنني لا أستطيع الحكم على التطبيق من هاتفي وحده. أكثر
المشكلات الجدّية — قطع النصّ، والمنبّه الذي لا يرنّ، والتحديث الذي لا يُثبَّت
— لم تظهر إلا على أجهزة غيري، بمقاسات شاشة مختلفة وإعدادات بطارية مختلفة.
وتعلّمتُ أن قُرّائي يهتمّون بأمرين قبل كل شيء: دقّة النصّ، ورنين المنبّه في
وقته. وسأُبقي بعد النشر نفس المجموعة على مسار الاختبار المغلق، وأنشر لهم
أولًا، وأستمرّ في جمع تصحيحات النصّ ممّن يحفظونه.

---

## ملاحظات مهمّة قبل الإرسال

1. **عدد المختبِرين** — قوقل تشترط **١٢ مختبِرًا على الأقلّ منضمّين ومستمرّين
   طوال الأربعة عشر يومًا كاملة**. لا تذكر في الاستبيان رقمًا أكبر ممّا عندك
   فعلًا. وتأكّد من صفحة Closed testing أن العدد ما زال ١٢ فأكثر إلى آخر يوم —
   لو انسحب أحدهم أو حذف التطبيق نقص العدد وقد يكون هذا سبب الرفض الأول.

2. **لا تنسخ الإجابة كما هي إن لم تكن صحيحة عندك.** احذف كل جملة لم تحدث،
   وخصوصًا: «نشرتُ عدّة نسخ محدَّثة أثناء الاختبار» و«التقيتُ بعدد منهم وجهًا
   لوجه» — أبقِها فقط إن كانت واقعة.

3. **أسباب الرفض الشائعة** غير عدد المختبِرين: إجابات عامّة بلا تفاصيل، أو
   ذكر ملاحظات دون ذكر ما تغيّر بسببها. هذه المسودّة تعالج ذلك بذكر أسماء
   الأقسام والمشكلات بعينها.

4. **الطول** — إن كان صندوق الإجابة يحدّ عدد الحروف، احذف من القوائم واحتفظ
   بالأبواب الخمسة وبمثال واحد لكل باب.
