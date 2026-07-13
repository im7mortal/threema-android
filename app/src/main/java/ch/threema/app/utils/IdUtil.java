package ch.threema.app.utils;

import java.util.HashMap;
import java.util.Map;

import androidx.annotation.NonNull;

public class IdUtil {

    private static final Map<String, Integer> contactIds = new HashMap<>();
    private static final String KEY_CONTACT = "c-";

    private static int getTempId(String key) {
        synchronized (contactIds) {
            if (!contactIds.containsKey(key)) {
                contactIds.put(key, contactIds.size() + 1);
            }
            return contactIds.get(key);
        }
    }

    /**
     * Return a unique integer for the specified contact.
     * <p>
     * The function always returns the same value for the same contact as long as the app is
     * running. After an app restart (when the memory is cleared), a new value will be generated.
     * <p>
     * Currently the function is implemented with a sequential positive integer, so the first
     * contact will get the number 1, the second contact will get the number 2, and so on.
     */
    public static int getContactTempId(@NonNull String identity) {
        return getTempId(KEY_CONTACT + identity);
    }
}
