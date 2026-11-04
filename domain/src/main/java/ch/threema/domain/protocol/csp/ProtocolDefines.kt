package ch.threema.domain.protocol.csp

import ch.threema.base.crypto.NaCl
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds

@Suppress("unused")
object ProtocolDefines {
    // Timers and timeouts
    val CONNECT_TIMEOUT = 15.seconds
    val CONNECT_TIMEOUT_IPV6 = 3.seconds

    val READ_TIMEOUT = 20.seconds
    val WRITE_TIMEOUT = 20.seconds
    val BLOB_CONNECT_TIMEOUT = 30.seconds
    val BLOB_LOAD_TIMEOUT = 100.seconds
    val API_REQUEST_TIMEOUT = 20.seconds

    val RECONNECT_BASE_INTERVAL = 2.seconds
    val RECONNECT_MAX_INTERVAL = 10.seconds

    // Echo request timeouts
    val ECHO_RESPONSE_TIMEOUT = 10.seconds
    val ECHO_REQUEST_INTERVAL_CSP = 60.seconds
    val CONNECTION_IDLE_TIMEOUT_CSP = 120.seconds
    val ECHO_REQUEST_INTERVAL_MD = 15.seconds
    val CONNECTION_IDLE_TIMEOUT_MD = 30.seconds

    // object lengths
    const val COOKIE_LEN = 16
    const val PUSH_FROM_LEN = 32
    const val IDENTITY_LEN = 8
    const val MESSAGE_ID_LEN = 8
    const val BLOB_ID_LEN = 16
    const val BLOB_KEY_LEN = 32
    const val GROUP_ID_LEN = 8
    const val POLL_ID_LEN = 8

    const val MAX_AVAILABILITY_STATUS_DESCRIPTION_BYTES = 256

    // server handshake
    const val SERVER_HELLO_BOXLEN = NaCl.PUBLIC_KEY_BYTES + COOKIE_LEN + NaCl.BOX_OVERHEAD_BYTES
    const val SERVER_HELLO_LEN = COOKIE_LEN + SERVER_HELLO_BOXLEN
    const val SERVER_LOGIN_ACK_LEN = 32
    const val EXTENSION_INDICATOR_LEN = 32
    const val VOUCH_LEN = 32
    const val RESERVED1_LEN = 24
    const val RESERVED2_LEN = 16
    const val LOGIN_LEN = IDENTITY_LEN + EXTENSION_INDICATOR_LEN + COOKIE_LEN + RESERVED1_LEN + VOUCH_LEN + RESERVED2_LEN

    // max message size
    const val MAX_PKT_LEN = 8192
    const val OVERHEAD_NACL_BOX = 16 // Excluding nonce
    const val OVERHEAD_PKT_HDR = 4
    const val OVERHEAD_MSG_HDR = 88
    const val MAX_TEXT_MESSAGE_LEN = 6000
    const val MIN_MESSAGE_PADDED_LEN = 32

    // message types
    const val MSGTYPE_TEXT = 0x01
    const val MSGTYPE_LOCATION = 0x10
    const val MSGTYPE_POLL_CREATE = 0x15
    const val MSGTYPE_POLL_VOTE = 0x16
    const val MSGTYPE_FILE = 0x17
    const val MSGTYPE_CONTACT_SET_PHOTO = 0x18
    const val MSGTYPE_CONTACT_DELETE_PHOTO = 0x19
    const val MSGTYPE_CONTACT_REQUEST_PHOTO = 0x1a
    const val MSGTYPE_GROUP_TEXT = 0x41
    const val MSGTYPE_GROUP_LOCATION = 0x42
    const val MSGTYPE_GROUP_FILE = 0x46
    const val MSGTYPE_GROUP_CREATE = 0x4a
    const val MSGTYPE_GROUP_RENAME = 0x4b
    const val MSGTYPE_GROUP_LEAVE = 0x4c
    const val MSGTYPE_GROUP_CALL_START = 0x4f
    const val MSGTYPE_GROUP_SET_PHOTO = 0x50
    const val MSGTYPE_GROUP_REQUEST_SYNC = 0x51
    const val MSGTYPE_GROUP_POLL_CREATE = 0x52
    const val MSGTYPE_GROUP_POLL_VOTE = 0x53
    const val MSGTYPE_GROUP_DELETE_PHOTO = 0x54
    const val MSGTYPE_VOIP_CALL_OFFER = 0x60
    const val MSGTYPE_VOIP_CALL_ANSWER = 0x61
    const val MSGTYPE_VOIP_ICE_CANDIDATES = 0x62
    const val MSGTYPE_VOIP_CALL_HANGUP = 0x63
    const val MSGTYPE_VOIP_CALL_RINGING = 0x64
    const val MSGTYPE_DELIVERY_RECEIPT = 0x80
    const val MSGTYPE_GROUP_DELIVERY_RECEIPT = 0x81
    const val MSGTYPE_TYPING_INDICATOR = 0x90
    const val MSGTYPE_FS_ENVELOPE = 0xa0
    const val MSGTYPE_EMPTY = 0xfc
    const val MSGTYPE_WEB_SESSION_RESUME = 0xfe
    const val MSGTYPE_EDIT_MESSAGE = 0x91
    const val MSGTYPE_DELETE_MESSAGE = 0x92
    const val MSGTYPE_GROUP_EDIT_MESSAGE = 0x93
    const val MSGTYPE_GROUP_DELETE_MESSAGE = 0x94
    const val MSGTYPE_REACTION = 0x82
    const val MSGTYPE_GROUP_REACTION = 0x83
    const val MSGTYPE_WORK_SYNC_DELTA = 0xfd

