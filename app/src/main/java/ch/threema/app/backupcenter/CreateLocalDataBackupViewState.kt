package ch.threema.app.backupcenter

import androidx.compose.runtime.Immutable
import ch.threema.app.backupcenter.models.LocalDataBackupPassword

@Immutable
data class CreateLocalDataBackupViewState(
    val step: Step,
) {
    @Immutable
    sealed interface Step {
        val isSensitive: Boolean
            get() = false

        val isAbortable: Boolean
            get() = false
    }

    @Immutable
    data class Step1RecoveryKey(
        val password: LocalDataBackupPassword,
    ) : Step {
        override val isSensitive = true
    }

    @Immutable
    data class Step1ConfirmKey(
        val input: String = "",
        private val expectedInput: String,
    ) : Step {
        val isMatch = input == expectedInput

        override val isSensitive = true
    }

    @Immutable
    data class Step2(
        val isFirstBackup: Boolean,
        val includeMedia: Boolean,
        val backupLocationName: String,
        val passwordSnippet: String,
    ) : Step

    @Immutable
    data class Step3InProgress(
        val progress: Progress,
        val showAbortDialog: Boolean,
    ) : Step {
        override val isAbortable: Boolean
            get() = true

        data class Progress(
            val preparingStatus: Status,
            val chatsStatus: Status,
            val mediaFilesStatus: Status?,
            val finalizingStatus: Status,
        )

        enum class Status {
            WAITING,
            IN_PROGRESS,
            DONE,
        }
    }

    @Immutable
    data class Step3Done(
        val includeMedia: Boolean,
    ) : Step

    @Immutable
    data object Step3Error : Step
}
