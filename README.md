# FastFinder

**FastFinder** is a Windows application that helps you quickly search files and folders within your computer's internal storage. It indexes your files in the background, allowing you to perform searches even while the index is being updated. With customizable search features and the ability to filter results, FastFinder makes it easy to find exactly what you're looking for.

## Features

- **File and Folder Search**: Easily search through files and folders in your computer’s internal storage.
- **Background Indexing**: On the first run, FastFinder creates a database of files and folders in the background.
- **Live Search During Indexing**: Even while the index is being updated, you can still search using the old database.
- **Custom Directory Search**: Perform searches within specific directories while the database is being indexed or updated.
- **Filters for File Types and Folders**: Narrow down your search by filtering for files or folders.
- **File Search Filters**: Additional filters for file searches based on extensions, size, date, etc.

## Screenshots

![FastFinder Screenshot 1](path/to/screenshot1.png)
*Search Interface*

![FastFinder Screenshot 2](path/to/screenshot2.png)
*Search Results with Filters*

## Installation

### Prerequisites

Make sure you have the following installed:
- Windows 10 or later
- .NET Framework (if applicable) or the required dependencies for your app

### Steps

1. **Download the Latest Release**
   - Go to the [FastFinder Releases](https://github.com/yourusername/FastFinder/releases) page.
   - Download the latest version of the `.exe` file.

2. **Run the Installer**
   - Double-click the downloaded file to run the installer.
   - Follow the prompts to complete the installation process.

3. **Launch the Application**
   - After installation, open FastFinder from your Start menu or desktop shortcut.
   - On the first run, the app will automatically index your files in the background.

## Usage

1. **First Run: Indexing Database**
   - The first time you run FastFinder, it will index your computer’s files and folders in the background. This may take some time, depending on the size of your storage.

2. **Search Files or Folders**
   - Once the database is created, you can use the search bar to find files or folders. FastFinder will display results as you type.

3. **Custom Search**
   - To search in a specific directory, go to the **Custom Search** tab.
   - Select the directory you want to search within, and enter your search query.

4. **Filters**
   - Use the filter options to narrow down results. You can filter by:
     - **Files or Folders**
     - **File Type (e.g., .txt, .jpg, etc.)**
     - **File Size**
     - **Date Modified**
     
5. **Updating the Database**
   - To update the database, click the **Update Database** button. While the database is being updated, you can still search using the old index.

## Contributing

1. Fork the repository.
2. Create a new branch (`git checkout -b feature/your-feature`).
3. Make your changes.
4. Commit your changes (`git commit -am 'Add your feature'`).
5. Push to the branch (`git push origin feature/your-feature`).
6. Create a new Pull Request.

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

---

Thank you for using FastFinder! If you encounter any issues or have suggestions for new features, feel free to open an issue or contribute to the project.

