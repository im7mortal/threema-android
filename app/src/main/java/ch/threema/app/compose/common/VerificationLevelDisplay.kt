package ch.threema.app.compose.common

import androidx.compose.foundation.Image
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.sp
import ch.threema.app.compose.theme.ThreemaThemePreview
import ch.threema.app.utils.ConfigUtils
import ch.threema.app.utils.ContactUtil
import ch.threema.domain.models.VerificationLevel
import ch.threema.domain.models.WorkVerificationLevel

@Composable
fun VerificationLevelDisplay(
    modifier: Modifier = Modifier,
    verificationLevel: VerificationLevel,
    workVerificationLevel: WorkVerificationLevel,
) {
    Image(
        modifier = modifier,
        painter = painterResource(
            id = ContactUtil.getVerificationLevelIconResource(verificationLevel, workVerificationLevel),
        ),
        contentDescription = stringResource(
            id = ContactUtil.getVerificationLevelDescription(verificationLevel, workVerificationLevel),
        ),
    )
}

@Composable
@PreviewLightDark
private fun Preview_VerificationLevelDisplay_Unverified() {
    ThreemaThemePreview {
        Surface {
            VerificationLevelDisplay(
                verificationLevel = VerificationLevel.UNVERIFIED,
                workVerificationLevel = WorkVerificationLevel.NONE,
            )
        }
    }
}

@Composable
@PreviewLightDark
private fun Preview_VerificationLevelDisplay_ServerVerified() {
    ThreemaThemePreview {
        Surface {
            VerificationLevelDisplay(
                verificationLevel = VerificationLevel.SERVER_VERIFIED,
                workVerificationLevel = WorkVerificationLevel.NONE,
            )
        }
    }
}

@Composable
@PreviewLightDark
private fun Preview_VerificationLevelDisplay_FullyVerified() {
    ThreemaThemePreview {
        Surface {
            VerificationLevelDisplay(
                verificationLevel = VerificationLevel.FULLY_VERIFIED,
                workVerificationLevel = WorkVerificationLevel.NONE,
            )
        }
    }
}

@Composable
@PreviewLightDark
private fun Preview_VerificationLevelDisplay_Work() {
    if (ConfigUtils.isWorkBuild()) {
        ThreemaThemePreview {
            Surface {
                VerificationLevelDisplay(
                    verificationLevel = VerificationLevel.FULLY_VERIFIED,
                    workVerificationLevel = WorkVerificationLevel.WORK_SUBSCRIPTION_VERIFIED,
                )
            }
        }
    } else {
        Text(text = "Switch active build variant", fontSize = 6.sp)
    }
}

@Composable
@PreviewLightDark
private fun Preview_VerificationLevelDisplay_WorkServer() {
    if (ConfigUtils.isWorkBuild()) {
        ThreemaThemePreview {
            Surface {
                VerificationLevelDisplay(
                    verificationLevel = VerificationLevel.SERVER_VERIFIED,
                    workVerificationLevel = WorkVerificationLevel.WORK_SUBSCRIPTION_VERIFIED,
                )
            }
        }
    } else {
        Text(text = "Switch active build variant", fontSize = 6.sp)
    }
}
