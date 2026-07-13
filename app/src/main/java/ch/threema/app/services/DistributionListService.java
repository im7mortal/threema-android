package ch.threema.app.services;

import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import ch.threema.app.messagereceiver.DistributionListMessageReceiver;
import ch.threema.base.SessionScoped;
import ch.threema.base.ThreemaException;
import ch.threema.data.datatypes.ConversationVisibility;
import ch.threema.storage.models.ContactModel;
import ch.threema.storage.models.DistributionListMemberModel;
import ch.threema.storage.models.DistributionListModel;

@SessionScoped
public interface DistributionListService extends AvatarService<Long> {
    interface DistributionListFilter {
        boolean sortingByDate();

        boolean sortingAscending();

        boolean showHidden();
    }

    @Nullable
    DistributionListModel getById(long id);

    @NonNull
    List<DistributionListModel> getByIds(@NonNull List<Long> ids);

    DistributionListModel createDistributionList(@Nullable String name, String[] memberIdentities) throws ThreemaException;

    DistributionListModel createDistributionList(@Nullable String name, String[] memberIdentities, boolean isHidden) throws ThreemaException;

    DistributionListModel updateDistributionList(DistributionListModel distributionListModel, String name, String[] memberIdentities) throws ThreemaException;

    boolean addMemberToDistributionList(DistributionListModel distributionListModel, String identity);

    boolean remove(DistributionListModel distributionListModel);

    boolean removeAll();

    String[] getDistributionListIdentities(DistributionListModel distributionListModel);

    List<DistributionListMemberModel> getDistributionListMembers(DistributionListModel distributionListModel);

    List<DistributionListModel> getAll();

    List<DistributionListModel> getAll(DistributionListFilter filter);

    List<ContactModel> getMembers(DistributionListModel distributionListModel);

    @NonNull
    String getMembersString(DistributionListModel distributionListModel);

    @Nullable
    DistributionListMessageReceiver createReceiver(long distributionListId);

    @NonNull
    DistributionListMessageReceiver createReceiver(@NonNull DistributionListModel distributionListModel);

    /**
     * Archive the distribution list model. Note that this change will only be applied if it is
     * currently not archived. This sets the conversation visibility to 'archived' - even if it is
     * currently 'pinned'.
     */
    void archive(@NonNull DistributionListModel distributionListModel);

    /**
     * Unarchive the distribution list model. Note that this change will only be applied if it is
     * currently archived. A pinned distribution list will therefore remain pinned after calling
     * this method.
     */
    void unarchive(@NonNull DistributionListModel distributionListModel);

    /**
     * Pin the distribution list. Note that this change will only be applied if it is currently not
     * pinned. An archived distribution list will therefore also be pinned and unarchived after
     * calling this method.
     */
    void pin(@NonNull DistributionListModel distributionListModel);

    /**
     * Unpin the distribution list. Note that this change will only be applied if it is currently
     * pinned. An archived distribution list model will therefore remain archived after calling this
     * method.
     */
    void unpin(@NonNull DistributionListModel distributionListModel);

    /**
     * Set the conversation visibility of the distribution list model.
     */
    void setConversationVisibility(
        @NonNull DistributionListModel distributionListModel,
        @NonNull ConversationVisibility conversationVisibility
    );

    /**
     * Set the `lastUpdate` field of the specified distribution list to the current date.
     * <p>
     * Save the model and notify the event bus.
     */
    void bumpLastUpdate(@NonNull DistributionListModel distributionListModel);
}
