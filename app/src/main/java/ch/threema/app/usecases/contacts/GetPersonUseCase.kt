package ch.threema.app.usecases.contacts

import androidx.core.app.Person
import androidx.core.graphics.drawable.IconCompat
import ch.threema.android.buildPerson
import ch.threema.app.services.ContactService
import ch.threema.base.SessionScoped
import ch.threema.data.datatypes.AndroidContactLookupInfo
import ch.threema.data.datatypes.ContactConversationId
import ch.threema.domain.types.IdentityString

@SessionScoped
class GetPersonUseCase(
    private val contactService: ContactService,
) {
    fun call(
        identity: IdentityString,
        androidContactLookupInfo: AndroidContactLookupInfo?,
        name: String,
    ): Person = buildPerson {
        setKey(
            ContactConversationId(identity).obfuscated.value,
        )
        setName(name)

        contactService
            .getAvatar(identity, false)
            ?.let { profilePicture ->
                setIcon(IconCompat.createWithBitmap(profilePicture))
            }

        androidContactLookupInfo
            ?.getContactUri()
            ?.let { contactUri ->
                setUri(contactUri.toString())
            }
    }
}
