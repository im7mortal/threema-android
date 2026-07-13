package ch.threema.domain.protocol.csp.messages.poll;

import ch.threema.base.ThreemaException;
import ch.threema.domain.protocol.csp.ProtocolDefines;

import java.util.Arrays;

import static ch.threema.common.ByteArrayExtensionsKt.toHexString;
import static ch.threema.common.SecureRandomExtensionsKt.generateRandomBytes;
import static ch.threema.common.SecureRandomExtensionsKt.secureRandom;

import androidx.annotation.NonNull;

/**
 * Wrapper class for poll IDs (consisting of 8 bytes, chosen by the poll creator and not guaranteed
 * to be unique across multiple poll creators).
 */
public class PollId {

    @NonNull
    private final byte[] pollId;

    public PollId() {
        pollId = generateRandomBytes(secureRandom(), ProtocolDefines.POLL_ID_LEN);
    }

    public PollId(byte[] pollId) throws ThreemaException {
        if (pollId.length != ProtocolDefines.POLL_ID_LEN)
            /* Invalid poll ID length */
            throw new ThreemaException("TM028");

        this.pollId = pollId;
    }

    public PollId(byte[] data, int offset) {
        pollId = new byte[ProtocolDefines.POLL_ID_LEN];
        System.arraycopy(data, offset, pollId, 0, ProtocolDefines.POLL_ID_LEN);
    }

    public byte[] getPollId() {
        return pollId;
    }

    @Override
    public String toString() {
        return toHexString(pollId);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null)
            return false;
        if (obj == this)
            return true;
        if (obj.getClass() != getClass())
            return false;

        return Arrays.equals(pollId, ((PollId) obj).pollId);
    }

    @Override
    public int hashCode() {
        /* poll IDs are usually random, so just taking the first four bytes is fine */
        return pollId[0] << 24 | (pollId[1] & 0xFF) << 16 | (pollId[2] & 0xFF) << 8 | (pollId[3] & 0xFF);
    }
}
