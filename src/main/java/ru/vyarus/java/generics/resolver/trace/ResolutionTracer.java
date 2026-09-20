package ru.vyarus.java.generics.resolver.trace;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

/**
 * Mutable per-session recorder of a {@link ResolutionTrace}. A tracer is created when caller explicitly
 * opts in for tracing and is not used anywhere otherwise.
 * <p>
 * One tracer belongs to one resolution session and, because active tracer is bound to a thread,
 * events from simultaneous resolutions on different threads never mix. Events always snapshot the
 * currently active {@link TraceScope}, so when a cached hierarchy descriptor is reused by different
 * contexts caller scopes could not cross line.
 * <p>
 * Callers obtain a tracer through {@code context.startTracing()} (or {@link #start()}) and consume
 * immutable {@link ResolutionTrace} results.
 *
 * @author Vyacheslav Rusakov
 * @since 3.1.0
 */
@SuppressWarnings("PMD.TooManyMethods")
public final class ResolutionTracer {

    private static final ThreadLocal<ResolutionTracer> CURRENT = new ThreadLocal<>();

    private final List<TraceEvent> events = new ArrayList<>();
    private TraceScope scope;
    private boolean finished;

    private ResolutionTracer() {
    }

    /**
     * Starts a new tracing session on the current thread and binds it as active.
     * Resolution calls performed on the same thread afterwards will be recorded.
     *
     * @return new tracer instance
     * @throws IllegalStateException if another session is already active on the thread
     */
    public static ResolutionTracer start() {
        if (CURRENT.get() != null) {
            throw new IllegalStateException("Resolution trace is already active on the current thread");
        }
        final ResolutionTracer tracer = new ResolutionTracer();
        CURRENT.set(tracer);
        return tracer;
    }

    /**
     * The only hot path check used by all instrumentation points. When tracing is off this is a single
     * thread local read returning null: no event lists, events or strings are ever allocated.
     *
     * @return active tracer or null
     */
    public static ResolutionTracer active() {
        return CURRENT.get();
    }

    /**
     * Unbinds the tracer from the current thread. Recorded events remain available through
     * {@link #snapshot()} / {@link #finish(Type)} / {@link #fail(Throwable)}.
     */
    @SuppressWarnings("PMD.CompareObjectsWithEquals")
    public void stop() {
        if (CURRENT.get() == this) {
            CURRENT.remove();
        }
    }

    /**
     * Replaces active declaration scope (class, method or constructor context).
     * Consecutive equal scopes are not recorded again to keep output focused on actual transitions.
     *
     * @param scope new scope snapshot
     */
    public void enterScope(final TraceScope scope) {
        if (scope != null && !scope.equals(this.scope)) {
            this.scope = scope;
            add(TraceEventKind.SCOPE, null, null, null, null, null);
        }
    }

    /**
     * @return current scope snapshot or null
     */
    public TraceScope currentScope() {
        return scope;
    }

    /**
     * Records that a declared type was read as resolution input.
     *
     * @param type read type
     */
    public void read(final Type type) {
        add(TraceEventKind.READ_TYPE, null, type, null, null, null);
    }

    /**
     * Records variable substitution by actual known type.
     *
     * @param name     variable name
     * @param declared declared variable (as read)
     * @param value    resolved value
     */
    public void substitute(final String name, final Type declared, final Type value) {
        add(TraceEventKind.VARIABLE_SUBSTITUTE, name, declared, null, value, null);
    }

    /**
     * Records bounds narrowing (wildcard or interface merge).
     *
     * @param name generic name (or null for anonymous narrowing)
     * @param from original type
     * @param to   narrowed type
     */
    public void narrow(final String name, final Type from, final Type to) {
        add(TraceEventKind.BOUND_NARROW, name, null, from, to, null);
    }

    /**
     * Records outer (owner) generics transfer into non static inner class context.
     *
     * @param owner       owner class or type
     * @param description transferred generics, rendered as stable string
     */
    public void ownerTransfer(final Type owner, final String description) {
        add(TraceEventKind.OWNER_TRANSFER, null, owner, null, null, description);
    }

    /**
     * Records construction of an inlying (drill down) context for type.
     *
     * @param type inlying root type
     */
    public void inlying(final Type type) {
        add(TraceEventKind.INLYING, null, type, null, null, null);
    }

    /**
     * Records cache descriptor reuse.
     *
     * @param type descriptor root class
     */
    public void cacheHit(final Type type) {
        add(TraceEventKind.CACHE_HIT, null, type, null, null, null);
    }

    /**
     * Records root generic backtracking from known middle type.
     *
     * @param name  root generic name
     * @param value tracked value
     */
    public void track(final String name, final Type value) {
        add(TraceEventKind.TRACK, name, null, null, value, null);
    }

    /**
     * Records automatic context switch to a compatible scope.
     *
     * @param target target class of switch
     */
    public void contextSwitch(final Class<?> target) {
        add(TraceEventKind.CONTEXT_SWITCH, null, target, null, null, null);
    }

    /**
     * Finishes resolution successfully with final value and unbinds the tracer.
     *
     * @param result final resolved type or value
     * @return immutable trace
     */
    public ResolutionTrace finish(final Type result) {
        complete(result);
        return snapshotAndStop();
    }

    /**
     * Records successful completion with final value (without unbinding).
     *
     * @param result final resolved type or value
     */
    public void complete(final Type result) {
        if (!finished) {
            add(TraceEventKind.COMPLETE, null, result, null, null, null);
            finished = true;
        }
    }

    /**
     * Finishes resolution with failure, records terminal failure event and unbinds the tracer.
     *
     * @param error resolution exception
     * @return immutable trace (events recorded up to termination)
     */
    public ResolutionTrace fail(final Throwable error) {
        failure(error);
        return snapshotAndStop();
    }

    /**
     * Records terminal failure event (without unbinding). Safe to call after failure was already recorded.
     *
     * @param error resolution exception
     */
    public void failEvent(final Throwable error) {
        failure(error);
    }

    /**
     * @return immutable copy of current trace (session may continue afterwards)
     */
    public ResolutionTrace snapshot() {
        return new ResolutionTrace(copyEvents(), scope, null, null, false, finished);
    }

    /**
     * @return immutable snapshot marked as failed (last event is the terminal failure)
     */
    public ResolutionTrace failureSnapshot() {
        final TraceEvent failure = events.isEmpty() ? null : events.get(events.size() - 1);
        final String detail = failure != null && failure.getKind() == TraceEventKind.FAILURE
                ? failure.getDetail() : null;
        return new ResolutionTrace(copyEvents(), scope, null, detail, false, true);
    }

    private void failure(final Throwable error) {
        if (!finished) {
            final String detail = error.getClass().getName() + ": " + error.getMessage();
            add(TraceEventKind.FAILURE, null, null, null, null, detail);
            finished = true;
        }
    }

    private ResolutionTrace snapshotAndStop() {
        stop();
        final TraceEvent last = events.isEmpty() ? null : events.get(events.size() - 1);
        final boolean failed = last != null && last.getKind() == TraceEventKind.FAILURE;
        final Object result = last != null && last.getKind() == TraceEventKind.COMPLETE ? last.getType() : null;
        return new ResolutionTrace(copyEvents(), scope, (Type) result,
                failed ? last.getDetail() : null,
                !failed, finished);
    }

    private List<TraceEvent> copyEvents() {
        return new ArrayList<>(events);
    }

    private void add(final TraceEventKind kind,
                     final String subject,
                     final Type type,
                     final Type from,
                     final Type to,
                     final String detail) {
        events.add(new TraceEvent(kind, scope, subject, type, from, to, detail));
    }
}
