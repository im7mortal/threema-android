package ch.threema.app.libthreema.provider

import ch.threema.app.libthreema.conversion.DistributionListModelConverter
import ch.threema.app.services.DistributionListService
import ch.threema.data.repositories.ContactModelRepository
import ch.threema.domain.types.IdentityString
import ch.threema.libthreema.DistributionList as LibthreemaDistributionList
import ch.threema.libthreema.ProviderException as LibthreemaProviderException
import ch.threema.storage.models.DistributionListModel
import ch.threema.test.TestIdentityProvider
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import testdata.TestData

class DistributionListProviderTest {
    private val guaranteedIdentityProvider = GuaranteedIdentityProvider(
        TestIdentityProvider(
            identity = TestData.Identities.ME,
        ),
    )

    @Test
    fun `get returns null for not existent distribution list id`() {
        val distributionListServiceMock: DistributionListService = mockk {
            every { getById(any()) } returns null
        }

        val distributionListProvider = DistributionListProvider(
            distributionListService = distributionListServiceMock,
            distributionListModelConverter = mockk(),
            contactModelRepository = mockk(),
            guaranteedIdentityProvider = guaranteedIdentityProvider,
        )

        assertNull(distributionListProvider.get(42u))
    }

    @Test
    fun `add fails when distribution list with id already exists`() {
        val distributionListServiceMock: DistributionListService = mockk {
            every { existsAnyById(setOf(42L)) } returns true
        }
        val distributionListProvider = DistributionListProvider(
            distributionListService = distributionListServiceMock,
            distributionListModelConverter = mockk(),
            contactModelRepository = mockk(),
            guaranteedIdentityProvider = guaranteedIdentityProvider,
        )
        val distributionListMock: LibthreemaDistributionList = mockk {
            every { identity } returns 42u
        }

        assertFailsWith<LibthreemaProviderException.ContractViolation> {
            distributionListProvider.add(listOf(distributionListMock))
        }
    }

    @Test
    fun `add fails when user identity is part of distribution list`() {
        val distributionListServiceMock: DistributionListService = mockk {
            every { existsAnyById(setOf(42L)) } returns false
        }
        val distributionListProvider = DistributionListProvider(
            distributionListService = distributionListServiceMock,
            distributionListModelConverter = mockk(),
            contactModelRepository = mockk(),
            guaranteedIdentityProvider = guaranteedIdentityProvider,
        )
        val distributionListMock: LibthreemaDistributionList = mockk {
            every { identity } returns 42u
            every { members } returns setOf(TestData.Identities.ME.value)
        }

        assertFailsWith<LibthreemaProviderException.ContractViolation> {
            distributionListProvider.add(listOf(distributionListMock))
        }
    }

    @Test
    fun `add fails when member is not a contact`() {
        val distributionListServiceMock: DistributionListService = mockk {
            every { existsAnyById(setOf(42L)) } returns false
        }
        val contactModelRepositoryMock: ContactModelRepository = mockk {
            every { countExisting(any()) } answers {
                (firstArg<Set<IdentityString>>().size - 1).toUInt()
            }
        }
        val distributionListProvider = DistributionListProvider(
            distributionListService = distributionListServiceMock,
            distributionListModelConverter = mockk(),
            contactModelRepository = contactModelRepositoryMock,
            guaranteedIdentityProvider = guaranteedIdentityProvider,
        )
        val distributionListMock: LibthreemaDistributionList = mockk {
            every { identity } returns 42u
            every { members } returns setOf(TestData.Identities.OTHER_1.value)
        }

        assertFailsWith<LibthreemaProviderException.ContractViolation> {
            distributionListProvider.add(listOf(distributionListMock))
        }
    }

    @Test
    fun `add works when distribution list is valid`() {
        val distributionListModelMock: DistributionListModel = mockk()
        val memberIdentities = setOf(TestData.Identities.OTHER_1.value)
        val distributionListServiceMock: DistributionListService = mockk {
            every { existsAnyById(setOf(42L)) } returns false
            every {
                persistDistributionListModel(
                    distributionListModelMock,
                    memberIdentities,
                )
            } just Runs
        }
        val contactModelRepositoryMock: ContactModelRepository = mockk {
            every { countExisting(any()) } answers {
                firstArg<Set<IdentityString>>().size.toUInt()
            }
        }
        val distributionListMock: LibthreemaDistributionList = mockk {
            every { identity } returns 42u
            every { members } returns memberIdentities
        }
        val distributionListModelConverterMock: DistributionListModelConverter = mockk {
            every { toDistributionListModel(distributionListMock) } returns distributionListModelMock
        }
        val distributionListProvider = DistributionListProvider(
            distributionListService = distributionListServiceMock,
            distributionListModelConverter = distributionListModelConverterMock,
            contactModelRepository = contactModelRepositoryMock,
            guaranteedIdentityProvider = guaranteedIdentityProvider,
        )

        distributionListProvider.add(listOf(distributionListMock))

        verify(exactly = 1) {
            distributionListServiceMock.persistDistributionListModel(
                distributionListModelMock,
                memberIdentities,
            )
        }
    }
}
