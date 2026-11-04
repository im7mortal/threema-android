package ch.threema.app.threemasafe;

import android.content.Context;
import android.text.TextUtils;
import android.text.format.DateUtils;

import ch.threema.app.BuildFlavor;
import ch.threema.app.preference.service.SynchronizedSettingsService;
import ch.threema.app.profilepicture.CheckedProfilePicture;
import ch.threema.app.protocolsteps.ValidContactsLookupSteps;
import ch.threema.app.restrictions.AppRestrictions;
import ch.threema.app.usecases.OverrideOneTimeHintsUseCase;
import ch.threema.base.crypto.NaCl;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.koin.java.KoinJavaComponent;
import org.slf4j.Logger;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.Deflater;
import java.util.zip.GZIPInputStream;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import ch.threema.app.R;
import ch.threema.app.asynctasks.AddContactRestrictionPolicy;
import ch.threema.app.asynctasks.BasicAddOrUpdateContactBackgroundTask;
import ch.threema.app.asynctasks.ContactAvailable;
import ch.threema.app.asynctasks.ContactResult;
import ch.threema.app.services.ApiService;
import ch.threema.app.services.BlockedIdentitiesService;
import ch.threema.app.services.ContactService;
import ch.threema.app.services.ExcludedSyncIdentitiesService;
import ch.threema.app.services.ProfilePictureRecipientsService;
import ch.threema.app.services.LocaleService;
import ch.threema.app.preference.service.PreferenceService;
import ch.threema.app.services.UserService;
import ch.threema.app.utils.ConfigUtils;
import ch.threema.app.utils.GzipOutputStream;
import ch.threema.app.utils.WorkManagerUtil;
import ch.threema.app.workers.ThreemaSafeUploadWorker;
import ch.threema.app.workers.WorkerNames;
import ch.threema.base.ThreemaException;

import static ch.threema.base.utils.LoggingKt.getThreemaLogger;

import ch.threema.common.Base64;
import ch.threema.common.Http;
import ch.threema.common.JsonExtensionsKt;
import ch.threema.data.datatypes.AvailabilityStatus;
import ch.threema.data.datatypes.ConversationCategory;
import ch.threema.data.datatypes.ConversationSpamReportingState;
import ch.threema.data.datatypes.ConversationVisibility;
import ch.threema.data.datatypes.GroupIdentity;
import ch.threema.data.datatypes.IdColor;
import ch.threema.data.datatypes.PredefinedContact;
import ch.threema.data.models.ContactModel;
import ch.threema.data.models.ContactModelData;
import ch.threema.data.models.GroupModelData;
import ch.threema.data.repositories.ContactModelRepository;
import ch.threema.data.repositories.ContactStoreException;
import ch.threema.data.repositories.GroupCreateException;
import ch.threema.data.repositories.GroupModelRepository;
import ch.threema.domain.libthreema.LibthreemaAware;
import ch.threema.domain.models.AcquaintanceLevel;
import ch.threema.domain.models.ContactSyncState;
import ch.threema.domain.models.GroupId;
import ch.threema.domain.models.IdentityState;
import ch.threema.domain.models.IdentityType;
import ch.threema.domain.models.ReadReceiptPolicy;
import ch.threema.domain.models.TypingIndicatorPolicy;
import ch.threema.domain.models.UserState;
import ch.threema.domain.models.VerificationLevel;
import ch.threema.domain.models.WorkVerificationLevel;
import ch.threema.domain.protocol.api.APIConnector;
import ch.threema.domain.protocol.csp.ProtocolDefines;
import ch.threema.domain.stores.IdentityStore;
import ch.threema.domain.taskmanager.TriggerSource;
import ch.threema.libthreema.CryptoException;
import ch.threema.domain.libthreema.LibthreemaJavaBridge;
import ch.threema.storage.factories.DistributionListMemberModelFactory;
import ch.threema.storage.factories.DistributionListModelFactory;
import ch.threema.storage.models.DistributionListMemberModel;
import ch.threema.storage.models.DistributionListModel;
import kotlinx.serialization.json.JsonObject;
import okhttp3.OkHttpClient;

import static ch.threema.app.preference.service.PreferenceService.PROFILEPIC_RELEASE_EVERYONE;
import static ch.threema.app.preference.service.PreferenceService.PROFILEPIC_RELEASE_NOBODY;
import static ch.threema.app.preference.service.PreferenceService.PROFILEPIC_RELEASE_ALLOW_LIST;
import static ch.threema.common.ByteArrayExtensionsKt.toLong;
import static ch.threema.common.JavaCompat.hexToByteArray;
import static ch.threema.common.JavaCompat.isNullOrEmpty;
import static ch.threema.common.SecureRandomExtensionsKt.generateRandomBytes;
import static ch.threema.common.SecureRandomExtensionsKt.secureRandom;
import static ch.threema.common.ByteBufferExtensionsKt.toDirectByteBuffer;
import static ch.threema.libthreema.LibthreemaKt.scrypt;

@LibthreemaAware
public class ThreemaSafeServiceImpl implements ThreemaSafeService {
    private static final Logger logger = getThreemaLogger("ThreemaSafeServiceImpl");

    private static final int MASTERKEY_LENGTH = 64;

    private static final int ENCRYPTION_KEY_LENGTH = NaCl.SYMM_KEY_BYTES;
    private static final int PROTOCOL_VERSION = 1;

    public static final int MIN_PW_LENGTH = 8;
    public static final int MAX_PW_LENGTH = 4096;

    private final Context context;
    @NonNull
    private final PreferenceService preferenceService;
    @NonNull
    private final SynchronizedSettingsService synchronizedSettingsService;
    private final UserService userService;
    private final IdentityStore identityStore;
    @NonNull
    private final APIConnector apiConnector;
    private final LocaleService localeService;
    private final ContactService contactService;
    private final BlockedIdentitiesService blockedIdentitiesService;
    @NonNull
    private final ExcludedSyncIdentitiesService excludedSyncIdentitiesService;
    private final ProfilePictureRecipientsService profilePictureRecipientsService;
    @NonNull
    private final DistributionListModelFactory distributionListModelFactory;
    @NonNull
    private final DistributionListMemberModelFactory distributionListMemberModelFactory;
    @NonNull
    private final ContactModelRepository contactModelRepository;
    @NonNull
    private final GroupModelRepository groupModelRepository;
    @NonNull
    private final ThreemaSafeApiClient threemaSafeApiClient;
    @NonNull
    private final AppRestrictions appRestrictions;
    @NonNull
    private final ValidContactsLookupSteps validContactsLookupSteps;
    @NonNull
    private final ThreemaSafeBackupCreator threemaSafeBackupCreator;

