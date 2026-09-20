package ru.vyarus.java.generics.resolver.trace;

import java.lang.reflect.Type;

/**
 * Immutable single resolution trace event. Events are created only when tracing is explicitly enabled and
 * never expose mutable internal resolver maps: all type payload is the actual {@link Type} objects
 * (safe to share), scope payload is an immutable snapshot.
 * <p>
 * Not all payload fields are used for each event kind; see {@link TraceEventKind} documentation.
 *
 * @author Vyacheslav Rusakov
 * @since 3.1.0
 */
public final class TraceEvent {

    private final TraceEventKind kind;
    private final TraceScope scope;
    private final String subject;
    private final Type type;
    private final Type fromType;
    private final Type toType;
    private final String detail;

    TraceEvent(final TraceEventKind kind,
               final TraceScope scope,
               final String subject,
               final Type type,
               final Type fromType,
               final Type toType,
               final String detail) {
        this.kind = kind;
        this.scope = scope;
        this.subject = subject;
        this.type = type;
        this.fromType = fromType;
        this.toType = toType;
        this.detail = detail;
    }

    /**
     * @return event kind
     */
    public TraceEventKind getKind() {
        return kind;
    }

    /**
     * @return immutable snapshot of declaration scope active when event occurred (never null)
     */
    public TraceScope getScope() {
        return scope;
    }

    /**
     * Meaning depends on kind: variable name for substitution/tracking/narrowing, otherwise null.
     *
     * @return event subject or null
     */
    public String getSubject() {
        return subject;
    }

    /**
     * Meaning depends on kind: read type, descriptor root type (cache hit), final type (complete), otherwise null.
     *
     * @return primary event type or null
     */
    public Type getType() {
        return type;
    }

    /**
     * @return original type before narrowing/substitution or null
     */
    public Type getFromType() {
        return fromType;
    }

    /**
     * @return resulting type after narrowing/substitution or null
     */
    public Type getToType() {
        return toType;
    }

    /**
     * @return additional textual detail (e.g. owner generics, failure message) or null
     */
    public String getDetail() {
        return detail;
    }
}
