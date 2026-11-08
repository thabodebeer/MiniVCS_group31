# MiniVCS – Java Version Control System

**SDN260S Software Design II – Group 31 (CPUT)**  
**Final Version – Check-In 3**  
**Apache NetBeans – Java with Ant | Java 24**

## Introduction

MiniVCS is a simple version control system developed using Java. It helps users keep track of changes made to files and save different versions of their work.

The system works in a similar way to Git. Users can add files, save commits, view previous versions, compare changes and restore files to an earlier version.

MiniVCS has two interfaces:

* A command-line interface (CLI).
* A graphical user interface (GUI) built using Java Swing.

Both interfaces use the same `Repository` class to perform the main version control operations.

The aim of the project is to make it easier to manage file versions without creating multiple copies of the same file. MiniVCS uses SHA-256 hashing to identify file contents and avoid storing the same content more than once. It also keeps a record of actions and does not delete previous commits when a rollback is performed.

## 1\. Project Development

During Check-In 2, the project had a working command-line interface where users could enter commands to manage their files.

For Check-In 3, a Swing graphical interface was added. Users can now perform the same operations using buttons and lists instead of typing commands.

The GUI was added without changing the existing core version control logic. Both interfaces communicate with the `Repository` class, which handles the main operations.

## 2\. Main Features

MiniVCS includes the following features:

* **Initialise repository:** Creates a new MiniVCS repository inside a selected folder.
* **Stage files:** Adds selected files, folders or all changes to the staging area.
* **Unstage files:** Removes selected files from staging.
* **Check status:** Shows staged, modified, deleted and untracked files.
* **Commit changes:** Saves a snapshot of the project with a commit message.
* **View history:** Displays previous commits, starting with the newest.
* **View commit details:** Shows information about a selected commit.
* **Compare files:** Shows differences between two commits or between a commit and the current working folder.
* **Rollback:** Restores the working folder to an earlier commit. A backup is made before files are changed.
* **Activity log:** Keeps a record of actions and errors.
* **Ignore rules:** Allows certain files and folders to be ignored using `.minivcsignore`.

Commit IDs can be entered using the full ID or a shortened version of at least four characters. Users can also use `HEAD` to refer to the latest commit.

## 3\. Requirements

To run the project, the following software is needed:

* JDK 24 or newer.
* Apache NetBeans 28 or a compatible version that supports Java with Ant projects.
* JUnit 4 and Hamcrest for running the tests.

The project does not require an internet connection. MiniVCS uses Java's built-in libraries for file handling, hashing, collections, saving data and creating the graphical interface.

## 4\. Building and Running the Project

### 4.1 Running in Apache NetBeans

1. Open Apache NetBeans.
2. Select **File → Open Project**.
3. Select the `MiniVCS` folder. Make sure it contains `build.xml` and the `nbproject` folder.
4. Right-click the project and select **Clean and Build**.
5. Wait for the message `BUILD SUCCESSFUL`.

After building, the project creates a JAR file inside the `dist` folder.

### 4.2 Running the GUI

1. Open the MiniVCS project in NetBeans.
2. Press **F6** or select **Run Project**.
3. The graphical interface will open.
4. The main class is `minivcs.ui.MainGUI`.

### 4.3 Running the CLI

1. Open **Source Packages**.
2. Find the `minivcs` package.
3. Right-click `Main.java`.
4. Select **Run File** or press **Shift + F6**.
5. Click inside the Output window.
6. Enter the project folder path and start using the commands.

### 4.4 Running the Tests

1. Right-click the MiniVCS project.
2. Select **Test** or press **Alt + F6**.
3. NetBeans will run the test classes.
4. The expected result is 51 passing tests.

If NetBeans reports a missing library, check the project properties and make sure JUnit 4.13.2 and Hamcrest 1.3 are available under the test libraries.

### 4.5 Running from Command Prompt

After building the project, these commands can be used:

```bash
java -jar dist\\\\\\\\MiniVCS.jar
```

This opens the GUI.

```bash
java -jar dist\\\\\\\\MiniVCS.jar C:\\\\\\\\demo\\\\\\\\project
```

This opens the GUI with a project folder already selected.

```bash
java -cp dist\\\\\\\\MiniVCS.jar minivcs.Main
```

This starts the command-line menu.

```bash
java -cp dist\\\\\\\\MiniVCS.jar minivcs.Main --repo C:\\\\\\\\demo\\\\\\\\project status
```

This runs one command and then exits. The program returns `0` if the command succeeds and `1` if an error occurs.

**Note:** Copy the `sample-project` folder to another location, such as the Desktop, before using it for testing. Avoid creating a test repository inside the MiniVCS project folder.

