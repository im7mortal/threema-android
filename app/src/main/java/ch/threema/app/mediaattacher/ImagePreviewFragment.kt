package ch.threema.app.mediaattacher

import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import ch.threema.app.R
import ch.threema.app.utils.logScreenVisibility
import ch.threema.base.utils.getThreemaLogger
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.bumptech.glide.request.target.CustomViewTarget
import com.bumptech.glide.request.transition.Transition
import com.davemorrissey.labs.subscaleview.ImageSource
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView

private val logger = getThreemaLogger("ImagePreviewFragment")

class ImagePreviewFragment(
    private val mediaAttachItem: MediaAttachItem,
) : Fragment() {
    init {
        logScreenVisibility(logger)
        setRetainInstance(true)
    }

    private var rootView: View? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        rootView = inflater.inflate(R.layout.fragment_image_preview, container, false)
        return rootView
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val rootView = rootView ?: return

        val scaleImageView: SubsamplingScaleImageView = rootView.findViewById(R.id.scale_image_view)
        val imageView: ImageView = rootView.findViewById(R.id.image_view)

        if (isAnimated()) {
            Glide.with(this)
                .load(mediaAttachItem.uri)
                .transition(DrawableTransitionOptions.withCrossFade())
                .optionalFitCenter()
                .error(R.drawable.ic_baseline_broken_image_200)
                .into(imageView)
        } else {
            Glide.with(this)
                .load(mediaAttachItem.uri)
                .transition(DrawableTransitionOptions.withCrossFade())
                .optionalCenterInside()
                .error(R.drawable.ic_baseline_broken_image_200)
                .into(
                    object : CustomViewTarget<SubsamplingScaleImageView?, Drawable>(scaleImageView) {
                        override fun onLoadFailed(errorDrawable: Drawable?) = Unit

                        override fun onResourceReady(resource: Drawable, transition: Transition<in Drawable>?) {
                            if (resource is BitmapDrawable) {
                                scaleImageView.setImage(ImageSource.bitmap(resource.bitmap))
                                scaleImageView.isVisible = true
                                imageView.isVisible = false
                            } else {
                                Glide.with(this@ImagePreviewFragment)
                                    .load(resource)
                                    .optionalFitCenter()
                                    .error(R.drawable.ic_baseline_broken_image_200)
                                    .into(imageView)
                            }
                        }

                        override fun onResourceCleared(placeholder: Drawable?) = Unit
                    },
                )
        }
    }

    private fun isAnimated(): Boolean =
        mediaAttachItem.type == MediaAttachItem.TYPE_GIF ||
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && mediaAttachItem.type == MediaAttachItem.TYPE_WEBP)
}
