package com.brostoffed.contextbuilder

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.knuddels.jtokkit.Encodings
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class GenerateContextAction : AnAction("Generate Context") {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val selectedFiles = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY)
        e.presentation.isEnabledAndVisible = (selectedFiles != null && selectedFiles.isNotEmpty())
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project: Project = e.project ?: return
        val selectedFiles = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY) ?: return

        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Generating Context", false) {
            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = false

                val fileListHistory = mutableListOf<String>()
                val sb = StringBuilder()
                val projectBasePath = project.basePath ?: ""

                // Process all selected files/folders recursively
                selectedFiles.forEachIndexed { index, file ->
                    indicator.checkCanceled()
                    processFileRecursively(file, sb, projectBasePath, fileListHistory)
                    indicator.fraction = (index + 1).toDouble() / selectedFiles.size
                }

                val finalMarkdown = sb.toString()
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
                    val entry = HistoryEntryBean(timestamp = now, filePaths = fileListHistory)
                    ContextHistoryPersistentState.getInstance().state.entries.add(entry)
                    ContextHistoryDialog(project).show()
                }
            }
        })
    }

    private fun processFileRecursively(
        file: VirtualFile,
        builder: StringBuilder,
        projectBasePath: String,
        fileListHistory: MutableList<String>
    ) {
        if (file.isDirectory) {
            file.children.forEach { child ->
                processFileRecursively(child, builder, projectBasePath, fileListHistory)
            }
        } else {
            // Check against the excluded file types
            val settings = ContextHistoryPersistentState.getInstance().state
            val fileTypeName = file.fileType.name
            if (settings.excludedFiletypes.any { it.equals(fileTypeName, ignoreCase = true) }) {
                // Skip processing for this file type
                return
            }

            val fullPath = file.path
            val relativePath = if (projectBasePath.isNotEmpty() && fullPath.startsWith(projectBasePath))
                fullPath.substring(projectBasePath.length + 1)
            else
                fullPath

            fileListHistory.add(relativePath)

            val fileTypeLanguage = file.fileType.name.lowercase()
            val template = ContextHistoryPersistentState.getInstance().state.markdownTemplate
            val content = try {
                String(file.contentsToByteArray(), file.charset)
            } catch (ex: Exception) {
                "Error reading file: ${ex.message}"
            }
            builder.append(
                template
                    .replace("{path}", relativePath)
                    .replace("{filetype}", fileTypeLanguage)
                    .replace("{content}", content)
            ).append("\n\n")
        }
    }

    private fun copyToClipboard(text: String) {
        val selection = StringSelection(text)
        Toolkit.getDefaultToolkit().systemClipboard.setContents(selection, null)
    }

    /**
     * Counts tokens using jtokkit’s BPE encoding.
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
