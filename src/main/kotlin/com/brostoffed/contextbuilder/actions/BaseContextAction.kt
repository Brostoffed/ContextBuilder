package com.brostoffed.contextbuilder.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.vfs.VirtualFile

abstract class BaseContextAction : com.intellij.openapi.actionSystem.AnAction() {

    /**
     * Returns the VirtualFile from the current context.
     * It first tries to get CommonDataKeys.VIRTUAL_FILE.
     * If not found, it falls back to getting the PSI_FILE and then its virtual file.
     */
    protected fun getSelectedFile(e: AnActionEvent): VirtualFile? {
        var file: VirtualFile? = e.getData(CommonDataKeys.VIRTUAL_FILE)
        if (file == null) {
            // Attempt to retrieve the PSI file and then its VirtualFile.
            val psiFile = e.getData(CommonDataKeys.PSI_FILE)
            file = psiFile?.virtualFile
        }
        return file
    }
}
