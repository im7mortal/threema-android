package ch.threema.app.activities.wizard;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;

import com.google.android.material.textfield.TextInputLayout;

import org.koin.java.KoinJavaComponent;
import org.slf4j.Logger;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import ch.threema.android.LifecycleAwareAsyncTask;
import ch.threema.android.textwatchers.Base32InputSanitizer;
import ch.threema.app.AppConstants;
import ch.threema.app.R;
import ch.threema.app.activities.ThreemaAppCompatActivity;
import ch.threema.app.ui.interop.ButtonPrimaryXml;
import ch.threema.app.camera.QRScannerActivity;
import ch.threema.app.di.DependencyContainer;
import ch.threema.app.dialogs.GenericProgressDialog;
import ch.threema.app.dialogs.SimpleStringAlertDialog;
import ch.threema.app.ui.InsetSides;
import ch.threema.android.textwatchers.SimpleTextWatcher;
import ch.threema.app.ui.SpacingValues;
import ch.threema.app.ui.ViewExtensionsKt;
import ch.threema.app.usecases.OverrideOneTimeHintsUseCase;
import ch.threema.app.utils.ConfigUtils;
import ch.threema.app.utils.DialogUtil;
import ch.threema.app.utils.EditTextUtil;

import static ch.threema.android.ToastKt.showToast;
import static ch.threema.base.utils.LoggingKt.getThreemaLogger;

import ch.threema.domain.protocol.api.FetchIdentityException;
import ch.threema.domain.protocol.connection.ServerConnection;

import static ch.threema.app.di.DIJavaCompat.isSessionScopeReady;
import static ch.threema.app.utils.ActiveScreenLoggerKt.logScreenVisibility;

public class WizardIDRestoreActivity extends ThreemaAppCompatActivity {
    private static final Logger logger = getThreemaLogger("WizardIDRestoreActivity");
    private static final String DIALOG_TAG_RESTORE_PROGRESS = "rp";
    private static final int PERMISSION_REQUEST_CAMERA = 1;

    /**
     * extremely ancient versions of the app on some platform accepted four-letter passwords when generating ID exports
     */
    private static final int MIN_PW_LENGTH_ID_EXPORT_LEGACY = 4;

    private EditText backupIdText;
    private EditText passwordEditText;
    private boolean passwordOK = false;
    private boolean idOK = false;
    private ButtonPrimaryXml nextButtonCompose;
    private final int BACKUP_V1_STRING_LENGTH = 99;
    private final int BACKUP_V2_STRING_LENGTH = 129;

    private static final int REQUEST_CODE_QR_SCANNER = 26657;

    @NonNull
    private final DependencyContainer dependencies = KoinJavaComponent.get(DependencyContainer.class);

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        logScreenVisibility(this, logger);

        if (!isSessionScopeReady()) {
            finish();
            return;
        }

        setContentView(R.layout.activity_wizard_restore_id);

