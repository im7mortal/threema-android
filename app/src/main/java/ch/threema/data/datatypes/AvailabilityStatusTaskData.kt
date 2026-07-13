package ch.threema.data.datatypes

import ch.threema.domain.types.IdentityString
import kotlinx.serialization.Serializable

@Serializable
data class AvailabilityStatusTaskData(
    val identity: IdentityString,
    val category: Int,
    val description: String,
)
