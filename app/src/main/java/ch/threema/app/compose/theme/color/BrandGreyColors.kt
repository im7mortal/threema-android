package ch.threema.app.compose.theme.color

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import ch.threema.app.R

/**
 *  Grey shades across for *every* flavor
 */
@Suppress("unused")
object BrandGreyColors {
    const val SHADE_50 = 0xFFF4F4F3
    const val SHADE_100 = 0xFFE8E8E7
    const val SHADE_200 = 0xFFD0D0D0
    const val SHADE_300 = 0xFFB9B9B8
    const val SHADE_400 = 0xFF8A8A89
    const val SHADE_500 = 0xFF737371
    const val SHADE_600 = 0xFF5B5B5A
    const val SHADE_700 = 0xFF444442
    const val SHADE_800 = 0xFF2C2C2B
    const val SHADE_900 = 0xFF151513

    val dynamicGrey300: Color
        @Composable
        @ReadOnlyComposable
        get() = colorResource(R.color.dynamic_grey_300)

    val dynamicGrey400: Color
        @Composable
        @ReadOnlyComposable
        get() = colorResource(R.color.dynamic_grey_400)

    val dynamicGrey700: Color
        @Composable
        @ReadOnlyComposable
        get() = colorResource(R.color.dynamic_grey_700)
}
