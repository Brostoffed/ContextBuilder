package com.brostoffed.contextbuilder

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
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
import java.awt.datatransfer.StringSelection
import java.io.File
import java.io.FileWriter
import java.nio.charset.StandardCharsets
import javax.swing.*
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeCellRenderer
import javax.swing.tree.DefaultTreeModel

class ContextHistoryDialog(private val project: Project) : DialogWrapper(project, false) {

    // ------------------- History Components ------------------- //
    private val historyListModel = DefaultListModel<HistoryEntryBean>()
    private val historyList = JBList(historyListModel)
    private val historySearchField = SearchTextField().apply {
        toolTipText = "Search timestamps or file paths"
    }

    private val clearButton = JButton("Clear")
    private val exportButton = JButton("Export")
    private val importButton = JButton("Import")

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

    // NEW: Token count label to display token size in the UI
    private val tokenCountLabel = JLabel("Token count: -")

    init {
        title = "Context Generation History"
        init()
        loadHistory()
        setupListeners()
    }

    override fun createCenterPanel(): JComponent {
        // Main container panel
        val mainPanel = JBPanel<JBPanel<*>>(BorderLayout(8, 8)).apply {
            minimumSize = Dimension(800, 600)
        }

        // ---------- LEFT: History Panel ----------
        val historyPanel = JBPanel<JBPanel<*>>(BorderLayout(5, 5)).apply {
            val topPanel = JBPanel<JBPanel<*>>(BorderLayout(5, 5)).apply {
                add(JLabel("History Search:"), BorderLayout.WEST)
                add(historySearchField, BorderLayout.CENTER)
            }

            val listScroll = JBScrollPane(historyList).apply {
                preferredSize = Dimension(200, 400)
            }

            val bottomPanel = JBPanel<JBPanel<*>>().apply {
                add(clearButton)
                add(exportButton)
                add(importButton)
            }

            add(topPanel, BorderLayout.NORTH)
            add(listScroll, BorderLayout.CENTER)
            add(bottomPanel, BorderLayout.SOUTH)
        }

        // ---------- RIGHT: File Tree & Preview (vertical split) ----------
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
            // Add a header panel with "Preview:" label on the left and token count on the right.
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

        // A horizontal split: left = historyPanel, right = verticalSplit
        val horizontalSplit = JSplitPane(JSplitPane.HORIZONTAL_SPLIT, historyPanel, verticalSplit).apply {
            dividerLocation = 250
            resizeWeight = 0.0
        }

        mainPanel.add(horizontalSplit, BorderLayout.CENTER)
        return mainPanel
    }

    override fun createActions(): Array<Action> {
        val regenerateAction = object : DialogWrapperAction("Regenerate") {
            override fun doAction(e: java.awt.event.ActionEvent?) {
                val selected = historyList.selectedValue
                if (selected == null) {
                    Messages.showErrorDialog("No history entry selected.", "Error")
                    return
                }
                val md = regenerateMarkdownForPaths(selected.filePaths)
                copyToClipboard(md)
                val tokens = countTokens(md)
                tokenCountLabel.text = "Token count: $tokens"
                NotificationGroupManager.getInstance()
                    .getNotificationGroup("Context Builder")
                    .createNotification(
                        "Markdown copied to clipboard. Token count: $tokens",
                        NotificationType.INFORMATION
                    )
                    .notify(project)
                previewArea.text = md
            }
        }
        return arrayOf(regenerateAction, cancelAction)
    }

    // ------------------- Setup & Listeners ------------------- //

    private fun setupListeners() {
        // Attach DocumentListeners to the textEditor.document of SearchTextFields
        historySearchField.textEditor.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent) {
                filterHistory(historySearchField.text)
            }

            override fun removeUpdate(e: DocumentEvent) {
                filterHistory(historySearchField.text)
            }

            override fun changedUpdate(e: DocumentEvent) {
                filterHistory(historySearchField.text)
            }
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
            override fun insertUpdate(e: DocumentEvent) {
                filterFileTree(fileTreeSearchField.text)
            }

            override fun removeUpdate(e: DocumentEvent) {
                filterFileTree(fileTreeSearchField.text)
            }

            override fun changedUpdate(e: DocumentEvent) {
                filterFileTree(fileTreeSearchField.text)
            }
        })

        clearButton.addActionListener { clearHistory() }
        exportButton.addActionListener { exportHistory() }
        importButton.addActionListener { importHistory() }
    }

    private fun loadHistory() {
        historyListModel.clear()
        val allEntries = ContextHistoryPersistentState.getInstance().state.entries
        allEntries.forEach { historyListModel.addElement(it) }
    }

    private fun filterHistory(query: String) {
        historyListModel.clear()
        val allEntries = ContextHistoryPersistentState.getInstance().state.entries
        val filtered = allEntries.filter { entry ->
            entry.timestamp.contains(query, ignoreCase = true) ||
                    entry.filePaths.any { it.contains(query, ignoreCase = true) }
        }
        filtered.forEach { historyListModel.addElement(it) }
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

    // ------------------- File Tree Logic ------------------- //

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

    // ------------------- Preview Logic ------------------- //

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

    /**
     * Counts tokens using jtokkit's BPE encoding.
     * First attempts to get encoding for "gpt_3.5-turbo" and falls back to "cl100k_base" if not found.
     */
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
