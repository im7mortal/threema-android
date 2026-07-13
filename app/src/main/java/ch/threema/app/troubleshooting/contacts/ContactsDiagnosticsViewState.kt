package ch.threema.app.troubleshooting.contacts

import androidx.compose.runtime.Immutable
import ch.threema.domain.types.Identity

@Immutable
data class ContactsDiagnosticsViewState(
    val contactsWithProblems: List<ContactUiModel>,
    val fixInProgress: Boolean = false,
) {
    @Immutable
    data class ContactUiModel(
        val identity: Identity,
        val name: String?,
        val problem: String,
    ) {
        val displayName: String
            get() = "$identity (${name ?: "???"})"
    }
}
