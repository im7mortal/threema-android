package ch.threema.app.services;

import android.content.Intent;
import android.graphics.Bitmap;
import android.widget.ImageView;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import ch.threema.data.datatypes.ConversationId;
import kotlin.jvm.functions.Function0;

public interface WallpaperService {

    ActivityResultLauncher<Intent> getWallpaperActivityResultLauncher(
        @NonNull Fragment fragment,
        @Nullable Runnable onResultAction,
        @Nullable Function0<ConversationId> getConversationId
    );

    CompletableFuture<Bitmap> getWallpaper(
        @NonNull ConversationId conversationId,
        boolean landscape,
        boolean isTheDarkside
    );

    void removeWallpaper(@NonNull ConversationId conversationId);

    void setupWallpaperBitmap(
        @NonNull ConversationId conversationId,
        ImageView wallpaperView,
        boolean landscape,
        boolean isTheDarkside
    );

    boolean hasGalleryWallpaper(@Nullable ConversationId conversationId);

    void selectWallpaper(
        @NonNull Fragment fragment,
        @NonNull ActivityResultLauncher<Intent> fileSelectionLauncher,
        @Nullable ConversationId conversationId,
        @Nullable Runnable onSuccess
    );

    void deleteAll() throws IOException;

    boolean hasGlobalGalleryWallpaper();

    boolean hasGlobalEmptyWallpaper();
}
