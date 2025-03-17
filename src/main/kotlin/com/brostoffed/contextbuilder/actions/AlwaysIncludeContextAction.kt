package com.brostoffed.contextbuilder.actions

import com.brostoffed.contextbuilder.ContextHistoryPersistentState
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.diagnostic.Logger

class AlwaysIncludeContextAction : AnAction(
    "Always Include in Context",
    "Always include this file or folder, even if excluded",
    null
) {

    companion object {
        private val LOG: Logger = Logger.getInstance(AlwaysIncludeContextAction::class.java)
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val files = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY)
        val file = if (files != null && files.isNotEmpty()) files.first() else null

        LOG.debug("AlwaysIncludeContextAction.update() called. file=$file, project=${e.project?.name}")

        if (file != null) {
            val projectBase = e.project?.basePath
            val alwaysIncludePath = if (projectBase != null && file.path.startsWith(projectBase)) {
                file.path.substring(projectBase.length + 1)
            } else {
                file.path
            }
            val state = ContextHistoryPersistentState.getInstance().state
            val isAlreadyIncluded = state.alwaysIncludePaths.contains(alwaysIncludePath)
            e.presentation.isEnabled = !isAlreadyIncluded
            e.presentation.isVisible = true
            LOG.debug("update(): alwaysIncludePath=$alwaysIncludePath, isAlreadyIncluded=$isAlreadyIncluded")
        } else {
            e.presentation.isEnabled = false
            e.presentation.isVisible = true
            LOG.debug("update(): No file selected, disabling action.")
        }
    }

    override fun actionPerformed(e: AnActionEvent) {
        val files = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY)
        val file = if (files != null && files.isNotEmpty()) files.first() else null

        LOG.debug("AlwaysIncludeContextAction.actionPerformed() called with file=$file, project=${e.project?.name}")
        if (file == null) {
            LOG.debug("actionPerformed(): No file selected, aborting.")
            return
        }

        val projectBase = e.project?.basePath
        val alwaysIncludePath = if (projectBase != null && file.path.startsWith(projectBase)) {
            file.path.substring(projectBase.length + 1)
        } else {
            file.path
        }

        val state = ContextHistoryPersistentState.getInstance().state
        val isAlreadyIncluded = state.alwaysIncludePaths.contains(alwaysIncludePath)
        LOG.debug("actionPerformed(): alwaysIncludePath=$alwaysIncludePath, isAlreadyIncluded=$isAlreadyIncluded")

        if (!isAlreadyIncluded) {
            state.alwaysIncludePaths.add(alwaysIncludePath)
            NotificationGroupManager.getInstance().getNotificationGroup("Context Builder")
                .createNotification("Always include: $alwaysIncludePath", NotificationType.INFORMATION)
                .notify(e.project)
            LOG.info("Successfully added to alwaysIncludePaths: $alwaysIncludePath")
        } else {
            NotificationGroupManager.getInstance().getNotificationGroup("Context Builder")
                .createNotification("Already in always-include list: $alwaysIncludePath", NotificationType.WARNING)
                .notify(e.project)
            LOG.info("Path already in alwaysIncludePaths: $alwaysIncludePath")
        }
    }
}
