package ch.threema.storage.models

class ContactMessageModel : MessageModel() {
    override fun toString() = "ContactMessageModel(id=$id)"

    companion object {
        const val TABLE = "message"
    }
}
