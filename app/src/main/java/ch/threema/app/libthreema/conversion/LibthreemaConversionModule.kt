package ch.threema.app.libthreema.conversion

import org.koin.dsl.module
import org.koin.plugin.module.dsl.factory

val libthreemaConversionModule = module {
    factory<MessageModelConverter>()
    factory<DeletedMessageStateConverter>()
    factory<DistributionListModelConverter>()
    factory<LocationMessageBodyConverter>()
    factory<LocationMessageStateConverter>()
    factory<MessageBodyStateConverter>()
    factory<MessageStateConverter>()
    factory<TextMessageBodyConverter>()
    factory<TextMessageStateConverter>()
}