## 5\. Using the Command-Line Interface

The CLI allows users to manage their project by entering commands.

* `init` – creates a MiniVCS repository.
* `add .` – stages all changes in the project.
* `add <path>` – stages a selected file or folder.
* `unstage <path>` – removes a file from staging.
* `status` – displays the current file changes.
* `commit <message>` – saves the staged changes.
* `log` – displays the commit history.
* `show <id>` – displays details about a commit.
* `diff` – compares the current working folder with the latest commit.
* `diff <id>` – compares the working folder with a selected commit.
* `diff <id1> <id2>` – compares two commits.
* `rollback <id>` – restores the working folder to a selected commit.
* `activity` – displays the activity log.
* `author <name>` – changes the author name.
* `help` – displays the available commands.
* `exit` – closes the program.

For example, a user can initialise a repository, add files and save the first version:

```text
minivcs> init
minivcs> add .
minivcs> commit First version
minivcs> status
minivcs> log
```

If a file is changed later, the user can check the differences using `diff`, stage the updated file and create another commit. The user can also roll back to an earlier commit to restore an older version.

When entering file paths that contain spaces, place the path inside quotation marks:

```text
add "My Documents/report.docx"
```

## 6\. Using the Graphical User Interface

The GUI was developed using Java Swing. It provides buttons, text fields, lists and a table to make the system easier to use.

### Project Folder

At the top of the window, users can enter a project folder path or use **Browse** to select a folder. The **Open** button opens the selected folder. If it is not already a MiniVCS repository, the user can select **Initialise repository**.

### Working Folder Changes

The left side displays files that have been changed. Different colours help identify the file status:

* Green – staged files.
* Orange – modified files.
* Red – deleted files.
* Grey – untracked files.

Users can select files and use **Stage selected**, **Stage all**, **Unstage selected** or **Status**.

### Commit Section

Users can enter a commit message and select **Commit** to save the staged changes. The author name can also be entered in the field at the top of the window.

### Commit History

The right side displays the commit history, with the newest commit shown first. Users can select a commit and use:

* **Show commit details** – displays information about a selected commit.
* **Diff selected vs working folder** – compares a commit with the current files.
* **Diff two selected commits** – compares two selected commits. Hold Ctrl while selecting the two commits.
* **Roll back to selected** – restores the working folder to the selected commit after showing a confirmation message.

### Output Area

The output section displays messages from the system, including successful operations and errors. **Activity log** displays recorded activities, while **Clear output** removes the messages currently displayed.

The GUI uses `Theme.java` to manage its colours and fonts. It also uses `SwingWorker` to run slower tasks in the background so that the window does not freeze.

## 7\. How MiniVCS Stores Files

When a repository is created, MiniVCS creates a hidden folder called `.minivcs` inside the selected project folder.

This folder stores the information needed to manage the repository:

* **objects:** Stores file contents using their SHA-256 hashes. Identical file contents are only stored once.
* **metadata/history.ser:** Stores the commit history and the current HEAD.
* **metadata/index.txt:** Stores information about files that have been staged.
* **logs/activity.log:** Keeps a record of actions and errors.
* **backups:** Stores copies of files before they are changed or removed during a rollback.

The `.minivcsignore` file can be used to specify files and folders that should be ignored. Some supported examples are:

```text
# comment
\\\\\\\*.log
temp?.txt
build/
docs/\\\\\\\*.tmp
```

The system supports basic ignore rules, but not every feature available in Git's `.gitignore` file. Ignore rules only apply to untracked files. Files that are already tracked remain tracked.

## 8\. How the System Works

### 8.1 Snapshots and Commits

MiniVCS saves a snapshot of the project whenever a commit is created. Each snapshot contains file paths and their SHA-256 hashes. The actual file contents are stored separately in the objects folder.

This means unchanged files do not need to be stored again in every commit.

### 8.2 Staging

When a user adds a file, MiniVCS checks its contents and creates a hash. If the file is different from the version in the latest commit, its information is added to the staging area.

Deleted files are also recorded so that the next commit can remove them from the snapshot.

### 8.3 Creating a Commit

When a user creates a commit, MiniVCS uses the latest snapshot and applies the staged changes to create a new snapshot.

A commit stores information such as:

* Commit ID.
* Commit message.
* Author.
* Date and time.
* Parent commit ID.
* File paths and their hashes.

The staging area is cleared after the commit history has been saved successfully.

### 8.4 File Deletions and Renames

When a file is deleted, it is removed from the next snapshot. Its previous content remains stored so that it can still be restored.

Renamed files are treated as a deletion of the old file and an addition of the new file. If the contents are the same, MiniVCS does not need to store another copy.

