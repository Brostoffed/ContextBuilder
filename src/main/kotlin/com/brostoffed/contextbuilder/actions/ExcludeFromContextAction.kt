package com.brostoffed.contextbuilder.actions

import com.brostoffed.contextbuilder.ContextHistoryPersistentState
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnActionEvent

class ExcludeFromContextAction : BaseContextAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val file = getSelectedFile(e) ?: return
        val projectBase = e.project?.basePath
        val excludePath = if (projectBase != null && file.path.startsWith(projectBase)) {
            file.path.substring(projectBase.length + 1)
        } else {
            file.path
        }
        val state = ContextHistoryPersistentState.getInstance().state

        if (!state.excludedDirectories.contains(excludePath)) {
            state.excludedDirectories.add(excludePath)
            NotificationGroupManager.getInstance().getNotificationGroup("Context Builder")
                .createNotification("Excluded: $excludePath", NotificationType.INFORMATION)
                .notify(e.project)
        } else {
            NotificationGroupManager.getInstance().getNotificationGroup("Context Builder")
                .createNotification("Already excluded: $excludePath", NotificationType.WARNING)
                .notify(e.project)
        }
    }

    override fun update(e: AnActionEvent) {
        val file = getSelectedFile(e)
        if (file != null) {
            val projectBase = e.project?.basePath
            val excludePath = if (projectBase != null && file.path.startsWith(projectBase)) {
                file.path.substring(projectBase.length + 1)
            } else {
                file.path
            }
            e.presentation.isEnabled =
                !ContextHistoryPersistentState.getInstance().state.excludedDirectories.contains(excludePath)
        } else {
            e.presentation.isEnabled = false
        }
    }
}
