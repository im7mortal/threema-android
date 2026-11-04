package ch.threema.app.emojis;

import android.content.Context;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.RelativeSizeSpan;
import android.text.style.StrikethroughSpan;
import android.util.Pair;
import android.widget.TextView;

import org.koin.java.KoinJavaComponent;

import androidx.annotation.ColorInt;

import java.util.ArrayList;
import java.util.Iterator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.managers.ServiceManager;
import ch.threema.app.preference.service.PreferenceService;
import ch.threema.app.services.ContactService;
import ch.threema.app.services.UserService;
import ch.threema.app.ui.MentionClickableSpan;
import ch.threema.app.ui.MentionSpan;
import ch.threema.app.utils.NameUtil;
import ch.threema.domain.types.Mention;
import kotlin.Lazy;

import static ch.threema.common.JavaCompat.isNullOrEmpty;
import static ch.threema.common.LazyKt.lazy;

public class EmojiMarkupUtil {

    private static final int LARGE_EMOJI_SCALE_FACTOR = 2;
    private static final int LARGE_EMOJI_THRESHOLD = 3;

    @NonNull
    private static final Lazy<EmojiMarkupUtil> instance = lazy(EmojiMarkupUtil::new);
    @NonNull
    private static final Lazy<PreferenceService> preferenceService = KoinJavaComponent.inject(PreferenceService.class);

    @NonNull
    public static EmojiMarkupUtil getInstance() {
        return instance.getValue();
    }

    // the ContactService may not yet exist when the instance is created (e.g. when masterkey is locked)
    @Nullable
    private ContactService getContactService() {
        final @Nullable ServiceManager serviceManager = ServiceManager.get();
        try {
            return (serviceManager != null) ? serviceManager.getContactService() : null;
        } catch (Exception e) {
            return null;
        }
    }

    // the UserService may not yet exist when the instance is created (e.g. when masterkey is locked)
    @Nullable
    private UserService getUserService() {
        final @Nullable ServiceManager serviceManager = ServiceManager.get();
        try {
            return (serviceManager != null) ? serviceManager.getUserService() : null;
        } catch (Exception e) {
            return null;
        }
    }

    @NonNull
    private PreferenceService getPreferenceService() {
        return preferenceService.getValue();
    }

    @NonNull
    public CharSequence addTextSpans(CharSequence text) {
        return addTextSpans(null, text, null, false, false);
    }

    @NonNull
    public CharSequence addTextSpans(Context context, CharSequence text, TextView textView, boolean ignoreMarkup) {
        return addTextSpans(context, text, textView, ignoreMarkup, false);
    }

    @NonNull
    public CharSequence addTextSpans(Context context, CharSequence text, TextView textView, boolean ignoreMarkup, boolean singleScale) {
        return addTextSpans(context, text, textView, ignoreMarkup, ignoreMarkup, singleScale, false);
    }

