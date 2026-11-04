package ch.threema.common

import java.nio.ByteBuffer

fun CharArray.toDirectByteBuffer(): ByteBuffer =
    String(this).toDirectByteBuffer()

fun String.toDirectByteBuffer(): ByteBuffer =
    toByteArray().toDirectByteBuffer()

fun ByteArray.toDirectByteBuffer(): ByteBuffer =
    ByteBuffer.allocateDirect(size).put(this).flip()

fun emptyDirectByteBuffer(): ByteBuffer =
    ByteBuffer.allocateDirect(0)

fun concatenateByteArraysToDirectByteBuffer(vararg byteArrays: ByteArray): ByteBuffer =
    ByteBuffer.allocateDirect(byteArrays.sumOf { it.size })
        .also { byteBuffer ->
            byteArrays.forEach { byteArray ->
                byteBuffer.put(byteArray)
            }
        }
        .flip()
