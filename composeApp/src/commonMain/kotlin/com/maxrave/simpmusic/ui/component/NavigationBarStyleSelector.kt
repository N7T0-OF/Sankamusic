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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.maxrave.simpmusic.ui.icon.Home
import com.maxrave.simpmusic.ui.icon.LibraryMusic
import com.maxrave.simpmusic.ui.icon.Search
import com.maxrave.simpmusic.ui.icon.SimpIcons
import com.maxrave.simpmusic.ui.theme.seed
import com.maxrave.simpmusic.ui.theme.typo

/**
 * Navigation bar style presets.
 *
 * Each style maps to a combination of the existing translucent + liquid glass settings:
 * - CLASSIC: solid background, opaque
 * - TRANSLUCENT: semi-transparent, shows content behind
 * - GLASS: full liquid glass effect (blur + vibrancy)
 */
enum class NavigationBarStyle(val label: String, val hasLiquidGlass: Boolean, val hasTranslucent: Boolean) {
    CLASSIC("Classic", false, false),
    TRANSLUCENT("Translucent", false, true),
    GLASS("Glass", true, true),
}

/**
 * A slider-based navigation bar style selector with a live preview.
 *
 * @param currentStyle The currently selected style.
 * @param onStyleChanged Called when the user changes the style.
 */
@Composable
fun NavigationBarStyleSelector(
    currentStyle: NavigationBarStyle,
    onStyleChanged: (NavigationBarStyle) -> Unit,
) {
    val styles = NavigationBarStyle.entries
    var sliderValue by remember(currentStyle) {
        mutableFloatStateOf(styles.indexOf(currentStyle).toFloat())
    }

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        // Live preview
        NavigationBarPreview(style = currentStyle)

        Spacer(modifier = Modifier.height(12.dp))

        // Slider
        Slider(
            value = sliderValue,
            onValueChange = { sliderValue = it },
            onValueChangeFinished = {
                val index = sliderValue.toInt().coerceIn(0, styles.size - 1)
                onStyleChanged(styles[index])
            },
            valueRange = 0f..(styles.size - 1).toFloat(),
            steps = styles.size - 2,
            colors = SliderDefaults.colors(
                thumbColor = seed,
                activeTrackColor = seed,
            ),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        )

        // Label
        Text(
            text = currentStyle.label,
            style = typo().bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
    }
}

/**
 * Visual preview of a navigation bar style.
 */
@Composable
fun NavigationBarPreview(style: NavigationBarStyle) {
    val bgColor = when (style) {
        NavigationBarStyle.CLASSIC -> Color(0xFF1A1A1A)
        NavigationBarStyle.TRANSLUCENT -> Color(0xFF1A1A1A).copy(alpha = 0.7f)
        NavigationBarStyle.GLASS -> Color.White.copy(alpha = 0.12f)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .height(72.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(bgColor),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            listOf(
                Pair(SimpIcons.Home, "Home"),
                Pair(SimpIcons.Search, "Search"),
                Pair(SimpIcons.LibraryMusic, "Library"),
            ).forEach { (icon, label) ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = label,
                        tint = if (style == NavigationBarStyle.GLASS) Color.White else Color.White.copy(alpha = 0.9f),
                        modifier = Modifier.size(24.dp),
                    )
                    Text(
                        text = label,
                        style = typo().bodySmall,
                        color = if (style == NavigationBarStyle.GLASS) Color.White else Color.White.copy(alpha = 0.7f),
                    )
                }
            }
        }
    }
}