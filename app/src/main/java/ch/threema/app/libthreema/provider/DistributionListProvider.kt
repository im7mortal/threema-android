package ch.threema.app.libthreema.provider

import ch.threema.app.libthreema.conversion.DistributionListModelConverter
import ch.threema.app.services.DistributionListService
import ch.threema.base.utils.getThreemaLogger
import ch.threema.base.utils.withTimeMeasurement
import ch.threema.data.repositories.ContactModelRepository
import ch.threema.libthreema.DistributionList
import ch.threema.libthreema.DistributionListId
import ch.threema.libthreema.DistributionListProvider as LibthreemaDistributionListProvider
import ch.threema.libthreema.ProviderException

private val logger = getThreemaLogger("DistributionListProvider")

class DistributionListProvider(
    private val distributionListService: DistributionListService,
    private val distributionListModelConverter: DistributionListModelConverter,
    private val contactModelRepository: ContactModelRepository,
    override val guaranteedIdentityProvider: GuaranteedIdentityProvider,
) : LibthreemaDistributionListProvider, IdentityAwareProvider() {
    override fun getIds(): Set<DistributionListId> =
        withTimeMeasurement(logger, "getIds") {
            distributionListService
                .allNonAdHocIds
                .map { it.toULong() }
                .toSet()
        }

    override fun get(id: DistributionListId): DistributionList? =
        withTimeMeasurement(logger, "get") {
            distributionListService
                .getById(id.toLong())
                ?.let(distributionListModelConverter::toLibthreemaDistributionList)
        }

    override fun add(distributionLists: List<DistributionList>): Unit =
        withTimeMeasurement(logger, "add", distributionLists.size) {
            val distributionListIds = distributionLists
                .map { distributionList ->
                    distributionList.identity.toLong()
                }
                .toSet()
            // TODO(ANDR-5097): Note that libthreema is not aware of ad-hoc distribution lists. When distribution lists are added (except via backup
            //  restore), then this may unexpectedly fail.
            if (distributionListService.existsAnyById(distributionListIds)) {
                throw ProviderException.ContractViolation("There is already at least one distribution list that exists")
            }
            distributionLists.forEach { distributionList ->
                requireUserIdentityIsNotIn(distributionList.members)
            }
            val memberIdentitiesAcrossAllLists = distributionLists.flatMap { distributionList -> distributionList.members }.toSet()
            val amountOfExistingMemberContacts = contactModelRepository.countExisting(memberIdentitiesAcrossAllLists)
            if (amountOfExistingMemberContacts != memberIdentitiesAcrossAllLists.size.toUInt()) {
                throw ProviderException.ContractViolation(
                    "Member contact count mismatch — expected: ${memberIdentitiesAcrossAllLists.size}, found: $amountOfExistingMemberContacts",
                )
            }

            // TODO(ANDR-5066): Persist distribution list models and members in one single database transaction.
            distributionLists.forEach { distributionList ->
                val distributionListModel = distributionListModelConverter.toDistributionListModel(distributionList)

                distributionListService.persistDistributionListModel(
                    distributionListModel,
                    distributionList.members.toSet(),
                )
            }
        }
}
