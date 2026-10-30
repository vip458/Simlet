package com.qianxi.simlet.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * 色值是逐像素从参考稿上取点量出来的，不是目测调的。
 *
 * 灰阶带黄调（每支的 b 通道都低于 r），所以不能用 Material3 默认灰或纯中性灰，
 * 否则会偏冷、和参考稿对不上。整套 UI 唯一的彩色是 Reicon 那支绿。
 */

// ---- 浅色 ----
val BackgroundLight = Color(0xFFF6F6F6)      // 页面底
val SurfaceLight = Color(0xFFFCFCFC)         // 卡片底
val SurfaceAltLight = Color(0xFFF2F2F0)      // chip / 输入底（派生：卡片与页面底之间）
val TextPrimaryLight = Color(0xFF131313)     // 主文字与图标
val TextSecondaryLight = Color(0xFF82817D)   // 行尾值、分组标题
val OutlineLight = Color(0xFF9E9D99)         // 箭头、占位、弱化态
val OutlineVariantLight = Color(0xFFE6E6E6)  // 分割线
val NeutralContainerLight = Color(0xFFEBEBE9)

// ---- 深色 ----
val BackgroundDark = Color(0xFF0D0D0B)
val SurfaceDark = Color(0xFF1A1917)
val SurfaceAltDark = Color(0xFF232320)
val TextPrimaryDark = Color(0xFFDBDAD8)
val TextSecondaryDark = Color(0xFF7C7B77)
val OutlineDark = Color(0xFF494846)
val OutlineVariantDark = Color(0xFF2A2925)
val NeutralContainerDark = Color(0xFF2A2926)

/**
 * 主色即「选中态与主按钮的底色」，浅色下是近黑、深色下反白——参考稿通篇没有实心彩色按钮，
 * 强调靠字重与黑白反差，不靠色相。
 */
val PrimaryLight = TextPrimaryLight
val OnPrimaryLight = SurfaceLight
val PrimaryDark = Color(0xFFF6F6F6)
val OnPrimaryDark = TextPrimaryLight

/** 唯一彩色：参考稿「体验版」徽章那支绿。只用于徽章、状态点、成功提示。 */
val AccentLight = Color(0xFF699973)
val AccentContainerLight = Color(0xFFE6EDE6)
val OnAccentContainerLight = Color(0xFF2C4A33)
val AccentDark = Color(0xFF89CC97)
val AccentContainerDark = Color(0xFF2A342B)
val OnAccentContainerDark = Color(0xFFBFE3C7)

// 错误色保留系统红：参考稿没有失败态场景，可用性优先。
val RedLight = Color(0xFFB3261E)
val RedContainerLight = Color(0xFFFFE8E6)
val OnRedContainerLight = Color(0xFF410E0B)
val RedDark = Color(0xFFFFB4AB)
val RedContainerDark = Color(0xFF8C1D18)
val OnRedContainerDark = Color(0xFFFFDAD6)

/**
 * 表面色阶。
 *
 * 上面只实测到了页面底、卡片底、chip 底三档，但 Material3 的弹层/输入框/开关走的是
 * `surfaceContainer*` 那一组角色。lightColorScheme 对没传的角色用的是**默认紫调灰**
 * （`#F7F2FA` 那一片），弹层会直接把它带回来——所以这一组必须逐档补齐，
 * 不能省。取值是在实测的三档之间按同一黄调线性插值，不是照抄默认值。
 */
// ---- 浅色 ----
val SurfaceLowestLight = Color(0xFFFFFFFF)
val SurfaceLowLight = SurfaceLight          // 弹层与卡片同底
val SurfaceContainerLight = Color(0xFFF7F7F5)
val SurfaceHighLight = SurfaceAltLight      // chip / 输入底
val SurfaceHighestLight = Color(0xFFEDEDEA)
val SurfaceBrightLight = SurfaceLight
val SurfaceDimLight = Color(0xFFDCDCD9)
val InverseSurfaceLight = Color(0xFF2A2A27)
val InverseOnSurfaceLight = Color(0xFFF3F3F0)

// ---- 深色 ----
val SurfaceLowestDark = Color(0xFF080806)
val SurfaceLowDark = Color(0xFF151513)
val SurfaceContainerDark = SurfaceDark
val SurfaceHighDark = SurfaceAltDark
val SurfaceHighestDark = Color(0xFF2E2E2A)
val SurfaceBrightDark = Color(0xFF353532)
val SurfaceDimDark = BackgroundDark
val InverseSurfaceDark = TextPrimaryDark
val InverseOnSurfaceDark = Color(0xFF2A2927)
