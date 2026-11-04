package ch.threema.app.compose.common.extensions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import ch.threema.android.ResolvableString
import ch.threema.android.format
import ch.threema.common.ByteSize
import kotlin.time.Duration
import kotlinx.coroutines.delay

/**
 *  Concatenates the [getOther] modifier using [Modifier.then] if [apply] is `true`
 */
fun Modifier.thenIf(apply: Boolean, getOther: () -> Modifier): Modifier =
    if (apply) {
        then(getOther())
    } else {
        this
    }

@Composable
fun Dp.dpToPx() = with(LocalDensity.current) { this@dpToPx.toPx() }

@Composable
fun Float.pxToDp() = with(LocalDensity.current) { this@pxToDp.toDp() }

fun Dp.dpToPx(density: Density): Float {
    return density.run { this@dpToPx.toPx() }
}

fun Float.pxToDp(density: Density): Dp {
    return density.run { this@pxToDp.toDp() }
}

val Int.spNoScale: TextUnit
    @Composable @ReadOnlyComposable
    get() = with(LocalDensity.current) { this@spNoScale.dp.toSp() }

@ReadOnlyComposable
@Composable
fun ResolvableString.get() =
    get(LocalContext.current)

@ReadOnlyComposable
@Composable
fun ByteSize.format() =
    format(LocalContext.current)

/**
 *  Returns [value], but only after it stayed the same for [getDelay]. A change followed by another change
 *  within that duration is never returned at all. [getDelay] may return [Duration.ZERO] to apply a value without any delay.
 *
 *  Note that the very first [value] is returned without any delay. Only the changes after that are delayed.
 */
@Composable
fun <T> debounced(value: T, getDelay: (T) -> Duration): T {
    var debouncedValue: T by remember { mutableStateOf(value) }
    LaunchedEffect(value) {
        delay(getDelay(value))
        debouncedValue = value
    }
    return debouncedValue
}

@Stable
fun Arrangement.HorizontalOrVertical.withSpacing(spacing: Dp): Arrangement.HorizontalOrVertical =
    object : Arrangement.HorizontalOrVertical by this {
        override val spacing: Dp
            get() = spacing
    }
