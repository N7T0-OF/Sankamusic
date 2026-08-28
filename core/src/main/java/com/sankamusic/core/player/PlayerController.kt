package com.sankamusic.core.player

import com.sankamusic.core.api.model.UnifiedTrack

/**
 * Machine à états PURE du player (étape 4 migration SpaceKai —
 * docs/MIGRATION.md).
 *
 * Porté du player SpaceKai-OLD (SharedViewModel / NowPlayingScreen) : statut de
 * lecture, piste courante, position/durée, file d'attente et commandes.
 *
 * Le Core reste pur : aucune dépendance audio/Android ici. Le moteur audio réel
 * (ExoPlayer/media3, côté infra/UI) consomme le [PlayerController] — il lit le
 * [snapshot], exécute les commandes et alimente position/durée. Toute commande
 * invalide échoue PROPREMENT (Result.failure) sans changer l'état : le player
 * ne casse jamais l'app.
 *
 * Bornes : `next()` en fin de file et `previous()` en tête échouent proprement ;
 * l'UI décide alors du comportement (arrêt, répétition…).
 */
class PlayerController(initialQueue: List<UnifiedTrack> = emptyList()) {

    private var status: PlayerStatus = PlayerStatus.IDLE
    private var queue: List<UnifiedTrack> = initialQueue
    private var currentIndex: Int? = null
    private var positionMillis: Long = 0
    private var durationMillis: Long? = null
    private var errorMessage: String? = null

    /** État courant, immuable — consommé par l'UI et le moteur audio. */
    fun snapshot(): PlayerSnapshot = PlayerSnapshot(
        status = status,
        queue = queue,
        currentIndex = currentIndex,
        currentTrack = currentIndex?.let { queue.getOrNull(it) },
        positionMillis = positionMillis,
        durationMillis = durationMillis,
        errorMessage = errorMessage,
    )

    // ── Commandes de lecture ────────────────────────────────────────────

    /** Démarre la lecture d'une piste (remplace la file, index 0). */
    fun play(track: UnifiedTrack): Result<Unit> {
        queue = listOf(track)
        currentIndex = 0
        positionMillis = 0
        durationMillis = track.durationMs
        errorMessage = null
        status = PlayerStatus.PLAYING
        return Result.success(Unit)
    }

    /** Démarre la lecture d'une file à partir de [startIndex] (défaut 0). */
    fun playQueue(tracks: List<UnifiedTrack>, startIndex: Int = 0): Result<Unit> {
        if (tracks.isEmpty()) {
            return Result.failure(IllegalStateException("File vide : rien à lire"))
        }
        if (startIndex !in tracks.indices) {
            return Result.failure(IllegalArgumentException("startIndex hors bornes : $startIndex"))
        }
        queue = tracks
        currentIndex = startIndex
        positionMillis = 0
        durationMillis = queue[startIndex].durationMs
        errorMessage = null
        status = PlayerStatus.PLAYING
        return Result.success(Unit)
    }

    /** Met en pause (PLAYING → PAUSED). Échoue proprement sinon. */
    fun pause(): Result<Unit> {
        if (status != PlayerStatus.PLAYING) {
            return Result.failure(IllegalStateException("pause() : le player n'est pas en lecture (${status.name})"))
        }
        status = PlayerStatus.PAUSED
        return Result.success(Unit)
    }

    /** Reprend la lecture (PAUSED → PLAYING). Échoue proprement sinon. */
    fun resume(): Result<Unit> {
        if (status != PlayerStatus.PAUSED) {
            return Result.failure(IllegalStateException("resume() : le player n'est pas en pause (${status.name})"))
        }
        status = PlayerStatus.PLAYING
        return Result.success(Unit)
    }

    /** Bascule lecture / pause (selon l'état courant). */
    fun togglePlayPause(): Result<Unit> =
        when (status) {
            PlayerStatus.PLAYING -> pause()
            PlayerStatus.PAUSED -> resume()
            else -> Result.failure(IllegalStateException("toggle : aucun morceau en cours (${status.name})"))
        }

