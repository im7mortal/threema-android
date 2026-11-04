package ch.threema.app.activities;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.method.LinkMovementMethod;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputLayout;

import org.koin.java.KoinJavaComponent;
import org.slf4j.Logger;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.text.HtmlCompat;
import ch.threema.android.LifecycleAwareAsyncTask;
import ch.threema.app.BuildConfig;
import ch.threema.app.BuildFlavor;
import ch.threema.app.R;
import ch.threema.app.license.ParameterSource;
import ch.threema.app.license.Parameters;
import ch.threema.app.license.PasswordParameter;
import ch.threema.app.license.SerialParameter;
import ch.threema.app.license.ServerUrlParameter;
import ch.threema.app.license.UsernameParameter;
import ch.threema.app.ui.interop.ButtonPrimaryXml;
import ch.threema.app.di.DependencyContainer;
import ch.threema.app.dialogs.GenericProgressDialog;
import ch.threema.app.restrictions.AppRestrictionService;
import ch.threema.app.services.license.LicenseService;
import ch.threema.app.services.license.LicenseServiceUser;
import ch.threema.domain.models.UserCredentials;
import ch.threema.app.ui.InsetSides;
import ch.threema.android.textwatchers.SimpleTextWatcher;
import ch.threema.app.ui.SpacingValues;
import ch.threema.app.ui.ViewExtensionsKt;
import ch.threema.app.utils.ConfigUtils;
import ch.threema.app.utils.DialogUtil;
import ch.threema.app.utils.EditTextUtil;
import ch.threema.app.utils.executor.BackgroundExecutor;
import ch.threema.app.utils.executor.BackgroundTask;
import static ch.threema.app.ui.EditTextExtensionsKt.getTextOrEmpty;
import static ch.threema.base.utils.LoggingKt.getThreemaLogger;
import kotlin.Lazy;

import static ch.threema.app.startup.AppStartupUtilKt.finishAndRestartLaterIfNotReady;
import static ch.threema.app.utils.ActiveScreenLoggerKt.logScreenVisibility;
import static ch.threema.common.JavaCompat.isNullOrBlank;
import static ch.threema.common.LazyKt.lazy;

// this should NOT extend ThreemaToolbarActivity
public class EnterSerialActivity extends ThreemaActivity {
    private static final Logger logger = getThreemaLogger("EnterSerialActivity");

    private static final String BUNDLE_PASSWORD = "bupw";
    private static final String BUNDLE_LICENSE_KEY = "bulk";
    private static final String BUNDLE_SERVER = "busv";
    private static final String DIALOG_TAG_CHECKING = "check";
    private TextView stateTextView = null;
    private EditText licenseKeyOrUsernameText, passwordText, serverText;
    private MaterialButton unlockButton;
    private ButtonPrimaryXml loginButtonCompose;

    @NonNull
    private final DependencyContainer dependencies = KoinJavaComponent.get(DependencyContainer.class);

    private final Lazy<BackgroundExecutor> backgroundExecutor = lazy(BackgroundExecutor::new);

    // We need to use getResources().getIdentifier(...) because of flavor specific layout files for this fragment
    @SuppressLint("DiscouragedApi")
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        logScreenVisibility(this, logger);
        if (finishAndRestartLaterIfNotReady(this)) {
            return;
        }

        if (!ConfigUtils.isSerialLicensed()) {
            finish();
            return;
        }

        setContentView(R.layout.activity_enter_serial);

        checkForValidCredentialsInBackground();

        ViewExtensionsKt.applyDeviceInsetsAsPadding(
            findViewById(R.id.layout_parent_top),
            InsetSides.all(),
            SpacingValues.symmetric(R.dimen.wizard_contents_padding, R.dimen.wizard_contents_padding_horizontal)
        );

        stateTextView = findViewById(R.id.unlock_state);
        licenseKeyOrUsernameText = findViewById(R.id.license_key);
        passwordText = findViewById(getResources().getIdentifier("password", "id", getPackageName()));
        serverText = findViewById(getResources().getIdentifier("server", "id", getPackageName()));

