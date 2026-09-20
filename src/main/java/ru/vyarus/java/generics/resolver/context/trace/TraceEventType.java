package ru.vyarus.java.generics.resolver.context.trace;

/**
 * Type of resolution trace event. Events are recorded in actual resolution order.
 *
 * @author Vyacheslav Rusakov
 * @since 20.09.2026
 */
public enum TraceEventType {

    /**
     * Declaration scope, active for the following events (class, method or constructor with all visible generics).
     * Recorded only when scope actually changes (consequent events in the same scope are not repeated).
     */
    SCOPE,

    /**
     * Type, provided for resolution (as is, before any variable substitution).
     */
    READ_TYPE,

    /**
     * Named variable replaced with actual type (e.g. {@code T => String}).
     */
    SUBSTITUTE,

    /**
     * Type narrowed to its bound: wildcard flattened to bound type or generic declaration resolved
     * to its upper bound.
     */
    NARROW_BOUND,

    /**
     * Owner type generics passed to inner type resolution.
     */
    OWNER,

    /**
     * Generics info cache event (hit, miss or bypass).
     */
    CACHE,

    /**
     * Final value of resolution operation.
     */
    RESULT,

    /**
     * Resolution terminated with error (always the last event of failed resolution).
     */
    ERROR
}
