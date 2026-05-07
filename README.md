# PDFFlip - Your Personal PDF Library & Reader 📚✨

Welcome to **PDFFlip**! This app is your digital bookshelf. It helps you keep your PDF books organized and makes reading them a joy, whether you like flipping pages like a physical book or scrolling like a webpage.

---

## 🌟 What makes PDFFlip special?

-   **Smart Library**: Automatically finds your PDFs and lets you tag them with colors.
-   **Folders inside Folders 📂**: Just like on your computer, you can create a "Science" folder and put a "Physics" folder inside it!
-   **Custom Tags 🎨**: Give your "Work" PDFs a blue tag and your "Hobbies" a bright yellow one.
-   **No More Mess**: The app is smart. If you try to import a file that is already there, it stops the duplicate from cluttering your shelf.
-   **Remembers Your Spot**: Close the app anytime! When you come back, you'll be right on the page where you left off.
-   **Two Ways to Read**: 
    -   **Flip**: Swiping left/right like a real book.
    -   **Scroll**: Sliding up/down like a social media feed.
-   **AI Assistant 🤖**: Circle any part of a page and let AI explain it to you instantly!
-   **Reading Timer ⏱️**: Keep track of how long you've been focused on your book.

---

## 📂 Where are my files? (Device Storage)

The app creates a special folder on your phone so you can easily find your books:
- **Location**: `Internal Storage > Documents > PDF Flip`
- **Folders**: Any folders you create in the app will appear here too.
- **Adding Books**: You can copy your PDFs directly into this `PDF Flip` folder using a computer or a file manager app, and PDFFlip will see them!

---

## 💡 Code Breakdown (Explained for Beginners) 🎓

If you are new to coding, looking at hundreds of lines can be scary. Think of the app like a house:

### 🏠 The Architecture (File Sizes)
- **`MainActivity.kt`** (237 lines): The **Front Door**. This is the first thing that runs. It decides which room (page) you are in. It also manages your global settings like your **AI API Key**.
- **`LibraryPage.kt`** (1119 lines): The **Bookshelf**. It manages all your files, tags, and folders.
- **`ReaderPage.kt`** (1158 lines): The **Reading Chair**. It handles the hard work of turning a PDF file into a picture you can read. It now includes the **AI "Circle to Explain"** tool.
- **`SettingsPage.kt`** (740 lines): The **Control Panel**. Where you change the lights (theme) and app behavior. This is where you enter your Google AI Studio key to unlock the AI features.

### 🔍 Deep Dive into the Code

#### 1. The AI Integration (New!) 🤖
- **ReaderPage.kt (Line 115)**: **`explainCircle()`** is the brain of the AI feature. It takes the area you circled, crops it into a small image, and sends it to Google's **Gemini AI**.
- **ReaderPage.kt (Line 1105)**: **`AiCanvas`** is a special "see-through" layer. When you use the AI tool, it watches where you circle on the screen.
- **SettingsPage.kt (Line 350)**: This is where we handle the **API Key** input. We use `PasswordVisualTransformation` so your key stays hidden while you type it.
- **NeumorphicComponents.kt**: A special file that creates "soft UI" designs. The AI explanation box uses this to look like it's floating gracefully over your book.

#### 1. The Bookshelf (`LibraryPage.kt`)
- **Line 113**: The main `LibraryScreen` starts here. It's like a container for everything you see on the library page.
- **Line 150-160**: These are "Switches" (`mutableStateOf`). If `showCreateFolderDialog` is ON (true), the folder window pops up!
- **Line 203**: **`scanLibrary()`** is the "Eyes" of the app. It looks into the `Documents/PDF Flip` folder to see what books you have.
- **Line 576**: This is where the app creates a new folder on your phone when you click the "New Folder" button.

#### 2. The Reading Room (`ReaderPage.kt`)
- **Line 98**: The `ReaderScreen` starts. It takes a PDF URI (the file's address) and opens it.
- **Line 102**: **`PdfRenderer`** is like a camera. It takes a snapshot of a PDF page so the app can show it to you as an image.
- **Line 595**: This is where the **`HorizontalPager`** lives. It's the magic code that lets you "flip" pages.
- **Line 978**: **`DrawingCanvas`** is your digital pen. It records where your finger moves and draws a line on top of the page.

#### 3. The Front Door (`MainActivity.kt`)
- **Line 33**: We define the **`Screen`** types (Library, Reader, Settings).
- **Line 160**: The **"GPS"** of the app. It checks `currentScreen` and takes you to the right page.

---

## 🛠️ Concepts to Know

-   **Composable (@Composable)**: Think of these as LEGO bricks. You write a small piece of code for a "Button" and another for a "Text Box," then snap them together to build a screen.
-   **State**: This is how the app "remembers" things. If you click a book, the `selectedUri` state changes, and the app knows which book to open.
-   **Modifier**: These are like adjectives for your LEGO bricks. You can say: "Make this LEGO brick **blue**," "**wide**," or "**clickable**."

---

## 🆙 How to Release an Update (For Developers)

1.  Open `app/build.gradle.kts` and increase `versionCode` by 1.
2.  Update the `versionName` (e.g., "1.0.5").
3.  Go to GitHub, create a new **Release**, and upload the `app-release.apk`.
4.  The "Check for Updates" button in Settings will automatically find it!

Happy Reading! 📖🚀
