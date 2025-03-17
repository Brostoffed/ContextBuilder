// src/main/kotlin/com/brostoffed/contextbuilder/ContextUtils.kt
package com.brostoffed.contextbuilder

import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile

object ContextUtils {

    /**
     * Recursively collects file paths (as relative paths) from the given VirtualFile.
     * - If the file/folder's relative path is in alwaysIncludePaths, it is always processed.
     * - Otherwise, if its relative path starts with any entry in excludedDirectories, it is skipped.
     * - Also, if it is a file (not a directory) and its file type is excluded (unless always included), it is skipped.
     *
     * @param file The current VirtualFile to process.
     * @param projectBasePath The base path of the project.
     * @param fileList A mutable list where collected relative paths are added.
     */
    fun collectPathsRespectingSettings(
        file: VirtualFile,
        projectBasePath: String,
        fileList: MutableList<String>
    ) {
        val fullPath = file.path
        // Compute a relative path if possible.
        val relativePath = if (projectBasePath.isNotEmpty() && fullPath.startsWith(projectBasePath)) {
            fullPath.substring(projectBasePath.length + 1)
        } else {
            fullPath
        }

        val settings = ContextHistoryPersistentState.getInstance().state

        // Determine if this file/folder is explicitly marked as always include.
        val alwaysInclude = settings.alwaysIncludePaths.contains(relativePath)
        // Check if the relative path falls under any excluded directory.
        val isExcludedDir = settings.excludedDirectories.any { relativePath.startsWith(it) }

        // If not marked as always include and it's in an excluded directory, skip it.
        if (!alwaysInclude && isExcludedDir) return

        // For files (not directories), check if the file type is excluded (unless always included).
        if (!file.isDirectory && !alwaysInclude) {
            val isExcludedType = settings.excludedFiletypes.any { it.equals(file.fileType.name, ignoreCase = true) }
            if (isExcludedType) return
        }

        if (file.isDirectory) {
            file.children.forEach { child ->
                collectPathsRespectingSettings(child, projectBasePath, fileList)
            }
        } else {
            fileList.add(relativePath)
        }
    }

    /**
     * Reads file contents for each relative path and applies the markdown template.
     * @param paths A list of relative file paths.
     * @param projectBasePath The project’s base path.
     * @return A markdown string built from the files.
     */
    fun buildContextContent(
        paths: List<String>,
        projectBasePath: String
    ): String {
        val sb = StringBuilder()
        val fs = LocalFileSystem.getInstance()
        val template = ContextHistoryPersistentState.getInstance().state.markdownTemplate

        for (relPath in paths) {
            val fullPath = if (projectBasePath.isNotEmpty()) "$projectBasePath/$relPath" else relPath
            val vf = fs.findFileByPath(fullPath) ?: continue
            if (!vf.isDirectory) {
                val fileTypeLanguage = vf.fileType.name.lowercase()
                val content = try {
                    String(vf.contentsToByteArray(), vf.charset)
                } catch (ex: Exception) {
                    "Error reading file: ${ex.message}"
                }
                sb.append(
                    template.replace("{path}", relPath)
                        .replace("{filetype}", fileTypeLanguage)
                        .replace("{content}", content)
                ).append("\n\n")
            }
        }
        return sb.toString()
    }
}