    /** Piste suivante (index + 1). Échoue proprement en fin de file. */
    fun next(): Result<Unit> {
        val index = currentIndex ?: return Result.failure(IllegalStateException("next() : aucune piste en cours"))
        if (index + 1 >= queue.size) {
            return Result.failure(IllegalStateException("next() : fin de file atteinte"))
        }
        return moveTo(index + 1)
    }

    /** Piste précédente (index - 1). Échoue proprement en tête de file. */
    fun previous(): Result<Unit> {
        val index = currentIndex ?: return Result.failure(IllegalStateException("previous() : aucune piste en cours"))
        if (index == 0) {
            return Result.failure(IllegalStateException("previous() : début de file atteint"))
        }
        return moveTo(index - 1)
    }

    /** Déplace la lecture à une position donnée dans la file (hors bornes → échec propre). */
    fun seekToIndex(index: Int): Result<Unit> {
        if (index !in queue.indices) {
            return Result.failure(IllegalArgumentException("seekToIndex hors bornes : $index"))
        }
        return moveTo(index)
    }

    /** Position de lecture en millisecondes (négatif → échec propre). */
    fun seekTo(positionMillis: Long): Result<Unit> {
        if (positionMillis < 0) {
            return Result.failure(IllegalArgumentException("seekTo négatif : $positionMillis"))
        }
        this.positionMillis = positionMillis
        return Result.success(Unit)
    }

    /** Durée de la piste courante, alimentée par le moteur audio / la piste. */
    fun setDuration(durationMillis: Long) {
        this.durationMillis = durationMillis
    }

    /** Signale une erreur de lecture (le moteur audio garde le contrôle). */
    fun reportError(message: String?): Result<Unit> {
        status = PlayerStatus.ERROR
        errorMessage = message
        return Result.failure(IllegalStateException(message ?: "Erreur de lecture"))
    }

    // ── File d'attente ──────────────────────────────────────────────────

    /** Ajoute une piste en fin de file (sans interrompre la lecture). */
    fun enqueue(track: UnifiedTrack) {
        queue = queue + track
    }

    fun enqueueAll(tracks: List<UnifiedTrack>) {
        queue = queue + tracks
    }

    /** Retire une piste de la file (hors bornes → échec propre). */
    fun removeAt(index: Int): Result<Unit> {
        if (index !in queue.indices) {
            return Result.failure(IllegalArgumentException("removeAt hors bornes : $index"))
        }
        queue = queue.filterIndexed { i, _ -> i != index }
        // Réajuste l'index courant si la piste courante (ou une précédente) a été retirée.
        currentIndex?.let { cur ->
            currentIndex = when {
                index < cur -> cur - 1
                index == cur -> if (queue.isEmpty()) null else cur.coerceAtMost(queue.lastIndex)
                else -> cur
            }
        }
        if (queue.isEmpty()) {
            status = PlayerStatus.IDLE
            positionMillis = 0
            durationMillis = null
            currentIndex = null
        }
        return Result.success(Unit)
    }

    /** Vide la file et repasse à l'arrêt. */
    fun clear() {
        queue = emptyList()
        currentIndex = null
        positionMillis = 0
        durationMillis = null
        errorMessage = null
        status = PlayerStatus.IDLE
    }

    // ── Interne ─────────────────────────────────────────────────────────

    private fun moveTo(index: Int): Result<Unit> {
        currentIndex = index
        positionMillis = 0
        durationMillis = queue[index].durationMs
        errorMessage = null
        return Result.success(Unit)
    }
}

/** Statut de lecture du player. */
enum class PlayerStatus {
    /** Aucune piste chargée. */
    IDLE,

    /** En lecture (le moteur audio peut être en buffering : position figée). */
    PLAYING,

    /** En pause. */
    PAUSED,

    /** Erreur de lecture signalée par le moteur audio. */
    ERROR,
}

/** État immuable du player, consommé par l'UI et le moteur audio. */
data class PlayerSnapshot(
    val status: PlayerStatus,
    val queue: List<UnifiedTrack>,
    val currentIndex: Int?,
    val currentTrack: UnifiedTrack?,
    val positionMillis: Long,
    val durationMillis: Long?,
    val errorMessage: String? = null,
)
