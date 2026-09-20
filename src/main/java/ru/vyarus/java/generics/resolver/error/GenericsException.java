package ru.vyarus.java.generics.resolver.error;

import ru.vyarus.java.generics.resolver.context.trace.ResolutionTrace;

/**
 * Base class for generic-related exceptions. May be used to intercept all generic analysis related exceptions
 * ({@code catch(GenericRelatedException ex)}).
 * <p>
 * When resolution trace was enabled
 * ({@link ru.vyarus.java.generics.resolver.context.AbstractGenericsContext#enableTracing()}), exception
 * carries trace of all resolution events, recorded before failure ({@link #getResolutionTrace()}).
 *
 * @author Vyacheslav Rusakov
 * @since 13.05.2018
 */
public abstract class GenericsException extends RuntimeException {

    private ResolutionTrace resolutionTrace;

    public GenericsException(final String message) {
        super(message);
    }

    public GenericsException(final String message, final Throwable cause) {
        super(message, cause);
    }

    /**
     * @return resolution trace, recorded before failure, or null if tracing was not enabled
     */
    public ResolutionTrace getResolutionTrace() {
        return resolutionTrace;
    }

    /**
     * Attach resolution trace to exception. Used internally when resolution fails under enabled tracing.
     * Trace is attached to the original exception instance, so exception stack trace is not affected.
     *
     * @param trace resolution trace (immutable)
     */
    public void setResolutionTrace(final ResolutionTrace trace) {
        this.resolutionTrace = trace;
    }
}
