package ch.threema.app.adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import ch.threema.app.BuildConfig
import ch.threema.app.R
import ch.threema.app.glide.AvatarOptions
import ch.threema.app.glide.AvatarOptions.DefaultAvatarPolicy
import ch.threema.app.preference.service.PreferenceService
import ch.threema.app.services.ContactService
import ch.threema.app.services.GroupService
import ch.threema.app.services.UserService
import ch.threema.app.ui.AvatarView
import ch.threema.app.utils.AdapterUtil
import ch.threema.app.utils.NameUtil
import ch.threema.data.datatypes.AvailabilityStatus
import ch.threema.data.models.GroupModel
import ch.threema.domain.models.IdentityState
import ch.threema.domain.types.Identity
import ch.threema.domain.types.Mention

class MentionSelectorAdapter(
    private val context: Context,
    private val contactService: ContactService,
    private val groupService: GroupService,
    private val userService: UserService,
    private val preferenceService: PreferenceService,
    private val groupModel: GroupModel,
) : AbstractRecyclerAdapter<MentionSelectorAdapter.MentionSelectorOption, RecyclerView.ViewHolder>() {

    private var onClickListener: OnClickListener? = null

    class ItemHolder(val view: View) : RecyclerView.ViewHolder(view) {
        val nameView: TextView = itemView.findViewById(R.id.contact_name)
        val idView: TextView = itemView.findViewById(R.id.threemaid)
        val avatarView: AvatarView = itemView.findViewById(R.id.avatar_view)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder =
        ItemHolder(
            LayoutInflater.from(context)
                .inflate(R.layout.item_mention_selector, parent, false),
        )

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val itemHolder = holder as ItemHolder
        val mentionSelectorOption = getEntity(position)

        when (mentionSelectorOption) {
            MentionSelectorOption.AllUsers -> {
                itemHolder.nameView.text = context.getString(R.string.all)
                itemHolder.idView.text = ""
                val groupProfilePicture = groupService.getAvatar(
                    groupModel,
                    AvatarOptions.Builder()
                        .setHighRes(false)
                        .setReturnPolicy(DefaultAvatarPolicy.DEFAULT_FALLBACK)
                        .setDarkerBackground(false)
                        .toOptions(),
                )
                if (groupProfilePicture != null) {
                    itemHolder.avatarView.setImageBitmap(groupProfilePicture)
                } else {
                    itemHolder.avatarView.setImageResource(R.drawable.ic_group)
                }
                itemHolder.avatarView.setIdentityTypeBadgeVisible(false)
                AdapterUtil.styleContact(itemHolder.nameView, IdentityState.ACTIVE)
            }
            is MentionSelectorOption.Me -> {
                itemHolder.nameView.text = NameUtil.getQuoteName(
                    userService.identity,
                    contactService,
                    userService,
                    preferenceService.getContactNameFormat(),
                )
                itemHolder.idView.text = userService.identity

                val profilePicture = contactService.getAvatar(userService.identity, false)
                itemHolder.avatarView.setImageBitmap(profilePicture)

                if (BuildConfig.AVAILABILITY_STATUS_ENABLED) {
                    itemHolder.avatarView.setAvailabilityStatusBadgeState(null)
                }
                itemHolder.avatarView.setIdentityTypeBadgeVisible(false)
                AdapterUtil.styleContact(itemHolder.nameView, IdentityState.ACTIVE)
            }
            is MentionSelectorOption.Contact -> {
                val contact = mentionSelectorOption.contact
                val contactData = contact.data
                itemHolder.nameView.text = NameUtil.getQuoteName(
                    contact.identity,
                    contactService,
                    userService,
                    preferenceService.getContactNameFormat(),
                )

                val profilePicture = contactService.getAvatar(contact.identity, false)

                itemHolder.idView.text = contact.identity
                itemHolder.avatarView.setImageBitmap(profilePicture)

                if (contactData != null) {
                    itemHolder.avatarView.setIdentityTypeBadgeVisible(
                        contactService.showIdentityTypeBadge(contactData),
                    )

                    if (BuildConfig.AVAILABILITY_STATUS_ENABLED) {
                        itemHolder.avatarView.setAvailabilityStatusBadgeState(
                            contactData.availabilityStatus as? AvailabilityStatus.Set,
                        )
                    }
                }
                AdapterUtil.styleContact(itemHolder.nameView, contactData?.activityState)
            }
        }

        itemHolder.view.setOnClickListener {
            onClickListener?.onItemClick(mentionSelectorOption)
        }
    }

    fun setOnClickListener(listener: OnClickListener) {
        onClickListener = listener
    }

    interface OnClickListener {
        fun onItemClick(mentionSelectorOption: MentionSelectorOption)
    }

    sealed interface MentionSelectorOption {
        data object AllUsers : MentionSelectorOption
        data object Me : MentionSelectorOption
        data class Contact(val contact: ch.threema.data.models.ContactModel) : MentionSelectorOption

        fun toMention(myIdentity: Identity): Mention =
            when (this) {
                AllUsers -> Mention.AllUsers
                Me -> Mention.Identity(myIdentity)
                is Contact -> Mention.Identity(Identity(contact.identity))
            }
    }
}
