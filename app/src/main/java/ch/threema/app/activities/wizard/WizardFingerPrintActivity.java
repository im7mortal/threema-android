package ch.threema.app.activities.wizard;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.koin.java.KoinJavaComponent;
import org.slf4j.Logger;

import java.time.Instant;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import ch.threema.android.LifecycleAwareAsyncTask;
import ch.threema.app.R;
import ch.threema.app.activities.ThreemaAppCompatActivity;
import ch.threema.app.di.DependencyContainer;
import ch.threema.app.dialogs.GenericAlertDialog;
import ch.threema.app.dialogs.GenericProgressDialog;
import ch.threema.app.dialogs.WizardDialog;
import ch.threema.app.licensing.StoreLicenseCheck;
import ch.threema.app.ui.InsetSides;
import ch.threema.app.ui.NewWizardFingerPrintView;
import ch.threema.app.ui.SpacingValues;
import ch.threema.app.ui.ViewExtensionsKt;
import ch.threema.app.utils.DialogUtil;
import ch.threema.base.ThreemaException;

import static ch.threema.base.utils.LoggingKt.getThreemaLogger;

import static ch.threema.app.di.DIJavaCompat.isSessionScopeReady;
import static ch.threema.app.utils.ActiveScreenLoggerKt.logScreenVisibility;
import static ch.threema.common.JavaCompat.isNullOrEmpty;

public class WizardFingerPrintActivity extends ThreemaAppCompatActivity
    implements WizardDialog.WizardDialogCallback, GenericAlertDialog.DialogClickListener {

    private static final Logger logger = getThreemaLogger("WizardFingerPrintActivity");

    public static final int PROGRESS_MAX = 100;
    private static final String DIALOG_TAG_CREATE_ID = "ci";
    private static final String DIALOG_TAG_CREATE_ERROR = "ni";
    private static final String DIALOG_TAG_FINGERPRINT_INFO = "fi";
    private ProgressBar swipeProgress;
    private ImageView fingerView;

    @NonNull
    private final DependencyContainer dependencies = KoinJavaComponent.get(DependencyContainer.class);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        logScreenVisibility(this, logger);

        if (!isSessionScopeReady()) {
            finish();
            return;
        }

        setContentView(R.layout.activity_new_fingerprint);

        ViewExtensionsKt.applyDeviceInsetsAsPadding(
            findViewById(R.id.new_fingerprint_content),
            InsetSides.all(),
            SpacingValues.all(R.dimen.grid_unit_x2)
        );

        swipeProgress = findViewById(R.id.wizard1_swipe_progress);
        swipeProgress.setMax(PROGRESS_MAX);
        swipeProgress.setProgress(0);

        fingerView = findViewById(R.id.finger_overlay);
        findViewById(R.id.wizard_icon_info).setOnClickListener(v -> {
            final Dialog infoDialog = new MaterialAlertDialogBuilder(this)
                .setMessage(R.string.new_wizard_info_fingerprint)
                .setPositiveButton(
                    R.string.ok,
                    (dialog, b) -> dialog.dismiss()
                )
                .create();
            infoDialog.show();
        });

        ((NewWizardFingerPrintView) findViewById(R.id.wizard1_finger_print))
            .setOnSwipeByte((bytes, step, maxSteps) -> {
                swipeProgress.setProgress(step);

                if (fingerView != null) {
                    fingerView.setVisibility(View.GONE);
                    fingerView = null;
                }

                if (step >= maxSteps) {
                    // disable fingerprint widget
                    findViewById(R.id.wizard1_finger_print).setEnabled(false);
                    // generate id and stuff
                    createIdentity(bytes);
                }
            }, PROGRESS_MAX);

        findViewById(R.id.cancel_compose).setOnClickListener(v -> finish());
    }

    @SuppressLint("StaticFieldLeak")
    private void createIdentity(final byte[] bytes) {
        new LifecycleAwareAsyncTask<Void, String>() {
            @Override
            protected void onPreExecute() {
                GenericProgressDialog.newInstance(R.string.wizard_first_create_id,
                    R.string.please_wait).show(getSupportFragmentManager(), DIALOG_TAG_CREATE_ID);
            }

            @Override
            protected String doInBackground(Void params) {
                try {
                    if (!dependencies.getUserService().hasIdentity()) {
                        dependencies.getUserService().createIdentity(bytes);
                        dependencies.getPreferenceService().resetIDBackupCount();
                        dependencies.getPreferenceService().setLastIDBackupReminderTimestamp(Instant.now());
                        dependencies.getNotificationPreferenceService().setWizardRunning(true);
                    }
                } catch (final ThreemaException e) {
                    logger.error("Exception", e);
                    return e.getMessage();
                } catch (final Exception e) {
                    logger.error("Exception", e);
                    return getString(R.string.new_wizard_need_internet);
                }
                return null;
            }

            @Override
            protected void onPostExecute(String errorString) {
                DialogUtil.dismissDialog(getSupportFragmentManager(), DIALOG_TAG_CREATE_ID, true);

                if (isNullOrEmpty(errorString)) {
                    Intent intent = new Intent(WizardFingerPrintActivity.this, WizardBaseActivity.class);
                    intent.putExtra(WizardBaseActivity.EXTRA_NEW_IDENTITY_CREATED, true);
                    startActivity(intent);

                    overridePendingTransition(R.anim.abc_fade_in, R.anim.abc_fade_out);
                    finish();
                } else {
                    try {
                        dependencies.getUserService().removeIdentity();
                    } catch (Exception e) {
                        logger.error("Exception", e);
                    }
                    GenericAlertDialog dialog = GenericAlertDialog.newInstance(
                        R.string.error,
                        errorString,
                        R.string.try_again,
                        R.string.cancel);
                    dialog.setData(bytes);
                    getSupportFragmentManager().beginTransaction().add(dialog, DIALOG_TAG_CREATE_ERROR).commitAllowingStateLoss();
                }
            }
        }.execute(this, null);
    }

    @Override
    public void onYes(@Nullable String tag, @Nullable Object data) {
        if (tag != null && tag.equals(DIALOG_TAG_CREATE_ERROR)) {
            // check again for a valid license and try to create identity
            StoreLicenseCheck.checkLicense(this, dependencies.getUserService());
            createIdentity((byte[]) data);
        }
    }

    @Override
    public void onNo(@Nullable String tag, @Nullable Object data) {
        finish();
    }

    @Override
    public void onNo(String tag) {
    }
}
