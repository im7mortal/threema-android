package ch.threema.app.backupcenter

import androidx.compose.runtime.Immutable
import ch.threema.app.backupcenter.models.BackupPassword

@Immutable
data class CreateLocalDataBackupViewState(
    val step: Step,
) {
    @Immutable
    sealed interface Step {
        val isSensitive: Boolean
            get() = false
    }

    @Immutable
    data class Step1RecoveryKey(
        val password: BackupPassword,
    ) : Step {
        override val isSensitive: Boolean = true
    }

    @Immutable
    data class Step1ConfirmKey(
        val input: String = "",
        private val expectedInput: String,
    ) : Step {
        val isMatch = input == expectedInput

        override val isSensitive: Boolean = true
    }

    @Immutable
    data class Step2(
        val includeMedia: Boolean,
    ) : Step

    @Immutable
    data class Step3InProgress(
        val messagesStatus: Status,
        val mediaFilesStatus: Status?,
        val encryptionStatus: Status,
        val finalizingStatus: Status,
    ) : Step {
        enum class Status {
            WAITING,
            IN_PROGRESS,
            DONE,
            FAILED,
        }
    }

    @Immutable
    data class Step3Done(
        val includeMedia: Boolean,
    ) : Step
}
