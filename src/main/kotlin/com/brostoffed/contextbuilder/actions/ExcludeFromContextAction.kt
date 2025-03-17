package com.brostoffed.contextbuilder.actions

import com.brostoffed.contextbuilder.ContextHistoryPersistentState
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.diagnostic.Logger

class ExcludeFromContextAction : AnAction(
    "Exclude from Context",
    "Exclude this file or folder from context generation",
    null
) {

    companion object {
        private val LOG: Logger = Logger.getInstance(ExcludeFromContextAction::class.java)
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val files = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY)
        val file = if (files != null && files.isNotEmpty()) files.first() else null

        LOG.debug("ExcludeFromContextAction.update() called. file=$file, project=${e.project?.name}")

        if (file != null) {
            val projectBase = e.project?.basePath
            val excludePath = if (projectBase != null && file.path.startsWith(projectBase)) {
                file.path.substring(projectBase.length + 1)
            } else {
                file.path
            }
            val state = ContextHistoryPersistentState.getInstance().state
            val isAlreadyExcluded = state.excludedDirectories.contains(excludePath)
            e.presentation.isEnabled = !isAlreadyExcluded
            e.presentation.isVisible = true
            LOG.debug("update(): excludePath=$excludePath, isAlreadyExcluded=$isAlreadyExcluded")
        } else {
            e.presentation.isEnabled = false
            e.presentation.isVisible = true
            LOG.debug("update(): No file selected, disabling action.")
        }
    }

    override fun actionPerformed(e: AnActionEvent) {
        val files = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY)
        val file = if (files != null && files.isNotEmpty()) files.first() else null

        LOG.debug("ExcludeFromContextAction.actionPerformed() called with file=$file, project=${e.project?.name}")
        if (file == null) {
            LOG.debug("actionPerformed(): No file selected, aborting.")
            return
        }

        val projectBase = e.project?.basePath
        val excludePath = if (projectBase != null && file.path.startsWith(projectBase)) {
            file.path.substring(projectBase.length + 1)
        } else {
            file.path
        }

        val state = ContextHistoryPersistentState.getInstance().state
        val isAlreadyExcluded = state.excludedDirectories.contains(excludePath)
        LOG.debug("actionPerformed(): excludePath=$excludePath, isAlreadyExcluded=$isAlreadyExcluded")

        if (!isAlreadyExcluded) {
            state.excludedDirectories.add(excludePath)
            NotificationGroupManager.getInstance().getNotificationGroup("Context Builder")
                .createNotification("Excluded: $excludePath", NotificationType.INFORMATION)
                .notify(e.project)
            LOG.info("Successfully excluded path: $excludePath")
        } else {
            NotificationGroupManager.getInstance().getNotificationGroup("Context Builder")
                .createNotification("Already excluded: $excludePath", NotificationType.WARNING)
                .notify(e.project)
            LOG.info("Path already excluded: $excludePath")
        }
    }
}
