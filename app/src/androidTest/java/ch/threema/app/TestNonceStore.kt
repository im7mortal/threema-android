package ch.threema.app

import ch.threema.base.crypto.HashedNonce
import ch.threema.base.crypto.Nonce
import ch.threema.base.crypto.NonceScope
import ch.threema.base.crypto.NonceStore

class TestNonceStore : NonceStore {
    override fun exists(scope: NonceScope, nonce: Nonce) = false

    override fun store(scope: NonceScope, nonce: Nonce) = true

    override fun getCount(scope: NonceScope) = 0L

    override fun getAllHashedNonces(scope: NonceScope): List<HashedNonce> = emptyList()

    override fun addHashedNoncesChunk(
        scope: NonceScope,
        chunkSize: Int,
        offset: Int,
        hashedNonces: MutableList<HashedNonce>,
    ) {
        // Nothing to do
    }

    override fun insertHashedNonces(scope: NonceScope, nonces: List<HashedNonce>) = true
}
