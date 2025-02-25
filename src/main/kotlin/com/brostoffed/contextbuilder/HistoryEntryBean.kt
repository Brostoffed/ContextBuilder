package com.brostoffed.contextbuilder

data class HistoryEntryBean(
    var timestamp: String = "",                   // Existing field for backward compatibility
    var filePaths: MutableList<String> = mutableListOf(),
    var customName: String = "",                  // New: user can rename
    var createdAt: Long = System.currentTimeMillis() // New: used for sorting by date
) {
    override fun toString(): String {
        // If user has renamed it, show that name; otherwise, show the old timestamp
        return if (customName.isNotBlank()) customName else timestamp
    }
}
