package ch.threema.app.activities.wizard;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Bundle;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.view.View;
import android.widget.TextView;

import org.koin.java.KoinJavaComponent;
import org.slf4j.Logger;

import java.io.File;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.core.content.ContextCompat;
import ch.threema.android.permissions.NotificationPermissionContract;
import ch.threema.app.AppConstants;
import ch.threema.app.BuildConfig;
import ch.threema.app.R;
import ch.threema.app.activities.DisableBatteryOptimizationsActivity;
import ch.threema.app.activities.ThreemaActivity;
import ch.threema.app.backuprestore.csv.RestoreService;
import ch.threema.app.ui.interop.ButtonPrimaryXml;
import ch.threema.app.backuprestore.libthreema.BackupRestoreProgressActivity;
import ch.threema.app.backuprestore.libthreema.BackupRestoreService;
import ch.threema.app.di.DependencyContainer;
import ch.threema.app.dialogs.GenericAlertDialog;
import ch.threema.app.dialogs.GenericProgressDialog;
import ch.threema.app.dialogs.PasswordEntryDialog;
import ch.threema.app.dialogs.SimpleStringAlertDialog;
import ch.threema.app.files.TempFilesCleanupWorker;
import ch.threema.app.ui.InsetSides;
import ch.threema.app.ui.SpacingValues;
import ch.threema.app.ui.ViewExtensionsKt;
import ch.threema.app.utils.DialogUtil;
import ch.threema.app.utils.FileUtil;
import ch.threema.app.utils.LocaleUtil;
import ch.threema.app.utils.MimeUtil;
import ch.threema.app.utils.RuntimeUtil;
import kotlin.Lazy;
import kotlin.Unit;

import static ch.threema.android.ToastKt.showToast;
import static ch.threema.android.UriExtensionsKt.isFileUri;
import static ch.threema.base.utils.LoggingKt.getThreemaLogger;

import static ch.threema.app.di.DIJavaCompat.isSessionScopeReady;
import static ch.threema.app.utils.ActiveScreenLoggerKt.logScreenVisibility;
import static ch.threema.common.JavaCompat.isNullOrEmpty;

