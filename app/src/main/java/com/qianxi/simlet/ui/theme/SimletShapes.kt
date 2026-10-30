package com.qianxi.simlet.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * 圆角按参考稿实测：卡片 12dp。
 *
 * 仍然保持逐档递减（外卡 12 > 卡内块 10 > chip 8 > 徽章 5），同半径套同半径会让内块
 * 看起来把卡片撑满。
 */
val SimletCardShape = RoundedCornerShape(12.dp)
val SimletInnerShape = RoundedCornerShape(10.dp)
val SimletButtonShape = RoundedCornerShape(12.dp)
val SimletChipShape = RoundedCornerShape(8.dp)
val SimletBadgeShape = RoundedCornerShape(5.dp)

val SimletShapes = Shapes(
    extraSmall = SimletBadgeShape,
    small = SimletChipShape,
    medium = SimletInnerShape,
    large = SimletCardShape,
    extraLarge = SimletCardShape
)
