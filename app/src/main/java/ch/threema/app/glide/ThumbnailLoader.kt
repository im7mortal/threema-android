package ch.threema.app.glide

import android.graphics.Bitmap
import ch.threema.app.services.FileService
import ch.threema.storage.models.MessageModel
import com.bumptech.glide.load.Options
import com.bumptech.glide.load.model.ModelLoader
import com.bumptech.glide.signature.ObjectKey
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class ThumbnailLoader : ModelLoader<MessageModel, Bitmap>, KoinComponent {

    private val fileService: FileService by inject()

    override fun buildLoadData(
        model: MessageModel,
        width: Int,
        height: Int,
        options: Options,
    ): ModelLoader.LoadData<Bitmap> =
        ModelLoader.LoadData(
            ObjectKey(model),
            ThumbnailFetcher(
                fileService = fileService,
                messageModel = model,
            ),
        )

    override fun handles(model: MessageModel) = true
}
