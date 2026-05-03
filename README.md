# PDFFlip - Your Personal PDF Library & Reader 📚✨

Welcome to **PDFFlip**! This is a simple yet powerful Android app designed to help you manage your PDF books and read them with style. Whether you like flipping pages like a real book or scrolling smoothly, PDFFlip has you covered.

---

## 🚀 Key Features

-   **Smart Library**: Import PDFs from your device and organize them with colorful tags.
-   **Custom Tag Colors 🎨**: You can now choose a specific color for every category you create!
-   **Ironclad Duplicate Protection**: The app prevents naming mess by strictly blocking duplicates. If the system tries to auto-rename a file with a suffix like `(1)`, the app identifies it and deletes it instantly to keep your folder clean.
-   **Import Summary**: When importing files, the app identifies duplicates and shows a detailed summary (Green for new, Red for skipped).
-   **Reading Progress**: The app remembers exactly where you left off.
-   **Two Reading Modes**:
    -   **Horizontal Flip**: A classic book-like experience.
    -   **Vertical Scroll**: For those who prefer a modern continuous feed.
-   **Eye Protection**: A warm "Sepia" mode to make long reading sessions easier on your eyes.
-   **Drawing & Notes**: Sketch directly on your PDF pages and save them permanently.
-   **Text-to-Speech**: Listen to your books while you're on the go.

---

## 📂 Project Structure (Where is everything?)

The heart of the app is located in `app/src/main/java/com/gong/pdfflip/`.

1.  **`MainActivity.kt`**: The "Brain" of the app. It handles navigation and remembers your settings.
2.  **`pages/LibraryPage.kt`**: The "Bookshelf." This is where you see all your imported books.
3.  **`pages/ReaderPage.kt`**: The "Reading Room." This handles the PDF rendering and all reading tools.
4.  **`pages/SettingsPage.kt`**: The "Control Center" where you change themes and reading styles.

---

## 💡 Code Breakdown (For Newbies!)

Let's look at some of the most important parts of the code.

### 1. The Library Screen (`LibraryPage.kt`)

This page uses a **`LazyVerticalGrid`** to show your books. Think of a `LazyVerticalGrid` like a smart table that only draws what you can see on the screen to save battery and memory.

#### 🎨 Custom Tag Colors Logic:
-   **Line 153-167**: We use a `tagToColorMap`. This is like a special lookup book where the app checks: *"Hey, what color did the user pick for the 'Science' tag?"* and then displays it!
-   **Line 466-483**: In the "Create New Category" dialog, we added a row of color circles. When you pick a color, it's saved alongside the tag name using a `|` separator (e.g., `Work|#FF0000`).

#### How a Book Card is Built (`BookGridItem` function):
```kotlin
// Line 1051: The Book Card function starts here
@Composable
fun BookGridItem(book: Book, tagColor: Color, ...) {
    Card(...) {
        Box(...) {
            Column(...) {
                // Line 1065: The Book Cover image
                BookCover(uri = book.uri, ...)
                
                // Line 1083: The Book Title
                Text(text = book.name.substringBeforeLast("."), ...)

                // Line 1102: The Progress Bar
                if (book.totalPages > 0) {
                    LinearProgressIndicator(progress = { progress }, color = MaterialTheme.colorScheme.primary, ...)
                }
            }
            
            // Line 1130: The Action Buttons (Tag & Delete)
            // Notice how 'tint = tagColor' uses your chosen color!
            Icon(Icons.Default.Label, tint = tagColor, ...)
        }
    }
}
```

---

### 2. The Reader Screen (`ReaderPage.kt`)

This is the most complex part of the app. It uses **`PdfRenderer`**, which is a built-in Android tool to turn PDF pages into images that we can display.

#### The "Magic" of Page Turning:
We use a **`HorizontalPager`** for flipping and a **`VerticalPager`** for scrolling.
-   **`pagerState`**: This object keeps track of which page you are on.
-   **`LaunchedEffect`**: This is like a "watchdog." When the page changes, it triggers code to save your progress to a file called `recent_data.txt`.

#### Drawing on PDFs:
We use a **`Canvas`**. A `Canvas` is like a digital whiteboard where you can draw anything using "Points" (X and Y coordinates).

---

### 3. Navigation (`MainActivity.kt`)

We use an **`enum`** called `Screen` to decide which page to show:
```kotlin
enum class Screen {
    Library,
    Reader,
    Settings
}
```
Think of an `enum` as a list of "fixed options." The app simply checks `if (currentScreen == Screen.Library)` and displays the correct view.

---

## 🛠️ How to Customize

-   **Change the Theme**: Go to `ui/theme/Theme.kt`. You can add new colors to the `PDFFlipTheme` to change the app's look.
-   **Add New Tags**: In the Library, click the "+" button next to the category list to create new tags. **Don't forget to pick a color!**

---

## 📚 Technical Concepts Explained

-   **Jetpack Compose**: The modern way to build Android UIs. Instead of writing complex XML, we write functions in Kotlin.
-   **State (`remember { ... }`)**: This tells the app to "remember" a piece of data even if the screen rotates or refreshes.
-   **Coroutine (`scope.launch`)**: This allows the app to do heavy work (like loading a big PDF) in the background so the screen doesn't freeze.

Happy Reading! 📖🚀
