package ch.threema.app.work

import ch.threema.common.takeUnlessEmpty

data class MdmSource(
    val hasThreemaMdmRestrictions: Boolean,
    val hasExternalMdmRestrictions: Boolean,
) {

    /**
     * Get the source of active mdm parameters in text representation.
     *
     * If at least one Threema-MDM parameter and at least one external MDM parameter is active, "me" is returned.
     * If at least one Threema-MDM parameter is active, "m" is returned.
     * If at least one external MDM parameter is active, "e" is returned.
     *
     * (See "Update Legacy Work Properties" in documentation)
     *
     * @return the source(s) of active mdm parameters as text, null if no mdm parameters are active
     */
    fun getStringRepresentation() = buildString {
        if (hasThreemaMdmRestrictions) {
            append("m")
        }
        if (hasExternalMdmRestrictions) {
            append("e")
        }
    }.takeUnlessEmpty()
}