    public ThreemaSafeServiceImpl(
        Context context,
        @NonNull PreferenceService preferenceService,
        @NonNull SynchronizedSettingsService synchronizedSettingsService,
        UserService userService,
        ContactService contactService,
        LocaleService localeService,
        @NonNull BlockedIdentitiesService blockedIdentitiesService,
        @NonNull ExcludedSyncIdentitiesService excludedSyncIdentitiesService,
        ProfilePictureRecipientsService profilePictureRecipientsService,
        @NonNull DistributionListModelFactory distributionListModelFactory,
        @NonNull DistributionListMemberModelFactory distributionListMemberModelFactory,
        IdentityStore identityStore,
        @NonNull ApiService apiService,
        @NonNull APIConnector apiConnector,
        @NonNull ContactModelRepository contactModelRepository,
        @NonNull GroupModelRepository groupModelRepository,
        @NonNull OkHttpClient okHttpClient,
        @NonNull AppRestrictions appRestrictions,
        @NonNull ValidContactsLookupSteps validContactsLookupSteps,
        @NonNull ThreemaSafeBackupCreator threemaSafeBackupCreator
    ) {
        this.context = context;
        this.preferenceService = preferenceService;
        this.synchronizedSettingsService = synchronizedSettingsService;
        this.userService = userService;
        this.contactService = contactService;
        this.identityStore = identityStore;
        this.apiConnector = apiConnector;
        this.localeService = localeService;
        this.distributionListModelFactory = distributionListModelFactory;
        this.distributionListMemberModelFactory = distributionListMemberModelFactory;
        this.blockedIdentitiesService = blockedIdentitiesService;
        this.excludedSyncIdentitiesService = excludedSyncIdentitiesService;
        this.profilePictureRecipientsService = profilePictureRecipientsService;
        this.contactModelRepository = contactModelRepository;
        this.groupModelRepository = groupModelRepository;
        this.appRestrictions = appRestrictions;
        this.validContactsLookupSteps = validContactsLookupSteps;
        this.threemaSafeBackupCreator = threemaSafeBackupCreator;

        this.threemaSafeApiClient = new ThreemaSafeApiClient(
            prepareOkHttpClient(okHttpClient, apiService)
        );
    }

    private static OkHttpClient prepareOkHttpClient(OkHttpClient okHttpClient, ApiService apiService) {
        if (BuildFlavor.IS_ON_PREM) {
            // TODO(ANDR-4201): Handling auth errors should be done at app level, not at feature level
            return okHttpClient.newBuilder()
                .addInterceptor(chain -> {
                    var response = chain.proceed(chain.request());
                    if (response.code() == Http.StatusCode.UNAUTHORIZED) {
                        apiService.invalidateAuthToken();
                    }
                    return response;
                })
                .build();
        }
        return okHttpClient;
    }

    @Override
    @Nullable
    public byte[] deriveMasterKey(@Nullable String password, @Nullable String identity) {
        if (!TextUtils.isEmpty(password) && !TextUtils.isEmpty(identity)) {
            try {
                return scrypt(
                    toDirectByteBuffer(password),
                    toDirectByteBuffer(identity),
                    LibthreemaJavaBridge.createScryptParameters(
                        (byte) MASTERKEY_LENGTH
                    )
                );
            } catch (Exception e) {
                logger.error("Could not derive master key", e);
            }
        }
        return null;
    }

    @Override
    public boolean storeMasterKey(byte[] masterKey) {
        if (masterKey != null) {
            preferenceService.setThreemaSafeMasterKey(masterKey);
        }
        return false;
    }

    @Override
    @Nullable
    public byte[] getThreemaSafeBackupId() {
        byte[] masterKey = preferenceService.getThreemaSafeMasterKey();

        if (masterKey != null && masterKey.length == MASTERKEY_LENGTH) {
            return Arrays.copyOfRange(masterKey, 0, BACKUP_ID_LENGTH);
        }
        return null;
    }

    @Override
    @Nullable
    public byte[] getThreemaSafeEncryptionKey() {
        byte[] masterKey = preferenceService.getThreemaSafeMasterKey();

        if (masterKey != null && masterKey.length == MASTERKEY_LENGTH) {
            return Arrays.copyOfRange(masterKey, BACKUP_ID_LENGTH, BACKUP_ID_LENGTH + ENCRYPTION_KEY_LENGTH);
        }
        return null;
    }

    @Override
    @Nullable
    public byte[] getThreemaSafeMasterKey() {
        return preferenceService.getThreemaSafeMasterKey();
    }

    @Override
    public ThreemaSafeServerTestResponse testServer(ThreemaSafeServerInfo serverInfo) throws ThreemaException {
        try {
            var response = threemaSafeApiClient.testServer(serverInfo, getThreemaSafeBackupId());
            preferenceService.setThreemaSafeServerMaxUploadSize(response.maxBackupBytes);
            preferenceService.setThreemaSafeServerRetention(response.retentionDays);
            return response;
        } catch (IOException e) {
            throw new ThreemaException(e.getMessage(), e);
        } catch (JSONException e) {
            throw new ThreemaException("Malformed server response", e);
        } catch (IllegalArgumentException e) {
            throw new ThreemaException(e.getMessage());
        }
    }

    @Override
    public boolean schedulePeriodicUpload() {
        logger.info("Scheduling Threema Safe upload");
        WorkManager workManager = WorkManager.getInstance(context);
        boolean reschedule = WorkManagerUtil.shouldScheduleNewWorkManagerInstance(
            workManager,
            WorkerNames.WORKER_PERIODIC_THREEMA_SAFE_UPLOAD,
            SCHEDULE_PERIOD
        );
        return schedulePeriodicWork(reschedule);
    }

    @Override
    public boolean reschedulePeriodicUpload() {
        logger.info("Rescheduling Threema Safe upload");
        return schedulePeriodicWork(true);
    }

    @Override
    public void unschedulePeriodicUpload() {
        logger.info("Unscheduling Threema Safe upload");

        WorkManager workManager = WorkManager.getInstance(context);
        workManager.cancelUniqueWork(WorkerNames.WORKER_PERIODIC_THREEMA_SAFE_UPLOAD);
    }

    @Override
    public void setEnabled(boolean enabled) {
        if (ConfigUtils.isWorkRestricted() && ThreemaSafeMDMConfig.getInstance().isBackupDisabled()) {
            enabled = false;
        }

        preferenceService.setThreemaSafeEnabled(enabled);
        if (enabled) {
            schedulePeriodicUpload();
        } else {
            // disable Safe
            unschedulePeriodicUpload();
            preferenceService.setThreemaSafeEnabled(false);
            preferenceService.setThreemaSafeMasterKey(new byte[0]);
            preferenceService.setThreemaSafeServerInfo(null);
            preferenceService.setThreemaSafeUploadTimestamp(null);
            preferenceService.setThreemaSafeBackupTimestamp(null);
            preferenceService.setThreemaSafeHashString("");
            preferenceService.setThreemaSafeErrorCode(ERROR_CODE_OK);
        }
    }

