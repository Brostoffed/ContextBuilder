package com.brostoffed.contextbuilder.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.vfs.VirtualFile

abstract class BaseContextAction : com.intellij.openapi.actionSystem.AnAction() {

    /**
     * Returns the first selected file/folder from the current context.
     */
    protected fun getSelectedFile(e: AnActionEvent): VirtualFile? {
        return e.getData(CommonDataKeys.VIRTUAL_FILE)
    }
}
