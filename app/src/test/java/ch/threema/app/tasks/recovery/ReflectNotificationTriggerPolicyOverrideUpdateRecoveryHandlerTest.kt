package ch.threema.app.tasks.recovery

import ch.threema.app.tasks.ReflectGroupSyncUpdateTask
import ch.threema.app.tasks.archive.recovery.handlers.ReflectNotificationTriggerPolicyOverrideUpdateRecoveryHandler
import ch.threema.data.datatypes.GroupIdentity
import ch.threema.data.models.GroupModel
import ch.threema.data.repositories.GroupModelRepository
import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.serialization.json.Json
import org.junit.Rule
import org.koin.dsl.module
import org.koin.test.KoinTestRule

class ReflectNotificationTriggerPolicyOverrideUpdateRecoveryHandlerTest {
    private val oldMutedIndefiniteTask =
        "{\"type\":\"ch.threema.app.tasks.ReflectGroupSyncUpdateTask.ReflectNotificationTriggerPolicyOverrideUpdate.ReflectNotificationTriggerPolicyOverrideUpdateData\",\"newNotificationTriggerPolicyOverride\":{\"type\":\"ch.threema.data.datatypes.NotificationTriggerPolicyOverride.MutedIndefinite\"},\"groupIdentity\":{\"creatorIdentity\":\"01234567\",\"groupId\":-5934730444204722858}}"
    private val newMutedIndefiniteTask =
        "{\"type\":\"ch.threema.app.tasks.ReflectGroupSyncUpdateTask.ReflectNotificationTriggerPolicyOverrideUpdate.ReflectNotificationTriggerPolicyOverrideUpdateDataV2\",\"notificationTriggerPolicyOverridePolicy\":1,\"notificationTriggerPolicyOverrideExpiresAt\":null,\"groupIdentity\":{\"creatorIdentity\":\"01234567\",\"groupId\":-5934730444204722858}}"
    private val oldNotMutedTask =
        "{\"type\":\"ch.threema.app.tasks.ReflectGroupSyncUpdateTask.ReflectNotificationTriggerPolicyOverrideUpdate.ReflectNotificationTriggerPolicyOverrideUpdateData\",\"newNotificationTriggerPolicyOverride\":{\"type\":\"ch.threema.data.datatypes.NotificationTriggerPolicyOverride.NotMuted\"},\"groupIdentity\":{\"creatorIdentity\":\"01234567\",\"groupId\":-5934730444204722858}}"
    private val newNotMutedTask =
        "{\"type\":\"ch.threema.app.tasks.ReflectGroupSyncUpdateTask.ReflectNotificationTriggerPolicyOverrideUpdate.ReflectNotificationTriggerPolicyOverrideUpdateDataV2\",\"notificationTriggerPolicyOverridePolicy\":null,\"notificationTriggerPolicyOverrideExpiresAt\":null,\"groupIdentity\":{\"creatorIdentity\":\"01234567\",\"groupId\":-5934730444204722858}}"
    private val oldMutedIndefiniteExceptMentionsTask =
        "{\"type\":\"ch.threema.app.tasks.ReflectGroupSyncUpdateTask.ReflectNotificationTriggerPolicyOverrideUpdate.ReflectNotificationTriggerPolicyOverrideUpdateData\",\"newNotificationTriggerPolicyOverride\":{\"type\":\"ch.threema.data.datatypes.NotificationTriggerPolicyOverride.MutedIndefiniteExceptMentions\"},\"groupIdentity\":{\"creatorIdentity\":\"01234567\",\"groupId\":-5934730444204722858}}"
    private val newMutedIndefiniteExceptMentionsTask =
        "{\"type\":\"ch.threema.app.tasks.ReflectGroupSyncUpdateTask.ReflectNotificationTriggerPolicyOverrideUpdate.ReflectNotificationTriggerPolicyOverrideUpdateDataV2\",\"notificationTriggerPolicyOverridePolicy\":0,\"notificationTriggerPolicyOverrideExpiresAt\":null,\"groupIdentity\":{\"creatorIdentity\":\"01234567\",\"groupId\":-5934730444204722858}}"
    private val oldMutedUntilTask =
        "{\"type\":\"ch.threema.app.tasks.ReflectGroupSyncUpdateTask.ReflectNotificationTriggerPolicyOverrideUpdate.ReflectNotificationTriggerPolicyOverrideUpdateData\",\"newNotificationTriggerPolicyOverride\":{\"type\":\"ch.threema.data.datatypes.NotificationTriggerPolicyOverride.MutedUntil\",\"dbValue\":1774242424242,\"utcMillis\":1774242424242},\"groupIdentity\":{\"creatorIdentity\":\"01234567\",\"groupId\":-5934730444204722858}}"
    private val newMutedUntilTask =
        "{\"type\":\"ch.threema.app.tasks.ReflectGroupSyncUpdateTask.ReflectNotificationTriggerPolicyOverrideUpdate.ReflectNotificationTriggerPolicyOverrideUpdateDataV2\",\"notificationTriggerPolicyOverridePolicy\":1,\"notificationTriggerPolicyOverrideExpiresAt\":1774242424242,\"groupIdentity\":{\"creatorIdentity\":\"01234567\",\"groupId\":-5934730444204722858}}"
    private val invalidTask =
        "{\"type\":\"ch.threema.app.tasks.ReflectGroupSyncUpdateTask.ReflectNotificationTriggerPolicyOverrideUpdate.ReflectNotificationTriggerPolicyOverrideUpdateData\",\"newNotificationTriggerPolicyOverride\":{\"type\":\"Invalid\"},\"groupIdentity\":{\"creatorIdentity\":\"01234567\",\"groupId\":-5934730444204722858}}"

