package ch.threema.app.tasks.archive.recovery.handlers

import ch.threema.app.tasks.ReflectGroupSyncUpdateTask
import ch.threema.app.tasks.archive.recovery.TaskRecoveryHandler
import ch.threema.base.utils.getThreemaLogger
import ch.threema.data.datatypes.GroupIdentity
import ch.threema.data.datatypes.GroupNotificationTriggerPolicyOverride
import ch.threema.data.datatypes.GroupNotificationTriggerPolicyOverridePolicy
import ch.threema.domain.taskmanager.Task
import ch.threema.domain.taskmanager.TaskCodec
import java.time.Instant
import org.json.JSONException
import org.json.JSONObject

private val logger = getThreemaLogger("ReflectNotificationTriggerPolicyOverrideUpdateRecoveryHandler")

object ReflectNotificationTriggerPolicyOverrideUpdateRecoveryHandler : TaskRecoveryHandler {
    private const val TARGET_TASK_TYPE =
        "ch.threema.app.tasks.ReflectGroupSyncUpdateTask.ReflectNotificationTriggerPolicyOverrideUpdate." +
            "ReflectNotificationTriggerPolicyOverrideUpdateData"

    override fun tryRecovery(encodedTask: String): Task<*, TaskCodec>? {
        try {
            val jsonObject = JSONObject(encodedTask)
            val type = jsonObject.getString("type")
            if (type != TARGET_TASK_TYPE) {
                return null
            }

            logger.info("Restoring notification trigger policy override update task")

            val groupIdentity = jsonObject.getGroupIdentityOrThrow()
            val groupNotificationTriggerPolicyOverride = jsonObject.getNotificationTriggerPolicyOverrideOrThrow()

            return ReflectGroupSyncUpdateTask.ReflectNotificationTriggerPolicyOverrideUpdate(
                newNotificationTriggerPolicyOverride = groupNotificationTriggerPolicyOverride,
                groupIdentity = groupIdentity,
            )
        } catch (e: JSONException) {
            logger.error("Could not restore task", e)
            return null
        }
    }

    private fun JSONObject.getGroupIdentityOrThrow(): GroupIdentity {
        getJSONObject("groupIdentity").let { groupIdentityJson ->
            val creatorIdentity = groupIdentityJson.getString("creatorIdentity")
            val groupId = groupIdentityJson.getLong("groupId")
            return GroupIdentity(
                creatorIdentity = creatorIdentity,
                groupId = groupId,
            )
        }
    }

    private fun JSONObject.getNotificationTriggerPolicyOverrideOrThrow(): GroupNotificationTriggerPolicyOverride? {
        getJSONObject("newNotificationTriggerPolicyOverride").apply {
            getMutedIndefiniteOrNull()?.let { return it }
            getMutedIndefiniteExceptMentions()?.let { return it }
            getMutedUntil()?.let { return it }
            if (isNotMuted()) {
                // In case it is not muted, then we don't need to return any notification trigger policy override
                return null
            }

            throw JSONException("Did not find any variant of the notification trigger policy")
        }
    }

    private fun JSONObject.getMutedIndefiniteOrNull(): GroupNotificationTriggerPolicyOverride? {
        if (getString("type") == "ch.threema.data.datatypes.NotificationTriggerPolicyOverride.MutedIndefinite") {
            return GroupNotificationTriggerPolicyOverride(
                policy = GroupNotificationTriggerPolicyOverridePolicy.NEVER,
                expiresAt = null,
            )
        }
        return null
    }

    private fun JSONObject.isNotMuted(): Boolean =
        getString("type") == "ch.threema.data.datatypes.NotificationTriggerPolicyOverride.NotMuted"

    private fun JSONObject.getMutedIndefiniteExceptMentions(): GroupNotificationTriggerPolicyOverride? {
        if (getString("type") == "ch.threema.data.datatypes.NotificationTriggerPolicyOverride.MutedIndefiniteExceptMentions") {
            return GroupNotificationTriggerPolicyOverride(
                policy = GroupNotificationTriggerPolicyOverridePolicy.MENTIONED,
                // Note: This task type did not support expiration of the MENTIONED-policy.
                expiresAt = null,
            )
        }
        return null
    }

    private fun JSONObject.getMutedUntil(): GroupNotificationTriggerPolicyOverride? {
        if (getString("type") == "ch.threema.data.datatypes.NotificationTriggerPolicyOverride.MutedUntil") {
            val utcMillis = getLong("utcMillis")
            return GroupNotificationTriggerPolicyOverride(
                policy = GroupNotificationTriggerPolicyOverridePolicy.NEVER,
                expiresAt = Instant.ofEpochMilli(utcMillis),
            )
        }
        return null
    }
}
