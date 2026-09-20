package ru.vyarus.java.generics.resolver.trace;

import java.lang.reflect.Type;
import java.util.Collections;
import java.util.List;

/**
 * Immutable resolution trace: ordered sequence of {@link TraceEvent}s describing how the final type
 * (or failure) was derived.
 * <p>
 * Instances never expose mutable internal resolver structures: scope generics are string snapshots and
 * all type payload is shareable {@link Type} references. {@link #toString()} renders events in the
 * stable recorded order with recursion safe type representation
 * ({@link TraceTypeRenderer}).
 *
 * @author Vyacheslav Rusakov
 * @since 3.1.0
 */
public final class ResolutionTrace {

    private static final String INDENT = "  ";

    private final List<TraceEvent> events;
    private final TraceScope scope;
    private final Type result;
    private final String failure;
    private final boolean successful;
    private final boolean complete;

    ResolutionTrace(final List<TraceEvent> events,
                    final TraceScope scope,
                    final Type result,
                    final String failure,
                    final boolean successful,
                    final boolean complete) {
        this.events = Collections.unmodifiableList(events);
        this.scope = scope;
        this.result = result;
        this.failure = failure;
        this.successful = successful;
        this.complete = complete;
    }

    /**
     * @return immutable ordered events (actual derivation order); never null
     */
    public List<TraceEvent> getEvents() {
        return events;
    }

    /**
     * @return last active scope at the moment trace was taken or null for empty trace
     */
    public TraceScope getLastScope() {
        return scope;
    }

    /**
     * @return final resolved type or null if trace ended with failure (or no completion event yet)
     */
    public Type getResult() {
        return result;
    }

    /**
     * @return failure description (exception class and message) recorded by terminal event or null
     */
    public String getFailure() {
        return failure;
    }

    /**
     * @return true if trace contains terminal successful completion event
     */
    public boolean isSuccessful() {
        return successful;
    }

    /**
     * @return true if resolution reached terminal state (complete or failure), false if it is a mid run snapshot
     */
    public boolean isComplete() {
        return complete;
    }

    /**
     * Renders trace as stable multi line text, one line per event in recorded order.
     *
     * @return textual trace
     */
    @Override
    public String toString() {
        final StringBuilder res = new StringBuilder(events.size() * 40);
        TraceScope renderedScope = null;
        for (TraceEvent event : events) {
            final TraceScope eventScope = event.getScope();
            if (eventScope != null && !eventScope.equals(renderedScope)) {
                appendScopeHeader(res, eventScope);
                renderedScope = eventScope;
            } else if (eventScope != null && event.getKind() == TraceEventKind.SCOPE) {
                renderedScope = eventScope;
            }
            res.append(INDENT).append(renderEvent(event)).append('\n');
        }
        if (res.length() == 0) {
            res.append("(empty resolution trace)\n");
        }
        return res.toString();
    }

    private void appendScopeHeader(final StringBuilder res, final TraceScope scope) {
        res.append(scope.getKind()).append(' ').append(scope.getDisplayName()).append('\n');
        if (!scope.getGenerics().isEmpty()) {
            res.append(INDENT).append("generics:");
            for (java.util.Map.Entry<String, String> entry : scope.getGenerics().entrySet()) {
                res.append(' ').append(entry.getKey()).append('=').append(entry.getValue()).append(',');
            }
            // remove trailing comma
            res.setLength(res.length() - 1);
            res.append('\n');
        }
    }

    private String renderEvent(final TraceEvent event) {
        final StringBuilder res = new StringBuilder();
        res.append('[').append(event.getKind().getLabel()).append(']');
        appendPayload(res, event);
        return res.toString();
    }

    private void appendPayload(final StringBuilder res, final TraceEvent event) {
        switch (event.getKind()) {
            case SCOPE:
                res.append(" enter ").append(event.getScope().getDisplayName());
                break;
            case READ_TYPE:
            case INLYING:
            case CACHE_HIT:
            case CONTEXT_SWITCH:
            case COMPLETE:
                res.append(' ').append(TraceTypeRenderer.render(event.getType()));
                break;
            case VARIABLE_SUBSTITUTE:
                res.append(' ').append(event.getSubject())
                        .append(": ").append(TraceTypeRenderer.render(event.getType()))
                        .append(" -> ").append(TraceTypeRenderer.render(event.getToType()));
                break;
            case BOUND_NARROW:
                res.append(event.getSubject() == null ? " " : ' ' + event.getSubject() + ": ")
                        .append(TraceTypeRenderer.render(event.getFromType()))
                        .append(" -> ").append(TraceTypeRenderer.render(event.getToType()));
                break;
            case OWNER_TRANSFER:
                res.append(' ').append(TraceTypeRenderer.render(event.getType()))
                        .append(" generics ").append(event.getDetail());
                break;
            case TRACK:
                res.append(' ').append(event.getSubject())
                        .append(" -> ").append(TraceTypeRenderer.render(event.getToType()));
                break;
            case FAILURE:
                res.append(' ').append(event.getDetail());
                break;
        }
    }
}