public class WizardBackupRestoreActivity extends ThreemaActivity implements GenericAlertDialog.DialogClickListener,
    PasswordEntryDialog.PasswordEntryDialogClickListener {
    private static final Logger logger = getThreemaLogger("WizardBackupRestoreActivity");

    private static final String DIALOG_TAG_DISABLE_ENERGYSAVE_CONFIRM = "de";
    private static final String DIALOG_TAG_DOWNLOADING_BACKUP = "dwnldBkp";
    private static final String DIALOG_TAG_NO_INTERNET = "nin";
    private static final String DIALOG_TAG_ERROR_TMP_FILE_DIR = "tmpFileDialog";

    public static final int REQUEST_ID_DISABLE_BATTERY_OPTIMIZATIONS = 541;

    @NonNull
    private final DependencyContainer dependencies = KoinJavaComponent.get(DependencyContainer.class);

    @NonNull
    private final Lazy<BackupRestoreService.Scheduler> restoreScheduler = KoinJavaComponent
        .inject(BackupRestoreService.Scheduler.class);

    private File backupFile;
    private String backupPassword;

    private final ActivityResultLauncher<Unit> permissionLauncher = registerForActivityResult(new NotificationPermissionContract(), isGranted -> {
        if (isGranted) {
            logger.info("Notification permission granted, starting restore");
        } else {
            logger.info("Notification permission not granted, starting restore anyway");
        }
        // Restore backup even if permission is not granted as we do not strictly require the
        // notification permission.
        startRestore();
    });

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        logScreenVisibility(this, logger);

        if (!isSessionScopeReady()) {
            finish();
            return;
        }

        // directly forward to ID restore activity
        Intent intent = getIntent();
        if (intent.hasExtra(AppConstants.INTENT_DATA_ID_BACKUP) &&
            intent.hasExtra(AppConstants.INTENT_DATA_ID_BACKUP_PW)) {

            restoreIDExport(intent.getStringExtra(AppConstants.INTENT_DATA_ID_BACKUP),
                intent.getStringExtra(AppConstants.INTENT_DATA_ID_BACKUP_PW));
        }

        initLayout();

        // Clean the temp directory to ensure that any backup files
        // from previous restore attempts are deleted.
        TempFilesCleanupWorker.Scheduler scheduler = KoinJavaComponent.get(TempFilesCleanupWorker.Scheduler.class);
        scheduler.scheduleImmediateFullCleanup();
    }

    private void initLayout() {
        setContentView(R.layout.activity_backup_restore);

        ViewExtensionsKt.applyDeviceInsetsAsPadding(
            findViewById(R.id.content),
            InsetSides.all(),
            SpacingValues.symmetric(R.dimen.wizard_contents_padding, R.dimen.wizard_contents_padding_horizontal)
        );

        String faqURL = String.format(getString(R.string.backup_faq_url), LocaleUtil.getAppLanguage());
        TextView backupSubtitle = findViewById(R.id.backup_restore_subtitle);
        backupSubtitle.setText(Html.fromHtml(
            String.format(getString(R.string.backup_restore_type), faqURL))
        );
        backupSubtitle.setMovementMethod(LinkMovementMethod.getInstance());

        final @NonNull ButtonPrimaryXml safeBackupButtonCompose = findViewById(R.id.safe_backup_compose);
        if (dependencies.getAppRestrictions().isRestricted() && dependencies.getThreemaSafeMDMConfig().isRestoreDisabled()) {
            safeBackupButtonCompose.setVisibility(View.GONE);
        } else {
            safeBackupButtonCompose.setOnClickListener(v -> {
                logger.info("Threema Safe Backup clicked");
                restoreSafe();
            });
        }
        findViewById(R.id.data_backup_compose).setOnClickListener(v -> {
            logger.info("Data Backup clicked");
            showDisableEnergySaveDialog();
        });
        findViewById(R.id.id_backup_compose).setOnClickListener(v -> {
            logger.info("Exported-ID clicked");
            restoreIDExport(null, null);
        });
        findViewById(R.id.cancel_compose).setOnClickListener(v -> {
            logger.info("Cancel clicked");
            finish();
        });
    }

    private void restoreSafe() {
        startActivity(WizardSafeRestoreActivity.createIntent(this));
        overridePendingTransition(R.anim.abc_fade_in, R.anim.abc_fade_out);
    }

    private void restoreIDExport(String backupString, String backupPassword) {
        Intent intent = new Intent(this, WizardIDRestoreActivity.class);

        if (!isNullOrEmpty(backupString) && !isNullOrEmpty(backupPassword)) {
            intent.putExtra(AppConstants.INTENT_DATA_ID_BACKUP, backupString);
            intent.putExtra(AppConstants.INTENT_DATA_ID_BACKUP_PW, backupPassword);
        }
        startActivityForResult(intent, ThreemaActivity.ACTIVITY_ID_RESTORE_KEY);
        overridePendingTransition(R.anim.abc_fade_in, R.anim.abc_fade_out);
    }

    private void restoreBackup(final Uri uri) {
        if (!isFileUri(uri)) {
            // copy "file" to cache directory first
            GenericProgressDialog.newInstance(R.string.importing_files, R.string.please_wait).show(getSupportFragmentManager(), DIALOG_TAG_DOWNLOADING_BACKUP);

            new Thread(() -> {
                // TODO(ANDR-4977): Refactor this workaround to pass the correct mime type to the actual restoration
                String mimeType = FileUtil.getMimeTypeFromUri(this, uri);
                String suffix = MimeUtil.MIME_TYPE_ZIP.equals(mimeType)
                    ? ".zip"
                    : ".backup";

                final File file = dependencies.getFileService().copyUriToTempFile(uri, "backup_restore", suffix);

                RuntimeUtil.runOnUiThread(() -> {
                    DialogUtil.dismissDialog(getSupportFragmentManager(), DIALOG_TAG_DOWNLOADING_BACKUP);

                    if (file != null) {
                        logger.info("Backup file copied, starting restore");
                        restoreBackupFile(file);
                        file.deleteOnExit();
                    } else {
                        logger.warn("Failed to copy backup file");
                        SimpleStringAlertDialog.newInstance(R.string.an_error_occurred, R.string.missing_permission_external_storage).show(getSupportFragmentManager(), DIALOG_TAG_ERROR_TMP_FILE_DIR);
                    }
                });
            }).start();

        } else {
            String path = FileUtil.getRealPathFromURI(this, uri);
            if (path != null && !path.isEmpty()) {
                File file = new File(path);
                if (file.exists()) {
                    restoreBackupFile(file);
                }
            }
        }
    }

    private void restoreBackupFile(@NonNull File file) {
        if (file.exists()) {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);

            NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
            if (activeNetwork == null || !activeNetwork.isConnectedOrConnecting()) {
                showNoInternetDialog(file);
            } else {
                confirmRestore(file);
            }
            return;
        }
        showToast(this, R.string.invalid_backup);
        logger.error("Invalid backup");
    }

    private void showDisableEnergySaveDialog() {
        GenericAlertDialog.newInstance(R.string.menu_restore, R.string.restore_disable_energy_saving, R.string.ok, R.string.cancel).show(getSupportFragmentManager(), DIALOG_TAG_DISABLE_ENERGYSAVE_CONFIRM);
    }

    private void confirmRestore(File file) {
        PasswordEntryDialog dialogFragment = PasswordEntryDialog.newInstance(
            R.string.backup_data_title,
            R.string.restore_data_password_msg,
            R.string.password_hint,
            R.string.ok,
            R.string.cancel,
            AppConstants.MIN_PW_LENGTH_BACKUP,
            AppConstants.MAX_PW_LENGTH_BACKUP,
            0,
            0,
            0,
            PasswordEntryDialog.ForgotHintType.PIN_PASSPHRASE,
            null
        );
        dialogFragment.setData(file);
        dialogFragment.show(getSupportFragmentManager(), "restorePW");
    }

    private void startRestore() {
        File file = this.backupFile;
        String password = this.backupPassword;

        if (!BuildConfig.CROSS_PLATFORM_BACKUPS_ENABLED || isLegacyBackupFile(file)) {
            startLegacyRestore(file, password);
        } else {
            startBackupRestore(file, password);
        }
    }

    private boolean isLegacyBackupFile(@Nullable File file) {
        if (file == null) {
            logger.warn("Backup file is not set. Cannot check mime type");
            return false;
        }
        String mimeType = FileUtil.getMimeTypeFromPath(file.getAbsolutePath());
        logger.debug("Backup file mime type: {}", mimeType);
        return MimeUtil.isZipFile(mimeType);
    }

    private void startLegacyRestore(@NonNull File file, @NonNull String password) {
        logger.info("Restoring legacy backup");
        Intent intent = RestoreService.createIntent(this, file, password);
        ContextCompat.startForegroundService(this, intent);
        setResult(Activity.RESULT_OK);
        finish();
    }

    private void startBackupRestore(@NonNull File file, @NonNull String password) {
        logger.info("Restoring cross platform backup");
        try {
            restoreScheduler.getValue().start(
                file,
                password
            );
            setResult(Activity.RESULT_OK);
        } catch (Exception e) {
            logger.error("Could not start restore", e);
            setResult(Activity.RESULT_CANCELED);
        }

        Intent intent = BackupRestoreProgressActivity.createIntent(this);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);

        finish();
    }

    @UiThread
    private void showNoInternetDialog(File file) {
        logger.info("Showing no-internet dialog");
        GenericAlertDialog dialog = GenericAlertDialog.newInstance(R.string.menu_restore, R.string.new_wizard_need_internet, R.string.retry, R.string.cancel);
        dialog.setData(file);
        dialog.show(getSupportFragmentManager(), DIALOG_TAG_NO_INTERNET);
    }

    // start generic alert dialog callbacks
    @Override
    public void onYes(@Nullable String tag, @Nullable Object data) {
        if (tag == null) {
            return;
        }
        switch (tag) {
            case DIALOG_TAG_DISABLE_ENERGYSAVE_CONFIRM:
                logger.info("Showing disable-battery-optimizations settings");
                Intent intent = new Intent(this, DisableBatteryOptimizationsActivity.class);
                intent.putExtra(DisableBatteryOptimizationsActivity.EXTRA_NAME, getString(R.string.restore));
                startActivityForResult(intent, REQUEST_ID_DISABLE_BATTERY_OPTIMIZATIONS);
                break;
            case DIALOG_TAG_NO_INTERNET:
                if (data != null) {
                    restoreBackupFile((File) data);
                }
                break;
        }
    }

    @Override
    public void onNo(@Nullable String tag, @Nullable Object data) {
        if (dependencies.getThreemaSafeMDMConfig().isRestoreDisabled()) {
            finish();
        }
    }
    // end generic alert dialog callbacks

    // start password dialog callbacks
    @Override
    public void onYes(@Nullable String tag, @NonNull String text, boolean isChecked, Object data) {
        this.backupFile = (File) data;
        this.backupPassword = text;
        logger.info("Password was entered");
        permissionLauncher.launch(Unit.INSTANCE);
    }

    @Override
    public void onNo(@Nullable String tag) {
        if (dependencies.getThreemaSafeMDMConfig().isRestoreDisabled()) {
            finish();
        }
    }
    // end password dialog callbacks

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent resultData) {
        if (resultCode != RESULT_OK) {
            if (requestCode != REQUEST_ID_DISABLE_BATTERY_OPTIMIZATIONS && requestCode != ThreemaActivity.ACTIVITY_ID_BACKUP_PICKER) {
                if (dependencies.getThreemaSafeMDMConfig().isRestoreDisabled()) {
                    finish();
                }
            }
        }

        switch (requestCode) {
            case REQUEST_ID_DISABLE_BATTERY_OPTIMIZATIONS:
                logger.info("Opening restore file picker");
                String[] mimeTypes = BuildConfig.CROSS_PLATFORM_BACKUPS_ENABLED
                    ? new String[]{MimeUtil.MIME_TYPE_ZIP, MimeUtil.MIME_TYPE_DEFAULT}
                    : new String[]{MimeUtil.MIME_TYPE_ZIP};
                FileUtil.selectFile(
                    WizardBackupRestoreActivity.this,
                    null,
                    mimeTypes,
                    ThreemaActivity.ACTIVITY_ID_BACKUP_PICKER,
                    false,
                    0,
                    dependencies.getFileService().getDefaultLegacyBackupPath().getPath()
                );
                break;

            case ThreemaActivity.ACTIVITY_ID_RESTORE_KEY:
                if (resultCode == RESULT_OK) {
                    setResult(RESULT_OK);
                    startNextWizard();
                }
                break;

            case ThreemaActivity.ACTIVITY_ID_BACKUP_PICKER:
                if (resultCode == RESULT_OK) {
                    setResult(RESULT_OK);
                    if (resultData != null) {
                        Uri uri;

                        uri = resultData.getData();
                        if (uri != null) {
                            logger.info("Restore file selected, startup backup restore");
                            restoreBackup(uri);
                        }
                    }
                }
                break;
        }
        super.onActivityResult(requestCode, resultCode, resultData);
    }

    private void startNextWizard() {
        if (dependencies.getUserService().hasIdentity()) {
            logger.info("Starting wizard");
            dependencies.getNotificationPreferenceService().setWizardRunning(true);
            startActivity(new Intent(this, WizardBaseActivity.class));
            overridePendingTransition(R.anim.abc_fade_in, R.anim.abc_fade_out);
            finish();
        }
    }
}
