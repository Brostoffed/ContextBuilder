package com.brostoffed.contextbuilder

import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurationException
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.*

class ContextBuilderSettingsConfigurable : Configurable {

    private var mainPanel: JPanel? = null

    // Models for the three lists
    private val excludedFileTypesModel = DefaultListModel<String>()
    private val excludedDirectoriesModel = DefaultListModel<String>()
    private val alwaysIncludeModel = DefaultListModel<String>()

    override fun getDisplayName(): String = "Context Builder Settings"

    override fun createComponent(): JComponent? {
        mainPanel = JPanel(BorderLayout(10, 10)).apply {
            border = BorderFactory.createEmptyBorder(10, 10, 10, 10)
        }

        val tabbedPane = JTabbedPane()

        // --- Exclusions Panel (File Types + Directories) ---
        val exclusionsPanel = JPanel()
        exclusionsPanel.layout = BoxLayout(exclusionsPanel, BoxLayout.Y_AXIS)

        // Panel for excluded file types
        val fileTypesPanel = JPanel(BorderLayout(5, 5)).apply {
            border = BorderFactory.createTitledBorder("Excluded File Types")
            preferredSize = Dimension(400, 180)
        }
        val fileTypesList = JList(excludedFileTypesModel)
        val fileTypesScroll = JScrollPane(fileTypesList).apply {
            preferredSize = Dimension(400, 100)
        }
        val fileTypeAddPanel = JPanel(BorderLayout(5, 5))
        val fileTypeTextField = JTextField()
        val fileTypeAddButton = JButton("Add")
        fileTypeAddPanel.add(fileTypeTextField, BorderLayout.CENTER)
        fileTypeAddPanel.add(fileTypeAddButton, BorderLayout.EAST)
        val fileTypeRemoveButton = JButton("Remove Selected")
        val fileTypesControlPanel = JPanel()
        fileTypesControlPanel.add(fileTypeAddPanel)
        fileTypesControlPanel.add(fileTypeRemoveButton)
        fileTypesPanel.add(fileTypesScroll, BorderLayout.CENTER)
        fileTypesPanel.add(fileTypesControlPanel, BorderLayout.SOUTH)

        // Panel for excluded directories
        val directoriesPanel = JPanel(BorderLayout(5, 5)).apply {
            border = BorderFactory.createTitledBorder("Excluded Directories")
            preferredSize = Dimension(400, 180)
        }
        val excludedDirsList = JList(excludedDirectoriesModel)
        val dirsScroll = JScrollPane(excludedDirsList).apply {
            preferredSize = Dimension(400, 100)
        }
        val dirsControlPanel = JPanel()
        val addDirButton = JButton("Add Directory")
        val removeDirButton = JButton("Remove Selected")
        dirsControlPanel.add(addDirButton)
        dirsControlPanel.add(removeDirButton)
        directoriesPanel.add(dirsScroll, BorderLayout.CENTER)
        directoriesPanel.add(dirsControlPanel, BorderLayout.SOUTH)

        exclusionsPanel.add(fileTypesPanel)
        exclusionsPanel.add(Box.createVerticalStrut(10))
        exclusionsPanel.add(directoriesPanel)

        // --- Always Include Panel ---
        val alwaysIncludePanel = JPanel(BorderLayout(5, 5)).apply {
            border = BorderFactory.createTitledBorder("Always Include")
            preferredSize = Dimension(400, 180)
        }
        val alwaysIncludeList = JList(alwaysIncludeModel)
        val alwaysIncludeScroll = JScrollPane(alwaysIncludeList).apply {
            preferredSize = Dimension(400, 100)
        }
        val alwaysIncludeControlPanel = JPanel()
        val addAlwaysButton = JButton("Add Path")
        val removeAlwaysButton = JButton("Remove Selected")
        alwaysIncludeControlPanel.add(addAlwaysButton)
        alwaysIncludeControlPanel.add(removeAlwaysButton)
        alwaysIncludePanel.add(alwaysIncludeScroll, BorderLayout.CENTER)
        alwaysIncludePanel.add(alwaysIncludeControlPanel, BorderLayout.SOUTH)

        tabbedPane.addTab("Exclusions", exclusionsPanel)
        tabbedPane.addTab("Always Include", alwaysIncludePanel)
        mainPanel?.add(tabbedPane, BorderLayout.CENTER)

        // --- Action Listeners ---
        fileTypeAddButton.addActionListener {
            val fileType = fileTypeTextField.text.trim()
            if (fileType.isNotEmpty() && !((0 until excludedFileTypesModel.size()).any { excludedFileTypesModel.get(it) == fileType })) {
                excludedFileTypesModel.addElement(fileType)
                fileTypeTextField.text = ""
            }
        }

        fileTypeRemoveButton.addActionListener {
            val selected = fileTypesList.selectedValue
            if (selected != null) {
                excludedFileTypesModel.removeElement(selected)
            }
        }

        addDirButton.addActionListener {
            val descriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor()
            descriptor.title = "Select Directory to Exclude"
            // Use null for project here, or pass a project reference if available.
            val folder = FileChooser.chooseFile(descriptor, null, null)
            if (folder != null) {
                // Here we store the path as shown in the VirtualFile (you may want to convert it to a relative path later)
                val path = folder.path
                if (!((0 until excludedDirectoriesModel.size()).any { excludedDirectoriesModel.get(it) == path })) {
                    excludedDirectoriesModel.addElement(path)
                }
            }
        }

        removeDirButton.addActionListener {
            val selected = excludedDirsList.selectedValue
            if (selected != null) {
                excludedDirectoriesModel.removeElement(selected)
            }
        }

        addAlwaysButton.addActionListener {
            // Create a descriptor that allows selection of both files and folders, with multiple selection enabled.
            val descriptor = FileChooserDescriptor(true, true, false, false, false, true)
            descriptor.title = "Select Path to Always Include"
            val files = FileChooser.chooseFiles(descriptor, null, null)
            files.forEach { vf ->
                val path = vf.path
                if (!((0 until alwaysIncludeModel.size()).any { alwaysIncludeModel.get(it) == path })) {
                    alwaysIncludeModel.addElement(path)
                }
            }
        }


        removeAlwaysButton.addActionListener {
            val selected = alwaysIncludeList.selectedValue
            if (selected != null) {
                alwaysIncludeModel.removeElement(selected)
            }
        }

        reset()
        return mainPanel
    }

