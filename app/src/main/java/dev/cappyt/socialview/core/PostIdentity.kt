package dev.cappyt.socialview.core

object PostIdentity {
    fun displayAuthor(author: String?, handle: String?): String? {
        val name = author?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val handleName = handle?.trim()?.removePrefix("@")?.takeIf { it.isNotEmpty() }
        return name.takeUnless {
            handleName != null && it.removePrefix("@").equals(handleName, ignoreCase = true)
        }
    }
}