package com.brostoffed.contextbuilder.toolwindow

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBScrollPane
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.datatransfer.DataFlavor
import java.awt.dnd.DnDConstants
import java.awt.dnd.DropTarget
import java.awt.dnd.DropTargetDropEvent
import java.io.File
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JTree
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreeSelectionModel

class FileStructurePanel(private val project: Project) : JPanel(BorderLayout()) {

    // Root node (invisible). We'll keep a flat list of children under it.
    private val rootNode = DefaultMutableTreeNode("Files")
    private val treeModel = DefaultTreeModel(rootNode)
    private val fileTree = JTree(treeModel).apply {
        isRootVisible = false
        // Enable multi-selection so users can remove one or more items.
        selectionModel.selectionMode = TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION
    }

    init {
        val scrollPane = JBScrollPane(fileTree).apply {
            preferredSize = Dimension(300, 400)
        }
        add(scrollPane, BorderLayout.CENTER)
        setupDragAndDrop(scrollPane)

        // Bottom panel with buttons
        val bottomPanel = JPanel()
        val addButton = JButton("Add File/Folder")
        val removeButton = JButton("Remove Selected")
        val generateButton = JButton("Generate Context")

        bottomPanel.add(addButton)
        bottomPanel.add(removeButton)
        bottomPanel.add(generateButton)
        add(bottomPanel, BorderLayout.SOUTH)

        // Add (via file chooser)
        addButton.addActionListener {
            val descriptor = FileChooserDescriptor(true, true, true, true, true, true)
                .withTitle("Select Files/Folders")
            val files = FileChooser.chooseFiles(descriptor, project, null)
            for (vf in files) {
                addFilePath(vf.path)
            }
        }

        // Remove selected nodes
        removeButton.addActionListener {
            removeSelectedNodes()
        }

        // Generate context → balloon notification
        generateButton.addActionListener {
            val filePaths = getAllFilePaths(rootNode)
            val context = generateContextForPaths(filePaths)

            // Show a balloon with a snippet of the context.
            val snippet = if (context.length > 300) context.take(300) + "..." else context
            NotificationGroupManager.getInstance()
                .getNotificationGroup("Context Builder")
                .createNotification(
                    "Generated Context",
                    snippet,
                    NotificationType.INFORMATION
                )
                .notify(project)
        }
    }

    /**
     * Drag-and-drop support.
     */
    private fun setupDragAndDrop(component: JComponent) {
        component.dropTarget = object : DropTarget() {
            override fun drop(dtde: DropTargetDropEvent) {
                try {
                    dtde.acceptDrop(DnDConstants.ACTION_COPY_OR_MOVE)
                    val transferable = dtde.transferable
                    if (transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                        val droppedFiles = transferable.getTransferData(DataFlavor.javaFileListFlavor) as List<File>
                        droppedFiles.forEach { file ->
                            addFilePath(file.absolutePath)
                        }
                    }
                    dtde.dropComplete(true)
                } catch (ex: Exception) {
                    ex.printStackTrace()
                    dtde.dropComplete(false)
                }
            }
        }
    }

    /**
     * Adds a single node for the entire path (flattened).
     */
    private fun addFilePath(path: String) {
        val newNode = DefaultMutableTreeNode(path)
        treeModel.insertNodeInto(newNode, rootNode, rootNode.childCount)
        treeModel.reload()
        fileTree.expandRow(0)
    }

    /**
     * Remove selected nodes (multi-selection supported).
     */
    private fun removeSelectedNodes() {
        val selectedPaths = fileTree.selectionPaths ?: return
        for (path in selectedPaths) {
            val node = path.lastPathComponent as DefaultMutableTreeNode
            if (node.parent != null) {
                treeModel.removeNodeFromParent(node)
            }
        }
    }

    /**
     * Returns all file paths from the flattened tree.
     */
    private fun getAllFilePaths(node: DefaultMutableTreeNode): List<String> {
        val results = mutableListOf<String>()
        for (i in 0 until node.childCount) {
            val child = node.getChildAt(i) as DefaultMutableTreeNode
            results.add(child.userObject.toString())
        }
        return results
    }

    /**
     * Example generation logic. Replace with your own.
     */
    private fun generateContextForPaths(paths: List<String>): String {
        return paths.joinToString(separator = "\n")
    }
}
