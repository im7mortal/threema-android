package ch.threema.app.restrictions

interface AppRestrictionProvider {
    val hasRestrictions: Boolean

    fun getBooleanRestriction(restriction: String): Boolean?

    fun getStringRestriction(restriction: String): String?

    fun getIntRestriction(restriction: String): Int?
}
