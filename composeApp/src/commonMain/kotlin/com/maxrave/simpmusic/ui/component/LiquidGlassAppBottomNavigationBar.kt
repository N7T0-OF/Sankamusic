package com.maxrave.simpmusic.ui.component

import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import com.maxrave.simpmusic.expect.ui.PlatformBackdrop
import com.maxrave.simpmusic.ui.icon.AutoGraph
import com.maxrave.simpmusic.ui.icon.Home
import com.maxrave.simpmusic.ui.icon.LibraryMusic
import com.maxrave.simpmusic.ui.icon.Search
import com.maxrave.simpmusic.ui.icon.Sensors
import com.maxrave.simpmusic.ui.icon.SimpIcons
import com.maxrave.simpmusic.ui.navigation.destination.home.AnalyticsDestination
import com.maxrave.simpmusic.ui.navigation.destination.home.HomeDestination
import com.maxrave.simpmusic.ui.navigation.destination.library.LibraryDestination
import com.maxrave.simpmusic.ui.navigation.destination.library.MixForYouDestination
import com.maxrave.simpmusic.ui.navigation.destination.search.SearchDestination
import com.maxrave.simpmusic.viewModel.SharedViewModel
import org.jetbrains.compose.resources.StringResource
import simpmusic.composeapp.generated.resources.Res
import simpmusic.composeapp.generated.resources.analytics
import simpmusic.composeapp.generated.resources.home
import simpmusic.composeapp.generated.resources.library
import simpmusic.composeapp.generated.resources.mix
import simpmusic.composeapp.generated.resources.search
import kotlin.reflect.KClass

@Composable
expect fun LiquidGlassAppBottomNavigationBar(
    startDestination: Any = HomeDestination,
    navController: NavController,
    backdrop: PlatformBackdrop,
    viewModel: SharedViewModel,
    isScrolledToTop: Boolean = false,
    showAnalyticsTab: Boolean = false,
    showMixForYouTab: Boolean = false,
    // SPACEKAI FEATURE: minimalisticNavigation — compact variant: icons-only, same
    // destinations (Mix-for-you is never dropped by the style); Analytics remains
    // available when local tracking is enabled.
    minimalistic: Boolean = false,
    // SPACEKAI FEATURE: personalized navigation — when non-null, this resolved tab list
    // (reordered / hidden per the user's saved config) replaces the bar's internal order.
    navTabs: List<BottomNavScreen>? = null,
    onOpenNowPlaying: () -> Unit = {},
    reloadDestinationIfNeeded: (KClass<*>) -> Unit = { _ -> },
)

sealed class BottomNavScreen(
    val ordinal: Int,
    // SPACEKAI FEATURE: stable identity key ("home" / "search" / ...) used by the
    // personalized-navigation resolver — `ordinal` is a position identity, not a name.
    val key: String,
    val destination: Any,
    val title: StringResource,
    val icon: @Composable () -> Unit,
) {
    data object Home : BottomNavScreen(
        ordinal = 0,
        key = "home",
        destination = HomeDestination,
        title = Res.string.home,
        icon = {
            Icon(
                SimpIcons.Home,
                contentDescription = null,
            )
        },
    )

    data object Search : BottomNavScreen(
        ordinal = 1,
        key = "search",
        destination = SearchDestination,
        title = Res.string.search,
        icon = {
            Icon(
                SimpIcons.Search,
                contentDescription = null,
            )
        },
    )

    data object Library : BottomNavScreen(
        ordinal = 2,
        key = "library",
        destination = LibraryDestination,
        title = Res.string.library,
        icon = {
            Icon(
                imageVector = SimpIcons.LibraryMusic,
                contentDescription = null,
            )
        },
    )

    // Only shown when local tracking is enabled.
    data object Analytics : BottomNavScreen(
        ordinal = 3,
        key = "analytics",
        destination = AnalyticsDestination,
        title = Res.string.analytics,
        icon = {
            Icon(
                imageVector = SimpIcons.AutoGraph,
                contentDescription = null,
            )
        },
    )

    // Only shown while signed in to YouTube — an anonymous session gets no mixes.
    // Labelled "Mix", not "Mix for you": the full title is the widest label in the bar and forces
    // every tab to be that wide. The screen itself still uses the full title.
    data object MixForYou : BottomNavScreen(
        ordinal = 4,
        key = "mix",
        destination = MixForYouDestination,
        title = Res.string.mix,
        icon = {
            Icon(
                imageVector = SimpIcons.Sensors,
                contentDescription = null,
            )
        },
    )
}