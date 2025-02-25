package com.brostoffed.contextbuilder.toolwindow

import com.brostoffed.contextbuilder.ContextHistoryPersistentState
import com.brostoffed.contextbuilder.HistoryEntryBean
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.SearchTextField
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.treeStructure.Tree
import com.knuddels.jtokkit.Encodings
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.awt.dnd.DnDConstants
import java.awt.dnd.DropTarget
import java.awt.dnd.DropTargetDropEvent
import java.io.File
import java.io.FileWriter
import java.nio.charset.StandardCharsets
import javax.swing.*
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeCellRenderer
import javax.swing.tree.DefaultTreeModel

/**
 * A tool window panel replicating the old ContextHistoryDialog layout.
 * Supports drag-and-drop to add new history entries.
 */
class ContextHistoryToolWindowPanel(private val project: Project) : JBPanel<JBPanel<*>>(BorderLayout(8, 8)) {

    companion object {
        var instance: ContextHistoryToolWindowPanel? = null
    }

    // ------------------- History Components ------------------- //
    private val historyListModel = DefaultListModel<HistoryEntryBean>()
    private val historyList = JBList(historyListModel)
    private val historySearchField = SearchTextField().apply {
        toolTipText = "Search timestamps or file paths"
    }

    // Sorting combo box
    private val sortComboBox = JComboBox(arrayOf("Date Desc", "Name Asc")).apply {
        selectedIndex = 0 // Default to "Date Desc"
    }

    // Buttons
    private val clearButton = JButton("Clear")
    private val exportButton = JButton("Export")
    private val importButton = JButton("Import")
    private val regenerateButton = JButton("Regenerate")
    private val renameButton = JButton("Rename")