Empty folders are not tracked because the system stores file contents rather than empty directories.

### 8.5 Commit IDs

Each commit receives an ID calculated using SHA-256. The ID is based on information such as the parent commit, author, timestamp, message and file information.

When the history is loaded, the system checks the commit IDs again to detect changes or invalid data.

### 8.6 Rollback

Rollback allows users to restore their working folder to an earlier commit.

Before changing files, MiniVCS checks the stored objects and creates backups of files that will be overwritten or removed. The rollback is then saved as a new commit, so the previous commit history is not deleted.

Untracked files are not changed, and staged changes are cleared.

If the user rolls back to the latest commit, local changes are discarded after being backed up, and no new commit is created.

### 8.7 Checking File Status

The status feature compares the latest commit, the staging area and the current files in the working folder. It uses this information to identify staged, modified, deleted and untracked files.

When there are many files, hashing can be done using multiple threads to improve performance.

## 9\. Project Design

MiniVCS was divided into different packages to keep the code organised. Each package contains classes responsible for a specific part of the system.

### 9.1 Main Package

* `Main` – starts the command-line version of the program.

### 9.2 Core Package

This package contains the main version control operations.

* `Repository` – handles initialising, adding files, committing, checking status, viewing history, comparing files and rollback.
* `StagingArea` – manages files that have been added but not yet committed.
* `HistoryManager` – manages the commit history and keeps track of the latest commit.
* `Commit` – stores information about each commit and its file snapshot.
* `RepositoryPaths` – manages repository paths and checks that files are accessed safely.
* `StatusReport` – stores information about the current file changes.
* `RollbackResult` – stores the result of a rollback operation.
* `FileChange` – stores information about an individual file change.
* `ChangeType` – identifies the type of file change.
* `HistoryData` – stores the commit history in a versioned format.
* `VcsException` – handles errors related to MiniVCS operations.

### 9.3 Storage Package

* `ObjectStore` – provides a general way of storing and retrieving objects.
* `BlobStore` – stores and restores file contents using their hashes.

### 9.4 Scan Package

* `FileScanner` – scans folders and files, checks for changes and calculates file hashes.
* `IgnoreRules` – reads and applies the rules from `.minivcsignore`.

### 9.5 Diff Package

* `DiffEngine` – compares files and identifies lines that have been added, removed or changed.

### 9.6 Utility Package

* `HashUtil` – calculates and checks SHA-256 hashes.
* `ActivityLogger` – records system activities and errors.

### 9.7 UI Package

* `CLIMenu` – provides the command-line menu.
* `CommandParser` – reads and separates commands entered by the user.
* `MainGUI` – creates the graphical interface and handles button actions.
* `Theme` – manages the colours and fonts used in the GUI.

### 9.8 UML Class Diagram

The class diagram shows how the different classes are connected and how they work together.

The `Repository` class is the main part of the system. Both the CLI and GUI use it to perform version control operations. It also communicates with the storage, scanning, history and diff classes.

The design keeps the user interface separate from the main version control logic.

