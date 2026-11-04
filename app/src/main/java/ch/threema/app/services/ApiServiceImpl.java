package ch.threema.app.services;

import org.json.JSONException;
import org.slf4j.Logger;

import java.io.IOException;
import java.net.URL;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import ch.threema.app.BuildFlavor;
import ch.threema.app.multidevice.MultiDeviceManager;
import ch.threema.base.ThreemaException;
import ch.threema.domain.protocol.ServerAddressProvider;
import ch.threema.domain.protocol.api.APIConnector;
import ch.threema.domain.protocol.blob.BlobLoader;
import ch.threema.domain.protocol.blob.BlobScope;
import ch.threema.domain.protocol.blob.BlobUploader;
import ch.threema.domain.stores.TokenStoreInterface;
import okhttp3.OkHttpClient;

import static ch.threema.base.utils.LoggingKt.getThreemaLogger;

public class ApiServiceImpl implements ApiService {
    private static final Logger logger = getThreemaLogger("ApiServiceImpl");

    private final APIConnector apiConnector;
    private final TokenStoreInterface authTokenStore;
    private final ServerAddressProvider serverAddressProvider;
    private final MultiDeviceManager multiDeviceManager;
    private final OkHttpClient blobDownloadOkHttpClient;
    private final OkHttpClient blobUploadOkHttpClient;

    public ApiServiceImpl(
        APIConnector apiConnector,
        TokenStoreInterface authTokenStore,
        ServerAddressProvider serverAddressProvider,
        MultiDeviceManager multiDeviceManager,
        OkHttpClient blobDownloadOkHttpClient,
        OkHttpClient blobUploadOkHttpClient
    ) {
        this.apiConnector = apiConnector;
        this.authTokenStore = authTokenStore;
        this.serverAddressProvider = serverAddressProvider;
        this.multiDeviceManager = multiDeviceManager;
        this.blobDownloadOkHttpClient = blobDownloadOkHttpClient;
        this.blobUploadOkHttpClient = blobUploadOkHttpClient;
    }

    @NonNull
    @Override
    public BlobUploader createUploader(
        @NonNull byte[] blobData,
        boolean shouldPersist,
        @NonNull BlobScope blobScope
    ) {
        final BlobUploader blobUploader;
        if (multiDeviceManager.isMultiDeviceActive()) {
            blobUploader = BlobUploader.mirror(
                blobUploadOkHttpClient,
                blobData,
                serverAddressProvider,
                null,
                shouldPersist,
                multiDeviceManager.getPropertiesProvider(),
                blobScope
            );
        } else {
            blobUploader = BlobUploader.usual(
                blobUploadOkHttpClient,
                blobData,
                serverAddressProvider,
                null,
                shouldPersist
            );
        }
        return blobUploader;
    }

    @NonNull
    @Override
    public BlobLoader createLoader(@NonNull byte[] blobId) {
        BlobLoader loader;
        if (multiDeviceManager.isMultiDeviceActive()) {
            loader = BlobLoader.mirror(
                blobDownloadOkHttpClient,
                blobId,
                serverAddressProvider,
                null,
                multiDeviceManager.getPropertiesProvider()
            );
        } else {
            loader = BlobLoader.usual(
                blobDownloadOkHttpClient,
                blobId,
                serverAddressProvider,
                null
            );
        }
        return loader;
    }

    @Override
    @Nullable
    public String getAuthToken() throws ThreemaException {
        if (!BuildFlavor.IS_ON_PREM) {
            return null;
        }
        try {
            return apiConnector.obtainAuthToken(authTokenStore, false);
        } catch (IOException | JSONException e) {
            throw new ThreemaException("Cannot obtain authentication token", e);
        }
    }

    @Override
    public void invalidateAuthToken() {
        logger.info("Invalidating auth token");
        this.authTokenStore.storeToken(null);
    }

    @Override
    @NonNull
    public URL getAvatarURL(@NonNull String identity) throws ThreemaException, IOException {
        return new URL(serverAddressProvider.getAvatarServerUrl() + identity);
    }
}
