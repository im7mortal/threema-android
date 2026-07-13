package ch.threema.common

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainCoroutineDispatcher

interface DispatcherProvider {
    val main: MainCoroutineDispatcher
    val worker: CoroutineDispatcher
    val io: CoroutineDispatcher

    companion object {
        val default = object : DispatcherProvider {
            override val main = Dispatchers.Main
            override val worker = Dispatchers.Default
            override val io = Dispatchers.IO
        }
    }
}
