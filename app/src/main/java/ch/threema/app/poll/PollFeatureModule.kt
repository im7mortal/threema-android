package ch.threema.app.poll

import org.koin.dsl.module
import org.koin.plugin.module.dsl.single

val pollFeatureModule = module {
    single<PollGroupStatusMonitor>()
    single<PollVoteRemovalMonitor>()
}
