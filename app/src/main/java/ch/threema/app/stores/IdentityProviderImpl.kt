package ch.threema.app.stores

import ch.threema.domain.types.Identity
import ch.threema.domain.types.IdentityString
import ch.threema.domain.types.toIdentityOrNull
import ch.threema.localcrypto.MasterKeyStorageManager

class IdentityProviderImpl(
    private val preferenceStore: PreferenceStore,
    masterKeyStorageManager: MasterKeyStorageManager,
) : MutableIdentityProvider {

    private var myIdentity: Identity? = null

    init {
        // If there is no master key file, it means that whatever data might already be stored in the app will be deleted, including the identity.
        // Therefore, we forego loading it into memory here.
        if (masterKeyStorageManager.keyExists()) {
            myIdentity = preferenceStore.getString(PreferenceStore.PREFS_IDENTITY)
                ?.toIdentityOrNull()
        }
    }

    override fun getIdentity() = myIdentity

    override fun setIdentity(identity: Identity?) {
        myIdentity = identity
        if (identity != null) {
            preferenceStore.save(PreferenceStore.PREFS_IDENTITY, identity.value)
        } else {
            preferenceStore.remove(PreferenceStore.PREFS_IDENTITY)
        }
    }

    override fun getIdentityString(): IdentityString? = myIdentity?.value
}
