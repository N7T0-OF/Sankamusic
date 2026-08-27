package com.sankamusic.app

import android.app.Application
import android.util.Log
import com.sankamusic.core.PluginEngine
import com.sankamusic.core.api.NetworkApi
import com.sankamusic.core.api.SpaceKaiApi
import com.sankamusic.core.api.UpstreamInfo
import com.sankamusic.core.update.HttpGitHubReleasesClient
import com.sankamusic.core.update.SemVer
import com.sankamusic.core.update.UpdateEngine
import com.sankamusic.plugins.hellospacekai.HelloSpaceKaiPlugin

/**
 * Point d'entrée applicatif (Phase 2/3 — voir docs/ROADMAP.md) :
 *  1. injecte l'implémentation de la [SpaceKaiApi] (réseau réel) ;
 *  2. démarre le [PluginEngine] et enregistre les plugins embarqués ;
 *  3. expose le [UpdateEngine] branché sur les repos réels
 *     (faits vérifiés le 2026-08-27, docs/UPSTREAM_SYSTEM.md § 8).
 *
 * Règles respectées :
 *  - un plugin invalide ou qui plante est isolé (état CRASHED) sans faire
 *    planter l'application ;
 *  - les plugins ne sont activés qu'après l'injection de la [SpaceKaiApi] ;
 *  - l'updater n'installe jamais rien tout seul (vérification SHA-256 avant
 *    installation, consentement utilisateur — Phase 4).
 */
class SankamusicApp : Application() {

    private val pluginEngine: PluginEngine = PluginEngine()

    private val network: NetworkApi by lazy { HttpNetworkApi() }

    /** Moteur de mises à jour — repos réels, jamais de mise à jour automatique. */
    val updateEngine: UpdateEngine by lazy {
        UpdateEngine(
            installedVersion = SemVer.parse(BuildConfig.SANKAMUSIC_VERSION)!!,
            upstreamInfo = UpstreamInfo(
                repository = "maxrave-dev/SimpMusic",
                // "TBD" tant que la base SimpMusic n'est pas réellement intégrée
                // (UpstreamAdapter, Phase 2) : la compatibilité affichera INCOMPATIBLE,
                // ce qui est le comportement conservateur voulu.
                version = BuildConfig.SANKAMUSIC_UPSTREAM_VERSION,
                adapterVersion = 1,
                compatibility = "inconnue",
            ),
            releasesClient = HttpGitHubReleasesClient(network),
            sankamusicRepository = "N7T0-OF/Sankamusic",
            upstreamRepository = "maxrave-dev/SimpMusic",
        )
    }

    override fun onCreate() {
        super.onCreate()

        SpaceKaiApi.instance = DefaultSpaceKaiApi(networkApi = network)

        pluginEngine.register(HelloSpaceKaiPlugin())
        pluginEngine.enable(HelloSpaceKaiPlugin.ID)
            .onFailure { Log.e(TAG, "Échec d'activation de ${HelloSpaceKaiPlugin.ID}", it) }
    }

    companion object {
        private const val TAG = "SankamusicApp"
    }
}
