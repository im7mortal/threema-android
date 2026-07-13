package ch.threema.app.backuprestore

import kotlin.test.Test
import kotlin.test.assertEquals

class LegacyMessageBodyTransformerTest {
    @Test
    fun `transform image message`() {
        val fileMessageBody = LegacyMessageBodyTransformer.transformImageBodyToFileBody(
            """[false,"7baeeac9a574ef6cdc6c1e2059d748b5deeb63ac9fa2ac432f4019483e5e44d8",""" +
                """"918bc66669102464938a1b08e81c9072","a0a1a2a3a4a5a6a7a8a9aaabacadaeafb0b1b2b3b4b5b6b7b8"]""",
        )
        assertEquals(
            """["918bc66669102464938a1b08e81c9072","7baeeac9a574ef6cdc6c1e2059d748b5deeb63ac9fa2ac432f4019483e5e44d8",""" +
                """"image/jpeg",0,null,1,false,null,null,{"_legacy_nonce":"a0a1a2a3a4a5a6a7a8a9aaabacadaeafb0b1b2b3b4b5b6b7b8"}]""",
            fileMessageBody,
        )
    }

    @Test
    fun `transform audio message`() {
        val fileMessageBody = LegacyMessageBodyTransformer.transformAudioBodyToFileBody(
            """[83,true,"7baeeac9a574ef6cdc6c1e2059d748b5deeb63ac9fa2ac432f4019483e5e44d8","918bc66669102464938a1b08e81c9072"]""",
        )
        assertEquals(
            """["918bc66669102464938a1b08e81c9072","7baeeac9a574ef6cdc6c1e2059d748b5deeb63ac9fa2ac432f4019483e5e44d8",""" +
                """"audio/aac",0,null,1,true,null,null,{"d":83}]""",
            fileMessageBody,
        )
    }

    @Test
    fun `transform video message`() {
        val fileMessageBody = LegacyMessageBodyTransformer.transformVideoBodyToFileBody(
            """[3,true,"68e59c39e5d1bf61345a06524e5c91f9a2ff45fa5f076d6c2559bf6377f3d45f","91c26972754bab09c765565b2d6040cf",662326]""",
        )
        assertEquals(
            """["91c26972754bab09c765565b2d6040cf","68e59c39e5d1bf61345a06524e5c91f9a2ff45fa5f076d6c2559bf6377f3d45f",""" +
                """"video/mpeg",662326,null,1,true,null,null,{"d":3}]""",
            fileMessageBody,
        )
    }
}