The UML diagram can be viewed by copying the Mermaid code from the software design document into [Mermaid Live Editor](https://mermaid.live).

## 10\. Assignment Requirements

The project includes different programming concepts and techniques covered in the module.

### Object-Oriented Programming

The system uses encapsulation, abstraction, inheritance and polymorphism. For example, `ObjectStore<T>` is an abstract class, while `BlobStore` extends it and provides the methods needed to store file contents.

### Modular Programming

The project is divided into seven packages. Each class has a specific responsibility, making the code easier to understand and maintain.

### File Handling and Streams

Java file handling and streams are used to read, write, store and restore files. `HashUtil`, `BlobStore`, `StagingArea` and `ActivityLogger` handle these operations.

### Collections and Generics

The project uses Java collections such as `ArrayList`, `HashMap`, `TreeMap`, `TreeSet`, `List` and `Map`. Generics are also used in the `ObjectStore<T>` class.

### Serialization

Serialization is used to save and load commit history. The `HistoryManager` class uses an object input filter and validation when loading saved history data.

### Algorithms

The system uses SHA-256 hashing, recursive folder scanning and the Longest Common Subsequence (LCS) algorithm for comparing files.

### Logging and Reporting

The `ActivityLogger` class records actions and errors. The `StatusReport` and `RollbackResult` classes provide information about system operations.

### Testing

The project includes 51 JUnit tests covering normal operations and different error cases.

### Concurrency

The file scanner uses an `ExecutorService` thread pool for hashing multiple files. The GUI uses `SwingWorker` so slower operations can run in the background.

## 11\. Algorithms and Performance

MiniVCS uses different algorithms to perform its operations:

* **File hashing:** SHA-256 calculates a hash for each file. The time taken depends on the file size.
* **Folder scanning:** A recursive depth-first search moves through folders and finds files.
* **Status checking:** The system scans files, calculates hashes and compares them with the latest commit and staging area.
* **Adding and committing:** Files are hashed and new content is stored before the commit is created.
* **Finding commits:** The system searches the commit history using the full ID or a shortened ID.
* **Viewing history:** The system follows the parent commit links starting from HEAD.
* **Comparing files:** The LCS algorithm finds added and removed lines.
* **Rollback:** The system compares the current files with the selected commit and restores the files that have changed.

There are some limits to the diff feature:

* The LCS table is limited to 4,000,000 cells.
* Files larger than 5 MB are summarised instead of showing a line-by-line comparison.
* Binary files and files that are not valid UTF-8 are reported as binary differences.

These limits help prevent the program from using too much memory when comparing large files.

## 12\. Testing

Testing was done using JUnit 4 in Apache NetBeans. The tests cover file handling, hashing, repository operations, file comparisons and rollback.

The following test classes were used:

* **HashUtilTest:** Checks SHA-256 calculations, file hashing and hash validation.
* **IgnoreRulesTest:** Checks ignore rules, comments, blank lines and folder patterns.
* **DiffEngineTest:** Checks file comparisons, added and removed lines, binary files and large files.
* **RepositoryPathsTest:** Checks file paths and makes sure invalid paths are rejected.
* **RepositoryTest:** Checks repository creation, staging, commits, status, history, rollback, file storage and error handling.
* **CLIMenuTest:** Checks command parsing and the command-line menu.

### Testing Results

All 51 JUnit tests passed with 0 failures and 0 errors.

The CLI was also tested by performing operations such as initialising a repository, adding files, creating commits, comparing file versions, viewing commit history, performing rollback and testing error cases.

The GUI was tested by using the main buttons to stage files, create commits, compare changes and roll back to an earlier version.

## 13\. Known Limitations

Although MiniVCS supports the main version control features, there are still some limitations:

* The system only supports one branch. Branching and merging are not available.
* It works locally and does not support pushing or pulling files from remote repositories.
* The ignore rules only support a small part of Git's `.gitignore` features.
* Stored objects are not compressed.
* Unused objects are not automatically removed.
* Renamed files are shown as a deletion and an addition.
* Empty folders are not tracked.
* Rollback does not remove folders that become empty.
* If a rollback fails while changing files, some files may already have been changed. Backups are created before the operation.
* Commit history is saved using Java serialization, so the saved file is not easy to read manually and is Java-specific.

## 14\. Project Structure

The project is organised into the following folders:

```text
MiniVCS/
├── build.xml
├── manifest.mf
├── nbproject/
├── README.md
├── sample-project/
├── src/
│   └── minivcs/
│       ├── Main.java
│       ├── core/
│       ├── storage/
│       ├── scan/
│       ├── diff/
│       ├── util/
│       └── ui/
└── test/
    └── minivcs/
```

The `src` folder contains the main Java source code, while the `test` folder contains the JUnit test classes.

The `nbproject` folder contains the NetBeans project settings. The `sample-project` folder contains files that can be used when demonstrating the system.

After running **Clean and Build**, NetBeans also creates the `build` and `dist` folders.

## 15\. Team Members and Responsibilities

MiniVCS was developed by Group 31. Each team member was responsible for different parts of the project.

* **Imanathi Ngqaka – Lead Developer and Integration:** Worked on the Repository class, ActivityLogger, project setup and integration.
* **Tamia Moonsamy – Core Storage Developer:** Worked on HistoryManager, Commit, ObjectStore and BlobStore.
* **Xola Rengo – Change Detection Developer:** Worked on StagingArea, FileScanner, IgnoreRules, HashUtil and status checking.
* **Ekageng Mokgothadi – Diff and Rollback Developer:** Worked on DiffEngine and rollback functionality.
* **Thabo De Beer – UI Developer:** Worked on CLIMenu and the Swing MainGUI.
* **Mongezi Mthembu – Testing and Debugging Engineer:** Worked on test folders, normal and edge cases, and bug reports.
* **Lebohang Motsoeneng – Documentation Lead:** Worked on the design document, README and GA2 and GA7 reflection.

## Conclusion

MiniVCS was developed to provide a simple way of managing file versions using Java. The system allows users to save commits, track changes, compare files and restore earlier versions.

The project also gave the group an opportunity to apply concepts such as object-oriented programming, file handling, data structures, algorithms, recursion, hashing, serialization and testing.

The final version includes both a command-line interface and a graphical interface, making it possible to use the same version control features in two different ways.

