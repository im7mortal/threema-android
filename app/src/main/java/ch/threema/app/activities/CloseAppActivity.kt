package ch.threema.app.activities

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import ch.threema.android.buildActivityIntent

class CloseAppActivity : Activity() {
    public override fun onCreate(bundle: Bundle?) {
        super.onCreate(bundle)
        finish()
    }

    companion object {
        /**
         * Closes all activities.
         *
         * Note that this will NOT stop the app process itself, meaning that services, workers and other things running in the background will
         * continue normally.
         */
        @JvmStatic
        fun closeApp(context: Context) {
            context.startActivity(
                buildActivityIntent<CloseAppActivity>(context) {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                },
            )
            (context as? Activity)?.finish()
        }
    }
}
