package com.sankamusic.core

import com.sankamusic.core.api.SpaceKaiThemeTokens
import com.sankamusic.core.api.ThemeDefinition

/**
 * Moteur de thèmes minimal (prototype Phase 2 — voir docs/THEME_SYSTEM.md).
 *
 * Responsabilités :
 *  - enregistrer des thèmes après validation de leur définition ;
 *  - activer un thème et retourner ses tokens (fusion `overlay` à venir) ;
 *  - suivre le thème actif.
 *
 * ⚠️ Le mapping des tokens vers MaterialTheme Compose, le Dynamic Color et le
 * live editing seront ajoutés par l'UI du Core (Phase 2/3). Changer de thème
 * ne doit JAMAIS casser l'app : en cas d'erreur, retour au thème précédent
 * (à implémenter avec l'UI).
 */
class ThemeEngine {

    private val registry = mutableMapOf<String, ThemeDefinition>()
    private var activeId: String? = null

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

    /** Active un thème et retourne ses tokens. */
    fun activate(id: String): Result<SpaceKaiThemeTokens> {
        val theme = registry[id]
            ?: return Result.failure(NoSuchElementException("Thème inconnu : $id"))
        activeId = id
        return Result.success(theme.tokens)
    }

    /** Thème actuellement actif, ou null si aucun. */
    fun active(): ThemeDefinition? = activeId?.let { registry[it] }

    fun registeredIds(): Set<String> = registry.keys
}
