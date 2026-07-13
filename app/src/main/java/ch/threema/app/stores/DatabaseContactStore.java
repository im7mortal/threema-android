package ch.threema.app.stores;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import ch.threema.data.datatypes.PredefinedContact;
import ch.threema.domain.models.BasicContact;
import ch.threema.domain.models.Contact;
import ch.threema.domain.stores.ContactStore;
import ch.threema.storage.factories.ContactModelFactory;
import ch.threema.storage.models.ContactModel;

/**
 * The {@link DatabaseContactStore} is an implementation of the {@link ContactStore} interface
 * which stores the contacts in the Android SQLite database.
 */
public class DatabaseContactStore implements ContactStore {
    private final @NonNull ContactModelFactory contactModelFactory;

    /**
     * The cache of contacts. Note that this cache only contains the cached contacts from a server
     * fetch. Contacts from the database are not cached here.
     */
    private final @NonNull Map<String, BasicContact> contactCache = new HashMap<>();

    public DatabaseContactStore(
        @NonNull ContactModelFactory contactModelFactory
    ) {
        this.contactModelFactory = contactModelFactory;
    }

    @Nullable
    @Override
    public ContactModel getContactForIdentity(@NonNull String identity) {
        return contactModelFactory.getByIdentity(identity);
    }

    @NonNull
    public List<ContactModel> getContactsForIdentities(@NonNull List<String> identities) {
        return contactModelFactory.getByIdentities(identities);
    }

    @Nullable
    public ContactModel getContactModelForLookupKey(final @NonNull String lookupKey) {
        return contactModelFactory.getByLookupKey(lookupKey);
    }

    @Override
    public void addCachedContact(@NonNull BasicContact contact) {
        contactCache.put(contact.getIdentity(), contact);
    }

    @Nullable
    @Override
    public BasicContact getCachedContact(@NonNull String identity) {
        return contactCache.get(identity);
    }

    @Nullable
    @Override
    public Contact getContactForIdentityIncludingCache(@NonNull String identity) {
        PredefinedContact specialContact = PredefinedContact.getSpecialContact(identity);
        if (specialContact != null) {
            return specialContact.toBasicContact();
        }

        Contact cached = contactCache.get(identity);
        if (cached != null) {
            return cached;
        }

        return getContactForIdentity(identity);
    }
}
