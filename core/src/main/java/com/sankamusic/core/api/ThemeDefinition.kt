package com.sankamusic.core.api

/**
 * Définition déclarative d'un thème (voir docs/THEME_SYSTEM.md § 3).
 * Équivalent Kotlin du manifest de thème documenté :
 *
 * ```json
 * {
 *   "id": "com.souanpt.spacekai.theme.amoled",
 *   "name": "AMOLED",
 *   "version": "1.0.0",
 *   "apiVersion": 1,
 *   "base": "dark",
 *   "tokens": { "color.background": "#000000", ... }
 * }
 * ```
 */
data class ThemeDefinition(
    /** Identifiant unique du thème (reverse-DNS recommandé). */
    val id: String,
    val name: String,
    val version: String,
    /** Version de l'API SpaceKai avec laquelle le thème a été écrit. */
    val apiVersion: Int,
    /** Base sur laquelle le thème s'applique : "dark" ou "light". */
    val base: String,
    /** Tokens de personnalisation (couleurs, surfaces, formes, navigation…). */
    val tokens: SpaceKaiThemeTokens,
) {
    /**
     * Retourne la liste des erreurs de validation ; vide si le thème est valide.
     * Un thème invalide est refusé par le [com.sankamusic.core.ThemeEngine].
     */
    fun validationErrors(): List<String> = buildList {
        if (id.isBlank()) add("id ne doit pas être vide")
        if (name.isBlank()) add("name ne doit pas être vide")
        if (version.isBlank()) add("version ne doit pas être vide")
        if (apiVersion <= 0) add("apiVersion doit être > 0")
        if (base != "dark" && base != "light") add("base doit être 'dark' ou 'light'")
    }
}
