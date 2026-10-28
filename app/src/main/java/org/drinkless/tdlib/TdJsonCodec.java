package org.drinkless.tdlib;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Base64;

/**
 * Compatibility codec for generated TdApi objects and the official TDLib JSON API.
 * This is deliberately strict about unknown constructors and int64 precision.
 * Do not log requests: authentication secrets and database keys may be present.
 */
public final class TdJsonCodec {
    private TdJsonCodec() {}

    public static final class CodecException extends IllegalArgumentException {
        public CodecException(String message) { super(message); }
        public CodecException(String message, Throwable cause) { super(message, cause); }
    }

    public static final class Envelope {
        public final int clientId;
        public final String extra;
        public final TdApi.Object object;
        Envelope(int clientId, String extra, TdApi.Object object) {
            this.clientId = clientId;
            this.extra = extra;
            this.object = object;
        }
    }

    public static String encode(TdApi.Object value, String correlationId) {
        if (value == null) throw new CodecException("TDLib request must not be null");
        try {
            JSONObject json = encodeObject(value);
            if (correlationId != null) json.put("@extra", correlationId);
            return json.toString();
        } catch (JSONException | ReflectiveOperationException e) {
            throw new CodecException("Unable to serialize TDLib " + value.getClass().getSimpleName(), e);
        }
    }

    public static Envelope decodeEnvelope(String response) {
        if (response == null || response.trim().isEmpty()) {
            throw new CodecException("TDLib returned an empty JSON envelope");
        }
        try {
            JSONObject json = new JSONObject(response);
            int clientId = json.has("@client_id") ? parseInt(json.get("@client_id")) : 0;
            Object extraValue = json.opt("@extra");
            String extra = extraValue == null || extraValue == JSONObject.NULL ? null : extraValue.toString();
            TdApi.Object decoded = (TdApi.Object) decodeValue(json, TdApi.Object.class);
            return new Envelope(clientId, extra, decoded);
        } catch (JSONException | ReflectiveOperationException e) {
            throw new CodecException("TDLib response could not be decoded", e);
        }
    }

    private static String lowerFirst(String value) {
        return Character.toLowerCase(value.charAt(0)) + value.substring(1);
    }

