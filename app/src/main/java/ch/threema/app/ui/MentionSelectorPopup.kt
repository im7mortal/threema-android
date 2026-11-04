package ch.threema.app.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.view.Gravity
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.ViewTreeObserver.OnGlobalLayoutListener
import android.view.WindowManager.BadTokenException
import androidx.core.view.ViewCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.window.layout.WindowMetricsCalculator
import ch.threema.android.textwatchers.SimpleTextWatcher
import ch.threema.app.R
import ch.threema.app.adapters.MentionSelectorAdapter
import ch.threema.app.adapters.MentionSelectorAdapter.MentionSelectorOption
import ch.threema.app.preference.service.PreferenceService
import ch.threema.app.services.ContactService
import ch.threema.app.services.GroupService
import ch.threema.app.services.UserService
import ch.threema.app.utils.AnimationUtil
import ch.threema.app.utils.ContactUtil
import ch.threema.data.IdentityProvider
import ch.threema.data.models.GroupModel
import ch.threema.data.models.GroupModelData
import ch.threema.data.repositories.ContactModelRepository
import ch.threema.domain.types.Mention
import com.google.android.material.card.MaterialCardView
import com.google.android.material.textfield.TextInputLayout

@SuppressLint("InflateParams")
class MentionSelectorPopup(
    private val context: Context,
    private val mentionSelectorListener: MentionSelectorListener?,
    private val groupService: GroupService,
    private val contactService: ContactService,
    private val contactModelRepository: ContactModelRepository,
    private val userService: UserService,
    private val identityProvider: IdentityProvider,
    private val preferenceService: PreferenceService,
    private val groupModel: GroupModel,
) : MovingPopupWindow(context), MentionSelectorAdapter.OnClickListener {
    private var mentionAdapter: MentionSelectorAdapter? = null
    private var filterText: String = ""
    private var filterStart: Int = 0
    private val recyclerView: RecyclerView
    private var editText: ComposeEditText? = null
    private val popupLayout: MaterialCardView
    private var viewableSpaceHeight = 0
    private var overlayMode = false // whether this popup is shown on top of another popupwindow
    private val textWatcher: TextWatcher = object : SimpleTextWatcher() {
        private fun run() {
            dismiss()
        }

        override fun onTextChanged(text: CharSequence, start: Int, before: Int, count: Int) {
            try {
                if (count == 0 && start == 0) { // @ at first position is deleted
                    editText?.post { this.run() }
                    return
                }

                // if spacebar or newline is added, escape the mention popup.
                val last = text[start - 1]
                if (count == 0 && (' ' == last || '\n' == last) || count == 1 && (' ' == text[start] || '\n' == text[start])) {
                    editText?.post { this.run() }
                }
            } catch (_: IndexOutOfBoundsException) {
                // don't care, happens when deleting a char after the @ the first time around
                // hacky because there is no other logic with the listener callback that would not mess with the rest of the logic.
            }
        }

        override fun afterTextChanged(editable: Editable) {
            if (TextUtils.isEmpty(editable)) { // if text field is completely empty
                editText?.post { this.run() }
            } else if (editable.toString() != filterText) {
                val filterTextAfterAtChar: String?
                var spacePosition = -1
                try {
                    filterTextAfterAtChar = editable.toString().substring(filterStart)
                    if (filterTextAfterAtChar.isNotEmpty()) {
                        spacePosition = filterTextAfterAtChar.indexOf(" ")
                        if (spacePosition == -1) {
                            spacePosition = filterTextAfterAtChar.indexOf("\n")
                        }
                    }
                } catch (_: IndexOutOfBoundsException) {
                    //
                }
                filterText = if (spacePosition != -1) {
                    editable.toString().substring(0, filterStart + spacePosition)
                } else {
                    editable.toString()
                }
                updateList(false)
                updateRecyclerViewDimensions()
            }
        }
    }

    init {
        popupLayout = LayoutInflater.from(context)
            .inflate(R.layout.popup_mention_selector, null, false) as MaterialCardView
        contentView = popupLayout
        inputMethodMode = INPUT_METHOD_NOT_NEEDED
        animationStyle = 0
        isFocusable = false
        isTouchable = true
        isOutsideTouchable = false
        setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        @Suppress("DEPRECATION")
        setWindowLayoutMode(0, ViewGroup.LayoutParams.WRAP_CONTENT)
        height = 1

        filterText = ""
        filterStart = 0

        recyclerView = popupLayout.findViewById(R.id.group_members_list)
        val linearLayoutManager = LinearLayoutManager(context)
        linearLayoutManager.stackFromEnd = true
        recyclerView.layoutManager = linearLayoutManager
        recyclerView.itemAnimator = null

        val adapter = updateList(true)
        if (adapter != null) {
            recyclerView.adapter = adapter
        }
    }

    fun show(activity: Activity, editText: ComposeEditText, anchorView: TextInputLayout?) {
        super.show(activity, anchorView)

        if (mentionAdapter == null) {
            dismiss()
            return
        }

        if (anchorView != null) {
            popupLayout.setCardBackgroundColor(anchorView.boxBackgroundColor)
            overlayMode =
                if (anchorView.boxCornerRadiusTopStart == anchorView.resources.getDimensionPixelSize(
                        R.dimen.compose_textinputlayout_radius_expanded,
                    )
                        .toFloat()
                ) {
                    true
                } else {
                    anchorView.setBoxCornerRadiiResources(
                        R.dimen.compose_textinputlayout_radius_expanded,
                        R.dimen.compose_textinputlayout_radius_expanded,
                        R.dimen.compose_textinputlayout_radius,
                        R.dimen.compose_textinputlayout_radius,
                    )
                    false
                }
        }

        val coordinates = getPopupWindowPositionAboveAnchor(activity, anchorView ?: editText)
        val popupX = if (anchorView == null) 0 else coordinates[0]
        var popupY = coordinates[1]

        if (anchorView == null) {
            popupY += context.resources.getDimensionPixelSize(R.dimen.compose_bottom_panel_padding_bottom)
        }

        this.editText = editText
        editText.setLocked(true)
        editText.addTextChangedListener(textWatcher)
        filterStart = editText.selectionStart
        viewableSpaceHeight =
            coordinates[2] - context.resources.getDimensionPixelSize(R.dimen.compose_bottom_panel_padding_bottom)
        this.width =
            if (anchorView == null) {
                WindowMetricsCalculator.getOrCreate()
                    .computeCurrentWindowMetrics(activity).bounds.width()
            } else {
                editText.width
            }
        this.height = viewableSpaceHeight

        try {
            showAtLocation(editText, Gravity.LEFT or Gravity.BOTTOM, popupX, popupY)
            contentView.viewTreeObserver.addOnGlobalLayoutListener(
                object : OnGlobalLayoutListener {
                    override fun onGlobalLayout() {
                        contentView.viewTreeObserver.removeOnGlobalLayoutListener(this)
                        AnimationUtil.slideInAnimation(contentView, true, 150)
                    }
                },
            )
            this.anchorView?.let {
                ViewCompat.setWindowInsetsAnimationCallback(
                    it,
                    windowInsetsAnimationCallback,
                )
                it.addOnLayoutChangeListener(onLayoutChangeListener)
            }
        } catch (_: BadTokenException) {
            //
        }
    }

    private fun updateRecyclerViewDimensions() {
        val maxHeight =
            context.resources.getDimensionPixelSize(R.dimen.group_detail_list_item_size) *
                (mentionAdapter?.itemCount ?: 1)
        recyclerView.layoutParams.height = maxHeight.coerceAtMost(viewableSpaceHeight)
        recyclerView.requestLayout()
    }

    private fun updateList(init: Boolean): MentionSelectorAdapter? {
        val groupModelData = groupModel.data ?: run {
            return null
        }

        var options = createOptions(groupModelData)

        if (!init && filterText.length - filterStart > 0) {
            val contactNameFormat = preferenceService.getContactNameFormat()
            val query = filterText.substring(filterStart)
            options = options.filter { option ->
                when (option) {
                    is MentionSelectorOption.Contact -> {
                        option.contact.data?.getDisplayName(contactNameFormat)?.contains(query, ignoreCase = true) == true ||
                            option.contact.data?.nickname?.contains(query, ignoreCase = true) == true ||
                            option.contact.identity.contains(query, ignoreCase = true)
                    }
                    MentionSelectorOption.Me -> {
                        userService.displayName.contains(query, ignoreCase = true) ||
                            userService.publicNickname.contains(query, ignoreCase = true)
                    }
                    MentionSelectorOption.AllUsers -> false
                }
            }
            if (options.isEmpty()) {
                options = listOf(MentionSelectorOption.AllUsers)
            }
        }

        if (mentionAdapter == null) {
            mentionAdapter = MentionSelectorAdapter(
                context,
                contactService,
                groupService,
                userService,
                preferenceService,
                groupModel,
            )
            mentionAdapter?.setOnClickListener(this)
        }

        mentionAdapter?.setData(options)

        return mentionAdapter
    }

    private fun createOptions(groupModelData: GroupModelData): List<MentionSelectorOption> {
        val options = mutableListOf<MentionSelectorOption>()

        groupModelData.getAllMembers(identityProvider.requireIdentityString())
            .minus(identityProvider.requireIdentityString())
            .let(contactModelRepository::getByIdentities)
            .forEach { contact ->
                options.add(MentionSelectorOption.Contact(contact))
            }

        options.add(MentionSelectorOption.Me)

        val sortOrder = preferenceService.getContactSortOrder()
        options.sortWith { option1: MentionSelectorOption, option2: MentionSelectorOption ->
            option1.getSortKey(sortOrder).compareTo(option2.getSortKey(sortOrder))
        }

        options.add(MentionSelectorOption.AllUsers)
        return options
    }

    private fun MentionSelectorOption.getSortKey(sortOrder: PreferenceService.ContactSortOrder): String =
        when (this) {
            is MentionSelectorOption.Contact -> ContactUtil.getSortKey(
                contact.identity,
                contact.data?.firstName,
                contact.data?.lastName,
                contact.data?.nickname,
                sortOrder,
            )
            MentionSelectorOption.Me -> ContactUtil.getSortKey(
                userService.identity,
                userService.publicNickname,
                null,
                userService.publicNickname,
                sortOrder,
            )
            MentionSelectorOption.AllUsers -> ""
        }

    override fun onItemClick(mentionSelectorOption: MentionSelectorOption) {
        if (mentionSelectorListener != null) {
            dismiss()
            mentionSelectorListener.onMentionOptionSelected(
                mentionSelectorOption.toMention(identityProvider.requireIdentity()),
                filterText.length - filterStart + 1,
                if (filterStart > 0) filterStart - 1 else 0,
            )
        }
    }

    override fun dismiss() {
        anchorView?.let {
            if (!overlayMode) {
                it.setBoxCornerRadiiResources(
                    R.dimen.compose_textinputlayout_radius,
                    R.dimen.compose_textinputlayout_radius,
                    R.dimen.compose_textinputlayout_radius,
                    R.dimen.compose_textinputlayout_radius,
                )
                it.removeOnLayoutChangeListener(onLayoutChangeListener)
                ViewCompat.setWindowInsetsAnimationCallback(it, null)
            }
        }

        editText?.let {
            it.removeTextChangedListener(textWatcher)
            it.setLocked(false)
        }
        super.dismiss()
    }

    interface MentionSelectorListener {
        fun onMentionOptionSelected(mention: Mention, length: Int, insertPosition: Int)
    }
}
