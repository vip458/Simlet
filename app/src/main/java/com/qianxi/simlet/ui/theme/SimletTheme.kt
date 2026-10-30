package com.qianxi.simlet.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowInsetsControllerCompat
import android.app.Activity
import com.qianxi.simlet.ui.UiPrefs

private val LightScheme = lightColorScheme(
    primary = PrimaryLight,
    onPrimary = OnPrimaryLight,
    primaryContainer = NeutralContainerLight,
    onPrimaryContainer = TextPrimaryLight,
    inversePrimary = BackgroundLight,
    secondary = AccentLight,
    onSecondary = OnPrimaryLight,
    secondaryContainer = AccentContainerLight,
    onSecondaryContainer = OnAccentContainerLight,
    // 参考稿没有第三种色相，tertiary 一律并入中性，防止 Material3 默认的藕荷色漏进控件。
    tertiary = PrimaryLight,
    onTertiary = OnPrimaryLight,
    tertiaryContainer = NeutralContainerLight,
    onTertiaryContainer = TextPrimaryLight,
    error = RedLight,
    onError = Color.White,
    errorContainer = RedContainerLight,
    onErrorContainer = OnRedContainerLight,
    background = BackgroundLight,
    onBackground = TextPrimaryLight,
    surface = SurfaceLight,
    onSurface = TextPrimaryLight,
    surfaceVariant = SurfaceAltLight,
    onSurfaceVariant = TextSecondaryLight,
    surfaceBright = SurfaceBrightLight,
    surfaceDim = SurfaceDimLight,
    surfaceContainerLowest = SurfaceLowestLight,
    surfaceContainerLow = SurfaceLowLight,
    surfaceContainer = SurfaceContainerLight,
    surfaceContainerHigh = SurfaceHighLight,
    surfaceContainerHighest = SurfaceHighestLight,
    inverseSurface = InverseSurfaceLight,
    inverseOnSurface = InverseOnSurfaceLight,
    outline = OutlineLight,
    outlineVariant = OutlineVariantLight
)

private val DarkScheme = darkColorScheme(
    primary = PrimaryDark,
    onPrimary = OnPrimaryDark,
    primaryContainer = NeutralContainerDark,
    onPrimaryContainer = TextPrimaryDark,
    inversePrimary = BackgroundDark,
    secondary = AccentDark,
    onSecondary = OnPrimaryDark,
    secondaryContainer = AccentContainerDark,
    onSecondaryContainer = OnAccentContainerDark,
    tertiary = PrimaryDark,
    onTertiary = OnPrimaryDark,
    tertiaryContainer = NeutralContainerDark,
    onTertiaryContainer = TextPrimaryDark,
    error = RedDark,
    onError = OnRedContainerLight,
    errorContainer = RedContainerDark,
    onErrorContainer = OnRedContainerDark,
    background = BackgroundDark,
    onBackground = TextPrimaryDark,
    surface = SurfaceDark,
    onSurface = TextPrimaryDark,
    surfaceVariant = SurfaceAltDark,
    onSurfaceVariant = TextSecondaryDark,
    surfaceBright = SurfaceBrightDark,
    surfaceDim = SurfaceDimDark,
    surfaceContainerLowest = SurfaceLowestDark,
    surfaceContainerLow = SurfaceLowDark,
    surfaceContainer = SurfaceContainerDark,
    surfaceContainerHigh = SurfaceHighDark,
    surfaceContainerHighest = SurfaceHighestDark,
    inverseSurface = InverseSurfaceDark,
    inverseOnSurface = InverseOnSurfaceDark,
    outline = OutlineDark,
    outlineVariant = OutlineVariantDark
)

/** 明暗档位；跟随系统之外都允许用户在设置里强制。 */
enum class NightMode { FollowSystem, Light, Dark }

/**
 * 主题入口。
 *
 * 档位来自 [prefs]（见 ui/UiPrefs），不再由调用方传布尔量——否则设置页改一次开关就要重建整棵
 * 主题树，且状态栏图标明暗会跟不上。
 *
 * 刻意不提供 Material You 动态取色：它按壁纸生成色板，壁纸偏冷界面就偏冷，
 * 与这套「灰阶 + 唯一绿」的规范直接冲突。
 */
@Composable
fun SimletTheme(
    prefs: UiPrefs = UiPrefs.of(LocalContext.current),
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val dark = when (prefs.nightMode) {
        NightMode.Light -> false
        NightMode.Dark -> true
        NightMode.FollowSystem -> isSystemInDarkTheme()
    }
    val scheme = if (dark) DarkScheme else LightScheme

    // enableEdgeToEdge 只按系统深色决定状态栏图标颜色；强制另一档时必须跟着翻，否则图标会看不见。
    val view = LocalView.current
    val window = (context as? Activity)?.window
    if (window != null) {
        WindowInsetsControllerCompat(window, view).isAppearanceLightStatusBars = !dark
    }

    MaterialTheme(colorScheme = scheme, typography = SimletTypography, shapes = SimletShapes, content = content)
}

/** 字号按参考稿的字高折算；字重整体比 Material3 默认收敛一档，靠反差而不是靠粗。 */
private val SimletTypography = Typography(
    headlineMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 26.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 22.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 17.sp),
    bodyLarge = TextStyle(fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 15.sp),
    labelSmall = TextStyle(fontWeight = FontWeight.Normal, fontSize = 12.sp, letterSpacing = 0.2.sp)
)
