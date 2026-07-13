package ch.threema.app.webclient.services.instance.message.updater

import androidx.annotation.AnyThread
import androidx.annotation.WorkerThread
import ch.threema.app.typingindicator.TypingIndicatorProvider
import ch.threema.app.utils.executor.HandlerExecutor
import ch.threema.app.webclient.Protocol
import ch.threema.app.webclient.converter.Contact
import ch.threema.app.webclient.converter.ContactTyping
import ch.threema.app.webclient.exceptions.ConversionException
import ch.threema.app.webclient.services.instance.MessageDispatcher
import ch.threema.app.webclient.services.instance.MessageUpdater
import ch.threema.base.utils.getThreemaLogger
import ch.threema.common.DispatcherProvider
import ch.threema.common.takeUnlessEmpty
import ch.threema.domain.types.Identity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.msgpack.core.MessagePackException

private val logger = getThreemaLogger("TypingUpdateHandler")

@WorkerThread
class TypingUpdateHandler @AnyThread constructor(
    private val handler: HandlerExecutor,
    private val dispatcher: MessageDispatcher,
) : MessageUpdater(Protocol.SUB_TYPE_TYPING), KoinComponent {

    private val dispatcherProvider: DispatcherProvider by inject()
    private val typingIndicatorProvider: TypingIndicatorProvider by inject()

    private var coroutineScope: CoroutineScope? = null

    override fun register() {
        coroutineScope?.cancel()
        coroutineScope = CoroutineScope(dispatcherProvider.worker)
        var previousIdentities = setOf<Identity>()
        coroutineScope?.launch {
            typingIndicatorProvider.watchTypingIdentities().collect { identities ->
                previousIdentities.minus(identities)
                    .takeUnlessEmpty()
                    ?.let { noLongerTyping ->
                        handler.post {
                            noLongerTyping.forEach { identity ->
                                update(identity, false)
                            }
                        }
                    }

                identities.minus(previousIdentities)
                    .takeUnlessEmpty()
                    ?.let { newlyTyping ->
                        handler.post {
                            newlyTyping.forEach { identity ->
                                update(identity, true)
                            }
                        }
                    }
                previousIdentities = identities
            }
        }
    }

    /**
     * This method can be safely called multiple times without any negative side effects
     */
    override fun unregister() {
        coroutineScope?.cancel()
        coroutineScope = null
    }

    private fun update(contactIdentity: Identity, isTyping: Boolean) {
        try {
            val args = Contact.fromIdentity(contactIdentity.value)
            val data = ContactTyping.convert(isTyping)

            logger.debug("Sending typing update")
            send(dispatcher, data, args)
        } catch (e: ConversionException) {
            logger.error("Exception", e)
        } catch (e: MessagePackException) {
            logger.error("Exception", e)
        }
    }
}
