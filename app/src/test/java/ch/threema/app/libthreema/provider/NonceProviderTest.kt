package ch.threema.app.libthreema.provider

import ch.threema.base.crypto.HashedNonce
import ch.threema.base.crypto.Nonce
import ch.threema.base.crypto.NonceFactory
import ch.threema.base.crypto.NonceScope
import ch.threema.common.toByteArray
import ch.threema.domain.helpers.InMemoryNonceStore
import ch.threema.libthreema.HashedNonce as HashedLibthreemaNonce
import java.nio.ByteOrder.BIG_ENDIAN
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NonceProviderTest {

    private val myIdentity = "01234567"

    private val nonceStore = InMemoryNonceStore(myIdentity)
    private val nonceFactory = NonceFactory(nonceStore)

    @Test
    fun `nonce provider correctly checks whether nonce exists`() {
        forAllNonceScopes { scope, nonceProvider ->
            assertFalse(nonceProvider.has(testNonceHashed.bytes))

            nonceStore.store(scope, testNonce)

            assertTrue(nonceProvider.has(testNonceHashed.bytes))
        }
    }

    @Test
    fun `nonces added via provider are added to nonce store`() {
        forAllNonceScopes { scope, nonceProvider ->
            assertFalse(nonceStore.exists(scope, testNonce))
            assertFalse(nonceStore.exists(scope, testNonceHashed))

            nonceProvider.addMany(listOf(testNonceHashed.bytes))

            assertTrue(nonceStore.exists(scope, testNonce))
            assertTrue(nonceStore.exists(scope, testNonceHashed))
        }
    }

    private fun forAllNonceScopes(testBlock: (NonceScope, NonceProvider) -> Unit) {
        NonceScope.entries.forEach { scope ->
            val nonceProvider = createNonceProvider(scope)
            testBlock.invoke(scope, nonceProvider)
        }
    }

    @Test
    fun `csp and d2d nonces added via providers are stored with correct scope in nonce store`() {
        val cspNonceProvider = createNonceProvider(NonceScope.CSP)
        val d2dNonceProvider = createNonceProvider(NonceScope.D2D)

        assertFalse(nonceStore.exists(NonceScope.CSP, testNonce))
        assertFalse(nonceStore.exists(NonceScope.D2D, testNonce))

        cspNonceProvider.addMany(listOf(testNonceHashed.bytes))

        assertTrue(nonceStore.exists(NonceScope.CSP, testNonce))
        assertFalse(nonceStore.exists(NonceScope.D2D, testNonce))

        d2dNonceProvider.addMany(listOf(testNonceHashed.bytes))

        assertTrue(nonceStore.exists(NonceScope.CSP, testNonce))
        assertTrue(nonceStore.exists(NonceScope.D2D, testNonce))
    }

    /**
     * This does intentionally not test whether the "last" batch correctly returns an empty list, as this is depending on the underlying
     * NonceStore used in the NonceFactory. In the tests the [InMemoryNonceStore] is used which behaves differently than the
     * [ch.threema.storage.DatabaseNonceStore] used in the "real world". Suitable tests exist in the tests for
     * [ch.threema.storage.DatabaseNonceStore].
     */
    @Test
    fun `getMany returns many nonces from correct scope`() {
        val cspNoncesPt1 = (0..<32768).map { n ->
            HashedNonce(ByteArray(28) { 0x01 } + n.toByteArray(BIG_ENDIAN))
        }
        nonceStore.insertHashedNonces(NonceScope.CSP, cspNoncesPt1)
        val cspNoncesPt2 = (1..10).map { n ->
            HashedNonce(ByteArray(28) { 0x01 } + (32768 + n).toByteArray(BIG_ENDIAN))
        }
        nonceStore.insertHashedNonces(NonceScope.CSP, cspNoncesPt2)

        val d2dNoncesPt1 = (0..<32768).map { n ->
            HashedNonce(ByteArray(28) { 0x02 } + n.toByteArray(BIG_ENDIAN))
        }
        nonceStore.insertHashedNonces(NonceScope.D2D, d2dNoncesPt1)
        val d2dNoncesPt2 = (1..10).map { n ->
            HashedNonce(ByteArray(28) { 0x02 } + (32768 + n).toByteArray(BIG_ENDIAN))
        }
        nonceStore.insertHashedNonces(NonceScope.D2D, d2dNoncesPt2)

        val cspNonceProvider = createNonceProvider(NonceScope.CSP)
        val cspProviderNoncesPt1 = cspNonceProvider.getMany(0u)
        val cspProviderNoncesPt2 = cspNonceProvider.getMany(1u)

        assertSameNonces(cspNoncesPt1, cspProviderNoncesPt1)
        assertSameNonces(cspNoncesPt2, cspProviderNoncesPt2)

        val d2dNonceProvider = createNonceProvider(NonceScope.D2D)
        val d2dProviderNoncesPt1 = d2dNonceProvider.getMany(0u)
        val d2dProviderNoncesPt2 = d2dNonceProvider.getMany(1u)

        assertSameNonces(d2dNoncesPt1, d2dProviderNoncesPt1)
        assertSameNonces(d2dNoncesPt2, d2dProviderNoncesPt2)
    }

    private fun assertSameNonces(hashedNonces: List<HashedNonce>, hashedLibthreemaNonces: List<HashedLibthreemaNonce>) {
        assertEquals(hashedNonces.size, hashedLibthreemaNonces.size)
        hashedLibthreemaNonces.forEachIndexed { index, nonce ->
            assertContentEquals(hashedNonces[index].bytes, nonce)
        }
    }

    private fun createNonceProvider(scope: NonceScope): NonceProvider {
        return when (scope) {
            NonceScope.CSP -> CspNonceProvider(
                nonceFactory = nonceFactory,
            )
            NonceScope.D2D -> D2dNonceProvider(
                nonceFactory = nonceFactory,
            )
        }
    }

    private companion object {
        val testNonce = Nonce(
            byteArrayOf(
                0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07,
                0x08, 0x09, 0x0A, 0x0B, 0x0C, 0x0D, 0x0E, 0x0F,
                0x10, 0x11, 0x12, 0x13, 0x14, 0x15, 0x16, 0x17,
            ),
        )

        /**
         * [testNonce] that has been hashed with the identity "01234567" as key.
         */
        val testNonceHashed = HashedNonce(
            byteArrayOf(
                0xad.toByte(), 0xa4.toByte(), 0x4c.toByte(), 0x70.toByte(), 0x57.toByte(), 0xec.toByte(), 0xa3.toByte(), 0x90.toByte(),
                0xfc.toByte(), 0xad.toByte(), 0x9e.toByte(), 0xef.toByte(), 0x50.toByte(), 0x81.toByte(), 0x3b.toByte(), 0xf8.toByte(),
                0x7a.toByte(), 0xbe.toByte(), 0xfc.toByte(), 0x35.toByte(), 0x8b.toByte(), 0xb7.toByte(), 0xfb.toByte(), 0xc1.toByte(),
                0x63.toByte(), 0xc1.toByte(), 0x98.toByte(), 0xf6.toByte(), 0xf7.toByte(), 0xce.toByte(), 0x06.toByte(), 0x37.toByte(),
            ),
        )
    }
}
