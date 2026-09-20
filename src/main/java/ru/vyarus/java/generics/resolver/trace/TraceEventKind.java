package ru.vyarus.java.generics.resolver.trace;

/**
 * Kinds of recorded resolution trace events. Events appear strictly in the actual derivation order
 * (the order in which resolver visited declarations and substituted types).
 *
 * @author Vyacheslav Rusakov
 * @since 3.1.0
 */
public enum TraceEventKind {

    /**
     * Resolution entered a new declaration scope: class, method or constructor context.
     * Payload: current scope description.
     */
    SCOPE("scope"),

    /**
     * A declared {@link java.lang.reflect.Type} (field type, return type, parameter type, bound etc.)
     * was read as an input for resolution. Payload: the read type.
     */
    READ_TYPE("read"),

    /**
     * Named generic variable was replaced by its actual (already known) value.
     * Payload: variable name (subject), declared variable type and resolved value.
     */
    VARIABLE_SUBSTITUTE("substitute"),

    /**
     * Generic value was narrowed: upper/lower wildcard bounds, declaration bounds or interface diamond
     * merging selected a more specific type. Payload: subject name, original and narrowed types.
     */
    BOUND_NARROW("narrow"),

    /**
     * Non static inner class resolution: outer (owner) generics were transferred into inner context.
     * Payload: owner class/type and transferred generics description.
     */
    OWNER_TRANSFER("owner"),

    /**
     * New (possibly inlying) context was built for a type: hierarchy descriptor obtained
     * (either computed or taken from cache).
     */
    INLYING("inlying"),

    /**
     * Cached hierarchy descriptor ({@code GenericsInfo}) was reused instead of computing a new one.
     * Payload: root class of cached descriptor.
     */
    CACHE_HIT("cache-hit"),

    /**
     * Root generic was tracked (backtracked) from a known middle type generics.
     * Payload: tracked generic name and value.
     */
    TRACK("track"),

    /**
     * Context was switched (or chosen) to continue resolution in a compatible declaration scope.
     * Payload: target scope.
     */
    CONTEXT_SWITCH("switch"),

    /**
     * Resolution finished successfully. Payload: final resolved value.
     */
    COMPLETE("complete"),

    /**
     * Resolution terminated with an exception. Payload: exception type and message.
     * Terminal event: always last in trace.
     */
    FAILURE("failure");

    private final String label;

    TraceEventKind(final String label) {
        this.label = label;
    }

    /**
     * @return stable short label used during text rendering
     */
    public String getLabel() {
        return label;
    }
}