    @Override
    public void uploadNow() {
        try {
            WorkManager workManager = WorkManager.getInstance(context);

            // If the periodic safe upload is scheduled, we can reschedule it so that it does not
            // get executed within the next schedule period.
            if (WorkManagerUtil.isWorkManagerInstanceScheduled(workManager, WorkerNames.WORKER_PERIODIC_THREEMA_SAFE_UPLOAD)) {
                reschedulePeriodicUpload();
            }

            // Upload the threema safe once
            OneTimeWorkRequest workRequest = ThreemaSafeUploadWorker.buildRunNowWorkRequest();
            workManager.enqueueUniqueWork(WorkerNames.WORKER_THREEMA_SAFE_UPLOAD, ExistingWorkPolicy.REPLACE, workRequest);
        } catch (IllegalStateException e) {
            logger.error("Unable to schedule safe upload one time work", e);
        }
    }

    @Override
    public void createBackup(boolean force) throws ThreemaSafeUploadException {
        logger.info("Starting Threema Safe backup");

        if (!preferenceService.getThreemaSafeEnabled()) {
            throw new ThreemaSafeUploadException("Disabled", false);
        }

        ThreemaSafeServerInfo serverInfo = preferenceService.getThreemaSafeServerInfo();

        // test server to update configuration
        final ThreemaSafeServerTestResponse serverTestResponse;
        try {
            serverTestResponse = testServer(serverInfo);
        } catch (ThreemaException e) {
            preferenceService.setThreemaSafeErrorCode(ERROR_CODE_SERVER_FAIL);
            throw new ThreemaSafeUploadException("Server test failed. " + e.getMessage(), true);
        }

        String safeJson = getSafeJson();
        if (safeJson == null) {
            preferenceService.setThreemaSafeErrorCode(ERROR_CODE_JSON_FAIL);
            throw new ThreemaSafeUploadException("Json failed", true);
        }

        // get a hash of the json to determine if there are any changes
        String hashString;
        try {
            MessageDigest messageDigest = MessageDigest.getInstance("SHA-1");
            messageDigest.update(safeJson.getBytes(StandardCharsets.UTF_8));
            hashString = new String(messageDigest.digest());
        } catch (NoSuchAlgorithmException e) {
            preferenceService.setThreemaSafeErrorCode(ERROR_CODE_HASH_FAIL);
            throw new ThreemaSafeUploadException("Hash calculation failed", true);
        }

        if (!force) {
            if (hashString.equals(preferenceService.getThreemaSafeHashString())) {
                long halfRetentionTimeMillis = (serverTestResponse.retentionDays == 0 ? 180 : serverTestResponse.retentionDays) * DateUtils.DAY_IN_MILLIS / 2;
                Instant reUploadThreshold = Instant.now().minusMillis(halfRetentionTimeMillis);
                Instant threemaSafeUploadDate = preferenceService.getThreemaSafeUploadTimestamp();
                if (preferenceService.getThreemaSafeErrorCode() == ERROR_CODE_OK &&
                    threemaSafeUploadDate != null &&
                    reUploadThreshold.isBefore(threemaSafeUploadDate)) {
                    logger.info("Threema Safe contents unchanged. NOT uploaded");
                    return;
                }
            } else {
                Instant reUploadThreshold = Instant.now().minus(23, ChronoUnit.HOURS);
                Instant threemaSafeUploadDate = preferenceService.getThreemaSafeUploadTimestamp();
                if (preferenceService.getThreemaSafeErrorCode() == ERROR_CODE_OK &&
                    threemaSafeUploadDate != null &&
                    reUploadThreshold.isBefore(threemaSafeUploadDate)) {
                    throw new ThreemaSafeUploadException("Grace time not yet reached. NOT uploaded", false);
                }
            }
        }

        byte[] gzippedPlaintext = gZipCompress(safeJson.getBytes());
        if (gzippedPlaintext == null || gzippedPlaintext.length <= 0) {
            preferenceService.setThreemaSafeErrorCode(ERROR_CODE_GZIP_FAIL);
            throw new ThreemaSafeUploadException("Compression failed", true);
        }

        if (getThreemaSafeEncryptionKey() == null) {
            throw new ThreemaSafeUploadException("No key", true);
        }

        byte[] nonce = generateRandomBytes(secureRandom(), NaCl.NONCE_BYTES);

        try {
            byte[] encdata = NaCl.symmetricEncryptData(gzippedPlaintext, getThreemaSafeEncryptionKey(), nonce);

            byte[] threemaSafeEncryptedBackup = new byte[nonce.length + encdata.length];
            System.arraycopy(nonce, 0, threemaSafeEncryptedBackup, 0, nonce.length);
            System.arraycopy(encdata, 0, threemaSafeEncryptedBackup, nonce.length, encdata.length);

            if (threemaSafeEncryptedBackup.length <= serverTestResponse.maxBackupBytes) {
                uploadData(serverInfo, threemaSafeEncryptedBackup);
                preferenceService.setThreemaSafeBackupSize(threemaSafeEncryptedBackup.length);
                preferenceService.setThreemaSafeUploadTimestamp(Instant.now());
                preferenceService.setThreemaSafeBackupTimestamp(Instant.now());
                preferenceService.setThreemaSafeHashString(hashString);
                preferenceService.setThreemaSafeErrorCode(ERROR_CODE_OK);
            } else {
                preferenceService.setThreemaSafeBackupSize(threemaSafeEncryptedBackup.length);
                throw new UploadSizeExceedException("Upload size exceeded");
            }
        } catch (UploadSizeExceedException e) {
            logger.error("Exception", e);
            preferenceService.setThreemaSafeErrorCode(ERROR_CODE_SIZE_EXCEEDED);
            throw new ThreemaSafeUploadException(e.getMessage(), true);
        } catch (Exception e) {
            logger.error("Exception", e);
            preferenceService.setThreemaSafeErrorCode(ERROR_CODE_UPLOAD_FAIL);
            throw new ThreemaSafeUploadException("Upload failed", true);
        }

        logger.info("Threema Safe backup successfully created and uploaded");
    }

