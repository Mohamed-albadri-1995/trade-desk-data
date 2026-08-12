# PDF Book — Android App

A simple Android app that opens and displays your PDF book. It supports smooth
scrolling, pinch-to-zoom, double-tap zoom, a draggable scroll bar with page
numbers, and it **remembers the last page you were on** so the book reopens
where you left off.

---

## ✅ You only need to do TWO things

### 1. Add your PDF
Put your book into this folder and name it **exactly** `book.pdf`:

```
app/src/main/assets/book.pdf
```

(There's a placeholder note in that folder — you can delete it once your PDF is in.)

### 2. Set the book's name
Open `app/src/main/res/values/strings.xml` and change this line to your book title:

```xml
<string name="app_name">My PDF Book</string>
```

That name shows under the app icon on the phone and in the title bar.

---

## 📱 Build the app (Android Studio)

1. **Install Android Studio** (free): https://developer.android.com/studio
2. Open Android Studio → **File → Open** → select this `android-app` folder.
3. Wait for **"Gradle sync"** to finish (first time downloads the build tools —
   this needs internet and can take a few minutes). Let it finish.
4. **To run it on a phone/emulator now:** click the green ▶ **Run** button.
   - Real phone: enable *Developer Options → USB debugging*, plug it in, pick it as the device.
   - No phone? Use *Device Manager* in Android Studio to create an emulator.
5. **To get an installable file (APK) to share:**
   **Build → Build Bundle(s) / APK(s) → Build APK(s)**.
   When it finishes, click **"locate"** in the popup. The file is:
   ```
   app/build/outputs/apk/debug/app-debug.apk
   ```
   Copy that `.apk` to any Android phone and tap it to install
   (you may need to allow "Install from unknown sources").

That's it — you now have your book as an app. 🎉

---

## 🎨 Optional tweaks

- **Page-flip (side-swipe) instead of vertical scroll:** in
  `MainActivity.kt`, change `.swipeHorizontal(false)` to `.swipeHorizontal(true)`.
- **Different PDF file name:** change the `assetFileName` line at the top of
  `MainActivity.kt`.
- **App icon color:** edit the `#3F51B5` values in
  `res/drawable/ic_launcher_background.xml` and `ic_launcher_foreground.xml`.

---

## 🔧 Technical details

| | |
|---|---|
| Language | Kotlin |
| Min Android version | 5.0 (API 21) |
| Target Android version | 15 (API 35) |
| PDF library | `com.github.mhiew:android-pdf-viewer` |
| Build system | Gradle (wrapper included — no manual Gradle install needed) |

The app reads the PDF from the app's bundled `assets`, so it works fully
offline and no internet permission is requested.
