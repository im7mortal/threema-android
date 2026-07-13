package ch.threema.storage.models.data.media;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Map;

import ch.threema.app.utils.FileUtil;
import ch.threema.app.utils.MimeUtil;

import static ch.threema.base.utils.LoggingKt.getThreemaLogger;

import ch.threema.domain.protocol.csp.messages.file.FileData;

public class FileDataModel implements MediaMessageDataInterface {
    private static final Logger logger = getThreemaLogger("FileDataModel");

    public static final String METADATA_KEY_DURATION = "d";
    public static final String METADATA_KEY_WIDTH = "w";
    public static final String METADATA_KEY_HEIGHT = "h";
    public static final String METADATA_KEY_ANIMATED = "a";

    /**
     * When a message of type "image" is received, it is transformed into a file message, because the "image" type is deprecated.
     * Image messages use a non-static nonce for encryption, so in order to decrypt the blob of this file, the nonce is stored in the
     * metaData object using this key.
     * Once the blob is downloaded and decrypted, the metaData value is no longer needed.
     * This key is not part of the protocol, it is only used locally by the Android app.
     */
    public static final String METADATA_KEY_LEGACY_NONCE = "_legacy_nonce";

    private byte[] fileBlobId;
    private byte[] encryptionKey;
    private String mimeType;
    @Nullable
    private String thumbnailMimeType;
    private long fileSize;
    @Nullable
    private String fileName;
    @FileData.RenderingType
    private int renderingType;
    private boolean isDownloaded;
    private String caption;
    @Nullable
    private Map<String, Object> metaData;

    /**
     * @return A new instance of {@code FileDataModel} with the field {@code isDownloaded} set to {@code false} (as its an incoming message file data).
     */
    @NonNull
    public static FileDataModel fromIncomingFileData(@NonNull FileData fileData) {
        return new FileDataModel(
            /* fileBlobId = */ fileData.getFileBlobId(),
            /* encryptionKey = */ fileData.getEncryptionKey(),
            /* mimeType = */ fileData.getMimeType(),
            /* thumbnailMimeType = */ fileData.getThumbnailMimeType(),
            /* fileSize = */ fileData.getFileSize(),
            /* fileName = */ FileUtil.sanitizeFileName(fileData.getFileName()),
            /* renderingType = */ fileData.getRenderingType(),
            /* caption = */ fileData.getCaption(),
            /* isDownloaded = */ false,
            /* metaData = */ fileData.getMetaData()
        );
    }

    // incoming
    public FileDataModel(
        byte[] fileBlobId,
        byte[] encryptionKey,
        String mimeType,
        @Nullable String thumbnailMimeType,
        long fileSize,
        @Nullable String fileName,
        @FileData.RenderingType int renderingType,
        String caption,
        boolean isDownloaded,
        @Nullable Map<String, Object> metaData
    ) {
        this.fileBlobId = fileBlobId;
        this.encryptionKey = encryptionKey;
        this.mimeType = mimeType;
        this.thumbnailMimeType = thumbnailMimeType;
        this.fileSize = fileSize;
        this.fileName = fileName;
        this.renderingType = renderingType;
        this.caption = caption;
        this.isDownloaded = isDownloaded;
        this.metaData = metaData;
    }

    // outgoing
    public FileDataModel(
        String mimeType,
        @Nullable String thumbnailMimeType,
        long fileSize,
        @Nullable String fileName,
        @FileData.RenderingType int renderingType,
        String caption,
        boolean isDownloaded,
        @Nullable Map<String, Object> metaData
    ) {
        this.mimeType = mimeType;
        this.thumbnailMimeType = thumbnailMimeType;
        this.fileSize = fileSize;
        this.fileName = fileName;
        this.renderingType = renderingType;
        this.caption = caption;
        this.isDownloaded = isDownloaded;
        this.metaData = metaData;
    }

    private FileDataModel() {
    }

    public void setCaption(String caption) {
        this.caption = caption;
    }

    public void setFileName(@Nullable String fileName) {
        this.fileName = fileName;
    }

    public void setRenderingType(@FileData.RenderingType int renderingType) {
        this.renderingType = renderingType;
    }

    public void setBlobId(byte[] blobId) {
        this.fileBlobId = blobId;
    }

    @Override
    public byte[] getBlobId() {
        return this.fileBlobId;
    }

    public void setEncryptionKey(byte[] encryptionKey) {
        this.encryptionKey = encryptionKey;
    }

    @Override
    public byte[] getEncryptionKey() {
        return this.encryptionKey;
    }

    @Override
    public boolean isDownloaded() {
        return this.isDownloaded;
    }

