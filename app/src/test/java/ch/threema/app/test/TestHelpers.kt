package ch.threema.app.test

import ch.threema.app.startup.AppStartupMonitor
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import kotlinx.coroutines.awaitCancellation
import org.koin.core.module.Module
import org.koin.dsl.module
import org.koin.test.KoinTestRule

fun koinTestModuleRule(moduleDeclaration: Module.() -> Unit) =
    KoinTestRule.create {
        modules(
            module(moduleDeclaration = moduleDeclaration),
        )
    }

fun mockAppReady(): AppStartupMonitor =
    mockk<AppStartupMonitor> {
        every { isReady() } returns true
        every { isReady(any()) } returns true
        coEvery { awaitSystem(any()) } just runs
        coEvery { awaitAll() } just runs
    }

fun mockAppNotReady(): AppStartupMonitor =
    mockk<AppStartupMonitor> {
        every { isReady() } returns false
        every { isReady(any()) } returns false
        coEvery { awaitSystem(any()) } coAnswers { awaitCancellation() }
        coEvery { awaitAll() } coAnswers { awaitCancellation() }
    }
