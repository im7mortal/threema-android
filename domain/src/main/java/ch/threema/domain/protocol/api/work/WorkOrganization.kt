package ch.threema.domain.protocol.api.work

import kotlinx.serialization.Serializable

@Serializable
data class WorkOrganization(
    var name: String? = null,
)