    @Override
    public void deleteBackup() throws ThreemaException {
        ThreemaSafeServerInfo serverInfo = preferenceService.getThreemaSafeServerInfo();
        try {
            threemaSafeApiClient.deleteBackup(serverInfo, getThreemaSafeBackupId());
        } catch (IOException e) {
            throw new ThreemaException("deleting backup failed", e);
        } catch (IllegalArgumentException e) {
            throw new ThreemaException(e.getMessage());
        }
    }

    @Override
    public void restoreBackup(String identity, String password, ThreemaSafeServerInfo serverInfo) throws ThreemaException, IOException {
        if (isNullOrEmpty(password) || serverInfo == null || isNullOrEmpty(identity) || identity.length() != ProtocolDefines.IDENTITY_LEN) {
            throw new ThreemaException("Illegal arguments");
        }

        byte[] masterKey = deriveMasterKey(password, identity);
        if (masterKey == null) {
            throw new ThreemaException("Unable to derive master key");
        }

        preferenceService.setThreemaSafeMasterKey(masterKey);
        preferenceService.setThreemaSafeServerInfo(serverInfo);

        byte[] threemaSafeEncryptedBackup = threemaSafeApiClient.downloadEncryptedBackup(serverInfo, getThreemaSafeBackupId());

        byte[] nonce = new byte[NaCl.NONCE_BYTES];
        byte[] gzippedData = new byte[threemaSafeEncryptedBackup.length - NaCl.NONCE_BYTES];
        System.arraycopy(threemaSafeEncryptedBackup, 0, nonce, 0, NaCl.NONCE_BYTES);
        System.arraycopy(threemaSafeEncryptedBackup, NaCl.NONCE_BYTES, gzippedData, 0, threemaSafeEncryptedBackup.length - NaCl.NONCE_BYTES);

        final @Nullable byte[] threemaSafeEncryptionKey = getThreemaSafeEncryptionKey();
        if (threemaSafeEncryptionKey == null) {
            throw new ThreemaException("Threema Safe encryption key must not be null");
        }

        try {
            NaCl.symmetricDecryptDataInPlace(gzippedData, threemaSafeEncryptionKey, nonce);
        } catch (IllegalArgumentException | CryptoException exception) {
            throw new ThreemaException("Unable to decrypt", exception);
        }

        byte[] uncompressedData = gZipUncompress(gzippedData);
        if (uncompressedData == null) {
            throw new ThreemaException("Uncompress failed");
        }

        String json = new String(uncompressedData, StandardCharsets.UTF_8);

        restoreJson(identity, json);

        OverrideOneTimeHintsUseCase overrideOneTimeHintsUseCase = KoinJavaComponent.get(OverrideOneTimeHintsUseCase.class);
        overrideOneTimeHintsUseCase.call();

        // successfully restored - update mdm settings config
        ThreemaSafeMDMConfig.getInstance().saveConfig(preferenceService);
    }

    private boolean schedulePeriodicWork(boolean reschedule) {
        if (preferenceService.getThreemaSafeEnabled()) {
            ExistingPeriodicWorkPolicy policy = reschedule ? ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE : ExistingPeriodicWorkPolicy.KEEP;

            try {
                // Schedule the start of the worker every 24 hours
                WorkManager workManager = WorkManager.getInstance(context);
                PeriodicWorkRequest workRequest = ThreemaSafeUploadWorker.buildPeriodicWorkRequest(SCHEDULE_PERIOD);
                workManager.enqueueUniquePeriodicWork(
                    WorkerNames.WORKER_PERIODIC_THREEMA_SAFE_UPLOAD,
                    policy,
                    workRequest
                );

                return true;
            } catch (IllegalStateException e) {
                logger.error("Unable to schedule periodic safe upload", e);
            }
        } else {
            logger.info("Threema Safe disabled");
        }
        return false;
    }

    private void restoreJson(String identity, String json) throws ThreemaException {
        JSONObject jsonObject;

        try {
            jsonObject = new JSONObject(json);
            parseInfo(jsonObject.getJSONObject(ThreemaSafeBackup.SECTION_INFO));
        } catch (JSONException e) {
            throw new ThreemaException("Missing Info object or version mismatch");
        }

        try {
            restoreUser(identity, jsonObject.getJSONObject(ThreemaSafeBackup.SECTION_USER));
        } catch (IOException | JSONException e) {
            throw new ThreemaException("Unable to restore user");
        }

        try {
            restoreSettings(jsonObject.getJSONObject(ThreemaSafeBackup.SECTION_SETTINGS));
        } catch (JSONException e) {
            // no settings - ignore and continue
        }

        try {
            restoreContacts(jsonObject.getJSONArray(ThreemaSafeBackup.SECTION_CONTACTS));
        } catch (JSONException e) {
            // no contacts - stop here as groups and distributions lists are of no use without contacts
            return;
        }

        try {
            restoreGroups(jsonObject.getJSONArray(ThreemaSafeBackup.SECTION_GROUPS));
        } catch (JSONException e) {
            // no groups - ignore and continue
        }

        try {
            restoreDistributionLists(jsonObject.getJSONArray(ThreemaSafeBackup.SECTION_DISTRIBUTION_LISTS));
        } catch (JSONException e) {
            // no distribution lists - ignore and continue
        }
    }

