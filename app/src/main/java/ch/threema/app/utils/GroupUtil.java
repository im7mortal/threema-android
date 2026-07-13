package ch.threema.app.utils;

import java.util.Set;
import java.util.stream.Collectors;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import ch.threema.domain.models.Contact;
import ch.threema.storage.models.group.GroupModelOld;

public class GroupUtil {

    public static String CENTRALLY_MANAGED_GATEWAY_GROUP_PREFIX = "☁";

    /**
     * Return true, if the group is created by a normal threema user
     * or by a gateway id and marked with a special prefix (cloud emoji {@link CENTRALLY_MANAGED_GATEWAY_GROUP_PREFIX}) as "centrally managed group"
     *
     * @see <a href="https://broadcast.threema.ch/en/faq#central-groups">What are centrally managed group chats?</a>
     */
    public static boolean shouldSendMessagesToCreator(@NonNull GroupModelOld groupModel) {
        return shouldSendMessagesToCreator(groupModel.getCreatorIdentity(), groupModel.getName());
    }

    public static boolean shouldSendMessagesToCreator(@NonNull String groupCreator, @Nullable String groupName) {
        return
            !ContactUtil.isGatewayContact(groupCreator)
                || (groupName != null && groupName.startsWith(CENTRALLY_MANAGED_GATEWAY_GROUP_PREFIX));
    }

    public static Set<String> getRecipientIdentitiesByFeatureSupport(@NonNull GroupFeatureSupport groupFeatureSupport) {
        return groupFeatureSupport
            .getContactsWithFeatureSupport()
            .stream()
            .map(Contact::getIdentity)
            .collect(Collectors.toSet());
    }
}
