package ch.threema.app.compose.common.buttons

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewWrapper
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import ch.threema.app.R
import ch.threema.app.compose.common.spacer.SpacerHorizontal
import ch.threema.app.compose.common.text.ThemedText
import ch.threema.app.compose.theme.ThreemaPreviewWrapper
import ch.threema.app.compose.theme.color.AlphaValues
import ch.threema.app.compose.theme.dimens.GridUnit
import ch.threema.app.utils.compose.stringResourceOrNull

@Composable
fun ButtonOutlined(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    text: String,
    maxLines: Int = Int.MAX_VALUE,
    shape: Shape = ButtonDefaults.outlinedShape,
    enabled: Boolean = true,
    leadingIcon: ButtonIconInfo? = null,
) {
    ButtonOutlinedBase(
        modifier = modifier
            .heightIn(min = GridUnit.x6),
        contentPadding = PaddingValues(
            horizontal = GridUnit.x3,
        ),
        onClick = onClick,
        text = text,
        maxLines = maxLines,
        shape = shape,
        enabled = enabled,
        leadingIcon = leadingIcon,
        iconSize = 24.sp,
    )
}

@Composable
fun ButtonOutlinedDense(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    text: String,
    maxLines: Int = Int.MAX_VALUE,
    shape: Shape = ButtonDefaults.outlinedShape,
    enabled: Boolean = true,
    leadingIcon: ButtonIconInfo? = null,
) {
    ButtonOutlinedBase(
        modifier = modifier,
        contentPadding = PaddingValues(
            vertical = GridUnit.x0,
            horizontal = GridUnit.x2,
        ),
        onClick = onClick,
        text = text,
        maxLines = maxLines,
        shape = shape,
        enabled = enabled,
        leadingIcon = leadingIcon,
        iconSize = 18.sp,
    )
}

@Composable
private fun ButtonOutlinedBase(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues,
    onClick: () -> Unit,
    text: String,
    maxLines: Int = Int.MAX_VALUE,
    shape: Shape = ButtonDefaults.outlinedShape,
    enabled: Boolean = true,
    iconSize: TextUnit,
    leadingIcon: ButtonIconInfo? = null,
) {
    OutlinedButton(
        modifier = modifier,
        contentPadding = contentPadding,
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.outlinedButtonColors().copy(
            contentColor = MaterialTheme.colorScheme.onSurface,
            disabledContentColor = MaterialTheme.colorScheme.onSurface
                .copy(alpha = AlphaValues.DISABLED),
        ),
        shape = shape,
    ) {
        if (leadingIcon != null) {
            Icon(
                modifier = Modifier.size(
                    with(LocalDensity.current) {
                        iconSize.toDp()
                    },
                ),
                painter = painterResource(leadingIcon.iconRes),
                contentDescription = stringResourceOrNull(leadingIcon.contentDescription),
                tint = LocalContentColor.current,
            )
            SpacerHorizontal(GridUnit.x1_5)
        }

        ThemedText(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = LocalContentColor.current,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

@Preview
@Composable
@PreviewWrapper(wrapper = ThreemaPreviewWrapper::class)
fun ButtonOutlined_Preview() {
    ButtonOutlined(
        modifier = Modifier.padding(GridUnit.x1),
        onClick = {},
        text = "Invite friends",
    )
}

@PreviewLightDark
@Composable
@PreviewWrapper(wrapper = ThreemaPreviewWrapper::class)
fun ButtonOutlined_Preview_Leading_Icon() {
    ButtonOutlined(
        modifier = Modifier.padding(GridUnit.x1),
        onClick = {},
        text = "Invite friends",
        leadingIcon = ButtonIconInfo(
            iconRes = R.drawable.ic_person_add_outline,
        ),
    )
}

@PreviewLightDark
@Composable
@PreviewWrapper(wrapper = ThreemaPreviewWrapper::class)
fun ButtonOutlined_Preview_Disabled() {
    ButtonOutlined(
        modifier = Modifier.padding(GridUnit.x1),
        onClick = {},
        text = "Invite friends",
        leadingIcon = ButtonIconInfo(
            iconRes = R.drawable.ic_person_add_outline,
        ),
        enabled = false,
    )
}

@PreviewLightDark
@Composable
@PreviewWrapper(wrapper = ThreemaPreviewWrapper::class)
fun ButtonOutlined_Preview_FullWidth() {
    ButtonOutlined(
        modifier = Modifier
            .padding(GridUnit.x1)
            .fillMaxWidth(),
        onClick = {},
        text = "Invite friends",
    )
}

@Preview
@Composable
@PreviewWrapper(wrapper = ThreemaPreviewWrapper::class)
fun ButtonOutlinedDense_Preview() {
    ButtonOutlinedDense(
        modifier = Modifier.padding(GridUnit.x1),
        onClick = {},
        text = "Invite friends",
    )
}

@Preview
@Composable
@PreviewWrapper(wrapper = ThreemaPreviewWrapper::class)
fun ButtonOutlinedDense_Preview_LeadingIcon() {
    ButtonOutlinedDense(
        modifier = Modifier.padding(GridUnit.x1),
        onClick = {},
        text = "Invite friends",
        leadingIcon = ButtonIconInfo(
            iconRes = R.drawable.ic_person_add_outline,
        ),
    )
}
