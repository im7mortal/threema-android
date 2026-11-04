package ch.threema.domain.protocol

import ch.threema.common.Base64
import ch.threema.common.emptyByteArray
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith

class ChallengeResponseHelperTest {
    @Test
    fun `calculate challenge response`() {
        val response = ChallengeResponseHelper().calculateResponse(
            sharedSecret = "shared-secret".toByteArray(),
            challenge = "challenge".toByteArray(),
        )
        assertContentEquals(
            Base64.decode("mGcvQrPUt3bDhzGuSM9u+wy2E9NXyhtPIDic+83+O4k="),
            response,
        )
    }

    @Test
    fun `challenge response fails when invalid parameters are used`() {
        assertFailsWith<IllegalArgumentException> {
            ChallengeResponseHelper().calculateResponse(
                sharedSecret = emptyByteArray(),
                challenge = "challenge".toByteArray(),
            )
        }
    }
}
