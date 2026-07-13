package ch.threema.app.webclient.converter;

import java.time.Instant;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;

import androidx.annotation.Nullable;
import ch.threema.data.datatypes.ContactNotificationTriggerPolicyOverride;
import ch.threema.data.datatypes.ContactNotificationTriggerPolicyOverridePolicy;
import ch.threema.data.datatypes.ConversationId;
import ch.threema.data.datatypes.GroupNotificationTriggerPolicyOverride;
import ch.threema.app.managers.ServiceManager;
import ch.threema.app.services.RingtoneService;
import ch.threema.app.webclient.exceptions.ConversionException;
import ch.threema.data.datatypes.GroupNotificationTriggerPolicyOverridePolicy;
import ch.threema.data.datatypes.NotificationTriggerPolicyOverride;
import ch.threema.storage.models.ConversationModel;

@AnyThread
public class NotificationSettings extends Converter {

    private final static String SOUND = "sound";
    private final static String DND = "dnd";
    private final static String MODE = "mode";
    private final static String MENTION_ONLY = "mentionOnly";
    private final static String UNTIL = "until";
    private final static String MODE_DEFAULT = "default";
    private final static String MODE_MUTED = "muted";
    private final static String MODE_ON = "on";
    private final static String MODE_OFF = "off";
    private final static String MODE_UNTIL = "until";

    @NonNull
    public static MsgpackObjectBuilder convert(@NonNull ConversationModel conversation) throws ConversionException {
        // Prepare objects
        final MsgpackObjectBuilder data = new MsgpackObjectBuilder();
        final MsgpackObjectBuilder sound = new MsgpackObjectBuilder();
        final MsgpackObjectBuilder dnd = new MsgpackObjectBuilder();

        // Services
        final ServiceManager serviceManager = ServiceManager.get();
        if (serviceManager == null) {
            throw new ConversionException("Could not get service manager");
        }
        final @NonNull RingtoneService ringtoneService = serviceManager.getRingtoneService();

        // Sound settings
        final @NonNull ConversationId conversationId = conversation.messageReceiver.getConversationId();
        if (ringtoneService.hasCustomRingtone(conversationId) && ringtoneService.isSilent(conversationId, conversation.isGroupConversation())) {
            sound.put(MODE, MODE_MUTED);
        } else {
            sound.put(MODE, MODE_DEFAULT);
        }

        // DND settings
        final @Nullable NotificationTriggerPolicyOverride<?> notificationTriggerPolicyOverride = conversation.messageReceiver
            .getNotificationTriggerPolicyOverrideOrNull();
        if (notificationTriggerPolicyOverride instanceof ContactNotificationTriggerPolicyOverride) {
            if (notificationTriggerPolicyOverride.getPolicy() == ContactNotificationTriggerPolicyOverridePolicy.NEVER) {
                Instant expiresAt = notificationTriggerPolicyOverride.getExpiresAt();
                if (expiresAt != null) {
                    dnd.put(MODE, MODE_UNTIL);
                    dnd.put(UNTIL, expiresAt.toEpochMilli());
                } else {
                    dnd.put(MODE, MODE_ON);
                }
            }
            dnd.put(MENTION_ONLY, false);
        } else if (notificationTriggerPolicyOverride instanceof GroupNotificationTriggerPolicyOverride) {
            if (notificationTriggerPolicyOverride.getPolicy() == GroupNotificationTriggerPolicyOverridePolicy.MENTIONED) {
                dnd.put(MENTION_ONLY, true);
            } else if (notificationTriggerPolicyOverride.getPolicy() == GroupNotificationTriggerPolicyOverridePolicy.NEVER) {
                dnd.put(MENTION_ONLY, false);
            }
            Instant expiresAt = notificationTriggerPolicyOverride.getExpiresAt();
            if (expiresAt != null) {
                dnd.put(MODE, MODE_UNTIL);
                dnd.put(UNTIL, expiresAt.toEpochMilli());
            } else {
                dnd.put(MODE, MODE_ON);
            }
        } else {
            dnd.put(MODE, MODE_OFF);
        }

        data.put(SOUND, sound);
        data.put(DND, dnd);
        return data;
    }
}