    // ------------------- File Tree & Preview Components ------------------- //
    private val fileTreeRoot = DefaultMutableTreeNode("Files")
    private val fileTreeModel = DefaultTreeModel(fileTreeRoot)
    private val fileTree = Tree(fileTreeModel).apply {
        isRootVisible = false
        cellRenderer = object : DefaultTreeCellRenderer() {
            override fun getTreeCellRendererComponent(
                tree: JTree?,
                value: Any?,
                selected: Boolean,
                expanded: Boolean,
                leaf: Boolean,
                row: Int,
                hasFocus: Boolean
            ): java.awt.Component {
                val comp = super.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus)
                if (comp is JLabel) {
                    comp.border = null
                }
                return comp
            }
        }
    }
    private val fileTreeSearchField = SearchTextField().apply {
        toolTipText = "Filter the file tree"
    }

    // Preview text area
    private val previewArea = JTextArea().apply {
        isEditable = false
        lineWrap = true
        wrapStyleWord = true
        toolTipText = "Preview of regenerated markdown"
    }

    // Token count label
    private val tokenCountLabel = JLabel("Token count: -")

    init {
        instance = this  // Save static reference for refresh

        border = BorderFactory.createEmptyBorder(8, 8, 8, 8)
        minimumSize = Dimension(800, 600)
        layout = BorderLayout(8, 8)

        add(buildMainSplitPanel(), BorderLayout.CENTER)
        add(buildBottomPanel(), BorderLayout.SOUTH)

        // Load existing history and set up listeners
        loadHistory()
        setupListeners()

        // Enable drag and drop on the entire panel.
        setupDragAndDrop(this)
    }

    /**
     * Return a path relative to the project base directory (if available).
     */
    private fun getRelativePath(file: File): String {
        val projectBase = project.basePath ?: return file.absolutePath
        return try {
            val projectRoot = File(projectBase).canonicalPath.replace("\\", "/")
            val droppedPath = file.canonicalPath.replace("\\", "/")
            if (droppedPath.startsWith(projectRoot)) {
                droppedPath.substring(projectRoot.length).trimStart('/')
            } else {
                droppedPath
            }
        } catch (ex: Exception) {
            file.absolutePath
        }
    }

    /**
     * Recursively collect all files from a folder or file.
     */
    private fun collectAllPathsRecursively(file: File, results: MutableList<String>) {
        if (file.isDirectory) {
            file.listFiles()?.forEach { child ->
                collectAllPathsRecursively(child, results)
            }
        } else {
            results.add(getRelativePath(file))
        }
    }

    private fun setupDragAndDrop(component: JComponent) {
        component.dropTarget = object : DropTarget() {
            override fun drop(dtde: DropTargetDropEvent) {
                try {
                    dtde.acceptDrop(DnDConstants.ACTION_COPY_OR_MOVE)
                    val transferable = dtde.transferable
                    if (transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                        val droppedFiles = transferable.getTransferData(DataFlavor.javaFileListFlavor) as List<File>
                        val filePaths = mutableListOf<String>()
                        for (droppedFile in droppedFiles) {
                            collectAllPathsRecursively(droppedFile, filePaths)
                        }
                        val now = java.time.LocalDateTime.now()
                            .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))

                        // Create a new entry with default 'timestamp' but also a creation time
                        val newEntry = HistoryEntryBean(
                            timestamp = now,
                            filePaths = filePaths,
                            createdAt = System.currentTimeMillis()
                        )
                        ContextHistoryPersistentState.getInstance().state.entries.add(newEntry)
                        loadHistory()
                        NotificationGroupManager.getInstance()
                            .getNotificationGroup("Context Builder")
                            .createNotification("New history entry added from drop", NotificationType.INFORMATION)
                            .notify(project)
                    }
                    dtde.dropComplete(true)
                } catch (ex: Exception) {
                    ex.printStackTrace()
                    dtde.dropComplete(false)
                }
            }
        }
    }

    private fun buildBottomPanel(): JPanel {
        val bottomPanel = JPanel()
        bottomPanel.add(sortComboBox)
        bottomPanel.add(regenerateButton)
        bottomPanel.add(renameButton)
        bottomPanel.add(clearButton)
        bottomPanel.add(exportButton)
        bottomPanel.add(importButton)
        return bottomPanel
    }

    private fun buildMainSplitPanel(): JSplitPane {
        // Left: history panel
        val historyPanel = JBPanel<JBPanel<*>>(BorderLayout(5, 5)).apply {
            val topPanel = JBPanel<JBPanel<*>>(BorderLayout(5, 5)).apply {
                add(JLabel("History Search:"), BorderLayout.WEST)
                add(historySearchField, BorderLayout.CENTER)
            }
            val listScroll = JBScrollPane(historyList).apply {
                preferredSize = Dimension(200, 400)
            }
            add(topPanel, BorderLayout.NORTH)
            add(listScroll, BorderLayout.CENTER)
        }

        // Right: vertical split for file tree + preview
        val fileTreePanel = JBPanel<JBPanel<*>>(BorderLayout(5, 5)).apply {
            val fileTreeTop = JBPanel<JBPanel<*>>(BorderLayout(5, 5)).apply {
                add(JLabel("File Tree Filter:"), BorderLayout.WEST)
                add(fileTreeSearchField, BorderLayout.CENTER)
            }
            val treeScroll = JBScrollPane(fileTree).apply {
                minimumSize = Dimension(300, 300)
            }
            add(fileTreeTop, BorderLayout.NORTH)
            add(treeScroll, BorderLayout.CENTER)
        }

        val previewPanel = JBPanel<JBPanel<*>>(BorderLayout(5, 5)).apply {
            val headerPanel = JPanel(BorderLayout(5, 5)).apply {
                add(JLabel("Preview:"), BorderLayout.WEST)
                add(tokenCountLabel, BorderLayout.EAST)
            }
            add(headerPanel, BorderLayout.NORTH)
            add(JBScrollPane(previewArea), BorderLayout.CENTER)
        }

        val verticalSplit = JSplitPane(JSplitPane.VERTICAL_SPLIT, fileTreePanel, previewPanel).apply {
            dividerLocation = 300
            resizeWeight = 0.5
        }
        return JSplitPane(JSplitPane.HORIZONTAL_SPLIT, historyPanel, verticalSplit).apply {
            dividerLocation = 250
            resizeWeight = 0.0
        }
    }

    private fun setupListeners() {
        historySearchField.textEditor.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent) = filterHistory(historySearchField.text)
            override fun removeUpdate(e: DocumentEvent) = filterHistory(historySearchField.text)
            override fun changedUpdate(e: DocumentEvent) = filterHistory(historySearchField.text)
        })

        historyList.addListSelectionListener {
            val entry = historyList.selectedValue
            if (entry != null) {
                updateFileTree(entry)
                val md = regenerateMarkdownForPaths(entry.filePaths)
                previewArea.text = md
                tokenCountLabel.text = "Token count: ${countTokens(md)}"
            } else {
                clearFileTree()
                previewArea.text = ""
                tokenCountLabel.text = "Token count: -"
            }
        }

        fileTreeSearchField.textEditor.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent) = filterFileTree(fileTreeSearchField.text)
            override fun removeUpdate(e: DocumentEvent) = filterFileTree(fileTreeSearchField.text)
            override fun changedUpdate(e: DocumentEvent) = filterFileTree(fileTreeSearchField.text)
        })

        regenerateButton.addActionListener {
            val selected = historyList.selectedValue ?: run {
                Messages.showErrorDialog("No history entry selected.", "Error")
                return@addActionListener
            }
            val md = regenerateMarkdownForPaths(selected.filePaths)
            copyToClipboard(md)
            val tokens = countTokens(md)
            tokenCountLabel.text = "Token count: $tokens"
            NotificationGroupManager.getInstance()
                .getNotificationGroup("Context Builder")
                .createNotification("Markdown copied to clipboard. Token count: $tokens", NotificationType.INFORMATION)
                .notify(project)
            previewArea.text = md
        }

        // Rename button: let user rename the selected history entry
        renameButton.addActionListener {
            val selected = historyList.selectedValue ?: run {
                Messages.showErrorDialog("No history entry selected.", "Error")
                return@addActionListener
            }
            val newName = Messages.showInputDialog(
                this,
                "Enter a new name for this history entry:",
                "Rename History Entry",
                Messages.getQuestionIcon(),
                selected.customName,
                null
            )
            if (!newName.isNullOrBlank()) {
                selected.customName = newName
                // Re-load to reflect new name (and keep sorting order)
                loadHistory()
            }
        }

        clearButton.addActionListener { clearHistory() }
        exportButton.addActionListener { exportHistory() }
        importButton.addActionListener { importHistory() }

        // When user changes sort mode, re-load the list
        sortComboBox.addActionListener {
            loadHistory()
        }
    }

    /**
     * Loads and sorts the history list based on the selected sorting mode.
     */
    fun loadHistory() {
        historyListModel.clear()
        val allEntries = ContextHistoryPersistentState.getInstance().state.entries

        val sorted = when (sortComboBox.selectedItem as String) {
            "Name Asc" -> allEntries.sortedBy { it.customName.ifBlank { it.timestamp }.lowercase() }
            else -> allEntries.sortedByDescending { it.createdAt } // default = Date Desc
        }

        // Filter by the current search text
        val query = historySearchField.text
        val filtered = sorted.filter { entry ->
            entry.customName.contains(query, ignoreCase = true)
                    || entry.timestamp.contains(query, ignoreCase = true)
                    || entry.filePaths.any { it.contains(query, ignoreCase = true) }
        }

        filtered.forEach { historyListModel.addElement(it) }
    }

    private fun filterHistory(query: String) {
        loadHistory() // We already do filtering in loadHistory
    }

    private fun clearHistory() {
        val result = Messages.showYesNoDialog("Are you sure you want to clear all history?", "Clear History", null)
        if (result == Messages.YES) {
            ContextHistoryPersistentState.getInstance().state.entries.clear()
            loadHistory()
        }
    }

    private fun exportHistory() {
        val descriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor()
        descriptor.title = "Export History"
        val folder: VirtualFile? = FileChooser.chooseFile(descriptor, project, null)
        if (folder != null) {
            try {
                val exportFile = File(folder.path, "history_export.json")
                FileWriter(exportFile, StandardCharsets.UTF_8).use { writer ->
                    val gson = Gson()
                    val allEntries = ContextHistoryPersistentState.getInstance().state.entries
                    gson.toJson(allEntries, writer)
                }
                Messages.showInfoMessage("History exported to ${exportFile.absolutePath}", "Export Successful")
            } catch (ex: Exception) {
                Messages.showErrorDialog("Error exporting history: ${ex.message}", "Export Error")
            }
        }
    }

    private fun importHistory() {
        val descriptor = FileChooserDescriptorFactory.createSingleFileDescriptor("json")
        descriptor.title = "Import History"
        val file: VirtualFile? = FileChooser.chooseFile(descriptor, project, null)
        if (file != null) {
            try {
                val importFile = File(file.path)
                val gson = Gson()
                val type = object : TypeToken<MutableList<HistoryEntryBean>>() {}.type
                val imported: MutableList<HistoryEntryBean> =
                    gson.fromJson(importFile.reader(StandardCharsets.UTF_8), type)
                ContextHistoryPersistentState.getInstance().state.entries.addAll(imported)
                loadHistory()
                Messages.showInfoMessage("History imported successfully.", "Import Successful")
            } catch (ex: Exception) {
                Messages.showErrorDialog("Error importing history: ${ex.message}", "Import Error")
            }
        }
    }

    private fun updateFileTree(entry: HistoryEntryBean) {
        val newRoot = buildTreeModel(entry.filePaths)
        fileTree.model = DefaultTreeModel(newRoot)
        expandAllNodes()
    }

    private fun clearFileTree() {
        fileTree.model = DefaultTreeModel(DefaultMutableTreeNode("Files"))
    }

    private fun filterFileTree(query: String) {
        val selectedEntry = historyList.selectedValue ?: return
        val root = buildTreeModel(selectedEntry.filePaths)
        filterTreeNodes(root, query)
        fileTree.model = DefaultTreeModel(root)
        expandAllNodes()
    }

    private fun buildTreeModel(paths: List<String>): DefaultMutableTreeNode {
        val root = DefaultMutableTreeNode("Files")
        for (path in paths) {
            val parts = path.split("/", "\\").filter { it.isNotEmpty() }
            var current = root
            for (part in parts) {
                val childNode = current.children().toList().find {
                    (it as DefaultMutableTreeNode).userObject == part
                } as? DefaultMutableTreeNode ?: DefaultMutableTreeNode(part).also {
                    current.add(it)
                }
                current = childNode
            }
        }
        return root
    }

    private fun filterTreeNodes(node: DefaultMutableTreeNode, query: String) {
        val toRemove = mutableListOf<DefaultMutableTreeNode>()
        for (i in 0 until node.childCount) {
            val child = node.getChildAt(i) as DefaultMutableTreeNode
            filterTreeNodes(child, query)
            if (child.childCount == 0 && !child.userObject.toString().contains(query, ignoreCase = true)) {
                toRemove.add(child)
            }
        }
        toRemove.forEach { node.remove(it) }
    }

    private fun expandAllNodes() {
        for (i in 0 until fileTree.rowCount) {
            fileTree.expandRow(i)
        }
    }

    private fun regenerateMarkdownForPaths(filePaths: List<String>): String {
        val basePath = project.basePath ?: return ""
        val sb = StringBuilder()
        val fs = com.intellij.openapi.vfs.LocalFileSystem.getInstance()
        val template = ContextHistoryPersistentState.getInstance().state.markdownTemplate

        for (relPath in filePaths) {
            val fullPath = "$basePath/$relPath"
            val vf = fs.findFileByPath(fullPath) ?: continue
            if (!vf.isDirectory) {
                val fileTypeLanguage = vf.fileType.name.lowercase()
                val content = try {
                    String(vf.contentsToByteArray(), vf.charset)
                } catch (ex: Exception) {
                    "Error reading file: ${ex.message}"
                }
                sb.append(
                    template
                        .replace("{path}", relPath)
                        .replace("{filetype}", fileTypeLanguage)
                        .replace("{content}", content)
                ).append("\n\n")
            }
        }
        return sb.toString()
    }

    private fun copyToClipboard(text: String) {
        val sel = StringSelection(text)
        Toolkit.getDefaultToolkit().systemClipboard.setContents(sel, null)
    }

    private fun countTokens(text: String): Int {
        val registry = Encodings.newDefaultEncodingRegistry()
        val encoding = registry.getEncodingForModel("gpt_3.5-turbo")
            .orElseGet {
                registry.getEncoding("cl100k_base")
                    .orElseThrow { IllegalArgumentException("No encoding found for cl100k_base") }
            }
        return encoding.countTokens(text)
    }
}
