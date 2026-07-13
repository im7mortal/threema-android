package ch.threema.app.compose.common.spacer

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun SpacerVertical(height: Dp) = Spacer(modifier = Modifier.height(height))

@Composable
fun SpacerHorizontal(width: Dp) = Spacer(modifier = Modifier.width(width))

@Composable
fun ColumnScope.SpacerRemainingVertical(minHeight: Dp = 0.dp) {
    if (minHeight != 0.dp) {
        SpacerVertical(minHeight)
    }
    Spacer(modifier = Modifier.weight(1f))
}

@Composable
fun RowScope.SpacerRemainingHorizontal(minWidth: Dp = 0.dp) {
    if (minWidth != 0.dp) {
        SpacerHorizontal(minWidth)
    }
    Spacer(modifier = Modifier.weight(1f))
}
