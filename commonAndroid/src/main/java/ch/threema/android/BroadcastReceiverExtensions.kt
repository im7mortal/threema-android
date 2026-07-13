package ch.threema.android

import android.content.BroadcastReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

@OptIn(DelicateCoroutinesApi::class)
fun BroadcastReceiver.goAsync(
    block: suspend CoroutineScope.() -> Unit,
) {
    val pendingResult = goAsync()
    GlobalScope.launch {
        try {
            block()
        } finally {
            pendingResult.finish()
        }
    }
}
