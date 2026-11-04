package ch.threema.app.libthreema.extensions

import ch.threema.app.preference.service.SynchronizedSettingsService
import ch.threema.libthreema.GroupCallPolicy as LibthreemaGroupCallPolicy
import ch.threema.libthreema.O2oCallConnectionPolicy as LibthreemaO2oCallConnectionPolicy
import ch.threema.libthreema.O2oCallPolicy as LibthreemaO2oCallPolicy
import ch.threema.libthreema.O2oCallVideoPolicy as LibthreemaO2oCallVideoPolicy
import ch.threema.libthreema.ReadReceiptPolicy as LibthreemaReadReceiptPolicy
import ch.threema.libthreema.ScreenshotPolicy as LibthreemaScreenshotPolicy
import ch.threema.libthreema.TypingIndicatorPolicy as LibthreemaTypingIndicatorPolicy
import ch.threema.libthreema.UnknownContactPolicy as LibthreemaUnknownContactPolicy

fun SynchronizedSettingsService.getUnknownContactPolicy(): LibthreemaUnknownContactPolicy =
    if (isBlockUnknown()) {
        LibthreemaUnknownContactPolicy.BLOCK_UNKNOWN
    } else {
        LibthreemaUnknownContactPolicy.ALLOW_UNKNOWN
    }

fun SynchronizedSettingsService.setUnknownContactPolicy(unknownContactPolicy: LibthreemaUnknownContactPolicy) {
    getUnknownContactPolicySetting().persist(
        when (unknownContactPolicy) {
            LibthreemaUnknownContactPolicy.ALLOW_UNKNOWN -> false
            LibthreemaUnknownContactPolicy.BLOCK_UNKNOWN -> true
        },
    )
}

fun SynchronizedSettingsService.getReadReceiptPolicy(): LibthreemaReadReceiptPolicy =
    if (areReadReceiptsEnabled()) {
        LibthreemaReadReceiptPolicy.SEND_READ_RECEIPT
    } else {
        LibthreemaReadReceiptPolicy.DONT_SEND_READ_RECEIPT
    }

fun SynchronizedSettingsService.setReadReceiptPolicy(readReceiptPolicy: LibthreemaReadReceiptPolicy) {
    getReadReceiptPolicySetting().persist(
        when (readReceiptPolicy) {
            LibthreemaReadReceiptPolicy.SEND_READ_RECEIPT -> true
            LibthreemaReadReceiptPolicy.DONT_SEND_READ_RECEIPT -> false
        },
    )
}

fun SynchronizedSettingsService.getTypingIndicatorPolicy(): LibthreemaTypingIndicatorPolicy =
    if (isTypingIndicatorEnabled()) {
        LibthreemaTypingIndicatorPolicy.SEND_TYPING_INDICATOR
    } else {
        LibthreemaTypingIndicatorPolicy.DONT_SEND_TYPING_INDICATOR
    }

fun SynchronizedSettingsService.setTypingIndicatorPolicy(typingIndicatorPolicy: LibthreemaTypingIndicatorPolicy) {
    getTypingIndicatorPolicySetting().persist(
        when (typingIndicatorPolicy) {
            LibthreemaTypingIndicatorPolicy.SEND_TYPING_INDICATOR -> true
            LibthreemaTypingIndicatorPolicy.DONT_SEND_TYPING_INDICATOR -> false
        },
    )
}

fun SynchronizedSettingsService.getO2oCallPolicy(): LibthreemaO2oCallPolicy =
    if (isVoipEnabled()) {
        LibthreemaO2oCallPolicy.ALLOW_O2O_CALL
    } else {
        LibthreemaO2oCallPolicy.DENY_O2O_CALL
    }

fun SynchronizedSettingsService.setO2oCallPolicy(o2oCallPolicy: LibthreemaO2oCallPolicy) {
    getO2oCallPolicySetting().persist(
        when (o2oCallPolicy) {
            LibthreemaO2oCallPolicy.ALLOW_O2O_CALL -> true
            LibthreemaO2oCallPolicy.DENY_O2O_CALL -> false
        },
    )
}

fun SynchronizedSettingsService.getO2oCallConnectionPolicy(): LibthreemaO2oCallConnectionPolicy =
    if (isForceTURN()) {
        LibthreemaO2oCallConnectionPolicy.REQUIRE_RELAYED_CONNECTION
    } else {
        LibthreemaO2oCallConnectionPolicy.ALLOW_DIRECT_CONNECTION
    }

fun SynchronizedSettingsService.setO2oCallConnectionPolicy(o2oCallConnectionPolicy: LibthreemaO2oCallConnectionPolicy) {
    getO2oCallConnectionPolicySetting().persist(
        when (o2oCallConnectionPolicy) {
            LibthreemaO2oCallConnectionPolicy.ALLOW_DIRECT_CONNECTION -> false
            LibthreemaO2oCallConnectionPolicy.REQUIRE_RELAYED_CONNECTION -> true
        },
    )
}

fun SynchronizedSettingsService.getO2oCallVideoPolicy(): LibthreemaO2oCallVideoPolicy =
    if (areVideoCallsEnabled()) {
        LibthreemaO2oCallVideoPolicy.ALLOW_VIDEO
    } else {
        LibthreemaO2oCallVideoPolicy.DENY_VIDEO
    }

fun SynchronizedSettingsService.setO2oCallVideoPolicy(o2oCallVideoPolicy: LibthreemaO2oCallVideoPolicy) {
    getO2oCallVideoPolicySetting().persist(
        when (o2oCallVideoPolicy) {
            LibthreemaO2oCallVideoPolicy.ALLOW_VIDEO -> true
            LibthreemaO2oCallVideoPolicy.DENY_VIDEO -> false
        },
    )
}

fun SynchronizedSettingsService.getGroupCallPolicy(): LibthreemaGroupCallPolicy =
    if (areGroupCallsEnabled()) {
        LibthreemaGroupCallPolicy.ALLOW_GROUP_CALL
    } else {
        LibthreemaGroupCallPolicy.DENY_GROUP_CALL
    }

fun SynchronizedSettingsService.setGroupCallPolicy(groupCallPolicy: LibthreemaGroupCallPolicy) {
    getGroupCallPolicySetting().persist(
        when (groupCallPolicy) {
            LibthreemaGroupCallPolicy.ALLOW_GROUP_CALL -> true
            LibthreemaGroupCallPolicy.DENY_GROUP_CALL -> false
        },
    )
}

fun SynchronizedSettingsService.getScreenshotPolicy(): LibthreemaScreenshotPolicy =
    if (areScreenshotsDisabled()) {
        LibthreemaScreenshotPolicy.DENY_SCREENSHOT
    } else {
        LibthreemaScreenshotPolicy.ALLOW_SCREENSHOT
    }

fun SynchronizedSettingsService.setScreenshotPolicy(screenshotPolicy: LibthreemaScreenshotPolicy) {
    getScreenshotPolicySetting().persist(
        when (screenshotPolicy) {
            LibthreemaScreenshotPolicy.ALLOW_SCREENSHOT -> false
            LibthreemaScreenshotPolicy.DENY_SCREENSHOT -> true
        },
    )
}
