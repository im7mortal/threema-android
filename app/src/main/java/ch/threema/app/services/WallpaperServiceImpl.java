package ch.threema.app.services;

import static android.app.Activity.RESULT_OK;
import static ch.threema.common.FileExtensionsKt.copyTo;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Parcel;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.Window;
import android.widget.ImageView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.fragment.app.Fragment;

import org.slf4j.Logger;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.concurrent.ExecutionException;

import ch.threema.android.LifecycleAwareAsyncTask;
import ch.threema.app.R;
import ch.threema.app.activities.CropImageActivity;
import ch.threema.app.dialogs.BottomSheetAbstractDialog;
import ch.threema.app.dialogs.BottomSheetListDialog;
import ch.threema.app.dialogs.GenericProgressDialog;
import ch.threema.app.files.AppDirectoryProvider;
import ch.threema.app.files.WallpaperFileHandleProvider;
import ch.threema.app.preference.service.PreferenceService;
import ch.threema.app.ui.BottomSheetItem;
import ch.threema.app.utils.BitmapUtil;
import ch.threema.app.utils.ConfigUtils;
import ch.threema.app.utils.DialogUtil;
import ch.threema.app.utils.FileProviderUtil;
import ch.threema.app.utils.FileUtil;
import ch.threema.app.utils.MimeUtil;

import static ch.threema.base.utils.LoggingKt.getThreemaLogger;
import static ch.threema.common.JavaCompat.copyStream;

import ch.threema.common.files.FileHandle;

import java.util.concurrent.CompletableFuture;

import ch.threema.data.datatypes.ConversationId;
import kotlin.jvm.functions.Function0;

public class WallpaperServiceImpl implements WallpaperService {
    private static final Logger logger = getThreemaLogger("WallpaperServiceImpl");

    private static final String DIALOG_TAG_LOADING_IMAGE = "lit";
    private static final String SELECTOR_TAG_WALLPAPER_DEFAULT = "def";
    private static final String SELECTOR_TAG_WALLPAPER_GALLERY = "gal";
    private static final String SELECTOR_TAG_WALLPAPER_NONE = "none";
    private static final String DIALOG_TAG_SELECT_WALLPAPER = "selwal";

    private final Context appContext;
    private final PreferenceService preferenceService;
    @NonNull
    private final WallpaperFileHandleProvider wallpaperFileHandleProvider;
    @NonNull
    private final AppDirectoryProvider appDirectoryProvider;

    private FileHandle wallpaperCropFile;

    public WallpaperServiceImpl(
        @NonNull Context appContext,
        @NonNull WallpaperFileHandleProvider wallpaperFileHandleProvider,
        @NonNull PreferenceService preferenceService,
        @NonNull AppDirectoryProvider appDirectoryProvider
    ) {
        this.appContext = appContext;
        this.preferenceService = preferenceService;
        this.wallpaperFileHandleProvider = wallpaperFileHandleProvider;
        this.appDirectoryProvider = appDirectoryProvider;
    }

