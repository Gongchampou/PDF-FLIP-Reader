# PDFFlip - Your Personal PDF Library & Reader 📚✨

Welcome to **PDFFlip**! This app is your digital bookshelf. It helps you keep your PDF books organized and makes reading them a joy, whether you like flipping pages like a physical book or scrolling like a webpage.

---

## 🌟 What makes PDFFlip special?

-   **Smart Library**: Automatically finds your PDFs and lets you tag them with colors.
-   **Folders inside Folders 📂**: Just like on your computer, you can create folders for your books!
-   **Backup & Share 📤**: Export your entire library—including all your folders, PDF files, and colorful categories—into a single file to share with friends or move to a new device.
-   **Unified Item Counts 🔢**: Each folder shows a single number in the bottom corner representing the total count of folders and files inside.
-   **Intelligent Text Contrast 🌓**: Library cards and folder titles automatically switch between black and white text to ensure they are always readable.
-   **Smart Navigation 📍**: When you go inside folders, the app shows the folder path (like `folder/subfolder`) so you always know where you are.
-   **Modern File Info ℹ️**: Tap the question mark icon in the reader bottom bar to see the file title, size, and folder path in a beautiful blurred overlay.
-   **AI Assistant 🤖**: Circle any part of a page and let AI explain it to you instantly!
-   **Space-Saving Settings ⚙️**: Keep your app clean by hiding advanced AI settings and backup hints until you need them.

---

## 📂 Where are my files? (Device Storage)

The app creates a special folder on your phone so you can easily find your books:
- **Location**: `Internal Storage > Documents > PDF Flip`
- **Folders**: Any folders you create in the app will appear here too.
- **Adding Books**: You can copy your PDFs directly into this `PDF Flip` folder, and PDFFlip will see them!

---

## 💡 Code Breakdown (Explained for Beginners) 🎓

If you are new to coding, think of the app like a house with different rooms:

### 🏠 The Architecture (File Sizes)
- **`MainActivity.kt`** (257 lines): The **Front Door**. This is the first thing that runs. It decides which room (page) you are in.
- **`LibraryPage.kt`** (1254 lines): The **Bookshelf**. It manages all your files and folders.
- **`ReaderPage.kt`** (1334 lines): The **Reading Chair**. It handles showing your PDF and the **Modern File Info Overlay**.
- **`SettingsPage.kt`** (1007 lines): The **Control Panel**. Where you change the theme, set up **AI**, and **Backup** your library.
- **`AiAssistantPage.kt`** (180 lines): The **AI Brain**. This is where the app talks to Google Gemini.

### 🔍 Deep Dive into the Code (Where things are)

#### 1. The Backup System 🚚
- **BackupUtils.kt**: The "Moving Truck". This utility handles zipping up your entire `PDF Flip` folder and all your settings so you can share them.
- **SettingsPage.kt (Line 118)**: This is where the **Export/Import** logic lives. It uses icon-only buttons (Cloud Upload/Download) to save or open your library backup files.
- **SettingsPage.kt (Line 598)**: The **Hint Toggle**. This code adds a small information icon that shows or hides the backup instructions to save space.

#### 2. The AI Integration 🤖
- **AiAssistantPage.kt (Line 124)**: **`performAiAnalysis()`** is the logic that sends your image to Google Gemini.
- **ReaderPage.kt (Line 1177)**: **`ReaderPageContent`** is where the page is drawn.
- **SettingsPage.kt (Line 774)**: The **Collapsible AI Section**.

#### 3. The Bookshelf (`LibraryPage.kt`)
- **Line 225**: **`scanLibrary()`** is the "Eyes" of the app. It looks for books in your folders.
- **Line 111**: **`getContrastColor()`** is a smart helper that makes sure text is either black or white depending on the background.

#### 4. The Reading Room (`ReaderPage.kt`)
- **Line 95**: The `ReaderScreen` starts. It takes a PDF and opens it for you.
- **Line 1277**: **`FileInfoOverlay`** creates the beautiful blurred box that shows your file's details.

---

## 🛠️ Concepts to Know

-   **Composable (@Composable)**: Think of these as LEGO bricks. You build a "Button" and a "Text Box" separately, then snap them together to build a screen.
-   **State**: This is how the app "remembers" things. For example, if you toggle "Dark Mode," the app remembers to stay dark.
-   **Modifier**: These are like adjectives. You can say: "Make this button **red**," "**large**," or "**draggable**."

### 💁‍♂️💁‍ Let contribute and developed his "PDF Flip" app for community of student who really i need of freemium for there work style.
Happy Reading! 📖🚀
