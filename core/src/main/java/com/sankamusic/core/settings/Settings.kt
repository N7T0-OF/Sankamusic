package com.sankamusic.core.settings

/**
 * Couche de préférences typées (étape 7 migration SpaceKai — docs/MIGRATION.md).
 *
 * Portée des réglages SpaceKai-OLD (DataStore : chaînes persistées + flags
 * booléens préfixés) : un store de chaînes ([StringSettings]) + des
 * [Preference] typées (booléen, chaîne, enum) avec parse/sérialisation et
 * défaut sûr. L'UI et les fonctionnalités lisent/écrivent des valeurs typées,
 * jamais des chaînes brutes.
 */

/** Store de chaînes minimal (persistance opaque — mémoire, DataStore, fichier…). */
interface StringSettings {
    fun get(key: String): String?
    fun set(key: String, value: String)
}

/** Une préférence typée : clé stable, défaut, parse tolérant, sérialisation. */
data class Preference<T>(
    val key: String,
    val default: T,
    val parse: (String?) -> T?,
    val serialize: (T) -> String,
)

/** Préférence booléenne (valeurs persistées "true"/"false" — port SpaceKai-OLD). */
fun booleanPreference(key: String, default: Boolean): Preference<Boolean> =
    Preference(
        key = key,
        default = default,
        parse = { raw ->
            when (raw?.trim()?.lowercase()) {
                "true", "1", "on" -> true
                "false", "0", "off" -> false
                else -> null
            }
        },
        serialize = { it.toString() },
    )

/** Préférence chaîne. */
fun stringPreference(key: String, default: String): Preference<String> =
    Preference(
        key = key,
        default = default,
        parse = { raw -> raw?.takeIf { it.isNotBlank() } },
        serialize = { it },
    )

/** Préférence enum (parse/sérialisation fournis, ex. `parsePlayerOrientationMode`). */
fun <T : Enum<T>> enumPreference(
    key: String,
    default: T,
    parse: (String?) -> T?,
    serialize: (T) -> String,
): Preference<T> =
    Preference(
        key = key,
        default = default,
        parse = parse,
        serialize = serialize,
    )

/** Accès typé à un [StringSettings] : défaut sûr en cas de valeur manquante/corrompue. */
class TypedSettings(private val store: StringSettings) {

    fun <T> get(preference: Preference<T>): T =
        preference.parse(store.get(preference.key)) ?: preference.default

    fun <T> set(preference: Preference<T>, value: T) {
        store.set(preference.key, preference.serialize(value))
    }
}
