package ch.threema.app.compose.common.text

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.PreviewWrapper
import ch.threema.app.compose.preview.PreviewThreemaAll
import ch.threema.app.compose.theme.ThreemaPreviewWrapper

@Composable
fun SectionLabel(
    label: String,
    modifier: Modifier = Modifier,
) {
    ThemedText(
        modifier = modifier,
        text = label,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
    )
}

@PreviewThreemaAll
@Composable
@PreviewWrapper(wrapper = ThreemaPreviewWrapper::class)
private fun SectionLabel_Preview() {
    SectionLabel(label = "Hello world")
}
