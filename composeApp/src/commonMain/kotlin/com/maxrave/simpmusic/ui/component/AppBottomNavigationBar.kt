package com.maxrave.simpmusic.ui.component

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.currentBackStackEntryAsState
import com.maxrave.simpmusic.extension.greyScale
import com.maxrave.simpmusic.ui.navigation.destination.home.AnalyticsDestination
import com.maxrave.simpmusic.ui.navigation.destination.home.HomeDestination
import com.maxrave.simpmusic.ui.navigation.destination.library.LibraryDestination
import com.maxrave.simpmusic.ui.navigation.destination.library.MixForYouDestination
import com.maxrave.simpmusic.ui.navigation.destination.search.SearchDestination
import com.maxrave.simpmusic.ui.theme.typo
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import simpmusic.composeapp.generated.resources.*
import kotlin.reflect.KClass

// SPACEKAI FEATURE: horizontal swipe on the navigation bar to skip tracks — swipe
// LEFT for the next song, RIGHT for the previous one (same direction semantics as
// the NowPlaying artwork pager, which uses SkipToPrevious for a left-to-right
// swipe). Wired from App.kt through onSwipeToNext / onSwipeToPrevious.
private const val SWIPE_SKIP_THRESHOLD_DP = 60f

/**
 * Adds track-skip swipes to a navigation bar/rail: a horizontal drag past the
 * threshold fires [onSwipeToNext] (dragged left) or [onSwipeToPrevious] (right).
 * A tap is not a drag, so tab selection keeps working untouched.
 */
@Composable
private fun Modifier.swipeToSkip(
    onSwipeToNext: (() -> Unit)?,
    onSwipeToPrevious: (() -> Unit)?,
): Modifier {
    if (onSwipeToNext == null && onSwipeToPrevious == null) return this
    // Key on Unit so a recomposition (new lambdas) never restarts an in-flight gesture.
    val latestNext by rememberUpdatedState(onSwipeToNext)
    val latestPrevious by rememberUpdatedState(onSwipeToPrevious)
    val thresholdPx = with(LocalDensity.current) { SWIPE_SKIP_THRESHOLD_DP.dp.toPx() }
    return pointerInput(Unit) {
        var dragged = 0f
        detectHorizontalDragGestures(
            onDragStart = { dragged = 0f },
            onHorizontalDrag = { _, dragAmount -> dragged += dragAmount },
            onDragEnd = {
                when {
                    dragged <= -thresholdPx -> latestNext?.invoke()
                    dragged >= thresholdPx -> latestPrevious?.invoke()
                }
            },
        )
    }
}

/**
 * The phone bottom bar with liquid glass OFF: the same floating capsule-and-FAB form as the glass
 * bar, drawn flat. One capsule of tabs (Search stays out — it is the round button beside it, the
 * same split the glass bar makes), a sliding rounded indicator instead of the frosted blob, solid
 * theme surfaces instead of refraction. Geometry mirrors the glass bar so switching the setting
 * changes the material, not the layout: 96dp tab cap, 64dp bar, 56dp indicator and FAB.
 */
