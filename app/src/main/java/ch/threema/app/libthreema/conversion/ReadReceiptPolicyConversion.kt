package ch.threema.app.libthreema.conversion

import ch.threema.domain.models.ReadReceiptPolicy
import ch.threema.libthreema.ReadReceiptPolicy as LibthreemaReadReceiptPolicy

fun ReadReceiptPolicy.toLibthreemaReadReceiptPolicy() = when (this) {
    ReadReceiptPolicy.SEND -> LibthreemaReadReceiptPolicy.SEND_READ_RECEIPT
    ReadReceiptPolicy.DONT_SEND -> LibthreemaReadReceiptPolicy.DONT_SEND_READ_RECEIPT
    ReadReceiptPolicy.DEFAULT -> null
}

fun LibthreemaReadReceiptPolicy?.toReadReceiptPolicy() = when (this) {
    LibthreemaReadReceiptPolicy.SEND_READ_RECEIPT -> ReadReceiptPolicy.SEND
    LibthreemaReadReceiptPolicy.DONT_SEND_READ_RECEIPT -> ReadReceiptPolicy.DONT_SEND
    null -> ReadReceiptPolicy.DEFAULT
}
