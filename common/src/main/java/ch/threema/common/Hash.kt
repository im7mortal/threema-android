package ch.threema.common

import java.security.MessageDigest
import java.security.NoSuchAlgorithmException

/**
 *  Compute the SHA-256 hash of the provided bytes.
 *
 *  While a `sha256(ByteArray)` function is provided by libthreema, this implementation is way faster.
 *
 *  @throws NoSuchAlgorithmException if the `SHA-256` algorithm is not supported
 */
fun sha256(input: ByteArray): ByteArray =
    MessageDigest
        .getInstance("SHA-256")
        .digest(input)

/**
 *  Compute the SHA-256 hash of the provided string using the `UTF-8` charset.
 *
 *  While a `sha256(ByteArray)` function is provided by libthreema, this implementation is way faster.
 *
 *  @throws NoSuchAlgorithmException if the `SHA-256` algorithm is not supported
 */
fun sha256(input: String): ByteArray =
    sha256(
        input.toByteArray(
            charset = Charsets.UTF_8,
        ),
    )
