package ch.threema.app.glide

import android.graphics.Bitmap
import ch.threema.storage.models.MessageModel
import com.bumptech.glide.load.model.ModelLoader
import com.bumptech.glide.load.model.ModelLoaderFactory
import com.bumptech.glide.load.model.MultiModelLoaderFactory

class ThumbnailLoaderFactory : ModelLoaderFactory<MessageModel, Bitmap> {
    override fun build(unused: MultiModelLoaderFactory): ModelLoader<MessageModel, Bitmap> =
        ThumbnailLoader()

    override fun teardown() {
        // Nothing to do here
    }
}
