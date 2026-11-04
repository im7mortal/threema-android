package ch.threema.app.backuprestore.libthreema

/**
 * Note: The order of entries is relevant for determining the overall progress.
 * The progress UI assumes that the steps will appear in this order.
 * It should match the order defined by libthreema.
 *
 * @See ch.threema.libthreema.DataBackupCreateStateUpdate
 * @see ch.threema.app.backupcenter.usecases.GetBackupProgressUseCase
 */
enum class BackupStep {
    UserData,
    Contacts,
    DistributionLists,
    Groups,
    CspNonces,
    Polls,
    Messages,
    Finalizing,
}
