package com.brostoffed.contextbuilder

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurationException
import java.awt.BorderLayout
import javax.swing.*

class ContextBuilderSettingsConfigurable : Configurable {

    private var mainPanel: JPanel? = null
    private var fileTypeList: JList<String>? = null
    private var addTextField: JTextField? = null
    private var addButton: JButton? = null
    private var removeButton: JButton? = null
    private val listModel = DefaultListModel<String>()

    override fun getDisplayName(): String = "Context Builder Settings"

    override fun createComponent(): JComponent? {
        mainPanel = JPanel(BorderLayout(10, 10)).apply {
            border = BorderFactory.createEmptyBorder(10, 10, 10, 10)
        }

        fileTypeList = JList(listModel)
        val listScrollPane = JScrollPane(fileTypeList).apply {
            preferredSize = java.awt.Dimension(200, 150)
        }

        // Panel for adding a new file type
        val addPanel = JPanel(BorderLayout(5, 5))
        addTextField = JTextField()
        addButton = JButton("Add")
        addPanel.add(addTextField, BorderLayout.CENTER)
        addPanel.add(addButton, BorderLayout.EAST)

        removeButton = JButton("Remove Selected")

        // Put addPanel + removeButton in a single bottomPanel
        val bottomPanel = JPanel()
        bottomPanel.layout = BoxLayout(bottomPanel, BoxLayout.Y_AXIS)
        bottomPanel.add(addPanel)
        bottomPanel.add(removeButton)

        // Build the main layout
        mainPanel?.add(JLabel("Excluded File Types:"), BorderLayout.NORTH)
        mainPanel?.add(listScrollPane, BorderLayout.CENTER)
        mainPanel?.add(bottomPanel, BorderLayout.SOUTH)

        // Listeners for adding/removing
        addButton?.addActionListener {
            val fileType = addTextField?.text?.trim()
            if (!fileType.isNullOrEmpty() && !listModel.elements().toList().contains(fileType)) {
                listModel.addElement(fileType)
                addTextField?.text = ""
            }
        }

        removeButton?.addActionListener {
            val selected = fileTypeList?.selectedValue
            if (selected != null) {
                listModel.removeElement(selected)
            }
        }

        reset()
        return mainPanel
    }


    override fun isModified(): Boolean {
        val settings = ContextHistoryPersistentState.getInstance().state
        return listModel.elements().toList() != settings.excludedFiletypes
    }

    @Throws(ConfigurationException::class)
    override fun apply() {
        val settings = ContextHistoryPersistentState.getInstance().state
        settings.excludedFiletypes = listModel.elements().toList().toMutableList()
    }

    override fun reset() {
        val settings = ContextHistoryPersistentState.getInstance().state
        listModel.clear()
        settings.excludedFiletypes.forEach { listModel.addElement(it) }
    }

    override fun disposeUIResources() {
        mainPanel = null
    }
}

// Extension function to convert Enumeration to List
fun <T> java.util.Enumeration<T>.toList(): List<T> = this.toList(mutableListOf())
fun <T> java.util.Enumeration<T>.toList(list: MutableList<T>): List<T> {
    while (this.hasMoreElements()) {
        list.add(this.nextElement())
    }
    return list
}
