package ru.vyarus.java.generics.resolver.trace;

import java.lang.reflect.Type;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Static, zero overhead entry points used by resolver internals to record trace events.
 * <p>
 * Every method starts with {@link ResolutionTracer#active()} thread local lookup and immediately returns
 * when tracing is off (the default) &mdash; no event lists, event objects, strings or maps are allocated
 * on the hot path. When tracing is enabled, recorded events describe the actual derivation order.
 * <p>
 * Failed resolutions are additionally associated with the thrown exception:
 * {@link #traceOf(Throwable)} safely returns the trace snapshot captured just before exception
 * propagation (without affecting exception stack trace or message in any way).
 *
 * @author Vyacheslav Rusakov
 * @since 3.1.0
 */
public final class TraceSupport {

    // exception -> failure trace; weak to avoid retaining exceptions handed to caller
    private static final Set<ResolutionFailureHolder> FAILURES =
            Collections.synchronizedSet(
                    Collections.newSetFromMap(new WeakHashMap<>()));

    private TraceSupport() {
    }

    /**
     * Enters declaration scope.
     *
     * @param scope scope snapshot
     */
    public static void scope(final TraceScope scope) {
        final ResolutionTracer tracer = ResolutionTracer.active();
        if (tracer != null) {
            tracer.enterScope(scope);
        }
    }

    /**
     * Records a read type.
     *
     * @param type declared type read for resolution
     */
    public static void read(final Type type) {
        final ResolutionTracer tracer = ResolutionTracer.active();
        if (tracer != null) {
            tracer.read(type);
        }
    }

    /**
     * Records variable substitution.
     *
     * @param name     variable name
     * @param declared declared type
     * @param value    resolved value
     */
    public static void substitute(final String name, final Type declared, final Type value) {
        final ResolutionTracer tracer = ResolutionTracer.active();
        if (tracer != null) {
            tracer.substitute(name, declared, value);
        }
    }

    /**
     * Records bounds narrowing.
     *
     * @param name generic name or null
     * @param from original type
     * @param to   narrowed type
     */
    public static void narrow(final String name, final Type from, final Type to) {
        final ResolutionTracer tracer = ResolutionTracer.active();
        if (tracer != null) {
            tracer.narrow(name, from, to);
        }
    }

    /**
     * Records owner generics transfer.
     *
     * @param owner       owner type
     * @param description stable description of transferred generics
     */
    public static void ownerTransfer(final Type owner, final String description) {
        final ResolutionTracer tracer = ResolutionTracer.active();
        if (tracer != null) {
            tracer.ownerTransfer(owner, description);
        }
    }

    /**
     * Records inlying context construction.
     *
     * @param type inlying root type
     */
    public static void inlying(final Type type) {
        final ResolutionTracer tracer = ResolutionTracer.active();
        if (tracer != null) {
            tracer.inlying(type);
        }
    }

    /**
     * Records cached descriptor reuse.
     *
     * @param type cached descriptor root type
     */
    public static void cacheHit(final Type type) {
        final ResolutionTracer tracer = ResolutionTracer.active();
        if (tracer != null) {
            tracer.cacheHit(type);
        }
    }

    /**
     * Records generic tracking (backtracking).
     *
     * @param name  root generic name
     * @param value tracked value
     */
    public static void track(final String name, final Type value) {
        final ResolutionTracer tracer = ResolutionTracer.active();
        if (tracer != null) {
            tracer.track(name, value);
        }
    }

    /**
     * Records automatic context switch.
     *
     * @param target target class
     */
    public static void contextSwitch(final Class<?> target) {
        final ResolutionTracer tracer = ResolutionTracer.active();
        if (tracer != null) {
            tracer.contextSwitch(target);
        }
    }

    /**
     * Records successful terminal value.
     *
     * @param result final result
     */
    public static void complete(final Type result) {
        final ResolutionTracer tracer = ResolutionTracer.active();
        if (tracer != null) {
            tracer.complete(result);
        }
    }

    /**
     * Associates failure trace with exception (capturing current tracer state) without modifying
     * the exception itself. If exception is wrapped (see {@link #traceOf(Throwable)}), cause chain
     * is searched too.
     *
     * @param error exception about to be thrown
     */
    public static void recordFailure(final Throwable error) {
        final ResolutionTracer tracer = ResolutionTracer.active();
        if (tracer != null && error != null && !isAlreadyRecorded(error)) {
            tracer.failEvent(error);
            final ResolutionTrace snapshot = tracer.failureSnapshot();
            FAILURES.add(new ResolutionFailureHolder(error, snapshot));
            Throwable cause = error.getCause();
            // associate wrapped causes too (without duplicating their own terminal event)
            while (cause != null && !isAlreadyRecorded(cause)) {
                FAILURES.add(new ResolutionFailureHolder(cause, snapshot));
                cause = cause.getCause();
            }
        }
    }

    @SuppressWarnings("PMD.CompareObjectsWithEquals")
    private static boolean isAlreadyRecorded(final Throwable error) {
        synchronized (FAILURES) {
            for (ResolutionFailureHolder holder : FAILURES) {
                if (holder.error == error) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Safely obtains resolution trace, recorded before exception termination. Looks up exception itself
     * and, if absent, walks the {@code getCause()} chain (covers wrapping exceptions like
     * GenericsResolutionException).
     *
     * @param error exception caught by caller
     * @return trace snapshot or null if tracing was off (or exception is not related to traced resolution)
     */
    @SuppressWarnings("PMD.CompareObjectsWithEquals")
    public static ResolutionTrace traceOf(final Throwable error) {
        ResolutionTrace res = null;
        Throwable current = error;
        // guard against malicious cause cycles
        final Set<Throwable> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        while (current != null && visited.add(current)) {
            synchronized (FAILURES) {
                for (ResolutionFailureHolder holder : FAILURES) {
                    if (holder.error == current) {
                        res = holder.trace;
                        break;
                    }
                }
            }
            if (res != null) {
                break;
            }
            current = current.getCause();
        }
        return res;
    }

    /**
     * Weak holder of failure trace (identity comparison of exceptions).
     */
    private static final class ResolutionFailureHolder {
        private final Throwable error;
        private final ResolutionTrace trace;

        ResolutionFailureHolder(final Throwable error, final ResolutionTrace trace) {
            this.error = error;
            this.trace = trace;
        }

        @Override
        public boolean equals(final Object o) {
            final Throwable otherError = o instanceof ResolutionFailureHolder
                    ? ((ResolutionFailureHolder) o).error : null;
            return error == otherError;
        }

        @Override
        public int hashCode() {
            return System.identityHashCode(error);
        }
    }
}
