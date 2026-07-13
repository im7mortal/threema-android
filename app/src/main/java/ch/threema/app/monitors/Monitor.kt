package ch.threema.app.monitors

/**
 * A monitor defines a process that is active throughout the entire lifecycle of the app. It typically monitors some data by suspending
 * until a data source such as a flow publishes changes, and reacts to them by performing specific actions.
 *
 * @param name A name that uniquely identifies this monitor. Used only for logging.
 */
abstract class Monitor(val name: String) {
    /**
     * Runs the monitor. This method is not expected to ever return, doing so is logged as an error.
     */
    abstract suspend fun run()
}
