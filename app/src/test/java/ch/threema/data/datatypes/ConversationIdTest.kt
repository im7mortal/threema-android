package ch.threema.data.datatypes

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import testdata.TestData

class ConversationIdTest {

    @Test
    fun toDatabaseValue() {
        assertEquals(
            expected = "i-11111111",
            actual = ContactConversationId(identity = TestData.Identities.OTHER_1.value).toDatabaseValue(),
        )
        assertEquals(
            expected = "g-250",
            actual = GroupConversationId(groupDatabaseId = 250L).toDatabaseValue(),
        )
        assertEquals(
            expected = "d-710",
            actual = DistributionListConversationId(distributionListId = 710L).toDatabaseValue(),
        )
    }

    @Test
    fun fromDatabaseValue() {
        assertEquals(
            expected = ContactConversationId(identity = "11111111"),
            actual = ConversationId.fromDatabaseValue("i-${TestData.Identities.OTHER_1.value}"),
        )
        assertEquals(
            expected = GroupConversationId(groupDatabaseId = 250),
            actual = ConversationId.fromDatabaseValue("g-250"),
        )
        assertEquals(
            expected = DistributionListConversationId(distributionListId = 710),
            actual = ConversationId.fromDatabaseValue("d-710"),
        )
        assertNull(
            actual = ConversationId.fromDatabaseValue(""),
        )
        assertNull(
            actual = ConversationId.fromDatabaseValue("   "),
        )
        assertNull(
            actual = ConversationId.fromDatabaseValue("i-1111"),
        )
        assertNull(
            actual = ConversationId.fromDatabaseValue("11111111"),
        )
        assertNull(
            actual = ConversationId.fromDatabaseValue("f-11111111"),
        )
        assertNull(
            actual = ConversationId.fromDatabaseValue("c-11111111"),
        )
        assertNull(
            actual = ConversationId.fromDatabaseValue("g 250"),
        )
        assertNull(
            actual = ConversationId.fromDatabaseValue("d 250"),
        )
        assertNull(
            actual = ConversationId.fromDatabaseValue("G-250"),
        )
        assertNull(
            actual = ConversationId.fromDatabaseValue("D-250"),
        )
        assertNull(
            actual = ConversationId.fromDatabaseValue("g- 250 "),
        )
        assertNull(
            actual = ConversationId.fromDatabaseValue("g-250.1"),
        )
        assertNull(
            actual = ConversationId.fromDatabaseValue("d-710.1"),
        )
        assertNull(
            actual = ConversationId.fromDatabaseValue("null"),
        )
    }

    @Test
    fun toStringObfuscatesActualValue() {
        assertEquals(
            expected = "ContactConversationId(CTAP6ZBDZFGQZ4PGKPV4R7YRT25TGQCBACXQC6ZJMYAK6OGBCURA)",
            actual = ContactConversationId(identity = TestData.Identities.OTHER_1.value).toString(),
        )
        assertEquals(
            expected = "GroupConversationId(YXMFW7WYJ2CRJZ7B7SITGIPNHIQ6AG7G55NHOSHNMX4XSULENDOQ)",
            actual = GroupConversationId(groupDatabaseId = 250L).toString(),
        )
        assertEquals(
            expected = "DistributionListConversationId(2C3TKZYYATVZVCQFHLP4R23PVJX66WIVZ3MNHVXPZQI7ILI5YIFQ)",
            actual = DistributionListConversationId(distributionListId = 710L).toString(),
        )
    }
}
