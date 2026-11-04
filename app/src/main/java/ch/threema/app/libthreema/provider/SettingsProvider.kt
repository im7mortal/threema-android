package ch.threema.app.libthreema.provider

import ch.threema.app.libthreema.extensions.getGroupCallPolicy
import ch.threema.app.libthreema.extensions.getO2oCallConnectionPolicy
import ch.threema.app.libthreema.extensions.getO2oCallPolicy
import ch.threema.app.libthreema.extensions.getO2oCallVideoPolicy
import ch.threema.app.libthreema.extensions.getReadReceiptPolicy
import ch.threema.app.libthreema.extensions.getScreenshotPolicy
import ch.threema.app.libthreema.extensions.getTypingIndicatorPolicy
import ch.threema.app.libthreema.extensions.getUnknownContactPolicy
import ch.threema.app.libthreema.extensions.setGroupCallPolicy
import ch.threema.app.libthreema.extensions.setO2oCallConnectionPolicy
import ch.threema.app.libthreema.extensions.setO2oCallPolicy
import ch.threema.app.libthreema.extensions.setO2oCallVideoPolicy
import ch.threema.app.libthreema.extensions.setReadReceiptPolicy
import ch.threema.app.libthreema.extensions.setScreenshotPolicy
import ch.threema.app.libthreema.extensions.setTypingIndicatorPolicy
import ch.threema.app.libthreema.extensions.setUnknownContactPolicy
import ch.threema.app.preference.service.SynchronizedSettingsService
import ch.threema.app.services.BlockedIdentitiesService
import ch.threema.app.services.ExcludedSyncIdentitiesService
import ch.threema.base.utils.getThreemaLogger
import ch.threema.base.utils.withTimeMeasurement
import ch.threema.libthreema.Settings as LibthreemaSettings
import ch.threema.libthreema.SettingsProvider as LibthreemaSettingsProvider
import ch.threema.libthreema.SettingsUpdate as LibthreemaSettingsUpdate

private val logger = getThreemaLogger("SettingsProvider")

class SettingsProvider(
    private val synchronizedSettingsService: SynchronizedSettingsService,
    private val blockedIdentitiesService: BlockedIdentitiesService,
    private val excludedSyncIdentitiesService: ExcludedSyncIdentitiesService,
    override val guaranteedIdentityProvider: GuaranteedIdentityProvider,
) : LibthreemaSettingsProvider, IdentityAwareProvider() {

    override fun get(): LibthreemaSettings =
        withTimeMeasurement(logger, "get") {
            LibthreemaSettings(
                unknownContactPolicy = synchronizedSettingsService.getUnknownContactPolicy(),
                readReceiptPolicy = synchronizedSettingsService.getReadReceiptPolicy(),
                typingIndicatorPolicy = synchronizedSettingsService.getTypingIndicatorPolicy(),
                o2oCallPolicy = synchronizedSettingsService.getO2oCallPolicy(),
                o2oCallConnectionPolicy = synchronizedSettingsService.getO2oCallConnectionPolicy(),
                o2oCallVideoPolicy = synchronizedSettingsService.getO2oCallVideoPolicy(),
                groupCallPolicy = synchronizedSettingsService.getGroupCallPolicy(),
                screenshotPolicy = synchronizedSettingsService.getScreenshotPolicy(),
                blockedIdentities = blockedIdentitiesService.getAllBlockedIdentities().toList(),
                excludeFromSyncIdentities = excludedSyncIdentitiesService.getAllExcludedIdentities().toList(),
            )
        }

    override fun set(settings: LibthreemaSettings): Unit =
        withTimeMeasurement(logger, "set") {
            synchronizedSettingsService.setUnknownContactPolicy(settings.unknownContactPolicy)
            synchronizedSettingsService.setReadReceiptPolicy(settings.readReceiptPolicy)
            synchronizedSettingsService.setTypingIndicatorPolicy(settings.typingIndicatorPolicy)
            synchronizedSettingsService.setO2oCallPolicy(settings.o2oCallPolicy)
            synchronizedSettingsService.setO2oCallConnectionPolicy(settings.o2oCallConnectionPolicy)
            synchronizedSettingsService.setO2oCallVideoPolicy(settings.o2oCallVideoPolicy)
            synchronizedSettingsService.setGroupCallPolicy(settings.groupCallPolicy)
            synchronizedSettingsService.setScreenshotPolicy(settings.screenshotPolicy)
            blockedIdentitiesService.replaceBlockedIdentitiesWithoutReflection(blockedIdentities = settings.blockedIdentities.toSet())
            excludedSyncIdentitiesService.replaceExcludedIdentitiesWithoutReflection(identities = settings.excludeFromSyncIdentities.toSet())
        }

    override fun update(update: LibthreemaSettingsUpdate): Unit =
        withTimeMeasurement(logger, "update") {
            update.unknownContactPolicy?.let { unknownContactPolicy ->
                synchronizedSettingsService.setUnknownContactPolicy(unknownContactPolicy)
            }
            update.readReceiptPolicy?.let { readReceiptPolicy ->
                synchronizedSettingsService.setReadReceiptPolicy(readReceiptPolicy)
            }
            update.typingIndicatorPolicy?.let { typingIndicatorPolicy ->
                synchronizedSettingsService.setTypingIndicatorPolicy(typingIndicatorPolicy)
            }
            update.o2oCallPolicy?.let { o2oCallPolicy ->
                synchronizedSettingsService.setO2oCallPolicy(o2oCallPolicy)
            }
            update.o2oCallConnectionPolicy?.let { o2oCallConnectionPolicy ->
                synchronizedSettingsService.setO2oCallConnectionPolicy(o2oCallConnectionPolicy)
            }
            update.o2oCallVideoPolicy?.let { o2oCallVideoPolicy ->
                synchronizedSettingsService.setO2oCallVideoPolicy(o2oCallVideoPolicy)
            }
            update.groupCallPolicy?.let { groupCallPolicy ->
                synchronizedSettingsService.setGroupCallPolicy(groupCallPolicy)
            }
            update.screenshotPolicy?.let { screenshotPolicy ->
                synchronizedSettingsService.setScreenshotPolicy(screenshotPolicy)
            }
            update.blockedIdentities?.let { blockedIdentities ->
                blockedIdentitiesService.replaceBlockedIdentitiesWithoutReflection(blockedIdentities = blockedIdentities.toSet())
            }
            update.excludeFromSyncIdentities?.let { excludeFromSyncIdentities ->
                excludedSyncIdentitiesService.replaceExcludedIdentitiesWithoutReflection(identities = excludeFromSyncIdentities.toSet())
            }
        }
}
