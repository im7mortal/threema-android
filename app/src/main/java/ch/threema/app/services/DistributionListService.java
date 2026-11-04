package ch.threema.app.services;

import java.util.List;
import java.util.Set;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import ch.threema.app.messagereceiver.DistributionListMessageReceiver;
import ch.threema.base.SessionScoped;
import ch.threema.base.ThreemaException;
import ch.threema.data.datatypes.ConversationCategory;
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

    boolean existsById(long id);

    /**
     * Checks whether any of the provided ids correspond to an existing distribution list.
     */
    boolean existsAnyById(@NonNull Set<Long> ids);

    @NonNull
    List<DistributionListModel> getByIds(@NonNull List<Long> ids);

    boolean allExist(@NonNull Set<Long> ids);

    @NonNull
    List<DistributionListModel> getAllArchived();

    @NonNull
    Set<Long> getAllNonAdHocIds();

    DistributionListModel createDistributionList(@Nullable String name, String[] memberIdentities) throws ThreemaException;

    DistributionListModel createDistributionList(@Nullable String name, String[] memberIdentities, boolean isHidden) throws ThreemaException;

    /**
     * Persist the given distribution list model.
     *
     * @throws IllegalArgumentException if a distribution list with the given id already exists or the members are empty
     */
    void persistDistributionListModel(
        @NonNull DistributionListModel distributionListModel,
        @NonNull Set<String> members
    );

    DistributionListModel updateDistributionList(DistributionListModel distributionListModel, String name, String[] memberIdentities) throws ThreemaException;

    void addMemberToDistributionList(DistributionListModel distributionListModel, String identity);

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
     * Set the conversation category of the distribution list model
     */
    void setConversationCategory(
        @NonNull DistributionListModel distributionListModel,
        @NonNull ConversationCategory conversationCategory
    );

    /**
     * Set the `lastUpdate` field of the specified distribution list to the current date.
     * <p>
     * Save the model and notify the event bus.
     */
    void bumpLastUpdate(@NonNull DistributionListModel distributionListModel);
}
