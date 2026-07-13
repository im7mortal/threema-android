package ch.threema.app.services;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import ch.threema.data.datatypes.ConversationId;

/**
 * The ringtone service provides either default or custom ringtones for contacts and groups. Note
 * that the ringtone manager only manages notification sounds until api 25. From api 26 on, the
 * ringtone manager won't return any custom notification sounds.
 */
public interface RingtoneService {

    void init();

    void setRingtone(@NonNull ConversationId conversationId, @Nullable Uri ringtoneUri);

    /**
     * Get the ringtone uri from the given conversation-id. Note that this method returns null on api 26 and newer.
     */
    @Nullable
    Uri getRingtoneByConversationId(@NonNull ConversationId conversationId);

    /**
     * Get the ringtone uri from the given conversation-id. Note that this method returns null on api 26 and newer.
     */
    @Nullable
    Uri getContactRingtone(@NonNull ConversationId conversationId);

    /**
     * Get the ringtone uri from the given conversation-id. Note that this method returns null on api 26 and newer.
     */
    @Nullable
    Uri getGroupRingtone(@NonNull ConversationId conversationId);

    /**
     * Get the default ringtone for contacts. Note that this method returns null on api 26 and newer.
     */
    @Nullable
    Uri getDefaultContactRingtone();

    /**
     * Get the default ringtone for groups. Note that this method returns null on api 26 and newer.
     */
    @Nullable
    Uri getDefaultGroupRingtone();

    /**
     * Check whether the given conversation is silent or not. Note that starting from api 26, this method always returns false as the sound is managed
     * by the system notification channel settings.
     */
    boolean isSilent(@NonNull ConversationId conversationId, boolean isGroup);

    boolean hasCustomRingtone(@NonNull ConversationId conversationId);

    void removeCustomRingtone(@NonNull ConversationId conversationId);

    void resetRingtones(@NonNull Context context);
}
