package ch.threema.app.activities;

import android.content.Intent;

import org.koin.java.KoinJavaComponent;

import androidx.annotation.NonNull;
import ch.threema.app.backuprestore.csv.BackupService;
import ch.threema.app.backuprestore.csv.RestoreService;
import ch.threema.app.services.ActivityService;

public abstract class ThreemaActivity extends ThreemaAppCompatActivity {

    final static public int ACTIVITY_ID_COMPOSE_MESSAGE = 20003;
    final static public int ACTIVITY_ID_ADD_CONTACT = 20004;
    final static public int ACTIVITY_ID_CONTACT_DETAIL = 20007;
    final static public int ACTIVITY_ID_PICK_CAMERA_EXTERNAL = 20011;
    final static public int ACTIVITY_ID_PICK_CAMERA_INTERNAL = 20012;
    final static public int ACTIVITY_ID_RESTORE_KEY = 20016;
    final static public int ACTIVITY_ID_SEND_MEDIA = 20019;
    final static public int ACTIVITY_ID_ATTACH_MEDIA = 20020;
    final static public int ACTIVITY_ID_GROUP_ADD = 20028;
    final static public int ACTIVITY_ID_GROUP_DETAIL = 20029;
    final static public int ACTIVITY_ID_MEDIA_VIEWER = 20035;
    public static final int ACTIVITY_ID_CREATE_POLL = 20037;
    final static public int ACTIVITY_ID_BACKUP_PICKER = 20042;
    final static public int ACTIVITY_ID_COPY_POLL = 20043;
    public static final int ACTIVITY_ID_PAINT = 20049;
    public static final int ACTIVITY_ID_PICK_MEDIA = 20050;
    public static final int ACTIVITY_ID_CROP_IMAGE = 20051;

    @NonNull
    private final ActivityService activityService = KoinJavaComponent.get(ActivityService.class);

    @Override
    protected void onPause() {
        super.onPause();
        if (isPinLockable()) {
            activityService.pause(this);
        }
    }

    @Override
    protected void onResume() {
        if (isPinLockable()) {
            activityService.resume(this);
        }

        if (BackupService.isRunning() || RestoreService.isRunning()) {
            Intent intent = BackupRestoreProgressActivity.createIntent(this);
            startActivity(intent);
            finish();
        }

        super.onResume();
    }

    @Override
    protected void onDestroy() {
        if (isPinLockable()) {
            activityService.destroy(this);
        }

        super.onDestroy();
    }

    @Override
    public void onUserInteraction() {
        if (isPinLockable()) {
            activityService.userInteract(this);
        }
        super.onUserInteraction();
    }

    protected boolean isPinLockable() {
        return true;
    }
}
