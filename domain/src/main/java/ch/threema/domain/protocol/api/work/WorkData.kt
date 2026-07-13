package ch.threema.domain.protocol.api.work

data class WorkData(
    @JvmField
    val workContacts: List<WorkContact> = emptyList(),
    @JvmField
    val mdm: WorkMDMSettings = WorkMDMSettings(),
    @JvmField
    val directory: WorkDirectorySettings = WorkDirectorySettings(),
    @JvmField
    val organization: WorkOrganization = WorkOrganization(),
    @JvmField
    val logoDark: String? = null,
    @JvmField
    val logoLight: String? = null,
    @JvmField
    val supportUrl: String? = null,
    @JvmField
    val checkInterval: Int = 0,
    @JvmField
    val responseCode: Int = 0,
) {
    companion object {
        @JvmStatic
        fun error(responseCode: Int) =
            WorkData(responseCode = responseCode)
    }
}
