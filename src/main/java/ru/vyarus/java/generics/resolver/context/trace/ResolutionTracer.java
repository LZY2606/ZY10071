package ru.vyarus.java.generics.resolver.context.trace;

import ru.vyarus.java.generics.resolver.context.GenericDeclarationScope;
import ru.vyarus.java.generics.resolver.context.container.ExplicitTypeVariable;
import ru.vyarus.java.generics.resolver.util.TypeToStringUtils;
import ru.vyarus.java.generics.resolver.util.map.PrintableGenericsMap;

import java.lang.reflect.Constructor;
import java.lang.reflect.GenericDeclaration;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Records resolution trace events. Tracing is disabled by default: all context objects use shared
 * {@link #NONE} instance which does not allocate anything and performs no work. Recording instance
 * is created on tracing enabling
 * ({@link ru.vyarus.java.generics.resolver.context.AbstractGenericsContext#enableTracing()}).
 * <p>
 * Tracer is propagated to all contexts, derived from the context where tracing was enabled
 * (type, method, constructor and inlying navigations), so the entire resolution process is described
 * with a single trace. Tracer is never stored inside {@code GenericsInfo} and so cached descriptors,
 * shared between independent contexts, never mix traces.
 * <p>
 * Note: tracer is not thread-safe (as well as context objects).
 *
 * @author Vyacheslav Rusakov
 * @since 20.09.2026
 */
@SuppressWarnings("PMD.LooseCoupling")
public final class ResolutionTracer {

    /**
     * Shared no-op instance (used by default). Does not allocate events list.
     */
    public static final ResolutionTracer NONE = new ResolutionTracer(false);

    private static final PrintableGenericsMap PRINTABLE_GENERICS = new PrintableGenericsMap();

    private final boolean enabled;
    private final List<TraceEvent> events;
    private String lastScope;

    private ResolutionTracer(final boolean enabled) {
        this.enabled = enabled;
        // no list allocation for disabled tracer
        this.events = enabled ? new ArrayList<>() : null;
    }

    /**
     * @return new recording tracer instance
     */
    public static ResolutionTracer recording() {
        return new ResolutionTracer(true);
    }

    /**
     * @return true if tracer records events
     */
    public boolean isRecording() {
        return enabled;
    }

    /**
     * @return immutable snapshot of all recorded events (empty trace for disabled tracer)
     */
    public ResolutionTrace snapshot() {
        return enabled ? new ResolutionTrace(events) : ResolutionTrace.empty();
    }

    /**
     * Record current declaration scope. Consequent records of the same scope are ignored
     * (scope event appears only when scope actually changes).
     *
     * @param scope   declaration scope
     * @param source  declaration source (class, method or constructor)
     * @param visible all generics, visible from scope (snapshot is stored inside event)
     */
    public void scope(final GenericDeclarationScope scope,
                      final GenericDeclaration source,
                      final Map<String, Type> visible) {
        if (enabled) {
            final String description = describeScope(scope, source);
            if (!description.equals(lastScope)) {
                lastScope = description;
                record(new TraceEvent(TraceEventType.SCOPE,
                        description + " " + TraceTypeRenderer.renderGenerics(visible), visible));
            }
        }
    }

    /**
     * Record type, provided for resolution (rendered as is, before any variable substitution).
     *
     * @param type type to resolve
     */
    public void readType(final Type type) {
        if (enabled) {
            record(new TraceEvent(TraceEventType.READ_TYPE,
                    TraceTypeRenderer.render(type, Collections.<String, Type>emptyMap())));
        }
    }

    /**
     * Record named variable substitution.
     *
     * @param variable generic variable
     * @param resolved actual variable value
     */
    public void substitute(final TypeVariable variable, final Type resolved) {
        if (enabled) {
            record(new TraceEvent(TraceEventType.SUBSTITUTE,
                    variable.getName() + " => " + render(resolved)));
        }
    }

    /**
     * Record preserved variable container substitution (when preserved variables resolution requested).
     *
     * @param variable preserved variable container
     * @param resolved actual variable value
     */
    public void substitute(final ExplicitTypeVariable variable, final Type resolved) {
        if (enabled) {
            record(new TraceEvent(TraceEventType.SUBSTITUTE,
                    variable.getName() + " => " + render(resolved)));
        }
    }

    /**
     * Record type narrowing to bound (wildcard flattening or generic declaration resolved to upper bound).
     *
     * @param source   source type (wildcard or generic declaration)
     * @param resolved narrowed type
     */
    public void boundNarrowed(final Type source, final Type resolved) {
        if (enabled) {
            record(new TraceEvent(TraceEventType.NARROW_BOUND,
                    TraceTypeRenderer.render(source, Collections.<String, Type>emptyMap())
                            + " => " + render(resolved)));
        }
    }

    /**
     * Record owner type generics passing (inner type resolution).
     *
     * @param owner owner type (with generics)
     */
    public void ownerType(final Type owner) {
        if (enabled) {
            record(new TraceEvent(TraceEventType.OWNER, render(owner)));
        }
    }

    /**
     * Record generics info cache hit.
     *
     * @param type requested root type
     */
    public void cacheHit(final Class<?> type) {
        if (enabled) {
            record(new TraceEvent(TraceEventType.CACHE, "hit for " + render(type)));
        }
    }

    /**
     * Record generics info cache miss.
     *
     * @param type   requested root type
     * @param cached true if resolved descriptor was stored in cache, false if cache is disabled
     */
    public void cacheMiss(final Class<?> type, final boolean cached) {
        if (enabled) {
            record(new TraceEvent(TraceEventType.CACHE,
                    "miss for " + render(type) + (cached ? " (resolved and cached)" : " (cache disabled)")));
        }
    }

    /**
     * Record generics info cache bypass (resolution with ignored types is never cached).
     *
     * @param type requested root type
     */
    public void cacheBypassed(final Class<?> type) {
        if (enabled) {
            record(new TraceEvent(TraceEventType.CACHE, "bypassed for " + render(type) + " (ignored types)"));
        }
    }

    /**
     * Record resolution operation result.
     *
     * @param type resolved type
     */
    public void result(final Type type) {
        if (enabled) {
            record(new TraceEvent(TraceEventType.RESULT, render(type)));
        }
    }

    /**
     * Record resolution operation result.
     *
     * @param types resolved types
     */
    public void result(final List<? extends Type> types) {
        if (enabled) {
            final StringBuilder res = new StringBuilder(types.size() * 20 + 2);
            res.append('[');
            boolean first = true;
            for (Type type : types) {
                if (!first) {
                    res.append(", ");
                }
                res.append(render(type));
                first = false;
            }
            record(new TraceEvent(TraceEventType.RESULT, res.append(']').toString()));
        }
    }

    /**
     * Record resolution operation result.
     *
     * @param description resolved value description
     */
    public void result(final String description) {
        if (enabled) {
            record(new TraceEvent(TraceEventType.RESULT, description));
        }
    }

    /**
     * Record resolution failure (always the last event of failed resolution).
     *
     * @param exception resolution error
     */
    public void error(final RuntimeException exception) {
        if (enabled) {
            record(new TraceEvent(TraceEventType.ERROR,
                    exception.getClass().getSimpleName() + ": " + exception.getMessage()));
        }
    }

    private void record(final TraceEvent event) {
        events.add(event);
    }

    private String render(final Type type) {
        return TraceTypeRenderer.render(type, Collections.<String, Type>emptyMap());
    }

    private String describeScope(final GenericDeclarationScope scope, final GenericDeclaration source) {
        final String res;
        if (scope == GenericDeclarationScope.METHOD) {
            res = "method " + TypeToStringUtils.toStringMethod((Method) source, PRINTABLE_GENERICS);
        } else if (scope == GenericDeclarationScope.CONSTRUCTOR) {
            res = "constructor " + TypeToStringUtils.toStringConstructor((Constructor) source,
                    PRINTABLE_GENERICS);
        } else {
            res = "class " + TypeToStringUtils.toStringWithNamedGenerics((Class<?>) source);
        }
        return res;
    }
}
