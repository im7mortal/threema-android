package ch.threema.app.protocolsteps

import ch.threema.app.processors.incomingcspmessage.blocking.IncomingCspMessageBlockingDetector
import ch.threema.app.processors.incomingcspmessage.workdelta.WorkSyncDeltaChangeDeterminationSteps
import org.koin.dsl.module
import org.koin.plugin.module.dsl.factory

val protocolStepsModule = module {
    factory<ApplicationSetupSteps>()
    factory<CommonEditMessageReceiveSteps>()
    factory<DedicatedGroupSetupBlockingExemptionSteps>()
    factory<IdentityBlockedSteps>()
    factory<IncomingCspMessageBlockingDetector>()
    factory<ValidContactsLookupSteps>()
    factory<WorkSyncDeltaChangeDeterminationSteps>()
    factory<GroupSpamPotentialDetectionSteps>()
}
