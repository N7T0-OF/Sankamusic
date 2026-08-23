package com.maxrave.simpmusic.extension

import com.maxrave.domain.data.model.browse.album.Track

/**
 * Calculates the total duration of a list of tracks as a human-readable string.
 * Handles null or zero durations gracefully.
 *
 * @return A formatted string like "1 h 23 min", "45 min", or "" if no tracks have valid durations.
 */
fun List<Track>.calculateTotalDuration(): String {
    if (isEmpty()) return ""
    val totalSeconds = sumOf { it.duration?.toLongOrNull() ?: 0L }
    if (totalSeconds == 0L) return ""
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60

    return when {
        hours > 0 -> "$hours h $minutes min"
        minutes > 0 -> "$minutes min ${seconds}s"
        else -> "${seconds}s"
    }
}

/**
 * Calculates the total duration of a list of tracks as milliseconds.
 */
fun List<Track>.totalDurationMs(): Long {
    return sumOf { it.duration?.toLongOrNull() ?: 0L }
}