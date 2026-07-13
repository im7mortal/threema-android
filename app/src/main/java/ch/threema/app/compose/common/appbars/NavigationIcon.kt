package ch.threema.app.compose.common.appbars

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import ch.threema.app.R
import ch.threema.app.compose.common.IconInfo

@Immutable
data class NavigationIcon(
    val icon: IconInfo,
    val containerColor: Color = Color.Unspecified,
    val onClick: () -> Unit,
) {

    companion object {
        fun back(
            containerColor: Color = Color.Unspecified,
            onClick: () -> Unit,
        ) = NavigationIcon(
            icon = IconInfo(
                iconRes = R.drawable.ic_arrow_left,
                contentDescription = R.string.back,
            ),
            containerColor = containerColor,
            onClick = onClick,
        )
    }
}