    /**
     * Add text spans to given CharSequence such as markup, mentions and emojis
     *
     * @param context                   A context
     * @param text                      CharSequence to add text spans to
     * @param textView                  The TextView where the text is going to be located (used for size calculations)
     * @param ignoreMarkup              Do not substitute markups with corresponding Spans
     * @param ignoreMentions            Do not substitute mentions with corresponding Spans
     * @param singleScale               Scale up single emojis in text containing emojis only
     * @param overrideEmojiStyleSetting Override the user's desired emoji style and always use the app supplied emoji set
     * @return CharSequence with spans applied, if any
     */
    @NonNull
    public CharSequence addTextSpans(
        @Nullable
        Context context,
        @Nullable
        CharSequence text,
        @Nullable
        TextView textView,
        boolean ignoreMarkup,
        boolean ignoreMentions,
        boolean singleScale,
        boolean overrideEmojiStyleSetting
    ) {
        if (text == null) {
            return "";
        }

        int length = text.length();

        SpannableStringBuilder builder = new SpannableStringBuilder(text);

        if (length == 0) {
            return builder;
        }

        var preferenceService = getPreferenceService();

        if (context != null && textView != null && (preferenceService.useDefaultEmojiStyle() || overrideEmojiStyleSetting || length <= 5)) {
            ArrayList<Pair<EmojiParser.ParseResult, Integer>> results = new ArrayList<>();
            boolean containsRegularText = false;

            for (int i = 0; i < length; i++) {
                // Try to find emoji at the specified index
                try {
                    final EmojiParser.ParseResult result = EmojiParser.parseAt(text, i);

                    if (result != null && result.length > 0) {
                        // An emoji was found!
                        results.add(new Pair<>(result, i));
                        i += result.length - 1;
                    } else {
                        containsRegularText = true;
                    }
                } catch (Exception e) {
                    containsRegularText = true;
                }
            }

            if (!results.isEmpty()) {
                int scaleFactor = singleScale && preferenceService.isBiggerSingleEmojis() && !containsRegularText && results.size() <= LARGE_EMOJI_THRESHOLD ? LARGE_EMOJI_SCALE_FACTOR : 1;

                if (preferenceService.useDefaultEmojiStyle() || overrideEmojiStyleSetting) {
                    for (Pair<EmojiParser.ParseResult, Integer> result : results) {
                        EmojiDrawable emojiDrawable = EmojiManager.getInstance(context).getEmojiDrawableAsync(result.first.coords);

                        builder.setSpan(new EmojiImageSpan(emojiDrawable, textView, scaleFactor),
                            result.second, result.second + result.first.length,
                            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    }
                } else if (scaleFactor != 1) {
                    for (Pair<EmojiParser.ParseResult, Integer> result : results) {
                        builder.setSpan(new RelativeSizeSpan(scaleFactor),
                            result.second, result.second + result.first.length,
                            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    }
                }
            }
        }

        if (!ignoreMarkup) {
            MarkupParser.getInstance().markify(builder);
        }

        if (!ignoreMentions) {
            if (textView == null) {
                builder = new SpannableStringBuilder(applyTextMentionMarkup(text));
            } else {
                builder = applyMentionMarkup(context, builder);
            }
        }

        return builder;
    }

    private SpannableStringBuilder applyMentionMarkup(Context context, SpannableStringBuilder inputText) {
        return applyMentionMarkup(context, inputText, false);
    }

    private SpannableStringBuilder applyMentionMarkup(Context context, SpannableStringBuilder inputText, boolean isStrikeThrough) {
        Iterator<Mention.Match> iterator = Mention.findMatches(inputText.toString()).iterator();

        if (!iterator.hasNext()) {
            return inputText;
        }

        var matches = new ArrayList<Mention.Match>();
        while (iterator.hasNext()) {
            var match = iterator.next();
            matches.add(match);
        }

        @ColorInt int mentionColor = ContextCompat.getColor(context, R.color.mention_background);
        @ColorInt int invertedMentionColor = ContextCompat.getColor(context, R.color.mention_background_inverted);
        @ColorInt int mentionTextColor = ContextCompat.getColor(context, R.color.mention_text_color);
        @ColorInt int invertedMentionTextColor = ContextCompat.getColor(context, R.color.mention_text_color_inverted);
        int padding = context.getResources().getDimensionPixelSize(R.dimen.mention_padding);
        int radius = context.getResources().getDimensionPixelSize(R.dimen.mention_radius);

        SpannableStringBuilder s = new SpannableStringBuilder(inputText);

        final StrikethroughSpan[] strikethroughSpans = s.getSpans(0, s.length(), StrikethroughSpan.class);
        final MentionSpan[] mentionSpans = s.getSpans(0, s.length(), MentionSpan.class);

        for (int i = matches.size() - 1; i >= 0; i--) {
            var match = matches.get(i);
            int start = match.getStartIndex();
            int end = match.getEndIndex();

            // Check if there is a strike through span surrounding this mention. If there is one,
            // then the line must be drawn explicitly as the mention draws over the text.
            boolean inStrikethroughSpan = isStrikeThrough;
            for (StrikethroughSpan sts : strikethroughSpans) {
                if (s.getSpanStart(sts) <= start && s.getSpanEnd(sts) >= end) {
                    inStrikethroughSpan = true;
                    break;
                }
            }

            MentionSpan mentionSpan = null;
            for (MentionSpan ms : mentionSpans) {
                if (s.getSpanStart(ms) == start) {
                    mentionSpan = ms;
                    break;
                }
            }

            // Create a new mention span if already available, otherwise update the existing span
            if (mentionSpan == null) {
                s.setSpan(
                    new MentionSpan(
                        context.getApplicationContext(),
                        match.getMention(),
                        mentionColor,
                        invertedMentionColor,
                        mentionTextColor,
                        invertedMentionTextColor,
                        padding,
                        radius,
                        inStrikethroughSpan
                    ),
                    start,
                    end,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                );
            } else {
                mentionSpan.setStrikeThroughText(inStrikethroughSpan);
            }
            // hack: https://stackoverflow.com/questions/20069537/replacementspans-draw-method-isnt-called
            if (inputText.length() == end - start) {
                s.append(" ");
            }

            final MentionClickableSpan[] mentionClickableSpans = s.getSpans(start, end, MentionClickableSpan.class);
            if (mentionClickableSpans.length == 0) {
                s.setSpan(new MentionClickableSpan(match.getMention()), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }
        return s;
    }

    /**
     * Replace mentions by text instead of spans (used where spans cannot be displayed, e.g. in notifications)
     *
     * @return CharSequence where all mentions have been replaced by contact names or ids
     */
    private CharSequence applyTextMentionMarkup(CharSequence inputText) {
        CharSequence outputText = inputText;

        Iterator<Mention.Match> iterator = Mention.findMatches(inputText.toString()).iterator();
        while (iterator.hasNext()) {
            var match = iterator.next();
            var mention = match.getMention();

            String prettyMention = "";

            if (mention instanceof Mention.Identity) {
                String identity = ((Mention.Identity) mention).getIdentityString();

                final @Nullable String quoteName;
                final @Nullable ContactService contactService = getContactService();
                final @Nullable UserService userService = getUserService();
                final @NonNull PreferenceService preferenceService = getPreferenceService();
                if (contactService != null && userService != null) {
                    quoteName = NameUtil.getQuoteName(identity, contactService, userService, preferenceService.getContactNameFormat());
                } else {
                    quoteName = null;
                }

                if (!isNullOrEmpty(quoteName)) {
                    prettyMention = MentionSpan.MENTION_INDICATOR + quoteName;
                }
            } else if (mention instanceof Mention.AllUsers) {
                prettyMention = MentionSpan.MENTION_INDICATOR + ThreemaApplication.getAppContext().getString(R.string.all);
            }

            outputText = TextUtils.replace(outputText, new String[]{mention.toString()}, new CharSequence[]{prettyMention});
        }

        return outputText;
    }

    public CharSequence addMarkup(Context context, CharSequence text) {
        SpannableStringBuilder builder = new SpannableStringBuilder(text);

        MarkupParser.getInstance().markify(builder);
        builder = applyMentionMarkup(context, builder);

        return builder;
    }

    public void addMentionMarkup(Context context, CharSequence text) {
        addMentionMarkup(context, text, false);
    }

    public CharSequence addMentionMarkup(Context context, CharSequence text, boolean isStrikeThrough) {
        SpannableStringBuilder builder = new SpannableStringBuilder(text);

        builder = applyMentionMarkup(context, builder, isStrikeThrough);

        return builder;
    }
}


