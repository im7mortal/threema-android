package ch.threema.app.compose.common.time

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalLocale
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun rememberDateTimeFormatter(style: FormatStyle): DateTimeFormatter {
    val locale = LocalLocale.current
    return remember(locale, style) {
        DateTimeFormatter.ofLocalizedDateTime(style)
            .withLocale(locale.platformLocale)
            .withZone(ZoneId.systemDefault())
    }
}
