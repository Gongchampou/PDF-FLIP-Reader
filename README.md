# PDFFlip - Your Personal PDF Library & Reader 📚✨

Welcome to **PDFFlip**! This app is your digital bookshelf. It helps you keep your PDF books organized and makes reading them a joy, whether you like flipping pages like a physical book or scrolling like a webpage.

---

## 🌟 What makes PDFFlip special?

-   **Smart Library**: Automatically finds your PDFs and lets you tag them with colors.
-   **Folders inside Folders 📂**: Just like on your computer, you can create folders for your books!
-   **Unified Item Counts 🔢**: Each folder shows a single number in the bottom corner representing the total count of folders and files inside. No extra text, just the data.
-   **Intelligent Text Contrast 🌓**: Library cards and folder titles automatically switch between black and white text to ensure they are always readable, even on dark custom backgrounds.
-   **Smart Navigation 📍**: When you go inside folders, the app shows the folder path (like `folder/subfolder`) so you always know where you are.
-   **Modern File Info ℹ️**: Tap the question mark icon in the reader bottom bar to see the file title, size, and folder path in a beautiful blurred overlay.
-   **Custom Tags 🎨**: Give your "Work" PDFs a blue tag and your "Hobbies" a bright yellow one. Tags are managed from the main library page to keep things simple.
-   **Remembers Your Spot**: Close the app anytime! When you come back, you'll be right where you left off.
-   **AI Assistant 🤖**: Circle any part of a page and let AI explain it to you instantly!
-   **Space-Saving Settings ⚙️**: Keep your app clean by hiding advanced AI settings until you need them.
-   **Floating AI Bubble 🫧**: A tiny, movable bubble that stays out of your way while you read.

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
- **`MainActivity.kt`** (255 lines): The **Front Door**. This is the first thing that runs. It decides which room (page) you are in.
- **`LibraryPage.kt`** (1254 lines): The **Bookshelf**. It manages all your files and folders.
- **`ReaderPage.kt`** (1334 lines): The **Reading Chair**. It handles showing your PDF and the **Modern File Info Overlay**.
- **`SettingsPage.kt`** (847 lines): The **Control Panel**. Where you change the theme and set up your **AI API Key**.
- **`AiAssistantPage.kt`** (180 lines): The **AI Brain**. This is where the app talks to Google Gemini to explain your circled text.

### 🔍 Deep Dive into the Code (Where things are)

#### 1. The AI Integration 🤖
- **AiAssistantPage.kt (Line 124)**: **`performAiAnalysis()`** is the logic that sends your image to Google Gemini.
- **ReaderPage.kt (Line 1177)**: **`ReaderPageContent`** is where the page is drawn, including zoom and drawing modes.
- **SettingsPage.kt (Line 635)**: The **Collapsible AI Section**. This hides the API Key settings inside an expandable card to keep things tidy.
- **AiAssistantPage.kt (Line 37)**: **`AiCanvas`** is a special invisible layer that "sees" where you draw a circle on the page.

#### 2. The Bookshelf (`LibraryPage.kt`)
- **Line 225**: **`scanLibrary()`** is the "Eyes" of the app. It looks for books in your folders.
- **Line 610**: This is where the app creates a new folder on your phone when you click the "New Folder" button.
- **Line 1015**: The **Dynamic Navigation Header**. This code checks if you are in a folder. If you are, it shows the **Folder Path**.
- **Line 111**: **`getContrastColor()`** is a smart helper that makes sure text is either black or white depending on the background color.

#### 3. The Reading Room (`ReaderPage.kt`)
- **Line 95**: The `ReaderScreen` starts. It takes a PDF and opens it for you.
- **Line 1277**: **`FileInfoOverlay`** creates the beautiful blurred box that shows your file's name and size.
- **Line 188**: **`getReadableFileSize()`** is a helper that calculates how much space your book takes up.

---

## 🛠️ Concepts to Know

-   **Composable (@Composable)**: Think of these as LEGO bricks. You build a "Button" and a "Text Box" separately, then snap them together to build a screen.
-   **State**: This is how the app "remembers" things. For example, if you toggle "Dark Mode," the app remembers to stay dark.
-   **Modifier**: These are like adjectives. You can say: "Make this button **red**," "**large**," or "**draggable**."

Happy Reading! 📖🚀
