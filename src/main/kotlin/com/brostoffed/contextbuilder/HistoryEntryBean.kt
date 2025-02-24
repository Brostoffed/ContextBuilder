package com.brostoffed.contextbuilder

data class HistoryEntryBean(
    var timestamp: String = "",
    var filePaths: MutableList<String> = mutableListOf()
) {
    override fun toString(): String = timestamp
}
