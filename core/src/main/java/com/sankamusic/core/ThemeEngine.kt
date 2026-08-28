package com.sankamusic.core

import com.sankamusic.core.api.SpaceKaiThemeTokens
import com.sankamusic.core.api.ThemeColorSource
import com.sankamusic.core.api.ThemeDefinition
import com.sankamusic.core.api.ThemeMode

/**
 * Moteur de thèmes (prototype Phase 2 — voir docs/THEME_SYSTEM.md).
 *
 * Responsabilités :
 *  - enregistrer des thèmes après validation de leur définition ;
 *  - activer un thème : retourne les tokens de la **base** (clair ou sombre
 *    selon `ThemeDefinition.base`) **fusionnés avec les personnalisations du
 *    thème** (`base.overlay(tokens)` — modèle « base + couche ») ;
 *  - suivre le thème actif, le mode (clair/sombre/système) et la source de
 *    couleur (défaut / Dynamic Color / seed custom) — portés de SpaceKai-OLD.
 *
 * ⚠️ Le mapping des tokens vers MaterialTheme Compose, le Dynamic Color et le
 * live editing seront ajoutés par l'UI du Core (Phase 2/3). Changer de thème
 * ne doit JAMAIS casser l'app : en cas d'erreur, retour au thème précédent
 * (à implémenter avec l'UI).
 */
class ThemeEngine {

    private val registry = mutableMapOf<String, ThemeDefinition>()
    private var activeId: String? = null

    private var mode: ThemeMode = ThemeMode.DARK
    private var colorSource: ThemeColorSource = ThemeColorSource.DEFAULT
    private var customSeedColor: Long? = null

    /** Enregistre un thème après validation de sa définition. */
    fun register(theme: ThemeDefinition): Result<Unit> {
        val errors = theme.validationErrors()
        if (errors.isNotEmpty()) {
            return Result.failure(IllegalArgumentException("Thème invalide : $errors"))
        }
        if (registry.containsKey(theme.id)) {
            return Result.failure(IllegalStateException("Thème déjà enregistré : ${theme.id}"))
        }
        registry[theme.id] = theme
        return Result.success(Unit)
    }

    /**
     * Active un thème et retourne ses tokens **appliqués sur la base**
     * (`base.overlay(tokens)`) : la base est choisie selon `theme.base`
     * (\"dark\" → [darkBase], \"light\" → [lightBase]).
     */
    fun activate(id: String): Result<SpaceKaiThemeTokens> {
        val theme = registry[id]
            ?: return Result.failure(NoSuchElementException("Thème inconnu : $id"))
        activeId = id
        val base = if (theme.base == "light") lightBase else darkBase
        return Result.success(base.overlay(theme.tokens))
    }

    /** Thème actuellement actif, ou null si aucun. */
    fun active(): ThemeDefinition? = activeId?.let { registry[it] }

    fun registeredIds(): Set<String> = registry.keys

    // ── Réglages (portés de SpaceKai-OLD, docs/THEME_SYSTEM.md § 4) ──────

    fun setMode(mode: ThemeMode) {
        this.mode = mode
    }

    fun mode(): ThemeMode = mode

    /**
     * Définit la source de couleur. Une source [ThemeColorSource.CUSTOM]
     * exige une couleur de graine ([customSeedColor]) ; sans elle, échec
     * propre (l'app ne change jamais d'état en cas d'erreur).
     */
    fun setColorSource(source: ThemeColorSource, customSeedColor: Long? = null): Result<Unit> {
        if (source == ThemeColorSource.CUSTOM && customSeedColor == null) {
            return Result.failure(
                IllegalArgumentException("Une source CUSTOM exige une couleur de graine (customSeedColor)"),
            )
        }
        this.colorSource = source
        this.customSeedColor = customSeedColor
        return Result.success(Unit)
    }

    fun colorSource(): ThemeColorSource = colorSource

    fun customSeedColor(): Long? = customSeedColor

    // ── Bases intégrées ─────────────────────────────────────────────────

    /** Base claire : palette Material 3 par défaut ([SpaceKaiThemeTokens]). */
    private val lightBase = SpaceKaiThemeTokens()

    /** Base sombre : palette Material 3 dark (fond/surfaces sombres, AMOLED-friendly). */
    private val darkBase = SpaceKaiThemeTokens(
        primary = 0xFFBB86FC,
        onPrimary = 0xFF000000,
        secondary = 0xFF03DAC6,
        background = 0xFF121212,
        surface = 0xFF1E1E1E,
        onSurface = 0xFFE0E0E0,
        error = 0xFFCF6679,
    )
}
