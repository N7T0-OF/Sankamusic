package com.maxrave.simpmusic.ui.component

import com.maxrave.domain.data.model.browse.album.Track

/**
 * Smart Shuffle algorithm that produces a playlist-aware shuffle order.
 *
 * Goals:
 * - Avoids the most recently played tracks
 * - Prioritizes tracks from the user's favorite artists
 * - Intersperses favorite-artist tracks with others for a balanced mix
 * - Never repeats the same track back-to-back
 */
object SmartShuffle {

    /**
     * Generates a smart-shuffled version of the given track list.
     *
     * @param tracks The full list of tracks to shuffle.
     * @param recentlyPlayedIds Video IDs of recently played tracks to avoid (up to ~5).
     * @param favoriteArtistNames Artist names the user listens to most (top ~10).
     * @return A new list in smart-shuffled order, or a simple Fisher-Yates shuffle if criteria can't be met.
     */
    fun shuffle(
        tracks: List<Track>,
        recentlyPlayedIds: Set<String> = emptySet(),
        favoriteArtistNames: Set<String> = emptySet(),
    ): List<Track> {
        if (tracks.isEmpty()) return emptyList()
        if (tracks.size == 1) return tracks

        // 1. Exclude recently played tracks if other options exist.
        val candidates = if (tracks.any { it.videoId !in recentlyPlayedIds }) {
            tracks.filter { it.videoId !in recentlyPlayedIds }
        } else {
            tracks // Fall back: shuffle everything.
        }

        // 2. Split into favorite-artist tracks and others.
        val favTracks = candidates.filter { track ->
            track.artists?.any { artist -> artist.name in favoriteArtistNames } == true
        }.toMutableList().also { it.shuffle() }

        val otherTracks = candidates.filter { it !in favTracks }.toMutableList().also { it.shuffle() }

        // 3. Interlace: fav, other, fav, other... then append whichever runs out.
        val result = mutableListOf<Track>()
        val favIter = favTracks.iterator()
        val otherIter = otherTracks.iterator()

        var useFav = favTracks.isNotEmpty()
        while (favIter.hasNext() || otherIter.hasNext()) {
            if (useFav && favIter.hasNext()) {
                result.add(favIter.next())
            } else if (otherIter.hasNext()) {
                result.add(otherIter.next())
            }
            useFav = !useFav
        }

        // 4. Ensure no back-to-back repeats (unlikely but possible if the source had duplicates).
        val deduplicated = result.distinctBy { it.videoId }

        return deduplicated.ifEmpty { candidates.shuffled() }
    }
}