    private void restoreUser(String identity, @NonNull JSONObject user) throws ThreemaException, IOException, JSONException {
        byte[] privateKey;

        String encodedPrivateKey = user.getString(ThreemaSafeUserSection.TAG_PRIVATE_KEY);
        if (isNullOrEmpty(encodedPrivateKey)) {
            throw new ThreemaException("Invalid JSON");
        }
        privateKey = Base64.decode(encodedPrivateKey);

        try {
            userService.restoreIdentity(identity, privateKey);
        } catch (Exception e) {
            throw new ThreemaException(context.getString(R.string.unable_to_restore_identity_because, e.getMessage()));
        }

        String nickname = user.optString(ThreemaSafeUserSection.TAG_NICKNAME, identity);
        userService.setPublicNickname(nickname, TriggerSource.LOCAL);

        boolean isLinksRestricted = false;
        if (ConfigUtils.isWorkRestricted()) {
            // if links have been set do not restore links if readonly profile is set to true and the user is unable to change or remove links
            String stringPreset;
            stringPreset = appRestrictions.getLinkedEmail();
            if (stringPreset != null) {
                isLinksRestricted = true;
                addUserLink(ThreemaSafeUserSection.TAG_LINK_TYPE_EMAIL, stringPreset);
            }
            stringPreset = appRestrictions.getLinkedPhone();
            if (stringPreset != null) {
                isLinksRestricted = true;
                addUserLink(ThreemaSafeUserSection.TAG_LINK_TYPE_MOBILE, stringPreset);
            }
            // do not restore links if readonly profile is set to true and the user is unable to change or remove links later
            if (appRestrictions.isReadOnlyProfile()) {
                isLinksRestricted = true;
            }
        }

        if (!isLinksRestricted) {
            restoreLinks(user.optJSONArray(ThreemaSafeUserSection.TAG_LINKS));
        }

        final String profilePic = user.optString(ThreemaSafeUserSection.TAG_PROFILE_PIC, "");
        if (!profilePic.isEmpty()) {
            try {
                var checkedProfilePicture = CheckedProfilePicture.getOrConvertFromBytes(Base64.decode(profilePic));
                if (checkedProfilePicture != null) {
                    userService.setUserProfilePicture(checkedProfilePicture, TriggerSource.LOCAL);
                }
            } catch (Exception e) {
                logger.error("Failed to restore profile picture", e);
            }
        }

        JSONArray profilePicRelease = user.optJSONArray(ThreemaSafeUserSection.TAG_PROFILE_PIC_RELEASE);
        if (profilePicRelease != null) {
            preferenceService.setProfilePicRelease(PROFILEPIC_RELEASE_ALLOW_LIST);

            for (int i = 0; i < profilePicRelease.length(); i++) {
                final @Nullable String identityShareWith = profilePicRelease.optString(i, null);
                if (identityShareWith == null || identityShareWith.equalsIgnoreCase(JSONObject.NULL.toString())) {
                    preferenceService.setProfilePicRelease(PROFILEPIC_RELEASE_NOBODY);
                    break;
                }
                if (ThreemaSafeUserSection.ProfilePicRelease.PROFILE_PICTURE_RELEASE_ALL_PLACEHOLDER.equals(identityShareWith)) {
                    preferenceService.setProfilePicRelease(PROFILEPIC_RELEASE_EVERYONE);
                    break;
                }
                if (identityShareWith.length() == ProtocolDefines.IDENTITY_LEN) {
                    profilePictureRecipientsService.add(identityShareWith);
                }
            }
        }
    }

    private void addUserLink(@NonNull String type, @Nullable String value) {
        if (value == null || value.isEmpty()) {
            return;
        }

        switch (type) {
            case ThreemaSafeUserSection.TAG_LINK_TYPE_EMAIL:
                try {
                    userService.linkWithEmail(value, TriggerSource.LOCAL);
                } catch (Exception e) {
                    // ignore "already linked" exceptions
                }
                break;
            case ThreemaSafeUserSection.TAG_LINK_TYPE_MOBILE:
                try {
                    // should always be a fully qualified phone number starting with a "+"
                    userService.linkWithMobileNumber(value.startsWith("+") ? value : "+" + value, TriggerSource.LOCAL);
                } catch (Exception e) {
                    // ignore "already linked" exceptions
                }
                break;
            default:
                break;
        }
    }

    private void restoreLink(@NonNull JSONObject link) {
        String type = link.optString(ThreemaSafeUserSection.TAG_LINK_TYPE);
        String value = link.optString(ThreemaSafeUserSection.TAG_LINK_VALUE);

        addUserLink(type, value);
    }

    private void restoreLinks(JSONArray links) {
        if (links == null) return;

        for (int i = 0; i < links.length(); i++) {
            JSONObject link = links.optJSONObject(i);
            if (link != null) {
                restoreLink(link);
            }
        }
    }

