package ch.threema.app.ui;

import android.content.Context;
import android.util.AttributeSet;

import androidx.annotation.NonNull;
import ch.threema.app.utils.ContactUtil;
import ch.threema.domain.models.VerificationLevel;
import ch.threema.domain.models.WorkVerificationLevel;

public class VerificationLevelImageView extends androidx.appcompat.widget.AppCompatImageView {

    private final Context context;

    public VerificationLevelImageView(Context context) {
        super(context);
        this.context = context;
    }

    public VerificationLevelImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
        this.context = context;
    }

    public VerificationLevelImageView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        this.context = context;
    }

    /**
     * Sets the view to the provided verification levels.
     */
    public void setVerificationLevel(
        @NonNull VerificationLevel verificationLevel,
        @NonNull WorkVerificationLevel workVerificationLevel
    ) {
        setContentDescription(
            context.getString(
                ContactUtil.getVerificationLevelDescription(
                    verificationLevel,
                    workVerificationLevel
                )
            )
        );
        setImageDrawable(
            ContactUtil.getVerificationDrawable(
                context,
                verificationLevel,
                workVerificationLevel
            )
        );
    }
}
