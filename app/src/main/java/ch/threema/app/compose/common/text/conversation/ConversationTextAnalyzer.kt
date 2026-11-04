package ch.threema.app.compose.common.text.conversation

import androidx.compose.runtime.Immutable
import ch.threema.android.ResolvableString
import ch.threema.android.ResourceIdString
import ch.threema.app.R
import ch.threema.app.compose.common.text.conversation.ConversationTextAnalyzer.Result.SearchResult
import ch.threema.app.emojis.EmojiParser
import ch.threema.app.emojis.SpriteCoordinates
import ch.threema.data.datatypes.ContactNameFormat
import ch.threema.data.datatypes.MentionNameData
import ch.threema.domain.types.Mention

object ConversationTextAnalyzer {
    /**
     * @param items A map of all found emojis and mentions. They key is the character index from the input string where the characters for
     * the [SearchResult] begin (inclusive).
     * @param containsOnlyEmojis Is true when the only contents of input string are emoji characters. Will be false if the input is empty.
     */
    @Immutable
    data class Result(
        val items: Map<Int, SearchResult>,
        val containsOnlyEmojis: Boolean,
    ) {

        @Immutable
        sealed interface SearchResult {

            /**
             *  The global start index within the searched text (inclusive)
             */
            val startIndex: Int

            val length: Int

            /**
             *  @param length the total character length of the (combined) emoji
             */
            @Immutable
            data class Emoji(
                override val startIndex: Int,
                override val length: Int,
                val spriteCoordinates: SpriteCoordinates,
            ) : SearchResult

            @Immutable
            data class Mention(
                override val startIndex: Int,
                val mention: ch.threema.domain.types.Mention,
            ) : SearchResult {
                override val length = ch.threema.domain.types.Mention.ENCODED_LENGTH
            }
        }

        internal companion object {
            val blank
                get() = Result(
                    items = emptyMap(),
                    containsOnlyEmojis = false,
                )
        }
    }

    /**
     *  Searches for every [SearchResult.Emoji] and [SearchResult.Mention] in the given [rawInput]. The result will contain a map of all found items.
     *  The key of this map is the index in the input string where the [SearchResult] character sequence begins (inclusive).
     */
    fun analyze(
        rawInput: String,
        searchMentions: Boolean,
    ): Result {
        if (rawInput.isBlank()) {
            return Result.blank
        }
        val emojis: Map<Int, SearchResult.Emoji> = searchEmojis(rawInput).associateBy(SearchResult::startIndex)
        val mentions: Map<Int, SearchResult.Mention> = if (searchMentions) {
            searchMentions(rawInput).associateBy(SearchResult::startIndex)
        } else {
            emptyMap()
        }
        val items: Map<Int, SearchResult> = emojis.plus(mentions)
        return Result(
            items = items,
            containsOnlyEmojis = emojis.isNotEmpty() && emojis.values.sumOf(SearchResult::length) == rawInput.length,
        )
    }

    fun searchEmojis(rawInput: String): List<SearchResult.Emoji> {
        if (rawInput.isBlank()) {
            return emptyList()
        }
        val emojiSearchResults = mutableListOf<SearchResult.Emoji>()
        var searchEmojisFromIndex = 0
        while (searchEmojisFromIndex < rawInput.length) {
            runCatching {
                EmojiParser.parseAt(rawInput, searchEmojisFromIndex)
            }.getOrNull()?.let { emojiSearchResult: EmojiParser.ParseResult ->
                emojiSearchResults.add(
                    SearchResult.Emoji(
                        startIndex = searchEmojisFromIndex,
                        length = emojiSearchResult.length,
                        spriteCoordinates = emojiSearchResult.coords,
                    ),
                )
                searchEmojisFromIndex += emojiSearchResult.length - 1
            }
            searchEmojisFromIndex++
        }
        return emojiSearchResults
    }

    fun searchMentions(rawInput: String): List<SearchResult.Mention> {
        if (rawInput.isBlank()) {
            return emptyList()
        }
        return Mention.findMatches(rawInput)
            .map { match ->
                SearchResult.Mention(
                    startIndex = match.startIndex,
                    mention = match.mention,
                )
            }
            .toList()
    }

    /**
     * Search for any mentions in the given [input] and determine the correct display name for it utilising [mentionNameData].
     *
     * **Special mentions:**
     * - A mention for "All users" will result in a display name value of "`All`"
     * - A mention of the current user's own identity **might** result in a display name value of "`Me`". See [MentionNameData.Me.getDisplayName]
     */
    fun findResolvableMentionNames(
        input: String,
        mentionNameData: List<MentionNameData>,
        contactNameFormat: ContactNameFormat,
    ): Map<Mention, ResolvableString> {
        if (input.isBlank()) {
            return emptyMap()
        }
        val mentions: Set<Mention> = Mention.findMatches(input)
            .map(Mention.Match::mention)
            .toSet()
        return mentions
            .mapNotNull { mention ->
                val value = when (mention) {
                    Mention.AllUsers -> ResourceIdString(resId = R.string.all)
                    is Mention.Identity ->
                        mentionNameData
                            .firstOrNull { mentionNameData -> mentionNameData.identity == mention.identity }
                            ?.getDisplayName(contactNameFormat)
                }
                    ?: return@mapNotNull null
                mention to value
            }
            .toMap()
    }
}