    /**
     * Get the wallpaper activity result launcher. This needs to be called before the fragment is created.
     *
     * @param fragment           the fragment that is launching the image selection activity
     * @param onCropResultAction this runnable is additionally executed when a new wallpaper has been cropped
     * @param getConversationId  this function must return the conversation-id; it will be called when the image selection result is available
     * @return the activity result launcher that is used when triggering a new wallpaper image selection
     */
    @Override
    public ActivityResultLauncher<Intent> getWallpaperActivityResultLauncher(
        @NonNull Fragment fragment,
        @Nullable Runnable onCropResultAction,
        @Nullable Function0<ConversationId> getConversationId
    ) {
        ActivityResultLauncher<Intent> cropLauncher = fragment.registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                final File tempWallpaperFile = getTempWallpaperFile();

                if (result.getResultCode() == Activity.RESULT_OK && wallpaperCropFile != null) {
                    try {
                        copyTo(tempWallpaperFile, wallpaperCropFile);
                        preferenceService.setCustomWallpaperEnabled(true);
                        if (onCropResultAction != null) {
                            onCropResultAction.run();
                        }
                    } catch (IOException e) {
                        logger.error("Failed to copy cropped wallpaper");
                    }
                }
                FileUtil.deleteFileOrWarn(tempWallpaperFile, "deleteCropFile", logger);
            }
        );

        return fragment.registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    onImageSelected(
                        fragment,
                        result.getData(),
                        cropLauncher,
                        getConversationId != null
                            ? getConversationId.invoke()
                            : null
                    );
                }
            }
        );
    }

    @Override
    public void removeWallpaper(@NonNull ConversationId conversationId) {
        try {
            wallpaperFileHandleProvider.get(conversationId).delete();
        } catch (IOException e) {
            logger.error("Failed to delete wallpaper", e);
        }
    }

    @Override
    @AnyThread
    public CompletableFuture<Bitmap> getWallpaper(
        @NonNull ConversationId conversationId,
        boolean landscape,
        boolean isTheDarkside
    ) {
        return CompletableFuture.supplyAsync(
            () -> {
                var fileHandleCustomWallpaper = wallpaperFileHandleProvider.get(conversationId);
                if (fileHandleCustomWallpaper.isEmpty()) {
                    return null;
                }

                Bitmap bitmap = null;
                final BitmapFactory.Options options = new BitmapFactory.Options();
                options.inSampleSize = 1;
                options.inPreferredConfig = Bitmap.Config.RGB_565;

                if (fileHandleCustomWallpaper.exists()) {
                    try (InputStream inputStream = fileHandleCustomWallpaper.read()) {
                        bitmap = BitmapFactory.decodeStream(inputStream, null, options);
                    } catch (Exception e) {
                        logger.error("Failed to read wallpaper file into bitmap", e);
                    }
                }

                if (bitmap == null && preferenceService.isCustomWallpaperEnabled()) {
                    var fileHandleGlobalWallpaper = wallpaperFileHandleProvider.getGlobal();
                    if (fileHandleGlobalWallpaper.exists() && !fileHandleGlobalWallpaper.isEmpty()) {
                        try (InputStream inputStream = fileHandleGlobalWallpaper.read()) {
                            bitmap = BitmapFactory.decodeStream(inputStream, null, options);
                        } catch (Exception e) {
                            logger.error("Failed to read global wallpaper file into bitmap", e);
                        }
                    }
                }

                if (bitmap == null && !hasGlobalEmptyWallpaper() && !ConfigUtils.isWorkBuild()) {
                    final BitmapFactory.Options noptions = new BitmapFactory.Options();
                    noptions.inPreferredConfig = Bitmap.Config.ALPHA_8;
                    noptions.inSampleSize = 1;

                    int resource = isTheDarkside ? R.drawable.wallpaper_dark : R.drawable.wallpaper_light;
                    try {
                        bitmap = BitmapFactory.decodeResource(appContext.getResources(), resource, noptions);
                    } catch (Exception e) {
                        logger.error("Exception", e);
                    }

                    if (bitmap != null && landscape) {
                        return BitmapUtil.rotateBitmap(bitmap, 90);
                    }
                }

                return bitmap;
            }
        );
    }

    @UiThread
    private void setImageView(@Nullable ImageView wallpaperView, @Nullable Bitmap bitmap) {
        if (wallpaperView == null) {
            return;
        }
        if (bitmap != null) {
            wallpaperView.setImageBitmap(bitmap);
            wallpaperView.setVisibility(View.VISIBLE);
        } else {
            wallpaperView.setBackgroundDrawable(null);
            wallpaperView.setVisibility(View.INVISIBLE);
        }
    }

    @Override
    @UiThread
    public void setupWallpaperBitmap(
        @NonNull ConversationId conversationId,
        ImageView wallpaperView,
        boolean landscape,
        boolean isTheDarkside
    ) {
        if (wallpaperView == null) {
            return;
        }
        try {
            final @Nullable Bitmap bitmap = getWallpaper(
                conversationId,
                landscape,
                isTheDarkside
            ).get();
            setImageView(wallpaperView, bitmap);
        } catch (InterruptedException e) {
            logger.error("Exception", e);
            // Restore interrupted state...
            Thread.currentThread().interrupt();
        } catch (ExecutionException e) {
            logger.error("Exception", e);
        }
    }

    @Override
    public void selectWallpaper(
        @NonNull Fragment fragment,
        @NonNull ActivityResultLauncher<Intent> fileSelectionLauncher,
        @Nullable ConversationId conversationId,
        @Nullable Runnable onSuccess
    ) {
        ArrayList<BottomSheetItem> items = new ArrayList<>();

        if (!ConfigUtils.isWorkBuild() || conversationId != null) {
            items.add(
                new BottomSheetItem(
                    conversationId == null
                        ? R.drawable.ic_notification_small
                        : R.drawable.ic_check,
                    conversationId == null
                        ? appContext.getString(R.string.wallpaper_threema, appContext.getString(R.string.app_name))
                        : appContext.getString(R.string.wallpaper_default),
                    SELECTOR_TAG_WALLPAPER_DEFAULT
                )
            );
        }

        items.add(new BottomSheetItem(
                R.drawable.ic_image_outline,
                appContext.getString(R.string.wallpaper_gallery),
                SELECTOR_TAG_WALLPAPER_GALLERY
            )
        );

        items.add(new BottomSheetItem(
                R.drawable.ic_delete_outline,
                appContext.getString(R.string.wallpaper_none),
                SELECTOR_TAG_WALLPAPER_NONE
            )
        );

        int defaultEntry = 0;
        if (conversationId == null) {
            // global
            if (hasGlobalEmptyWallpaper()) {
                defaultEntry = ConfigUtils.isWorkBuild() ? 1 : 2;
            } else if (hasGlobalGalleryWallpaper()) {
                defaultEntry = ConfigUtils.isWorkBuild() ? 0 : 1;
            }
        } else {
            // individual
            try {
                if (hasEmptyWallpaper(conversationId).get()) {
                    defaultEntry = 2;
                } else if (hasGalleryWallpaper(conversationId)) {
                    defaultEntry = 1;
                }
            } catch (InterruptedException e) {
                logger.error("Exception", e);
                // Restore interrupted state...
                Thread.currentThread().interrupt();
            } catch (ExecutionException e) {
                logger.error("Exception", e);
            }
        }

        BottomSheetListDialog dialog = BottomSheetListDialog.newInstance(
            R.string.prefs_title_wallpaper_switch,
            items,
            defaultEntry,
            new BottomSheetAbstractDialog.BottomSheetDialogInlineClickListener() {
                @Override
                public int describeContents() {
                    return 0;
                }

                @Override
                public void writeToParcel(Parcel dest, int flags) {
                }

                @Override
                public void onSelected(String tag, String data) {
                    if (fragment.isAdded()) {
                        switch (tag) {
                            case SELECTOR_TAG_WALLPAPER_DEFAULT:
                                setDefaultWallpaper(conversationId);
                                if (onSuccess != null) {
                                    onSuccess.run();
                                }
                                break;
                            case SELECTOR_TAG_WALLPAPER_GALLERY:
                                selectWallpaperFromGallery(fragment, fileSelectionLauncher);
                                break;
                            case SELECTOR_TAG_WALLPAPER_NONE:
                                setEmptyWallpaper(conversationId);
                                if (onSuccess != null) {
                                    onSuccess.run();
                                }
                                break;
                        }
                    }
                }

                @Override
                public void onCancel(String tag) {
                }
            });
        dialog.show(fragment.getParentFragmentManager(), DIALOG_TAG_SELECT_WALLPAPER);
    }

    private void deleteWallpaperFile(@Nullable ConversationId conversationId) {
        try {
            if (conversationId == null) {
                preferenceService.setCustomWallpaperEnabled(true);
                wallpaperFileHandleProvider.getGlobal().delete();
            } else {
                wallpaperFileHandleProvider.get(conversationId).delete();
            }
        } catch (IOException e) {
            logger.error("Failed to delete wallpaper", e);
        }
    }

    private void setDefaultWallpaper(@Nullable ConversationId conversationId) {
        deleteWallpaperFile(conversationId);
        if (conversationId == null) {
            preferenceService.setCustomWallpaperEnabled(false);
        }
    }

    private void setEmptyWallpaper(@Nullable ConversationId conversationId) {
        deleteWallpaperFile(conversationId);

        if (conversationId == null) {
            preferenceService.setCustomWallpaperEnabled(true);
        }

        // create an empty file
        try {
            if (conversationId == null) {
                wallpaperFileHandleProvider.getGlobal().create();
            } else {
                wallpaperFileHandleProvider.get(conversationId).create();
            }
        } catch (IOException e) {
            logger.error("Failed to create empty wallpaper file", e);
        }
    }

    private void selectWallpaperFromGallery(@NonNull Fragment fragment, ActivityResultLauncher<Intent> imageSelectLauncher) {
        FileUtil.selectFile(fragment.requireContext(), imageSelectLauncher, new String[]{MimeUtil.MIME_TYPE_IMAGE}, false, 0, null);
    }

    @Override
    public boolean hasGalleryWallpaper(@Nullable ConversationId conversationId) {
        if (conversationId != null) {
            var fileHandle = wallpaperFileHandleProvider.get(conversationId);
            return fileHandle.exists() && !fileHandle.isEmpty();
        }
        return false;
    }

    private CompletableFuture<Boolean> hasEmptyWallpaper(@NonNull ConversationId conversationId) {
        return CompletableFuture.supplyAsync(
            () -> wallpaperFileHandleProvider.get(conversationId).isEmpty()
        );
    }

    @Override
    public boolean hasGlobalGalleryWallpaper() {
        var globalWallpaper = wallpaperFileHandleProvider.getGlobal();
        return globalWallpaper.exists() && !globalWallpaper.isEmpty();
    }

    @Override
    public boolean hasGlobalEmptyWallpaper() {
        var globalWallpaper = wallpaperFileHandleProvider.getGlobal();
        return globalWallpaper.exists() && globalWallpaper.isEmpty();
    }

    @Override
    public void deleteAll() throws IOException {
        try {
            wallpaperFileHandleProvider.deleteAll();
        } catch (IOException e) {
            logger.error("Failed to delete wallpapers", e);
        }
    }

    private void onImageSelected(
        @NonNull Fragment fragment,
        @NonNull Intent data,
        @NonNull ActivityResultLauncher<Intent> cropLauncher,
        @Nullable ConversationId conversationId
    ) {
        wallpaperCropFile = conversationId != null
            ? wallpaperFileHandleProvider.get(conversationId)
            : wallpaperFileHandleProvider.getGlobal();

        final File tempWallpaperFile = getTempWallpaperFile();

        new LifecycleAwareAsyncTask<Void, Boolean>() {
            @Override
            protected void onPreExecute() {
                super.onPreExecute();
                GenericProgressDialog.newInstance(
                    R.string.download,
                    R.string.please_wait
                ).show(
                    fragment.getParentFragmentManager(),
                    DIALOG_TAG_LOADING_IMAGE
                );
            }

            @Override
            protected Boolean doInBackground(Void param) {
                Activity activity = fragment.getActivity();
                if (activity != null) {
                    try (
                        InputStream inputStream = activity.getContentResolver().openInputStream(data.getData());
                        FileOutputStream fos = new FileOutputStream(tempWallpaperFile)
                    ) {
                        if (inputStream != null) {
                            copyStream(inputStream, fos);
                            return true;
                        }
                    } catch (Exception e) {
                        logger.error("Exception", e);
                    }
                }
                return false;
            }

            @Override
            protected void onPostExecute(Boolean success) {
                super.onPostExecute(success);
                DialogUtil.dismissDialog(fragment.getParentFragmentManager(), DIALOG_TAG_LOADING_IMAGE, true);
                if (success) {
                    doCrop(fragment, FileProviderUtil.getUriForFile(appContext, tempWallpaperFile), cropLauncher);
                }
            }
        }.execute(fragment, null);
    }

    @NonNull
    private File getTempWallpaperFile() {
        return new File(appDirectoryProvider.getShareDirectory(), ".wallpaper-temp.png");
    }

    private void doCrop(@NonNull Fragment fragment, Uri imageUri, @NonNull ActivityResultLauncher<Intent> launcher) {
        Activity activity = fragment.getActivity();

        DisplayMetrics metrics = new DisplayMetrics();
        activity.getWindowManager().getDefaultDisplay().getMetrics(metrics);
        Rect rectangle = new Rect();
        Window window = activity.getWindow();
        window.getDecorView().getWindowVisibleDisplayFrame(rectangle);

        int width = metrics.widthPixels;
        int height = metrics.heightPixels;

        if (height > width) {
            // portrait
            height -= rectangle.top;
        } else {
            // landscape
            //noinspection SuspiciousNameCombination
            width -= rectangle.top;
        }

        CropImageActivity.CropImageParameters cropImageParameters =
            getCropImageParameters(imageUri, width, height);

        launcher.launch(CropImageActivity.createIntent(activity, cropImageParameters));
    }

    @NonNull
    private CropImageActivity.CropImageParameters getCropImageParameters(Uri imageUri, int width, int height) {
        int y = Math.max(width, height);
        int x = Math.min(width, height);

        CropImageActivity.CropImageParameters cropImageParameters =
            new CropImageActivity.CropImageParameters(
                /* sourceUri = */
                imageUri,
                /* saveUri = */
                imageUri
            );
        cropImageParameters.setAspectX(x);
        cropImageParameters.setAspectY(y);
        cropImageParameters.setMaxWidth(x);
        cropImageParameters.setMaxHeight(y);
        return cropImageParameters;
    }
}
