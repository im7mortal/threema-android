package ch.threema.app.services;

import android.content.Context;
import android.media.RingtoneManager;
import android.net.Uri;

import org.slf4j.Logger;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import java.util.HashMap;
import java.util.Map;

import ch.threema.app.R;
import ch.threema.app.utils.ConfigUtils;
import ch.threema.app.utils.RingtoneUtil;
import ch.threema.data.datatypes.ConversationId;
import ch.threema.data.datatypes.ConversationIdObfuscated;

import static ch.threema.base.utils.LoggingKt.getThreemaLogger;

public class RingtoneServiceImpl implements RingtoneService {
    private final static Logger logger = getThreemaLogger("RingtoneServiceImpl");

    private final @NonNull NotificationPreferenceService notificationPreferenceService;
    private @NonNull Map<ConversationIdObfuscated, String> ringtoneURIs = new HashMap<>();
    private final boolean supportsNotificationChannels = ConfigUtils.supportsNotificationChannels();

    public RingtoneServiceImpl(@NonNull NotificationPreferenceService notificationPreferenceService) {
        this.notificationPreferenceService = notificationPreferenceService;
        init();
    }

    @Override
    public void init() {
        if (supportsNotificationChannels) {
            // Set empty hash map as notification channels are supported and therefore ringtones
            // won't be managed by us.
            final HashMap<ConversationIdObfuscated, String> emptyRingtones = new HashMap<>();
            notificationPreferenceService.setLegacyRingtones(emptyRingtones);
            ringtoneURIs = emptyRingtones;
        } else {
            ringtoneURIs = notificationPreferenceService.getLegacyRingtones();
        }
    }

    @Override
    public void setRingtone(@NonNull ConversationId conversationId, @Nullable Uri ringtoneUri) {
        if (supportsNotificationChannels) {
            logger.error("Cannot set ringtone if notification channels are supported");
            return;
        }

        String ringtone = null;
        if (ringtoneUri != null) {
            ringtone = ringtoneUri.toString();
        }

        if (ringtoneUri != null && RingtoneManager.isDefault(ringtoneUri)) {
            ringtoneURIs.remove(conversationId.getObfuscated());
        } else {
            ringtoneURIs.put(conversationId.getObfuscated(), ringtone);
        }
        notificationPreferenceService.setLegacyRingtones(ringtoneURIs);
    }

    @Override
    @Nullable
    public Uri getRingtoneByConversationId(@NonNull ConversationId conversationId) {
        final @Nullable String ringtone = ringtoneURIs.get(conversationId.getObfuscated());
        // check for "null" string (HTC bug)
        if (ringtone != null && !ringtone.equals(ServicesConstants.PREFERENCES_NULL)) {
            return Uri.parse(ringtone);
        } else {
            // silent
            return null;
        }
    }

    @Override
    public boolean hasCustomRingtone(@NonNull ConversationId conversationId) {
        return ringtoneURIs.containsKey(conversationId.getObfuscated());
    }

    @Override
    public void removeCustomRingtone(@NonNull ConversationId conversationId) {
        if (supportsNotificationChannels) {
            logger.warn("No need to remove custom ringtone if notification channels are supported");
        }
        if (ringtoneURIs.containsKey(conversationId.getObfuscated())) {
            ringtoneURIs.remove(conversationId.getObfuscated());
            notificationPreferenceService.setLegacyRingtones(ringtoneURIs);
        }
    }

    @Override
    public void resetRingtones(@NonNull Context context) {
        ringtoneURIs.clear();
        notificationPreferenceService.setLegacyRingtones(ringtoneURIs);
        notificationPreferenceService.setLegacyGroupNotificationSound(Uri.parse(context.getString(R.string.default_notification_sound)));
        notificationPreferenceService.setLegacyNotificationSound(Uri.parse(context.getString(R.string.default_notification_sound)));
        notificationPreferenceService.setLegacyVoipCallRingtone(RingtoneUtil.THREEMA_CALL_RINGTONE_URI);
        notificationPreferenceService.setLegacyNotificationPriority(NotificationCompat.PRIORITY_HIGH);
    }

    @Override
    @Nullable
    public Uri getContactRingtone(@NonNull ConversationId conversationId) {
        return ringtoneURIs.containsKey(conversationId.getObfuscated())
            ? getRingtoneByConversationId(conversationId)
            : notificationPreferenceService.getLegacyNotificationSound();
    }

    @Override
    @Nullable
    public Uri getGroupRingtone(@NonNull ConversationId conversationId) {
        return ringtoneURIs.containsKey(conversationId.getObfuscated())
            ? getRingtoneByConversationId(conversationId)
            : notificationPreferenceService.getLegacyGroupNotificationSound();
    }

    @Override
    @Nullable
    public Uri getDefaultContactRingtone() {
        return supportsNotificationChannels
            ? null
            : notificationPreferenceService.getLegacyNotificationSound();
    }

    @Override
    @Nullable
    public Uri getDefaultGroupRingtone() {
        return supportsNotificationChannels
            ? null
            : notificationPreferenceService.getLegacyGroupNotificationSound();
    }

    @Override
    public boolean isSilent(@NonNull ConversationId conversationId, boolean isGroup) {
        if (supportsNotificationChannels) {
            // Note that we do not manage the sound of notifications if notification channels are
            // supported. Therefore, we always return false as we do not display this particularly.
            return false;
        }
        Uri defaultRingtone, selectedRingtone;
        if (isGroup) {
            defaultRingtone = getDefaultGroupRingtone();
            selectedRingtone = getGroupRingtone(conversationId);
        } else {
            defaultRingtone = getDefaultContactRingtone();
            selectedRingtone = getContactRingtone(conversationId);
        }
        return !(defaultRingtone != null && defaultRingtone.equals(selectedRingtone)) &&
            hasNoRingtone(conversationId);
    }

    private boolean hasNoRingtone(@NonNull ConversationId conversationId) {
        final @Nullable Uri ringtone = getRingtoneByConversationId(conversationId);
        return (ringtone == null || ringtone.toString().equals(ServicesConstants.PREFERENCES_NULL));
    }
}