    // deprecated message types
    const val MSGTYPE_IMAGE = 0x02
    const val MSGTYPE_VIDEO = 0x13
    const val MSGTYPE_AUDIO = 0x14
    const val MSGTYPE_GROUP_IMAGE = 0x43
    const val MSGTYPE_GROUP_VIDEO = 0x44
    const val MSGTYPE_GROUP_AUDIO = 0x45

    // message flags
    // Note: Do not forget to update AbstractMessage#getMessageTypeDefaultFlags when adding a flag
    const val MESSAGE_FLAG_SEND_PUSH = 0x01
    const val MESSAGE_FLAG_NO_SERVER_QUEUING = 0x02
    const val MESSAGE_FLAG_NO_SERVER_ACK = 0x04
    const val MESSAGE_FLAG_GROUP = 0x10
    const val MESSAGE_FLAG_SHORT_LIVED = 0x20
    const val MESSAGE_FLAG_NO_DELIVERY_RECEIPTS = 0x80

    // delivery receipt statuses
    const val DELIVERYRECEIPT_MSGRECEIVED = 0x01
    const val DELIVERYRECEIPT_MSGREAD = 0x02
    const val DELIVERYRECEIPT_MSGUSERACK = 0x03
    const val DELIVERYRECEIPT_MSGUSERDEC = 0x04

    // payload types
    const val PLTYPE_ECHO_REQUEST = 0x00
    const val PLTYPE_ECHO_REPLY = 0x80
    const val PLTYPE_OUTGOING_MESSAGE = 0x01
    const val PLTYPE_OUTGOING_MESSAGE_ACK = 0x81
    const val PLTYPE_INCOMING_MESSAGE = 0x02
    const val PLTYPE_INCOMING_MESSAGE_ACK = 0x82
    const val PLTYPE_PUSH_NOTIFICATION_TOKEN = 0x20
    const val PLTYPE_VOIP_PUSH_NOTIFICATION_TOKEN = 0x24
    const val PLTYPE_SET_CONNECTION_IDLE_TIMEOUT = 0x30
    const val PLTYPE_QUEUE_SEND_COMPLETE = 0xd0
    const val PLTYPE_DEVICE_COOKIE_CHANGE_INDICATION = 0xd2
    const val PLTYPE_CLEAR_DEVICE_COOKIE_CHANGE_INDICATION = 0xd3
    const val PLTYPE_ERROR = 0xe0
    const val PLTYPE_ALERT = 0xe1
    const val PLTYPE_UNBLOCK_INCOMING_MESSAGES = 0x03

    // push token types
    const val PUSHTOKEN_TYPE_NONE = 0x00
    const val PUSHTOKEN_TYPE_FCM = 0x11
    const val PUSHTOKEN_TYPE_HMS = 0x13

    // nonces
    val GROUP_PHOTO_NONCE = byteArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1)

    @JvmField
    val FILE_THUMBNAIL_NONCE = byteArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 2)

    @JvmField
    val CONTACT_PHOTO_NONCE = GROUP_PHOTO_NONCE

    @JvmField
    val FILE_NONCE = GROUP_PHOTO_NONCE

    // forward security
    const val FS_SESSION_ID_LENGTH = 16

    // Group Calls
    // TODO(ANDR-1974) Move gc-constants to other location
    const val GC_PROTOCOL_VERSION = 1
    const val GC_PCMK_LENGTH = 32
    val GC_PEEK_TIMEOUT = 5.seconds
    val GC_JOIN_TIMEOUT = 20.seconds
    val GC_GROUP_CALL_REFRESH_STEPS_TIMEOUT = 10.seconds
    val GC_GROUP_CALL_UPDATE_PERIOD = 10.seconds
    const val GC_PEEK_FAILED_ABANDON_MIN_TRIES = 3
    val GC_PEEK_FAILED_ABANDON_MIN_CALL_AGE = 10.hours
    const val GC_ALLOWED_BASE_URL_PROTOCOL = "https"

    // Auto Delete
    const val AUTO_DELETE_KEEP_MESSAGES_DAYS_OFF_VALUE = 0
    const val AUTO_DELETE_KEEP_MESSAGES_DAYS_MIN = 7
    const val AUTO_DELETE_KEEP_MESSAGES_DAYS_MAX = 3650

    // Profile pictures
    const val PROFILE_PICTURE_WIDTH_PX = 512
    const val PROFILE_PICTURE_HEIGHT_PX = 512
}
