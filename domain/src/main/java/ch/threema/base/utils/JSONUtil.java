package ch.threema.base.utils;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Helpers for sane JSON parsing.
 * <p>
 * Reason why this is required: By default `jsonObj.getString(key)` returns the string "null" for
 * null values, instead of returning null.
 * <p>
 * Also reason why this is required: When converting JSON numbers to (long) integers, the values are
 * silently truncated if out of range.
 */
public class JSONUtil {

    /**
     * Return the String value for the specified key. If the field is missing or if the field value
     * is null, return null. If the field value is not a string, return null.
     */
    public static String getStringOrNull(JSONObject o, String key) {
        if (o.isNull(key)) {
            return null;
        }
        try {
            final Object value = o.get(key);
            if (value instanceof String) {
                return (String) value;
            }
            return null;
        } catch (JSONException e) {
            return null;
        }
    }

    /**
     * Return the Integer value for the specified key. If the field is missing or if the field value
     * is null, return null. If the field value is not an integer, return null.
     */
    public static Integer getIntegerOrNull(JSONObject o, String key) {
        if (o.isNull(key)) {
            return null;
        }
        try {
            final Object value = o.get(key);
            if (value instanceof Integer) {
                return (Integer) value;
            }
            return null;
        } catch (JSONException e) {
            return null;
        }
    }

    /**
     * Return the Long value for the specified key. If the field is missing or if the field value
     * is null, return null. If the field value is out of range or not an integer, throw an exception.
     */
    public static Long getLongOrThrow(JSONObject o, String key) throws RuntimeException {
        if (o.isNull(key)) {
            return null;
        }
        final Object value = o.opt(key);
        if (value instanceof Number) {
            // Range check
            final double doubleValue = ((Number) value).doubleValue();
            if (doubleValue > Long.MAX_VALUE || doubleValue < Long.MIN_VALUE) {
                throw new RuntimeException("Number at key \"" + key + "\" out of range");
            }
            // Convert to long
            return ((Number) value).longValue();
        } else {
            throw new RuntimeException("Invalid object type for key " + key);
        }
    }
}