    private val groupIdentity = GroupIdentity(
        creatorIdentity = "01234567",
        groupId = -5934730444204722858,
    )

    private val groupModelRepositoryMock: GroupModelRepository = mockk {
        val groupModelMock: GroupModel = mockk()
        every { groupModelMock.groupIdentity } returns groupIdentity
        every { getByGroupIdentity(groupIdentity) } returns groupModelMock
    }

    @get:Rule
    val koinTestRule = KoinTestRule.create {
        modules(
            module {
                single<GroupModelRepository> { groupModelRepositoryMock }
            },
        )
    }

    @Test
    fun `old muted indefinite task can be restored`() {
        assertTaskRecovery(
            oldTaskSerialization = oldMutedIndefiniteTask,
            newTaskSerialization = newMutedIndefiniteTask,
        )
    }

    @Test
    fun `old not muted task can be restored`() {
        assertTaskRecovery(
            oldTaskSerialization = oldNotMutedTask,
            newTaskSerialization = newNotMutedTask,
        )
    }

    @Test
    fun `old muted indefinite except mentions task can be restored`() {
        assertTaskRecovery(
            oldTaskSerialization = oldMutedIndefiniteExceptMentionsTask,
            newTaskSerialization = newMutedIndefiniteExceptMentionsTask,
        )
    }

    @Test
    fun `old muted until task can be restored`() {
        assertTaskRecovery(
            oldTaskSerialization = oldMutedUntilTask,
            newTaskSerialization = newMutedUntilTask,
        )
    }

    @Test
    fun `invalid task cannot be restored`() {
        assertNull(ReflectNotificationTriggerPolicyOverrideUpdateRecoveryHandler.tryRecovery(invalidTask))
    }

    private fun assertTaskRecovery(oldTaskSerialization: String, newTaskSerialization: String) {
        val task = ReflectNotificationTriggerPolicyOverrideUpdateRecoveryHandler.tryRecovery(oldTaskSerialization)

        assertNotNull(task)
        assertIs<ReflectGroupSyncUpdateTask.ReflectNotificationTriggerPolicyOverrideUpdate>(task)
        assertEquals(newTaskSerialization, Json.encodeToString(task.serialize()))
    }
}
