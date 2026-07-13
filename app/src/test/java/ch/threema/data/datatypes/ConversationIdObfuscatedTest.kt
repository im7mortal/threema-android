package ch.threema.data.datatypes

import kotlin.test.Test
import kotlin.test.assertEquals
import testdata.TestData

class ConversationIdObfuscatedTest {

    @Test
    fun forContact() {
        val conversationIdObfuscated = ConversationIdObfuscated.forContact(
            ContactConversationId(
                identity = TestData.Identities.OTHER_1.value,
            ),
        )
        assertEquals(
            expected = "CTAP6ZBDZFGQZ4PGKPV4R7YRT25TGQCBACXQC6ZJMYAK6OGBCURA",
            actual = conversationIdObfuscated.value,
        )
    }

    @Test
    fun forGroup() {
        val conversationIdObfuscated = ConversationIdObfuscated.forGroup(
            GroupConversationId(
                groupDatabaseId = 250L,
            ),
        )
        assertEquals(
            expected = "YXMFW7WYJ2CRJZ7B7SITGIPNHIQ6AG7G55NHOSHNMX4XSULENDOQ",
            actual = conversationIdObfuscated.value,
        )
    }

    @Test
    fun forDistributionList() {
        val conversationIdObfuscated = ConversationIdObfuscated.forDistributionList(
            DistributionListConversationId(
                distributionListId = 710L,
            ),
        )
        assertEquals(
            expected = "2C3TKZYYATVZVCQFHLP4R23PVJX66WIVZ3MNHVXPZQI7ILI5YIFQ",
            actual = conversationIdObfuscated.value,
        )
    }
}
