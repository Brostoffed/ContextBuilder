package com.brostoffed.contextbuilder

import com.brostoffed.contextbuilder.toolwindow.ContextHistoryToolWindowPanel
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.knuddels.jtokkit.Encodings
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class GenerateContextAction : AnAction("Generate Context") {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val selectedFiles = e.getData(com.intellij.openapi.actionSystem.CommonDataKeys.VIRTUAL_FILE_ARRAY)
        e.presentation.isEnabledAndVisible = (selectedFiles != null && selectedFiles.isNotEmpty())
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project: Project = e.project ?: return
        val selectedFiles = e.getData(com.intellij.openapi.actionSystem.CommonDataKeys.VIRTUAL_FILE_ARRAY) ?: return
        val projectBasePath = project.basePath ?: ""
        val fileList = mutableListOf<String>()

        // Collect file paths from the user's selection
        selectedFiles.forEach { vf ->
            ContextUtils.collectPathsRespectingSettings(vf, projectBasePath, fileList)
        }

        // Also add files from the "always include" list—even if they weren't selected.
        val settings = ContextHistoryPersistentState.getInstance().state
        val fs = LocalFileSystem.getInstance()
        settings.alwaysIncludePaths.forEach { alwaysIncludeRelative ->
            // Avoid duplicates.
            if (!fileList.contains(alwaysIncludeRelative)) {
                // Try to find the VirtualFile using the relative path.
                val vf = fs.findFileByPath("$projectBasePath/$alwaysIncludeRelative")
                if (vf != null) {
                    ContextUtils.collectPathsRespectingSettings(vf, projectBasePath, fileList)
                }
            }
        }

        // Build the final markdown using the collected file paths.
        val finalMarkdown = ContextUtils.buildContextContent(fileList, projectBasePath)
        val tokenCount = countTokens(finalMarkdown)

        ApplicationManager.getApplication().invokeLater {
            copyToClipboard(finalMarkdown)
            NotificationGroupManager.getInstance()
                .getNotificationGroup("Context Builder")
                .createNotification(
                    "Merged context copied to clipboard. Token count: $tokenCount",
                    NotificationType.INFORMATION
                )
                .notify(project)

            val now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
            val entry = HistoryEntryBean(timestamp = now, filePaths = fileList.toMutableList())
            // Save the new history entry.
            ContextHistoryPersistentState.getInstance().state.entries.add(entry)

            // Refresh the tool window history if it is open.
            ContextHistoryToolWindowPanel.instance?.loadHistory()
        }
    }

    private fun copyToClipboard(text: String) {
        val selection = StringSelection(text)
        Toolkit.getDefaultToolkit().systemClipboard.setContents(selection, null)
    }

    /**
     * Counts tokens using jtokkit's BPE encoding.
     * First attempts to get encoding for "gpt_3.5-turbo" and falls back to "cl100k_base" if needed.
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