    private static String upperFirst(String value) {
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

    private static String snake(String value) {
        return value.replaceAll("([a-z0-9])([A-Z])", "$1_$2")
                .replaceAll("([A-Z])([A-Z][a-z])", "$1_$2").toLowerCase(java.util.Locale.ROOT);
    }

    private static JSONObject encodeObject(TdApi.Object obj) throws JSONException, ReflectiveOperationException {
        JSONObject json = new JSONObject();
        json.put("@type", lowerFirst(obj.getClass().getSimpleName()));
        for (Class<?> current = obj.getClass();
             current != null && TdApi.Object.class.isAssignableFrom(current) && current != TdApi.Object.class;
             current = current.getSuperclass()) {
            for (Field f : current.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers()) || !Modifier.isPublic(f.getModifiers())) continue;
                Object value = f.get(obj);
                if (value != null) json.put(snake(f.getName()), encodeValue(value));
            }
        }
        return json;
    }

    private static Object encodeValue(Object value) throws JSONException, ReflectiveOperationException {
        if (value instanceof TdApi.Object) return encodeObject((TdApi.Object) value);
        if (value instanceof byte[]) return Base64.getEncoder().encodeToString((byte[]) value);
        if (value instanceof Long) return value.toString();  // TDLib int64 is always JSON String.
        if (value.getClass().isArray()) {
            JSONArray array = new JSONArray();
            for (int i = 0; i < Array.getLength(value); i++) {
                Object item = Array.get(value, i);
                array.put(item == null ? JSONObject.NULL : encodeValue(item));
            }
            return array;
        }
        if (value instanceof String || value instanceof Boolean || value instanceof Integer ||
                value instanceof Double || value instanceof Float || value instanceof Short) return value;
        throw new CodecException("Unsupported TDLib field type " + value.getClass().getName());
    }

    private static Object decodeValue(Object source, Class<?> target)
            throws JSONException, ReflectiveOperationException {
        if (source == null || source == JSONObject.NULL) {
            if (target.isPrimitive()) throw new CodecException("Required primitive field is null");
            return null;
        }
        if (target == String.class) {
            if (!(source instanceof String)) throw new CodecException("Expected JSON string");
            return source;
        }
        if (target == byte[].class) {
            if (!(source instanceof String)) throw new CodecException("Expected Base64 JSON string");
            try { return Base64.getDecoder().decode((String) source); }
            catch (IllegalArgumentException e) { throw new CodecException("Invalid Base64 field", e); }
        }
        if (target == long.class || target == Long.class) {
            if (source instanceof Number && Math.abs(((Number) source).doubleValue()) > 9007199254740991d) {
                throw new CodecException("Unsafe numeric int64; TDLib must encode as JSON string");
            }
            try { return Long.parseLong(source.toString()); }
            catch (NumberFormatException e) { throw new CodecException("Invalid int64", e); }
        }
        if (target == int.class || target == Integer.class) return parseInt(source);
        if (target == boolean.class || target == Boolean.class) {
            if (!(source instanceof Boolean)) throw new CodecException("Expected JSON boolean");
            return source;
        }
        if (target == double.class || target == Double.class) return Double.parseDouble(source.toString());
        if (target == float.class || target == Float.class) return Float.parseFloat(source.toString());
        if (target.isArray()) {
            if (!(source instanceof JSONArray)) throw new CodecException("Expected JSON array");
            JSONArray values = (JSONArray) source;
            Object array = Array.newInstance(target.getComponentType(), values.length());
            for (int i = 0; i < values.length(); i++) {
                Array.set(array, i, decodeValue(values.get(i), target.getComponentType()));
            }
            return array;
        }
        if (!(source instanceof JSONObject) || !TdApi.Object.class.isAssignableFrom(target)) {
            throw new CodecException("Unsupported TDLib value for " + target.getName());
        }
        JSONObject json = (JSONObject) source;
        String type = json.optString("@type", "");
        Class<?> concrete = target;
        if (!type.isEmpty()) {
            if (!type.matches("[a-z][A-Za-z0-9]*")) throw new CodecException("Invalid TDLib constructor name");
            try { concrete = Class.forName("org.drinkless.tdlib.TdApi$" + upperFirst(type)); }
            catch (ClassNotFoundException e) { throw new CodecException("Unknown TDLib constructor: " + type, e); }
            if (!target.isAssignableFrom(concrete)) throw new CodecException("Unexpected TDLib type: " + type);
        }
        if (Modifier.isAbstract(concrete.getModifiers()) || concrete.isInterface()) {
            throw new CodecException("TDLib object is missing required @type: " + target.getSimpleName());
        }
        TdApi.Object instance = (TdApi.Object) concrete.getDeclaredConstructor().newInstance();
        for (Class<?> current = concrete;
             current != null && TdApi.Object.class.isAssignableFrom(current) && current != TdApi.Object.class;
             current = current.getSuperclass()) {
            for (Field field : current.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) || !Modifier.isPublic(field.getModifiers())) continue;
                String key = snake(field.getName());
                if (json.has(key) && !json.isNull(key)) {
                    field.set(instance, decodeValue(json.get(key), field.getType()));
                }
            }
        }
        return instance;
    }

    private static int parseInt(Object value) {
        try {
            long parsed = Long.parseLong(value.toString());
            if (parsed < Integer.MIN_VALUE || parsed > Integer.MAX_VALUE) throw new NumberFormatException();
            return (int) parsed;
        } catch (NumberFormatException e) {
            throw new CodecException("Invalid TDLib int32", e);
        }
    }
}
