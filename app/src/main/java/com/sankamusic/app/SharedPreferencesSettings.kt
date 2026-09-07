package com.sankamusic.app

import android.content.Context
import android.content.SharedPreferences
import com.sankamusic.core.settings.StringSettings

/**
 * Persistance **réelle** des préférences de la plateforme sur SharedPreferences
 * (implémentation [StringSettings] du Core). Ferme le gap « persistance réelle
 * (DataStore) au lieu de la mémoire » de `docs/MIGRATION.md` étape 7 sans
 * dépendance additionnelle : mêmes garanties (clé → chaîne, défaut sûr côté
 * [com.sankamusic.core.settings.TypedSettings]), valeurs conservées après
 * redémarrage du processus.
 */
class SharedPreferencesSettings private constructor(
    private val prefs: SharedPreferences,
) : StringSettings {

    override fun get(key: String): String? = prefs.getString(key, null)

    override fun set(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }

    companion object {
        /** Store persistant par défaut de l'application. */
        fun from(context: Context, name: String = "spacekai_settings"): SharedPreferencesSettings =
            SharedPreferencesSettings(
                context.getSharedPreferences(name, Context.MODE_PRIVATE),
            )
    }
}
