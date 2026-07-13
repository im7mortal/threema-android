package ch.threema.app.poll

import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val pollFeatureModule = module {
    singleOf(::PollGroupStatusMonitor)
    singleOf(::PollVoteRemovalMonitor)
}