    @Override
    public void isDownloaded(boolean isDownloaded) {
        this.isDownloaded = isDownloaded;
        if (isDownloaded && metaData != null && metaData.containsKey(METADATA_KEY_LEGACY_NONCE)) {
            // Once the file is downloaded, we don't need the nonce anymore and can discard it
            var newMetaData = new HashMap<>(metaData);
            newMetaData.remove(METADATA_KEY_LEGACY_NONCE);
            metaData = newMetaData;
        }
    }

    @Override
    public byte[] getNonce() {
        return new byte[0];
    }

    @NonNull
    public String getMimeType() {
        if (this.mimeType == null) {
            return MimeUtil.MIME_TYPE_DEFAULT;
        }
        return this.mimeType;
    }

    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }

    @Nullable
    public String getThumbnailMimeType() {
        return this.thumbnailMimeType;
    }

    public void setThumbnailMimeType(@Nullable String thumbnailMimeType) {
        this.thumbnailMimeType = thumbnailMimeType;
    }

    public void setFileSize(long fileSize) {
        this.fileSize = fileSize;
    }

    public long getFileSize() {
        return this.fileSize;
    }

    public @Nullable String getFileName() {
        return this.fileName;
    }

    public @FileData.RenderingType int getRenderingType() {
        return this.renderingType;
    }

    @Nullable
    public String getCaption() {
        return this.caption;
    }

    @Nullable
    public Map<String, Object> getMetaData() {
        return this.metaData;
    }

    public void setMetaData(@Nullable Map<String, Object> metaData) {
        this.metaData = metaData;
    }

    @Nullable
    public Integer getMetaDataInt(String metaDataKey) {
        return this.metaData != null
            && this.metaData.containsKey(metaDataKey)
            && this.metaData.get(metaDataKey) instanceof Number ?
            (Integer) this.metaData.get(metaDataKey) : null;
    }

    @Nullable
    public String getMetaDataString(String metaDataKey) {
        return this.metaData != null
            && this.metaData.containsKey(metaDataKey)
            && this.metaData.get(metaDataKey) instanceof String ?
            (String) this.metaData.get(metaDataKey) : null;
    }

    @Nullable
    public Boolean getMetaDataBool(String metaDataKey) {
        return this.metaData != null
            && this.metaData.containsKey(metaDataKey)
            && this.metaData.get(metaDataKey) instanceof Boolean ?
            (Boolean) this.metaData.get(metaDataKey) : null;
    }

    @Nullable
    public Float getMetaDataFloat(String metaDataKey) {
        if (this.metaData != null && this.metaData.containsKey(metaDataKey)) {
            final @Nullable Object value = this.metaData.get(metaDataKey);
            if (value == null) {
                return null;
            }
            if (value instanceof Number) {
                if (value instanceof Double) {
                    return ((Double) value).floatValue();
                } else if (value instanceof Float) {
                    return (Float) value;
                } else if (value instanceof Integer) {
                    return ((Integer) value).floatValue();
                } else {
                    return 0F;
                }
            }
        }
        return null;
    }

    /**
     * Return the duration in SECONDS as set in the metadata field.
     */
    public long getDurationSeconds() {
        try {
            Float durationF = getMetaDataFloat(METADATA_KEY_DURATION);
            if (durationF != null) {
                return Math.round(durationF);
            }
        } catch (Exception ignored) {
        }
        return 0L;
    }

    /**
     * Note: Floats are converted to long integers. No rounding.
     *
     * @return The value in the meta-data-map for key {@code d} converted to milliseconds or {@code 0L} as fallback.
     */
    public long getDurationMs() {
        try {
            @Nullable Float durationF = getMetaDataFloat(METADATA_KEY_DURATION);
            if (durationF != null) {
                durationF *= 1000F;
                return durationF.longValue();
            }
        } catch (Exception exception) {
            logger.warn("Ignored exception", exception);
        }
        return 0L;
    }

    @Override
    public String toString() {
        try {
            return FileDataModelSerializer.serializeFileDataBody(
                fileBlobId,
                encryptionKey,
                mimeType,
                fileSize,
                fileName,
                renderingType,
                isDownloaded,
                caption,
                thumbnailMimeType,
                metaData
            );
        } catch (Exception e) {
            logger.error("Failed to encode file data model", e);
            return null;
        }
    }

    @NonNull
    public static FileDataModel create(@NonNull String s) {
        if (s.isEmpty()) {
            return createEmpty();
        }
        FileDataModel model = FileDataModelSerializer.deserializeFileDataBody(s);
        if (model == null) {
            return createEmpty();
        }
        return model;
    }

    /**
     * Do not use this in new code. It only exists to handle places where a [FileModel] needs to be returned and `null` is not allowed.
     */
    @NonNull
    @Deprecated()
    public static FileDataModel createEmpty() {
        return new FileDataModel();
    }
}