    override fun isModified(): Boolean {
        val settings = ContextHistoryPersistentState.getInstance().state
        val fileTypesModified =
            (0 until excludedFileTypesModel.size()).map { excludedFileTypesModel.get(it) } != settings.excludedFiletypes
        val dirsModified =
            (0 until excludedDirectoriesModel.size()).map { excludedDirectoriesModel.get(it) } != settings.excludedDirectories
        val alwaysModified =
            (0 until alwaysIncludeModel.size()).map { alwaysIncludeModel.get(it) } != settings.alwaysIncludePaths
        return fileTypesModified || dirsModified || alwaysModified
    }

    @Throws(ConfigurationException::class)
    override fun apply() {
        val settings = ContextHistoryPersistentState.getInstance().state
        settings.excludedFiletypes =
            (0 until excludedFileTypesModel.size()).map { excludedFileTypesModel.get(it) }.toMutableList()
        settings.excludedDirectories =
            (0 until excludedDirectoriesModel.size()).map { excludedDirectoriesModel.get(it) }.toMutableList()
        settings.alwaysIncludePaths =
            (0 until alwaysIncludeModel.size()).map { alwaysIncludeModel.get(it) }.toMutableList()
    }

    override fun reset() {
        val settings = ContextHistoryPersistentState.getInstance().state
        excludedFileTypesModel.clear()
        settings.excludedFiletypes.forEach { excludedFileTypesModel.addElement(it) }
        excludedDirectoriesModel.clear()
        settings.excludedDirectories.forEach { excludedDirectoriesModel.addElement(it) }
        alwaysIncludeModel.clear()
        settings.alwaysIncludePaths.forEach { alwaysIncludeModel.addElement(it) }
    }

    override fun disposeUIResources() {
        mainPanel = null
    }
}
