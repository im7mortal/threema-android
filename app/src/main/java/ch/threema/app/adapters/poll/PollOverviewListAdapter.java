package ch.threema.app.adapters.poll;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import com.bumptech.glide.RequestManager;
import com.google.android.material.button.MaterialButton;

import java.util.List;
import java.util.Locale;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import ch.threema.app.R;
import ch.threema.app.messagereceiver.MessageReceiver;
import ch.threema.app.preference.service.PreferenceService;
import ch.threema.app.services.ContactService;
import ch.threema.app.services.UserService;
import ch.threema.app.services.poll.PollService;
import ch.threema.app.ui.AvatarListItemUtil;
import ch.threema.app.ui.listitemholder.AvatarListItemHolder;
import ch.threema.app.utils.PollUtil;
import ch.threema.app.utils.LocaleUtil;
import ch.threema.app.utils.NameUtil;
import ch.threema.app.utils.ViewUtil;
import ch.threema.storage.models.poll.PollModel;

public class PollOverviewListAdapter extends ArrayAdapter<PollModel> {

    @NonNull
    private final Context context;
    @NonNull
    private final List<PollModel> values;
    @Nullable
    private final MessageReceiver<?> messageReceiver;
    @NonNull
    private final PollService pollService;
    @NonNull
    private final ContactService contactService;
    @NonNull
    private final UserService userService;
    @NonNull
    private final PreferenceService preferenceService;
    @NonNull
    private final RequestManager requestManager;

    public PollOverviewListAdapter(
        @NonNull Context context,
        @NonNull List<PollModel> values,
        @Nullable MessageReceiver<?> messageReceiver,
        @NonNull PollService pollService,
        @NonNull ContactService contactService,
        @NonNull UserService userService,
        @NonNull PreferenceService preferenceService,
        @NonNull RequestManager requestManager
    ) {
        super(context, R.layout.item_poll_overview, values);

        this.context = context;
        this.values = values;
        this.messageReceiver = messageReceiver;
        this.pollService = pollService;
        this.contactService = contactService;
        this.userService = userService;
        this.preferenceService = preferenceService;
        this.requestManager = requestManager;
    }

    private static class PollOverviewItemHolder extends AvatarListItemHolder {
        public TextView name;
        public TextView state;
        public TextView creator;
        public TextView creationDate;
        public MaterialButton countBoxView;
    }

    @NonNull
    @Override
    public View getView(int position, View convertView, @NonNull ViewGroup parent) {
        View itemView = convertView;
        PollOverviewItemHolder holder;

        if (convertView == null) {
            holder = new PollOverviewItemHolder();
            LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            itemView = inflater.inflate(R.layout.item_poll_overview, parent, false);

            holder.name = itemView.findViewById(R.id.poll_name);
            holder.state = itemView.findViewById(R.id.poll_state);
            holder.creationDate = itemView.findViewById(R.id.poll_creation_date);
            holder.creator = itemView.findViewById(R.id.poll_creator);
            holder.countBoxView = itemView.findViewById(R.id.poll_updates);
            holder.avatarView = itemView.findViewById(R.id.avatar_view);

            itemView.setTag(holder);
        } else {
            holder = (PollOverviewItemHolder) itemView.getTag();
        }
        final PollModel pollModel = values.get(position);

        if (pollModel != null) {
            AvatarListItemUtil.loadAvatar(
                pollModel.getCreatorIdentity(),
                contactService,
                holder,
                requestManager
            );

            if (holder.name != null) {
                holder.name.setText(pollModel.getName());
            }

            if (pollModel.getState() == PollModel.State.CLOSED) {
                holder.state.setText(R.string.ballot_state_closed);
                holder.state.setVisibility(View.VISIBLE);
            } else if (pollModel.getState() == PollModel.State.OPEN) {
                var myIdentity = userService.getIdentity();
                if (PollUtil.canClose(pollModel, myIdentity, messageReceiver) || PollUtil.canViewMatrix(pollModel)) {
                    holder.state.setText(String.format(Locale.US, "%d / %d",
                        pollService.getVotedParticipants(pollModel.getId()).size(),
                        pollService.getParticipants(pollModel.getId()).length));
                } else if (messageReceiver == null) {
                    holder.state.setText("");
                } else {
                    holder.state.setText(R.string.ballot_secret);
                }
                holder.state.setVisibility(View.VISIBLE);
            } else {
                holder.state.setText("");
                holder.state.setVisibility(View.GONE);
            }

            ViewUtil.show(holder.countBoxView, false);

            if (holder.creationDate != null) {
                holder.creationDate.setText(LocaleUtil.formatTimeStampString(this.getContext(), pollModel.getCreatedAt(), true));
            }

            if (holder.creator != null) {
                holder.creator.setText(
                    NameUtil.getContactDisplayName(
                        this.contactService.getByIdentity(pollModel.getCreatorIdentity()),
                        preferenceService.getContactNameFormat()
                    )
                );
            }
        }

        return itemView;
    }
}
