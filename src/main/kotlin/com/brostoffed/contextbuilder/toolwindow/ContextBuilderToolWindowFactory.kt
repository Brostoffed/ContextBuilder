package com.brostoffed.contextbuilder.toolwindow

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory

class ContextBuilderToolWindowFactory : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        // Create the tool window panel using our new ContextHistoryToolWindowPanel
        val historyPanel = ContextHistoryToolWindowPanel(project)
        val contentFactory = ContentFactory.getInstance()
        val content = contentFactory.createContent(historyPanel, "", false)
        toolWindow.contentManager.addContent(content)
    }
}
