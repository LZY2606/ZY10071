package ru.vyarus.java.generics.resolver.context.trace;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Immutable resolution trace. Contains all events, recorded during generics resolution, in actual resolution order.
 * Obtained from context with
 * {@link ru.vyarus.java.generics.resolver.context.AbstractGenericsContext#getTrace()} or, in case of failed
 * resolution, from exception with
 * {@link ru.vyarus.java.generics.resolver.error.GenericsException#getResolutionTrace()}.
 * <p>
 * {@link #toString()} renders trace as text (one event per line, stable order). Recursive types are rendered
 * with reference numbers (e.g. {@code #1: Comparable<#1>}) to avoid infinite expansion.
 *
 * @author Vyacheslav Rusakov
 * @since 20.09.2026
 */
public final class ResolutionTrace {

    private static final ResolutionTrace EMPTY_INSTANCE =
            new ResolutionTrace(Collections.<TraceEvent>emptyList());

    private final List<TraceEvent> events;

    /**
     * @param events recorded events (copied)
     */
    public ResolutionTrace(final List<TraceEvent> events) {
        this.events = Collections.unmodifiableList(new ArrayList<>(events));
    }

    /**
     * @return shared empty trace instance
     */
    public static ResolutionTrace empty() {
        return EMPTY_INSTANCE;
    }

    /**
     * @return all recorded events (unmodifiable list, in resolution order)
     */
    public List<TraceEvent> getEvents() {
        return events;
    }

    /**
     * @return true if no events recorded
     */
    public boolean isEmpty() {
        return events.isEmpty();
    }

    /**
     * @return events count
     */
    public int size() {
        return events.size();
    }

    /**
     * @return last recorded event or null if trace is empty
     */
    public TraceEvent getLastEvent() {
        return events.isEmpty() ? null : events.get(events.size() - 1);
    }

    @Override
    public String toString() {
        final StringBuilder res = new StringBuilder(events.size() * 60);
        for (TraceEvent event : events) {
            res.append(event).append('\n');
        }
        return res.toString();
    }
}
