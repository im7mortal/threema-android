package ch.threema.app.restrictions

@Suppress("unused")
class NullAppRestrictionProvider : AppRestrictionProvider {
    override val hasRestrictions: Boolean
        get() = false

    override fun getBooleanRestriction(restriction: String) = null

    override fun getStringRestriction(restriction: String) = null

    override fun getIntRestriction(restriction: String) = null
}
