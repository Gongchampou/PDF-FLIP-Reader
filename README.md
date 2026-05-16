# PDFFlip - Your Personal PDF Library & Reader 📚✨

Welcome to **PDFFlip**! This app is your digital bookshelf. It helps you keep your PDF books organized and makes reading them a joy, whether you like flipping pages like a physical book or scrolling like a webpage.

---

## 🌟 What makes PDFFlip special?

-   **Smart Library**: Automatically finds your PDFs and lets you tag them with colors.
-   **Folders inside Folders 📂**: Just like on your computer, you can create folders for your books!
-   **Long-Term Memory 🧠**: The app remembers where you left off on up to **1,000 different books**.
-   **Progress Tracker 📊**: Every book card shows a progress bar and percentage, so you know how much you've read.
-   **Backup & Share 📤**: Export your entire library—including all your folders, PDF files, and colorful categories—into a single file.
-   **AI Assistant 🤖**: Circle any part of a page and let AI explain it to you instantly!
-   **Smart Renaming ✏️**: Rename any book in the app! It renames the physical file on your phone and automatically moves your progress and colors to the new name.
-   **Modern File Info ℹ️**: Tap the question mark icon in the reader to see file title, size, and location in a beautiful blurred overlay.

---

## 📂 Where are my files? (Device Storage)

The app creates a special folder on your phone:
- **Location**: `Internal Storage > Documents > PDF Flip`
- **Adding Books**: Simply copy your PDFs into this folder, and they will automatically appear in your Library!

---

## 💡 Code Breakdown (Explained for Beginners) 🎓

If you are new to coding, think of the app like a house with different rooms:

### 🏠 The Architecture (File Sizes)
- **`MainActivity.kt`** (256 lines): The **Front Door**. It manages which screen you are looking at (Library, Reader, or Settings).
- **`LibraryPage.kt`** (1,660 lines): The **Bookshelf**. It scans your folders for PDFs and remembers your tags and folder structure.
- **`ReaderPage.kt`** (1,524 lines): The **Reading Chair**. This is where the magic happens! It renders the PDF, handles page flips, and saves your progress.
- **`SettingsPage.kt`** (1,007 lines): The **Control Panel**. Where you change the theme, set up AI, and manage your backups.
- **`AiAssistantPage.kt`** (179 lines): The **AI Brain**. This is where the app talks to Google Gemini to explain parts of your book.

### 🔍 Deep Dive: How the "Resume Reading" Works 📍

Have you ever wondered how the app remembers you were on page 42 of a book you haven't opened in weeks, even if it was buried deep in folders? Here is the simple explanation:

1.  **Unique Digital IDs**: Instead of just using the book's name, the app uses its **Full Path** (like `/Documents/School/History.pdf`). This means even if you have two books named "Chapter 1", the app won't get them confused!
2.  **Saving the Spot**: Every time you flip a page in **`ReaderPage.kt`**, the app writes a note in `recent_data.txt`: *"Full Path | Time | Current Page | Total Pages"*.
3.  **The 40-Book Display**: The "Recent Activity" sidebar now shows your last **40 books**, so you can jump back into any of your recent projects quickly.
4.  **Global History 🌍**: You can now access your "Recent Activity" from **any folder**. Look for the History icon in the top-right corner!
5.  **Smart Navigation 📍**: If you open a book from your history, PDFFlip automatically remembers which folder it belongs to. When you press "Back," you'll go straight to that folder, not the home screen.
6.  **The 1,000 Book Memory**: Even if a book falls off the "Recent" list, PDFFlip still keeps its progress in its 1,000-note long-term memory.
7.  **Correct Sorting**: PDFFlip uses **Unix Timestamps** (the exact millisecond you opened a book) to make sure your most recently read books always stay at the very top of the list.
8.  **Reset Feature 🗑️**: If you want to start over, you can tap the **Delete Sweep** icon in the "Recent Activity" sidebar. This instantly wipes the memory for every book, making them disappear from the recent list and clearing all progress bars.

### 🖊️ How the Custom Drawing Pen Works (New Feature!) 🎨

Ever wanted a thicker marker for highlighting or a thin pen for tiny notes? Here is how the app handles your artistic choices:

1.  **Stroke Data (The Memory)**: When you draw, the app doesn't just save a picture. It saves a "List of Points" along with the **color** and **thickness** you chose. This is like the app remembering exactly how you moved your hand and which pen you were holding.
2.  **Normalized Points 📏**: Because phones have different screen sizes, the app saves your drawings in "Percentages" (0 to 1). So if you draw in the middle of the page on a small phone, it stays in the middle on a big tablet!
3.  **Real-Time Rendering 🖌️**: As you move the slider in the "Pen Size" dialog, the app uses a **Canvas** to show you a live preview. It's like testing a marker on a scrap piece of paper before writing in your book.
4.  **Vector Output 📄**: When you hit "Save", the app talks to a library called **PdfBox**. It translates your finger movements into professional PDF "Paths". If you chose a 20px thickness, the app tells the PDF: *"Make this line exactly 20 units wide"*. This ensures your notes look sharp even if you zoom in 500%!

### 🛠️ Key Coding Concepts

-   **@Composable**: Think of these as LEGO bricks. We build a "Book Card" brick and a "Top Bar" brick, then snap them together to make a screen.
-   **State**: This is the app's "Short-Term Memory". If you type something in the search bar, the "State" remembers those letters so it can filter your books.
-   **LaunchedEffect**: This is like a "Trigger". We use it to say: *"When the user flips a page, trigger the code that saves their progress to the file."*

---

### 💁‍♂️💁‍♀️ Let's contribute and develop this "PDF Flip" app for the community of students who need a high-quality, free way to study.

Happy Reading! 📖🚀