        ViewExtensionsKt.applyDeviceInsetsAsPadding(
            findViewById(R.id.content),
            InsetSides.all(),
            SpacingValues.symmetric(
                R.dimen.wizard_contents_padding,
                R.dimen.wizard_contents_padding_horizontal
            ),
            /* includingIme */
            true
        );

        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);

        backupIdText = findViewById(R.id.id_export);
        backupIdText.setImeOptions(EditorInfo.IME_ACTION_SEND);
        backupIdText.setRawInputType(InputType.TYPE_CLASS_TEXT | EditorInfo.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        backupIdText.setFilters(
            new InputFilter[]{
                new InputFilter.LengthFilter(129),
                new InputFilter.AllCaps(),
            }
        );
        backupIdText.addTextChangedListener(new Base32InputSanitizer());
        backupIdText.addTextChangedListener(new SimpleTextWatcher() {
            @Override
            public void afterTextChanged(@NonNull Editable editable) {
                int length = editable.toString().length();
                idOK = length == BACKUP_V1_STRING_LENGTH || length == BACKUP_V2_STRING_LENGTH;
                setRestoreButtonEnabled(idOK && passwordOK);
            }
        });

        passwordEditText = findViewById(R.id.password);
        passwordEditText.addTextChangedListener(new SimpleTextWatcher() {
            @Override
            public void afterTextChanged(@NonNull Editable editable) {
                passwordOK = editable.length() >= MIN_PW_LENGTH_ID_EXPORT_LEGACY;
                setRestoreButtonEnabled(idOK && passwordOK);
            }
        });

        findViewById(R.id.wizard_cancel_compose).setOnClickListener(v -> finish());

        nextButtonCompose = findViewById(R.id.wizard_finish_compose);
        nextButtonCompose.setOnClickListener(v -> restoreID());
        setRestoreButtonEnabled(false);

        final @NonNull TextInputLayout idExportLayout = findViewById(R.id.id_export_layout);
        idExportLayout.setEndIconOnClickListener(v -> {
            if (ConfigUtils.requestCameraPermissions(WizardIDRestoreActivity.this, null, PERMISSION_REQUEST_CAMERA)) {
                scanQR();
            }
        });

        Intent intent = getIntent();
        if (intent.hasExtra(AppConstants.INTENT_DATA_ID_BACKUP) &&
            intent.hasExtra(AppConstants.INTENT_DATA_ID_BACKUP_PW)) {
            backupIdText.setText(intent.getStringExtra(AppConstants.INTENT_DATA_ID_BACKUP));
            passwordEditText.setText(intent.getStringExtra(AppConstants.INTENT_DATA_ID_BACKUP_PW));
            restoreID();
        }
    }

    private void setRestoreButtonEnabled(final boolean isEnabled) {
        if (nextButtonCompose != null) {
            nextButtonCompose.setButtonEnabled(isEnabled);
        }
    }

    public void scanQR() {
        var intent = QRScannerActivity.createIntent(this);
        startActivityForResult(intent, REQUEST_CODE_QR_SCANNER);
    }

    @SuppressLint("StaticFieldLeak")
    public void restoreID() {
        EditTextUtil.hideSoftKeyboard(backupIdText);
        EditTextUtil.hideSoftKeyboard(passwordEditText);

        new LifecycleAwareAsyncTask<Void, RestoreResult>() {
            String password, backupString;

            @Override
            protected void onPreExecute() {
                GenericProgressDialog.newInstance(R.string.restoring_backup, R.string.please_wait).show(getSupportFragmentManager(), DIALOG_TAG_RESTORE_PROGRESS);
                password = passwordEditText.getText().toString();
                backupString = backupIdText.getText().toString();
            }

            @Override
            protected RestoreResult doInBackground(Void params) {
                try {
                    ServerConnection connection = dependencies.getServerConnection();
                    if (connection.isRunning()) {
                        connection.stop();
                    }
                    if (dependencies.getUserService().restoreIdentity(backupString, password)) {
                        return RestoreResult.success();
                    }
                } catch (InterruptedException e) {
                    logger.error("Interrupted", e);
                    cancel();
                } catch (FetchIdentityException e) {
                    return RestoreResult.failure(e.getMessage());
                } catch (Exception e) {
                    logger.error("Exception", e);
                }
                return RestoreResult.failure(getString(R.string.wrong_backupid_or_password_or_no_internet_connection));
            }

            @Override
            protected void onPostExecute(RestoreResult result) {
                DialogUtil.dismissDialog(getSupportFragmentManager(), DIALOG_TAG_RESTORE_PROGRESS, true);

                if (result.isSuccess()) {
                    OverrideOneTimeHintsUseCase overrideOneTimeHintsUseCase = KoinJavaComponent.get(OverrideOneTimeHintsUseCase.class);
                    overrideOneTimeHintsUseCase.call();

                    // ID successfully restored from ID backup - cancel reminder
                    dependencies.getPreferenceService().incrementIDBackupCount();
                    setResult(RESULT_OK);
                    finish();
                } else {
                    getSupportFragmentManager().beginTransaction().add(SimpleStringAlertDialog.newInstance(R.string.error, result.getErrorMessage()), "er").commitAllowingStateLoss();
                }
            }
        }.execute(this, null);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
        if (requestCode == REQUEST_CODE_QR_SCANNER && resultCode == RESULT_OK) {
            String scanResult = QRScannerActivity.extractResult(intent);
            if (scanResult != null) {
                int scanResultLength = scanResult.length();
                if (scanResultLength == BACKUP_V1_STRING_LENGTH || scanResultLength == BACKUP_V2_STRING_LENGTH) {
                    backupIdText.setText(scanResult);
                    backupIdText.invalidate();
                } else {
                    showToast(this, R.string.invalid_threema_qr_code);
                    logger.error("Invalid Threema QR code");
                }
            }
        }
        super.onActivityResult(requestCode, resultCode, intent);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CAMERA) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                scanQR();
            } else if (!shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
                ConfigUtils.showPermissionRationale(this, findViewById(R.id.top_view), R.string.permission_camera_qr_required);
            }
        }
    }

    private static class RestoreResult {
        private final @Nullable String errorMessage;

        public static RestoreResult success() {
            return new RestoreResult(null);
        }

        public static RestoreResult failure(@Nullable String errorMessage) {
            return new RestoreResult(errorMessage);
        }

        private RestoreResult(@Nullable String errorMessage) {
            this.errorMessage = errorMessage;
        }

        public boolean isSuccess() {
            return errorMessage == null;
        }

        @Nullable
        public String getErrorMessage() {
            return errorMessage;
        }
    }
}
