package com.sankamusic.core.update

import com.sankamusic.core.ThemeEngine
import com.sankamusic.core.UiExtensionRegistry
import com.sankamusic.core.api.ColorSchemeStrategy
import com.sankamusic.core.api.NavigationTab
import com.sankamusic.core.api.Orientation
import com.sankamusic.core.api.PlayerOrientationMode
import com.sankamusic.core.api.SpaceKaiContracts
import com.sankamusic.core.api.SpaceKaiThemeTokens
import com.sankamusic.core.api.ThemeColorSource
import com.sankamusic.core.api.ThemeDefinition
import com.sankamusic.core.api.ThemeMode
import com.sankamusic.core.api.UpstreamAdapter
import com.sankamusic.core.api.builtInSpaceKaiFeatures
import com.sankamusic.core.api.effectiveHapticsEnabled
import com.sankamusic.core.api.effectivePlayerOrientationMode
import com.sankamusic.core.api.effectiveSeedColor
import com.sankamusic.core.api.model.UnifiedTrack
import com.sankamusic.core.api.parseHapticsEnabled
import com.sankamusic.core.api.parsePlayerOrientationMode
import com.sankamusic.core.api.resolveColorSchemeStrategy
import com.sankamusic.core.api.resolvePlayerOrientation
import com.sankamusic.core.api.shouldFireHaptic
import com.sankamusic.core.api.withOledPinning
import com.sankamusic.core.player.PlayerController
import com.sankamusic.core.player.PlayerStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * INVARIANT D'INTÉGRITÉ DES CONTRATS (docs/FEATURE_MANIFEST.md § 3) :
 *
 * **Un Adapter ne doit jamais déclarer un contrat sans implémenter
 * réellement ses opérations.** Un `satisfiesContract("player-api") = true`
 * alors que le player est cassé est une faute : la fonctionnalité serait
 * activée mais ne fonctionnerait pas.
 *
 * Pour chaque Adapter du système, pour chaque contrat déclaré :
 *  1. le contrat doit être CONNU ([SpaceKaiContracts.requiredOperations] non vide) ;
 *  2. le contrat doit être référencé par le manifest (sinon déclaration orpheline) ;
 *  3. les opérations requises doivent EXISTER et FONCTIONNER — elles sont
 *     exercées sur les vraies implémentations (registre, moteur, contrôleur).
 *
 * ⚠️ Les sous-adaptateurs base (player/library/playlists) ne sont pas encore
 * reliés (Phase 2) : ils échouent explicitement — ce sont des opérations
 * D'ADAPTER, distinctes des opérations de contrat (API SpaceKai, testées
 * ici). Le jour où un contrat pointera vers une opération d'adapter, celle-ci
 * devra être ajoutée à l'exercice ci-dessous.
 */
class AdapterContractIntegrityTest {

    /** Tous les Adapters enregistrés : v1 (1.7.x) et v2 (2.0.x audité). */
    private val adapters: List<UpstreamAdapter> = listOf(SimpMusicAdapter(), SimpMusicAdapterV2())

    @Test
    fun `every declared contract is a known contract`() {
        adapters.forEach { adapter ->
            SpaceKaiContracts.SIMPMUSIC_ADAPTER_V1.forEach { contract ->
                assertTrue(
                    "contrat '$contract' déclaré par ${adapter.info.version} mais inconnu " +
                        "(requiredOperations vide) — contrat fantôme",
                    SpaceKaiContracts.requiredOperations(contract).isNotEmpty(),
                )
            }
        }
    }

    @Test
    fun `every manifest contract is declared by the adapter`() {
        // L'inverse : une fonctionnalité du manifest avec un contrat que
        // l'adapter ne déclare pas serait désactivée EN SILENCE.
        adapters.forEach { adapter ->
            builtInSpaceKaiFeatures.features.forEach { feature ->
                val contract = feature.contract ?: return@forEach
                assertTrue(
                    "contrat '$contract' de '${feature.id}' non déclaré par ${adapter.info.version}",
                    adapter.satisfiesContract(contract),
                )
            }
        }
    }

    @Test
    fun `declared contracts have working operations`() {
        adapters.forEach { adapter ->
            SpaceKaiContracts.SIMPMUSIC_ADAPTER_V1.forEach { contract ->
                val ops = SpaceKaiContracts.requiredOperations(contract)
                assertTrue(ops.isNotEmpty())
                // Exerce les opérations réelles de l'API SpaceKai : si une
                // opération casse, le contrat ne peut plus être déclaré.
                when (contract) {
                    SpaceKaiContracts.NAVIGATION -> exerciseNavigation(ops)
                    SpaceKaiContracts.THEME -> exerciseTheme(ops)
                    SpaceKaiContracts.ORIENTATION -> exerciseOrientation(ops)
                    SpaceKaiContracts.PLAYER -> exercisePlayer(ops)
                    SpaceKaiContracts.HAPTICS -> exerciseHaptics(ops)
                    SpaceKaiContracts.DYNAMIC_COLOR -> exerciseDynamicColor(ops)
                    else -> throw AssertionError("contrat '$contract' sans exercice défini")
                }
            }
        }
    }

