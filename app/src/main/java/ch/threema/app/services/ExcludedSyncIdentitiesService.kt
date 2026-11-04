package ch.threema.app.services

import ch.threema.domain.taskmanager.TriggerSource
import ch.threema.domain.types.IdentityString

/**
 * This service manages the identity exclusion list for the address book synchronization. Identities that are excluded won't be added when the address
 * book synchronization is run.
 */
interface ExcludedSyncIdentitiesService {

    /**
     * Adds the [identity] to the exclusion list. Depending on the [triggerSource], the change is reflected.
     */
    fun excludeFromSync(identity: IdentityString, triggerSource: TriggerSource)

    /**
     * Removes the [identity] from the exclusion list, so that it will be synced again. Depending on the [triggerSource], the change is reflected.
     */
    fun removeExcludedIdentity(identity: IdentityString, triggerSource: TriggerSource)

    /**
     * Replace all existing excluded identities with the [identities].
     */
    fun replaceExcludedIdentities(identities: Set<IdentityString>, triggerSource: TriggerSource)

    /**
     * Replaces the excluded identities with the given [identities]. Note that this change does *not* reflect anything.
     */
    fun replaceExcludedIdentitiesWithoutReflection(identities: Set<IdentityString>)

    /**
     * Get all excluded identities.
     */
    fun getAllExcludedIdentities(): Set<IdentityString>

    /**
     * Check whether the [identity] is excluded from sync or not.
     */
    fun isExcluded(identity: IdentityString): Boolean
}
