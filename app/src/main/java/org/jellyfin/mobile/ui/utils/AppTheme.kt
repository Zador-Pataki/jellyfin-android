package org.jellyfin.mobile.ui.utils

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Shapes
import androidx.compose.material.darkColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val colors = remember {
        @Suppress("MagicNumber")
        darkColors(
            primary = Color(0xFFE50914),
            primaryVariant = Color(0xFFB20710),
            secondary = Color(0xFFE50914),
            secondaryVariant = Color(0xFFB20710),
            background = Color(0xFF0B0B0B),
            surface = Color(0xFF181818),
            error = Color(0xFFFFB4AB),
            onPrimary = Color.White,
            onSecondary = Color.White,
            onBackground = Color.White,
            onSurface = Color.White,
            onError = Color(0xFF690005),
        )
    }
    MaterialTheme(
        colors = colors,
        shapes = Shapes(
            small = RoundedCornerShape(4.dp),
            medium = RoundedCornerShape(8.dp),
            large = RoundedCornerShape(0.dp),
        ),
        content = content,
    )
}
