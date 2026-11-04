package ch.threema.app.backupcenter.usecases

import ch.threema.app.backupcenter.models.LocalDataBackupPassword
import ch.threema.app.preference.service.PreferenceService
import ch.threema.common.DispatcherProvider
import java.security.SecureRandom
import kotlinx.coroutines.withContext

class GetOrGenerateBackupPasswordUseCase(
    private val preferenceService: PreferenceService,
    private val secureRandom: SecureRandom,
    private val dispatcherProvider: DispatcherProvider,
) {
    data class PasswordWrapper(
        val password: LocalDataBackupPassword,
        val newlyGenerated: Boolean,
    )

    suspend fun call(forceNew: Boolean = false): PasswordWrapper = withContext(dispatcherProvider.io) {
        if (!forceNew) {
            preferenceService.getLocalDataBackupPassword()
                ?.let { existingPassword ->
                    return@withContext PasswordWrapper(
                        password = existingPassword,
                        newlyGenerated = false,
                    )
                }
        }

        // Whenever a new password is generated, we must forget the old one.
        // The new one must not yet be saved. It will be saved once a backup was successfully created with it.
        preferenceService.setLocalDataBackupPassword(null)
        return@withContext PasswordWrapper(
            password = LocalDataBackupPassword.generate(secureRandom),
            newlyGenerated = true,
        )
    }
}
