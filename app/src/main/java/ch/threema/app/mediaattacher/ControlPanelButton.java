package ch.threema.app.mediaattacher;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.TypedArray;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.core.graphics.ColorUtils;
import androidx.core.widget.ImageViewCompat;

import ch.threema.app.R;
import ch.threema.app.utils.ConfigUtils;

public class ControlPanelButton extends FrameLayout {
    private @NonNull AppCompatImageView labelImageView;
    private @NonNull TextView labelTextView;

    public ControlPanelButton(Context context) {
        this(context, null);
    }

    public ControlPanelButton(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ControlPanelButton(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs);
    }

    private void init(@NonNull Context context, @Nullable AttributeSet attrs) {
        ((LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE)).inflate(R.layout.button_media_attach, this);

        this.labelImageView = findViewById(R.id.image);
        this.labelTextView = findViewById(R.id.label);

        if (attrs != null) {
            try (final @NonNull TypedArray attributes = context.obtainStyledAttributes(attrs, R.styleable.ControlPanelButton)) {

                // Image background
                final @ColorInt int fillColor = attributes.getColor(
                    R.styleable.ControlPanelButton_fillColor,
                    ConfigUtils.getColorFromAttribute(context, R.attr.attach_button_background)
                );
                final @ColorInt int strokeColor = attributes.getColor(R.styleable.ControlPanelButton_strokeColor, -1);
                final int fillColorAlpha = attributes.getInt(R.styleable.ControlPanelButton_fillColorAlpha, -1);
                setFillAndStrokeColor(context, fillColor, strokeColor, fillColorAlpha);

                // Image foreground
                labelImageView.setImageResource(
                    attributes.getResourceId(R.styleable.ControlPanelButton_labelIcon, R.drawable.ic_image_outline)
                );
                final @ColorInt int foregroundColor = attributes.getColor(
                    R.styleable.ControlPanelButton_foregroundColor,
                    ConfigUtils.getColorFromAttribute(context, R.attr.colorOnSurface)
                );
                ImageViewCompat.setImageTintList(labelImageView, ColorStateList.valueOf(foregroundColor));

                // Label
                setLabelText(attributes.getResourceId(R.styleable.ControlPanelButton_labelText, R.string.name));
            }
        }
    }

    private void setFillAndStrokeColor(@NonNull Context context, @ColorInt int fillColor, @ColorInt int strokeColor, int fillColorAlpha) {
        try {
            final @NonNull GradientDrawable gradientDrawable = (GradientDrawable) labelImageView.getBackground().mutate();

            if (ConfigUtils.isTheDarkSide(context)) {
                fillColorAlpha += 0x20;
            }

            final @ColorInt int effectiveFillColor = fillColorAlpha >= 0
                ? ColorUtils.setAlphaComponent(fillColor, fillColorAlpha)
                : fillColor;
            gradientDrawable.setColor(effectiveFillColor);

            if (strokeColor != -1) {
                gradientDrawable.setStroke(
                    getResources().getDimensionPixelSize(R.dimen.media_attach_button_stroke_width),
                    strokeColor
                );
            }
        } catch (Exception ignored) {
            // ignored
        }
    }

    public void setLabelText(@StringRes int labelText) {
        this.labelTextView.setText(labelText);
        setContentDescription(getContext().getString(labelText));
    }
}