    private void restoreContacts(JSONArray contacts) throws ThreemaException {
        if (contacts == null) return;

        final @Nullable String myIdentity = identityStore.getIdentityString();
        if (myIdentity == null) {
            throw new IllegalStateException("Cannot restore contacts before user identity");
        }

        ArrayList<String> identities = new ArrayList<>();
        for (int i = 0; i < contacts.length(); i++) {
            try {
                String identity = contacts.getJSONObject(i).getString(ThreemaSafeContactsSection.TAG_IDENTITY);
                if (!myIdentity.equals(identity)) {
                    identities.add(identity);
                }
            } catch (JSONException e) {
                // ignore & continue with next contact
            }
        }

        if (identities.isEmpty()) {
            return;
        }

        List<APIConnector.FetchIdentityResult> results;
        try {
            results = this.apiConnector.fetchIdentities(identities);
        } catch (Exception e) {
            throw new ThreemaException("Failed to fetch identities for contacts", e);
        }

        for (int i = 0; i < contacts.length(); i++) {
            try {
                JSONObject contact = contacts.getJSONObject(i);
                @NonNull String identity = contact.getString(ThreemaSafeContactsSection.TAG_IDENTITY);

                APIConnector.FetchIdentityResult result = getFetchResultByIdentity(results, identity);
                if (result == null) {
                    logger.warn("Identity {} not contained in results", identity);
                    continue;
                }

                ContactModel contactModel = contactModelRepository.getByIdentity(result.identity);
                if (contactModel != null && contactModel.getData() != null) {
                    logger.warn("Contact with identity {} already exists", identity);
                    continue;
                }

                VerificationLevel verificationLevel = VerificationLevel.from(contact.optInt(ThreemaSafeContactsSection.TAG_VERIFICATION_LEVEL, VerificationLevel.UNVERIFIED.getCode()));
                String publicKeyString = contact.optString(ThreemaSafeContactsSection.TAG_PUBLIC_KEY);
                byte[] publicKey;
                if (verificationLevel == VerificationLevel.FULLY_VERIFIED && !isNullOrEmpty(publicKeyString)) {
                    // Use the public key from the backup in case it is available and the contact was fully verified before.
                    publicKey = Base64.decode(publicKeyString);
                } else {
                    // As we use the fetched public key in this case, we need also to lower the verification level.
                    publicKey = result.publicKey;
                    verificationLevel = VerificationLevel.UNVERIFIED;
                }
                PredefinedContact predefinedContact = PredefinedContact.getPredefinedContact(identity);
                if (predefinedContact != null && !Arrays.equals(predefinedContact.getPublicKey(), publicKey)) {
                    logger.error("Cannot create predefined contact with different public key ({})", predefinedContact.getIdentity());
                    continue;
                }
                if (predefinedContact != null) {
                    verificationLevel = VerificationLevel.FULLY_VERIFIED;
                }
                long featureMask = result.featureMask;
                IdentityType identityType;
                switch (result.type) {
                    case 0:
                        identityType = IdentityType.REGULAR;
                        break;
                    case 1:
                        identityType = IdentityType.WORK;
                        break;
                    default:
                        logger.warn("Identity fetch returned invalid identity type: {}", result.type);
                        identityType = IdentityType.REGULAR;
                }
                IdentityState identityState;
                if (result.state == IdentityState.ACTIVE.getValue()) {
                    identityState = IdentityState.ACTIVE;
                } else if (result.state == IdentityState.INACTIVE.getValue()) {
                    identityState = IdentityState.INACTIVE;
                } else if (result.state == IdentityState.INVALID.getValue()) {
                    identityState = IdentityState.INVALID;
                } else {
                    logger.warn("Identity fetch returned invalid identity state: {}", result.state);
                    identityState = IdentityState.ACTIVE;
                }
                WorkVerificationLevel workVerificationLevel =
                    BuildFlavor.IS_WORK && contact.optBoolean(ThreemaSafeContactsSection.TAG_WORK_VERIFIED)
                        ? WorkVerificationLevel.WORK_SUBSCRIPTION_VERIFIED
                        : WorkVerificationLevel.NONE;
                String firstName = contact.optString(ThreemaSafeContactsSection.TAG_FIRST_NAME);
                String lastName = contact.optString(ThreemaSafeContactsSection.TAG_LAST_NAME);
                String nickname = !contact.isNull(ThreemaSafeContactsSection.TAG_NICKNAME)
                    ? contact.getString(ThreemaSafeContactsSection.TAG_NICKNAME)
                    : null;
                AcquaintanceLevel acquaintanceLevel = contact.optBoolean(ThreemaSafeContactsSection.TAG_HIDDEN, false)
                    ? AcquaintanceLevel.GROUP_OR_DELETED
                    : AcquaintanceLevel.DIRECT;
                Instant createdAt = Instant.ofEpochMilli(contact.optLong(ThreemaSafeContactsSection.TAG_CREATED_AT, System.currentTimeMillis()));
                TypingIndicatorPolicy typingIndicatorPolicy;
                int typingIndicatorPolicyInt = contact.optInt(ThreemaSafeContactsSection.TAG_TYPING_INDICATORS, 0);
                switch (typingIndicatorPolicyInt) {
                    case 0:
                        typingIndicatorPolicy = TypingIndicatorPolicy.DEFAULT;
                        break;
                    case 1:
                        typingIndicatorPolicy = TypingIndicatorPolicy.SEND;
                        break;
                    case 2:
                        typingIndicatorPolicy = TypingIndicatorPolicy.DONT_SEND;
                        break;
                    default:
                        logger.warn("Invalid typing indicator policy value in safe backup: {}", typingIndicatorPolicyInt);
                        typingIndicatorPolicy = TypingIndicatorPolicy.DEFAULT;
                        break;
                }
                ReadReceiptPolicy readReceiptPolicy;
                int readReceiptPolicyInt = contact.optInt(ThreemaSafeContactsSection.TAG_READ_RECEIPTS, 0);
                switch (readReceiptPolicyInt) {
                    case 0:
                        readReceiptPolicy = ReadReceiptPolicy.DEFAULT;
                        break;
                    case 1:
                        readReceiptPolicy = ReadReceiptPolicy.SEND;
                        break;
                    case 2:
                        readReceiptPolicy = ReadReceiptPolicy.DONT_SEND;
                        break;
                    default:
                        logger.warn("Invalid read receipt policy value in safe backup: {}", readReceiptPolicyInt);
                        readReceiptPolicy = ReadReceiptPolicy.DEFAULT;
                        break;
                }

                @NonNull ConversationCategory conversationCategory = contact.optBoolean(ThreemaSafeContactsSection.TAG_PRIVATE, false)
                    ? ConversationCategory.PROTECTED
                    : ConversationCategory.DEFAULT;

                @Nullable Instant lastUpdate;
                if (!contact.isNull(ThreemaSafeContactsSection.TAG_LAST_UPDATE)) {
                    lastUpdate = Instant.ofEpochMilli(contact.getLong(ThreemaSafeContactsSection.TAG_LAST_UPDATE));
                } else {
                    lastUpdate = null;
                }

                ContactModelData contactModelData = ContactModelData.javaCreate(
                    identity,
                    publicKey,
                    createdAt,
                    lastUpdate,
                    firstName,
                    lastName,
                    nickname,
                    IdColor.ofIdentity(identity),
                    verificationLevel,
                    workVerificationLevel,
                    identityType,
                    acquaintanceLevel,
                    identityState,
                    BigInteger.valueOf(featureMask),
                    ContactSyncState.INITIAL,
                    readReceiptPolicy,
                    typingIndicatorPolicy,
                    ConversationVisibility.NORMAL,
                    conversationCategory,
                    null,
                    null,
                    true,
                    null,
                    null,
                    null,
                    null,
                    AvailabilityStatus.None.INSTANCE,
                    null
                );

                // Note that persisting it (without reflection) is ok here as we can only restore a safe backup if multi device is not active.
                contactModelRepository.persistNewContact(contactModelData);
            } catch (JSONException | IllegalArgumentException | ContactStoreException e) {
                logger.error("Failed to restore contact", e);
            }
        }
    }

    private @Nullable APIConnector.FetchIdentityResult getFetchResultByIdentity(
        @NonNull List<APIConnector.FetchIdentityResult> results,
        @NonNull String identity
    ) {
        for (APIConnector.FetchIdentityResult result : results) {
            if (identity.equals(result.identity)) {
                return result;
            }
        }
        return null;
    }

