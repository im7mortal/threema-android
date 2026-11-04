package ch.threema.app.libthreema.provider

import ch.threema.base.crypto.HashedNonce
import ch.threema.base.crypto.NonceFactory
import ch.threema.base.crypto.NonceScope
import ch.threema.base.utils.getThreemaLogger
import ch.threema.base.utils.withTimeMeasurement
import ch.threema.libthreema.HashedNonce as LibthreemaHashedNonce
import ch.threema.libthreema.NonceProvider as LibthreemaNonceProvider

class CspNonceProvider(
    nonceFactory: NonceFactory,
) : NonceProvider(
    nonceScope = NonceScope.CSP,
    nonceFactory = nonceFactory,
)

class D2dNonceProvider(
    nonceFactory: NonceFactory,
) : NonceProvider(
    nonceScope = NonceScope.D2D,
    nonceFactory = nonceFactory,
)

private val logger = getThreemaLogger("NonceProvider")

sealed class NonceProvider(
    private val nonceScope: NonceScope,
    private val nonceFactory: NonceFactory,
) : LibthreemaNonceProvider {

    private val getHashedNonceBatch: (batchIndex: ULong) -> List<LibthreemaHashedNonce> by lazy {
        createHashedNonceBatchProvider()
    }

    private fun createHashedNonceBatchProvider(): (batchIndex: ULong) -> List<LibthreemaHashedNonce> {
        val noncesList = ArrayList<HashedNonce>(NONCES_BATCH_SIZE)
        return { batchIndex ->
            noncesList.clear()
            nonceFactory.addHashedNoncesChunk(
                scope = nonceScope,
                chunkSize = NONCES_BATCH_SIZE,
                offset = NONCES_BATCH_SIZE * batchIndex.toInt(),
                nonces = noncesList,
            )
            logger.info("Get {} nonces for batchIndex={} and scope={}", noncesList.size, batchIndex, nonceScope)
            noncesList.map(HashedNonce::bytes)
        }
    }

    override fun has(hashedNonce: LibthreemaHashedNonce): Boolean = nonceFactory.exists(
        scope = nonceScope,
        hashedNonce = HashedNonce(hashedNonce),
    )

    override fun addMany(hashedNonces: List<LibthreemaHashedNonce>): Unit =
        withTimeMeasurement(logger, "addMany", hashedNonces.size) {
            logger.info("Add {} nonces for scope={}", hashedNonces.size, nonceScope)
            nonceFactory.insertHashedNonces(
                scope = nonceScope,
                nonces = hashedNonces.map(::HashedNonce),
            )
        }

    override fun getMany(batchIndex: ULong): List<LibthreemaHashedNonce> =
        withTimeMeasurement(logger, "getMany") {
            getHashedNonceBatch(batchIndex)
        }

    private companion object {
        // 1 MiB per nonce batch
        const val NONCES_BATCH_SIZE = 32768
    }
}
