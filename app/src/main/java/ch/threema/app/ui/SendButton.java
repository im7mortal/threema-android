package ch.threema.app.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.TransitionDrawable;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;

import ch.threema.app.R;

public class SendButton extends FrameLayout {
    private static final int STATE_SEND = 1;
    private static final int STATE_RECORD = 2;
    private static final int TRANSITION_DURATION_MS = 150;

    private Drawable backgroundEnabled, backgroundDisabled;
    private Context context;
    private @Nullable TransitionDrawable transitionDrawable;
    private int currentState;
    private final Object currentStateLock = new Object();

    public SendButton(Context context) {
        this(context, null);
    }

    public SendButton(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public SendButton(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init(context);
    }

    private void init(@NonNull Context context) {
        LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        inflater.inflate(R.layout.send_button, this);

        this.context = context;

        this.backgroundEnabled = ResourcesCompat.getDrawable(getResources(), R.drawable.ic_circle_send, context.getTheme());
        this.backgroundDisabled = ResourcesCompat.getDrawable(getResources(), R.drawable.ic_circle_send_disabled, context.getTheme());
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        AppCompatImageView icon = this.findViewById(R.id.icon);

        final @Nullable Drawable sendDrawable = ContextCompat.getDrawable(getContext(), R.drawable.ic_send);
        final @Nullable Drawable micDrawable = ContextCompat.getDrawable(getContext(), R.drawable.ic_microphone);

        if (sendDrawable != null && micDrawable != null) {
            BitmapDrawable sendBitmap = toBitmapDrawable(sendDrawable);
            BitmapDrawable micBitmap = toBitmapDrawable(micDrawable);

            if (sendBitmap != null && micBitmap != null) {
                this.transitionDrawable = new TransitionDrawable(new Drawable[]{
                    sendBitmap,
                    micBitmap
                });
                this.transitionDrawable.setCrossFadeEnabled(true);
                icon.setImageDrawable(this.transitionDrawable);
            } else {
                // Fallback: no transition, just show the send icon
                icon.setImageDrawable(sendDrawable);
            }
        }

        synchronized (currentStateLock) {
            if (this.transitionDrawable != null) {
                this.transitionDrawable.resetTransition();
            }
            currentState = STATE_SEND;
        }
    }

    @Nullable
    private BitmapDrawable toBitmapDrawable(Drawable drawable) {
        if (drawable instanceof BitmapDrawable) {
            return (BitmapDrawable) drawable;
        }
        try {
            int width = drawable.getIntrinsicWidth();
            int height = drawable.getIntrinsicHeight();
            if (width <= 0 || height <= 0) {
                // Fallback size if drawable has no intrinsic dimensions
                width = 24;
                height = 24;
            }
            final @NonNull Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            drawable.setBounds(0, 0, width, height);
            drawable.draw(canvas);
            return new BitmapDrawable(getResources(), bitmap);
        } catch (Exception | OutOfMemoryError e) {
            return null;
        }
    }

    public void setSend() {
        synchronized (currentStateLock) {
            if (currentState != STATE_SEND) {
                if (this.transitionDrawable != null) {
                    this.transitionDrawable.reverseTransition(TRANSITION_DURATION_MS);
                    setContentDescription(this.context.getString(R.string.send));
                    currentState = STATE_SEND;
                }
            }
        }
    }

    public void setRecord() {
        synchronized (currentStateLock) {
            if (currentState != STATE_RECORD) {
                if (this.transitionDrawable != null) {
                    this.transitionDrawable.startTransition(TRANSITION_DURATION_MS);
                    setContentDescription(this.context.getString(R.string.voice_message_record));
                    currentState = STATE_RECORD;
                }
            }
        }
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        setBackground(enabled ? this.backgroundEnabled : this.backgroundDisabled);
        if (!enabled) {
            setSend();
        }
    }
}
