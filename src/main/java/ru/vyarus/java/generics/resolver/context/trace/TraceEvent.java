package ru.vyarus.java.generics.resolver.context.trace;

import java.lang.reflect.Type;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Single resolution trace event. Event is immutable: all type information is rendered to string at record time
 * and generics map (present only in {@link TraceEventType#SCOPE} events) is an unmodifiable snapshot.
 *
 * @author Vyacheslav Rusakov
 * @since 20.09.2026
 */
public final class TraceEvent {

    private final TraceEventType type;
    private final String message;
    private final Map<String, Type> generics;

    /**
     * @param type    event type
     * @param message event description (already rendered, immutable)
     */
    public TraceEvent(final TraceEventType type, final String message) {
        this(type, message, Collections.<String, Type>emptyMap());
    }

    /**
     * @param type     event type
     * @param message  event description (already rendered, immutable)
     * @param generics generics snapshot (copied into unmodifiable map)
     */
    public TraceEvent(final TraceEventType type, final String message, final Map<String, Type> generics) {
        this.type = type;
        this.message = message;
        this.generics = Collections.unmodifiableMap(new LinkedHashMap<>(generics));
    }

    /**
     * @return event type
     */
    public TraceEventType getType() {
        return type;
    }

    /**
     * @return event description
     */
    public String getMessage() {
        return message;
    }

    /**
     * @return unmodifiable generics snapshot (not empty only for {@link TraceEventType#SCOPE} events)
     */
    public Map<String, Type> getGenerics() {
        return generics;
    }

    @Override
    public String toString() {
        return type + " " + message;
    }
}
