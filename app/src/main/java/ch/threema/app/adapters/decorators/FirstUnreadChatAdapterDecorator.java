package ch.threema.app.adapters.decorators;

import android.content.Context;

import androidx.annotation.NonNull;
import ch.threema.app.R;
import ch.threema.app.ui.listitemholder.ComposeMessageHolder;
import ch.threema.app.utils.LinkifyUtil;
import ch.threema.storage.models.AbstractMessageModel;

import static ch.threema.common.JavaCompat.isNullOrEmpty;

public class FirstUnreadChatAdapterDecorator extends ChatAdapterDecorator {
    private final int unreadMessagesCount;

    public FirstUnreadChatAdapterDecorator(
        AbstractMessageModel messageModel,
        @NonNull ChatAdapterDecoratorListener chatAdapterDecoratorListener,
        @NonNull LinkifyUtil.LinkifyListener linkifyListener,
        Helper helper,
        final int unreadMessagesCount
    ) {
        super(messageModel, chatAdapterDecoratorListener, linkifyListener, helper);

        this.unreadMessagesCount = unreadMessagesCount;
    }

    @Override
    protected void configureChatMessage(final ComposeMessageHolder holder, Context context, final int position) {
        String s = context.getResources().getQuantityString(R.plurals.unread_messages, unreadMessagesCount, unreadMessagesCount);

        if (this.showHide(holder.bodyTextView, !isNullOrEmpty(s))) {
            holder.bodyTextView.setText(s);
        }
    }
}
