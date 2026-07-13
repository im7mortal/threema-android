package ch.threema.app.usecases.contacts

import ch.threema.app.eventbus.GlobalEventFlows
import ch.threema.app.eventbus.events.ProfileEvent
import ch.threema.common.DispatcherProvider
import ch.threema.data.datatypes.MentionNameData
import ch.threema.data.repositories.ContactModelRepository
import ch.threema.domain.stores.IdentityStore
import ch.threema.domain.types.toIdentityOrNull
import kotlinx.coroutines.channels.Channel.Factory.CONFLATED
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

class WatchAllMentionNamesUseCase(
    private val globalEventFlows: GlobalEventFlows,
    private val contactModelRepository: ContactModelRepository,
    private val identityStore: IdentityStore,
    private val dispatcherProvider: DispatcherProvider,
) {

    /**
     *  Creates a *cold* [Flow] that emits the latest collection of [MentionNameData] from every contact, also including the users own profile.
     *
     *  In the edge-case that no **own** identity exists, the flow will not include a [MentionNameData.Me] instance.
     *
     *  ##### Direct emit promise
     *  This flow fulfills the promise to directly emit the current values because [watchOwnMentionNameData] and [watchContactsMentionNameData] do so.
     *
     *  ##### Overflow strategy
     *  See [watchOwnMentionNameData] and [watchContactsMentionNameData]
     *
     *  ##### Error strategy
     *  See [watchOwnMentionNameData] and [watchContactsMentionNameData]
     */
    fun call(): Flow<List<MentionNameData>> =
        combine(
            watchContactsMentionNameData(),
            watchOwnMentionNameData(),
        ) { contactsMentionNameData: List<MentionNameData>, ownMentionNameData: MentionNameData.Me? ->
            if (ownMentionNameData != null) {
                contactsMentionNameData + ownMentionNameData
            } else {
                contactsMentionNameData
            }
        }

    /**
     *  Creates a *cold* [Flow] that emits the latest [MentionNameData.Me] from the users profile. If no own identity exists, this flow will emit
     *  `null`.
     *
     *  ##### Direct emit promise
     *  This flow fulfills the promise to directly emit the current value (or `null`).
     *
     *  ##### Overflow strategy
     *  If a consumer consumes the values slower than they get produced, the old unconsumed value gets **dropped** in favor of the most recent value.
     *
     *  ##### Error strategy
     *  Every exception will flow downstream.
     */
    private fun watchOwnMentionNameData(): Flow<MentionNameData.Me?> =
        globalEventFlows.profiles
            .filterIsInstance<ProfileEvent.NicknameUpdated>()
            .map { event ->
                getOwnMentionNameDataOrNull(
                    nickname = { event.newNickname },
                )
            }
            .buffer(capacity = CONFLATED)
            .onStart {
                emit(
                    getOwnMentionNameDataOrNull(
                        nickname = identityStore::getPublicNickname,
                    ),
                )
            }
            .flowOn(dispatcherProvider.io)

    private fun getOwnMentionNameDataOrNull(nickname: () -> String?): MentionNameData.Me? =
        identityStore.getIdentity()
            ?.let { ownIdentity ->
                MentionNameData.Me(
                    identity = ownIdentity,
                    nickname = nickname(),
                )
            }

    /**
     *  Creates a *cold* [Flow] that emits the most recent collection of [MentionNameData.Contact] from all contacts.
     *
     *  ##### Direct emit promise
     *  This flow fulfills the promise to directly emit the current values.
     *
     *  ##### Overflow strategy
     *  If a consumer consumes the values slower than they get produced, the old unconsumed value gets **dropped** in favor of the most recent value.
     *
     *  ##### Error strategy
     *  Every exception will flow downstream.
     */
    private fun watchContactsMentionNameData(): Flow<List<MentionNameData.Contact>> =
        globalEventFlows.contacts
            .map { }
            .buffer(capacity = CONFLATED)
            .onStart {
                emit(Unit)
            }
            .map {
                getCurrentContactsMentionNameData()
            }
            .flowOn(dispatcherProvider.io)

    private fun getCurrentContactsMentionNameData(): List<MentionNameData.Contact> =
        contactModelRepository
            .getAll()
            .mapNotNull { contactModel ->
                contactModel.data?.let { contactModelData ->
                    val identity = contactModelData.identity.toIdentityOrNull()
                        ?: return@mapNotNull null
                    MentionNameData.Contact(
                        identity = identity,
                        nickname = contactModelData.nickname,
                        firstname = contactModelData.firstName,
                        lastname = contactModelData.lastName,
                    )
                }
            }
}
