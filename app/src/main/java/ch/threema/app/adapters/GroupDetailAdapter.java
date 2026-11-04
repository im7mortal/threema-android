package ch.threema.app.adapters;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.chip.Chip;

import java.util.List;

import ch.threema.app.BuildConfig;
import ch.threema.app.R;
import ch.threema.app.services.ContactService;
import ch.threema.app.ui.AvatarView;
import ch.threema.app.ui.SectionHeaderView;
import ch.threema.app.utils.AdapterUtil;
import ch.threema.app.utils.DisplayableContactOrUser;
import ch.threema.app.utils.DisplayableGroupParticipant;
import ch.threema.data.models.GroupModel;

public class GroupDetailAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private static final int TYPE_HEADER = 0;
    private static final int TYPE_ITEM = 1;

    private final Context context;
    private final ContactService contactService;
    private final GroupModel groupModel;
    private List<DisplayableGroupParticipant> groupMembers;
    private OnGroupDetailsClickListener onClickListener;
    HeaderHolder headerHolder;

    public static class ItemHolder extends RecyclerView.ViewHolder {
        public final View view;
        public final TextView nameView, idView;
        public final AvatarView avatarView;
        public final Chip adminChip;

        public ItemHolder(View view) {
            super(view);
            this.view = view;
            this.nameView = itemView.findViewById(R.id.group_name);
            this.avatarView = itemView.findViewById(R.id.avatar_view);
            this.idView = itemView.findViewById(R.id.threemaid);
            this.adminChip = itemView.findViewById(R.id.admin_chip);
        }
    }

    public class HeaderHolder extends RecyclerView.ViewHolder {
        private final SectionHeaderView groupMembersTitleView;
        private final View addMembersView;
        public final View groupNoticeView;
        public final TextView groupNoticeTextView;

        public HeaderHolder(View view) {
            super(view);

            // items in object
            this.groupMembersTitleView = itemView.findViewById(R.id.group_members_title);
            this.addMembersView = itemView.findViewById(R.id.add_member);
            this.groupNoticeView = itemView.findViewById(R.id.group_notice_view);
            this.groupNoticeTextView = itemView.findViewById(R.id.group_notice);

            boolean isCreator = groupModel.isCreator();
            boolean isMember = groupModel.isMember();

            if (!isCreator && !isMember) {
                // Show empty group notice
                this.groupNoticeView.setVisibility(View.VISIBLE);
                this.groupNoticeTextView.setText(R.string.group_not_a_member_notice);
            } else if (isCreator && !isMember) {
                // Show notice that this group has been dissolved
                this.groupNoticeView.setVisibility(View.VISIBLE);
                this.groupNoticeTextView.setText(R.string.group_dissolved_notice);
            } else {
                // Don't show any notice
                this.groupNoticeView.setVisibility(View.GONE);
            }
        }
    }

    /**
     * Create the adapter to display the group details.
     *
     * @param context              the context
     * @param groupModel           the group model of the group
     */
    public GroupDetailAdapter(
        Context context,
        GroupModel groupModel,
        @NonNull ContactService contactService
    ) {
        this.context = context;
        this.groupModel = groupModel;
        this.contactService = contactService;
    }

    @SuppressLint("NotifyDataSetChanged")
    public void setGroupMembers(List<DisplayableGroupParticipant> groupMembers) {
        this.groupMembers = groupMembers;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == TYPE_ITEM) {
            View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_group_detail, parent, false);

            return new ItemHolder(v);
        } else if (viewType == TYPE_HEADER) {
            View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.header_group_detail, parent, false);
            return new HeaderHolder(v);
        }
        throw new RuntimeException("No matching item type (" + viewType + ")");
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, final int position) {
        if (holder instanceof ItemHolder) {
            ItemHolder itemHolder = (ItemHolder) holder;
            final DisplayableGroupParticipant displayableGroupParticipant = getItem(position);
            final DisplayableContactOrUser displayableContactOrUser = displayableGroupParticipant.getDisplayableContactOrUser();
            Bitmap avatar = this.contactService.getAvatar(displayableContactOrUser.getIdentity(), false);

            itemHolder.nameView.setText(displayableContactOrUser.getDisplayName());
            itemHolder.idView.setText(displayableContactOrUser.getIdentity());
            AdapterUtil.styleContact(itemHolder.nameView, displayableContactOrUser.getIdentityState());
            itemHolder.avatarView.setImageBitmap(avatar);
            itemHolder.avatarView.setIdentityTypeBadgeVisible(displayableGroupParticipant.getDisplayableContactOrUser().getShowBadge());
            itemHolder.view.setOnClickListener(v -> onClickListener.onGroupMemberClick(v, displayableContactOrUser.getIdentity()));

            boolean isCreator = displayableGroupParticipant instanceof DisplayableGroupParticipant.Creator;
            itemHolder.adminChip.setVisibility(isCreator ? View.VISIBLE : View.GONE);
            itemHolder.idView.setVisibility(isCreator ? View.GONE : View.VISIBLE);
        } else {
            this.headerHolder = (HeaderHolder) holder;
            headerHolder.addMembersView.setOnClickListener(v -> onClickListener.onAddMembersClick(v));

            boolean addMembersViewVisibility = groupModel.isCreator() && groupModel.isMember()
                && groupMembers != null && groupMembers.size() < BuildConfig.MAX_GROUP_SIZE;

            if (groupMembers != null && !groupMembers.isEmpty()) {
                headerHolder.groupMembersTitleView.setVisibility(View.VISIBLE);
                headerHolder.groupMembersTitleView.setText(
                    context.getResources().getQuantityString(R.plurals.number_of_group_members, groupMembers.size(), groupMembers.size())
                );
            } else {
                headerHolder.groupMembersTitleView.setVisibility(View.GONE);
            }

            headerHolder.addMembersView.setVisibility(addMembersViewVisibility ? View.VISIBLE : View.GONE);
        }
    }

    @Override
    public int getItemCount() {
        if (groupMembers != null)
            return groupMembers.size() + 1;
        else return 1;
    }

    @Override
    public int getItemViewType(int position) {
        if (isPositionHeader(position))
            return TYPE_HEADER;

        return TYPE_ITEM;
    }

    private boolean isPositionHeader(int position) {
        return position == 0;
    }

    public int getPosition(DisplayableGroupParticipant displayableGroupMember) {
        return groupMembers.indexOf(displayableGroupMember) + 1;
    }

    public DisplayableGroupParticipant getItem(int position) {
        return groupMembers.get(position - 1);
    }

    public void setOnClickListener(OnGroupDetailsClickListener listener) {
        onClickListener = listener;
    }

    public interface OnGroupDetailsClickListener {
        void onGroupMemberClick(View v, @NonNull String identity);

        void onAddMembersClick(View v);
    }
}
