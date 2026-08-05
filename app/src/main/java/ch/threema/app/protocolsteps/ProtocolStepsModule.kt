package ch.threema.app.protocolsteps

import ch.threema.app.BuildFlavor
import ch.threema.app.processors.incomingcspmessage.workdelta.WorkSyncDeltaChangeDeterminationSteps
import ch.threema.app.usecases.availabilitystatus.UpdateUserAvailabilityStatusUseCase
import org.koin.core.module.dsl.factoryOf
import org.koin.dsl.module

val protocolStepsModule = module {
    factory<ApplicationSetupSteps> {
        ApplicationSetupSteps(
            contactModelRepository = get(),
            groupModelRepository = get(),
            contactService = get(),
            groupService = get(),
            apiConnector = get(),
            userService = get(),
            preferenceService = get(),
            groupFlowDispatcher = get(),
            multiDeviceManager = get(),
            taskManager = get(),
            dispatcherProvider = get(),
            updateUserAvailabilityStatus = { availabilityStatus ->
                if (BuildFlavor.current.isWork) {
                    // UpdateUserAvailabilityStatusUseCase is only available in work builds
                    get<UpdateUserAvailabilityStatusUseCase>().call(availabilityStatus)
                }
            },
        )
    }
    factoryOf(::IdentityBlockedSteps)
    factoryOf(::ValidContactsLookupSteps)
    factoryOf(::WorkSyncDeltaChangeDeterminationSteps)
}
