package com.brostoffed.contextbuilder.actions

import com.brostoffed.contextbuilder.ContextHistoryPersistentState
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnActionEvent

class AlwaysIncludeContextAction : BaseContextAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val file = getSelectedFile(e) ?: return
        // Convert absolute path to relative path if possible.
        val projectBase = e.project?.basePath
        val alwaysIncludePath = if (projectBase != null && file.path.startsWith(projectBase)) {
            file.path.substring(projectBase.length + 1)
        } else {
            file.path
        }
        val state = ContextHistoryPersistentState.getInstance().state

        if (!state.alwaysIncludePaths.contains(alwaysIncludePath)) {
            state.alwaysIncludePaths.add(alwaysIncludePath)
            NotificationGroupManager.getInstance().getNotificationGroup("Context Builder")
                .createNotification("Always include: $alwaysIncludePath", NotificationType.INFORMATION)
                .notify(e.project)
        } else {
            NotificationGroupManager.getInstance().getNotificationGroup("Context Builder")
                .createNotification("Already in always-include list: $alwaysIncludePath", NotificationType.WARNING)
                .notify(e.project)
        }
    }

    override fun update(e: AnActionEvent) {
        val file = getSelectedFile(e)
        if (file != null) {
            val projectBase = e.project?.basePath
            val alwaysIncludePath = if (projectBase != null && file.path.startsWith(projectBase)) {
                file.path.substring(projectBase.length + 1)
            } else {
                file.path
            }
            e.presentation.isEnabled =
                !ContextHistoryPersistentState.getInstance().state.alwaysIncludePaths.contains(alwaysIncludePath)
        } else {
            e.presentation.isEnabled = false
        }
    }
}