@Composable
fun AppBottomNavigationBar(
    startDestination: Any = HomeDestination,
    navController: NavController,
    isTranslucentBackground: Boolean = false,
    showLabels: Boolean = true,
    showAnalyticsTab: Boolean = false,
    showMixForYouTab: Boolean = false,
    // SPACEKAI FEATURE: minimalisticNavigation — compact variant: the optional
    // Mix-for-you / Analytics tabs are dropped. Both tab lists below must filter
    // together so the bottom bar and the landscape rail stay consistent.
    minimalistic: Boolean = false,
    reloadDestinationIfNeeded: (KClass<*>) -> Unit = { _ -> },
    // SPACEKAI FEATURE: horizontal swipe on the bar to skip tracks (left = next,
    // right = previous). Null disables the gesture.
    onSwipeToNext: (() -> Unit)? = null,
    onSwipeToPrevious: (() -> Unit)? = null,
) {
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    // `ordinal` identifies a tab, it is NOT the position — Mix for you and Analytics sit before
    // Library here while keeping the ordinal they were declared with, so that the numbering stays
    // stable whether or not those tabs are present.
    val bottomNavScreens =
        listOfNotNull(
            BottomNavScreen.Home,
            BottomNavScreen.MixForYou.takeIf { showMixForYouTab && !minimalistic },
            BottomNavScreen.Analytics.takeIf { showAnalyticsTab && !minimalistic },
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
                else -> BottomNavScreen.Home.ordinal // Default to Home if not recognized
            },
        )
    }
    // A tab can disappear from the list under the user: tracking gets turned off while Analytics is
    // selected, the YouTube session ends while Mix for you is, or the minimalistic variant removes
    // both. Fall back to Home in all cases so nothing is left highlighted.
    LaunchedEffect(showAnalyticsTab, showMixForYouTab, minimalistic) {
        if (((!showAnalyticsTab || minimalistic) && selectedIndex == BottomNavScreen.Analytics.ordinal) ||
            ((!showMixForYouTab || minimalistic) && selectedIndex == BottomNavScreen.MixForYou.ordinal)
        ) {
            selectedIndex = BottomNavScreen.Home.ordinal
        }
    }
    val selectTab: (BottomNavScreen) -> Unit = { screen ->
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
            navController.navigate(screen.destination) {
                popUpTo(navController.graph.startDestinationId) {
                    saveState = true
                }
                launchSingleTop = true
                restoreState = true
            }
        }
    }

    // Search rides in its own circular button, so the capsule holds everything else.
    val barTabs = bottomNavScreens.filter { it != BottomNavScreen.Search }
    // SPACEKAI FEATURE: minimalisticNavigation — icons-only. Hides the item texts and
    // shrinks the capsule to the icon height. independent of the "hide text" setting
    // (showLabels), so either one can blank the labels; minimalistic also reduces size.
    val showItemLabels = showLabels && !minimalistic
    val barHeight = if (showItemLabels) FlatBarHeight else FlatIconBarHeight
    val indicatorHeight = if (showItemLabels) FlatIndicatorHeight else FlatIconIndicatorHeight

    // The translucent switch tints the CAPSULE ITSELF, never a strip behind it — the area around
    // the floating cluster always shows the page. ON reads the content through the pill; OFF is a
    // solid surface. The indicator stays nearer opaque so the selection survives busy artwork.
    val capsuleColor =
        MaterialTheme.colorScheme.surfaceContainer.copy(alpha = if (isTranslucentBackground) 0.72f else 1f)
    val indicatorColor =
        MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = if (isTranslucentBackground) 0.85f else 1f)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        // One centred cluster — capsule, gap, FAB — exactly like the glass bar: fill = false keeps
        // the capsule at its measured width, so the leftover goes around the cluster instead of
        // wedging itself between the capsule and the search button.
        horizontalArrangement = Arrangement.Center,
        modifier =
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(NavigationBarDefaults.windowInsets)
                .padding(horizontal = 16.dp)
                .padding(top = 4.dp, bottom = 8.dp),
    ) {
        BoxWithConstraints(
            Modifier.weight(1f, fill = false).swipeToSkip(
                onSwipeToNext = onSwipeToNext,
                onSwipeToPrevious = onSwipeToPrevious,
            ),
        ) {
            // Every tab the same width, capped so two tabs on a wide screen do not stretch into
            // slabs — the same budget rule as the glass tab bar.
            val tabWidth = ((maxWidth - CapsuleInset * 2) / barTabs.size).coerceAtMost(FlatTabWidth)
            val selectedPosition = barTabs.indexOfFirst { it.ordinal == selectedIndex }
            val indicatorOffset by animateDpAsState(tabWidth * selectedPosition.coerceAtLeast(0), label = "flatBarIndicator")
            Box(
                modifier =
                    Modifier
                        .height(barHeight)
                        .clip(RoundedCornerShape(barHeight / 2))
                        .background(capsuleColor)
                        .padding(horizontal = CapsuleInset),
                contentAlignment = Alignment.CenterStart,
            ) {
                // The sliding indicator — the flat stand-in for the glass bar's frosted blob. Hidden
                // while Search (a non-capsule tab) is the selection, so nothing sits half-lit.
                if (selectedPosition >= 0) {
                    Box(
                        modifier =
                            Modifier
                                .offset(x = indicatorOffset)
                                .size(width = tabWidth, height = indicatorHeight)
                                .clip(RoundedCornerShape(FlatIndicatorHeight / 2))
                                .background(indicatorColor),
                    )
                }
                Row {
                    barTabs.forEach { screen ->
                        val selected = selectedIndex == screen.ordinal
                        val contentColor =
                            if (selected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        Column(
                            modifier =
                                Modifier
                                    .width(tabWidth)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(indicatorHeight / 2))
                                    .clickable { selectTab(screen) },
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            CompositionLocalProvider(LocalContentColor provides contentColor) {
                                screen.icon()
                            }
                            if (showItemLabels) {
                                Text(
                                    stringResource(screen.title),
                                    style = typo().bodySmall,
                                    color = contentColor,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.size(12.dp))
        val searchSelected = selectedIndex == BottomNavScreen.Search.ordinal
        Box(
            modifier =
                Modifier
                    .size(FlatIndicatorHeight)
                    .clip(CircleShape)
                    .background(if (searchSelected) indicatorColor else capsuleColor)
                    .clickable { selectTab(BottomNavScreen.Search) },
            contentAlignment = Alignment.Center,
        ) {
            CompositionLocalProvider(
                LocalContentColor provides
                    if (searchSelected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
            ) {
                BottomNavScreen.Search.icon()
            }
        }
    }
}

// Mirrors the glass tab bar's geometry (TabWidth/BarHeight/BlobHeight/BarInset in
// LiquidGlassTabBar.android.kt) so the two bars are one form in two materials.
private val FlatTabWidth = 96.dp
private val FlatBarHeight = 64.dp
private val FlatIndicatorHeight = 56.dp
private val CapsuleInset = 6.dp
// SPACEKAI FEATURE: minimalisticNavigation — icons-only compact geometry.
private val FlatIconBarHeight = 52.dp
private val FlatIconIndicatorHeight = 44.dp

@Composable
fun AppNavigationRail(
    startDestination: Any = HomeDestination,
    navController: NavController,
    showAnalyticsTab: Boolean = false,
    showMixForYouTab: Boolean = false,
    // SPACEKAI FEATURE: minimalisticNavigation — compact variant: the optional
    // Mix-for-you / Analytics tabs are dropped, same as the bottom bar.
    minimalistic: Boolean = false,
    reloadDestinationIfNeeded: (KClass<*>) -> Unit = { _ -> },
    modifier: Modifier = Modifier,
    // When false, the rail renders icon-only (used by the “hide text” setting and by the
    // compact phone-landscape variant where vertical space is scarce).
    showLabels: Boolean = true,
    // Tablets/desktop show the brand logo as a header; the phone-landscape right rail is
    // compact and skips it.
    showHeader: Boolean = true,
    // Window insets to respect (status/navigation bar, display cutout, safe areas).
    // The right-side rail must also pad the end (right) edge, not just top/bottom.
    windowInsets: WindowInsets = NavigationRailDefaults.windowInsets,
    // SPACEKAI FEATURE: horizontal swipe on the rail to skip tracks (left = next,
    // right = previous), same as the bottom bar. Null disables the gesture.
    onSwipeToNext: (() -> Unit)? = null,
    onSwipeToPrevious: (() -> Unit)? = null,
) {
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    // See the note in AppBottomNavigationBar: `ordinal` is the tab's identity, not its position.
    val bottomNavScreens =
        listOfNotNull(
            BottomNavScreen.Home,
            BottomNavScreen.MixForYou.takeIf { showMixForYouTab && !minimalistic },
            BottomNavScreen.Analytics.takeIf { showAnalyticsTab && !minimalistic },
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
                else -> BottomNavScreen.Home.ordinal // Default to Home if not recognized
            },
        )
    }
    // A tab can disappear from the list under the user: tracking gets turned off while Analytics is
    // selected, the YouTube session ends while Mix for you is, or the minimalistic variant removes
    // both. Fall back to Home in all cases so nothing is left highlighted.
    LaunchedEffect(showAnalyticsTab, showMixForYouTab, minimalistic) {
        if (((!showAnalyticsTab || minimalistic) && selectedIndex == BottomNavScreen.Analytics.ordinal) ||
            ((!showMixForYouTab || minimalistic) && selectedIndex == BottomNavScreen.MixForYou.ordinal)
        ) {
            selectedIndex = BottomNavScreen.Home.ordinal
        }
    }
    NavigationRail(
        modifier =
            modifier.swipeToSkip(
                onSwipeToNext = onSwipeToNext,
                onSwipeToPrevious = onSwipeToPrevious,
            ),
        windowInsets = windowInsets,
    ) {
        if (showHeader) {
            Spacer(Modifier.height(16.dp))
            Box(Modifier.padding(horizontal = 16.dp)) {
                Box(
                    Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        painter = painterResource(Res.drawable.mono),
                        contentDescription = null,
                        modifier =
                            Modifier
                                .height(32.dp)
                                .clip(CircleShape),
                    )
                }
            }
        }
        Spacer(Modifier.weight(1f))
        bottomNavScreens.forEach { screen ->
            NavigationRailItem(
                icon = screen.icon,
                label =
                    if (showLabels && !minimalistic) {
                        {
                            Text(
                                stringResource(screen.title),
                                style =
                                    if (selectedIndex == screen.ordinal) {
                                        typo().bodySmall
                                    } else {
                                        typo().bodySmall.greyScale()
                                    },
                            )
                        }
                    } else {
                        null
                    },
                selected = selectedIndex == screen.ordinal,
                onClick = {
                    if (selectedIndex == screen.ordinal) {
                        if (currentBackStackEntry?.destination?.hierarchy?.any {
                                it.hasRoute(screen.destination::class)
                            } == true
                        ) {
                            reloadDestinationIfNeeded(
                                screen.destination::class,
                            )
                        } else {
                            navController.navigate(screen.destination)
                        }
                    } else {
                        selectedIndex = screen.ordinal
                        navController.navigate(screen.destination) {
                            popUpTo(navController.graph.startDestinationId) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                },
            )
        }
        Spacer(Modifier.height(32.dp))
    }
}