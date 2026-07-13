package ch.threema.app.voip.managers;


import ch.threema.app.managers.ListenerManager;
import ch.threema.app.voip.listeners.VoipAudioManagerListener;
import ch.threema.app.voip.listeners.VoipMessageListener;

// TODO(ANDR-4687): These listeners should be replaced with event buses
public class VoipListenerManager {
    public static final ListenerManager.TypedListenerManager<VoipMessageListener> messageListener = new ListenerManager.TypedListenerManager<>();
    public static final ListenerManager.TypedListenerManager<VoipAudioManagerListener> audioManagerListener = new ListenerManager.TypedListenerManager<>();
}