    private void restoreGroups(JSONArray groups) {
        if (groups == null) return;

        for (int i = 0; i < groups.length(); i++) {
            try {
                JSONObject group = groups.getJSONObject(i);
                if (group.optBoolean(ThreemaSafeGroupsSection.TAG_DELETED, false)) {
                    // do not restore groups that have the legacy 'deleted' flag
                    continue;
                }

                String creatorIdentity = group.getString(ThreemaSafeGroupsSection.TAG_CREATOR);

                // do not create group if creator no longer exists (i.e. was revoked)
                if (contactService.getByIdentity(creatorIdentity) != null) {
                    long createdAtValue = group.optLong(ThreemaSafeGroupsSection.TAG_CREATED_AT, 0L);
                    if (createdAtValue < 0) {
                        // Due to a bug in older versions, safe backups with a negative createdAt timestamp might exist
                        createdAtValue = 0;
                    }
                    Instant createdAt = Instant.ofEpochMilli(createdAtValue);

                    GroupId groupId = new GroupId(group.getString(ThreemaSafeGroupsSection.TAG_ID).toLowerCase());
                    GroupIdentity groupIdentity = new GroupIdentity(
                        creatorIdentity,
                        groupId.toLong()
                    );
                    String name = group.optString(ThreemaSafeGroupsSection.TAG_NAME, "");
                    Instant lastUpdate = null;
                    if (!group.isNull(ThreemaSafeGroupsSection.TAG_LAST_UPDATE)) {
                        long lastUpdateValue = group.getLong(ThreemaSafeGroupsSection.TAG_LAST_UPDATE);
                        if (lastUpdateValue < 0) {
                            // Due to a bug in older versions, safe backups with a negative lastUpdateAt timestamp might exist
                            lastUpdateValue = 0L;
                        }
                        lastUpdate = Instant.ofEpochMilli(lastUpdateValue);
                    }

                    ConversationCategory conversationCategory = group.optBoolean(ThreemaSafeGroupsSection.TAG_PRIVATE, false)
                        ? ConversationCategory.PROTECTED
                        : ConversationCategory.DEFAULT;

                    String myIdentity = userService.getIdentity();
                    boolean userIsMember = false;
                    boolean creatorIsMember = false;
                    Set<String> otherMembers = new HashSet<>();

                    JSONArray members = group.getJSONArray(ThreemaSafeGroupsSection.TAG_MEMBERS);
                    for (int j = 0; j < members.length(); j++) {
                        String identity = members.getString(j);
                        if (!isNullOrEmpty(identity)) {
                            if (contactService.getByIdentity(identity) == null) {
                                // Fetch group contact if not in contact list. Note that we do
                                // not add the contact to the group if it couldn't be created.
                                ContactResult result = new BasicAddOrUpdateContactBackgroundTask(
                                    identity,
                                    AcquaintanceLevel.GROUP_OR_DELETED,
                                    validContactsLookupSteps,
                                    contactModelRepository,
                                    AddContactRestrictionPolicy.IGNORE,
                                    appRestrictions,
                                    null
                                ).runSynchronously();

                                if (!(result instanceof ContactAvailable)) {
                                    logger.error("Group member contact {} could not be created", identity);
                                    continue;
                                }
                            }

                            if (identity.equals(myIdentity)) {
                                userIsMember = true;
                            } else if (identity.equals(creatorIdentity)) {
                                creatorIsMember = true;
                            } else {
                                // Only add the identity as member if it is not the user and not the group creator
                                otherMembers.add(identity);
                            }
                        }
                    }

                    UserState userState;
                    if (userIsMember && !myIdentity.equals(creatorIdentity) && !creatorIsMember) {
                        // If the creator is not a member, the group is considered to be 'orphaned'. This is not allowed, so instead we treat
                        // the group as if we had been kicked from it.
                        userState = UserState.KICKED;
                    } else {
                        userState = userIsMember ? UserState.MEMBER : UserState.LEFT;
                    }

                    GroupModelData groupModelData = new GroupModelData(
                        groupIdentity,
                        name,
                        createdAt,
                        Instant.EPOCH,
                        lastUpdate,
                        ConversationVisibility.NORMAL,
                        conversationCategory,
                        IdColor.invalid(),
                        null,
                        null,
                        otherMembers,
                        userState,
                        null,
                        ConversationSpamReportingState.DEFAULT
                    );

                    groupModelRepository.persistNewGroup(groupModelData);
                }
            } catch (JSONException | NullPointerException | GroupCreateException e) {
                // ignore and continue with next group
            }
        }
    }

    private void restoreDistributionLists(JSONArray distributionLists) {
        if (distributionLists == null) return;

        for (int i = 0; i < distributionLists.length(); i++) {
            try {
                JSONObject distributionList = distributionLists.getJSONObject(i);
                DistributionListModel distributionListModel = new DistributionListModel();

                long createdAt = distributionList.optLong(ThreemaSafeDistributionListsSection.TAG_CREATED_AT, 0L);
                if (createdAt < 0) {
                    // Due to a bug in older versions, safe backups with a negative createdAt timestamp might exist
                    createdAt = 0;
                }

                String id = distributionList.optString(ThreemaSafeDistributionListsSection.TAG_ID);
                if (id.length() == 16) {
                    final byte[] distributionListIdBytes = hexToByteArray(id);
                    distributionListModel.setId(toLong(distributionListIdBytes, ByteOrder.LITTLE_ENDIAN));
                }
                distributionListModel.setName(distributionList.getString(ThreemaSafeDistributionListsSection.TAG_NAME));
                distributionListModel.setCreatedAt(Instant.ofEpochMilli(createdAt));
                if (!distributionList.isNull(ThreemaSafeDistributionListsSection.TAG_LAST_UPDATE)) {
                    long lastUpdate = distributionList.getLong(ThreemaSafeDistributionListsSection.TAG_LAST_UPDATE);
                    if (lastUpdate < 0) {
                        // Due to a bug in older versions, safe backups with a negative lastUpdateAt timestamp might exist
                        lastUpdate = 0;
                    }
                    distributionListModel.setLastUpdate(Instant.ofEpochMilli(lastUpdate));
                }
                if (distributionList.optBoolean(ThreemaSafeDistributionListsSection.TAG_PRIVATE, false)) {
                    distributionListModel.setConversationCategory(ConversationCategory.PROTECTED);
                } else {
                    distributionListModel.setConversationCategory(ConversationCategory.DEFAULT);
                }

                distributionListModelFactory.create(distributionListModel);

                JSONArray members = distributionList.getJSONArray(ThreemaSafeDistributionListsSection.TAG_MEMBERS);
                for (int j = 0; j < members.length(); j++) {
                    String identity = members.getString(j);
                    if (!isNullOrEmpty(identity)) {
                        if (contactService.getByIdentity(identity) == null) {
                            // Fetch contact if not in contact list. Note that we do not add the
                            // contact to the distribution list if it couldn't be created.
                            ContactResult result = new BasicAddOrUpdateContactBackgroundTask(
                                identity,
                                AcquaintanceLevel.DIRECT,
                                validContactsLookupSteps,
                                contactModelRepository,
                                AddContactRestrictionPolicy.IGNORE,
                                appRestrictions,
                                null
                            ).runSynchronously();

                            if (!(result instanceof ContactAvailable)) {
                                logger.error("Contact {} could not be created", identity);
                                continue;
                            }
                        }

                        DistributionListMemberModel distributionListMemberModel = new DistributionListMemberModel();
                        distributionListMemberModel.setIdentity(identity);
                        distributionListMemberModel.setDistributionListId(distributionListModel.getId());
                        distributionListMemberModel.setActive(true);
                        distributionListMemberModelFactory.create(distributionListMemberModel);
                    }
                }
            } catch (JSONException | NullPointerException e) {
                // ignore and continue with next distribution list
            }
        }
    }

