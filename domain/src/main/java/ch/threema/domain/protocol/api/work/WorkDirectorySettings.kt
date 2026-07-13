package ch.threema.domain.protocol.api.work

data class WorkDirectorySettings(
    val enabled: Boolean = false,
    val categories: List<WorkDirectoryCategory> = emptyList(),
)
