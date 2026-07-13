package ch.threema.base.crypto

class SymmetricEncryptionResult(
    @JvmField val data: ByteArray,
    @JvmField val key: ByteArray,
) {
    val isEmpty: Boolean
        get() = data.isEmpty()
}
