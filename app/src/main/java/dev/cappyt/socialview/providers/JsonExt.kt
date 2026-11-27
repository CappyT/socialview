package dev.cappyt.socialview.providers

import org.json.JSONObject

internal fun JSONObject.stringOrNull(key: String): String? =
    if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() && it != "null" }

/** The balanced JSON object that starts at [start], which must be the index of its `{`. */
internal fun jsonObjectAt(body: String, start: Int): JSONObject? {
    var depth = 0
    var inString = false
    var escaped = false
    for (index in start until body.length) {
        val char = body[index]
        if (inString) {
            when {
                escaped -> escaped = false
                char == '\\' -> escaped = true
                char == '"' -> inString = false
            }
            continue
        }
        when (char) {
            '"' -> inString = true
            '{' -> depth++
            '}' -> {
                depth--
                if (depth == 0) {
                    return runCatching { JSONObject(body.substring(start, index + 1)) }.getOrNull()
                }
            }
        }
    }
    return null
}
