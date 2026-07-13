package ch.threema.app.ui

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ch.threema.app.adapters.GroupDetailAdapter.GroupDescState
import ch.threema.app.preference.service.PreferenceService
import ch.threema.app.services.UserService
import ch.threema.app.utils.DisplayableContactOrUser
import ch.threema.app.utils.DisplayableGroupParticipant
import ch.threema.common.DispatcherProvider
import ch.threema.data.models.GroupModel
import ch.threema.data.models.GroupModelData
import ch.threema.data.repositories.ContactModelRepository
import ch.threema.domain.types.IdentityString
import java.io.File
import java.time.Instant
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class GroupDetailViewModel(
    private val savedState: SavedStateHandle,
    private val contactModelRepository: ContactModelRepository,
    private val preferenceService: PreferenceService,
    private val userService: UserService,
    private val dispatcherProvider: DispatcherProvider,
) : ViewModel() {

    private val _groupParticipants: MutableLiveData<List<DisplayableGroupParticipant>> =
        object : MutableLiveData<List<DisplayableGroupParticipant>>() {
            override fun getValue(): List<DisplayableGroupParticipant> = getDisplayableGroupParticipants()
        }
    val groupParticipants: LiveData<List<DisplayableGroupParticipant>?>
        get() = _groupParticipants

    private var hasParticipantChanges = false
    private var hasAvatarChanges = false

    private var updateGroupParticipantsJob: Job? = null

    var group: LiveData<GroupModelData?>? = null
        private set

    fun setGroup(group: GroupModel) {
        this.group = group.liveData()
    }

    var avatarFile: File?
        get() = savedState[KEY_AVATAR_FILE]
        set(avatarFile) {
            savedState.set<File?>(KEY_AVATAR_FILE, avatarFile)
        }

    var isAvatarRemoved: Boolean
        get() = savedState.get<Boolean?>(KEY_AVATAR_REMOVED) == true
        set(isRemoved) {
            savedState.set<Boolean?>(KEY_AVATAR_REMOVED, isRemoved)
            hasAvatarChanges = true
        }

    var groupName: String?
        get() = savedState.get<String?>(KEY_GROUP_NAME)?.trim { it <= ' ' } ?: ""
        set(groupName) {
            savedState.set<String?>(KEY_GROUP_NAME, groupName)
        }

    var groupDesc: String?
        get() = savedState[KEY_GROUP_DESC]
        set(groupDesc) {
            savedState.set<String?>(KEY_GROUP_DESC, groupDesc)
        }

    var groupDescTimestamp: Instant?
        get() = savedState[KEY_GROUP_DESC_TIMESTAMP]
        set(groupDescDate) {
            savedState.set<Instant?>(KEY_GROUP_DESC_TIMESTAMP, groupDescDate)
        }

    var groupDescState: GroupDescState?
        get() = savedState[KEY_GROUP_DESC_STATE]
        set(groupDescState) {
            savedState.set<GroupDescState?>(KEY_GROUP_DESC_STATE, groupDescState)
        }

    var groupIdentities: Array<IdentityString>
        get() = savedState[KEY_GROUP_IDENTITIES] ?: emptyArray()
        set(groupIdentities) {
            savedState.set<Array<IdentityString>?>(KEY_GROUP_IDENTITIES, groupIdentities)
            onDataChanged()
        }

    fun getDisplayableGroupParticipants(): List<DisplayableGroupParticipant> =
        getParticipantsFromIdentities(groupIdentities)

    private fun getParticipantsFromIdentities(identities: Array<IdentityString>): List<DisplayableGroupParticipant> {
        val groupModelData = group?.value ?: return emptyList()
        return identities
            .distinct()
            .map { identity ->
                val displayableContactOrUser = if (identity == userService.getIdentity()) {
                    DisplayableContactOrUser.User.createByIdentity(userService)
                } else {
                    DisplayableContactOrUser.Contact.createByIdentity(identity, contactModelRepository, preferenceService)
                }
                if (groupModelData.groupIdentity.creatorIdentity == identity) {
                    DisplayableGroupParticipant.Creator(displayableContactOrUser)
                } else {
                    DisplayableGroupParticipant.Member(displayableContactOrUser)
                }
            }
    }

    fun setGroupParticipants(groupContacts: List<DisplayableGroupParticipant>) {
        groupIdentities = getIdentitiesFromDisplayableGroupParticipants(groupContacts)
    }

    private fun getIdentitiesFromDisplayableGroupParticipants(
        displayableGroupParticipants: List<DisplayableGroupParticipant>,
    ): Array<IdentityString> =
        displayableGroupParticipants
            .map { displayableGroupParticipant ->
                displayableGroupParticipant.displayableContactOrUser.identity
            }
            .toTypedArray<IdentityString>()

    fun removeGroupParticipant(identity: IdentityString) {
        setGroupParticipants(
            getDisplayableGroupParticipants()
                .filter { displayableGroupMember ->
                    displayableGroupMember.displayableContactOrUser.identity != identity
                },
        )
        hasParticipantChanges = true
    }

    fun addGroupContacts(contactIdentities: Array<IdentityString>) {
        setGroupParticipants(
            getParticipantsFromIdentities(
                groupIdentities + contactIdentities,
            ),
        )
        hasParticipantChanges = true
    }

    fun containsParticipant(contactId: IdentityString): Boolean =
        groupIdentities.contains(contactId)

    fun hasAvatarChanges(): Boolean = hasAvatarChanges

    fun hasMemberChanges(): Boolean = hasParticipantChanges

    fun onDataChanged() {
        updateGroupParticipantsJob?.cancel()
        updateGroupParticipantsJob = viewModelScope.launch {
            _groupParticipants.value = withContext(dispatcherProvider.io) {
                getDisplayableGroupParticipants()
            }
        }
    }

    companion object {
        private const val KEY_AVATAR_FILE = "avatar"
        private const val KEY_GROUP_NAME = "name"
        private const val KEY_GROUP_IDENTITIES = "contacts"
        private const val KEY_AVATAR_REMOVED = "isRemoved"
        private const val KEY_GROUP_DESC = "description"
        private const val KEY_GROUP_DESC_TIMESTAMP = "descTimestamp"
        private const val KEY_GROUP_DESC_STATE = "descState"
    }
}
