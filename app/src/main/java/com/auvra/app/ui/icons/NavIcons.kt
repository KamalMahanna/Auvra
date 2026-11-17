package com.auvra.app.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Navigation bar icons ported from BitChord:
 * - [Home]: Filled and stroked house glyph with rounded joins and door notch.
 * - [Search]: Cupertino-style thick-ringed search glyph with optical balance.
 * - [Library]: Cupertino-style stacked cards library glyph.
 */
object NavIcons {

    private val stroke = SolidColor(Color.Black)

    val Home: ImageVector by lazy {
        ImageVector.Builder(
            name = "bc_home",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                fill = stroke,
                stroke = stroke,
                strokeLineWidth = 1.7f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(12f, 4.05f)
                lineTo(19.75f, 11.35f)
                lineTo(19.75f, 19.65f)
                lineTo(14.95f, 19.65f)
                lineTo(14.95f, 15.6f)
                lineTo(9.05f, 15.6f)
                lineTo(9.05f, 19.65f)
                lineTo(4.25f, 19.65f)
                lineTo(4.25f, 11.35f)
                close()
            }
        }.build()
    }

    val Library: ImageVector by lazy {
        fontGlyph(
            "bc_tab_library",
            "M154 27.24Q156 0.24 185 0.24H322Q352 0.24 353 27.24Z" +
                "M116 87.24Q121 56.24 150 56.24H356Q385 56.24 390 87.24Z" +
                "M133 500.24Q66 500.24 66 434.24V187.24Q66 121.24 133 121.24H378Q446 121.24 446 187.24V434.24Q446 500.24 385 500.24Z",
        )
    }

    val Search: ImageVector by lazy {
        fontGlyph(
            "bc_tab_search",
            "M219 374.24Q151 374.24 102.5 325.74Q54 277.24 54 209.24Q54 141.24 102.5 92.74Q151 44.24 219 44.24Q287 44.24 335.5 92.74Q384 141.24 384 209.24Q384 264.24 350 309.24L451 410.24Q458 417.24 458 427.24Q458 438.24 451 445.24Q444 452.24 434 452.24Q423 452.24 416 445.24L315 343.24Q271 374.24 219 374.24Z" +
                "M128 300.24Q166 338.24 219 338.24Q272 338.24 310 300.24Q348 262.24 348 209.24Q348 156.24 310 118.24Q272 80.24 219 80.24Q166 80.24 128 118.24Q90 156.24 90 209.24Q90 262.24 128 300.24Z",
        )
    }

    private fun fontGlyph(name: String, pathData: String): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 512f,
            viewportHeight = 512f,
        ).addPath(pathData = addPathNodes(pathData), fill = stroke).build()
}
