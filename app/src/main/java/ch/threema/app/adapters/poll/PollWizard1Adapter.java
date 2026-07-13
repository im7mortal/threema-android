package ch.threema.app.adapters.poll;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.List;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import ch.threema.app.R;
import ch.threema.storage.models.poll.PollChoiceModel;

public class PollWizard1Adapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    public interface OnChoiceListener {
        void onEditClicked(int position);

        void onRemoveClicked(int position);
    }

    private static class PollAdminChoiceItemHolder extends RecyclerView.ViewHolder {

        public TextView name;
        public ImageView removeButton;
        public ImageView editButton;

        public PollAdminChoiceItemHolder(@NonNull View itemView) {
            super(itemView);
            name = itemView.findViewById(R.id.choice_name_readonly);
            removeButton = itemView.findViewById(R.id.remove_button);
            editButton = itemView.findViewById(R.id.edit_button);
        }

        public void bind(PollChoiceModel choiceModel, OnChoiceListener onChoiceListener) {
            if (choiceModel != null) {
                name.setText(choiceModel.getName());
                if (canEdit(choiceModel)) {
                    removeButton.setOnClickListener(view -> {
                        if (onChoiceListener != null) {
                            onChoiceListener.onRemoveClicked(getAdapterPosition());
                        }
                    });
                    removeButton.setVisibility(View.VISIBLE);
                    editButton.setOnClickListener(view -> {
                        if (onChoiceListener != null) {
                            onChoiceListener.onEditClicked(getAdapterPosition());
                        }
                    });
                    editButton.setVisibility(View.VISIBLE);
                } else {
                    removeButton.setVisibility(View.GONE);
                    editButton.setVisibility(View.GONE);
                }
            }
        }

        private boolean canEdit(PollChoiceModel choiceModel) {
            return choiceModel.getId() <= 0;
        }
    }

    private final List<PollChoiceModel> values;
    private OnChoiceListener onChoiceListener;

    public PollWizard1Adapter(List<PollChoiceModel> values) {
        this.values = values;
    }

    public PollWizard1Adapter setOnChoiceListener(OnChoiceListener onChoiceListener) {
        this.onChoiceListener = onChoiceListener;
        return this;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        View view = inflater.inflate(R.layout.item_poll_wizard1, parent, false);
        return new PollAdminChoiceItemHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        PollAdminChoiceItemHolder viewHolder = (PollAdminChoiceItemHolder) holder;
        viewHolder.bind(values.get(position), onChoiceListener);
    }

    @Override
    public int getItemCount() {
        return values.size();
    }
}