        // Workaround to fix the prefix in the TextInputLayout not being aligned correctly when the phones font size changes
        // Open Issue: https://github.com/material-components/material-components-android/issues/773
        final @Nullable TextInputLayout serverContainer = findViewById(getResources().getIdentifier("server_container", "id", getPackageName()));
        if (serverContainer != null) {
            serverContainer.getPrefixTextView().setLayoutParams(
                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT)
            );
            serverContainer.getPrefixTextView().setGravity(Gravity.CENTER);
        }

        TextView enterKeyExplainText = findViewById(R.id.layout_top);
        enterKeyExplainText.setText(HtmlCompat.fromHtml(getString(R.string.flavored__enter_serial_body), HtmlCompat.FROM_HTML_MODE_COMPACT));
        enterKeyExplainText.setClickable(true);
        enterKeyExplainText.setMovementMethod(LinkMovementMethod.getInstance());

        if (!BuildFlavor.IS_WORK && !BuildFlavor.IS_ON_PREM) {
            setupForShopBuild();
        } else {
            setupForWorkBuild();
        }

        handleUrlIntent(getIntent());
    }

    private void checkForValidCredentialsInBackground() {
        // In case there are credentials, we can validate them and skip this activity so that the
        // user does not have to enter them again.
        if (dependencies.getLicenseService().hasCredentials()) {
            backgroundExecutor.getValue().execute(new BackgroundTask<Boolean>() {
                @Override
                public void runBefore() {
                    // Nothing to do
                }

                @Override
                public Boolean runInBackground() {
                    return dependencies.getLicenseService().validate(false) == null;
                }

                @Override
                public void runAfter(Boolean result) {
                    if (Boolean.TRUE.equals(result)) {
                        logger.info("Credentials are available and valid");
                        ConfigUtils.recreateActivity(EnterSerialActivity.this);
                    }
                }
            });
        }
    }

    private void setupForShopBuild() {
        licenseKeyOrUsernameText.addTextChangedListener(new PasswordWatcher());
        licenseKeyOrUsernameText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        licenseKeyOrUsernameText.setFilters(new InputFilter[]{new InputFilter.AllCaps(), new InputFilter.LengthFilter(11)});
        licenseKeyOrUsernameText.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_ENTER) {
                if (licenseKeyOrUsernameText.getText().length() == 11) {
                    doUnlock();
                }
                return true;
            }
            return false;
        });
        unlockButton = findViewById(R.id.unlock_button);
        unlockButton.setOnClickListener(v -> doUnlock());

        this.setLoginButtonEnabled(false);
    }

    @SuppressLint("DiscouragedApi")
    private void setupForWorkBuild() {
        licenseKeyOrUsernameText.addTextChangedListener(new TextChangeWatcher());
        passwordText.addTextChangedListener(new TextChangeWatcher());

        // We need to use getResources().getIdentifier(...) because of flavor specific layout files for this fragment
        loginButtonCompose = findViewById(getResources().getIdentifier("unlock_button_work_compose", "id", getPackageName()));
        loginButtonCompose.setOnClickListener(v -> doUnlock());

        // We need to use getResources().getIdentifier(...) because of flavor specific layout files for this fragment
        TextView lostCredentialsHelp = findViewById(getResources().getIdentifier("work_lost_credential_help", "id", getPackageName()));
        lostCredentialsHelp.setText(getString(R.string.work_lost_credentials_help));

        // Always enable for work build
        setLoginButtonEnabled(true);

        // Disable the edit texts based on whether there are unchangeable preset values that must be
        // used.
        UsernameParameter configuredUsername = getConfiguredUsername();
        if (configuredUsername != null) {
            licenseKeyOrUsernameText.setText(configuredUsername.getValue());
            licenseKeyOrUsernameText.setEnabled(false);
        }

        PasswordParameter configuredPassword = getConfiguredPassword();
        if (configuredPassword != null) {
            logger.info("A password is configured, disabling password field");
            passwordText.setEnabled(false);
        }

        if (BuildFlavor.IS_ON_PREM) {
            ServerUrlParameter configuredServerUrl = getConfiguredOnPremServerUrl();
            if (configuredServerUrl != null) {
                serverText.setText(configuredServerUrl.getValue());
                serverText.setEnabled(false);
            }

            if (ConfigUtils.isWhitelabelOnPremBuild(this) && configuredServerUrl == null) {
                // In case of a whitelabel build without pre-configured server url, we do not want
                // the server url to be edited even if there is no configured server url. App setup
                // won't be possible.
                serverText.setEnabled(false);
                setLoginButtonEnabled(false);
                onMissingPresetUrl();
            }
        }
    }

    private void handleUrlIntent(@Nullable Intent intent) {
        // In case the activation link is not available or in the wrong format, it will be null.
        Uri activationLink = getActivationLink(intent);

        if (ConfigUtils.isSerialLicenseValid()) {
            if (activationLink != null) {
                // We inform the user only if there was an active attempt to re-license the app.
                Toast.makeText(this, R.string.already_licensed, Toast.LENGTH_LONG).show();
            }
            finish();
            return;
        }

        if (activationLink != null) {
            // In case there is an activation link that could be checked, we parse it, combine it
            // with mdm values (if work) and check the resulting credentials.
            checkActivationLinkAndMdm(activationLink);
        } else if (ConfigUtils.isWorkRestricted()) {
            // Otherwise we just check whether we have all the information solely from mdm config.
            UsernameParameter configuredUsername = getConfiguredUsername();
            PasswordParameter configuredPassword = getConfiguredPassword();

            if (BuildFlavor.IS_ON_PREM) {
                ServerUrlParameter configuredServerUrl = getConfiguredOnPremServerUrl();
                if (configuredUsername != null && configuredPassword != null && configuredServerUrl != null) {
                    check(
                        new Parameters.OnPremParameters(
                            configuredUsername,
                            configuredPassword,
                            configuredServerUrl
                        )
                    );
                }
            } else {
                if (configuredUsername != null && configuredPassword != null) {
                    check(
                        new Parameters.WorkParameters(
                            configuredUsername,
                            configuredPassword
                        )
                    );
                }
            }
        }
    }

    private void setLoginButtonEnabled(final boolean isEnabled) {
        if (!BuildFlavor.IS_WORK && !BuildFlavor.IS_ON_PREM) {
            if (this.unlockButton != null) {
                unlockButton.setClickable(isEnabled);
                unlockButton.setEnabled(isEnabled);
            }
        } else if (this.loginButtonCompose != null) {
            loginButtonCompose.setButtonEnabled(isEnabled);
        }
    }

    private void checkActivationLinkAndMdm(@NonNull Uri activationLink) {
        String query = activationLink.getQuery();
        if (query != null && !query.isEmpty()) {
            if (dependencies.getLicenseService() instanceof LicenseServiceUser) {
                checkWorkActivationLinkAndMdm(activationLink);
            } else {
                checkPrivateActivationLink(activationLink);
            }
        }
    }

    private void checkPrivateActivationLink(@NonNull Uri data) {
        final String key = data.getQueryParameter("key");
        SerialParameter serialParameter = SerialParameter.create(key);
        if (serialParameter != null) {
            check(
                new Parameters.PrivateParameters(
                    serialParameter
                )
            );
        }
    }

    private void checkWorkActivationLinkAndMdm(@NonNull Uri data) {
        final UsernameParameter intentUsername = getIntentUsername(data);
        final PasswordParameter intentPassword = getIntentPassword(data);
        final ServerUrlParameter intentServerUrl = getIntentServerUrl(data);

        final UsernameParameter configuredUsername = getConfiguredUsername();
        final PasswordParameter configuredPassword = getConfiguredPassword();
        final ServerUrlParameter configuredServerUrl = getConfiguredOnPremServerUrl();

        final UsernameParameter effectiveUsername = configuredUsername != null ? configuredUsername : intentUsername;
        final PasswordParameter effectivePassword = configuredPassword != null ? configuredPassword : intentPassword;
        final ServerUrlParameter effectiveServerUrl;

        if (ConfigUtils.isWhitelabelOnPremBuild(this)) {
            // Assert that we have a server url on the whitelabel build. If we don't, we abort
            // parsing the uri.
            if (configuredServerUrl == null) {
                return;
            }

            // Check that the intent server url matches the configured server url
            if (intentServerUrl != null) {
                if (!configuredServerUrl.equals(intentServerUrl)) {
                    onIntentServerUrlMismatch();
                    return;
                }
            }

            // The effective server url is always the configured server url on whitelabel builds.
            effectiveServerUrl = configuredServerUrl;
        } else if (BuildFlavor.IS_ON_PREM) {
            // Check that both server urls equal if they are both defined
            if (configuredServerUrl != null && intentServerUrl != null && !configuredServerUrl.equals(intentServerUrl)) {
                onIntentServerUrlMismatch();
                return;
            }

            // If there is a configured server url, use it. Otherwise, try the intent server url.
            effectiveServerUrl = configuredServerUrl != null ? configuredServerUrl : intentServerUrl;
        } else {
            // On non-onprem builds we never use a server url
            effectiveServerUrl = null;
        }

        // Pre-fill the available credentials into the edit texts.
        licenseKeyOrUsernameText.setText(effectiveUsername != null ? effectiveUsername.getValue() : "");
        // We must not display the password that has been set via mdm. However, we can show
        // the password provided by the activation link.
        if (configuredPassword == null) {
            passwordText.setText(intentPassword != null ? intentPassword.getValue() : "");
        }

        // Check the credentials if available
        if (BuildFlavor.IS_ON_PREM) {
            // Also show the server url if available
            serverText.setText(effectiveServerUrl != null ? effectiveServerUrl.getValue() : null);

            // Check license if credentials and server url are available
            if (effectiveUsername != null && effectivePassword != null && effectiveServerUrl != null) {
                check(
                    new Parameters.OnPremParameters(
                        effectiveUsername,
                        effectivePassword,
                        effectiveServerUrl
                    )
                );
            }
        } else {
            // Check license if credentials are available
            if (effectiveUsername != null && effectivePassword != null) {
                check(
                    new Parameters.WorkParameters(
                        effectiveUsername,
                        effectivePassword
                    )
                );
            }
        }
    }

    private void doUnlock() {
        // hide keyboard to make error message visible on low resolution displays
        EditTextUtil.hideSoftKeyboard(this.licenseKeyOrUsernameText);

        this.setLoginButtonEnabled(false);

        if (BuildFlavor.IS_ON_PREM) {
            UsernameParameter configuredUsername = getConfiguredUsername();
            PasswordParameter configuredPassword = getConfiguredPassword();
            ServerUrlParameter configuredServerUrl = getConfiguredOnPremServerUrl();

            UsernameParameter effectiveUsername = configuredUsername != null ? configuredUsername : getEnteredUsername();
            PasswordParameter effectivePassword = configuredPassword != null ? configuredPassword : getEnteredPassword();
            ServerUrlParameter effectiveServerUrl = configuredServerUrl != null ? configuredServerUrl : getEnteredServerUrl();

            if (effectiveUsername != null && effectivePassword != null && effectiveServerUrl != null) {
                this.check(
                    new Parameters.OnPremParameters(
                        effectiveUsername,
                        effectivePassword,
                        effectiveServerUrl
                    )
                );
            } else {
                this.setLoginButtonEnabled(true);
                this.stateTextView.setText(getString(R.string.invalid_input));
            }
        } else if (BuildFlavor.IS_WORK) {
            UsernameParameter configuredUsername = getConfiguredUsername();
            PasswordParameter configuredPassword = getConfiguredPassword();

            UsernameParameter effectiveUsername = configuredUsername != null ? configuredUsername : getEnteredUsername();
            PasswordParameter effectivePassword = configuredPassword != null ? configuredPassword : getEnteredPassword();

            if (effectiveUsername != null && effectivePassword != null) {
                this.check(
                    new Parameters.WorkParameters(
                        effectiveUsername,
                        effectivePassword
                    )
                );
            } else {
                this.setLoginButtonEnabled(true);
                this.stateTextView.setText(getString(R.string.invalid_input));
            }
        } else {
            SerialParameter serialParameter = getEnteredSerialLicense();
            if (serialParameter != null) {
                this.check(
                    new Parameters.PrivateParameters(
                        serialParameter
                    )
                );
            }
        }
    }

    private class PasswordWatcher extends SimpleTextWatcher {
        @Override
        public void afterTextChanged(@NonNull Editable editable) {
            String initial = editable.toString();
            String processed = initial.replaceAll("[^a-zA-Z0-9]", "");
            processed = processed.replaceAll("([a-zA-Z0-9]{5})(?=[a-zA-Z0-9])", "$1-");

            if (!initial.equals(processed)) {
                editable.replace(0, initial.length(), processed);
            }

            //enable login only if the length of the key is 11 chars
            setLoginButtonEnabled(editable.length() == 11);
        }
    }

    public class TextChangeWatcher extends SimpleTextWatcher {
        @Override
        public void afterTextChanged(@NonNull Editable editable) {
            if (stateTextView != null) {
                stateTextView.setText("");
            }
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);

        if (licenseKeyOrUsernameText != null && !isNullOrBlank(licenseKeyOrUsernameText.getText())) {
            outState.putString(BUNDLE_LICENSE_KEY, licenseKeyOrUsernameText.getText().toString());
        }

        if (passwordText != null && !isNullOrBlank(passwordText.getText())) {
            outState.putString(BUNDLE_PASSWORD, passwordText.getText().toString());
        }

        if (serverText != null && !isNullOrBlank(serverText.getText())) {
            outState.putString(BUNDLE_SERVER, serverText.getText().toString());
        }
    }

    private void check(@Nullable Parameters parameters) {
        if (parameters == null || !parameters.getCanBeChecked()) {
            return;
        }

        if (BuildFlavor.IS_ON_PREM) {
            if (!(parameters instanceof Parameters.OnPremParameters)) {
                throw new IllegalStateException("Cannot handle parameters of type " + parameters.getClass() + " in onprem build");
            }

            var oppfUrl = ((Parameters.OnPremParameters) parameters).getServerUrl().getUrlToOppf();
            var preferenceService = dependencies.getPreferenceService();
            preferenceService.setOppfUrl(oppfUrl);
            preferenceService.setLicenseUsername(((Parameters.OnPremParameters) parameters).getUsername().getValue());
            preferenceService.setLicensePassword(((Parameters.OnPremParameters) parameters).getPassword().getValue());
        }

        new LifecycleAwareAsyncTask<Parameters, String>() {
            @Override
            protected void onPreExecute() {
                GenericProgressDialog.newInstance(R.string.flavored__checking_serial, R.string.please_wait).show(getSupportFragmentManager(), DIALOG_TAG_CHECKING);
            }

            @Override
            protected String doInBackground(Parameters params) {
                try {
                    LicenseService licenseService = dependencies.getLicenseService();
                    String error = licenseService.validate(params.getCredentials());
                    if (error != null) {
                        return error;
                    }
                    if (BuildFlavor.IS_WORK) {
                        UserCredentials userCredentials = (UserCredentials) params.getCredentials();

                        var result = dependencies.getApiConnector().fetchWorkData(userCredentials.username, userCredentials.password, new String[]{});
                        if (result.responseCode > 0) {
                            logger.error("Failed to fetch2 work data. Server response code = {}", result.responseCode);
                        } else {
                            AppRestrictionService appRestrictionService = KoinJavaComponent.get(AppRestrictionService.class);
                            appRestrictionService.storeWorkMDMSettings(result.mdm);
                        }
                    }
                } catch (Exception e) {
                    logger.error("Exception", e);
                    // Note that an empty string will still be treated as error.
                    return "";
                }
                return null;
            }

            @Override
            protected void onPostExecute(String error) {
                setLoginButtonEnabled(true);
                DialogUtil.dismissDialog(getSupportFragmentManager(), DIALOG_TAG_CHECKING);
                if (error == null) {
                    ConfigUtils.recreateActivity(EnterSerialActivity.this);
                } else {
                    changeState(error);
                }
            }
        }.execute(this, parameters);
    }

    /**
     * Shows an error to the user that the provided server url in the activation link does not match
     * the requirements, i.e., either the server url set via mdm (onprem) or the pre-configured
     * server url (whitelabel onprem).
     */
    private void onIntentServerUrlMismatch() {
        logger.error("The intent's server url does not match the requirements");
        changeState(getString(R.string.error_preset_onprem_url_mismatch_intent));
    }

    /**
     * Shows an error to the user that there is no pre-configured server url. This can happen when
     * a whitelabel build does not have the preconfigured url set.
     */
    private void onMissingPresetUrl() {
        logger.error("The preset server url is missing for a whitelabel build");
        changeState("No preset OPPF Url");
    }

    private void changeState(@NonNull String state) {
        String errorMessage = state.isBlank() ? getString(R.string.error) : state;
        stateTextView.setText(errorMessage);
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        // We override this method to avoid restarting the entire
        // activity when the keyboard is opened or orientation changes
        super.onConfigurationChanged(newConfig);
    }

    @Override
    protected void onNewIntent(@NonNull Intent intent) {
        super.onNewIntent(intent);
        handleUrlIntent(intent);
    }

    @Nullable
    private ServerUrlParameter getPresetOnPremServerUrlIfWhiteLabeled() {
        //noinspection ConstantValue
        return ConfigUtils.isWhitelabelOnPremBuild(this) && BuildConfig.PRESET_OPPF_URL != null
            ? ServerUrlParameter.create(
            BuildConfig.PRESET_OPPF_URL,
            ParameterSource.PRECONFIGURED
        ) : null;
    }

    @Nullable
    private Uri getActivationLink(@Nullable Intent intent) {
        if (intent == null) {
            return null;
        }

        Uri data = intent.getData();
        String scheme = data != null ? data.getScheme() : null;
        if (scheme == null) {
            return null;
        }

        if (scheme.startsWith(BuildConfig.uriScheme)) {
            return data;
        } else if (scheme.startsWith("https")) {
            String path = data.getPath();
            if (path == null || path.isEmpty()) {
                return null;
            }

            return path.startsWith("/license") ? data : null;
        } else {
            return null;
        }
    }

    @Nullable
    private SerialParameter getEnteredSerialLicense() {
        String serialLicenseKey = getTextOrEmpty(licenseKeyOrUsernameText);
        return SerialParameter.create(serialLicenseKey);
    }

    @Nullable
    private UsernameParameter getEnteredUsername() {
        String username = getTextOrEmpty(licenseKeyOrUsernameText);
        if (!username.isEmpty()) {
            return UsernameParameter.create(
                username,
                ParameterSource.USER
            );
        }

        return null;
    }

    @Nullable
    private PasswordParameter getEnteredPassword() {
        String password = getTextOrEmpty(passwordText);
        if (!password.isEmpty()) {
            return PasswordParameter.create(
                password,
                ParameterSource.USER
            );
        }

        return null;
    }

    @Nullable
    private ServerUrlParameter getEnteredServerUrl() {
        String serverUrl = getTextOrEmpty(serverText);
        if (!serverUrl.isBlank()) {
            return ServerUrlParameter.create(
                serverUrl,
                ParameterSource.USER
            );
        }

        return null;
    }

    /**
     * Get the username from the uri. Returns null if no username is present.
     */
    @Nullable
    private UsernameParameter getIntentUsername(@NonNull Uri uri) {
        String username = uri.getQueryParameter("username");
        if (username != null) {
            return UsernameParameter.create(
                username,
                ParameterSource.ACTIVATION_LINK
            );
        }

        return null;
    }

    /**
     * Get the password from the uri. Returns null if no password is present.
     */
    @Nullable
    private PasswordParameter getIntentPassword(@NonNull Uri uri) {
        String password = uri.getQueryParameter("password");
        if (password != null) {
            return PasswordParameter.create(
                password,
                ParameterSource.ACTIVATION_LINK
            );
        }

        return null;
    }

    /**
     * Get the server url parameter. Returns null if no server url is set.
     */
    @Nullable
    private ServerUrlParameter getIntentServerUrl(@NonNull Uri uri) {
        String serverUrl = uri.getQueryParameter("server");
        if (serverUrl != null) {
            return ServerUrlParameter.create(
                serverUrl,
                ParameterSource.ACTIVATION_LINK
            );
        }

        return null;
    }

    /**
     * Get the username configured via mdm. If no username is configured via mdm, null is returned.
     */
    @Nullable
    private UsernameParameter getConfiguredUsername() {
        String username = dependencies.getAppRestrictions().getLicenseUsername();
        if (username != null) {
            return UsernameParameter.create(
                username,
                ParameterSource.MDM
            );
        }

        return null;
    }

    /**
     * Get the password configured via mdm. If no password is configured via mdm, null is returned.
     */
    @Nullable
    private PasswordParameter getConfiguredPassword() {
        String password = dependencies.getAppRestrictions().getLicensePassword();
        if (password != null) {
            return PasswordParameter.create(
                password,
                ParameterSource.MDM
            );
        }

        return null;
    }

    /**
     * Get the configure onprem server url. On whitelabel builds this is the pre-configured url, on normal onprem builds it is the mdm defined server
     * url or null. On non-onprem builds, null is returned.
     */
    @Nullable
    private ServerUrlParameter getConfiguredOnPremServerUrl() {
        if (ConfigUtils.isWhitelabelOnPremBuild(this)) {
            return getPresetOnPremServerUrlIfWhiteLabeled();
        } else if (BuildFlavor.IS_ON_PREM) {
            String configuredOnPremServerUrl = dependencies.getAppRestrictions().getOnPremServer();
            if (configuredOnPremServerUrl != null) {
                return ServerUrlParameter.create(
                    configuredOnPremServerUrl,
                    ParameterSource.MDM
                );
            }
        }

        return null;
    }

    @NonNull
    public static Intent createIntent(@NonNull Context context) {
        return new Intent(context, EnterSerialActivity.class);
    }
}
