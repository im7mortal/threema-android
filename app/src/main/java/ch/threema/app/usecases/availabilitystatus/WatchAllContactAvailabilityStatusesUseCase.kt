package ch.threema.app.usecases.availabilitystatus

import ch.threema.app.BuildConfig
import ch.threema.app.eventbus.GlobalEventFlows
import ch.threema.app.eventbus.events.ContactEvent
import ch.threema.common.DispatcherProvider
import ch.threema.data.datatypes.AvailabilityStatus
import ch.threema.data.repositories.ContactModelRepository
import ch.threema.domain.types.IdentityString
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel.Factory.CONFLATED
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onStart

@OptIn(FlowPreview::class)
class WatchAllContactAvailabilityStatusesUseCase(
    private val contactModelRepository: ContactModelRepository,
    private val dispatcherProvider: DispatcherProvider,
    private val globalEventFlows: GlobalEventFlows,
) {

    /**
     *  If the feature is supported by this build, a *cold* [Flow] that emits the most recent [AvailabilityStatus] values for all contacts is
     *  returned. Otherwise, a flow emitting exactly one empty map is returned. The emission of subsequent values is slightly delayed to
     *  avoid having to repeatedly and unnecessarily load all contacts from the database in case of multiple changes to contacts in short succession.
     *
     *  ##### Direct emit promise
     *  This flow fulfills the promise to directly emit the current values.
     *
     *  ##### Overflow strategy
     *  If a consumer consumes the values slower than they get produced, the old unconsumed value gets **dropped** in favor of the most recent value.
     *
     *  ##### Error strategy
     *  Every exception will flow downstream.
     */
    fun call(): Flow<Map<IdentityString, AvailabilityStatus>> {
        if (!BuildConfig.AVAILABILITY_STATUS_ENABLED) {
            return flowOf(emptyMap())
        }
        return globalEventFlows.contacts
            .filter { event ->
                when (event) {
                    is ContactEvent.ContactRemoved,
                    is ContactEvent.ContactUpdated,
                    is ContactEvent.NewContact,
                    -> true
                    is ContactEvent.ContactProfilePictureUpdated -> false
                }
            }
            .debounce(500.milliseconds)
            .map {
                getCurrentAvailabilityStatuses()
            }
            .onStart {
                emit(getCurrentAvailabilityStatuses())
            }
            .buffer(capacity = CONFLATED)
            .flowOn(context = dispatcherProvider.io)
    }

    private fun getCurrentAvailabilityStatuses(): Map<IdentityString, AvailabilityStatus> {
        return contactModelRepository
            .getAll()
            .mapNotNull { contactModel ->
                contactModel.data?.availabilityStatus?.let { availabilityStatus ->
                    contactModel.identity to availabilityStatus
                }
            }
            .toMap()
    }
}