    private void restoreSettings(JSONObject settings) {
        boolean syncContactsRestricted = false;
        if (ConfigUtils.isWorkRestricted()) {
            Boolean booleanPreset = appRestrictions.isContactSyncEnabledOrNull();
            if (booleanPreset != null) {
                synchronizedSettingsService.getContactSyncPolicySetting().setFromLocal(booleanPreset);
                syncContactsRestricted = true;
            }
        }

        if (!syncContactsRestricted) {
            synchronizedSettingsService.getContactSyncPolicySetting().setFromLocal(settings.optBoolean(ThreemaSafeSettingsSection.TAG_SYNC_CONTACTS, false));
        }

        synchronizedSettingsService.getUnknownContactPolicySetting().setFromLocal(settings.optBoolean(ThreemaSafeSettingsSection.TAG_BLOCK_UNKNOWN, false));
        synchronizedSettingsService.getTypingIndicatorPolicySetting().setFromLocal(settings.optBoolean(ThreemaSafeSettingsSection.TAG_SEND_TYPING, true));
        synchronizedSettingsService.getReadReceiptPolicySetting().setFromLocal(settings.optBoolean(ThreemaSafeSettingsSection.TAG_READ_RECEIPTS, true));
        synchronizedSettingsService.getO2oCallPolicySetting().setFromLocal(settings.optBoolean(ThreemaSafeSettingsSection.TAG_THREEMA_CALLS, true));
        synchronizedSettingsService.getO2oCallConnectionPolicySetting().setFromLocal(settings.optBoolean(ThreemaSafeSettingsSection.TAG_RELAY_THREEMA_CALLS, false));
        synchronizedSettingsService.getScreenshotPolicySetting().setFromLocal(settings.optBoolean(ThreemaSafeSettingsSection.TAG_DISABLE_SCREENSHOTS, false));
        synchronizedSettingsService.getKeyboardDataCollectionPolicySetting().setFromLocal(settings.optBoolean(ThreemaSafeSettingsSection.TAG_INCOGNITO_KEYBOARD, false));

        setSettingsBlockedContacts(settings.optJSONArray(ThreemaSafeSettingsSection.TAG_BLOCKED_CONTACTS));
        setSettingsSyncExcluded(settings.optJSONArray(ThreemaSafeSettingsSection.TAG_SYNC_EXCLUDED_IDS));
        setSettingsRecentEmojis(settings.optJSONArray(ThreemaSafeSettingsSection.TAG_RECENT_EMOJIS));
    }

    private void parseInfo(@NonNull JSONObject info) throws ThreemaException, JSONException {
        int version = info.getInt(ThreemaSafeInfoSection.TAG_VERSION);
        if (version > PROTOCOL_VERSION) {
            throw new ThreemaException(context.getResources().getString(R.string.backup_version_mismatch));
        }
    }

    @Override
    @Nullable
    public ArrayList<String> searchID(String phone, String email) {
        if (phone != null || email != null) {
            Set<String> phoneNumbers = Collections.singleton(phone);
            Set<String> emailAddresses = Collections.singleton(email);

            try {
                Map<String, APIConnector.MatchIdentityResult> results = apiConnector.matchIdentities(
                    emailAddresses,
                    phoneNumbers,
                    localeService.getCountryIsoCode(),
                    true,
                    identityStore,
                    null
                );
                if (!results.isEmpty()) {
                    return new ArrayList<>(results.keySet());
                }
            } catch (Exception e) {
                logger.error("Exception", e);
            }
        }
        return null;
    }

    private void uploadData(ThreemaSafeServerInfo serverInfo, byte[] data) throws ThreemaException {
        try {
            threemaSafeApiClient.uploadEncryptedBackup(serverInfo, getThreemaSafeBackupId(), data);
        } catch (IOException e) {
            throw new ThreemaException("Failed to upload backup", e);
        } catch (IllegalArgumentException e) {
            throw new ThreemaException(e.getMessage());
        }
    }

    @Nullable
    private byte[] gZipCompress(byte[] uncompressedBytes) {
        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream(uncompressedBytes.length);
            // Use gzip for backward compatibility, but don't actually compress (to avoid compression oracles)
            GzipOutputStream gzipOutputStream = new GzipOutputStream(outputStream, Deflater.NO_COMPRESSION, 512, false);
            gzipOutputStream.write(uncompressedBytes);
            gzipOutputStream.close();
            byte[] compressedBytes = outputStream.toByteArray();
            outputStream.close();
            return compressedBytes;
        } catch (Exception e) {
            logger.error("Error compressing", e);
            return null;
        }
    }

    @Nullable
    private byte[] gZipUncompress(byte[] compressedBytes) {
        byte[] buffer = new byte[16384];
        try {
            GZIPInputStream gzipInputStream = new GZIPInputStream(new ByteArrayInputStream(compressedBytes));
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            int len;
            while ((len = gzipInputStream.read(buffer)) > 0) {
                outputStream.write(buffer, 0, len);
            }
            gzipInputStream.close();
            byte[] uncompressedBytes = outputStream.toByteArray();
            outputStream.close();
            return uncompressedBytes;
        } catch (Exception e) {
            logger.error("Error uncompressing", e);
            return null;
        }
    }

    private void setSettingsBlockedContacts(JSONArray blockedContacts) {
        if (blockedContacts == null) return;

        for (int i = 0; i < blockedContacts.length(); i++) {
            try {
                blockedIdentitiesService.blockIdentity(blockedContacts.getString(i), null);
            } catch (JSONException e) {
                // ignore invalid entry
            }
        }
    }

    private void setSettingsSyncExcluded(JSONArray excludedIdentities) {
        if (excludedIdentities == null) return;

        for (int i = 0; i < excludedIdentities.length(); i++) {
            try {
                excludedSyncIdentitiesService.excludeFromSync(
                    excludedIdentities.getString(i),
                    TriggerSource.LOCAL
                );
            } catch (JSONException e) {
                // ignore invalid entry
            }
        }
    }

    private void setSettingsRecentEmojis(JSONArray recentEmojis) {
        if (recentEmojis == null) return;

        LinkedList<String> emojiList = new LinkedList<>();

        for (int i = 0; i < recentEmojis.length(); i++) {
            try {
                emojiList.add(recentEmojis.getString(i));
            } catch (JSONException e) {
                // ignore invalid entry
            }
        }

        if (!emojiList.isEmpty()) {
            preferenceService.setRecentEmojis(emojiList);
        }
    }

    /**
     * Generate and return the Threema Safe JSON string.
     * <p>
     * If an error occurs, `null` is returned.
     */
    @Nullable
    private String getSafeJson() {
        JsonObject safeJson = threemaSafeBackupCreator.createThreemaSafeBackup();
        if (safeJson == null) {
            return null;
        }
        return JsonExtensionsKt.toString(safeJson, "");
    }

    public static class UploadSizeExceedException extends Exception {
        UploadSizeExceedException(String e) {
            super(e);
        }
    }
}