    // ── Exercices des opérations par contrat ────────────────────────────────

    private fun exerciseNavigation(ops: List<String>) {
        assertTrue(ops.containsAll(listOf("registerNavigationTab", "navigationTabs", "removeNavigationTab")))
        val registry = UiExtensionRegistry()
        registry.registerNavigationTab(NavigationTab(id = "t", label = "Test", priority = 1))
        assertEquals(1, registry.navigationTabs().size)
        registry.removeNavigationTab("t")
        assertTrue(registry.navigationTabs().isEmpty())
    }

    private fun exerciseTheme(ops: List<String>) {
        assertTrue(ops.containsAll(listOf("setMode", "setColorSource", "activate", "lightBaseTokens", "darkBaseTokens")))
        val engine = ThemeEngine()
        engine.setMode(ThemeMode.LIGHT)
        assertEquals(ThemeMode.LIGHT, engine.mode())
        assertTrue(engine.setColorSource(ThemeColorSource.DEFAULT).isSuccess)
        assertTrue(engine.lightBaseTokens().background != engine.darkBaseTokens().background)
        val theme = ThemeDefinition(
            id = "demo", name = "Demo", version = "1.0.0", apiVersion = 1,
            base = "dark", tokens = SpaceKaiThemeTokens(primary = 0xFF112233),
        )
        assertTrue(engine.register(theme).isSuccess)
        assertTrue(engine.activate("demo").isSuccess)
    }

    private fun exerciseOrientation(ops: List<String>) {
        assertTrue(ops.containsAll(listOf("resolvePlayerOrientation", "parsePlayerOrientationMode", "effectivePlayerOrientationMode")))
        assertEquals(Orientation.LANDSCAPE, resolvePlayerOrientation(PlayerOrientationMode.FORCE_LANDSCAPE, Orientation.PORTRAIT))
        assertEquals(Orientation.PORTRAIT, resolvePlayerOrientation(PlayerOrientationMode.FOLLOW_SYSTEM, Orientation.PORTRAIT))
        assertEquals(PlayerOrientationMode.FORCE_LANDSCAPE, parsePlayerOrientationMode("landscape"))
        assertEquals(PlayerOrientationMode.FOLLOW_SYSTEM, effectivePlayerOrientationMode("garbage"))
    }

    private fun exercisePlayer(ops: List<String>) {
        assertTrue(ops.containsAll(listOf("play", "playQueue", "pause", "resume", "togglePlayPause", "next", "seekTo", "enqueue", "snapshot")))
        val controller = PlayerController()
        val a = UnifiedTrack(id = "1", title = "A", provider = "test")
        val b = UnifiedTrack(id = "2", title = "B", provider = "test")
        assertTrue(controller.play(a).isSuccess)
        assertTrue(controller.pause().isSuccess)
        assertTrue(controller.resume().isSuccess)
        assertTrue(controller.togglePlayPause().isSuccess) // PLAYING → PAUSED
        assertTrue(controller.playQueue(listOf(a, b)).isSuccess)
        assertTrue(controller.next().isSuccess)
        assertTrue(controller.seekTo(1_000).isSuccess)
        controller.enqueue(b)
        assertEquals(PlayerStatus.PLAYING, controller.snapshot().status)
    }

    private fun exerciseHaptics(ops: List<String>) {
        assertTrue(ops.containsAll(listOf("shouldFireHaptic", "effectiveHapticsEnabled", "parseHapticsEnabled")))
        assertTrue(shouldFireHaptic(true))
        assertFalse(shouldFireHaptic(false))
        assertEquals(true, parseHapticsEnabled("on"))
        assertFalse(effectiveHapticsEnabled("garbage"))
    }

    private fun exerciseDynamicColor(ops: List<String>) {
        assertTrue(ops.containsAll(listOf("resolveColorSchemeStrategy", "effectiveSeedColor", "withOledPinning")))
        assertEquals(ColorSchemeStrategy.WALLPAPER_DYNAMIC, resolveColorSchemeStrategy(ThemeColorSource.WALLPAPER, true))
        assertEquals(ColorSchemeStrategy.SEED_GENERATED, resolveColorSchemeStrategy(ThemeColorSource.WALLPAPER, false))
        assertEquals(0x112233L, effectiveSeedColor(ThemeColorSource.CUSTOM, 0x112233L))
        assertEquals(0xFF000000L, SpaceKaiThemeTokens().withOledPinning(isDark = true).background)
    }
}
