package ch.threema.app.activities

import ch.threema.app.services.ActivityService
import org.koin.android.ext.android.inject

abstract class ThreemaActivity : ThreemaAppCompatActivity() {
    private val activityService: ActivityService by inject()

    override fun onPause() {
        super.onPause()
        activityService.pause(this)
    }

    override fun onResume() {
        activityService.resume(this)
        super.onResume()
    }

    override fun onDestroy() {
        activityService.destroy(this)
        super.onDestroy()
    }

    override fun onUserInteraction() {
        activityService.userInteract(this)
        super.onUserInteraction()
    }

    companion object {
        const val ACTIVITY_ID_COMPOSE_MESSAGE = 20003
        const val ACTIVITY_ID_CONTACT_DETAIL = 20007
        const val ACTIVITY_ID_PICK_CAMERA_EXTERNAL = 20011
        const val ACTIVITY_ID_PICK_CAMERA_INTERNAL = 20012
        const val ACTIVITY_ID_RESTORE_KEY = 20016
        const val ACTIVITY_ID_SEND_MEDIA = 20019
        const val ACTIVITY_ID_ATTACH_MEDIA = 20020
        const val ACTIVITY_ID_GROUP_ADD = 20028
        const val ACTIVITY_ID_GROUP_DETAIL = 20029
        const val ACTIVITY_ID_MEDIA_VIEWER = 20035
        const val ACTIVITY_ID_CREATE_POLL = 20037
        const val ACTIVITY_ID_BACKUP_PICKER = 20042
        const val ACTIVITY_ID_COPY_POLL = 20043
        const val ACTIVITY_ID_PAINT = 20049
        const val ACTIVITY_ID_PICK_MEDIA = 20050
        const val ACTIVITY_ID_CROP_IMAGE = 20051
    }
}
