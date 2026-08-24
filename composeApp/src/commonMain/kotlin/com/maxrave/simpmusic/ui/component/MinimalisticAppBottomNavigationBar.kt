package com.maxrave.simpmusic.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.currentBackStackEntryAsState
import com.maxrave.simpmusic.expect.HapticManager
import com.maxrave.simpmusic.expect.HapticType
import com.maxrave.simpmusic.ui.navigation.destination.home.AnalyticsDestination
import com.maxrave.simpmusic.ui.navigation.destination.home.HomeDestination
import com.maxrave.simpmusic.ui.navigation.destination.library.LibraryDestination
import com.maxrave.simpmusic.ui.navigation.destination.library.MixForYouDestination
import com.maxrave.simpmusic.ui.navigation.destination.search.SearchDestination
import com.maxrave.simpmusic.ui.theme.typo
import org.jetbrains.compose.resources.stringResource
import kotlin.reflect.KClass

/**
 * A compact, minimal bottom navigation bar.
 *
 * Deliberately NOT a big rounded container: just icons + small labels on a
 * slim strip with a tiny dot as the active indicator. Height is reduced and
 * the background is transparent so it reads as a lightweight nav, closer to
 * a minimalistic design than the classic pill bars.
 */
@Composable
fun MinimalisticAppBottomNavigationBar(
    startDestination: Any = HomeDestination,
    navController: NavController,
    showAnalyticsTab: Boolean = false,
    showMixForYouTab: Boolean = false,
    reloadDestinationIfNeeded: (KClass<*>) -> Unit = { _ -> },
) {
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val bottomNavScreens =
        listOfNotNull(
            BottomNavScreen.Home,
            BottomNavScreen.MixForYou.takeIf { showMixForYouTab },
            BottomNavScreen.Analytics.takeIf { showAnalyticsTab },
            BottomNavScreen.Library,
            BottomNavScreen.Search,
        )
    var selectedIndex by rememberSaveable {
        mutableIntStateOf(
            when (startDestination) {
                is HomeDestination -> BottomNavScreen.Home.ordinal
                is SearchDestination -> BottomNavScreen.Search.ordinal
                is LibraryDestination -> BottomNavScreen.Library.ordinal
                is AnalyticsDestination -> BottomNavScreen.Analytics.ordinal
                is MixForYouDestination -> BottomNavScreen.MixForYou.ordinal
                else -> BottomNavScreen.Home.ordinal
            },
        )
    }

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        bottomNavScreens.forEach { screen ->
            val selected = selectedIndex == screen.ordinal
            Column(
                modifier =
                    Modifier
                        .clip(CircleShape)
                        .clickable {
                            if (selectedIndex == screen.ordinal) {
                                if (currentBackStackEntry?.destination?.hierarchy?.any {
                                        it.hasRoute(screen.destination::class)
                                    } == true
                                ) {
                                    reloadDestinationIfNeeded(screen.destination::class)
                                } else {
                                    navController.navigate(screen.destination)
                                }
                            } else {
                                selectedIndex = screen.ordinal
                                HapticManager.vibrate(HapticType.SELECT)
                                navController.navigate(screen.destination) {
                                    popUpTo(navController.graph.startDestinationId) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        }
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    // screen.icon is a @Composable () -> Unit that already renders the Icon.
                    // Wrap it in a ContentColor so the active tab is tinted with the primary
                    // colour while the rest keep the default onSurfaceVariant.
                    val iconColor =
                        if (selected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    CompositionLocalProvider(LocalContentColor provides iconColor) {
                        screen.icon()
                    }
                    if (selected) {
                        Box(
                            modifier =
                                Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(top = 2.dp)
                                    .size(4.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary),
                        )
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = stringResource(screen.title),
                    style = typo().labelSmall,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    color =
                        if (selected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                )
            }
        }
    }
}
